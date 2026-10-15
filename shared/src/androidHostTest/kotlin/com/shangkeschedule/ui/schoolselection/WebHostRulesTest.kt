package com.shangkeschedule.ui.schoolselection

import java.io.File
import com.shangkeschedule.ui.schoolselection.web.hostOfUrl
import com.shangkeschedule.ui.schoolselection.web.isSameSiteHost
import com.shangkeschedule.ui.schoolselection.web.registrableDomainOf
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 主机解析与同站规则的单测。
 *
 * v4.75.14 按用户要求「精准回退到 v4.74.1 的导入行为」——桥接来源门禁
 * （`bridgeCallAllowed` / `declaredLandingHosts` / `trustedRegistrableDomains`）
 * 已整体移除，因此本文件只保留**仍在生效**的判定规则用例：
 * 这些函数现在唯一的消费者是 `WebViewRequestInterceptor` 的 CORS 同站白名单。
 *
 * 覆盖三类断言，避免「全为真」的退化写法：
 *  - **阳性**：同一可注册域的子域视为同站；
 *  - **阴性**：跨站、取不到主机、解析失败必须判不同站（fail-closed 的前提）；
 *  - **公共后缀陷阱**：`hbvtc.edu.cn` 与 `zjnu.edu.cn` 末两段同为 `edu.cn`，
 *    只取末两段会被误判成同站 —— 该用例钉住取末三段的规则不被改回。
 */
class WebHostRulesTest {

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("未找到仓库根目录")
        }
        return dir
    }

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
    fun isSameSiteHost_不得因实现细节变化而恒真() {
        // 判别力自证：写法不同但语义相同必须判同站；语义不同必须判不同站。
        // 若这两条同时成立，说明判定确实读了参数而非恒真。
        val urls = listOf(
            "cas.hbvtc.edu.cn" to "JWGL.HBVTC.EDU.CN",
            "jwgl.hbvtc.edu.cn" to "evil.com"
        )
        val verdicts = urls.map { (a, b) -> isSameSiteHost(a, b) }
        assertEquals(listOf(true, false), verdicts, "判定结果必须随主机变化，不能恒真")
    }

    // =========================================================================
    // v4.75.14：钉住「桥接来源门禁已按用户要求整体回退」这一事实。
    //
    // 为什么要有这条：门禁是「导入无响应」的根因，但它当初是为了堵住
    // 「导入页里的第三方内容调用原生能力」。回退后必须让后来者一眼看到
    // 这是**有意的取舍**（用户明确选择精准回退），而不是误删或漏改；
    // 同时确保残余风险（跨源 iframe 仍可调用桥接）不被当成「已修」。
    // =========================================================================
    @Test
    fun 桥接来源门禁已整体回退且不留死代码() {
        val root = repoRoot()

        // ⚠️ 必须先剔除注释再判定：回退时按项目惯例在代码旁写了说明注释，
        // 注释里**引用了被移除的函数名**（正是为了说清「为什么移除」）——
        // 不剔注释会把「已经撤干净的文件」判成仍有残留。
        // 这与项目已固化的「静态扫描要把注释排除在外」纪律一致
        //（此前 EmptyWeeksImportTest 首版也因同一原因假红）。
        fun stripKotlinComments(src: String): String =
            src.replace(Regex("""/\*[\s\S]*?\*/"""), " ")
                .replace(Regex("""(?m)^\s*//.*$"""), " ")
                .replace(Regex("""(?m)//[^\n]*$"""), " ")

        fun stripXmlComments(src: String): String =
            src.replace(Regex("""(?s)<!--.*?-->"""), " ")

        val androidWeb = stripKotlinComments(
            File(
                root,
                "shared/src/androidMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebView.android.kt",
            ).readText()
        )
        val hostRules = stripKotlinComments(
            File(
                root,
                "shared/src/androidMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebHostRules.kt",
            ).readText()
        )
        val handler = stripKotlinComments(
            File(
                root,
                "shared/src/commonMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebBridgeHandler.kt",
            ).readText()
        )

        // 门禁本体必须已移除（含其专用函数与接线）。
        assertTrue(
            !hostRules.contains("bridgeCallAllowed"),
            "WebHostRules 仍残留 bridgeCallAllowed ⇒ 回退未撤干净",
        )
        assertTrue(
            !hostRules.contains("declaredLandingHosts"),
            "WebHostRules 仍残留 declaredLandingHosts ⇒ 回退未撤干净",
        )
        assertTrue(
            !hostRules.contains("trustedRegistrableDomains"),
            "WebHostRules 仍残留 trustedRegistrableDomains ⇒ 回退未撤干净",
        )
        assertTrue(
            !androidWeb.contains("bridgeCallAllowed") && !androidWeb.contains("sessionEntryUrl"),
            "NativeBridge 仍在做来源门禁 ⇒ 回退未生效，该校回传仍会被拦",
        )
        // 归因链路也要一并撤掉，否则会留下永不触发的死代码。
        assertTrue(
            !handler.contains("noteBridgeRejected") && !handler.contains("bridgeRejected"),
            "WebBridgeHandler 仍残留「被门禁拦」的归因分支 ⇒ 死代码未清理",
        )
        // 三语文案同步移除（否则会被「未使用资源」检查或后续会话误认为仍有该功能）。
        for (locale in listOf("values", "values-en", "values-zh-rTW")) {
            val xml = stripXmlComments(
                File(root, "shared/src/commonMain/composeResources/$locale/strings.xml").readText()
            )
            assertTrue(
                !xml.contains("wb_import_blocked"),
                "$locale/strings.xml 仍残留 wb_import_blocked 文案",
            )
        }
    }

    @Test
    fun 回退后仍保留与门禁无关的两项既有修复() {
        val root = repoRoot()
        val androidWeb = File(
            root,
            "shared/src/androidMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebView.android.kt",
        ).readText()
        // P1-12：日志脱敏与门禁无关，回退时**不应**被一起撤掉（否则隐私泄露回归）。
        assertTrue(
            androidWeb.contains("bridgeActionOf(jsonMessage)"),
            "日志脱敏（只记 action + 长度）被连带回退 ⇒ 会把含课表数据/报错栈的整条消息写进 logcat",
        )
        // 直转形态：收到的消息一律交给 handler。
        assertTrue(
            androidWeb.contains("handler.onMessageReceived(jsonMessage)"),
            "桥接未直转 handler ⇒ 回退不完整",
        )
    }
}
