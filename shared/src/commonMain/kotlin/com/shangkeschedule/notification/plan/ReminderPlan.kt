package com.shangkeschedule.notification.plan

import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.notification.identity.NotificationIds
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * 课程提醒的「选哪几节课、分别挂到哪一刻、用哪个请求码」**纯决策**。
 *
 * 为什么单独存在：这套选择逻辑原先内联在 `CourseReminderScheduler.schedule()` 里，
 * 而该类持有 `Context` / `Intent` / `AlarmManager`，在只有 JUnit4 的单测环境里
 * 根本无法实例化（Android framework stub 会抛 `Stub!`）。于是「过期提醒要丢弃」
 * 「槽位耗尽要停」「同一 occurrence 只能用掉一个槽位」这些真正的分支**一条都测不到**。
 *
 * 本对象把它降为无平台依赖的纯函数：请求码分配经 [codeOf] 回调注入，
 * 生产环境传入 `AlarmScheduler::codeFor`，单测传入一个可断言的假分配器即可。
 *
 * 只做决策、不碰系统：真正的 `AlarmManager.setExact` 仍由
 * `com.shangkeschedule.service.notification.alarm.AlarmScheduler` 负责。
 */
object ReminderPlan {

    /**
     * 一条待挂的课程提醒。
     *
     * @param course    源课程（调用方据此构建广播 Intent）
     * @param key       occurrence 身份键，既用于分配请求码，也用于回收已失效的站内通知
     * @param triggerAt 绝对触发时刻（已含提前量，可能落在课程日前一天）
     * @param code      经 [select] 的 `codeOf` 分配到的请求码
     */
    data class Entry(
        val course: WidgetCourse,
        val key: String,
        val triggerAt: LocalDateTime,
        val code: Int
    )

    /**
     * 选出本轮应当挂载的提醒。
     *
     * 跳过规则与判定顺序（与拆分前的内联实现语义一致）：
     *  1. `course.date` 不可解析 → 跳过该课，继续看后面的；
     *  2. [ReminderEngine.reminderDateTime] 返回 null（开始时间不可解析 / 偏移超 ±1 天）→ 跳过；
     *  3. `triggerAt <= now`（已过期）→ 跳过；
     *  4. 同一 [Entry.key] 只消耗一个槽位，复用首次分配到的请求码；
     *  5. 槽位耗尽或 `codeOf` 返回 null → **终止**本轮选择（不再看后面的课）。
     *
     * @param courses        已由 [ReminderEngine.effectiveCourses] 过滤过的有效课程
     * @param leadMinutes    提前量（分钟），越界由 [ReminderEngine.reminderDateTime] 自行收敛
     * @param now            当前时刻
     * @param availableSlots 剩余可用槽位数（通常为 `totalSlots - usedSlots`）；`<= 0` 时直接返回空
     * @param codeOf         请求码分配器；返回 `null` 表示分配失败，终止本轮
     */
    fun select(
        courses: List<WidgetCourse>,
        leadMinutes: Int,
        now: LocalDateTime,
        availableSlots: Int,
        codeOf: (String) -> Int?
    ): List<Entry> {
        if (availableSlots <= 0) return emptyList()

        val selected = mutableListOf<Entry>()
        val codeByKey = mutableMapOf<String, Int>()
        var remaining = availableSlots

        for (course in courses) {
            val date = runCatching { LocalDate.parse(course.date) }.getOrNull() ?: continue
            val (reminderDate, reminderTime) =
                ReminderEngine.reminderDateTime(course, date, leadMinutes) ?: continue

            val triggerAt = LocalDateTime(reminderDate, reminderTime)
            if (triggerAt <= now) continue

            val key = NotificationIds.occurrenceKey(course)
            val code = codeByKey[key] ?: run {
                if (remaining <= 0) return selected
                val allocated = codeOf(key) ?: return selected
                remaining--
                codeByKey[key] = allocated
                allocated
            }
            selected += Entry(course = course, key = key, triggerAt = triggerAt, code = code)
        }

        return selected
    }
}
