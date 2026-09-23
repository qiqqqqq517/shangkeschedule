package com.shangkeschedule.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ListVertical（4×N）条数推算单测（v3.66.5 新增）。
 *
 * 回归目标：v3.66.4 用偏小的行高常量（36dp）估算，而条目实际占位约 49dp，
 * 条数偏高约 36% ⇒ 4×N 拉高后列表溢出、末条被静默裁切。
 * 本测试锁死「宁可少显示、不可多算」的方向性约束。
 */
class WidgetListCapacityTest {

    private val chrome = WidgetListCapacity.CHROME_DP
    private val maxRows = WidgetListCapacity.MAX_ROWS

    /** 条目实测高度约 49dp（13sp + 10sp + 10sp + 间隔 + paddingVertical 3dp×2）。 */
    private val rowPx = 49

    private fun rows(heightDp: Int, row: Int = rowPx, density: Float = 1f) =
        WidgetListCapacity.rowsFor(heightDp, chrome, row, density, maxRows)

    @Test
    fun `4x2 高度下只放得下 1 条`() {
        // 110 - 39 = 71 可用；71 / 49 = 1.44 → 向下取整 1
        assertEquals(1, rows(110))
    }

    @Test
    fun `旧实现的 36dp 常量在大尺寸下会多算——本公式必须更保守`() {
        // 注意：4×2（110dp）时两者恰好都是 1（71/36 与 71/49 的整数商都是 1），
        // 偏差出现在拉高之后 —— 旧常量算得越多，溢出越明显。
        fun oldFormula(heightDp: Int) = (heightDp - chrome) / 36

        assertEquals(1, rows(110))
        assertEquals(1, oldFormula(110))

        // 拉高后旧公式明显多算
        assertTrue("250dp：新公式必须不多于旧公式", rows(250) <= oldFormula(250))
        assertTrue("350dp：新公式必须不多于旧公式", rows(350) <= oldFormula(350))

        assertEquals(4, rows(250))
        assertEquals(5, oldFormula(250))
        assertEquals(6, rows(350))
        assertEquals(8, oldFormula(350))
    }

    @Test
    fun `可用高度不足一条时仍返回 1（由 minResizeHeight 保证不触发）`() {
        assertEquals(1, rows(70))
        assertEquals(1, rows(0))
        assertEquals(1, rows(-50))
    }

    @Test
    fun `随高度增长线性增加并向下取整`() {
        // (H-39)/49 向下取整
        assertEquals(1, rows(100))   // 61/49 = 1.24
        assertEquals(2, rows(140))   // 101/49 = 2.06
        assertEquals(4, rows(250))   // 211/49 = 4.30
        assertEquals(6, rows(350))   // 311/49 = 6.34
    }

    @Test
    fun `触顶时封顶到 MAX_ROWS`() {
        assertEquals(maxRows, rows(100_000))
    }

    @Test
    fun `行高为 0 或负数时不崩且回落为 1`() {
        assertEquals(1, rows(110, row = 0))
        assertEquals(1, rows(110, row = -10))
    }

    @Test
    fun `密度换算正确（2x 屏）`() {
        // 220dp @2x = 440px；chrome 39dp @2x = 78px → 362px；row 49px（已按 px 传入）
        assertEquals(7, rows(220, row = 49, density = 2f))  // 362/49 = 7.38
    }

    @Test
    fun `高 fontScale 下条数随之减少（行高变大）`() {
        val normal = rows(250, row = 49)
        val large = rows(250, row = 64)   // fontScale 1.3 近似
        assertTrue("字号放大后条数必须不增", large <= normal)
        assertEquals(4, normal)
        assertEquals(3, large)            // 211/64 = 3.29
    }
}
