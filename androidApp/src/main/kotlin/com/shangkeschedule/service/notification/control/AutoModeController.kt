package com.shangkeschedule.service.notification.control

import android.content.Context
import android.media.AudioManager
import android.app.NotificationManager
import android.util.Log
import androidx.core.content.getSystemService
import com.shangkeschedule.data.model.AutoControlMode
import com.shangkeschedule.service.PermissionNoticeNotifier

/** 仅恢复本应用记录过的系统状态；切换类型前先归还旧类型。 */
object AutoModeController {

    private const val TAG = "AutoModeController"

    /** 记录「进入自动模式前的状态」的 SharedPreferences 名。 */
    private const val PREFS_NAME = "auto_mode_controller"

    /** 静音分支：进入前的铃声模式。 */
    private const val KEY_RINGER_MODE_BEFORE = "ringer_mode_before"

    /** 勿扰分支：进入前的过滤档（XL-002 新增）。 */
    private const val KEY_DND_FILTER_BEFORE = "dnd_filter_before"

    /**
     * 切换自动模式。
     *
     * @param enableMode true = 进入勿扰/静音；false = 恢复正常
     * @return true 表示已施加**并经回读确认生效**；false 表示缺权限或系统未真正生效
     */
    @Synchronized
    fun toggle(context: Context, enableMode: Boolean, modeType: AutoControlMode): Boolean {
        // 所有入口共用锁，防止 START、END 与前台校准交错覆盖恢复记录。
        for (other in AutoControlMode.entries) {
            if (!enableMode || other != modeType) {
                val restored = when (other) {
                    AutoControlMode.DND -> toggleDnd(context, false)
                    AutoControlMode.SILENT -> toggleSilent(context, false)
                }
                if (!restored) return false
            }
        }
        if (!enableMode) return true
        return when (modeType) {
            AutoControlMode.DND -> toggleDnd(context, true)
            AutoControlMode.SILENT -> toggleSilent(context, true)
        }
    }

    /**
     * 勿扰模式切换：**需要**「勿扰访问」权限。
     * 未授权时给出可点击直达系统设置页的提示通知（用户侧可见、可恢复）。
     *
     * 关闭时**还原进入前的过滤档**而非无条件写 `INTERRUPTION_FILTER_ALL`：
     * 用户原本就开着勿扰（他自己的偏好），下课被强制关掉等于替他改了系统设置。
     * 进入前只记录一次（连续重排/校准不会覆盖记录），还原成功后清除。
     */
    private fun toggleDnd(context: Context, enableMode: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!enableMode && !prefs.contains(KEY_DND_FILTER_BEFORE)) return true
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
        val current = nm.currentInterruptionFilter
        if (enableMode && current == NotificationManager.INTERRUPTION_FILTER_PRIORITY) return true
        if (!enableMode && current != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
            // 用户已经自行调整过，记录不再拥有当前系统状态。
            prefs.edit().remove(KEY_DND_FILTER_BEFORE).commit()
            return true
        }

        val applied = runCatching {
            if (enableMode) {
                // 先持久化恢复记录，再改变系统；原本已在目标档的分支已提前返回。
                if (!prefs.contains(KEY_DND_FILTER_BEFORE)) {
                    check(prefs.edit().putInt(KEY_DND_FILTER_BEFORE, current).commit()) {
                        "无法持久化勿扰恢复记录"
                    }
                }
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else {
                // 只有存在本应用的记录才到达这里，不能用默认值推断状态归属。
                val restore = prefs.getInt(
                    KEY_DND_FILTER_BEFORE,
                    NotificationManager.INTERRUPTION_FILTER_ALL
                )
                nm.setInterruptionFilter(restore)
                restore
            }
        }.onFailure {
            Log.w(TAG, "勿扰切换被系统拒绝", it)
        }.getOrElse {
            Log.w(TAG, "勿扰切换异常，跳过还原")
            return false
        }

        if (enableMode) {
            val actual = nm.currentInterruptionFilter
            if (actual != applied) {
                Log.w(TAG, "勿扰写入后回读不一致：期望=$applied 实际=$actual，判定未生效")
                return false
            }
        } else {
            // 还原成功后才清记录：失败时保留记录，下一次 END 还能重试。
            if (nm.currentInterruptionFilter == applied) {
                prefs.edit().remove(KEY_DND_FILTER_BEFORE).apply()
            } else {
                Log.w(
                    TAG,
                    "勿扰还原回读不一致：期望=$applied 实际=${nm.currentInterruptionFilter}，保留记录待重试"
                )
                return false
            }
        }
        return true
    }

    /**
     * 静音模式切换：**不需要**「勿扰访问」权限（只改铃声模式）。
     * 个别 OEM 仍可能抛 [SecurityException]，此处兜住并记录，不让提醒链路崩掉。
     *
     * 关闭时**恢复进入前的铃声模式**，而不是无条件写 [AudioManager.RINGER_MODE_NORMAL]：
     * 用户原本用「仅振动/正常」是个人偏好，下课被强制切成 NORMAL 等于替他改了系统设置。
     * 进入前只记录一次（连续重排/校准不会覆盖记录），关闭成功后清除。
     */
    private fun toggleSilent(context: Context, enableMode: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!enableMode && !prefs.contains(KEY_RINGER_MODE_BEFORE)) return true
        val audioManager = context.getSystemService<AudioManager>()
        if (audioManager == null) {
            Log.w(TAG, "AudioManager 不可用，静音切换跳过")
            return false
        }
        val current = audioManager.ringerMode
        if (enableMode && current == AudioManager.RINGER_MODE_SILENT) return true
        if (!enableMode && current != AudioManager.RINGER_MODE_SILENT) {
            prefs.edit().remove(KEY_RINGER_MODE_BEFORE).commit()
            return true
        }
        return try {
            if (enableMode) {
                if (!prefs.contains(KEY_RINGER_MODE_BEFORE)) {
                    check(prefs.edit().putInt(KEY_RINGER_MODE_BEFORE, current).commit()) {
                        "无法持久化静音恢复记录"
                    }
                }
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                if (audioManager.ringerMode != AudioManager.RINGER_MODE_SILENT) {
                    Log.w(
                        TAG,
                        "静音写入后回读不一致：期望=${AudioManager.RINGER_MODE_SILENT} " +
                            "实际=${audioManager.ringerMode}，判定未生效"
                    )
                    return false
                }
            } else {
                // 仅归还本应用实际施加过的铃声状态。
                val restore = prefs.getInt(KEY_RINGER_MODE_BEFORE, AudioManager.RINGER_MODE_NORMAL)
                audioManager.ringerMode = restore
                if (audioManager.ringerMode == restore) {
                    prefs.edit().remove(KEY_RINGER_MODE_BEFORE).apply()
                } else {
                    Log.w(
                        TAG,
                        "铃声还原回读不一致：期望=$restore 实际=${audioManager.ringerMode}，保留记录待重试"
                    )
                    return false
                }
            }
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
