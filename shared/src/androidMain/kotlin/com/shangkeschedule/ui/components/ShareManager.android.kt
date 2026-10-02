package com.shangkeschedule.ui.components

import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.getString
import org.koin.core.context.GlobalContext
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_share

actual val isShareDialogSupported: Boolean = true

actual fun platformShareFile(filePath: String, mimeType: String) {
    shareFile(filePath = filePath, mimeType = mimeType, text = null, subject = null)
}

actual fun platformShareTextWithFile(
    subject: String,
    text: String,
    filePath: String,
    mimeType: String
) {
    shareFile(filePath = filePath, mimeType = mimeType, text = text, subject = subject)
}

/** [platformShareFile] / [platformShareTextWithFile] 的公共实现（有 text 时即「文本 + 附件」分享）。 */
private fun shareFile(filePath: String, mimeType: String, text: String?, subject: String?) {
    val context = GlobalContext.get().get<android.content.Context>()

    val file = filePath.toPath().toFile()
    if (!file.exists()) return

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = mimeType
        if (text != null) putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val chooserTitle = runBlocking { getString(Res.string.action_share) }

    val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}