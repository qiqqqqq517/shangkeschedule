package com.shangkeschedule.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import shangkeschedule.shared.generated.resources.Lora_Variable
import shangkeschedule.shared.generated.resources.Newsreader_Variable
import shangkeschedule.shared.generated.resources.Poppins_Bold
import shangkeschedule.shared.generated.resources.Poppins_Medium
import shangkeschedule.shared.generated.resources.Poppins_Regular
import shangkeschedule.shared.generated.resources.Poppins_SemiBold
import shangkeschedule.shared.generated.resources.Res

// ============================================================================
// CLAUDE 主题（Anthropic / Claude 设计系统）
//
// 设计原则：
// - 色板严格取自 Claude 设计系统的 primitive 阶梯（brand / text / bg / border / chart）
// - 浅色 = 暖砂纸底（bg-100 #faf9f5）+ 白卡 + 赤陶主色 brand-500 #c96442
// - 深色 = 暖炭底（bg-100 #262624）+ 亮赤陶主色 brand-500 #d97757
// - 语义淡底色（soft）沿用 iOS tokens 的透明度约定：浅色 0x14 / 深色 0x3D；
//   设计系统自带 50 档色值的（success #f0f3ea、destructive #fcecea）直接取 50 档
// - 圆角更克制（16dp 卡 / 12dp 胶囊），留白更宽松（20dp 页边距）
// - 字体：UI = Poppins，标题 = Newsreader（衬线），正文 = Lora（阅读衬线）
// ============================================================================

// --- 浅色 primitive ---------------------------------------------------------

private val ClaudeBrand50 = Color(0xFFFBF2ED)
private val ClaudeBrand400 = Color(0xFFD6866A)
private val ClaudeBrand500 = Color(0xFFC96442)
private val ClaudeBrand600 = Color(0xFFB0562F)
private val ClaudeBrand800 = Color(0xFF753A22)

private val ClaudeText500 = Color(0xFF6E6D68)
private val ClaudeText600 = Color(0xFF535146)
private val ClaudeText700 = Color(0xFF46443B)
private val ClaudeText800 = Color(0xFF3D3929)
private val ClaudeText900 = Color(0xFF28261B)

private val ClaudeBg50 = Color(0xFFFFFFFF)
private val ClaudeBg100 = Color(0xFFFAF9F5)
private val ClaudeBg200 = Color(0xFFF5F4EF)
private val ClaudeBg300 = Color(0xFFEDE9DE)

private val ClaudeBorder300 = Color(0xFFDAD9D4)

private val ClaudeIcon900 = Color(0xFF141413)

private val ClaudeSecondaryLight = Color(0xFFE9E6DC)
private val ClaudeSuccess500 = Color(0xFF788C5D)
private val ClaudeSuccess50 = Color(0xFFF0F3EA)
private val ClaudeError500 = Color(0xFFD64545)
private val ClaudeError50 = Color(0xFFFCECEA)

/** chart-2 紫罗兰：设计系统的图表强调色，同时充当 tertiary / info 色相。 */
private val ClaudeChart2 = Color(0xFF9C87F5)
private val ClaudeChart4 = Color(0xFFDBD3F0)

// --- 深色 primitive ---------------------------------------------------------

private val ClaudeDarkBrand50 = Color(0xFF3A2A22)
private val ClaudeDarkBrand500 = Color(0xFFD97757)
private val ClaudeDarkBrand600 = Color(0xFFE08D6F)
private val ClaudeDarkBrand900 = Color(0xFFF8E3D8)

private val ClaudeDarkText500 = Color(0xFFB7B5A9)
private val ClaudeDarkText800 = Color(0xFFF1F1EF)
private val ClaudeDarkText900 = Color(0xFFFAF9F5)

private val ClaudeDarkBg100 = Color(0xFF262624)
private val ClaudeDarkBg200 = Color(0xFF2C2C2B)
private val ClaudeDarkBg300 = Color(0xFF30302E)
private val ClaudeDarkBg400 = Color(0xFF3E3E38)

