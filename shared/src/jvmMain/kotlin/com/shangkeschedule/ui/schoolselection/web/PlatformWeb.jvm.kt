package com.shangkeschedule.ui.schoolselection.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.webview_load_error_detail

/**
 * JVM（桌面端）WebView 能力桩：
 * Compose Desktop 无内嵌系统 WebView，教务导入属移动端能力；
 * 此处提供可编译的最小实现 + 明确的占位提示，保证桌面端整体可构建可运行。
 */
actual val isDesktopPlatform: Boolean = true

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // 桌面端无系统返回键：ESC 返回由窗口层处理，这里保持空实现
}

/**
 * 桌面端控制器桩：所有能力为安全空实现（不崩溃、无副作用）。
 */
class DesktopWebViewController : WebViewController {
    override val currentUrl: String get() = ""

    override fun reload() = Unit

    override fun goBack(): Boolean = false

    override fun canGoBack(): Boolean = false

    override fun setDevToolsEnabled(enabled: Boolean) = Unit

    override fun executeScript(jsCode: String) = Unit

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        callback?.invoke(null)
    }
}

@Composable
actual fun rememberWebViewController(): WebViewController {
    return remember { DesktopWebViewController() }
}

@Composable
actual fun PlatformWebView(
    modifier: Modifier,
    url: String,
    isDesktopMode: Boolean,
    isDevToolsEnabled: Boolean,
    controller: WebViewController,
    bridgeHandler: WebBridgeHandler,
    onProgressChange: (Float) -> Unit,
    onTitleChange: (String) -> Unit,
    onNavigateToSchedule: () -> Unit,
    onWebViewLoadError: (String) -> Unit
) {
    // 让页面立即进入「加载完成」状态，避免上层永久卡住进度条
    LaunchedEffect(Unit) {
        onProgressChange(1f)
        onTitleChange("")
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.webview_load_error_detail),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
