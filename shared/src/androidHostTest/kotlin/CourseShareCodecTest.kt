import com.shangkeschedule.data.codec.CourseShareCodec
import com.shangkeschedule.data.model.CourseImportExport.CourseConfigJsonModel
import com.shangkeschedule.data.model.CourseImportExport.CourseTableExportModel
import com.shangkeschedule.data.model.CourseImportExport.ExportCourseJsonModel
import com.shangkeschedule.data.model.CourseImportExport.SchemeMetaJsonModel
import com.shangkeschedule.data.model.CourseImportExport.TimeSlotJsonModel
import com.shangkeschedule.ui.share.QrCodeMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CourseShareCodec] 纯函数回归测试（D1 课表分享串）。
 *
 * 覆盖三件事：
 * 1. 往返一致性——完整模式必须保真（含课程 id / 作息 / 方案 / 配置），
 *    精简模式必须只丢教师、备注、学分、考核方式、作息与课程 id；
 * 2. 容错——聊天软件折行、前后粘上说明文字，仍能解出来；
 * 3. 体积与损坏——分享串长度可控（否则二维码放不下），改动一个字符必须判为无效。
 */
class CourseShareCodecTest {

    // ------------------------------------------------------------ 完整模式

    @Test
    fun fullModeRoundTripKeepsEveryField() {
        val model = CourseTableExportModel(
            courses = listOf(
                course(id = "id-1", name = "高等数学"),
                course(
                    id = "id-2",
                    name = "数据结构实验",
                    teacher = "李老师",
                    position = "机房 A402",
                    day = 6,
                    start = 5,
                    end = 6,
                    weeks = listOf(1, 2, 3, 7, 8, 15),
                    remark = "自带电脑",
                    assessment = "实验报告",
                    isLab = true
                )
            ),
            timeSlots = listOf(
                TimeSlotJsonModel(number = 1, startTime = "08:00", endTime = "08:45"),
                TimeSlotJsonModel(number = 2, startTime = "08:55", endTime = "09:40", alias = "第二小节", schemeId = "summer")
            ),
            config = CourseConfigJsonModel(
                semesterStartDate = "2026-09-01",
                semesterTotalWeeks = 18,
                currentSchemeId = "summer",
                autoSwitchScheme = true,
                showWeekends = true
            ),
            timeSlotSchemes = listOf(
                SchemeMetaJsonModel("default", "01-01", "06-30"),
                SchemeMetaJsonModel("summer", "07-01", "09-30")
            )
        )

        val code = CourseShareCodec.encode(model, CourseShareCodec.Mode.FULL, tableName = "2026 秋")
        assertTrue(code.startsWith(CourseShareCodec.PREFIX), "分享串必须以 SK1: 开头：$code")

        val decoded = assertIs<CourseShareCodec.DecodeResult.Success>(CourseShareCodec.decode(code))
        assertEquals("2026 秋", decoded.tableName)

        val restored = decoded.model
        assertEquals(2, restored.courses.size)

        val first = restored.courses[0]
        assertEquals("id-1", first.id)
        assertEquals("高等数学", first.name)
        assertEquals("张老师", first.teacher)
        assertEquals("教三 305", first.position)
        assertEquals(1, first.day)
        assertEquals(1, first.startSection)
        assertEquals(2, first.endSection)
        assertEquals((1..16).toList(), first.weeks)
        assertEquals("4.0", first.credit)

        val second = restored.courses[1]
        assertEquals(listOf(1, 2, 3, 7, 8, 15), second.weeks)
        assertEquals("数据结构实验", second.name)
        assertEquals("实验报告", second.assessmentMethod)
        assertTrue(second.isLab)

        val slots = restored.timeSlots.orEmpty()
        assertEquals(2, slots.size)
        assertEquals("第二小节", slots[1].alias)
        assertEquals("summer", slots[1].schemeId)
        assertEquals("08:55", slots[1].startTime)

        val config = restored.config
        assertNotNull(config)
        assertEquals("2026-09-01", config.semesterStartDate)
        assertEquals(18, config.semesterTotalWeeks)
        assertEquals("summer", config.currentSchemeId)
        assertTrue(config.autoSwitchScheme)
        assertTrue(config.showWeekends)

        assertEquals(2, restored.timeSlotSchemes.size)
        assertEquals("07-01", restored.timeSlotSchemes[1].startMonthDay)
        assertEquals("09-30", restored.timeSlotSchemes[1].endMonthDay)
    }

