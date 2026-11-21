package com.shangkeschedule.service.notification.reminder

import kotlinx.coroutines.CancellationException

internal data class ReminderSchedulingResult(val scheduled: Int, val failed: Int)

/** 单条失败不阻断其他提醒，同时把失败总账交给重试入口。 */
internal fun <T> scheduleReminderEntries(
    entries: List<T>, schedule: (T) -> Unit, onFailure: (T, Exception) -> Unit
): ReminderSchedulingResult {
    var scheduled = 0
    var failed = 0
    for (entry in entries) {
        try {
            schedule(entry)
            scheduled++
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed++
            onFailure(entry, e)
        }
    }
    return ReminderSchedulingResult(scheduled, failed)
}
