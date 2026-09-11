package com.shangkeschedule.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect

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
 * 效果链：先 [inner] 后 [outer]（即 `outer(inner(x))`）。
 *
 * 本库固定顺序为「模糊 → 折射」，与 backdrop 文档要求一致
 * （color filter ⇒ blur ⇒ lens）：折射必须采样「已模糊」的图，
 * 反序会让折射采到清晰原图，边缘出现生硬的原图纹路。
 */
internal expect fun chainLiquidEffects(inner: RenderEffect?, outer: RenderEffect): RenderEffect

/**
 * 平台无关的运行时着色器句柄（只需支持本库用到的 uniform 类型）。
 */
interface LiquidShader {

    fun setFloatUniform(name: String, value: Float)

    fun setFloatUniform(name: String, values: FloatArray)

    fun setColorUniform(name: String, color: Color)
}
