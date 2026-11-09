package com.shangkeschedule.service.notification.receiver

import com.shangkeschedule.notification.morning.MorningAlarmSyncGate

internal fun dispatchMorningFallback(readEnabled: () -> Boolean, post: () -> Unit) = MorningAlarmSyncGate.run {
    if (readEnabled()) post()
}
