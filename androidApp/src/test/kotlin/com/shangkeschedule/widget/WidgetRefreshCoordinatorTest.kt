package com.shangkeschedule.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetRefreshCoordinatorTest {
    @Test
    fun requiredRequestWaitsForItsOwnRender() = runBlocking {
        val coordinator = WidgetRefreshCoordinator()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val rendered = mutableListOf<String>()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.refresh(WidgetRefreshReason.REQUIRED) {
                firstStarted.complete(Unit)
                releaseFirst.await()
                rendered += "first"
            }
        }
        firstStarted.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.refresh(WidgetRefreshReason.REQUIRED) { rendered += "second" }
        }
        val completedTooEarly = second.isCompleted
        releaseFirst.complete(Unit)
        first.await()
        second.await()
        assertFalse(completedTooEarly)
        assertEquals(listOf("first", "second"), rendered)
    }

    @Test
    fun systemNudgeDuringRenderIsCoalesced() = runBlocking {
        val coordinator = WidgetRefreshCoordinator()
        val release = CompletableDeferred<Unit>()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.refresh(WidgetRefreshReason.REQUIRED) { release.await() }
        }
        var rendered = false
        coordinator.refresh(WidgetRefreshReason.SYSTEM_NUDGE) { rendered = true }
        release.complete(Unit)
        first.await()
        assertFalse(rendered)
    }
}
