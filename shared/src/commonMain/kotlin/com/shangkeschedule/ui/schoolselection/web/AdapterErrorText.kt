package com.shangkeschedule.ui.schoolselection.web

/**
 * 适配脚本错误文案归一（v4.81.1）。
 *
 * ## 背景
 *
 * WebView 内 `fetch` / `XMLHttpRequest` 的网络层失败只抛**原生英文消息**
 * （`TypeError: Failed to fetch`、`NetworkError when attempting to fetch resource.`）。
 * 2026-10-28 全量静态普查：199 个适配脚本里 **123 个的 fetch 链全文没有 `.catch`**、
 * **38 个的 XHR 没有 `onerror`**、**16 处原样 `throw error`** —— 这些脚本一旦遇到断网、
 * 代理拦截、教务域名校外不可达，异常就一路冒泡到 [WebBridgeHandler.reportAdapterError]，
 * 用户看到的便是「适配脚本执行出错：Failed to fetch」。
 * 2026-10-27 常州信息职院报障原文即此。
 *
 * ## 为什么在 Native 侧归一而不是逐个改脚本
 *
 * 195+ 个脚本（含 OTA 热更新下发的新版本）逐个补 catch 既不现实、也会随下发漂移；
 * 而上报点是**唯一必经通道**，在这里归一可一处覆盖全部脚本，且与项目既有纪律一致
 * （落库侧自己守空周次、门禁侧自己守基线——不依赖脚本自觉）。
 *
 * ## 判定边界
 *
 * - 只命中「Chromium 原生网络错误」的英文特征串；
 * - **含中日韩字符的消息一律原样保留**：脚本自己给出的中文提示（如「当前学期没有课表」）
 *   不能被吞掉，否则用户失去可操作信息；
 * - 非网络类的英文（如 `HTTP 500`）也不替换，保持原文便于排查。
 */
internal val NATIVE_NETWORK_ERROR_MARKERS: List<String> = listOf(
    "failed to fetch",
    "networkerror when attempting",
    "network error",
    "load failed",
    "network request failed",
    "net::err_",
    "err_name_not_resolved",
    "err_connection_",
    "err_internet_disconnected",
    "err_address_unreachable",
    "err_network_changed",
    "err_tunnel_connection_failed",
    "err_ssl_",
    "ns_error_",
    "quic_",
)

private val CJK_PATTERN = Regex("[\\u3040-\\u30ff\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff]")

/**
 * 该错误消息是否只是浏览器原生网络错误（无任何用户可操作信息）。
 *
 * @param raw 适配脚本上报的原始 message
 */
internal fun isNativeNetworkErrorText(raw: String): Boolean {
    val text = raw.trim()
    if (text.isEmpty()) return false
    if (CJK_PATTERN.containsMatchIn(text)) return false
    val lowered = text.lowercase()
    return NATIVE_NETWORK_ERROR_MARKERS.any { lowered.contains(it) }
}
