package com.shangkeschedule.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// 通透主题（iOS 26 · Liquid Glass）样式层
//
// 本文件是 App 唯一的主题样式实现（旧 ClaudeStyle.kt 已删除）。
// 「书卷」主题的全部功能位——页头 / 周次条 / 课程卡片流 / 明日预览 /
// inset grouped 设置列表 / 详情面板 / 弹窗 / 悬浮件——由本文件提供 iOS 26 形态，
// 结构、位置与信息层级与书卷逐项一致，只替换视觉与动效。
//
// 设计基线（Apple Human Interface Guidelines · iOS 26）：
// - 色板 = iOS System Colors（label / secondaryLabel / systemGroupedBackground /
//   separator 全部为 Apple 原始定义值，不做主观调色）
// - 材质 = Liquid Glass：半透明 + 背景模糊 + 镜面高光 + 边缘折射环
// - 圆角 = 连续圆角阶梯 26/22/16/12/10（卡片 16、分组 16、控件 12、小标 10）
// - 字体 = SF 风格：标题 Bold + 紧字距（-0.4），正文 Regular，辅助 Medium
// - 层级 = 靠留白与分组卡分层，不用线框（分隔线只在分组内出现且缩进对齐内容）
// ============================================================================

// --- iOS 26 语义色（Apple 原始定义） ---------------------------------------

/** iOS 26 systemBlue（浅色）。 */
const val IosSystemBlueLight = 0xFF007AFF
/** iOS 26 systemBlue（深色，暗底上提亮一档）。 */
const val IosSystemBlueDark = 0xFF0A84FF

private val IosLabelLight = Color(0xFF000000)
private val IosSecondaryLabelLight = Color(0x993C3C43)   // secondaryLabel 60%
private val IosTertiaryLabelLight = Color(0x4D3C3C43)    // tertiaryLabel 30%
private val IosLabelDark = Color(0xFFFFFFFF)
private val IosSecondaryLabelDark = Color(0x99EBEBF5)
private val IosTertiaryLabelDark = Color(0x4CEBEBF5)

// systemGroupedBackground 家族（浅色）
private val IosGroupedBgLight = Color(0xFFF2F2F7)
private val IosSecondaryGroupedBgLight = Color(0xFFFFFFFF)
private val IosTertiaryGroupedBgLight = Color(0xFFF2F2F7)

// systemGroupedBackground 家族（深色）
private val IosGroupedBgDark = Color(0xFF000000)
private val IosSecondaryGroupedBgDark = Color(0xFF1C1C1E)
private val IosTertiaryGroupedBgDark = Color(0xFF2C2C2E)

// 填充色家族
private val IosFillLight = Color(0x1F787880)   // systemFill 12%
private val IosFillDark = Color(0x3D787880)    // systemFill 24%

// 分隔线（separator / opaqueSeparator）
private val IosSeparatorLight = Color(0x493C3C43)
private val IosSeparatorDark = Color(0x99545458)

// --- iOS 26 系统色（浅 / 深） ----------------------------------------------

private val IosBlueLight = Color(0xFF007AFF)
private val IosGreenLight = Color(0xFF34C759)
private val IosOrangeLight = Color(0xFFFF9500)
private val IosRedLight = Color(0xFFFF3B30)
private val IosPinkLight = Color(0xFFFF2D55)
private val IosPurpleLight = Color(0xFFAF52DE)
private val IosTealLight = Color(0xFF5AC8FA)
private val IosIndigoLight = Color(0xFF5856D6)
private val IosYellowLight = Color(0xFFFFCC00)

private val IosBlueDark = Color(0xFF0A84FF)
private val IosGreenDark = Color(0xFF30D158)
private val IosOrangeDark = Color(0xFFFF9F0A)
private val IosRedDark = Color(0xFFFF453A)
private val IosPinkDark = Color(0xFFFF375F)
private val IosPurpleDark = Color(0xFFBF5AF2)
private val IosTealDark = Color(0xFF64D2FF)
private val IosIndigoDark = Color(0xFF5E5CE6)
private val IosYellowDark = Color(0xFFFFD60A)

