// DailyRolloverWorker.kt
package com.shangkeschedule.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shangkeschedule.data.sync.WidgetDataSynchronizer
import com.shangkeschedule.widget.WorkManagerHelper
import org.koin.android.annotation.KoinWorker

/**
 * 每日零点自愈 Worker（任务：桌面组件与上课提醒不打开 App 也按课表自动更新）。
 *
 * 背景：此前课程提醒闹钟重排与小组件数据同步只挂在「打开 App」与「开机广播」两个锚点上——
 * 跨天之后（尤其周一换课表日），若用户不打开 App，小组件停留在昨天、提醒与灵动岛排程也依赖
 * 预挂的 7 天闹钟存量，遇到课程变动即失效。
 *
 * 本 Worker 与「打开 App」完全等效：调用 [WidgetDataSynchronizer.syncNow]，同步完成后
 * syncCompletedFlow 会驱动 SyncManager 重排 CourseNotificationWorker（未来 7 天提醒闹钟）、
 * DndSchedulerWorker、灵动岛并刷新全部小组件。执行完自续链接到次日零点（见
 * [WorkManagerHelper.scheduleDailyRollover]）；开机广播与 App 启动也会重新锚定，三重兜底。
 */
@KoinWorker
class DailyRolloverWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val widgetDataSynchronizer: WidgetDataSynchronizer
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("DailyRollover", "每日零点自愈开始：同步课表数据并重排提醒与小组件")
        val result = try {
            widgetDataSynchronizer.syncNow()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Worker 被 REPLACE / 约束变更打断：透传取消，不打断续链外的生命周期语义
            throw e
        } catch (e: Exception) {
            // 失败不 retry（避免长期后台空转）；链仍在下方续到次日零点，次日自动补跑
            Log.e("DailyRollover", "每日零点自愈同步失败", e)
            Result.failure()
        }
        // 无论成败都续链到下一次零点（WorkManager 对 OneTime 任务不自动重排）
        WorkManagerHelper.scheduleDailyRollover(applicationContext)
        return result
    }
}
