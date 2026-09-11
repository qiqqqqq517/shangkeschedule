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
import com.shangkeschedule.ui.glass.GlassBackdrop
import com.shangkeschedule.ui.glass.GlassRefractionSettings
import com.shangkeschedule.ui.glass.LocalGlassRefraction
import com.shangkeschedule.ui.glass.glassBlur
import com.shangkeschedule.ui.glass.glassLens
import com.shangkeschedule.ui.glass.glassSurface
import com.shangkeschedule.ui.glass.isGlassRefractionAvailable

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
    enabled: Boolean = true,
    /**
     * v3.47.0 新增：背景快照。传入且 [refraction] 打开、平台支持时，本修饰符切换到
     * 自带玻璃引擎（同样的模糊 + **边缘折射**）；否则完全走原 Haze 路径（默认路径）。
     */
    glassBackdrop: GlassBackdrop? = null,
    /** v3.47.0 新增：折射配置，由 [LocalGlassRefraction] 全局注入。 */
    refraction: GlassRefractionSettings = LocalGlassRefraction.current
): Modifier {
    if (!enabled) return this

    // v3.47.0：是否切到自带引擎。注意**模糊半径两条路径共用同一个 effectiveBlurRadius**，
    // 即用户的「最下侧模糊强度」设置不会被旁路——折射只是叠加在原雾度之上的光学层。
    val refractionBackdrop = glassBackdrop?.takeIf {
        refraction.enabled && isGlassRefractionAvailable()
    }

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
        .then(
            if (refractionBackdrop != null) {
                // v3.47.0：自带引擎路径。模糊半径取与 Haze 路径同一个 effectiveBlurRadius
                //（= 用户「最下侧模糊强度」），再叠加边缘折射 / 色散 / 厚度感。
                Modifier.glassSurface(
                    backdrop = refractionBackdrop,
                    shape = shape,
                    effects = {
                        glassBlur(effectiveBlurRadius.toPx())
                        glassLens(
                            refractionHeight = refraction.heightDp.dp.toPx(),
                            refractionAmount = refraction.amountDp.dp.toPx(),
                            depthEffect = refraction.depthEffect,
                            chromaticAberration = refraction.dispersion
                        )
                    },
                    // 表面三层遮盖与 Haze 路径的 tints 逐值对应：两条路径观感同源，
                    // 切换开关时不会出现"底栏突然换了一块玻璃"的跳变。
                    onDrawSurface = {
                        drawRect(containerColorOrBlack(containerColor, isTransparent).copy(alpha = baseAlpha))
                        drawRect(LiquidGlassBodyScrim.copy(alpha = bodyScrim))
                        drawRect(Color.White.copy(alpha = veilAlpha))
                    }
                )
            } else {
                Modifier.hazeEffect(hazeState) {
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
            }
        )
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

/**
 * iOS 26 Liquid Glass **描边环**（轻量玻璃，用于不透明卡片）。
 *
 * 与 [liquidGlass] 的分工：
 * - [liquidGlass] 是**悬浮层**玻璃（底栏胶囊 / 圆钮 / 挂起条 / FAB）：带 haze 背景模糊、
 *   多层 tint 与大面积边缘光学，必须叠在可滚动内容之上才有意义；
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
