package com.shangkeschedule.tool

import platform.UIKit.UIDevice

/**
 * iOS 端实现：`iOS 18.1`。
 *
 * 注意：这里刻意**不取** `identifierForVendor` / `name` —— 前者是可追踪标识、后者是用户自定义设备名，
 * 都不该出现在反馈文本里。
 */
actual fun systemDescription(): String {
    val device = UIDevice.currentDevice
    return device.systemName + " " + device.systemVersion
}
