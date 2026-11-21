package com.shangkeschedule.service.notification.schedule

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 前台、Worker 和广播共用整轮锁；设置与课程必须在取得锁后重读。 */
internal object NotificationRescheduleGate {
    private val mutex = Mutex()
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}
