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
import kotlinx.datetime.LocalTime
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * 视觉回归预览宿主（仅本地校验用，不参与发布构建）。
 *
 * 与 [ClaudePreviewKt] / [IosPreviewKt] 同构，补齐**柔绘（SOFT）**主题：
 * SOFT 没有设计包基线，回归方式是「重构前自渲染 PNG vs 重构后自渲染 PNG」
 * 逐像素比对（基线请用 build_qa/softdraw/ 下保留的 pre-refactor 副本）。
 *
 * 运行：
 *   .\gradlew.bat :desktopApp:run "-PpreviewMainClass=com.shangkeschedule.SoftPreviewKt"
 */
private val OUT_DIR = File("../build_qa/softdraw")

fun main() {
    val dark = false
    val width = 430
    val height = 932
    val shotName = "soft-today-light"

    runBlocking(Dispatchers.Default) {
        val scene = ImageComposeScene(
            width = width,
            height = height,
            density = Density(1f),
        ) {
            ShangKeScheduleTheme(
                darkTheme = dark,
                dynamicColor = false,
                customLightPrimary = Color(0xFF7C86C9),
                customDarkPrimary = Color(0xFF9AA3DC),
                themePreset = AppThemePreset.SOFT,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appColors().pageBg)
                ) {
                    CompositionLocalProvider(LocalThemePreset provides AppThemePreset.SOFT) {
                        TodayContent(
                            state = previewState(),
                            bottomInset = 0.dp,
                            gridStyle = AppThemePreset.SOFT.gridStyle,
                            isDark = dark,
                            onToggleTodo = { _, _ -> },
                            onToggleEventDone = { _, _ -> },
                            onEditTodo = { },
                            nowOverride = LocalTime(22, 32),
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

/** 与 ClaudePreview/IosPreview 同一份预览数据，保证三主题渲染内容可比。 */
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
