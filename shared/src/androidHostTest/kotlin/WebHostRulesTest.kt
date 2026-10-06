package com.shangkeschedule.ui.schoolselection

import com.shangkeschedule.ui.schoolselection.web.bridgeCallAllowed
import com.shangkeschedule.ui.schoolselection.web.hostOfUrl
import com.shangkeschedule.ui.schoolselection.web.isSameSiteHost
import com.shangkeschedule.ui.schoolselection.web.registrableDomainOf
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 桥接来源门禁与同站规则的单测（P1-11）。
 *
 * 覆盖三类断言，避免「全为真」的退化写法：
 *  - **阳性**：应放行的教务同站场景必须放行（否则是把正常导入全拦了）；
 *  - **阴性**：跨站 / 取不到主机 / 解析失败 必须拒绝（fail-closed，不得静默放行）；
 *  - **公共后缀陷阱**：`hbvtc.edu.cn` 与 `zjnu.edu.cn` 末两段同为 `edu.cn`，
 *    只取末两段会被误判成同站 —— 该用例钉住取末三段的规则不被改回。
 */
class WebHostRulesTest {

    @Test
    fun hostOfUrl_解析正常并剥掉userinfo与端口() {
        assertEquals("jwgl.hbvtc.edu.cn", hostOfUrl("https://jwgl.hbvtc.edu.cn/portal/index.jsp"))
        assertEquals("jwgl.hbvtc.edu.cn", hostOfUrl("http://jwgl.hbvtc.edu.cn:8080/a?b=1#c"))
        assertEquals("example.com", hostOfUrl("https://user:pw@example.com/x"))
    }

    @Test
    fun hostOfUrl_无法解析时返回null而非空串() {
        assertNull(hostOfUrl("about:blank"))
        assertNull(hostOfUrl(""))
        assertNull(hostOfUrl("https:///path"))
        assertNull(hostOfUrl("no-scheme"))
    }

    @Test
    fun registrableDomainOf_两段公共后缀必须取末三段() {
        // edu.cn 是公共后缀：只取末两段会把两所不同的学校判成同一站。
        assertEquals("hbvtc.edu.cn", registrableDomainOf("cas.hbvtc.edu.cn"))
        assertEquals("zjnu.edu.cn", registrableDomainOf("info.zjnu.edu.cn"))
        assertFalse(isSameSiteHost("hbvtc.edu.cn", "zjnu.edu.cn"), "两段公共后缀导致误判同站")
    }

    @Test
    fun isSameSiteHost_同一可注册域的子域视为同站() {
        assertTrue(isSameSiteHost("cas.hbvtc.edu.cn", "jwgl.hbvtc.edu.cn"))
        assertTrue(isSameSiteHost("a.b.example.com", "example.com"))
        assertFalse(isSameSiteHost("evil.com", "jwgl.hbvtc.edu.cn"))
        assertFalse(isSameSiteHost("", "jwgl.hbvtc.edu.cn"), "空主机不得视为同站")
    }

    @Test
    fun bridgeCallAllowed_教务流程内的同站跳转必须放行() {
        // 入口 CAS → 教务系统，属于同站，必须放行。
        assertTrue(bridgeCallAllowed(
            "https://cas.hbvtc.edu.cn/cas/login",
            "https://jwgl.hbvtc.edu.cn/xkb"
        ))
        // 同一主机、路径/协议/端口变化不影响放行。
        assertTrue(bridgeCallAllowed("https://jwgl.hbvtc.edu.cn/a", "http://jwgl.hbvtc.edu.cn:80/b"))
    }

    @Test
    fun bridgeCallAllowed_跨站与来源不可判定一律拒绝() {
        // 阴性对照：用户被引到第三方页面后调用桥接 —— 必须拒。
        assertFalse(bridgeCallAllowed("https://jwgl.hbvtc.edu.cn/a", "https://evil.example.net/x"))
        // 来源未知（页面尚未完成加载 / about:blank）必须拒，且不得因「解析不出」而放行。
        assertFalse(bridgeCallAllowed("https://jwgl.hbvtc.edu.cn/a", null))
        assertFalse(bridgeCallAllowed("https://jwgl.hbvtc.edu.cn/a", "about:blank"))
        // 入口未知（sessionEntryUrl 为空）同样拒绝。
        assertFalse(bridgeCallAllowed(null, "https://jwgl.hbvtc.edu.cn/a"))
        assertFalse(bridgeCallAllowed("", "https://jwgl.hbvtc.edu.cn/a"))
    }

    @Test
    fun bridgeCallAllowed_不得因实现细节变化而恒真() {
        // 判别力自证：入口/当前主机写法不同但语义相同，仍应放行；
        // 语义不同则必须拒。若这两条同时成立，说明判定确实读了参数而非恒真。
        val urls = listOf(
            "https://jwgl.hbvtc.edu.cn/a" to "https://JWGL.HBVTC.EDU.CN/b",
            "https://jwgl.hbvtc.edu.cn/a" to "https://evil.com/b"
        )
        val verdicts = urls.map { (a, b) -> bridgeCallAllowed(a, b) }
        assertEquals(listOf(true, false), verdicts, "判定结果必须随主机变化，不能恒真")
    }
}