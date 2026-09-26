package com.shangkeschedule.tool

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
// DEFAULT 是 Logger.Companion 上的扩展属性（common 侧为 expect，各平台给 actual），
// 因此必须显式 import 才能写 Logger.DEFAULT —— 它不在 Logger 的伴生对象成员里。
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging

/**
 * 统一 HttpClient 工厂。
 *
 * 背景（v3.71.0 审计落地）：改造前全仓有 5 处各自 new 的 HttpClient ——
 * [AdapterRemoteUpdater]、[com.shangkeschedule.data.api.webdav.WebDavClient]、
 * [com.shangkeschedule.data.api.date.ApiDateImporter] 用无引擎重载，
 * `WebViewRequestInterceptor`（androidMain）则硬编码 CIO。后果是：
 * 1. 引擎选择散落在平台源集里，Android 侧一直用 CIO —— 而 Android 的推荐引擎是
 *    OkHttp（HTTP/2、连接池、系统代理/TLS/DNS 集成都走平台原生栈）；
 * 2. 超时值各写各的（15s/30s/60s 混用），改一处要翻五个文件；
 * 3. 谁装了 Logging、谁没装，只能逐个读代码才知道。
 *
 * 本工厂把这三件事收口到一处，并显式化到参数上：
 * - **引擎**：调用方不再 import 任何引擎，统一由各平台源集的依赖决定
 *   （androidMain = OkHttp / jvmMain = CIO / iosMain = Darwin）。这样引擎切换是
 *   一处 `build.gradle.kts` 的一行依赖变更，而不是散在业务代码里的 import。
 * - **超时**：默认值集中在此，单点可调；需要特例的站点用命名参数覆写。
 * - **Logging**：默认**不装**。[AdapterRemoteUpdater] 的注释写明「不安装 Ktor Logging
 *   插件，避免把鉴权请求头写进日志」，因此把 Logging 做成显式 opt-in，
 *   避免「为了统一而顺手给所有客户端都装上日志」这类反向优化。
 *
 * 注意：本工厂**不**注入默认 User-Agent。各站点对 UA 的要求不同
 * （[ApiDateImporter] 需要伪装成移动端 Chrome，WebDAV 服务端则可能校验 UA），
 * 统一强加一个值会改变既有行为，故 UA 仍由调用方在 `configure` 里自行设置。
 */
object HttpClientFactory {

    /** 默认连接超时（ms）。偏保守：局域网/校园网教务系统握手慢，10s 容易误判为不可达。 */
    const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000L

    /** 默认整请求超时（ms）。 */
    const val DEFAULT_REQUEST_TIMEOUT_MS = 30_000L

    /** 默认 socket 空闲超时（ms）。 */
    const val DEFAULT_SOCKET_TIMEOUT_MS = 30_000L

    /**
     * 创建一个统一配置的 [HttpClient]。
     *
     * @param followRedirects 是否自动跟随 3xx。默认 true；WebView 代理的主框架请求需传 false，
     *   因为 WebView 无法原生解析 3xx，必须由拦截器自己把 Location 转成 JS 跳转。
     * @param connectTimeout 连接超时（ms），传 null 表示不设限。
     * @param request 整请求超时（ms），传 null 表示不设限。
     * @param socket socket 空闲超时（ms），传 null 表示不设限。
     * @param enableLogging 是否安装 Ktor Logging 插件。默认 false —— 带鉴权头的客户端
     *   必须保持 false，否则 Authorization / Cookie 会被写进日志。
     * @param configure 站点专属配置（Auth / ContentNegotiation / defaultRequest 等），
     *   在本工厂的基线配置之后执行，因此可以覆写基线。
     */
    fun create(
        followRedirects: Boolean = true,
        connectTimeout: Long? = DEFAULT_CONNECT_TIMEOUT_MS,
        request: Long? = DEFAULT_REQUEST_TIMEOUT_MS,
        socket: Long? = DEFAULT_SOCKET_TIMEOUT_MS,
        enableLogging: Boolean = false,
        configure: HttpClientConfig<*>.() -> Unit = {}
    ): HttpClient = HttpClient {
        this.followRedirects = followRedirects

        install(HttpTimeout) {
            connectTimeoutMillis = connectTimeout
            requestTimeoutMillis = request
            socketTimeoutMillis = socket
        }

        if (enableLogging) {
            install(Logging) {
                level = LogLevel.INFO
                logger = Logger.DEFAULT
            }
        }

        configure()
    }
}
