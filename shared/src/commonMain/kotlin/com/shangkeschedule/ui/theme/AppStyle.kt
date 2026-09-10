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
 * iOS 浅色 tokens：严格对齐 iOS 17/18 系统应用风格
 * 设计原则：
 * - 页面底 = systemGroupedBackground (#F2F2F7) —— iOS 设置/健康 App 的分组列表底色
 * - 卡片 = secondarySystemGroupedBackground (#FFFFFF) —— 分组列表内的白卡
 * - 主色严格锁定 systemBlue (#007AFF)，不跟随动态取色/自定义主色
 * - 语义色全部使用 Apple 系统色系（Blue/Green/Indigo/Orange/Red/Pink/Teal/Yellow/Purple）
 * - 分隔线 = separator 半透明色（非不透明色）
 * - 文字 = label / secondaryLabel / tertiaryLabel 三级
 * - 导航栏/底栏 = 毛玻璃材质（ultraThinMaterial 质感）
 * - 阴影极淡，内容优先
 */
private fun iosLightAppColorTokens() = AppColorTokens(
    // 页面与容器 —— iOS 分组列表背景层级
    pageBg = Color(0xFFF2F2F7),              // systemGroupedBackground
    cardBg = Color(0xFFFFFFFF),              // secondarySystemGroupedBackground (card)
    cardBgElevated = Color(0xFFF7F7FA),      // tertiarySystemGroupedBackground
    inputBg = Color(0xFFE5E5EA),             // systemGray5
    divider = Color(0x5C3C3C43),             // separator (non-opaque, 0.36 alpha) —— iOS 标准半透明分隔线
    // 文本 —— Apple label 三级体系
    textPrimary = Color(0xFF000000),         // label
    textSecondary = Color(0xFF8E8E93),       // secondaryLabel (systemGray)
    textOnPrimary = Color.White,             // 主色按钮上的白字
    // 语义色 —— 严格使用 Apple 系统色系
    primary = Color(0xFF007AFF),             // systemBlue —— 锁定，不跟随动态取色
    primarySoft = Color(0x14007AFF),         // 8% 系统蓝淡底（iOS 选中态/胶囊底）
    success = Color(0xFF34C759),             // systemGreen
    successSoft = Color(0x1434C759),
    info = Color(0xFF5AC8FA),                // systemTeal
    infoSoft = Color(0x145AC8FA),
    warning = Color(0xFFFF9500),             // systemOrange
    warningSoft = Color(0x14FF9500),
    amber = Color(0xFFFFCC00),               // systemYellow
    amberSoft = Color(0x14FFCC00),
    danger = Color(0xFFFF3B30),              // systemRed
    dangerSoft = Color(0x14FF3B30),
    favorite = Color(0xFFFF2D55),            // systemPink
    favoriteSoft = Color(0x14FF2D55),
    // 头部渐变 —— iOS 系统蓝 → 靛蓝 斜向（Health/健身 App 风格）
    gradientStart = Color(0xFF007AFF),
    gradientEnd = Color(0xFF5856D6),         // systemIndigo
    // 底部导航 / 徽标 —— iOS 毛玻璃底栏质感
    navBarBg = Color(0xE6F9F9F9),            // 90% 不透明 + 毛玻璃 ultraThinMaterial
    navSelectedBg = Color(0x14007AFF),       // 8% 蓝选底（iOS Tab 选中高亮）
    badgeBg = Color(0xFFFF3B30),             // systemRed 徽标
    badgeFg = Color(0xFFFFFFFF),
    // Snackbar —— iOS 风格深色毛玻璃提示
    snackbarBg = Color(0xE61C1C1E),
    snackbarFg = Color(0xFFFFFFFF),
    // 阴影 —— iOS 风格极淡阴影，几乎不可见
    shadow = Color(0x0A000000)
)

/**
 * iOS 深色 tokens：严格对齐 iOS 深色模式规范
 * 设计原则：
 * - 页面底 = systemBackground (#1C1C1E) —— 深灰非纯黑，保护眼睛
 * - 卡片 = secondarySystemBackground (#2C2C2E)
 * - 语义色使用 Apple 深色模式变体（更鲜亮、更高饱和度）
 * - 分隔线 = separator 深色半透明色
 * - 主色 systemBlue (dark) (#0A84FF) 锁定
 * - 毛玻璃效果在深色下更明显（dark chrome 质感）
 */
private fun iosDarkAppColorTokens() = run {
    val primary = Color(0xFF0A84FF)          // systemBlue (dark) —— 锁定
    val success = Color(0xFF30D158)          // systemGreen (dark)
    val info = Color(0xFF64D2FF)             // systemTeal (dark)
    val warning = Color(0xFFFF9F0A)          // systemOrange (dark)
    val amber = Color(0xFFFFD60A)            // systemYellow (dark)
    val danger = Color(0xFFFF453A)           // systemRed (dark)
    val favorite = Color(0xFFFF375F)         // systemPink (dark)
    AppColorTokens(
        // 页面与容器 —— iOS 深色三级背景
        pageBg = Color(0xFF1C1C1E),          // systemBackground (dark)
        cardBg = Color(0xFF2C2C2E),          // secondarySystemBackground (dark)
        cardBgElevated = Color(0xFF3A3A3C),  // tertiarySystemBackground (dark)
        inputBg = Color(0xFF3A3A3C),         // tertiarySystemBackground
        divider = Color(0x5A84848A),         // separator (dark, non-opaque) — 0.35 alpha
        // 文本 —— 深色 label 三级
        textPrimary = Color(0xFFFFFFFF),     // label (dark)
        textSecondary = Color(0xFF98989D),   // secondaryLabel (dark)
        textOnPrimary = Color.White,
        // 语义色 —— Apple 深色系统色系
        primary = primary,
        primarySoft = Color(0x3D0A84FF),     // 24% 蓝淡底（深色选中态）
        success = success,
        successSoft = Color(0x3D30D158),
        info = info,
        infoSoft = Color(0x3D64D2FF),
        warning = warning,
        warningSoft = Color(0x3DFF9F0A),
        amber = amber,
        amberSoft = Color(0x3DFFD60A),
        danger = danger,
        dangerSoft = Color(0x3DFF453A),
        favorite = favorite,
        favoriteSoft = Color(0x3DFF375F),
        // 头部渐变 —— 深色蓝 → 靛蓝
        gradientStart = primary,
        gradientEnd = Color(0xFF5E5CE6),     // systemIndigo (dark)
        // 底部导航 / 徽标 —— 深色毛玻璃底栏
        navBarBg = Color(0xE61C1C1E),        // 90% 不透明 + 毛玻璃
        navSelectedBg = Color(0x3D0A84FF),   // 24% 蓝选底
        badgeBg = danger,
        badgeFg = Color(0xFFFFFFFF),
        // Snackbar —— 深色毛玻璃提示
        snackbarBg = Color(0xE62C2C2E),
        snackbarFg = Color(0xFFFFFFFF),
        // 阴影 —— 深色下几乎无阴影，靠层级区分
        shadow = Color(0x05000000)
    )
}

/** 主色同步后的 token 集合：由 Theme 在组合内提供（primary 跟随用户实际主题）。 */
val LocalAppColorTokens = staticCompositionLocalOf { appColorTokens(false) }

/** 组件层快捷访问（跟随全局深浅色与主题主色同步）。 */
@Composable
fun appColors(): AppColorTokens = LocalAppColorTokens.current

/**
 * 形状 tokens（v2 规范 §2/§4）：
 * 卡片 20dp、头部渐变卡 24dp、图标 chip / 菜单 16dp、底部菜单顶角 24dp、
 * 气泡 18dp（单侧收尾 6dp）、对话框统一走 extraLarge=24。
 */
object AppShape {
    val card = RoundedCornerShape(20.dp)
    val heroCard = RoundedCornerShape(24.dp)
    val chip = RoundedCornerShape(16.dp)
    /** 小号 chip：图标 chip / 输入框等 14dp 形态（原散落的 14dp 魔法数收敛于此）。 */
    val chipSmallRadius = 14.dp
    val chipSmall = RoundedCornerShape(chipSmallRadius)
    val menu = RoundedCornerShape(16.dp)
    val sheetTop = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val bubble = RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp
    )
    val capsule = RoundedCornerShape(50)
}

/** MaterialTheme Shapes 全局覆盖：让 AlertDialog / BottomSheet / Menu 全部对齐基线圆角。
 *  注意 extraLarge 必须是四角 24dp：AlertDialog 四角都用它；BottomSheet 只露出顶部两角，
 *  底部两角贴屏幕边缘不可见，因此统一四角圆角即可，不能用只定义顶角的 sheetTop。
 */
val AppMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = AppShape.chip,
    large = AppShape.card,
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * 间距 / 尺寸 tokens（v2 规范 §2）。
 */
object AppSpacing {
    val pageHorizontal: Dp = 16.dp
    val cardGap: Dp = 12.dp
    /** 列表纵向节奏：卡与卡 / 分区与分区之间的主力间距。 */
    val listGap: Dp = 20.dp
    val cardInner: Dp = 16.dp
    val rowMinHeight: Dp = 64.dp
    val touchMin: Dp = 48.dp
    val chipIcon: Dp = 48.dp
    val fab: Dp = 56.dp
    val navBarHorizontal: Dp = 16.dp
    val navBarBottom: Dp = 12.dp
}

/**
 * 字阶 tokens（v2 规范 §2）：大数字 28–34 Bold / 页标题 18–20 Bold /
 * 行标题 16 SemiBold / 正文 14–15 / 辅助 12–13 灰。
 */
object AppType {
    val bigNumber = 28.sp
    /** Hero 大标题（设置页头部 / 关于页应用名）：24sp，配合 ExtraBold。 */
    val hero = 24.sp
    /** 分区标题（二级页内的小节标题）：18sp，配合 Bold。 */
    val sectionTitle = 18.sp
    /** 时间列标签（课表时间轴）：17sp。 */
    val timeLabel = 17.sp
    /** 徽标 / 底部导航小字：11sp。 */
    val badge = 11.sp
    val pageTitle = 20.sp
    val rowTitle = 16.sp
    val body = 15.sp
    val caption = 13.sp
    val hint = 12.sp
}

/**
 * 课表网格微字号（网格微排版收敛）：
 * 课程块内文字基值为 Float，随用户 fontScale 缩放；固定文字直接用 sp。
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
 * 把基线页面的结构性颜色映射进 Material ColorScheme：
 * surface/background → 淡灰白页面底，surfaceContainer* → 白色卡片，
 * surfaceVariant → 输入/嵌入底。
 * 强调/选中容器（primary/secondary/tertiaryContainer）统一收敛为基线淡紫胶囊语言：
 * 动态取色在暖色墙纸下会生成棕/土色容器（浅色模式出现深棕色暗调的根因），在此一并压平。
 * 主色 primary 本身保持用户主题/动态取色不变。
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

/** 默认形状 tokens（v2 基线，经典/云舒/利落主题使用，与原 AppShape 对象完全一致）。 */
private val defaultShapeTokens = AppShapeTokens(
    card = RoundedCornerShape(20.dp),
    sheetTop = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    chip = RoundedCornerShape(16.dp),
    chipSmall = RoundedCornerShape(14.dp),
    chipSmallRadius = 14.dp,
    menu = RoundedCornerShape(16.dp),
    capsule = RoundedCornerShape(50),
    fab = RoundedCornerShape(50),
    heroCard = RoundedCornerShape(24.dp),
    bubble = RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp
    )
)

/** 默认间距 tokens（v2 基线，经典/云舒/利落主题使用，与原 AppSpacing 对象完全一致）。 */
private val defaultSpacingTokens = AppSpacingTokens(
    pageHorizontal = 16.dp,
    cardGap = 12.dp,
    listGap = 20.dp,
    cardInner = 16.dp,
    rowMinHeight = 52.dp,
    touchMin = 48.dp,
    chipIcon = 48.dp,
    fab = 56.dp,
    navBarHorizontal = 12.dp,
    navBarBottom = 8.dp
)

/** 默认字阶 tokens（v2 基线，经典/云舒/利落主题使用，与原 AppType 对象完全一致）。 */
private val defaultTypeTokens = AppTypeTokens(
    bigNumber = 48.sp,
    hero = 32.sp,
    sectionTitle = 18.sp,
    timeLabel = 12.sp,
    badge = 10.sp,
    pageTitle = 22.sp,
    rowTitle = 16.sp,
    body = 15.sp,
    caption = 13.sp,
    hint = 12.sp
)

// ============================================================================
// iOS 主题 tokens（严格对齐 iOS 17/18 系统应用）
// ============================================================================

/**
 * iOS 形状 tokens：与画布设计稿完全一致
 * - 卡片 19dp —— 对齐画布 --radius: 1.2rem（基准 16sp 下 ≈ 19.2dp）
 * - heroCard 22dp —— 大卡片/统计卡（画布上统计卡用标准圆角，但 hero 区域略大）
 * - chip 10dp —— 胶囊/标签小圆角
 * - chipSmall 8dp —— 输入框/小标签
 * - menu 14dp —— 弹出菜单
 * - 底部面板顶部 19dp —— 与卡片圆角一致
 * - FAB 圆形
 */
private val iosShapeTokens = AppShapeTokens(
    card = RoundedCornerShape(19.dp),
    sheetTop = RoundedCornerShape(topStart = 19.dp, topEnd = 19.dp),
    chip = RoundedCornerShape(10.dp),
    chipSmall = RoundedCornerShape(8.dp),
    chipSmallRadius = 8.dp,
    menu = RoundedCornerShape(14.dp),
    capsule = RoundedCornerShape(50),
    fab = RoundedCornerShape(50),
    heroCard = RoundedCornerShape(22.dp),
    bubble = RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp
    )
)

