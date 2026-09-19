package com.shangkeschedule.ui.schoolselection.web

import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.parsing.ParseException
import io.ktor.http.parseHeaderValue
import io.ktor.http.takeFrom
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.util.Collections

/**
 * WebView 请求拦截器（电脑模式专用）
 * 用于过滤特定指纹 Header 并接管 XHR/Fetch POST 数据流
 */
class WebViewRequestInterceptor {
    companion object {
        private val ktorClientNoRedirects = HttpClient(CIO) {
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
        }

        private val ktorClientWithRedirects = HttpClient(CIO) {
            followRedirects = true
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
        }

        private var postBodyRegistry = Collections.synchronizedMap(mutableMapOf<String, RegisteredPostData>())

        /**
         * 协议归一目标主机：湖北职业技术学院教务域（jwgl.hbvtc.edu.cn）。
         *
         * 背景：Android WebView（Chromium）会把页面内相对路径的 iframe/fetch 子资源请求
         * 自动升级为 https（Upgrade-Insecure-Requests / HTTPS-First）。若登录会话落在 http 域
         * （学校侧 CAS service 曾配置为 http），升级后的 https 请求不带会话，服务器返回
         * {"flag1":2,"msgContent":"请先登录系统"}（GBK）被按 UTF-8 解码成乱码渲染在内容区。
         * 本拦截器对该域的非主框架「页面/接口类」子资源接管，强制改写为主文档（会话域）
         * 的协议后用 ktor 转发（自动附带对应协议的 Cookie），从根上消除 http/https 会话失配。
         */
        private const val PROTOCOL_NORMALIZE_HOST = "jwgl.hbvtc.edu.cn"

        /** 判断声明的 charset 是否含 UTF-8（大小写不敏感，容忍 "utf8" / "utf-8" / "UTF_8"） */
        private fun containsUtf8(charset: String): Boolean {
            val normalized = charset.lowercase().replace("_", "")
            return normalized.contains("utf8") || normalized.contains("utf-8")
        }

        /**
         * 判断字节流是否为合法 UTF-8 编码（含纯 ASCII）。
         * 仅做结构校验，不产生解码副作用，供编码回填判定使用。
         */
        private fun isValidUtf8(bytes: ByteArray): Boolean {
            val len = bytes.size
            var i = 0
            while (i < len) {
                val b = bytes[i].toInt() and 0xFF
                val n: Int
                if (b < 0x80) {
                    n = 1
                } else if (b >= 0xC2 && b <= 0xDF) {
                    n = 2
                } else if (b >= 0xE0 && b <= 0xEF) {
                    n = 3
                } else if (b >= 0xF0 && b <= 0xF4) {
                    n = 4
                } else {
                    return false
                }
                if (i + n > len) return false
                for (j in 1 until n) {
                    val cb = bytes[i + j].toInt() and 0xFF
                    if (cb < 0x80 || cb > 0xBF) return false
                }
                // 拒绝过长编码 / 过短表示（合法 UTF-8 的额外约束）
                if (n == 2 && b < 0xC2) return false
                if (n == 3 && b == 0xE0 && (bytes[i + 1].toInt() and 0xFF) < 0xA0) return false
                if (n == 3 && b == 0xED && (bytes[i + 1].toInt() and 0xFF) > 0x9F) return false
                if (n == 4 && b == 0xF0 && (bytes[i + 1].toInt() and 0xFF) < 0x90) return false
                if (n == 4 && b == 0xF4 && (bytes[i + 1].toInt() and 0xFF) > 0x8F) return false
                i += n
            }
            return true
        }

        /**
         * 是否为静态资源请求（按扩展名判断）。
         * 静态资源无需会话，被原生栈升级到 https 也能正常加载，故放行原生栈以保留性能。
         */
        private fun isStaticAsset(url: String): Boolean {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return path.endsWith(".js") || path.endsWith(".css") || path.endsWith(".png") ||
                path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".gif") ||
                path.endsWith(".ico") || path.endsWith(".woff") || path.endsWith(".woff2") ||
                path.endsWith(".ttf") || path.endsWith(".svg") || path.endsWith(".webp") ||
                path.endsWith(".map") || path.endsWith(".eot") || path.endsWith(".otf")
        }

        fun registerPostData(id: String, body: String, contentType: String) {
            // 容量防护：防止恶意页面无限注册 POST 体撑爆内存；
            // 单条 body 也限制在 1MB 内（正常表单提交远小于此）。
            if (postBodyRegistry.size >= 16 || body.length > 1_000_000) return
            postBodyRegistry[id] = RegisteredPostData(body, contentType)
        }

