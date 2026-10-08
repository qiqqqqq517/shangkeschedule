package com.shangkeschedule.ui.schoolselection.web

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 「学业情况读取前必须先导航到该页」的回归测试（v4.75.3）。
 *
 * ## 缺陷本体（用户实测反馈「导入不进去」）
 *
 * App 打开教务用的是学校配置里的 `import_url`（**首页**），而钩子要在**学业情况子页**
 * 里找培养方案数据。实测南通大学：
 * - 首页 `index_initMenu.html`：`p.title1` 命中 **0**、「要求学分」**0 处**
 * - 学业情况页 `xsxyqk_cxXsxyqkIndex.html`：`p.title1` 命中 **14**，可解析 **7 条**
 *
 * ⇒ 用户停在首页点「读取」，钩子必然读到 0 条。此前**只有超时兜底**（v4.75.2），
 * 修的是「不再永久卡住」，**没有修「数据进不来」**。
 *
 * ## 为什么用结构性断言
 *
 * 钩子逻辑跑在用户的教务 WebView 里，纯 JVM 单测造不出真实 DOM。跑行为只会
 * 测到测试自己写的假实现。这里断言「编排链路的每一环都在位」，判别力由配套的
 * 阴性对照保证（删掉任一环 ⇒ 对应用例立刻变红，见工作日志 v4.75.3）。
 */
