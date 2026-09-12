package com.shangkeschedule.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appSurface
import com.shangkeschedule.ui.glass.GlassBackdrop
import com.shangkeschedule.ui.theme.LiquidGlass
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.LocalIsSoftTheme
import com.shangkeschedule.ui.theme.claudeGroupBg
import com.shangkeschedule.data.model.AppThemePreset

/**
 * 基线补充组件（UI 一致性收敛件，v2 风格规范 §3/§4）：
 * [AppSectionHeader] 分区标题 · [AppEmptyState] 空状态 · [AppLoading] 加载态 ·
 * [AppFab] 统一 FAB · [AppDangerDialog] 危险操作确认。
 * 全部为纯视觉皮肤：文案与行为一律由调用方提供，保证各页面零散实现收敛到同一语言。
 */

/**
 * 分区标题：labelLarge + 主色 SemiBold，左侧 4dp 视觉对齐卡片内文。
 * 替代各页面自绘的 labelLarge/titleSmall/titleLarge/titleMedium 混杂分区头。
 */
@Composable
fun AppSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    val isClaude = LocalThemePreset.current == AppThemePreset.CLAUDE
    val isSoft = LocalThemePreset.current == AppThemePreset.SOFT
    Text(
        text = text,
        style = if (isClaude) {
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.01).em
            )
        } else if (isSoft) {
            // 柔绘：13sp Medium + 松字距——柔绘字号阶梯比通透略轻（字重降一档），
            // 低对比配色下用字重而非色差来建立分区层级。
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.05.sp
            )
        } else {
            // 通透（iOS 26）：13sp SemiBold + SF 字阶绝对字距
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.08).sp
            )
        },
        fontWeight = if (isSoft) FontWeight.Medium else FontWeight.SemiBold,
        color = appColors().primary,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/**
 * 空状态：淡灰胶囊底 + 居中辅助文案（与 Today 空态同语言，v2 规范 §3）。
 * [fillScreen] = true 时撑满父容器并居中（整页空态）；false 时仅在本行内居中（局部空态）。
 */
@Composable
fun AppEmptyState(
    hint: String,
    modifier: Modifier = Modifier,
    fillScreen: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillScreen) Modifier.fillMaxSize() else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .then(rememberContentEnterMotion())
                .clip(appShapes().card)
                .background(appColors().inputBg.copy(alpha = 0.55f))
                .padding(horizontal = 28.dp, vertical = 22.dp)
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = appColors().textSecondary
            )
        }
    }
}

/**
 * 主题化加载指示器（v3.43.0 ·《交互动效审查》P2）。
 *
 * 此前全站加载态都是 M3 默认 [CircularProgressIndicator]——一套 Material 旋转环穿三套主题。
 * 现在按主题语言各给一种：
 * - **柔绘**：三枚柔光圆点依次呼吸（缩放 + 浓度、对称缓动、无锐利边缘）；
 * - **书卷**：单枚墨点缓慢浓淡起伏 + 一圈向外晕开的淡环（零位移、零旋转，静态纸面不"游动"）；
 * - **通透（iOS 26）**：八段环形指示器匀速旋转（对齐 iOS `UIActivityIndicator` 语汇）。
 *
 * 关闭「页面入场」分组或开启「减弱动态效果」⇒ 退化为静态形态（不闪动）。
 */
@Composable
fun ThemedLoadingIndicator(modifier: Modifier = Modifier) {
    val motion = LocalAppMotion.current
    val animated = motion.isEnabled(AnimationGroup.PAGE_ENTRANCE) && motion.tokens.pulseDurationMs > 0
    when (LocalThemePreset.current) {
        AppThemePreset.SOFT -> SoftBreathingDots(animated = animated, modifier = modifier)
        AppThemePreset.CLAUDE -> ClaudeInkDot(animated = animated, modifier = modifier)
        else -> IosSegmentedSpinner(animated = animated, modifier = modifier)
    }
}

