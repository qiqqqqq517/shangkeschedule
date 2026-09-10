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

/**
 * 在颜色自带的 alpha 之上再做一次乘算，得到最终不透明度。
 *
 * 颜色池里的颜色自带 alpha，且该 alpha 是**设计令牌**：书卷浅色池 = `0x40` 淡底、通透浅色池 = `0x1F`
 * 淡底（深色池与经典 / 云舒 / 利落的浅色池为 `0xFF` 实色）。因此「课程块不透明度」这类设置必须与
 * 颜色自带的 alpha **相乘**，而不是用 `copy(alpha = x)` 直接覆盖——覆盖会让淡底令牌失效，
 * 页面渲染出的颜色与颜色池里显示的颜色对不上（历史问题：周课表网格把书卷浅色池渲染成了实色）。
 */
fun Color.scaleAlpha(scale: Float): Color = copy(alpha = (alpha * scale).coerceIn(0f, 1f))
