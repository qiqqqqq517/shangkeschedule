package com.shangkeschedule.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shangkeschedule.data.db.main.TodoItem
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.NativeNumberPicker
import dev.chrisbanes.haze.HazeState
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.close_24px
import shangkeschedule.shared.generated.resources.confirm_delete
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.todo_add
import shangkeschedule.shared.generated.resources.todo_delete_message
import shangkeschedule.shared.generated.resources.todo_delete_title
import shangkeschedule.shared.generated.resources.todo_edit
import shangkeschedule.shared.generated.resources.todo_note_label
import shangkeschedule.shared.generated.resources.todo_time_label
import shangkeschedule.shared.generated.resources.todo_title_label

/**
 * 今日页待办弹窗组件集（v3.54.0 自 TodayScheduleScreen 5600 行主体拆出）：
 * 编辑弹窗 + 时间滚轮 + 删除确认，三个组件相互独立、只依赖通用组件层。
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodoEditDialog(
    existing: TodoItem?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, note: String?, time: String?) -> Unit,
    onDeleteRequest: () -> Unit,
    hazeState: HazeState? = null
) {
    var title by remember(existing) { mutableStateOf(existing?.title ?: "") }
    var time by remember(existing) { mutableStateOf(existing?.time ?: "") }
    var note by remember(existing) { mutableStateOf(existing?.note ?: "") }
    var showTimePicker by remember { mutableStateOf(false) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (existing != null) {
                    IconButton(onClick = onDeleteRequest) {
                        Icon(
                            vectorResource(Res.drawable.delete_24px),
                            contentDescription = stringResource(Res.string.confirm_delete)
                        )
                    }
                }
                Text(
                    text = stringResource(if (existing == null) Res.string.todo_add else Res.string.todo_edit),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                // 键盘弹起时顶起表单（v3.54.0）
                modifier = Modifier.imePadding()
            ) {
                AppTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(Res.string.todo_title_label),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                // 时间选择式：点击输入框弹出 TimePicker，右侧 × 清除已选时间
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppTextField(
                            value = time,
                            onValueChange = {},
                            label = stringResource(Res.string.todo_time_label),
                            placeholder = stringResource(Res.string.todo_time_label),
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = if (time.isNotBlank()) {
                                {
                                    IconButton(onClick = { time = "" }) {
                                        Icon(
                                            vectorResource(Res.drawable.close_24px),
                                            contentDescription = stringResource(Res.string.action_cancel),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showTimePicker = true }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = stringResource(Res.string.todo_note_label),
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            AppDialogActions(
                confirmText = stringResource(Res.string.action_confirm),
                // 标题为空时禁用确认（v3.54.0）：此前可点击但静默无反应
                confirmEnabled = title.isNotBlank(),
                onConfirm = {
                    if (title.isNotBlank()) {
                        onConfirm(
                            title.trim(),
                            note.trim().takeIf { it.isNotBlank() },
                            time.trim().takeIf { it.isNotBlank() }
                        )
                    }
                },
                dismissText = stringResource(Res.string.action_cancel),
                onDismiss = onDismiss
            )
        },
        dismissButton = {}
    )

    if (showTimePicker) {
        TodoTimePickerSheet(
            initialTime = time,
            onDismissRequest = { showTimePicker = false },
            onTimeSelected = { time = it },
            hazeState = hazeState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodoTimePickerSheet(
    initialTime: String?,
    onDismissRequest: () -> Unit,
    onTimeSelected: (String) -> Unit,
    hazeState: HazeState? = null
) {
    // 与应用课程时间选择一致的滚轮底部弹窗（ModalBottomSheet + NativeNumberPicker）
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val parsed = initialTime?.split(":")
    val initialHour = (parsed?.getOrNull(0)?.toIntOrNull() ?: 12).coerceIn(0, 23)
    val initialMinute = (parsed?.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)
    var hour by remember { mutableStateOf(initialHour) }
    var minute by remember { mutableStateOf(initialMinute) }
    val hours = remember { (0..23).map { it.toString().padStart(2, '0') } }
    val minutes = remember { (0..59).map { it.toString().padStart(2, '0') } }

    // 毛玻璃面板：实色内容列由 AppGlassBottomSheet 统一处理
    AppGlassBottomSheet(
        hazeState = hazeState,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.todo_time_label),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                NativeNumberPicker(
                    values = hours,
                    selectedValue = hour.toString().padStart(2, '0'),
                    onValueChange = { hour = it.toInt() },
                    modifier = Modifier.weight(1f)
                )
                Text(":", style = MaterialTheme.typography.titleMedium)
                NativeNumberPicker(
                    values = minutes,
                    selectedValue = minute.toString().padStart(2, '0'),
                    onValueChange = { minute = it.toInt() },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            // 主色胶囊确认钮（AppDialogActions，与其他弹窗操作区同语言）
            AppDialogActions(
                confirmText = stringResource(Res.string.action_confirm),
                onConfirm = {
                    val hh = hour.toString().padStart(2, '0')
                    val mm = minute.toString().padStart(2, '0')
                    onTimeSelected("$hh:$mm")
                    onDismissRequest()
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun TodoDeleteDialog(
    todo: TodoItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    // 危险操作统一走 AppDangerDialog：危险色胶囊确认钮（v2 规范 §4 对话框统一）
    AppDangerDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.todo_delete_title),
        text = stringResource(Res.string.todo_delete_message, todo.title),
        confirmText = stringResource(Res.string.confirm_delete),
        onConfirm = onConfirm,
        dismissText = stringResource(Res.string.action_cancel),
        onDismiss = onDismiss
    )
}
