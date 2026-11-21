package com.shangkeschedule.service.notification.schedule

import com.shangkeschedule.service.notification.reminder.scheduleReminderEntries
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NotificationRecoveryTest {
    @Test fun readFailureAndPartialFailureBothRequireRetry() {
        assertTrue(NotificationScheduler.Summary(readFailed = true).needsRetry)
        assertTrue(NotificationScheduler.Summary(failedStrategies = 1).needsRetry)
        assertFalse(NotificationScheduler.Summary().needsRetry)
    }

    @Test fun failedReminderDoesNotHideFailureOrStopLaterReminders() {
        val visited = mutableListOf<Int>()
        val result = scheduleReminderEntries(listOf(1, 2, 3), schedule = {
            visited += it
            if (it == 2) error("合成系统挂载失败")
        }, onFailure = { _, _ -> })
        assertEquals(listOf(1, 2, 3), visited)
        assertEquals(2, result.scheduled)
        assertEquals(1, result.failed)
    }

    @Test fun cancellationIsNotReportedAsSuccessfulPartialScheduling() {
        assertThrows(CancellationException::class.java) {
            scheduleReminderEntries(listOf(1), schedule = { throw CancellationException() }, onFailure = { _, _ -> })
        }
    }

    @Test fun queuedRescheduleReadsSettingsAfterThePreviousRoundFinishes() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val secondRead = CompletableDeferred<Int>()
        var settingsVersion = 1
        val first = launch {
            NotificationRescheduleGate.run {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val second = launch(start = CoroutineStart.UNDISPATCHED) {
            NotificationRescheduleGate.run { secondRead.complete(settingsVersion) }
        }
        assertFalse("后轮不能在旧轮次持锁期间读取快照", secondRead.isCompleted)
        settingsVersion = 2
        release.complete(Unit)
        withTimeout(3000) { joinAll(first, second) }
        assertEquals(2, secondRead.await())
    }
}
