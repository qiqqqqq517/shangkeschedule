package com.shangkeschedule.notification

import com.shangkeschedule.data.db.widget.WidgetCourse
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * 通知/自动化领域计算引擎（纯逻辑，无平台依赖）。
 *
 * 背景（旧实现病根）：课程提醒、上课自动勿扰、灵动岛窗口、早八闹钟四套调度**各自实现**
 * 「读课程 → 算时刻」的重复逻辑，分散在 `CourseNotificationWorker` / `DndSchedulerWorker` /
 * `DynamicIslandManager` 三处：
 *  - 时间解析 + 跳过日过滤 + 排序反复重写，行为漂移；
 *  - 任一处修 bug 另两处不受益；
 *  - 全部无法单测（都依赖 Android framework 与 Room）。
 *
 * 本对象把「课程 + 当前时刻 → 应当发生的调度事件」收敛为单一来源。
 * 平台层只负责「把事件挂成闹钟/通知」，不再自己算时间。
 *
 * 设计约束：本文件属 `commonMain`，**不得**引用任何 Android / java.time API——
 * 时间一律用 kotlinx-datetime 的 [LocalDate] / [LocalTime] / [kotlin.time.Instant] 表达，
 * 以保证可在 `androidHostTest` 与 JVM 桌面端直接运行。
 */
object ReminderEngine {

    /** 提醒提前量的合法区间（分钟）。0 = 正点提醒，180 = 最多提前 3 小时。 */
    val LEAD_MINUTES_RANGE = 0..180

    /** 早八闹钟提前量的合法区间（分钟）。 */
    val MORNING_LEAD_RANGE = 0..180

