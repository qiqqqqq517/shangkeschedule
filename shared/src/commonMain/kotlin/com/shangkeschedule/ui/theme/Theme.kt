package com.shangkeschedule.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.AppThemeMode
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.ui.glass.GlassRefractionSettings
import com.shangkeschedule.ui.glass.LocalGlassRefraction

/**
 * 定义一个用于全局同步深色模式状态的 Local 变量
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * 当前 App 主题预设（通透 / 柔绘 / 书卷）。
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

    // 三套主题预设各自锁定身份主色（通透 systemBlue / 柔绘雾蓝紫 / 书卷赤陶 brand-500），
    // 主色不再由用户的自定义色或系统动态取色推导——原「自定义主色调 + 动态取色」能力已删除。
    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalThemePreset provides settings.themePreset,
        LocalIsSoftTheme provides (settings.themePreset == AppThemePreset.SOFT),
        // 玻璃雾度全局注入：用户在「个性化显示」里设定的一个值，喂给所有悬浮玻璃件
        LocalGlassBlurRadius provides settings.glassBlurRadiusDp.dp,
        // 液态折射配置全局注入（v3.47.0）。**所有**液态玻璃件——底栏 LiquidGlassTabs 与
        // FAB / 圆钮 / 挂起条（liquidGlass）——都读这一份配置：开启时统一按用户设定的
        // 高度 / 强度 / 色散做边缘折射，关闭时底栏回落参考实现的默认折射；
        // 模糊半径始终读上面的 LocalGlassBlurRadius。
        LocalGlassRefraction provides GlassRefractionSettings(
            enabled = settings.glassRefractionEnabled,
            heightDp = settings.glassRefractionHeightDp,
            amountDp = settings.glassRefractionAmountDp,
            dispersion = settings.glassRefractionDispersion,
            depthEffect = settings.glassRefractionDepthEffect
        ),
        // 动效全局注入：用户在「个性化显示 → 动画效果」里选的风格 + 分组开关 + 主题预设
        // （主题决定动效语言：阻尼 / 时长 / 曲线 / 按压形态按主题分档，v3.43.0），
        // 解析成一套令牌喂给全 App 动画；改一次全端同步，不再各处写死时长
        LocalAppMotion provides resolveMotion(
            style = settings.animationStyle,
            disabledGroups = settings.disabledAnimationGroups,
            preset = settings.themePreset,
            reduceMotion = settings.reduceMotionEnabled,
            speed = settings.motionSpeed,
        )
    ) {
        // 主题/深浅切换为原位重组（终审 P1：曾试以 (深浅, 预设) 为 key 包 Crossfade，
        // 但它为每个 key 组建全新分支组合，会连同 rememberNavBackStack 一起重建，
        // 用户被弹回起始页并丢失返回栈——含导航状态的 content 严禁以主题为 key 重建）
        ShangKeScheduleTheme(
            darkTheme = darkTheme,
            themeMode = settings.themeMode,
            themePreset = settings.themePreset,
            content = content
        )
    }
}

/**
 * 核心主题实现函数（跨平台通用）。
 *
 * 三套预设各自提供完整 ColorScheme，互不派生：
 * - 通透（IOS）：ColorScheme 取 [iosLightColorScheme] / [iosDarkColorScheme]（systemBlue 为身份色，
 *   不做任何色相旋转派生）；token 走 `IosStyle.kt`，排版 SF 字阶（[iosTypography]）。
 * - 柔绘（SOFT）：ColorScheme 取 [softLightColorScheme] / [softDarkColorScheme]；token 走
 *   `SoftStyle.kt`（虚化大圆角 / 干净留白 / 轻字重），材质走 softWash / softSurface 系列。
 * - 书卷（CLAUDE）：ColorScheme 取 [claudeLightColorScheme] / [claudeDarkColorScheme]，
 *   色板 / token 走 `ClaudeStyle.kt`，排版用 Poppins / Newsreader / Lora（[claudeTypography]）。
 *
 * [themePreset] 为穷尽 when：新增预设时编译器会强制补齐配色方案，不再有隐藏兜底分支。
 */