/** 柔绘：三枚柔光圆点，依次呼吸（错峰 1/4 周期，对称缓动）。 */
@Composable
private fun SoftBreathingDots(animated: Boolean, modifier: Modifier = Modifier) {
    val color = appColors().primary
    val period = LocalAppMotion.current.tokens.pulseDurationMs.coerceAtLeast(600)
    val transition = rememberInfiniteTransition(label = "softDots")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = period,
                        delayMillis = i * (period / 4),
                        easing = LinearOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "softDot$i"
            )
            val f = if (animated) phase else 0.5f
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        // 缩放幅度刻意很小（0.78~1.0），配合浓度起伏即可读出「呼吸」
                        scaleX = 0.78f + 0.22f * f
                        scaleY = 0.78f + 0.22f * f
                        alpha = 0.32f + 0.68f * f
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** 书卷：单枚墨点浓淡起伏 + 一圈向外晕开的淡环（无位移、无旋转）。 */
@Composable
private fun ClaudeInkDot(animated: Boolean, modifier: Modifier = Modifier) {
    val color = appColors().primary
    val period = LocalAppMotion.current.tokens.pulseDurationMs.coerceAtLeast(600)
    val transition = rememberInfiniteTransition(label = "claudeInk")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = period, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "inkPhase"
    )
    val f = if (animated) phase else 0.5f
    Box(modifier = modifier.size(26.dp), contentAlignment = Alignment.Center) {
        // 晕开环：自小而大、同时淡出（「墨在纸上化开」）
        Box(
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    val s = 0.52f + 0.48f * f
                    scaleX = s
                    scaleY = s
                    alpha = (1f - f) * 0.42f
                }
                .border(1.dp, color, CircleShape)
        )
        // 墨点：浓度随时间起伏
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer { alpha = 0.40f + 0.60f * (1f - f) }
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** 通透（iOS 26）：八段环形指示器匀速旋转（对齐 UIActivityIndicator 语汇）。 */
@Composable
private fun IosSegmentedSpinner(animated: Boolean, modifier: Modifier = Modifier) {
    val color = appColors().primary
    val period = LocalAppMotion.current.tokens.pulseDurationMs.coerceAtLeast(600)
    val transition = rememberInfiniteTransition(label = "iosSpinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = period, easing = LinearEasing)
        ),
        label = "spinnerAngle"
    )
    Box(
        modifier = modifier
            .size(24.dp)
            .graphicsLayer { rotationZ = if (animated) angle else 0f },
        contentAlignment = Alignment.Center
    ) {
        repeat(8) { i ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = 45f * i },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(color.copy(alpha = 0.22f + 0.78f * (i / 7f)))
                )
            }
        }
    }
}

/**
 * 统一加载态：居中圆形进度指示。
 */
@Composable
fun AppLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ThemedLoadingIndicator(modifier = rememberContentEnterMotion())
    }
}

/**
 * 统一 FAB：56dp 圆形、大投影 + 按压缩放（Telegram 形态，v2 规范 §4.1）。
 * 替代各页面 M3 默认 FAB 与 Today 页内联实现。
 *
 * v3.23.5 起支持液态玻璃形态：传入 [glassBackdrop]（页面内容需挂 glassBackdropSource）时，
 * FAB 渲染为玻璃圆钮（与「回到本周」圆钮同源玻璃语言，主色图标）；不传则保持原主色实心。
 */
@Composable
fun AppFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    glassBackdrop: GlassBackdrop? = null,
    hazeState: HazeState? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberFabPressedScale(interaction)
    if (glassBackdrop == null && hazeState == null) {
        // 无背景快照的页面：保持原主色实心 FAB（向下兼容）
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier
                .size(appSpacing().fab)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = CircleShape,
            containerColor = appColors().primary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 10.dp
            ),
            interactionSource = interaction
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = contentDescription
            )
        }
    } else {
        // 液态玻璃圆钮：表面极淡 + 轻模糊 + 边缘光学，主色图标保持视觉锚点
        Box(
            modifier = modifier
                .size(appSpacing().fab)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(bounded = true),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlass(
                modifier = Modifier.fillMaxSize(),
                glassBackdrop = glassBackdrop,
                shape = CircleShape,
                containerColor = appColors().inputBg,
                shadowElevation = 8.dp,
                hazeState = hazeState
                // 模糊强度统一取 LocalGlassBlurRadius（用户设置注入）
            )
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = appColors().primary
            )
        }
    }
}

