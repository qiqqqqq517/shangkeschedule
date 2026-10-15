package com.shangkeschedule.ui.schedule.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CourseNameLineBudget]：课程名行数预算。
 *
 * 锁两件事：
 * 1. **钳制生效**：再大的格子也不超过 [MAX_LINES]（否则地点/教师被顶出可视区）；
 * 2. **下限生效**：再窄的格子也至少给 [MIN_LINES]（否则「大学物理（一）」被截成「大学物…」），
 *    且异常输入（0 / 负 / 极大）不抛异常、不返回 0 行。
 */
class CourseNameLineBudgetTest {

    @Test
    fun tinyBlockGetsMinimumLines() {
        // 一节课的小格子：可用高度约 24dp，字号 9dp → 只够 2 行
        assertEquals(2, CourseNameLineBudget.linesFor(contentHeightDp = 24f, fontSizeDp = 9f))
        assertEquals(2, CourseNameLineBudget.MIN_LINES)
    }

    @Test
    fun tallBlockIsCappedAtMax() {
        // 两倍时长的大格子：高度远超需要 → 必须被钳制在 4 行
        assertEquals(4, CourseNameLineBudget.linesFor(contentHeightDp = 400f, fontSizeDp = 9f))
        assertEquals(4, CourseNameLineBudget.MAX_LINES)
    }

    @Test
    fun scalesWithAvailableHeight() {
        val font = 10f
        val few = CourseNameLineBudget.linesFor(contentHeightDp = 30f, fontSizeDp = font)
        val mid = CourseNameLineBudget.linesFor(contentHeightDp = 50f, fontSizeDp = font)
        val many = CourseNameLineBudget.linesFor(contentHeightDp = 70f, fontSizeDp = font)
        assertTrue("行数应随高度单调不减：$few/$mid/$many", few <= mid && mid <= many)
    }

    @Test
    fun largerFontYieldsFewerLines() {
        // 同样高度，字号翻倍 → 可容纳行数减半
        val small = CourseNameLineBudget.linesFor(contentHeightDp = 72f, fontSizeDp = 9f)
        val large = CourseNameLineBudget.linesFor(contentHeightDp = 72f, fontSizeDp = 18f)
        assertTrue("字号越大行数越少：$small vs $large", large <= small)
    }

    @Test
    fun degenerateInputFallsBackToMinimum() {
        assertEquals(2, CourseNameLineBudget.linesFor(contentHeightDp = 0f, fontSizeDp = 10f))
        assertEquals(2, CourseNameLineBudget.linesFor(contentHeightDp = -5f, fontSizeDp = 10f))
        assertEquals(2, CourseNameLineBudget.linesFor(contentHeightDp = 100f, fontSizeDp = 0f))
        assertEquals(2, CourseNameLineBudget.linesFor(contentHeightDp = 100f, fontSizeDp = -3f))
        assertEquals(2, CourseNameLineBudget.linesFor(Float.NaN, fontSizeDp = 10f))
    }

    @Test
    fun resultAlwaysWithinBounds() {
        // 扫描一组有代表性的组合，结果必须永远落在 [MIN, MAX]
        val heights = listOf(0f, 8f, 16f, 24f, 32f, 48f, 64f, 96f, 128f, 256f, 512f)
        val fonts = listOf(6f, 8f, 10f, 12f, 16f, 20f, 28f)
        for (h in heights) for (f in fonts) {
            val n = CourseNameLineBudget.linesFor(h, f)
            assertTrue(
                "height=$h font=$f 得到 $n，越界",
                n in CourseNameLineBudget.MIN_LINES..CourseNameLineBudget.MAX_LINES
            )
        }
    }

    /**
     * 回归：真机截图上的那条长课名。
     *
     * 旧实现无 maxLines，「毛泽东思想和中国特色社会主义理论体系概论」（20 字）
     * 在约 44dp 宽的格子里折到 7 行，把地点与教师挤出可视区。
     * 现在必须在预算行数内截断 —— 本例可用高度按「一节课 ≈ 24dp」给 2 行。
     */
    @Test
    fun realLongCourseNameIsTruncatedToBudget() {
        val name = "毛泽东思想和中国特色社会主义理论体系概论"
        assertTrue("课名长度应确实很长（${name.length} 字）", name.length >= 18)
        val lines = CourseNameLineBudget.linesFor(contentHeightDp = 24f, fontSizeDp = 9f)
        assertTrue("长课名必须被限制在 ${lines} 行内，而不是不限行数", lines <= 4)
    }
}