package com.shangkeschedule.notification.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber

/**
 * 按用户明确选择的响铃星期，划分每周重复闹钟与按日期的一次性闹钟。
 * 不会根据近期样本自动认定任何星期适合长期重复。
 */
object MorningAlarmHybridPlan {
    data class Weekly(val time: LocalTime, val days: Set<DayOfWeek>)

    /**
     * [conflicts] 是已有重复闹钟覆盖但当天课表要求不同时间，须由用户检查的课程。
     * [unmatchedDates] 是已确认覆盖的日期中，重复闹钟会响但无课程计划的日期。
     * 两者都不会额外生成一次性闹钟，避免和系统重复闹钟双响。
     */
    data class Partition(
        val weekly: List<Weekly>,
        val oneOff: List<MorningAlarmPlan.MorningAlarm>,
        val conflicts: List<MorningAlarmPlan.MorningAlarm>,
        val unmatchedDates: List<LocalDate>
    )

    /**
     * [weeklyByDay] 按实际响铃星期而非课程星期指定时刻。
     * [assessedAlarmDates] 只能包含调用方已完整获取课程计划的响铃日期；
     * 调用方必须考虑提前量跨零点可能涉及次日课程，否则不能把缺项判作无课。
     */
    fun partition(
        plan: List<MorningAlarmPlan.MorningAlarm>,
        weeklyByDay: Map<DayOfWeek, LocalTime>,
        assessedAlarmDates: Set<LocalDate> = emptySet()
    ): Partition {
        val weekly = weeklyByDay.entries
            .sortedBy { it.key.isoDayNumber }
            .groupBy { it.value }
            .map { (time, entries) -> Weekly(time, entries.map { it.key }.toSet()) }
            .sortedBy { it.time }
        val oneOff = plan.filter { it.alarmDate.dayOfWeek !in weeklyByDay }
        val conflicts = plan.filter {
            val expected = weeklyByDay[it.alarmDate.dayOfWeek]
            expected != null && expected != it.alarmTime
        }
        val plannedDates = plan.map { it.alarmDate }.toSet()
        val unmatchedDates = assessedAlarmDates
            .filter { it.dayOfWeek in weeklyByDay && it !in plannedDates }
            .sorted()
        return Partition(weekly, oneOff, conflicts, unmatchedDates)
    }

    /** 根据可见样本建议时刻；缺课与不同时刻不提供建议，不能据此判定长期稳定。 */
    fun suggestTimes(plan: List<MorningAlarmPlan.MorningAlarm>): Map<DayOfWeek, LocalTime> =
        plan.groupBy { it.alarmDate.dayOfWeek }.mapNotNull { (day, alarms) ->
            val times = alarms.map { it.alarmTime }.distinct()
            times.singleOrNull()?.let { day to it }
        }.toMap()
}
