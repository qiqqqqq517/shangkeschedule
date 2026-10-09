import com.shangkeschedule.ui.settings.style.computeSliderTrackGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 滑块轨道几何（`StyleSliderItem`）的纯逻辑单测。
 *
 * 背景：v4.75.5 ~ v4.75.10 期间，滑块几何写死在 Canvas lambda 里并依赖对 Material3
 * 内部 thumb 摆放公式的逆向推导，导致真机连续四轮返工（thumb 探出轨道 / 轨道两端露白 /
 * 右端拉不到位 / 已选段与 thumb 之间有残留）。现将几何收敛为纯函数
 * [computeSliderTrackGeometry]，并由本组件自绘 thumb，使三者坐标同源。
 *
 * 本测试把这些「肉眼反复出问题」的不变量钉死：**不需要模拟器、不需要装机**。
 */
class SliderTrackGeometryTest {

    // 典型参数：一屏 1080px 宽、内容区左右各约 32px、density 3 ⇒ 轨道高 22dp ≈ 66px。
    private val canvasWidth = 1016f
    private val trackHeight = 66f

    private fun geo(fraction: Float) =
        computeSliderTrackGeometry(canvasWidth, trackHeight, fraction)

    // --- thumb 与轨道等高 ---

    @Test
    fun `thumb 直径等于轨道高度`() {
        val g = geo(0.5f)
        assertEquals(trackHeight / 2f, g.thumbRadius, TOL, "thumb 半径应等于轨道高的一半")
        assertEquals(trackHeight, g.thumbRadius * 2f, TOL, "thumb 直径应等于轨道高度")
    }

    // --- 两端恰好贴齐，既不溢出也不留白 ---

    @Test
    fun `最左端 thumb 左缘贴齐轨道左端且不溢出`() {
        val g = geo(0f)
        assertEquals(g.trackLeft, g.thumbCenter - g.thumbRadius, TOL, "最左端 thumb 左缘应与轨道左端齐平")
        assertTrue(g.thumbCenter - g.thumbRadius >= g.trackLeft - TOL, "thumb 不得溢出轨道左端")
    }

    @Test
    fun `最右端 thumb 右缘贴齐轨道右端且不溢出`() {
        val g = geo(1f)
        assertEquals(g.trackRight, g.thumbCenter + g.thumbRadius, TOL, "最右端 thumb 右缘应与轨道右端齐平")
        assertTrue(g.thumbCenter + g.thumbRadius <= g.trackRight + TOL, "thumb 不得溢出轨道右端")
    }

    @Test
    fun `两端都不留多余空轨道`() {
        val left = geo(0f)
        val right = geo(1f)
        assertEquals(0f, left.trackLeft, TOL, "可见轨道左端应为 canvas 左边界，不外扩")
        assertEquals(canvasWidth, right.trackRight, TOL, "可见轨道右端应为 canvas 右边界，不外扩")
    }

    // --- 已选段与 thumb 无缝相接（本次反复出现的症状）---

    @Test
    fun `已选段右端至少覆盖到 thumb 圆心`() {
        listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f).forEach { f ->
            val g = geo(f)
            assertTrue(
                g.fillEnd >= g.thumbCenter - TOL,
                "fraction=$f：已选段必须盖到 thumb 圆心，否则滑块左侧会露出底色空隙"
            )
        }
    }

    @Test
    fun `已选段右端的圆角与 thumb 右半圆完全重合`() {
        // fillEnd = thumbCenter + thumbRadius ⇒ 圆角矩形的右端圆角中心即 thumb 圆心、半径相同。
        listOf(0f, 0.37f, 0.5f, 0.83f, 1f).forEach { f ->
            val g = geo(f)
            assertEquals(
                g.thumbCenter + g.thumbRadius,
                g.fillEnd,
                TOL,
                "fraction=$f：已选段右端应恰为 thumb 右缘，其圆角与 thumb 右半圆重合"
            )
        }
    }

    // --- 单调性与边界 ---

    @Test
    fun `thumb 圆心随比例单调右移`() {
        val centers = listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f).map { geo(it).thumbCenter }
        centers.zipWithNext().forEach { (a, b) ->
            assertTrue(b > a, "thumb 圆心必须严格递增，实际=$centers")
        }
    }

    @Test
    fun `越界比例被夹到有效区间`() {
        assertEquals(geo(0f).thumbCenter, geo(-3f).thumbCenter, TOL, "负比例应夹到 0")
        assertEquals(geo(1f).thumbCenter, geo(9f).thumbCenter, TOL, "超范围比例应夹到 1")
    }

    @Test
    fun `任意比例下 thumb 始终完整落在轨道内`() {
        var f = 0f
        while (f <= 1f) {
            val g = geo(f)
            assertTrue(
                g.thumbCenter - g.thumbRadius >= g.trackLeft - TOL &&
                    g.thumbCenter + g.thumbRadius <= g.trackRight + TOL,
                "fraction=$f：thumb 越出轨道 [$g]"
            )
            f += 0.05f
        }
    }

    @Test
    fun `已选段始终不超出轨道右端`() {
        listOf(0f, 0.5f, 0.99f, 1f).forEach { f ->
            val g = geo(f)
            assertTrue(
                g.fillEnd <= g.trackRight + TOL,
                "fraction=$f：已选段不得画到轨道之外，实际 fillEnd=${g.fillEnd} trackRight=${g.trackRight}"
            )
        }
    }

    private companion object {
        /** 亚像素级容差：绘制最终按像素取整，允许 0.01px 误差。 */
        const val TOL = 0.01f
    }
}