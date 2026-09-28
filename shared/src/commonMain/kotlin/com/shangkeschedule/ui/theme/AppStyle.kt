package com.shangkeschedule.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
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

/**
 * Material 3 基线描边色 —— 与 [AppColorTokens.outline] / [AppColorTokens.outlineVariant]
 * 的 data class 默认值**逐位相同**。
 *
 * 存在理由：C3「token 显式覆盖」要求每套主题都写出 outline / outlineVariant，不得依赖
 * data class 默认值（否则调整默认值会静默影响所有未声明的主题）。沿用 Material 基线的
 * 主题（书卷 / 通透）引用这两个常量显式声明，取值与不传时完全一致 —— 视觉零变化。
 */
internal val MaterialBaselineOutline: Color = Color(0xFF79747E)
internal val MaterialBaselineOutlineVariant: Color = Color(0xFFCAC4D0)

data class AppColorTokens(
    // 页面与容器
    val pageBg: Color,
    val cardBg: Color,
    val cardBgElevated: Color,
    val inputBg: Color,
    val divider: Color,
    /**
     * 极淡分隔线（hairline 级）：分组内分隔、色板点描边等「需要极弱存在感」的场合。
     *
     * 取代此前散落的 `Color(0x14FFFFFF)` / `Color(0x0F000000)` 压黑压白硬编码。
     * 与 [divider] 的分工：`divider` 是**结构性**分隔（页面级、列表行间），
     * `dividerSoft` 是**装饰性**弱化线，浓度约为前者的一半。
     */
    val dividerSoft: Color,
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
    val shadow: Color,
    /**
     * Material `outline` 角色：只被 M3 自带描边组件读取
     * （OutlinedButton / OutlinedTextField / Switch 未选中边框 / 默认 Divider）。
     *
     * 默认值刻意保持 Material 3 基线 `#79747E` —— 书卷、通透两套既有主题
     * **不传该字段即等于原行为，视觉零变化**；柔绘显式传低对比淡边，
     * 以消除全站唯一残留的 1dp 硬灰描边（柔绘「无锐利硬边缘」）。
     */
    val outline: Color = Color(0xFF79747E),
    /**
     * Material `outlineVariant` 角色：默认 Divider / 未聚焦 Outlined 组件的浅色描边。
     *
     * 默认值刻意保持 Material 3 基线 `#CAC4D0` —— 书卷、通透不传该字段即等于
     * 原行为，视觉零变化（此前用 `outline × 50% alpha` 推导会得到 `#8079747E`，
     * 与基线不一致，会让两套既有主题的默认分隔线变深，已修正）。
     */
    val outlineVariant: Color = Color(0xFFCAC4D0)
) {
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
    dividerSoft = Color(0x0F000000),
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
        dividerSoft = Color(0x14FFFFFF),
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
 * 柔绘（SOFT）深浅模式均走低饱和柔绘色板，
 * 三者严格锁定各自色系（不跟随动态取色/自定义主色），
 * 其余预设沿用 v2 基线 token；深色模式下非 iOS / 非 CLAUDE / 非 SOFT 预设走统一深色 tokens。
 */
fun appColorTokens(isDark: Boolean, preset: AppThemePreset): AppColorTokens = when {
    preset == AppThemePreset.IOS && isDark -> iosDarkAppColorTokens()
    preset == AppThemePreset.IOS -> iosLightAppColorTokens()
    preset == AppThemePreset.CLAUDE && isDark -> claudeDarkAppColorTokens()
    preset == AppThemePreset.CLAUDE -> claudeLightAppColorTokens()
    preset == AppThemePreset.SOFT && isDark -> softDarkAppColorTokens()
    preset == AppThemePreset.SOFT -> softLightAppColorTokens()
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
    /** 紧凑高度下的星期/时间降级字号。 */
    val timeCompact = 11.sp
}

/**
 * 透明度档位（替代散落的 0.4/0.45/0.618/0.75 等魔法值）。
 * 新代码取档位；特殊设计值（如黄金比例 0.618f）可豁免但需注释。
 *
 * 瘦身（2026-09-23）：`soft` / `faint` / `secondary` 三档长期零引用——`faint` 的唯一使用者
 * 随 `GradientHeroCard` 一并删除，另两档自加入起就无人使用——已移除。需要时按新语义重新加入，
 * 不要照搬旧值。
 */
object AppAlpha {
    /** 半透明：降级内容 / 弱化遮罩。 */
    const val dimmed = 0.5f

    /**
     * 发丝级：分隔线、色板点描边等「要看见但几乎不存在」的场合。
     * 与 [AppColorTokens.dividerSoft] 的分工：本值是**运行时对已有色再降浓度**的系数，
     * 用于调用方手头只有 `divider` 的场合；有 token 时优先用 token。
     */
    const val hairline = 0.12f

    /** 最低可见：羽化描边环、极淡薄涂底。低于此值在多数屏幕上不可辨识。 */
    const val subtle = 0.06f
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
    onSurfaceVariant = tokens.textSecondary,
    // M3 自带描边组件（OutlinedButton / OutlinedTextField / Switch 边框 / 默认 Divider）
    // 统一走主题 outline / outlineVariant 角色：书卷/通透不传字段 = Material 基线原值，
    // 视觉零变化；柔绘传低对比淡边，去掉全站残留的 1dp 硬灰描边。
    outline = tokens.outline,
    outlineVariant = tokens.outlineVariant
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
    /**
     * 设置列表行最小高度 —— **独立于 [rowMinHeight]**。
     *
     * 由 A1/P2 token 补齐引入（规范 R2：每个 token 必须由每套主题显式赋值）。
     * 原先 `SettingsScreen` 用 `if (isClaudePreset) 48.dp else appSpacing().rowMinHeight` 表达，
     * 而 `rowMinHeight` 描述的是**普通列表行**（书卷 56dp），设置行是另一个角色（书卷 48dp）
     * —— `SoftStyle.kt` 的注释里把 48dp 记为"书卷设置行的历史硬编码"。补 token 后分支消失。
     */
    val settingsRowMinHeight: Dp,
    val touchMin: Dp,
    val chipIcon: Dp,
    val fab: Dp,
    val navBarHorizontal: Dp,
    val navBarBottom: Dp,
    // ---- 留白节奏（全局 UI 优化批 1 新增）----
    /**
     * 页面内容区顶部留白（状态栏之下、页头之上）。
     * 取代此前各页自写的 `statusBarsPadding() + padding(vertical = 12.dp)` 一类魔法值。
     */
    val pageTop: Dp,
    /** 分区（section）之间的纵向间距 —— 取代散落的 `listGap` 误用与裸 24/20/26dp。 */
    val sectionGap: Dp,
    /** 分区标题与其下方第一张卡片之间的间距。 */
    val sectionTitleGap: Dp,
    /**
     * 列表内容底部的额外安全留白（底栏之上）。
     * 取代 `TodayScheduleScreen` 的 `bottomInset + 100.dp` 魔法值。
     */
    val contentBottom: Dp
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
    /**
     * 设置项行标题字号与字重 —— **独立于 [rowTitle]**。
     *
     * 由 A1/P2 token 补齐引入（规范 R2）。原 `SettingsScreen` 用
     * `if (isClaudePreset) 15.sp else appType().rowTitle` + 同款字重分支表达；
     * `rowTitle` 是**页面行标题**（书卷 18sp），设置行是另一个角色（书卷 15sp / Medium）。
     * 字重也随主题不同（书卷 Medium，通透/柔绘 SemiBold），故一并成为 token。
     */
    val settingsRowTitle: TextUnit,
    val settingsRowTitleWeight: FontWeight,
    val body: TextUnit,
    val caption: TextUnit,
    val hint: TextUnit,
    // ---- 字重语义（全局 UI 优化批 1 新增）----
    /**
     * 标题字重。三主题各自不同（书卷衬线 SemiBold / 柔绘 Medium / 通透 SemiBold），
     * 此前散落为组件层的 `FontWeight.SemiBold` / `Bold` 硬编码，现按语义收口为 token。
     */
    val titleWeight: FontWeight,
    /** 正文字重（常规 Normal）。 */
    val bodyWeight: FontWeight,
    /** 辅助文字字重（比正文略重一档，保证小字号下的可读性）。 */
    val captionWeight: FontWeight
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

/** 按主题预设取形状 tokens：通透走 iOS 26 连续圆角，柔绘走虚化大圆角，书卷走设计系统圆角阶梯。 */
fun appShapeTokens(preset: AppThemePreset): AppShapeTokens = when (preset) {
    AppThemePreset.CLAUDE -> claudeShapeTokens
    AppThemePreset.SOFT -> softShapeTokens
    else -> iosShapeTokens
}

/** 按主题预设取间距 tokens：通透走 iOS 26 8pt 网格，柔绘走干净留白，书卷走设计系统留白。 */
fun appSpacingTokens(preset: AppThemePreset): AppSpacingTokens = when (preset) {
    AppThemePreset.CLAUDE -> claudeSpacingTokens
    AppThemePreset.SOFT -> softSpacingTokens
    else -> iosSpacingTokens
}

/** 按主题预设取字阶 tokens：通透走 iOS 26 SF 字阶，柔绘走轻字重柔绘字阶，书卷走设计系统字阶。 */
fun appTypeTokens(preset: AppThemePreset): AppTypeTokens = when (preset) {
    AppThemePreset.CLAUDE -> claudeTypeTokens
    AppThemePreset.SOFT -> softTypeTokens
    else -> iosTypeTokens
}

// ============================================================================
// 全局 UI 优化批 1（v3.71.0）：图标尺寸与页头材质两组新 token
//
// 两组都是「参数型差异」——按 [判定口诀] 走 V2 范式：三套 Style 显式赋值、
// 组件只读 token，不得在组件层判断主题身份。
// ============================================================================

/**
 * 图标尺寸 tokens：全站图标渲染尺寸收敛为三档。
 *
 * 背景：此前图标尺寸散落为 13 / 18 / 22 / 24dp 等多种非标值（同一语义的图标
 * 在不同页面大小不同），且未按网格取值。现统一为 small 16 / medium 20 / large 24，
 * 与 `touchMin = 48dp` 的最小触控区解耦（尺寸归尺寸，命中区归命中区）。
 */
data class AppIconTokens(
    /** 行内前置小图标、元信息图标。 */
    val small: Dp,
    /** 列表行主图标、顶栏操作图标。 */
    val medium: Dp,
    /** 页面级强调图标、空状态插图图标。 */
    val large: Dp
)

/** CompositionLocal：图标尺寸 tokens（默认 = 通透 / iOS 26）。 */
val LocalAppIconTokens = staticCompositionLocalOf { iosIconTokens }

/** 组件层快捷访问图标尺寸 tokens。 */
@Composable
fun appIconSize(): AppIconTokens = LocalAppIconTokens.current

/** 按主题预设取图标尺寸 tokens。 */
fun appIconTokens(preset: AppThemePreset): AppIconTokens = when (preset) {
    AppThemePreset.CLAUDE -> claudeIconTokens
    AppThemePreset.SOFT -> softIconTokens
    else -> iosIconTokens
}

/**
 * 页面页头 tokens（[AppPageHeader]）：四个主页面统一页头后的参数差异。
 *
 * 背景（全局 UI 优化批 2）：四个主页面此前页头形态各不相同 —— 今日页无标题且靠
 * `padding(horizontal = 84.dp)` 硬编码避让、课表页顶栏塞 4 个控件、日程页用
 * `hero`(34sp) 当月份标题、我的页仅通透有吸顶标题（书卷/柔绘无标题）。
 * 统一为同一组件后，差异退化为参数，由本 token 组承载。
 *
 * ⚠️ [titleSize] 三主题取值不同是**既定主题身份**（书卷衬线 28sp / 柔绘 21sp /
 * 通透 22sp），不做跨主题统一；统一的是**台阶语义**（都用 `pageTitle` 角色）。
 */
data class AppPageHeaderTokens(
    val titleSize: TextUnit,
    val titleWeight: FontWeight,
    val titleLetterSpacing: TextUnit,
    val subtitleSize: TextUnit,
    /** 页头与其下方内容区的间距。 */
    val bottomGap: Dp
)

/** CompositionLocal：页头 tokens（默认 = 通透 / iOS 26）。 */
val LocalAppPageHeaderTokens = staticCompositionLocalOf { iosPageHeaderTokens }

/** 组件层快捷访问页头 tokens。 */
@Composable
fun appPageHeader(): AppPageHeaderTokens = LocalAppPageHeaderTokens.current

/** 按主题预设取页头 tokens。 */
fun appPageHeaderTokens(preset: AppThemePreset): AppPageHeaderTokens = when (preset) {
    AppThemePreset.CLAUDE -> claudePageHeaderTokens
    AppThemePreset.SOFT -> softPageHeaderTokens
    else -> iosPageHeaderTokens
}

/**
 * 顶栏材质 tokens：玻璃顶栏（`AppNavigationBar`）在柔绘下的四处参数差异 ——
 * 模糊半径、玻璃色调 alpha、标题字重、标题字距。
 *
 * A1 / V2（v3.69.0）：原先由 `AppTopAppBar.kt` 用 `LocalIsSoftTheme.current` 就地
 * `if (isSoft) 22.dp else 20.dp` 表达，属「组件层按主题身份分支」（清单 V2 判定口诀）。
 * 这四项描述的是**顶栏玻璃材质**这一个角色，故独立成组，与 [AppSpacingTokens] 中
 * `settingsRowMinHeight` 的补齐方式同构。
 *
 * ⚠️ 与用户可调的 `LocalGlassBlurRadius` 不是一回事：后者是用户在「个性化显示」里设的
 * 全局雾度，顶栏用的是固定值，不可混用。
 *
 * ⚠️ 顶栏**下缘**原为「柔绘羽化渐变 vs 其余发丝线」的结构分支，一并收口为
 * [NavBarBottomEdge] 枚举 —— 组件按角色渲染两种形态，不再判断主题身份。
 */
data class AppNavBarTokens(
    val glassBlurRadius: Dp,
    val glassTintAlpha: Float,
    val titleFontWeight: FontWeight,
    val titleLetterSpacing: TextUnit,
    val bottomEdge: NavBarBottomEdge
)

/**
 * 顶栏下缘形态：柔绘为羽化渐变（无硬线），其余为 iOS `.hairline` 发丝线。
 *
 * 这两者是**两种不同的组件结构**，不是参数差异，故用枚举表达「角色」而非数值。
 */
enum class NavBarBottomEdge {
    /** 羽化渐变：由极淡到透明，10dp 高。 */
    FEATHERED,

    /** 发丝线：0.5dp 高 + 分隔线色。 */
    HAIRLINE
}

/** CompositionLocal：顶栏材质 tokens（默认 = 通透 / iOS 26 玻璃顶栏）。 */
val LocalAppNavBarTokens = staticCompositionLocalOf { iosNavBarTokens }

/** 组件层快捷访问顶栏材质 tokens。 */
@Composable
fun appNavBar(): AppNavBarTokens = LocalAppNavBarTokens.current

/** 按主题预设取顶栏材质 tokens：柔绘走更虚化的玻璃 + 轻一档字重，书卷与通透同值（书卷不用玻璃顶栏）。 */
fun appNavBarTokens(preset: AppThemePreset): AppNavBarTokens = when (preset) {
    AppThemePreset.SOFT -> softNavBarTokens
    AppThemePreset.CLAUDE -> claudeNavBarTokens
    else -> iosNavBarTokens
}

/** CompositionLocal：当前是否柔绘主题（供材质层做形态判定）。 */
val LocalIsSoftTheme = staticCompositionLocalOf { false }

// ============================================================================
// A1 / V2 第二批（v3.69.0）：组件层「参数型」主题分支收口
//
// 与 [AppNavBarTokens] 同构：把「同一组件在不同主题下取不同参数」的分支，
// 提升为按角色命名的 token 组，组件只读 token、不再判断主题身份。
//
// 三条边界（照抄清单判定口诀，避免误收口）：
//   · 结构型差异（两种不同组件）→ 用枚举表达角色，不用数值 token；
//   · 组件族并存（SettingsScreen 的三套 UserRow）→ 属 V3 去重，补 token 无效；
//   · 用户可调项（LocalGlassBlurRadius / fontScale 等）→ 不是主题 token，不得混入。
// ============================================================================

/**
 * 分区标题材质 tokens（[AppSectionHeader]）：书卷 / 柔绘 / 通透三套字重与字距。
 *
 * 原先由 `AppBasicComponents.kt` 就地 `if (isClaude) … else if (isSoft) … else …`
 * 表达「同一行文本在三主题下取不同字重与字距」，属参数型分支。
 *
 * ⚠️ 字号三套**都是 13sp**，看起来"没有差异"——但字重与字距确实分歧
 * （柔绘 Medium 松字距 / 书卷与通透 SemiBold 紧字距，且紧的程度不同），
 * 故字号一并纳入 token，保证「每套主题显式赋值」的规范 R2 成立。
 */
data class AppSectionHeaderTokens(
    val textSize: TextUnit,
    val textWeight: FontWeight,
    val letterSpacing: TextUnit
)

/** CompositionLocal：分区标题材质 tokens（默认 = 通透 / iOS 26）。 */
val LocalAppSectionHeaderTokens = staticCompositionLocalOf { iosSectionHeaderTokens }

/** 组件层快捷访问分区标题材质 tokens。 */
@Composable
fun appSectionHeader(): AppSectionHeaderTokens = LocalAppSectionHeaderTokens.current

/** 按主题预设取分区标题材质 tokens。 */
fun appSectionHeaderTokens(preset: AppThemePreset): AppSectionHeaderTokens = when (preset) {
    AppThemePreset.SOFT -> softSectionHeaderTokens
    AppThemePreset.CLAUDE -> claudeSectionHeaderTokens
    else -> iosSectionHeaderTokens
}

/**
 * 底部毛玻璃面板 tokens（[AppGlassBottomSheet]）：遮罩浓度、背板模糊、噪点、涂色 alpha。
 *
 * 原先由 `AppBasicComponents.kt` 用 `LocalIsSoftTheme.current` 表达四处参数差异
 * （柔绘：遮罩更浅 26% / 模糊更弱 14dp / 噪点更低 0.06 / 涂色更实 0.90），
 * 其余主题走 M3 默认遮罩 + 20dp / 0.12 / 0.86。
 *
 * [scrimAlpha] 为 null 表示"交给 M3 默认值"——书卷与通透沿用
 * `BottomSheetDefaults.ScrimColor`，不用数值强行统一（口径不同，不得折叠）。
 */
data class AppBottomSheetTokens(
    val scrimAlpha: Float?,
    val blurRadius: Dp,
    val noiseFactor: Float,
    val tintAlpha: Float
)

/** CompositionLocal：底部面板材质 tokens（默认 = 通透 / iOS 26）。 */
val LocalAppBottomSheetTokens = staticCompositionLocalOf { iosBottomSheetTokens }

/** 组件层快捷访问底部面板材质 tokens。 */
@Composable
fun appBottomSheet(): AppBottomSheetTokens = LocalAppBottomSheetTokens.current

/** 按主题预设取底部面板材质 tokens。 */
fun appBottomSheetTokens(preset: AppThemePreset): AppBottomSheetTokens = when (preset) {
    AppThemePreset.SOFT -> softBottomSheetTokens
    AppThemePreset.CLAUDE -> claudeBottomSheetTokens
    else -> iosBottomSheetTokens
}

/**
 * 开关 tokens（[AppSwitch]]）：轨道 / 图标的选中与未选中色、外层圆角形态。
 *
 * 柔绘把 success 与 inputBg 各压一档（[trackAlpha] 0.72 / [uncheckedAlpha] 0.78），
 * 并改用羽化描边环替代实色边；其余主题压色系数为 1（即原色）、无外层装饰。
 *
 * 之所以存"压色系数"而不是直接存四个 Color：底座色本身随深浅色切换
 * （`iosLightAppColorTokens()` / `iosDarkAppColorTokens()`），若把 Color 固化进
 * 本 token 就会丢掉深浅联动。系数 × `appColors()` 的运行时取值，深浅自动跟随。
 */
data class AppSwitchTokens(
    val trackAlpha: Float,
    val uncheckedAlpha: Float,
    val featherRim: Boolean
)

/** CompositionLocal：开关 tokens（默认 = 通透 / iOS 26）。 */
val LocalAppSwitchTokens = staticCompositionLocalOf { iosSwitchTokens }

/** 组件层快捷访问开关 tokens。 */
@Composable
fun appSwitch(): AppSwitchTokens = LocalAppSwitchTokens.current

/** 按主题预设取开关 tokens。 */
fun appSwitchTokens(preset: AppThemePreset): AppSwitchTokens = when (preset) {
    AppThemePreset.SOFT -> softSwitchTokens
    AppThemePreset.CLAUDE -> claudeSwitchTokens
    else -> iosSwitchTokens
}

/**
 * 分段控件 tokens（[AppSegmentedControl]）：容器底 + 选中胶囊材质形态。
 *
 * [containerAlpha] 为容器的 `inputBg` 涂色 alpha（柔绘 0.78，其余 1.0）；
 * [pillStyle] 用枚举表达「软模糊投影 + 羽化环」与「轻投影」两种**结构不同的胶囊**
 * ——这是两种组件结构，不是数值差异。
 */
data class AppSegmentedTokens(
    val containerAlpha: Float,
    val pillStyle: SegmentedPillStyle
)

/** 分段控件选中胶囊的材质形态。 */
enum class SegmentedPillStyle {
    /** 柔绘：软模糊投影 + 羽化描边环（无硬边 elevation 投影）。 */
    SOFT_FEATHER,

    /** 通透 / 书卷：轻投影 + 圆形裁剪 + 卡底色。 */
    ELEVATED
}

/** CompositionLocal：分段控件 tokens（默认 = 通透 / iOS 26）。 */
val LocalAppSegmentedTokens = staticCompositionLocalOf { iosSegmentedTokens }

/** 组件层快捷访问分段控件 tokens。 */
@Composable
fun appSegmented(): AppSegmentedTokens = LocalAppSegmentedTokens.current

/** 按主题预设取分段控件 tokens。 */
fun appSegmentedTokens(preset: AppThemePreset): AppSegmentedTokens = when (preset) {
    AppThemePreset.SOFT -> softSegmentedTokens
    AppThemePreset.CLAUDE -> claudeSegmentedTokens
    else -> iosSegmentedTokens
}

/**
 * 课表网格高亮 tokens：今天列 / 当前节次的淡底浓度、圆角块形态、内侧竖条开关。
 *
 * 收口 `ScheduleGridComponents.kt` 的 5 处 `isSoft` 分支。柔绘：淡底压到
 * 0.28 / 0.26，并把直角通栏换成虚化圆角薄涂块 + 内侧渐变竖条（不依赖描边的定位锚点）。
 *
 * [useRoundedHighlight] / [useInnerBar] 均为结构开关：柔绘下"今天列"是通栏色块
 * （不加圆角，避免列缝留竖向亮带），而"当前节次"才换圆角块——两者取值不同，
 * 故分开成两个字段而不是共用一个布尔。
 */
data class AppScheduleHighlightTokens(
    val todayColumnAlpha: Float,
    val activeSectionAlpha: Float,
    val useRoundedHighlight: Boolean,
    val useInnerBar: Boolean,
    val featherRim: Boolean
)

/** CompositionLocal：课表网格高亮 tokens（默认 = 通透 / iOS 26）。 */
val LocalAppScheduleHighlightTokens = staticCompositionLocalOf { iosScheduleHighlightTokens }

/** 组件层快捷访问课表网格高亮 tokens。 */
@Composable
fun appScheduleHighlight(): AppScheduleHighlightTokens = LocalAppScheduleHighlightTokens.current

/** 按主题预设取课表网格高亮 tokens。 */
fun appScheduleHighlightTokens(preset: AppThemePreset): AppScheduleHighlightTokens = when (preset) {
    AppThemePreset.SOFT -> softScheduleHighlightTokens
    AppThemePreset.CLAUDE -> claudeScheduleHighlightTokens
    else -> iosScheduleHighlightTokens
}

/**
 * 悬浮胶囊 tokens（[FloatingCourseBar] 等页内悬浮件）：**结构型**角色。
 *
 * 三种悬浮件不是"同一组件的不同参数"，而是**三种不同的表面语言**：
 *   · 书卷：不透明暖米分组底 + 0.5dp 实色描边（纸面语言，不用玻璃、不用模糊）；
 *   · 柔绘：卡底薄涂 + 软模糊投影 + 漫射柔光 + 羽化描边（无实色边、无锐利硬边）；
 *   · 通透：本体透明，玻璃底由下垫 `LiquidGlass` 层承担。
 *
 * 故用枚举表达角色而非数值 token（清单判定口诀：结构差异用枚举）——
 * 组件改为 `when (floating.surface)` 分派三种绘制，不再判断主题身份。
 *
 * [contentFallbackToSemantic]：书卷与柔绘的文字色回落到语义色（页面内卡片件口径），
 * 只有通透沿用调用方传入的 contentColor（玻璃件口径）。这是**色彩来源口径**的差异，
 * 不是颜色值差异，故保留为布尔角色标记。
 */
data class AppFloatingTokens(
    val surface: FloatingSurfaceStyle,
    val contentFallbackToSemantic: Boolean
)

/** 悬浮件表面语言（三种不同结构）。 */
enum class FloatingSurfaceStyle {
    /** 书卷：不透明分组底 + 实色描边。 */
    OPAQUE_GROUPED,

    /** 柔绘：薄涂卡底 + 软模糊投影 + 柔光 + 羽化环。 */
    SOFT_FEATHER,

    /** 通透：本体透明，玻璃底由下垫 LiquidGlass 承担。 */
    GLASS_UNDERLAY
}

/** CompositionLocal：悬浮胶囊 tokens（默认 = 通透 / iOS 26）。 */
val LocalAppFloatingTokens = staticCompositionLocalOf { iosFloatingTokens }

/** 组件层快捷访问悬浮胶囊 tokens。 */
@Composable
fun appFloating(): AppFloatingTokens = LocalAppFloatingTokens.current

/**
 * 分组卡 tokens（[SemesterCard] 一类「大卡面 + 可选高亮描边」容器）：**结构型**角色。
 *
 * 收口 `ManageCourseTablesScreen.kt` 的 5 处 isClaude / isSoft 分支
 * （形状、底色、描边色、表面材质、投影强度）。三种卡片是**三种表面语言**，
 * 故用枚举表达角色，数值只保留「同一语言内的参数」：
 *
 * | 角色 | 形状 | 底色 | 表面 | 常规描边 | 投影 |
 * | --- | --- | --- | --- | --- | --- |
 * | SOFT_TEXTURE（柔绘） | appShapes().card（24dp 虚化） | cardBg | softSurface（可选手绘肌理） | 无（高亮由薄涂底表达） | 调用方给 |
 * | PAPER_GROUPED（书卷） | 14dp 收口 | 暖米分组底 | 极轻投影 + 裁切 + 底色 | 0.5dp 实色 | shadowElevation |
 * | GLASS_CARD（通透） | appShapes().card（16dp） | 白卡 | 轻投影 + 裁切 + 底色 | iosGlassRim 玻璃高光 | shadowElevation |
 *
 * ⚠️ 柔绘档的软投影强度**不进 token**：它随卡片大小由调用方给
 * （学期大卡 10dp、整宽按钮 6dp），是卡片自身属性而非跨主题差异。
 */
data class AppGroupCardTokens(
    val style: GroupCardStyle,
    val shadowElevation: Dp,
    /** 高亮态描边宽度（柔绘为 0 —— 它用薄涂底而非描边表达高亮）。 */
    val highlightBorderWidth: Dp
)

/** 分组卡表面语言（三种不同结构）。 */
enum class GroupCardStyle {
    /** 柔绘：薄涂卡 + 软模糊投影 + 漫射柔光 + 羽化描边 + 手绘肌理，无实色描边。 */
    SOFT_TEXTURE,

    /** 书卷：暖米分组底 + 14dp 收口圆角 + 0.5dp 实色描边（纸面语言）。 */
    PAPER_GROUPED,

    /** 通透：白卡 + 玻璃高光内描边（iOS 26 inset grouped）。 */
    GLASS_CARD
}

/** CompositionLocal：分组卡 tokens（默认 = 通透 / iOS 26）。 */
val LocalAppGroupCardTokens = staticCompositionLocalOf { iosGroupCardTokens }

/** 组件层快捷访问分组卡 tokens。 */
@Composable
fun appGroupCard(): AppGroupCardTokens = LocalAppGroupCardTokens.current

/** 按主题预设取分组卡 tokens。 */
fun appGroupCardTokens(preset: AppThemePreset): AppGroupCardTokens = when (preset) {
    AppThemePreset.SOFT -> softGroupCardTokens
    AppThemePreset.CLAUDE -> claudeGroupCardTokens
    else -> iosGroupCardTokens
}

/**
 * 周次切换过渡 tokens（[WeeklyScheduleScreen] 切周扫光）：**结构型**角色。
 *
 * 柔绘与其余主题是**两种不同的过渡**，不是同一过渡的不同参数：
 *   · BREATHE（柔绘）：整屏换气 —— sin 曲线一次明度起伏，无方向、无边界；
 *     斜向扫光是镜面/玻璃语言（明确方向 + 明确边界 + 瞬时高亮），落在雾面薄涂底上
 *     会切出一条可见"锋面"，与柔绘「化开」的材质定义直接对立。
 *   · DIRECTIONAL（通透 / 书卷）：Apple HIG 风格收窄光带横扫。
 *
 * [durationScale] 柔绘为 2（换气仍比扫光长一档，但跟随「动效速度」而非写死 600ms）；
 * [linearEasing] 柔绘必须线性 —— sin(π·f) 本身已是单峰曲线，外面再套 ease-in-out
 * 会把亮度峰值压进中段极窄区间，观感变成"停顿—突亮—停顿"三段。
 */
data class AppWeekPagerTokens(
    val sheen: WeekPagerSheen,
    val durationScale: Float,
    val linearEasing: Boolean
)

/** 周次切换的过渡形态（两种不同结构）。 */
enum class WeekPagerSheen {
    /** 整屏换气：无方向、无边界的明度起伏。 */
    BREATHE,

    /** 斜向扫光：收窄光带自左向右横扫。 */
    DIRECTIONAL
}

/** CompositionLocal：周次切换过渡 tokens（默认 = 通透 / iOS 26）。 */
val LocalAppWeekPagerTokens = staticCompositionLocalOf { iosWeekPagerTokens }

/** 组件层快捷访问周次切换过渡 tokens。 */
@Composable
fun appWeekPager(): AppWeekPagerTokens = LocalAppWeekPagerTokens.current

/** 按主题预设取周次切换过渡 tokens。 */
fun appWeekPagerTokens(preset: AppThemePreset): AppWeekPagerTokens = when (preset) {
    AppThemePreset.SOFT -> softWeekPagerTokens
    AppThemePreset.CLAUDE -> claudeWeekPagerTokens
    else -> iosWeekPagerTokens
}

/**
 * 课程块装饰 tokens（网格课程块 [CourseBlock] 与列表课程块）：**结构 + 参数**混合角色。
 *
 * 收口 `CourseBlock.kt` / `WeeklyScheduleScreen.kt` 的 5 处身份分支。
 * 投影语言、描边策略、取色口径是**三种结构差异**，用枚举；
 * 分隔线粗细与浓度是同一语言内的参数，用数值。
 *
 * ⚠️ 软投影强度**不进 token**：它随块大小由调用方给（网格 6dp、列表 8dp）。
 */
data class AppCourseBlockTokens(
    val shadow: CourseBlockShadow,
    val border: CourseBlockBorder,
    val palette: CourseBlockPalette,
    /** 色条口径下的时间文字起始内缩（色条占用左侧空间）。 */
    val stripStartPadding: Dp,
    /** 列表块：是否叠羽化描边环（柔绘）。 */
    val featherRim: Boolean,
    val metaDividerThickness: Dp,
    val metaDividerAlpha: Float
)

/** 课程块投影语言（两种不同结构）。 */
enum class CourseBlockShadow {
    /** 柔绘：软模糊投影，替代 elevation 硬边投影。 */
    SOFT_BLUR,

    /** 通透 / 书卷：不加投影，靠材质与留白分层。 */
    NONE
}

/**
 * 课程块描边策略：柔绘**完全不画** 1dp/2dp 实色描边（含用户在课表样式里选的实线/虚线）——
 * 「无锐利硬边缘」是柔绘的硬约束，层级改由软模糊投影 + 羽化描边表达。
 */
enum class CourseBlockBorder {
    /** 允许实色 / 虚线描边（通透 / 书卷）。 */
    SOLID_ALLOWED,

    /** 禁止任何实色硬边描边（柔绘）。 */
    NONE
}

/**
 * 课程块取色口径：通透（色条样式）与柔绘共用「淡底 + 深色条」；书卷走实色块。
 */
enum class CourseBlockPalette {
    /** 淡底 + 左侧深色条（通透 / 柔绘）。 */
    STRIP,

    /** 整块实色（书卷）。 */
    SOLID
}

/** CompositionLocal：课程块装饰 tokens（默认 = 通透 / iOS 26）。 */
val LocalAppCourseBlockTokens = staticCompositionLocalOf { iosCourseBlockTokens }

/** 组件层快捷访问课程块装饰 tokens。 */
@Composable
fun appCourseBlock(): AppCourseBlockTokens = LocalAppCourseBlockTokens.current

/** 按主题预设取课程块装饰 tokens。 */
fun appCourseBlockTokens(preset: AppThemePreset): AppCourseBlockTokens = when (preset) {
    AppThemePreset.SOFT -> softCourseBlockTokens
    AppThemePreset.CLAUDE -> claudeCourseBlockTokens
    else -> iosCourseBlockTokens
}

/** 按主题预设取悬浮胶囊 tokens。 */
fun appFloatingTokens(preset: AppThemePreset): AppFloatingTokens = when (preset) {
    AppThemePreset.SOFT -> softFloatingTokens
    AppThemePreset.CLAUDE -> claudeFloatingTokens
    else -> iosFloatingTokens
}

// ============================================================================
// A1 / V3（v3.69.0）：设置页组件族去重 —— 三套组件族合一后的差异参数
//
// 背景：设置页原本是**三套组件族并存**（Claude/Ios/Soft × {UserRow, GroupLabel,
// SettingsGroup, SettingCell} 共 ~1079 行近乎 1:1 重复）。补 token 无法解决——
// 因为差异不在「同一组件取不同参数」，而在「有三个同名不同姓的组件」。
//
// V3 的做法：先删到只剩**一套组件**，差异才退化成参数，此时才能用 token 表达。
// 以下五组即合一后残留的参数差异，取值与三份原实现逐条对应（像素回归以此为准）。
// ============================================================================

/**
 * 设置页表面材质角色：分组卡与身份行共用同一套画法语言。
 *
 * 三者是**三种不同的材质**（不是同一材质的三个数值），故用枚举表达。
 */
enum class SettingsSurfaceMaterial {
    /** 书卷：暖米实底 + 收口圆角，无投影无描边 —— 像一整块纸。 */
    PAPER,

    /** 通透：卡片底 + 主题卡圆角，无描边 —— 分组卡另由 glassRim 叠玻璃高光。 */
    CARD,

    /** 柔绘：softSurface 软模糊投影 + 薄涂底 —— 分组卡另叠手绘肌理。 */
    SOFT_BLUR
}

// 批 3（v3.70.4）：`SettingsRowHeight` 枚举已删除 ——
// 它表达的「书卷 48dp / 其余 rowMinHeight」与 `AppSettingsRowTokens.settingsRowMinHeight`
// （三主题都写 52dp）互相矛盾（token 说一套、渲染做另一套）。设置行高现统一读
// `appSpacing().settingsRowMinHeight`，单一事实来源（规范 R4）。

/** 设置行分隔线取色：书卷是硬编码的极淡压黑/压白，其余走主题 divider 角色。 */
enum class SettingsDividerColor {
    /** 硬编码压黑 / 压白（书卷）。 */
    SCRIM,

    /** `appColors().divider`（通透 / 柔绘）。 */
    DIVIDER_TOKEN
}

/** 设置行图标徽章材质：三种画法（淡彩底 / 实色底白图标 / 晕染底 + 羽化描边）。 */
enum class SettingsIconMaterial {
    /** 淡彩底 + 同色系图标（书卷）。 */
    FLAT,

    /** 实色底 + 白图标（通透，iOS 系统色徽章）。 */
    SOLID,

    /** 同色薄涂底 + 同色图标 + 羽化描边（柔绘，避免实色块破坏低对比）。 */
    SOFT_FEATHER
}

/** 设置行尾部插槽：书卷直接内联，通透 / 柔绘用固定 56×32dp 盒（保证开关行与导航行等高）。 */
enum class SettingsTrailingSlot {
    /** 内联：无插槽，尾部内容直接排在行内（书卷）。 */
    INLINE,

    /** 固定 56dp × 32dp 盒，右对齐、内容居中（通透 / 柔绘）。 */
    FIXED_56X32
}

/** 分组标签取色：书卷走主色（琥珀），其余走次级文本色。 */
enum class SettingsLabelColor { PRIMARY, SECONDARY }

/** 身份行头像画法：实色 / 系统色渐变 / 柔绘晕染渐变（带柔光）。 */
enum class SettingsAvatarStyle { SOLID, SYSTEM_GRADIENT, SOFT_GLOW_GRADIENT }

/** 身份行头像首字色：书卷走主色，通透 / 柔绘走白色（压在渐变底上）。 */
enum class SettingsAvatarInitialColor { PRIMARY, WHITE }

/** 设置页顶栏形态：决定大标题由谁承担，以及是否挂载折叠滚动。 */
enum class SettingsTopBar {
    /** 无顶栏：不挂 exitUntilCollapsed 的 nestedScroll（书卷 / 柔绘）。 */
    NONE,

    /** 左对齐大标题顶栏（通透，对齐 iOS 设置 App）。 */
    LEADING_LARGE
}

/** 设置列表行 tokens（原 ClaudeListItem / IosSettingCell / SoftSettingCell 的差异参数）。 */
data class AppSettingsRowTokens(
    val dividerInset: Dp,
    val dividerColor: SettingsDividerColor,
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
    val iconBoxSize: Dp,
    val iconBoxRadius: Dp,
    val iconMaterial: SettingsIconMaterial,
    val iconGap: Dp,
    val titleSize: TextUnit,
    val titleWeight: FontWeight,
    val titleLetterSpacing: TextUnit,
    val detailSize: TextUnit,
    val chevronSize: Dp,
    val chevronAlpha: Float,
    val trailingSlot: SettingsTrailingSlot
)

/** 设置分组卡 tokens（原 ClaudeInsetGroup / IosSettingsGroup / SoftSettingsGroup）。 */
data class AppSettingsGroupTokens(
    val material: SettingsSurfaceMaterial,
    val cornerRadius: Dp,
    /** 通透分组卡叠 iosGlassRim 玻璃高光内描边。 */
    val glassRim: Boolean,
    /** 柔绘分组卡叠手绘肌理。 */
    val texture: Boolean,
    /** 柔绘软投影强度（其余材质忽略）。 */
    val elevation: Dp
)

/** 分组标签 tokens（原 ClaudeGroupLabel / IosGroupLabel / SoftGroupLabel）。 */
data class AppSettingsLabelTokens(
    val fontSize: TextUnit,
    val fontWeight: FontWeight,
    val letterSpacing: TextUnit,
    val uppercase: Boolean,
    val color: SettingsLabelColor,
    val startPadding: Dp
)

/** 身份行 tokens（原 ClaudeUserRow / IosUserRow / SoftUserRow）。 */
data class AppSettingsUserRowTokens(
    val material: SettingsSurfaceMaterial,
    val cornerRadius: Dp,
    val elevation: Dp,
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
    val avatarStyle: SettingsAvatarStyle,
    val avatarInitialColor: SettingsAvatarInitialColor,
    val avatarInitialWeight: FontWeight,
    val nameSize: TextUnit,
    val nameWeight: FontWeight,
    val chevronSize: Dp,
    val chevronAlpha: Float
)

/** 设置页骨架 tokens：顶栏形态 + 宽屏是否居中。 */
data class AppSettingsPageTokens(
    val topBar: SettingsTopBar,
    val centerContent: Boolean
)

/** CompositionLocal：设置列表行 tokens（默认 = 通透）。 */
val LocalAppSettingsRowTokens = staticCompositionLocalOf { iosSettingsRowTokens }

/** 组件层快捷访问设置列表行 tokens。 */
@Composable
fun appSettingsRow(): AppSettingsRowTokens = LocalAppSettingsRowTokens.current

/** 按主题预设取设置列表行 tokens。 */
fun appSettingsRowTokens(preset: AppThemePreset): AppSettingsRowTokens = when (preset) {
    AppThemePreset.SOFT -> softSettingsRowTokens
    AppThemePreset.CLAUDE -> claudeSettingsRowTokens
    else -> iosSettingsRowTokens
}

/** CompositionLocal：设置分组卡 tokens（默认 = 通透）。 */
val LocalAppSettingsGroupTokens = staticCompositionLocalOf { iosSettingsGroupTokens }

/** 组件层快捷访问设置分组卡 tokens。 */
@Composable
fun appSettingsGroup(): AppSettingsGroupTokens = LocalAppSettingsGroupTokens.current

/** 按主题预设取设置分组卡 tokens。 */
fun appSettingsGroupTokens(preset: AppThemePreset): AppSettingsGroupTokens = when (preset) {
    AppThemePreset.SOFT -> softSettingsGroupTokens
    AppThemePreset.CLAUDE -> claudeSettingsGroupTokens
    else -> iosSettingsGroupTokens
}

/** CompositionLocal：分组标签 tokens（默认 = 通透）。 */
val LocalAppSettingsLabelTokens = staticCompositionLocalOf { iosSettingsLabelTokens }

/** 组件层快捷访问分组标签 tokens。 */
@Composable
fun appSettingsLabel(): AppSettingsLabelTokens = LocalAppSettingsLabelTokens.current

/** 按主题预设取分组标签 tokens。 */
fun appSettingsLabelTokens(preset: AppThemePreset): AppSettingsLabelTokens = when (preset) {
    AppThemePreset.SOFT -> softSettingsLabelTokens
    AppThemePreset.CLAUDE -> claudeSettingsLabelTokens
    else -> iosSettingsLabelTokens
}

/** CompositionLocal：身份行 tokens（默认 = 通透）。 */
val LocalAppSettingsUserRowTokens = staticCompositionLocalOf { iosSettingsUserRowTokens }

/** 组件层快捷访问身份行 tokens。 */
@Composable
fun appSettingsUserRow(): AppSettingsUserRowTokens = LocalAppSettingsUserRowTokens.current

/** 按主题预设取身份行 tokens。 */
fun appSettingsUserRowTokens(preset: AppThemePreset): AppSettingsUserRowTokens = when (preset) {
    AppThemePreset.SOFT -> softSettingsUserRowTokens
    AppThemePreset.CLAUDE -> claudeSettingsUserRowTokens
    else -> iosSettingsUserRowTokens
}

/** CompositionLocal：设置页骨架 tokens（默认 = 通透）。 */
val LocalAppSettingsPageTokens = staticCompositionLocalOf { iosSettingsPageTokens }

/** 组件层快捷访问设置页骨架 tokens。 */
@Composable
fun appSettingsPage(): AppSettingsPageTokens = LocalAppSettingsPageTokens.current

/** 按主题预设取设置页骨架 tokens。 */
fun appSettingsPageTokens(preset: AppThemePreset): AppSettingsPageTokens = when (preset) {
    AppThemePreset.SOFT -> softSettingsPageTokens
    AppThemePreset.CLAUDE -> claudeSettingsPageTokens
    else -> iosSettingsPageTokens
}

// ============================================================================
// 统一表面渲染模式（三主题共用一套调度）
//
// 全站所有「卡片 / 面板 / 列表块」类表面统一从这里生成材质与边缘，由
// [LocalThemePreset] 在**唯一一处**分派到对应主题的渲染实现：
//   · 柔绘 SOFT   → softSurface（软模糊投影 + 漫射柔光 + 羽化描边；必要时叠手绘肌理）
//   · 书卷 CLAUDE → 收口 14dp 圆角 + 暖米分组底 + 0.5dp 实色描边
//   · 通透 IOS    → 白卡 + iosGlassRim 玻璃高光内描边
//
// ⚠️ 全局 UI 优化批 1（v3.71.0）：书卷 / 通透路径**不再加投影**（原为 1dp 硬边投影）。
// 卡片分层改由 `cardBg ≠ pageBg` 的底色差承担（规范 R7「投影只归悬浮层 / 拖拽态 / 柔绘材质」）；
// 柔绘的软模糊投影是**材质本体**（softSurface 内部），不受此约束。
//
// 之所以集中到一处：此前每个调用点各自 if/else 拼材质，三套主题的渲染路径散落各处、
// 边界互相渗漏（典型：柔绘卡片在 softSurface 之外又被重复叠一遍羽化描边）。
// 统一后：① 三主题走同一种「投影 → 裁切 → 底色 → 边缘」调度，层数与顺序一致；
// ② 各主题材质自带 LocalIsSoftTheme 门禁，跨主题调用直接 no-op，互不干扰。
// ============================================================================

/**
 * 统一表面渲染入口。调用点不再各自判断主题，交给本函数按 [LocalThemePreset] 分派。
 *
 * @param shape 主题无关的基准形状（通透直接采用；书卷收口 14dp、柔绘取 appShapes().card）
 * @param containerColor 显式底色；为 null 时按主题取默认容器色（书卷暖米分组底 / 其余卡底）
 * @param elevation 柔绘软投影强度；书卷 / 通透**忽略**（无投影，规范 R7）
 * @param texture 柔绘是否叠手绘肌理（仅默认分组卡，避免干扰其上文字）
 * @param selected 选中态：书卷 / 通透改用主色描边表达，柔绘由底色承担
 */
@Composable
fun Modifier.appSurface(
    shape: Shape,
    containerColor: Color? = null,
    elevation: Dp = 10.dp,
    texture: Boolean = false,
    selected: Boolean = false
): Modifier {
    val preset = LocalThemePreset.current

    if (preset == AppThemePreset.SOFT) {
        val softShape = appShapes().card
        return this
            .softSurface(shape = softShape, containerColor = containerColor, elevation = elevation)
            .then(if (texture && containerColor == null) Modifier.softTexture(softShape) else Modifier)
    }

    // 书卷 / 通透：同一种「极轻投影 + 裁切 + 底色 + 边缘」调度，只差形状与边缘语言
    val tokens = appColors()
    val isClaude = preset == AppThemePreset.CLAUDE
    val resolvedShape: Shape = if (isClaude) RoundedCornerShape(14.dp) else shape
    val bg = containerColor ?: if (isClaude) claudeGroupBg() else tokens.cardBg
    // 全局 UI 优化批 1：删除静态卡片的 1dp 硬边投影。
    // 卡片分层已由 `cardBg ≠ pageBg` 的底色差承担（三主题 ΔL* ≈ 4.4 / 3.2 / 2.7，
    // 删描边后仍成立），再叠一层硬边投影属「多余装饰」—— 投影从此只归
    // 悬浮层（FAB / 菜单 / 底部面板）、拖拽态与柔绘 softSurface 的软模糊投影。
    val surface = this
        .clip(resolvedShape)
        .background(bg)
    return when {
        isClaude -> surface.border(
            width = if (selected) 2.dp else 0.5.dp,
            color = if (selected) tokens.primary else claudeGroupBorder(),
            shape = resolvedShape
        )
        selected -> surface.border(2.dp, tokens.primary, resolvedShape)
        else -> surface.iosGlassRim(resolvedShape)
    }
}
