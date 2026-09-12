package com.shangkeschedule.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost
import kotlin.math.PI
import kotlin.math.ceil

/*
 * 玻璃装饰层：Highlight（镜面高光）/ Shadow（投影）/ InnerShadow（内阴影）。
 *
 * 三者均移植自 Kyant/backdrop（仓库 Kyant0/AndroidLiquidGlass，Apache License 2.0，
 * Copyright 2025 Kyant）的同名实现，绘制顺序与修饰符链位置保持一致：
 * `graphicsLayer(layerBlock) → innerShadow → shadow → highlight → glassSurface`。
 */

/**
 * 镜面高光：形状描边一圈的方向性亮边（45° 光照角），
 * 通过 [HIGHLIGHT_AGSL] 着色器让"迎光"的边缘更亮。
 */
@Immutable
data class GlassHighlight(
    val width: Dp = 0.5f.dp,
    val blurRadius: Dp = width / 2f,
    val alpha: Float = 1f,
    val color: Color = Color.White.copy(alpha = 0.5f),
    val blendMode: BlendMode = BlendMode.Plus,
    val angle: Float = 45f,
    val falloff: Float = 1f
) {
    companion object {
        @Stable
        val Default: GlassHighlight = GlassHighlight()
    }
}

/** 投影：把玻璃从底层内容上托起来。 */
@Immutable
data class GlassShadow(
    val radius: Dp = 24f.dp,
    val offset: DpOffset = DpOffset(0f.dp, radius / 6f),
    val color: Color = Color.Black.copy(alpha = 0.1f),
    val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode
) {
    companion object {
        @Stable
        val Default: GlassShadow = GlassShadow()
    }
}

/** 内阴影：玻璃内壁的暗边，按压时出现（厚度被压实的观感）。 */
@Immutable
data class GlassInnerShadow(
    val radius: Dp = 8f.dp,
    val color: Color = Color.Black,
    val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode
)

/** 形状描边裁剪辅助：Rounded 轮廓用 Path 复用，避免每帧新建。 */
private fun androidx.compose.ui.graphics.Canvas.clipGlassOutline(outline: Outline, path: Path?) {
    when (outline) {
        is Outline.Rectangle -> clipRect(outline.rect)
        is Outline.Rounded -> {
            path!!.rewind()
            path.addRoundRect(outline.roundRect)
            clipPath(path)
        }
        is Outline.Generic -> clipPath(outline.path)
    }
}

// ---------------------------------------------------------------------------
// Highlight
// ---------------------------------------------------------------------------

fun Modifier.glassHighlight(
    shape: Shape,
    highlight: () -> GlassHighlight?
): Modifier = this then GlassHighlightElement(shape, highlight)

private class GlassHighlightElement(
    val shape: Shape,
    val highlight: () -> GlassHighlight?
) : ModifierNodeElement<GlassHighlightNode>() {

    override fun create(): GlassHighlightNode = GlassHighlightNode(shape, highlight)

    override fun update(node: GlassHighlightNode) {
        node.shape = shape
        node.highlight = highlight
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassHighlight"
        properties["shape"] = shape
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlassHighlightElement) return false
        return shape == other.shape && highlight == other.highlight
    }

    override fun hashCode(): Int = 31 * shape.hashCode() + highlight.hashCode()
}

