package com.shangkeschedule.service.notification.morning

import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.MorningAlarmMode
import com.shangkeschedule.notification.plan.MorningAlarmPlan
import com.shangkeschedule.notification.plan.MorningAlarmSemesterProjection
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningAlarmReviewDecisionTest {
    private val settings = AppSettingsModel(morningAlarmEnabled = true, morningAlarmMode = MorningAlarmMode.HYBRID_WEEKLY,
        morningAlarmWeeklyTimes = mapOf(1 to "07:15"), morningAlarmWeeklyRevision = "revision")
    private val confirmed = mapOf(DayOfWeek.MONDAY to LocalTime(7, 15))
    private fun alarm(day: Int, time: LocalTime = LocalTime(7, 15)) = MorningAlarmPlan.MorningAlarm(
        LocalDate(2026, 9, day), LocalDate(2026, 9, day), time, "课程", LocalTime(8, 0))
    private val complete = MorningAlarmSemesterProjection.Result(true, LocalDate(2026, 9, 7), LocalDate(2026, 9, 20), listOf(alarm(7), alarm(14)))

    @Test fun missingConfirmationOrIncompleteSnapshotRequiresReview() {
        assertTrue(MorningAlarmReviewDecision.requiresReview(settings, emptyMap(), complete))
        assertTrue(MorningAlarmReviewDecision.requiresReview(settings, confirmed, MorningAlarmSemesterProjection.Result.incomplete()))
    }

    @Test fun changedOrSkippedClassRequiresReviewEvenWithConfirmedWeeklyAlarm() {
        assertTrue(MorningAlarmReviewDecision.requiresReview(settings, confirmed, complete.copy(alarms = listOf(alarm(7)))))
        assertTrue(MorningAlarmReviewDecision.requiresReview(settings, confirmed, complete.copy(alarms = listOf(alarm(7), alarm(14, LocalTime(6, 45))))))
        assertFalse(MorningAlarmReviewDecision.requiresReview(settings, confirmed, complete))
    }

    @Test fun changedRelevantSettingsInvalidateInFlightAssessment() {
        assertTrue(MorningAlarmReviewDecision.sameSettings(settings, settings))
        assertFalse(MorningAlarmReviewDecision.sameSettings(settings, settings.copy(morningAlarmWeeklyRevision = "new")))
        assertFalse(MorningAlarmReviewDecision.sameSettings(settings, settings.copy(skippedDates = setOf("2026-09-14"))))
        assertFalse(MorningAlarmReviewDecision.sameSettings(settings, settings.copy(currentCourseTableId = "other")))
        assertFalse(MorningAlarmReviewDecision.sameSettings(settings, settings.copy(morningAlarmLeadMinutes = 30)))
    }
    @Test fun disablingSuppressesPendingReviewNotice() {
        assertEquals(MorningAlarmReviewDecision.Notice.CANCEL,
            MorningAlarmReviewDecision.notice(enabled = false, pending = true, clearResolved = false))
    }

    @Test fun enabledReviewStillNotifiesAndResolvedInspectionDoesNotClear() {
        assertEquals(MorningAlarmReviewDecision.Notice.POST,
            MorningAlarmReviewDecision.notice(enabled = true, pending = true, clearResolved = false))
        assertEquals(MorningAlarmReviewDecision.Notice.KEEP,
            MorningAlarmReviewDecision.notice(enabled = true, pending = false, clearResolved = false))
        assertEquals(MorningAlarmReviewDecision.Notice.CANCEL,
            MorningAlarmReviewDecision.notice(enabled = true, pending = false, clearResolved = true))
    }
}
