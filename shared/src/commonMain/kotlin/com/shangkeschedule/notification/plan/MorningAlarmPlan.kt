package com.shangkeschedule.notification.plan

import com.shangkeschedule.data.db.widget.WidgetCourse
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * 每日「早八闹钟」计划（纯逻辑，无平台依赖）。
 *
 * 需求：自动读取**每天第一节课**的时间，按用户自由设置的提前分钟数，
 * 算出应当写入系统时钟应用（桌面时钟/闹钟页）的闹钟时刻。
 *
 * 关键语义：
 *  - **每天一节**：只取当天最早的一节课（`min(startTime)`），早八场景就是要「叫你起床去上第一节课」；
 *  - **无课日不排**：当天无有效课程（或整日被 `skippedDates` 跳过）→ 当天无闹钟；
 *  - **提前量自由**：0–180 分钟，0 = 第一节课正点；默认 45；
 *  - **跨零点守卫**：若 `第一节课 − 提前量` 落到前一天（如 00:20 的课提前 45 分钟 → 前一日 23:35），
 *    仍归属该课当天但时刻回退到前一日，此处显式允许并保留真实时刻，由调用方决定是否写入
 *    （避免产生「昨天 23:35」这种看起来像 bug 的闹钟）。
 */
object MorningAlarmPlan {

    /** 一条待写入系统时钟的闹钟计划。 */
    data class MorningAlarm(
        /** 该闹钟对应的课程日期（用于标签展示与注册簿键） */
        val courseDate: LocalDate,
        /** 闹钟实际响铃日期（提前量跨零点时为前一天） */
        val alarmDate: LocalDate,
        /** 闹钟实际响铃时刻 */
        val alarmTime: LocalTime,
        /** 第一节课的名称（写入标签，方便用户在系统闹钟页辨认） */
        val courseName: String,
        /** 第一节课的上课时刻（用于预览展示「第一节 08:00」） */
        val courseStart: LocalTime
    ) {
        /** 是否发生了跨零点（闹钟日期早于课程日期）。 */
        val crossedMidnight: Boolean get() = alarmDate != courseDate
    }

    /**
     * 计算未来 [days] 天的早八闹钟计划。
     *
     * @param courses 已按日期范围取出的课程（widget 表预计算未来若干天）
     * @param skippedDates 跳过的节假日/停课日期集合
     * @param today 起始日期（通常为今天）
     * @param days 前瞻天数（默认 7，与提醒闹钟的 7 天窗口一致）
     * @param leadMinutes 提前分钟数（0–180）
     */
    fun plan(
        courses: List<WidgetCourse>,
        skippedDates: Set<String> = emptySet(),
        today: LocalDate,
        days: Int = 7,
        leadMinutes: Int = 45
    ): List<MorningAlarm> {
        if (days <= 0) return emptyList()
        val lead = leadMinutes.coerceIn(ReminderEngine.MORNING_LEAD_RANGE)
        val effective = ReminderEngine.effectiveCourses(courses, skippedDates)

        return (0 until days).mapNotNull { offset ->
            val date = today.plusDaysCompat(offset)
            // 当天最早的一节课
            val first = effective
                .filter { it.date == date.toString() }
                .minByOrNull { ReminderEngine.parseTime(it.startTime)!! }
                ?: return@mapNotNull null

            val start = ReminderEngine.parseTime(first.startTime) ?: return@mapNotNull null
            val end = ReminderEngine.parseTime(first.endTime) ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null // 数据异常：结束不晚于开始，跳过

            // 用引擎的绝对偏移拿到真实响铃「日期 + 时刻」，跨零点时自然归到前一天
            val (alarmDate, alarmTime) = ReminderEngine.shift(date, start, -lead)
                ?: return@mapNotNull null
            MorningAlarm(
                courseDate = date,
                alarmDate = alarmDate,
                alarmTime = alarmTime,
                courseName = first.name,
                courseStart = start
            )
        }
    }

