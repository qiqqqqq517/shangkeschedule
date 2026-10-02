package com.shangkeschedule.tool

/**
 * 桌面小组件的放置情况（v4.66.0，K7 小组件排障）。
 *
 * @property providerCount 本应用在系统里注册的小组件 provider 数量（上课目前是 8 类）。
 * @property placedCount 这些 provider 上**已经放到桌面的**组件实例总数；0 表示用户还没添加过。
 */
data class WidgetPlacement(
    val providerCount: Int,
    val placedCount: Int,
)

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
 * 桌面端与 iOS 端没有这套机制，actual 实现一律返回空值或 `false`，界面据此给出「当前平台不支持」的
 * 提示，而不是假装刷新成功。
 */
expect object WidgetTroubleshootBridge {

    /** 读取本应用在系统里注册/放置的小组件数量。 */
    fun placement(): WidgetPlacement

    /** 请求所有已放置的小组件重绘；返回是否至少成功发出了一个刷新请求。 */
    fun requestRefresh(): Boolean

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
