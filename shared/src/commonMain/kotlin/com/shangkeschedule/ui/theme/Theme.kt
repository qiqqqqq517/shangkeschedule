package com.shangkeschedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import com.shangkeschedule.ui.theme.LocalAppColorTokens
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

    // 配色优先级：动态取色 > 自定义主色 > 主题预设种子色
    val seedColor: Color? = when {
        settings.useDynamicColor && supportsDynamicColor -> null
        settings.customLightPrimary != DefaultThemeColor.toArgb().toLong() ->
            Color(if (darkTheme) settings.customDarkPrimary else settings.customLightPrimary)
        else -> settings.themePreset.seedColor
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalThemePreset provides settings.themePreset
    ) {
        ShangKeScheduleTheme(
            darkTheme = darkTheme,
            dynamicColor = settings.useDynamicColor && seedColor == null,
            customLightPrimary = seedColor ?: DefaultThemeColor,
            customDarkPrimary = seedColor ?: DefaultThemeColor,
            themeMode = settings.themeMode,
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
    content: @Composable () -> Unit
) {
    val colorScheme = rememberColorScheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        customLightPrimary = customLightPrimary,
        customDarkPrimary = customDarkPrimary
    )

    // 全局风格对齐（v2 规范 §2）：页面底色/卡片白等结构性颜色统一映射，
    // 让所有 Scaffold / TopAppBar / BottomSheet / Dialog 无需逐页修改即向基线收敛。
    //
    // 主色同步（v2 规范修订）：token 的 primary/primarySoft/渐变 必须跟随
    // colorScheme 实际主色（动态取色 / 用户自定义 / 主题预设种子色派生），
    // 否则组件层 appColors().primary 与 M3 colorScheme.primary 同屏分裂
    // （如 FAB 紫 vs 网格今日高亮动态棕橙）。Hero 渐变跟随主色，保证全局同源。
    val styledTokens = appColorTokens(darkTheme).let { tokens ->
        tokens.copy(
            primary = colorScheme.primary,
            primarySoft = colorScheme.primaryContainer,
            gradientStart = colorScheme.primary,
            gradientEnd = colorScheme.primary.copy(alpha = 0.82f)
        )
    }
    val styledScheme = colorScheme.withAppSurfaces(styledTokens)

    // 组件层 appColors() 读同一份同步 token（否则组件 primary 与 M3 primary 分裂）
    CompositionLocalProvider(LocalAppColorTokens provides styledTokens) {
        // 应用平台特定的窗口与系统栏外观控制
        SetupPlatformThemeEffects(
            colorScheme = styledScheme,
            darkTheme = darkTheme,
            themeMode = themeMode
        )

        MaterialTheme(
            colorScheme = styledScheme,
            typography = Typography,
            shapes = AppMaterialShapes,
            content = content
        )
    }
}

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