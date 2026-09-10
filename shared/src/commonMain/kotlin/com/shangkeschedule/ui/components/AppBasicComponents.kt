package com.shangkeschedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.liquidGlass
import com.shangkeschedule.ui.theme.iosGlassRim
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.claudeGroupBg
import com.shangkeschedule.ui.theme.claudeGroupBorder
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
    Text(
        text = text,
        style = if (isClaude) {
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.01).em
            )
        } else {
            // 通透（iOS 26）：13sp SemiBold + SF 字阶绝对字距
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.08).sp
            )
        },
        fontWeight = FontWeight.SemiBold,
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
 * 统一加载态：居中圆形进度指示。
 */
@Composable
fun AppLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * 统一 FAB：56dp 圆形、大投影 + 按压缩放（Telegram 形态，v2 规范 §4.1）。
 * 替代各页面 M3 默认 FAB 与 Today 页内联实现。
 *
 * v3.23.5 起支持液态玻璃形态：传入 [hazeState]（页面内容需以 hazeSource 标记）时，
 * FAB 渲染为玻璃圆钮（与「回到本周」圆钮同源玻璃语言，主色图标）；不传则保持原主色实心。
 */
@Composable
fun AppFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberFabPressedScale(interaction)
    if (hazeState == null) {
        // 无 hazeState 的页面：保持原主色实心 FAB（向下兼容）
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
                .liquidGlass(
                    hazeState = hazeState,
                    shape = CircleShape,
                    containerColor = appColors().inputBg,
                    shadowElevation = 8.dp
                    // v3.24.7：blur 统一取 LiquidGlassBlurRadius（不再单独传 4.dp）
                )
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(bounded = true),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = appColors().primary
            )
        }
    }
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
    AlertDialog(
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
    // 书卷：14dp 圆角 + 暖米色分组底；通透（iOS 26）：16dp 连续圆角 + 白卡 + 玻璃高光内描边
    val finalShape = if (isClaude) RoundedCornerShape(14.dp) else shape
    val bg = when {
        containerColor != null -> containerColor
        selected -> tokens.primarySoft
        isClaude -> claudeGroupBg()
        else -> tokens.cardBg
    }
    Column(
        modifier = modifier
            .shadow(
                elevation = (if (isClaude) 1 else 1).dp,
                shape = finalShape,
                clip = false,
                ambientColor = tokens.shadow,
                spotColor = tokens.shadow
            )
            .clip(finalShape)
            .background(bg)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .then(
                when {
                    selected -> Modifier.border(2.dp, tokens.primary, finalShape)
                    isClaude -> Modifier.border(0.5.dp, claudeGroupBorder(), finalShape)
                    else -> Modifier.iosGlassRim(finalShape)
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
    if (hazeState == null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = tokens.cardBg,
            shape = appShapes().sheetTop,
            modifier = modifier,
            content = content
        )
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shape = appShapes().sheetTop,
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hazeEffect(hazeState) {
                        blurRadius = 20.dp
                        noiseFactor = 0.12f
                        tints = listOf(HazeTint(tokens.cardBg.copy(alpha = 0.86f)))
                        fallbackTint = HazeTint(tokens.cardBg)
                        backgroundColor = Color.Transparent
                    },
                content = content
            )
        }
    }
}
