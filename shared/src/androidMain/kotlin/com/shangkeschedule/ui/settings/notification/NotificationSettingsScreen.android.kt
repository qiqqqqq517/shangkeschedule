package com.shangkeschedule.ui.settings.notification

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shangkeschedule.data.model.AutoControlMode
import com.shangkeschedule.ui.components.ToastManager
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.auto_mode_dnd
import shangkeschedule.shared.generated.resources.auto_mode_off
import shangkeschedule.shared.generated.resources.auto_mode_silent
import shangkeschedule.shared.generated.resources.toast_enable_reminder_first
import shangkeschedule.shared.generated.resources.toast_notification_permission_denied

/**
 * 平台通用的常规设置区块实现（Android 端）
 */
@Composable
actual fun PlatformGeneralSettingsSection(
    uiState: NotificationSettingsUiState,
    viewModel: NotificationSettingsViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 注册通知权限请求器，若用户拒绝授权则通过 ToastManager 弹出提示
    val permissionDeniedMessage = stringResource(Res.string.toast_notification_permission_denied)
    // 记录"因缺少通知权限而挂起开启灵动岛"的意图，授权成功后自动补开
    var pendingDynamicIslandEnable by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            ToastManager.show(permissionDeniedMessage)
        } else if (pendingDynamicIslandEnable) {
            pendingDynamicIslandEnable = false
            viewModel.updateDynamicIslandEnabled(true)
        }
    }

    // 监听生命周期：每次页面重新回到前台
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateExactAlarmStatus(hasExactAlarmPermission(context))
                viewModel.updateDndPermissionStatus(hasDndPermission(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 页面首次挂载时在 Android 13+ 平台上按需发起通知权限申请
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val currentModeText = if (!uiState.autoModeEnabled) {
        stringResource(Res.string.auto_mode_off)
    } else {
        when (uiState.autoControlMode) {
            AutoControlMode.DND -> stringResource(Res.string.auto_mode_dnd)
            AutoControlMode.SILENT -> stringResource(Res.string.auto_mode_silent)
        }
    }

    val enableReminderToast = stringResource(Res.string.toast_enable_reminder_first)

    GeneralSettingsCard(
        uiState = uiState,
        currentModeText = currentModeText,
        onReminderToggle = { isEnabled ->
            viewModel.updateReminderEnabled(isEnabled)
        },
        onDynamicIslandToggle = { isEnabled ->
            if (isEnabled) {
                // 1) 通知权限（Android 13+）：缺失则先请求，授权后自动补开开关
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
                    pendingDynamicIslandEnable = true
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.updateDynamicIslandEnabled(true)
                }
                // 2) 精确闹钟权限（Android 12+）：用于「时间窗口启停」调度，缺失则主动唤起系统授权页
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission(context)) {
                    openExactAlarmSettings(context)
                }
            } else {
                viewModel.updateDynamicIslandEnabled(false)
            }
        },
        onCompatWearableToggle = { isEnabled ->
            viewModel.updateCompatWearableSync(isEnabled)
        },
        onAutoModeClick = {
            if (uiState.reminderEnabled) {
                viewModel.showDialog(NotificationDialogType.AutoModeSelection)
            } else {
                ToastManager.show(enableReminderToast)
            }
        },
        onRemindTimeClick = { viewModel.showDialog(NotificationDialogType.EditRemindMinutes) },
        onAppSettingsClick = { openAppSettings(context) },
        onBatteryOptimizationClick = { openIgnoreBatteryOptimizationSettings(context) }
    )
}

/**
 * 平台通用的通知设置弹窗分发器实现（Android 端）
 */
@Composable
actual fun PlatformNotificationDialogDispatcher(
    uiState: NotificationSettingsUiState,
    viewModel: NotificationSettingsViewModel
) {
    NotificationDialogDispatcher(
        uiState = uiState,
        viewModel = viewModel
    )
}