package com.shangkeschedule.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.shangkeschedule.ui.theme.LocalGlassBlurRadius
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 液态玻璃底栏 —— Kyant0/AndroidLiquidGlass（Apache License 2.0, Copyright 2025 Kyant）
 * catalog 组件 `LiquidBottomTabs` 的**逐行移植**，参数与结构不做任何改动：
 *
 * - **A 层 · 玻璃条**（64dp）：`vibrancy → blur(LocalGlassBlurRadius)`，**不折射不色散**；
 *   按压时整条放大 `1 + 16dp / width`；表面画 `containerColor`（浅色 FAFAFA@0.4 / 深色 121212@0.4）；
 * - **B 层 · Tab 录制层**（56dp，屏幕上透明）：同一份 Tab 内容被 accent 着色后录进
 *   [tabsBackdrop]，效果链 `vibrancy → blur`；
 * - **C 层 · 选中指示器**（56dp，宽 1/n）：叠在「页面背景 + Tab 录制层」组合背景上，
 *   **按住时** `lens(14dp·p, 20dp·p, 色散)`（静止态为 0）+ 按压投影/内阴影/高光 +
 *   按压缩放 78/56 + 速度挤压回弹；
 *   手势（拖拽/按压）全部挂在**这一层**上 —— 与原版一致，玻璃只在按住指示器时跟手。
 *
 * 点击 Tab：由 B 层隐形 Tab 的 `clickable` 承接（B 覆盖在 A 之上接收全部点击），
 * 更新外部 `selectedTabIndex` 后经 `snapshotFlow(currentIndex)` 驱动
 * `animateToValue`（内部自带 press → 滑动 → release）并回调 [onTabSelected]。
 *
 * 与参考实现的参数化偏离（其余结构/参数逐行对齐原版）：
 * 模糊半径读 [LocalGlassBlurRadius]（用户「模糊强度」设置）；
 * **底栏本体（A / B 层）全面取消折射与色散**（`glassLens` 移除、不随按压出现）；
 * 折射 / 色散仅保留为 **C 层指示器的按压反馈**（静止态为 0，受 [LocalGlassRefraction] 开关约束）。
 *
 * ⚠️ 曾于 v3.50.3 / v3.50.4 试验过「静止态不折射 / 快速点击不放大 / 拖动先抓手指」等
 * 交互改造，**2026-09-12 15:57 按用户要求整体回退**（用户要 15:05 即 v3.50.2 的交互），
 * 备份见 `build_qa/rollback_20260912_1557/`。请勿在未获用户明确要求前重新引入。
 */
internal val LocalLiquidTabScale =
    staticCompositionLocalOf { { 1f } }

