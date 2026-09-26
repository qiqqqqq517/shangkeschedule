package com.shangkeschedule

import androidx.compose.ui.graphics.Color
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.ui.schedule.components.resolveCourseBlockColors
import com.shangkeschedule.ui.theme.appColorTokens
import java.io.File

/**
 * V4 色彩语义与可区分性审计探针（仅诊断用）。
 *
 * 要回答的问题：课程色板每套 12 色，在**实际渲染状态**下，同屏相邻色块能不能被区分开？
 *
 * 为什么必须测「实际渲染色」而不是色板原值：
 * `resolveCourseBlockColors` 会按主题/深浅分档取色并做 alpha 叠加 ——
 * 通透、柔绘走「深色档强调色 + 半透明淡底」（浅色 0.30 / 深色 0.38），
 * 书卷与壁纸模式走实色。也就是说**同一条色板在不同主题下呈现完全不同**，
 * 拿色板原值算色差没有意义。这里直接调用该 public 函数取回 background，
 * 再合成到课表页底色上，得到用户真正看到的颜色。
 *
 * 判据（参考值，非硬门禁）：
 *   CIEDE2000 ΔE —— ≥10 明显可区分；3–10 可察觉但相近；<3 基本无法分辨。
 *   色觉缺陷模拟下放宽（红/绿/蓝色盲普遍压缩色域，要求过严会永远不达标）。
 *
 * 运行：
 *   ./gradlew :desktopApp:run "-PpreviewMainClass=com.shangkeschedule.PaletteProbeKt"
 * 产物：build_qa/palette_probe/report.md
 */

// ---------------------------------------------------------------- 色彩空间

private fun srgbToLinear(v: Double): Double =
    if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)

private data class Lab(val l: Double, val a: Double, val b: Double)

/** sRGB → CIELAB（D65）。 */
private fun rgbToLab(r: Double, g: Double, b: Double): Lab {
    val rl = srgbToLinear(r)
    val gl = srgbToLinear(g)
    val bl = srgbToLinear(b)
    val x = (0.4124564 * rl + 0.3575761 * gl + 0.1804375 * bl) / 0.95047
    val y = (0.2126729 * rl + 0.7151522 * gl + 0.0721750 * bl)
    val z = (0.0193339 * rl + 0.1191920 * gl + 0.9503041 * bl) / 1.08883
    fun f(t: Double) = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116.0
    val fx = f(x)
    val fy = f(y)
    val fz = f(z)
    return Lab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
}

private fun colorToLab(c: Color): Lab = rgbToLab(c.red.toDouble(), c.green.toDouble(), c.blue.toDouble())

