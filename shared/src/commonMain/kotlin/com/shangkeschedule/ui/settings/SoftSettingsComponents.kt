package com.shangkeschedule.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.softFeatherRim
import com.shangkeschedule.ui.theme.softGlow
import com.shangkeschedule.ui.theme.softShadow
import com.shangkeschedule.ui.theme.softSurface
import com.shangkeschedule.ui.theme.softTexture
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.app_name
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.hero_subtitle

// ============================================================================
// 柔绘主题设置页组件
//
// 信息架构与「书卷」「通透」逐项一致（页头 + 分组标签 + 分组卡 + 列表行，
// 行 = 彩色图标容器 + 标题 + 可选 detail + 尾部内容，同一套分组顺序与条目集合），
// 只把材质换成柔绘：
// - 页头：轻字重大标题 + 说明，无描边
// - 分组标签：13sp Medium + 松字距，低对比灰
// - 分组卡：24dp 虚化圆角 + 薄涂底 + 漫射柔光 + 手绘纹理 + 软模糊投影 + 羽化描边
// - 列表行：52dp 行高（与通透一致）、30dp 圆角图标容器（晕染底 + 同色图标，不用实色徽章）
// - 分隔线：12% 正文色极淡线，左缩进对齐图标右侧
// ============================================================================

/** 柔绘设置行图标色系：低饱和马卡龙档（与 SoftStyle.kt 语义色同源）。 */
enum class SoftCellTone { LILAC, SAGE, APRICOT, ROSE, MIST, MAUVE, SAND, CLAY, FERN, LAVENDER, COCOA, STEEL }

@Composable
fun softCellToneColor(tone: SoftCellTone): Color {
    val isDark = LocalIsDarkTheme.current
    return if (isDark) when (tone) {
        SoftCellTone.LILAC -> Color(0xFF9AA3DC)
        SoftCellTone.SAGE -> Color(0xFF97C4A6)
        SoftCellTone.APRICOT -> Color(0xFFE0BB96)
        SoftCellTone.ROSE -> Color(0xFFDBAAC0)
        SoftCellTone.MIST -> Color(0xFF9CC0D8)
        SoftCellTone.MAUVE -> Color(0xFFB3ACCF)
        SoftCellTone.SAND -> Color(0xFFE0CD9E)
        SoftCellTone.CLAY -> Color(0xFFDDA0A0)
        SoftCellTone.FERN -> Color(0xFFA8C0B8)
        SoftCellTone.LAVENDER -> Color(0xFFBDB2D6)
        SoftCellTone.COCOA -> Color(0xFFD6BFA8)
        SoftCellTone.STEEL -> Color(0xFFA8B8D6)
    } else when (tone) {
        SoftCellTone.LILAC -> Color(0xFF7C86C9)
        SoftCellTone.SAGE -> Color(0xFF7BAE8C)
        SoftCellTone.APRICOT -> Color(0xFFD9A97E)
        SoftCellTone.ROSE -> Color(0xFFC98FA8)
        SoftCellTone.MIST -> Color(0xFF7FA8C4)
        SoftCellTone.MAUVE -> Color(0xFF9A93B8)
        SoftCellTone.SAND -> Color(0xFFD8C089)
        SoftCellTone.CLAY -> Color(0xFFCC8A8A)
        SoftCellTone.FERN -> Color(0xFF8FA8A0)
        SoftCellTone.LAVENDER -> Color(0xFFA89AC4)
        SoftCellTone.COCOA -> Color(0xFFC4A88F)
        SoftCellTone.STEEL -> Color(0xFF8FA0C4)
    }
}

/** 柔绘薄涂淡底：图标容器的底（16% 同色，低对比）。 */
@Composable
fun softCellToneSoft(tone: SoftCellTone): Color =
    softCellToneColor(tone).copy(alpha = if (LocalIsDarkTheme.current) 0.22f else 0.16f)

/**
 * 柔绘主题页面头部：轻字重大标题 + 说明。
 *
 * 位置与书卷 `ClaudePageHeader` / 通透 `IosPageHeader` 完全一致（内容区顶部、左右页边距对齐）。
 */
@Composable
fun SoftPageHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 20.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 38.sp,
                letterSpacing = 0.2.sp
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
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * 柔绘身份行：薄涂卡 + 56dp 晕染圆头像 + 主副标题 + 尾部箭头。
 * 位置与职责对齐书卷 `ClaudeUserRow` / 通透 `IosUserRow`。
 *
 * v3.49.0：卡片增高（头像 46→56dp、上下内边距 14→18dp），并支持真实头像图
 * （[avatarPath] 非空时渲染图片，否则回落为双色晕染首字）；点击进入「我的信息」页。
 */
