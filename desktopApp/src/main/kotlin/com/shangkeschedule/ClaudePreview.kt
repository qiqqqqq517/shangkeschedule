package com.shangkeschedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.shangkeschedule.data.db.main.Course
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.ui.theme.ShangKeScheduleTheme
import com.shangkeschedule.ui.theme.LocalThemePreset
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

/**
 * 视觉回归预览宿主（仅本地校验用，不参与发布构建）。
 *
 * 用 ImageComposeScene 把 Claude 主题的今日课表页离屏渲染成 PNG，输出到
 * build_qa/claude-design/，与设计包 claude-schedule-page.design/pages/今日日程.html
 * 的同尺寸截图做像素比对（tools/compare-shots.mjs）。
 *
 * 运行：
 *   .\gradlew.bat :desktopApp:run "-PpreviewMainClass=com.shangkeschedule.ClaudePreviewKt"
 * 可用 -DclaudePreviewDark / -DclaudePreviewWidth / -DclaudePreviewHeight / -DclaudePreviewName 调整。
 */
private val OUT_DIR = File("../build_qa/claude-design")

fun main() {
    val dark = System.getProperty("claudePreviewDark", "false").toBoolean()
    val width = System.getProperty("claudePreviewWidth", "430").toInt()
    val height = System.getProperty("claudePreviewHeight", "932").toInt()
    val shotName = System.getProperty("claudePreviewName", "claude-today-preview")

    runBlocking(Dispatchers.Default) {
        val scene = ImageComposeScene(
            width = width,
            height = height,
            density = Density(1f),
        ) {
            // 与真机同一条主题链：ShangKeScheduleTheme 内部通过 withAppSurfaces 把页面底/卡片
            // 映射进 M3 ColorScheme，并注入 appColors()/appShapes()/appType() 三套 token
            ShangKeScheduleTheme(
                darkTheme = dark,
                dynamicColor = false,
                customLightPrimary = Color(0xFFC96442),
                customDarkPrimary = Color(0xFFD97757),
                themePreset = AppThemePreset.CLAUDE,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appColors().pageBg)
                ) {
                    // 与真机同源：预览宿主走的是 ShangKeScheduleTheme(themePreset = CLAUDE)，
                    // 因此这里显式注入同一预设，保证页面分支与配色令牌一致。
                    CompositionLocalProvider(LocalThemePreset provides AppThemePreset.CLAUDE) {
                        TodayContent(
                            state = previewState(),
                            bottomInset = 0.dp,
                            // 必须用 CLAUDE 预设自己的课表样式，否则课程色条会落到默认马卡龙色板
                            gridStyle = AppThemePreset.CLAUDE.gridStyle,
                            isDark = dark,
                            onToggleTodo = { _, _ -> },
                            onToggleEventDone = { _, _ -> },
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
    }
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