/** CIEDE2000 色差（完整实现，含 hue 环绕与权重项）。 */
private fun deltaE2000(l1: Lab, l2: Lab): Double {
    val kL = 1.0
    val kC = 1.0
    val kH = 1.0
    val c1 = Math.hypot(l1.a, l1.b)
    val c2 = Math.hypot(l2.a, l2.b)
    val cBar = (c1 + c2) / 2
    val g = 0.5 * (1 - Math.sqrt(Math.pow(cBar, 7.0) / (Math.pow(cBar, 7.0) + Math.pow(25.0, 7.0))))
    val a1p = (1 + g) * l1.a
    val a2p = (1 + g) * l2.a
    val c1p = Math.hypot(a1p, l1.b)
    val c2p = Math.hypot(a2p, l2.b)
    fun hue(a: Double, b: Double): Double {
        if (a == 0.0 && b == 0.0) return 0.0
        var h = Math.toDegrees(Math.atan2(b, a))
        if (h < 0) h += 360
        return h
    }
    val h1p = hue(a1p, l1.b)
    val h2p = hue(a2p, l2.b)
    val dLp = l2.l - l1.l
    val dCp = c2p - c1p
    var dhp = h2p - h1p
    if (c1p * c2p == 0.0) dhp = 0.0 else {
        if (dhp > 180) dhp -= 360 else if (dhp < -180) dhp += 360
    }
    val dHp = 2 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(dhp) / 2)
    val lBarP = (l1.l + l2.l) / 2
    val cBarP = (c1p + c2p) / 2
    var hBarP: Double
    if (c1p * c2p == 0.0) hBarP = h1p + h2p
    else {
        val d = Math.abs(h1p - h2p)
        hBarP = if (d <= 180) (h1p + h2p) / 2
        else if (h1p + h2p < 360) (h1p + h2p + 360) / 2
        else (h1p + h2p - 360) / 2
    }
    val t = 1 - 0.17 * Math.cos(Math.toRadians(hBarP - 30)) +
        0.24 * Math.cos(Math.toRadians(2 * hBarP)) +
        0.32 * Math.cos(Math.toRadians(3 * hBarP + 6)) -
        0.20 * Math.cos(Math.toRadians(4 * hBarP - 63))
    val dTheta = 30 * Math.exp(-Math.pow((hBarP - 275) / 25.0, 2.0))
    val rc = 2 * Math.sqrt(Math.pow(cBarP, 7.0) / (Math.pow(cBarP, 7.0) + Math.pow(25.0, 7.0)))
    val sl = 1 + (0.015 * Math.pow(lBarP - 50, 2.0)) / Math.sqrt(20 + Math.pow(lBarP - 50, 2.0))
    val sc = 1 + 0.045 * cBarP
    val sh = 1 + 0.015 * cBarP * t
    val rt = -Math.sin(Math.toRadians(2 * dTheta)) * rc
    // 标准 CIEDE2000：ΔL'、ΔC'、ΔH' 三个分量各除以自己的权重项，再加交互项
    return Math.sqrt(
        Math.pow(dLp / (kL * sl), 2.0) +
            Math.pow(dCp / (kC * sc), 2.0) +
            Math.pow(dHp / (kH * sh), 2.0) +
            rt * (dCp / (kC * sc)) * (dHp / (kH * sh))
    )
}

// ------------------------------------------------------- 色觉缺陷模拟

/**
 * Machado et al. (2009) 的线性 RGB 变换矩阵，severity = 1.0（完全二色视）。
 * 用于评估色盲用户能否区分相邻色块 —— 这是 12 色相色板最实际的风险。
 */
private val CVD_MATRICES = mapOf(
    "红色盲 protan" to doubleArrayOf(
        0.152286, 1.052583, -0.204868,
        0.114503, 0.786281, 0.099216,
        -0.003882, -0.048116, 1.051998,
    ),
    "绿色盲 deutan" to doubleArrayOf(
        0.367322, 0.860646, -0.227968,
        0.280085, 0.672501, 0.047413,
        -0.011820, 0.042940, 0.968881,
    ),
    "蓝色盲 tritan" to doubleArrayOf(
        1.255528, -0.076749, -0.178779,
        -0.078411, 0.930809, 0.147602,
        0.004733, 0.691367, 0.303900,
    ),
)

private fun simulateCvd(c: Color, m: DoubleArray): Color {
    val r = c.red.toDouble()
    val g = c.green.toDouble()
    val b = c.blue.toDouble()
    fun ch(i: Int) = (m[i * 3] * r + m[i * 3 + 1] * g + m[i * 3 + 2] * b).coerceIn(0.0, 1.0)
    return Color(ch(0).toFloat(), ch(1).toFloat(), ch(2).toFloat(), c.alpha)
}

// ---------------------------------------------------------------- 工具

private fun composite(fg: Color, bg: Color): Color {
    val a = fg.alpha
    if (a >= 1f) return fg
    return Color(
        red = fg.red * a + bg.red * (1f - a),
        green = fg.green * a + bg.green * (1f - a),
        blue = fg.blue * a + bg.blue * (1f - a),
        alpha = 1f,
    )
}

