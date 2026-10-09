package com.shangkeschedule.ui.schoolselection

import java.io.File
import com.shangkeschedule.ui.schoolselection.web.bridgeCallAllowed
import com.shangkeschedule.ui.schoolselection.web.declaredLandingHosts
import com.shangkeschedule.ui.schoolselection.web.hostOfUrl
import com.shangkeschedule.ui.schoolselection.web.isSameSiteHost
import com.shangkeschedule.ui.schoolselection.web.registrableDomainOf
import com.shangkeschedule.ui.schoolselection.web.trustedRegistrableDomains
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

    // =========================================================================
    // v4.75.7：本轮「正方教务导入无响应」回归的钉住用例
    //
    // 回归成因：索引里大量学校登记的是**统一认证/门户**入口，登录后被服务端跳到
    // 另一个可注册域的教务系统。旧门禁只比「入口主机」，于是这类学校的回传被
    // 100% 丢弃 ⇒ 30 秒看门狗误报「适配脚本未在限定时间内启动」。
    // =========================================================================

    @Test
    fun declaredLandingHosts_解析入口声明的落地主机() {
        val entry = "https://authserver.chu.edu.cn/authserver/login" +
            "?service=https%3A%2F%2Fehall.chtc.edu.cn%2Fjzxxxt%3Fgnmkdm%3DN1"
        assertEquals(listOf("ehall.chtc.edu.cn"), declaredLandingHosts(entry))
        // 无 service 参数 ⇒ 不臆造落地主机
        assertEquals(emptyList(), declaredLandingHosts("https://cas.bjut.edu.cn/cas/login"))
        assertEquals(emptyList(), declaredLandingHosts(null))
        assertEquals(emptyList(), declaredLandingHosts(""))
    }

    @Test
    fun trustedRegistrableDomains_入口与其声明的落地域都可信() {
        val entry = "https://authserver.chu.edu.cn/authserver/login" +
            "?service=https%3A%2F%2Fehall.chtc.edu.cn%2Fx"
        assertEquals(
            setOf("chu.edu.cn", "chtc.edu.cn"),
            trustedRegistrableDomains(entry),
            "入口自身与它自己声明的落地域都必须可信，否则正常登录流程会被拦",
        )
    }

    @Test
    fun bridgeCallAllowed_统一认证入口跳到另一可注册域的教务必须放行() {
        // 阳性（回归用例）：这正是「旧版本能导入、新版不行」的那批学校。
        val entry = "https://authserver.chu.edu.cn/authserver/login" +
            "?service=https%3A%2F%2Fehall.chtc.edu.cn%2Fjzxxxt"
        assertTrue(
            bridgeCallAllowed(entry, "https://ehall.chtc.edu.cn/jzxxxt?gnmkdm=N1"),
            "学校数据自己声明的落地域被判跨站 ⇒ 该校回传会被整条丢弃，必然报「脚本未在限定时间内启动」",
        )
        // 入口自身域也仍放行（登录页上脚本可能先回传）。
        assertTrue(bridgeCallAllowed(entry, "https://authserver.chu.edu.cn/authserver/login"))
    }

    @Test
    fun bridgeCallAllowed_声明的落地域不得成为任意第三方通行证() {
        // 阴性对照：可信域**只**来自入口自己声明的 service，不是「谁都能进」。
        val entry = "https://authserver.chu.edu.cn/authserver/login" +
            "?service=https%3A%2F%2Fehall.chtc.edu.cn%2Fx"
        assertFalse(
            bridgeCallAllowed(entry, "https://evil.example.net/steal"),
            "未在入口声明里的域必须拒绝，否则门禁被绕过",
        )
        // 同 edu.cn 后缀但不同可注册域：不得因后缀相同而放行。
        assertFalse(bridgeCallAllowed(entry, "https://jw.zjnu.edu.cn/x"))
    }

    @Test
    fun bridgeCallAllowed_入口无主机时靠会话基线兜底且不钉住就拒() {
        // 索引里 import_url 为空的通用入口（如「正方教务系统（通用）」）：
        // 用户自己输地址，入口主机恒为 null。没有基线时一律拒（fail-closed，不放行）。
        assertFalse(bridgeCallAllowed("", "https://jw.example.edu.cn/xkb"))
        assertFalse(bridgeCallAllowed("about:blank", "https://jw.example.edu.cn/xkb"))
        // 调用方在首个成功加载的主框架上钉住基线后，同站放行。
        assertTrue(bridgeCallAllowed("", "https://jw.example.edu.cn/xkb", "jw.example.edu.cn"))
        // 钉住基线也不是通行证：换到别的可注册域仍必须拒。
        assertFalse(bridgeCallAllowed("", "https://evil.example.net/x", "jw.example.edu.cn"))
        // 有入口主机时，基线参数**不得**放宽判定（否则门禁形同虚设）。
        assertFalse(
            bridgeCallAllowed("https://jwgl.hbvtc.edu.cn/a", "https://evil.example.net/x", "evil.example.net"),
            "入口有主机时不能靠 pinnedHost 放行任意域",
        )
    }

    // =========================================================================
    // v4.75.7：「回传被门禁拦掉」必须与「脚本没跑起来」给出**不同**的归因
    // =========================================================================

    @Test
    fun 门禁拒绝必须走独立归因而不是静默写日志() {
        val repo = File(".").absoluteFile.let { d ->
            var dir = d
            while (!File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile ?: error("未找到仓库根")
            dir
        }
        val handler = File(
            repo,
            "shared/src/commonMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebBridgeHandler.kt",
        ).readText()
        val androidWeb = File(
            repo,
            "shared/src/androidMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebView.android.kt",
        ).readText()

        assertTrue(
            handler.contains("fun noteBridgeRejected"),
            "缺少 noteBridgeRejected：回传被门禁拒绝后只会写 logcat，用户 30 秒后收到的是「脚本未在限定时间内启动」这一错误归因",
        )
        assertTrue(
            androidWeb.contains("handler.noteBridgeRejected()"),
            "NativeBridge 拒绝分支未上报 ⇒ 上面那个归因函数永远不会被调用（守卫写了不等于生效）",
        )
        // 归因必须真的分流：超时点要按 bridgeRejected 选文案，否则仍是同一条误导性提示。
        assertTrue(
            handler.contains("Res.string.wb_import_blocked"),
            "超时归因未区分「被门禁拦」与「脚本未启动」两种故障",
        )
        assertTrue(
            handler.contains("bridgeRejected = false"),
            "新导入会话未复位 bridgeRejected ⇒ 上一次被拦会让下一次正常导入也报错文案",
        )
    }
}