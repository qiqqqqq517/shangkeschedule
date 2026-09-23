package com.shangkeschedule.widget

/**
 * ListVertical（4×N）可容纳课程条数的**纯计算**（不依赖 Android API，可直接 JVM 单测）。
 *
 * 为什么要单独抽出来：v3.66.4 曾用常量 `36dp` 估算条目高度，而条目实际占位约 49dp，
 * 导致条数偏高约 36% —— 4×N 被拉高后列表溢出、末条被静默裁切（LinearLayout 不滚动，
 * 超出部分直接不可见）。这是「算多」方向的事故，故此处把公式独立出来并用单测锁死：
 *
 * **宁可少显示一条（留白），也不能多算一条（裁切）** —— 因此一律向下取整。
 */
internal object WidgetListCapacity {

    /**
     * 卡片中「非课程条目」占用的固定高度（dp）。
     *
     * 构成（由 `widget_list_vertical_native.xml` 实值推得）：卡片 padding 8dp×2 = 16
     * + 头部 14sp 行高 ≈16.8dp + 头部 `layout_marginBottom` 6dp ≈ 38.8dp，取 39dp。
     * 该布局的 padding / 头部字号 / 间距若改动，此值需同步。
     */
    const val CHROME_DP = 39

    /** 单次渲染的条数上限，避免异常尺寸下构造过大的 RemoteViews。 */
    const val MAX_ROWS = 12

    /**
     * 按可用高度推算可**完整**显示的条数。
     *
     * @param heightDp 组件高度（应传 `OPTION_APPWIDGET_MAX_HEIGHT`，即当前高度上界；
     *   `MIN_HEIGHT` 是可缩放下界，代表不了当前可用高度）
     * @param chromeDp 非条目占位（[CHROME_DP]）
     * @param rowHeightPx 实测条目高度（px）
     * @param density 屏幕密度
     * @param maxRows 条数上限（[MAX_ROWS]）
     * @return 1..maxRows
     */
    fun rowsFor(
        heightDp: Int,
        chromeDp: Int,
        rowHeightPx: Int,
        density: Float,
        maxRows: Int
    ): Int {
        val availablePx = (heightDp * density).toInt() - (chromeDp * density).toInt()
        // 两种保守出口：
        //  - 可用高度不足 → 仍返回 1（调用方用 minResizeHeight 保证不会小到这个程度）
        //  - 行高非法（≤0，说明测量失败且兜底也没生效）→ 返回 1，**不能**退化成除以 1px
        //    （那会算出几十条、封顶到 maxRows，反而比「算多」更严重地溢出）
        if (availablePx <= 0 || rowHeightPx <= 0) return 1
        return (availablePx / rowHeightPx).coerceIn(1, maxRows)
    }
}
