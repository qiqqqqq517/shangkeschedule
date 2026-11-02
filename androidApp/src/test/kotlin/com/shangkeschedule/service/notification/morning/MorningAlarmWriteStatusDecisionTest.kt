package com.shangkeschedule.service.notification.morning

import com.shangkeschedule.notification.morning.MorningAlarmWriteStatusStore.Kind
import com.shangkeschedule.notification.morning.MorningAlarmWriteStatusStore.Status
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MorningAlarmWriteStatusDecisionTest {
    private val target = LocalDateTime.parse("2026-09-29T07:15:00")

    @Test fun unsuccessfulWritesAlwaysRemainVisible() {
        assertEquals(Status(Kind.WRITE_FAILED, target), MorningAlarmWriteStatusDecision.evaluate(MorningAlarmWriter.Result.Written(0), null, target))
        assertEquals(Status(Kind.WRITE_FAILED, target), MorningAlarmWriteStatusDecision.evaluate(MorningAlarmWriter.Result.WriteFailed, null, target))
        assertEquals(Status(Kind.BLOCKED, target), MorningAlarmWriteStatusDecision.evaluate(MorningAlarmWriter.Result.BlockedByExisting, null, target))
        assertEquals(Status(Kind.DEFERRED, target), MorningAlarmWriteStatusDecision.evaluate(MorningAlarmWriter.Result.Deferred, null, target))
        assertEquals(Status(Kind.NOT_EXPRESSIBLE, target), MorningAlarmWriteStatusDecision.evaluate(MorningAlarmWriter.Result.NotExpressible, null, target))
    }

    @Test fun missingClockPreservesEarlierWarningOrReportsUnknownFallback() {
        val old = Status(Kind.BLOCKED, target)
        assertEquals(old, MorningAlarmWriteStatusDecision.evaluate(MorningAlarmWriter.Result.NoClockApp, old, null))
        assertEquals(Status(Kind.NO_CLOCK, target), MorningAlarmWriteStatusDecision.evaluate(MorningAlarmWriter.Result.NoClockApp, null, target))
    }

    @Test fun successfulOrUnchangedScheduleClearsOldWarning() {
        val old = Status(Kind.WRITE_FAILED, target)
        assertNull(MorningAlarmWriteStatusDecision.evaluate(MorningAlarmWriter.Result.Written(1), old, target))
        assertNull(MorningAlarmWriteStatusDecision.evaluate(MorningAlarmWriter.Result.Unchanged, old, target))
    }
}
