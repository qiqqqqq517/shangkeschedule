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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    // 利落（TIMETABLE）预设深色模式下的课表文字
    val timetableTextOnDark: Color,
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
    timetableTextOnDark = Color(0xFFE0E0E0),
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
        timetableTextOnDark = Color(0xFFE0E0E0),
        shadow = Color(0x66000000)
    )
}

fun appColorTokens(isDark: Boolean): AppColorTokens =
    if (isDark) darkAppColorTokens() else lightAppColorTokens()

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
