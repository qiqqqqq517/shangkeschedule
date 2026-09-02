import com.shangkeschedule.data.parser.UniversalScheduleParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IcsParseTest {

    @Test
    fun wakeupIcsParse() {
        val content = java.io.File("D:/qqwenjian/日历-江西中医药大学.ics").readText()
        val result = UniversalScheduleParser.parseAuto(content)
        assertTrue(result is UniversalScheduleParser.ParseResult.Success, "expected success but got: $result")
        val success = result as UniversalScheduleParser.ParseResult.Success
        assertEquals("ICS日历", success.format)
        val courses = success.model.courses
        assertEquals(26, courses.size, "课程条数")

        // 形势与政策Ⅶ：周一 1-2 节，周4-5，阳明楼0106，文林
        val xszc = courses.filter { it.name == "形势与政策Ⅶ" }
        assertEquals(1, xszc.size)
        val c0 = xszc.first()
        assertEquals(1, c0.day)
        assertEquals(1, c0.startSection)
        assertEquals(2, c0.endSection)
        assertEquals(listOf(4, 5), c0.weeks)
        assertEquals("阳明楼0106", c0.position)
        assertEquals("文林", c0.teacher)

        // 温病学：至少有一条周一 3-5 节，周1-5，教师含转义还原
        val wbx = courses.filter { it.name == "温病学" }
        assertTrue(wbx.isNotEmpty())
        val wbxFirst = wbx.first { it.startSection == 3 && it.endSection == 5 }
        assertEquals(1, wbxFirst.day)
        assertEquals(listOf(1, 2, 3, 4, 5), wbxFirst.weeks)
        assertEquals("阳明楼0406", wbxFirst.position)
        assertEquals("贾冬冬,王伶改", wbxFirst.teacher)  // \, 转义还原

        // 外科学：存在周一 6-9 节
        val wkx = courses.filter { it.name == "外科学" }
        assertTrue(wkx.any { it.day == 1 && it.startSection == 6 && it.endSection == 9 })

        // 临床技能实训Ⅰ 出现 5 次（多地点/多时段拆分）
        val lcjn = courses.filter { it.name == "临床技能实训Ⅰ" }
        assertEquals(5, lcjn.size)

        // 所有课程名均非空白
        assertTrue(courses.all { it.name.isNotBlank() })
    }

    @Test
    fun oldCountStyleIcsStillWorks() {
        // 旧格式：COUNT=16 每周固定课程，week 从1开始
        val oldIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:高等数学
            DTSTART:20250901T080000
            DTEND:20250901T094000
            RRULE:FREQ=WEEKLY;COUNT=16;INTERVAL=1
            LOCATION:教学楼101
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:大学英语
            DTSTART:20250903T101000
            DTEND:20250903T115000
            RRULE:FREQ=WEEKLY;COUNT=16;INTERVAL=1
            LOCATION:教学楼202 李老师
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val result = UniversalScheduleParser.parseAuto(oldIcs)
        assertTrue(result is UniversalScheduleParser.ParseResult.Success, "expected success but got: $result")
        val success = result as UniversalScheduleParser.ParseResult.Success
        val courses = success.model.courses
        assertEquals(2, courses.size)
        // 高等数学：周一，8:00 -> 1-2 节，16周
        val math = courses.first { it.name == "高等数学" }
        assertEquals(1, math.day)
        assertEquals(1, math.startSection)
        assertEquals(2, math.endSection)
        assertEquals((1..16).toList(), math.weeks)
        // 大学英语：周三（2025-09-03），10:10 -> 默认55分钟连续作息下第3节起、11:50 止于第5节，周1-16
        val eng = courses.first { it.name == "大学英语" }
        assertEquals(3, eng.day)
        assertEquals(3, eng.startSection)
        assertEquals(5, eng.endSection)
        assertEquals((1..16).toList(), eng.weeks)
        // LOCATION 拆出教师（最后一段）
        assertEquals("教学楼202", eng.position)
        assertEquals("李老师", eng.teacher)
    }
}
