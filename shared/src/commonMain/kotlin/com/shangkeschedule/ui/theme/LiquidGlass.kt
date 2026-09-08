package com.shangkeschedule.ui.theme

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 玻璃本体的冷灰基色：只做极淡混入，让玻璃在纯白内容上比背景略沉一档，
 * 从而显出轮廓与体积（避免暖色污染主题，故选中性偏冷的石板色）。
 */
private val LiquidGlassBodyScrim = Color(0xFF0B1A2B)

/**
 * 全应用玻璃件的**默认**高斯模糊半径（v3.24.7 收口）。
 *
 * 为什么要收成常量：底栏胶囊、「回到本周」圆钮、课程挂起条、玻璃 AppFab 是同一套玻璃
 * 语言，用户要求「同一配置下模糊度必须一致」。此前各调用点各自写死数值
 * （底栏 1.5 / 4dp、圆钮 4dp、挂起条用默认 6dp），改一处忘一处就再次分叉。
 *
 * 取值 4dp 的依据：haze 的 `HazeInputScale.Auto` 在 blurRadius < 7dp 时输入不降采样
 * （scale=1.0，见 HazeEffectNode.calculateInputScaleFactor），全分辨率采样 ⇒ 各件
 * 实际雾化宽度只由该半径决定，横向可比、无隐藏缩放因子。
 */
val LiquidGlassBlurRadius = 4.dp

/**
 * 当前生效的玻璃模糊半径（v3.25.0 起可被用户设置覆盖）。
 *
 * 由 `ShangKeScheduleTheme` 从 `AppSettingsModel.glassBlurRadiusDp` 注入，
 * 调节入口在「外观与样式 → 个性化显示」。所有悬浮玻璃件都读这一个 Local，
 * 因此"用户调一次，全端同步"，不会重新出现各件各值。
 */
val LocalGlassBlurRadius = compositionLocalOf { LiquidGlassBlurRadius }

/**
 * 液态玻璃（Liquid Glass）修饰符：给悬浮层（玻璃底栏胶囊 / 「回到本周」圆钮等）
 * 提供统一的玻璃观感。
 *
 * 设计参照 Kyant/backdrop（LiquidBottomTabs）的光学结构，并按本项目需求反向调参：
 * 该库用 `blur(8dp) + lens(24dp, 24dp) + vibrancy()` —— 即**液体感主要来自折射位移光学，
 * 而非模糊**；本项目要求「少一点模糊变形、多一点透明感」，因此：
 * - **不做 lens 位移**（那正是"变形"的来源），只保留轻微 blur 提供雾度；
 * - 表面基色 alpha 由 0.40 降到约 0.22，让底层内容更多透上来；
 * - 所有高光改用 **BlendMode.Plus**（只加亮、不遮盖），这是"既亮又透"的关键：
 *   SrcOver 的白渐变会把底下内容糊住，Plus 只是在原有像素上叠加亮度。
 *
 * 玻璃的四层光学（无透镜版）：
 * 1. 投影：把玻璃从底层内容上托起来；
 * 2. 外轮廓线：形状之外的半环（clip 之前绘制），纯白底色上的「存在线」；
 * 3. 中心区：低 alpha 基色 + 低半径模糊，**保持通透**；
 * 4. 边缘光学：贴边暗环 + 内壁亮环（Stroke）+ 45° 斜向 sheen + 上下内壁光，
 *    全部集中在边缘几像素／窄带内，中心不覆盖。
 */
