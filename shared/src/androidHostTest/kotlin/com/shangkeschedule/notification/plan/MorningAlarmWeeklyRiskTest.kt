package com.shangkeschedule.notification.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MorningAlarmWeeklyRiskTest {
    private val monday = DayOfWeek.MONDAY
    private val at715 = LocalTime(7, 15)
    private fun alarm(courseDate: String, alarmDate: String = courseDate, time: LocalTime = at715) =
        MorningAlarmPlan.MorningAlarm(LocalDate.parse(courseDate), LocalDate.parse(alarmDate), time, "课程", LocalTime(8, 0))

    private fun assess(plan: List<MorningAlarmPlan.MorningAlarm>, complete: Boolean = true) =
        MorningAlarmWeeklyRisk.assess(
            LocalDate(2026, 9, 28), LocalDate(2026, 10, 11), complete, plan, mapOf(monday to at715)
        )

    @Test fun weeklyRepeatRequiresAMatchingAlarmForEveryKnownMonday() {
        val weekly = assess(listOf(alarm("2026-09-28"), alarm("2026-10-05")))
        assertEquals(mapOf(monday to at715), weekly.safe)
        assertTrue(weekly.unsafe.isEmpty())

        val alternatingOrSkipped = assess(listOf(alarm("2026-09-28")))
        assertTrue(alternatingOrSkipped.safe.isEmpty())
        assertEquals(setOf(MorningAlarmWeeklyRisk.Reason.MISSING_ALARM), alternatingOrSkipped.unsafe[monday])
    }

    @Test fun changedCourseTimeOrTwoDifferentAlarmsOnSameDayAreRisky() {
        val changed = assess(listOf(alarm("2026-09-28"), alarm("2026-10-05", time = LocalTime(6, 30))))
        assertEquals(setOf(MorningAlarmWeeklyRisk.Reason.TIME_MISMATCH), changed.unsafe[monday])

        val collision = assess(listOf(alarm("2026-09-28"), alarm("2026-10-05"), alarm("2026-10-05", time = LocalTime(6, 30))))
        assertTrue(MorningAlarmWeeklyRisk.Reason.TIME_MISMATCH in collision.unsafe.getValue(monday))
    }

    @Test fun incompleteDataAndShortOrEmptyWindowCannotProveWeeklySafety() {
        val completePlan = listOf(alarm("2026-09-28"), alarm("2026-10-05"))
        assertTrue(assess(completePlan, complete = false).safe.isEmpty())
        val short = MorningAlarmWeeklyRisk.assess(
            LocalDate(2026, 9, 28), LocalDate(2026, 10, 4), true, completePlan, mapOf(monday to at715)
        )
        assertTrue(short.safe.isEmpty())
        assertTrue(MorningAlarmWeeklyRisk.Reason.INSUFFICIENT_COVERAGE in short.unsafe.getValue(monday))
        val reversed = MorningAlarmWeeklyRisk.assess(
            LocalDate(2026, 10, 5), LocalDate(2026, 9, 28), true, completePlan, mapOf(monday to at715)
        )
        assertTrue(reversed.safe.isEmpty())
    }

    @Test fun crossedMidnightIsClassifiedOnAlarmDay() {
        val overnight = listOf(
            alarm("2026-09-29", "2026-09-28"), alarm("2026-10-06", "2026-10-05")
        )
        assertEquals(mapOf(monday to at715), assess(overnight).safe)
    }
}