@Composable
fun SoftUserRow(
    name: String,
    school: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatarPath: String? = null
) {
    val shape = appShapes().card
    Row(
        modifier = modifier
            .fillMaxWidth()
            .softSurface(shape = shape, elevation = 8.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = appSpacing().cardInner, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 柔绘头像：两色柔和晕染（不用实色渐变，保留薄涂感）
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            softCellToneColor(SoftCellTone.LILAC).copy(alpha = 0.85f),
                            softCellToneColor(SoftCellTone.MAUVE).copy(alpha = 0.75f)
                        )
                    )
                )
                .softGlow(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarPath.isNullOrBlank()) {
                AsyncImage(
                    model = avatarPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = name.take(1),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = appColors().textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
            tint = appColors().textSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(15.dp)
        )
    }
}

/**
 * 柔绘分组标签：13sp Medium + 松字距 + 低对比灰。
 * 位置与书卷 `ClaudeGroupLabel` / 通透 `IosGroupLabel` 一致。
 */
@Composable
fun SoftGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp
        ),
        color = appColors().textSecondary,
        modifier = modifier.padding(start = 6.dp, top = 2.dp, bottom = 8.dp)
    )
}

/**
 * 柔绘分组卡：24dp 虚化圆角 + 薄涂底 + 漫射柔光 + 手绘纹理 + 软模糊投影 + 羽化描边。
 *
 * 与书卷 `ClaudeInsetGroup`（暖米色实底 + 0.5dp 描边）、
 * 通透 `IosSettingsGroup`（白卡 + 玻璃高光描边）同结构，只换材质。
 */
@Composable
fun SoftSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = appShapes().card
    Column(
        modifier = modifier
            .fillMaxWidth()
            .softSurface(shape = shape, elevation = 10.dp)
            .softTexture(shape),
        content = content
    )
}

/**
 * 尾部插槽统一尺寸。
 *
 * 与通透同一约束：行的尾部要么是 15dp 箭头，要么是带 48dp 触控扩张的开关；
 * 若按内容自适应，两类行的宽高与点击热区都会不一致（实测开关行被撑高 19px）。
 * 固定宽 × 固定高即可让两类行完全等宽等高。
 */
private val SoftTrailingSlot = 56.dp
private val SoftTrailingSlotHeight = 32.dp

/**
 * 柔绘设置列表行：52dp 最小行高（与通透一致，见 softSpacingTokens 注释）+ 30dp 晕染图标容器 +
 * 16sp 标题 + 可选 detail + 固定尾部插槽。
 *
 * 位置、信息层级与书卷 `ClaudeListItem` / 通透 `IosSettingCell` 逐项一致。
 * 分隔线左缩进 = 18 页内距 + 30 图标 + 14 间隔 = 62dp。
 * 柔绘的分隔线极淡（12% 正文色），符合「低对比 + 无锐利硬边缘」。
 */
@Composable
fun SoftSettingCell(
    title: String,
    icon: ImageVector,
    tone: SoftCellTone,
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
                modifier = Modifier.padding(start = 62.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = appSpacing().rowMinHeight)
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(
                    start = appSpacing().cardInner,
                    end = appSpacing().cardInner,
                    top = 10.dp,
                    bottom = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 柔绘图标容器：30dp 虚化圆角 + 晕染淡底 + 同色图标（不用实色徽章 + 白图标，
            // 实色徽章在低对比界面上会形成硬色块）
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(softCellToneSoft(tone))
                    .softFeatherRim(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = softCellToneColor(tone),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.05).sp
                ),
                color = appColors().textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = appColors().textSecondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Box(
                modifier = Modifier
                    .width(SoftTrailingSlot)
                    .height(SoftTrailingSlotHeight),
                contentAlignment = Alignment.Center
            ) {
                if (trailing != null) {
                    trailing()
                } else {
                    Icon(
                        imageVector = vectorResource(Res.drawable.chevron_right_24px),
                        contentDescription = null,
                        tint = appColors().textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/** 柔绘设置行尾部开关：沿用全局 AppSwitch（形态由主题的 success 色承载）。 */
@Composable
fun SoftSwitchTrailing(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    com.shangkeschedule.ui.components.AppSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

/** 页头默认应用名。 */
@Composable
fun softHeaderTitle(): String = stringResource(Res.string.app_name)

/** 页头默认副标题。 */
@Composable
fun softHeaderSubtitle(): String = stringResource(Res.string.hero_subtitle)
