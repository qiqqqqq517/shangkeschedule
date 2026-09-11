package com.shangkeschedule.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.toIntSize

/**
 * 「背景快照」（backdrop）：玻璃要糊的、要折射的那层画面。
 *
 * 与 backdrop 的同名抽象一致：把某个节点的内容录进 [GraphicsLayer]，
 * 之后任何位置的玻璃件都可以按自身坐标取样它。
 */
interface GlassBackdrop {

    /**
     * 取样是否依赖"我在屏幕上的位置"。
     * [LayerGlassBackdrop] 为 true（需要按相对位移平移取样）；
     * 若是与位置无关的画布型 backdrop，则为 false，代价格外低。
     */
    val isCoordinatesDependent: Boolean

    fun DrawScope.drawGlassBackdrop(coordinates: LayoutCoordinates?)
}

/**
 * 层级背景：内容由 [Modifier.glassBackdropSource] 录制，取样时按坐标对齐。
 *
 * 坐标对齐是这套机制的关键：玻璃件往往被外层 `graphicsLayer` 平移
 * （本项目的底栏就有"下滑隐藏"的 translationY），若不换算相对位置，
 * 玻璃里透出的画面会与实际背景错位。
 */
@Stable
class LayerGlassBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    internal val onDraw: ContentDrawScope.() -> Unit
) : GlassBackdrop {

    override val isCoordinatesDependent: Boolean = true

    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)

    override fun DrawScope.drawGlassBackdrop(
        coordinates: LayoutCoordinates?
    ) {
        val coordinates = coordinates ?: return
        val layerCoordinates = layerCoordinates ?: return
        withTransform({
            val offset = try {
                layerCoordinates.localPositionOf(coordinates)
            } catch (_: Exception) {
                // 外层存在未纳入计算的变换时 localPositionOf 可能抛异常，
                // 退化为「窗口坐标之差」这一近似解（上游同样保留此兜底）。
                coordinates.positionInWindow() - layerCoordinates.positionInWindow()
            }
            translate(-offset.x, -offset.y)
        }) {
            drawLayer(graphicsLayer)
        }
    }
}

/**
 * 创建一块「层级背景」。默认 [onDraw] 即 `drawContent()` ——
 * 也就是把该节点自身的内容录成背景，等价于 haze 的 `hazeSource` 语义。
 */
@Composable
fun rememberGlassBackdrop(
    onDraw: ContentDrawScope.() -> Unit = { drawContent() }
): LayerGlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer, onDraw) { LayerGlassBackdrop(layer, onDraw) }
}

/**
 * 把被修饰节点的内容录制为 [backdrop] 的来源。
 * 与 `Modifier.hazeSource` 同构，可与之叠加使用（Haze 走它自己的录制，互不干扰）。
 */
fun Modifier.glassBackdropSource(backdrop: LayerGlassBackdrop): Modifier =
    this then GlassBackdropSourceElement(backdrop)

private class GlassBackdropSourceElement(
    val backdrop: LayerGlassBackdrop
) : ModifierNodeElement<GlassBackdropSourceNode>() {

    override fun create(): GlassBackdropSourceNode = GlassBackdropSourceNode(backdrop)

    override fun update(node: GlassBackdropSourceNode) {
        if (node.backdrop !== backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassBackdropSource"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlassBackdropSourceElement) return false
        return backdrop === other.backdrop
    }

    override fun hashCode(): Int = backdrop.hashCode()
}

private class GlassBackdropSourceNode(
    var backdrop: LayerGlassBackdrop
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    override fun ContentDrawScope.draw() {
        drawContent()
        // 内容照常绘制后，再把它录进 backdrop 的图层；recording 的 DrawScope
        // 直接复用本节点的 ContentDrawScope（`this@draw`），因此 `onDraw` 里
        // 若调用 drawContent() 就会把内容画进图层而不是屏幕。
        backdrop.graphicsLayer.record(
            density = this,
            layoutDirection = layoutDirection,
            size = size.toIntSize()
        ) {
            backdrop.onDraw(this@draw)
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }

    override fun onDetach() {
        backdrop.layerCoordinates = null
    }
}
