package com.shangkeschedule

import androidx.compose.ui.graphics.Color
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.ui.theme.AppColorTokens
import com.shangkeschedule.ui.theme.appColorTokens
import java.io.File

/**
 * 三主题 × 深浅 对比度审计探针（AC1，仅诊断用）。
 *
 * 为什么需要它：此前的对比度校验（`scripts/check_widget_contrast.py`）只覆盖**桌面小组件**
 * 那套独立色表，App 内正文只跑过一次**书卷**配色，另外两套（含**默认的通透**）从未审过。
 *
 * 为什么用运行时探针而不是静态解析源码：三套 Style 的颜色值是混合写法
 * （字面量 `Color(0xFF..)`、常量引用、带 alpha 的软色 `Color(0x1F..)`），
 * 静态解析要复刻常量表与 alpha 合成，容易与真实值产生偏差。
 * 这里直接调用 `appColorTokens(isDark, preset)`，拿到的是**真正会渲染的值**。
 *
 * 判据（WCAG 2.2 AA）：正文文本 ≥ 4.5:1；大号文本 / 非文本 UI 边界 ≥ 3:1。
 *
 * 运行：
 *   ./gradlew :desktopApp:run "-PpreviewMainClass=com.shangkeschedule.ContrastProbeKt"
 * 产物：`build_qa/contrast_probe/report.md` + 控制台明细。
 */

