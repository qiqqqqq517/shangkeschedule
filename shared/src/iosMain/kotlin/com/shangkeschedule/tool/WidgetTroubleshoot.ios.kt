package com.shangkeschedule.tool

/**
 * iOS 端实现（v4.66.0，K7 小组件排障）。
 *
 * iOS 的桌面小组件由 WidgetKit extension 提供，本仓库尚未接入（小组件只有 Android 端实现），
 * 因此这里如实返回空结果，界面会显示「当前平台不支持小组件排障」。
 */
actual object WidgetTroubleshootBridge {

    actual fun placement(): WidgetPlacement = WidgetPlacement(providerCount = 0, placedCount = 0)

    actual fun requestRefresh(): Boolean = false

    actual fun openSystemSettings(): Boolean = false
}