private val ClaudeDarkBorder300 = Color(0xFF3E3E38)

private val ClaudeDarkSuccess500 = Color(0xFF8CA06F)
private val ClaudeDarkSuccess50 = Color(0xFF232A1C)
private val ClaudeDarkError500 = Color(0xFFEF4444)
private val ClaudeDarkError50 = Color(0xFF3A1F1F)

/**
 * CLAUDE 预设锁定的 M3 ColorScheme（浅色）。
 * 绕过 MaterialKolor 派生，primary 严格锁定赤陶 brand-500；
 * 容器色按设计系统 50 档暖砂淡底推导，结构色由 withAppSurfaces 统一映射。
 */
fun claudeLightColorScheme(): ColorScheme = lightColorScheme(
    primary = ClaudeBrand500,             // brand-500 #c96442
    onPrimary = Color(0xFFFFFFFF),        // primary-foreground
    primaryContainer = ClaudeBrand50,     // brand-50 #fbf2ed（暖砂淡底）
    onPrimaryContainer = ClaudeBrand800,  // brand-800 #753a22
    secondary = ClaudeSecondaryLight,     // #e9e6dc
    onSecondary = ClaudeText600,          // secondary-foreground = text-600
    secondaryContainer = ClaudeBg300,     // bg-300 淡一档
    onSecondaryContainer = ClaudeText700, // text-700
    tertiary = ClaudeChart2,              // chart-2 #9c87f5
    onTertiary = ClaudeText900,           // text-900（紫罗兰上取深字，保证对比度）
    tertiaryContainer = ClaudeChart4,     // chart-4 #dbd3f0
    onTertiaryContainer = ClaudeText800,  // text-800
    error = ClaudeError500,               // #d64545
    onError = Color(0xFFFFFFFF),
    errorContainer = ClaudeError50,       // error-50 #fcecea
    onErrorContainer = Color(0xFF7F2222)  // 派生：error-500 加深，保证 50 档底上的对比度
)

/**
 * CLAUDE 预设锁定的 M3 ColorScheme（深色）。
 */
fun claudeDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = ClaudeDarkBrand500,             // brand-500 (dark) #d97757
    onPrimary = ClaudeIcon900,                // primary-foreground (dark) = #141413
    primaryContainer = ClaudeDarkBrand50,     // brand-50 (dark) #3a2a22
    onPrimaryContainer = ClaudeDarkBrand900,  // brand-900 (dark) #f8e3d8
    secondary = ClaudeDarkText900,            // text-900 (dark) #faf9f5
    onSecondary = ClaudeDarkBg300,            // secondary-foreground = bg-300 (dark)
    secondaryContainer = ClaudeDarkBorder300, // border-300 (dark) #3e3e38
    onSecondaryContainer = ClaudeDarkText900,
    tertiary = ClaudeChart2,                  // chart-2（深浅一致）
    onTertiary = ClaudeIcon900,               // #141413
    tertiaryContainer = ClaudeDarkBorder300,
    onTertiaryContainer = ClaudeChart4,       // chart-4 #dbd3f0
    error = ClaudeDarkError500,               // #ef4444
    onError = Color(0xFFFFFFFF),
    errorContainer = ClaudeDarkError50,       // error-50 (dark) #3a1f1f
    onErrorContainer = ClaudeError50          // error-50 (light) #fcecea
)

/**
 * CLAUDE 浅色 tokens：逐项对齐 Claude 设计系统语义层。
 * 结构色（页面/卡片/输入/分隔）取 bg 与 border 阶梯，语义色取 brand / success / destructive。
 */