@Composable
fun ShangKeScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    themePreset: AppThemePreset = AppThemePreset.default,
    content: @Composable () -> Unit
) {
    val isClaude = themePreset == AppThemePreset.CLAUDE
    val isSoft = themePreset == AppThemePreset.SOFT
    val colorScheme = when (themePreset) {
        AppThemePreset.IOS -> if (darkTheme) iosDarkColorScheme() else iosLightColorScheme()
        AppThemePreset.SOFT -> if (darkTheme) softDarkColorScheme() else softLightColorScheme()
        AppThemePreset.CLAUDE -> if (darkTheme) claudeDarkColorScheme() else claudeLightColorScheme()
    }

    // 结构性颜色映射：页面底 / 卡片底等映射进 ColorScheme，让所有 Scaffold /
    // TopAppBar / BottomSheet / Dialog 无需逐页修改即向当前主题收敛。
    //
    // primary / primarySoft / 渐变按 ColorScheme 实际主色同步，避免组件层
    // appColors().primary 与 M3 colorScheme.primary 同屏分裂。
    // A1/P2：统一取 token 入口 —— 一律走 appColorTokens(isDark, preset) 这**一条**路径（规范 R4）。
    // 原先是三套写法：书卷走 appColorTokens()、柔绘直调 softAppColorTokens()、通透直调 iosAppColorTokens()。
    // 已核对等价：appColorTokens 的分派（AppStyle.kt）正是转调这两个函数，而
    // `xxxAppColorTokens(isDark) = if (isDark) xxxDarkAppColorTokens() else xxxLightAppColorTokens()`
    // 与分派结果逐项相同 ⇒ 本改为**行为等价、视觉零变化**。
    val styledTokens = appColorTokens(darkTheme, themePreset)
    val syncedTokens = styledTokens.copy(
        primary = colorScheme.primary,
        primarySoft = colorScheme.primaryContainer,
        gradientStart = colorScheme.primary,
        gradientEnd = colorScheme.primary.copy(alpha = 0.82f),
        navSelectedBg = colorScheme.primary.copy(alpha = if (darkTheme) 0.22f else 0.14f)
    )
    val styledScheme = colorScheme.withAppSurfaces(syncedTokens)

    // 主题化形状 / 间距 / 字阶 tokens（三套主题各一套，兜底走 iOS 26）
    val shapeTokens = appShapeTokens(themePreset)
    val spacingTokens = appSpacingTokens(themePreset)
    val typeTokens = appTypeTokens(themePreset)

    // 字阶：书卷走 Poppins/Newsreader/Lora，柔绘走轻字重柔绘字阶，通透走 SF 字阶
    val typography = when {
        isClaude -> claudeTypography()
        isSoft -> softTypography()
        else -> iosTypography()
    }

    // 按主题预设构建 MaterialTheme Shapes（让 M3 内置组件同步圆角）
    val materialShapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = shapeTokens.chipSmall,
        medium = shapeTokens.chip,
        large = shapeTokens.card,
        extraLarge = shapeTokens.heroCard
    )

    // 组件层 appColors() / appShapes() / appSpacing() / appType() 读同一份同步 token
    //
    // 全局触摸指示（v3.43.0）：按主题 provide LocalIndication，覆盖全站 Modifier.clickable /
    // combinedClickable / selectable 的默认 Material 涟漪——三套主题从「共用一套涟漪」
    // 变为各用各的语言（柔绘软边渗开 / 书卷底色加深 / 通透 HIG highlight）。
    val appMotion = LocalAppMotion.current
    val indicationColor = when (appMotion.profile.indicationStyle) {
        IndicationStyle.SOFT_RADIAL -> syncedTokens.primary.copy(alpha = appMotion.profile.indicationAlpha)
        IndicationStyle.COLOR_DARKEN, IndicationStyle.HIG_HIGHLIGHT -> if (darkTheme) {
            Color.White.copy(alpha = appMotion.profile.indicationAlpha)
        } else {
            Color.Black.copy(alpha = appMotion.profile.indicationAlpha)
        }
    }
    val indication = remember(
        appMotion.profile.indicationStyle,
        appMotion.profile.indicationAlpha,
        indicationColor,
        appMotion.tokens.touchExpandMs,
        appMotion.tokens.touchFadeMs,
        appMotion.tokens.touchEasing,
    ) {
        ThemeIndication(
            style = appMotion.profile.indicationStyle,
            color = indicationColor,
            expandMs = appMotion.tokens.touchExpandMs,
            fadeMs = appMotion.tokens.touchFadeMs,
            easing = appMotion.tokens.touchEasing,
        )
    }
    CompositionLocalProvider(
        LocalAppColorTokens provides syncedTokens,
        LocalAppShapeTokens provides shapeTokens,
        LocalAppSpacingTokens provides spacingTokens,
        LocalAppTypeTokens provides typeTokens,
        LocalIsSoftTheme provides isSoft,
        LocalIndication provides indication
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
 * 平台特定的窗口与系统栏外观控制声明
 */
@Composable
expect fun SetupPlatformThemeEffects(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
    themeMode: AppThemeMode
)
