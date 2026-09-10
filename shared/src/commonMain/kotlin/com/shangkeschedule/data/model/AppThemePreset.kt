package com.shangkeschedule.data.model

import androidx.compose.ui.graphics.Color
import com.shangkeschedule.data.model.schedule_style.BorderTypeProto
import com.shangkeschedule.data.model.schedule_style.ScheduleModeProto
import org.jetbrains.compose.resources.StringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.theme_preset_classic
import shangkeschedule.shared.generated.resources.theme_preset_claude
import shangkeschedule.shared.generated.resources.theme_preset_clean
import shangkeschedule.shared.generated.resources.theme_preset_cloud
import shangkeschedule.shared.generated.resources.theme_preset_ios

/**
 * App 主题预设：把全局配色种子色与课表视觉样式统一为一套主题。
 *
 * IOS = 「通透」iOS 风格严格 Apple 系统色；ORIGINAL = 「经典」默认蓝紫；SLEEPY = 「云舒」大圆角柔和阴影；TIMETABLE = 「利落」白底左侧色条紧凑；CLAUDE = 「Claude」Anthropic 设计系统暖砂纸底 + 赤陶主色。
 */
enum class AppThemePreset(
    val value: String,
    val labelRes: StringResource,
    val seedColor: Color,
    val gridStyle: ScheduleGridStyle
) {
    IOS(
        value = "IOS",
        labelRes = Res.string.theme_preset_ios,
        seedColor = Color(0xFF007AFF),
        gridStyle = ScheduleGridStyle.IOS
    ),
    CLAUDE(
        value = "CLAUDE",
        labelRes = Res.string.theme_preset_claude,
        seedColor = Color(0xFFC96442),
        gridStyle = ClaudeGridStyle
    ),
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
        fun fromString(value: String?): AppThemePreset = when (value) {
            // 老数据迁移：原「通透」(AIRY) 预设已并入 IOS 预设（同名「通透」）
            "AIRY" -> IOS
            else -> entries.find { it.value == value } ?: ORIGINAL
        }
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

// --- CLAUDE 主题专属课表样式 ---
// 课程配色：20 个色相按 18° 等距环绕，相邻色相之间再交替明度（0.54 / 0.38），
// 使「相邻索引」既差色相又差明度。经离屏距离度量校准，全池最近色对距离 ≈ 88（旧 12 色池仅 23，
// 赤陶/珊瑚/红/玫瑰等暖色几乎无法区分，是课表撞色的根因）；前 12 色最近距离 ≈ 89。
// 浅色沿用同色 0x40 淡底、深色实底的约定（色条/色块/色卡共用）。
private val ClaudeGridStyle = ScheduleGridStyle(
    timeColumnWidthDp = 44f,
    dayHeaderHeightDp = 48f,
    sectionHeightDp = 76f,
    courseBlockCornerRadiusDp = 8f,
    courseBlockOuterPaddingDp = 1f,
    courseBlockInnerPaddingDp = 3f,
    courseBlockAlphaFloat = 1f,
    courseColorMaps = listOf(
        DualColor(light = Color(0x40D04343), dark = Color(0xFFD04343)), // 赤红
        DualColor(light = Color(0x409B4A27), dark = Color(0xFF9B4A27)), // 赭石
        DualColor(light = Color(0x40D09843), dark = Color(0xFFD09843)), // 琥珀
        DualColor(light = Color(0x409B8F27), dark = Color(0xFF9B8F27)), // 橄榄金
        DualColor(light = Color(0x40B4D043), dark = Color(0xFFB4D043)), // 芽黄
        DualColor(light = Color(0x40619B27), dark = Color(0xFF619B27)), // 苔绿
        DualColor(light = Color(0x405FD043), dark = Color(0xFF5FD043)), // 翠绿
        DualColor(light = Color(0x40279B32), dark = Color(0xFF279B32)), // 松绿
        DualColor(light = Color(0x4043D07C), dark = Color(0xFF43D07C)), // 青瓷
        DualColor(light = Color(0x40279B78), dark = Color(0xFF279B78)), // 湖水
        DualColor(light = Color(0x4043D0D0), dark = Color(0xFF43D0D0)), // 天青
        DualColor(light = Color(0x4027789B), dark = Color(0xFF27789B)), // 靛青
        DualColor(light = Color(0x40437CD0), dark = Color(0xFF437CD0)), // 宝蓝
        DualColor(light = Color(0x4027329B), dark = Color(0xFF27329B)), // 藏蓝
        DualColor(light = Color(0x405F43D0), dark = Color(0xFF5F43D0)), // 紫罗兰
        DualColor(light = Color(0x4061279B), dark = Color(0xFF61279B)), // 紫
        DualColor(light = Color(0x40B443D0), dark = Color(0xFFB443D0)), // 品红
        DualColor(light = Color(0x409B278F), dark = Color(0xFF9B278F)), // 紫红
        DualColor(light = Color(0x40D04398), dark = Color(0xFFD04398)), // 玫红
        DualColor(light = Color(0x409B274A), dark = Color(0xFF9B274A)), // 酒红
    ),
    courseBlockFontScale = 1.15f,
    hideGridLines = false,
    hideSectionTime = false,
    hideDateUnderDay = false,
    showStartTime = false,
    hideLocation = false,
    hideTeacher = false,
    removeLocationAt = false,
    textAlignCenterHorizontal = false,
    textAlignCenterVertical = false,
    borderType = BorderTypeProto.BORDER_TYPE_NONE,
    scheduleMode = ScheduleModeProto.SECTION_MODE,
    pageTextColorLong = null,
    courseTextColorLong = null,
    backgroundImagePath = null
)
