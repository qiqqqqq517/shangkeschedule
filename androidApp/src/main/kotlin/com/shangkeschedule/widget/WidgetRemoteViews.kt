package com.shangkeschedule.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.shangkeschedule.MainActivity
import com.shangkeschedule.R
import com.shangkeschedule.widget.WidgetRoute

/**
 * 「点击整块组件跳转 MainActivity」的 PendingIntent 构造（v3.66.3 收敛自四个 Renderer 的逐字重复）。
 *
 * ## 路由参数（v4.67.39）
 *
 * 原实现**不带任何参数**：8 个组件点进去一律落到 App 默认页，用户看到「桌面写着下一节课是 X」，
 * 点进去却得自己再找一次 X 在哪一页 —— 组件省下的那一次点击被又还了回去。
 *
 * 现按 [providerClass] 决定落点（今日 / 课程表 / 日程），路由值由 shared 侧 [WidgetRoute]
 * 给出（纯字符串、可单测），本文件只负责塞进 extras。
 *
 * ## PendingIntent 身份不变（重要）
 *
 * 该 PendingIntent 的身份是 (requestCode = 0, filterEquals)，与 CourseAlarmReceiver
 * 的课程闹钟通知、DynamicIslandService 的灵动岛「打开应用」共用同一个身份（filterEquals
 * 不比较 Intent.flags 与 extras）；`FLAG_UPDATE_CURRENT` 会**替换 extras**。
 *
 * ⇒ 因此「只改 extras、不改 flags 与 requestCode」是硬约束：改了 flags 会让
 * DynamicIslandService 先创建的同身份 PendingIntent 行为被静默改写。
 * 同理，**每个组件的请求码必须不同**，否则后渲染的组件会覆盖前一个的路由
 * （extras 是随 PendingIntent 走的，不是随 RemoteViews 走的）。
 */
internal fun bindWidgetClickIntent(
    context: Context,
    rv: RemoteViews,
    providerClass: Class<*>? = null,
) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (providerClass != null) {
            putExtra(
                WidgetRoute.EXTRA_ROUTE,
                WidgetRoute.routeForProvider(providerClass.name)
            )
        }
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        widgetClickRequestCode(providerClass),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    rv.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
}

/**
 * 逐组件的请求码：让 8 个组件各自持有**独立**的 PendingIntent 身份。
 *
 * 关键在于 `PendingIntent.getActivity` 的匹配只比较 requestCode 与 `filterEquals`
 * （组件 + action + data），**不比较 extras**。若 8 个组件都用 requestCode = 0，
 * 则它们的 PendingIntent 是同一个对象，`FLAG_UPDATE_CURRENT` 只会保留最后一次写入的 extras ——
 * 结果是「所有组件点进去都到同一个页面」，路由等于没做。
 *
 * 用类名的稳定 hash 取一个非负码（避开 `hashCode()` 可能为负）。
 * 冲突概率对 8 个类可忽略；即便冲突也只是两个组件落到同一路由，不会崩、不会泄漏。
 */
internal fun widgetClickRequestCode(providerClass: Class<*>?): Int {
    if (providerClass == null) return WIDGET_CLICK_CODE_FALLBACK
    return (providerClass.name.hashCode() and Int.MAX_VALUE) % WIDGET_CLICK_CODE_RANGE
        + WIDGET_CLICK_CODE_BASE
}

private const val WIDGET_CLICK_CODE_BASE = 10_000
private const val WIDGET_CLICK_CODE_RANGE = 90_000

/**
 * 未能拿到 provider 类时的兜底码（沿用历史值 0）。
 *
 * 注意：保留 0 是为了与升级前**已存在的**那个 PendingIntent 保持同一身份，
 * 否则旧 PendingIntent 撤不掉、会在用户桌面残留一个「点了没反应」的组件。
 */
private const val WIDGET_CLICK_CODE_FALLBACK = 0
