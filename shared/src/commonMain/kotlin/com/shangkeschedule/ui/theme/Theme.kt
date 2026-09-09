package com.shangkeschedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.AppThemeMode
import com.shangkeschedule.data.model.AppThemePreset

/**
 * 定义一个用于全局同步深色模式状态的 Local 变量
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * 当前 App 主题预设（经典 / 云舒 / 利落）。
 * 供课表课程块等 UI 层直接读取，避免通过样式参数反推预设。
 */
val LocalThemePreset = staticCompositionLocalOf { AppThemePreset.ORIGINAL }

/**
 * 外部调用的快捷主题函数
 * 自动根据 AppSettingsModel 处理所有主题逻辑
 */
@Composable
fun ShangKeScheduleTheme(
    settings: AppSettingsModel,
    content: @Composable () -> Unit
) {
    val darkTheme = when (settings.themeMode) {
        AppThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    // 配色优先级：动态取色 > 当前模式的自定义主色 > 主题预设种子色
    // 浅色和深色主色独立保存，必须按当前模式检查对应字段；否则深色模式修改
    // customDarkPrimary 时仍会因 customLightPrimary 未变化而回退到预设色。
    // 例外：iOS 预设主色锁定 systemBlue，CLAUDE 预设主色锁定赤陶 brand-500，
    // 两者都不跟随动态取色 / 自定义主色。
    val defaultPrimaryArgb = DefaultThemeColor.toArgb().toLong()
    val currentCustomPrimary = if (darkTheme) settings.customDarkPrimary else settings.customLightPrimary
    val seedColor: Color? = when {
        settings.themePreset == AppThemePreset.IOS -> settings.themePreset.seedColor
        settings.themePreset == AppThemePreset.CLAUDE -> settings.themePreset.seedColor
        settings.useDynamicColor && supportsDynamicColor -> null
        currentCustomPrimary != defaultPrimaryArgb -> Color(currentCustomPrimary)
        else -> settings.themePreset.seedColor
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalThemePreset provides settings.themePreset,
        // 玻璃雾度全局注入：用户在「个性化显示」里设定的一个值，喂给所有悬浮玻璃件
        LocalGlassBlurRadius provides settings.glassBlurRadiusDp.dp,
        // 动效全局注入（v3.26.0）：用户在「个性化显示 → 动画效果」里选的风格 + 分组开关，
        // 解析成一套令牌喂给全 App 动画；改一次全端同步，不再各处写死时长
        LocalAppMotion provides resolveMotion(settings.animationStyle, settings.disabledAnimationGroups)
    ) {
        ShangKeScheduleTheme(
            darkTheme = darkTheme,
            dynamicColor = settings.useDynamicColor && seedColor == null,
            customLightPrimary = seedColor ?: DefaultThemeColor,
            customDarkPrimary = seedColor ?: DefaultThemeColor,
            themeMode = settings.themeMode,
            themePreset = settings.themePreset,
            content = content
        )
    }
}

/**
 * 核心主题实现函数（跨平台通用）
 */
@Composable
fun ShangKeScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    customLightPrimary: Color = DefaultThemeColor,
    customDarkPrimary: Color = DefaultThemeColor,
    themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    themePreset: AppThemePreset = AppThemePreset.ORIGINAL,
    content: @Composable () -> Unit
) {
    // iOS 预设：锁定 Apple 系统色系，绕过 MaterialKolor Expressive 的色相旋转
    // （Expressive 会对种子色做 hue 旋转，systemBlue #007AFF 会被派生成绿色系 primary）
    // CLAUDE 预设：同样锁定 Anthropic/Claude 设计系统色板，不跟随动态取色 / 自定义主色
    val colorScheme = if (themePreset == AppThemePreset.IOS) {
        if (darkTheme) iosDarkColorScheme() else iosLightColorScheme()
    } else if (themePreset == AppThemePreset.CLAUDE) {
        if (darkTheme) claudeDarkColorScheme() else claudeLightColorScheme()
    } else {
        rememberColorScheme(
            darkTheme = darkTheme,
            dynamicColor = dynamicColor,
            customLightPrimary = customLightPrimary,
            customDarkPrimary = customDarkPrimary
        )
    }

    // 全局风格对齐（v2 规范 §2）：页面底色/卡片白等结构性颜色统一映射，
    // 让所有 Scaffold / TopAppBar / BottomSheet / Dialog 无需逐页修改即向基线收敛。
    //
    // 主色同步（v2 规范修订）：token 的 primary/primarySoft/渐变 必须跟随
    // colorScheme 实际主色（动态取色 / 用户自定义 / 主题预设种子色派生），
    // 否则组件层 appColors().primary 与 M3 colorScheme.primary 同屏分裂
    // （如 FAB 紫 vs 网格今日高亮动态棕橙）。Hero 渐变跟随主色，保证全局同源。
    // 例外：iOS 预设保留 ios tokens 原生 systemBlue（ColorScheme 已锁定同色）。
    val styledTokens = appColorTokens(darkTheme, themePreset).let { tokens ->
        tokens.copy(
            primary = colorScheme.primary,
            primarySoft = colorScheme.primaryContainer,
            gradientStart = colorScheme.primary,
            gradientEnd = colorScheme.primary.copy(alpha = 0.82f),
            navSelectedBg = colorScheme.primary.copy(alpha = if (darkTheme) 0.22f else 0.14f)
        )
    }
    val styledScheme = colorScheme.withAppSurfaces(styledTokens)

    // 主题化形状 / 间距 / 字阶 tokens（通透主题走 Apple HIG 变体，其余走默认基线）
    val shapeTokens = appShapeTokens(themePreset)
    val spacingTokens = appSpacingTokens(themePreset)
    val typeTokens = appTypeTokens(themePreset)

    // 字阶：CLAUDE 预设走 Poppins / Newsreader / Lora 专属字体族，其余沿用全局基线 Typography
    val typography = if (themePreset == AppThemePreset.CLAUDE) claudeTypography() else Typography

    // 按主题预设构建 MaterialTheme Shapes（让 M3 内置组件同步圆角）
    val materialShapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = shapeTokens.chipSmall,
        medium = shapeTokens.chip,
        large = shapeTokens.card,
        extraLarge = shapeTokens.heroCard
    )

    // 组件层 appColors() / appShapes() / appSpacing() / appType() 读同一份同步 token
    CompositionLocalProvider(
        LocalAppColorTokens provides styledTokens,
        LocalAppShapeTokens provides shapeTokens,
        LocalAppSpacingTokens provides spacingTokens,
        LocalAppTypeTokens provides typeTokens
    ) {
        // 应用平台特定的窗口与系统栏外观控制
        SetupPlatformThemeEffects(
            colorScheme = styledScheme,
            darkTheme = darkTheme,
            themeMode = themeMode
        )

        MaterialTheme(
            colorScheme = styledScheme,
            typography = typography,
            shapes = materialShapes,
            content = content
        )
    }
}

