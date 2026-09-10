package com.shangkeschedule.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.iosGroupBg
import com.shangkeschedule.ui.theme.iosGlassRim
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.app_name
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.hero_subtitle

// ============================================================================
// 通透主题（iOS 26）设置页组件
//
// 信息架构与「书卷」主题逐项一致（页头 + 分组标签 + inset grouped 卡 + 列表行，
// 行 = 彩色图标容器 + 标题 + 可选 detail + 尾部内容，同一套分组顺序与条目集合），
// 只把视觉与动效换成 iOS 26：
// - 页头：34sp Bold 大标题（iOS Large Title）+ 13sp 次级说明，白卡承载
// - 分组标签：13sp 半粗 + 大写 + 字距（iOS Grouped Table 的标准分组头）
// - 分组卡：16dp 连续圆角 + 玻璃底 + 高光内描边（Liquid Glass 而非暖米色实底）
// - 列表行：52dp 最小行高、28dp 系统色图标徽章、17sp 行标题
// - 分隔线：从图标右侧缩进（与书卷 54dp 缩进同一思路，按 iOS 尺寸为 58dp）
// ============================================================================

/** 通透设置行图标色系：严格取 Apple 系统色（深浅两套由 [iosCellToneColor] 决定）。 */
enum class IosCellTone { BLUE, GREEN, ORANGE, PURPLE, RED, TEAL, INDIGO, PINK, YELLOW, GRAY }

@Composable
fun iosCellToneColor(tone: IosCellTone): Color {
    val isDark = LocalIsDarkTheme.current
    return if (isDark) when (tone) {
        IosCellTone.BLUE -> Color(0xFF0A84FF)     // systemBlue (dark)
        IosCellTone.GREEN -> Color(0xFF30D158)    // systemGreen (dark)
        IosCellTone.ORANGE -> Color(0xFFFF9F0A)   // systemOrange (dark)
        IosCellTone.PURPLE -> Color(0xFFBF5AF2)   // systemPurple (dark)
        IosCellTone.RED -> Color(0xFFFF453A)      // systemRed (dark)
        IosCellTone.TEAL -> Color(0xFF64D2FF)     // systemTeal (dark)
        IosCellTone.INDIGO -> Color(0xFF5E5CE6)   // systemIndigo (dark)
        IosCellTone.PINK -> Color(0xFFFF375F)     // systemPink (dark)
        IosCellTone.YELLOW -> Color(0xFFFFD60A)   // systemYellow (dark)
        IosCellTone.GRAY -> Color(0xFF98989D)     // systemGray (dark)
    } else when (tone) {
        IosCellTone.BLUE -> Color(0xFF007AFF)     // systemBlue
        IosCellTone.GREEN -> Color(0xFF34C759)    // systemGreen
        IosCellTone.ORANGE -> Color(0xFFFF9500)   // systemOrange
        IosCellTone.PURPLE -> Color(0xFFAF52DE)   // systemPurple
        IosCellTone.RED -> Color(0xFFFF3B30)      // systemRed
        IosCellTone.TEAL -> Color(0xFF30B0C7)     // systemTeal
        IosCellTone.INDIGO -> Color(0xFF5856D6)   // systemIndigo
        IosCellTone.PINK -> Color(0xFFFF2D55)     // systemPink
        IosCellTone.YELLOW -> Color(0xFFF7C600)   // systemYellow（浅底上压暗一档保证白图标可读）
        IosCellTone.GRAY -> Color(0xFF8E8E93)     // systemGray
    }
}

/**
 * 通透分组卡底色（inset grouped 卡）。
 * 与书卷的 `claudeInsetGroupBg()` 同职责：都是「承载一组同类行的卡片底色」。
 */
@Composable
fun iosInsetGroupBg(): Color = iosGroupBg()

/**
 * 通透主题页面头部：iOS Large Title 34sp Bold + 13sp 说明。
 *
 * 位置与书卷的 `ClaudePageHeader` 完全一致（内容区顶部、左右页面边距对齐），
 * 只把衬线大标题换成 SF 风格系统无衬线大标题。
 */
@Composable
fun IosPageHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 20.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 41.sp,
                letterSpacing = 0.37.sp
            ),
            color = appColors().textPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            ),
            color = appColors().textSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * 通透主题用户行 / 身份行：白卡 + 44dp 系统蓝→紫渐变圆头像 + 主副标题 + chevron。
 *
 * 位置与职责对齐书卷的 `ClaudeUserRow`（书卷用暖米色条 + 40dp 实色头像）。
 */
@Composable
fun IosUserRow(
    name: String,
    school: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(appShapes().card)
            .background(appColors().cardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = appSpacing().cardInner, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            iosCellToneColor(IosCellTone.BLUE),
                            iosCellToneColor(IosCellTone.PURPLE)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = appColors().textPrimary
            )
            Text(
                text = school,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = appColors().textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = vectorResource(Res.drawable.chevron_right_24px),
            contentDescription = null,
            tint = appColors().textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(15.dp)
        )
    }
}

/**
 * 通透分组标签：13sp SemiBold + 大写 + 0.6sp 字距，灰字。
 * 对齐 iOS Grouped Table 的标准分组头；位置与书卷 `ClaudeGroupLabel` 一致。
 */
