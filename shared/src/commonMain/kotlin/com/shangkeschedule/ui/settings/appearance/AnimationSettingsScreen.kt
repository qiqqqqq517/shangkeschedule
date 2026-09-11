package com.shangkeschedule.ui.settings.appearance

import com.shangkeschedule.ui.components.AppTopAppBar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.shangkeschedule.ui.components.AppRadioIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import com.shangkeschedule.ui.components.AppSegmentedControl
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.settings.SettingsViewModel
import com.shangkeschedule.ui.theme.MotionSpeed
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.AnimationStyle
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appColors
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.anim_group_bar_hide
import shangkeschedule.shared.generated.resources.anim_group_bar_hide_desc
import shangkeschedule.shared.generated.resources.anim_group_course_cell
import shangkeschedule.shared.generated.resources.anim_group_course_cell_desc
import shangkeschedule.shared.generated.resources.anim_group_glass_floating
import shangkeschedule.shared.generated.resources.anim_group_glass_floating_desc
import shangkeschedule.shared.generated.resources.anim_group_nav_transition
import shangkeschedule.shared.generated.resources.anim_group_nav_transition_desc
import shangkeschedule.shared.generated.resources.anim_group_page_entrance
import shangkeschedule.shared.generated.resources.anim_group_page_entrance_desc
import shangkeschedule.shared.generated.resources.anim_group_week_pager
import shangkeschedule.shared.generated.resources.anim_group_week_pager_desc
import shangkeschedule.shared.generated.resources.anim_reduce_motion
import shangkeschedule.shared.generated.resources.anim_reduce_motion_desc
import shangkeschedule.shared.generated.resources.anim_section_groups
import shangkeschedule.shared.generated.resources.anim_section_groups_desc
import shangkeschedule.shared.generated.resources.anim_section_speed
import shangkeschedule.shared.generated.resources.anim_section_speed_desc
import shangkeschedule.shared.generated.resources.anim_section_style
import shangkeschedule.shared.generated.resources.anim_section_style_desc
import shangkeschedule.shared.generated.resources.anim_settings_more_placeholder
import shangkeschedule.shared.generated.resources.anim_style_gentle
import shangkeschedule.shared.generated.resources.anim_style_gentle_desc
import shangkeschedule.shared.generated.resources.anim_style_glass
import shangkeschedule.shared.generated.resources.anim_style_glass_desc
import shangkeschedule.shared.generated.resources.anim_style_snappy
import shangkeschedule.shared.generated.resources.anim_style_snappy_desc
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.item_animation_settings

/**
 * 「个性化显示 → 动画效果」三级页（v3.26.0 新增）。
 *
 * 三块内容：
 * 1. 动效风格——三档四字效果名（琉璃轻弹 / 舒缓轻移 / 灵动跟手）单选；
 * 2. 动效速度——三档倍率统一缩放全部毫秒级时长（倍率越高越快）；
 * 3. 动画分组——九个可独立开关的分组（关掉 ⇒ 该类动画瞬切无动效）+ 减弱动态效果 + 预留占位。
 *
 * 值经 `AppSettingsModel.animationStyle` + `disabledAnimationGroups` + `reduceMotionEnabled`
 * → `LocalAppMotion`
 * 注入全 App：本页改一次，所有页面动画手感同步切换（与玻璃雾度同源思路）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationSettingsScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val settings = uiState.appSettings
    // 顶栏滚动折叠（v3.43.0 ·《交互动效审查》P2）：本页内容较长，顶栏随滚动收起
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.item_animation_settings),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = appColors().divider, thickness = 0.5.dp)

            // 1) 动效风格
            Text(
                text = stringResource(Res.string.anim_section_style),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
            Text(
                text = stringResource(Res.string.anim_section_style_desc),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            AnimationStyle.entries.forEach { style ->
                MotionStyleCard(
                    style = style,
                    selected = settings.animationStyle == style,
                    onSelect = { settingsViewModel.onAnimationStyleChanged(style) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = appColors().divider, thickness = 0.5.dp)

            // 2) 动效速度（v3.44.0）：统一缩放全部毫秒级时长——倍率越高越快
            Text(
                text = stringResource(Res.string.anim_section_speed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
            Text(
                text = stringResource(Res.string.anim_section_speed_desc),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            AppSegmentedControl(
                options = MotionSpeed.entries.map { stringResource(it.labelRes) },
                selectedIndex = MotionSpeed.entries.indexOf(settings.motionSpeed),
                onSelect = { settingsViewModel.onMotionSpeedChanged(MotionSpeed.entries[it]) },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = appColors().divider, thickness = 0.5.dp)

            // 3) 动画分组
            Text(
                text = stringResource(Res.string.anim_section_groups),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
            Text(
                text = stringResource(Res.string.anim_section_groups_desc),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            AnimationGroup.entries.forEach { group ->
                MotionGroupToggle(
                    label = stringResource(group.labelRes),
                    desc = stringResource(group.descRes),
                    enabled = group !in settings.disabledAnimationGroups,
                    onToggle = { settingsViewModel.onToggleAnimationGroup(group, it) }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = appColors().divider,
                thickness = 0.5.dp
            )

            // 无障碍：减弱动态效果（v3.43.0 ·《交互动效审查》P3）
            // 开启后位移 / 缩放 / 错峰全部归零，只保留短促的不透明度溶解。
            MotionGroupToggle(
                label = stringResource(Res.string.anim_reduce_motion),
                desc = stringResource(Res.string.anim_reduce_motion_desc),
                enabled = settings.reduceMotionEnabled,
                onToggle = { settingsViewModel.onReduceMotionChanged(it) }
            )

            // 预留占位：未来更多分组
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().alpha(0.45f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.anim_settings_more_placeholder),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

/** 三档风格单选卡。 */
@Composable
private fun MotionStyleCard(
    style: AnimationStyle,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onSelect,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) selectedColor.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.dp, selectedColor.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(style.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(style.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary
                )
            }
            AppRadioIndicator(selected = selected)
        }
    }
}

/** 分组开关行：标题 + 描述 + AppSwitch。 */
@Composable
private fun MotionGroupToggle(
    label: String,
    desc: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        onClick = { onToggle(!enabled) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary
                )
            }
            AppSwitch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}
