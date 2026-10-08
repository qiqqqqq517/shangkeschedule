package com.shangkeschedule.service.notification.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.service.PermissionNoticeNotifier
import com.shangkeschedule.service.notification.schedule.NotificationSyncWorker
import com.shangkeschedule.service.notification.schedule.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 「闹钟和提醒」权限状态变更监听（XL-003）。
 *
 * ## 之前缺了什么
 *
 * [AlarmScheduler] 在**排期时**会查 `canScheduleExactAlarms()`，缺权限就降级为
 * `setAndAllowWhileIdle` 并提示用户 —— 这一半是对的。缺的是另一半：
 * **权限被撤销时没有任何人通知应用**。
 *
 * 用户在系统设置里随时可以关掉「闹钟和提醒」。此时系统会**静默取消**本应用已注册的
 * 全部精确闹钟，App 收不到任何回调，于是：
 *
 * - 课程提醒、自动勿扰/静音、灵动岛、早八闹钟**同时全部失灵**；
 * - 用户侧表现是「提醒莫名其妙不响了」，且**无从排查**。
 *
 * 复盘时已确认：本仓库的 manifest 里没有任何 Receiver 监听
 * `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`。
 *
 * ## 本类做什么
 *
 * 1. **授权被撤销** → 提示用户，并触发一次全量重排。重排走 [AlarmScheduler] 自己的
 *    降级分支，于是闹钟以不精确形式重新挂上 —— **可能延迟，但不再彻底静默**。
 *    「延迟几分钟」远好于「一整天不响」。
 * 2. **授权被恢复** → 撤掉提示，并重排一次把闹钟升回精确形式。
 *
 * 必须**静态注册到 manifest**：权限变更发生在用户离开本 App 之后，
 * 运行时注册的 Receiver 与进程同寿，收不到（[com.shangkeschedule.service.notification.TimeChangeReceiver]
 * 曾长期踩这个坑，已一并改正）。
 */
class AlarmPermissionReceiver : BroadcastReceiver(), KoinComponent {

    private val appSettingsRepository: AppSettingsRepository by inject()
    private val widgetRepository: WidgetRepository by inject()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACTION_EXACT_ALARM_PERMISSION_CHANGED) {
            Log.d(TAG, "忽略未知 action: ${intent?.action}")
            return
        }
        val ctx = context ?: return

        // Android 11 以下没有精确闹钟权限这回事，收到也是异常路径，直接收工。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val granted = ctx.getSystemService<AlarmManager>()?.canScheduleExactAlarms() ?: false
        Log.i(
            TAG,
            if (granted) "精确闹钟权限已恢复，重排升级为精确闹钟"
            else "精确闹钟权限被撤销：提示用户并降级重排，保证提醒仍会触发"
        )

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (granted) {
                    PermissionNoticeNotifier.clear(ctx, PermissionNoticeNotifier.NOTICE_ID_EXACT_ALARM)
                } else {
                    // 只提示一次由 AlarmScheduler 的「每轮一次」节流负责；这里只在权限
                    // 状态**发生变化**时提示，不在一轮重排里反复刷屏。
                    PermissionNoticeNotifier.notifyExactAlarmMissing(ctx)
                }
                val summary = NotificationScheduler(
                    context = ctx.applicationContext,
                    appSettingsRepository = appSettingsRepository,
                    widgetRepository = widgetRepository
                ).reschedule()
                Log.i(
                    TAG,
                    "按新权限重排完成：课程 ${summary.reminderCount} 条，自动模式 ${summary.autoModeCount} 条"
                )
                // R41-09：与 TimeChangeReceiver 同源修法 —— 策略级失败必须可自愈，
                // 不能只留一行日志（此处尤其关键：权限刚变更，正是重排最容易失败的时刻）。
                if (summary.failedStrategies > 0) {
                    Log.w(TAG, "有 ${summary.failedStrategies} 个策略排程失败，请求重排补齐")
                    NotificationSyncWorker.enqueue(context = ctx.applicationContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "按新权限重排失败（保持既有排程）", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmPermissionReceiver"

        /**
         * 精确闹钟权限状态变更广播。
         *
         * 直接用字面量而非 `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`
         * 的常量引用（该常量在部分 compileSdk 上以 `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`
         * 暴露），字面量在所有 API 级别上语义稳定，且与 manifest 声明一一对应。
         */
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
