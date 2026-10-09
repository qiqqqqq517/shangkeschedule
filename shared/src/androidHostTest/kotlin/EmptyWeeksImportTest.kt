import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 「导入不成功」的**结构性回归测试**（v4.75.14）。
 *
 * ## 缺陷成因（实测取证见 `build_qa/regress/run_weeks_empty_ab.js`）
 *
 * 课表 UI 的显示判据是 `weeks.any { it.weekNumber == currentWeek }`
 * （`WeeklyScheduleViewModel.kt:1114`）。因此**周次为空的课程永远不显示**。
 * 而适配脚本里存在一个**空操作**：
 * ```js
 * var weeks = parseWeeks(full);
 * if (weeks.length === 0) weeks = [];   // 空数组赋给空数组 —— 什么都没做
 * ```
 * 于是「页面没给出周次」的课程照样被 push 并落库，用户看到「导入成功 N 门课」
 * 却在课表上找不到那几门 —— 表现为「导入不成功」。
 *
 * 夹具实测：单元格含 `1-16周` → 落库 weeks=[1..16]、可见；单元格**不含周次**
 * → 落库 weeks=[]、**任何一周都不显示**。全仓仅 2 处该写法（zhengfang.js / hbeu.js），
 * 且同文件的**接口路径**本来就正确地丢弃了空周次课程 —— 两条路径口径不一致。
 *
 * ## 为什么是结构性断言
 *
 * 真跑需要 WebView/DOM/数据库，纯 JVM 单测里构造不出。这里断言「修复所需的几件事
 * 同时在位」，判别力由阴性对照保证（把 `continue` 改回 `weeks = []` ⇒ 必红）。
 */
class EmptyWeeksImportTest {

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("未找到仓库根目录")
        }
        return dir
    }

    private fun read(rel: String): String = File(repoRoot(), rel).readText()

    private val adapterDir = "shared/assets/offline_repo/schools/resources"

    @Test
    fun `适配脚本不得再用空操作把空周次放行落库`() {
        // 判据：不得再出现 `if (weeks.length === 0) weeks = [];` 这一空操作写法。
        //
        // ⚠️ 必须**先剔除注释**再匹配：修复时按项目惯例在代码旁写了说明注释，
        // 注释里引用了旧写法本身 —— 不剔注释会把「已经改好的文件」判成仍在违规
        //（实测首版即因此假红：hbeu.js / zhengfang.js 被误报）。
        // 这与项目已固化的「静态扫描要把注释排除在外」纪律一致。
        fun stripComments(src: String): String =
            src.replace(Regex("""/\*[\s\S]*?\*/"""), " ")
                .replace(Regex("""(?m)^\s*//.*$"""), " ")

        val offenders = mutableListOf<String>()
        File(repoRoot(), adapterDir).walkTopDown()
            .filter { it.isFile && it.extension == "js" }
            .forEach { f ->
                val code = stripComments(f.readText())
                Regex("""if\s*\(\s*weeks\.length\s*===?\s*0\s*\)\s*weeks\s*=\s*\[\s*\]\s*;""")
                    .findAll(code)
                    .forEach { offenders += f.relativeTo(File(repoRoot(), adapterDir)).path }
            }
        assertTrue(
            offenders.isEmpty(),
            "仍有适配脚本把「周次为空」写成空操作 ⇒ 这些课程会落库但永远不显示（用户看到「导入成功」却找不到课）：$offenders",
        )
    }

    @Test
    fun `受影响的两个脚本改为丢弃并计数提示`() {
        for (rel in listOf("zhengfang/zhengfang.js", "hubei_engineering/hbeu.js")) {
            val src = read("$adapterDir/$rel")
            assertTrue(
                src.contains("skippedNoWeeks"),
                "$rel 未对被丢弃的无周次课程计数 ⇒ 用户不知道「少的那几门」为什么没进来（静默丢）",
            )
            assertTrue(
                Regex("""if\s*\(\s*weeks\.length\s*===?\s*0\s*\)\s*\{""").containsMatchIn(src),
                "$rel 未把「周次为空」改为丢弃分支（应 continue，而不是放行落库）",
            )
        }
    }

    @Test
    fun `落库侧必须自己守住空周次而不是依赖脚本自觉`() {
        val repo = read("shared/src/commonMain/kotlin/com/shangkeschedule/data/repository/CourseConversionRepository.kt")
        val body = repo.substringAfter("suspend fun importCoursesFromList(")
            .substringBefore("suspend fun importCourseTableFromJson(")
        assertTrue(
            body.contains("it.weeks.isNotEmpty()"),
            "落库侧未过滤空周次课程 ⇒ 存量旧版适配脚本（OTA 分发）仍会写进永远不显示的课程：\n$body",
        )
        // 全部为空时不得把课表清空（否则用户原有课表被删）。
        assertTrue(
            body.contains("已保留原有课表"),
            "过滤后全空时未拒绝导入 ⇒ 会把用户原有课表清空：\n$body",
        )
    }

    @Test
    fun `课表显示判据确实以周次为准（钉住本缺陷的前提）`() {
        val vm = read("shared/src/commonMain/kotlin/com/shangkeschedule/ui/schedule/WeeklyScheduleViewModel.kt")
        assertTrue(
            vm.contains("it.weekNumber == currentWeek"),
            "课表显示判据不再是「按周次匹配」⇒ 本缺陷的前提已变，需重新评估上面几条判据是否仍成立",
        )
    }
}