@Composable
fun Modifier.liquidGlass(
    hazeState: HazeState,
    shape: Shape,
    containerColor: Color,
    isTransparent: Boolean = false,
    shadowElevation: Dp = 14.dp,
    blurRadius: Dp = Dp.Unspecified,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this

    // 未显式传值 ⇒ 跟随用户在「个性化显示」里设定的全局模糊半径
    val effectiveBlurRadius = blurRadius.takeOrElse { LocalGlassBlurRadius.current }

    val tokens = appColors()
    val isDark = LocalIsDarkTheme.current

    // 表面不透明度：对齐 backdrop 的 LiquidButton（其 surface 默认为 Unspecified，
    // 即一块色都不画）。这里做不到完全不画（否则在纯白底色上形状会彻底消失、
    // 也不利于图标可读性），故只保留刚够压住噪点的一档，形态辨识交给边缘光学。
    // v3.23.5：用户反馈「导航栏再透明一点」，三层遮盖继续下调（light 0.07→0.04）。
    val baseAlpha = if (isTransparent) 0.08f else if (isDark) 0.12f else 0.04f
    val veilAlpha = if (isTransparent) 0.03f else if (isDark) 0.03f else 0.02f
    val bodyScrim = if (isTransparent) 0.10f else if (isDark) 0.03f else 0.005f

    // 边缘光学力度：混合模式必须是 Screen 而非 Plus——Plus 直接把 RGB 相加，
    // 浅色内容上会瞬间顶到 255 纯白，把底下内容洗掉（实测 2217-2230 全为 255 的教训）。
    // Screen 是饱和式加亮（1-(1-a)(1-b)），越亮加得越少，既立体又保留内容层次。
    val sheenAlpha = if (isDark) 0.08f else 0.12f
    val topInnerAlpha = if (isDark) 0.12f else 0.26f
    val bottomInnerAlpha = if (isDark) 0.08f else 0.15f
    val specularAlpha = if (isDark) 0.08f else 0.16f
    // 表面几乎不再遮盖，玻璃的存在感改由边缘光学承担：亮环与暗环力度都要上调
    val rimBright = if (isDark) 0.34f else 0.88f
    val rimDim = if (isDark) 0.07f else 0.18f
    val rimMid = if (isDark) 0.18f else 0.52f
    // 贴边暗环：玻璃与外界折射的最外一圈，浅色背景上给玻璃"边界感"
    val edgeShade = if (isDark) 0.34f else 0.16f
    val outlineAlpha = if (isTransparent) 0.26f else if (isDark) 0.34f else 0.14f
    val rimWidth = 1.1.dp

    return this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = tokens.shadow,
            spotColor = tokens.shadow
        )
        // 外轮廓线：排在 clip 之前，保留形状外的半环
        .border(width = 1.dp, color = Color.Black.copy(alpha = outlineAlpha), shape = shape)
        .clip(shape)
        .hazeEffect(hazeState) {
            this.blurRadius = effectiveBlurRadius
            // v3.24.2：noiseFactor 归零——用户要求删除导航栏颗粒感；noise 是铺满
            // 整面的颗粒噪声（v3.23.8 已减半仍可感知），直接移除后玻璃只保留
            // 模糊 + 三层 tint + 边缘光学，观感更清澈。全局生效（底栏/圆钮/悬浮条/FAB）。
            noiseFactor = 0f
            tints = listOf(
                HazeTint(containerColorOrBlack(containerColor, isTransparent).copy(alpha = baseAlpha)),
                HazeTint(LiquidGlassBodyScrim.copy(alpha = bodyScrim)),
                HazeTint(Color.White.copy(alpha = veilAlpha))
            )
            fallbackTint = HazeTint(
                containerColorOrBlack(containerColor, isTransparent)
                    .copy(alpha = (baseAlpha + 0.24f).coerceAtMost(0.92f))
            )
            backgroundColor = Color.Transparent
        }
        // 边缘光学：全部用 Screen 加亮、且只覆盖边缘窄带 / 左上角小椭圆，
        // 中心区域一律留空，保证底层内容清晰透出（透明感的第一优先级）。
        .drawBehind {
            // ① 45° 斜向 sheen：一条斜穿的极淡亮带，液体的镜面反光感
            drawRect(
                brush = Brush.linearGradient(
                    0.00f to Color.Transparent,
                    0.40f to Color.White.copy(alpha = sheenAlpha),
                    0.50f to Color.White.copy(alpha = sheenAlpha * 0.4f),
                    0.60f to Color.Transparent,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f)
                ),
                blendMode = BlendMode.Screen
            )
            // ② 左上角小椭圆镜面光：范围收得比整宽小得多，不铺满整个面
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = specularAlpha),
                        Color.White.copy(alpha = 0f)
                    ),
                    center = Offset(size.width * 0.22f, -size.height * 0.35f),
                    radius = size.height * 0.95f
                ),
                blendMode = BlendMode.Screen
            )
            // ③ 上内壁窄亮带（仅上部 14%）
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = topInnerAlpha),
                    0.06f to Color.White.copy(alpha = topInnerAlpha * 0.5f),
                    0.14f to Color.Transparent
                ),
                blendMode = BlendMode.Screen
            )
            // ④ 下内壁窄反光（仅下部 10%）
            drawRect(
                brush = Brush.verticalGradient(
                    0.90f to Color.Transparent,
                    1f to Color.White.copy(alpha = bottomInnerAlpha)
                ),
                blendMode = BlendMode.Screen
            )
        }
        // 边缘三层光学（clip 已裁掉形状外的一半描边，所以每条 border 只留内侧一半）：
        // 由外到内依次绘制「宽(V) → 中(亮) → 窄(暗)」，后者覆盖前者的内侧部分，
        // 最终从形状边缘向内形成「暗环 → 亮环 → 柔和光晕」的层递，即玻璃厚度感。
        .border(
            width = rimWidth * 2.6f,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = rimDim),
                    Color.White.copy(alpha = rimDim * 0.6f),
                    Color.White.copy(alpha = rimMid * 0.5f)
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            ),
            shape = shape
        )
        .border(
            width = rimWidth * 1.8f,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = rimBright),
                    Color.White.copy(alpha = rimDim),
                    Color.White.copy(alpha = rimMid)
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            ),
            shape = shape
        )
        .border(
            width = rimWidth,
            color = Color.Black.copy(alpha = edgeShade),
            shape = shape
        )
}

private fun containerColorOrBlack(containerColor: Color, isTransparent: Boolean): Color =
    if (isTransparent) Color.Black else containerColor
