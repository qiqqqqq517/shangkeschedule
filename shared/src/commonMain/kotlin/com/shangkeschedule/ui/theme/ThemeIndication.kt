package com.shangkeschedule.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch

/**
 * 全局触摸指示形态（v3.43.0 · 按主题分档）。
 *
 * 三套主题的按压反馈必须落在各自的语言里，而不是共用 Material 涟漪：
 * - [SOFT_RADIAL]  柔绘：软边径向浓度渗开——无环边、无中心高光点，从触点向外"化开"；
 * - [COLOR_DARKEN] 书卷：整块底色均匀加深一档——无波纹、无扩散方向，静态纸面上不"游动"；
 * - [HIG_HIGHLIGHT] 通透：HIG highlight——整块极淡压暗/提亮，无波纹（iOS 没有涟漪）。
 *
 * 由 `ShangKeScheduleTheme` 经 `LocalIndication` 提供，`Modifier.clickable` /
 * `combinedClickable` / `selectable` 默认即取该值——全站 100+ 处交互元素一处生效，
 * 取代此前 110 处 Material 默认涟漪与仅覆盖 1 处的自研 `touchFeedback`。
 */
enum class IndicationStyle {
    /** 柔绘：软边径向浓度渗开（无环边、无高光点）。 */
    SOFT_RADIAL,

    /** 书卷：整块底色加深（无波纹、无扩散）。 */
    COLOR_DARKEN,

    /** 通透（iOS 26）：HIG highlight（无波纹）。 */
    HIG_HIGHLIGHT,
}

/**
 * 主题化 [androidx.compose.foundation.Indication] 实现。
 *
 * 用 [IndicationNodeFactory] 而非旧的 `Indication` + `remember`：
 * 每个拨动节点只创建一次绘制节点（Node），按下/抬起经 [InteractionSource] 的协程收集
 * 驱动一条 [Animatable]，不做重组——这正是 Compose 官方推荐的指示器写法。
 *
 * [color] 的 alpha 即"峰值强度"，绘制时再乘进度：调用方传入已定档的低 alpha 色即可。
 */
class ThemeIndication(
    private val style: IndicationStyle,
    private val color: Color,
    private val expandMs: Int,
    private val fadeMs: Int,
    private val easing: Easing,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        ThemeIndicationNode(style, color, expandMs, fadeMs, easing, interactionSource)

    // Indication 必须实现 equals/hashCode：否则每次重组都被判定为"换了指示器"，
    // Compose 会拆掉旧拨动节点重建，动画中途丢失。
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ThemeIndication) return false
        return style == other.style &&
            color == other.color &&
            expandMs == other.expandMs &&
            fadeMs == other.fadeMs &&
            easing == other.easing
    }

    override fun hashCode(): Int {
        var result = style.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + expandMs
        result = 31 * result + fadeMs
        result = 31 * result + easing.hashCode()
        return result
    }
}

private class ThemeIndicationNode(
    private val style: IndicationStyle,
    private val color: Color,
    private val expandMs: Int,
    private val fadeMs: Int,
    private val easing: Easing,
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode {

    /** 扩散进度 0→1（按下推进，抬起回落）。 */
    private val progress = Animatable(0f)

    private var pressPosition: Offset = Offset.Unspecified

    override fun onAttach() {
        // 时长归零（减弱动效 / 关闭分组）时整层不订阅交互，零开销
        if (expandMs <= 0 && fadeMs <= 0) return
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressPosition = interaction.pressPosition
                        progress.snapTo(0f)
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(expandMs.coerceAtLeast(1), easing = easing)
                        )
                    }

                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        progress.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(fadeMs.coerceAtLeast(1), easing = easing)
                        )
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val fraction = progress.value
        if (fraction <= 0.001f) return
        when (style) {
            // 书卷 / 通透：整块均匀加深——没有任何方向与边界，静态表面上不会产生"水波"
            IndicationStyle.COLOR_DARKEN,
            IndicationStyle.HIG_HIGHLIGHT -> drawRect(
                color = color.copy(alpha = color.alpha * fraction)
            )

            // 柔绘：软边径向浓度渗开——中心最浓、极缓慢衰减到全透明，
            // 没有环状外缘，也不画"光源点"，保证与柔绘的"无锐利硬边缘"一致
            IndicationStyle.SOFT_RADIAL -> {
                val center = if (pressPosition == Offset.Unspecified) {
                    Offset(size.width / 2f, size.height / 2f)
                } else {
                    pressPosition
                }
                val maxRadius = maxOf(size.width, size.height) * 0.95f
                val radius = maxRadius * (0.30f + 0.70f * fraction)
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to color.copy(alpha = color.alpha * fraction),
                            0.45f to color.copy(alpha = color.alpha * 0.62f * fraction),
                            0.78f to color.copy(alpha = color.alpha * 0.24f * fraction),
                            1f to Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }
    }
}
