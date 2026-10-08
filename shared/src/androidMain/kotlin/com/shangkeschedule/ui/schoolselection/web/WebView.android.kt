package com.shangkeschedule.ui.schoolselection.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

actual val isDesktopPlatform: Boolean = false

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

/**
 * Android 端的 WebViewController 实现
 */
class AndroidWebViewController : WebViewController {
    var webViewInstance: WebView? = null

    /**
     * 当前注册到页面的桥接实例（R40-07）。
     *
     * 入口地址变化时用它同步门禁基准，见 [NativeBridge.updateEntryUrl]。
     */
    var nativeBridge: NativeBridge? = null

    override val currentUrl: String
        get() = webViewInstance?.url ?: ""

    override fun reload() {
        webViewInstance?.reload()
    }

    override fun goBack(): Boolean {
        return if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
            true
        } else {
            false
        }
    }

    override fun canGoBack(): Boolean {
        return webViewInstance?.canGoBack() == true
    }

    override fun setDevToolsEnabled(enabled: Boolean) {
        WebView.setWebContentsDebuggingEnabled(enabled)
    }

    override fun executeScript(jsCode: String) {
        evaluateJavascript(jsCode, null)
    }

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        webViewInstance?.post {
            val finalScript = if (script.contains("_shangkeBridgeInjected")) {
                script
            } else {
                """
                $JS_BRIDGE_INIT
                $script
                """.trimIndent()
            }

            webViewInstance?.evaluateJavascript(finalScript) { res ->
                callback?.invoke(res)
            }
        }
    }
}

@Composable
actual fun rememberWebViewController(): WebViewController {
    return remember { AndroidWebViewController() }
}

/**
 * JS → 原生 桥接。
 *
 * P1-11：此前 `postMessage` 无任何来源校验，而
 * `addJavascriptInterface` 对**所有 frame** 暴露本对象 ⇒ WebView 里加载的任意页面
 * （用户误点的外链、页面内嵌的第三方 iframe）都能调起原生对话框，甚至写用户课表。
 * 现按 [bridgeCallAllowed] 做**来源门禁**：主框架主机必须与本次导入会话入口主机**同站**，
 * 否则整条消息丢弃（fail-closed）。
 *
 * P1-12：日志**只记元数据**（动作名 + 长度 + 来源主机），**不再打印整条消息** ——
 * 原实现把含课表数据、提示原文、报错栈与 URL 的完整 JSON 写进 logcat，属隐私泄露。
 */
class NativeBridge(
    private val handler: WebBridgeHandler,
    entryUrl: String?,
    private val currentMainFrameUrl: () -> String?
) {
    /**
     * 本次导入会话的入口地址（可注册域比对的基准）。
     *
     * R40-07：此前是不可变构造属性，在 WebView factory 里只赋值一次。
     * 而 AndroidView 没有 key、factory 仅首次组合执行 ⇒ 用户在地址栏改了入口
     *（v4.72.0 起开放）之后，门禁仍在拿**旧入口**与当前页比 ⇒ 改到不同可注册域的真实
     * 教务入口（WebVPN 与教务常分属不同域）时，脚本注入成功也真的跑了，但它的一切回传
     *（含 reportAdapterError）都被整条丢弃 ⇒ JS 侧静默 → 30 秒后看门狗弹「导入超时」，
     * 且失败条给出的重试必然再次超时（入口主机仍是冻结的旧主机）。
     *
     * 修法：改为可变属性，由 updateEntryUrl 在入口变化时同步。
     * 同站判定与 fail-closed 语义完全不变 —— 变的只是「基准随实际入口走」。
     */
    @Volatile
    var sessionEntryUrl: String? = entryUrl
        private set

    /** 入口地址变化时同步基准（见 sessionEntryUrl）。 */
    fun updateEntryUrl(newEntryUrl: String?) {
        sessionEntryUrl = newEntryUrl
    }
    @JavascriptInterface
    fun postMessage(jsonMessage: String) {
        val currentUrl = currentMainFrameUrl()
        if (!bridgeCallAllowed(sessionEntryUrl, currentUrl)) {
            // fail-closed：来源不可判定（同站判定失败、URL 解析不出、尚未完成加载）一律拒绝。
            // 只记主机与长度，绝不回显消息正文。
            Log.w(
                "ShangKeBridge",
                "postMessage rejected: entryHost=${hostOfUrl(sessionEntryUrl)}, " +
                    "currentHost=${hostOfUrl(currentUrl)}, len=${jsonMessage.length}"
            )
            return
        }
        // 保留适配脚本与原生之间的通信日志（便于排查「点击导入无反应」类问题），
        // 但**只记元数据**。
        Log.d(
            "ShangKeBridge",
            "postMessage: action=${bridgeActionOf(jsonMessage)}, len=${jsonMessage.length}"
        )
        handler.onMessageReceived(jsonMessage)
    }
}

/**
 * 从桥接消息里取 `action` 字段，**仅用于日志**。
 *
 * 不引入第二份 JSON 解析器：真正的解析在 `WebBridgeHandler` 里用 kotlinx.serialization，
 * 若此处另起一份，出现「日志写 A、实际按 B 执行」的偏差比缺字段更难排查。
 * 正则失配只会让该日志字段变成 `?`，**不影响放行/拒绝判定** ——
 * 那部分完全由 [bridgeCallAllowed] 决定。
 */
