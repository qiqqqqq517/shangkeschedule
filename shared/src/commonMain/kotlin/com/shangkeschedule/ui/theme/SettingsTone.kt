package com.shangkeschedule.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.shangkeschedule.data.model.AppThemePreset

/**
 * 设置页条目的**语义色调角色**（10 个）。
 *
 * 这是「语义角色 → 主题视觉色」映射的**上游**：每个设置条目在数据层
 * （`buildSettingsSections()`）只声明语义角色（如「课程管理 = PURPLE」），
 * 具体用什么颜色由当前主题在 [settingsToneColors] 里决定。
 *
 * ## v3.69.0（V3 组件族去重）：为什么要上移到主题层
 *
 * 原结构是四层跳转：
 * ```
 * SettingsEntryTone → toClaudeTone()/toSoftTone()/toIosTone() → Claude/Soft/Ios*CellTone → 三套色函数
 * ```
 * 中间那三张映射表存在**折叠**——柔绘把 GREEN 与 OLIVE 都映射成 SAGE（10 → 9），
 * 通透把 GREEN/MATCHA 都映射成 GREEN、BROWN/GRAY 都映射成 GRAY（10 → 8）。
 * 折叠本身不是 bug（两套主题的色彩库本来就比语义角色少），但把「语义角色」与
 * 「主题色库」混在同一张表里，会让角色数量被主题色库反向约束。
 *
 * 上移后映射只剩一层：角色表由主题层持有，组件层只读 [SettingsEntryTone]，
 * 三张折叠表与三套视觉枚举一并删除。
 */
enum class SettingsEntryTone { PURPLE, ORANGE, RED, OLIVE, MATCHA, PINK, GREEN, GRAY, AMBER, BROWN }

/**
 * 设置行图标徽章的色对：[bg] 容器底、[fg] 图标色。
 *
 * 三主题的取法差异在此收敛：
 * - 书卷：淡彩底 + 同色系深色图标
 * - 柔绘：16%（深色 22%）同色薄涂底 + 同色图标
 * - 通透：实色系统色底 + **白色**图标
 */
data class SettingsToneColors(val bg: Color, val fg: Color)

/** 按当前主题取语义角色的图标色对。组件层唯一入口。 */
@Composable
fun settingsToneColors(tone: SettingsEntryTone): SettingsToneColors = when (LocalThemePreset.current) {
    AppThemePreset.CLAUDE -> claudeSettingsToneColors(tone)
    AppThemePreset.SOFT -> softSettingsToneColors(tone)
    AppThemePreset.IOS -> iosSettingsToneColors(tone)
}

// ---------------------------------------------------------------- 书卷（Claude）

/** 书卷：淡彩底 + 同色系深色图标；深色下底色加深、前景提亮（取值与 v3.68.x 逐条一致）。 */
@Composable
private fun claudeSettingsToneColors(tone: SettingsEntryTone): SettingsToneColors {
    val isDark = LocalIsDarkTheme.current
    return if (isDark) when (tone) {
        SettingsEntryTone.PURPLE -> SettingsToneColors(Color(0xFF2E2A44), Color(0xFFB7A8F5))
        SettingsEntryTone.ORANGE -> SettingsToneColors(Color(0xFF3A2A1E), Color(0xFFE8A87A))
        SettingsEntryTone.RED -> SettingsToneColors(Color(0xFF3A2220), Color(0xFFE88A87))
        SettingsEntryTone.OLIVE -> SettingsToneColors(Color(0xFF2E3122), Color(0xFFB5C28F))
        SettingsEntryTone.MATCHA -> SettingsToneColors(Color(0xFF263226), Color(0xFF9FCB9A))
        SettingsEntryTone.PINK -> SettingsToneColors(Color(0xFF3A242C), Color(0xFFE898B3))
        SettingsEntryTone.GREEN -> SettingsToneColors(Color(0xFF243021), Color(0xFF9EC89B))
        SettingsEntryTone.BROWN -> SettingsToneColors(Color(0xFF332620), Color(0xFFC79A7E))
        SettingsEntryTone.AMBER -> SettingsToneColors(Color(0xFF3A2D1B), Color(0xFFE8B07A))
        SettingsEntryTone.GRAY -> SettingsToneColors(Color(0xFF2E2D28), Color(0xFFB5B3A8))
    } else when (tone) {
        SettingsEntryTone.PURPLE -> SettingsToneColors(Color(0xFFECE9FF), Color(0xFF7C6FD4))
        SettingsEntryTone.ORANGE -> SettingsToneColors(Color(0xFFFBE6D8), Color(0xFFD97C3E))
        SettingsEntryTone.RED -> SettingsToneColors(Color(0xFFFBE0DE), Color(0xFFD65450))
        SettingsEntryTone.OLIVE -> SettingsToneColors(Color(0xFFE8ECD6), Color(0xFF7A8A4F))
        SettingsEntryTone.MATCHA -> SettingsToneColors(Color(0xFFE0EFDA), Color(0xFF5F9A5A))
        SettingsEntryTone.PINK -> SettingsToneColors(Color(0xFFFBE0E6), Color(0xFFD86485))
        SettingsEntryTone.GREEN -> SettingsToneColors(Color(0xFFD9ECD3), Color(0xFF6BA368))
        SettingsEntryTone.BROWN -> SettingsToneColors(Color(0xFFEAD7C9), Color(0xFF9A6B4E))
        SettingsEntryTone.AMBER -> SettingsToneColors(Color(0xFFFBE6D0), Color(0xFFD98A3E))
        SettingsEntryTone.GRAY -> SettingsToneColors(Color(0xFFE4E2D9), Color(0xFF7A786C))
    }
}

