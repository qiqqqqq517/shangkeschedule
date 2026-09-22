package com.shangkeschedule

import com.shangkeschedule.data.model.AppThemePreset
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
 * 尺寸可调（默认 430x932 与另两个宿主一致；新增页面底部区段如「今日待办」在 932
 * 视口下不可见，验该区时用 `-DsoftPreviewHeight=1400` 渲染整页——Gradle 的 run 会
 * fork JVM，`-D` 需经 JAVA_TOOL_OPTIONS 传入；也可用环境变量 SOFT_PREVIEW_HEIGHT）。
 */
fun main() = renderTodayPreview(
    preset = AppThemePreset.SOFT,
    outDir = File("../build_qa/softdraw"),
    propPrefix = "softPreview",
    defaultShotName = "soft-today-light",
)
