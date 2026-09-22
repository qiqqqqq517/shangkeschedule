package com.shangkeschedule

import com.shangkeschedule.data.model.AppThemePreset
import java.io.File
import javax.imageio.ImageIO

/**
 * 视觉核验预览宿主（仅本地校验用，不参与发布构建）。
 *
 * 用 ImageComposeScene 把**通透（iOS 26）**主题的今日课表页离屏渲染成 PNG，
 * 输出到 build_qa/ios26/，并附一份关键像素采样报告（像素级验证在无可视化工具时也能跑）。
 *
 * 与 [ClaudePreviewKt] 的分工：那个渲染书卷主题，这个渲染通透主题——两套主题各自
 * 走自己的 CompositionLocal 链（`themePreset` 分别传 CLAUDE / IOS）。
 *
 * 运行：
 *   .\gradlew.bat :desktopApp:run "-PpreviewMainClass=com.shangkeschedule.IosPreviewKt"
 * 可用 -DiosPreviewDark / -DiosPreviewWidth / -DiosPreviewHeight / -DiosPreviewName 调整。
 */
fun main() = renderTodayPreview(
    preset = AppThemePreset.IOS,
    outDir = File("../build_qa/ios26"),
    propPrefix = "iosPreview",
    defaultShotName = "ios26-today-light",
    // 深色档默认文件名自动带 -dark，省去每次手动指定
    adjustShotName = { name, dark ->
        if (dark && name.endsWith("-light")) name.replace("-light", "-dark") else name
    },
    afterSave = ::reportPixels,
)

/**
 * 关键像素采样报告：在无截图工具的环境下，用「取色 + 出现次数」证明主题确实生效。
 *
 * 检查项（通透浅色）：
 * - 页面底必须是 systemGroupedBackground #F2F2F7；
 * - 必须出现 systemBlue #007AFF 像素（周次胶囊 / 圆点 / 图标）；
 * - 不得出现书卷暖砂底 #FAF9F5 或赤陶 #C96442（那说明主题串了）。
 */
private fun reportPixels(file: File, dark: Boolean) {
    val img = ImageIO.read(file) ?: run {
        println("pixel report skipped: cannot read back png")
        return
    }
    fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)
    val counts = HashMap<Int, Int>()
    for (y in 0 until img.height step 2) {
        for (x in 0 until img.width step 2) {
            val c = img.getRGB(x, y) and 0xFFFFFF
            counts[c] = (counts[c] ?: 0) + 1
        }
    }
    val top = counts.entries.sortedByDescending { it.value }.take(12)
    println("--- pixel report (${if (dark) "dark" else "light"}) ---")
    println("top colors: " + top.joinToString(", ") { "${hex(it.key)}x${it.value}" })

    fun countOf(target: Int) = counts[target] ?: 0
    println("pageBg F2F2F7        = ${countOf(0xF2F2F7)}")
    println("cardBg FFFFFF        = ${countOf(0xFFFFFF)}")
    println("systemBlue 007AFF    = ${countOf(0x007AFF)}")
    println("claude sand FAF9F5   = ${countOf(0xFAF9F5)}  (必须为 0)")
    println("claude terra C96442  = ${countOf(0xC96442)}  (必须为 0)")
}
