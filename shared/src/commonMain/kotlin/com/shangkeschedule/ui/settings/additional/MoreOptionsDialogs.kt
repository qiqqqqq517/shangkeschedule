package com.shangkeschedule.ui.settings.additional

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shangkeschedule.data.model.StartScreen
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppRadioRow
import com.shangkeschedule.ui.theme.appColors
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.dialog_select_start_screen

/**
 * 启动页面选择弹窗
 */
@Composable
fun StartScreenSelectionDialog(
    showDialog: Boolean,
    currentSelected: StartScreen,
    onDismiss: () -> Unit,
    onConfirm: (StartScreen) -> Unit
) {
    if (!showDialog) return

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_select_start_screen)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                StartScreen.entries.forEach { screen ->
                    AppRadioRow(
                        text = stringResource(screen.labelRes),
                        selected = screen == currentSelected,
                        onClick = { onConfirm(screen) }
                    )
                }
            }
        },
        confirmButton = {
            // 取消弱化为灰字文本钮（与其他对话框取消语言一致）
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel), color = appColors().textSecondary)
            }
        }
    )
}