    /**
     * 取计划中**最近一次尚未到来**的闹钟（无则返回 null）。
     *
     * ## 为什么只能维护「最近一条」（真机实测结论）
     *
     * `AlarmClock.ACTION_SET_ALARM` 只能携带 `EXTRA_HOUR` / `EXTRA_MINUTES`，
     * **无法表达日期**；系统时钟应用把它理解为「**下一次**该时刻」，并建一条
     * **一次性**闹钟（本机 Xiaomi 22041216UC / MIUI / Android 14 的 DeskClock
     * 实测为 `deleteAfterUse:true`，响过即被系统删除）。
     *
     * 后果：在 09-27 写「09-30 07:05」并不会得到 09-30 的闹钟，而是得到
     * **09-28 07:05**（最近的一次 07:05），标签却写着 09-30；写三条未来闹钟
     * 会得到三条**不同时刻、同一个错误日期**的闹钟（实测三条全落在 09-28）。
     *
     * 所以想写多条未来闹钟必然错日期，唯一正确做法是**只写最近一条**，
     * 之后由「每日零点自愈 + 回到前台」滚动补位下一条
     * （见 `MorningAlarmWriter` 的登记簿回收：已响过的条目会被丢弃，从而触发补位）。
     */
    fun nextUpcoming(
        plan: List<MorningAlarm>,
        now: LocalDateTime
    ): MorningAlarm? = plan
        .filter { LocalDateTime(it.alarmDate, it.alarmTime) > now }
        .minByOrNull { LocalDateTime(it.alarmDate, it.alarmTime) }

    /**
     * 校验这条闹钟能否被系统时钟**如实表达**。
     *
     * 系统时钟的语义是「以 [now] 为基准的下一次 [MorningAlarm.alarmTime]」。
     * 只有当本计划的 `alarmDate` 恰好等于那一次时，系统建出的闹钟才与计划日期一致。
     *
     * 跨零点的闹钟（提前量把时刻推到课程前一天，如 00:20 的课提前 45 分钟 →
     * 前一日 23:35）天然满足该等式；不满足说明计划本身有异常，此时**宁可不写**——
     * 写下去只会得到一条日期错误、且因为「登记簿已记入」而永远无法纠正的闹钟。
     */
    fun expressibleBySystemClock(
        alarm: MorningAlarm,
        now: LocalDateTime
    ): Boolean {
        val target = LocalDateTime(alarm.alarmDate, alarm.alarmTime)
        if (target <= now) return false
        val sameDay = LocalDateTime(now.date, alarm.alarmTime)
        val next = if (sameDay > now) sameDay else LocalDateTime(now.date.plus(1, DateTimeUnit.DAY), alarm.alarmTime)
        return target == next
    }

    /**
     * 生成写入系统时钟的闹钟标签。
     *
     * 格式：`早八·MM-dd HH:mm 课程名`。
     * 标签中带**完整日期与时刻**，一是用户在系统闹钟页可辨认是哪天的哪节课，
     * 二是删除时可用它做精确搜索（见平台层 `MorningAlarmWriter`）。
     */
    fun label(alarm: MorningAlarm): String {
        val dateText = formatMonthDay(alarm.courseDate)
        val timeText = formatTime(alarm.alarmTime)
        val name = alarm.courseName.ifBlank { "第一节课" }
        return "早八·$dateText $timeText $name"
    }

    fun formatMonthDay(date: LocalDate): String =
        "${pad2(date.monthNumber)}-${pad2(date.dayOfMonth)}"

    fun formatTime(time: LocalTime): String =
        "${pad2(time.hour)}:${pad2(time.minute)}"

    private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()

    /** kotlinx-datetime 0.8.0 的 commonMain 面没有 `LocalDate.plusDays`，统一走 `plus(n, DAY)`。 */
    private fun LocalDate.plusDaysCompat(days: Int): LocalDate =
        plus(days, DateTimeUnit.DAY)
}
