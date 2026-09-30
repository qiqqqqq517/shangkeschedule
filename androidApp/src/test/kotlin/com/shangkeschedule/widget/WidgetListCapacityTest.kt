package com.shangkeschedule.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ListVertical（4×N）条数推算单测（v3.66.5 新增，v4.64.17 补分隔线口径）。
 *
 * 回归目标一：v3.66.4 用偏小的行高常量（36dp）估算，而条目实际占位约 49dp，
 * 条数偏高约 36% ⇒ 4×N 拉高后列表溢出、末条被静默裁切。
 *
 * 回归目标二（v4.64.17）：行与行之间还有 `widget_divider_horizontal`
 * （1dp + 上下 margin 各 2dp = 5dp），N 行实际占 `N×行高 + (N-1)×5dp`。
 * 旧闭式解 `可用/行高` 把 (N-1)×5dp 整段漏掉：只要可用高度不是行高的整数倍、
 * 余量不足 5×(N-1)，就算多一条 —— 而 `container_courses` 是 LinearLayout（不滚动），
 * 多算即末条被静默裁切，与 v3.66.4 同形。
 *
 * 本测试锁死「宁可少显示、不可多算」的方向性约束。
 */
class WidgetListCapacityTest {

    private val chrome = WidgetListCapacity.CHROME_DP
    private val maxRows = WidgetListCapacity.MAX_ROWS
    private val divider = WidgetListCapacity.DIVIDER_DP

    /** 条目实测高度约 49dp（13sp + 10sp + 10sp + 间隔 + paddingVertical 3dp×2）。 */
    private val rowPx = 49

    private fun rows(heightDp: Int, row: Int = rowPx, density: Float = 1f) =
        WidgetListCapacity.rowsFor(heightDp, chrome, row, density, maxRows)

    /** 独立复算：N 行实际占用的总高度（px）—— 与被测实现不共享代码路径。 */
    private fun usedPx(count: Int, row: Int, density: Float): Int {
        val dividerPx = (divider * density).toInt()
        return count * row + (count - 1).coerceAtLeast(0) * dividerPx
    }

    @Test
    fun `4x2 高度下只放得下 1 条`() {
        // 可用 110-39 = 71；1 条需 49，第 2 条需 49+5+49 = 103 > 71 → 1
        assertEquals(1, rows(110))
    }

    @Test
    fun `旧实现的 36dp 常量在大尺寸下会多算——本公式必须更保守`() {
        // 4×2（110dp）时两者恰好都是 1；偏差出现在拉高之后。
        fun oldFormula(heightDp: Int) = (heightDp - chrome) / 36

        assertEquals(1, rows(110))
        assertEquals(1, oldFormula(110))

        assertTrue("250dp：新公式必须不多于旧公式", rows(250) <= oldFormula(250))
        assertTrue("350dp：新公式必须不多于旧公式", rows(350) <= oldFormula(350))

        assertEquals(4, rows(250))
        assertEquals(5, oldFormula(250))
        assertEquals(5, rows(350))
        assertEquals(8, oldFormula(350))
    }

    @Test
    fun `可用高度不足一条时仍返回 1（由 minResizeHeight 保证不触发）`() {
        assertEquals(1, rows(70))
        assertEquals(1, rows(0))
        assertEquals(1, rows(-50))
    }

    @Test
    fun `随高度增长逐条递增并向下取整`() {
        // 可用 = H - 39；每多一条需再吃 行高(49) + 分隔线(5)
        assertEquals(1, rows(100))   // 可用  61：1 条需  49，第 2 条需 103 → 1
        assertEquals(1, rows(140))   // 可用 101：1 条需  49，第 2 条需 103 → 1
        assertEquals(4, rows(250))   // 可用 211：4 条需 211（正好），第 5 条需 265 → 4
        assertEquals(5, rows(350))   // 可用 311：5 条需 265，第 6 条需 319 → 5
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
        // 220dp @2x = 440px；chrome 39dp @2x = 78px → 可用 362px；
        // 分隔线 5dp @2x = 10px；row 49px（已按 px 传入）
        // 1:49 2:108 3:167 4:226 5:285 6:344 7:403 > 362 → 6
        assertEquals(6, rows(220, row = 49, density = 2f))
    }

    @Test
    fun `高 fontScale 下条数随之减少（行高变大）`() {
        val normal = rows(250, row = 49)
        val large = rows(250, row = 64)   // fontScale 1.3 近似
        assertTrue("字号放大后条数必须不增", large <= normal)
        assertEquals(4, normal)
        assertEquals(3, large)            // 可用 211：3 条需 64×3+5×2 = 202，第 4 条需 271
    }

    @Test
    fun `分隔线计入后末条必须完整放得下（逐档交叉验证）`() {
        // 逐个高度档断言「返回条数所需总高 ≤ 可用高」且「第 N+1 条放不下」，
        // 杜绝闭式解漏算分隔线导致的越界。usedPx 与被测实现不共享代码路径。
        for (heightDp in listOf(110, 140, 180, 210, 250, 300, 350, 420, 500)) {
            val n = rows(heightDp)
            val available = heightDp - chrome
            assertTrue(
                "高度 ${heightDp}dp：$n 条需 ${usedPx(n, rowPx, 1f)}px，必须 ≤ 可用 ${available}px",
                usedPx(n, rowPx, 1f) <= available
            )
            assertTrue(
                "高度 ${heightDp}dp：第 ${n + 1} 条必须放不下，否则说明算少了",
                usedPx(n + 1, rowPx, 1f) > available
            )
        }
    }

    @Test
    fun `余量不足分隔线时旧闭式解会多算一条——这是本轮修复的核心场景`() {
        // 250dp 时 4 条恰好占满 211px；但高度少 1dp（249dp，可用 210px）时：
        //   旧闭式解 210/49 = 4 → 实际需 211px > 210px ⇒ 末条被裁切
        //   新公式      4 条需 211 > 210 → 回落 3 条（3 条需 158，留白）
        fun oldFormula(heightDp: Int) = (heightDp - chrome) / rowPx

        assertEquals(211, usedPx(4, rowPx, 1f))
        assertTrue("旧闭式解在 249dp 会给出 4 条", oldFormula(249) == 4)
        assertTrue("而 4 条真实需要 211px", usedPx(4, rowPx, 1f) > 249 - chrome)

        assertEquals(3, rows(249))
        assertEquals(4, rows(250))
    }

    @Test
    fun `传 0 分隔线时退化为旧闭式解（供无分隔线布局复用）`() {
        assertEquals(4, WidgetListCapacity.rowsFor(250, chrome, rowPx, 1f, maxRows, dividerDp = 0))
        assertEquals(6, WidgetListCapacity.rowsFor(350, chrome, rowPx, 1f, maxRows, dividerDp = 0))
    }

    @Test
    fun `分隔线占位不合法时按 0 处理（不崩溃、不算少）`() {
        val negative = WidgetListCapacity.rowsFor(350, chrome, rowPx, 1f, maxRows, dividerDp = -5)
        assertEquals(6, negative)   // 与 dividerDp=0 同解
    }
}