    /** 周次区间化：连续 16 周只占 4 个数字，但仍要能原样还原。 */
    @Test
    fun weekRangesSurviveContiguousAndSparseWeeks() {
        val model = CourseTableExportModel(
            courses = listOf(
                course(id = "a", name = "连续课", weeks = (1..16).toList()),
                course(id = "b", name = "断周课", weeks = listOf(1, 3, 5, 7, 9, 11)),
                course(id = "c", name = "单周课", weeks = listOf(8))
            ),
            timeSlots = emptyList(),
            config = CourseConfigJsonModel()
        )

        val restored = assertIs<CourseShareCodec.DecodeResult.Success>(
            CourseShareCodec.decode(CourseShareCodec.encode(model, CourseShareCodec.Mode.FULL))
        ).model

        assertEquals((1..16).toList(), restored.courses[0].weeks)
        assertEquals(listOf(1, 3, 5, 7, 9, 11), restored.courses[1].weeks)
        assertEquals(listOf(8), restored.courses[2].weeks)
    }

    // ------------------------------------------------------------ 精简模式

    @Test
    fun compactModeDropsHeavyFieldsButKeepsScheduleShape() {
        val model = CourseTableExportModel(
            courses = listOf(
                course(id = "id-1", name = "大学英语（视听说）", weeks = listOf(2, 3, 4, 9)),
                course(id = "id-2", name = "体育", day = 5, position = "田径场")
            ),
            timeSlots = listOf(TimeSlotJsonModel(number = 1, startTime = "08:00", endTime = "08:45")),
            config = CourseConfigJsonModel(semesterTotalWeeks = 20),
            timeSlotSchemes = listOf(SchemeMetaJsonModel("default", "01-01", "06-30"))
        )

        val compact = CourseShareCodec.encode(model, CourseShareCodec.Mode.COMPACT)
        val full = CourseShareCodec.encode(model, CourseShareCodec.Mode.FULL)
        assertTrue(compact.length < full.length, "精简模式应当更短：${compact.length} vs ${full.length}")

        val restored = assertIs<CourseShareCodec.DecodeResult.Success>(CourseShareCodec.decode(compact)).model
        val first = restored.courses[0]
        assertEquals("大学英语（视听说）", first.name)
        assertEquals(listOf(2, 3, 4, 9), first.weeks)
        assertEquals("教三 305", first.position)
        assertEquals(3, first.color)
        // 精简模式刻意丢掉的字段
        assertEquals("", first.teacher)
        assertNull(first.id)
        assertNull(first.credit)
        assertNull(first.assessmentMethod)
        assertNull(first.remark)
        assertTrue(restored.timeSlots.orEmpty().isEmpty())
        assertTrue(restored.timeSlotSchemes.isEmpty())
        // 配置仍保留（学期开始/总周数决定单双周与周次显示）
        assertEquals(20, restored.config?.semesterTotalWeeks)
    }

    // ------------------------------------------------------------ 体积

    /** 一条真实规模的课表（20 门 / 全周）压缩后应短到能贴进聊天软件。 */
    @Test
    fun realisticTableStaysShort() {
        val model = CourseTableExportModel(
            courses = (1..20).map { index ->
                course(
                    id = "id-$index",
                    name = "课程$index",
                    teacher = "教师${index % 5}",
                    position = "教${index % 4} ${index}0${index % 6}",
                    day = (index % 5) + 1,
                    start = (index % 6) + 1,
                    end = (index % 6) + 2,
                    weeks = (1..16).toList()
                )
            },
            timeSlots = (1..6).map {
                TimeSlotJsonModel(number = it, startTime = "0${it + 7}:00", endTime = "0${it + 7}:45")
            },
            config = CourseConfigJsonModel(semesterStartDate = "2026-09-01")
        )

        val full = CourseShareCodec.encode(model, CourseShareCodec.Mode.FULL)
        val compact = CourseShareCodec.encode(model, CourseShareCodec.Mode.COMPACT)
        assertTrue(full.length < 6_000, "完整分享串 ${full.length} 字符过长")
        assertTrue(compact.length < 4_000, "精简分享串 ${compact.length} 字符过长")
        assertEquals(20, assertIs<CourseShareCodec.DecodeResult.Success>(CourseShareCodec.decode(compact)).model.courses.size)
    }

    // ------------------------------------------------------------ 容错与损坏

    @Test
    fun decodesWhenSurroundedByProseAndLineBreaks() {
        val model = CourseTableExportModel(
            courses = listOf(course(id = "id-1", name = "高等数学")),
            timeSlots = emptyList(),
            config = CourseConfigJsonModel()
        )
        val code = CourseShareCodec.encode(model, CourseShareCodec.Mode.FULL)

        assertTrue(CourseShareCodec.looksLikeCode("我的课表：\n$code\n（长按复制）"))
        val folded = "同学你好，我的课表如下：\n" + code.chunked(48).joinToString("\n") + "\n谢谢！"
        val restored = assertIs<CourseShareCodec.DecodeResult.Success>(CourseShareCodec.decode(folded)).model
        assertEquals("高等数学", restored.courses[0].name)
    }

