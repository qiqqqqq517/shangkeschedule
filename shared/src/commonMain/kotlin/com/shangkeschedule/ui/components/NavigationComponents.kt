package com.shangkeschedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangkeschedule.Destination
import com.shangkeschedule.ui.theme.AppShape
import com.shangkeschedule.ui.theme.AppSpacing
import com.shangkeschedule.ui.theme.appColors
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
                val railTextSize = 12.sp
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
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    bottomBar = {
                        // 紧凑悬浮胶囊条（基线 §2）：宽度包内容、水平居中、高度收敛；
                        // 选中项 = 图文一体的浅色胶囊高亮 + 加粗，未选中灰。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(
                                    horizontal = AppSpacing.navBarHorizontal,
                                    vertical = AppSpacing.navBarBottom
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = navigationModifier
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = AppShape.capsule,
                                        clip = false,
                                        ambientColor = tokens.shadow,
                                        spotColor = tokens.shadow
                                    )
                                    .clip(AppShape.capsule)
                                    .background(if (isTransparent) Color.Transparent else resolvedContainerColor)
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
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { if (!isSelected) onTabSelected(item.destination) }
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label,
                                            tint = if (isSelected) resolvedSelectedColor else resolvedUnselectedColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = item.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) resolvedSelectedTextColor else resolvedUnselectedColor,
                                            modifier = Modifier.padding(start = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    content(innerPadding)
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