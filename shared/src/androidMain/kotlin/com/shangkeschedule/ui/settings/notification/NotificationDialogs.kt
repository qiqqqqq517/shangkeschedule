package com.shangkeschedule.ui.settings.notification

import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.shangkeschedule.ui.components.DatePickerModal
import com.shangkeschedule.ui.components.DateRangePickerModal
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.components.toUtcLocalDate
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_add_date_range
import shangkeschedule.shared.generated.resources.action_add_single_date
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_close
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.action_go_to_settings
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.auto_mode_dnd
import shangkeschedule.shared.generated.resources.auto_mode_dnd_permission_warning
import shangkeschedule.shared.generated.resources.auto_mode_off
import shangkeschedule.shared.generated.resources.auto_mode_silent
import shangkeschedule.shared.generated.resources.calendar_today_24px
import shangkeschedule.shared.generated.resources.close_24px
import shangkeschedule.shared.generated.resources.dialog_desc_manage_skipped_dates
import shangkeschedule.shared.generated.resources.dialog_text_clear_confirmation
import shangkeschedule.shared.generated.resources.dialog_text_dnd_permission
import shangkeschedule.shared.generated.resources.dialog_text_exact_alarm_permission
import shangkeschedule.shared.generated.resources.dialog_title_auto_mode_selection
import shangkeschedule.shared.generated.resources.dialog_title_clear_confirmation
import shangkeschedule.shared.generated.resources.dialog_title_dnd_permission
import shangkeschedule.shared.generated.resources.dialog_title_exact_alarm_permission
import shangkeschedule.shared.generated.resources.dialog_title_set_morning_alarm_lead
import shangkeschedule.shared.generated.resources.dialog_title_set_remind_time
import shangkeschedule.shared.generated.resources.dialog_title_add_date_range
import shangkeschedule.shared.generated.resources.dialog_title_manage_skipped_dates
import shangkeschedule.shared.generated.resources.label_minutes_input
import shangkeschedule.shared.generated.resources.skipped_dates_none
import shangkeschedule.shared.generated.resources.toast_clear_failed
import shangkeschedule.shared.generated.resources.toast_clear_success
import shangkeschedule.shared.generated.resources.toast_skip_date_added
import shangkeschedule.shared.generated.resources.toast_skip_date_removed
import shangkeschedule.shared.generated.resources.toast_skip_range_added
import shangkeschedule.shared.generated.resources.toast_skip_range_invalid

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
                dialogTitle = stringResource(Res.string.dialog_title_set_remind_time),
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

        is NotificationDialogType.EditMorningAlarmLead -> {
            var tempInput by remember(uiState.morningAlarmLeadMinutes) {
                mutableStateOf(uiState.morningAlarmLeadMinutes.toString())
            }
            EditRemindMinutesDialog(
                dialogTitle = stringResource(Res.string.dialog_title_set_morning_alarm_lead),
                currentMinutes = tempInput,
                onMinutesChange = { tempInput = it.filter { c -> c.isDigit() } },
                onConfirm = {
                    val mins = tempInput.toIntOrNull() ?: 45
                    viewModel.updateMorningAlarmLeadMinutes(mins)
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

        is NotificationDialogType.ManageSkippedDates -> {
            val rangeAddedMsg = stringResource(Res.string.toast_skip_range_added)
            val rangeInvalidMsg = stringResource(Res.string.toast_skip_range_invalid)

            ManageSkippedDatesDialog(
                dates = uiState.skippedDates,
                onAddDate = { date ->
                    viewModel.addSkippedDate(date)
                    coroutineScope.launch {
                        ToastManager.show(getString(Res.string.toast_skip_date_added, date.toString()))
                    }
                },
                onRemoveDate = { date ->
                    viewModel.removeSkippedDate(date)
                    coroutineScope.launch {
                        ToastManager.show(getString(Res.string.toast_skip_date_removed, date))
                    }
                },
                onAddRange = { start, end ->
                    viewModel.addSkippedDateRange(start, end) { ok ->
                        ToastManager.show(if (ok) rangeAddedMsg else rangeInvalidMsg)
                    }
                },
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
                // 仅在「勿扰」这条路线上提示缺权限；静音不需要该权限，不再误导
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
                    // 只有「勿扰」模式需要「勿扰访问」权限；
                    // 「静音」只改铃声模式（AudioManager.ringerMode），不需要该权限 ——
                    // 旧实现此处对所有非 OFF 选项一律拦截，逼着只想静音的用户
                    // 去开一个他并不需要的权限，属于实证缺陷。
                    val needsDndPermission =
                        selectedKey == AutoControlMode.DND && !hasDndPermission
                    if (needsDndPermission) {
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
    dialogTitle: String,
    currentMinutes: String,
    onMinutesChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
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

/**
 * 「管理跳过日期」弹窗：放假 / 停课日期可逐条增删，也可按区间批量添加。
 *
 * 所有改动立即写入设置（不弹二次确认）；写入后由 DataStore Flow 传导到
 * 提醒排程、早八闹钟与小组件课表。
 *
 * @param dates 当前跳过日期集合（`yyyy-MM-dd`）
 * @param onAddDate 新增单个日期
 * @param onRemoveDate 移除某个日期（点按日期胶囊即可）
 * @param onAddRange 按区间批量新增；区间非法时由 ViewModel 回调 false，不会写入
 * @param onDismiss 关闭弹窗
 */
@Composable
fun ManageSkippedDatesDialog(
    dates: Set<String>,
    onAddDate: (LocalDate) -> Unit,
    onRemoveDate: (String) -> Unit,
    onAddRange: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var showSingleDatePicker by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_title_manage_skipped_dates)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.dialog_desc_manage_skipped_dates),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showSingleDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            vectorResource(Res.drawable.add_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(Res.string.action_add_single_date))
                    }
                    OutlinedButton(
                        onClick = { showRangePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            vectorResource(Res.drawable.calendar_today_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(Res.string.action_add_date_range))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (dates.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.skipped_dates_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                                // 点一下即移除：胶囊里带叉号，避免用户以为只能看
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { onRemoveDate(date) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        vectorResource(Res.drawable.close_24px),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
        }
    )

    // 选择器渲染在 AlertDialog 之外，避免弹窗套弹窗
    if (showSingleDatePicker) {
        DatePickerModal(
            onDateSelected = { millis ->
                if (millis != null) {
                    onAddDate(millis.toUtcLocalDate())
                }
            },
            onDismiss = { showSingleDatePicker = false }
        )
    }
    if (showRangePicker) {
        DateRangePickerModal(
            title = stringResource(Res.string.dialog_title_add_date_range),
            onDismiss = { showRangePicker = false },
            onConfirm = { start, end ->
                showRangePicker = false
                onAddRange(start, end)
            }
        )
    }
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
