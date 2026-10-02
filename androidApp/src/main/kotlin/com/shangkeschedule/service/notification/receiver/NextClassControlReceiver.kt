package com.shangkeschedule.service.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.service.notification.notify.NextClassNotifier
import com.shangkeschedule.service.notification.schedule.NextClassNotificationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 「下一节课」常驻通知里的「关闭常驻」动作（补全方案 §G1 的「能被彻底撤下」）。
 *
 * 常驻通知划不掉，所以必须有应用内的退路。点一下这个动作要同时做到三件事：
 *  1. **立刻撤下通知**（同步执行，用户按下就必须消失）；
 *  2. 把设置里的开关写回 false（否则下一次排程又会把它挂回来）；
 *  3. 注销 15 分钟周期刷新任务（否则空转耗电，且下次开机又冒出来）。
 *
 * 顺序上有意「先撤通知、后异步落库」：即使落库失败，用户看到的结果也是通知没了，
 * 而最坏情况（开关仍为 true）会在下一轮 `reschedule()` 时重新显示 —— 可自愈。
 */
class NextClassControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISABLE) return

        val appContext = context.applicationContext
        NextClassNotifier(appContext).cancel()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Deps.appSettingsRepository.updateNextClassNotificationEnabled(false)
                NextClassNotificationWorker.cancel(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "关闭「下一节课」常驻通知失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NextClassControlReceiver"

        /** 通知动作的广播 action。 */
        const val ACTION_DISABLE = "com.shangkeschedule.action.DISABLE_NEXT_CLASS_NOTIFICATION"
    }

    private object Deps : KoinComponent {
        val appSettingsRepository: AppSettingsRepository by inject()
    }
}