/**
 * 通透主题锁定的 M3 ColorScheme（浅色）。
 *
 * 绕过 MaterialKolor 派生：Expressive 风格会对种子色做色相旋转，systemBlue #007AFF
 * 会被派生成绿色系 primary。这里严格锁定 Apple 系统色，保证全站蓝是同一个蓝。
 */
internal fun iosLightColorScheme(): ColorScheme = lightColorScheme(
    primary = IosBlueLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0x1F007AFF),          // 12% 系统蓝选中底
    onPrimaryContainer = Color(0xFF003E7A),
    secondary = IosIndigoLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0x1F5856D6),
    onSecondaryContainer = Color(0xFF1B1B4B),
    tertiary = IosGreenLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0x1F34C759),
    onTertiaryContainer = Color(0xFF0B3D1D),
    error = IosRedLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0x1FFF3B30),
    onErrorContainer = Color(0xFF5C100A)
)

/**
 * 通透主题锁定的 M3 ColorScheme（深色）。
 */
internal fun iosDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = IosBlueDark,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0x3D0A84FF),          // 24% 系统蓝选中底
    onPrimaryContainer = Color(0xFFCFE3FF),
    secondary = IosIndigoDark,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0x3D5E5CE6),
    onSecondaryContainer = Color(0xFFE2E1FF),
    tertiary = IosGreenDark,
    onTertiary = Color(0xFF00210C),
    tertiaryContainer = Color(0x3D30D158),
    onTertiaryContainer = Color(0xFFD3F8DC),
    error = IosRedDark,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0x3DFF453A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * 通透浅色 tokens：逐项对齐 iOS 26 分组列表层级。
 */
internal fun iosLightAppColorTokens(): AppColorTokens = AppColorTokens(
    // 页面与容器
    pageBg = IosGroupedBgLight,                    // systemGroupedBackground
    cardBg = IosSecondaryGroupedBgLight,           // secondarySystemGroupedBackground（白卡）
    cardBgElevated = IosSecondaryGroupedBgLight,
    inputBg = IosFillLight,                        // systemFill 12%
    divider = IosSeparatorLight,
    // 文本
    textPrimary = IosLabelLight,
    textSecondary = Color(0xFF8E8E93),             // systemGray（secondaryLabel 实色档）
    textOnPrimary = Color(0xFFFFFFFF),
    // 语义色 —— 严格 Apple 系统色
    primary = IosBlueLight,
    primarySoft = Color(0x1F007AFF),
    success = IosGreenLight,
    successSoft = Color(0x1F34C759),
    info = IosTealLight,
    infoSoft = Color(0x1F5AC8FA),
    warning = IosOrangeLight,
    warningSoft = Color(0x1FFF9500),
    amber = IosYellowLight,
    amberSoft = Color(0x1FFFCC00),
    danger = IosRedLight,
    dangerSoft = Color(0x1FFF3B30),
    favorite = IosPinkLight,
    favoriteSoft = Color(0x1FFF2D55),
    // 头部渐变 —— 系统蓝 → 靛蓝
    gradientStart = IosBlueLight,
    gradientEnd = IosIndigoLight,
    // 底部导航 / 徽标 —— Liquid Glass 底栏：近乎透明，靠 blur 与边缘光学立形
    navBarBg = Color(0xD9F9F9F9),                  // 85% 系统底 + 玻璃模糊
    navSelectedBg = Color(0x1F007AFF),
    badgeBg = IosRedLight,
    badgeFg = Color(0xFFFFFFFF),
    // Snackbar —— iOS 深色毛玻璃提示条
    snackbarBg = Color(0xE61C1C1E),
    snackbarFg = Color(0xFFFFFFFF),
    // 阴影 —— iOS 26 靠材质而非投影分层，阴影极淡
    shadow = Color(0x0F000000)
)

/**
 * 通透深色 tokens：逐项对齐 iOS 26 深色分组列表层级。
 */
