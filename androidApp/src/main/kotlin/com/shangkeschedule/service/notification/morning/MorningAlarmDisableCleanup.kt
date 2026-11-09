package com.shangkeschedule.service.notification.morning

import android.content.Context
import android.util.Log
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.notification.morning.MorningAlarmSyncGate
import com.shangkeschedule.service.notification.alarm.AlarmScheduler
import com.shangkeschedule.service.notification.notify.MorningAlarmNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

/** 调度锁内复读开关，避免延迟的关闭清理误伤重新开启后的通知。 */
internal object MorningAlarmDisableCleanup {
    fun clearIfDisabled(context: Context, settings: AppSettingsRepository) = MorningAlarmSyncGate.run {
        val enabled = try {
            runBlocking { settings.getAppSettingsOnce().morningAlarmEnabled }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Log.w("MorningAlarmCleanup", "无法复核开关，跳过清理", error)
            return@run
        }
        if (enabled) return@run
        // 不删除每周核对屏障或系统时钟登记簿：重新开启时仍须安全核对。
        runCatching { AlarmScheduler(context).cancelMorningFallback() }
        MorningAlarmReviewNotifier.cancel(context)
        MorningAlarmWriteStatusReporter.clear(context)
        MorningAlarmNotifier.cancel(context)
    }
}
