package com.shangkeschedule.data.repository

import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.model.CourseImportExport.CourseConfigJsonModel
import com.shangkeschedule.data.repository.resolveImportedAutoSwitch
import com.shangkeschedule.data.repository.resolveImportedSchemeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 教务导入课表配置不得静默重置用户的作息方案。
 *
 * 缺陷背景：`insertOrUpdateCourseConfig` 是整行 REPLACE，未列出的字段回落为实体默认值。
 * `importCourseConfig` 此前只列了 6 个字段，漏掉 currentSchemeId / autoSwitchScheme，
 * 于是**每导入一次课表配置，用户的作息方案就被无声重置为 default、冬夏自动切换被无声关掉**。
 */
class ImportCourseConfigSchemeTest {

    /** 用户已设好作息：夏令时方案 + 自动切换开启。 */
    private val existing = CourseTableConfig(
        courseTableId = "T1",
        showWeekends = true,
        semesterTotalWeeks = 20,
        currentSchemeId = "summer",
        autoSwitchScheme = true
    )

    /** 教务端下发的配置：不含作息字段，落到默认值。 */
    private val fromJiaowu = CourseConfigJsonModel(
        semesterStartDate = "2026-09-01",
        semesterTotalWeeks = 20,
        defaultClassDuration = 45,
        defaultBreakDuration = 10,
        firstDayOfWeek = 1
    )

    // ---- 阴性用例：导入不得破坏用户既有作息 ----

    @Test
    fun 教务导入_不得把作息方案重置为default() {
        val r = resolveForTest(fromJiaowu, existing)
        assertEquals("summer", r.currentSchemeId, "作息方案被导入重置了")
        assertTrue(r.autoSwitchScheme, "冬夏自动切换被导入关掉了")
    }

    @Test
    fun 教务导入_不得改动用户已开启的自动切换() {
        // 教务端恒给 autoSwitchScheme=false，若直接采纳就会把用户设置关掉
        assertFalse(fromJiaowu.autoSwitchScheme, "夹具前提：教务端配置不携带该字段")
        val r = resolveForTest(fromJiaowu, existing)
        assertTrue(r.autoSwitchScheme, "夹具前提不成立，本用例失去意义")
    }

    // ---- 阳性用例：v2 备份确实带了非默认值时必须采纳 ----

    @Test
    fun v2备份_显式带来的作息方案应当被采纳() {
        val backup = fromJiaowu.copy(currentSchemeId = "winter")
        val r = resolveForTest(backup, existing)
        assertEquals("winter", r.currentSchemeId, "备份里明确的作息方案未被采纳")
    }

    @Test
    fun v2备份_显式开启的自动切换应当被采纳() {
        val existingOff = existing.copy(autoSwitchScheme = false)
        val backup = fromJiaowu.copy(autoSwitchScheme = true)
        val r = resolveForTest(backup, existingOff)
        assertTrue(r.autoSwitchScheme, "备份里明确开启的自动切换未被采纳")
    }

    // ---- 边界：无既有配置时回落到默认值，不得崩 ----

    @Test
    fun 无既有配置时回落到默认作息() {
        val r = resolveForTest(fromJiaowu, null)
        assertEquals(TimeSlot.DEFAULT_SCHEME_ID, r.currentSchemeId)
        assertFalse(r.autoSwitchScheme)
    }

    @Test
    fun 空白作息ID视为未指定并保留用户设置() {
        val blank = fromJiaowu.copy(currentSchemeId = "   ")
        val r = resolveForTest(blank, existing)
        assertEquals("summer", r.currentSchemeId, "空白值不应把用户的作息清空")
    }

    /**
     * 判别力自证：同一份输入下，作息相关字段必须真的被求值。
     * 若实现退化成「恒返回 default / 恒返回 false」，上面所有断言都会失败 ——
     * 本用例额外确认「非默认输入确实被读到了」，防止探针整体失效仍报 PASS。
     */
    @Test
    fun 判别力自证_实现不得恒真() {
        // 两个只差 currentSchemeId 的输入，必须得到不同结果
        val a = resolveForTest(fromJiaowu.copy(currentSchemeId = "default"), existing)
        val b = resolveForTest(fromJiaowu.copy(currentSchemeId = "winter"), existing)
        assertTrue(
            a.currentSchemeId != b.currentSchemeId,
            "实现未读取 currentSchemeId，判据已退化"
        )
        // 两个只差 autoSwitchScheme 的输入，也必须得到不同结果
        val c = resolveForTest(fromJiaowu.copy(autoSwitchScheme = false), existing.copy(autoSwitchScheme = false))
        val d = resolveForTest(fromJiaowu.copy(autoSwitchScheme = true), existing.copy(autoSwitchScheme = false))
        assertTrue(
            c.autoSwitchScheme != d.autoSwitchScheme,
            "实现未读取 autoSwitchScheme，判据已退化"
        )
    }

    // ---- 夹具桥：直接调用被测的顶层函数，避免复制粘贴导致判据与实现漂移 ----

    private fun resolveForTest(
        model: CourseConfigJsonModel,
        current: CourseTableConfig?
    ): CourseTableConfig {
        // 复刻 importCourseConfig 里与作息相关的两行求值
        return CourseTableConfig(
            courseTableId = "T1",
            currentSchemeId = resolveImportedSchemeId(model.currentSchemeId, current),
            autoSwitchScheme = resolveImportedAutoSwitch(model.autoSwitchScheme, current)
        )
    }
}
