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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangkeschedule.Destination
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.liquidGlass
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.account_circle_24px
import shangkeschedule.shared.generated.resources.account_circle_filled_24px
import shangkeschedule.shared.generated.resources.calendar_today_24px
import shangkeschedule.shared.generated.resources.nav_course_schedule
import shangkeschedule.shared.generated.resources.nav_schedule
import shangkeschedule.shared.generated.resources.nav_settings
import shangkeschedule.shared.generated.resources.nav_today
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
            // 底栏 4 个 Tab 后横向空间紧张，首项用短标签「今日」避免换行（完整名称见页面标题）
            label = stringResource(Res.string.nav_today),
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
            label = stringResource(Res.string.nav_schedule),
            destination = Destination.Schedule,
            selectedIcon = vectorResource(Res.drawable.calendar_today_24px),
            unselectedIcon = vectorResource(Res.drawable.calendar_today_24px)
        ),
        NavItemData(
            label = stringResource(Res.string.nav_settings),
            destination = Destination.Settings,
            selectedIcon = vectorResource(Res.drawable.account_circle_filled_24px),
            unselectedIcon = vectorResource(Res.drawable.account_circle_24px)
        )
    )

    val tokens = appColors()
    // v3.43.0：Tab 切换动效读全局令牌 + 主题档位
    val navMotion = LocalAppMotion.current

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

    val layoutType = if (!showNavigation) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (layoutType) {
            NavigationSuiteType.NavigationRail -> {
                // 宽屏侧边栏自绘：主题化胶囊选中 + 图文上下排列
                val railIconSize = 24.dp
                val railTextSize = appType().hint
                val railWidth = 80.dp
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(railWidth)
                            .background(if (isTransparent) Color.Transparent else resolvedContainerColor)
                            .padding(vertical = appSpacing().navBarBottom),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        navItems.forEach { item ->
                            val isSelected = currentDestination::class == item.destination::class
                            // v3.43.0：宽屏侧边栏同样补上选中过渡（颜色补间），节奏与底栏一致
                            val itemColor by animateColorAsState(
                                targetValue = if (isSelected) resolvedSelectedColor else resolvedUnselectedColor,
                                animationSpec = tween(
                                    navMotion.tokens.tabIndicatorMs,
                                    easing = navMotion.tokens.navEasing
                                ),
                                label = "railItemColor"
                            )
                            val railItemBg by animateColorAsState(
                                targetValue = if (isSelected) resolvedIndicatorColor else Color.Transparent,
                                animationSpec = tween(
                                    navMotion.tokens.tabIndicatorMs,
                                    easing = navMotion.tokens.navEasing
                                ),
                                label = "railItemBg"
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .clip(appShapes().capsule)
                                    .background(railItemBg)
                                    .clickable { if (!isSelected) onTabSelected(item.destination) }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(railIconSize),
                                    tint = itemColor
                                )
                                Text(
                                    text = item.label,
                                    fontSize = railTextSize,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = itemColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
                val barOccupied = appSpacing().touchMin + 14.dp + appSpacing().navBarBottom * 2
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
                                horizontal = appSpacing().navBarHorizontal,
                                vertical = appSpacing().navBarBottom
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // 选中胶囊 = 一个共享胶囊，按各 Tab 的实测位置在项之间平滑迁移
                        // （v3.43.0：此前每个 Tab 各自瞬切底色，在柔绘薄涂底 / 书卷实色底上
                        //   会被读成一次"闪"，且完全没有位移语义）。
                        val tabBounds = remember { mutableStateMapOf<Int, Rect>() }
                        val selectedTabIndex = navItems.indexOfFirst {
                            currentDestination::class == it.destination::class
                        }
                        val indicatorLeft by animateDpAsState(
                            targetValue = tabBounds[selectedTabIndex]
                                ?.let { with(density) { it.left.toDp() } } ?: 0.dp,
                            animationSpec = tween(
                                navMotion.tokens.tabIndicatorMs,
                                easing = navMotion.tokens.navEasing
                            ),
                            label = "tabIndicatorLeft"
                        )
                        val indicatorWidth by animateDpAsState(
                            targetValue = tabBounds[selectedTabIndex]
                                ?.let { with(density) { it.width.toDp() } } ?: 0.dp,
                            animationSpec = tween(
                                navMotion.tokens.tabIndicatorMs,
                                easing = navMotion.tokens.navEasing
                            ),
                            label = "tabIndicatorWidth"
                        )
                        val animatedIndicatorColor by animateColorAsState(
                            targetValue = resolvedIndicatorColor,
                            animationSpec = tween(
                                navMotion.tokens.tabIndicatorMs,
                                easing = navMotion.tokens.navEasing
                            ),
                            label = "tabIndicatorColor"
                        )

                        Box(
                            modifier = navigationModifier
                                // 液态玻璃：毛玻璃 + 白纱 + 顶部高光 + 折射亮边（与「回到本周」圆钮同源），
                                // 内部已按「shadow → clip → hazeEffect → 高光 → 亮边」顺序封装。
                                // blur 不在此处写死：统一取 LiquidGlassBlurRadius（v3.24.7），
                                // 任何一处单独调参都会让底栏与其余玻璃件再次分叉。
                                .liquidGlass(
                                    hazeState = hazeState,
                                    shape = appShapes().capsule,
                                    containerColor = bottomBarContainerColor ?: tokens.inputBg,
                                    isTransparent = isTransparent
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            // 高亮胶囊：位于内容之下，位置 / 宽度 / 颜色三者同时补间
                            if (selectedTabIndex >= 0 && indicatorWidth > 0.dp) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .offset(x = indicatorLeft)
                                        .width(indicatorWidth)
                                        .height(appSpacing().touchMin)
                                        .clip(appShapes().capsule)
                                        .background(animatedIndicatorColor)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                navItems.forEachIndexed { index, item ->
                                    val isSelected = currentDestination::class == item.destination::class
                                    // 图标与文字颜色补间；字号**不参与**动画（避免文本重排），
                                    // 选中层级由字重 + 颜色 + 胶囊共同承载
                                    val animatedItemColor by animateColorAsState(
                                        targetValue = if (isSelected) resolvedSelectedColor else resolvedUnselectedColor,
                                        animationSpec = tween(
                                            navMotion.tokens.tabIndicatorMs,
                                            easing = navMotion.tokens.navEasing
                                        ),
                                        label = "tabItemColor"
                                    )
                                    Row(
                                        modifier = Modifier
                                            .clip(appShapes().capsule)
                                            // 触控标准 ≥48dp（appSpacing().touchMin）+ 无障碍：selectable 提供
                                            // selected 语义与 Tab 角色（TalkBack 播报「已选中」且无重复朗读）
                                            .heightIn(min = appSpacing().touchMin)
                                            .onGloballyPositioned { coords ->
                                                val rect = coords.boundsInParent()
                                                if (tabBounds[index] != rect) tabBounds[index] = rect
                                            }
                                            .selectable(
                                                selected = isSelected,
                                                role = Role.Tab,
                                                onClick = { if (!isSelected) onTabSelected(item.destination) }
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 图标：SF Symbol 式变体切换（填充 ↔ 线性）用 Crossfade；
                                        // 关掉「切换标签」分组（tabIconMs=0）⇒ 直接替换
                                        if (navMotion.isEnabled(AnimationGroup.TAB_SWITCH) &&
                                            navMotion.tokens.tabIconMs > 0
                                        ) {
                                            Crossfade(
                                                targetState = isSelected,
                                                animationSpec = tween(
                                                    navMotion.tokens.tabIconMs,
                                                    easing = navMotion.tokens.navEasing
                                                ),
                                                label = "tabIconCrossfade"
                                            ) { selected ->
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    // 图标语义由下方 Text 承担，置 null 避免 TalkBack 双读
                                                    contentDescription = null,
                                                    tint = animatedItemColor,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = null,
                                                tint = animatedItemColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Text(
                                            text = item.label,
                                            // v3.23.10 对比增强：选中项字号 11sp(badge)→12sp(hint)（保持 Bold），
                                            // 未选中 11sp Medium，字号+字重双通道拉开层级
                                            fontSize = if (isSelected) appType().hint else appType().badge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = animatedItemColor,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
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