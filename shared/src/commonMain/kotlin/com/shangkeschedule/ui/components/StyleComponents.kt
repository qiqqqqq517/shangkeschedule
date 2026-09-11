package com.shangkeschedule.ui.components

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.remember
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.shangkeschedule.ui.theme.TouchFeedbackStyle
import kotlinx.coroutines.launch
import com.shangkeschedule.ui.theme.AppAlpha
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.AccentTone
import com.shangkeschedule.ui.theme.AppColorTokens
import com.shangkeschedule.ui.theme.AppSemanticColors
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.claudeGroupBg
import com.shangkeschedule.ui.theme.claudeGroupBorder
import com.shangkeschedule.ui.theme.iosGlassRim
import com.shangkeschedule.ui.theme.softFeatherRim
import com.shangkeschedule.ui.theme.softShadow
import com.shangkeschedule.ui.theme.softSurface
import com.shangkeschedule.ui.theme.softTexture
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

// ============================================================
// 触摸反馈（v3.27.2 Apple HIG 风格）
// ============================================================

/**
 * 触摸反馈状态：跟踪触点位置与扩散进度，由调用方在 pointerInput 的 onPress 中驱动。
 *
 * 使用方式：
 * ```
 * val state = rememberTouchFeedbackState()
 * Box(Modifier.touchFeedback(state, style).pointerInput(Unit) {
 *     detectTapGestures(onPress = { offset ->
 *         state.show(offset)
 *         try { awaitRelease() } finally { state.hide() }
 *     })
 * })
 * ```
 */
class TouchFeedbackState {
    /** 当前触点位置（组件内坐标），null 表示未激活 */
    var touchPosition by mutableStateOf<Offset?>(null)
        private set

    /** 扩散进度 0f~1f：0=刚按下，1=最大扩散 */
    val progress: Float get() = _progress.value

    private val _progress = Animatable(0f)
    private val _fade = Animatable(0f)

    /** 淡出透明度 0f~1f：1=完全显示，0=消失 */
    val alpha: Float get() = _fade.value

    /** 按下时启动扩散动画 */
    fun show(position: Offset, scope: kotlinx.coroutines.CoroutineScope) {
        touchPosition = position
        scope.launch {
            _fade.snapTo(1f)
            _progress.snapTo(0f)
            _progress.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = 400,
                    easing = TouchEaseOut
                )
            )
        }
    }

    /** 抬起时淡出 */
    fun hide(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            _fade.animateTo(
                0f,
                animationSpec = tween(
                    durationMillis = 220,
                    easing = TouchEaseOut
                )
            )
            // 淡出结束后重置触点，避免下一帧在旧位置闪烁
            if (_fade.value == 0f) {
                touchPosition = null
                _progress.snapTo(0f)
            }
        }
    }
}

/** Apple HIG 触摸反馈缓动：快出慢收，柔和克制 */
private val TouchEaseOut = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** 创建并记住一个触摸反馈状态 */
@Composable
fun rememberTouchFeedbackState(): TouchFeedbackState {
    return remember { TouchFeedbackState() }
}

/**
 * 苹果风格触摸反馈 Modifier：手指到哪，动效跟到哪。
 *
 * 两种风格：
 * - [TouchFeedbackStyle.RIPPLE] 涟漪式：从触点向外扩散的柔和圆形，低透明度边缘渐隐
 * - [TouchFeedbackStyle.HALO] 光晕式：触点周围柔和光晕，模糊边缘，更梦幻
 * - [TouchFeedbackStyle.NONE] 关闭：不绘制任何效果
 *
 * 需配合 [TouchFeedbackState] 使用，在 pointerInput 的 onPress 中调用
 * state.show() / state.hide() 驱动动画。
 */
fun Modifier.touchFeedback(
    state: TouchFeedbackState,
    style: TouchFeedbackStyle,
    color: Color = Color.White
): Modifier {
    if (style == TouchFeedbackStyle.NONE) return this
    return this.drawWithContent {
        // 先绘制原始内容
        drawContent()
        // 再在内容之上绘制触摸反馈
        val position = state.touchPosition ?: return@drawWithContent
        val progress = state.progress.coerceIn(0f, 1f)
        val alpha = state.alpha.coerceIn(0f, 1f)
        if (alpha <= 0f) return@drawWithContent

        when (style) {
            TouchFeedbackStyle.RIPPLE -> drawRipple(
                center = position,
                progress = progress,
                alpha = alpha,
                color = color,
                size = size
            )
            TouchFeedbackStyle.HALO -> drawHalo(
                center = position,
                progress = progress,
                alpha = alpha,
                color = color,
                size = size
            )
            TouchFeedbackStyle.NONE -> Unit
        }
    }
}

/**
 * 涟漪式：从触点向外扩散的圆环，内实外虚，类似 iOS 列表 cell 按压。
 * 涟漪环厚度随进度逐渐变薄，透明度随进度渐隐。
 */
