package com.shangkeschedule.ui.settings.import

import com.shangkeschedule.data.model.CourseImportExport
import com.shangkeschedule.data.model.CourseImportExport.CourseConfigJsonModel
import com.shangkeschedule.data.model.CourseImportExport.CourseTableImportModel
import com.shangkeschedule.data.model.CourseImportExport.ImportCourseJsonModel
import com.shangkeschedule.data.model.CourseImportExport.SchemeMetaJsonModel
import com.shangkeschedule.data.model.CourseImportExport.TimeSlotJsonModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 导入体系整合（v4.80.0）后的解析口径回归。
 *
 * 背景：原先「JSON 文件导入」是一条独立链路，它用 `CourseTableImportModel` **严格解码**，
 * 因此能保留 `timeSlots` / `config` / `timeSlotSchemes`；其余入口走
 * `UniversalScheduleParser.parseAuto`，`parseWakeUpJson` 只取 `courses` 数组。
 *
 * 合并为统一导入页后，如果只是简单地把所有内容都丢给自动嗅探，本 App 导出的 JSON
 * 会**静默丢掉作息与学期配置** —— 用户看到的仍是「导入成功」，但作息回到默认。
 * 因此 [TextImportViewModel] 的解析顺序规定：`SK1:` 分享串 → 严格 JSON → 自动嗅探。
 *
 * 这里守护的是顺序本身能表达的行为（纯解析层，不依赖 Android/Compose）。
 */
class StrictJsonImportPriorityTest {

    private val course = ImportCourseJsonModel(
        id = "c1",
        name = "高等数学",
        teacher = "张三",
        position = "教5-103",
        day = 1,
        startSection = 1,
        endSection = 2,
        weeks = listOf(1, 2, 3),
    )

    private fun exportedJson(): String = CourseImportExport.json.encodeToString(
        CourseTableImportModel.serializer(),
        CourseTableImportModel(
            courses = listOf(course),
            timeSlots = listOf(
                TimeSlotJsonModel(number = 1, startTime = "08:00", endTime = "08:45"),
                TimeSlotJsonModel(number = 2, startTime = "08:55", endTime = "09:40"),
            ),
            config = CourseConfigJsonModel(
                semesterStartDate = "2026-09-01",
                semesterTotalWeeks = 18,
            ),
            timeSlotSchemes = listOf(SchemeMetaJsonModel(schemeId = "default")),
        ),
    )

    /**
     * 严格解码保留的是「完整模型」；通用嗅探只保留课程与作息，**丢失学期配置与课程 id**。
     * 两者对同一份导出 JSON 的差异，正是必须让严格分支先跑的理由。
     */
    @Test
    fun strictDecodeKeepsTimeSlotsAndConfigWhileGenericParserDropsThem() {
        val json = exportedJson()

        val strictModel = CourseImportExport.json.decodeFromString<CourseTableImportModel>(json)
        assertEquals(2, strictModel.timeSlots?.size)
        assertEquals(18, strictModel.config?.semesterTotalWeeks)
        assertEquals(1, strictModel.timeSlotSchemes.size)

        val generic = com.shangkeschedule.data.parser.UniversalScheduleParser.parseAuto(json)
        assertTrue(
            generic is com.shangkeschedule.data.parser.UniversalScheduleParser.ParseResult.Success,
            "通用嗅探应能解析该 JSON，实际 = $generic",
        )
        val genericModel = generic.model
        assertEquals(1, genericModel.courses.size)
        // 通用链 `parseWakeUpJson` 会保留 timeSlots，但**丢掉学期配置与作息方案元信息**，
        // 且不保留课程 id —— 这正是不能让它先跑（否则「导入成功」却把作息/学期配置重置）。
        assertNull(genericModel.config, "通用嗅探不应保留学期配置")
        assertTrue(
            genericModel.timeSlotSchemes.isEmpty(),
            "通用嗅探不应保留作息方案元信息，实际 = ${genericModel.timeSlotSchemes}",
        )
        assertNull(genericModel.courses.first().id, "通用嗅探不应保留课程 id")
        assertNotNull(strictModel.courses.first().id, "严格解码应保留课程 id")
    }

    /** WakeUp JSON（只有 courses 数组）不得被严格分支吞掉：它必须继续能解析。 */
    @Test
    fun wakeUpJsonWithoutConfigStillParses() {
        val wakeUp = """{"courses":[{"name":"大学物理","teacher":"李四","position":"B201",""" +
            """"day":2,"startSection":3,"endSection":4,"weeks":[1,2]}]}"""
        val strictAttempt = runCatching {
            CourseImportExport.json.decodeFromString<CourseTableImportModel>(wakeUp)
        }
        // 缺 timeSlots/config 时仍能解码（二者有默认值），课程必须被保留
        assertTrue(strictAttempt.isSuccess, "WakeUp JSON 也应能被完整模型解码：$strictAttempt")
        assertEquals("大学物理", strictAttempt.getOrThrow().courses.first().name)

        val generic = com.shangkeschedule.data.parser.UniversalScheduleParser.parseAuto(wakeUp)
        assertTrue(
            generic is com.shangkeschedule.data.parser.UniversalScheduleParser.ParseResult.Success,
            "自动嗅探也应能解析 WakeUp JSON",
        )
    }

    /** 非 JSON 文本不能被严格分支误判为「解析失败」，必须交回通用嗅探。 */
    @Test
    fun nonJsonTextIsLeftToGenericSniffing() {
        val plain = "高等数学 张三 教5-103 周一 1-2节 1-16周"
        val strictAttempt = runCatching {
            CourseImportExport.json.decodeFromString<CourseTableImportModel>(plain)
        }
        assertTrue(strictAttempt.isFailure, "纯文本不应被当作导出 JSON 解码成功")

        val generic = com.shangkeschedule.data.parser.UniversalScheduleParser.parseAuto(plain)
        assertTrue(
            generic is com.shangkeschedule.data.parser.UniversalScheduleParser.ParseResult.Success,
            "纯文本应由通用嗅探接管，实际 = $generic",
        )
        assertNotNull(generic.model.courses.firstOrNull())
    }
}
