package com.shangkeschedule.data.sync

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSyncNotificationTest {
    @Test
    fun changedSnapshotFollowedByUnchangedSnapshotStillNotifies() = runBlocking {
        val notifications = flow {
            emit(true)
            delay(20)
            emit(false)
            delay(600)
        }.changedSnapshotNotifications().toList()
        assertEquals(listOf(true), notifications)
    }

    @Test
    fun unchangedSnapshotsDoNotNotify() = runBlocking {
        val notifications = flow { emit(false); emit(false) }.changedSnapshotNotifications().toList()
        assertEquals(emptyList<Boolean>(), notifications)
    }
}