internal fun iosDarkAppColorTokens(): AppColorTokens = run {
    val primary = IosBlueDark
    AppColorTokens(
        // 页面与容器 —— iOS 深色下页面底是纯黑，卡片抬起一级
        pageBg = IosGroupedBgDark,                 // systemGroupedBackground (dark) = #000000
        cardBg = IosSecondaryGroupedBgDark,        // #1C1C1E
        cardBgElevated = IosTertiaryGroupedBgDark, // #2C2C2E
        inputBg = IosFillDark,
        divider = IosSeparatorDark,
        // 文本
        textPrimary = IosLabelDark,
        textSecondary = Color(0xFF98989D),         // systemGray (dark)
        textOnPrimary = Color(0xFFFFFFFF),
        // 语义色 —— Apple 深色系统色
        primary = primary,
        primarySoft = Color(0x3D0A84FF),
        success = IosGreenDark,
        successSoft = Color(0x3D30D158),
        info = IosTealDark,
        infoSoft = Color(0x3D64D2FF),
        warning = IosOrangeDark,
        warningSoft = Color(0x3DFF9F0A),
        amber = IosYellowDark,
        amberSoft = Color(0x3DFFD60A),
        danger = IosRedDark,
        dangerSoft = Color(0x3DFF453A),
        favorite = IosPinkDark,
        favoriteSoft = Color(0x3DFF375F),
        // 头部渐变
        gradientStart = primary,
        gradientEnd = IosIndigoDark,
        // 底部导航 / 徽标 —— 深色玻璃底栏
        navBarBg = Color(0xD91C1C1E),
        navSelectedBg = Color(0x3D0A84FF),
        badgeBg = IosRedDark,
        badgeFg = Color(0xFFFFFFFF),
        // Snackbar
        snackbarBg = Color(0xF22C2C2E),
        snackbarFg = Color(0xFFFFFFFF),
        // 阴影 —— 深色下更弱
        shadow = Color(0x66000000)
    )
}

/**
 * 按深浅模式取通透（iOS 26）颜色 tokens。
 *
 * App 现在只有这一套 tokens——旧 `lightAppColorTokens` / `darkAppColorTokens`
 * （v2 紫渐变基线）已随经典/云舒主题一并删除。
 */
fun iosAppColorTokens(isDark: Boolean): AppColorTokens =
    if (isDark) iosDarkAppColorTokens() else iosLightAppColorTokens()

// ============================================================================
// 通透形状 / 间距 / 字阶 tokens（iOS 26 连续圆角 + 8pt 网格 + SF 字阶）
//
// 圆角阶梯：卡片 16（iOS inset grouped 标准）、hero 22（大分组头卡）、
// 控件 12（按钮/输入/单元格图标容器）、小标 10（chip）、菜单 14。
// 间距遵循 8pt 网格：页边距 20、卡间距 16、分区间距 24、卡内 16、行高 52。
// ============================================================================

/** 通透形状 tokens。 */
internal val iosShapeTokens = AppShapeTokens(
    card = RoundedCornerShape(16.dp),
    sheetTop = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    chip = RoundedCornerShape(10.dp),
    chipSmall = RoundedCornerShape(8.dp),
    chipSmallRadius = 8.dp,
    menu = RoundedCornerShape(14.dp),
    capsule = RoundedCornerShape(50),
    fab = RoundedCornerShape(50),
    heroCard = RoundedCornerShape(22.dp),
    bubble = RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp
    )
)

/** 通透间距 tokens（8pt 网格）。 */
internal val iosSpacingTokens = AppSpacingTokens(
    pageHorizontal = 20.dp,
    cardGap = 16.dp,
    listGap = 24.dp,
    cardInner = 16.dp,
    rowMinHeight = 52.dp,
    touchMin = 48.dp,
    chipIcon = 44.dp,
    fab = 56.dp,
    navBarHorizontal = 16.dp,
    navBarBottom = 12.dp
)

/**
 * 通透字阶 tokens（SF 字阶）。
 * - hero 34 = iOS Large Title
 * - bigNumber 28 = Title 1
 * - pageTitle 22 = Title 2
 * - sectionTitle 17 = Headline（iOS 分组小标题）
 * - rowTitle 17 = Body（列表行标题恰是 17）
 * - body 17 = Body（iOS 正文字号是 17，不是 15）
 * - caption 13 = Footnote
 * - hint 11 = Caption 2
 */
