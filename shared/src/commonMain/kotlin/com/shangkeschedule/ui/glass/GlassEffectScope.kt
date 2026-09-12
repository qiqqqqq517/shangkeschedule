package com.shangkeschedule.ui.glass

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceAtMost

/**
 * 玻璃效果作用域：在 [Modifier.glassSurface] 的 `effects { }` 块内描述
 * 「背景拷贝要经过哪些光学处理」。
 *
 * 与 backdrop 的 `BackdropEffectScope` 同构，但按本项目 Compose 1.11.1 的能力裁剪：
 * - 只保留 blur 与 lens（颜色滤镜在 commonMain 缺 `createColorFilterEffect`，
 *   而底栏的表面色本来就用矩形 tint 绘制，无需颜色滤镜）；
 * - [padding] 的语义与上游一致：把离屏层的录制范围向外扩 N 像素，
 *   让模糊在形状边缘外仍有真实像素可用，避免边缘发暗 / 拖影。
 *
 * 实现同时充当 [Density]，因此 `effects { }` 内可直接写 `24.dp.toPx()`。
 */
sealed interface GlassEffectScope : Density {

    val size: Size

    val layoutDirection: LayoutDirection

    val shape: Shape

    /** 当前形状的四角半径（左上/右上/右下/左下），仅圆角类形状可得；不支持时返回 null。 */
    val cornerRadii: FloatArray?

    var padding: Float

    var renderEffect: RenderEffect?

    /**
     * v3.50.2 UI 树路径（[GlassSurface] 容器组件）置 false：层即节点本身，
     * 无「离屏层外扩采样」——[glassBlur] 不扩 padding、[glassLens] 不收缩/偏移。
     */
    val paddingEnabled: Boolean

    /** 按 [key] 复用运行时着色器（避免每帧重新编译 AGSL）。 */
    fun obtainShader(key: String, agsl: String): LiquidShader?

    /**
     * 临时诊断：同一 [key] 只打印一次。
     * 玻璃效果链一旦某环节静默失败（着色器编译失败 / 无法包装成 RenderEffect），
     * 表现就是「模糊、折射、色散全部没作用」，必须有日志可查。
     */
    fun reportOnce(key: String, message: () -> String)
}

internal class GlassEffectScopeImpl : GlassEffectScope {

    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var size: Size = Size.Unspecified
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override var padding: Float = 0f
    override var renderEffect: RenderEffect? = null

    /**
     * v3.50.2：效果改由 UI 树 `Modifier.graphicsLayer{renderEffect}` 子节点承载后，
     * 不再存在「离屏层外扩采样」——padding 语义整体作废。置 false 时
     * [glassBlur] 不扩 padding、[glassLens] 不收缩/偏移 padding（shader offset 恒 0）。
     */
    private var _paddingEnabled: Boolean = true

    override val paddingEnabled: Boolean get() = _paddingEnabled

    private var currentShape: Shape = RectangleShape

    override val shape: Shape get() = currentShape

    /** 按需从当前形状解析；不支持圆角解析的形状返回 null（折射会据此跳过）。 */
    override val cornerRadii: FloatArray? get() = resolveCornerRadii()

    private val shaders = mutableMapOf<String, LiquidShader?>()

    private val reported = mutableSetOf<String>()

    override fun reportOnce(key: String, message: () -> String) {
        if (reported.add(key)) {
            println("GLASS: " + message())
        }
    }

    override fun obtainShader(key: String, agsl: String): LiquidShader? {
        if (!isLiquidRefractionSupported()) return null
        return shaders.getOrPut(key) {
            val result = runCatching { createLiquidShader(agsl) }
            // 编译失败必须留痕：此前静默返回 null 会让「折射/色散完全没作用」无从排查
            result.exceptionOrNull()?.let { err ->
                println("GLASS: 运行时着色器编译失败 key=$key, ${err.message}")
            }
            result.getOrNull()
        }
    }

    /**
     * 同步绘制环境（尺寸 / 密度 / 书写方向）。返回 true 表示发生了变化，
     * 调用方据此决定是否需要重算效果。
     */
    fun update(scope: DrawScope): Boolean {
        val newDensity = scope.density
        val newFontScale = scope.fontScale
        val newSize = scope.size
        val newLayoutDirection = scope.layoutDirection

        val changed = newDensity != density ||
            newFontScale != fontScale ||
            newSize != size ||
            newLayoutDirection != layoutDirection

        if (changed) {
            density = newDensity
            fontScale = newFontScale
            size = newSize
            layoutDirection = newLayoutDirection
        }
        return changed
    }

