package com.shangkeschedule.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush

/**
 * 液态玻璃（Liquid Glass）**折射层**的平台能力抽象。
 *
 * 缘起：本项目要「按自有 Compose/Kotlin 版本复刻 Kyant/backdrop 的玻璃栏」，
 * 而 Compose Multiplatform 1.11.1 的 commonMain **没有** `RuntimeShader` /
 * `RenderEffect.createRuntimeShaderEffect`（实证：ui-graphics 1.11.1 的 commonMain
 * linkdata 内不含该符号，只有 `BlurEffect` / `RenderEffect`），因此折射必须走平台实现。
 *
 * 能力边界（与 backdrop 一致）：
 * - Android：`android.graphics.RuntimeShader`，需 **API 33 (TIRAMISU)**；
 *   低版本 [isLiquidRefractionSupported] 返回 false，玻璃继续走原有 Haze 模糊路径；
 * - 其余平台（desktop / iOS，基于 Skiko）：走 Skia `RuntimeEffect`。
 *
 * 注意：**能力不支持时一律静默降级，绝不抛异常**——底栏在旧机型上必须照常可用。
 */
internal expect fun isLiquidRefractionSupported(): Boolean

/**
 * 平台是否支持 RenderEffect（离屏效果链）。Android 需 API 31 (S)；
 * 比 [isLiquidRefractionSupported] 门槛低一档——API 31/32 上模糊与色彩滤镜
 * （vibrancy）仍可用，只是没有 AGSL 折射。
 */
internal expect fun isLiquidRenderEffectSupported(): Boolean

/**
 * 色彩滤镜环节（vibrancy / colorControls）在当前系统渲染路径上是否可靠。
 *
 * 背景：Android 15（API 35）起，targetSdk ≥ 35 的应用 HWUI 默认切到 Vulkan
 * 硬件渲染；部分机型的 GPU 驱动（如 OPPO Find X8 的 Mali）对
 * `createChainEffect(createColorFilterEffect, blur)` 这类**链式 RenderEffect**
 * 存在原生崩溃，且发生在 RenderThread——Java 层 try/catch 拦不住。
 * 这类平台上返回 false ⇒ [glassVibrancy] / [glassColorControls] 整环节跳过，
 * 启动关键路径只剩「单一模糊」——与旧版 Haze 路径同类（已被全部机型验证安全）。
 * 桌面 / iOS（Skiko GL）恒为 true。
 */
internal expect fun isColorFilterEffectReliable(): Boolean

/**
 * 玻璃渲染**自愈兜底**开关：进程上次因崩溃（原生 / Java）退出时置 true，
 * 本进程内所有 [GlassSurface] 退化为纯色调面板（无背板采样、无效果链），
 * 保证 App 在驱动级崩溃面前**一定打得开**。由 Android 侧
 * [applyGlassNativeCrashFallback] 在 `Application.onCreate` 检测写入
 * （带版本锁存：升级新版本后自动重试完整玻璃）。桌面 / iOS 恒为 false。
 */
var isGlassFallbackActive: Boolean = false
    internal set

/**
 * 创建一块运行时着色器（AGSL / SkSL 源码）。
 *
 * 传入的着色器统一以 `uniform shader content;` 作为输入纹理、
 * 以 `half4 main(float2 coord)` 为入口，形如 Android AGSL 与 Skia SkSL 的公共子集。
 * 平台不支持时返回 null。
 */
internal expect fun createLiquidShader(agsl: String): LiquidShader?

/**
 * 把运行时着色器包装成 Compose 的 [RenderEffect]。
 *
 * @param uniformShaderName 着色器中承载「输入纹理」的 uniform 名（本库统一为 "content"）。
 */
internal expect fun liquidShaderEffect(shader: LiquidShader, uniformShaderName: String): RenderEffect?

/**
 * 把色彩滤镜包装成 [RenderEffect]（用于 vibrancy 等效果链环节）。
 * Android 走 `RenderEffect.createColorFilterEffect`（API 31+）；Skia 走
 * `ImageFilter.makeColorFilter`。平台不支持时返回 null（调用方静默跳过该环节）。
 */
internal expect fun liquidColorFilterEffect(colorFilter: ColorFilter): RenderEffect?

/**
 * 把运行时着色器包装成 [ShaderBrush]（用于 InteractiveHighlight 这类
 * 直接画在 Paint/画布上的着色器，而非 RenderEffect 链）。
 */
internal expect fun liquidShaderBrush(shader: LiquidShader): ShaderBrush?

/**
 * 效果链：先 [inner] 后 [outer]（即 `outer(inner(x))`）。
 *
 * 本库固定顺序为「色彩滤镜 ⇒ 模糊 ⇒ 折射」，与 backdrop 文档要求一致
 * （color filter ⇒ blur ⇒ lens）：折射必须采样「已模糊」的图，
 * 反序会让折射采到清晰原图，边缘出现生硬的原图纹路。
 */
internal expect fun chainLiquidEffects(inner: RenderEffect?, outer: RenderEffect): RenderEffect

/**
 * 给 [Paint] 设置模糊 MaskFilter（Highlight / Shadow / InnerShadow 用）。
 * radius 单位 px；radius <= 0 时清除滤镜。
 */
internal expect fun Paint.liquidBlur(radius: Float)

/**
 * 给 [Paint] 设置运行时着色器（Highlight 的方向性反光用）。
 * shader 为 null 时清除。
 */
internal expect fun Paint.liquidSetShader(shader: LiquidShader?)

/**
 * 平台无关的运行时着色器句柄（只需支持本库用到的 uniform 类型）。
 */
interface LiquidShader {

    fun setFloatUniform(name: String, value: Float)

    fun setFloatUniform(name: String, values: FloatArray)

    fun setColorUniform(name: String, color: Color)
}
