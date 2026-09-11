package com.shangkeschedule.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize

/**
 * 液态玻璃**表面**修饰符：把 [backdrop] 的一份拷贝经 `effects { }` 处理后
 * 铺在本节点下方，然后依次绘制 [onDrawSurface]（表面色 / 白纱）与节点自身内容。
 *
 * 绘制顺序（与 backdrop 的 `drawBackdrop` 对齐）：
 * `背景拷贝（模糊→折射）` → `onDrawSurface` → `drawContent()`。
 *
 * 两个刻意的取舍：
 * 1. **effect 链为空时完全不建离屏层**（模糊半径 0 且未开折射）——
 *    此时只画表面色与内容，等价于本项目"关模糊只留 tint 与边缘光学"的既有语义，
 *    不为一层空玻璃付出一次离屏录制的代价；
 * 2. **不抛异常**：形状无法解析 SDF、平台不支持运行时着色器时，
 *    只退化为「模糊玻璃」，底栏在 Android 12 及以下照常可用。
 *
 * 调用方通常应先在链上 `clip(shape)`，使离屏层被裁进形状内。
 */
@Composable
fun Modifier.glassSurface(
    backdrop: GlassBackdrop,
    shape: Shape,
    effects: GlassEffectScope.() -> Unit,
    onDrawSurface: (DrawScope.() -> Unit)? = null
): Modifier {
    val effectLayer = rememberGraphicsLayer()
    val scope = remember { GlassEffectScopeImpl() }
    DisposableEffect(scope) {
        onDispose { scope.reset() }
    }
    return this then GlassSurfaceElement(
        backdrop = backdrop,
        shape = shape,
        effects = effects,
        onDrawSurface = onDrawSurface,
        layer = effectLayer,
        scope = scope
    )
}

private class GlassSurfaceElement(
    val backdrop: GlassBackdrop,
    val shape: Shape,
    val effects: GlassEffectScope.() -> Unit,
    val onDrawSurface: (DrawScope.() -> Unit)?,
    val layer: GraphicsLayer,
    val scope: GlassEffectScopeImpl
) : ModifierNodeElement<GlassSurfaceNode>() {

    override fun create(): GlassSurfaceNode =
        GlassSurfaceNode(backdrop, shape, effects, onDrawSurface, layer, scope)

    override fun update(node: GlassSurfaceNode) {
        node.backdrop = backdrop
        node.shape = shape
        node.effects = effects
        node.onDrawSurface = onDrawSurface
        node.layer = layer
        node.scope = scope
        // effects 是新 lambda ⇒ 需要重算效果链；同时重绘
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassSurface"
        properties["backdrop"] = backdrop
        properties["shape"] = shape
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlassSurfaceElement) return false
        return backdrop === other.backdrop &&
            shape == other.shape &&
            effects === other.effects &&
            onDrawSurface === other.onDrawSurface &&
            layer === other.layer &&
            scope === other.scope
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + shape.hashCode()
        result = 31 * result + effects.hashCode()
        result = 31 * result + (onDrawSurface?.hashCode() ?: 0)
        result = 31 * result + layer.hashCode()
        result = 31 * result + scope.hashCode()
        return result
    }
}

private class GlassSurfaceNode(
    var backdrop: GlassBackdrop,
    var shape: Shape,
    var effects: GlassEffectScope.() -> Unit,
    var onDrawSurface: (DrawScope.() -> Unit)?,
    var layer: GraphicsLayer,
    var scope: GlassEffectScopeImpl
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private var layoutCoordinates: LayoutCoordinates? by mutableStateOf(null)

    override fun ContentDrawScope.draw() {
        // 同步绘制环境并重算效果链：effects 块里读取的快照状态（如按压缩放进度）
        // 在 draw 阶段被观察 ⇒ 其变化会自动触发本节点重绘，无需额外的手动失效。
        scope.update(this)
        scope.apply(shape, effects)

        val padding = scope.padding
        val renderEffect = scope.renderEffect

        if (renderEffect != null) {
            layer.renderEffect = renderEffect
            val width = (size.width + padding * 2f).toInt().coerceAtLeast(1)
            val height = (size.height + padding * 2f).toInt().coerceAtLeast(1)
            // record 需要 Density 对象：此处 `this` 即 ContentDrawScope（自身实现 Density），
            // 注意不能写 `density`——那是 Float 属性
            layer.record(this, layoutDirection, IntSize(width, height)) {
                if (padding != 0f) {
                    translate(padding, padding) {
                        with(backdrop) { drawGlassBackdrop(layoutCoordinates) }
                    }
                } else {
                    with(backdrop) { drawGlassBackdrop(layoutCoordinates) }
                }
            }
            if (padding != 0f) {
                translate(-padding, -padding) { drawLayer(layer) }
            } else {
                drawLayer(layer)
            }
        }

        onDrawSurface?.invoke(this)
        drawContent()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        if (backdrop.isCoordinatesDependent) {
            layoutCoordinates = coordinates
        } else if (layoutCoordinates != null) {
            layoutCoordinates = null
        }
    }

    override fun onDetach() {
        layoutCoordinates = null
    }
}
