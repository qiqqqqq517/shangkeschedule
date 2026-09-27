package com.shangkeschedule.service.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.service.notification.control.AutoModeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 上课自动勿扰/静音闹钟接收器。
 *
 * 取代旧实现「借用课程提醒 action + `EXTRA_DND_ACTION` extras 挂在 `CourseAlarmReceiver`」的
 * 混淆做法（旧版槽位 50001/50002 与课程提醒 50010–50110 挤在同一区间，靠 extras 区分语义）。
 * 现在有**专属 action + 专属请求码命名空间**，两侧彻底解耦。
 *
 * 触发后重新排程以续链（承接旧版 `DND_ACTION_END -> DndSchedulerWorker.enqueueWork(ctx)` 行为）。
 */
class AutoModeAlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val appSettingsRepository: AppSettingsRepository by inject()

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val enable = when (intent?.action) {
            ACTION_AUTO_MODE_START -> true
            ACTION_AUTO_MODE_END -> false
            else -> {
                Log.d(TAG, "忽略未知 action: ${intent?.action}")
                return
            }
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = appSettingsRepository.getAppSettingsOnce()
                if (!settings.autoModeEnabled) {
                    Log.d(TAG, "自动模式已关闭，忽略本次切换")
                    return@launch
                }
                AutoModeController.toggle(ctx, enable, settings.autoControlMode)
            } catch (e: Exception) {
                Log.e(TAG, "自动模式切换失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AutoModeAlarmReceiver"

        /** 进入勿扰/静音。 */
        const val ACTION_AUTO_MODE_START = "com.shangkeschedule.action.AUTO_MODE_START"

        /** 退出勿扰/静音。 */
        const val ACTION_AUTO_MODE_END = "com.shangkeschedule.action.AUTO_MODE_END"
    }
}
