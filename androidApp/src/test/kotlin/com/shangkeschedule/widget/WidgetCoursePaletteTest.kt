package com.shangkeschedule.widget

import com.shangkeschedule.data.model.schedule_style.DualColorProto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WidgetCoursePalette] 固定色板单测（v4.63.2，主题与小组件解耦）。
 *
 * 锁死四条产品约束：
 * ① 12 档、全部实色（半透明是之前「看不见」的来源之一，禁用）；
 * ② 色条可辨认 —— 浅档 vs 浅卡 `#FEF7FF`、深档 vs 深卡 `#141218` 一律 ≥ 3:1；
 * ③ 气泡数字可读 —— 24 档经 [bubbleTextColorFor] 的前景色一律 ≥ 4.5:1；
 * ④ 索引确定性 —— 任何主题序号（含负值 / 超界）回绕落档，缺池才回 null。
 */
class WidgetCoursePaletteTest {

    private val cardLight = 0xFFFEF7FF.toInt()
    private val cardDark = 0xFF141218.toInt()

    @Test
    fun `十二档且全部实色`() {
        assertEquals(12, WIDGET_COURSE_COLORS.size)
        for (c in WIDGET_COURSE_COLORS) {
            assertEquals("light %08X 须实色".format(c.light), 0xFF, (c.light ushr 24) and 0xFF)
            assertEquals("dark %08X 须实色".format(c.dark), 0xFF, (c.dark ushr 24) and 0xFF)
        }
    }

    @Test
    fun `色条在各自卡底上可辨认`() {
        WIDGET_COURSE_COLORS.forEachIndexed { index, c ->
            assertTrue(
                "第 $index 档浅色 %08X vs 浅卡仅 %.2f".format(c.light, contrastRatio(c.light, cardLight)),
                contrastRatio(c.light, cardLight) >= 3.0
            )
            assertTrue(
                "第 $index 档深色 %08X vs 深卡仅 %.2f".format(c.dark, contrastRatio(c.dark, cardDark)),
                contrastRatio(c.dark, cardDark) >= 3.0
            )
        }
    }

    @Test
    fun `气泡数字在全部二十四档上可读`() {
        for (c in listOf(WIDGET_COURSE_COLORS.map { it.light }, WIDGET_COURSE_COLORS.map { it.dark }).flatten()) {
            val bg = effectiveBubbleColor(c)
            assertTrue(
                "底色 %08X 前景对比仅 %.2f".format(bg, contrastRatio(bubbleTextColorFor(bg), bg)),
                contrastRatio(bubbleTextColorFor(bg), bg) >= 4.5
            )
        }
    }

    @Test
    fun `索引回绕与缺池回落`() {
        val maps = widgetCoursePaletteProto()
        assertEquals(maps[0], widgetCourseColorAt(maps, 0))
        assertEquals(maps[0], widgetCourseColorAt(maps, 12))
        assertEquals(maps[11], widgetCourseColorAt(maps, -1))
        assertEquals(maps[1], widgetCourseColorAt(maps, 25))
        assertNull(widgetCourseColorAt(null, 3))
        assertNull(widgetCourseColorAt(emptyList(), 3))
    }

    @Test
    fun `快照转换保真`() {
        val maps: List<DualColorProto> = widgetCoursePaletteProto()
        assertEquals(12, maps.size)
        assertEquals(WIDGET_COURSE_COLORS[0].light, maps[0].light_color.toInt())
        assertEquals(WIDGET_COURSE_COLORS[0].dark, maps[0].dark_color.toInt())
    }
}
