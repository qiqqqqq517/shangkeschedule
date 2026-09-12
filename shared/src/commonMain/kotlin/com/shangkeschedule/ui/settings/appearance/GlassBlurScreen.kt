package com.shangkeschedule.ui.settings.appearance

import com.shangkeschedule.ui.components.AppTopAppBar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shangkeschedule.ui.settings.SettingsViewModel
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.glass.GlassRefractionSettings
import com.shangkeschedule.ui.glass.LocalGlassRefraction
import com.shangkeschedule.ui.glass.glassBackdropSource
import com.shangkeschedule.ui.glass.isGlassRefractionAvailable
import com.shangkeschedule.ui.glass.rememberGlassBackdrop
import com.shangkeschedule.ui.settings.style.StyleSliderItem
import com.shangkeschedule.ui.theme.AccentTone
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.LiquidGlassBlurRadius
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.LiquidGlass
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.account_circle_24px
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.class_24px
import shangkeschedule.shared.generated.resources.desc_glass_blur
import shangkeschedule.shared.generated.resources.glass_preset_heavy
import shangkeschedule.shared.generated.resources.glass_preset_light
import shangkeschedule.shared.generated.resources.glass_preset_misty
import shangkeschedule.shared.generated.resources.glass_preset_off
import shangkeschedule.shared.generated.resources.glass_preset_standard
import shangkeschedule.shared.generated.resources.glass_refraction_amount
import shangkeschedule.shared.generated.resources.glass_refraction_depth
import shangkeschedule.shared.generated.resources.glass_refraction_depth_desc
import shangkeschedule.shared.generated.resources.glass_refraction_desc
import shangkeschedule.shared.generated.resources.glass_refraction_dispersion
import shangkeschedule.shared.generated.resources.glass_refraction_dispersion_desc
import shangkeschedule.shared.generated.resources.glass_refraction_height
import shangkeschedule.shared.generated.resources.glass_refraction_preset_none
import shangkeschedule.shared.generated.resources.glass_refraction_preset_obvious
import shangkeschedule.shared.generated.resources.glass_refraction_preset_strong
import shangkeschedule.shared.generated.resources.glass_refraction_preset_subtle
import shangkeschedule.shared.generated.resources.glass_refraction_switch
import shangkeschedule.shared.generated.resources.glass_refraction_title
import shangkeschedule.shared.generated.resources.glass_refraction_unsupported
import shangkeschedule.shared.generated.resources.glass_section_desc
import shangkeschedule.shared.generated.resources.glass_section_title
import shangkeschedule.shared.generated.resources.label_glass_blur
import shangkeschedule.shared.generated.resources.nav_course_schedule
import shangkeschedule.shared.generated.resources.nav_settings
import shangkeschedule.shared.generated.resources.nav_today_schedule
import shangkeschedule.shared.generated.resources.style_demo_conflict_a
import shangkeschedule.shared.generated.resources.style_demo_conflict_b
import shangkeschedule.shared.generated.resources.style_demo_position
import shangkeschedule.shared.generated.resources.style_demo_regular_course
import shangkeschedule.shared.generated.resources.style_demo_teacher
import shangkeschedule.shared.generated.resources.view_agenda_24px
import shangkeschedule.shared.generated.resources.view_week_24px

