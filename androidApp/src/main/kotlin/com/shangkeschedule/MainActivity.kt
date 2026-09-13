package com.shangkeschedule

import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Window
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shangkeschedule.data.model.RefreshRateMode
import com.shangkeschedule.data.repository.AppSettingsRepository
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

class MainActivity : AppCompatActivity(), KoinComponent {

    private val appSettingsRepository: AppSettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 屏幕刷新率（v3.56.0）：订阅设置流（默认 AUTO = 120/90/60 按机型能力取最高档），
        // 档位或机型能力变化时把窗口钉到对应的显示模式（preferredDisplayModeId 比
        // preferredRefreshRate 提示更可靠——部分 ROM 会忽略纯 rate 提示）。
        lifecycleScope.launch {
            appSettingsRepository.getAppSettings().collect { settings ->
                applyPreferredRefreshRate(window, settings.refreshRateMode)
            }
        }

        setContent {
            App()
        }
    }

    private fun applyPreferredRefreshRate(window: Window, mode: RefreshRateMode) {
        val display: Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display ?: return
        } else {
            @Suppress("DEPRECATION")
            window.windowManager.defaultDisplay
        }
        val modes = display.supportedModes
        if (modes.isEmpty()) return
        // 目标档（AUTO 视作 120：三档里取机型能支持的最高档）
        val target = when (mode) {
            RefreshRateMode.AUTO, RefreshRateMode.HZ_120 -> 120f
            RefreshRateMode.HZ_90 -> 90f
            RefreshRateMode.HZ_60 -> 60f
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
