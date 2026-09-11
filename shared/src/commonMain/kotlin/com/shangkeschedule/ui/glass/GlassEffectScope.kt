package com.shangkeschedule.ui.glass

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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

    /** 按 [key] 复用运行时着色器（避免每帧重新编译 AGSL）。 */
    fun obtainShader(key: String, agsl: String): LiquidShader?
}

internal class GlassEffectScopeImpl : GlassEffectScope {

    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var size: Size = Size.Unspecified
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override var padding: Float = 0f
    override var renderEffect: RenderEffect? = null

    private var currentShape: Shape = RectangleShape

    override val shape: Shape get() = currentShape

    /** 按需从当前形状解析；不支持圆角解析的形状返回 null（折射会据此跳过）。 */
    override val cornerRadii: FloatArray? get() = resolveCornerRadii()

    private val shaders = mutableMapOf<String, LiquidShader?>()

    override fun obtainShader(key: String, agsl: String): LiquidShader? {
        if (!isLiquidRefractionSupported()) return null
        return shaders.getOrPut(key) {
            runCatching { createLiquidShader(agsl) }.getOrNull()
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

    /** 每帧重算：清空 padding / 效果链（着色器缓存保留），再执行用户的 effects 块。 */
    fun apply(shape: Shape, effects: GlassEffectScope.() -> Unit) {
        currentShape = shape
        padding = 0f
        renderEffect = null
        effects()
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
    if (radius > padding) padding = radius
    renderEffect = BlurEffect(renderEffect, radius, radius, edgeTreatment)
}

/**
 * 边缘折射（透镜）。
 *
 * @param refractionHeight 折射带宽度，单位 px。取值应 ≤ 形状最小圆角半径；
 *   超过后四角的折射会在直边处出现不连续（上游同样如此，属可接受范围）。
 * @param refractionAmount 折射位移量，单位 px。上限为形状短边的一半左右。
 * @param depthEffect 是否叠加"厚度"感：把 SDF 梯度与径向梯度混合，
 *   使折射在形状内部也有轻微汇聚，观感更像一块有厚度的玻璃。
 * @param chromaticAberration 是否开启彩虹色散（彩边集中在四个圆角）。
 *
 * 形状不是圆角矩形类时**静默跳过**（绝不抛异常）：底栏形状可能是任意 Shape，
 * 缺少 SDF 参数时退化为「只有模糊的玻璃」，这与上游抛
 * `UnsupportedOperationException` 的行为不同，是刻意的健壮性取舍。
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

    val effect = liquidShaderEffect(shader, "content") ?: return
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
