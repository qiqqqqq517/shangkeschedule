package com.shangkeschedule.data.model

import androidx.compose.ui.graphics.Color
import com.shangkeschedule.data.model.schedule_style.BorderTypeProto
import com.shangkeschedule.data.model.schedule_style.ScheduleModeProto
import org.jetbrains.compose.resources.StringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.theme_preset_claude
import shangkeschedule.shared.generated.resources.theme_preset_ios

/**
 * App 主题预设：把全局配色种子色与课表视觉样式统一为一套主题。
 *
 * 当前保留两套主题：
 * - [IOS]「通透」—— iOS 26 / Liquid Glass 风格：Apple 系统色 + 玻璃材质 + 连续圆角。
 *   功能面（页头 / 周次条 / 课程卡片流 / 明日预览 / inset grouped 设置列表 / 弹窗 /
 *   悬浮件）与「书卷」**逐项对齐、位置一致**，只替换 UI 与动画组件。
 * - [CLAUDE]「书卷」—— Anthropic / Claude 设计系统：暖砂纸底 + 赤陶主色 + 衬线字体。
 *   **保持原样，不受通透主题改动影响。**
 *
 * 「经典」(ORIGINAL)、「云舒」(SLEEPY)、「利落」(TIMETABLE) 三套旧主题已删除；
 * 旧用户持久化的这些取值（含早期「通透」AIRY）在 [fromString] 里统一迁移，不会丢配置。
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
    );

    companion object {
        /** 新装用户的默认主题：通透（iOS 26）。 */
        val default: AppThemePreset get() = IOS

        /**
         * 旧数据迁移：历史上存在过 ORIGINAL（经典）/ SLEEPY（云舒）/ TIMETABLE（利落）/
         * AIRY（早期通透）四个已删除取值，统一收敛到 [IOS]。
         */
        fun fromString(value: String?): AppThemePreset = when (value) {
            "ORIGINAL", "SLEEPY", "TIMETABLE", "AIRY" -> IOS
            else -> entries.find { it.value == value } ?: IOS
        }
    }
}

// --- CLAUDE（书卷）主题专属课表样式 ----------------------------------------
// 课程配色：20 个色相按 18° 等距环绕。浅色 / 深色**同色相配对**（浅色 0x40 淡底、深色实底）。
//
// ⚠️ 色差口径（重要）：设计注释里「全池最近色差 ≈ 88」是**原始 RGB、忽略 alpha** 的口径，
//    只有深色池（实底 alpha=1.0）成立；浅色池每色仅 0x40（25%）alpha，渲染色 = 0.25×色池色 +
//    0.75×暖砂底 #FAF9F5，所有色相被同向拉向页面底，色差按比例压缩约 4 倍。
//
// 实测（redmean，渲染色口径）：
//   · 旧 12 色池（v3.35.5 之前）        5.3
//   · v3.35.5 的 20 色池（修正前）      21.7
//   · 本方案（仅改浅色池）              42.5   ← 已接近 0x40 淡底约定的理论上限（47.6）
//   · 深色池（实色，未改动）            88.5
// 浅色池 20 色对正文 #3D3929 的最低对比度 7.2:1，满足 WCAG AA。
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
