package com.shangkeschedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangkeschedule.ui.theme.NavBarBottomEdge
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appNavBar
import com.shangkeschedule.ui.theme.appPageHeader
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import com.shangkeschedule.ui.theme.softFeatherRim
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 顶部导航栏（iOS 26）。App 只有「通透」一套主题，因此这里是唯一的导航栏实现：
 * 44pt（48dp）高度 + 居中 17sp SemiBold 标题 + 左侧返回钮 + 底部发丝分隔线，
 * 底衬 iOS 26 的导航栏玻璃（`.navigationBar` 材质 —— 半透明 + 背景模糊 + 极淡发丝线）。
 *
 * 与书卷主题的差别只在材质：书卷是透明栏 + 居中标题（无玻璃底衬），
 * 通透按 iOS 26 给导航栏真正的玻璃底衬与发丝线，位置、尺寸、标题字阶完全不变。
 *
 * [hazeState] 非空时启用真实背景模糊（需要调用方把主内容标为 hazeSource）；
 * 为 null 时退化为「半透明底 + 发丝线」，在无滚动的静态页面上观感一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    hazeState: HazeState? = null
) {
    if (hazeState != null) {
        AppNavigationBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            hazeState = hazeState,
            scrollBehavior = scrollBehavior
        )
    } else {
        // 无 haze 场景（静态页 / Dialog 内）：保留 M3 TopAppBar，但底色与分隔线按 iOS 26 收敛
        TopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
            windowInsets = windowInsets
        )
    }
}

/**
 * 页面页头（全局 UI 优化批 2，v3.71.0）：四个主页面共用的统一页头。
 *
 * 背景：此前四个主页面的页头形态**各不相同** —— 今日页无标题且靠
 * `padding(horizontal = 84.dp)` 硬编码避让居中日期；课表页顶栏塞 4 个控件；
 * 日程页用 `hero`(34sp) 当月份标题；我的页**仅通透有吸顶标题**（书卷 / 柔绘无标题，
 * A1 遗留缺陷）。统一为同一组件后，差异退化为参数，由 `AppPageHeaderTokens` 承载。
 *
 * 骨架（三主题位置与信息位逐项一致）：
 * ```
 * [statusBarsPadding + pageTop]
 * 左：页面标题（titleSize / titleWeight，textPrimary）        右：actions（≤2 枚）
 * [sectionTitleGap]
 * 下：副标题行（subtitleSize，textSecondary）＋ 可选 leading（周次胶囊 / 月份）
 * [bottomGap]
 * ```
 *
 * ⚠️ 本组件**不承担横向页边距**：调用方容器已带 `appSpacing().pageHorizontal`
 * （今日页父容器即如此）—— 此前 `TodayHeader` 又叠了 4dp，导致页头比卡片内缩 4dp 的错位，
 * 统一后自然消失。
 *
 * ⚠️ 本组件**也不自动加 `statusBarsPadding()`**：各页归属不同 —— 今日页父容器没有
 * （需调用方传 `Modifier.statusBarsPadding()`），日程页父容器 `Column` 已经加过
 * （再加会双份）。由调用方按实际情况传入。
 *
 * @param title 页面标题（页面名，用于定位，不是内容）
 * @param subtitle 副标题（日期 / 月份一类的次级信息）；为 null 则不渲染副标题行
 * @param leading 副标题行左侧的可点控件（周次胶囊 / 翻月胶囊）；为 null 则只渲染副标题
 */
@Composable
fun AppPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = appColors()
    val spacing = appSpacing()
    val type = appType()
    val header = appPageHeader()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = spacing.pageTop)
            .padding(bottom = header.bottomGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = header.titleSize,
                    fontWeight = header.titleWeight,
                    letterSpacing = header.titleLetterSpacing
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
        if (subtitle != null || leading != null) {
            Spacer(modifier = Modifier.height(spacing.sectionTitleGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leading != null) {
                    leading()
                    if (subtitle != null) Spacer(modifier = Modifier.width(spacing.sectionTitleGap))
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = header.subtitleSize,
                            fontWeight = type.captionWeight
                        ),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 导航栏本体：48dp 高 + 居中标题 + 主题化底衬。
 *
 * - 通透（iOS 26）：玻璃底衬（blur 20dp）+ 底部发丝线。
 * - 柔绘：更厚的薄雾模糊（blur 22dp）+ 更淡着色，且**没有发丝线**——
 *   柔绘要求「无锐利硬边缘」，下缘改为一条羽化渐变带，导航栏像薄雾一样化进内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppNavigationBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    hazeState: HazeState,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val tokens = appColors()
    // A1/V2（v3.69.0）：顶栏玻璃材质（模糊半径 / 色调 alpha / 标题字重字距 / 下缘形态）
    // 全部改由 `AppNavBarTokens` 提供，本组件不再做主题身份判断。
    val navBar = appNavBar()
    // v3.43.0（《交互动效审查》P2「顶栏滚动折叠」）：玻璃导航栏此前**直接丢弃**
    // scrollBehavior（该参数只在 M3 分支生效），因此「通透」这套自带玻璃顶栏的形态
    // 永远不折叠。现在按 collapsedFraction 插值：栏高 48→36dp、标题 17→15sp、
    // 底部发丝线同步淡出，与 M3 顶栏的折叠行为对齐。
    val collapsedFraction = scrollBehavior?.state?.collapsedFraction ?: 0f
    val barHeight = (48f - 12f * collapsedFraction).dp
    val barTitleSize = (17f - 2f * collapsedFraction).sp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .hazeEffect(hazeState) {
                blurRadius = navBar.glassBlurRadius
                noiseFactor = 0f
                tints = listOf(HazeTint(tokens.navBarBg.copy(alpha = navBar.glassTintAlpha)))
                fallbackTint = HazeTint(tokens.navBarBg)
                backgroundColor = Color.Transparent
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(barHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                navigationIcon()
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize = barTitleSize,
                        fontWeight = navBar.titleFontWeight,
                        letterSpacing = navBar.titleLetterSpacing
                    )
                ) { title() }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        when (navBar.bottomEdge) {
            NavBarBottomEdge.FEATHERED -> {
                // 柔绘下缘：羽化渐变（由极淡到透明），无硬线
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(10.dp)
                        .softFeatherRim(
                            androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                        )
                )
            }

            NavBarBottomEdge.HAIRLINE -> {
                // iOS 导航栏的 `.hairline`（1px 高度 + separator 半透明色）
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(tokens.divider.copy(alpha = 1f - 0.55f * collapsedFraction))
                )
            }
        }
    }
}
