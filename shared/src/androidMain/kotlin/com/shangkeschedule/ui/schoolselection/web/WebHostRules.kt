package com.shangkeschedule.ui.schoolselection.web

/**
 * 主机名 / 同站判定的**唯一实现**（拦截器与桥接门禁共用）。
 *
 * 抽取原因（P1-11）：桥接侧此前**没有任何来源校验**，而它的注释却宣称
 * 「风险由拦截器 CORS 同站白名单与桥接来源校验两项对冲」—— 实际只有前者存在。
 * 现在桥接门禁也要做同站判定，**必须与拦截器用同一套规则**：
 * 否则两处对「同站」的理解一旦漂移，就会出现「拦截器放行、门禁拒绝」这类
 * 难以定位的偏差；更危险的是反向（门禁按更宽的口径放行）。
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

/**
 * 桥接调用是否放行（**fail-closed**）。
 *
 * 判据：调用发生时的**主框架主机**必须与本次导入会话的**入口主机同站**。
 *
 * 为什么用「同站」而不是「相等」：教务流程正常会在同一可注册域内跳转
 * （`jwgl.hbvtc.edu.cn` → `cas.hbvtc.edu.cn` → 门户），要求完全相等会把
 * 正常导入全部拦掉。
 *
 * **不变量（改动本函数必须重新评估）**：
 *  - 任一侧主机取不到（null / 空 / 解析失败）⇒ **拒绝**，不得放行；
 *  - 只用「主框架」主机判定。
 *
 * **已知残余缺口（诚实标注，勿当作已修）**：`addJavascriptInterface` 对
 * **所有 frame** 暴露桥接，而 `WebView.url` 只反映**主框架**。因此当教务页内
 * 嵌入了**跨源 iframe** 时，该 iframe 仍可调用桥接（此时主框架是同站的）。
 * 彻底关闭需要迁移到 `WebViewCompat.addWebMessageListener`（其
 * `allowedOriginRules` 由框架按**调用方 origin** 强制执行）—— 该迁移会改动
 * 197 个 OTA 分发的适配脚本，需与应用侧协商兼容窗口，故单列待裁决。
 */
internal fun bridgeCallAllowed(sessionEntryUrl: String?, currentMainFrameUrl: String?): Boolean {
    val entryHost = sessionEntryUrl?.let(::hostOfUrl) ?: return false
    val currentHost = currentMainFrameUrl?.let(::hostOfUrl) ?: return false
    return isSameSiteHost(entryHost, currentHost)
}
