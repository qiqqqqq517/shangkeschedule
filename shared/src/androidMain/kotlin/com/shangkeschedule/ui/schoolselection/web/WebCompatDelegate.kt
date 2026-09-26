package com.shangkeschedule.ui.schoolselection.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

import com.shangkeschedule.ui.components.ToastManager

/**
 * WebView 代理配置与客户端包装类
 */
class WebCompatDelegate(private val webView: WebView) {

    private val defaultUserAgent: String = webView.settings.userAgentString
    private val requestInterceptor = WebViewRequestInterceptor()

    @SuppressLint("SetJavaScriptEnabled")
    fun enhanceSettings(isDesktopMode: Boolean): WebCompatDelegate {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // 本 WebView 只加载 http(s) 教务页面，file:// 相关开关纯属多余攻击面：
            // allowUniversalAccessFromFileURLs / allowFileAccessFromFileURLs 均已 deprecated，
            // 官方建议恒为 false；allowFileAccess / allowContentAccess 同理。
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            allowFileAccess = false
            allowContentAccess = false
            // 注意：mixedContentMode 保持 ALWAYS_ALLOW —— 大量教务站点为 http，
            // 且 https 门户页会嵌入 http 子资源；收紧会直接打断这些学校的正常访问。
            // 该风险由「拦截器 CORS 同站白名单」与「桥接来源校验」两项来对冲（见审计清单 E8）。
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true

            if (isDesktopMode) {
                userAgentString = DESKTOP_USER_AGENT
                layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            } else {
                userAgentString = defaultUserAgent
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            }

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // CAS/门户页面常用 window.open 或 target=_blank 打开业务系统，需启用多窗口支持
            setSupportMultipleWindows(true)
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        return this
    }

