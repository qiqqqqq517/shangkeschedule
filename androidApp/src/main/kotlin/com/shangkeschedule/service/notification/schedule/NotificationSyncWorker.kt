package com.shangkeschedule.service.notification.schedule

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.WidgetRepository
import org.koin.android.annotation.KoinWorker

/**
 * 通知排程薄壳 Worker。
 *
 * 只做一件事：构造 `NotificationScheduler` 并调用 `reschedule()`。
 * 之所以经过 WorkManager（而不是直接在 `SyncManager` 的协程里跑）：
 *  - 进程可能随时被杀，WorkManager 的持久化队列保证排程不丢；
 *  - 拿到系统级重试（`Result.retry()`）。
 *
 * 取代旧版的 `CourseNotificationWorker`（课程提醒）与 `DndSchedulerWorker`（自动勿扰）：
 * 两者各自读库、各自清理闹钟，且异常路径互不一致
 * （`DndSchedulerWorker` 抛异常即 failure，调度链断到下次触发源）。
 */
@KoinWorker
class NotificationSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val appSettingsRepository: AppSettingsRepository,
    private val widgetRepository: WidgetRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val scheduler = NotificationScheduler(
            context = applicationContext,
            appSettingsRepository = appSettingsRepository,
            widgetRepository = widgetRepository
        )
        return try {
            val summary = scheduler.reschedule()
            if (summary.readFailed) {
                // 读库失败：已保留旧排程，retry 让 WorkManager 按退避策略重试
                Log.w(TAG, "排程读库失败，稍后重试")
                return Result.retry()
            }
            Log.d(
                TAG,
                "排程完成：课程提醒 ${summary.reminderCount} 条、自动模式 ${summary.autoModeCount} 条、" +
                    "早八 ${summary.morningAlarmResult}"
            )
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Worker 被 REPLACE 打断：透传取消，不当作失败
            throw e
        } catch (e: Exception) {
            // 瞬态失败（数据源未就绪等）→ retry；已保留旧排程，不会造成提醒真空
            Log.e(TAG, "通知排程失败", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "NotificationSyncWorker"

        /** 唯一任务名：同一次设置/课表变更链上的重复触发会被 REPLACE 合并。 */
        const val UNIQUE_WORK_NAME = "NotificationSyncWorker_Sync_Update"
    }
}
