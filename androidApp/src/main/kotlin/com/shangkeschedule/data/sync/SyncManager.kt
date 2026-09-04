package com.shangkeschedule.data.sync

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.shangkeschedule.data.repository.StyleSettingsRepository
import com.shangkeschedule.service.CourseNotificationWorker
import com.shangkeschedule.service.DndSchedulerWorker
import com.shangkeschedule.service.DynamicIslandManager
import com.shangkeschedule.widget.updateAllWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single

/**
 * 平台层同步管理器（Android 端）。
 * 仅负责响应共享层 (KMP) 的同步完成信号与 Android 本地样式更新，执行系统 Widget 刷新及 WorkManager 调度。
 */
@Single(createdAtStart = true)
class SyncManager(
    private val appContext: Context,
    private val widgetDataSynchronizer: WidgetDataSynchronizer,
    private val styleSettingsRepository: StyleSettingsRepository,
    private val dynamicIslandManager: DynamicIslandManager
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // 1. 监听 KMP 共享层的同步完成信号（包含了课表变更与通知/自动化设置变更）
        widgetDataSynchronizer.syncCompletedFlow
            .onEach {
                Log.d("SyncManager", "收到共享层同步完成通知，正在调度 Worker 任务及刷新小组件...")
                triggerNotificationWorker()
                DndSchedulerWorker.enqueueWork(appContext)
                updateAllWidgets(appContext)
                runCatching { dynamicIslandManager.sync() }
                    .onFailure { Log.e("SyncManager", "同步状态栏灵动岛服务状态失败", it) }
            }
            .launchIn(scope)

        // 2. 监听 Android 端专属的样式更新事件（styleFlow 本身即响应式数据流）
        styleSettingsRepository.styleFlow
            .onEach {
                Log.d("SyncManager", "收到样式更改通知，正在刷新小组件...")
                updateAllWidgets(appContext)
            }
            .launchIn(scope)

        Log.d("SyncManager", "Android 平台同步调度器初始化完毕。")
    }

    private fun triggerNotificationWorker() {
        val workRequest = OneTimeWorkRequestBuilder<CourseNotificationWorker>().build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "CourseNotificationWorker_Sync_Update",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}