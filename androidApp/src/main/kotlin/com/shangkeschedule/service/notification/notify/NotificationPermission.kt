package com.shangkeschedule.service.notification.notify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * `POST_NOTIFICATIONS` 权限检查的**唯一**实现。
 *
 * ## 为什么必须收敛
 *
 * Android 13+ 起投递通知需要运行时权限。早八闹钟相关的三个通知器
 * （`MorningAlarmNotifier` / `MorningAlarmReviewNotifier` / `MorningAlarmWriteStatusReporter`）
 * 此前各自写了一份同样的判断，逐字重复且分散在三处：
 * 任何一处漏判都会造成「状态写进了本地、通知却没出来」——用户看到的是开关开着、
 * 系统里什么都没有，而代码里每个类看起来都「检查了权限」。
 *
 * 语义要点：Android 13 **以下**默认具备投递能力，因此返回值在低版本恒为 true，
 * 不能写成 `checkSelfPermission(...) == GRANTED` 就直接返回——那样低版本会被判成无权限。
 */
object NotificationPermission {

    /** @return true 表示当前可以投递通知。 */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