    /**
     * 解析 `HH:mm` / `H:mm` 形式的时间串。
     * 兼容旧数据里可能存在的非补零写法；无法解析返回 null（调用方按「无效课程」跳过）。
     */
    fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalTime.parse(value) }.getOrNull()
            ?: runCatching {
                val parts = value.trim().split(":")
                if (parts.size != 2) return@runCatching null
                LocalTime(parts[0].toInt(), parts[1].toInt())
            }.getOrNull()
    }

    /**
     * 过滤出可参与调度的有效课程：未跳过、时间可解析、且不在跳过日期集合中。
     *
     * @param skippedDates 节假日/跳过日期集合（`yyyy-MM-dd`）
     */
    fun effectiveCourses(
        courses: List<WidgetCourse>,
        skippedDates: Set<String> = emptySet()
    ): List<WidgetCourse> = courses
        .filter { !it.isSkipped }
        .filter { it.date !in skippedDates }
        .filter { parseTime(it.startTime) != null }
        .sortedWith(compareBy({ it.date }, { it.startTime }, { it.endTime }))

    /**
     * 计算一次课程提醒的绝对触发时刻 = 上课时刻 − [leadMinutes]。
     *
     * 返回「日期 + 时刻」：提前量较大（或课程在凌晨）时结果可能落到**前一天**，
     * 此时调用方需要拿真实日期去比对 `now`，不能只看时刻。
     *
     * 这里**允许**结果早于 `now`（不做过期判断）——引擎只做纯计算，
     * 「是否已过期」属调用方策略。
     *
     * @return null 当课程开始时间不可解析，或偏移超出 ±1 天（脏数据防御）
     */
    fun reminderDateTime(
        course: WidgetCourse,
        date: LocalDate,
        leadMinutes: Int
    ): Pair<LocalDate, LocalTime>? {
        val start = parseTime(course.startTime) ?: return null
        return shift(date, start, -leadMinutes.coerceIn(LEAD_MINUTES_RANGE))
    }

    /**
     * 以「当天秒数」做时间加减，并回报是否发生了跨天回绕。
     *
     * 不用 kotlinx-datetime 的 `LocalTime.minus(Duration)`：该 API 在 0.8.0 的 commonMain
     * 面已收敛，且其回绕语义未定义。显式秒数取模可精确控制跨天方向，供早八闹钟的
     * 「跨零点守卫」使用。
     *
     * @return null 当 [minutes] 偏移超过 ±1 天（正常提前量不可能触及，属脏数据防御）
     */
    fun shiftMinutes(time: LocalTime, minutes: Int): ShiftedTime? {
        val daySeconds = 24 * 60 * 60
        val target = secondOfDay(time) + minutes * 60
        // 允许跨 1 天（-1 / +1 天方向），再多则视为脏数据
        if (target < -daySeconds || target >= daySeconds * 2) return null
        val wrappedForward = target >= daySeconds
        val wrappedBackward = target < 0
        val normalized = ((target % daySeconds) + daySeconds) % daySeconds
        return ShiftedTime(
            time = LocalTime.fromSecondOfDay(normalized),
            crossedForward = wrappedForward,
            crossedBackward = wrappedBackward
        )
    }

    /** 当天的秒数（00:00:00 = 0）。kotlinx-datetime 0.8.0 的 commonMain 面未暴露该属性，故显式计算。 */
    private fun secondOfDay(time: LocalTime): Int =
        time.hour * 3600 + time.minute * 60 + time.second

    /** [shiftMinutes] 的结果：偏移后的时刻 + 跨天方向。 */
    data class ShiftedTime(
        val time: LocalTime,
        /** 偏移后落到次日 */
        val crossedForward: Boolean = false,
        /** 偏移后落到前一日 */
        val crossedBackward: Boolean = false
    ) {
        /** 是否发生了跨天回绕（任一方向）。 */
        val crossedDay: Boolean get() = crossedForward || crossedBackward
    }

    /**
     * 按分钟偏移一个「日期 + 时刻」，得到真实的目标日期与时刻。
     * 专门服务于需要绝对时刻的场景（早八闹钟写入系统时钟）。
     */
    fun shift(date: LocalDate, time: LocalTime, minutes: Int): Pair<LocalDate, LocalTime>? {
        val shifted = shiftMinutes(time, minutes) ?: return null
        val targetDate = when {
            shifted.crossedForward -> date.plus(1, DateTimeUnit.DAY)
            shifted.crossedBackward -> date.minus(1, DateTimeUnit.DAY)
            else -> date
        }
        return targetDate to shifted.time
    }

    // ---------------------------------------------------------------------
    // 上课自动勿扰 / 静音
    // ---------------------------------------------------------------------

    /** 自动模式的一次状态切换事件。 */
    data class AutoModeTransition(
        val date: LocalDate,
        /** 触发时刻（本地时间） */
        val time: LocalTime,
        /** true = 开启勿扰/静音；false = 关闭恢复 */
        val enable: Boolean
    )

    /**
     * 计算自动模式的完整切换时刻序列。
     *
     * 规则：每节课「开始 → 开启」、「结束 → 关闭」。课间相邻课（上一节结束 == 下一节开始）
     * 会产生「关-开」同刻对，此处**合并**为一次开启，避免在课间瞬间闪一下恢复正常铃声。
     *
     * 旧实现只排「下一个开 / 下一个关」两个闹钟（`findNextDndAlarmTimes`），
     * 一节课结束后若进程被杀、END 闹钟丢失，模式会**卡在开启态**直到下一次同步——
     * 本引擎输出完整序列，配合 `shouldModeBeOn` 校准可自愈。
     */
    fun autoModeTransitions(
        courses: List<WidgetCourse>,
        skippedDates: Set<String> = emptySet()
    ): List<AutoModeTransition> {
        val effective = effectiveCourses(courses, skippedDates)
        val transitions = mutableListOf<AutoModeTransition>()

        for (course in effective) {
            val date = runCatching { LocalDate.parse(course.date) }.getOrNull() ?: continue
            val start = parseTime(course.startTime) ?: continue
            val end = parseTime(course.endTime) ?: continue
            transitions += AutoModeTransition(date, start, enable = true)
            transitions += AutoModeTransition(date, end, enable = false)
        }

        return mergeAdjacent(transitions)
    }

    /**
     * 归一化转换序列，分两步：
     *
     * 1. **同刻合并**：把「上节结束 == 下节开始」的（关, 开）同刻对压成单次**开启**
     *    ——课间不应闪回正常铃声；
     * 2. **连续去重**：合并后若出现相邻同态事件（如 08:00 开 与 08:45 开），
     *    后者不制造任何状态变化，属空操作，丢弃之。
     */
    private fun mergeAdjacent(transitions: List<AutoModeTransition>): List<AutoModeTransition> {
        if (transitions.isEmpty()) return transitions
        val sorted = transitions.sortedWith(compareBy({ it.date }, { it.time }))

        // 步骤 1：同刻合并（开启优先）
        val merged = mutableListOf<AutoModeTransition>()
        for (t in sorted) {
            val last = merged.lastOrNull()
            if (last != null && last.date == t.date && last.time == t.time) {
                if (!last.enable && t.enable) {
                    merged[merged.lastIndex] = last.copy(enable = true)
                }
                continue
            }
            merged += t
        }

        // 步骤 2：连续同态去重（保留首次出现）
        val result = mutableListOf<AutoModeTransition>()
        for (t in merged) {
            if (result.lastOrNull()?.enable == t.enable) continue
            result += t
        }
        return result
    }

    /**
     * 给定时刻是否应当处于自动模式开启态。
     *
     * 供平台层做「卡态校准」：每次重新排程时对齐真实状态，
     * 修复旧实现 `isCurrentlyInDndTime()` 算完只打日志、从不应用的死代码缺陷。
     */
    fun shouldModeBeOn(
        courses: List<WidgetCourse>,
        skippedDates: Set<String>,
        date: LocalDate,
        time: LocalTime
    ): Boolean {
        return effectiveCourses(courses, skippedDates).any { course ->
            if (course.date != date.toString()) return@any false
            val start = parseTime(course.startTime) ?: return@any false
            val end = parseTime(course.endTime) ?: return@any false
            time >= start && time < end
        }
    }

    // ---------------------------------------------------------------------
    // 灵动岛显示窗口
    // ---------------------------------------------------------------------

    /** 灵动岛显示窗口：第一节课开始 − 提前量 → 最后一节课结束。 */
    data class IslandWindow(
        val date: LocalDate,
        val start: LocalTime,
        val end: LocalTime
    )

    /**
     * 计算某天的灵动岛显示窗口；当天无有效课程返回 null。
     *
     * 语义与原 `DynamicIslandManager.computeWindow` 一致，此处收敛为纯逻辑版本。
     */
    fun islandWindow(
        courses: List<WidgetCourse>,
        skippedDates: Set<String>,
        date: LocalDate,
        leadMinutes: Int
    ): IslandWindow? {
        val valid = effectiveCourses(courses, skippedDates).filter { it.date == date.toString() }
        if (valid.isEmpty()) return null
        // 结束时间缺失/不可解析的课程不参与「最后一节课」判定（否则会算出离谱窗口）
        val starts = valid.mapNotNull { parseTime(it.startTime) }
        val ends = valid.mapNotNull { parseTime(it.endTime) }
        if (starts.isEmpty() || ends.isEmpty()) return null
        // 窗口起点允许跨到前一日（凌晨第一节课 + 提前量），此处只回绕时刻、不影响窗口归属日
        val start = shiftMinutes(starts.min(), -leadMinutes.coerceIn(LEAD_MINUTES_RANGE))?.time
            ?: starts.min()
        return IslandWindow(date, start, ends.max())
    }
}
