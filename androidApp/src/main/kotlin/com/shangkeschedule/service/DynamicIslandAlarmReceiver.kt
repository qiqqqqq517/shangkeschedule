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
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        when (intent?.action) {
            DynamicIslandManager.ACTION_DYNAMIC_ISLAND_START -> {
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
