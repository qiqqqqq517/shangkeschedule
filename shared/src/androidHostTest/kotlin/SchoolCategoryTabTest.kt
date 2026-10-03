package com.shangkeschedule.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import school_index.AdapterCategory

/**
 * 选校页分类口径：本科/专科与研究生合并为「教务系统」（v4.70.0）。
 *
 * ## 这个测试在钉死什么
 *
 * 三件事都属于「不写测试就会静默出错」的类型 —— 错了不会编译报错，只会让用户看到错误的结果：
 *
 * 1. **合并口径本身**：`ACADEMIC_SYSTEM` 必须同时命中本科/专科与研究生。这是本次需求的全部内容，
 *    退化成只命中一个也能照常编译，症状只是「某一类学校在列表里凭空消失」。
 * 2. **旧导航参数回退**：v4.70.0 之前 `AdapterSelection` 的参数存的是 `AdapterCategory` 数值
 *    （2 = 本科/专科、3 = 研究生）；已保存的返回栈里可能仍是 3，`fromNumber` 必须回退到
 *    「教务系统」而不是越界崩溃或落到「通用工具」。
 * 3. **DataStore 键名**：合并后「教务系统」沿用原本科/专科槽位。键名是**用户历史数据的主键**，
 *    改一个字就会让升级后的「最近访问」整体丢失，而编译器、静态门禁都不看这个。
 */
class SchoolCategoryTabTest {

    @Test
    fun `教务系统口径同时覆盖本科专科与研究生`() {
        val tab = SchoolCategoryTab.ACADEMIC_SYSTEM

        assertTrue(
            "本科/专科必须落在合并后的「教务系统」里",
            tab.matches(AdapterCategory.BACHELOR_AND_ASSOCIATE),
        )
        assertTrue(
            "研究生必须落在合并后的「教务系统」里（本次需求的核心）",
            tab.matches(AdapterCategory.POSTGRADUATE),
        )
        assertFalse(
            "通用工具不属于教务系统",
            tab.matches(AdapterCategory.GENERAL_TOOL),
        )
    }

    @Test
    fun `通用工具口径不含任何教务类别`() {
        val tab = SchoolCategoryTab.GENERAL_TOOL

        assertTrue(tab.matches(AdapterCategory.GENERAL_TOOL))
        assertFalse(tab.matches(AdapterCategory.BACHELOR_AND_ASSOCIATE))
        assertFalse(tab.matches(AdapterCategory.POSTGRADUATE))
    }

    @Test
    fun `分类胶囊只有教务系统与通用工具两个口径`() {
        assertEquals(
            "合并后胶囊应为 2 段（教务系统 / 通用工具）",
            listOf(SchoolCategoryTab.ACADEMIC_SYSTEM, SchoolCategoryTab.GENERAL_TOOL),
            SchoolCategoryTab.entries.toList(),
        )
    }

    @Test
    fun `旧版导航参数 2 与 3 都回退到教务系统`() {
        // 新口径的 ordinal
        assertEquals(SchoolCategoryTab.ACADEMIC_SYSTEM, SchoolCategoryTab.fromNumber(0))
        assertEquals(SchoolCategoryTab.GENERAL_TOOL, SchoolCategoryTab.fromNumber(1))

        // 旧口径的 AdapterCategory 数值：2 = 本科/专科、3 = 研究生（均已越界）
        assertEquals(SchoolCategoryTab.ACADEMIC_SYSTEM, SchoolCategoryTab.fromNumber(2))
        assertEquals(SchoolCategoryTab.ACADEMIC_SYSTEM, SchoolCategoryTab.fromNumber(3))

        // 任何越界值都不抛异常
        assertEquals(SchoolCategoryTab.ACADEMIC_SYSTEM, SchoolCategoryTab.fromNumber(-1))
        assertEquals(SchoolCategoryTab.ACADEMIC_SYSTEM, SchoolCategoryTab.fromNumber(99))
    }

    @Test
    fun `教务系统的最近访问优先本科槽位并回退旧研究生槽位`() {
        val bachelor = CategoryLastSchool(id = "u_bachelor", name = "本科学校", resourceFolder = "BK")
        val postgrad = CategoryLastSchool(id = "u_postgrad", name = "研究生学校", resourceFolder = "YJS")

        // 本科槽位有记录 → 用它（合并后的新记录都写这里）
        assertEquals(
            bachelor,
            SchoolHistoryModel(bachelor = bachelor, postgraduate = postgrad).academic,
        )

        // 升级前最后一次选的是研究生学校 → 回退到旧研究生槽位，不能让「最近访问」凭空消失
        assertEquals(
            postgrad,
            SchoolHistoryModel(postgraduate = postgrad).academic,
        )

        // 两个槽位都为空 → 空记录（列表不渲染「最近访问」区）
        assertTrue(SchoolHistoryModel().academic.isEmpty)
    }

    @Test
    fun `教务系统沿用原本科存储键且清理时带上旧研究生键`() {
        val academicKeys = SchoolHistoryModel.getPrimaryKeys(SchoolCategoryTab.ACADEMIC_SYSTEM)
        assertEquals("last_school_bachelor_id", academicKeys.id.name)
        assertEquals("last_school_bachelor_name", academicKeys.name.name)
        assertEquals("last_school_bachelor_folder", academicKeys.folder.name)

        val generalKeys = SchoolHistoryModel.getPrimaryKeys(SchoolCategoryTab.GENERAL_TOOL)
        assertEquals("last_school_general_id", generalKeys.id.name)
        assertEquals("last_school_general_name", generalKeys.name.name)
        assertEquals("last_school_general_folder", generalKeys.folder.name)

        // 清空「教务系统」的最近访问时，必须连旧研究生槽位一起清，
        // 否则旧记录会从 academic 的回退分支里再冒出来。
        val legacyKeys = SchoolHistoryModel.getLegacyKeys(SchoolCategoryTab.ACADEMIC_SYSTEM)
        assertEquals(
            listOf("last_school_postgrad_id", "last_school_postgrad_name", "last_school_postgrad_folder"),
            legacyKeys.flatMap { listOf(it.id.name, it.name.name, it.folder.name) },
        )

        assertTrue(
            "通用工具没有遗留槽位",
            SchoolHistoryModel.getLegacyKeys(SchoolCategoryTab.GENERAL_TOOL).isEmpty(),
        )
    }
}
