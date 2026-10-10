package com.shangkeschedule.ui.schedule.components

import androidx.compose.ui.graphics.Color

/**
 * 壁纸（课表背景图）上的文字颜色自适应 —— 纯逻辑，便于单测。
 *
 * ## 背景（v4.76.24 新增）
 *
 * 课表可设自定义背景图。此前侧边栏时间文字固定取 `pageTextColor ?: onSurface`：
 * - **浅色模式 + 深色图片** ⇒ 文字取深色 ⇒ 深字压深图，时间读不出来；
 * - **深色模式 + 浅色图片** ⇒ 文字取浅色 ⇒ 浅字压浅图，同样读不出来。
 *
 * 两种场景本质是同一个问题：**文字颜色没有参考图片自身的明暗**。
 *
 * ## 方案
 *
 * 对壁纸整体采样出一个平均亮度，据此在**黑 / 白**二选一（不引入第三种颜色，
 * 保证与任意图片都有最大对比度）。用户显式设置过页面文字色时以用户为准，
 * 本函数只在「未手动指定」时介入（见调用点）。
 */

/**
 * 判定壁纸明暗的亮度阈值。
 *
 * 取 0.5 而非课程块用的 0.6：壁纸是整张照片，平均亮度天然偏中间调，
 * 阈值偏向 0.5 可让「偏亮 / 偏暗」的划分更贴近人眼观感（0.6 会让大量中等偏亮的
 * 照片被误判为「暗」而配白字）。
 */
internal const val WALLPAPER_LUMINANCE_THRESHOLD = 0.5f

/**
 * 按壁纸平均亮度选择文字颜色。
 *
 * @param averageLuminance 壁纸的平均相对亮度，取值 [0, 1]；越接近 1 越亮。
 *   传 null 表示**亮度未知**（尚未采样完成、采样失败、或非 Android 平台暂无实现），
 *   此时返回 [fallback]（即维持原有主题色，不改变现状）。
 * @param fallback 亮度未知时的回退色，通常传主题的 `onSurface`。
 * @return 亮图返回黑色文字，暗图返回白色文字；亮度未知返回 [fallback]。
 */
internal fun textColorForWallpaper(
    averageLuminance: Float?,
    fallback: Color,
): Color {
    if (averageLuminance == null) return fallback
    val luma = averageLuminance.coerceIn(0f, 1f)
    return if (luma > WALLPAPER_LUMINANCE_THRESHOLD) Color.Black else Color.White
}

/**
 * 由像素颜色分量累加出平均相对亮度。
 *
 * 采用 Rec.601 亮度权重（0.299/0.587/0.114），与 [adaptiveTextColor] 保持同一套口径，
 * 避免同一张图在「课程块文字」与「页面文字」两处得出相反的明暗结论。
 *
 * @param sumR/sumG/sumB 各通道的累加和，取值 [0, 255 * sampleCount]。
 * @param sampleCount 参与累加的像素数；<= 0 时返回 null（视为亮度未知）。
 * @return 平均亮度，取值 [0, 1]；样本数为 0 时返回 null。
 */
internal fun averageLuminanceFrom(
    sumR: Long,
    sumG: Long,
    sumB: Long,
    sampleCount: Long,
): Float? {
    if (sampleCount <= 0L) return null
    val r = (sumR.toDouble() / sampleCount) / 255.0
    val g = (sumG.toDouble() / sampleCount) / 255.0
    val b = (sumB.toDouble() / sampleCount) / 255.0
    return (0.299 * r + 0.587 * g + 0.114 * b).toFloat().coerceIn(0f, 1f)
}