/** sRGB 相对亮度（WCAG 2.2 定义）。 */
private fun luminance(c: Color): Double {
    fun channel(v: Float): Double {
        val s = v.toDouble()
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
}

/** 把带 alpha 的前景合成到背景上，得到实际呈现色。 */
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

/** 对比度。前景若是半透明，先按背景合成再算（软色做前景时的真实观感）。 */
private fun contrast(fg: Color, bg: Color): Double {
    val f = composite(fg, bg)
    val l1 = luminance(f)
    val l2 = luminance(bg)
    val hi = maxOf(l1, l2)
    val lo = minOf(l1, l2)
    return (hi + 0.05) / (lo + 0.05)
}

private fun hex(c: Color): String = "#%02X%02X%02X".let {
    String.format(it, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()) +
        if (c.alpha < 1f) String.format(" α%.0f%%", c.alpha * 100) else ""
}

private data class Row(
    val section: String,
    val fgName: String,
    val fg: Color,
    val bgName: String,
    val bg: Color,
    val minRatio: Double,
    /** false = 仅作参考记录，不参与「不达标」判定（如装饰性分隔线、既定设计）。 */
    val enforced: Boolean = true,
    val note: String = "",
) {
    val ratio: Double get() = contrast(fg, bg)
    val pass: Boolean get() = !enforced || ratio >= minRatio
}

/** 文本类：正文 4.5、大号/次要 4.5（次要文本也是正文，仍按 4.5 要求）。 */
private val TEXT_MIN = 4.5

/** 非文本：UI 组件边界 / 图形对象 ≥ 3.0。 */
private val NON_TEXT_MIN = 3.0

private fun collect(t: AppColorTokens, preset: AppThemePreset, dark: Boolean): List<Row> {
    val surfaces = listOf(
        "pageBg" to t.pageBg,
        "cardBg" to t.cardBg,
        "cardBgElevated" to t.cardBgElevated,
        // inputBg 是**半透明填充**（如 iOS systemFill 仅 12% 不透明度）。
        // 必须先把半透明背景合成到它实际叠放的父表面（卡片）上再算对比度 ——
        // 上一版直接拿半透明色当不透明背景，算出 textSecondary 对比度 1.34 这种
        // 显然不成立的值（半透明色本身没有「亮度」，必须先合成）。
        "inputBg(合成于 cardBg)" to composite(t.inputBg, t.cardBg),
    )
    val out = mutableListOf<Row>()

    // 1) 正文与次要文本 × 各表面（最基本、最常出现）
    val texts = listOf("textPrimary" to t.textPrimary, "textSecondary" to t.textSecondary)
    for ((tn, tc) in texts) {
        for ((sn, sc) in surfaces) {
            // 文本若半透明，需按各自表面分别合成（inputBg 上的 12% 文字与卡片上不同）
            out += Row("文本 / 表面", tn, tc, sn, sc, TEXT_MIN)
        }
    }

    // 2) 主色上的文字
    out += Row("主色底", "textOnPrimary", t.textOnPrimary, "primary", t.primary, TEXT_MIN)

    // 3) 语义色对：实心前景 on 淡色软底（软底为半透明，合成到卡片）。
    // 判据用**非文本 3:1** —— 已核实软底的实际用途：全库唯一消费点是
    // `iconBg = colors.successSoft`（ManageCourseTablesScreen），即**图标背景**下的实心图标，
    // 属图形对象而非正文；按 4.5 判会产生成片误报。
    val semantics = listOf(
        "primary", "success", "info", "warning", "amber", "danger", "favorite",
    )
    for (name in semantics) {
        val fg = semanticColor(t, name) ?: continue
        val soft = semanticSoft(t, name) ?: continue
        out += Row("语义色对(图标)", name, fg, "${name}Soft", composite(soft, t.cardBg), NON_TEXT_MIN)
    }

    // 4) 徽标 / 提示条底上的文字
    out += Row("徽标", "badgeFg", t.badgeFg, "badgeBg", t.badgeBg, TEXT_MIN)
    out += Row("提示条", "snackbarFg", t.snackbarFg, "snackbarBg", t.snackbarBg, TEXT_MIN)

    // 底栏选中项：文字用**实心主色**（NavigationComponents 的 selectedContentColor
    // 默认取 `colorScheme.primary`，不是 textPrimary —— 上一版按 textPrimary 测出的
    // 2.63 / 1.95 是配对错误），背景是**主色 12% 半透明胶囊**，
    // 且底栏底（navBarBg）本身多为半透明玻璃 ⇒ 两层都必须逐级合成。
    val navGround = composite(t.navBarBg, t.pageBg)
    val navSelected = composite(t.navSelectedBg, navGround)
    out += Row("底栏选中", "primary", t.primary, "navSelectedBg(逐级合成)", navSelected, TEXT_MIN)
    out += Row("底栏未选中", "textSecondary", t.textSecondary, "navBarBg(合成于 pageBg)", navGround, TEXT_MIN)

    // 5) 非文本：组件边界 / 分隔线在卡片底上的可见度。
    // outline 是 M3 组件的**必要**边界（OutlinedButton / OutlinedTextField 的描边）⇒ 默认强制 3:1；
    // 但**柔绘显式采用极淡描边**（"无锐利硬边缘"是该主题的既定设计语言）⇒ 仅记录，不计不达标。
    // divider 在本项目里只作列表**装饰性**分隔（不代表可交互边界）⇒ 同样仅记录。
    val softOutline = preset == AppThemePreset.SOFT
    out += Row(
        "非文本", "outline", t.outline, "cardBg", t.cardBg, NON_TEXT_MIN,
        enforced = !softOutline,
        note = if (softOutline) "柔绘既定设计：极淡描边（无锐利硬边缘）" else "",
    )
    out += Row("非文本(参考)", "divider", t.divider, "cardBg", t.cardBg, NON_TEXT_MIN, enforced = false)
    return out
}

private fun semanticColor(t: AppColorTokens, name: String): Color? = when (name) {
    "primary" -> t.primary
    "success" -> t.success
    "info" -> t.info
    "warning" -> t.warning
    "amber" -> t.amber
    "danger" -> t.danger
    "favorite" -> t.favorite
    else -> null
}

private fun semanticSoft(t: AppColorTokens, name: String): Color? = when (name) {
    "primary" -> t.primarySoft
    "success" -> t.successSoft
    "info" -> t.infoSoft
    "warning" -> t.warningSoft
    "amber" -> t.amberSoft
    "danger" -> t.dangerSoft
    "favorite" -> t.favoriteSoft
    else -> null
}

fun main() {
    // 控制台按 UTF-8 输出：`:desktopApp:run` 的工作目录是 desktopApp，且重定向到日志时
    // JVM 默认用系统 GBK 编码，中文会变成乱码（读日志时无法判断哪一项不达标）。
    System.setOut(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))

    val presets = listOf(
        AppThemePreset.IOS to "通透",
        AppThemePreset.SOFT to "柔绘",
        AppThemePreset.CLAUDE to "书卷",
    )
    // 产物固定落在**仓库根**的 build_qa/，不跟随 run 任务的工作目录（否则会写进 desktopApp/）
    val cwd = File(System.getProperty("user.dir") ?: ".")
    val root = if (cwd.name == "desktopApp") cwd.parentFile ?: cwd else cwd
    val dir = File(root, "build_qa/contrast_probe")
    dir.mkdirs()

    val md = StringBuilder()
    md.appendLine("# 三主题对比度审计（AC1）")
    md.appendLine()
    md.appendLine("判据：WCAG 2.2 AA —— 文本 ≥ 4.5:1，非文本 UI 边界 ≥ 3:1。")
    md.appendLine("数据来源：运行时 `appColorTokens(isDark, preset)`，即真实渲染值。")
    md.appendLine()

    var total = 0
    var failed = 0
    val failures = mutableListOf<String>()

    for ((preset, label) in presets) {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "深色" else "浅色"
            val t = appColorTokens(isDark = dark, preset = preset)
            val rows = collect(t, preset, dark)
            val bad = rows.filter { !it.pass }
            total += rows.size
            failed += bad.size

            println("\n=== $label · $mode（${rows.size} 项，不达标 ${bad.size}）===")
            for (r in rows) {
                val flag = if (r.pass) "OK  " else "FAIL"
                val line = "  $flag ${r.fgName} on ${r.bgName}: " +
                    String.format("%.2f", r.ratio) + " (需 ≥${r.minRatio})"
                println(line)
                if (!r.pass) {
                    failures += "$label·$mode ${r.fgName} on ${r.bgName} = " +
                        String.format("%.2f", r.ratio)
                }
            }

            md.appendLine("## $label · $mode")
            md.appendLine()
            md.appendLine("| 类别 | 前景 | 背景 | 对比度 | 要求 | 结论 | 备注 |")
            md.appendLine("|---|---|---|---|---|---|---|")
            for (r in rows) {
                md.appendLine(
                    "| ${r.section} | `${r.fgName}` ${hex(r.fg)} | `${r.bgName}` ${hex(r.bg)} | " +
                        String.format("%.2f", r.ratio) + " | " +
                        (if (r.enforced) "≥${r.minRatio}" else "参考") + " | " +
                        (if (!r.enforced) "—" else if (r.pass) "✅" else "❌") + " | ${r.note} |"
                )
            }
            md.appendLine()
        }
    }

    println("\n\n=== 汇总：$total 项，不达标 $failed 项 ===")
    for (f in failures) println("  ❌ $f")

    md.appendLine("## 汇总")
    md.appendLine()
    md.appendLine("共 $total 项，不达标 **$failed** 项。")
    if (failures.isNotEmpty()) {
        md.appendLine()
        for (f in failures) md.appendLine("- ❌ $f")
    }

    val out = File(dir, "report.md")
    out.writeText(md.toString())
    println("\n报告：${out.absolutePath}")
}
