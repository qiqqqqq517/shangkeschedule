import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 适配扫描超时看门狗的**结构性回归测试**（v4.75.2）。
 *
 * ## 这个缺陷是怎么来的
 *
 * 看门狗（`WebBridgeHandler.importWatchdogJob`）只在 `setImportTableId(非 null)`
 * 时启动，而那条路径只用于**课表导入**。成绩 / 空教室 / 学业三个钩子走的是
 * `beginAdapterScan()`，两者互不相干 —— 于是这三个扫描**一个超时兜底都没有**。
 * 钩子不回传时（页面没加载完 / 没登录 / 脚本注入失败 / 页面结构变了），
 * 界面永久停在「正在读取…」、按钮一直禁用，用户既等不到结果也等不到报错。
 * 用户实测反馈即：「导入学业情况长时间不成功」。
 *
 * ## 为什么是结构性断言而不是跑行为
 *
 * 看门狗依赖 Compose `remember` / `coroutineScope` / WebView 实例，纯 JVM 单测里
 * 构造不出真实的 `WebBridgeHandler`，硬造一个只会测到测试自己写的假实现。
 * 这里退一步断言「兜底所需的几件事是否同时在位」，判据全部指向真实代码行；
 * 其判别力由配套的阴性对照保证（删掉看门狗 ⇒ 本文件必红，见工作日志 v4.75.2 记录）。
 */
class AdapterScanWatchdogTest {

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("未找到仓库根目录")
        }
        return dir
    }

    /**
     * 取 `needle` 之后**括号配平**的那一段（含起始行）。
     *
     * 纪律：结构断言禁止用固定字符窗口（`take(N)`）—— 被测代码补注释即改变窗口内的
     * 内容，会让「实现正确」被误判为失败（本项目已多次踩到）。配平扫描对注释长度免疫。
     */
    private fun balancedBlockAfter(src: String, needle: String): String {
        val start = src.indexOf(needle)
        if (start < 0) return ""
        var depth = 0
        var i = start
        var seen = false
        while (i < src.length) {
            when (src[i]) {
                '{' -> { depth++; seen = true }
                '}' -> {
                    depth--
                    if (seen && depth == 0) return src.substring(start, i + 1)
                }
            }
            i++
        }
        return src.substring(start)
    }

    private fun read(relPath: String): String {
        val f = File(repoRoot(), relPath)
        assertTrue(f.exists(), "找不到 ${f.path}")
        return f.readText()
    }

    private val handlerSrc: String =
        read("shared/src/commonMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebBridgeHandler.kt")

    private val screenSrc: String =
        read("shared/src/commonMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebViewScreen.kt")

    @Test
    fun `扫描看门狗由 beginAdapterScan 启动（而不是只挂在课表导入上）`() {
        val beginBody = handlerSrc.substringAfter("fun beginAdapterScan(")
            .substringBefore("\n    }")
        assertTrue(
            beginBody.contains("armAdapterScanWatchdog"),
            "beginAdapterScan 未启动看门狗 ⇒ 扫描无响应时界面会永久卡在「正在读取…」：\n$beginBody",
        )
    }

    @Test
    fun `超时时长有明确常量且在合理区间`() {
        val m = Regex("""ADAPTER_SCAN_TIMEOUT_MS\s*=\s*([0-9_]+)L""").find(handlerSrc)
        assertTrue(m != null, "未找到 ADAPTER_SCAN_TIMEOUT_MS 常量")
        val ms = m!!.groupValues[1].replace("_", "").toLong()
        assertTrue(ms in 5_000..60_000, "超时时长不合理：${ms}ms（应为 5s~60s）")
    }

    @Test
    fun `投递成功与钩子报错两条路径都会取消看门狗`() {
        val delivered = handlerSrc.substringAfter("AdapterScanActions.STUDY -> parsePayload")
            .take(400)
        assertTrue(
            delivered.contains("cancelAdapterScanWatchdog"),
            "回传成功未取消看门狗 ⇒ 正常完成后仍会在超时点误报一次失败：\n$delivered",
        )

        // R40-10 / R52-01 更新：判据锚点改到稳定的 `val handledByScan` 赋值本身，
        // 且**不再用固定字符窗口取块** —— 原实现 `take(500)` 在本轮为解释 R40-10 归属判据
        // 补上大段注释后直接把 `cancelAdapterScanWatchdog` 截在窗口之外，
        // 造成「实现正确、测试假红」。改为按大括号配平取整个 if 块。
        val failedBlock = balancedBlockAfter(handlerSrc, "if (handledByScan) {")
        assertTrue(
            failedBlock.contains("cancelAdapterScanWatchdog"),
            "钩子报错未取消看门狗 ⇒ 会二次回调失败路径：\n$failedBlock",
        )
    }

    @Test
    fun `超时走独立回调而不是复用失败回调`() {
        assertTrue(
            handlerSrc.contains("onAdapterScanTimeout"),
            "缺少 onAdapterScanTimeout：超时与「读了但为空」是两种用户动作，不能共用提示",
        )
        assertTrue(
            screenSrc.contains("onAdapterScanTimeout"),
            "界面未接入 onAdapterScanTimeout ⇒ 超时后仍显示「未读取到培养方案学分要求」",
        )
        assertTrue(
            screenSrc.contains("toastStudyTimeout"),
            "超时提示未使用 study_scan_timeout 文案",
        )
    }

    @Test
    fun `超时后必须退出运行态（否则按钮永久禁用）`() {
        val timeoutHandler = screenSrc.substringAfter("onAdapterScanTimeoutState")
            .take(600)
        assertTrue(
            timeoutHandler.contains("studyScanRunning = false"),
            "超时后未把 studyScanRunning 置回 false ⇒「正在读取…」与按钮禁用会一直留着",
        )
    }

    @Test
    fun `三语文案齐备（缺一档会在该语言下崩或回落错误文案）`() {
        for (locale in listOf("values", "values-en", "values-zh-rTW")) {
            val xml = read("shared/src/commonMain/composeResources/$locale/strings.xml")
            assertTrue(
                xml.contains("name=\"study_scan_timeout\""),
                "$locale/strings.xml 缺 study_scan_timeout",
            )
        }
    }
}