/**
 * 「个性化显示 → 玻璃模糊」三级页（v3.26.0 自 PersonalizedDisplayScreen 拆出）。
 *
 * 承载全局液态玻璃的雾度调节：预览 + 滑杆 + 常用档位。内容与 v3.25.0 完全一致，
 * 只是随着「个性化显示」升级为两卡 hub（玻璃模糊 / 动画效果）而下移一级。
 *
 * 值经 `AppSettingsModel.glassBlurRadiusDp` → `LocalGlassBlurRadius` 注入，所有玻璃件
 * 共读一个 Local：调一次全端同步，不会再出现各件各值的历史问题（v3.24.7 收口）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBlurScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val blurDp = uiState.appSettings.glassBlurRadiusDp
    // v3.47.0：折射配置直接读全局注入值 —— 与真机玻璃件同源，预览即真机观感，
    // 不再是"第二套近似值"（沿用 v3.24.7 定下的规矩）。
    val refraction = LocalGlassRefraction.current
    val refractionAvailable = isGlassRefractionAvailable()
    val onRefractionChange: (GlassRefractionSettings) -> Unit = remember(settingsViewModel) {
        { settingsViewModel.onGlassRefractionChanged(it) }
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.glass_section_title),
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
                .padding(appSpacing().pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap)
        ) {
            Text(
                text = stringResource(Res.string.glass_section_desc),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // 实时预览：底栏胶囊 + 圆钮压在模拟课程格上，改档位立刻看得到雾度
            GlassBlurPreview()

            StyleSliderItem(
                label = stringResource(Res.string.label_glass_blur),
                value = blurDp,
                // 量程必须覆盖全部预设档（0/4/8/16/24）：v3.48.1 档位重标定到 24 后
                // 滑杆仍停在 0..12，选中「朦胧/磨砂」后滑杆顶死、往回拖再也回不去
                //（用户实测"无法调模糊"的根因之一）。
                range = 0f..24f,
                stepValue = 0.5f
            ) { settingsViewModel.onGlassBlurRadiusChanged(it) }

            // 常用档位：关闭 / 清澈 / 标准(默认) / 磨砂
            GlassPresetRow(
                currentDp = blurDp,
                onSelect = { settingsViewModel.onGlassBlurRadiusChanged(it) }
            )

            Text(
                text = stringResource(Res.string.desc_glass_blur),
                style = MaterialTheme.typography.labelSmall,
                color = appColors().textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = appColors().divider, thickness = 0.5.dp)

            // ============ v3.47.0 液态折射（复刻 Kyant/backdrop 的边缘折射光学）============
            // 与上面的「模糊强度」是两个独立维度：模糊决定雾度，这里决定边缘透镜层。
            Text(
                text = stringResource(Res.string.glass_refraction_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
            Text(
                text = stringResource(Res.string.glass_refraction_desc),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            if (!refractionAvailable) {
                // 能力提示：Android 13 以下没有 RuntimeShader，折射无法生效（不静默欺骗用户）
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = stringResource(Res.string.glass_refraction_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    )
                }
            }

            GlassRefractionToggleRow(
                label = stringResource(Res.string.glass_refraction_switch),
                desc = null,
                checked = refraction.enabled && refractionAvailable,
                enabled = refractionAvailable,
                onToggle = { onRefractionChange(refraction.copy(enabled = it)) }
            )

            // 参数只在开启后展开：避免关着的时候还摆一堆无效滑杆
            if (refraction.enabled && refractionAvailable) {
                StyleSliderItem(
                    label = stringResource(Res.string.glass_refraction_height),
                    value = refraction.heightDp,
                    range = GlassRefractionSettings.MIN_HEIGHT_DP..GlassRefractionSettings.MAX_HEIGHT_DP,
                    stepValue = 1f
                ) { onRefractionChange(refraction.copy(heightDp = it)) }

                StyleSliderItem(
                    label = stringResource(Res.string.glass_refraction_amount),
                    value = refraction.amountDp,
                    range = GlassRefractionSettings.MIN_AMOUNT_DP..GlassRefractionSettings.MAX_AMOUNT_DP,
                    stepValue = 1f
                ) { onRefractionChange(refraction.copy(amountDp = it)) }

                GlassRefractionPresetRow(
                    current = refraction,
                    onSelect = onRefractionChange
                )

                GlassRefractionToggleRow(
                    label = stringResource(Res.string.glass_refraction_dispersion),
                    desc = stringResource(Res.string.glass_refraction_dispersion_desc),
                    checked = refraction.dispersion,
                    enabled = true,
                    onToggle = { onRefractionChange(refraction.copy(dispersion = it)) }
                )

                GlassRefractionToggleRow(
                    label = stringResource(Res.string.glass_refraction_depth),
                    desc = stringResource(Res.string.glass_refraction_depth_desc),
                    checked = refraction.depthEffect,
                    enabled = true,
                    onToggle = { onRefractionChange(refraction.copy(depthEffect = it)) }
                )
            }
        }
    }
}

/**
 * 折射开关行：标题 +（可选）描述 + [AppSwitch]，形态与动画设置页的开关行一致。
 */