fun claudeLightAppColorTokens(): AppColorTokens = AppColorTokens(
    // 页面与容器 —— 暖砂纸底 + 白卡 + 略深的嵌入底
    pageBg = ClaudeBg100,                     // bg-100 #faf9f5
    cardBg = ClaudeBg200,                     // bg-200 #f5f4ef
    cardBgElevated = ClaudeBg50,              // bg-50 #ffffff（浮起卡）
    inputBg = ClaudeBg300,                    // bg-300 #ede9de（输入/嵌入底）
    divider = ClaudeBorder300,                // border-300 #dad9d4
    // 文本 —— text 阶梯三级
    textPrimary = ClaudeText800,              // text-800 #3d3929
    textSecondary = ClaudeText500,            // text-500 #6e6d68
    textOnPrimary = Color(0xFFFFFFFF),        // primary-foreground
    // 语义色对 —— 主色 = brand-500，淡底 = brand-50
    primary = ClaudeBrand500,                 // brand-500 #c96442
    primarySoft = ClaudeBrand50,              // brand-50 #fbf2ed
    success = ClaudeSuccess500,               // #788c5d
    successSoft = ClaudeSuccess50,            // success-50 #f0f3ea
    info = ClaudeChart2,                      // 设计系统无 info 色相，取 chart-2 紫罗兰（图表强调色）
    infoSoft = Color(0x149C87F5),             // 浅色 0x14 淡底（iOS tokens 约定）
    // 设计系统无独立 warning 色相，取 brand-600 暖赭 #b0562f（与赤陶主色同族但不撞色）
    warning = ClaudeBrand600,
    warningSoft = Color(0x14B0562F),
    // 设计系统无独立 amber 色相，取与 success 橄榄绿同族的暖赭金 #a8863c
    amber = Color(0xFFA8863C),
    amberSoft = Color(0x14A8863C),
    danger = ClaudeError500,                  // #d64545
    dangerSoft = ClaudeError50,               // error-50 #fcecea
    // 设计系统无 favorite 色相，取与赤陶同族的暖玫瑰 #c0506a（避开 destructive 红）
    favorite = Color(0xFFC0506A),
    favoriteSoft = Color(0x14C0506A),
    // 头部渐变 —— 赤陶主色 → 浅赤陶（Theme 会同步为 colorScheme.primary 同源渐变）
    gradientStart = ClaudeBrand500,
    gradientEnd = ClaudeBrand400,
    // 底部导航 / 徽标 —— 取 sidebar 语义色
    navBarBg = Color(0xFFF5F4EE),             // sidebar #f5f4ee
    navSelectedBg = ClaudeSecondaryLight,     // sidebar-accent #e9e6dc
    badgeBg = ClaudeBrand500,                 // brand-500 徽标
    badgeFg = Color(0xFFFFFFFF),
    // Snackbar —— 深底浅字（暖炭底 + 暖砂字）
    snackbarBg = ClaudeText800,               // text-800 #3d3929
    snackbarFg = ClaudeBg100,                 // bg-100 #faf9f5
    // 阴影 —— 对齐设计系统 shadow-sm/md 的 rgba(0,0,0,.1)
    shadow = Color(0x1A000000)
)

/**
 * CLAUDE 深色 tokens：同样的结构与语义，底色反转为暖炭；
 * 淡底沿用深色 0x3D 透明度约定（success / destructive 直接用设计系统的 50 档深色值）。
 */
