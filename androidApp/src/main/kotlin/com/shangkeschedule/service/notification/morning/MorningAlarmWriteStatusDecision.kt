package com.shangkeschedule.service.notification.morning

import com.shangkeschedule.notification.morning.MorningAlarmWriteStatusStore.Status
import com.shangkeschedule.notification.morning.MorningAlarmWriteStatusStore.Kind
import kotlinx.datetime.LocalDateTime

internal object MorningAlarmWriteStatusDecision {
    fun evaluate(result: MorningAlarmWriter.Result, old: Status?, plannedAt: LocalDateTime?): Status? = when (result) {
        MorningAlarmWriter.Result.BlockedByExisting -> Status(Kind.BLOCKED, plannedAt)
        MorningAlarmWriter.Result.Deferred -> Status(Kind.DEFERRED, plannedAt)
        MorningAlarmWriter.Result.NotExpressible -> Status(Kind.NOT_EXPRESSIBLE, plannedAt)
        MorningAlarmWriter.Result.WriteFailed -> Status(Kind.WRITE_FAILED, plannedAt)
        MorningAlarmWriter.Result.NoClockApp -> old ?: Status(Kind.NO_CLOCK, plannedAt)
        is MorningAlarmWriter.Result.Written -> if (result.count > 0) null else Status(Kind.WRITE_FAILED, plannedAt)
        MorningAlarmWriter.Result.Unchanged -> null
    }
}