    /**
     * 每帧重算：清空 padding / 效果链（着色器缓存保留），再执行用户的 effects 块。
     */
    fun apply(shape: Shape, effects: GlassEffectScope.() -> Unit) {
        currentShape = shape
        padding = 0f
        renderEffect = null
        effects()
    }

    /**
     * v3.50.2 UI 树路径专用：由 `Modifier.graphicsLayer{}` 块在每帧调用。
     * 层参数（密度/尺寸）由 GraphicsLayerScope 提供；padding 强制关闭。
     */
    fun applyForLayer(
        shape: Shape,
        density: Float,
        fontScale: Float,
        size: Size,
        layoutDirection: LayoutDirection,
        effects: GlassEffectScope.() -> Unit
    ) {
        this.density = density
        this.fontScale = fontScale
        this.size = size
        this.layoutDirection = layoutDirection
        currentShape = shape
        padding = 0f
        renderEffect = null
        _paddingEnabled = false
        try {
            effects()
        } finally {
            _paddingEnabled = true
        }
    }

    fun reset() {
        density = 1f
        fontScale = 1f
        size = Size.Unspecified
        layoutDirection = LayoutDirection.Ltr
        currentShape = RectangleShape
        padding = 0f
        renderEffect = null
        shaders.clear()
    }
}

/**
 * 高斯模糊。
 *
 * 与上游不同：这里**始终**把 [GlassEffectScope.padding] 扩到模糊半径
 * （上游仅在非 Clamp 或已有前置效果时扩）。原因是本库的模糊几乎总是接在折射之前，
 * 折射会采样形状内缘一圈，若录制范围没有外扩，边缘一圈拿不到真实背景像素，
 * 会表现为一圈发暗的"脏边"。
 */
fun GlassEffectScope.glassBlur(
    radius: Float,
    edgeTreatment: TileMode = TileMode.Clamp
) {
    if (radius <= 0f) return
    // 上游 Blur.kt 逐行：Clamp 且无前置效果时不需要外扩（边缘由 Clamp 补齐）。
    // v3.50.2 UI 树路径 paddingEnabled=false：层即节点本身，无外扩可言。
    if (paddingEnabled && (edgeTreatment != TileMode.Clamp || renderEffect != null)) {
        if (radius > padding) {
            padding = radius
        }
    }

    renderEffect =
        BlurEffect(
            renderEffect,
            radius,
            radius,
            edgeTreatment
        )
}

/**
 * 色彩控制滤镜（brightness / contrast / saturation）。
 * 移植自 backdrop 的 `colorControls`（Apache-2.0, Kyant）。
 *
 * 放在效果链最前（最内层），与上游顺序一致：color filter ⇒ blur ⇒ lens。
 */
fun GlassEffectScope.glassColorControls(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f
) {
    if (brightness == 0f && contrast == 1f && saturation == 1f) {
        return
    }
    if (!isLiquidRenderEffectSupported()) return

    val effect = liquidColorFilterEffect(colorControlsColorFilter(brightness, contrast, saturation))
        ?: return
    renderEffect = chainLiquidEffects(renderEffect, effect)
}

/**
 * 鲜艳度：饱和度 1.5 的色彩滤镜，让玻璃后的内容更"水灵"。
 * 与上游 backdrop 的 `vibrancy()` 完全同一参数。
 */
fun GlassEffectScope.glassVibrancy() {
    if (!isLiquidRenderEffectSupported()) {
        reportOnce("vibrancy") { "vibrancy 跳过（平台不支持 RenderEffect）" }
        return
    }

    val effect = liquidColorFilterEffect(VibrantColorFilter) ?: run {
        reportOnce("vibrancy") { "vibrancy 跳过（色彩滤镜无法包装成 RenderEffect）" }
        return
    }
    renderEffect = chainLiquidEffects(renderEffect, effect)
}

private val VibrantColorFilter = colorControlsColorFilter(saturation = 1.5f)

private fun colorControlsColorFilter(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f
): ColorFilter {
    val invSat = 1f - saturation
    val r = 0.213f * invSat
    val g = 0.715f * invSat
    val b = 0.072f * invSat

    val c = contrast
    val t = (0.5f - c * 0.5f + brightness) * 255f
    val s = saturation

    val cr = c * r
    val cg = c * g
    val cb = c * b
    val cs = c * s

    val colorMatrix = ColorMatrix(
        floatArrayOf(
            cr + cs, cg, cb, 0f, t,
            cr, cg + cs, cb, 0f, t,
            cr, cg, cb + cs, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )
    )
    return ColorMatrixColorFilter(colorMatrix)
}

