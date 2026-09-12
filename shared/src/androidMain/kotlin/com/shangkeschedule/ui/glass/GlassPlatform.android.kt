package com.shangkeschedule.ui.glass

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb

/**
 * Android 实现：折射走 `android.graphics.RuntimeShader`（AGSL），需 API 33 (TIRAMISU)。
 *
 * 低版本（26 ~ 32）返回 false ⇒ 底栏自动退回原有 Haze 模糊路径，
 * 不影响 minSdk 26 的既有用户（本项目 android-minSdk = 26）。
 */
internal actual fun isLiquidRefractionSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

internal actual fun isLiquidRenderEffectSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createLiquidShader(agsl: String): LiquidShader? =
    runCatching { AndroidLiquidShader(android.graphics.RuntimeShader(agsl)) }.getOrNull()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun liquidShaderEffect(
    shader: LiquidShader,
    uniformShaderName: String
): RenderEffect? =
    runCatching {
        android.graphics.RenderEffect
            .createRuntimeShaderEffect(
                (shader as AndroidLiquidShader).shader,
                uniformShaderName
            )
            .asComposeRenderEffect()
    }.onFailure {
        println("GLASS: createRuntimeShaderEffect 失败 uniform=$uniformShaderName, ${it.message}")
    }.getOrNull()

@RequiresApi(Build.VERSION_CODES.S)
internal actual fun liquidColorFilterEffect(colorFilter: ColorFilter): RenderEffect? =
    runCatching {
        android.graphics.RenderEffect
            .createColorFilterEffect(colorFilter.asAndroidColorFilter())
            .asComposeRenderEffect()
    }.getOrNull()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun liquidShaderBrush(shader: LiquidShader): ShaderBrush? =
    runCatching { ShaderBrush((shader as AndroidLiquidShader).shader) }.getOrNull()

/**
 * 效果链。Android 的 `createChainEffect(outer, inner)` 语义是 `outer(inner(x))`，
 * 因此把"已有链"作为 inner、新效果作为 outer，得到「先模糊后折射」。
 *
 * 注意：Compose 的 `androidx.compose.ui.graphics.RenderEffect` 在 Android 端是**独立包装类型**，
 * 不是 typealias；两个方向的转换不对称：
 * - 转成 Android 侧：`asAndroidRenderEffect()` 是 **RenderEffect 的成员函数**（不能 import），
 * - 转回 Compose 侧：`asComposeRenderEffect()` 是**顶层扩展函数**（需要 import）。
 * 这是本项目的 Compose 1.11.1 与上游 backdrop（Compose 1.12）写法上的一处实证差异。
 */
@RequiresApi(Build.VERSION_CODES.S)
internal actual fun chainLiquidEffects(inner: RenderEffect?, outer: RenderEffect): RenderEffect =
    if (inner == null) {
        outer
    } else {
        android.graphics.RenderEffect
            .createChainEffect(
                outer.asAndroidRenderEffect(),
                inner.asAndroidRenderEffect()
            )
            .asComposeRenderEffect()
    }

internal actual fun Paint.liquidBlur(radius: Float) {
    this.asFrameworkPaint().maskFilter =
        if (radius > 0f) BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL) else null
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun Paint.liquidSetShader(shader: LiquidShader?) {
    asFrameworkPaint().shader =
        (shader as? AndroidLiquidShader)?.shader
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AndroidLiquidShader(
    val shader: android.graphics.RuntimeShader
) : LiquidShader {

    override fun setFloatUniform(name: String, value: Float) {
        shader.setFloatUniform(name, value)
    }

    override fun setFloatUniform(name: String, values: FloatArray) {
        shader.setFloatUniform(name, values)
    }

    override fun setColorUniform(name: String, color: Color) {
        shader.setColorUniform(name, color.toArgb())
    }
}
