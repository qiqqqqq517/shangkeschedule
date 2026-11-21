package com.shangkeschedule.widget

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDayBoundaryActionsTest {

    /**
     * 顺序契约（v4.83.3）：续链必须在最前；快照同步必须**早于**重绘，
     * 否则零点第一帧渲染的是昨天那份窗口。
     */
    @Test
    fun `有组件时先续链再同步快照最后重绘`() = runBlocking {
        val calls = mutableListOf<String>()
        runDayBoundaryActions(
            hasWidget = true,
            scheduleNext = { calls += "next" },
            scheduleRollover = { calls += "rollover" },
            syncData = { calls += "sync" },
            enqueueSync = { calls += "enqueue" },
            refresh = { calls += "refresh" },
            onFailure = { _, _ -> }
        )
        assertEquals(listOf("next", "rollover", "sync", "refresh"), calls)
    }

    /** 就地同步成功时不再派发 Worker（避免同一时刻两份全量同步）。 */
    @Test
    fun `同步成功不派发兜底任务`() = runBlocking {
        var enqueued = false
        var synced = false
        runDayBoundaryActions(
            hasWidget = true,
            scheduleNext = {},
            scheduleRollover = {},
            syncData = { },
            enqueueSync = { enqueued = true },
            refresh = {},
            onFailure = { _, _ -> },
            onSynced = { synced = true }
        )
        assertTrue(synced)
        assertEquals(false, enqueued)
    }

    /** 同步抛异常：不得中断流程，退化为派发 Worker + 用旧快照重绘。 */
    @Test
    fun `同步失败改为派发兜底任务并继续重绘`() = runBlocking {
        val calls = mutableListOf<String>()
        val failures = mutableListOf<String>()
        runDayBoundaryActions(
            hasWidget = true,
            scheduleNext = { calls += "next" },
            scheduleRollover = { calls += "rollover" },
            syncData = { throw IllegalStateException("db locked") },
            enqueueSync = { calls += "enqueue" },
            refresh = { calls += "refresh" },
            onFailure = { action, _ -> failures += action }
        )
        assertEquals(listOf("next", "rollover", "enqueue", "refresh"), calls)
        assertTrue(failures.contains("快照同步"))
    }

    /** 同步超时同样退化为派发 Worker：广播预算有限，不能无限等。 */
    @Test
    fun `同步超时改为派发兜底任务并继续重绘`() = runBlocking {
        val calls = mutableListOf<String>()
        runDayBoundaryActions(
            hasWidget = true,
            scheduleNext = { calls += "next" },
            scheduleRollover = { calls += "rollover" },
            syncData = { delay(500) },
            enqueueSync = { calls += "enqueue" },
            refresh = { calls += "refresh" },
            onFailure = { _, _ -> },
            syncTimeoutMillis = 30
        )
        assertEquals(listOf("next", "rollover", "enqueue", "refresh"), calls)
    }

    @Test
    fun `重绘超时不阻止续链与同步`() = runBlocking {
        val calls = mutableListOf<String>()
        runDayBoundaryActions(
            hasWidget = true,
            scheduleNext = { calls += "next" },
            scheduleRollover = { calls += "rollover" },
            syncData = { calls += "sync" },
            enqueueSync = { calls += "enqueue" },
            refresh = { delay(500); calls += "late" },
            onFailure = { _, _ -> },
            refreshTimeoutMillis = 30
        )
        assertEquals(listOf("next", "rollover", "sync"), calls)
    }

    @Test
    fun `单个续链失败不阻止同步和重绘`() = runBlocking {
        val calls = mutableListOf<String>()
        runDayBoundaryActions(
            hasWidget = true,
            scheduleNext = { throw IllegalStateException() },
            scheduleRollover = { calls += "rollover" },
            syncData = { calls += "sync" },
            enqueueSync = { calls += "enqueue" },
            refresh = { calls += "refresh" },
            onFailure = { _, _ -> }
        )
        assertEquals(listOf("rollover", "sync", "refresh"), calls)
    }

    @Test
    fun `没有组件时不安排和刷新`() = runBlocking {
        val calls = mutableListOf<String>()
        runDayBoundaryActions(
            hasWidget = false,
            scheduleNext = { calls += "next" },
            scheduleRollover = { calls += "rollover" },
            syncData = { calls += "sync" },
            enqueueSync = { calls += "enqueue" },
            refresh = { calls += "refresh" },
            onFailure = { _, _ -> }
        )
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `外部取消必须传播`() = runBlocking {
        var cancelled = false
        try {
            withTimeout(30) {
                runDayBoundaryActions(
                    hasWidget = true,
                    scheduleNext = {},
                    scheduleRollover = {},
                    syncData = {},
                    enqueueSync = {},
                    refresh = { delay(500) },
                    onFailure = { _, _ -> }
                )
            }
        } catch (_: TimeoutCancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }
}
