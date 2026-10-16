package com.shangkeschedule.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.AppThemeMode
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.ui.components.AppEmptyState
import com.shangkeschedule.ui.components.AppErrorState
import com.shangkeschedule.ui.components.AppPageHeader
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppSegmentedControl
import com.shangkeschedule.ui.settings.AppSettingRow
import com.shangkeschedule.ui.settings.AppSettingsGroup
import com.shangkeschedule.ui.theme.SettingsEntryTone
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private val previewIcon = ImageVector.Builder("preview-list", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 7f); lineTo(4f, 7f); close()
        moveTo(4f, 11f); lineTo(20f, 11f); lineTo(20f, 13f); lineTo(4f, 13f); close()
        moveTo(4f, 17f); lineTo(16f, 17f); lineTo(16f, 19f); lineTo(4f, 19f); close()
    }
}.build()

/**
 * 共用组件视觉核验：三主题深浅档、窄屏及大字体，始终渲染正式组件。
 * COMPONENTS_PREVIEW_PHASE 区分改动前后产物；WIDTH / FONT_SCALE 可覆盖默认视口。
 */
fun main() = runBlocking(Dispatchers.Default) {
    val phase = readPreviewProp("componentsPreviewPhase", "after")
    val width = readPreviewProp("componentsPreviewWidth", "390").toInt()
    val fontScale = readPreviewProp("componentsPreviewFontScale", "1.0").toFloat()
    val outDir = File("../build_qa/ui-polish/$phase").apply { mkdirs() }
    for (preset in AppThemePreset.entries) {
        for (dark in listOf(false, true)) {
            val scene = ImageComposeScene(
                width = width,
                height = 1320,
                density = Density(1f, fontScale),
            ) {
                ShangKeComponentsPreview(preset, dark)
            }
            try {
                scene.render()
                Thread.sleep(600)
                val bytes = checkNotNull(scene.render().encodeToData(EncodedImageFormat.PNG)).bytes
                val name = "${preset.name.lowercase()}-${if (dark) "dark" else "light"}-$width-$fontScale.png"
                File(outDir, name).writeBytes(bytes)
                println("rendered -> ${File(outDir, name).absolutePath}")
            } finally {
                scene.close()
            }
        }
    }
}

@Composable
private fun ShangKeComponentsPreview(preset: AppThemePreset, dark: Boolean) {
    com.shangkeschedule.ui.theme.ShangKeScheduleTheme(
        settings = AppSettingsModel(
            themePreset = preset,
            themeMode = if (dark) AppThemeMode.DARK else AppThemeMode.LIGHT,
            reduceMotionEnabled = true,
        )
    ) {
        val spacing = appSpacing()
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(appColors().pageBg),
            contentPadding = PaddingValues(horizontal = spacing.pageHorizontal, vertical = spacing.pageTop),
            verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
        ) {
            item {
                AppPageHeader(title = "我的课表", subtitle = "10 月 16 日 · 星期五")
            }
            item {
                var selected by remember { mutableStateOf(0) }
                AppSegmentedControl(
                    options = listOf("本周", "全部课程", "已结束"),
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
            }
            item {
                Column {
                    AppSectionHeader("课程与同步")
                    AppSettingsGroup {
                        AppSettingRow(
                            title = "当前课表",
                            icon = previewIcon,
                            tone = SettingsEntryTone.PURPLE,
                            detail = "2026—2027 学年第一学期 · 软件工程专业",
                            onClick = {},
                        )
                        AppSettingRow(
                            title = "自动同步",
                            subtitle = "备份课表与偏好设置，换设备时也能继续使用。",
                            icon = previewIcon,
                            tone = SettingsEntryTone.GREEN,
                            showDivider = true,
                            onClick = {},
                        )
                    }
                }
            }
            item {
                AppEmptyState(
                    hint = "今天没有课程，留一点时间给自己。",
                    actionLabel = "查看本周课表",
                    onAction = {},
                )
            }
            item {
                AppErrorState(
                    hint = "暂时无法获取课程信息，请检查网络后再试。",
                    onRetry = {},
                )
            }
        }
    }
}
