package com.shangkeschedule.service.notification.control

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.shangkeschedule.data.model.AutoControlMode

/**
 * **当前系统是否已处于目标模式**的探测。
 *
 * 与 [AutoModeController] 成对：那个负责「改成目标状态」，这个负责「先看看是不是已经在了」。
 *
 * ## 为什么必须探测
 * 勿扰/铃声切换会**实际改变设备状态**（且用户可能在系统里手动改过）。
 * 不做差异判断就会每次同步都重新设置一遍——对用户是可见的抖动，
 * 也会把用户手动做的调整反复覆盖。
 *
 * 旧实现把这段判断写在调度器内部（`NotificationScheduler` 尾部的游离声明），
 * 与「切换」逻辑分离在两个概念层，故一并归入 `control` 作用域。
 */
internal object AutoModeStateProbe {

    /** 当前设备是否已处于 [mode] 对应的「开启」状态。 */
    fun isModeOn(context: Context, mode: AutoControlMode): Boolean = when (mode) {
        AutoControlMode.DND -> {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }

        AutoControlMode.SILENT -> {
            val manager = context.getSystemService(AudioManager::class.java)
            manager?.ringerMode == AudioManager.RINGER_MODE_SILENT
        }
    }
}
