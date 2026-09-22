package com.shangkeschedule

import com.shangkeschedule.data.model.AppThemePreset
import java.io.File

/**
 * 视觉回归预览宿主（仅本地校验用，不参与发布构建）。
 *
 * 用 ImageComposeScene 把**书卷（Claude）**主题的今日课表页离屏渲染成 PNG，输出到
 * build_qa/claude-design/，与设计包 claude-schedule-page.design/pages/今日日程.html
 * 的同尺寸截图做像素比对（tools/compare-shots.mjs）。
 *
 * 运行：
 *   .\gradlew.bat :desktopApp:run "-PpreviewMainClass=com.shangkeschedule.ClaudePreviewKt"
 * 可用 -DclaudePreviewDark / -DclaudePreviewWidth / -DclaudePreviewHeight / -DclaudePreviewName 调整。
 */
fun main() = renderTodayPreview(
    preset = AppThemePreset.CLAUDE,
    outDir = File("../build_qa/claude-design"),
    propPrefix = "claudePreview",
    defaultShotName = "claude-today-preview",
    // 书卷主题走自定义字体，异步加载更慢，需要更长的稳定帧等待
    settleMillis = 5000L,
)