private fun bridgeActionOf(jsonMessage: String): String {
    val m = Regex("\\\"action\\\"\\s*:\\s*\\\"([^\\\"]{0,64})\\\"").find(jsonMessage)
    return m?.groupValues?.get(1) ?: "?"
}

@SuppressLint("JavascriptInterface", "SetJavaScriptEnabled")
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
    val androidController = controller as? AndroidWebViewController

    val currentOnProgressChange by rememberUpdatedState(onProgressChange)
    val currentOnTitleChange by rememberUpdatedState(onTitleChange)
    val currentOnWebViewLoadError by rememberUpdatedState(onWebViewLoadError)
    val currentOnPageLoaded by rememberUpdatedState(onPageLoaded)

    val currentIsDesktopMode by rememberUpdatedState(isDesktopMode)

    var loadedBaseUrl by remember { mutableStateOf("") }

    var isFirstLaunch by remember { mutableStateOf(true) }

    // 监听外部强制 URL 变更
    LaunchedEffect(url) {
        if (url.isNotBlank() && url != "about:blank" && url != loadedBaseUrl) {
            loadedBaseUrl = url
            androidController?.webViewInstance?.let { wv ->
                if (wv.url != url) {
                    wv.loadUrl(url)
                }
            }
        }
    }

    // 监听 桌面/移动 模式动态切换
    LaunchedEffect(isDesktopMode) {
        if (isFirstLaunch) {
            isFirstLaunch = false
            return@LaunchedEffect
        }

        androidController?.webViewInstance?.let { wv ->
            val delegate = WebCompatDelegate(wv)
            delegate.enhanceSettings(isDesktopMode)

            val currentRealUrl = wv.url?.takeIf { it.isNotBlank() && it != "about:blank" } ?: url
            if (currentRealUrl.isNotBlank() && currentRealUrl != "about:blank") {
                wv.loadUrl(currentRealUrl)
            }
        }
    }

    // 3. 监听 开发者工具 开关
    LaunchedEffect(isDevToolsEnabled) {
        WebView.setWebContentsDebuggingEnabled(isDevToolsEnabled)
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }

                    androidController?.webViewInstance = this

                    val delegate = WebCompatDelegate(this)
                    delegate.enhanceSettings(isDesktopMode)

                    addJavascriptInterface(WebPostBridge(), "WebPostService")
                    // P1-11：桥接必须做来源门禁 —— `addJavascriptInterface` 对所有 frame 暴露本对象。
                    // 入口 URL 取 `PlatformWebView(url = ...)`（本次导入会话要访问的教务地址），
                    // 当前主框架 URL 由 WebView 自身提供；两者不同站即整条消息丢弃（见 [bridgeCallAllowed]）。
                    // R40-07：留引用，update{} 在入口地址变化时同步门禁基准。
                    val bridge = NativeBridge(bridgeHandler, url) {
                        androidController?.webViewInstance?.url
                    }
                    androidController?.nativeBridge = bridge
                    addJavascriptInterface(bridge, "_shangkeNativeBridge")

                    val baseChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            currentOnProgressChange(newProgress / 100f)
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            if (!title.isNullOrBlank() && !title.startsWith("http")) {
                                currentOnTitleChange(title)
                            }
                        }
                    }

                    val baseViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            currentOnProgressChange(0.1f)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            currentOnProgressChange(1.0f)
                            currentOnPageLoaded()
                            view?.evaluateJavascript("document.title") { value ->
                                val unquoted = value?.trim('"')?.replace("\\\"", "\"")?.trim() ?: ""
                                if (unquoted.isNotBlank() && unquoted != "null") {
                                    currentOnTitleChange(unquoted)
                                }
                            }
                        }
                    }

                    webChromeClient = delegate.wrapWebChromeClient(baseChromeClient, bridgeHandler) { progressInt ->
                        currentOnProgressChange(progressInt.toFloat())
                    }

                    webViewClient = delegate.wrapWebViewClient(baseViewClient, { currentIsDesktopMode }) { errorDescription ->
                        currentOnWebViewLoadError(errorDescription)
                    }

                    if (url.isNotBlank() && url != "about:blank") {
                        loadedBaseUrl = url
                        loadUrl(url)
                    }
                }
            },
            update = { webView ->
                androidController?.webViewInstance = webView
                // R40-07：入口地址变了就同步门禁基准。
                // 不做这一步的话，用户改地址后所有回传都会被按旧入口主机拒收，
                // 表现为「导入必然超时」且重试无用（真因只在 logcat）。
                androidController?.nativeBridge?.updateEntryUrl(url)
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            // 复位进程级调试开关：该 API 是静态的，开启后本进程所有 WebView 都会开放
            // chrome://inspect 调试口（可读取教务会话与页面内容）。用户离开页面即关闭。
            WebView.setWebContentsDebuggingEnabled(false)

            androidController?.webViewInstance?.let { wv ->
                wv.stopLoading()
                wv.webChromeClient = null
                wv.webViewClient = WebViewClient()
                (wv.parent as? ViewGroup)?.removeView(wv)

                wv.clearHistory()
                wv.clearCache(true)
                wv.clearFormData()
                wv.clearSslPreferences()
                wv.destroy()
            }

            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()

            androidController?.webViewInstance = null
        }
    }
}