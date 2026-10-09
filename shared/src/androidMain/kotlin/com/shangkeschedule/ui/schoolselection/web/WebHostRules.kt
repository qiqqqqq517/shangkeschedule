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
 * 入口 URL 里**显式声明的落地主机**。
 *
 * 为什么需要它（v4.75.7，本轮「正方教务导入无响应」回归的修复）：
 * 大量学校在索引里登记的是**统一认证 / 门户**入口，形如
 * `https://authserver.chu.edu.cn/authserver/login?service=https%3A%2F%2Fehall.chtc.edu.cn%2F...`。
 * 用户登录后会被服务端跳到 `service=` 指定的**另一个可注册域**（`chu.edu.cn` → `chtc.edu.cn`）。
 * 只看「入口主机」的话，这类学校的回传会被 100% 丢弃 —— 而这是学校数据自己声明的正常流程。
 *
 * 安全边界：只信任入口 URL **自己写着**的落地主机，不接受任意第三方；
 * 且仍按可注册域比较，不引入通配。
 */
internal fun declaredLandingHosts(entryUrl: String?): List<String> {
    if (entryUrl.isNullOrBlank()) return emptyList()
    val out = LinkedHashSet<String>()
    val re = Regex(
        """(?:service|redirect_uri|redirecturl|redirect|target|reurl|url)=([^&#]+)""",
        RegexOption.IGNORE_CASE
    )
    for (m in re.findAll(entryUrl)) {
        val raw = runCatching {
            java.net.URLDecoder.decode(m.groupValues[1], "UTF-8")
        }.getOrDefault(m.groupValues[1])
        val host = hostOfUrl(raw) ?: hostOfUrl("https://" + raw.trimStart('/'))
        if (host != null) out.add(host)
    }
    return out.toList()
}

/** 本次导入会话可信的**可注册域**集合：入口自身 + 入口声明的落地主机。 */
internal fun trustedRegistrableDomains(entryUrl: String?): Set<String> {
    val out = LinkedHashSet<String>()
    hostOfUrl(entryUrl)?.let { out.add(registrableDomainOf(it)) }
    for (h in declaredLandingHosts(entryUrl)) out.add(registrableDomainOf(h))
    return out.filter { it.isNotEmpty() }.toSet()
}

/**
 * 桥接调用是否放行（**fail-closed**）。
 *
 * 判据：调用发生时的**主框架主机**必须落在本次会话的可信域集合内 ——
 * 即「入口主机」或「入口自己声明的落地主机」的同可注册域。
 *
 * 为什么用「同站」而不是「相等」：教务流程正常会在同一可注册域内跳转
 * （`jwgl.hbvtc.edu.cn` → `cas.hbvtc.edu.cn` → 门户），要求完全相等会把
 * 正常导入全部拦掉。
 *
 * @param pinnedHost 入口 URL **取不到主机**时（索引里 `import_url` 为空 / 无协议，
 *   典型是「正方教务系统（通用）」这类通用入口，用户自己输地址）的会话基线：
 *   由调用方在**首次成功加载的主框架**上钉住。此后同一会话内跨域仍拒绝。
 *   没有它，这类入口会因「基线恒为空」而 100% 拒绝 —— 通用入口覆盖所有未收录学校，
 *   这正是「所有正方教务导入都失败」的成因之一。
 *
 * **不变量（改动本函数必须重新评估）**：
 *  - 当前主框架主机取不到（null / 空 / 解析失败）⇒ **拒绝**，不得放行；
 *  - 可信域集合为空且未钉住基线 ⇒ **拒绝**（不得因为「没基准」就默认放行）；
 *  - 只用「主框架」主机判定。
 *
 * **已知残余缺口（诚实标注，勿当作已修）**：`addJavascriptInterface` 对
 * **所有 frame** 暴露桥接，而 `WebView.url` 只反映**主框架**。因此当教务页内
 * 嵌入了**跨源 iframe** 时，该 iframe 仍可调用桥接（此时主框架是同站的）。
 * 彻底关闭需要迁移到 `WebViewCompat.addWebMessageListener`（其
 * `allowedOriginRules` 由框架按**调用方 origin** 强制执行）—— 该迁移会改动
 * 197 个 OTA 分发的适配脚本，需与应用侧协商兼容窗口，故单列待裁决。
 */
internal fun bridgeCallAllowed(
    sessionEntryUrl: String?,
    currentMainFrameUrl: String?,
    pinnedHost: String? = null
): Boolean {
    val currentHost = currentMainFrameUrl?.let(::hostOfUrl) ?: return false
    val trusted = trustedRegistrableDomains(sessionEntryUrl)
    if (trusted.isNotEmpty()) return registrableDomainOf(currentHost) in trusted
    // 入口没有可用主机：只能用调用方钉住的会话基线（未钉住 ⇒ 拒绝，不放行）
    val pin = pinnedHost ?: return false
    return isSameSiteHost(pin, currentHost)
}
