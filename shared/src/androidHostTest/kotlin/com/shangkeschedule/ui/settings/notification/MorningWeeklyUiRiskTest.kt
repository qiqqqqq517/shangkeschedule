package com.shangkeschedule.ui.settings.notification

import com.shangkeschedule.notification.plan.MorningAlarmPlan
import com.shangkeschedule.notification.plan.MorningAlarmSemesterProjection
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MorningWeeklyUiRiskTest {
    private fun alarm(date: String) = MorningAlarmPlan.MorningAlarm(
        LocalDate.parse(date), LocalDate.parse(date), LocalTime(7, 15), "课程", LocalTime(8, 0)
    )

    @Test fun onlyWeekdaysMatchingEveryCoveredDateCanBeCreated() {
        val projection = MorningAlarmSemesterProjection.Result(
            true, LocalDate(2026, 9, 28), LocalDate(2026, 10, 11),
            listOf(alarm("2026-09-28"), alarm("2026-10-05"), alarm("2026-09-29"))
        )
        assertEquals(setOf(2), morningWeeklyUnsafeDays(projection, mapOf(1 to "07:15", 2 to "07:15")))
    }

    @Test fun missingCoverageAndInvalidTimesCannotPassTheUiGuard() {
        assertEquals(setOf(1, 3), morningWeeklyUnsafeDays(
            MorningAlarmSemesterProjection.Result.incomplete(), mapOf(1 to "07:15", 3 to "08:00")
        ))
        assertEquals(setOf(1), morningWeeklyUnsafeDays(
            MorningAlarmSemesterProjection.Result.incomplete(), mapOf(1 to "not-a-time")
        ))
    }
}