/**
 * 主题化对话框入场修饰符（v3.43.0 ·《交互动效审查_三主题》P0-2）。
 *
 * M3 `AlertDialog` 不暴露进入 / 退出 spec，所以全库 30+ 处弹窗此前都是「平台默认瞬现」，
 * 既不读任何动画令牌，也不受任何分组开关控制。本函数把令牌化的淡入 + 0.94→1 轻微放大
 * 接到 dialog surface 上，时长取 [MotionTokens.dialogEnterMs]、曲线取 [MotionTokens.expandEasing]；
 * 关闭「对话框」分组或开启「减弱动态效果」时直接返回 [Modifier]（瞬现）。
 *
 * 用 `graphicsLayer`（绘制阶段变换）而非重组动画：只影响像素，不触发子内容重新布局，
 * 因此对任意 AlertDialog 内容都是几何安全的。
 */
@Composable
fun rememberDialogEnterMotion(): Modifier {
    val motion = LocalAppMotion.current
    if (!motion.isEnabled(AnimationGroup.DIALOG)) return Modifier
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(motion.tokens.dialogEnterMs, easing = motion.tokens.expandEasing),
        label = "dialogEnterMotion"
    )
    val scale = 0.94f + 0.06f * progress
    return Modifier.graphicsLayer {
        alpha = progress
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 内容入场修饰符：主题化淡入 + 轻微上浮（PAGE_ENTRANCE 角色）。
 *
 * 供空态 / 加载态 / 一次性内容复用——这类内容此前都是整块瞬现，与列表的错峰入场不同步。
 * 时长 / 曲线 / 位移 / 起始不透明度全部取全局令牌；关闭「页面入场」分组或开启
 * 「减弱动态效果」（`entranceInitialAlpha = 1f`、`entranceSlideDp = 0.dp`）时返回 [Modifier]。
 *
 * @param key 变化时重播入场——Snackbar 这类「同一个 composable 反复出现」的场景需要传入
 *   当前数据对象，否则第二次弹出不会再播。
 */
@Composable
fun rememberContentEnterMotion(key: Any? = Unit): Modifier {
    val motion = LocalAppMotion.current
    if (!motion.isEnabled(AnimationGroup.PAGE_ENTRANCE)) return Modifier
    var entered by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(motion.tokens.entranceDurationMs, easing = motion.tokens.entranceEasing),
        label = "contentEnterMotion"
    )
    val initial = motion.tokens.entranceInitialAlpha
    return Modifier.graphicsLayer {
        alpha = initial + (1f - initial) * progress
        translationY = motion.tokens.entranceSlideDp.toPx() * (1f - progress)
    }
}

/**
 * 主题化确认对话框：与 M3 [AlertDialog] **同名同默认值**的薄包装。
 *
 * 唯一差异是把 [rememberDialogEnterMotion] 叠到 `modifier` 上，使全站弹窗拥有统一的
 * 主题化入场并纳入「对话框」分组开关。调用方只需把 `AlertDialog(` 换成 `AppAlertDialog(`，
 * 其余参数（含 shape / containerColor / properties 等高级用法）原样透传。
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = contentColorFor(containerColor),
    titleContentColor: Color = contentColorFor(containerColor),
    textContentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.then(rememberDialogEnterMotion()),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}

/**
 * 危险操作确认对话框：标准标题/正文 + 危险色胶囊确认钮（AppDialogActions danger 变体）。
 * 替代散落各页的 error TextButton / 无危险色确认钮。
 */
@Composable
fun AppDangerDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            AppDialogActions(
                confirmText = confirmText,
                onConfirm = onConfirm,
                dismissText = dismissText,
                onDismiss = onDismiss,
                danger = true
            )
        },
        dismissButton = {}
    )
}