    fun wrapWebViewClient(
        original: WebViewClient,
        isDesktopModeProvider: () -> Boolean,
        onLoadError: (String) -> Unit
    ): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return original.shouldOverrideUrlLoading(view, request)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request != null) {
                    val currentDesktopMode = isDesktopModeProvider()
                    val interceptedResponse = requestInterceptor.intercept(request, currentDesktopMode)
                    if (interceptedResponse != null) {
                        return interceptedResponse
                    }
                }
                return original.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                original.onPageStarted(view, url, favicon)
                view?.let { wv ->
                    // 早期注入 JS_INTERCEPT_POST 拦截网络请求
                    wv.evaluateJavascript(JS_INTERCEPT_POST, null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                original.onPageFinished(view, url)

                view?.let { wv ->
                    wv.evaluateJavascript(JS_INTERCEPT_POST, null)

                    if (isDesktopModeProvider()) {
                        injectDesktopViewportFix(wv)
                    }
                    wv.injectAllJavaScript()
                }
            }

            override fun onReceivedSslError(v: WebView?, h: SslErrorHandler?, e: SslError?) {
                // 仅当失败请求与主框架同主机时才提示全屏错误页：
                // 页面内子资源（统计脚本、外链图片）证书异常不应打断用户，
                // 否则会误触全屏「加载失败」，且原实现把主机名当作错误描述展示。
                // 取消行为保持不变（不调用 proceed()）。
                val mainHost = v?.url?.let { runCatching { java.net.URI(it).host }.getOrNull() }
                val errHost = e?.url?.let { runCatching { java.net.URI(it).host }.getOrNull() }
                if (mainHost != null && errHost != null && mainHost == errHost) {
                    onLoadError(errHost)
                }
                h?.cancel()
                original.onReceivedSslError(v, h, e)
            }

            override fun onReceivedError(v: WebView, q: WebResourceRequest, e: WebResourceError) {
                // 仅对主框架的网络级失败（DNS / 连接拒绝 / 超时等）提示，避免页面内子资源 404 误触全屏错误页
                if (q.isForMainFrame && !q.url.scheme.equals("about", ignoreCase = true)) {
                    val desc = runCatching { e.description?.toString() }.getOrNull() ?: ""
                    onLoadError(desc.ifBlank { "Load failed" })
                }
                original.onReceivedError(v, q, e)
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                // HTTP 4xx/5xx：仅主框架请求提示
                if (request?.isForMainFrame == true && !(request.url.scheme?.equals("about", ignoreCase = true) ?: false)) {
                    val statusCode = errorResponse?.statusCode ?: 0
                    val reason = errorResponse?.reasonPhrase ?: "HTTP Error"
                    onLoadError("$statusCode $reason".trim())
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }
        }
    }

    /**
     * 仅在桌面模式下补全 Viewport Meta 标签与触发 resize，避免 PC 网页排版挤压
     */
    private fun injectDesktopViewportFix(view: WebView) {
        val desktopWidth = 1280
        view.evaluateJavascript("""
            (function() {
                try {
                    var metas = document.getElementsByTagName('meta');
                    for (var i = metas.length - 1; i >= 0; i--) {
                        if (metas[i].getAttribute('name') === 'viewport') {
                            metas[i].parentNode.removeChild(metas[i]);
                        }
                    }
                    var meta = document.createElement('meta');
                    meta.name = "viewport";
                    meta.content = "width=$desktopWidth, initial-scale=1.0, minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes";
                    document.head.appendChild(meta);

                    // 触发 resize 事件促使根据 window 宽高度重绘的 JS 组件重新计算高度
                    window.dispatchEvent(new Event('resize'));
                } catch(e) {
                    console.error("injectDesktopViewportFix Error: ", e);
                }
            })();
        """.trimIndent(), null)
    }

    fun wrapWebChromeClient(
        original: WebChromeClient,
        bridgeHandler: WebBridgeHandler,
        onProgress: (Int) -> Unit
    ): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgress(newProgress)
                original.onProgressChanged(view, newProgress)
            }
            override fun onReceivedTitle(v: WebView?, t: String?) = original.onReceivedTitle(v, t)

            /**
             * alert() 没有返回值，直接转成应用内轻提示并立即放行脚本。
             * 内置适配脚本里有 21 处调用 alert()，交给系统对话框会阻塞 WebView 的 JS 线程，
             * 而且样式与应用完全不一致。
             */
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                message?.let { ToastManager.show(it) }
                result?.confirm()
                return true
            }

            /**
             * confirm() 有同步返回值，必须等用户做完选择才能 confirm()/cancel()，
             * 否则页面拿到的结果与用户操作相反。这里走应用内弹窗并在回调里恢复 JS 语义。
             */
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                if (result == null) return false
                bridgeHandler.showNativeConfirm(message.orEmpty()) { confirmed ->
                    if (confirmed) result.confirm() else result.cancel()
                }
                return true
            }

            /**
             * prompt() 同理，还要把用户输入回传给页面（4 个适配脚本依赖其同步返回值）。
             */
            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                if (result == null) return false
                bridgeHandler.showNativePrompt(message.orEmpty(), defaultValue.orEmpty()) { input ->
                    if (input == null) result.cancel() else result.confirm(input)
                }
                return true
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.d(
                        "ShangKeConsole",
                        "[${it.messageLevel()}] ${it.message()} @${it.sourceId()}:${it.lineNumber()}"
                    )
                }
                return true
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                // 门户/CAS 页面常用 window.open / target=_blank 打开业务系统。
                // 直接把主 WebView 实例赋给新窗口会导致闪退（同一 WebView 不能同时被两个窗口使用）。
                // 这里用代理 WebView 承接新窗口的目标 URL，再转发回主 WebView 加载：
                // 既不出现空白窗口，也不闪退，且能继续使用主 WebView 的 Bridge/拦截器完成导入。
                val proxy = WebView(view?.context ?: this@WebCompatDelegate.webView.context)
                proxy.settings.javaScriptEnabled = true
                proxy.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                        request?.url?.toString()?.let { url ->
                            this@WebCompatDelegate.webView.loadUrl(url)
                        }
                        return true
                    }
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        if (!url.isNullOrBlank() && url != "about:blank") {
                            this@WebCompatDelegate.webView.loadUrl(url)
                            view?.stopLoading()
                        }
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = proxy
                resultMsg?.sendToTarget()
                return true
            }
        }
    }
}

/** 统一注入 Bridge 初始化与 POST 拦截 JS */
internal fun WebView.injectAllJavaScript() {
    evaluateJavascript(JS_BRIDGE_INIT, null)
    evaluateJavascript(JS_INTERCEPT_POST, null)
}