        private data class RegisteredPostData(val body: String, val contentType: String)
    }

    private val cookieManager = CookieManager.getInstance()

    /**
     * 最近一次主框架导航的协议（http/https），作为「登录会话所在域」的指示。
     * 主框架导航先于其页面内子资源请求到达，故子资源处理时可据此归一协议。
     */
    private var currentMainFrameScheme: String? = null

    fun intercept(request: WebResourceRequest, isDesktopMode: Boolean): WebResourceResponse? {
        val rawUrl = request.url.toString()

        // 0. 记录主框架导航协议（登录会话所在域指示），供协议归一使用。
        if (request.isForMainFrame) {
            request.url.scheme?.let { currentMainFrameScheme = it }
        }

        // 0.5 康普「全新教务」等教务登录页依赖微信 wxLogin.js；
        //    WebView 请求该脚本常被拒绝/失败，页面内联脚本在 new WxLogin 处抛
        //    ReferenceError 而整体中断，导致「立即登录」按钮事件未绑定、点击无反应。
        //    这里在任意模式下都用本地无害 stub 兜底，确保账号密码登录可正常使用。
        if (rawUrl.contains("res.wx.qq.com") && rawUrl.contains("wxLogin.js")) {
            return WebResourceResponse(
                "application/javascript",
                "UTF-8",
                200,
                "OK",
                emptyMap(),
                "window.WxLogin = function () {};".byteInputStream()
            )
        }

        // 1. 基础校验：仅处理 http/https 请求。
        //    注意：不要求 isDesktopMode——主框架 HTML 即使在手机模式也需接管，
        //    用于清洗服务端返回的多余 UTF-8 BOM（如湖北职院 casp 双 BOM 导致 WebView 用 GBK 解码乱码）。
        if (!rawUrl.startsWith("http")) return null

        val requestIdHeader = request.requestHeaders["X-WebView-Post-Id"]
        val requestIdParam = request.url.getQueryParameter("_webview_post_id")
        val requestId = requestIdHeader ?: requestIdParam

        // 2. 关键过滤逻辑：
        // 如果既不是 MainFrame (主网页导航)，也没有带 POST ID 标，
        // 说明这只是网页内部的普通 GET/AJAX/图片/JS 资源请求，直接放行给 WebView 原生网络栈！
        //     ——例外：湖北职院 jwgl 域的非主框架「页面/接口类」请求需先做协议归一（见 2.5），
        //        否则 WebView 原生栈把 http iframe/fetch 升级为 https 后无会话，返回乱码 JSON。
        if (!request.isForMainFrame && requestId == null) {
            // 2.5 协议归一（湖北职院教务域）：仅接管「页面/接口类」子资源（排除静态资源），
            //     且仅当请求协议与主文档（会话域）不一致时，改写协议后用 ktor 转发。
            val scheme = request.url.scheme
            val isNormalizeTarget = rawUrl.contains(PROTOCOL_NORMALIZE_HOST) &&
                currentMainFrameScheme != null &&
                scheme != currentMainFrameScheme
            if (isNormalizeTarget && !isStaticAsset(rawUrl)) {
                val normalizedUrl = rawUrl.replaceFirst("$scheme://", "$currentMainFrameScheme://")
                Log.i(
                    "WebViewInterceptor",
                    "PROTO-NORMALIZE $rawUrl -> $normalizedUrl (main=$currentMainFrameScheme)"
                )
                return forwardWithKtor(normalizedUrl, request, null, isMainFrame = false)
            }
            return null
        }

        // 3. 剥离内部凭据 Query 参数，恢复真实的目标请求 URL
        val url = if (requestIdParam != null) {
            val uriBuilder = request.url.buildUpon().clearQuery()
            request.url.queryParameterNames.forEach { name ->
                if (name != "_webview_post_id") {
                    request.url.getQueryParameters(name).forEach { value ->
                        uriBuilder.appendQueryParameter(name, value)
                    }
                }
            }
            uriBuilder.build().toString()
        } else {
            rawUrl
        }

        val registeredData = requestId?.let { postBodyRegistry.remove(it) }

        // 4. 如果不是 GET 且没有获取到 Body 数据，放回原生处理
        if (request.method.uppercase() != "GET" && registeredData == null) {
            return null
        }

        return forwardWithKtor(url, request, registeredData, request.isForMainFrame)
    }

    /**
     * 用 ktor 转发请求并构造 WebResourceResponse。
     *
     * @param url 实际请求 URL（协议归一后可能与原始 URL 不同）
     * @param request 原始 WebResourceRequest（携带请求头 / 方法 / 是否主框架）
     * @param registeredData 已注册的 POST Body（无则 null）
     * @param isMainFrame 是否主框架导航（决定跟随重定向策略与 3xx 处理方式）
     */
    private fun forwardWithKtor(
        url: String,
        request: WebResourceRequest,
        registeredData: RegisteredPostData?,
        isMainFrame: Boolean
    ): WebResourceResponse? {
        val client = if (isMainFrame) ktorClientNoRedirects else ktorClientWithRedirects

        try {
            return runBlocking {
                val response = client.request {
                    this.url.takeFrom(url)
                    this.method = HttpMethod.parse(request.method)

                    if (registeredData != null) {
                        val rawContentType = registeredData.contentType.ifBlank { "application/x-www-form-urlencoded" }
                        val parsedContentType = try {
                            ContentType.parse(rawContentType)
                        } catch (e: Exception) {
                            ContentType.Application.FormUrlEncoded
                        }
                        setBody(ByteArrayContent(registeredData.body.toByteArray(Charsets.UTF_8), parsedContentType))
                    }

                    headers {
                        request.requestHeaders.forEach { (key, value) ->
                            // 过滤容易被服务端反爬系统标记为移动端 WebView 的 Header
                            if (!key.equals("X-Requested-With", ignoreCase = true) &&
                                !key.equals("X-WebView-Post-Id", ignoreCase = true)) {
                                append(key, value)
                            }
                        }

                        val cookies = cookieManager.getCookie(url)
                        if (!cookies.isNullOrEmpty()) {
                            append(HttpHeaders.Cookie, cookies)
                        }
                    }
                }

                // 同步 Set-Cookie 到 WebView 容器
                response.headers.getAll(HttpHeaders.SetCookie)?.forEach { cookieStr ->
                    cookieManager.setCookie(url, cookieStr)
                }
                cookieManager.flush()

                val statusCode = response.status.value

                // WebView 无法原生解析 3xx 响应，主框架需通过 JS 跳转替代
                if (statusCode in 300..399) {
                    if (isMainFrame) {
                        val location = response.headers[HttpHeaders.Location]
                        if (location != null) {
                            val absoluteLocation = resolveAbsoluteUrl(url, location)
                            // Location 头为服务端可控值，必须转义后再拼入 <script> 字符串上下文，
                            // 防止注入任意 JS（如构造 ');<code>;// 或 </script> 逃逸）。
                            val safeLocation = escapeJsStringForHtml(absoluteLocation)
                            val html = "<html><script>window.location.replace('$safeLocation');</script></html>"
                            return@runBlocking WebResourceResponse(
                                "text/html",
                                "UTF-8",
                                200,
                                "OK",
                                mapOf("Cache-Control" to "no-cache"),
                                html.byteInputStream()
                            )
                        }
                    }
                    return@runBlocking null
                }

                val contentTypeHeader = response.headers[HttpHeaders.ContentType]
                var mimeType = "text/html"
                var encoding = "UTF-8"

                if (!contentTypeHeader.isNullOrBlank()) {
                    try {
                        val parsedHeader = parseHeaderValue(contentTypeHeader)
                        if (parsedHeader.isNotEmpty()) {
                            mimeType = parsedHeader[0].value.substringBefore(";")
                            val charsetParam = parsedHeader[0].params.find { it.name.equals("charset", ignoreCase = true) }
                            if (charsetParam != null) {
                                encoding = charsetParam.value
                            }
                        }
                    } catch (e: ParseException) {
                        mimeType = contentTypeHeader.substringBefore(";")
                        if (contentTypeHeader.contains("charset=")) {
                            encoding = contentTypeHeader.substringAfter("charset=").substringBefore(";")
                        }
                    }
                }

                // 部分教务系统（如湖北职院强智 jwgl）返回的是 UTF-8 字节，但响应头要么不带 charset、
                // 要么标注成 GBK/ISO-8859-1 等非 UTF-8 值。若未显式声明 charset，国产 ROM WebView 会回退
                // 到系统默认 GBK，把 UTF-8 中文解成乱码（本会话见过的 {"flag1":2,"msgContent":"..乱码.."}）。
                val rawBytes = response.bodyAsChannel().toInputStream().readBytes()
                if (isMainFrame) {
                    val preview = rawBytes.take(8).joinToString(" ") { "%02x".format(it) }
                    Log.i("WebViewInterceptor", "MAIN $url CT=$contentTypeHeader enc=$encoding mime=$mimeType len=${rawBytes.size} first8=$preview")
                }
                // 仅当「未声明 UTF-8」（默认/空/ISO-8859-1）才校验字节：若字节是合法 UTF-8，则强制按 UTF-8
                // 解码，规避系统回退 GBK；若确实是 GBK 字节，则保留原 encoding，不影响其它正常学校。
                if (!containsUtf8(encoding)) {
                    val validUtf8 = isValidUtf8(rawBytes)
                    val verboseLog = isMainFrame || encoding.equals("ISO-8859-1", ignoreCase = true)
                    if (verboseLog) {
                        Log.i("WebViewInterceptor", "ENC-CHECK declared=$encoding validUtf8=$validUtf8 -> ${if (validUtf8) "UTF-8" else "keep"}")
                    }
                    if (validUtf8) encoding = "UTF-8"
                }

                val responseHeadersMap = mutableMapOf<String, String>()
                response.headers.forEach { name, values ->
                    // 剔除 Content-Encoding，防止底层自动解压后 WebView 重复解压导致乱码
                    if (!name.equals("Content-Encoding", ignoreCase = true)) {
                        responseHeadersMap[name] = values.joinToString(", ")
                    }
                }

                // 若上面将 encoding 修正为 UTF-8，同步改写 Content-Type 头里的 charset，
                // 否则 WebView 仍可能按头里残留的 GBK 解码（编码修正对 JSON 等子资源同样生效）。
                if (containsUtf8(encoding) && !contentTypeHeader.isNullOrBlank()) {
                    val fixedHeader = contentTypeHeader.replace(Regex("(?i)charset\\s*=\\s*[^;]+"), "charset=UTF-8")
                    responseHeadersMap[HttpHeaders.ContentType] = fixedHeader
                }

                // CORS 跨域子资源：适配器跨域 fetch（如国科大 xkgo→xkcts 课程详情页）经拦截器
                // 转发时，目标服务器不返回 Access-Control-Allow-Origin，渲染进程同源策略会拦截响应。
                // 对非主框架的拦截响应回填 ACAO:*（适配器侧配合 credentials:'omit'），绕过浏览器 CORS。
                if (!isMainFrame &&
                    !responseHeadersMap.keys.any { it.equals("Access-Control-Allow-Origin", ignoreCase = true) }
                ) {
                    responseHeadersMap["Access-Control-Allow-Origin"] = "*"
                }

                // 对 text/html 清洗开头可能多余的 UTF-8 BOM：
                // 部分学校（如湖北职院 casp）服务端会输出双重 BOM（EF BB BF EF BB BF），
                // 国产 ROM WebView 遇双 BOM 时编码检测会回退到系统默认 GBK，把 UTF-8 中文解成乱码。
                val servedBytes = if (mimeType.startsWith("text/html", ignoreCase = true)) {
                    var i = 0
                    while (i + 2 < rawBytes.size &&
                        rawBytes[i] == 0xEF.toByte() &&
                        rawBytes[i + 1] == 0xBB.toByte() &&
                        rawBytes[i + 2] == 0xBF.toByte()
                    ) i += 3
                    if (i > 0) rawBytes.copyOfRange(i, rawBytes.size) else rawBytes
                } else rawBytes
                if (isMainFrame) {
                    val after = servedBytes.take(16).joinToString(" ") { "%02x".format(it) }
                    val sample = runCatching { servedBytes.toString(Charsets.UTF_8).substring(0, 200) }.getOrDefault("decode-fail")
                    Log.i("WebViewInterceptor", "AFTER-CLEAN len=${servedBytes.size} first16=$after enc=$encoding")
                    Log.i("WebViewInterceptor", "SAMPLE: $sample")
                }

                WebResourceResponse(
                    mimeType,
                    encoding,
                    statusCode,
                    response.status.description.ifBlank { "OK" },
                    responseHeadersMap,
                    servedBytes.inputStream()
                )
            }
        } catch (e: Exception) {
            Log.e("WebViewInterceptor", "Error intercepting request: $url", e)
            return null
        }
    }

    private fun resolveAbsoluteUrl(baseUrl: String, location: String): String {
        return try {
            URI(baseUrl).resolve(location).toString()
        } catch (e: Exception) {
            location
        }
    }

    /**
     * 将字符串安全地转义为嵌在 HTML <script> 单引号字面量中的 JS 字符串。
     * 转义反斜杠/引号/换行，并将 `<` `>` 编码为 \u003C/\u003E，
     * 既防止 JS 字符串逃逸注入代码，也防止 `</script>` 提前闭合脚本块。
     */
    private fun escapeJsStringForHtml(input: String): String {
        return buildString {
            for (c in input) {
                when (c) {
                    '\\' -> append("\\\\")
                    '\'' -> append("\\'")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '<' -> append("\\u003C")
                    '>' -> append("\\u003E")
                    else -> append(c)
                }
            }
        }
    }
}

/**
 * JSBridge 注入桥接，供 JS 侧暂存 POST Body 数据
 */
class WebPostBridge {
    @JavascriptInterface
    fun register(id: String, body: String, type: String) {
        WebViewRequestInterceptor.registerPostData(id, body, type)
    }
}
