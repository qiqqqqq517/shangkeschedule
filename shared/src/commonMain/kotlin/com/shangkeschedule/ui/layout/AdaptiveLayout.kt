package com.shangkeschedule.ui.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * 窗口宽度档（平板适配 · v4.64.0）。
 *
 * 断点沿用 Material 3 的窗口宽度分档，但**判定不依赖 `material3-window-size-class`**
 * —— 全仓只有 `material3-adaptive`（导航栏 Rail/Bar 切换）与 `material3-adaptive-navigation-suite`，
 * 没有 window-size-class 依赖；这里改为直接读 `LocalWindowInfo.current.containerSize`
 * （与本仓 `AppearanceSettingsScreen` 取屏幕宽高比的同款写法），
 * 行为一致且天然覆盖 Android 分屏 / 多窗口（containerSize 就是实际窗口尺寸）。
 *
 * | 档位 | 宽度 | 典型设备 |
 * |---|---|---|
 * | [COMPACT] | < 600dp | 手机（竖屏 / 横屏）、桌面 430×932 预览宿主 |
 * | [MEDIUM] | 600–839dp | 8/10 寸平板**竖屏**、折叠屏展开、分屏半屏、桌面 800×600 默认窗口 |
 * | [EXPANDED] | ≥ 840dp | 平板横屏、13 寸平板任意方向、桌面大窗 |
 */
enum class AppWidthClass { COMPACT, MEDIUM, EXPANDED }

/** Medium 档下限（dp）。 */
private const val MEDIUM_MIN_DP = 600

/** Expanded 档下限（dp）。 */
private const val EXPANDED_MIN_DP = 840

/**
 * 自适应布局 tokens —— 屏幕宽度档驱动的一整套「布局口径」。
 *
 * ⚠️ 与主题 token（[com.shangkeschedule.ui.theme.AppSpacingTokens] 等）是**两个维度**：
 * 主题 token 回答「书卷 / 柔绘 / 通透各是什么数值」，本组回答「当前窗口有多宽、该按什么口径排布」。
 * 因此分支条件是**窗口宽度**，不是主题身份 —— 不触碰 `scripts/check_theme_leak.py` 的门禁
 * （门禁只禁 `ui/` 下按主题身份分支）。
 *
 * [contentMaxWidth] / [dialogMaxWidth] / [sheetMaxWidth] 为 [Dp.Unspecified] 时表示**不限宽**
 * （Compact 档：与改动前完全一致，手机端零差异）。
 */
data class AppLayoutTokens(
    val widthClass: AppWidthClass,
    /** 页面内容列最大宽度（卡片 / 列表 / 表单）。 */
    val contentMaxWidth: Dp,
    /** 居中弹窗最大宽度。 */
    val dialogMaxWidth: Dp,
    /** 底部面板最大宽度（透传给 M3 `ModalBottomSheet.sheetMaxWidth`）。 */
    val sheetMaxWidth: Dp,
    /** 字阶缩放系数（作用于 `AppTypeTokens`）。 */
    val typeScale: Float,
    /** 图标尺寸缩放系数（作用于 `AppIconTokens`）。 */
    val iconScale: Float,
    /** 纵向节奏 + 页边距缩放系数（作用于 `AppSpacingTokens` 的节奏类字段）。 */
    val spacingScale: Float
) {
    /** 是否处于宽屏（平板 / 桌面大窗 / 分屏宽半屏）。 */
    val isWide: Boolean get() = widthClass != AppWidthClass.COMPACT
}

/** Compact 档：一切不限宽、系数全 1 —— 与平板适配前的表现逐像素一致。 */
private val CompactLayoutTokens = AppLayoutTokens(
    widthClass = AppWidthClass.COMPACT,
    contentMaxWidth = Dp.Unspecified,
    dialogMaxWidth = Dp.Unspecified,
    sheetMaxWidth = Dp.Unspecified,
    typeScale = 1f,
    iconScale = 1f,
    spacingScale = 1f
)

/**
 * Medium 档（600–839dp）：内容限宽 640dp。
 *
 * 640dp 不是新发明的数值 —— 仓库里 11 处二级页（设置 / 备份 / 学期 / 时间表 / 课表管理 /
 * 资料 / 导入中心）早已约定「宽屏内容限宽 640dp 居中」，本档只是把这条约定提升为全局口径。
 */
private val MediumLayoutTokens = AppLayoutTokens(
    widthClass = AppWidthClass.MEDIUM,
    contentMaxWidth = 640.dp,
    dialogMaxWidth = 480.dp,
    sheetMaxWidth = 560.dp,
    typeScale = 1.06f,
    iconScale = 1.10f,
    spacingScale = 1f
)

/**
 * Expanded 档（≥840dp）：内容限宽 720dp，字号 +10%、图标 +15%、纵向节奏 +12%。
 *
 * 已知代价：本轮明确不做多列 / 双栏（已与用户确认），因此 13 寸平板横屏（约 1707dp）
 * 两侧会各留约 490dp 空白 —— 这是「不分栏」的既定取舍，不是缺陷。
 */
