package com.shangkeschedule.tool

/**
 * 桌面端（JVM）实现（v4.66.0，K7 小组件排障）。
 *
 * 桌面版没有 Android 那套 `AppWidgetManager` 宿主，因此这几件事全部如实返回空结果：
 * 注册数 0 ⇒ 界面显示「当前平台不支持小组件排障」，而不是伪造一份诊断数据。
 */
actual object WidgetTroubleshootBridge {

    actual fun placement(): WidgetPlacement = WidgetPlacement(providerCount = 0, placedCount = 0)

    actual fun requestRefresh(): Boolean = false

    /** 桌面端没有桌面可固定，也不该弹一个「已请求添加」的假成功。 */
    actual fun requestPin(key: String): WidgetPinOutcome = WidgetPinOutcome.UNSUPPORTED

    actual fun openSystemSettings(): Boolean = false

    /** 桌面端没有国产 ROM 那套厂商自启动机制，也不该弹一个打不开的假入口。 */
    actual fun openOemStartupSettings(): Boolean = false

    actual fun manufacturer(): String? = null
}
