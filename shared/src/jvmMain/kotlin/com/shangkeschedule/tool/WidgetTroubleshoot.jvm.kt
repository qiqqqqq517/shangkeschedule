package com.shangkeschedule.tool

/**
 * 桌面端（JVM）实现（v4.66.0，K7 小组件排障）。
 *
 * 桌面版没有 Android 那套 `AppWidgetManager` 宿主，因此三件事全部如实返回空结果：
 * 注册数 0 ⇒ 界面显示「当前平台不支持小组件排障」，而不是伪造一份诊断数据。
 */
actual object WidgetTroubleshootBridge {

    actual fun placement(): WidgetPlacement = WidgetPlacement(providerCount = 0, placedCount = 0)

    actual fun requestRefresh(): Boolean = false

    actual fun openSystemSettings(): Boolean = false

    actual fun manufacturer(): String? = null
}
