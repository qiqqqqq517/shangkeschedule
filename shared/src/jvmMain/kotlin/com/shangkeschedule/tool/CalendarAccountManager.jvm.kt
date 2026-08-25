package com.shangkeschedule.tool

import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.TimeSlot
import kotlinx.datetime.LocalDate

actual object CalendarAccountManager {
    actual suspend fun syncCurrentTableToSystemCalendar(
        courses: List<CourseWithWeeks>,
        timeSlots: List<TimeSlot>,
        semesterStartDate: LocalDate,
        semesterTotalWeeks: Int,
        firstDayOfWeekInt: Int,
        alarmMinutes: Int?,
        skippedDates: Set<String>?
    ): Boolean {
        return false
    }
}