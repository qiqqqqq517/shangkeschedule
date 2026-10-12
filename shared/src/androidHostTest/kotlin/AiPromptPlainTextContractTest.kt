import com.shangkeschedule.data.api.AI_IMPORT_SYSTEM_PROMPT
import com.shangkeschedule.data.model.CourseImportExport.ImportCourseJsonModel
import com.shangkeschedule.data.parser.TextImportFormat
import com.shangkeschedule.data.parser.UniversalScheduleParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v4.78.0 回归：固定提示词（AI 图片导入 / AI 识别导入共用）与纯文本解析器之间的契约。
 *
 * 新导入方式「图片 + 固定提示词 → 用户自己的 AI → 粘回解析导入」整条链路只有这一处
 * 需要单测守护：提示词若与 `TextImportFormat.PLAIN` 的字段约定（`课程名 教师 教室
 * 星期 节次 周次`）脱节，模型输出得再漂亮也解析不出来，而这类退化在真机上表现为
 * 「解析预览报无法识别格式」，无法归因。
 *
 * 覆盖三条：
 * 1. 提示词自身必含解析器认得的令牌（周一 / 1-2节 / 1-16周）与字段顺序声明；
 * 2. 提示词里的示例行必须能被 PLAIN 解析器完整还原（字段、星期、节次、周次）；
 * 3. 用户从自己 AI 那边复制回来的典型形态（外套 Markdown 代码围栏 + 解释行 + 空行）
 *    必须照常解析 —— 强制 PLAIN 路径下无关行被跳过，不应报错。
 */
class AiPromptPlainTextContractTest {

    private fun parsePlain(text: String): List<ImportCourseJsonModel> {
        val result = UniversalScheduleParser.parseWithFormat(text, TextImportFormat.PLAIN)
        assertTrue(result is UniversalScheduleParser.ParseResult.Success, "解析应成功，实际：$result")
        return result.model.courses
    }

    @Test
    fun `提示词包含解析器认得的令牌与字段顺序`() {
        // 字段顺序声明（解析器按 名/师/室/星期/节次/周次 取列）
        assertTrue(
            AI_IMPORT_SYSTEM_PROMPT.contains("课程名 教师 教室 星期 节次 周次"),
            "提示词必须写出字段顺序：$AI_IMPORT_SYSTEM_PROMPT",
        )
        // 星期令牌：parseDay 认「周一」「星期一」
        assertTrue(AI_IMPORT_SYSTEM_PROMPT.contains("周一"), "提示词必须要求输出「周一」这类星期令牌")
        // 节次令牌：parseSectionRange 靠数字区间
        assertTrue(AI_IMPORT_SYSTEM_PROMPT.contains("1-2节"), "提示词必须给出节次区间示例")
        // 周次令牌：parseWeeksSimple 认「1-16周」「3周」「1-8,10-16周」
        assertTrue(AI_IMPORT_SYSTEM_PROMPT.contains("1-16周"), "提示词必须给出周次区间示例")
        assertTrue(AI_IMPORT_SYSTEM_PROMPT.contains("1-8,10-16周"), "提示词必须覆盖不连续周次写法")
    }

    @Test
    fun `提示词示例行可被原样解析`() {
        val example = "高等数学 张三 教5-103 周一 1-2节 1-16周"
        assertTrue(AI_IMPORT_SYSTEM_PROMPT.contains(example), "提示词应自带一行示例：$example")

        val courses = parsePlain(example)
        assertEquals(1, courses.size)
        val course = courses.first()
        assertEquals("高等数学", course.name)
        assertEquals("张三", course.teacher)
        assertEquals("教5-103", course.position)
        assertEquals(1, course.day)
        assertEquals(1, course.startSection)
        assertEquals(2, course.endSection)
        assertEquals((1..16).toList(), course.weeks)
    }

    @Test
    fun `AI 输出带代码围栏与解释行时照常解析`() {
        // 典型乱象：外套 ``` 围栏、开头一句解释、中间空行、末尾一句收尾。
        // 提示词已要求「不要 Markdown 代码块」，但用户自己的 AI 未必听话；
        // 强制 PLAIN 路径必须靠「无关行解析不出就跳过」消化这些噪音。
        val aiOutput = """
            好的，以下是整理后的课表：

            ```
            高等数学 张三 教5-103 周一 1-2节 1-16周
            线性代数 李四 教3-201 周三 3-4节 1-16周
            大学英语 王五 待定 周五 5-6节 1-8,10-16周
            ```

            如需调整格式请告诉我。
        """.trimIndent()

        val courses = parsePlain(aiOutput)
        assertEquals(3, courses.size, "围栏与解释行应被跳过，实际：$courses")
        assertEquals(listOf("高等数学", "线性代数", "大学英语"), courses.map { it.name })
        assertEquals(3, courses[1].day)   // 周三
        assertEquals(5, courses[2].startSection)
        assertEquals((1..8).toList() + (10..16).toList(), courses[2].weeks)
    }

    @Test
    fun `单周与单双周写法都能解析`() {
        val courses = parsePlain(
            """
            体育 赵六 体育馆 周二 7-8节 3周
            物理实验 孙七 实验楼B 周四 9-10节 1-16(单)
            形势与政策 周八 教1-101 周一 11-12节 2-16(双)
            """.trimIndent()
        )
        assertEquals(3, courses.size)
        assertEquals(listOf(3), courses[0].weeks)
        assertEquals((1..15 step 2).toList(), courses[1].weeks)
        assertEquals((2..16 step 2).toList(), courses[2].weeks)
    }

    @Test
    fun `繁体「週一」不会被静默丢弃`() {
        // zh-rTW 用户的课表与 AI 回复常用繁体；此前只认简体「周一」，
        // 这些行会被 mapNotNull 静默丢掉 —— 预览里直接少课且无任何提示。
        val courses = parsePlain(
            """
            高等數學 張三 教5-103 週一 1-2節 1-16週
            線性代數 李四 教3-201 週三 3-4節 1-16週
            """.trimIndent()
        )
        assertEquals(2, courses.size, "繁体星期必须能解析，实际：$courses")
        assertEquals(1, courses[0].day)
        assertEquals(3, courses[1].day)
    }
}
