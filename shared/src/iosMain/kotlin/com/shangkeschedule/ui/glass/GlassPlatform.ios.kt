package com.shangkeschedule.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.skiaImageFilter
import androidx.compose.ui.graphics.skiaPaint
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * iOS（Kotlin/Native / Skiko）实现：与 `jvmMain` 的实现同源同语义。
 *
 * 两处各留一份的原因见 `GlassPlatform.jvm.kt` 的说明（不改动多目标构建脚本）。
 * iOS 与桌面共用同一套 Skia API，故 AGSL 源码可直接复用。
 */
internal actual fun isLiquidRefractionSupported(): Boolean = true

internal actual fun isLiquidRenderEffectSupported(): Boolean = true

/** iOS 为 Skiko Metal/GL 路径，无 Android 15 Vulkan 驱动问题，色彩滤镜链恒可用。 */
internal actual fun isColorFilterEffectReliable(): Boolean = true

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

internal actual fun liquidColorFilterEffect(colorFilter: ColorFilter): RenderEffect? =
    runCatching {
        ImageFilter.makeColorFilter(colorFilter.asSkiaColorFilter(), null, null)
            .asComposeRenderEffect()
    }.getOrNull()

internal actual fun liquidShaderBrush(shader: LiquidShader): ShaderBrush? =
    runCatching {
        ShaderBrush((shader as SkikoLiquidShader).builder.makeShader().asComposeShader())
    }.getOrNull()

/** 与 Android 侧同语义：`outer(inner(x))`。 */
internal actual fun chainLiquidEffects(inner: RenderEffect?, outer: RenderEffect): RenderEffect =
    if (inner == null) {
        outer
    } else {
        ImageFilter.makeCompose(outer.skiaImageFilter, inner.skiaImageFilter).asComposeRenderEffect()
    }

internal actual fun Paint.liquidBlur(radius: Float) {
    skiaPaint.maskFilter =
        if (radius > 0f) MaskFilter.makeBlur(FilterBlurMode.NORMAL, radius) else null
}

internal actual fun Paint.liquidSetShader(shader: LiquidShader?) {
    skiaPaint.shader = (shader as? SkikoLiquidShader)?.builder?.makeShader()
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