@Composable
private fun GlassRefractionToggleRow(
    label: String,
    desc: String?,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        onClick = { if (enabled) onToggle(!checked) },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (desc != null) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textSecondary
                    )
                }
            }
            AppSwitch(
                checked = checked,
                onCheckedChange = if (enabled) onToggle else null,
                enabled = enabled
            )
        }
    }
}

/**
 * 折射档位：不折射 / 轻微 / 明显 / 强烈。
 *
 * ⚠️ v3.47.0 修订：初版直接复用了「模糊强度」的档位文案（关闭/清澈/标准/磨砂），
 * 导致同一页出现两排同名按钮 —— 真机上极易把「模糊强度」那一排的「关闭」当成折射档位点掉，
 * 结果模糊被清零、玻璃彻底没雾化（用户实测反馈"玻璃模糊完全无法使用"）。
 * 现改用完全不同的一组措辞，从源头消除歧义。
 */
@Composable
private fun GlassRefractionPresetRow(
    current: GlassRefractionSettings,
    onSelect: (GlassRefractionSettings) -> Unit
) {
    val presets = listOf(
        GlassRefractionSettings.Off to stringResource(Res.string.glass_refraction_preset_none),
        GlassRefractionSettings.Light to stringResource(Res.string.glass_refraction_preset_subtle),
        GlassRefractionSettings.Standard to stringResource(Res.string.glass_refraction_preset_obvious),
        GlassRefractionSettings.Strong to stringResource(Res.string.glass_refraction_preset_strong)
    )
    val selectedColor = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { (preset, label) ->
            val selected = current.enabled == preset.enabled &&
                kotlin.math.abs(current.heightDp - preset.heightDp) < 0.01f &&
                kotlin.math.abs(current.amountDp - preset.amountDp) < 0.01f
            Surface(
                onClick = { onSelect(preset) },
                shape = appShapes().capsule,
                color = if (selected) selectedColor.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = if (selected) BorderStroke(1.dp, selectedColor.copy(alpha = 0.35f)) else null
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) selectedColor else appColors().textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/**
 * 玻璃实时预览：模拟课程格做背板，上面压一颗胶囊与一个圆钮。
 *
 * ⚠️ 拓扑必须与真机一致（v3.24.7 教训）：`hazeSource` 只包背板内容，玻璃件放**平级兄弟层**。
 * 玻璃件若写进同一个 hazeSource 的子树，haze 会按「area.zIndex < 祖先 source.zIndex」
 * 把唯一的背板 area 过滤掉，玻璃件不采样、不模糊——预览就会永远"看不出差别"。
 *
 * 两件玻璃都**不传 blurRadius**，与真机一样走 `liquidGlass` 默认值 →
 * `LocalGlassBlurRadius`（由用户的设置注入），所以预览就是真机观感，不是第二套近似值。
 */
@Composable
private fun GlassBlurPreview() {
    val tokens = appColors()
    // v3.48.0：预览接自带玻璃引擎（与真机同一套实现），不再走 Haze
    val glassBackdrop = rememberGlassBackdrop()

    Surface(
        shape = appShapes().card,
        color = tokens.pageBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
        ) {
            // 1) 背板内容层：玻璃要糊的就是它
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassBackdropSource(glassBackdrop)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val demoBlocks = listOf(
                        Triple(
                            stringResource(Res.string.style_demo_regular_course),
                            stringResource(Res.string.style_demo_teacher),
                            tokens.tone(AccentTone.INFO)
                        ),
                        Triple(
                            stringResource(Res.string.style_demo_conflict_a),
                            stringResource(Res.string.style_demo_position),
                            tokens.tone(AccentTone.AMBER)
                        ),
                        Triple(
                            stringResource(Res.string.style_demo_conflict_b),
                            stringResource(Res.string.style_demo_teacher),
                            tokens.tone(AccentTone.SUCCESS)
                        )
                    )
                    demoBlocks.forEach { (title, sub, semantic) ->
                        DemoCourseBlock(title = title, subtitle = sub, bg = semantic.bg, fg = semantic.fg)
                    }
                }
            }

            // 2) 玻璃胶囊（模拟底栏）——与背板平级
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                LiquidGlass(
                    modifier = Modifier.matchParentSize(),
                    glassBackdrop = glassBackdrop,
                    shape = appShapes().capsule,
                    containerColor = tokens.inputBg,
                    shadowElevation = 10.dp
                )
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DemoNavItem(
                        icon = vectorResource(Res.drawable.view_agenda_24px),
                        label = stringResource(Res.string.nav_today_schedule),
                        selected = false
                    )
                    DemoNavItem(
                        icon = vectorResource(Res.drawable.view_week_24px),
                        label = stringResource(Res.string.nav_course_schedule),
                        selected = true
                    )
                    DemoNavItem(
                        icon = vectorResource(Res.drawable.account_circle_24px),
                        label = stringResource(Res.string.nav_settings),
                        selected = false
                    )
                }
            }

            // 3) 玻璃圆钮（模拟回到本周）——同参数不同尺寸，用来核对大小件雾度是否一致
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                LiquidGlass(
                    modifier = Modifier.fillMaxSize(),
                    glassBackdrop = glassBackdrop,
                    shape = CircleShape,
                    containerColor = tokens.inputBg,
                    shadowElevation = 6.dp
                )
                Icon(
                    vectorResource(Res.drawable.class_24px),
                    contentDescription = null,
                    tint = tokens.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DemoCourseBlock(title: String, subtitle: String, bg: Color, fg: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = fg.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DemoNavItem(icon: ImageVector, label: String, selected: Boolean) {
    val tokens = appColors()
    Row(
        modifier = Modifier
            .clip(appShapes().capsule)
            .background(if (selected) tokens.navSelectedBg else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) tokens.textPrimary else tokens.textSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) tokens.textPrimary else tokens.textSecondary
        )
    }
}

