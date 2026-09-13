package com.shangkeschedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.shangkeschedule.ui.theme.LocalAppMotion

/**
 * 全局触觉反馈统一通道（v3.54.0）。
 *
 * 此前全库零触觉反馈——长按挂起、滚轮拨动、Tab 切换、勾选完成、删除确认均为纯视觉，
 * 「反馈的最后一公里」缺失。本工具收敛三档语义（轻点 / 确认 / 长按），统一受
 * 「减弱动态效果」门控：开启无障碍降级时静音震动（与动效降级同口径）。
 * commonMain 仅保证 LongPress / TextHandleMove 两档可用，CONFIRM 以长按档替代；
 * 桌面端平台实现为 no-op，无副作用。
 */
class AppHaptics internal constructor(
    private val haptic: HapticFeedback,
    private val enabled: Boolean
) {
    /** 轻点：滚轮拨动一档 / Tab 切换 / 勾选完成等瞬时小反馈。 */
    fun tick() {
        if (enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /** 确认：危险操作确认、拖拽落定等重要节点（重于 tick）。 */
    fun confirm() {
        if (enabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** 长按：进入挂起模式等长按手势触发时刻。 */
    fun longPress() {
        if (enabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

/** 组合内获取受全局设置门控的触觉反馈工具。 */
@Composable
fun rememberAppHaptics(): AppHaptics {
    val haptic = LocalHapticFeedback.current
    val reduceMotion = LocalAppMotion.current.reduceMotion
    return remember(haptic, reduceMotion) {
        AppHaptics(haptic, enabled = !reduceMotion)
    }
}
