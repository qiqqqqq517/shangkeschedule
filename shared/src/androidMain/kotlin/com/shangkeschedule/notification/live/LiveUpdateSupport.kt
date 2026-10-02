package com.shangkeschedule.notification.live

import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * 「实况通知」（状态栏胶囊 / 厂商实况区）能力探测（补全方案 §G2）。
 *
 * ## 为什么只做探测，不做厂商写入
 * 1. **公版通道**：Android 16（API 36）起系统提供官方「实况更新」——
 *    通知声明「推广常驻」（promoted ongoing）后可由系统呈现为状态栏胶囊，
 *    并能带一段芯片短文本。本类只负责回答「本机现在支不支持」，
 *    真正的声明动作在投递通知处（见 `NextClassNotifier.applyChip`）。
 * 2. **厂商通道**：小米「超级岛」等厂商实况区**没有公开接入协议**，
 *    焦点通知的 extras 键名未公开且按白名单放行，第三方自造键名只会变成
 *    永远不会生效的死代码，还会给人「已接入」的错觉。因此这里**不伪造任何厂商键值**，
 *    只在界面上如实告知：小米机型上实况区由系统按通知自行呈现。
 * 3. **探测结果不参与投递决策**：通知一律先按公版能力投递，不支持时系统自然降级为
 *    普通常驻通知，探测结果只用于界面告知与「是否值得声明胶囊」。
 */
object LiveUpdateSupport {

    /**
     * 本机现在是否支持把常驻通知呈现为状态栏胶囊。
     *
     * 需要两件事同时成立：系统版本 ≥ Android 16，且系统**当前允许**本应用投递推广通知
     * （用户可在通知设置里关闭，关闭后本方法返回 false —— 此时再声明也是空操作）。
     */
    fun supportsLiveUpdate(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        return runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.canPostPromotedNotifications() == true
        }.getOrDefault(false)
    }

    /**
     * 是否小米系机型（小米 / Redmi / POCO）。
     *
     * 只用于补一句「超级岛由系统呈现」的说明，**不**据此走任何厂商私有分支。
     */
    fun isXiaomiDevice(): Boolean {
        val signature = (Build.MANUFACTURER + "|" + Build.BRAND).lowercase()
        return signature.contains("xiaomi") || signature.contains("redmi") || signature.contains("poco")
    }
}
