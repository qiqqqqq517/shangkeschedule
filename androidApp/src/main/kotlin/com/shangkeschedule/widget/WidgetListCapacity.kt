package com.shangkeschedule.widget

import kotlin.math.ceil

/**
 * 列表型（4×N）可容纳条目数的**纯计算**（不依赖 Android API，可直接 JVM 单测）。
 *
 * 为什么要单独抽出来：v3.66.4 曾用常量 `36dp` 估算条目高度，而条目实际占位约 49dp，
 * 导致条数偏高约 36% —— 4×N 被拉高后列表溢出、末条被静默裁切（LinearLayout 不滚动，
 * 超出部分直接不可见）。这是「算多」方向的事故，故此处把公式独立出来并用单测锁死：
 *
 * **宁可少显示一条（留白），也不能多算一条（裁切）** —— 因此一律向下取整。
 */
internal object WidgetListCapacity {

    /**
     * 卡片中「非条目、非头部文字」的**固定**占位（dp，不随系统字体缩放）。
     *
     * 构成（三份布局实值一致）：卡片 `padding` 8dp×2 = 16 + 头部 `layout_marginBottom` 6dp = 22。
     * 需同步的三份布局（本值被三个列表型规格共用）：
     * - `widget_list_vertical_native.xml`（`inner_content_card` / 头部 `RelativeLayout`）
     * - `widget_week_courses_native.xml`（同构，布局注释自述「结构照抄 list_vertical」）
     * - `widget_agenda_list_native.xml`（同构，同上）
     */
    const val CHROME_FIXED_DP = 22

    /**
     * 头部标题字号（sp）：三份布局的 `tv_header_title` 默认值一致，也是运行时能取到的**最大值**
     * （`headerSizeSp` 按 S / M / L 档给出 12 / 13 / 14sp）。取最大值即「宁可少算条数」的保守方向。
     */
    const val HEADER_TITLE_SP = 14

    /** 单行文字高度与字号的经验比（`TextView` 单行 `wrap_content` ≈ 字号 × 1.2）。 */
    const val TEXT_LINE_HEIGHT = 1.2f

    /**
     * 卡片非条目占位（dp），**按系统字体缩放折算头部文字高度**。
     *
     * 为什么不能是常量：条目高度是运行时实测的（`WidgetUpdateHelper.measureRowHeightPx` 会带上
     * fontScale），若头部仍恒按 fontScale = 1.0 的 39dp 计，则 1.3 / 1.5 / 2.0 倍字体下可用高度被
     * **高估**，末条可能被静默裁切 —— 正是本文件要防的 v3.66.4 事故形态（见单测
     * `高 fontScale 下条数随之减少（行高变大）`）。
     *
     * @param fontScale 系统字体缩放；小于 1 时按 1 处理（此时真实头部更小，取大值同样保守）
     */
    fun chromeDpFor(fontScale: Float): Int = ceil(
        CHROME_FIXED_DP + HEADER_TITLE_SP * TEXT_LINE_HEIGHT * fontScale.coerceAtLeast(1f)
    ).toInt()

    /** fontScale = 1.0 时的参考值（39dp）；单测做 dp 换算断言时使用。 */
    val CHROME_DP: Int get() = chromeDpFor(1f)

    /**
     * 行间分隔线实占高度（dp）。
     *
     * 构成（`widget_divider_horizontal.xml` 实值）：`layout_height` 1dp
     * + `layout_marginTop` 2dp + `layout_marginBottom` 2dp = 5dp。
     *
     * v4.64.17 补：此前 [rowsFor] 只按 `N × 行高` 计算，漏掉这 (N-1)×5dp，
     * 在 250dp 高 / density 3 / 行高 49dp 时余量恰好归零 —— 系统字体一放大
     * （`container_courses` 是 LinearLayout、不滚动）末条就被静默裁切，
     * 正是 v3.66.4/3.66.5 事故的形态。该布局的 margin 若改动，此值需同步。
     */
    const val DIVIDER_DP = 5

    /** 单次渲染的条数上限，避免异常尺寸下构造过大的 RemoteViews。 */
    const val MAX_ROWS = 12

    /**
     * 按可用高度推算可**完整**显示的条数。
     *
     * @param heightDp 组件高度（应传 `OPTION_APPWIDGET_MAX_HEIGHT`，即当前高度上界；
     *   `MIN_HEIGHT` 是可缩放下界，代表不了当前可用高度）
     * @param chromeDp 非条目占位（调用方应传 [chromeDpFor] 的返回值；[CHROME_DP] 仅是
     *   fontScale = 1.0 的参考值）
     * @param rowHeightPx 实测条目高度（px）
     * @param density 屏幕密度
     * @param maxRows 条数上限（[MAX_ROWS]）
     * @param dividerDp 行间分隔线占位（[DIVIDER_DP]）；0 或负数表示不计分隔线
     * @return 1..maxRows
     */
    fun rowsFor(
        heightDp: Int,
        chromeDp: Int,
        rowHeightPx: Int,
        density: Float,
        maxRows: Int,
        dividerDp: Int = DIVIDER_DP
    ): Int {
        val availablePx = (heightDp * density).toInt() - (chromeDp * density).toInt()
        // 两种保守出口：
        //  - 可用高度不足 → 仍返回 1（调用方用 minResizeHeight 保证不会小到这个程度）
        //  - 行高非法（≤0，说明测量失败且兜底也没生效）→ 返回 1，**不能**退化成除以 1px
        //    （那会算出几十条、封顶到 maxRows，反而比「算多」更严重地溢出）
        if (availablePx <= 0 || rowHeightPx <= 0) return 1
        val dividerPx = (dividerDp * density).toInt().coerceAtLeast(0)
        // N 行需要 N×行高 + (N-1)×分隔线 ⇒ 用「逐行递减累加」求最大满足 N 的值，
        // 而不是除法闭式解（除法无法把 (N-1) 的依赖写进去，闭式会系统性算多）。
        var count = 0
        var used = 0
        while (count < maxRows) {
            val next = used + rowHeightPx + if (count == 0) 0 else dividerPx
            if (next > availablePx) break
            used = next
            count++
        }
        return count.coerceIn(1, maxRows)
    }
}
