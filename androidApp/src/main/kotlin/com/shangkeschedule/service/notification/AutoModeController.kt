package com.shangkeschedule.service.notification

import android.content.Context
import android.media.AudioManager
import android.app.NotificationManager
import android.util.Log
import androidx.core.content.getSystemService
import com.shangkeschedule.data.model.AutoControlMode
import com.shangkeschedule.service.PermissionNoticeNotifier

/**
 * 上课自动模式控制器：开启/关闭勿扰或静音。
 *
 * 逻辑迁自旧 `CourseAlarmReceiver.toggleMode`（伴生对象静态方法），但修掉了两处缺陷：
 *
 * 1. **静音不再被误要求勿扰权限**。旧实现把权限检查放在 `when` 之前，
 *    `AutoControlMode.SILENT` 分支白纸黑字的注释说「铃声模式切换不需要勿扰访问权限」，
 *    代码却已被上面的检查拦掉了（设置页弹窗还额外强制拦截一次），
 *    导致只想用静音的用户被迫去开一个他根本不需要的权限。
 *
 * 2. **DND 分支权限缺失时提示用户**（保留既有的 `PermissionNoticeNotifier` 提示），
 *    而不是静默 Log 一行。
 */
object AutoModeController {

    private const val TAG = "AutoModeController"

    /**
     * 切换自动模式。
     *
     * @param enableMode true = 进入勿扰/静音；false = 恢复正常
     * @return true 表示已施加（或被静默接受）；false 表示因缺权限等原因未生效
     */
    fun toggle(context: Context, enableMode: Boolean, modeType: AutoControlMode): Boolean {
        return when (modeType) {
            AutoControlMode.DND -> toggleDnd(context, enableMode)
            AutoControlMode.SILENT -> toggleSilent(context, enableMode)
        }
    }

    /**
     * 勿扰模式切换：**需要**「勿扰访问」权限。
     * 未授权时给出可点击直达系统设置页的提示通知（用户侧可见、可恢复）。
     */
    private fun toggleDnd(context: Context, enableMode: Boolean): Boolean {
        val nm = context.getSystemService<NotificationManager>()
        if (nm == null) {
            Log.w(TAG, "NotificationManager 不可用，勿扰切换跳过")
            return false
        }
        if (!nm.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "勿扰访问权限未授予，已提示用户")
            PermissionNoticeNotifier.notifyDndMissing(context)
            return false
        }
        PermissionNoticeNotifier.clear(context, PermissionNoticeNotifier.NOTICE_ID_DND)
        return runCatching {
            nm.setInterruptionFilter(
                if (enableMode) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            true
        }.onFailure { Log.w(TAG, "勿扰切换被系统拒绝", it) }.getOrDefault(false)
    }

    /**
     * 静音模式切换：**不需要**「勿扰访问」权限（只改铃声模式）。
     * 个别 OEM 仍可能抛 [SecurityException]，此处兜住并记录，不让提醒链路崩掉。
     */
    private fun toggleSilent(context: Context, enableMode: Boolean): Boolean {
        val audioManager = context.getSystemService<AudioManager>()
        if (audioManager == null) {
            Log.w(TAG, "AudioManager 不可用，静音切换跳过")
            return false
        }
        return try {
            audioManager.ringerMode = if (enableMode) AudioManager.RINGER_MODE_SILENT
            else AudioManager.RINGER_MODE_NORMAL
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "静音模式切换被系统拒绝", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "静音模式切换失败", e)
            false
        }
    }
}
