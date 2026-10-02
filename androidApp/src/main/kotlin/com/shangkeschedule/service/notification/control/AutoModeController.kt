package com.shangkeschedule.service.notification.control

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
 * 逻辑迁自旧 `CourseAlarmReceiver.toggleMode`（伴生对象静态方法），并在此后修掉四处缺陷：
 *
 * 1. **静音不再被误要求勿扰权限**。旧实现把权限检查放在 `when` 之前，
 *    `AutoControlMode.SILENT` 分支白纸黑字的注释说「铃声模式切换不需要勿扰访问权限」，
 *    代码却已被上面的检查拦掉了（设置页弹窗还额外强制拦截一次），
 *    导致只想用静音的用户被迫去开一个他根本不需要的权限。
 *
 * 2. **DND 分支权限缺失时提示用户**（保留既有的 `PermissionNoticeNotifier` 提示），
 *    而不是静默 Log 一行。
 *
 * 3. **DND 也记录并还原进入前的过滤档**（对齐 [toggleSilent] 早已有的语义）。
 *    旧实现在关闭时无条件写 `INTERRUPTION_FILTER_ALL`，
 *    等于替用户改掉了他自己打开的勿扰设置。记录一次、只还自己动过的那一次。
 *
 * 4. **切换后回读校验**。`setInterruptionFilter` / `ringerMode` 赋值都可能「看起来调用成功、
 *    实际没生效」（部分 OEM 定制会静默忽略）。旧实现无条件 `return true`，
 *    上层因此认为降噪已生效，实际用户在课上照样响铃且无从排查。
 *    现在写入后立刻读回比对，不一致就记警告并返回 `false`。
 */
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
    fun toggle(context: Context, enableMode: Boolean, modeType: AutoControlMode): Boolean {
        return when (modeType) {
            AutoControlMode.DND -> toggleDnd(context, enableMode)
            AutoControlMode.SILENT -> toggleSilent(context, enableMode)
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val applied = runCatching {
            if (enableMode) {
                // 只在「还没记录过」时落盘，且当前并非目标档才记 ——
                // 否则用户自己开的勿扰会被记成「我们开的」，下课就替他关掉了。
                if (!prefs.contains(KEY_DND_FILTER_BEFORE)) {
                    prefs.edit()
                        .putInt(KEY_DND_FILTER_BEFORE, nm.currentInterruptionFilter)
                        .apply()
                }
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else {
                // 取回进入前的档；无记录（旧版本升级而来 / 从未记录）时退回 ALL，
                // 与旧行为一致，且是最不打扰用户的还原目标。
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
        val audioManager = context.getSystemService<AudioManager>()
        if (audioManager == null) {
            Log.w(TAG, "AudioManager 不可用，静音切换跳过")
            return false
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return try {
            if (enableMode) {
                if (!prefs.contains(KEY_RINGER_MODE_BEFORE)) {
                    val current = audioManager.ringerMode
                    if (current != AudioManager.RINGER_MODE_SILENT) {
                        prefs.edit().putInt(KEY_RINGER_MODE_BEFORE, current).apply()
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
                // 取回进入前的模式；无记录（旧版本升级而来 / 从未记录）时退回 NORMAL，
                // 保持与旧行为一致的兜底，而不是突然保持静音。
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
