package com.shangkeschedule.widget

import kotlin.math.pow

/**
 * Tiny 小组件状态圆（气泡）数字的前景色选择。
 *
 * 根因：气泡底是**动态课程色**（[applyCourseColor] 把色板里的 light/dark 色以
 * `setColorFilter`（SRC_ATOP）压到 `#3498DB` 基底圆上），而圆心数字
 * `tv_remaining_count` 一律用固定主题文字色（浅色 `#2C3E50` / 深色 `#F2F2F7`）。
 * 部分课程色与固定文字色几乎同亮度 —— 如深色琥珀 `#FBC02D` 配近白字仅 **1.48:1**、
 * 取色兜底浅色 `#5D6D7E` 配深字仅 **2.07:1**（正文需 4.5:1）—— 于是「有的用户」
 *（取决于其主题色板与当前课程色）看到数字糊在气泡底上难以辨认。
 *
 * 修复（即 `docs/widget-display-optimization.md` §4.5 的 P2 项，提前落地）：
 * 渲染时按气泡**实际底色**的相对亮度二选一 —— `L > 0.179` 用纯黑字，否则用纯白字。
 * 在 `L = 0.179` 处两种选择均为 4.58:1，且偏离该点后所选色的对比度单调上升，
 * 故该规则**可证明**恒 ≥ 4.58:1 ≥ WCAG AA 4.5:1（[WidgetBubbleContrastTest] 另有
 * 全色域暴力扫描锁死该下界）。
 *
 * 纯函数（无 Android / Compose 依赖，可进 JVM 单测）；半透明课程色先按 SRC_ATOP
 * 与基底混合（与 `ImageView.setColorFilter(int)` 的默认模式一致）再算亮度。
 * 所有颜色参数均为 ARGB 整型。
 */

/** 气泡基底圆 `widget_shape_circle_base` 的实色（浅色 / 深色布局共用同一份 drawable）。 */
const val WIDGET_BUBBLE_BASE_COLOR: Int = 0xFF3498DB.toInt()

/** 亮度阈值：在该点黑白字均为 4.58:1（见上），是「恒 ≥ 4.5:1」证明的支点。 */
const val BUBBLE_LUMINANCE_THRESHOLD: Double = 0.179

private fun linearChannel(c: Int): Double {
    val v = c / 255.0
    return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

/** ARGB 整型的相对亮度 L（WCAG 2.x；调用方负责先把半透明色混合为实色）。 */
fun relativeLuminance(argb: Int): Double {
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    return 0.2126 * linearChannel(r) + 0.7152 * linearChannel(g) + 0.0722 * linearChannel(b)
}

/** 前景 / 背景（均为实色 ARGB）的对比度（WCAG 2.x）。 */
fun contrastRatio(fg: Int, bg: Int): Double {
    val a = relativeLuminance(fg)
    val b = relativeLuminance(bg)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

/**
 * SRC_ATOP 混合：半透明课程色盖在气泡基底上（与 `setColorFilter(int)` 默认一致）。
 * 基底实色 ⇒ 结果恒实色；`alpha = 0` 时退化为基底本身。
 */
fun effectiveBubbleColor(courseColor: Int, baseColor: Int = WIDGET_BUBBLE_BASE_COLOR): Int {
    val a = ((courseColor ushr 24) and 0xFF) / 255.0
    fun blend(src: Int, dst: Int): Int = (src * a + dst * (1.0 - a) + 0.5).toInt().coerceIn(0, 255)
    val r = blend((courseColor ushr 16) and 0xFF, (baseColor ushr 16) and 0xFF)
    val g = blend((courseColor ushr 8) and 0xFF, (baseColor ushr 8) and 0xFF)
    val b = blend(courseColor and 0xFF, baseColor and 0xFF)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/** 气泡数字前景色：底亮用纯黑字，底暗用纯白字（恒 ≥ 4.58:1，见文件头证明）。 */
fun bubbleTextColorFor(effectiveBackground: Int): Int =
    if (relativeLuminance(effectiveBackground) > BUBBLE_LUMINANCE_THRESHOLD) {
        0xFF000000.toInt()
    } else {
        0xFFFFFFFF.toInt()
    }

/**
 * 组合入口：`courseColor` 为 null（色池为空 / 索引非法）时用兜底色计算，
 * 与 [applyCourseColor] 的回落口径一致。
 */
fun resolveBubbleTextColor(courseColor: Int?, fallbackColor: Int): Int =
    bubbleTextColorFor(effectiveBubbleColor(courseColor ?: fallbackColor))
