package com.shangkeschedule.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 状态栏「灵动岛」窗口闹钟接收器。
 *
 * 由 [DynamicIslandManager] 通过 AlarmManager 精确闹钟触发：
 *  - [DynamicIslandManager.ACTION_DYNAMIC_ISLAND_START]：窗口开始 → 重新校验并启动前台服务；
 *  - [DynamicIslandManager.ACTION_DYNAMIC_ISLAND_STOP]：窗口结束 → 停止服务、移除通知并排程下一窗口。
 */
class DynamicIslandAlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val dynamicIslandManager: DynamicIslandManager by inject()

    companion object {
        private const val TAG = "DynamicIslandAlarmReceiver"

        /**
         * 「窗口开始」重复投递兜底窗口（v3.57.1）。
         *
         * 正常情况下窗口开始只会投递一次 START 闹钟；但历史遗留调度、厂商 ROM 重复派发等
         * 都可能让同一个 START 在窗口内被连续投递。此时若服务已在运行，重复拉起只会让前台
         * 服务与灵动岛通知被反复重建（观感即「隔几秒又弹一次」）。故：
         * 仅当**服务已在运行**且距上次处理不足 [START_DEBOUNCE_MS] 时忽略本次——
         * 服务未运行时绝不丢弃，保证窗口开始那一次一定生效。
         */
        private const val START_DEBOUNCE_MS = 5_000L

        /** 上一次真正处理 START 的时刻（进程内状态，接收器实例按广播创建故放伴生对象）。 */
        private val lastStartHandledAt = java.util.concurrent.atomic.AtomicLong(0L)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        when (intent?.action) {
            DynamicIslandManager.ACTION_DYNAMIC_ISLAND_START -> {
                val now = System.currentTimeMillis()
                if (DynamicIslandService.isRunning &&
                    now - lastStartHandledAt.get() < START_DEBOUNCE_MS
                ) {
                    Log.d(TAG, "窗口开始闹钟重复投递（服务已在运行），忽略本次")
                    return
                }
                lastStartHandledAt.set(now)
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        // sync 内部会再次校验设置、权限与时间窗口，确实在窗口内才启动
                        dynamicIslandManager.sync()
                    } catch (e: Exception) {
                        Log.e(TAG, "窗口开始同步失败", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            DynamicIslandManager.ACTION_DYNAMIC_ISLAND_STOP -> {
                DynamicIslandService.stop(ctx)
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        // 跨天兜底：为下一个未来窗口排程闹钟
                        dynamicIslandManager.scheduleNextWindow()
                    } catch (e: Exception) {
                        Log.e(TAG, "窗口结束排程失败", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