    @Test
    fun tamperedCodeIsRejected() {
        val model = CourseTableExportModel(
            courses = listOf(course(id = "id-1", name = "高等数学")),
            timeSlots = emptyList(),
            config = CourseConfigJsonModel()
        )
        val code = CourseShareCodec.encode(model, CourseShareCodec.Mode.FULL)

        val lastChar = code.last()
        val tampered = code.dropLast(1) + if (lastChar == 'a') 'b' else 'a'
        assertIs<CourseShareCodec.DecodeResult.Invalid>(CourseShareCodec.decode(tampered))

        val truncated = code.dropLast(6)
        assertIs<CourseShareCodec.DecodeResult.Invalid>(CourseShareCodec.decode(truncated))
    }

    @Test
    fun plainTextIsNotAShareCode() {
        assertIs<CourseShareCodec.DecodeResult.NotAShareCode>(CourseShareCodec.decode("周一 1-2 高等数学 教三 305"))
        assertTrue(!CourseShareCodec.looksLikeCode("周一 1-2 高等数学"))
    }

    // ------------------------------------------------------------ 二维码

    @Test
    fun compactCodeFitsInQrCode() {
        val model = CourseTableExportModel(
            courses = (1..16).map { index ->
                course(
                    id = "id-$index",
                    name = "课程$index",
                    teacher = "教师${index % 5}",
                    position = "教${index % 4} ${index}0${index % 6}",
                    day = (index % 5) + 1,
                    start = (index % 6) + 1,
                    end = (index % 6) + 2,
                    weeks = (1..16).toList()
                )
            },
            timeSlots = emptyList(),
            config = CourseConfigJsonModel(semesterStartDate = "2026-09-01")
        )

        val compact = CourseShareCodec.encode(model, CourseShareCodec.Mode.COMPACT)
        val matrix = assertNotNull(
            QrCodeMatrix.of(compact),
            "16 门课的精简分享串（${compact.length} 字符）必须能生成二维码"
        )
        val moduleCount = matrix.size
        assertTrue(moduleCount in 21..QrCodeMatrix.MAX_MODULES, "模块数 $moduleCount 超出可扫范围")
        assertEquals(moduleCount, matrix[0].size)
    }

    @Test
    fun oversizedCodeIsRejectedInsteadOfTruncated() {
        // 超出二维码版本 40 容量时库会返回最大版本而不是失败，必须由我们自己挡掉
        assertNull(QrCodeMatrix.of("a".repeat(4_000)))
        assertNull(QrCodeMatrix.of(""))
    }

    @Test
    fun fullCodeOfLargeTableIsNotSilentlyShrunk() {
        val model = CourseTableExportModel(
            courses = (1..16).map { index ->
                course(id = "id-$index", name = "课程$index", weeks = (1..16).toList())
            },
            timeSlots = (1..6).map {
                TimeSlotJsonModel(number = it, startTime = "0${it + 7}:00", endTime = "0${it + 7}:45")
            },
            config = CourseConfigJsonModel(semesterStartDate = "2026-09-01")
        )

        val full = CourseShareCodec.encode(model, CourseShareCodec.Mode.FULL)
        // 完整串通常超出单张二维码容量，此时界面必须退到精简串（见 CourseShareScreen）
        if (full.length > 2_953) {
            assertNull(QrCodeMatrix.of(full), "完整分享串 ${full.length} 字符不应被塞进二维码")
        }
    }

    // ------------------------------------------------------------ 夹具

    private fun course(
        id: String,
        name: String,
        teacher: String = "张老师",
        position: String = "教三 305",
        day: Int = 1,
        start: Int? = 1,
        end: Int? = 2,
        weeks: List<Int> = (1..16).toList(),
        remark: String? = "高数A",
        credit: String? = "4.0",
        assessment: String? = "考试",
        isLab: Boolean = false
    ) = ExportCourseJsonModel(
        id = id,
        name = name,
        teacher = teacher,
        position = position,
        day = day,
        startSection = start,
        endSection = end,
        color = 3,
        weeks = weeks,
        isCustomTime = false,
        customStartTime = null,
        customEndTime = null,
        remark = remark,
        credit = credit,
        assessmentMethod = assessment,
        isLab = isLab
    )
}
