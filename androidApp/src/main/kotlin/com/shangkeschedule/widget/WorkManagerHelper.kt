// WorkManagerHelper.kt
package com.shangkeschedule.widget

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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
    private const val WEBDAV_AUTO_SYNC_WORK_NAME = "WebDavAutoSyncWorker_Periodic"
    private const val WEBDAV_AUTO_SYNC_NOW_WORK_NAME = "WebDavAutoSyncWorker_Once"

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

        // 调度完整数据同步任务 (每天一次)。KEEP：启动时不重写既有调度，
        // 与实时同步流 / 每日零点自愈互为兜底即可，避免每次冷启动改写 WorkManager spec（v3.54.0）
        val fullDataSyncWorkRequest = PeriodicWorkRequestBuilder<FullDataSyncWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            FULL_DATA_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
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

    /**
     * 调度 WebDAV 自动同步的每日兜底任务。
     *
     * 实时数据变更由 [enqueueWebDavAutoSyncNow] 触发；周期任务只负责进程被系统回收、
     * 长时间未打开 App 等场景，确保云端备份最终会追上本地数据。
     */
    fun scheduleWebDavAutoSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<WebDavAutoSyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WEBDAV_AUTO_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.d("WidgetWorkManager", "WebDAV 自动同步每日任务已调度")
    }

    /**
     * 安排一次 WebDAV 自动同步。
     *
     * 延迟用于合并短时间内的连续数据写入（如拖拽课程、批量编辑设置），
     * REPLACE 会把触发时间顺延到最近一次变更之后。
     */
    fun enqueueWebDavAutoSyncNow(context: Context, delaySeconds: Long = 30L) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<WebDavAutoSyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delaySeconds.coerceAtLeast(0L), TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WEBDAV_AUTO_SYNC_NOW_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** 关闭自动同步或断开 WebDAV 时，取消周期任务与待执行的一次性任务。 */
    fun cancelWebDavAutoSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WEBDAV_AUTO_SYNC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(WEBDAV_AUTO_SYNC_NOW_WORK_NAME)
        Log.d("WidgetWorkManager", "WebDAV 自动同步任务已取消")
    }

    /**
     * 某类小组件的最后实例被移除（onDisabled）时调用：仅当**全部**小组件类型都归零
     * 才取消组件专属任务（UI 更新 + 全量同步），避免删一种组件连累其它组件停更。
     * [DAILY_ROLLOVER_WORK_NAME] 始终保留——它承担提醒/勿扰/灵动岛的跨天自愈，
     * 与小组件是否还在无关（代价仅每天一次 00:05 本地同步）。
     */
    fun onWidgetDisabled(context: Context) {
        runCatching {
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            val providers = listOf(
                com.shangkeschedule.widget.tiny.TinyNativeProvider::class.java,
                com.shangkeschedule.widget.compact.CompactNativeProvider::class.java,
                com.shangkeschedule.widget.double_days.DoubleDaysNativeProvider::class.java,
                com.shangkeschedule.widget.list_vertical.ListVerticalNativeProvider::class.java
            )
            val hasAnyWidget = providers.any { cls ->
                manager.getAppWidgetIds(android.content.ComponentName(context, cls)).isNotEmpty()
            }
            if (!hasAnyWidget) {
                Log.d("WidgetWorkManager", "全部小组件已移除，仅取消组件专属任务（保留每日零点自愈）")
                WorkManager.getInstance(context).cancelUniqueWork(UI_UPDATE_WORK_NAME)
                WorkManager.getInstance(context).cancelUniqueWork(FULL_DATA_SYNC_WORK_NAME)
            }
        }.onFailure { Log.e("WidgetWorkManager", "onWidgetDisabled 检查失败", it) }
    }
}
