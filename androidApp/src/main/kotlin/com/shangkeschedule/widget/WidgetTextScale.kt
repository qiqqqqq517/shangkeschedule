package com.shangkeschedule.widget

/**
 * 小组件「按空间放大 + 按字数自适应」字号策略（v4.61.0）。
 *
 * 背景：RemoteViews 不支持 `autoSizeTextType`（见 `docs/widget-display-optimization.md` §5.2），
 * 真正的量体裁衣做不到；可行的是 App 侧分档 + `RemoteViews.setTextViewTextSize()` 运行时压入。
 *
 * 两条规则：
 * 1. **空间定字号**：按组件 `minWidth` 分 S / M / L 三档，增量 +0 / +1 / +2sp。
 *    取 `minWidth`（下界）而非上界，保证再窄的实际宽度也不溢出。
 * 2. **同组件内统一**：课程名/时间/地点字号只与空间档有关，与课程名长短无关；
 *    长名由 `maxLines=1 + ellipsize=end` 截断，保证同一组件内所有条目视觉一致。
 *
 * 纯函数，由 `WidgetTextScaleTest` 锁死边界。
 */
enum class WidgetSpaceClass { S, M, L }

/** 空间档阈值（dp）：S < 200 ≤ M < 320 ≤ L。缺失/非法宽度一律回落 S。 */
fun spaceClassFor(minWidthDp: Int): WidgetSpaceClass = when {
    minWidthDp >= 320 -> WidgetSpaceClass.L
    minWidthDp >= 200 -> WidgetSpaceClass.M
    else -> WidgetSpaceClass.S
}

/** 空间档字号增量（sp）：S +0 / M +1 / L +2。 */
fun spaceDeltaSp(space: WidgetSpaceClass): Int = when (space) {
    WidgetSpaceClass.S -> 0
    WidgetSpaceClass.M -> 1
    WidgetSpaceClass.L -> 2
}

/** 条目课程名基准（sp）：v4.61.0 起条目 A / B 统一 14sp（教师行删除省出的空间即来源）。 */
const val ROW_NAME_BASE_SP = 14

/** 条目地点/时间基准（sp）：v4.61.0 起 10sp → 11sp。 */
const val ROW_META_BASE_SP = 11

/** 栏头日期基准（sp）：v4.61.0 起 11sp → 12sp。 */
const val HEADER_BASE_SP = 12

/**
 * 课程名字号 = 基准 + 空间增量（与课程名长短无关，同组件内保持一致）。
 *
 * @param baseSp 条目 A / B 均为 [ROW_NAME_BASE_SP]；Tiny 传 14（Tiny 强制 S 档）。
 */
fun courseNameSizeSp(space: WidgetSpaceClass, baseSp: Int = ROW_NAME_BASE_SP): Float =
    (baseSp + spaceDeltaSp(space)).toFloat()

/** 条目地点/时间字号 = 基准 + 空间增量（不随字数变，地点过长由 ellipsize 截断）。 */
fun rowMetaSizeSp(space: WidgetSpaceClass): Float =
    (ROW_META_BASE_SP + spaceDeltaSp(space)).toFloat()

/** 栏头日期字号 = 基准 + 空间增量。 */
fun headerSizeSp(space: WidgetSpaceClass): Float =
    (HEADER_BASE_SP + spaceDeltaSp(space)).toFloat()
