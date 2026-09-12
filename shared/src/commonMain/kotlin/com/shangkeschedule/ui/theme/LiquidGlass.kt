package com.shangkeschedule.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import com.shangkeschedule.ui.glass.GlassBackdrop
import com.shangkeschedule.ui.glass.GlassHighlight
import com.shangkeschedule.ui.glass.GlassRefractionSettings
import com.shangkeschedule.ui.glass.GlassShadow
import com.shangkeschedule.ui.glass.GlassSurface
import com.shangkeschedule.ui.glass.LocalGlassRefraction
import com.shangkeschedule.ui.glass.glassBlur
import com.shangkeschedule.ui.glass.glassLens
import com.shangkeschedule.ui.glass.glassVibrancy

/**
 * 玻璃本体的冷灰基色：只做极淡混入，让玻璃在纯白内容上比背景略沉一档，
 * 从而显出轮廓与体积（避免暖色污染主题，故选中性偏冷的石板色）。
 */
private val LiquidGlassBodyScrim = Color(0xFF0B1A2B)

/**
 * 全应用玻璃件的**默认**高斯模糊半径。
 *
 * v3.48.1 重新标定（用户要求「从完全透明到磨砂感」）：
 * 标准档取 **8dp** —— 与参考实现（Kyant/backdrop 的 `blur(8f.dp)`）数值一致；
 * 设置页档位 0(全透明) / 4(清澈) / 8(标准) / 16(朦胧) / 24(磨砂)。
 */
val LiquidGlassBlurRadius = 8.dp

/**
 * 当前生效的玻璃模糊半径（v3.25.0 起可被用户设置覆盖）。
 *
 * 由 `ShangKeScheduleTheme` 从 `AppSettingsModel.glassBlurRadiusDp` 注入，
 * 调节入口在「外观与样式 → 个性化显示」。所有悬浮玻璃件都读这一个 Local，
 * 因此"用户调一次，全端同步"，不会重新出现各件各值。
 */
val LocalGlassBlurRadius = compositionLocalOf { LiquidGlassBlurRadius }

/**
 * 液态玻璃（Liquid Glass）**容器组件**：给悬浮层（「回到本周」圆钮 / 课程挂起条 / 玻璃 AppFab）
 * 提供统一的玻璃观感。
 *
 * v3.50.2 渲染架构变更（用户真机实证驱动）：修饰符 + 手动离屏层的实现
 * 在 Compose 1.11.1 上不渲染任何效果（模糊/折射/色散全灭，探针+真机双重实证），
 * 现改走 [GlassSurface] 容器组件——效果经背板子节点的 UI 树
 * `Modifier.graphicsLayer{renderEffect}` 应用（全平台可用路径）。
 * 结构对齐参考实现的 **LiquidButton**：`vibrancy → blur(用户模糊强度) → lens(折射设置)`
 * 效果链 + 镜面高光 + 投影，表面画三层极淡 tint。
 *
 * @param glassBackdrop 页面内容快照。由页面在**悬浮件所在的兄弟层**挂
 *   `Modifier.glassBackdropSource` 提供；传 null 时退化为纯半透明表面（无玻璃）。
 */
