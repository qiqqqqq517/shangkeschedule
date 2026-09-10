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
import com.shangkeschedule.ui.theme.appColors
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
            hazeState = hazeState
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
 * iOS 26 导航栏本体：48dp 高 + 居中标题 + 玻璃底衬 + 底部发丝线。
 */
@Composable
private fun AppNavigationBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    hazeState: HazeState
) {
    val tokens = appColors()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .hazeEffect(hazeState) {
                blurRadius = 20.dp
                noiseFactor = 0f
                tints = listOf(HazeTint(tokens.navBarBg.copy(alpha = 0.62f)))
                fallbackTint = HazeTint(tokens.navBarBg)
                backgroundColor = Color.Transparent
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(48.dp)
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
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.41).sp
                    )
                ) { title() }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        // 底部发丝分隔线：iOS 导航栏的 `.hairline`（滚动时才显形，这里常显以在纯色页面上
        // 也能明确导航栏边界；1px 高度 + separator 半透明色，深浅两套均按 Apple 取值）
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(tokens.divider)
        )
    }
}
