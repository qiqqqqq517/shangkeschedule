package com.shangkeschedule.ui.settings.notification

import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shangkeschedule.data.model.AutoControlMode
import com.shangkeschedule.ui.components.ToastManager
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_close
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.action_go_to_settings
import shangkeschedule.shared.generated.resources.auto_mode_dnd
import shangkeschedule.shared.generated.resources.auto_mode_dnd_permission_warning
import shangkeschedule.shared.generated.resources.auto_mode_off
import shangkeschedule.shared.generated.resources.auto_mode_silent
import shangkeschedule.shared.generated.resources.dialog_text_clear_confirmation
import shangkeschedule.shared.generated.resources.dialog_text_dnd_permission
import shangkeschedule.shared.generated.resources.dialog_text_exact_alarm_permission
import shangkeschedule.shared.generated.resources.dialog_title_auto_mode_selection
import shangkeschedule.shared.generated.resources.dialog_title_clear_confirmation
import shangkeschedule.shared.generated.resources.dialog_title_dnd_permission
import shangkeschedule.shared.generated.resources.dialog_title_exact_alarm_permission
import shangkeschedule.shared.generated.resources.dialog_title_set_remind_time
import shangkeschedule.shared.generated.resources.dialog_title_view_skipped_dates
import shangkeschedule.shared.generated.resources.label_minutes_input
import shangkeschedule.shared.generated.resources.skipped_dates_none
import shangkeschedule.shared.generated.resources.toast_clear_failed
import shangkeschedule.shared.generated.resources.toast_clear_success

/**
 * Android 专属的弹窗派发器
 * 移除多余的 Worker 触发回调，由后台的 SyncManager 统一通过 Flow 响应式调度
 */
