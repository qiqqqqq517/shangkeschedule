package com.shangkeschedule.ui.settings.appearance

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.shangkeschedule.ui.components.AppRadioIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.components.rememberFabPressedScale
import com.shangkeschedule.ui.settings.SettingsViewModel
import com.shangkeschedule.ui.theme.AccentTone
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.AnimationStyle
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.appColors
import kotlinx.coroutines.delay
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
import shangkeschedule.shared.generated.resources.anim_preview_replay
import shangkeschedule.shared.generated.resources.anim_preview_press_hint
import shangkeschedule.shared.generated.resources.anim_section_groups
import shangkeschedule.shared.generated.resources.anim_section_groups_desc
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
import shangkeschedule.shared.generated.resources.style_demo_conflict_a
import shangkeschedule.shared.generated.resources.style_demo_conflict_b
import shangkeschedule.shared.generated.resources.style_demo_regular_course

/**
 * 「个性化显示 → 动画效果」三级页（v3.26.0 新增）。
 *
 * 三块内容：
 * 1. 实时预览——演示课程块（入场错峰）+ 演示胶囊（按压缩放），随当前风格/分组即时变化，
 *    「重播」可反复触发入场；
 * 2. 动效风格——三档四字效果名（琉璃轻弹 / 舒缓轻移 / 灵动跟手）单选；
 * 3. 动画分组——六个可独立开关的分组（关掉 ⇒ 该类动画瞬切无动效）+ 预留占位。
 *
 * 值经 `AppSettingsModel.animationStyle` + `disabledAnimationGroups` → `LocalAppMotion`
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.item_animation_settings),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1) 实时预览（随风格与分组开关即时变化）
            MotionPreviewCard()

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = appColors().divider, thickness = 0.5.dp)

            // 2) 动效风格
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

/**
 * 实时预览卡：三块演示课程块（入场错峰淡入）+ 一颗演示胶囊（按压缩放）+ 重播按钮。
 * 预览读的就是全局 [LocalAppMotion]，与真机完全同源——不存在第二套近似值。
 */
@Composable
private fun MotionPreviewCard() {
    val motion = LocalAppMotion.current
    val tokens = appColors()

    // 重播键：每次 +1 重置入场动画
    var previewKey by remember { mutableStateOf(0) }

    Surface(
        shape = appShapes().card,
        color = tokens.pageBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.item_animation_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = { previewKey++ },
                    shape = appShapes().capsule,
                    color = tokens.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = stringResource(Res.string.anim_preview_replay),
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // 演示课程块 ×3（错峰入场；文案复用玻璃模糊预览的既有四语言 demo 资源）
            val demoNames = listOf(
                stringResource(Res.string.style_demo_regular_course),
                stringResource(Res.string.style_demo_conflict_a),
                stringResource(Res.string.style_demo_conflict_b)
            )
            demoNames.forEachIndexed { index, name ->
                MotionDemoBlock(
                    label = name,
                    previewKey = previewKey,
                    delayMs = motion.tokens.entranceStaggerMs * index
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = appColors().divider, thickness = 0.5.dp)

            // 演示胶囊（按压反馈；对应「玻璃悬浮件」分组的 pressSpec/pressScale）
            PressDemoPill()
        }
    }
}

/** 单条演示课程块：入场 = 淡入 + 微上移（读全局令牌；关组 ⇒ 直接显示）。 */
@Composable
private fun MotionDemoBlock(label: String, previewKey: Int, delayMs: Int) {
    val motion = LocalAppMotion.current
    val tokens = appColors()
    val entranceEnabled = motion.isEnabled(AnimationGroup.PAGE_ENTRANCE) &&
        motion.tokens.entranceDurationMs > 0
    var entered by remember(previewKey) { mutableStateOf(!entranceEnabled) }
    LaunchedEffect(previewKey) {
        if (!entered) {
            delay(delayMs.toLong())
            entered = true
        }
    }
    val fraction = remember(previewKey) { Animatable(if (entered) 1f else 0f) }
    LaunchedEffect(previewKey, entered) {
        if (entered && fraction.value < 1f) {
            fraction.animateTo(
                1f,
                tween(motion.tokens.entranceDurationMs, easing = motion.tokens.entranceEasing)
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = tokens.tone(AccentTone.PRIMARY).bg,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = fraction.value.coerceIn(0f, 1f)
                translationY = motion.tokens.entranceSlideDp.toPx() * (1f - fraction.value)
            }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = tokens.tone(AccentTone.PRIMARY).fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/** 演示胶囊：按压时按全局令牌缩放（关掉「玻璃悬浮件」⇒ 无缩放）。 */
@Composable
private fun PressDemoPill() {
    val motion = LocalAppMotion.current
    val tokens = appColors()
    val interaction = remember { MutableInteractionSource() }
    val pressedScale = rememberFabPressedScale(interaction)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = tokens.primary.copy(alpha = 0.12f),
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pressedScale
                    scaleY = pressedScale
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null
                ) { /* 演示件：无动作 */ }
        ) {
            Text(
                text = stringResource(Res.string.anim_preview_press_hint),
                style = MaterialTheme.typography.labelLarge,
                color = tokens.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
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