fun claudeDarkAppColorTokens(): AppColorTokens = run {
    val primary = ClaudeDarkBrand500
    AppColorTokens(
        // 页面与容器 —— 暖炭三级
        pageBg = ClaudeDarkBg100,             // bg-100 (dark) #262624
        cardBg = ClaudeDarkBg200,             // bg-200 (dark) #2c2c2b
        cardBgElevated = ClaudeDarkBg300,     // bg-300 (dark) #30302e
        inputBg = ClaudeDarkBg400,            // bg-400 (dark) #3e3e38（比卡片亮一档，与浅色 bg-300 输入底对称）
        divider = ClaudeDarkBorder300,        // border-300 (dark) #3e3e38
        // 文本 —— text 阶梯（深色 800 为近白）
        textPrimary = ClaudeDarkText800,      // text-800 (dark) #f1f1ef
        textSecondary = ClaudeDarkText500,    // text-500 (dark) #b7b5a9
        textOnPrimary = ClaudeIcon900,        // primary-foreground (dark) #141413
        // 语义色对 —— 深色 0x3D 淡底约定
        primary = primary,                    // brand-500 (dark) #d97757
        primarySoft = ClaudeDarkBrand50,      // brand-50 (dark) #3a2a22
        success = ClaudeDarkSuccess500,       // #8ca06f
        successSoft = ClaudeDarkSuccess50,    // success-50 (dark) #232a1c
        info = ClaudeChart2,                  // chart-2（深浅一致）
        infoSoft = Color(0x3D9C87F5),
        warning = ClaudeDarkBrand600,         // brand-600 (dark) #e08d6f
        warningSoft = Color(0x3DE08D6F),
        amber = Color(0xFFC7A45C),            // 浅色 #a8863c 在深底上提亮一档
        amberSoft = Color(0x3DC7A45C),
        danger = ClaudeDarkError500,          // #ef4444
        dangerSoft = ClaudeDarkError50,       // error-50 (dark) #3a1f1f
        favorite = Color(0xFFDA7E92),         // 浅色 #c0506a 在深底上提亮一档
        favoriteSoft = Color(0x3DDA7E92),
        // 头部渐变 —— 深色赤陶 → 亮赤陶
        gradientStart = primary,
        gradientEnd = ClaudeDarkBrand600,
        // 底部导航 / 徽标 —— sidebar 语义色
        navBarBg = Color(0xFF1F1E1D),         // sidebar (dark)
        navSelectedBg = Color(0xFF0F0F0E),    // sidebar-accent (dark)
        badgeBg = primary,
        badgeFg = ClaudeIcon900,
        // Snackbar —— 深色下同样保持深底浅字
        snackbarBg = ClaudeDarkBg300,
        snackbarFg = ClaudeDarkText800,
        // 阴影 —— 深色下更重，靠层级区分
        shadow = Color(0x66000000)
    )
}

/**
 * CLAUDE 形状 tokens：对齐设计系统 radius 阶梯
 * - card 16dp / heroCard 20dp（--radius 16 / --radius-xl 20）
 * - chip 12dp / chipSmall 8dp（--radius-md 12 / --radius-sm 8）
 * - menu 12dp、底部面板顶部 20dp
 * - 气泡沿用其它预设的非对称 6/18
 */
val claudeShapeTokens: AppShapeTokens = AppShapeTokens(
    card = RoundedCornerShape(16.dp),
    sheetTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    chip = RoundedCornerShape(12.dp),
    chipSmall = RoundedCornerShape(8.dp),
    chipSmallRadius = 8.dp,
    menu = RoundedCornerShape(12.dp),
    capsule = RoundedCornerShape(50),
    fab = RoundedCornerShape(50),
    heroCard = RoundedCornerShape(20.dp),
    bubble = RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp
    )
)

/**
 * CLAUDE 间距 tokens：更宽松的页边距与卡片内边距（20/12/20/16），
 * 行高 56dp、触控 48dp、图标 chip 44dp。
 */
val claudeSpacingTokens: AppSpacingTokens = AppSpacingTokens(
    pageHorizontal = 20.dp,
    cardGap = 12.dp,
    listGap = 20.dp,
    cardInner = 16.dp,
    rowMinHeight = 56.dp,
    touchMin = 48.dp,
    chipIcon = 44.dp,
    fab = 56.dp,
    navBarHorizontal = 16.dp,
    navBarBottom = 12.dp
)

/**
 * CLAUDE 字阶 tokens：页面标题 28sp、行标题 18sp、正文 15sp、辅助 12–13sp。
 */
val claudeTypeTokens: AppTypeTokens = AppTypeTokens(
    bigNumber = 28.sp,
    hero = 34.sp,
    sectionTitle = 18.sp,
    timeLabel = 12.sp,
    badge = 11.sp,
    pageTitle = 28.sp,
    rowTitle = 18.sp,
    body = 15.sp,
    caption = 13.sp,
    hint = 12.sp
)

