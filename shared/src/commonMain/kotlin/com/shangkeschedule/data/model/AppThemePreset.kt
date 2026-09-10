package com.shangkeschedule.data.model

import androidx.compose.ui.graphics.Color
import com.shangkeschedule.data.model.schedule_style.BorderTypeProto
import com.shangkeschedule.data.model.schedule_style.ScheduleModeProto
import org.jetbrains.compose.resources.StringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.theme_preset_classic
import shangkeschedule.shared.generated.resources.theme_preset_claude
import shangkeschedule.shared.generated.resources.theme_preset_cloud
import shangkeschedule.shared.generated.resources.theme_preset_ios

/**
 * App 主题预设：把全局配色种子色与课表视觉样式统一为一套主题。
 *
 * IOS = 「通透」iOS 风格严格 Apple 系统色；ORIGINAL = 「经典」默认蓝紫；SLEEPY = 「云舒」大圆角柔和阴影；CLAUDE = 「Claude」Anthropic 设计系统暖砂纸底 + 赤陶主色。
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
    ;

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

// --- CLAUDE 主题专属课表样式 ---
// 课程配色：20 个色相按 18° 等距环绕。浅色 / 深色**同色相配对**（浅色 0x40 淡底、深色实底）。
//
// ⚠️ 色差口径（重要）：设计注释里「全池最近色差 ≈ 88」是**原始 RGB、忽略 alpha** 的口径，
//    只有深色池（实底 alpha=1.0）成立；浅色池每色仅 0x40（25%）alpha，渲染色 = 0.25×色池色 +
//    0.75×暖砂底 #FAF9F5，所有色相被同向拉向页面底，色差按比例压缩约 4 倍。
//    因此 88 这个数字**不能**当作浅色模式的实际区分度（v3.35.5 的注释即因此误导）。
//
// 本轮修正在「0x40 淡底 + 深浅同色相配对 + 颜色池 20 项」三个约定都不动的前提下，只重排浅色池的
// 明度与饱和度：把原始色明度跨度从 0.38–0.54 拉大到约 0.20–0.95，靠「色相 + 明度」双维度拉开距离。
// 同时把**渲染后亮度带**约束在 0.60–0.88（修正前 0.58–0.83），因此块的深浅观感基本不变、不会出现
// 过白看不出的块。实测（redmean，渲染色口径）：
//   · 旧 12 色池（v3.35.5 之前）        5.3
//   · v3.35.5 的 20 色池（修正前）      21.7
//   · 本方案（仅改浅色池）              42.5   ← 已接近 0x40 淡底约定的理论上限（47.6）
//   · 深色池（实色，未改动）            88.5
// 浅色池 20 色对正文 #3D3929 的最低对比度 7.2:1，满足 WCAG AA。
// 若要继续提高，只能放弃 0x40 淡底：alpha ≥ 0xB0(0.69) 才能让浅色池也达到 88.5（颜色池会明显变实）。
private val ClaudeGridStyle = ScheduleGridStyle(
    timeColumnWidthDp = 44f,
    dayHeaderHeightDp = 48f,
    sectionHeightDp = 76f,
    courseBlockCornerRadiusDp = 8f,
    courseBlockOuterPaddingDp = 1f,
    courseBlockInnerPaddingDp = 3f,
    courseBlockAlphaFloat = 1f,
    courseColorMaps = listOf(
        DualColor(light = Color(0x40913030), dark = Color(0xFFD04343)), // 赤红（0°）
        DualColor(light = Color(0x40EE4B04), dark = Color(0xFF9B4A27)), // 赭石（18°）
        DualColor(light = Color(0x40FFA51D), dark = Color(0xFFD09843)), // 琥珀（36°）
        DualColor(light = Color(0x40E5DA76), dark = Color(0xFF9B8F27)), // 橄榄金（54°）
        DualColor(light = Color(0x405E7502), dark = Color(0xFFB4D043)), // 芽黄（72°）
        DualColor(light = Color(0x4088F31E), dark = Color(0xFF619B27)), // 苔绿（90°）
        DualColor(light = Color(0x406BAF5B), dark = Color(0xFF5FD043)), // 翠绿（108°）
        DualColor(light = Color(0x4026FF3B), dark = Color(0xFF279B32)), // 松绿（126°）
        DualColor(light = Color(0x4002B048), dark = Color(0xFF43D07C)), // 青瓷（144°）
        DualColor(light = Color(0x4001593F), dark = Color(0xFF279B78)), // 湖水（162°）
        DualColor(light = Color(0x401EC0C0), dark = Color(0xFF43D0D0)), // 天青（180°）
        DualColor(light = Color(0x406FD4FF), dark = Color(0xFF27789B)), // 靛青（198°）
        DualColor(light = Color(0x400062F1), dark = Color(0xFF437CD0)), // 宝蓝（216°）
        DualColor(light = Color(0x404A5191), dark = Color(0xFF27329B)), // 藏蓝（234°）
        DualColor(light = Color(0x40968CBC), dark = Color(0xFF5F43D0)), // 紫罗兰（252°）
        DualColor(light = Color(0x408F39E6), dark = Color(0xFF61279B)), // 紫（270°）
        DualColor(light = Color(0x40E478FF), dark = Color(0xFFB443D0)), // 品红（288°）
        DualColor(light = Color(0x40F5CEF1), dark = Color(0xFF9B278F)), // 紫红（306°）
        DualColor(light = Color(0x40D50D85), dark = Color(0xFFD04398)), // 玫红（324°）
        DualColor(light = Color(0x40EF628D), dark = Color(0xFF9B274A)), // 酒红（342°）
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
