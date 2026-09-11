package com.shangkeschedule.ui.components

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import com.shangkeschedule.ui.theme.AppAlpha
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appSurface
import com.shangkeschedule.ui.theme.appType
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.AccentTone
import com.shangkeschedule.ui.theme.AppColorTokens
import com.shangkeschedule.ui.theme.AppSemanticColors
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.softFeatherRim
import com.shangkeschedule.ui.theme.softShadow
import com.shangkeschedule.data.model.AppThemePreset
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.check_24px

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
    shape: RoundedCornerShape = appShapes().card,
    containerColor: Color? = null,
    elevation: Int = 2,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // 材质 / 边缘全部交给统一表面渲染入口（appSurface），按主题在唯一一处分派：
    // 书卷 = 极轻投影 + 暖米分组底 + 0.5dp 实色描边；通透 = 白卡 + 玻璃高光内描边；
    // 柔绘 = 软模糊投影 + 漫射柔光 + 羽化描边（默认分组卡再叠手绘肌理）。
    // 此前柔绘分支在本组件内又叠了一遍 softFeatherRim，与 softSurface 内含的重复 → 已收口。
    Box(
        modifier = modifier
            .appSurface(
                shape = shape,
                containerColor = containerColor,
                elevation = 10.dp,
                texture = true
            )
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
    size: Dp = appSpacing().chipIcon,
    cornerRadius: Dp = appShapes().chipSmallRadius,
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
 * 融合配图（motif=true）：柔光斑 ×2 + 课程格纸插画（整组位于卡片右缘内侧，微倾 8° 不溢出裁剪）；
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
                shape = appShapes().heroCard,
                clip = false,
                ambientColor = tokens.shadow,
                spotColor = tokens.shadow
            )
            .clip(appShapes().heroCard)
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
            // 课程格纸插画：整组收在卡片右缘内侧（预留旋转摆动余量，不溢出裁剪）；装饰元素无障碍语义置空
            val rtlSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .graphicsLayer { rotationZ = 8f * rtlSign }
                    .offset(x = 22.dp * rtlSign)
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
    blockWidth: Dp = 64.dp,
    /**
     * true = 只呼吸**一次**后静止（空状态 / 详情面板用）。
     * v3.43.0：HIG 明确反对常驻装饰动效，空态这类"停留很久"的场景不应循环呼吸。
     */
    singlePulse: Boolean = false
) {
    // v3.26.0 动效收口：呼吸节奏读全局令牌（pulseDurationMs），随动画风格变化
    val motion = LocalAppMotion.current
    val pulseAlpha: Float = when {
        singlePulse -> {
            val duration = motion.tokens.pulseDurationMs
            if (duration <= 0) {
                1f
            } else {
                val oneShot = remember { Animatable(0.55f) }
                LaunchedEffect(Unit) {
                    oneShot.snapTo(0.55f)
                    oneShot.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = (duration * 1.2f).toInt(),
                            easing = LinearOutSlowInEasing
                        )
                    )
                }
                oneShot.value
            }
        }

        pulse -> {
            val infinite = rememberInfiniteTransition(label = "appHeroMotif")
            infinite.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(durationMillis = motion.tokens.pulseDurationMs.coerceAtLeast(1)),
                    RepeatMode.Reverse
                ),
                label = "motifPulse"
            ).value
        }

        else -> 1f
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
        shape = appShapes().menu,
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
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = appType().body),
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
            style = MaterialTheme.typography.labelSmall.copy(fontSize = appType().badge),
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
    val isSoft = LocalThemePreset.current == AppThemePreset.SOFT
    // 柔绘：轨道色降饱和（success 0x7BAE8C 再压一档 → 72%，避免低对比界面上开关"跳"出来），
    // 未选中轨道同样压淡一档；并加羽化描边环替代任何实色描边。
    // 柔绘圆角：M3 开关轨道本身是全高胶囊，这里只把外层按 appShapes().capsule 收敛，
    // 使左右端与柔绘整体圆角阶梯一致。
    val softTrackAlpha = 0.72f
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = if (isSoft) {
            modifier.softFeatherRim(appShapes().capsule)
        } else {
            modifier
        },
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = if (isSoft) tokens.success.copy(alpha = softTrackAlpha) else tokens.success,
            checkedBorderColor = Color.Transparent,
            checkedIconColor = if (isSoft) tokens.success.copy(alpha = softTrackAlpha) else tokens.success,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = if (isSoft) tokens.inputBg.copy(alpha = 0.78f) else tokens.inputBg,
            uncheckedBorderColor = Color.Transparent,
            uncheckedIconColor = if (isSoft) tokens.inputBg.copy(alpha = 0.78f) else tokens.inputBg
        )
    )
}

/**
 * iOS 风格单选指示器：空心圆环，选中时内部填充主色圆点。
 */
@Composable
fun AppRadioIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val tokens = appColors()
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) tokens.primary else tokens.divider, CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(tokens.primary)
            )
        }
    }
}

/**
 * iOS 风格多选指示器：圆角方块，选中时填充主色并显示对勾。
 */