private fun DrawScope.drawRipple(
    center: Offset,
    progress: Float,
    alpha: Float,
    color: Color,
    size: Size
) {
    // 最大半径：到最远角落距离的 ~75%，不会充满整个元素
    val maxRadius = kotlin.math.sqrt(
        size.width * size.width + size.height * size.height
    ) * 0.5f * 0.75f
    // 起始半径很小（8dp），逐渐扩散到最大
    val minRadius = 8.dp.toPx()
    val radius = minRadius + (maxRadius - minRadius) * progress

    // 涟漪环：外边缘透明、中间最亮、内边缘稍淡
    // 环厚度：起始 12dp，随扩散逐渐变薄
    val ringThickness = (12.dp.toPx()) * (1f - progress * 0.6f)

    // 用径向渐变模拟涟漪环
    val innerRadius = (radius - ringThickness).coerceAtLeast(0f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                (innerRadius / radius).coerceIn(0f, 1f) to Color.Transparent,
                (innerRadius / radius + 0.15f).coerceIn(0f, 1f) to color.copy(alpha = 0.18f * alpha),
                0.85f to color.copy(alpha = 0.12f * alpha),
                1f to Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/**
 * 光晕式：触点周围一团柔和的光韵扩散，模糊边缘，更梦幻更「玻璃」。
 * 中心亮、向外渐隐，像一盏灯从手指下晕开。
 */
private fun DrawScope.drawHalo(
    center: Offset,
    progress: Float,
    alpha: Float,
    color: Color,
    size: Size
) {
    // 光晕扩散范围比涟漪小，更聚焦于触点周围
    val maxRadius = kotlin.math.sqrt(
        size.width * size.width + size.height * size.height
    ) * 0.5f * 0.55f
    val minRadius = 6.dp.toPx()
    val radius = minRadius + (maxRadius - minRadius) * progress

    // 光晕：中心最亮（柔和白光），向外逐渐消散为透明
    // 用多层径向渐变模拟柔和发光感
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = 0.22f * alpha),
                0.3f to color.copy(alpha = 0.14f * alpha),
                0.6f to color.copy(alpha = 0.06f * alpha),
                1f to Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )

    // 中心加一个更小更亮的光点，模拟「光源」
    val coreRadius = radius * 0.25f
    if (coreRadius > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to color.copy(alpha = 0.35f * alpha),
                    0.5f to color.copy(alpha = 0.18f * alpha),
                    1f to Color.Transparent
                ),
                center = center,
                radius = coreRadius
            ),
            radius = coreRadius,
            center = center
        )
    }
}

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
    val tokens = appColors()
    val isClaude = LocalThemePreset.current == AppThemePreset.CLAUDE
    val isSoft = LocalThemePreset.current == AppThemePreset.SOFT
    // 书卷：14dp 圆角 + 暖米色分组底 + 0.5dp 实色描边；
    // 通透（iOS 26）：16dp 连续圆角 + 白卡 + 玻璃高光内描边；
    // 柔绘：24dp 虚化圆角（appShapes().card）+ 薄涂卡材质（软模糊投影 + 漫射柔光 + 手绘纹理 + 羽化描边）
    val finalShape = when {
        isClaude -> RoundedCornerShape(14.dp)
        isSoft -> appShapes().card
        else -> shape
    }
    val container = containerColor ?: when {
        isClaude -> claudeGroupBg()
        else -> tokens.cardBg
    }
    val surfaceModifier = if (isSoft) {
        // 显式传入底色时视为「局部卡」（如高亮卡），不叠手绘纹理，避免纹理干扰其上文字；
        // 默认分组卡才叠 softTexture（大卡面专用，装饰性，alpha ≤ 0.05 不影响可读性）
        Modifier
            .softSurface(shape = finalShape, containerColor = containerColor, elevation = 10.dp)
            .then(if (containerColor == null) Modifier.softTexture(finalShape) else Modifier)
    } else {
        Modifier
            .shadow(
                elevation = (if (isClaude) 1 else 1).dp,
                shape = finalShape,
                clip = false,
                ambientColor = tokens.shadow,
                spotColor = tokens.shadow
            )
            .clip(finalShape)
            .background(container)
    }
    Box(
        modifier = modifier
            .then(surfaceModifier)
            .then(
                when {
                    isClaude -> Modifier.border(0.5.dp, claudeGroupBorder(), finalShape)
                    // 柔绘：无实色描边，改用羽化描边环（softSurface 已内含，此处对显式底色卡补齐）
                    isSoft -> Modifier.softFeatherRim(finalShape)
                    // 通透（iOS 26）：白卡 + 玻璃高光内描边（靠材质分层，不用实色描边）
                    else -> Modifier.iosGlassRim(finalShape)
                }
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
    blockWidth: Dp = 64.dp
) {
    val pulseAlpha: Float = if (pulse) {
        // v3.26.0 动效收口：呼吸节奏读全局令牌（pulseDurationMs），随动画风格变化
        val motion = LocalAppMotion.current
        val infinite = rememberInfiniteTransition(label = "appHeroMotif")
        infinite.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = motion.tokens.pulseDurationMs),
                RepeatMode.Reverse
            ),
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
    val isSoft = LocalThemePreset.current == AppThemePreset.SOFT
    // 柔绘：容器底压淡一档（低对比），选中胶囊改用软模糊投影 + 羽化描边，不用硬边 elevation 投影
    val softContainer = tokens.inputBg.copy(alpha = 0.78f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (isSoft) softContainer else tokens.inputBg)
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
                        when {
                            // 柔绘：软模糊投影（双层扩散影）替代 elevation 硬边投影
                            selected && isSoft -> Modifier
                                .softShadow(shape = CircleShape, elevation = 4.dp)
                                .clip(CircleShape)
                                .background(tokens.cardBg)
                                .softFeatherRim(CircleShape)
                            isSoft -> Modifier.background(Color.Transparent)
                            selected -> Modifier
                                .shadow(
                                    elevation = 2.dp,
                                    shape = CircleShape,
                                    clip = false,
                                    ambientColor = tokens.shadow,
                                    spotColor = tokens.shadow
                                )
                                .background(tokens.cardBg)
                            else -> Modifier.background(Color.Transparent)
                        }
                    )
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
