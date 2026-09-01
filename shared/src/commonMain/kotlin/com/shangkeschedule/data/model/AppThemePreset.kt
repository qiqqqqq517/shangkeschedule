package com.shangkeschedule.data.model

import androidx.compose.ui.graphics.Color
import com.shangkeschedule.data.model.schedule_style.BorderTypeProto
import com.shangkeschedule.data.model.schedule_style.ScheduleModeProto
import org.jetbrains.compose.resources.StringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.theme_preset_classic
import shangkeschedule.shared.generated.resources.theme_preset_clean
import shangkeschedule.shared.generated.resources.theme_preset_cloud

/**
 * App 主题预设：把全局配色种子色与课表视觉样式统一为一套主题。
 *
 * ORIGINAL = 「经典」默认蓝紫；SLEEPY = 「云舒」大圆角柔和阴影；TIMETABLE = 「利落」白底左侧色条紧凑。
 */
enum class AppThemePreset(
    val value: String,
    val labelRes: StringResource,
    val seedColor: Color,
    val gridStyle: ScheduleGridStyle
) {
    ORIGINAL(
        value = "ORIGINAL",
        labelRes = Res.string.theme_preset_classic,
        seedColor = Color(0xFF6750A4),
        gridStyle = ScheduleGridStyle.DEFAULT
    ),
    SLEEPY(
        value = "SLEEPY",
        labelRes = Res.string.theme_preset_cloud,
        seedColor = Color(0xFF6750A4),
        gridStyle = CloudGridStyle
    ),
    TIMETABLE(
        value = "TIMETABLE",
        labelRes = Res.string.theme_preset_clean,
        seedColor = Color(0xFF4A6CF7),
        gridStyle = CleanGridStyle
    );

    companion object {
        fun fromString(value: String?): AppThemePreset =
            entries.find { it.value == value } ?: ORIGINAL
    }
}

private val CloudGridStyle = ScheduleGridStyle(
    timeColumnWidthDp = 46f,
    dayHeaderHeightDp = 56f,
    sectionHeightDp = 72f,
    courseBlockCornerRadiusDp = 8f,
    courseBlockOuterPaddingDp = 1f,
    courseBlockInnerPaddingDp = 2f,
    courseBlockAlphaFloat = 1f,
    courseColorMaps = listOf(
        DualColor(light = Color(0xFFEADDFF), dark = Color(0xFF4F378B)),
        DualColor(light = Color(0xFFD1E4FF), dark = Color(0xFF00497D)),
        DualColor(light = Color(0xFFB7F397), dark = Color(0xFF295D09)),
        DualColor(light = Color(0xFFFFD8E4), dark = Color(0xFF633B48)),
        DualColor(light = Color(0xFFFFDBC8), dark = Color(0xFF783200)),
        DualColor(light = Color(0xFFD7E3F7), dark = Color(0xFF3B4858)),
        DualColor(light = Color(0xFFF2DAFF), dark = Color(0xFF523F5F)),
        DualColor(light = Color(0xFFBCEBEB), dark = Color(0xFF1E4E4E)),
        DualColor(light = Color(0xFFE9E4AA), dark = Color(0xFF4A481D)),
        DualColor(light = Color(0xFFFFF9C4), dark = Color(0xFF7A5B00)),
        DualColor(light = Color(0xFFFFD8D8), dark = Color(0xFF8C1D18)),
        DualColor(light = Color(0xFFE3F2E9), dark = Color(0xFF1B5E20)),
    ),
    courseBlockFontScale = 1.2f,
    hideGridLines = false,
    hideSectionTime = false,
    hideDateUnderDay = false,
    showStartTime = false,
    hideLocation = false,
    hideTeacher = false,
    removeLocationAt = false,
    textAlignCenterHorizontal = true,
    textAlignCenterVertical = false,
    borderType = BorderTypeProto.BORDER_TYPE_NONE,
    scheduleMode = ScheduleModeProto.SECTION_MODE,
    pageTextColorLong = null,
    courseTextColorLong = null,
    backgroundImagePath = null
)

private val CleanGridStyle = ScheduleGridStyle(
    timeColumnWidthDp = 44f,
    dayHeaderHeightDp = 44f,
    sectionHeightDp = 58f,
    courseBlockCornerRadiusDp = 8f,
    courseBlockOuterPaddingDp = 1f,
    courseBlockInnerPaddingDp = 2f,
    courseBlockAlphaFloat = 1f,
    courseColorMaps = listOf(
        DualColor(light = Color(0xFFE0F7FA), dark = Color(0xFF006064)),
        DualColor(light = Color(0xFFE8F5E9), dark = Color(0xFF2E7D32)),
        DualColor(light = Color(0xFFFFF8E1), dark = Color(0xFFF9A825)),
        DualColor(light = Color(0xFFF3E5F5), dark = Color(0xFF7B1FA2)),
        DualColor(light = Color(0xFFE3F2FD), dark = Color(0xFF1565C0)),
        DualColor(light = Color(0xFFFCE4EC), dark = Color(0xFFC62828)),
        DualColor(light = Color(0xFFFFF3E0), dark = Color(0xFFEF6C00)),
        DualColor(light = Color(0xFFE0F2F1), dark = Color(0xFF00695C)),
        DualColor(light = Color(0xFFF1F8E9), dark = Color(0xFF558B2F)),
        DualColor(light = Color(0xFFEDE7F6), dark = Color(0xFF4527A0)),
        DualColor(light = Color(0xFFE1F5FE), dark = Color(0xFF0277BD)),
        DualColor(light = Color(0xFFFBE9E7), dark = Color(0xFFBF360C)),
    ),
    courseBlockFontScale = 1.2f,
    hideGridLines = false,
    hideSectionTime = false,
    hideDateUnderDay = false,
    showStartTime = false,
    hideLocation = false,
    hideTeacher = false,
    removeLocationAt = true,
    textAlignCenterHorizontal = false,
    textAlignCenterVertical = false,
    borderType = BorderTypeProto.BORDER_TYPE_NONE,
    scheduleMode = ScheduleModeProto.SECTION_MODE,
    pageTextColorLong = null,
    courseTextColorLong = null,
    backgroundImagePath = null
)
