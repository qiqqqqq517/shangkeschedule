package com.shangkeschedule.ui.glass

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
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

internal actual fun isColorFilterEffectReliable(): Boolean =
    // v3.50.11 修正：**不再按系统版本一刀切**。v3.50.9 / v3.50.10 曾以 SDK_INT < 35 为门槛，
    // 把所有 Android 15 机型的鲜艳度 / 折射 / 高光一并关掉；而实测中大量 Android 15 机型
    //（如 vivo X200）跑完整玻璃完全正常、长期不闪退 —— 属**误伤可用机型**。
    // 现在「能不能跑」由**本机是否真的崩过**决定（见下方 applyGlassNativeCrashFallback）。
    true

internal actual fun isLiquidShaderReliable(): Boolean =
    // 同上：AGSL 是否可用由本机崩溃记录决定，而非 SDK 版本。
    true

/** 自愈兜底的锁存标记文件（filesDir 下，内容 = 降级时的 versionCode）。 */
private const val GLASS_FALLBACK_MARKER = "glass_fallback.flag"

/** 只把「最近 15 分钟内的崩溃退出」视为本次玻璃渲染所致。 */
private const val GLASS_FALLBACK_RECENT_MS = 15 * 60 * 1000L

/**
 * 玻璃渲染自愈兜底（Android 实现），在 `Application.onCreate` 最先调用：
 *
 * 1. **版本锁存**：`filesDir/glass_fallback.flag` 存在且内容 == 当前 versionCode
 *    ⇒ 本进程直接进入降级（[isGlassFallbackActive] = true），保证一定打得开；
 *    版本不一致（已升级 / 数据残留）⇒ 删除标记，给新版本重试完整玻璃的机会。
 * 2. **崩溃检测**：`ApplicationExitInfo`（API 30+）最近一条退出记录若为
 *    CRASH / CRASH_NATIVE 且发生在 15 分钟内 ⇒ 写入标记 + 本进程降级。
 *
 * 设计取舍：锁存按版本号而非按时间清除——对确定性的驱动级崩溃（每帧必崩），
 * "下次打开重试"只会造成「崩一次 / 能开一次」的循环；锁存到下次升级更符合
 * 本项目的热修发布节奏。若崩溃与玻璃无关（一次性 OOM 等），代价只是本版本
 * 玻璃降级为色调面板，功能不受影响。
 */
public fun applyGlassNativeCrashFallback(context: Context) {
    val marker = File(context.filesDir, GLASS_FALLBACK_MARKER)
    val versionCode = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    }.getOrDefault(0L)

    if (marker.exists()) {
        val markerVersion = marker.readText().trim().toLongOrNull()
        if (markerVersion == versionCode) {
            isGlassFallbackActive = true
            println("GLASS: 检测到降级锁存（versionCode=$versionCode），本进程玻璃退化为色调面板")
            return
        }
        marker.delete()
    }

    // v3.51.2：debug（可调试）构建**不启用崩溃窗口期自动检测** —— 自动兜底会在
    // 真实崩溃后 15 分钟内把玻璃静默降级，掩盖真问题、污染复测（本项目已两次踩坑：
    // 「修复后不崩」实为兜底降级假象）。debug 下仅响应手动写入的锁存标记，
    // 完整玻璃路径始终可用于复现与验证；release 构建行为不变。
    val isDebuggable = runCatching {
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }.getOrDefault(false)
    if (isDebuggable) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        hasRecentGlassKillingCrash(context, System.currentTimeMillis())
    ) {
        runCatching { marker.writeText(versionCode.toString()) }
        isGlassFallbackActive = true
        println("GLASS: 上次进程崩溃退出，本进程玻璃退化为色调面板（已锁存到 versionCode=$versionCode）")
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun hasRecentGlassKillingCrash(context: Context, nowMs: Long): Boolean =
    runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getHistoricalProcessExitReasons(context.packageName, 0, 3).any { info ->
            (info.reason == ApplicationExitInfo.REASON_CRASH ||
                info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) &&
                nowMs - info.timestamp < GLASS_FALLBACK_RECENT_MS
        }
    }.getOrDefault(false)

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
