package com.shangkeschedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangkeschedule.data.model.AppThemePreset

/**
 * 全局风格 tokens（v2 风格对齐规范 §2）。
 *
 * 两张基线图（日程 / 我的）提取的设计基准：淡灰白页面底 + 白色大圆角卡片 +
 * 紫渐变头部 + 语义淡底色对（chip）+ 胶囊控件。所有页面统一从这里取值。
 *
 * 结构性收敛通过 [withAppSurfaces] 映射到 MaterialTheme ColorScheme 实现
 * （surface/background=页面底色，surfaceContainer*=卡片白），语义强调色
 * 通过 [appColors] 在组件层显式引用。
 */
data class AppSemanticColors(
    val fg: Color,
    val bg: Color
)

data class AppColorTokens(
    // 页面与容器
    val pageBg: Color,
    val cardBg: Color,
    val cardBgElevated: Color,
    val inputBg: Color,
    val divider: Color,
    // 文本
    val textPrimary: Color,
    val textSecondary: Color,
    val textOnPrimary: Color,
    // 语义色对（淡底 + 深色前景）
    val primary: Color,
    val primarySoft: Color,
    val success: Color,
    val successSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val amber: Color,
    val amberSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val favorite: Color,
    val favoriteSoft: Color,
    // 头部渐变（紫：左上 → 右下）
    val gradientStart: Color,
    val gradientEnd: Color,
    // 底部导航 / 徽标
    val navBarBg: Color,
    val navSelectedBg: Color,
    val badgeBg: Color,
    val badgeFg: Color,
    // Snackbar 深色提示条（Telegram 形态：深底浅字，深浅两套观感一致）
    val snackbarBg: Color,
    val snackbarFg: Color,
    // 阴影基色
    val shadow: Color
) {
    val headerGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(gradientStart, gradientEnd),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset.Infinite
        )

    fun tone(tone: AccentTone): AppSemanticColors = when (tone) {
        AccentTone.PRIMARY -> AppSemanticColors(primary, primarySoft)
        AccentTone.SUCCESS -> AppSemanticColors(success, successSoft)
        AccentTone.INFO -> AppSemanticColors(info, infoSoft)
        AccentTone.WARNING -> AppSemanticColors(warning, warningSoft)
        AccentTone.AMBER -> AppSemanticColors(amber, amberSoft)
        AccentTone.DANGER -> AppSemanticColors(danger, dangerSoft)
        AccentTone.FAVORITE -> AppSemanticColors(favorite, favoriteSoft)
    }
}

enum class AccentTone { PRIMARY, SUCCESS, INFO, WARNING, AMBER, DANGER, FAVORITE }

/**
 * 浅色 tokens：与基线图逐项对齐的固定值。
 */
private fun lightAppColorTokens() = AppColorTokens(
    pageBg = Color(0xFFF2F3F7),
    cardBg = Color(0xFFFFFFFF),
    cardBgElevated = Color(0xFFFFFFFF),
    inputBg = Color(0xFFE9EBF2),
    divider = Color(0xFFECEDF3),
    textPrimary = Color(0xFF191B22),
    textSecondary = Color(0xFF8A8F99),
    textOnPrimary = Color.White,
    primary = Color(0xFF6C5CE7),
    primarySoft = Color(0xFFE8EAF9),
    success = Color(0xFF22A45D),
    successSoft = Color(0xFFE9F8EF),
    info = Color(0xFF4F7CFF),
    infoSoft = Color(0xFFEBF0FF),
    warning = Color(0xFFF08C00),
    warningSoft = Color(0xFFFFF4E5),
    amber = Color(0xFFE8A213),
    amberSoft = Color(0xFFFFF6E6),
    danger = Color(0xFFE5484D),
    dangerSoft = Color(0xFFFDECEC),
    favorite = Color(0xFFF5A623),
    favoriteSoft = Color(0xFFFFF8E1),
    gradientStart = Color(0xFF6C5CE7),
    gradientEnd = Color(0xFF8E7CF3),
    navBarBg = Color(0xFFFFFFFF),
    navSelectedBg = Color(0xFFE8EAF9),
    badgeBg = Color(0xFF9AA0AB),
    badgeFg = Color(0xFFFFFFFF),
    snackbarBg = Color(0xFF23262E),
    snackbarFg = Color(0xFFFFFFFF),
    shadow = Color(0x14101828)
)

