package com.shangkeschedule.service.notification.morning

import com.shangkeschedule.notification.plan.MorningAlarmPlan
import com.shangkeschedule.notification.registry.MorningAlarmRegistry
import kotlinx.datetime.LocalDateTime

internal object MorningAlarmWriteDecision {
    enum class State { NoPlan, AlreadyRegistered, BlockedByExisting, NotExpressible, Deferred, ReadyToWrite }

    fun evaluate(
        plan: List<MorningAlarmPlan.MorningAlarm>,
        registry: Map<String, MorningAlarmRegistry.Entry>,
        now: LocalDateTime,
        visible: Boolean
    ): State {
        val unexpired = MorningAlarmRegistry.prune(registry, now, MORNING_ALARM_RETENTION_DAYS)
        val next = MorningAlarmPlan.nextUpcoming(plan, now)
            ?: return if (unexpired.isEmpty()) State.NoPlan else State.BlockedByExisting
        val targetTime = LocalDateTime(next.alarmDate, next.alarmTime)
        val registered = unexpired[next.courseDate.toString()]
        if (unexpired.size == 1 && registered?.firedAt == targetTime && registered.label == MorningAlarmPlan.label(next)) {
            return State.AlreadyRegistered
        }
        if (unexpired.isNotEmpty()) return State.BlockedByExisting
        if (!MorningAlarmPlan.expressibleBySystemClock(next, now)) return State.NotExpressible
        if (!visible) return State.Deferred
        return State.ReadyToWrite
    }
}