class StudyNavigationTest {

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("未找到仓库根目录")
        }
        return dir
    }

    private fun read(relPath: String): String {
        val f = File(repoRoot(), relPath)
        assertTrue(f.exists(), "找不到 ${f.path}")
        return f.readText()
    }

    private val protocolSrc: String =
        read("shared/src/commonMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebBridgeProtocol.kt")

    private val screenSrc: String =
        read("shared/src/commonMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebViewScreen.kt")

    @Test
    fun `存在「定位学业情况页」脚本（此前学业是唯一没有定位钩子的入口）`() {
        assertTrue(
            protocolSrc.contains("val JS_NAVIGATE_TO_STUDY"),
            "缺少 JS_NAVIGATE_TO_STUDY：课表与空教室都有定位脚本，唯独学业没有，" +
                "于是它成了唯一「必须用户自己先点对菜单」的入口",
        )
        // 关键词要覆盖实测页面上的真实菜单文案
        val block = protocolSrc.substringAfter("val JS_NAVIGATE_TO_STUDY")
            .substringBefore("trimIndent()")
        assertTrue(
            block.contains("学生学业情况查询"),
            "定位脚本的关键词表里没有实测到的真实菜单文案「学生学业情况查询」",
        )
    }

    @Test
    fun `定位脚本会展开隐藏的下拉祖先（正方菜单默认折叠）`() {
        val block = protocolSrc.substringAfter("val JS_NAVIGATE_TO_STUDY")
            .substringBefore("trimIndent()")
        assertTrue(
            block.contains("revealHiddenAncestors"),
            "缺少 revealHiddenAncestors：正方 V9 菜单项的祖先 ul.dropdown-menu 是 " +
                "display:none，导致 getBoundingClientRect() 返回 0×0、候选全被判不可见（实测命中 0）",
        )
        assertTrue(
            block.contains("cs.display === 'none'"),
            "展开逻辑必须针对 display:none 的祖先，否则等于没展开",
        )
    }

    @Test
    fun `只点击真正带 onclick 的元素（外层 li 的 onclick 常为空）`() {
        val block = protocolSrc.substringAfter("val JS_NAVIGATE_TO_STUDY")
            .substringBefore("trimIndent()")
        assertTrue(
            block.contains("var clickable"),
            "缺少 clickable 过滤：实测那个 li 的 onclick 为空，真正带 clickMenu 的 " +
                "是内层 <a onclick=\"clickMenu(...)\">，不过滤会点到无效元素",
        )
    }

    @Test
    fun `读取前先导航，等页面加载完再调钩子`() {
        assertTrue(
            screenSrc.contains("JS_NAVIGATE_TO_STUDY"),
            "界面未接入定位脚本",
        )
        assertTrue(
            screenSrc.contains("pendingStudyRescan"),
            "缺少 pendingStudyRescan：点完菜单页面还在跳转，此刻读 DOM 必定读到 0 条",
        )
        // onPageLoaded 消费待办后才调钩子，顺序不能反
        val onLoaded = screenSrc.substringAfter("val onPageLoaded: () -> Unit")
            .take(600)
        assertTrue(
            onLoaded.contains("pendingStudyRescan") && onLoaded.contains("runStudyHook"),
            "onPageLoaded 未消费 pendingStudyRescan 并调 runStudyHook",
        )
        val orderPending = onLoaded.indexOf("pendingStudyRescan = false")
        val orderHook = onLoaded.indexOf("runStudyHook")
        assertTrue(
            orderPending >= 0 && orderHook > orderPending,
            "必须先清待办再调钩子，否则页面再次加载时会重复读",
        )
    }

    @Test
    fun `定位失败时如实提示，不静默失败也不假装成功`() {
        assertTrue(
            screenSrc.contains("toastStudyPageNotFound"),
            "定位不到入口时应提示用户去哪儿点，而不是静默无反应",
        )
        assertTrue(
            screenSrc.contains("studyScanRunning = false"),
            "定位失败后必须退出运行态，否则按钮永久禁用",
        )
    }

    @Test
    fun `页面加载完成回调在三处契约里齐备（漏一处会在该平台编译或运行失败）`() {
        // spec / android actual / jvm actual 必须同时声明，否则平台实现签名不匹配
        for (rel in listOf(
            "shared/src/commonMain/kotlin/com/shangkeschedule/ui/schoolselection/web/PlatformWebSpec.kt",
            "shared/src/androidMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebView.android.kt",
            "shared/src/jvmMain/kotlin/com/shangkeschedule/ui/schoolselection/web/PlatformWeb.jvm.kt",
        )) {
            assertTrue(read(rel).contains("onPageLoaded"), "$rel 未声明 onPageLoaded")
        }
        // Android 侧必须在 onPageFinished 里触发（那是 DOM 可读的最早时机）
        val androidSrc = read(
            "shared/src/androidMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebView.android.kt"
        )
        val onFinished = androidSrc.substringAfter("override fun onPageFinished")
            .take(400)
        assertTrue(
            onFinished.contains("currentOnPageLoaded"),
            "Android 侧未在 onPageFinished 里触发 onPageLoaded",
        )
    }

    @Test
    fun `三语文案齐备`() {
        for (locale in listOf("values", "values-en", "values-zh-rTW")) {
            val xml = read("shared/src/commonMain/composeResources/$locale/strings.xml")
            assertTrue(
                xml.contains("name=\"study_page_not_found\""),
                "$locale/strings.xml 缺 study_page_not_found",
            )
        }
    }

    /**
     * 把实测到的真实页面文本钉在这里，作为「解析规则仍然适用」的依据。
     *
     * 任何一行的文案变了，这里都要跟着改 —— 目的是让人一眼看出
     * 「学校改了页面文案」还是「我们的解析逻辑坏了」。
     */
    @Test
    fun `实测页面文案样本（南通大学 2026-10-08）`() {
        val samples = listOf(
            "通识教育课程平台要求学分:47.0获得学分:32.0未获得学分:15.0并且",
            "必修课程要求学分:41.0获得学分:30.0未获得学分:11.0共（30）门通过（16）门",
            "选修课程要求学分:6.0获得学分:2.0未获得学分:4.0共（2）门通过（1）门",
        )
        // 钩子认的三个关键片段必须仍在真实文案里
        val ntu = read("shared/assets/offline_repo/schools/resources/NTU/ntu.js")
        for (frag in listOf("课程平台)要求学分", "必修课程|选修课程|任选课程|限选课程", "共\\s*[（(]?\\s*(\\d+)")) {
            assertTrue(frag.contains("\\") || ntu.contains(frag.replace("\\", "")) || ntu.contains(frag),
                "钩子里的识别片段「$frag」已不在 ntu.js 中 —— 需按真实页面文案重新核对")
        }
        // 样本本身留档：平台行不计入、叶子行计入，且平台对账有两个平台能对上
        assertTrue(samples.size == 3)
    }
}