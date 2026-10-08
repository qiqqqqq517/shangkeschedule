import com.shangkeschedule.data.parser.ExcelScheduleParser
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.FileSystem

/**
 * N14 回归：xlsx **合并单元格**的回填。
 *
 * xlsx 里合并区域（`<mergeCells>`）只有左上格带值，其余格在 sheet XML 中根本不出现。
 * 课表里「同一门课多个课次纵向合并课程名」是常见写法；不回填就会变成
 * 第一行有课名、其余行课名为空 ⇒ 解析器丢课或把课次错配到别的课程。
 *
 * 本测试不依赖任何外部文件，现场构造最小 xlsx（ZIP + sheet1.xml），
 * 断言：① 合并区域内各格都拿到左上格的值；② 本来就有值的格子不被覆盖。
 */
class ExcelMergedCellTest {

    @Test
    fun `合并区域内的空格回填左上格的值`() {
        // A2:A4 纵向合并（课程名跨 3 个课次）
        val sheet = """
        <?xml version="1.0" encoding="UTF-8"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <sheetData>
        <row r="1"><c r="A1" t="inlineStr"><is><t>课程名</t></is></c><c r="B1" t="inlineStr"><is><t>时间</t></is></c></row>
        <row r="2"><c r="A2" t="inlineStr"><is><t>高等数学</t></is></c><c r="B2" t="inlineStr"><is><t>周一1-2</t></is></c></row>
        <row r="3"><c r="B3" t="inlineStr"><is><t>周三1-2</t></is></c></row>
        <row r="4"><c r="B4" t="inlineStr"><is><t>周五1-2</t></is></c></row>
        </sheetData>
        <mergeCells count="1"><mergeCell ref="A2:A4"/></mergeCells>
        </worksheet>
        """.trimIndent()

        val grid = extractGrid(sheet)
        assertEquals("高等数学", grid[1][0], "A2 应保留原值")
        assertEquals("高等数学", grid[2][0], "A3 应由合并区域回填")
        assertEquals("高等数学", grid[3][0], "A4 应由合并区域回填")
        assertEquals("周三1-2", grid[2][1], "B3 原值不得被覆盖")
        assertEquals("周五1-2", grid[3][1], "B4 原值不得被覆盖")
    }

    @Test
    fun `合并区域内已有值的格子不被覆盖`() {
        // 异常文件：合并区域内 B2:B3 本应有值，这里刻意让 B3 也带值
        val sheet = """
        <?xml version="1.0" encoding="UTF-8"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <sheetData>
        <row r="2"><c r="B2" t="inlineStr"><is><t>锚点</t></is></c></row>
        <row r="3"><c r="B3" t="inlineStr"><is><t>自有值</t></is></c></row>
        </sheetData>
        <mergeCells count="1"><mergeCell ref="B2:B3"/></mergeCells>
        </worksheet>
        """.trimIndent()

        val grid = extractGrid(sheet)
        assertEquals("锚点", grid[1][1], "B2 为锚点")
        assertEquals("自有值", grid[2][1], "B3 已有值，不得被锚点覆盖")
    }

    @Test
    fun `无合并声明时行为不变（阴性对照）`() {
        val sheet = """
        <?xml version="1.0" encoding="UTF-8"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <sheetData>
        <row r="2"><c r="A2" t="inlineStr"><is><t>只有一格</t></is></c><c r="B2" t="inlineStr"><is><t>x</t></is></c></row>
        </sheetData>
        </worksheet>
        """.trimIndent()

        val grid = extractGrid(sheet)
        assertEquals("只有一格", grid[1][0])
        assertEquals("x", grid[1][1])
    }

    private fun extractGrid(sheetXml: String): List<List<String>> {
        val bytes = buildXlsx(sheetXml)
        assertTrue(ExcelScheduleParser.isZipBytes(bytes), "构造物必须是合法 ZIP")
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / ("excel_merge_test_" + Random.nextLong(100000, 999999))
        return try {
            ExcelScheduleParser.extractGrid(bytes, dir)
        } finally {
            runCatching { FileSystem.SYSTEM.deleteRecursively(dir) }
        }
    }

    /** 构造最小可用 xlsx：必须含 [Content_Types].xml 之外的 sheet1.xml；sharedStrings 供解析器可选读取。 */
    private fun buildXlsx(sheetXml: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="xml" ContentType="application/xml"/>
            </Types>
            """.trimIndent())
            put("xl/workbook.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets>
            </workbook>
            """.trimIndent())
            put("xl/sharedStrings.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="0" uniqueCount="0"/>
            """.trimIndent())
            put("xl/worksheets/sheet1.xml", sheetXml)
        }
        return out.toByteArray()
    }
}