@Composable
fun IosGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        ),
        color = appColors().textSecondary,
        modifier = modifier.padding(start = 4.dp, top = 2.dp, bottom = 8.dp)
    )
}

/**
 * 尾部插槽的统一宽度。
 *
 * 为什么固定：行的尾部要么是 15dp 的 chevron（导航行），要么是开关（开关行）。
 * 如果让插槽按内容自适应，两类行会不一致——实测导航行可点区宽 725px、开关行只有 628px。
 * iOS 的 inset grouped 列表里所有行是等宽的，行的点击热区也应当一致：
 * 因此把尾部插槽做成**固定宽度 + 右对齐**的盒子，箭头与开关都居中放在盒子里，
 * 标题的右边界（= 盒子的左边界）对所有行都落在同一个 x 上。
 *
 * 取值 56dp：M3 Switch 的触控区是 48dp × 48dp（轨道视觉宽 52dp），56dp 让开关完整落在
 * 插槽内、左右各留 2dp 呼吸，同时不至于把标题挤窄（标题仍占卡片宽度的绝大部分）。
 */
private val IosTrailingSlot = 56.dp

/**
 * 尾部插槽的统一高度（= 行内容高度）。
 *
 * 为什么固定：M3 Switch 自带 `minimumInteractiveComponentSize` 触控扩张（≥48dp 高），
 * 而导航行的内容（28dp 图标 / 17sp 文字）只有 ~32dp 高。若让插槽按内容自适应，
 * **开关行会被开关的触控区撑高**——实测导航行 143px、开关行 162px（差 19px ≈ 6.3dp），
 * 于是「显示非本周课程 / 是否显示周末」两行明显比其它行高。
 *
 * 把插槽高度钉在 32dp（= 图标徽章高度），开关按可用高度居中排布，
 * 两类行的内容高度就都由图标/文字决定，行高必然一致。
 * 开关自身的触控热区在 32dp 盒内仍可正常点按（M3 Switch 的判定区域为整颗控件）。
 */
private val IosTrailingSlotHeight = 32.dp

/**
 * 通透 inset grouped 卡：16dp 连续圆角 + 卡片底 + Liquid Glass 高光内描边。
 *
 * 与书卷 `ClaudeInsetGroup` 同结构（Column + 圆角 + 分组底），只换材质与圆角阶梯。
 */
@Composable
fun IosSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(appShapes().card)
            .background(iosInsetGroupBg())
            .iosGlassRim(appShapes().card),
        content = content
    )
}

/**
 * 通透设置列表行：52dp 最小行高 + 28dp 系统色图标徽章 + 17sp 标题 + 可选 detail + 固定尾部插槽。
 *
 * 位置、信息层级与书卷 `ClaudeListItem` 逐项一致（唯一差别是图标容器为
 * iOS 的实色系统色徽章 + 白图标，书卷是淡彩底 + 同色图标）。
 * 分隔线左缩进 = 16 页内距 + 28 图标 + 14 间隔 = 58dp。
 *
 * **所有行等宽**：行容器 `fillMaxWidth()` 撑满卡片，尾部统一走 [IosTrailingSlot]
 * 固定宽插槽（右对齐），因此导航行与开关行在视觉、点击热区、语义范围上完全一致。
 *
 * [trailing] 传 `null` 表示使用默认 chevron（导航行）；传内容表示自定义尾部
 * （开关行传 [IosSwitchTrailing]）。两种情况的插槽宽度相同。
 */
@Composable
fun IosSettingCell(
    title: String,
    icon: ImageVector,
    tone: IosCellTone,
    modifier: Modifier = Modifier,
    detail: String? = null,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = appColors().divider,
                modifier = Modifier.padding(start = 58.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = appSpacing().rowMinHeight)
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(start = appSpacing().cardInner, end = appSpacing().cardInner, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(iosCellToneColor(tone)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.41).sp
                ),
                color = appColors().textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = appColors().textSecondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            // 固定宽 × 固定高尾部插槽：箭头 / 开关都在同一个盒子里居中。
            // 高度钉住是关键——否则开关自带的 48dp 触控扩张会把开关行撑高，
            // 导致「显示非本周课程 / 是否显示周末」比其它行高一截（实测差 19px）。
            Box(
                modifier = Modifier
                    .width(IosTrailingSlot)
                    .height(IosTrailingSlotHeight),
                contentAlignment = Alignment.Center
            ) {
                if (trailing != null) {
                    trailing()
                } else {
                    Icon(
                        imageVector = vectorResource(Res.drawable.chevron_right_24px),
                        contentDescription = null,
                        tint = appColors().textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/** 通透设置行尾部开关：沿用全局 AppSwitch（iOS 26 胶囊开关形态由 AppSwitch 承载）。 */
@Composable
fun IosSwitchTrailing(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    com.shangkeschedule.ui.components.AppSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

/** 页头默认使用的应用名（供各页面复用，避免重复 stringResource 调用点）。 */
@Composable
fun iosHeaderTitle(): String = stringResource(Res.string.app_name)

/** 页头默认使用的副标题。 */
@Composable
fun iosHeaderSubtitle(): String = stringResource(Res.string.hero_subtitle)