/**
 * iOS 预设锁定的 M3 ColorScheme（浅色）。
 * 绕过 MaterialKolor 派生，primary 严格锁定 systemBlue；
 * 其余容器色对齐 Apple 系统色系，结构色由 withAppSurfaces 统一映射。
 */
internal fun iosLightColorScheme(): ColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),          // systemBlue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5F0FF), // systemBlue 淡底
    onPrimaryContainer = Color(0xFF0A3C7A),
    secondary = Color(0xFF5856D6),        // systemIndigo
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDEEFB),
    onSecondaryContainer = Color(0xFF1B1B4B),
    tertiary = Color(0xFF34C759),         // systemGreen
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9F9EE),
    onTertiaryContainer = Color(0xFF0B3D1D),
    error = Color(0xFFFF3B30),            // systemRed
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE5E2),
    onErrorContainer = Color(0xFF5C100A)
)

/**
 * iOS 预设锁定的 M3 ColorScheme（深色）。
 */
internal fun iosDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),          // systemBlue (dark)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0A3C7A),
    onPrimaryContainer = Color(0xFFE5F0FF),
    secondary = Color(0xFF5E5CE6),        // systemIndigo (dark)
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF2A2A5E),
    onSecondaryContainer = Color(0xFFEDEDFA),
    tertiary = Color(0xFF30D158),         // systemGreen (dark)
    onTertiary = Color(0xFF00210C),
    tertiaryContainer = Color(0xFF0B3D1D),
    onTertiaryContainer = Color(0xFFE9F9EE),
    error = Color(0xFFFF453A),            // systemRed (dark)
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF5C100A),
    onErrorContainer = Color(0xFFFFE5E2)
)

/**
 * 共享的 MaterialKolor 动态配色方案生成函数
 */
@Composable
fun rememberMaterialKolorScheme(
    darkTheme: Boolean,
    seedColor: Color,
    style: PaletteStyle = PaletteStyle.Expressive
): ColorScheme {
    return rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = darkTheme,
        style = style
    )
}

/**
 * 平台特定的配色生成声明
 */
@Composable
expect fun rememberColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    customLightPrimary: Color,
    customDarkPrimary: Color
): ColorScheme

/**
 * 平台特定的窗口与系统栏外观控制声明
 */
@Composable
expect fun SetupPlatformThemeEffects(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
    themeMode: AppThemeMode
)

/**
 * 平台特定的能力：当前系统/平台是否支持 Dynamic Color (动态取色)
 */
expect val supportsDynamicColor: Boolean