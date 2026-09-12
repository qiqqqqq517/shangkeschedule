// WorkManagerHelper.kt
package com.shangkeschedule.widget

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shangkeschedule.service.DailyRolloverWorker
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 负责调度和取消小组件相关定期任务的通用帮助类。
 */
object WorkManagerHelper {

    private const val UI_UPDATE_WORK_NAME = "WidgetUiUpdateWorker_Periodic"
    private const val FULL_DATA_SYNC_WORK_NAME = "FullDataSyncWorker_Periodic"
    private const val DAILY_ROLLOVER_WORK_NAME = "DailyRolloverWorker_Once"

    /** 每日零点自愈的触发时刻（00:05，避开部分 ROM 零点任务拥塞）。 */
    private val ROLLOVER_TIME: LocalTime = LocalTime.of(0, 5)

    fun schedulePeriodicWork(context: Context) {
        Log.d("WidgetWorkManager", "正在调度小组件定期任务...")

        // 调度小组件UI更新任务 (每15分钟)
        val uiUpdateWorkRequest = PeriodicWorkRequestBuilder<WidgetUiUpdateWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UI_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            uiUpdateWorkRequest
        )

        // 调度完整数据同步任务 (每天一次)
        val fullDataSyncWorkRequest = PeriodicWorkRequestBuilder<FullDataSyncWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            FULL_DATA_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            fullDataSyncWorkRequest
        )

        // 每日零点自愈：跨天自动重排课程提醒闹钟 / 勿扰 / 灵动岛 / 小组件（无需打开 App）
        scheduleDailyRollover(context)
    }

    /**
     * 排定下一次 [ROLLOVER_TIME] 触发 [DailyRolloverWorker]。
     *
     * 锚点语义：App 启动 / 开机 / 添加小组件 / Worker 自续链四个来源都会调用本方法，
     * REPLACE 策略把下一次触发统一推到「最近的下一个 00:05」——跨天时刻保证有一个
     * 确定性的刷新点（周期任务的全量同步仅在任意时刻兜底）。今天 00:05 已过则排明天。
     */
    fun scheduleDailyRollover(context: Context) {
        val now = LocalDateTime.now()
        val todayRun = now.toLocalDate().atTime(ROLLOVER_TIME)
        val next = if (now.isBefore(todayRun)) todayRun else todayRun.plusDays(1)
        val delayMillis = Duration.between(now, next).toMillis().coerceAtLeast(0)

        val request = OneTimeWorkRequestBuilder<DailyRolloverWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DAILY_ROLLOVER_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        Log.d("WidgetWorkManager", "每日零点自愈已锚定到 ${next}（延迟 ${delayMillis}ms）")
    }

    fun cancelAllWork(context: Context) {
        Log.d("WidgetWorkManager", "正在取消所有小组件定期任务...")
        WorkManager.getInstance(context).cancelUniqueWork(UI_UPDATE_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(FULL_DATA_SYNC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_ROLLOVER_WORK_NAME)
    }
}
