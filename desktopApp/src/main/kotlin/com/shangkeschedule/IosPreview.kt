package com.shangkeschedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.shangkeschedule.data.db.main.Course
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.ShangKeScheduleTheme
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.today.CourseDisplayModel
import com.shangkeschedule.ui.today.TodayContent
import com.shangkeschedule.ui.today.TodayStatus
import com.shangkeschedule.ui.today.TodayUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
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
private val OUT_DIR = File("../build_qa/ios26")

fun main() {
    // 读 -D 系统属性；Gradle 的 :desktopApp:run 会 fork 子进程，-D 不总是透传，
    // 因此同时支持环境变量便于从命令行切换（IOS_PREVIEW_DARK=true）。
    fun prop(name: String, default: String): String =
        System.getProperty(name)
            ?: System.getenv(name.replace('.', '_').uppercase())
            ?: default

    val dark = prop("iosPreviewDark", "false").toBoolean()
    val width = prop("iosPreviewWidth", "430").toInt()
    val height = prop("iosPreviewHeight", "932").toInt()
    val shotName = prop("iosPreviewName", "ios26-today-light").let {
        if (dark && it.endsWith("-light")) it.replace("-light", "-dark") else it
    }

    runBlocking(Dispatchers.Default) {
        val scene = ImageComposeScene(
            width = width,
            height = height,
            density = Density(1f),
        ) {
            // 与真机同一条主题链：ShangKeScheduleTheme 内部按 themePreset 分流
            // （IOS → iosColorScheme + IosStyle token + SF 字阶；CLAUDE → claude* 那一套）
            ShangKeScheduleTheme(
                darkTheme = dark,
                dynamicColor = false,
                customLightPrimary = Color(0xFF007AFF),
                customDarkPrimary = Color(0xFF0A84FF),
                themePreset = AppThemePreset.IOS,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appColors().pageBg)
                ) {
                    // 与真机同源：预览宿主走的是 ShangKeScheduleTheme(themePreset = IOS)，
                    // 因此这里显式注入同一预设，保证页面分支与配色令牌一致。
                    CompositionLocalProvider(LocalThemePreset provides AppThemePreset.IOS) {
                        TodayContent(
                            state = previewState(),
                            bottomInset = 0.dp,
                            // 必须用 IOS 预设自己的课表样式，否则课程色条会落到默认马卡龙色板
                            gridStyle = AppThemePreset.IOS.gridStyle,
                            isDark = dark,
                            onToggleTodo = { _, _ -> },
                            onEditTodo = { },
                        )
                    }
                }
            }
        }

        // 第一帧完成布局，第二帧拿到稳定画面（字体异步加载也在此时就绪）
        scene.render()
        Thread.sleep(600)
        val image = scene.render()
        scene.close()

        val bytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes
        if (bytes == null) {
            println("render failed: encode returned null")
            return@runBlocking
        }
        OUT_DIR.mkdirs()
        val file = File(OUT_DIR, "$shotName.png")
        file.writeBytes(bytes)
        println("rendered -> ${file.absolutePath} (${width}x$height, ${bytes.size} bytes)")

        reportPixels(file, dark)
    }
}

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

private fun previewState(): TodayUiState.Success {
    fun course(
        id: String,
        name: String,
        teacher: String,
        room: String,
        color: Int,
        lab: Boolean = false,
        credit: String = "3.0",
    ) = Course(
        id = id,
        courseTableId = "preview",
        name = name,
        teacher = teacher,
        position = room,
        day = 3,
        startSection = 1,
        endSection = 2,
        isCustomTime = false,
        customStartTime = null,
        customEndTime = null,
        colorInt = color,
        remark = null,
        credit = credit,
        assessmentMethod = null,
        isLab = lab,
        isCrush = false,
    )

    val today = listOf(
        CourseDisplayModel(course("1", "高等数学", "王教授", "教一 201", 0, credit = "4.0"), "08:00", "09:40"),
        CourseDisplayModel(course("2", "大学英语", "李老师", "外语楼 305", 1), "10:00", "11:40"),
        CourseDisplayModel(course("3", "数据结构", "张教授", "计算机楼 412", 2), "14:00", "15:40"),
        CourseDisplayModel(course("4", "物理实验", "陈老师", "实验楼 B203", 4, lab = true, credit = "1.0"), "16:00", "17:40"),
    )
    val tomorrow = listOf(
        CourseDisplayModel(course("5", "计算机网络", "赵教授", "信工楼 501", 3), "08:00", "09:40"),
        CourseDisplayModel(course("6", "线性代数", "孙老师", "教二 103", 0), "10:00", "11:40"),
    )

    return TodayUiState.Success(
        courses = today,
        tomorrowCourses = tomorrow,
        todos = emptyList(),
        weekIndex = 3,
        today = LocalDate(2026, 9, 9),
        status = TodayStatus.Normal,
        startDate = LocalDate(2026, 8, 24),
        totalWeeks = 20,
        firstDayOfWeek = 1,
    )
}