internal val iosTypeTokens = AppTypeTokens(
    bigNumber = 28.sp,
    hero = 34.sp,
    sectionTitle = 17.sp,
    timeLabel = 13.sp,
    badge = 11.sp,
    pageTitle = 22.sp,
    rowTitle = 17.sp,
    body = 17.sp,
    caption = 13.sp,
    hint = 11.sp
)

// ============================================================================
// 通透分组卡（inset grouped）材质
//
// iOS 26 的「Inset Grouped List」是 App 的主题语言：白色（深色下 #1C1C1E）圆角卡片
// 承载一组同类行，卡片之间用页面底色的留白分隔，组内行与行之间用缩进分隔线。
// 书卷主题用「暖米色分组底 + 0.5dp 描边」，这里换成 iOS 26 的
// 「卡片底 + Liquid Glass 高光描边」——位置与层级完全一致，材质不同。
// ============================================================================

/** 通透分组卡底色：浅色白卡 / 深色 #1C1C1E。 */
@Composable
fun iosGroupBg(): Color = if (LocalIsDarkTheme.current) {
    IosSecondaryGroupedBgDark
} else {
    IosSecondaryGroupedBgLight
}

/**
 * 通透分组卡描边：iOS 26 玻璃高光边——浅色下是极淡的冷灰内描边（把白卡从浅灰页面上
 * 托起来），深色下是极淡的白色内描边（把深卡从纯黑页面上托起来）。
 */
@Composable
fun iosGroupBorder(): Color = if (LocalIsDarkTheme.current) {
    Color(0x14FFFFFF)
} else {
    Color(0x0F3C3C43)
}

// ============================================================================
// 通透主题字体
//
// iOS 用 SF Pro。Android 上无法内置（授权限制），因此走「系统无衬线 + SF 字阶 +
// SF 字重/字距」的等价路线：字号、字重、行高、字距严格按 HIG 取值，字形交给
// 系统 sans（Roboto 与 SF 同为 Helvetica 系人文无衬线，观感接近）。
// 书卷主题用 Poppins / Newsreader / Lora 三套字体——这是它视觉性格的主要来源；
// 通透换成系统无衬线，属于用户要求的「只替换 UI 与动画组件」范围内的字体替换。
// ============================================================================

/** 通透 UI 无衬线字族（系统默认，等价 SF Pro Text）。 */
@Composable
fun iosUiSans(): FontFamily = FontFamily.Default

/** 通透展示字族（系统默认，等价 SF Pro Display —— iOS 大标题同族仅光学尺寸不同）。 */
@Composable
fun iosDisplaySans(): FontFamily = FontFamily.Default

/**
 * 通透专属 Material3 Typography：严格按 iOS 26 字阶取值。
 *
 * 与书卷的关键差别是**没有衬线**：书卷的 display/headline 用 Newsreader 衬线制造
 * 「书卷气」，通透全部改为系统无衬线并加大字重，这是 iOS 系统的原生观感。
 */
@Composable
fun iosTypography(): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = iosDisplaySans(),
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.37.sp
    ),
    displayMedium = TextStyle(
        fontFamily = iosDisplaySans(),
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.36.sp
    ),
    displaySmall = TextStyle(
        fontFamily = iosDisplaySans(),
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.35.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = iosDisplaySans(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = iosUiSans(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.26).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = iosUiSans(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp
    ),
    titleLarge = TextStyle(
        fontFamily = iosUiSans(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp
    ),
    titleMedium = TextStyle(
        fontFamily = iosUiSans(),
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.32).sp
    ),
    titleSmall = TextStyle(
        fontFamily = iosUiSans(),
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = iosUiSans(),
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = iosUiSans(),
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp
    ),
    bodySmall = TextStyle(
        fontFamily = iosUiSans(),
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp
    ),
    labelLarge = TextStyle(
        fontFamily = iosUiSans(),
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp
    ),
    labelMedium = TextStyle(
        fontFamily = iosUiSans(),
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp
    ),
    labelSmall = TextStyle(
        fontFamily = iosUiSans(),
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.07.sp
    )
)