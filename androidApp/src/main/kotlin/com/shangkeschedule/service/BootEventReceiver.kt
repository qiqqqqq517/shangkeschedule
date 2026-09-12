package com.shangkeschedule.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shangkeschedule.widget.WorkManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开机自启广播接收器。
 *
 * 背景：课程提醒（CourseNotificationWorker 挂载的精确闹钟）与勿扰排程（DndSchedulerWorker）
 * 在设备重启后会被系统清除，而此前 Manifest 声明了 RECEIVE_BOOT_COMPLETED 权限却没有对应的
 * BOOT_COMPLETED receiver，导致重启后提醒与自动勿扰全部失联，只能等用户手动打开 App 才恢复。
 *
 * 工作原理：收到开机广播 → 唤醒应用进程（Application.onCreate 中 startKoin 已完成）→
 * 通过 Koin 取共享层的 WidgetDataSynchronizer 触发一次 syncNow()。
 * 同步完成后会发出 syncCompletedFlow，平台层 SyncManager（createdAtStart 单例）订阅该流，
 * 自动重排 CourseNotificationWorker / DndSchedulerWorker 并刷新全部小组件；
 * 同时补排小组件的周期任务（KEEP 策略，已有调度不受影响）。
 */
class BootEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Log.d("BootEventReceiver", "收到开机广播，重新排程课程提醒与小组件任务...")

                // 1. 补排小组件周期任务（KEEP：若 WorkManager 已持久化的调度仍在，不会重复）
                WorkManagerHelper.schedulePeriodicWork(context.applicationContext)

                // 2. 全量同步改由 WorkManager 执行：goAsync 前台广播窗口约 10s，
                //    冷启动 syncNow（DataStore/Room 初始化 + 多表查询 + 全部小组件渲染）
                //    在慢机上极易超时被系统回收，导致当日提醒失联最长可达 ~34 小时；
                //    挂入 WorkManager 后由系统保证执行，完成后照常级联重排提醒/勿扰/灵动岛
                androidx.work.WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    "BootSync_Once",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    androidx.work.OneTimeWorkRequestBuilder<com.shangkeschedule.widget.FullDataSyncWorker>().build()
                )
            } catch (e: Exception) {
                Log.e("BootEventReceiver", "开机重排程失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
