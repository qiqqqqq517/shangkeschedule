package com.shangkeschedule.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.ui.theme.appColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一条应用内反馈消息；[id] 随每次 show 自增，保证连续两条相同文本也能重新触发显示。 */
data class ToastEvent(val text: String, val id: Long)

/**
 * 全局反馈管理器（v3.54.0 重构）。
 *
 * 此前 [show] 直发平台 Toast——Android 用系统黑色气泡（与三套主题语言都不符）、
 * desktop 端 emit 后无人消费（消息被静默丢弃）。现统一为状态流 + [AppToastHost]
 * 主题化应用内横幅：一次改动，全部 15+ 调用页的反馈观感即刻与主题一致。
 * 与页面级 AppSnackbarHost 的分工：Snackbar 承载「带操作结果的页面内反馈」，
 * 本横幅承载「轻量操作确认 / 全局提示」（无动作按钮场景）。
 */
object ToastManager {
    private var nextId = 0L

    private val _event = MutableStateFlow<ToastEvent?>(null)

    /** 当前待展示的反馈事件（CONFLATED 语义：新消息覆盖旧消息）。 */
    val event: StateFlow<ToastEvent?> = _event.asStateFlow()

    fun show(message: String) {
        if (message.isNotBlank()) {
            _event.value = ToastEvent(message, ++nextId)
        }
    }

    /** 展示期满后由 UI 调用；新消息到达时旧的被覆盖，无需取消。 */
    fun consume() {
        _event.value = null
    }
}

/** 自动消退时长。 */
private const val TOAST_DURATION_MS = 2600L

/**
 * 主题化应用内反馈横幅（v3.54.0）：挂在 App 根部（主题内、导航之上），
 * 底部居中胶囊，淡入 + 上滑入场；连续消息按 [ToastEvent.id] 重新计时。
 */
@Composable
fun AppToastHost(modifier: Modifier = Modifier) {
    val event by ToastManager.event.collectAsStateWithLifecycle()
    var shownText by remember { mutableStateOf("") }

    // 每条新事件（含相同文本）都重置消退计时
    LaunchedEffect(event) {
        val current = event ?: return@LaunchedEffect
        shownText = current.text
        delay(TOAST_DURATION_MS)
        ToastManager.consume()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = event != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
                    .clip(CircleShape)
                    .background(appColors().cardBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    // 直接读 event 文本（终审 P3）：shownText 由 LaunchedEffect 落后一帧，
                    // 首次弹出会出现一帧空胶囊、连续不同文案会闪一帧旧文案
                    text = event?.text ?: shownText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors().textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}