/**
 * 深色 tokens：同样的结构与语义，底色反转；淡底统一用前景色低透明度实现。
 */
private fun darkAppColorTokens() = run {
    val primary = Color(0xFF8E7CF3)
    val success = Color(0xFF3DBA75)
    val info = Color(0xFF7295FF)
    val warning = Color(0xFFFFA53D)
    val amber = Color(0xFFF0B43C)
    val danger = Color(0xFFFF6B6E)
    val favorite = Color(0xFFFCBB4A)
    val cardBgElevated = Color(0xFF20242D)
    val textPrimary = Color(0xFFE9EAF0)
    AppColorTokens(
        pageBg = Color(0xFF0F1115),
        cardBg = Color(0xFF191C23),
        cardBgElevated = cardBgElevated,
        inputBg = Color(0xFF262A33),
        divider = Color(0xFF262A32),
        textPrimary = textPrimary,
        textSecondary = Color(0xFF8B909B),
        textOnPrimary = Color.White,
        primary = primary,
        primarySoft = primary.copy(alpha = 0.22f),
        success = success,
        successSoft = success.copy(alpha = 0.22f),
        info = info,
        infoSoft = info.copy(alpha = 0.22f),
        warning = warning,
        warningSoft = warning.copy(alpha = 0.22f),
        amber = amber,
        amberSoft = amber.copy(alpha = 0.22f),
        danger = danger,
        dangerSoft = danger.copy(alpha = 0.22f),
        favorite = favorite,
        favoriteSoft = favorite.copy(alpha = 0.22f),
        gradientStart = Color(0xFF6C5CE7),
        gradientEnd = Color(0xFF8E7CF3),
        navBarBg = Color(0xFF191C23),
        navSelectedBg = primary.copy(alpha = 0.22f),
        badgeBg = Color(0xFF3A3F4A),
        badgeFg = Color(0xFFE9EAF0),
        snackbarBg = cardBgElevated,
        snackbarFg = textPrimary,
        shadow = Color(0x66000000)
    )
}

fun appColorTokens(isDark: Boolean): AppColorTokens =
    if (isDark) darkAppColorTokens() else lightAppColorTokens()

/**
 * 按主题预设取颜色 tokens：通透（iOS）深浅模式均走独立 Apple HIG 色板，
 * Claude（CLAUDE）深浅模式均走 Anthropic/Claude 设计系统色板，
 * 两者严格锁定各自系统色系（不跟随动态取色/自定义主色），
 * 其余预设沿用 v2 基线 token；深色模式下非 iOS / 非 CLAUDE 预设走统一深色 tokens。
 */
fun appColorTokens(isDark: Boolean, preset: AppThemePreset): AppColorTokens = when {
    preset == AppThemePreset.IOS && isDark -> iosDarkAppColorTokens()
    preset == AppThemePreset.IOS -> iosLightAppColorTokens()
    preset == AppThemePreset.CLAUDE && isDark -> claudeDarkAppColorTokens()
    preset == AppThemePreset.CLAUDE -> claudeLightAppColorTokens()
    isDark -> darkAppColorTokens()
    else -> lightAppColorTokens()
}

/**
 * 主色同步后的 token 集合：由 Theme 在组合内提供（primary 跟随用户实际主题）。
 *
 * 默认值 = 通透（iOS 26）浅色 tokens —— 首次组合（预览宿主 / 未注入时）即落在 iOS 26 基线上，
 * 不再回落到已随「经典 / 云舒 / 利落」一并删除的 v2 紫渐变基线。
 */
val LocalAppColorTokens = staticCompositionLocalOf { iosLightAppColorTokens() }

