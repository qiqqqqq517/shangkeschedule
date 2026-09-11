package com.shangkeschedule.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.skiaImageFilter
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * 桌面（JVM / Skiko）实现：折射走 Skia `RuntimeEffect`。
 *
 * Skia 的 `RuntimeEffect` 吃的是 SkSL，与 Android 的 AGSL 在本库用到的语法子集内兼容
 * （`uniform shader content;` / `half4 main(float2)` / `content.eval()`），
 * 因此 [REFRACTION_AGSL] 等同一份源码可在两个平台共用。
 *
 * 注：本文件与 `iosMain` 下的同名实现内容一致 —— 本项目未引入 `skikoMain` 中间源集，
 * 为了不改动多目标构建脚本，选择两处各留一份（仅约 50 行，避免动 shared/build.gradle.kts）。
 */
internal actual fun isLiquidRefractionSupported(): Boolean = true

internal actual fun createLiquidShader(agsl: String): LiquidShader? =
    runCatching {
        SkikoLiquidShader(RuntimeShaderBuilder(RuntimeEffect.makeForShader(agsl)))
    }.getOrNull()

internal actual fun liquidShaderEffect(
    shader: LiquidShader,
    uniformShaderName: String
): RenderEffect? =
    runCatching {
        ImageFilter.makeRuntimeShader(
            (shader as SkikoLiquidShader).builder,
            uniformShaderName,
            null
        ).asComposeRenderEffect()
    }.getOrNull()

/** 与 Android 侧同语义：`outer(inner(x))`。 */
internal actual fun chainLiquidEffects(inner: RenderEffect?, outer: RenderEffect): RenderEffect =
    if (inner == null) {
        outer
    } else {
        ImageFilter.makeCompose(outer.skiaImageFilter, inner.skiaImageFilter).asComposeRenderEffect()
    }

private class SkikoLiquidShader(
    val builder: RuntimeShaderBuilder
) : LiquidShader {

    override fun setFloatUniform(name: String, value: Float) {
        builder.uniform(name, value)
    }

    override fun setFloatUniform(name: String, values: FloatArray) {
        builder.uniform(name, values)
    }

    override fun setColorUniform(name: String, color: Color) {
        // SkSL 没有 `layout(color)`，按预乘 RGB 传四个分量（上游同样处理）
        val srgb = color.convert(ColorSpaces.Srgb)
        val alpha = srgb.alpha
        builder.uniform(name, srgb.red * alpha, srgb.green * alpha, srgb.blue * alpha, alpha)
    }
}