// ---------------------------------------------------------------- 柔绘（Soft）

/**
 * 柔绘：薄涂底 = 同色 16%（深色 22%）+ 同色图标。
 *
 * 语义角色 → 柔绘马卡龙档的对应关系（与原 `toSoftTone()` 逐条一致）：
 * PURPLE→LILAC、ORANGE→APRICOT、RED→CLAY、OLIVE→SAGE、MATCHA→FERN、PINK→ROSE、
 * GREEN→SAGE、GRAY→STEEL、AMBER→SAND、BROWN→COCOA。
 * 注：OLIVE 与 GREEN 在柔绘下同为 SAGE（柔绘色库比语义角色少一档），属主题色库容量
 * 而非角色折叠——角色本身仍是两个。
 */
@Composable
private fun softSettingsToneColors(tone: SettingsEntryTone): SettingsToneColors {
    val isDark = LocalIsDarkTheme.current
    val fg = if (isDark) when (tone) {
        SettingsEntryTone.PURPLE -> Color(0xFF9AA3DC) // LILAC
        SettingsEntryTone.ORANGE -> Color(0xFFE0BB96) // APRICOT
        SettingsEntryTone.RED -> Color(0xFFDDA0A0) // CLAY
        SettingsEntryTone.OLIVE -> Color(0xFF97C4A6) // SAGE
        SettingsEntryTone.MATCHA -> Color(0xFFA8C0B8) // FERN
        SettingsEntryTone.PINK -> Color(0xFFDBAAC0) // ROSE
        SettingsEntryTone.GREEN -> Color(0xFF97C4A6) // SAGE
        SettingsEntryTone.GRAY -> Color(0xFFA8B8D6) // STEEL
        SettingsEntryTone.AMBER -> Color(0xFFE0CD9E) // SAND
        SettingsEntryTone.BROWN -> Color(0xFFD6BFA8) // COCOA
    } else when (tone) {
        SettingsEntryTone.PURPLE -> Color(0xFF7C86C9) // LILAC
        SettingsEntryTone.ORANGE -> Color(0xFFD9A97E) // APRICOT
        SettingsEntryTone.RED -> Color(0xFFCC8A8A) // CLAY
        SettingsEntryTone.OLIVE -> Color(0xFF7BAE8C) // SAGE
        SettingsEntryTone.MATCHA -> Color(0xFF8FA8A0) // FERN
        SettingsEntryTone.PINK -> Color(0xFFC98FA8) // ROSE
        SettingsEntryTone.GREEN -> Color(0xFF7BAE8C) // SAGE
        SettingsEntryTone.GRAY -> Color(0xFF8FA0C4) // STEEL
        SettingsEntryTone.AMBER -> Color(0xFFD8C089) // SAND
        SettingsEntryTone.BROWN -> Color(0xFFC4A88F) // COCOA
    }
    return SettingsToneColors(bg = fg.copy(alpha = if (isDark) 0.22f else 0.16f), fg = fg)
}

