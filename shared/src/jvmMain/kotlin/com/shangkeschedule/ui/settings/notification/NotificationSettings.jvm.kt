package com.shangkeschedule.ui.settings.notification

import androidx.compose.runtime.Composable

/**
 * JVM（桌面端）通知设置补充区桩：
 * 「常规设置」区承载的是 Android 专属能力（通知渠道 / 精确闹钟权限等），
 * 桌面端无对应系统能力，保持空实现直接隐藏。
 */
@Composable
actual fun PlatformGeneralSettingsSection(
    uiState: NotificationSettingsUiState,
    viewModel: NotificationSettingsViewModel
) {
    // 桌面端隐藏 Android 专属设置项（空实现）
}

/**
 * JVM（桌面端）通知弹窗派发器桩：
 * 权限引导/精确闹钟等弹窗均为 Android 专属流程，桌面端无弹窗。
 */
@Composable
actual fun PlatformNotificationDialogDispatcher(
    uiState: NotificationSettingsUiState,
    viewModel: NotificationSettingsViewModel
) {
    // 桌面端无通知运行时权限流程（空实现）
}