private fun hex(c: Color): String =
    String.format("#%02X%02X%02X", (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

private data class PairDiff(val i: Int, val j: Int, val d: Double)

private fun worstPairs(colors: List<Color>, count: Int = 5): List<PairDiff> {
    val labs = colors.map { colorToLab(it) }
    val all = mutableListOf<PairDiff>()
    for (i in colors.indices) {
        for (j in i + 1 until colors.size) {
            all += PairDiff(i, j, deltaE2000(labs[i], labs[j]))
        }
    }
    return all.sortedBy { it.d }.take(count)
}

fun main() {
    System.setOut(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))

    val cwd = File(System.getProperty("user.dir") ?: ".")
    val root = if (cwd.name == "desktopApp") cwd.parentFile ?: cwd else cwd
    val dir = File(root, "build_qa/palette_probe")
    dir.mkdirs()

    val presets = listOf(
        Triple(AppThemePreset.IOS, "通透", ScheduleGridStyle.IOS),
        Triple(AppThemePreset.SOFT, "柔绘", ScheduleGridStyle.SOFT),
        Triple(AppThemePreset.CLAUDE, "书卷", ScheduleGridStyle.DEFAULT),
    )

    val md = StringBuilder()
    md.appendLine("# 课程色板可区分性审计（V4）")
    md.appendLine()
    md.appendLine("数据来源：`resolveCourseBlockColors` 返回的**实际渲染色**，再合成到课表页底色。")
    md.appendLine("判据（参考）：CIEDE2000 ΔE ≥10 明显可区分；3–10 相近；<3 基本无法分辨。")
    md.appendLine()

    for ((preset, label, style) in presets) {
        val palette = style.courseColorMaps
        for (dark in listOf(false, true)) {
            val mode = if (dark) "深色" else "浅色"
            val tokens = appColorTokens(isDark = dark, preset = preset)
            // 课表网格底：色块叠在页面底上
            val ground = tokens.pageBg
            val rendered = palette.map { pair ->
                val bg = resolveCourseBlockColors(
                    themePreset = preset,
                    isDarkTheme = dark,
                    colorPair = pair,
                    blockAlpha = 1f,
                    fallbackContent = tokens.textPrimary,
                ).background
                composite(bg, ground)
            }

            val worst = worstPairs(rendered)
            val min = worst.first().d

            println("\n=== $label · $mode（${palette.size} 色，最小 ΔE = ${String.format("%.1f", min)}）===")
            for (p in worst) {
                println(
                    "  ΔE ${String.format("%5.1f", p.d)}  #${p.i} ${hex(rendered[p.i])}  vs  " +
                        "#${p.j} ${hex(rendered[p.j])}"
                )
            }

            md.appendLine("## $label · $mode（${palette.size} 色）")
            md.appendLine()
            md.appendLine("**最小 ΔE = ${String.format("%.1f", min)}**（小于 10 即需关注）")
            md.appendLine()
            md.appendLine("| 色对 | 渲染色 A | 渲染色 B | ΔE(正常) | ΔE(红盲) | ΔE(绿盲) | ΔE(蓝盲) |")
            md.appendLine("|---|---|---|---|---|---|---|")
            for (p in worst) {
                val a = rendered[p.i]
                val b = rendered[p.j]
                val row = CVD_MATRICES.map { (_, m) ->
                    deltaE2000(colorToLab(simulateCvd(a, m)), colorToLab(simulateCvd(b, m)))
                }
                md.appendLine(
                    "| #${p.i} vs #${p.j} | `${hex(a)}` | `${hex(b)}` | ${String.format("%.1f", p.d)} | " +
                        row.joinToString(" | ") { String.format("%.1f", it) } + " |"
                )
            }
            md.appendLine()
        }
    }

    val out = File(dir, "report.md")
    out.writeText(md.toString())
    println("\n报告：${out.absolutePath}")
}
