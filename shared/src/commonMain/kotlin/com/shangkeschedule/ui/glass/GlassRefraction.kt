package com.shangkeschedule.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * 液态玻璃「边缘折射」配置（v3.47.0 新增，可在设置里自定义）。
 *
 * 为什么单独成一层：本项目原有的玻璃只做「模糊 + 表面色 + 边缘光学」，
 * 折射（透镜位移）是这次按 Kyant/backdrop 复刻补上的新光学层。
 * 它默认**关闭** —— 关闭时玻璃走原来的 Haze 路径，观感与旧版逐像素一致；
 * 打开后才切换到自带引擎（模糊半径仍取用户设定的
 * [com.shangkeschedule.ui.theme.LocalGlassBlurRadius]，不另起一套）。
 *
 * @param enabled 总开关。默认 false（保持既有观感，零回归）。
 * @param heightDp 折射带宽度：从形状边缘向内多少 dp 内发生位移。
 *   应 ≤ 形状最小圆角半径（胶囊上即半高），否则直边处会出现折射不连续。
 * @param amountDp 折射位移量：边缘像素被"拉"向中心的距离，越大越像厚玻璃。
 * @param dispersion 彩虹色散：在四个圆角处产生棱镜彩边（开销：每像素 7 次采样）。
 * @param depthEffect 厚度感：把 SDF 梯度与径向梯度混合，形状内部也有轻微汇聚。
 */
@Immutable
data class GlassRefractionSettings(
    val enabled: Boolean = false,
    val heightDp: Float = DEFAULT_HEIGHT_DP,
    val amountDp: Float = DEFAULT_AMOUNT_DP,
    val dispersion: Boolean = false,
    val depthEffect: Boolean = true
) {

    companion object {

        const val MIN_HEIGHT_DP = 4f
        const val MAX_HEIGHT_DP = 32f
        const val MIN_AMOUNT_DP = 0f
        const val MAX_AMOUNT_DP = 48f

        const val DEFAULT_HEIGHT_DP = 24f
        const val DEFAULT_AMOUNT_DP = 24f

        /** 关闭（默认）：走原有 Haze 玻璃路径。 */
        val Off = GlassRefractionSettings()

        /** 轻：只在边缘给一点点厚度暗示。 */
        val Light = GlassRefractionSettings(enabled = true, heightDp = 14f, amountDp = 18f)

        /**
         * 标准：**对齐参考实现（Kyant/backdrop 的 LiquidBottomTabs）的 `lens(24dp, 24dp)`**。
         * v3.47.0 修订：初版标准档只给 16/24 且默认更弱，实测"几乎看不出折射"，
         * 与用户要的参考观感差距过大 —— 现取 24/24（折射带宽约占底栏高度 1/3，肉眼可见）。
         */
        val Standard = GlassRefractionSettings(enabled = true, heightDp = 24f, amountDp = 24f)

        /**
         * 强：明显透镜感 + 色散，接近参考实现开启色散后的"液体"观感。
         * 参考实现另有 `blur(8dp)` 与 `vibrancy()`，本项目分别由「模糊强度」与表面 tint 承担。
         */
        val Strong = GlassRefractionSettings(
            enabled = true,
            heightDp = 32f,
            amountDp = 40f,
            dispersion = true
        )
    }

    /** 把数值收进合理区间，避免越界参数导致 SDF 退化（设置读回时使用）。 */
    fun sanitized(): GlassRefractionSettings = copy(
        heightDp = heightDp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP),
        amountDp = amountDp.coerceIn(MIN_AMOUNT_DP, MAX_AMOUNT_DP)
    )
}

/**
 * 全局折射配置注入点（由 `ShangKeScheduleTheme` 从设置读取后提供）。
 * 与 [com.shangkeschedule.ui.theme.LocalGlassBlurRadius] 同构：一处设置，全端玻璃件同步。
 */
val LocalGlassRefraction = compositionLocalOf { GlassRefractionSettings.Off }

/**
 * 当前平台/系统是否支持运行时着色器折射。
 *
 * Android 需 API 33+；桌面与 iOS（Skiko）恒为 true。
 * 不支持时玻璃自动退化为「只有模糊的玻璃」，设置页会给出说明。
 */
fun isGlassRefractionAvailable(): Boolean = isLiquidRefractionSupported()
