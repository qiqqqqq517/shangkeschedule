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
 * 色条主题（通透 IOS / 柔绘 SOFT）课程方块的**填充**不透明度系数。
 *
 * 为什么需要它：色条主题的方块底色由「深色档强调色 × 系数」得到，系数过小时填充与页面底几乎
 * 同色，方块视觉上就是**透明**的——只剩 4dp 左侧色条与文字可见。离屏渲染真实 CourseBlock 实测
 * （反解实际填充 alpha）：
 *   · 柔绘浅色 0.12：填充 `#E9E9F4` vs 页底 `#F4F3F7`，差 ≈10/255 ⇒ 基本看不见；
 *   · 通透浅色 0.12：填充 `#D4E3F8` vs 页底 `#F2F2F7`，差 ≈(31,15,0) ⇒ 很淡；
 *   · 通透深色 0.25：填充 `#001F40` 叠纯黑页底 ⇒ 等同黑（真机截图复现）。
 * 现提高到浅色 0.30 / 深色 0.38：色条语言（4dp 色条 + 淡同色底）不变，方块本身成为可辨识的淡色
 * 卡片；深色档刻意低于浅色档的视觉当量，保证深色下文字（= 深色档强调色）仍压得住底色。
 *
 * ⚠️ 这是**填充**系数，不是色板令牌：色板自带 alpha（书卷 `0x40` / 柔绘 `0x33` / 通透 `0x1F`）
 * 仍由 [scaleAlpha] 原样保留，本系数只在其上缩放。
 */
internal const val STRIP_FILL_ALPHA_LIGHT = 0.30f
internal const val STRIP_FILL_ALPHA_DARK = 0.38f

/**
 * 在颜色自带的 alpha 之上再做一次乘算，得到最终不透明度。
 *
 * 颜色池里的颜色自带 alpha，且该 alpha 是**设计令牌**：书卷浅色池 = `0x40` 淡底、通透浅色池 = `0x1F`
 * 淡底（深色池与经典 / 云舒 / 利落的浅色池为 `0xFF` 实色）。因此「课程块不透明度」这类设置必须与
 * 颜色自带的 alpha **相乘**，而不是用 `copy(alpha = x)` 直接覆盖——覆盖会让淡底令牌失效，
 * 页面渲染出的颜色与颜色池里显示的颜色对不上（历史问题：周课表网格把书卷浅色池渲染成了实色）。
 */
fun Color.scaleAlpha(scale: Float): Color = copy(alpha = (alpha * scale).coerceIn(0f, 1f))
