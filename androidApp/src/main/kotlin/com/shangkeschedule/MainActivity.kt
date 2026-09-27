package com.shangkeschedule

import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Window
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.util.Log
import com.shangkeschedule.data.model.RefreshRateMode
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.service.notification.LegacyAlarmMigrator
import com.shangkeschedule.service.notification.MorningAlarmWriter
import com.shangkeschedule.notification.MorningAlarmPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

class MainActivity : AppCompatActivity(), KoinComponent {

    private val appSettingsRepository: AppSettingsRepository by inject()
    private val widgetRepository: WidgetRepository by inject()

    private companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 升级迁移（幂等）：尽早清掉旧版遗留闹钟/通知，避免新旧两套提醒并存
        runCatching { LegacyAlarmMigrator.migrateIfNeeded(applicationContext) }
            .onFailure { Log.w(TAG, "通知迁移失败", it) }

        // 屏幕刷新率（v3.56.0）：订阅设置流，档位变化时把窗口钉到对应的显示模式
        // （preferredDisplayModeId 比 preferredRefreshRate 提示更可靠——部分 ROM 会忽略纯 rate 提示）。
        // AUTO 档 = 解除钉定（回 0），交还系统自适应，静止画面可自行降频省电。
        // 收集限定在 STARTED 以上：后台不再消费设置流。
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appSettingsRepository.getAppSettings().collect { settings ->
                    // 个别 ROM 对窗口显示模式赋值会抛异常（InvalidDisplayException 等）；
                    // 刷新率属体验优化，失败不得影响设置读取链与本 Activity。
                    runCatching { applyPreferredRefreshRate(window, settings.refreshRateMode) }
                        .onFailure { Log.w(TAG, "应用屏幕刷新率失败", it) }
                }
            }
        }

        setContent {
            App()
        }
    }

    /**
     * 早八闹钟的**前台补救**时机。
     *
     * 写入系统时钟依赖 `startActivity(AlarmClock.ACTION_SET_ALARM)`，受 Android 10+
     * 后台启动限制（BAL）约束：后台调度会被系统**静默拦下**，此时
     * `MorningAlarmWriter` 返回 `Deferred` 且不登记（见 `ForegroundGate`）。
     *
     * 主路径是「拨动开关时前台立刻重排」（`SyncManager.triggerNotificationSync` 在前台
     * 直接内联跑 `NotificationScheduler.reschedule()`）；这里回到前台再补一次，
     * 覆盖「后台期间错过的写入」与「进程被杀后重进」两种情况。
     */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            runCatching {
                val settings = appSettingsRepository.getAppSettingsOnce()
                if (!settings.morningAlarmEnabled) return@runCatching

                val now = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                val today = now.date
                val courses = widgetRepository
                    .getWidgetCoursesByDateRange(
                        today.toString(),
                        today.plus(7, DateTimeUnit.DAY).toString()
                    )
                    .first()
                val plan = MorningAlarmPlan.plan(
                    courses = courses,
                    skippedDates = settings.skippedDates,
                    today = today,
                    days = 7,
                    leadMinutes = settings.morningAlarmLeadMinutes
                )
                MorningAlarmWriter(applicationContext, now).sync(plan)
            }.onFailure { Log.w(TAG, "前台补写早八闹钟失败", it) }
        }
    }

    private fun applyPreferredRefreshRate(window: Window, mode: RefreshRateMode) {
        // AUTO：解除钉定，回到系统自适应（窗口属性 0 = 不指定模式）
        if (mode == RefreshRateMode.AUTO) {
            if (window.attributes.preferredDisplayModeId != 0) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = 0 }
            }
            return
        }
        val display: Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display ?: return
        } else {
            @Suppress("DEPRECATION")
            window.windowManager.defaultDisplay
        }
        val modes = display.supportedModes
        if (modes.isEmpty()) return
        val target = when (mode) {
            RefreshRateMode.HZ_120 -> 120f
            RefreshRateMode.HZ_90 -> 90f
            RefreshRateMode.HZ_60 -> 60f
            RefreshRateMode.AUTO -> return
        }
        // 只在「与当前模式同分辨率」的候选里选，避免钉模式时顺带切换分辨率；
        // 优先取不超过目标档的最高者（目标档高于机型能力时自然向下回落），否则取最接近者。
        val current = display.mode
        val best = modes.asSequence()
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .filter { it.refreshRate <= target + 0.1f }
            .maxByOrNull { it.refreshRate }
            ?: modes.asSequence()
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .minByOrNull { abs(it.refreshRate - target) }
            ?: return
        if (window.attributes.preferredDisplayModeId != best.modeId) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
    }
}
