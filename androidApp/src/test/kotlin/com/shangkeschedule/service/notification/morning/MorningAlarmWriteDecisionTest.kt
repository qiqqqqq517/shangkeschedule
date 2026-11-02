package com.shangkeschedule.service.notification.morning

import com.shangkeschedule.notification.plan.MorningAlarmPlan
import com.shangkeschedule.notification.registry.MorningAlarmRegistry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MorningAlarmWriteDecisionTest {
    private val now = LocalDateTime.parse("2026-09-28T12:00:00")
    private val tomorrow = alarm("2026-09-29", 7, 15)
    private fun alarm(date: String, hour: Int, minute: Int) = MorningAlarmPlan.MorningAlarm(
        LocalDate.parse(date), LocalDate.parse(date), LocalTime(hour, minute), "课程", LocalTime(8, 0)
    )
    private fun entry(alarm: MorningAlarmPlan.MorningAlarm, firedAt: LocalDateTime = LocalDateTime(alarm.alarmDate, alarm.alarmTime)) =
        MorningAlarmRegistry.Entry(firedAt, MorningAlarmPlan.label(alarm))

    @Test fun backgroundWithMatchingFutureRegistrationDoesNotWarn() {
        assertEquals(MorningAlarmWriteDecision.State.AlreadyRegistered,
            MorningAlarmWriteDecision.evaluate(listOf(tomorrow), mapOf(tomorrow.courseDate.toString() to entry(tomorrow)), now, false))
    }

    @Test fun matchingAlarmDoesNotHideAnotherPendingOldAlarm() {
        val extra = alarm("2026-09-30", 8, 0)
        assertEquals(MorningAlarmWriteDecision.State.BlockedByExisting,
            MorningAlarmWriteDecision.evaluate(listOf(tomorrow), mapOf(
                tomorrow.courseDate.toString() to entry(tomorrow),
                extra.courseDate.toString() to entry(extra)
            ), now, false))
    }

    @Test fun expiredRegistrationDoesNotSuppressDeferredWarning() {
        val old = alarm("2026-09-28", 7, 15)
        assertEquals(MorningAlarmWriteDecision.State.Deferred,
            MorningAlarmWriteDecision.evaluate(listOf(tomorrow), mapOf(old.courseDate.toString() to entry(old)), now, false))
    }

    @Test fun differentUnexpiredAlarmBlocksNewPlanEvenWhenInBackground() {
        val old = alarm("2026-09-29", 8, 0)
        assertEquals(MorningAlarmWriteDecision.State.BlockedByExisting,
            MorningAlarmWriteDecision.evaluate(listOf(tomorrow), mapOf(old.courseDate.toString() to entry(old)), now, false))
    }

    @Test fun systemClockCannotExpressAPlanSeveralDaysAway() {
        val later = alarm("2026-10-01", 7, 15)
        assertEquals(MorningAlarmWriteDecision.State.NotExpressible,
            MorningAlarmWriteDecision.evaluate(listOf(later), emptyMap(), now, true))
    }

    @Test fun visibleExpressiblePlanCanBeWrittenAndEmptyPlanNeedsNoNotice() {
        assertEquals(MorningAlarmWriteDecision.State.ReadyToWrite,
            MorningAlarmWriteDecision.evaluate(listOf(tomorrow), emptyMap(), now, true))
        assertEquals(MorningAlarmWriteDecision.State.NoPlan,
            MorningAlarmWriteDecision.evaluate(emptyList(), emptyMap(), now, false))
    }

    @Test fun pendingOldAlarmWithNoCurrentCourseStillNeedsReview() {
        assertEquals(MorningAlarmWriteDecision.State.BlockedByExisting,
            MorningAlarmWriteDecision.evaluate(emptyList(), mapOf(tomorrow.courseDate.toString() to entry(tomorrow)), now, true))
    }
}
