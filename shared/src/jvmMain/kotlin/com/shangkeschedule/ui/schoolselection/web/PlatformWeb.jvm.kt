package com.shangkeschedule.ui.schoolselection.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
    onWebViewLoadError: (String) -> Unit,
    onPageLoaded: () -> Unit
) {
    // R54-01：回调每次重组都可能是新 lambda，轮询必须调用最新引用，
    // 否则会一直调用首帧那份闭包、读不到最新状态。
    val currentOnPageLoaded by rememberUpdatedState(onPageLoaded)
    val currentOnProgressChange by rememberUpdatedState(onProgressChange)
    val currentOnTitleChange by rememberUpdatedState(onTitleChange)

    // 让页面立即进入「加载完成」状态，避免上层永久卡住进度条
    //
    // R54-01：桌面端此前用 `LaunchedEffect(Unit)`，按 Compose 语义**整个生命周期只跑一次**，
    // 于是 `onPageLoaded()` 只在首次组合被调用一次 —— 而此刻 `pendingStudyRescan` 还是 false，
    // 这次触发被白白消耗。用户随后点「学业识别」置位 pendingStudyRescan 之后，
    // 该回调不会再次被调用 ⇒ 一旦 `found` 分支可达就**必然卡死**（与 Android 侧契约不一致）。
    //
    // 修法：把「上报一次加载完成」做成可重入的周期上报 —— 桌面端没有真实的页面加载事件，
    // 唯一能表达「页面上内容已就绪」的方式就是在上层需要时再次回调。这里以极低频轮询重复上报，
    // 上层的 `onPageLoaded` 消费 `pendingStudyRescan` 后会立即把它清掉，因此重复上报是幂等的、
    // 不产生额外副作用（`onProgressChange(1f)` / `onTitleChange("")` 同理）。
    LaunchedEffect(Unit) {
        while (true) {
            currentOnProgressChange(1f)
            currentOnTitleChange("")
            currentOnPageLoaded()
            delay(DESKTOP_PAGE_LOADED_REPOLL_MS)
        }
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
/**
 * 桌面端「页面已就绪」的重复上报间隔（R54-01）。
 *
 * 桌面端没有真实的页面加载事件，只能靠重复上报让上层有机会消费延迟置位的待办
 *（如学业识别的 `pendingStudyRescan`）。取值取较长的 500ms：
 * 该回调在上层是幂等的（pending 被消费后立即清空），间隔只影响「用户点击到生效」的延迟上限。
 */
private const val DESKTOP_PAGE_LOADED_REPOLL_MS = 500L
