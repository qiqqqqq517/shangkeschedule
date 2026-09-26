package com.shangkeschedule.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shangkeschedule.data.sync.WidgetDataSynchronizer
import org.koin.android.annotation.KoinWorker

/**
 * 负责每15分钟更新一次小组件UI的Worker。
 * 它直接调用所有小组件的UI更新，确保UI及时刷新。
 */
@KoinWorker
class WidgetUiUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        Log.d("WidgetSync", "WidgetUiUpdateWorker 开始执行")
        updateAllWidgets(applicationContext)
        return Result.success()
    }
}

/**
 * 负责每天执行一次完整数据同步的Worker。
 * 它调用 WidgetDataSynchronizer 的 syncNow() 方法，同步主数据库数据。
 */
@KoinWorker
class FullDataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val widgetDataSynchronizer: WidgetDataSynchronizer
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        Log.d("WidgetSync", "FullDataSyncWorker 开始执行")
        try {
            widgetDataSynchronizer.syncNow()
            return Result.success()
        } catch (e: Exception) {
            // FIX: 原先返回 Result.failure()，一次瞬时 IO/数据库抖动就会让本次同步彻底放弃，
            // 而该 Worker 是周期任务（返回 retry 会按退避策略重试，不会失控重排）。
            Log.e("WidgetSync", "FullDataSyncWorker 全量同步失败，将按退避策略重试", e)
            return Result.retry()
        }
    }
}