// ---------------------------------------------------------------- 通透（iOS 26）

/**
 * 通透：实色系统色徽章 + **白色**图标（iOS 设置 App 的标准画法）。
 *
 * 语义角色 → Apple 系统色的对应关系（与原 `toIosTone()` 逐条一致）：
 * PURPLE→systemPurple、ORANGE→systemOrange、RED→systemRed、OLIVE→systemTeal、
 * MATCHA→systemGreen、PINK→systemPink、GREEN→systemGreen、GRAY→systemGray、
 * AMBER→systemYellow、BROWN→systemGray。
 */
@Composable
private fun iosSettingsToneColors(tone: SettingsEntryTone): SettingsToneColors {
    val isDark = LocalIsDarkTheme.current
    val bg = if (isDark) when (tone) {
        SettingsEntryTone.PURPLE -> Color(0xFFBF5AF2) // systemPurple (dark)
        SettingsEntryTone.ORANGE -> Color(0xFFFF9F0A) // systemOrange (dark)
        SettingsEntryTone.RED -> Color(0xFFFF453A) // systemRed (dark)
        SettingsEntryTone.OLIVE -> Color(0xFF64D2FF) // systemTeal (dark)
        SettingsEntryTone.MATCHA -> Color(0xFF30D158) // systemGreen (dark)
        SettingsEntryTone.PINK -> Color(0xFFFF375F) // systemPink (dark)
        SettingsEntryTone.GREEN -> Color(0xFF30D158) // systemGreen (dark)
        SettingsEntryTone.GRAY -> Color(0xFF98989D) // systemGray (dark)
        SettingsEntryTone.AMBER -> Color(0xFFFFD60A) // systemYellow (dark)
        SettingsEntryTone.BROWN -> Color(0xFF98989D) // systemGray (dark)
    } else when (tone) {
        SettingsEntryTone.PURPLE -> Color(0xFFAF52DE) // systemPurple
        SettingsEntryTone.ORANGE -> Color(0xFFFF9500) // systemOrange
        SettingsEntryTone.RED -> Color(0xFFFF3B30) // systemRed
        SettingsEntryTone.OLIVE -> Color(0xFF30B0C7) // systemTeal
        SettingsEntryTone.MATCHA -> Color(0xFF34C759) // systemGreen
        SettingsEntryTone.PINK -> Color(0xFFFF2D55) // systemPink
        SettingsEntryTone.GREEN -> Color(0xFF34C759) // systemGreen
        SettingsEntryTone.GRAY -> Color(0xFF8E8E93) // systemGray
        SettingsEntryTone.AMBER -> Color(0xFFF7C600) // systemYellow（浅底上压暗一档保证白图标可读）
        SettingsEntryTone.BROWN -> Color(0xFF8E8E93) // systemGray
    }
    return SettingsToneColors(bg = bg, fg = Color.White)
}

/**
 * 通透身份行头像渐变：systemBlue → systemPurple。
 *
 * 该色对**不参与** [SettingsEntryTone]（身份行不是列表条目），随主题身份行一起取用。
 */
@Composable
fun iosAvatarGradientColors(): List<Color> = if (LocalIsDarkTheme.current) {
    listOf(Color(0xFF0A84FF), Color(0xFFBF5AF2))
} else {
    listOf(Color(0xFF007AFF), Color(0xFFAF52DE))
}

/**
 * 柔绘身份行头像渐变：LILAC@85% → MAUVE@75% 双色晕染（不用实色渐变，保留薄涂感）。
 */
@Composable
fun softAvatarGradientColors(): List<Color> {
    val isDark = LocalIsDarkTheme.current
    return listOf(
        (if (isDark) Color(0xFF9AA3DC) else Color(0xFF7C86C9)).copy(alpha = 0.85f),
        (if (isDark) Color(0xFFB3ACCF) else Color(0xFF9A93B8)).copy(alpha = 0.75f)
    )
}
