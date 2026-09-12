package com.shangkeschedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangkeschedule.Destination
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.glass.GlassBackdrop
import com.shangkeschedule.ui.glass.LiquidGlassTab
import com.shangkeschedule.ui.glass.LiquidGlassTabs
import com.shangkeschedule.ui.glass.glassBackdropSource
import com.shangkeschedule.ui.glass.rememberGlassBackdrop
import com.shangkeschedule.ui.glass.isGlassFallbackActive
import com.shangkeschedule.ui.theme.legacyHazeGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
 * 脚手架级「玻璃背景快照」（v3.49.1）：由 [AdaptiveNavigationScaffold] 录制页面内容后，
 * 经此 CompositionLocal 暴露给页面内容 —— 课表页 / 今日页的玻璃 FAB、回到本周圆钮、
 * 课程挂起条因此可以复用**同一份**快照，不必再在页面里自建第二份（此前导致每帧
 * 两份全页 `GraphicsLayer.record`，且是「玻璃取样玻璃」的冗余）。
 */
val LocalNavigationGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

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

    // 全页**唯一**一份背景快照：由脚手架录制页面内容，喂给底栏，并经 Local 暴露给页面内的
    // 玻璃件（FAB / 回到本周圆钮 / 挂起条）——页面不再自建第二份。
    val glassBackdrop = rememberGlassBackdrop()

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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .glassBackdropSource(glassBackdrop)
                    ) {
                        CompositionLocalProvider(LocalNavigationGlassBackdrop provides glassBackdrop) {
                            content(PaddingValues(0.dp))
                        }
                    }
                }
            }
            NavigationSuiteType.NavigationBar -> {
                // 底栏有两套方案（v3.50.10）：
                // - **方案 A（默认）**：自研玻璃引擎 LiquidGlassTabs（v3.48.0 起的原版三层结构）；
                // - **方案 B（兜底）**：v3.44.0 的 Haze 胶囊底栏（共享指示器 + tabBounds 补间），
                //   当 isGlassFallbackActive 为真（上次进程崩溃退出）时**自动启用**。
                // 方案 B 只用 Haze 模糊 + 纯 Compose 绘制，不含引擎的 RenderEffect 链 / AGSL /
                // BlendMode.Plus 离屏层 —— 这些正是部分 Android 15 机型闪退的嫌疑操作；
                // v3.44.0 已被用户实测可在 OPPO Find X8 上正常运行，故作为回滚方案。
                val useLegacyBar = isGlassFallbackActive
                val hazeState = rememberHazeState()
                val density = LocalDensity.current
                val navInsetPx = WindowInsets.navigationBars.getBottom(density)
                // 底栏占用 = 胶囊高（方案 A 64dp / 方案 B touchMin + 上下 7dp）+ 上下外距
                val barOccupied = if (useLegacyBar) {
                    appSpacing().touchMin + 14.dp + appSpacing().navBarBottom * 2
                } else {
                    64.dp + appSpacing().navBarBottom * 2
                }
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
                            .then(
                                if (useLegacyBar) {
                                    // 方案 B：Haze 背景源（与 v3.44.0 同机制）
                                    Modifier.hazeSource(hazeState)
                                } else {
                                    // 方案 A：页面内容录为玻璃背景快照（新引擎唯一数据源）
                                    Modifier.glassBackdropSource(glassBackdrop)
                                }
                            )
                    ) {
                        // P2-3 隐藏后留白回收：底栏隐藏动画进行中，内容底部 padding 同步收缩，
                        // 让列表内容顺势延伸至底栏空位，不残留一块空白。
                        // ⚠️ 必须钳制：弹性风格（琉璃轻弹 GlassEase）末端过冲会让 hideFraction > 1，
                        // 不钳制则算出负 padding 直接 IllegalArgumentException（真机已实锤）。
                        CompositionLocalProvider(LocalNavigationGlassBackdrop provides glassBackdrop) {
                            content(PaddingValues(bottom = barInsetBottom * (1f - hideFraction).coerceIn(0f, 1f)))
                        }
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
                        // v3.48.0：底栏 = 原版 LiquidBottomTabs 三层结构
                        //（64dp 玻璃条 + 隐形 Tab 录制层 + 56dp 可拖拽玻璃指示器 + 按压形变），
                        // 光学与交互逐层对齐 Kyant0/AndroidLiquidGlass 的参考实现。
                        var selectedTabIndex by remember {
                                mutableIntStateOf(
                                    navItems.indexOfFirst {
                                        currentDestination::class == it.destination::class
                                    }.coerceAtLeast(0)
                                )
                            }
                            LaunchedEffect(currentDestination) {
                                selectedTabIndex = navItems.indexOfFirst {
                                    currentDestination::class == it.destination::class
                                }.coerceAtLeast(0)
                            }
                            if (useLegacyBar) {
                                // ── 方案 B（v3.44.0 兜底栏）──
                                LegacyGlassBottomBar(
                                    hazeState = hazeState,
                                    navItems = navItems,
                                    selectedTabIndex = selectedTabIndex,
                                    onTabSelected = onTabSelected,
                                    containerColor = resolvedContainerColor,
                                    indicatorColor = resolvedIndicatorColor,
                                    selectedColor = resolvedSelectedTextColor,
                                    unselectedColor = resolvedUnselectedColor,
                                    isTransparent = isTransparent,
                                    navigationModifier = navigationModifier
                                )
                            } else {
                            // ── 方案 A（默认：自研玻璃引擎）──
                            // 稳定的取值闭包：避免每次重组重置 LiquidGlassTabs 内部的拖拽状态
                            val selectedTabIndexGetter = remember { { selectedTabIndex } }
                            LiquidGlassTabs(
                                selectedTabIndex = selectedTabIndexGetter,
                                onTabSelected = { index ->
                                    onTabSelected(navItems[index].destination)
                                },
                                backdrop = glassBackdrop,
                                tabsCount = navItems.size,
                                accentColor = resolvedSelectedColor,
                                isDark = LocalIsDarkTheme.current,
                                modifier = Modifier.fillMaxWidth()
                            ) { index ->
                                val item = navItems[index]
                                val isSelected = index == selectedTabIndex
                                LiquidGlassTab(
                                    onClick = {
                                        // 原版流程：点击只更新外部 selectedTabIndex 状态；
                                        // 指示器滑动与导航由 LiquidGlassTabs 的
                                        // snapshotFlow(currentIndex) → onTabSelected 驱动
                                        selectedTabIndex = index
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = null,
                                        tint = finalContentColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = item.label,
                                        fontSize = 12.sp,
                                        // 行高修剪：默认 12sp 字号的行盒 ≈16sp 且带字体上下留白，
                                        // 视觉上"图标与文字之间还有挺大间距"主要来自这里而非
                                        // Column 的 spacedBy。lineHeight 收到 14sp + 去掉
                                        // includeFontPadding + Trim.Both，让字形贴住图标，
                                        // 图标+文字整体在 Tab 内居中。
                                        lineHeight = 14.sp,
                                        style = TextStyle(
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.Both
                                            )
                                        ),
                                        fontWeight = FontWeight.Medium,
                                        color = finalContentColor,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            }
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .glassBackdropSource(glassBackdrop)
                ) {
                    CompositionLocalProvider(LocalNavigationGlassBackdrop provides glassBackdrop) {
                        content(PaddingValues(0.dp))
                    }
                }
            }
        }
    }
}

