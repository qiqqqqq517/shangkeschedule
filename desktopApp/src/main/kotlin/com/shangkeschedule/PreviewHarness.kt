package com.shangkeschedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shangkeschedule.data.db.main.Course
import com.shangkeschedule.data.db.main.TodoItem
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
 * 三主题视觉回归预览宿主的公共骨架（仅本地校验用，不参与发布构建）。
 *
 * 三个 `*PreviewKt` 入口只差「主题预设 / 输出目录 / 默认文件名 / 附加校验」，
 * 渲染链、稳定帧等待、PNG 编码与共享预览数据统一收口在这里。
 */

/** 读 `-D` 系统属性；Gradle 的 `:desktopApp:run` 会 fork JVM，`-D` 不总透传，故同时支持同名大写环境变量。 */
internal fun readPreviewProp(name: String, default: String): String =
    System.getProperty(name)
        ?: System.getenv(
            name.replace(Regex("([a-z])([A-Z])"), "$1_$2").replace('.', '_').uppercase()
        )
        ?: default

internal fun renderTodayPreview(
    preset: AppThemePreset,
    outDir: File,
    propPrefix: String,
    defaultShotName: String,
    settleMillis: Long = 600L,
    adjustShotName: (name: String, dark: Boolean) -> String = { name, _ -> name },
    afterSave: ((file: File, dark: Boolean) -> Unit)? = null,
) {
    val dark = readPreviewProp("${propPrefix}Dark", "false").toBoolean()
    val width = readPreviewProp("${propPrefix}Width", "430").toInt()
    val height = readPreviewProp("${propPrefix}Height", "932").toInt()
    val shotName = adjustShotName(readPreviewProp("${propPrefix}Name", defaultShotName), dark)

    runBlocking(Dispatchers.Default) {
        val scene = ImageComposeScene(
            width = width,
            height = height,
            density = Density(1f),
        ) {
            // 与真机同一条主题链：ShangKeScheduleTheme 内部按 themePreset 分流
            // token / 配色 / 字阶，并注入 appColors()/appShapes()/appType()
            ShangKeScheduleTheme(
                darkTheme = dark,
                themePreset = preset,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appColors().pageBg)
                ) {
                    // 与真机同源：显式注入同一预设，保证页面分支与配色令牌一致
                    CompositionLocalProvider(LocalThemePreset provides preset) {
                        TodayContent(
                            state = previewTodayState(),
                            bottomInset = 0.dp,
                            // 必须用该预设自己的课表样式，否则课程色条会落到默认马卡龙色板
                            gridStyle = preset.gridStyle,
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
        Thread.sleep(settleMillis)
        val image = scene.render()
        scene.close()

        val bytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes
        if (bytes == null) {
            println("render failed: encode returned null")
            return@runBlocking
        }
        outDir.mkdirs()
        val file = File(outDir, "$shotName.png")
        file.writeBytes(bytes)
        println("rendered -> ${file.absolutePath} (${width}x$height, ${bytes.size} bytes)")

        afterSave?.invoke(file, dark)
    }
}

/** 三主题共用的预览数据，保证三个宿主的渲染内容可比。 */
internal fun previewTodayState(): TodayUiState.Success {
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
        // 今日待办区（todo_items）像素回归样例：带时间 / 无时间 / 已完成各一条
        todos = listOf(
            TodoItem(
                id = "todo-1", date = "2026-09-09", title = "交操作系统实验报告",
                note = "第 4 章 进程调度", time = "18:00", done = false,
                sortOrder = 0, createdAt = 0L, updatedAt = 0L
            ),
            TodoItem(
                id = "todo-2", date = "2026-09-09", title = "复习英语六级单词",
                time = null, done = false,
                sortOrder = 1, createdAt = 0L, updatedAt = 0L
            ),
            TodoItem(
                id = "todo-3", date = "2026-09-09", title = "归还图书馆借书",
                note = null, time = "20:30", done = true,
                sortOrder = 2, createdAt = 0L, updatedAt = 0L
            ),
        ),
        weekIndex = 3,
        today = LocalDate(2026, 9, 9),
        status = TodayStatus.Normal,
        startDate = LocalDate(2026, 8, 24),
        totalWeeks = 20,
        firstDayOfWeek = 1,
    )
}
