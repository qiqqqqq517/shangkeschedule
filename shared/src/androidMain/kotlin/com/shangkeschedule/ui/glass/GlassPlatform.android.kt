package com.shangkeschedule.ui.glass

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
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
    }.getOrNull()

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
