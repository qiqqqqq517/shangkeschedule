package com.shangkeschedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangkeschedule.ui.theme.LocalIsSoftTheme
import com.shangkeschedule.ui.theme.appColors
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
    val isSoft = LocalIsSoftTheme.current
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
                blurRadius = if (isSoft) 22.dp else 20.dp
                noiseFactor = 0f
                tints = listOf(HazeTint(tokens.navBarBg.copy(alpha = if (isSoft) 0.58f else 0.62f)))
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
                        // 柔绘字重降一档（Medium），配合低对比配色更柔
                        fontWeight = if (isSoft) FontWeight.Medium else FontWeight.SemiBold,
                        letterSpacing = if (isSoft) (-0.1).sp else (-0.41).sp
                    )
                ) { title() }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (isSoft) {
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
        } else {
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