@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    glassBackdrop: GlassBackdrop?,
    shape: Shape,
    containerColor: Color,
    isTransparent: Boolean = false,
    shadowElevation: Dp = 14.dp,
    enabled: Boolean = true,
    refraction: GlassRefractionSettings = LocalGlassRefraction.current,
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit = {}
) {
    if (!enabled) {
        Box(modifier = modifier, content = content)
        return
    }

    val effectiveBlurRadius = LocalGlassBlurRadius.current
    val tokens = appColors()
    val isDark = LocalIsDarkTheme.current

    // 表面不透明度（v3.23.5 起「导航栏再透明一点」后的取值，保持不变）：
    // 只保留刚够压住噪点的一档，形态辨识交给边缘光学。
    val baseAlpha = if (isTransparent) 0.08f else if (isDark) 0.12f else 0.04f
    val veilAlpha = if (isTransparent) 0.03f else if (isDark) 0.03f else 0.02f
    val bodyScrim = if (isTransparent) 0.10f else if (isDark) 0.03f else 0.005f

    // v3.51.2：页内悬浮件专用 Haze 分支。这些悬浮件位于页面 glassBackdrop 的
    // source 子树内，采样它会构成渲染树自引用环（HWUI prepareTreeImpl 栈溢出，
    // Redmi/vivo X200 真机实证）。改走 legacyHazeGlass（v3.44.0 同款 Haze 实现，
    // 页面 hazeState 的 hazeSource 范围已刻意排除悬浮件——见各页 v3.23.5 修复注释，
    // 为 v3.44.0 在 X200 上长期验证安全的路径）。观感与自研玻璃分支的"无背板"态一致级别。
    if (glassBackdrop == null && hazeState != null) {
        Box(
            modifier
                .legacyHazeGlass(
                    hazeState = hazeState,
                    shape = shape,
                    containerColor = containerColor,
                    isTransparent = isTransparent,
                    shadowElevation = shadowElevation
                ),
            content = content
        )
        return
    }

    if (glassBackdrop != null) {
        GlassSurface(
            modifier = modifier,
            backdrop = glassBackdrop,
            shape = shape,
            effects = {
                glassVibrancy()
                glassBlur(effectiveBlurRadius.toPx())
                if (refraction.enabled) {
                    glassLens(
                        refractionHeight = refraction.heightDp.dp.toPx(),
                        refractionAmount = refraction.amountDp.dp.toPx(),
                        depthEffect = refraction.depthEffect,
                        chromaticAberration = refraction.dispersion
                    )
                }
            },
            highlight = { GlassHighlight.Default },
            shadow = {
                GlassShadow(radius = shadowElevation, offset = DpOffset(0.dp, shadowElevation / 6))
            },
            onDrawSurface = {
                drawRect(containerColorOrBlack(containerColor, isTransparent).copy(alpha = baseAlpha))
                drawRect(LiquidGlassBodyScrim.copy(alpha = bodyScrim))
                drawRect(Color.White.copy(alpha = veilAlpha))
            },
            content = content
        )
    } else {
        // 页面未提供背景快照：退化为纯半透明表面（投影 + 三层 tint + 顶部窄高光），
        // 不依赖玻璃引擎——该形态本来就无效果链。
        Box(
            modifier
                .shadow(
                    elevation = shadowElevation,
                    shape = shape,
                    ambientColor = tokens.shadow,
                    spotColor = tokens.shadow
                )
                .border(width = 1.dp, color = Color.Black.copy(alpha = 0.10f), shape = shape)
                .clip(shape)
                .drawBehind {
                    drawRect(containerColorOrBlack(containerColor, isTransparent).copy(alpha = baseAlpha))
                    drawRect(LiquidGlassBodyScrim.copy(alpha = bodyScrim))
                    drawRect(Color.White.copy(alpha = veilAlpha))
                    // 顶部一道窄高光，避免退化态完全"死平"
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.20f),
                            0.14f to Color.Transparent
                        )
                    )
                },
            content = content
        )
    }
}

private fun containerColorOrBlack(containerColor: Color, isTransparent: Boolean): Color =
    if (isTransparent) Color.Black else containerColor

/**
 * iOS 26 Liquid Glass **描边环**（轻量玻璃，用于不透明卡片）。
 *
 * 与 [LiquidGlass] 的分工：
 * - [LiquidGlass] 是**悬浮层**玻璃（圆钮 / 挂起条 / FAB）：带背景折射模糊、
 *   多层 tint，必须叠在可滚动内容之上才有意义；
 * - [iosGlassRim] 是**不透明卡片**的玻璃感描边：卡片本身是实底（分组列表里的白卡必须
 *   保证文字对比度，不能真的半透明），但 iOS 26 的白卡并不是纯平面——它在顶边有一道
 *   镜面高光、底边有一道极淡的反射暗边，四角还有一圈更亮的「玻璃厚度」内描边。
 *
 * 三层，全部在形状内侧（调用前应先 clip）：
 * 1. 顶边高光带（垂直渐变，仅上部 38%）—— 光线从上方打到玻璃上沿；
 * 2. 底边反射暗带（仅下部 22%）—— 玻璃下沿把环境光反射成一层深色；
 * 3. 内描边环（1dp 亮线）—— 玻璃的边缘厚度。
 *
 * 深浅模式力度不同：浅色卡在白底上需要更明显的高光才有「玻璃」感，
 * 深色卡（#1C1C1E）在纯黑页面上只需极淡的白线即可立形。
 */
@Composable
fun Modifier.iosGlassRim(
    shape: Shape,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    val isDark = LocalIsDarkTheme.current

    val topHighlight = if (isDark) 0.06f else 0.55f
    val bottomShade = if (isDark) 0.10f else 0.035f
    val rimBright = if (isDark) 0.10f else 0.95f
    val rimDim = if (isDark) 0.04f else 0.10f

    return this
        .drawBehind {
            // ① 顶边镜面高光：只覆盖上部 38%，中心与下半部保持卡片本色
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = topHighlight),
                    0.16f to Color.White.copy(alpha = topHighlight * 0.45f),
                    0.38f to Color.Transparent
                )
            )
            // ② 底边反射暗带：仅下部 22%
            drawRect(
                brush = Brush.verticalGradient(
                    0.78f to Color.Transparent,
                    1f to Color.Black.copy(alpha = bottomShade)
                )
            )
        }
        // ③ 内描边环：由亮到暗的斜向渐变，模拟玻璃边缘的厚度与方向性
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = rimBright),
                    Color.White.copy(alpha = rimDim),
                    Color.White.copy(alpha = rimBright * 0.7f),
                    Color.White.copy(alpha = rimDim)
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            ),
            shape = shape
        )
}
