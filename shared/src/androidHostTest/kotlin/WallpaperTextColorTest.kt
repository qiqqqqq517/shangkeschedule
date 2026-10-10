import androidx.compose.ui.graphics.Color
import com.shangkeschedule.ui.schedule.components.WALLPAPER_LUMINANCE_THRESHOLD
import com.shangkeschedule.ui.schedule.components.averageLuminanceFrom
import com.shangkeschedule.ui.schedule.components.textColorForWallpaper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 壁纸明暗自适应文字色（v4.76.24）的纯逻辑单测。
 *
 * 需求原文：浅色模式下深色背景图会让侧边时间（黑字）看不清；深色模式下浅色背景图同理。
 * 本测试把「按图片明暗自动选黑/白」的判定钉死，**不需要装机、不需要真机**。
 */
class WallpaperTextColorTest {

    private val fallback = Color(0xFF123456)

    // --- 核心需求：深图配白字、浅图配黑字 ---

    @Test
    fun `深色壁纸返回白色文字`() {
        // 需求场景①：浅色模式 + 深色图片 ⇒ 必须给白字，否则黑字压深图读不出来
        assertEquals(Color.White, textColorForWallpaper(0.0f, fallback))
        assertEquals(Color.White, textColorForWallpaper(0.2f, fallback))
        assertEquals(Color.White, textColorForWallpaper(0.49f, fallback))
    }

    @Test
    fun `浅色壁纸返回黑色文字`() {
        // 需求场景②：深色模式 + 浅色图片 ⇒ 必须给黑字
        assertEquals(Color.Black, textColorForWallpaper(0.51f, fallback))
        assertEquals(Color.Black, textColorForWallpaper(0.8f, fallback))
        assertEquals(Color.Black, textColorForWallpaper(1.0f, fallback))
    }

    @Test
    fun `阈值处按大于判定`() {
        // 恰好等于阈值不算「亮」，走白字；仅用于锁定边界语义，避免日后被误改
        assertEquals(Color.White, textColorForWallpaper(WALLPAPER_LUMINANCE_THRESHOLD, fallback))
        assertEquals(Color.Black, textColorForWallpaper(WALLPAPER_LUMINANCE_THRESHOLD + 0.001f, fallback))
    }

    // --- 亮度未知时不得改变现状 ---

    @Test
    fun `亮度未知时回退到主题色`() {
        // 未采样完 / 采样失败 / 非 Android 平台 ⇒ 必须原样返回，界面不因采样失败而变差
        assertEquals(fallback, textColorForWallpaper(null, fallback))
    }

    @Test
    fun `越界亮度被夹到有效区间`() {
        assertEquals(Color.White, textColorForWallpaper(-5f, fallback))
        assertEquals(Color.Black, textColorForWallpaper(9f, fallback))
    }

    // --- 亮度计算 ---

    @Test
    fun `纯黑图平均亮度为 0`() {
        val luma = averageLuminanceFrom(sumR = 0, sumG = 0, sumB = 0, sampleCount = 100)
        assertEquals(0f, luma)
    }

    @Test
    fun `纯白图平均亮度为 1`() {
        val luma = averageLuminanceFrom(
            sumR = 255L * 100, sumG = 255L * 100, sumB = 255L * 100, sampleCount = 100
        )
        assertEquals(1f, luma)
    }

    @Test
    fun `零样本返回 null`() {
        // 图片解码成功但取不到像素（0 尺寸）⇒ 视为亮度未知，而不是当作纯黑
        assertNull(averageLuminanceFrom(0, 0, 0, 0))
        assertNull(averageLuminanceFrom(10, 10, 10, -1))
    }

    @Test
    fun `绿色比红色亮符合 Rec601 权重`() {
        // 同值下 G 的权重(0.587) 高于 R(0.299)：锁定权重口径，
        // 避免与课程块 adaptiveTextColor 的口径分叉
        val green = averageLuminanceFrom(0, 255L * 10, 0, 10)!!
        val red = averageLuminanceFrom(255L * 10, 0, 0, 10)!!
        assertTrue(green > red, "绿色亮度($green)应高于红色($red)")
    }

    @Test
    fun `中灰图落在阈值下方给白字`() {
        // 128/255 ≈ 0.502 的平均通道值，经 Rec601 加权后 ≈0.502 —— 略高于阈值，
        // 但若三通道同为 127 则应低于阈值。这里锁定「中灰偏暗给白字」的实际行为。
        val luma = averageLuminanceFrom(
            sumR = 127L * 10, sumG = 127L * 10, sumB = 127L * 10, sampleCount = 10
        )!!
        assertTrue(luma < WALLPAPER_LUMINANCE_THRESHOLD, "127 灰应判为暗，实际=$luma")
        assertEquals(Color.White, textColorForWallpaper(luma, fallback))
    }

    @Test
    fun `亮度计算结果始终落在 0 到 1`() {
        // 防御：即使上游传入越界累加值也不得产出越界亮度
        listOf(
            averageLuminanceFrom(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 1),
            averageLuminanceFrom(-100, -100, -100, 1),
        ).forEach { luma ->
            assertTrue(luma!! in 0f..1f, "亮度越界：$luma")
        }
    }
}