private val ExpandedLayoutTokens = AppLayoutTokens(
    widthClass = AppWidthClass.EXPANDED,
    contentMaxWidth = 720.dp,
    dialogMaxWidth = 520.dp,
    sheetMaxWidth = 600.dp,
    typeScale = 1.10f,
    iconScale = 1.15f,
    spacingScale = 1.12f
)

/** CompositionLocal：布局 token（默认 Compact —— 无窗口信息时等价于手机）。 */
val LocalAppLayoutTokens = staticCompositionLocalOf { CompactLayoutTokens }

/** 组件层快捷访问布局 token。 */
@Composable
fun appLayout(): AppLayoutTokens = LocalAppLayoutTokens.current

/**
 * 由当前窗口尺寸推出布局 token。
 *
 * 读 `LocalWindowInfo.current.containerSize`（像素）经 `LocalDensity` 换算为 dp ——
 * 与 `AppearanceSettingsScreen` 取屏幕宽高比同款写法，且天然支持分屏 / 多窗口 / 旋转。
 */
@Composable
fun rememberAppLayoutTokens(): AppLayoutTokens {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val widthDp = with(density) { containerSize.width.toDp() }
    return rememberAppLayoutTokensForWidth(widthDp)
}

/** 纯函数分档：给定窗口宽度（dp）返回对应档位的 token。便于预览与单测。 */
fun rememberAppLayoutTokensForWidth(widthDp: Dp): AppLayoutTokens = when {
    widthDp < MEDIUM_MIN_DP.dp -> CompactLayoutTokens
    widthDp < EXPANDED_MIN_DP.dp -> MediumLayoutTokens
    else -> ExpandedLayoutTokens
}

/** 当前窗口宽度档。 */
@Composable
fun rememberAppWidthClass(): AppWidthClass = rememberAppLayoutTokens().widthClass

/**
 * 内容列限宽：Compact 档等价于 `fillMaxWidth()`（零差异），宽屏档叠加上限。
 *
 * ⚠️ 本修饰符**只管宽度、不管居中**。居中由容器的 `horizontalAlignment` 承担 ——
 * 与设置页既有写法（`LazyColumn(horizontalAlignment = CenterHorizontally)` +
 * 子项 `widthIn(max = 640.dp)`）保持一致，语义明确、无 `wrapContentWidth` 的顺序陷阱。
 * 取居中对齐值用 [appContentAlignment]。
 */
@Composable
fun Modifier.appContentWidth(): Modifier {
    val max = appLayout().contentMaxWidth
    return if (max == Dp.Unspecified) {
        this.fillMaxWidth()
    } else {
        // ⚠️ 顺序不可颠倒：必须**先** widthIn 再 fillMaxWidth。
        // 若写成 fillMaxWidth().widthIn(max)，fillMaxWidth 已把 minWidth 顶到父容器宽度，
        // 随后 widthIn 的 max 会被 coerceIn(minWidth, …) 夹回父宽 ⇒ 限宽**静默失效**。
        this.widthIn(max = max).fillMaxWidth()
    }
}

/**
 * 内容列的水平对齐方式：窄屏 `Start`（= 改动前），宽屏 `CenterHorizontally`（内容居中）。
 * 供 `Column` / `LazyColumn` 的 `horizontalAlignment` 直接取值。
 */
@Composable
fun appContentAlignment(): Alignment.Horizontal =
    if (appLayout().contentMaxWidth == Dp.Unspecified) Alignment.Start else Alignment.CenterHorizontally

/**
 * 宽屏弹窗的**最小**宽度（dp）。
 *
 * 关掉 `usePlatformDefaultWidth` 后窗口变 wrap_content，短文案弹窗（如「确定删除？」）
 * 会被压得很窄；给一个 280dp 下限（与 M3 AlertDialog 自身的 DialogMinWidth 同量级）保证形态稳定。
 */
private val DIALOG_MIN_WIDTH_WIDE = 280.dp

/**
 * 弹窗内容宽度约束：宽屏 280dp .. [AppLayoutTokens.dialogMaxWidth]，窄屏不加任何约束。
 *
 * 用法：`Surface(modifier = Modifier.appDialogWidth())`。窄屏返回 [Modifier] ⇒ 手机端零差异。
 */
@Composable
fun Modifier.appDialogWidth(): Modifier {
    val layout = appLayout()
    return if (layout.isWide) {
        this.widthIn(min = DIALOG_MIN_WIDTH_WIDE, max = layout.dialogMaxWidth)
    } else {
        this
    }
}

/**
 * 弹窗窗口属性：宽屏关掉 `usePlatformDefaultWidth`（改由 [appDialogWidth] 定宽并居中），
 * 窄屏沿用 M3 默认 —— 窄屏返回的就是默认 `DialogProperties()`，手机端零差异。
 */
@Composable
fun rememberDialogPropertiesWidth(): DialogProperties {
    val layout = appLayout()
    return remember(layout.isWide) {
        DialogProperties(usePlatformDefaultWidth = !layout.isWide)
    }
}

/**
 * 字阶缩放系数（供散落的硬编码 `fontSize = N.sp` 微标签放大用）。
 * Compact 档恒为 1f ⇒ 手机端零差异；用法：`fontSize = 9.sp * appTypeScale()`。
 */
@Composable
fun appTypeScale(): Float = appLayout().typeScale
