import com.shangkeschedule.data.parser.TextImportFormat
import com.shangkeschedule.data.parser.UniversalScheduleParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v4.64.23 回归：单双周标记「1-16(单) / 2-16(双)」在 CSV / 非 ◇ 单元格等
 * 走 parseWeeksSimple 的导入路径上必须做奇偶过滤；此前只有 ◇ 分隔路径过滤，
 * 这些路径会把单周课静默当成全周（预览页看上去完全正常、没有任何提示）。
 */
class UniversalScheduleParserOddEvenTest {

    private val header = "课程,教师,教室,星期,节次,周次"

    private fun weeksOfCsv(csv: String): List<Int> {
        val result = UniversalScheduleParser.parseWithFormat(csv, TextImportFormat.CSV)
        assertTrue(result is UniversalScheduleParser.ParseResult.Success, "解析应成功，实际：$result")
        val model = result.model
        assertEquals(1, model.courses.size, "应解析出 1 门课，实际：${model.courses}")
        return model.courses.first().weeks
    }

    @Test
    fun `CSV 单周 1-16(单) 只保留奇数周`() {
        assertEquals((1..15 step 2).toList(), weeksOfCsv("$header\n高等数学,张三,教5-101,周一,1-2节,1-16(单)"))
    }

    @Test
    fun `CSV 双周 2-16(双) 只保留偶数周`() {
        assertEquals((2..16 step 2).toList(), weeksOfCsv("$header\n大学英语,李四,教3-201,周二,3-4节,2-16(双)"))
    }

    @Test
    fun `CSV 无单双标记时保持全周`() {
        assertEquals((1..16).toList(), weeksOfCsv("$header\n高等数学,张三,教5-101,周一,1-2节,1-16"))
    }

    @Test
    fun `CSV 全角括号与波浪号同样生效`() {
        assertEquals((1..15 step 2).toList(), weeksOfCsv("$header\n高等数学,张三,教5-101,周一,1-2节,1~16（单）"))
    }

    @Test
    fun `网格 ◇ 单元格单双周过滤保持一致`() {
        val grid = listOf(
            listOf("节次", "星期一", "星期二"),
            listOf("第一节", "高等数学◇1-16(单)◇教5-101◇张三", "大学英语◇2-16(双)◇教3-201◇李四"),
        )
        val result = UniversalScheduleParser.parseExcelGrid(grid)
        assertTrue(result is UniversalScheduleParser.ParseResult.Success, "解析应成功，实际：$result")
        val byName = result.model.courses.associateBy { it.name }
        assertEquals((1..15 step 2).toList(), byName.getValue("高等数学").weeks)
        assertEquals((2..16 step 2).toList(), byName.getValue("大学英语").weeks)
    }
}