/**
 * iOS 间距 tokens：与画布设计稿完全一致
 * - 页边距 24dp —— 对齐画布 spacing*6（0.24rem × 6 = 23.04dp ≈ 24dp）
 * - 卡片间距 24dp —— 分组列表组间距与页边距一致
 * - 列表纵向节奏 28dp —— 分区之间的大间距
 * - 卡片内边距 18dp —— 画布卡片内 padding
 * - 行高 56dp —— 课程卡片最小高度
 * - 底部导航底部 12dp —— Home Indicator 上方间距
 * - chipIcon 44dp —— 44pt 标准触控
 */
private val iosSpacingTokens = AppSpacingTokens(
    pageHorizontal = 24.dp,
    cardGap = 24.dp,
    listGap = 28.dp,
    cardInner = 18.dp,
    rowMinHeight = 56.dp,
    touchMin = 48.dp,
    chipIcon = 44.dp,
    fab = 56.dp,
    navBarHorizontal = 16.dp,
    navBarBottom = 12.dp
)

/**
 * iOS 字阶 tokens：与画布设计稿完全一致
 * - hero 34sp Bold —— 导航栏大标题（画布 .nav-bar__title 34px/700）
 * - bigNumber 24sp Bold —— 统计卡大数字（画布 .stat-item__value 24px/700）
 * - pageTitle 22sp Bold —— 页面内大分区标题（画布 .section-header__title 22px/700）
 * - sectionTitle 18sp Bold —— 次级分区标题
 * - rowTitle 17sp Semibold —— 课程卡片标题（画布 .course-card__name 17px/600）
 * - body 15sp Regular —— 正文/卡片次级文字
 * - caption 13sp Medium —— 卡片辅助信息（画布 .course-card__meta-item 13px/500）
 * - hint 12sp Medium —— 最小文字/分组标题（画布 .group-header__title 13px 但 uppercase，取 12sp）
 * - timeLabel 12sp —— 时间标签
 * - badge 11sp —— 徽标/底部导航小字
 */
