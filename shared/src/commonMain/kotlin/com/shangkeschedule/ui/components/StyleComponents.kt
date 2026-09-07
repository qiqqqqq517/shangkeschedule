package com.shangkeschedule.ui.components

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.remember
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.shangkeschedule.ui.theme.AppAlpha
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.AccentTone
import com.shangkeschedule.ui.theme.AppColorTokens
import com.shangkeschedule.ui.theme.AppSemanticColors
import com.shangkeschedule.ui.theme.AppShape
import com.shangkeschedule.ui.theme.AppSpacing
import com.shangkeschedule.ui.theme.AppType
import com.shangkeschedule.ui.theme.appColors

/**
 * 风格基线通用组件（v2 规范 §2 / §4）。
 *
 * 全部为纯视觉皮肤：白卡、图标 chip、渐变头卡、Telegram 形态的
 * 溢出菜单 / 徽标 / 开关 / Snackbar / 分段控件。行为一律由调用方既有逻辑提供。
 */

/**
 * 基线白卡：白底、20dp 圆角、无边框、极轻投影（y≈2 blur≈8 6–8% 黑）。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = AppShape.card,
    containerColor: Color? = null,
    elevation: Int = 2,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = appColors()
    val container = containerColor ?: tokens.cardBg
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation.dp,
                shape = shape,
                clip = false,
                ambientColor = tokens.shadow,
                spotColor = tokens.shadow
            )
            .clip(shape)
            .background(container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(content = content)
    }
}

/**
 * 图标 chip：48dp、14dp 圆角、语义淡底 + 单色前景图标（列表行 / 菜单项通用）。
 */
@Composable
fun IconChip(
    icon: ImageVector,
    tone: AccentTone,
    modifier: Modifier = Modifier,
    size: Dp = AppSpacing.chipIcon,
    cornerRadius: Dp = AppShape.chipSmallRadius,
    iconSize: Dp = 22.dp,
    semantic: AppSemanticColors? = null
) {
    val tokens = appColors()
    val colors = semantic ?: tokens.tone(tone)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.fg,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * 头部渐变卡：渐变（左上 → 右下）、24dp 圆角，内容为白色 / 半透明白。
 * 融合配图（motif=true）：柔光斑 ×2 + 课程格纸插画（右缘溢出裁剪，与卡片无缝融合）；
 * 插画元素用 colorScheme.onPrimary，任意用户主题色相下对比自动协调。
 */
@Composable
fun GradientHeroCard(
    modifier: Modifier = Modifier,
    motif: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = appColors()
    val isDark = LocalIsDarkTheme.current
    Box(
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = AppShape.heroCard,
                clip = false,
                ambientColor = tokens.shadow,
                spotColor = tokens.shadow
            )
            .clip(AppShape.heroCard)
            .background(
                Brush.linearGradient(
                    colors = listOf(tokens.gradientStart, tokens.gradientEnd)
                )
            )
    ) {
        if (motif) {
            // 柔光斑 ×2：大面积低对比景深光晕（深色减半避免脏灰斑）
            val blobAlpha = if (isDark) AppAlpha.faint / 2 else AppAlpha.faint
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(190.dp)
                    .offset(x = (-36).dp, y = (-48).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = blobAlpha), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(150.dp)
                    .offset(x = 28.dp, y = 34.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = blobAlpha), Color.Transparent)
                        )
                    )
            )
            // 课程格纸插画：右缘溢出裁剪，与渐变无缝融合；装饰元素无障碍语义置空
            val rtlSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .graphicsLayer { rotationZ = 8f * rtlSign }
                    .offset(x = -14.dp * rtlSign)
            ) {
                AppHeroMotif(tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Column(content = content)
    }
}

/**
 * 课程格纸母题插画（Hero / 空态融合配图，v2 规范「融景卡片」）：
 * 3 条圆角课程块（中一条高亮呼吸）+ 底部时间刻度线，模拟迷你课表。
 * 所有元素基于 [tint]（渐变语境传 onPrimary，空态语境传 primary）+ AppAlpha 档位；
 * 高亮块呼吸 2s 循环（0.55↔1.0）。
 */
@Composable
fun AppHeroMotif(
    modifier: Modifier = Modifier,
    tint: Color,
    pulse: Boolean = true,
    blockWidth: Dp = 64.dp
) {
    val pulseAlpha: Float = if (pulse) {
        val infinite = rememberInfiniteTransition(label = "appHeroMotif")
        infinite.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1000), RepeatMode.Reverse),
            label = "motifPulse"
        ).value
    } else {
        1f
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        HeroMotifBlock(blockWidth, tint.copy(alpha = AppAlpha.dimmed))
        HeroMotifBlock(blockWidth * 0.78f, tint.copy(alpha = pulseAlpha))
        HeroMotifBlock(blockWidth * 0.6f, tint.copy(alpha = AppAlpha.dimmed))
        HeroMotifBlock(blockWidth * 0.42f, tint.copy(alpha = AppAlpha.dimmed), height = 6.dp)
    }
}

/** 母题单条课程块。 */
@Composable
private fun HeroMotifBlock(width: Dp, color: Color, height: Dp = 12.dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
    )
}