@Composable
fun NotificationDialogDispatcher(
    uiState: NotificationSettingsUiState,
    viewModel: NotificationSettingsViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    var showDndGuideDialog by remember { mutableStateOf(false) }

    when (uiState.activeDialog) {
        is NotificationDialogType.EditRemindMinutes -> {
            var tempInput by remember(uiState.remindBeforeMinutes) {
                mutableStateOf(uiState.remindBeforeMinutes.toString())
            }
            EditRemindMinutesDialog(
                currentMinutes = tempInput,
                onMinutesChange = { tempInput = it.filter { c -> c.isDigit() } },
                onConfirm = {
                    val mins = tempInput.toIntOrNull() ?: 15
                    viewModel.updateRemindBeforeMinutes(mins)
                    viewModel.dismissDialog()
                },
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        is NotificationDialogType.AutoModeSelection -> {
            AutoModeSelectionDialog(
                currentAutoModeEnabled = uiState.autoModeEnabled,
                currentAutoControlMode = uiState.autoControlMode,
                hasDndPermission = uiState.dndPermissionStatus,
                onModeSelected = { selectedKey ->
                    if (selectedKey == "OFF") {
                        viewModel.updateAutoMode(false, uiState.autoControlMode)
                    } else if (selectedKey is AutoControlMode) {
                        viewModel.updateAutoMode(true, selectedKey)
                    }
                    viewModel.dismissDialog()
                },
                onRequireDndPermission = {
                    showDndGuideDialog = true
                },
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        is NotificationDialogType.ClearConfirmation -> {
            val successMsg = stringResource(Res.string.toast_clear_success)

            // 危险操作（清空跳过日期）统一走 AppDangerDialog
            AppDangerDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = stringResource(Res.string.dialog_title_clear_confirmation),
                text = stringResource(Res.string.dialog_text_clear_confirmation),
                confirmText = stringResource(Res.string.action_confirm),
                onConfirm = {
                    viewModel.clearSkippedDates { result ->
                        result.fold(
                            onSuccess = { ToastManager.show(successMsg) },
                            onFailure = { e ->
                                coroutineScope.launch {
                                    val errorMsg = getString(Res.string.toast_clear_failed, e.message ?: "")
                                    ToastManager.show(errorMsg)
                                }
                            }
                        )
                    }
                    viewModel.dismissDialog()
                },
                dismissText = stringResource(Res.string.action_cancel),
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        is NotificationDialogType.ViewSkippedDates -> {
            ViewSkippedDatesDialog(
                dates = uiState.skippedDates,
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        else -> {}
    }

    // 选中自动模式无权限时，弹出勿扰权限引导弹窗
    if (showDndGuideDialog) {
        DndPermissionGuideDialog(
            onDismiss = { showDndGuideDialog = false }
        )
    }
}

/**
 * Android 专属的权限引导弹窗组
 * 由 Android 侧组件按照本地 UI State 显隐控制并调用
 */
@Composable
fun ExactAlarmPermissionGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    PermissionGuideDialog(
        title = stringResource(Res.string.dialog_title_exact_alarm_permission),
        text = stringResource(Res.string.dialog_text_exact_alarm_permission),
        onConfirm = { openExactAlarmSettings(context) },
        onDismiss = onDismiss
    )
}

@Composable
fun DndPermissionGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    PermissionGuideDialog(
        title = stringResource(Res.string.dialog_title_dnd_permission),
        text = stringResource(Res.string.dialog_text_dnd_permission),
        onConfirm = { openDndSettings(context) },
        onDismiss = onDismiss
    )
}

@Composable
fun AutoModeSelectionDialog(
    currentAutoModeEnabled: Boolean,
    currentAutoControlMode: AutoControlMode,
    hasDndPermission: Boolean,
    onModeSelected: (Any) -> Unit,
    onRequireDndPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedKey by remember { mutableStateOf<Any>(if (currentAutoModeEnabled) currentAutoControlMode else "OFF") }

    val modeOptions = listOf(
        "OFF" to stringResource(Res.string.auto_mode_off),
        AutoControlMode.DND to stringResource(Res.string.auto_mode_dnd),
        AutoControlMode.SILENT to stringResource(Res.string.auto_mode_silent)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_title_auto_mode_selection)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!hasDndPermission) {
                    Text(
                        text = stringResource(Res.string.auto_mode_dnd_permission_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                modeOptions.forEach { (optionKey, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedKey = optionKey }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = (selectedKey == optionKey), onClick = { selectedKey = optionKey })
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            // 统一操作区：取消灰字 + 确认主色胶囊（AppDialogActions）
            AppDialogActions(
                confirmText = stringResource(Res.string.action_confirm),
                onConfirm = {
                    if (selectedKey != "OFF" && !hasDndPermission) {
                        onDismiss()
                        onRequireDndPermission()
                    } else {
                        onModeSelected(selectedKey)
                    }
                },
                dismissText = stringResource(Res.string.action_cancel),
                onDismiss = onDismiss
            )
        },
        dismissButton = {}
    )
}

@Composable
fun EditRemindMinutesDialog(
    currentMinutes: String,
    onMinutesChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_title_set_remind_time)) },
        text = {
            // 统一柔和填充输入框
            AppTextField(
                value = currentMinutes,
                onValueChange = onMinutesChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = stringResource(Res.string.label_minutes_input),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            // 统一操作区
            AppDialogActions(
                confirmText = stringResource(Res.string.action_confirm),
                onConfirm = onConfirm,
                dismissText = stringResource(Res.string.action_cancel),
                onDismiss = onDismiss
            )
        },
        dismissButton = {}
    )
}

@Composable
fun ViewSkippedDatesDialog(dates: Set<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_title_view_skipped_dates)) },
        text = {
            if (dates.isEmpty()) {
                Text(stringResource(Res.string.skipped_dates_none))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(dates.toList().sorted()) { date ->
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(
                                text = date,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
        }
    )
}

/**
 * 通用权限引导弹窗基础 UI Component
 */
@Composable
fun PermissionGuideDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            // 统一操作区：取消灰字 + 去设置主色胶囊（AppDialogActions）
            AppDialogActions(
                confirmText = stringResource(Res.string.action_go_to_settings),
                onConfirm = {
                    onConfirm()
                    onDismiss()
                },
                dismissText = stringResource(Res.string.action_cancel),
                onDismiss = onDismiss
            )
        },
        dismissButton = {}
    )
}