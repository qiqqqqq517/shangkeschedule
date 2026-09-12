package com.shangkeschedule.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * 液态玻璃**表面**容器：把 [backdrop] 的一份拷贝经 `effects { }` 处理后铺在内容下方，
 * 再依次绘制表面色（[onDrawSurface]）与 [content]。
 *
 * 层级（自下而上）：
 * ① 背板拷贝 + 效果（效果在父节点、`drawLayer` 在子节点）
 * ② 表面色子节点（[onDrawSurface]，画在背板之上、内容之下）
 * ③ [content]
 *
 * ## v3.50.2 两条结构性修正（离屏探针机制对照 + 上游源码交叉取证）
 *
 * 1. **背板必须真的被录进层**（见 `GlassBackdropSourceNode.draw`）：Compose 1.11.1 的
 *    `record(density, layoutDirection, size, block)` 不会像 1.12 的 `record(size){}`
 *    那样临时把调用方 `drawContext.canvas` 指向录制画布 ⇒ 直接用外层作用域调
 *    `drawContent()` 时内容画到屏幕、层恒为空。这是「模糊 / 折射 / 色散同时不生效、
 *    桌面与 Android 一致失效、日志却全绿」的根因。
 * 2. 效果必须挂在**父节点**、`drawLayer` 放**子节点**。对照实验（`desktopApp/GlassProbe.kt`
 *    的 mech 场景，格子编号见实现）：
 *    - `self+direct`（节点自带效果 + 直接内容）= 生效
 *    - `self+drawLayer`（节点自带效果 + **同节点** `drawLayer`）= **静默失效**（mean=0.000）
 *    - `parent+childLayer`（**父**节点效果 + 子节点 `drawLayer`）= 生效（mean=115.4）
 *    即 Skiko 合成路径不消费「自己画的层」的层画笔（Android 的 `RenderNode.setRenderEffect`
 *    是全绘制路径生效的另一套实现，不受此限制）。
 *
 * 复现命令：`.\gradlew.bat :desktopApp:run "-PpreviewMainClass=com.shangkeschedule.GlassProbeKt"`
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop,
    shape: Shape,
    effects: GlassEffectScope.() -> Unit,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    highlight: (() -> GlassHighlight?)? = null,
    shadow: (() -> GlassShadow?)? = null,
    innerShadow: (() -> GlassInnerShadow?)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val scope = remember { GlassEffectScopeImpl() }
    DisposableEffect(scope) {
        onDispose { scope.reset() }
    }
    // v3.51.2 采样守卫：本 GlassSurface 若被绘制于某个 backdrop 的 source 子树内
    // （录制期标记），此后永不采样该 backdrop —— 根除「P 的 DL 引用本节点、
    // 本节点 DL 又引用 P」的渲染树自引用环（HWUI prepareTreeImpl 栈溢出）。
    val sampleGuard = remember { GlassSampleGuard() }
    val layoutDirection = LocalLayoutDirection.current
    var backdropCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier
            .then(if (layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier)
            // 兜底模式（isGlassFallbackActive）：装饰件同样跳过 —— 高光 / 投影 / 内阴影
            // 内部含 AGSL 运行时着色器与 BlurMaskFilter，属"未经该机型验证"的 GPU 操作，
            // 必须一并摘除，否则兜底挡不住崩溃（v3.50.10 修正）。
            .then(
                if (!isGlassFallbackActive && innerShadow != null)
                    Modifier.glassInnerShadow(shape, innerShadow) else Modifier
            )
            .then(
                if (!isGlassFallbackActive && shadow != null)
                    Modifier.glassShadow(shape, shadow) else Modifier
            )
            .then(
                if (!isGlassFallbackActive && highlight != null)
                    Modifier.glassHighlight(shape, highlight) else Modifier
            )
            // 容器级裁剪：约束背板子节点效果输出（blur/着色器边界外溢）的范围
            .clip(shape)
    ) {
        // ① 背板拷贝 + 效果：效果在父、drawLayer 在子（见类文档对照实验）。
        // 自愈兜底（isGlassFallbackActive）：上次进程原生/Java 崩溃退出后，本进程跳过
        // 背板采样与整条效果链（RenderEffect 渲染在 RenderThread，Java 无法拦截），
        // 只留 ② 表面 tint + ③ 内容——观感退化为半透明面板，但 App 一定可用。
        if (!isGlassFallbackActive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { if (it.isAttached) backdropCoords = it }
                    .graphicsLayer {
                        scope.applyForLayer(
                            shape = shape,
                            density = density,
                            fontScale = fontScale,
                            size = size,
                            layoutDirection = layoutDirection,
                            effects = effects
                        )
                        renderEffect = scope.renderEffect
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            with(backdrop) { drawGlassBackdrop(backdropCoords, layerBlock, sampleGuard) }
                        }
                )
            }
        }
        // ② 表面色 / tint（背板之上、内容之下）
        if (onDrawSurface != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { onDrawSurface(this) }
            )
        }
        // ③ 内容
        Box(Modifier.fillMaxSize()) {
            content()
        }
    }
}
