package com.shangkeschedule.data.repository

import com.shangkeschedule.data.db.main.CurriculumCourse
import com.shangkeschedule.data.db.main.Grade
import com.shangkeschedule.data.model.CreditRequirement
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 学业情况的类别归位（v4.76.16）。
 *
 * 南通大学实测场景：成绩的 category 是教务「性质」列的**短名**（「必修」），
 * 而要求 / 课程清单用学校**长名**（「学科基础课程平台/必修」）。
 * 旧实现按 grade.category 严格分组 ⇒ 各类已获学分恒为 0，
 * 但「已修门数」走课程名匹配是对的 ⇒ 页面出现「已修 2 门 / 已获 0 学分」并存。
 *
 * 放同名包下是为了访问 internal 的 [alignGradesToCategories]。
 */
class CategoryCreditAlignmentTest {

    private fun grade(name: String, category: String) = Grade(
        id = name,
        semester = "2025-2026-2",
        courseName = name,
        credit = null,
        category = category,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun plan(name: String, category: String) = CurriculumCourse(
        id = name,
        courseName = name,
        category = category,
        suggestedTerm = null,
        source = CurriculumCourse.SOURCE_IMPORT,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun shortGradeCategory_isAlignedToSchoolLongName() {
        val requirements = listOf(
            CreditRequirement(category = "学科基础课程平台/必修", requiredCredits = 67.0, requiredCourses = 25),
            CreditRequirement(category = "通识教育课程平台/必修", requiredCredits = 35.0, requiredCourses = 21)
        )
        val curriculum = listOf(
            plan("高等数学A（一）", "学科基础课程平台/必修"),
            plan("电路", "学科基础课程平台/必修"),
            plan("军事训练", "通识教育课程平台/必修")
        )
        // 三门的 category 都是短名「必修」
        val grades = listOf(
            grade("高等数学A（一）", "必修"),
            grade("电路", "必修"),
            grade("军事训练", "必修")
        )

        val byCat = alignGradesToCategories(grades, requirements, curriculum)

        // 旧实现：三门的 key 都是「必修」，两类长名下都是 0 门
        assertEquals(
            2, byCat["学科基础课程平台/必修"]?.size ?: -1,
            "学科基础课程平台/必修 应归到 2 门（旧实现为 0）"
        )
        assertEquals(
            1, byCat["通识教育课程平台/必修"]?.size ?: -1,
            "通识教育课程平台/必修 应归到 1 门（旧实现为 0）"
        )
        assertEquals(3, byCat.values.sumOf { it.size }, "总数应仍为 3，不丢不重")
    }

    @Test
    fun knownCategory_isKeptAsIs() {
        // 成绩类别本身就是已知长名时，保持原样，不被清单改写
        val requirements = listOf(CreditRequirement(category = "通识教育课程平台/选修", requiredCredits = 6.0))
        val curriculum = listOf(plan("创新思维训练", "通识教育课程平台/选修"))
        val grades = listOf(grade("创新思维训练", "通识教育课程平台/选修"))

        val byCat = alignGradesToCategories(grades, requirements, curriculum)
        assertEquals(1, byCat["通识教育课程平台/选修"]?.size ?: -1)
    }

    @Test
    fun unknownCourse_fallsBackToRawCategory() {
        // 清单里没有这门课 → 保留原类别，不凭空归位
        val grades = listOf(grade("某门课", "选修"))
        val byCat = alignGradesToCategories(grades, emptyList(), emptyList())
        assertEquals(1, byCat["选修"]?.size ?: -1)
    }
}