private val iosTypeTokens = AppTypeTokens(
    bigNumber = 24.sp,
    hero = 34.sp,
    sectionTitle = 18.sp,
    timeLabel = 12.sp,
    badge = 11.sp,
    pageTitle = 22.sp,
    rowTitle = 17.sp,
    body = 15.sp,
    caption = 13.sp,
    hint = 12.sp
)

/** 按主题预设取形状 tokens：通透（iOS）走 iOS 系统规范，Claude（CLAUDE）走设计系统圆角阶梯，其余走默认基线。 */
fun appShapeTokens(preset: AppThemePreset): AppShapeTokens = when (preset) {
    AppThemePreset.IOS -> iosShapeTokens
    AppThemePreset.CLAUDE -> claudeShapeTokens
    else -> defaultShapeTokens
}

/** 按主题预设取间距 tokens：通透（iOS）走 iOS 系统规范，Claude（CLAUDE）走设计系统留白，其余走默认基线。 */
fun appSpacingTokens(preset: AppThemePreset): AppSpacingTokens = when (preset) {
    AppThemePreset.IOS -> iosSpacingTokens
    AppThemePreset.CLAUDE -> claudeSpacingTokens
    else -> defaultSpacingTokens
}

/** 按主题预设取字阶 tokens：通透（iOS）走 iOS 系统规范，Claude（CLAUDE）走设计系统字阶，其余走默认基线。 */
fun appTypeTokens(preset: AppThemePreset): AppTypeTokens = when (preset) {
    AppThemePreset.IOS -> iosTypeTokens
    AppThemePreset.CLAUDE -> claudeTypeTokens
    else -> defaultTypeTokens
}

/** CompositionLocal：形状 tokens。 */
val LocalAppShapeTokens = staticCompositionLocalOf { defaultShapeTokens }

/** CompositionLocal：间距 tokens。 */
val LocalAppSpacingTokens = staticCompositionLocalOf { defaultSpacingTokens }

/** CompositionLocal：字阶 tokens。 */
val LocalAppTypeTokens = staticCompositionLocalOf { defaultTypeTokens }

/** 组件层快捷访问形状 tokens。 */
@Composable
fun appShapes(): AppShapeTokens = LocalAppShapeTokens.current

/** 组件层快捷访问间距 tokens。 */
@Composable
fun appSpacing(): AppSpacingTokens = LocalAppSpacingTokens.current

/** 组件层快捷访问字阶 tokens。 */
@Composable
fun appType(): AppTypeTokens = LocalAppTypeTokens.current
