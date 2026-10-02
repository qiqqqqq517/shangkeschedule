package com.shangkeschedule.ui.components

actual val isShareDialogSupported: Boolean = false

actual fun platformShareFile(filePath: String, mimeType: String) {
    // 桌面端不支持或不启用，空实现
}

actual fun platformShareTextWithFile(
    subject: String,
    text: String,
    filePath: String,
    mimeType: String
) {
    // 桌面端没有系统分享面板，空实现（调用方降级为提示保存路径）
}