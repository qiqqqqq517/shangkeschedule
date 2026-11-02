package com.shangkeschedule.notification.plan

import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.main.TimeSlotScheme
import com.shangkeschedule.data.db.widget.WidgetCourse
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** 从完整课表快照展开各个实际日期，无法解释的数据不得视为无课。 */
object MorningAlarmSemesterProjection {
    data class Result(
        val complete: Boolean,
        val coverageStart: LocalDate?,
        val coverageEnd: LocalDate?,
        val alarms: List<MorningAlarmPlan.MorningAlarm>
    ) {
        companion object {
            fun incomplete() = Result(false, null, null, emptyList())
        }
    }

    fun project(
        config: CourseTableConfig?, courses: List<CourseWithWeeks>, schemes: List<TimeSlotScheme>,
        slotsByScheme: Map<String, List<TimeSlot>>, skippedDates: Set<String>, today: LocalDate, leadMinutes: Int
    ): Result {
        return runCatching {
        val table = config ?: return Result.incomplete()
        val start = table.semesterStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return Result.incomplete()
        if (table.courseTableId.isBlank() || table.firstDayOfWeek !in 1..7 || table.semesterTotalWeeks !in 1..60) return Result.incomplete()
        if (skippedDates.any { runCatching { LocalDate.parse(it) }.isFailure }) return Result.incomplete()
        if (schemes.any { it.courseTableId != table.courseTableId } || slotsByScheme.any { (scheme, slots) ->
                slots.any { it.courseTableId != table.courseTableId || it.schemeId != scheme } ||
                    slots.map { it.number }.distinct().size != slots.size
            }) return Result.incomplete()
        if (table.autoSwitchScheme && schemes.any {
                (it.startMonthDay == null) != (it.endMonthDay == null) ||
                    (it.startMonthDay != null && TimeSlotScheme.parseMonthDay(it.startMonthDay) < 0) ||
                    (it.endMonthDay != null && TimeSlotScheme.parseMonthDay(it.endMonthDay) < 0)
            }) return Result.incomplete()
        if (courses.any { entry ->
                entry.course.courseTableId != table.courseTableId || entry.course.day !in 1..7 || entry.weeks.isEmpty() ||
                    entry.weeks.any { it.courseId != entry.course.id || it.weekNumber !in 1..table.semesterTotalWeeks }
            }) return Result.incomplete()

        val weekOffset = (start.dayOfWeek.isoDayNumber - table.firstDayOfWeek + 7) % 7
        val first = start.minus(weekOffset, DateTimeUnit.DAY)
        val last = first.plus(table.semesterTotalWeeks * 7 - 1, DateTimeUnit.DAY)
        // 最后一天的次日已超出数据覆盖，不能判定跨午夜的提醒是否存在。
        val coveredEnd = last.minus(1, DateTimeUnit.DAY)
        if (today < start || today > coveredEnd) return Result.incomplete()

        val expanded = mutableListOf<WidgetCourse>()
        var day = today
        while (day <= last) {
            val week = first.daysUntil(day) / 7 + 1
            val activeScheme = if (table.autoSwitchScheme) {
                val monthDay = day.monthNumber * 100 + day.dayOfMonth
                schemes.firstOrNull { it.isActiveAt(monthDay) }?.schemeId ?: table.currentSchemeId
            } else table.currentSchemeId
            val slots = slotsByScheme[activeScheme].orEmpty().associateBy { it.number }
            for (entry in courses) {
                val course = entry.course
                if (course.day != day.dayOfWeek.isoDayNumber || entry.weeks.none { it.weekNumber == week }) continue
                if (day.toString() in skippedDates) continue
                val startText = if (course.isCustomTime) course.customStartTime else slots[course.startSection]?.startTime
                val endText = if (course.isCustomTime) course.customEndTime else slots[course.endSection]?.endTime
                val startTime = ReminderEngine.parseTime(startText) ?: return Result.incomplete()
                val endTime = ReminderEngine.parseTime(endText) ?: return Result.incomplete()
                if (endTime <= startTime) return Result.incomplete()
                expanded.add(WidgetCourse("${course.id}-$day", course.name, course.teacher, course.position,
                    startTime.toString(), endTime.toString(), false, day.toString(), course.colorInt))
            }
            day = day.plus(1, DateTimeUnit.DAY)
        }
        Result(true, today, coveredEnd, MorningAlarmPlan.plan(
            expanded, today = today, days = today.daysUntil(last) + 1, leadMinutes = leadMinutes
        ))
        }.getOrElse { Result.incomplete() }
    }
}
