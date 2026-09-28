package com.shangkeschedule.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [WidgetTextScale] 字号策略单测（v4.61.0 新增）。
 *
 * 锁死两条方向性约束：
 * ① 空间只放大不缩小（S ≤ M ≤ L 单调不减）；
 * ② 同空间档内课程名/时间/地点字号与课程名长短无关（统一），长名由 ellipsize 截断。
 */
class WidgetTextScaleTest {

    @Test
    fun `空间分档阈值 200 和 320`() {
        assertEquals(WidgetSpaceClass.S, spaceClassFor(0))
        assertEquals(WidgetSpaceClass.S, spaceClassFor(-50))
        assertEquals(WidgetSpaceClass.S, spaceClassFor(199))
        assertEquals(WidgetSpaceClass.M, spaceClassFor(200))
        assertEquals(WidgetSpaceClass.M, spaceClassFor(319))
        assertEquals(WidgetSpaceClass.L, spaceClassFor(320))
        assertEquals(WidgetSpaceClass.L, spaceClassFor(800))
    }

    @Test
    fun `空间增量 S0 M1 L2`() {
        assertEquals(0, spaceDeltaSp(WidgetSpaceClass.S))
        assertEquals(1, spaceDeltaSp(WidgetSpaceClass.M))
        assertEquals(2, spaceDeltaSp(WidgetSpaceClass.L))
    }

    @Test
    fun `课程名字号与字数无关——同档统一`() {
        // S 档一律 14sp，无论 2 字还是 20 字（长名由 ellipsize 截断）
        assertEquals(14f, courseNameSizeSp(WidgetSpaceClass.S))
        assertEquals(15f, courseNameSizeSp(WidgetSpaceClass.M))
        assertEquals(16f, courseNameSizeSp(WidgetSpaceClass.L))
    }

    @Test
    fun `空间档单调不减`() {
        val s = courseNameSizeSp(WidgetSpaceClass.S)
        val m = courseNameSizeSp(WidgetSpaceClass.M)
        val l = courseNameSizeSp(WidgetSpaceClass.L)
        assertEquals(1f, m - s)
        assertEquals(2f, l - s)
    }

    @Test
    fun `行元信息与栏头只随空间变`() {
        assertEquals(11f, rowMetaSizeSp(WidgetSpaceClass.S))
        assertEquals(12f, rowMetaSizeSp(WidgetSpaceClass.M))
        assertEquals(13f, rowMetaSizeSp(WidgetSpaceClass.L))
        assertEquals(12f, headerSizeSp(WidgetSpaceClass.S))
        assertEquals(13f, headerSizeSp(WidgetSpaceClass.M))
        assertEquals(14f, headerSizeSp(WidgetSpaceClass.L))
    }
}