@Composable
fun LiquidGlassTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: GlassBackdrop,
    tabsCount: Int,
    accentColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    tab: @Composable RowScope.(index: Int) -> Unit
) {
    // 上游固定值：浅色 FAFAFA@0.4，深色 121212@0.4
    val containerColor =
        if (!isDark) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)
    val tabsBackdrop = rememberGlassBackdrop()
    val capsule = RoundedCornerShape(50)
    val blurRadius = LocalGlassBlurRadius.current
    // 顶层（Theme.kt）注入的「液态折射」配置：v3.50.8 起仅作用于 C 层指示器的**按压反馈**
    //（底栏本体 A / B 层已全面取消折射与色散，静止态无折射）。
    val refraction = LocalGlassRefraction.current
    val lensDispersion = refraction.enabled && refraction.dispersion

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        // 手势层按下定位用：A/B 行内容距条左缘 4dp（行内 padding）
        val gestureContentStartPx = with(density) { 4f.dp.toPx() }
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = { down ->
                    // 本项目交互需求「点击迅速放大」：手指按下的瞬间就把指示器定位到
                    // 按压的 Tab 并进入按压态（放大 + 折射 + 彩虹边同时出现）。
                    // currentIndex 变化经 snapshotFlow 触发 animateToValue（放大滑动）
                    // 与 onTabSelected（导航），与松手后的吸附共用同一条链路。
                    val pressedIndex = ((down.x - gestureContentStartPx) / tabWidth)
                        .toInt().coerceIn(0, tabsCount - 1)
                    if (pressedIndex != currentIndex) {
                        currentIndex = pressedIndex
                    }
                },
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }
        val interactiveHighlight = remember(animationScope) {
            // 跟手高光：光斑直接落在指尖（全栏手势层上，position 用默认=手指位置）
            InteractiveHighlight(
                animationScope = animationScope
            )
        }
        // A 层 · 玻璃条：**只做 vibrancy + blur（用户要求：底栏取消折射和色散）**，
        // 按压整条放大保留；折射 / 色散不再存在于底栏任何一层。
        GlassSurface(
            modifier = Modifier
                .graphicsLayer { translationX = panelOffset }
                .height(64f.dp)
                .fillMaxWidth(),
            backdrop = backdrop,
            shape = capsule,
            effects = {
                glassVibrancy()
                glassBlur(blurRadius.toPx())
            },
            layerBlock = {
                val progress = dampedDragAnimation.pressProgress
                val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                scaleX = scale
                scaleY = scale
            },
            highlight = { GlassHighlight.Default },
            shadow = { GlassShadow.Default },
            onDrawSurface = { drawRect(containerColor) }
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .then(interactiveHighlight.modifier)
                    .padding(4f.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = {
                    repeat(tabsCount) { index -> tab(index) }
                }
            )
        }
        // B 层 · Tab 录制层：屏幕上透明（alpha 0），内容被录进 tabsBackdrop 并 accent 着色；
        // LocalLiquidTabScale 让按压时录制层内的 Tab 放大 1.2（经指示器玻璃可见）。
        CompositionLocalProvider(
            LocalLiquidTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .glassBackdropSource(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .height(56f.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxSize(),
                    backdrop = backdrop,
                    shape = capsule,
                    effects = {
                        glassVibrancy()
                        glassBlur(blurRadius.toPx())
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        GlassHighlight.Default.copy(alpha = progress)
                    },
                    shadow = { GlassShadow.Default },
                    onDrawSurface = { drawRect(containerColor) }
                ) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .then(interactiveHighlight.modifier)
                            .padding(horizontal = 4f.dp)
                            .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                        verticalAlignment = Alignment.CenterVertically,
                        content = {
                            repeat(tabsCount) { index -> tab(index) }
                        }
                    )
                }
            }
        }
        // C 层 · 选中指示器：叠在「页面背景 + Tab 录制层」组合背景上的玻璃胶囊。
        // 手势不在这层 —— 由下方全栏手势层统一处理（按任意 Tab 都立即响应）。
        GlassSurface(
            modifier = Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .height(56f.dp)
                .fillMaxWidth(1f / tabsCount),
            backdrop = rememberCombinedGlassBackdrop(backdrop, tabsBackdrop),
            shape = capsule,
            effects = {
                val progress = dampedDragAnimation.pressProgress
                // 按压指示器的折射 + 色散反馈（v3.50.8 按用户要求恢复）：
                // 静止态为 0（底栏静止观感不变），按住时长到 14dp / 20dp
                //（与原版按压峰值 6+8 / 8+12 一致），色散跟随顶层「液态折射 · 色散」开关。
                if (refraction.enabled && progress > 0f) {
                    glassLens(
                        14f.dp.toPx() * progress,
                        20f.dp.toPx() * progress,
                        chromaticAberration = lensDispersion
                    )
                }
            },
            highlight = {
                val progress = dampedDragAnimation.pressProgress
                GlassHighlight.Default.copy(alpha = progress)
            },
            shadow = {
                val progress = dampedDragAnimation.pressProgress
                GlassShadow.Default.copy(alpha = progress)
            },
            innerShadow = {
                val progress = dampedDragAnimation.pressProgress
                GlassInnerShadow(
                    radius = 8f.dp * progress,
                    alpha = progress
                )
            },
            layerBlock = {
                scaleX = dampedDragAnimation.scaleX
                scaleY = dampedDragAnimation.scaleY
                val velocity = dampedDragAnimation.velocity / 10f
                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
            },
            onDrawSurface = {
                val progress = dampedDragAnimation.pressProgress
                drawRect(
                    if (!isDark) Color.Black.copy(0.1f)
                    else Color.White.copy(0.1f),
                    alpha = 1f - progress
                )
                drawRect(Color.Black.copy(alpha = 0.03f * progress))
            }
        )
        // 全栏手势层（matchParentSize：不参与父布局测量，避免把 BoxWithConstraints 撑到整屏
        // 导致玻璃条被垂直居中到屏幕中间 —— fillMaxSize 的教训）。
        // 按下任意位置：立即定位指示器 + 进入按压态（放大/折射/彩虹边/跟手光斑）；
        // 拖动：指示器跟手；松手：吸附最近 Tab。不消费事件 ⇒ B 层 Tab 的 clickable 正常工作。
        Box(
            Modifier
                .matchParentSize()
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
        )
    }
}

/** 单个 Tab（原版 LiquidBottomTab 逐行移植）：无涟漪、按压时随 [LocalLiquidTabScale] 放大。 */
@Composable
fun RowScope.LiquidGlassTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidTabScale.current
    Column(
        modifier
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        // 图标与文字的间距：用户两轮要求逐步收紧（2dp → 1dp → 0.5dp，仍保留间距）。
        // 注：同批其余交互改造均被用户废弃并回退，仅此一项保留。
        verticalArrangement = Arrangement.spacedBy(0.5f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}
