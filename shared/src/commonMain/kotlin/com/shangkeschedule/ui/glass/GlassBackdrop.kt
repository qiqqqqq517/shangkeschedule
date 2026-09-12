package com.shangkeschedule.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.DefaultCameraDistance
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toIntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

    fun DrawScope.drawGlassBackdrop(
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)? = null
    )
}

/**
 * 层级背景：内容由 [Modifier.glassBackdropSource] 录制，取样时按坐标对齐。
 *
 * 坐标对齐是这套机制的关键：玻璃件往往被外层 `graphicsLayer` 平移
 * （本项目的底栏就有"下滑隐藏"的 translationY），若不换算相对位置，
 * 玻璃里透出的画面会与实际背景错位。
 *
 * [layerBlock] 与上游 backdrop 同语义：玻璃件自身形变（如按压缩放）时，
 * 背景取样要做**逆变换**，保证玻璃"贴着"形变后的位置取到正确的背景。
 */
@Stable
class LayerGlassBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    internal val onDraw: ContentDrawScope.() -> Unit
) : GlassBackdrop {

    override val isCoordinatesDependent: Boolean = true

    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)

    /**
     * 「本层正在被录制」标志（v3.51.1 修复）。
     *
     * 背景：`GlassBackdropSourceNode.draw()` 会在 `record()` 块内手工交换
     * `drawContext.canvas` 后执行 `backdrop.onDraw`（默认 `drawContent()`）把**整页内容**
     * 画进录制层。当页面内容里恰好包含玻璃件（悬浮 FAB / 圆钮 / 挂起条 / `LiquidGlassTabs`），
     * 这些玻璃件的 `GlassSurface` 背板在 `drawGlassBackdrop` 中会 `drawLayer(本层)`——
     * 即**在本层录制期间又把本层画进来**，形成渲染树自引用环。
     *
     * 该环在 HWUI 的 `SkiaDisplayList::prepareListAndChildren` 上表现为**无限递归直至
     * RenderThread 栈溢出**（真机 Redmi/vivo X200 实测：`Fatal signal 11 (SIGSEGV)`、
     * `Cause: stack overflow`。课表横向切周时大范围重组触发，故"切周闪退、普通滑动不崩"）。
     *
     * 修复：录制入口置位、退出复位；`drawGlassBackdrop` 发现自己正在被录制时**跳过本次
     * `drawLayer`**（该帧玻璃取不到自身背景，视觉上只是遮罩退一帧，不影响稳定态效果），
     * 从而切断自引用环。此标志由 `GlassBackdropSourceNode` 维护，在 Draw 阶段读写、无并发风险。
     */
    internal var isRecording: Boolean = false

    private var inverseLayerScope: InverseLayerScope? = null

    override fun DrawScope.drawGlassBackdrop(
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        // 自引用环防御（v3.51.1）：本层正在被录制（即 · 我是当前 record 的目标层）时，
        // drawLayer(自己) 会构成渲染树环导致 RenderThread 栈溢出 ⇒ 本帧跳过背景采样。
        if (isRecording) return
        val coordinates = coordinates ?: return
        val layerCoordinates = layerCoordinates ?: return
        withTransform({
            if (layerBlock != null) {
                with(obtainInverseLayerScope()) {
                    this@withTransform.inverseTransform(
                        density = this@drawGlassBackdrop,
                        scope = this@drawGlassBackdrop,
                        layerBlock = layerBlock
                    )
                }
            }
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

    private fun obtainInverseLayerScope(): InverseLayerScope {
        return inverseLayerScope?.apply { reset() }
            ?: InverseLayerScope().also { inverseLayerScope = it }
    }
}

/**
 * 把两块背景叠加取样（后画的盖在先画的上面）。
 * 用于「选中指示器」玻璃：底下透**页面内容 + Tab 文字层**两层。
 */
@Stable
class CombinedGlassBackdrop(
    private val backdrop1: GlassBackdrop,
    private val backdrop2: GlassBackdrop
) : GlassBackdrop {

    override val isCoordinatesDependent: Boolean =
        backdrop1.isCoordinatesDependent || backdrop2.isCoordinatesDependent

    override fun DrawScope.drawGlassBackdrop(
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        with(backdrop1) { drawGlassBackdrop(coordinates, layerBlock) }
        with(backdrop2) { drawGlassBackdrop(coordinates, layerBlock) }
    }
}

@Composable
fun rememberCombinedGlassBackdrop(
    backdrop1: GlassBackdrop,
    backdrop2: GlassBackdrop
): GlassBackdrop = remember(backdrop1, backdrop2) { CombinedGlassBackdrop(backdrop1, backdrop2) }

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
        val layer = backdrop.graphicsLayer
        layer.record(
            density = this,
            layoutDirection = layoutDirection,
            size = size.toIntSize()
        ) {
            // ── v3.50.2 根因修复：把内容真正录进层 ──────────────────────────────
            // 录制块里的 `this` 才是「录制画布」作用域；`backdrop.onDraw(this@draw)`
            // （默认实现 `drawContent()`）走的是**外层**作用域，而外层作用域经
            // `drawContext` 绘制 —— 若不做处理，内容会画到**屏幕画布**上，
            // 录制层永远为空。
            //
            // 上游（Kyant0/backdrop 的 internal/LayerRecorder）依赖 Compose 1.12 的
            // `record(size){}`：该重载会临时把**调用方**的 `drawContext.canvas`
            // 指向录制画布（层内交换 density 也正是同一 `drawContext` 才讲得通）。
            // 本项目 Compose 1.11.1 的 `record(density, layoutDirection, size, block)`
            // **不给调用方换画布**，于是上游写法在这里静默失效：
            // 玻璃件全部采样到空层 ⇒ 模糊 / 折射 / 色散同时零像素输出
            //（离屏探针三配置差分全 0、真机 A/B 逐位相同、双平台一致失效的根因）。
            //
            // 这里手工补上这一次画布交换（与 Haze 的录制同款手法），使 1.11.1
            // 具备与 1.12 `record(size){}` 相同的语义；try/finally 保证必然复原，
            // 且因交换的是共享 `drawContext` 的 canvas，嵌套录制（B 层录 Tab 内容）
            // 也能正确逐层重定向。
            val nodeContext = this@draw.drawContext
            val screenCanvas = nodeContext.canvas
            nodeContext.canvas = this.drawContext.canvas
            // v3.51.1：置位「本层录制中」，使本层内嵌玻璃件在 drawGlassBackdrop 时
            // 跳过 drawLayer(本层)，切断渲染树自引用环（避免 RenderThread 栈溢出）。
            backdrop.isRecording = true
            try {
                backdrop.onDraw(this@draw)
            } finally {
                backdrop.isRecording = false
                nodeContext.canvas = screenCanvas
            }
        }
        drawLayer(layer)
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

/**
 * graphicsLayer 逆变换作用域：把玻璃件的 layerBlock 形变（缩放/旋转）
 * 反向施加到背景取样上，移植自 backdrop 的 InverseLayerScope（Apache-2.0, Kyant）。
 */
internal class InverseLayerScope : GraphicsLayerScope {

    override var size: Size = Size.Unspecified
    override var density: Float = 1f
    override var fontScale: Float = 1f

    override var scaleX: Float = 1f
    override var scaleY: Float = 1f
    override var alpha: Float = 0f
    override var translationX: Float = 0f
    override var translationY: Float = 0f
    override var shadowElevation: Float = 0f
    override var ambientShadowColor: Color = DefaultShadowColor
    override var spotShadowColor: Color = DefaultShadowColor
    override var rotationX: Float = 0f
    override var rotationY: Float = 0f
    override var rotationZ: Float = 0f
    override var cameraDistance: Float = DefaultCameraDistance
    override var transformOrigin: TransformOrigin = TransformOrigin.Center
    override var shape: Shape = RectangleShape
    override var clip: Boolean = false
    override var renderEffect: RenderEffect? = null
    override var blendMode: BlendMode = BlendMode.SrcOver
    override var colorFilter: androidx.compose.ui.graphics.ColorFilter? = null
    override var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto

    private var matrix: Matrix? = null

    fun DrawTransform.inverseTransform(
        density: Density,
        scope: DrawScope,
        layerBlock: GraphicsLayerScope.() -> Unit
    ) {
        this@InverseLayerScope.size = scope.size
        this@InverseLayerScope.density = density.density
        fontScale = density.fontScale

        layerBlock()

        inverseTransformAtTopLeft(
            rotationZ = rotationZ,
            scaleX = scaleX,
            scaleY = scaleY
        )
    }

    fun reset() {
        size = Size.Unspecified
        density = 1f
        fontScale = 1f

        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        translationX = 0f
        translationY = 0f
        shadowElevation = 0f
        ambientShadowColor = DefaultShadowColor
        spotShadowColor = DefaultShadowColor
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
        cameraDistance = DefaultCameraDistance
        transformOrigin = TransformOrigin.Center
        shape = RectangleShape
        clip = false
        renderEffect = null
        blendMode = BlendMode.SrcOver
        colorFilter = null
        compositingStrategy = CompositingStrategy.Auto

        matrix = null
    }

    private fun DrawTransform.inverseTransformAtTopLeft(
        rotationZ: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f
    ) {
        if (rotationZ == 0f) {
            if (scaleX != 0f && scaleY != 0f) {
                scale(1f / scaleX, 1f / scaleY, Offset.Zero)
            }
            return
        }

        val matrix = matrix ?: Matrix().also { matrix = it }
        if (matrix.values.size < 16) return

        val rz = rotationZ * (PI / 180.0)
        val rsz = sin(rz).toFloat()
        val rcz = cos(rz).toFloat()

        val a00 = rcz * scaleX
        val a01 = rsz * scaleY
        val a10 = -rsz * scaleX
        val a11 = rcz * scaleY

        val det = a00 * a11 - a01 * a10
        if (det == 0f) return
        val invDet = 1f / det
        matrix[0, 0] = a11 * invDet
        matrix[0, 1] = -a01 * invDet
        matrix[1, 0] = -a10 * invDet
        matrix[1, 1] = a00 * invDet

        transform(matrix)
    }
}
