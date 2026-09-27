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
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.service.notification.alarm.ForegroundGate
import com.shangkeschedule.service.notification.schedule.NotificationScheduler
import com.shangkeschedule.service.notification.schedule.NotificationSyncWorker
import com.shangkeschedule.widget.WorkManagerHelper
import com.shangkeschedule.widget.updateAllWidgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.time.Duration.Companion.milliseconds

/**
 * 平台层同步管理器（Android 端）。
 * 负责响应共享层 (KMP) 的同步完成信号与 Android 本地样式更新，执行系统 Widget 刷新及通知排程。
 *
 * 重写要点：通知相关的调度入口从「三处各自排 Worker」收敛为
 * `NotificationScheduler`（前台内联执行，后台经 `NotificationSyncWorker` 调度，
 * 拿到 WorkManager 的重试与进程保活背书）。
 *
 * 「前台内联」这条分支是 2026-09-27 真机修复引入的：早八闹钟写入系统时钟依赖
 * `startActivity(ACTION_SET_ALARM)`，受 Android 10+ 后台启动限制（BAL）约束，
 * 只有应用在前台的时刻才写得进去（详见 `ForegroundGate` 与 `MorningAlarmWriter`）。
 */
@OptIn(FlowPreview::class)
@Single(createdAtStart = true)
class SyncManager(
    private val appContext: Context,
    private val widgetDataSynchronizer: com.shangkeschedule.data.sync.WidgetDataSynchronizer,
    private val styleSettingsRepository: StyleSettingsRepository,
    private val apiConfigRepository: ApiConfigRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val widgetRepository: WidgetRepository,
    private val todoRepository: TodoRepository,
    private val scheduleEventRepository: ScheduleEventRepository
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var webDavAutoSyncEnabled = false

    init {
        // 0. App 启动即锚定小组件周期任务与每日零点自愈
        runCatching { WorkManagerHelper.schedulePeriodicWork(appContext) }
            .onFailure { Log.e(TAG, "锚定小组件周期任务失败", it) }

        // 0.1 WebDAV 自动同步开关
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

        // 1. 监听 KMP 共享层的同步完成信号（含课表变更与通知/自动化设置变更）
        widgetDataSynchronizer.syncCompletedFlow
            .onEach {
                Log.d(TAG, "收到共享层同步完成通知，正在调度通知排程与刷新小组件...")
                triggerNotificationSync()
                updateAllWidgets(appContext)
                scheduleWebDavAutoSyncIfEnabled()
            }
            .launchIn(scope)

        // 2. 监听 Android 端专属的样式更新事件
        styleSettingsRepository.styleFlow
            .distinctUntilChanged()
            .debounce(800.milliseconds)
            .onEach {
                Log.d(TAG, "收到样式更改通知，正在刷新小组件...")
                updateAllWidgets(appContext)
                scheduleWebDavAutoSyncIfEnabled()
            }
            .launchIn(scope)

        // 0.2 全量备份覆盖的全局数据（应用设置/待办/日程）变更也触发自动同步
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

        Log.d(TAG, "Android 平台同步调度器初始化完毕。")
    }

    /**
     * 统一的通知排程入口：把「课程提醒 + 自动勿扰 + 早八闹钟」全量重排委托给
     * `NotificationScheduler.reschedule()`。
     *
     * ## 为什么前台要**内联**跑，而不是交给 Worker
     *
     * 早八闹钟写入系统时钟靠 `startActivity(ACTION_SET_ALARM)`，受 Android 10+
     * 后台启动限制（BAL）约束。用户拨动开关的那一刻应用正在前台，这是**最可靠**的
     * 写入时机；若照旧丢给 WorkManager，等它真正跑起来时用户往往已经退出应用，
     * 写入会被系统**静默拦下**（真机症状：开关开着、登记簿为空、系统时钟里一条闹钟都没有）。
     *
     * 因此：**前台直接内联执行**（当下就能写进去）；**后台才走 `NotificationSyncWorker`**
     * （拿 WorkManager 的持久化队列与重试背书）。两条路径都只调 `reschedule()`，
     * 语义与结果完全一致，不存在双重排程（前台分支不会同时入队）。
     *
     * 内联失败（读库未就绪等）会转交 Worker 兜底，避免这一次信号被丢掉。
     */
    private fun triggerNotificationSync() {
        if (ForegroundGate.isAppVisible(appContext)) {
            scope.launch {
                try {
                    val scheduler = NotificationScheduler(
                        context = appContext,
                        appSettingsRepository = appSettingsRepository,
                        widgetRepository = widgetRepository
                    )
                    val summary = scheduler.reschedule()
                    Log.d(
                        TAG,
                        "前台内联排程完成：课程提醒 ${summary.reminderCount} 条、" +
                            "自动模式 ${summary.autoModeCount} 条、早八 ${summary.morningAlarmResult}"
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "前台内联排程失败，转交 Worker 兜底", e)
                    enqueueNotificationSyncWork()
                }
            }
            return
        }
        enqueueNotificationSyncWork()
    }

    /** 后台路径：入持久化队列，拿 WorkManager 的重试与进程保活背书。 */
    private fun enqueueNotificationSyncWork() {
        val workRequest = OneTimeWorkRequestBuilder<NotificationSyncWorker>().build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            NotificationSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun scheduleWebDavAutoSyncIfEnabled() {
        if (webDavAutoSyncEnabled) {
            WorkManagerHelper.enqueueWebDavAutoSyncNow(appContext)
        }
    }

    private companion object {
        const val TAG = "SyncManager"
    }
}