/**
 * 书卷主题分组卡底色：浅色暖米 #f3efe4、深色 #2f2e2c，对齐设计包 .group-card。
 */
@Composable
fun claudeGroupBg(): Color = if (LocalIsDarkTheme.current) Color(0xFF2F2E2C) else Color(0xFFF3EFE4)

/**
 * 书卷主题分组卡描边：0.5dp 浅边框，对齐设计包 border-200。
 */
@Composable
fun claudeGroupBorder(): Color = if (LocalIsDarkTheme.current) Color(0x14FFFFFF) else Color(0xFFE3E0D4)

/**
 * CLAUDE 专属 Material3 Typography。
 *
 * - 标题（displayLarge / displayMedium / displaySmall / headlineLarge）= Newsreader 衬线，
 *   对齐设计系统 display 28–34sp / 600
 * - UI（headlineMedium 以下）= Poppins，卡片标题 18sp/600、正文 14–15sp、辅助 12–13sp
 * - 阅读正文 bodyLarge = Lora 阅读衬线 16sp
 *
 * 变量字体（Newsreader / Lora）按设计系统说明用显式 FontWeight 加载：CMP 会把声明的字重
 * 交给平台字体引擎（Android Typeface weight / Skia wght 轴），若某平台未应用 wght 轴，
 * 只会退化成该字体文件默认实例的字重（观感略轻），不会运行时报错。
 * Geist Mono（等宽）本版未接入：需要等宽的地方目前都用系统等宽，接入会额外增加字体加载面，
 * 按设计系统说明「可选」处理，后续需要时再补 FontFamily。
 */
/**
 * UI 无衬线字族（Poppins 四档静态字重）——供 Claude 主题页面自定义排版直接复用。
 */
@Composable
fun claudeUiSans(): FontFamily = FontFamily(
    Font(Res.font.Poppins_Regular, FontWeight.Normal),
    Font(Res.font.Poppins_Medium, FontWeight.Medium),
    Font(Res.font.Poppins_SemiBold, FontWeight.SemiBold),
    Font(Res.font.Poppins_Bold, FontWeight.Bold)
)

/** 展示衬线字族（Newsreader 变量字体，标题统一 600 档）。 */
@Composable
fun claudeDisplaySerif(): FontFamily = FontFamily(
    Font(Res.font.Newsreader_Variable, FontWeight.SemiBold)
)

/** 阅读衬线字族（Lora 变量字体，正文 400 档）。 */
@Composable
fun claudeReadingSerif(): FontFamily = FontFamily(
    Font(Res.font.Lora_Variable, FontWeight.Normal)
)

@Composable
fun claudeTypography(): Typography {
    val uiSans = claudeUiSans()
    val displaySerif = claudeDisplaySerif()
    val readingSerif = claudeReadingSerif()

    return remember(uiSans, displaySerif, readingSerif) {
    Typography(
        displayLarge = TextStyle(
            fontFamily = displaySerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 34.sp,
            lineHeight = 42.sp,
            letterSpacing = 0.sp
        ),
        displayMedium = TextStyle(
            fontFamily = displaySerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            lineHeight = 38.sp,
            letterSpacing = 0.sp
        ),
        displaySmall = TextStyle(
            fontFamily = displaySerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = displaySerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = uiSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            letterSpacing = 0.sp
        ),
        // 弹窗标题槽（M3 AlertDialog 默认吃 headlineSmall）：20sp SemiBold，与基线观感一致
        headlineSmall = TextStyle(
            fontFamily = uiSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = uiSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = uiSans,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp
        ),
        titleSmall = TextStyle(
            fontFamily = uiSans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = readingSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.2.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = uiSans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp
        ),
        bodySmall = TextStyle(
            fontFamily = uiSans,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelLarge = TextStyle(
            fontFamily = uiSans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp
        ),
        labelMedium = TextStyle(
            fontFamily = uiSans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.1.sp
        ),
        labelSmall = TextStyle(
            fontFamily = uiSans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.1.sp
        )
    )
    }
}