private class GlassHighlightNode(
    var shape: Shape,
    var highlight: () -> GlassHighlight?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var highlightLayer: GraphicsLayer? = null

    private val paint = Paint().apply { style = PaintingStyle.Stroke }
    private var clipPath: Path? = null

    private var highlightShader: LiquidShader? = null
    private var shaderCreated = false

    override fun ContentDrawScope.draw() {
        val highlight = highlight()
        if (highlight == null || highlight.width.value <= 0f) {
            return drawContent()
        }

        drawContent()

        val highlightLayer = highlightLayer ?: return
        val size = size
        val density: Density = this
        val layoutDirection = layoutDirection

        val safeSize = IntSize(
            ceil(size.width).toInt() + 2,
            ceil(size.height).toInt() + 2
        )

        val outline = shape.createOutline(size, layoutDirection, density)
        val clipPath =
            if (outline is Outline.Rounded) {
                clipPath ?: Path().also { clipPath = it }
            } else {
                null
            }

        configurePaint(highlight)

        highlightLayer.alpha = highlight.alpha
        highlightLayer.blendMode = highlight.blendMode
        highlightLayer.record(safeSize) {
            translate(1f, 1f) {
                val canvas = drawContext.canvas
                canvas.save()
                canvas.clipGlassOutline(outline, clipPath)
                canvas.drawOutline(outline, paint)
                canvas.restore()
            }
        }

        translate(-1f, -1f) {
            drawLayer(highlightLayer)
        }
    }

    private fun DrawScope.configurePaint(highlight: GlassHighlight) {
        paint.color = highlight.color
        paint.strokeWidth = ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f
        paint.liquidBlur(highlight.blurRadius.toPx())
        if (isLiquidRefractionSupported()) {
            if (!shaderCreated) {
                shaderCreated = true
                highlightShader = runCatching { createLiquidShader(HIGHLIGHT_AGSL) }.getOrNull()
            }
            val shader = highlightShader
            if (shader != null) {
                shader.setFloatUniform("size", floatArrayOf(size.width, size.height))
                shader.setFloatUniform("cornerRadii", cornerRadiiOf(shape))
                shader.setColorUniform("color", highlight.color.copy(alpha = 1f))
                shader.setFloatUniform("angle", highlight.angle * (PI / 180f).toFloat())
                shader.setFloatUniform("falloff", highlight.falloff)
            }
            paint.liquidSetShader(shader)
        } else {
            paint.liquidSetShader(null)
        }
    }

    private fun DrawScope.cornerRadiiOf(shape: Shape): FloatArray {
        val maxRadius = size.minDimension / 2f
        val radii = (shape as? androidx.compose.foundation.shape.CornerBasedShape)
            ?: return FloatArray(4) { maxRadius }
        val isLtr = layoutDirection == LayoutDirection.Ltr
        val topLeft = (if (isLtr) radii.topStart else radii.topEnd).toPx(size, this)
        val topRight = (if (isLtr) radii.topEnd else radii.topStart).toPx(size, this)
        val bottomRight = (if (isLtr) radii.bottomEnd else radii.bottomStart).toPx(size, this)
        val bottomLeft = (if (isLtr) radii.bottomStart else radii.bottomEnd).toPx(size, this)
        return floatArrayOf(
            topLeft.fastCoerceAtMost(maxRadius),
            topRight.fastCoerceAtMost(maxRadius),
            bottomRight.fastCoerceAtMost(maxRadius),
            bottomLeft.fastCoerceAtMost(maxRadius)
        )
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer = graphicsContext.createGraphicsLayer()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            highlightLayer = null
        }
        clipPath = null
        highlightShader = null
        shaderCreated = false
    }
}

// ---------------------------------------------------------------------------
// Shadow
// ---------------------------------------------------------------------------

fun Modifier.glassShadow(
    shape: Shape,
    shadow: () -> GlassShadow?
): Modifier = this then GlassShadowElement(shape, shadow)

private class GlassShadowElement(
    val shape: Shape,
    val shadow: () -> GlassShadow?
) : ModifierNodeElement<GlassShadowNode>() {

    override fun create(): GlassShadowNode = GlassShadowNode(shape, shadow)

    override fun update(node: GlassShadowNode) {
        node.shape = shape
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassShadow"
        properties["shape"] = shape
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlassShadowElement) return false
        return shape == other.shape && shadow == other.shadow
    }

    override fun hashCode(): Int = 31 * shape.hashCode() + shadow.hashCode()
}

private val ShadowMaskPaint = Paint().apply { blendMode = BlendMode.Clear }

