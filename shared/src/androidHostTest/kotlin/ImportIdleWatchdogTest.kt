import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 「点击执行导入后一直没有反应」的**结构性回归测试**（v4.75.13）。
 *
 * ## 缺陷成因（实测取证见 `build_qa/regress/run_watchdog_ab.js`）
 *
 * `WebBridgeHandler.onMessageReceived` 原先对**任意**一条桥接消息都调用
 * `cancelImportWatchdog()` —— 看门狗被**永久取消**，理由是「脚本已证明存活」。
 * 这条推理对教务适配脚本不成立：绝大多数脚本在**注入顶层**就会先发一条与导入
 * 无关的提示（正方实测为「检测到正方教务系统，点击导入按钮抓取课表」）。于是：
 *   1. 脚本注入 → 立刻发提示 → 看门狗被永久取消；
 *   2. 用户点「执行导入」→ 页面表格解析不出课程 → 回落接口抓取；
 *   3. 教务接口假死（会话过期 / WebVPN 挂起）⇒ 再无任何消息；
 *   4. 看门狗已不在 ⇒ 既无结果也无超时 ⇒ 界面**永远**停在「正在执行导入脚本...」。
 *
 * 夹具实测（4 个场景）：A 正常成功 2 条消息、B「表格解析不出 + 接口假死」**仅 1 条**
 * （即那条无关提示）之后彻底静默、C 接口返回空有反馈、D 零消息会正常超时 ——
 * 只有 B 命中「一直没反应」，与用户描述一致。
 *
 * ## 为什么是结构性断言
 *
 * 看门狗依赖 Compose `remember` / `coroutineScope` / WebView 实例，纯 JVM 单测里构造
 * 不出真实的 `WebBridgeHandler`。这里断言「兜底所需的几件事同时在位」，判别力由
 * 配套阴性对照保证（把 `resetImportWatchdog()` 改回 `cancelImportWatchdog()` ⇒ 必红）。
 */
class ImportIdleWatchdogTest {

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("未找到仓库根目录")
        }
        return dir
    }

    private val handlerSrc: String = File(
        repoRoot(),
        "shared/src/commonMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebBridgeHandler.kt",
    ).readText()

    /**
     * 取 `needle` 之后**括号配平**的那一段（含起始行）。
     *
     * 纪律：结构断言禁止用固定字符窗口或 `substringBefore("private fun")` 这类
     * **按下一个无关标记截断**的写法 —— 后者会把后面合法的调用点一起圈进来
     * （本测试首版即因此假红：`notifyTaskCompletion()` 内部合法调用了
     * `cancelImportWatchdog()`，被误判成「onMessageReceived 仍在永久取消看门狗」）。
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

    @Test
    fun `收到桥接消息必须重新计时而不是永久取消看门狗`() {
        val recv = balancedBlockAfter(handlerSrc, "fun onMessageReceived(")
        assertTrue(recv.isNotEmpty(), "未定位到 onMessageReceived 函数体")
        assertTrue(
            recv.contains("resetImportWatchdog()"),
            "onMessageReceived 未重新计时 ⇒ 脚本注入期那条无关提示会永久废掉看门狗，" +
                "之后脚本卡在接口假死上时界面永远停在「正在执行导入脚本...」：\n$recv",
        )
        assertTrue(
            !recv.contains("cancelImportWatchdog()"),
            "onMessageReceived 仍在永久取消看门狗 ⇒ 上面那条回归未修复：\n$recv",
        )
    }

    @Test
    fun `空闲超时必须能重新武装且只在运行态生效`() {
        val body = balancedBlockAfter(handlerSrc, "private fun resetImportWatchdog()")
        assertTrue(body.isNotEmpty(), "未定位到 resetImportWatchdog 函数体")
        assertTrue(
            body.contains("armImportWatchdog()"),
            "resetImportWatchdog 未重新武装计时器 ⇒ 只重置不重启，等于没改：\n$body",
        )
        // 已结束的会话不该被重新武装（否则失败/成功后还会再弹一次超时）。
        assertTrue(
            body.contains("ImportRunState.Running"),
            "未限定运行态 ⇒ 已成功的导入会被重新计时并在 30 秒后误报失败：\n$body",
        )
    }

    @Test
    fun `超时点不得覆盖已出的结果`() {
        val body = balancedBlockAfter(handlerSrc, "private fun armImportWatchdog()")
        assertTrue(body.isNotEmpty(), "未定位到 armImportWatchdog 函数体")
        assertTrue(
            body.contains("if (importState !is ImportRunState.Running) return@launch"),
            "超时回调未判运行态 ⇒ 空闲超时会在结果已落地后把状态覆盖成 Failed：\n$body",
        )
    }

    @Test
    fun `超时时长常量仍为 idle 语义且取值合理`() {
        val m = Regex("""IMPORT_IDLE_TIMEOUT_MS\s*=\s*([0-9_]+)L""").find(handlerSrc)
        assertTrue(m != null, "未找到 IMPORT_IDLE_TIMEOUT_MS 常量")
        val ms = m!!.groupValues[1].replace("_", "").toLong()
        assertTrue(ms in 10_000..120_000, "空闲超时时长不合理：${ms}ms（应为 10s~120s）")
    }
}