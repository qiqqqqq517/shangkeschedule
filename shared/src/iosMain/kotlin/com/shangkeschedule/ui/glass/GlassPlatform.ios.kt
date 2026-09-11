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
 * iOS（Kotlin/Native / Skiko）实现：与 `jvmMain` 的实现同源同语义。
 *
 * 两处各留一份的原因见 `GlassPlatform.jvm.kt` 的说明（不改动多目标构建脚本）。
 * iOS 与桌面共用同一套 Skia API，故 AGSL 源码可直接复用。
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
        val srgb = color.convert(ColorSpaces.Srgb)
        val alpha = srgb.alpha
        builder.uniform(name, srgb.red * alpha, srgb.green * alpha, srgb.blue * alpha, alpha)
    }
}