private class GlassShadowNode(
    var shape: Shape,
    var shadow: () -> GlassShadow?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()

    override fun ContentDrawScope.draw() {
        val shadow = shadow() ?: return drawContent()

        val shadowLayer = shadowLayer ?: return
        val size = size
        val density: Density = this
        val layoutDirection = layoutDirection

        val radius = shadow.radius.toPx()
        val offsetX = shadow.offset.x.toPx()
        val offsetY = shadow.offset.y.toPx()
        val shadowSize = IntSize(
            ceil(size.width + radius * 4f + offsetX).toInt(),
            ceil(size.height + radius * 4f + offsetY).toInt()
        )
        val outline = shape.createOutline(size, layoutDirection, density)

        paint.color = shadow.color
        paint.liquidBlur(shadow.radius.toPx())

        shadowLayer.alpha = shadow.alpha
        shadowLayer.blendMode = shadow.blendMode
        shadowLayer.record(shadowSize) {
            translate(radius * 2f + offsetX, radius * 2f + offsetY) {
                val canvas = drawContext.canvas
                canvas.drawOutline(outline, paint)
                canvas.translate(-offsetX, -offsetY)
                canvas.drawOutline(outline, ShadowMaskPaint)
                canvas.translate(offsetX, offsetY)
            }
        }

        translate(-radius * 2f, -radius * 2f) {
            drawLayer(shadowLayer)
        }

        drawContent()
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer = graphicsContext.createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            shadowLayer = null
        }
    }
}

// ---------------------------------------------------------------------------
// InnerShadow
// ---------------------------------------------------------------------------

fun Modifier.glassInnerShadow(
    shape: Shape,
    shadow: () -> GlassInnerShadow?
): Modifier = this then GlassInnerShadowElement(shape, shadow)

private class GlassInnerShadowElement(
    val shape: Shape,
    val shadow: () -> GlassInnerShadow?
) : ModifierNodeElement<GlassInnerShadowNode>() {

    override fun create(): GlassInnerShadowNode = GlassInnerShadowNode(shape, shadow)

    override fun update(node: GlassInnerShadowNode) {
        node.shape = shape
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassInnerShadow"
        properties["shape"] = shape
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlassInnerShadowElement) return false
        return shape == other.shape && shadow == other.shadow
    }

    override fun hashCode(): Int = 31 * shape.hashCode() + shadow.hashCode()
}

private class GlassInnerShadowNode(
    var shape: Shape,
    var shadow: () -> GlassInnerShadow?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()
    private var clipPath: Path? = null
    private var prevRadius = Float.NaN

    override fun ContentDrawScope.draw() {
        drawContent()

        if (!isLiquidRenderEffectSupported()) return

        val shadow = shadow() ?: return

        val shadowLayer = shadowLayer ?: return
        val size = size
        val density: Density = this
        val layoutDirection = layoutDirection

        val radius = shadow.radius.toPx()
        val offsetX = 0f
        val offsetY = shadow.radius.toPx() / 6f

        val outline = shape.createOutline(size, layoutDirection, density)
        val clipPath =
            if (outline is Outline.Rounded) {
                clipPath ?: Path().also { clipPath = it }
            } else {
                null
            }

        paint.color = shadow.color

        shadowLayer.alpha = shadow.alpha
        shadowLayer.blendMode = shadow.blendMode
        if (prevRadius != radius) {
            shadowLayer.renderEffect =
                if (radius > 0f) {
                    androidx.compose.ui.graphics.BlurEffect(radius, radius, androidx.compose.ui.graphics.TileMode.Decal)
                } else {
                    null
                }
            prevRadius = radius
        }
        shadowLayer.record {
            val canvas = drawContext.canvas
            canvas.save()
            canvas.clipGlassOutline(outline, clipPath)
            canvas.drawOutline(outline, paint)
            canvas.translate(offsetX, offsetY)
            canvas.drawOutline(outline, ShadowMaskPaint)
            canvas.translate(-offsetX, -offsetY)
            canvas.restore()
        }

        val canvas = drawContext.canvas
        canvas.save()
        canvas.clipGlassOutline(outline, clipPath)
        drawLayer(shadowLayer)
        canvas.restore()
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer = graphicsContext.createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            shadowLayer = null
        }
    }
}
