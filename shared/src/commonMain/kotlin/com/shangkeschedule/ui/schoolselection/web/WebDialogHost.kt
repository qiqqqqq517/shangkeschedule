package com.shangkeschedule.ui.schoolselection.web

import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.theme.appColors

import com.shangkeschedule.ui.components.AppRadioRow
import com.shangkeschedule.ui.components.AppTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.*

/**
 * 宿主：监听 Bridge 事件，负责显示 JS 触发的 Compose 弹窗。
 *
 * 事件按 FIFO 排队依次呈现：同一时刻只渲染队首弹窗，前一个关闭后再显示下一个。
 * 此前实现是「后来的事件直接覆盖前一个」，会把排在前面那个弹窗连同它的 JS Promise
 * 一起丢掉——适配脚本若 await 那个 Promise 就会永久挂起，导入流程卡死。
 */
@Composable
fun WebDialogHost(
    uiEvents: Flow<WebUiEvent>
) {
    val pendingEvents = remember { mutableStateListOf<WebUiEvent>() }

    LaunchedEffect(uiEvents) {
        uiEvents.collect { event ->
            pendingEvents.add(event)
        }
    }

    val currentEvent = pendingEvents.firstOrNull()
    if (currentEvent != null) {
        // key(eventId)：连续两个同类弹窗之间隔离 rememberSaveable 状态，
        // 否则后一个弹窗会带着前一个残留的输入框文本/选中项出现。
        key(currentEvent.eventId) {
            // 用「身份比对」出队，而不是无条件 removeAt(0)：
            // 弹窗存在同时触发 onConfirm 与 onDismiss 的可能（按钮 + 点击遮罩/返回键），
            // 无条件出队会连下一个排队弹窗一起吃掉。
            val eventToClose = currentEvent
            val closeCurrent: () -> Unit = {
                if (pendingEvents.firstOrNull() === eventToClose) {
                    pendingEvents.removeAt(0)
                }
            }

            when (currentEvent) {
                is WebUiEvent.ShowAlert -> {
                    AlertHost(currentEvent.data, onConfirm = {
                        currentEvent.callback(true)
                        closeCurrent()
                    }, onDismiss = {
                        currentEvent.callback(false)
                        closeCurrent()
                    })
                }
                is WebUiEvent.ShowPrompt -> {
                    PromptHost(
                        currentEvent.data,
                        onRequestValidation = { input ->
                            currentEvent.onRequestValidation(input) {
                                closeCurrent()
                            }
                        },
                        errorFlow = currentEvent.errorFeedbackFlow,
                        onCancel = {
                            currentEvent.onCancel()
                            closeCurrent()
                        }
                    )
                }
                is WebUiEvent.ShowSingleSelection -> {
                    SingleSelectionHost(currentEvent.data, onResult = { index ->
                        currentEvent.callback(index)
                        closeCurrent()
                    })
                }
            }
        }
    }
}

/** 显示 Alert/Confirm 弹窗。 */
@Composable
private fun AlertHost(data: AlertDialogData, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(data.title) },
        text = { Text(data.content) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(data.confirmText) }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        }
    )
}

/** 显示 Prompt 弹窗。 */
@Composable
private fun PromptHost(
    data: PromptDialogData,
    onRequestValidation: (String) -> Unit,
    errorFlow: Flow<String?>,
    onCancel: () -> Unit
) {
    var inputText by rememberSaveable { mutableStateOf(data.defaultText) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(errorFlow) {
        errorFlow.collect { message ->
            errorText = message
        }
    }

    AppAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(data.title) },
        text = {
            Column {
                AppTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        errorText = null
                    },
                    label = data.tip,
                    isError = errorText != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorText?.let {
                    Text(it, color = appColors().danger, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onRequestValidation(inputText) }
            ) { Text(stringResource(Res.string.action_confirm)) }
        },
        dismissButton = {
            Button(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
        }
    )
}

/** 显示单选列表弹窗。 */
@Composable
private fun SingleSelectionHost(data: SingleSelectionDialogData, onResult: (Int?) -> Unit) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(data.defaultSelectedIndex) }

    AppAlertDialog(
        onDismissRequest = { onResult(null) },
        title = { Text(data.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                data.items.forEachIndexed { index, item ->
                    AppRadioRow(
                        text = item,
                        selected = (index == selectedIndex),
                        onClick = { selectedIndex = index }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onResult(selectedIndex) },
                enabled = selectedIndex != -1
            ) { Text(stringResource(Res.string.action_confirm)) }
        },
        dismissButton = {
            Button(onClick = { onResult(null) }) { Text(stringResource(Res.string.action_cancel)) }
        }
    )
}