@Composable
fun AppCheckboxIndicator(
    checked: Boolean,
    modifier: Modifier = Modifier
) {
    val tokens = appColors()
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) tokens.primary else tokens.inputBg)
            .border(1.5.dp, if (checked) tokens.primary else tokens.divider, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = vectorResource(Res.drawable.check_24px),
                contentDescription = null,
                tint = tokens.textOnPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 胶囊单选行：iOS 风格，整行可点击，选中时右侧显示实心圆点。
 */
@Composable
fun AppRadioRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val tokens = appColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = appSpacing().rowMinHeight)
            .clip(appShapes().menu)
            .clickable(onClick = onClick)
            .padding(horizontal = appSpacing().pageHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = appType().body),
                fontWeight = FontWeight.Medium,
                color = tokens.textPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = appType().hint),
                    color = tokens.textSecondary
                )
            }
        }
        AppRadioIndicator(selected = selected)
    }
}

/**
 * 胶囊多选行：iOS 风格，整行可点击，选中时右侧显示对勾。
 */
@Composable
fun AppCheckboxRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val tokens = appColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = appSpacing().rowMinHeight)
            .clip(appShapes().menu)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = appSpacing().pageHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = appType().body),
                fontWeight = FontWeight.Medium,
                color = tokens.textPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = appType().hint),
                    color = tokens.textSecondary
                )
            }
        }
        AppCheckboxIndicator(checked = checked)
    }
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
            shape = appShapes().menu,
            containerColor = tokens.snackbarBg,
            contentColor = tokens.snackbarFg,
            actionColor = tokens.primary,
            // v3.43.0：M3 SnackbarHost 自带的只有淡入淡出 + 缩放，这里补一层主题化
            // 「上浮入位」——以 data 为 key，保证连续多条提示各自重播。
            modifier = rememberContentEnterMotion(key = data)
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
    val isSoft = LocalThemePreset.current == AppThemePreset.SOFT
    val motion = LocalAppMotion.current
    // 柔绘：容器底压淡一档（低对比）
    val softContainer = tokens.inputBg.copy(alpha = 0.78f)
    val density = LocalDensity.current

    // v3.43.0：选中胶囊由「各选项瞬切底色」改为**共享胶囊滑动**——
    // 位置 / 宽度 / 颜色三者同时补间，关掉「切换标签」分组（tabIndicatorMs=0）⇒ 瞬移。
    val optionBounds = remember { mutableStateMapOf<Int, Rect>() }
    val pillLeft by animateDpAsState(
        targetValue = optionBounds[selectedIndex]?.let { with(density) { it.left.toDp() } } ?: 0.dp,
        animationSpec = tween(motion.tokens.tabIndicatorMs, easing = motion.tokens.navEasing),
        label = "segmentedPillLeft"
    )
    val pillWidth by animateDpAsState(
        targetValue = optionBounds[selectedIndex]?.let { with(density) { it.width.toDp() } } ?: 0.dp,
        animationSpec = tween(motion.tokens.tabIndicatorMs, easing = motion.tokens.navEasing),
        label = "segmentedPillWidth"
    )
    val pillMeasured = pillWidth > 0.dp

    // 胶囊材质：柔绘用软模糊投影 + 羽化环（无硬边 elevation 投影），其余主题用轻投影
    val pillSurface = if (isSoft) {
        Modifier
            .softShadow(shape = CircleShape, elevation = 4.dp)
            .clip(CircleShape)
            .background(tokens.cardBg)
            .softFeatherRim(CircleShape)
    } else {
        Modifier
            .shadow(
                elevation = 2.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = tokens.shadow,
                spotColor = tokens.shadow
            )
            .background(tokens.cardBg)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (isSoft) softContainer else tokens.inputBg)
    ) {
        if (pillMeasured) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = pillLeft)
                    .width(pillWidth)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .then(pillSurface)
            )
        }
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val animatedTextColor by animateColorAsState(
                    targetValue = if (selected) tokens.textPrimary else tokens.textSecondary,
                    animationSpec = tween(motion.tokens.tabIndicatorMs, easing = motion.tokens.navEasing),
                    label = "segmentedTextColor"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        // 测量失败（首帧）时保留旧式瞬切底色，避免出现"无选中态"的一帧
                        .background(
                            if (selected && !pillMeasured) tokens.cardBg else Color.Transparent
                        )
                        .onGloballyPositioned { coords ->
                            val rect = coords.boundsInParent()
                            if (optionBounds[index] != rect) optionBounds[index] = rect
                        }
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = animatedTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * FAB 按压缩放反馈（v2 规范 §4.1：按压缩放 + 大投影）。
 * v3.26.0 动效收口：按压幅度/弹簧手感读全局动效令牌（pressScale + pressSpec）；
 * 关掉「玻璃悬浮件」分组 ⇒ pressScale=1 + snap，按压不再缩放。
 * 返回按下状态对应的缩放值，供 graphicsLayer 使用。
 */
@Composable
fun rememberFabPressedScale(interactionSource: MutableInteractionSource): Float {
    val motion = LocalAppMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed) motion.tokens.pressScale else 1f
    return animateFloatAsState(
        targetValue = target,
        animationSpec = motion.tokens.pressSpec,
        label = "fabPressScale"
    ).value
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
        shape = appShapes().chipSmall,
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
                    .heightIn(min = appSpacing().touchMin)
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
                .heightIn(min = appSpacing().touchMin),
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