/**
 * 边缘折射（透镜）。
 *
 * @param refractionHeight 折射带宽度，单位 px。
 * @param refractionAmount 折射位移量，单位 px。
 * @param depthEffect 是否叠加"厚度"感：把 SDF 梯度与**径向**梯度混合。
 *   ⚠️ 参考实现（Kyant/backdrop 的 LiquidBottomTabs）**从不开**这一项（默认 false）——
 *   径向分量把采样拉向形状中心，在"短边很小"的胶囊上会读成"中间被挤压"。
 * @param chromaticAberration 是否开启彩虹色散（沿形状四周整圈彩边）。
 *
 * v3.48.1 起参数语义完全还原上游（v3.48.0 的"按形状短边收口"已删除）：
 * 折射带 / 位移原样透传，不做任何钳制 —— 过大的折射带（如 32dp ≥ 64dp 胶囊的半高）
 * 会覆盖整条玻璃，属用户自选观感，不再代做决定。
 *
 * 形状不是圆角矩形类时**静默跳过**（绝不抛异常）。
 */
fun GlassEffectScope.glassLens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false
) {
    if (!isLiquidRefractionSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    val radii = cornerRadii ?: return
    // 上游 Lens.kt 逐行：lens 会收缩 blur 留下的 padding（折射位移向内拉，
    // 不需要向外扩采样范围）。v3.50.2 UI 树路径 paddingEnabled=false：offset 恒 0。
    if (paddingEnabled && padding > 0f) {
        padding = (padding - refractionHeight).fastCoerceAtLeast(0f)
    }

    val agsl = if (chromaticAberration) REFRACTION_DISPERSION_AGSL else REFRACTION_AGSL
    val key = if (chromaticAberration) "RefractionDispersion" else "Refraction"
    val shader = obtainShader(key, agsl) ?: return

    shader.setFloatUniform("size", floatArrayOf(size.width, size.height))
    shader.setFloatUniform("offset", floatArrayOf(-padding, -padding))
    shader.setFloatUniform("cornerRadii", radii)
    shader.setFloatUniform("refractionHeight", refractionHeight)
    // 取负：着色器里法线朝外，负位移把采样点拉向形状中心 ⇒ 边缘放大
    shader.setFloatUniform("refractionAmount", -refractionAmount)
    shader.setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
    if (chromaticAberration) {
        shader.setFloatUniform("chromaticAberration", 1f)
    }

    val effect = liquidShaderEffect(shader, "content")
    if (effect == null) {
        reportOnce("lens-null-$key") { "liquidShaderEffect 返回 null，折射被跳过 key=$key" }
        return
    }
    renderEffect = chainLiquidEffects(renderEffect, effect)
}

/**
 * 取得形状的四角半径。支持 [CornerBasedShape]（含 `RoundedCornerShape` / `CircleShape`）
 * 与 [AbsoluteRoundedCornerShape]；其它形状（如 `GenericShape`）返回 null ⇒ 不做折射。
 *
 * 实现要点与上游一致：半径按 [LayoutDirection] 解析 start/end，并以短边一半封顶，
 * 否则胶囊形状（percent = 50%）会算出超过半高的半径，SDF 出现退化。
 */
internal fun GlassEffectScope.resolveCornerRadii(): FloatArray? {
    if (size.width <= 0f || size.height <= 0f) return null
    val maxRadius = size.minDimension / 2f
    val isLtr = layoutDirection == LayoutDirection.Ltr

    return when (val shape = shape) {
        is AbsoluteRoundedCornerShape -> floatArrayOf(
            shape.topStart.toPx(size, this).fastCoerceAtMost(maxRadius),
            shape.topEnd.toPx(size, this).fastCoerceAtMost(maxRadius),
            shape.bottomEnd.toPx(size, this).fastCoerceAtMost(maxRadius),
            shape.bottomStart.toPx(size, this).fastCoerceAtMost(maxRadius)
        )

        is CornerBasedShape -> floatArrayOf(
            (if (isLtr) shape.topStart else shape.topEnd).toPx(size, this).fastCoerceAtMost(maxRadius),
            (if (isLtr) shape.topEnd else shape.topStart).toPx(size, this).fastCoerceAtMost(maxRadius),
            (if (isLtr) shape.bottomEnd else shape.bottomStart).toPx(size, this).fastCoerceAtMost(maxRadius),
            (if (isLtr) shape.bottomStart else shape.bottomEnd).toPx(size, this).fastCoerceAtMost(maxRadius)
        )

        else -> null
    }
}
