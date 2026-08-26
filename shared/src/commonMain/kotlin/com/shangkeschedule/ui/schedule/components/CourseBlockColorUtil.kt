package com.shangkeschedule.ui.schedule.components

import androidx.compose.ui.graphics.Color

/**
 * 根据课程块背景色自动计算文字颜色。
 *
 * 参考 sleep 的 CourseColorUtil 思路：
 *  - 计算相对亮度 (0.299R + 0.587G + 0.114B)
 *  - 亮度 &gt; 0.6 说明是浅色背景，返回黑字
 *  - 亮度 &lt;= 0.6 说明是深色背景，返回白字
 *  - 背景 alpha &lt; 0.5 时说明背景几乎透明，无法保证对比度，回退到调用方提供的固定颜色
 */
fun adaptiveTextColor(background: Color, fallback: Color): Color {
    if (background.alpha < 0.5f) return fallback

    val luminance = 0.299f * background.red +
        0.587f * background.green +
        0.114f * background.blue

    return if (luminance > 0.6f) Color.Black else Color.White
}
