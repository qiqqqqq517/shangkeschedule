package com.shangkeschedule.ui.settings.style

/**
 * 滑块（StyleSliderItem）轨道几何 —— 纯函数，供 Canvas 绘制与单测共用。
 *
 * 背景（v4.75.5 ~ v4.75.10 反复返工的根因）：
 *   初版把几何写死在 `Slider`'s `track` 插槽的 Canvas lambda 里，且**依赖对 Material3
 *   内部 thumb 摆放公式的逆向推导**（`thumbOffsetX = trackWidth * fraction`）。该推导在真机上
 *   连续多轮失配，先后出现「thumb 探出轨道」「轨道两端露白」「右端拉不到位」「已选段与 thumb
 *   之间有残留」四种症状，每轮都靠肉眼截图纠偏，代价高且不稳定。
 *
 * 本函数把几何收敛为**唯一真源**，并作出一个关键决策：
 *   **thumb 由本组件自己绘制**（M3 的 thumb 设为透明占位），因此
 *   「轨道 / 已选段 / thumb」三者由同一组坐标算出，**在构造上不可能不一致**，
 *   也不再需要知道 M3 把 thumb 摆在哪个坐标系里。
 *
 * 坐标系与不变量（[computeSliderTrackGeometry] 的返回值即满足）：
 *   - 可见轨道横跨 [0, canvasWidth]，**两端不外扩**（canvas 就是 M3 给的 track placeable 宽度）；
 *   - thumb 为圆，半径 = 轨道高 / 2 ⇒ **thumb 与轨道等高**；
 *   - thumb 圆心行程 [radius, canvasWidth - radius] ⇒ 最左/最右时 thumb 边缘**恰好贴齐轨道端点**，
 *     既不溢出也不留白；
 *   - 已选段右端 = thumb 圆心 ⇒ 深蓝与白色 thumb **严格相接、无残留空隙**；
 *   - 已选段右端额外多画一个半径并用同半径圆角 ⇒ 其右端圆角与 thumb 右半圆**完全重合**，
 *     深蓝没入白块，视觉无缝。
 */
internal data class SliderTrackGeometry(
    /** 可见轨道左端（Canvas 局部坐标）。恒为 0。 */
    val trackLeft: Float,
    /** 可见轨道右端（Canvas 局部坐标）。 */
    val trackRight: Float,
    /** thumb 圆心 x。 */
    val thumbCenter: Float,
    /** thumb 半径（= 轨道高的一半 ⇒ thumb 与轨道等高）。 */
    val thumbRadius: Float,
    /** 已选段（深蓝）右端 x。 */
    val fillEnd: Float,
)

/**
 * 计算滑块轨道几何。
 *
 * @param canvasWidthPx Canvas 可用宽度（px），即 M3 提供的 track placeable 宽度。
 * @param trackHeightPx 轨道高度（px）。
 * @param fraction 当前值归一化后的比例，会被夹到 [0, 1]。
 */
internal fun computeSliderTrackGeometry(
    canvasWidthPx: Float,
    trackHeightPx: Float,
    fraction: Float,
): SliderTrackGeometry {
    val radius = trackHeightPx / 2f
    val f = fraction.coerceIn(0f, 1f)
    // thumb 圆心在 [radius, canvasWidth - radius] 之间线性移动：
    // 两端时 thumb 的左右边缘恰好落在轨道端点上。
    val center = radius + f * (canvasWidthPx - 2f * radius)
    return SliderTrackGeometry(
        trackLeft = 0f,
        trackRight = canvasWidthPx,
        thumbCenter = center,
        thumbRadius = radius,
        // 多画一个半径：右端圆角中心即落在 thumb 圆心、半径相同 ⇒ 与 thumb 右半圆完全重合。
        fillEnd = center + radius,
    )
}