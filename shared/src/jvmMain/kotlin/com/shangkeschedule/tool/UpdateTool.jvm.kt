package com.shangkeschedule.tool

import java.awt.Desktop
import java.net.URI

/**
 * JVM（桌面端）更新策略：
 * 桌面包产物为 .msi（Windows）/ .dmg（macOS）/ .deb（Linux），按平台后缀挑选下载地址。
 */
actual object PlatformUpdateStrategy {
    actual val isUpdateSupported: Boolean = true

    actual fun parseTargetUrl(response: ApiReleaseResponse): String? {
        val assets = response.assets
        val osName = System.getProperty("os.name").lowercase()
        val preferredExt = when {
            osName.contains("win") -> listOf(".msi", ".exe")
            osName.contains("mac") || osName.contains("darwin") -> listOf(".dmg")
            else -> listOf(".deb", ".rpm")
        }
        return assets.firstOrNull { asset ->
            preferredExt.any { asset.downloadUrl.lowercase().endsWith(it) }
        }?.downloadUrl
            ?: assets.firstOrNull { it.downloadUrl.isNotBlank() }?.downloadUrl
    }

    actual fun openUrl(url: String) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(url))
            }
        } catch (_: Exception) {
            // 浏览器打开失败时静默（与移动端 openUrl 的容错口径一致）
        }
    }
}
