package com.shangkeschedule.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appType
import com.shangkeschedule.ui.theme.claudeGroupBg
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.app_name
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.hero_subtitle

// ============================================================================
// Claude / 书卷 主题设置页组件
// 对齐设计包 claude-style-page.design/pages/profile.html（我的页）
// - 页面大标题 28sp Newsreader 700 + 副标题 13sp
// - 用户行：浅米色条 + 40dp 圆形头像
// - 分组标签：12sp 600 琥珀色、字距 0.05em
// - inset grouped 卡：#f3efe4 浅米色底、14dp 圆角
// - 列表行：48dp 高、28dp 彩色图标容器（8dp 圆角、淡彩底 + 同色图标）、15sp 500 标题
// ============================================================================

/** 书卷主题列表行图标色系：淡彩底 + 同色系图标，取自设计稿 profile.html icon-*。 */
enum class ClaudeCellTone { PURPLE, ORANGE, RED, OLIVE, MATCHA, PINK, GREEN, BROWN, AMBER, GRAY }

private data class ClaudeToneColors(val bg: Color, val fg: Color)

@Composable
private fun claudeToneColors(tone: ClaudeCellTone): ClaudeToneColors {
    // 深色模式下底色加深、前景提亮，保持对比度
    val isDark = LocalIsDarkTheme.current
    return if (isDark) when (tone) {
        ClaudeCellTone.PURPLE -> ClaudeToneColors(Color(0xFF2E2A44), Color(0xFFB7A8F5))
        ClaudeCellTone.ORANGE -> ClaudeToneColors(Color(0xFF3A2A1E), Color(0xFFE8A87A))
        ClaudeCellTone.RED -> ClaudeToneColors(Color(0xFF3A2220), Color(0xFFE88A87))
        ClaudeCellTone.OLIVE -> ClaudeToneColors(Color(0xFF2E3122), Color(0xFFB5C28F))
        ClaudeCellTone.MATCHA -> ClaudeToneColors(Color(0xFF263226), Color(0xFF9FCB9A))
        ClaudeCellTone.PINK -> ClaudeToneColors(Color(0xFF3A242C), Color(0xFFE898B3))
        ClaudeCellTone.GREEN -> ClaudeToneColors(Color(0xFF243021), Color(0xFF9EC89B))
        ClaudeCellTone.BROWN -> ClaudeToneColors(Color(0xFF332620), Color(0xFFC79A7E))
        ClaudeCellTone.AMBER -> ClaudeToneColors(Color(0xFF3A2D1B), Color(0xFFE8B07A))
        ClaudeCellTone.GRAY -> ClaudeToneColors(Color(0xFF2E2D28), Color(0xFFB5B3A8))
    } else when (tone) {
        ClaudeCellTone.PURPLE -> ClaudeToneColors(Color(0xFFECE9FF), Color(0xFF7C6FD4))
        ClaudeCellTone.ORANGE -> ClaudeToneColors(Color(0xFFFBE6D8), Color(0xFFD97C3E))
        ClaudeCellTone.RED -> ClaudeToneColors(Color(0xFFFBE0DE), Color(0xFFD65450))
        ClaudeCellTone.OLIVE -> ClaudeToneColors(Color(0xFFE8ECD6), Color(0xFF7A8A4F))
        ClaudeCellTone.MATCHA -> ClaudeToneColors(Color(0xFFE0EFDA), Color(0xFF5F9A5A))
        ClaudeCellTone.PINK -> ClaudeToneColors(Color(0xFFFBE0E6), Color(0xFFD86485))
        ClaudeCellTone.GREEN -> ClaudeToneColors(Color(0xFFD9ECD3), Color(0xFF6BA368))
        ClaudeCellTone.BROWN -> ClaudeToneColors(Color(0xFFEAD7C9), Color(0xFF9A6B4E))
        ClaudeCellTone.AMBER -> ClaudeToneColors(Color(0xFFFBE6D0), Color(0xFFD98A3E))
        ClaudeCellTone.GRAY -> ClaudeToneColors(Color(0xFFE4E2D9), Color(0xFF7A786C))
    }
}

/** 书卷主题分组卡底色：浅米色 #f3efe4（深色下略加深）。 */
@Composable
fun claudeInsetGroupBg(): Color = claudeGroupBg()

/**
 * 书卷主题页面头部：大标题 28sp + 副标题 13sp。
 * 对齐设计稿 .page-header。
 */
@Composable
fun ClaudePageHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp,
                letterSpacing = (-0.01).sp
            ),
            color = appColors().textPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal),
            color = appColors().textSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * 书卷主题用户行：浅米色圆角条 + 56dp 圆形头像 + 用户名/学校 + chevron。
 * 对齐设计稿 .user-row。
 *
 * v3.49.0：卡片增高（头像 40→56dp、上下内边距 12→18dp），并支持真实头像图
 * （[avatarPath] 非空时渲染图片，否则回落为首字圆弧）；点击进入「我的信息」页。
 */
@Composable
fun ClaudeUserRow(
    name: String,
    school: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatarPath: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(claudeInsetGroupBg())
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (LocalIsDarkTheme.current) Color(0xFF3A2A22) else Color(0xFFF4E0D5)),
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
                    color = appColors().primary
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
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
            tint = appColors().textSecondary.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 书卷主题分组标签：12sp 600 琥珀色、字距 0.05em。
 * 对齐设计稿 .group-label。
 */
@Composable
fun ClaudeGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        ),
        color = appColors().primary,
        modifier = modifier.padding(start = 2.dp, top = 2.dp, bottom = 8.dp)
    )
}

/**
 * 书卷主题 inset grouped 卡：浅米色底、14dp 圆角，内含多个 [ClaudeListItem]。
 * 对齐设计稿 .group-card。
 */
@Composable
fun ClaudeInsetGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(claudeInsetGroupBg()),
        content = content
    )
}

/**
 * 书卷主题列表行：48dp 高 + 28dp 彩色图标容器 + 标题 + 尾部内容。
 * 对齐设计稿 .list-item；分隔线从图标右侧（44dp 处）开始缩进。
 */
@Composable
fun ClaudeListItem(
    title: String,
    icon: ImageVector,
    tone: ClaudeCellTone,
    modifier: Modifier = Modifier,
    detail: String? = null,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {
        Icon(
            imageVector = vectorResource(Res.drawable.chevron_right_24px),
            contentDescription = null,
            tint = appColors().textSecondary.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
    }
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = if (LocalIsDarkTheme.current) Color(0x14FFFFFF) else Color(0x0F000000),
                modifier = Modifier.padding(start = 54.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val colors = claudeToneColors(tone)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.fg,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
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
            trailing()
        }
    }
}

/**
 * 书卷主题开关行尾部：使用 AppSwitch 保持全局一致。
 */
@Composable
fun ClaudeSwitchTrailing(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
}
