package com.shangkeschedule.widget

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDayBoundaryActionsTest {
    @Test
    fun `有组件时先续链并派发完整同步再刷新快照`() = runBlocking {
        val calls = mutableListOf<String>()
        runDayBoundaryActions(
            hasWidget = true,
            scheduleNext = { calls += "next" },
            scheduleRollover = { calls += "rollover" },
            enqueueSync = { calls += "sync" },
            refresh = { calls += "refresh" },
            onFailure = { _, _ -> },
            refreshTimeoutMillis = 1000
        )
        assertEquals(listOf("next", "rollover", "sync", "refresh"), calls)
    }

    @Test
    fun `重绘超时不阻止续链与同步`() = runBlocking {
        val calls = mutableListOf<String>()
        runDayBoundaryActions(true, { calls += "next" }, { calls += "rollover" },
            { calls += "sync" }, { delay(500); calls += "late" }, { _, _ -> }, 30)
        assertEquals(listOf("next", "rollover", "sync"), calls)
    }

    @Test
    fun `单个续链失败不阻止同步和重绘`() = runBlocking {
        val calls = mutableListOf<String>()
        runDayBoundaryActions(true, { throw IllegalStateException() }, { calls += "rollover" },
            { calls += "sync" }, { calls += "refresh" }, { _, _ -> }, 1000)
        assertEquals(listOf("rollover", "sync", "refresh"), calls)
    }

    @Test
    fun `没有组件时不安排和刷新`() = runBlocking {
        val calls = mutableListOf<String>()
        runDayBoundaryActions(false, { calls += "next" }, { calls += "rollover" },
            { calls += "sync" }, { calls += "refresh" }, { _, _ -> }, 1000)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `外部取消必须传播`() = runBlocking {
        var cancelled = false
        try {
            withTimeout(30) {
                runDayBoundaryActions(true, {}, {}, {}, { delay(500) }, { _, _ -> }, 1000)
            }
        } catch (_: TimeoutCancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }
}