/** 组件层快捷访问（跟随全局深浅色与主题主色同步）。 */
@Composable
fun appColors(): AppColorTokens = LocalAppColorTokens.current

/**
 * 形状 / 间距 / 字阶 tokens 的结构定义见下方 AppShapeTokens / AppSpacingTokens /
 * AppTypeTokens；具体取值由主题下发（通透走 IosStyle.kt，书卷走 ClaudeStyle.kt），
 * 组件层统一用 ppShapes() / ppSpacing() / ppType() 读取，不再直接引用常量对象。
 */

object AppTypeGrid {
    /** 课程块名称基值（× fontScale）。 */
    const val courseName = 13f
    /** 课程块元信息（教师/教室/时间）基值（× fontScale）。 */
    const val courseMeta = 10f
    /** 星期表头。 */
    val dayHeader = 14.sp
    /** 节次序号 / 时间标签。 */
    val timeLabel = 12.sp
    /** 次级时间 / 小标签。 */
    val timeSmall = 10.sp
    /** 24 小时模式微时间。 */
    val timeTiny = 8.sp
    /** 紧凑高度下的星期/时间降级字号。 */
    val timeCompact = 11.sp
}

/**
 * 透明度档位（替代散落的 0.4/0.45/0.618/0.75 等魔法值）。
 * 新代码必须取档位；存量逐步替换。特殊设计值（如黄金比例 0.618f）可豁免但需注释。
 */
object AppAlpha {
    /** 极淡：提示底 / 微分隔。 */
    const val subtle = 0.12f
    /** 淡色：淡色底 / 降级内容。 */
    const val soft = 0.25f
    /** 微弱：装饰光斑等大面积低对比元素。 */
    const val faint = 0.06f
    /** 半透明：降级内容 / 弱化遮罩。 */
    const val dimmed = 0.5f
    /** 次级：次级文字 / 图标。 */
    const val secondary = 0.7f
}

/**
 * 把结构性颜色映射进 Material ColorScheme：
 * surface/background → 页面底色，surfaceContainer* → 卡片底，
 * surfaceVariant → 输入/嵌入底。
 * 强调/选中容器（primary/secondary/tertiaryContainer）统一收敛为「主色淡底」语言：
 * 动态取色在暖色墙纸下会生成棕/土色容器（浅色模式出现深棕色暗调的根因），在此一并压平。
 * 主色 primary 本身保持主题预设/动态取色不变。
 */
fun ColorScheme.withAppSurfaces(tokens: AppColorTokens): ColorScheme = copy(
    background = tokens.pageBg,
    surface = tokens.pageBg,
    surfaceVariant = tokens.inputBg,
    surfaceContainerLowest = tokens.cardBg,
    surfaceContainerLow = tokens.cardBg,
    surfaceContainer = tokens.cardBg,
    surfaceContainerHigh = tokens.cardBgElevated,
    surfaceContainerHighest = tokens.cardBgElevated,
    primaryContainer = tokens.primarySoft,
    onPrimaryContainer = tokens.primary,
    secondaryContainer = tokens.primarySoft,
    onSecondaryContainer = tokens.primary,
    tertiaryContainer = tokens.primarySoft,
    onTertiaryContainer = tokens.primary,
    surfaceDim = tokens.pageBg,
    surfaceBright = tokens.cardBg,
    onSurface = tokens.textPrimary,
    onSurfaceVariant = tokens.textSecondary
)

// ============================================================================
// 主题化形状 / 间距 / 字阶 tokens（v3.30.0 通透主题全站 HIG 重构新增）
//
// 设计原则：
// - 默认值 = 现有 v2 基线（经典/云舒主题完全不变）
// - Apple HIG 值 = 通透主题专属（更大圆角、更多留白、SF 风格字阶）
// - 通过 CompositionLocal 注入，组件层用 appShapes()/appSpacing()/appType() 访问
// ============================================================================

/**
 * 形状 tokens：统一管理所有圆角形状。
 * 默认值与原 AppShape 对象完全一致，保证经典/云舒主题零回归。
 *
 * 使用 RoundedCornerShape 而非 Shape 接口，保证可用于 MaterialTheme Shapes
 * （M3 Shapes 要求 CornerBasedShape 类型）。
 */
