package com.shangkeschedule.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_share
import shangkeschedule.shared.generated.resources.dialog_text_file_saved_share_prompt
import shangkeschedule.shared.generated.resources.dialog_title_file_saved

/**
 * 平台开关：
 * - Android / iOS: true（启用分享管理器与相关提示）
 * - Desktop (JVM): false（直接拦截，不调用分享管理器）
 */
expect val isShareDialogSupported: Boolean

/**
 * 平台底层文件分享执行逻辑
 */
expect fun platformShareFile(filePath: String, mimeType: String)

/**
 * 分享「一段文本 + 一个文件附件」（意见反馈的截图附件用，v4.66.0）。
 *
 * 与 [platformShareFile] 的差别：Android 走同一个 `ACTION_SEND`，但额外带
 * `EXTRA_TEXT` / `EXTRA_SUBJECT` —— 用户可以在分享面板里直接选邮件客户端，
 * 正文与截图一起带走（`mailto:` 协议无法携带附件，所以有附件时改走这里）。
 *
 * Desktop 无分享面板，实现为空（调用方需自行降级为「提示保存路径」）。
 */
expect fun platformShareTextWithFile(
    subject: String,
    text: String,
    filePath: String,
    mimeType: String
)

/**
 * 文件保存成功后的分享确认弹窗（分享管理器的 UI 组件之一）
 */
@Composable
fun ShareDialog(
    filePath: String,
    mimeType: String,
    onDismiss: () -> Unit
) {
    if (!isShareDialogSupported) return

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_title_file_saved)) },
        text = { Text(stringResource(Res.string.dialog_text_file_saved_share_prompt)) },
        confirmButton = {
            // 统一操作区：取消灰字 + 分享主色胶囊（AppDialogActions）
            AppDialogActions(
                confirmText = stringResource(Res.string.action_share),
                onConfirm = {
                    platformShareFile(filePath, mimeType)
                    onDismiss()
                },
                dismissText = stringResource(Res.string.action_cancel),
                onDismiss = onDismiss
            )
        },
        dismissButton = {}
    )
}
