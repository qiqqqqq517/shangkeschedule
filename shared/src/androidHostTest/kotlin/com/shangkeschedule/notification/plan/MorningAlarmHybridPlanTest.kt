package com.shangkeschedule.notification.plan

import com.shangkeschedule.data.db.widget.WidgetCourse
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MorningAlarmHybridPlanTest {
    private fun alarm(courseDate: String, alarmDate: String, time: String) = MorningAlarmPlan.MorningAlarm(
        courseDate = LocalDate.parse(courseDate), alarmDate = LocalDate.parse(alarmDate),
        alarmTime = LocalTime.parse(time), courseName = "课程", courseStart = LocalTime(8, 0)
    )

    @Test fun groupExplicitWeekdaysByTimeWithoutInferringOtherDays() {
        val mon = alarm("2026-09-28", "2026-09-28", "07:15")
        val tue = alarm("2026-09-29", "2026-09-29", "07:15")
        val wed = alarm("2026-09-30", "2026-09-30", "08:15")
        val result = MorningAlarmHybridPlan.partition(
            listOf(mon, tue, wed), mapOf(DayOfWeek.MONDAY to LocalTime(7, 15), DayOfWeek.TUESDAY to LocalTime(7, 15))
        )
        assertEquals(listOf(MorningAlarmHybridPlan.Weekly(LocalTime(7, 15), setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))), result.weekly)
        assertEquals(listOf(wed), result.oneOff)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test fun crossingMidnightUsesAlarmDayRatherThanCourseDay() {
        val mondayCourse = alarm("2026-09-28", "2026-09-27", "23:35")
        val result = MorningAlarmHybridPlan.partition(listOf(mondayCourse), mapOf(DayOfWeek.SUNDAY to LocalTime(23, 35)))
        assertTrue(result.oneOff.isEmpty())
        assertTrue(result.conflicts.isEmpty())
        assertEquals(setOf(DayOfWeek.SUNDAY), result.weekly.single().days)
    }

    @Test fun changedTimeOnRepeatedDayIsConflictNotSecondAlarm() {
        val monday = alarm("2026-09-28", "2026-09-28", "06:30")
        val result = MorningAlarmHybridPlan.partition(listOf(monday), mapOf(DayOfWeek.MONDAY to LocalTime(7, 15)))
        assertEquals(listOf(monday), result.conflicts)
        assertTrue(result.oneOff.isEmpty())
    }

    @Test fun skippedDateInAnExplicitlyAssessedWindowIsReported() {
        val course = WidgetCourse("a", "课程", "", "", "08:00", "08:45", false, "2026-09-28", 0)
        val plan = MorningAlarmPlan.plan(listOf(course), skippedDates = setOf("2026-09-28"), today = LocalDate(2026, 9, 28), days = 1)
        val result = MorningAlarmHybridPlan.partition(
            plan, mapOf(DayOfWeek.MONDAY to LocalTime(7, 15)), setOf(LocalDate(2026, 9, 28))
        )
        assertEquals(listOf(LocalDate(2026, 9, 28)), result.unmatchedDates)
        assertEquals(1, result.weekly.size)
    }

    @Test fun suggestionsOmitAmbiguousAndUnavailableDays() {
        val mondayA = alarm("2026-09-28", "2026-09-28", "07:15")
        val mondayB = alarm("2026-10-05", "2026-10-05", "06:30")
        val tuesday = alarm("2026-09-29", "2026-09-29", "08:15")
        assertEquals(mapOf(DayOfWeek.TUESDAY to LocalTime(8, 15)), MorningAlarmHybridPlan.suggestTimes(listOf(mondayA, mondayB, tuesday)))
        assertTrue(MorningAlarmHybridPlan.suggestTimes(emptyList()).isEmpty())
    }
}
