package com.shangkeschedule.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WidgetBubbleContrast] 气泡数字前景色单测。
 *
 * 锁死三条约束：
 * ① 真实事故回归 —— 旧实现（固定主题文字色）在下列真实课程色/兜底色上远低于
 *    AA（深色琥珀 `#FBC02D` 配近白字仅 1.48:1、浅色兜底 `#5D6D7E` 配深字仅 2.07:1），
 *    新规则在这些用例上必须选对比色且 ≥ 4.5:1；
 * ② 混合数学 —— `effectiveBubbleColor` 的 SRC_ATOP 语义（不透明直通、全透明退化为基底）；
 * ③ 全色域下界 —— 不透明 RGB 立方体 + 典型半透明档暴力扫描，所选前景色恒 ≥ 4.5:1
 *   （与实现头注释的「恒 ≥ 4.58:1」证明对应，0.08 为断言裕量）。
 */
class WidgetBubbleContrastTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun `黑白锚点`() {
        assertEquals(black, bubbleTextColorFor(0xFFFFFFFF.toInt()))
        assertEquals(white, bubbleTextColorFor(0xFF000000.toInt()))
    }

    @Test
    fun `真实事故用例一律选对比色且达标`() {
        // (气泡底实际色, 期望前景色)：均为旧实现翻车的真实值，注释为旧对比度
        val cases = listOf(
            0xFFFBC02D.toInt() to black, // 深色琥珀（夜）：旧近白字 1.48:1
            0xFF007AFF.toInt() to black, // 通透蓝（夜）：旧近白字 3.60:1
            0xFF388E3C.toInt() to black, // 默认绿（夜）：旧近白字 3.69:1
            0xFFD32F2F.toInt() to white, // 默认红（夜）：深红底必须白字
            0xFFAEA9AF.toInt() to black, // 兜底色（夜）：旧近白字 2.07:1
            0xFF5D6D7E.toInt() to white, // 兜底色（昼）：旧深字 2.07:1
            0xFFFFF9C4.toInt() to black, // 默认黄（昼）：旧深字虽过（10.25），黑字更优（19.6）
        )
        for ((bg, expected) in cases) {
            assertEquals("底色 %08X".format(bg), expected, bubbleTextColorFor(bg))
            assertTrue(
                "底色 %08X 对比度不足".format(bg),
                contrastRatio(bubbleTextColorFor(bg), bg) >= 4.5
            )
        }
    }

    @Test
    fun `半透明课程色先混合再选色`() {
        // 通透主题浅色档仅 0x1F alpha：旧深字约 3.6:1（糊），新规则须选黑字且达标
        val yellow = effectiveBubbleColor(0x1FFFCC00.toInt())
        val green = effectiveBubbleColor(0x1F34C759.toInt())
        assertEquals(black, bubbleTextColorFor(yellow))
        assertEquals(black, bubbleTextColorFor(green))
        assertTrue(contrastRatio(black, yellow) >= 4.5)
        assertTrue(contrastRatio(black, green) >= 4.5)
    }

    @Test
    fun `混合语义`() {
        // 不透明直通
        assertEquals(0xFFFBC02D.toInt(), effectiveBubbleColor(0xFFFBC02D.toInt()))
        // 全透明退化为基底
        assertEquals(WIDGET_BUBBLE_BASE_COLOR, effectiveBubbleColor(0x00000000))
        // 半透明白盖纯黑 = #808080（0x80 / 255 的精确中点）
        assertEquals(0xFF808080.toInt(), effectiveBubbleColor(0x80FFFFFF.toInt(), 0xFF000000.toInt()))
    }

    @Test
    fun `缺色回落兜底`() {
        val dayFallback = 0xFF5D6D7E.toInt()
        val nightFallback = 0xFFAEA9AF.toInt()
        assertEquals(white, resolveBubbleTextColor(null, dayFallback))
        assertEquals(black, resolveBubbleTextColor(null, nightFallback))
        assertEquals(white, resolveBubbleTextColor(0xFF5D6D7E.toInt(), nightFallback))
    }

    @Test
    fun `全色域扫描所选前景色恒达标`() {
        // 不透明立方体（步 8 ⇒ 32^3 = 32768 点）
        var worst = Double.MAX_VALUE
        var c = 0
        while (c <= 255) {
            var d = 0
            while (d <= 255) {
                var e = 0
                while (e <= 255) {
                    val bg = (0xFF shl 24) or (c shl 16) or (d shl 8) or e
                    val ratio = contrastRatio(bubbleTextColorFor(bg), bg)
                    if (ratio < worst) worst = ratio
                    e += 8
                }
                d += 8
            }
            c += 8
        }
        assertTrue("不透明色域最差 %.3f".format(worst), worst >= 4.5)

        // 半透明档（各主题浅色真实 alpha：0x1F / 0x33 / 0x59 / 0x80）盖基底后同样扫描
        for (alpha in intArrayOf(0x1F, 0x33, 0x59, 0x80)) {
            var cc = 0
            while (cc <= 255) {
                var dd = 0
                while (dd <= 255) {
                    var ee = 0
                    while (ee <= 255) {
                        val bg = effectiveBubbleColor((alpha shl 24) or (cc shl 16) or (dd shl 8) or ee)
                        val ratio = contrastRatio(bubbleTextColorFor(bg), bg)
                        assertTrue(
                            "alpha=%02X 色 %02X%02X%02X 仅 %.3f".format(alpha, cc, dd, ee, ratio),
                            ratio >= 4.5
                        )
                        ee += 32
                    }
                    dd += 32
                }
                cc += 32
            }
        }
    }
}
