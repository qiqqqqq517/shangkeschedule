package com.shangkeschedule.notification.plan

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** 判断已知完整日期区间内，星期闹钟是否与实际计划一致。 */
object MorningAlarmWeeklyRisk {
    enum class Reason { INCOMPLETE_DATA, INSUFFICIENT_COVERAGE, MISSING_ALARM, TIME_MISMATCH }

    data class Assessment(
        val safe: Map<DayOfWeek, LocalTime>,
        val unsafe: Map<DayOfWeek, Set<Reason>>
    )

    /**
     * 起止日期为实际响铃日且包含两端；调用方须保证这些日期及次日跨零点课程已完整读取。
     * safe 只代表已知区间内一致：未来调课、未知假期及学期结束后仍需用户处理。
     */
    fun assess(
        coverageStart: LocalDate,
        coverageEnd: LocalDate,
        complete: Boolean,
        alarms: List<MorningAlarmPlan.MorningAlarm>,
        weeklyByDay: Map<DayOfWeek, LocalTime>
    ): Assessment {
        if (!complete || coverageStart > coverageEnd) {
            return Assessment(emptyMap(), weeklyByDay.keys.associateWith { setOf(Reason.INCOMPLETE_DATA) })
        }

        val alarmsByDate = alarms.groupBy { it.alarmDate }
        val datesByDay = mutableMapOf<DayOfWeek, MutableList<LocalDate>>()
        var date = coverageStart
        while (true) {
            if (date.dayOfWeek in weeklyByDay) datesByDay.getOrPut(date.dayOfWeek) { mutableListOf() }.add(date)
            if (date == coverageEnd) break
            date = date.plus(1, DateTimeUnit.DAY)
        }

        val safe = mutableMapOf<DayOfWeek, LocalTime>()
        val unsafe = mutableMapOf<DayOfWeek, Set<Reason>>()
        for ((day, expectedTime) in weeklyByDay) {
            val dates = datesByDay[day].orEmpty()
            val reasons = mutableSetOf<Reason>()
            // 一周的偶然一致不能证明周期稳定。
            if (dates.size < 2) reasons.add(Reason.INSUFFICIENT_COVERAGE)
            for (alarmDate in dates) {
                val dayAlarms = alarmsByDate[alarmDate].orEmpty()
                if (dayAlarms.isEmpty()) reasons.add(Reason.MISSING_ALARM)
                if (dayAlarms.any { it.alarmTime != expectedTime }) reasons.add(Reason.TIME_MISMATCH)
            }
            if (reasons.isEmpty()) safe[day] = expectedTime else unsafe[day] = reasons
        }
        return Assessment(safe, unsafe)
    }
}
