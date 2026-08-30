package com.shangkeschedule.ui.schoolselection.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

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
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            allowFileAccess = true
            allowContentAccess = true
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

    fun wrapWebViewClient(original: WebViewClient, isDesktopModeProvider: () -> Boolean): WebViewClient {
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
                original.onReceivedSslError(v, h, e)
            }

            override fun onReceivedError(v: WebView, q: WebResourceRequest, e: WebResourceError) =
                original.onReceivedError(v, q, e)
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

    fun wrapWebChromeClient(original: WebChromeClient, onProgress: (Int) -> Unit): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgress(newProgress)
                original.onProgressChanged(view, newProgress)
            }
            override fun onReceivedTitle(v: WebView?, t: String?) = original.onReceivedTitle(v, t)

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