/**
 * Telegram 形态溢出菜单：白圆角 16dp 卡、图标 + 文字行高 52dp、组间分隔线。
 * 只承载调用方传入的既有操作入口。
 */
@Composable
fun TelegramMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = appColors()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = AppShape.menu,
        containerColor = tokens.cardBg,
        shadowElevation = 8.dp,
        content = content
    )
}

/**
 * Telegram 菜单项：图标 + 文字行，行高 52dp，按压 ripple。
 */
@Composable
fun TelegramMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: AccentTone = AccentTone.PRIMARY,
    danger: Boolean = false
) {
    val tokens = appColors()
    val iconTone = if (danger) AccentTone.DANGER else tone
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.tone(iconTone).fg,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = AppType.body),
            fontWeight = FontWeight.Medium,
            color = if (danger) tokens.danger else tokens.textPrimary,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}

/** Telegram 菜单组间分隔线（细、通栏）。 */
@Composable
fun TelegramMenuDivider() {
    val tokens = appColors()
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        thickness = 0.5.dp,
        color = tokens.divider
    )
}

/**
 * Telegram 计数徽标：灰底白字胶囊，>99 显示 99+。
 */
@Composable
fun AppBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    val tokens = appColors()
    val text = if (count > 99) "99+" else count.toString()
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(tokens.badgeBg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = AppType.badge),
            fontWeight = FontWeight.Bold,
            color = tokens.badgeFg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 统一开关：大号胶囊，ON = 主题绿，OFF = 浅灰（v2 规范 §2）。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val tokens = appColors()
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = tokens.success,
            checkedBorderColor = Color.Transparent,
            checkedIconColor = tokens.success,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = tokens.inputBg,
            uncheckedBorderColor = Color.Transparent,
            uncheckedIconColor = tokens.inputBg
        )
    )
}

/**
 * 统一 Snackbar 宿主：深色圆角 16dp 条（Telegram 形态），深浅色两套一致。
 * 底色/文字色走 [AppColorTokens.snackbarBg]/[snackbarFg]，不再依赖深浅色推断。
 */
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val tokens = appColors()
    SnackbarHost(hostState = hostState, modifier = modifier) { data: SnackbarData ->
        Snackbar(
            data,
            shape = AppShape.menu,
            containerColor = tokens.snackbarBg,
            contentColor = tokens.snackbarFg,
            actionColor = tokens.primary
        )
    }
}

/**
 * 基线分段控件：浅灰胶囊容器 + 白色选中胶囊（带投影）+ 未选中灰字。
 */
@Composable
fun AppSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = appColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(tokens.inputBg)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.shadow(
                                elevation = 2.dp,
                                shape = CircleShape,
                                clip = false,
                                ambientColor = tokens.shadow,
                                spotColor = tokens.shadow
                            )
                        } else Modifier
                    )
                    .background(if (selected) tokens.cardBg else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) tokens.textPrimary else tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * FAB 按压缩放反馈（v2 规范 §4.1：按压缩放 + 大投影）。
 * 返回按下状态对应的缩放值，供 graphicsLayer 使用。
 */
@Composable
fun rememberFabPressedScale(interactionSource: MutableInteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    return if (pressed) 0.92f else 1f
}

/**
 * 柔和输入框（弹窗用）：填充式 14dp 圆角 + 淡灰底 + 无描边指示线（Telegram 输入风格），
 * 取代 M3 默认的方硬 OutlinedTextField。
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val tokens = appColors()
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = if (label != null) {
            { Text(label) }
        } else {
            null
        },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        isError = isError,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon,
        supportingText = supportingText,
        trailingIcon = trailingIcon,
        shape = AppShape.chipSmall,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = tokens.inputBg,
            unfocusedContainerColor = tokens.inputBg,
            disabledContainerColor = tokens.inputBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = tokens.textPrimary,
            unfocusedTextColor = tokens.textPrimary,
            focusedLabelColor = tokens.primary,
            unfocusedLabelColor = tokens.textSecondary,
            cursorColor = tokens.primary
        )
    )
}

/**
 * 柔和对话框操作区：取消 = 灰字文本钮，确认 = 主色胶囊实心钮（Telegram 风格）。
 * 两个按钮等宽铺满，替换 M3 默认的右下角双 TextButton。
 * [danger] = true 时确认钮使用危险色（删除/重置等不可逆操作）。
 */
@Composable
fun AppDialogActions(
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    danger: Boolean = false
) {
    val tokens = appColors()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dismissText != null && onDismiss != null) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = AppSpacing.touchMin)
            ) {
                Text(
                    dismissText,
                    color = tokens.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier
                .then(if (dismissText != null && onDismiss != null) Modifier.weight(1f) else Modifier)
                .heightIn(min = AppSpacing.touchMin),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (danger) tokens.danger else tokens.primary,
                contentColor = Color.White,
                disabledContainerColor = (if (danger) tokens.danger else tokens.primary).copy(alpha = 0.45f),
                disabledContentColor = Color.White
            )
        ) {
            Text(confirmText, fontWeight = FontWeight.Bold)
        }
    }
}
