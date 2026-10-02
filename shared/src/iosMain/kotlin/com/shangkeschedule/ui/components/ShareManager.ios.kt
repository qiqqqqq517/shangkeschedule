package com.shangkeschedule.ui.components

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindowScene

actual val isShareDialogSupported: Boolean = true

actual fun platformShareFile(filePath: String, mimeType: String) {
    presentShareSheet(activityItems = listOf(NSURL.fileURLWithPath(filePath)))
}

actual fun platformShareTextWithFile(
    subject: String,
    text: String,
    filePath: String,
    mimeType: String
) {
    presentShareSheet(
        activityItems = listOf(text, NSURL.fileURLWithPath(filePath))
    )
}

/** 从当前 key window 弹出系统分享面板（文本与附件都塞进 activityItems）。 */
private fun presentShareSheet(activityItems: List<Any>) {
    val activityViewController = UIActivityViewController(
        activityItems = activityItems,
        applicationActivities = null
    )

    // 获取当前处于激活状态的 UIViewController 来弹出分享面板
    val windowScene = UIApplication.sharedApplication.connectedScenes
        .firstOrNull { it is UIWindowScene } as? UIWindowScene
    val rootViewController = windowScene?.windows
        .orEmpty()
        .map { it as? platform.UIKit.UIWindow }
        .firstOrNull { it?.isKeyWindow() == true }
        ?.rootViewController

    rootViewController?.presentViewController(
        activityViewController,
        animated = true,
        completion = null
    )
}