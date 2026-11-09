package com.shangkeschedule.widget

import kotlinx.coroutines.sync.Mutex

/** 必要刷新等待执行；系统重复提醒可在渲染期间合并。 */
internal class WidgetRefreshCoordinator {
    private val mutex = Mutex()

    suspend fun refresh(reason: WidgetRefreshReason, render: suspend () -> Unit) {
        if (reason == WidgetRefreshReason.REQUIRED) {
            mutex.lock()
        } else if (!mutex.tryLock()) {
            return
        }
        try {
            render()
        } finally {
            mutex.unlock()
        }
    }
}
