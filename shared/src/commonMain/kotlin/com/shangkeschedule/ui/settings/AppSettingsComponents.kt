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
import com.shangkeschedule.ui.theme.SettingsAvatarInitialColor
import com.shangkeschedule.ui.theme.SettingsAvatarStyle
import com.shangkeschedule.ui.theme.SettingsDividerColor
import com.shangkeschedule.ui.theme.SettingsEntryTone
import com.shangkeschedule.ui.theme.SettingsIconMaterial
import com.shangkeschedule.ui.theme.SettingsLabelColor
import com.shangkeschedule.ui.theme.SettingsRowHeight
import com.shangkeschedule.ui.theme.SettingsSurfaceMaterial
import com.shangkeschedule.ui.theme.SettingsTrailingSlot
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSettingsGroup
import com.shangkeschedule.ui.theme.appSettingsLabel
import com.shangkeschedule.ui.theme.appSettingsRow
import com.shangkeschedule.ui.theme.appSettingsUserRow
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.claudeGroupBg
import com.shangkeschedule.ui.theme.iosAvatarGradientColors
import com.shangkeschedule.ui.theme.iosGlassRim
import com.shangkeschedule.ui.theme.iosGroupBg
import com.shangkeschedule.ui.theme.settingsToneColors
import com.shangkeschedule.ui.theme.softAvatarGradientColors
import com.shangkeschedule.ui.theme.softFeatherRim
import com.shangkeschedule.ui.theme.softGlow
import com.shangkeschedule.ui.theme.softSurface
import com.shangkeschedule.ui.theme.softTexture
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.chevron_right_24px

// ============================================================================
// A1 / V3（v3.69.0）：设置页组件族三合一
//
// 原状：`Claude/Ios/SoftSettingsComponents.kt` 三份文件、~1079 行近乎 1:1 重复——
// 三个 UserRow、三个 GroupLabel、三个 SettingsGroup、三个 SettingCell，角色一一对应，
// 差异全在参数（尺寸 / 字阶 / 材质 / 图标画法）。
//
// 问题不在「参数没进 token」，而在「有三份组件」：只要组件是三份，差异就永远无法
// 用 token 表达——每个文件各自硬编码自己的值，token 无从插入。
//
// 合一后差异退化为参数，改由 [appSettingsRow] / [appSettingsGroup] /
// [appSettingsLabel] / [appSettingsUserRow] 四组 token 提供；本文件只保留一份实现。
// 像素回归口径：每个字段的取值都取自对应主题的原实现。
// ============================================================================

/** 尾部插槽固定宽 / 高：保证导航行与开关行等宽等高（通透 / 柔绘共用）。 */
private val SettingsTrailingSlotWidth = 56.dp
private val SettingsTrailingSlotHeight = 32.dp

/** 图标徽章内的图标尺寸：三主题一致。 */
private val SettingsRowIconSize = 16.dp

/**
 * 设置分组标签（原 ClaudeGroupLabel / IosGroupLabel / SoftGroupLabel 合一）。
 *
 * 差异：字阶（12/13sp）、字重、字距、是否大写、取主色还是次级文本色、左缩进。
 */
@Composable
fun AppGroupLabel(text: String, modifier: Modifier = Modifier) {
    val t = appSettingsLabel()
    val colors = appColors()
    Text(
        text = if (t.uppercase) text.uppercase() else text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = t.fontSize,
            fontWeight = t.fontWeight,
            letterSpacing = t.letterSpacing
        ),
        color = when (t.color) {
            SettingsLabelColor.PRIMARY -> colors.primary
            SettingsLabelColor.SECONDARY -> colors.textSecondary
        },
        modifier = modifier.padding(start = t.startPadding, top = 2.dp, bottom = 8.dp)
    )
}

/**
 * 设置分组卡（原 ClaudeInsetGroup / IosSettingsGroup / SoftSettingsGroup 合一）。
 *
 * 三种材质是**不同画法**（纸感整块 / 白卡 + 玻璃高光 / 软模糊 + 手绘肌理），
 * 故用 [SettingsSurfaceMaterial] 枚举分派，不用数值参数硬凑。
 */
@Composable
fun AppSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val g = appSettingsGroup()
    val surface: Modifier = when (g.material) {
        SettingsSurfaceMaterial.PAPER -> Modifier
            .clip(RoundedCornerShape(g.cornerRadius))
            .background(claudeGroupBg())

        SettingsSurfaceMaterial.CARD -> {
            val shape = appShapes().card
            Modifier
                .clip(shape)
                .background(iosGroupBg())
                .then(if (g.glassRim) Modifier.iosGlassRim(shape) else Modifier)
        }

        SettingsSurfaceMaterial.SOFT_BLUR -> {
            val shape = appShapes().card
            Modifier
                .softSurface(shape = shape, elevation = g.elevation)
                .then(if (g.texture) Modifier.softTexture(shape) else Modifier)
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(surface),
        content = content
    )
}

/**
 * 设置列表行（原 ClaudeListItem / IosSettingCell / SoftSettingCell 合一）。
 *
 * 信息层级与顺序三主题逐项一致：分隔线 → 图标徽章 → 标题 → detail → 尾部插槽。
 * 差异全部走 [appSettingsRow]（行高策略 / 分隔线取色与缩进 / 内距 / 图标画法 /
 * 字阶 / 箭头尺寸与透明度 / 尾部插槽形态）。
 *
 * [tone] 是**语义角色**（如「课程管理 = PURPLE」），具体颜色由主题层
 * [settingsToneColors] 决定——组件不再接触任何主题专属色调枚举。
 */
