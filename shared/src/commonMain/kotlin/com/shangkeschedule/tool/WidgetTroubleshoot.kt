package com.shangkeschedule.tool

/**
 * 桌面小组件的放置情况（v4.66.0，K7 小组件排障）。
 *
 * @property providerCount 本应用在系统里注册的小组件 provider 数量（上课目前是 8 类）。
 * @property placedCount 这些 provider 上**已经放到桌面的**组件实例总数；0 表示用户还没添加过。
 * @property specs 逐个 provider 的明细，供「添加到桌面」列出可选项（v4.66.6 / XL-015）。
 */
data class WidgetPlacement(
    val providerCount: Int,
    val placedCount: Int,
    val specs: List<WidgetSpec> = emptyList(),
)

/**
 * 一类可添加的小组件（v4.66.6 / XL-015）。
 *
 * @property key 稳定标识，Android 侧是 provider 的全限定类名。**只在本进程内往返、从不落盘、
 *   从不上报**，因此用类名当键最省事且不会冲突；换实现时也不必迁移历史数据。
 * @property label 平台解析出的名字。刻意**不用** shared 侧另写一套文案 ——
 *   取的是 `AppWidgetProviderInfo.loadLabel`，也就是用户即将在桌面选择器里看到的那个标题，
 *   天然与选择器一致，不会出现两套名字对不上的情况。
 * @property placedCount 该类已放到桌面的实例数。
 */
data class WidgetSpec(
    val key: String,
    val label: String,
    val placedCount: Int,
)

/**
 * 请求把小组件固定到桌面的结果（v4.66.6 / XL-015）。
 *
 * 用枚举而非文案：界面按语言自己取提示，平台层不产出字符串。
 */
enum class WidgetPinOutcome {
    /** 已向系统发起请求，用户会看到一个确认弹窗。 */
    REQUESTED,

    /** 系统拒绝了这个请求（桌面不支持固定，或该 provider 未被系统识别）。 */
    REJECTED,

    /** 当前平台没有小组件机制（桌面版 / iOS / Android 8 以下）。 */
    UNSUPPORTED,
}

/**
 * 小组件排障的平台桥（v4.66.0，K7）。
 *
 * 为什么需要它：小组件「不刷新」的根因几乎都在平台侧（桌面没有重绘、后台被限制、
 * 电池优化杀进程），而排障界面跑在 `commonMain` 的 Compose 里，拿不到 `AppWidgetManager`。
 * 于是把三件平台相关的事收在这一个 `expect` 桥里：
 *
 * 1. 读放置情况 —— 用于区分「组件根本没添加」和「添加了但没刷新」，避免误报；
 * 2. 请求重绘 —— 给每个已放置的组件发一次系统更新广播，等价于把桌面小组件删掉重挂；
 * 3. 打开系统设置 —— 自启动 / 后台运行 / 电池优化只能由用户在系统页面里放开，应用不能代劳。
 *
 * v4.66.6 追加第 4 件：**请求固定到桌面**（[requestPin]），让「一个组件都没添加」的用户
 * 不必退回桌面长按才能开始用。
 *
 * 桌面端与 iOS 端没有这套机制，actual 实现一律返回空值或 `false`，界面据此给出「当前平台不支持」的
 * 提示，而不是假装刷新成功。
 */
expect object WidgetTroubleshootBridge {

    /** 读取本应用在系统里注册/放置的小组件数量。 */
    fun placement(): WidgetPlacement

    /** 请求所有已放置的小组件重绘；返回是否至少成功发出了一个刷新请求。 */
    fun requestRefresh(): Boolean

    /**
     * 请求把 [key] 那种小组件固定到桌面（v4.66.6 / XL-015）。
     *
     * 系统会在固定前弹一个确认框，由用户自己按下 —— 应用**不能**替用户决定往桌面放东西，
     * 也放不了：桌面位置只有桌面知道。
     *
     * @param key [WidgetPlacement.specs] 里的 `key`，不是一个已知值时按 [WidgetPinOutcome.REJECTED] 处理。
     */
    fun requestPin(key: String): WidgetPinOutcome

    /** 跳转到本应用的系统设置页（自启动、后台限制、电池优化都在那里）；返回是否成功拉起。 */
    fun openSystemSettings(): Boolean

    /**
     * 设备制造商（小米 / 华为 / vivo …），用于选择后台限制的引导方案。
     *
     * 非 Android 平台返回 null（那些平台无此机制）。
     *
     * 不属于设备标识，不可用于追踪；只用于选一份引导文案。
     */
    fun manufacturer(): String?
}
