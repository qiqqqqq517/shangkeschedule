import com.shangkeschedule.data.parser.UniversalScheduleParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 无编号-4 / P0-3：解析层必须产出**可本地化错误码**，而不是只给中文文案。
 *
 * 这是「英文 / 繁体用户看到简体中文」这一缺陷的**根因侧**回归：
 * 只要码在，UI 层就能解析成当前语言；码丢了就会静默退回中文。
 */
class ParserErrorCodeTest {

    private fun errorOf(content: String): UniversalScheduleParser.ParseResult.Error {
        val r = UniversalScheduleParser.parseAuto(content)
        assertTrue(r is UniversalScheduleParser.ParseResult.Error, "期望 Error，实际 = $r")
        return r as UniversalScheduleParser.ParseResult.Error
    }

    @Test
    fun emptyContentCarriesCode() {
        assertEquals(
            UniversalScheduleParser.ParseErrorCode.EMPTY_CONTENT,
            errorOf("   ").code,
        )
    }

    @Test
    fun unrecognizedContentCarriesCode() {
        val code = errorOf("这不是任何已知格式的课表内容 zzz").code
        assertTrue(code != null, "必须带错误码，不得只给中文文案")
    }

    @Test
    fun csvHeaderOnlyCarriesCode() {
        // 有表头但无课程行 —— 命中 CSV 分支的「未找到有效课程」
        val code = errorOf("课程名,教师,教室,星期,节次,周次").code
        assertTrue(code != null, "必须带错误码，实际 = $code")
    }

    @Test
    fun successCarriesFormatCode() {
        val ics = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "BEGIN:VEVENT\n" +
            "SUMMARY:高等数学\n" +
            "DTSTART:20250901T080000\n" +
            "DTEND:20250901T094000\n" +
            "RRULE:FREQ=WEEKLY;COUNT=4;INTERVAL=1\n" +
            "LOCATION:教学楼101\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n"
        val r = UniversalScheduleParser.parseAuto(ics)
        assertTrue(r is UniversalScheduleParser.ParseResult.Success, "期望 Success，实际 = $r")
        assertEquals(
            UniversalScheduleParser.ParseFormatCode.ICS_CALENDAR,
            (r as UniversalScheduleParser.ParseResult.Success).formatCode,
        )
    }
}
