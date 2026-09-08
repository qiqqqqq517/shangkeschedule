package com.shangkeschedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangkeschedule.Destination
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.shangkeschedule.ui.theme.AppShape
import com.shangkeschedule.ui.theme.AppSpacing
import com.shangkeschedule.ui.theme.AppType
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.liquidGlass
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.account_circle_24px
import shangkeschedule.shared.generated.resources.account_circle_filled_24px
import shangkeschedule.shared.generated.resources.nav_course_schedule
import shangkeschedule.shared.generated.resources.nav_settings
import shangkeschedule.shared.generated.resources.nav_today_schedule
import shangkeschedule.shared.generated.resources.view_agenda_24px
import shangkeschedule.shared.generated.resources.view_agenda_filled_24px
import shangkeschedule.shared.generated.resources.view_week_24px
import shangkeschedule.shared.generated.resources.view_week_filled_24px

private data class NavItemData(
    val label: String,
    val destination: Destination,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * 自适应导航栏组件
 */
@Composable
fun AdaptiveNavigationScaffold(
    currentDestination: Destination,
    onTabSelected: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
    isTransparent: Boolean = false,
    contentColor: Color? = null,
    bottomBarContainerColor: Color? = null,
    bottomBarSelectedColor: Color? = null,
    bottomBarUnselectedColor: Color? = null,
    navigationModifier: Modifier = Modifier,
    /**
     * 底栏滚动隐藏状态回调：true 表示底栏已随下滑隐藏。
     * 供页面内的悬浮控件（如课表页「回到本周」圆钮）与底栏同步下沉/淡出，
     * 避免两套隐藏逻辑各自为政导致动画不同步。
     */
    onNavBarHiddenChange: (Boolean) -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val navItems = listOf(
        NavItemData(
            label = stringResource(Res.string.nav_today_schedule),
            destination = Destination.TodaySchedule,
            selectedIcon = vectorResource(Res.drawable.view_agenda_filled_24px),
            unselectedIcon = vectorResource(Res.drawable.view_agenda_24px)
        ),
        NavItemData(
            label = stringResource(Res.string.nav_course_schedule),
            destination = Destination.CourseSchedule,
            selectedIcon = vectorResource(Res.drawable.view_week_filled_24px),
            unselectedIcon = vectorResource(Res.drawable.view_week_24px)
        ),
        NavItemData(
            label = stringResource(Res.string.nav_settings),
            destination = Destination.Settings,
            selectedIcon = vectorResource(Res.drawable.account_circle_filled_24px),
            unselectedIcon = vectorResource(Res.drawable.account_circle_24px)
        )
    )

    val tokens = appColors()

    val finalContentColor = contentColor ?: MaterialTheme.colorScheme.onSurface
    val finalSubTextColor = finalContentColor.copy(alpha = 0.7f)

    // 悬浮胶囊底栏（v2 规范 §2）：白色胶囊条 + 选中浅紫胶囊高亮 + 图文加粗
    // v3.23.10：回退 v3.23.9 的"选中固定主色"方案（用户否决），配色恢复为
    // 跟随 contentColor / navSelectedBg 的原逻辑；对比增强改为选中项字号
    // 11sp→12sp（见下方 Text），与未选中拉开层级。
    val resolvedContainerColor = bottomBarContainerColor ?: tokens.navBarBg
    val resolvedSelectedColor = bottomBarSelectedColor
        ?: (if (contentColor != null) finalContentColor else MaterialTheme.colorScheme.primary)
    val resolvedSelectedTextColor = bottomBarSelectedColor
        ?: (if (contentColor != null) finalContentColor else MaterialTheme.colorScheme.primary)
    val resolvedUnselectedColor = bottomBarUnselectedColor
        ?: (if (contentColor != null) finalSubTextColor else tokens.textSecondary)
    val resolvedIndicatorColor = if (isTransparent) {
        Color.Transparent
    } else {
        bottomBarSelectedColor?.copy(alpha = 0.12f) ?: tokens.navSelectedBg
    }

    // Rail（宽屏侧边栏）配色；手机端底栏已改为自绘紧凑胶囊，不再走 NavigationBarItem
    val itemColors: NavigationSuiteItemColors = NavigationSuiteDefaults.itemColors(
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            indicatorColor = resolvedIndicatorColor,
            selectedIconColor = resolvedSelectedColor,
            selectedTextColor = resolvedSelectedTextColor,
            unselectedIconColor = resolvedUnselectedColor,
            unselectedTextColor = resolvedUnselectedColor
        )
    )

    val layoutType = if (!showNavigation) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (layoutType) {
            NavigationSuiteType.NavigationRail -> {
                // 宽屏侧边栏沿用 M3 NavigationRail 规格（图标 24dp / 文字 12sp）
                val railIconSize = 24.dp
                val railTextSize = AppType.hint
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = if (isTransparent) Color.Transparent else resolvedContainerColor,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navItems.forEach { item ->
                            val isSelected = currentDestination::class == item.destination::class
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { if (!isSelected) onTabSelected(item.destination) },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                        modifier = Modifier.size(railIconSize)
                                    )
                                },
                                label = { Text(item.label, fontSize = railTextSize) },
                                colors = itemColors.navigationRailItemColors
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        content(PaddingValues(0.dp))
                    }
                }
            }
            NavigationSuiteType.NavigationBar -> {
                // 毛玻璃悬浮胶囊底栏（Telegram 形态 + 克制玻璃质感）：
                // 内容整体作为 hazeSource，胶囊 hazeEffect 背板模糊 + 半透明底色。
                // innerPadding 语义与原 Scaffold 一致（bottom = 底栏占用高度），
                // 壁纸模式或未来内容滚动到底栏之下时，玻璃后方即为真实内容。
                val hazeState = rememberHazeState()
                val density = LocalDensity.current
                val navInsetPx = WindowInsets.navigationBars.getBottom(density)
                // 底栏占用 = 胶囊高（touchMin 48 + 上下 7dp）+ 上下外距（navBarBottom × 2）
                val barOccupied = AppSpacing.touchMin + 14.dp + AppSpacing.navBarBottom * 2
                val barInsetBottom = barOccupied + (navInsetPx / density.density).dp

                // 滚动隐藏（Telegram 手势）：下滑累积超过阈值隐藏，上滑立即显示；
                // 切换 Tab / 列表顶部 overscroll 时恢复显示
                var barHidden by remember { mutableStateOf(false) }
                var downAccumPx by remember { mutableFloatStateOf(0f) }
                // 滚动状态切换冷却时间（毫秒）：防止单手势内 hide/show 反复抖动
                var lastToggleMs by remember { mutableLongStateOf(0L) }
                val hideThresholdPx = with(density) { 72.dp.toPx() }
                val toggleCooldownMs = 300L
                val hideRangePx = with(density) { barInsetBottom.toPx() }
                val nestedConnection = remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            val dy = available.y
                            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                            val inCooldown = now - lastToggleMs < toggleCooldownMs
                            if (!barHidden && dy < 0) {
                                downAccumPx = (downAccumPx - dy).coerceAtMost(hideThresholdPx * 2)
                                if (downAccumPx >= hideThresholdPx) {
                                    barHidden = true
                                    downAccumPx = 0f
                                    lastToggleMs = now
                                }
                            } else if (dy > 2f && barHidden && !inCooldown) {
                                barHidden = false
                                downAccumPx = 0f
                                lastToggleMs = now
                            }
                            return Offset.Zero
                        }

                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                            val inCooldown = now - lastToggleMs < toggleCooldownMs
                            // 仅在上滑（available.y>0）时恢复显示；顶部下拉（<0）不再触发 re-show，
                            // 避免「已在顶部持续下拉」时 hide→show 在同一手势内反复闪烁
                            if (barHidden && available.y > 4f && !inCooldown) {
                                barHidden = false
                                downAccumPx = 0f
                                lastToggleMs = now
                            }
                            return Offset.Zero
                        }
                    }
                }
                LaunchedEffect(currentDestination) {
                    barHidden = false
                    downAccumPx = 0f
                }
                // 同步隐藏状态给宿主页面内的悬浮控件
                LaunchedEffect(barHidden) {
                    onNavBarHiddenChange(barHidden)
                }
                // v3.26.0 动效收口：底栏隐藏时长/缓动读全局动效令牌，
                // 关掉「底栏隐藏」分组（hideDurationMs=0）⇒ 瞬切出/入屏。
                val motion = LocalAppMotion.current
                val hideAnimSpec = remember(motion) {
                    tween<Float>(durationMillis = motion.tokens.hideDurationMs, easing = motion.tokens.hideEasing)
                }
                val hideFraction by animateFloatAsState(
                    targetValue = if (barHidden) 1f else 0f,
                    animationSpec = hideAnimSpec,
                    label = "navBarHide"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedConnection)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState)
                    ) {
                        // P2-3 隐藏后留白回收：底栏隐藏动画进行中，内容底部 padding 同步收缩，
                        // 让列表内容顺势延伸至底栏空位，不残留一块空白。
                        // ⚠️ 必须钳制：弹性风格（琉璃轻弹 GlassEase）末端过冲会让 hideFraction > 1，
                        // 不钳制则算出负 padding 直接 IllegalArgumentException（真机已实锤）。
                        content(PaddingValues(bottom = barInsetBottom * (1f - hideFraction).coerceIn(0f, 1f)))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = hideFraction * hideRangePx
                                // v3.24.4：淡出曲线与「回到本周」圆钮对齐——隐藏时完全淡出
                                // （原为 1 - fraction*0.4 只淡到 60%，与圆钮 1-fraction 不一致）
                                alpha = (1f - hideFraction).coerceIn(0f, 1f)
                            }
                            .navigationBarsPadding()
                            .padding(
                                horizontal = AppSpacing.navBarHorizontal,
                                vertical = AppSpacing.navBarBottom
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = navigationModifier
                                // 液态玻璃：毛玻璃 + 白纱 + 顶部高光 + 折射亮边（与「回到本周」圆钮同源），
                                // 内部已按「shadow → clip → hazeEffect → 高光 → 亮边」顺序封装。
                                // blur 不在此处写死：统一取 LiquidGlassBlurRadius（v3.24.7），
                                // 任何一处单独调参都会让底栏与其余玻璃件再次分叉。
                                .liquidGlass(
                                    hazeState = hazeState,
                                    shape = AppShape.capsule,
                                    containerColor = bottomBarContainerColor ?: tokens.inputBg,
                                    isTransparent = isTransparent
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            navItems.forEach { item ->
                                val isSelected = currentDestination::class == item.destination::class
                                Row(
                                    modifier = Modifier
                                        .clip(AppShape.capsule)
                                        .background(
                                            if (isSelected) resolvedIndicatorColor else Color.Transparent
                                        )
                                        // 触控标准 ≥48dp（AppSpacing.touchMin）+ 无障碍：selectable 提供
                                        // selected 语义与 Tab 角色（TalkBack 播报「已选中」且无重复朗读）
                                        .heightIn(min = AppSpacing.touchMin)
                                        .selectable(
                                            selected = isSelected,
                                            role = Role.Tab,
                                            onClick = { if (!isSelected) onTabSelected(item.destination) }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        // 图标语义由下方 Text 承担，置 null 避免 TalkBack 双读
                                        contentDescription = null,
                                        tint = if (isSelected) resolvedSelectedColor else resolvedUnselectedColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = item.label,
                                        // v3.23.10 对比增强：选中项字号 11sp(badge)→12sp(hint)（保持 Bold），
                                        // 未选中 11sp Medium，字号+字重双通道拉开层级
                                        fontSize = if (isSelected) AppType.hint else AppType.badge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) resolvedSelectedTextColor else resolvedUnselectedColor,
                                        modifier = Modifier.padding(start = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                content(PaddingValues(0.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AdaptiveNavigationScaffoldPreview() {
    MaterialTheme {
        AdaptiveNavigationScaffold(
            currentDestination = Destination.CourseSchedule,
            onTabSelected = {}
        ) { _ ->
            // Preview Content
        }
    }
}