@Composable
fun AppSettingRow(
    title: String,
    icon: ImageVector,
    tone: SettingsEntryTone,
    modifier: Modifier = Modifier,
    detail: String? = null,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val r = appSettingsRow()
    val colors = appColors()
    val toneColors = settingsToneColors(tone)
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = when (r.dividerColor) {
                    SettingsDividerColor.SCRIM -> if (LocalIsDarkTheme.current) {
                        Color(0x14FFFFFF)
                    } else {
                        Color(0x0F000000)
                    }
                    SettingsDividerColor.DIVIDER_TOKEN -> colors.divider
                },
                modifier = Modifier.padding(start = r.dividerInset)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when (r.rowHeight) {
                        SettingsRowHeight.FIXED_48 -> Modifier.height(48.dp)
                        SettingsRowHeight.MIN_ROW_MIN_HEIGHT ->
                            Modifier.defaultMinSize(minHeight = appSpacing().rowMinHeight)
                    }
                )
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = r.paddingHorizontal, vertical = r.paddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconShape = RoundedCornerShape(r.iconBoxRadius)
            Box(
                modifier = Modifier
                    .size(r.iconBoxSize)
                    .clip(iconShape)
                    .background(toneColors.bg)
                    .then(
                        if (r.iconMaterial == SettingsIconMaterial.SOFT_FEATHER) {
                            Modifier.softFeatherRim(iconShape)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = toneColors.fg,
                    modifier = Modifier.size(SettingsRowIconSize)
                )
            }
            Spacer(modifier = Modifier.width(r.iconGap))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = r.titleSize,
                    fontWeight = r.titleWeight,
                    letterSpacing = r.titleLetterSpacing
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = r.detailSize),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            if (r.trailingSlot == SettingsTrailingSlot.FIXED_56X32) {
                // 固定宽 × 固定高尾部插槽：箭头 / 开关都在同一个盒子里居中。
                // 高度钉住是关键——否则开关自带的 48dp 触控扩张会把开关行撑高
                // （实测开关行比导航行高 19px）。
                Box(
                    modifier = Modifier
                        .width(SettingsTrailingSlotWidth)
                        .height(SettingsTrailingSlotHeight),
                    contentAlignment = Alignment.Center
                ) {
                    if (trailing != null) trailing() else SettingsChevron(r.chevronSize, r.chevronAlpha)
                }
            } else if (trailing != null) {
                trailing()
            } else {
                SettingsChevron(r.chevronSize, r.chevronAlpha)
            }
        }
    }
}

@Composable
private fun SettingsChevron(size: androidx.compose.ui.unit.Dp, alpha: Float) {
    Icon(
        imageVector = vectorResource(Res.drawable.chevron_right_24px),
        contentDescription = null,
        tint = appColors().textSecondary.copy(alpha = alpha),
        modifier = Modifier.size(size)
    )
}

/**
 * 设置页身份行（原 ClaudeUserRow / IosUserRow / SoftUserRow 合一）。
 *
 * 头像三种画法（书卷实色 / 通透系统色渐变 / 柔绘晕染渐变 + 柔光）由
 * [SettingsAvatarStyle] 枚举分派；头像底色的具体色值由主题层提供，本文件不持有色表。
 */
@Composable
fun AppSettingsUserRow(
    name: String,
    school: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatarPath: String? = null
) {
    val u = appSettingsUserRow()
    val colors = appColors()
    val surface: Modifier = when (u.material) {
        SettingsSurfaceMaterial.PAPER -> Modifier
            .clip(RoundedCornerShape(u.cornerRadius))
            .background(claudeGroupBg())

        SettingsSurfaceMaterial.CARD -> Modifier
            .clip(appShapes().card)
            .background(colors.cardBg)

        SettingsSurfaceMaterial.SOFT_BLUR -> Modifier.softSurface(
            shape = appShapes().card,
            elevation = u.elevation
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(surface)
            .clickable(onClick = onClick)
            .padding(horizontal = u.paddingHorizontal, vertical = u.paddingVertical),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val gradient = when (u.avatarStyle) {
            SettingsAvatarStyle.SOLID -> null
            SettingsAvatarStyle.SYSTEM_GRADIENT -> iosAvatarGradientColors()
            SettingsAvatarStyle.SOFT_GLOW_GRADIENT -> softAvatarGradientColors()
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .then(
                    if (gradient != null) {
                        Modifier.background(Brush.linearGradient(colors = gradient))
                    } else {
                        Modifier.background(
                            if (LocalIsDarkTheme.current) Color(0xFF3A2A22) else Color(0xFFF4E0D5)
                        )
                    }
                )
                .then(
                    if (u.avatarStyle == SettingsAvatarStyle.SOFT_GLOW_GRADIENT) {
                        Modifier.softGlow(CircleShape)
                    } else {
                        Modifier
                    }
                ),
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
                        fontWeight = u.avatarInitialWeight
                    ),
                    color = when (u.avatarInitialColor) {
                        SettingsAvatarInitialColor.PRIMARY -> colors.primary
                        SettingsAvatarInitialColor.WHITE -> Color.White
                    }
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = u.nameSize,
                    fontWeight = u.nameWeight
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = school,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SettingsChevron(u.chevronSize, u.chevronAlpha)
    }
}
