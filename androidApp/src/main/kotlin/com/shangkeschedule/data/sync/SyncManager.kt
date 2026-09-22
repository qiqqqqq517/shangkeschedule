package com.shangkeschedule.data.sync

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.shangkeschedule.data.repository.ApiConfigRepository
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.ScheduleEventRepository
import com.shangkeschedule.data.repository.StyleSettingsRepository
import com.shangkeschedule.data.repository.TodoRepository
import com.shangkeschedule.service.CourseNotificationWorker
import com.shangkeschedule.service.DndSchedulerWorker
import com.shangkeschedule.service.DynamicIslandManager
import com.shangkeschedule.widget.WorkManagerHelper
import com.shangkeschedule.widget.updateAllWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.milliseconds

/**
 * 平台层同步管理器（Android 端）。
 * 仅负责响应共享层 (KMP) 的同步完成信号与 Android 本地样式更新，执行系统 Widget 刷新及 WorkManager 调度。
 */
@OptIn(FlowPreview::class)
@Single(createdAtStart = true)
class SyncManager(
    private val appContext: Context,
    private val widgetDataSynchronizer: WidgetDataSynchronizer,
    private val styleSettingsRepository: StyleSettingsRepository,
    private val dynamicIslandManager: DynamicIslandManager,
    private val apiConfigRepository: ApiConfigRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val todoRepository: TodoRepository,
    private val scheduleEventRepository: ScheduleEventRepository
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var webDavAutoSyncEnabled = false

    init {
        // 0. App 启动即锚定小组件周期任务与每日零点自愈（此前只挂在「添加小组件」与
        //    「开机广播」上，从未添加过小组件或 ROM 清掉调度时，组件/提醒只能靠打开 App 续命）
        runCatching { com.shangkeschedule.widget.WorkManagerHelper.schedulePeriodicWork(appContext) }
            .onFailure { Log.e("SyncManager", "锚定小组件周期任务失败", it) }

        // 0.1 WebDAV 自动同步开关：开启时调度每日兜底并立即安排一次上传；
        //     关闭或断开配置时取消全部自动同步任务。
        apiConfigRepository.webDavAutoSyncEnabledFlow
            .distinctUntilChanged()
            .onEach { enabled ->
                webDavAutoSyncEnabled = enabled
                if (enabled) {
                    WorkManagerHelper.scheduleWebDavAutoSync(appContext)
                    WorkManagerHelper.enqueueWebDavAutoSyncNow(appContext)
                } else {
                    WorkManagerHelper.cancelWebDavAutoSync(appContext)
                }
            }
            .launchIn(scope)

        // 1. 监听 KMP 共享层的同步完成信号（包含了课表变更与通知/自动化设置变更）
        widgetDataSynchronizer.syncCompletedFlow
            .onEach {
                Log.d("SyncManager", "收到共享层同步完成通知，正在调度 Worker 任务及刷新小组件...")
                triggerNotificationWorker()
                DndSchedulerWorker.enqueueWork(appContext)
                updateAllWidgets(appContext)
                runCatching { dynamicIslandManager.sync() }
                    .onFailure { Log.e("SyncManager", "同步状态栏灵动岛服务状态失败", it) }
                scheduleWebDavAutoSyncIfEnabled()
            }
            .launchIn(scope)

        // 2. 监听 Android 端专属的样式更新事件（styleFlow 本身即响应式数据流）。
        //    样式滑杆拖动期 styleFlow 逐帧发射：distinctUntilChanged 去重 + debounce(800ms)
        //    合并为一次组件重绘，避免拖动过程每帧渲染 4 个小组件（v3.54.0）。
        styleSettingsRepository.styleFlow
            .distinctUntilChanged()
            .debounce(800.milliseconds)
            .onEach {
                Log.d("SyncManager", "收到样式更改通知，正在刷新小组件...")
                updateAllWidgets(appContext)
                scheduleWebDavAutoSyncIfEnabled()
            }
            .launchIn(scope)

        // 0.2 自动同步也监听全量备份中不经过小组件链路的全局数据：
        //     应用设置、待办与日程。初始发射跳过，避免冷启动时与开关本身的立即同步重复。
        appSettingsRepository.getAppSettings()
            .drop(1)
            .onEach { scheduleWebDavAutoSyncIfEnabled() }
            .launchIn(scope)
        todoRepository.getAllTodos()
            .drop(1)
            .onEach { scheduleWebDavAutoSyncIfEnabled() }
            .launchIn(scope)
        scheduleEventRepository.getAllEvents()
            .drop(1)
            .onEach { scheduleWebDavAutoSyncIfEnabled() }
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

    private fun scheduleWebDavAutoSyncIfEnabled() {
        if (webDavAutoSyncEnabled) {
            WorkManagerHelper.enqueueWebDavAutoSyncNow(appContext)
        }
    }
}
