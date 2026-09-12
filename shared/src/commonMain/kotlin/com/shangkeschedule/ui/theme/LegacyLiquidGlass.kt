package com.shangkeschedule.ui.theme

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * v3.44.0 版液态玻璃修饰符（**兜底方案 B**）。
 *
 * 移植自 v3.44.0（提交 c261e96）的 `Modifier.liquidGlass`，逐行还原、只改名。
 * 存在的唯一理由：v3.44.0 在 OPPO Find X8 等 Android 15 机型上**实测可正常启动**
 * （用户反馈），而 v3.46.0 起的自研玻璃引擎在这些机型上会闪退。
 *
 * 与现行引擎（`ui/glass`）的本质区别 —— 也是它安全的原因：
 * - 模糊只用 **Haze**（`hazeEffect`）—— v3.44.0 已验证的机制；
 * - 边缘光学全部是**纯 Compose 绘制**（`drawBehind` 的 Screen 混合 + 多条 `border`），
 *   **不含** `RenderEffect` 链、`createColorFilterEffect`、AGSL `RuntimeShader`、
 *   `BlendMode.Plus` 离屏层 —— 这些都只在自研引擎里才有。
 *
 * 触发时机：`isGlassFallbackActive`（上次进程崩溃退出后，见 `ui/glass/GlassPlatform.android.kt`）。
 */
private val LegacyGlassBodyScrim = Color(0xFF0B1A2B)

@Composable
fun Modifier.legacyHazeGlass(
    hazeState: HazeState,
    shape: Shape,
    containerColor: Color,
    isTransparent: Boolean = false,
    shadowElevation: Dp = 14.dp,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this

    val effectiveBlurRadius = LocalGlassBlurRadius.current
    val tokens = appColors()
    val isDark = LocalIsDarkTheme.current

    val baseAlpha = if (isTransparent) 0.08f else if (isDark) 0.12f else 0.04f
    val veilAlpha = if (isTransparent) 0.03f else if (isDark) 0.03f else 0.02f
    val bodyScrim = if (isTransparent) 0.10f else if (isDark) 0.03f else 0.005f

    // 边缘光学力度：混合模式必须是 Screen 而非 Plus——Plus 直接把 RGB 相加，
    // 浅色内容上会瞬间顶到 255 纯白，把底下内容洗掉（实测 2217-2230 全为 255 的教训）。
    val sheenAlpha = if (isDark) 0.08f else 0.12f
    val topInnerAlpha = if (isDark) 0.12f else 0.26f
    val bottomInnerAlpha = if (isDark) 0.08f else 0.15f
    val specularAlpha = if (isDark) 0.08f else 0.16f
    val rimBright = if (isDark) 0.34f else 0.88f
    val rimDim = if (isDark) 0.07f else 0.18f
    val rimMid = if (isDark) 0.18f else 0.52f
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
            noiseFactor = 0f
            tints = listOf(
                HazeTint(legacyContainerColor(containerColor, isTransparent).copy(alpha = baseAlpha)),
                HazeTint(LegacyGlassBodyScrim.copy(alpha = bodyScrim)),
                HazeTint(Color.White.copy(alpha = veilAlpha))
            )
            fallbackTint = HazeTint(
                legacyContainerColor(containerColor, isTransparent)
                    .copy(alpha = (baseAlpha + 0.24f).coerceAtMost(0.92f))
            )
            backgroundColor = Color.Transparent
        }
        // 边缘光学：全部用 Screen 加亮、且只覆盖边缘窄带 / 左上角小椭圆，
        // 中心区域一律留空，保证底层内容清晰透出（透明感的第一优先级）。
        .drawBehind {
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
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = topInnerAlpha),
                    0.06f to Color.White.copy(alpha = topInnerAlpha * 0.5f),
                    0.14f to Color.Transparent
                ),
                blendMode = BlendMode.Screen
            )
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

private fun legacyContainerColor(containerColor: Color, isTransparent: Boolean): Color =
    if (isTransparent) Color.Black else containerColor