/**
 * 可选中卡片（选中态唯一化）：白卡 + 选中时 primarySoft 底 + 2dp 主色描边。
 * [onLongClick] 支持长按进入多选等场景；[containerColor] 可覆盖底色（如「当前使用中」的第三态）。
 */
@Composable
fun AppSelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    shape: Shape = appShapes().card,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = appColors()
    val isClaude = LocalThemePreset.current == AppThemePreset.CLAUDE
    // 底色优先级：显式底色 > 选中（主色薄涂） > 书卷暖米分组底 > 卡底；
    // 材质 / 边缘 / 选中描边全部交给统一表面渲染入口 appSurface 按主题分派
    // （柔绘：羽化描边环由 softSurface 内含，此前在此又叠了一遍 → 已收口）。
    val bg = when {
        containerColor != null -> containerColor
        selected -> tokens.primarySoft
        isClaude -> claudeGroupBg()
        else -> tokens.cardBg
    }
    Column(
        modifier = modifier
            .appSurface(
                shape = shape,
                containerColor = bg,
                elevation = 8.dp,
                selected = selected
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
    ) { content() }
}

/**
 * 毛玻璃底部面板（悬浮面板玻璃质感，v2 规范「克制毛玻璃」）。
 *
 * [hazeState] 非空时：面板容器透明，内容列施加背板模糊 + 卡底色 86% 半透明，
 * 拖拽把手与圆角形态与 M3 默认一致；为 null 时退化为普通实色面板（cardBg）。
 * 调用方需保证主窗口内容已用 hazeSource 标注（传入同一 HazeState），
 * 且 HazeState 从主组合创建后传入面板（跨 Dialog 窗口由 Haze 内部处理）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGlassBottomSheet(
    hazeState: HazeState?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = appColors()
    val isSoft = LocalIsSoftTheme.current
    // v3.43.0（《交互动效审查》P0-2）：面板动效接入全局令牌并纳入「底部面板」分组。
    // M3 ModalBottomSheet 的内部 spec 不可配，故在 sheet surface 的 modifier 上叠一层
    // 主题化淡入 + 自下微移（书卷 24dp 滑入，柔绘 / 通透 0dp 纯淡入）——与 M3 自身的升起
    // 动画叠加而非替代，且只做绘制变换、不新增布局层级，几何安全。
    val motion = LocalAppMotion.current
    val sheetTokens = motion.tokens
    val sheetMotionEnabled = motion.isEnabled(AnimationGroup.BOTTOM_SHEET)
    var sheetEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sheetEntered = true }
    val sheetProgress by animateFloatAsState(
        targetValue = if (sheetEntered) 1f else 0f,
        animationSpec = tween(sheetTokens.sheetEnterMs, easing = sheetTokens.expandEasing),
        label = "sheetEnter"
    )
    val sheetFactor = if (sheetMotionEnabled) sheetProgress else 1f
    val sheetModifier = modifier.graphicsLayer {
        alpha = sheetFactor
        translationY = sheetTokens.sheetSlideDp.toPx() * (1f - sheetFactor)
    }
    // 柔绘：薄涂面板 —— 遮罩更浅（26% vs M3 默认 32%）、背板模糊更弱、涂色更实（0.90 vs 0.86），
    // 拖拽把手颜色由 outlineVariant 角色自动变为柔绘淡边，无需单独改写
    val scrim = if (isSoft) Color.Black.copy(alpha = 0.26f) else BottomSheetDefaults.ScrimColor
    if (hazeState == null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = tokens.cardBg,
            shape = appShapes().sheetTop,
            scrimColor = scrim,
            modifier = sheetModifier,
            content = content
        )
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shape = appShapes().sheetTop,
            scrimColor = scrim,
            modifier = sheetModifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hazeEffect(hazeState) {
                        blurRadius = if (isSoft) 14.dp else 20.dp
                        noiseFactor = if (isSoft) 0.06f else 0.12f
                        tints = listOf(HazeTint(tokens.cardBg.copy(alpha = if (isSoft) 0.90f else 0.86f)))
                        fallbackTint = HazeTint(tokens.cardBg)
                        backgroundColor = Color.Transparent
                    },
                content = content
            )
        }
    }
}