data class AppShapeTokens(
    val card: RoundedCornerShape,
    val sheetTop: RoundedCornerShape,
    val chip: RoundedCornerShape,
    val chipSmall: RoundedCornerShape,
    val chipSmallRadius: Dp,
    val menu: RoundedCornerShape,
    val capsule: RoundedCornerShape,
    val fab: RoundedCornerShape,
    val heroCard: RoundedCornerShape,
    val bubble: RoundedCornerShape
)

/**
 * 间距 tokens：统一管理所有尺寸/间距/留白。
 * 默认值与原 AppSpacing 对象完全一致。
 */
data class AppSpacingTokens(
    val pageHorizontal: Dp,
    val cardGap: Dp,
    val listGap: Dp,
    val cardInner: Dp,
    val rowMinHeight: Dp,
    val touchMin: Dp,
    val chipIcon: Dp,
    val fab: Dp,
    val navBarHorizontal: Dp,
    val navBarBottom: Dp
)

/**
 * 字阶 tokens：统一管理所有字号。
 * 默认值与原 AppType 对象完全一致。
 */
data class AppTypeTokens(
    val bigNumber: TextUnit,
    val hero: TextUnit,
    val sectionTitle: TextUnit,
    val timeLabel: TextUnit,
    val badge: TextUnit,
    val pageTitle: TextUnit,
    val rowTitle: TextUnit,
    val body: TextUnit,
    val caption: TextUnit,
    val hint: TextUnit
)

// ============================================================================
// CompositionLocal 与访问器
//
// tokens 的具体取值不再放在本文件：通透（iOS 26）在 IosStyle.kt、书卷（CLAUDE）在
// ClaudeStyle.kt，两者各锁一套系统色/圆角/字阶。本文件只保留结构定义（上面的三个
// data class）与注入通道，避免「同名两套值」的分叉隐患。
// ============================================================================

/** CompositionLocal：形状 tokens（默认 = 通透 / iOS 26 连续圆角阶梯）。 */
val LocalAppShapeTokens = staticCompositionLocalOf { iosShapeTokens }

/** CompositionLocal：间距 tokens（默认 = 通透 / iOS 26 8pt 网格）。 */
val LocalAppSpacingTokens = staticCompositionLocalOf { iosSpacingTokens }

/** CompositionLocal：字阶 tokens（默认 = 通透 / iOS 26 SF 字阶）。 */
val LocalAppTypeTokens = staticCompositionLocalOf { iosTypeTokens }

/** 组件层快捷访问形状 tokens。 */
@Composable
fun appShapes(): AppShapeTokens = LocalAppShapeTokens.current

/** 组件层快捷访问间距 tokens。 */
@Composable
fun appSpacing(): AppSpacingTokens = LocalAppSpacingTokens.current

/** 组件层快捷访问字阶 tokens。 */
@Composable
fun appType(): AppTypeTokens = LocalAppTypeTokens.current

/** 按主题预设取形状 tokens：通透走 iOS 26 连续圆角，书卷走设计系统圆角阶梯。 */
fun appShapeTokens(preset: AppThemePreset): AppShapeTokens = when (preset) {
    AppThemePreset.CLAUDE -> claudeShapeTokens
    else -> iosShapeTokens
}

/** 按主题预设取间距 tokens：通透走 iOS 26 8pt 网格，书卷走设计系统留白。 */
fun appSpacingTokens(preset: AppThemePreset): AppSpacingTokens = when (preset) {
    AppThemePreset.CLAUDE -> claudeSpacingTokens
    else -> iosSpacingTokens
}

/** 按主题预设取字阶 tokens：通透走 iOS 26 SF 字阶，书卷走设计系统字阶。 */
fun appTypeTokens(preset: AppThemePreset): AppTypeTokens = when (preset) {
    AppThemePreset.CLAUDE -> claudeTypeTokens
    else -> iosTypeTokens
}