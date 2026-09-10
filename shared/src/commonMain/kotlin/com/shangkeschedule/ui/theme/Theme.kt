package com.shangkeschedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
 * 当前 App 主题预设（通透 / 书卷）。
 * 供课表课程块、设置页、今日页等 UI 层直接读取，避免通过样式参数反推预设。
 */
val LocalThemePreset = staticCompositionLocalOf { AppThemePreset.default }

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
    // 例外：通透（IOS）主色锁定 systemBlue、书卷（CLAUDE）主色锁定赤陶 brand-500，
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
        // 动效全局注入：用户在「个性化显示 → 动画效果」里选的风格 + 分组开关，
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
 * 核心主题实现函数（跨平台通用）。
 *
 * 主题分流：
 * - 通透（IOS）：ColorScheme 直接取 [iosLightColorScheme] / [iosDarkColorScheme]，
 *   **不经过 MaterialKolor 派生**（Expressive 风格会对种子色做色相旋转，systemBlue 会被
 *   派生成绿色系 primary）；色板 / 圆角 / 间距 / 字阶全部走 `IosStyle.kt`，
 *   排版用 SF 字阶（[iosTypography]）。
 * - 书卷（CLAUDE）：ColorScheme 取 [claudeLightColorScheme] / [claudeDarkColorScheme]，
 *   色板 / token 走 `ClaudeStyle.kt`，排版用 Poppins / Newsreader / Lora（[claudeTypography]）。
 * - 兜底分支：仅当未来新增预设时落到 MaterialKolor 派生 + v2 基线 token。
 */
@Composable
fun ShangKeScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    customLightPrimary: Color = DefaultThemeColor,
    customDarkPrimary: Color = DefaultThemeColor,
    themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    themePreset: AppThemePreset = AppThemePreset.default,
    content: @Composable () -> Unit
) {
    val isClaude = themePreset == AppThemePreset.CLAUDE
    val colorScheme = when {
        themePreset == AppThemePreset.IOS -> if (darkTheme) iosDarkColorScheme() else iosLightColorScheme()
        isClaude -> if (darkTheme) claudeDarkColorScheme() else claudeLightColorScheme()
        else -> rememberColorScheme(
            darkTheme = darkTheme,
            dynamicColor = dynamicColor,
            customLightPrimary = customLightPrimary,
            customDarkPrimary = customDarkPrimary
        )
    }

    // 结构性颜色映射：页面底 / 卡片底等映射进 ColorScheme，让所有 Scaffold /
    // TopAppBar / BottomSheet / Dialog 无需逐页修改即向当前主题收敛。
    //
    // primary / primarySoft / 渐变按 ColorScheme 实际主色同步，避免组件层
    // appColors().primary 与 M3 colorScheme.primary 同屏分裂。
    val styledTokens = if (isClaude) appColorTokens(darkTheme, themePreset) else iosAppColorTokens(darkTheme)
    val syncedTokens = styledTokens.copy(
        primary = colorScheme.primary,
        primarySoft = colorScheme.primaryContainer,
        gradientStart = colorScheme.primary,
        gradientEnd = colorScheme.primary.copy(alpha = 0.82f),
        navSelectedBg = colorScheme.primary.copy(alpha = if (darkTheme) 0.22f else 0.14f)
    )
    val styledScheme = colorScheme.withAppSurfaces(syncedTokens)

    // 主题化形状 / 间距 / 字阶 tokens（通透走 iOS 26，书卷走设计系统，其余走兜底基线）
    val shapeTokens = appShapeTokens(themePreset)
    val spacingTokens = appSpacingTokens(themePreset)
    val typeTokens = appTypeTokens(themePreset)

    // 字阶：书卷走 Poppins / Newsreader / Lora 专属字体族，通透走 SF 字阶系统无衬线
    val typography = if (isClaude) claudeTypography() else iosTypography()

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
        LocalAppColorTokens provides syncedTokens,
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
 * 平台特定的配色生成声明（保留给未来可能恢复的自定义主色能力）。
 */
@Composable
expect fun rememberColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    customLightPrimary: Color,
    customDarkPrimary: Color
): ColorScheme

/**
 * 共享的 MaterialKolor 动态配色方案生成函数。
 *
 * ⚠️ 通透（iOS 26）主题**不使用**它：Expressive 风格会对种子色做色相旋转，
 * systemBlue 会被派生成绿色系 primary，破坏 iOS 系统色的身份一致性。
 * 保留此函数仅供平台侧 [rememberColorScheme] 的既有实现与后续扩展使用。
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
