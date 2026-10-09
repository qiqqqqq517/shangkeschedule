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
 * **v4.75.14：已按用户要求撤掉「桥接来源门禁」（精准回退到 v4.74.1 的导入行为）。**
 *
 * 门禁于 v4.74.15 引入（`bridgeCallAllowed`，fail-closed：主框架主机必须与入口同站），
 * 目的是挡住「导入页里误开的外链 / 内嵌第三方页面」调用原生能力。但它在本项目的数据形态下
 * 把**本校自己的正常回传**也一起拦掉了：索引里大量学校登记的是**统一认证 / 门户**入口，
 * 登录后服务端会跳到**另一个可注册域**的教务系统，而门禁只比对「索引登记的那个入口主机」
 * ⇒ 脚本明明成功解析出课程，回传却被整条丢弃，用户只看到「导入无响应」。
 * 实测正方教务覆盖的 966 所学校里有一批属于这种形态。
 *
 * 回退取舍（用户明确选择「精准回退：只撤掉来源门禁」）：
 *  - 恢复 v4.74.1 的**直转**行为 —— 收到的消息一律交给 [WebBridgeHandler]；
 *  - **保留 P1-12 的日志脱敏**（只记 action + 长度，绝不打印消息正文），
 *    这是与门禁无关的隐私修复，没有理由跟着回退；
 *  - 拦截器侧的 CORS 同站白名单（`WebViewRequestInterceptor`）**照旧生效**，未受影响。
 *
 * 残余风险（诚实标注）：`addJavascriptInterface` 对**所有 frame** 暴露本对象，
 * 因此导入页内嵌的跨源 iframe 理论上可调用原生能力（写课表 / 弹窗）。
 * 彻底关闭需迁移到 `WebViewCompat.addWebMessageListener`（其 allowedOriginRules
 * 由框架按**调用方 origin** 强制校验），会改动全部 OTA 分发脚本，属待裁决项。
 */
class NativeBridge(private val handler: WebBridgeHandler) {

    @JavascriptInterface
    fun postMessage(jsonMessage: String) {
        // P1-12：保留通信日志（便于排查「点击导入无反应」类问题），但**只记元数据**，
        // 绝不打印整条消息 —— 原实现把含课表数据、提示原文、报错栈与 URL 的完整 JSON
        // 写进 logcat，属隐私泄露。
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
 * 正则失配只会让该日志字段变成 `?`，不影响消息处理。
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
                    // v4.75.14：桥接**不再做来源门禁**（按用户要求精准回退到 v4.74.1 行为）。
                    // 原因见 [NativeBridge] 的 KDoc：门禁会把「统一认证入口 → 另一可注册域教务」
                    // 这类本校正常流程的回传整条拦掉，症状是「导入无响应」。
                    // 拦截器侧的 CORS 同站白名单不受影响，仍照旧生效。
                    addJavascriptInterface(NativeBridge(bridgeHandler), "_shangkeNativeBridge")

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