/**
 * 常用档位。`标准` 即默认值 [LiquidGlassBlurRadius]；`0` 关闭模糊（只留表面 tint 与边缘光学）。
 * 注意 haze 在 blurRadius ≥ 7dp 时会把输入降到 0.3334 倍再模糊（见 calculateInputScaleFactor），
 * 所以 8dp 的"磨砂"比 4dp 明显更软，不是线性加倍。
 */
@Composable
private fun GlassPresetRow(currentDp: Float, onSelect: (Float) -> Unit) {
    val presets = listOf(
        0f to stringResource(Res.string.glass_preset_off),
        4f to stringResource(Res.string.glass_preset_light),
        LiquidGlassBlurRadius.value to stringResource(Res.string.glass_preset_standard),
        16f to stringResource(Res.string.glass_preset_misty),
        24f to stringResource(Res.string.glass_preset_heavy)
    )
    val selectedColor = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { (dpValue, label) ->
            val selected = kotlin.math.abs(currentDp - dpValue) < 0.01f
            Surface(
                onClick = { onSelect(dpValue) },
                shape = appShapes().capsule,
                color = if (selected) selectedColor.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = if (selected) BorderStroke(1.dp, selectedColor.copy(alpha = 0.35f)) else null
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) selectedColor else appColors().textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}