/**
 * 方案 B · v3.44.0 兜底底栏（**仅在 `isGlassFallbackActive` 时启用**）。
 *
 * 移植自 v3.44.0（提交 c261e96）的 `NavigationSuiteType.NavigationBar` 分支，
 * 逐行还原「选中胶囊 = 一个共享胶囊按各 Tab 实测位置平滑迁移」+「Haze 毛玻璃胶囊」结构。
 *
 * 安全性：只用 Haze（`hazeEffect`，v3.44.0 实测可用）与纯 Compose 绘制
 * （`drawBehind` 的 Screen 混合 + 多条 `border`），**不含**自研引擎的
 * `RenderEffect` 链 / AGSL `RuntimeShader` / `BlendMode.Plus` 离屏层。
 */
@Composable
private fun LegacyGlassBottomBar(
    hazeState: HazeState,
    navItems: List<NavItemData>,
    selectedTabIndex: Int,
    onTabSelected: (Destination) -> Unit,
    containerColor: Color,
    indicatorColor: Color,
    selectedColor: Color,
    unselectedColor: Color,
    isTransparent: Boolean,
    navigationModifier: Modifier
) {
    val density = LocalDensity.current
    val navMotion = LocalAppMotion.current

    // 选中胶囊按各 Tab 的实测位置在项之间平滑迁移
    // （v3.43.0：此前每个 Tab 各自瞬切底色，在柔绘薄涂底 / 书卷实色底上会被读成一次"闪"，
    //   且完全没有位移语义）。
    val tabBounds = remember { mutableStateMapOf<Int, Rect>() }
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
        targetValue = indicatorColor,
        animationSpec = tween(
            navMotion.tokens.tabIndicatorMs,
            easing = navMotion.tokens.navEasing
        ),
        label = "tabIndicatorColor"
    )

    Box(
        modifier = navigationModifier
            // Haze 毛玻璃 + 白纱 + 顶部高光 + 边缘光学（v3.44.0 版实现）
            .legacyHazeGlass(
                hazeState = hazeState,
                shape = appShapes().capsule,
                containerColor = containerColor,
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
                val isSelected = index == selectedTabIndex
                // 图标与文字颜色补间；字号**不参与**动画（避免文本重排），
                // 选中层级由字重 + 颜色 + 胶囊共同承载
                val animatedItemColor by animateColorAsState(
                    targetValue = if (isSelected) selectedColor else unselectedColor,
                    animationSpec = tween(
                        navMotion.tokens.tabIndicatorMs,
                        easing = navMotion.tokens.navEasing
                    ),
                    label = "tabItemColor"
                )
                Row(
                    modifier = Modifier
                        .clip(appShapes().capsule)
                        // 触控标准 ≥48dp + 无障碍：selectable 提供 selected 语义与 Tab 角色
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
                    // 图标：SF Symbol 式变体切换（填充 ↔ 线性）用 Crossfade
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
