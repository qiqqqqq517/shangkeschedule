package com.shangkeschedule.ui.schoolselection.web

/**
 * 主机名 / 同站判定的**唯一实现**（供拦截器的 CORS 同站白名单使用）。
 *
 * 历史（v4.75.14 按用户要求精准回退）：本文件原为「拦截器 + 桥接来源门禁」共用。
 * 桥接侧的门禁（`bridgeCallAllowed` 及配套的 `declaredLandingHosts` /
 * `trustedRegistrableDomains`）已随本次回退**整体移除** —— 它会把
 * 「统一认证入口 → 另一可注册域教务」这类本校正常流程的回传整条拦掉，
 * 症状是「导入无响应」。判定逻辑因此只剩拦截器一个消费者，保留本文件是因为
 * `WebViewRequestInterceptor` 的同站判定仍需要它（且已有单测钉住公共后缀规则）。
 */

/**
 * 两段式公共后缀（近似，非完整 PSL）。
 *
 * 不能简单取末两段标签：`hbvtc.edu.cn` 与 `zjnu.edu.cn` 的末两段都是 `edu.cn`
 * （公共后缀），会被误判成同站，使跨站保护形同虚设。故对已知的两段公共后缀取末三段。
 */
internal val MULTI_PART_SUFFIXES = setOf(
    "edu.cn", "com.cn", "net.cn", "org.cn", "gov.cn", "ac.cn", "mil.cn",
    "edu.hk", "com.hk", "org.hk", "gov.hk",
    "edu.tw", "com.tw", "org.tw", "gov.tw",
    "edu.mo", "com.mo", "org.mo"
)

/**
 * 从 URL 中取出主机名（小写、去掉 userinfo 与端口）。
 *
 * 入参允许为 `null`：桥接门禁要判定的两个来源（会话入口 URL、当前主框架 URL）
 * 都可能取不到 —— 那属于**必须拒绝**的情形，而不是「当作空串继续」，
 * 故返回 `null` 交由调用方按 fail-closed 处理。
 */
internal fun hostOfUrl(url: String?): String? {
    if (url == null) return null
    val start = url.indexOf("://")
    if (start < 0) return null
    val rest = url.substring(start + 3)
    val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val authority = if (end < 0) rest else rest.substring(0, end)
    val host = authority.substringAfter('@').substringBefore(':').lowercase()
    return host.ifBlank { null }
}

/**
 * 取可注册域（近似 eTLD+1）。见 [MULTI_PART_SUFFIXES] 的说明。
 */
internal fun registrableDomainOf(host: String): String {
    val h = host.lowercase().trim('.')
    if (h.isEmpty()) return ""
    val parts = h.split('.')
    if (parts.size <= 2) return h
    val last2 = parts.takeLast(2).joinToString(".")
    return if (last2 in MULTI_PART_SUFFIXES) parts.takeLast(3).joinToString(".") else last2
}

/**
 * 是否为同一站点（比较可注册域）。
 * 例：`cas.hbvtc.edu.cn` 与 `jwgl.hbvtc.edu.cn` → 同站；`hbvtc.edu.cn` 与 `zjnu.edu.cn` → 不同站。
 */
internal fun isSameSiteHost(a: String, b: String): Boolean {
    val ra = registrableDomainOf(a)
    val rb = registrableDomainOf(b)
    return ra.isNotEmpty() && ra == rb
}
