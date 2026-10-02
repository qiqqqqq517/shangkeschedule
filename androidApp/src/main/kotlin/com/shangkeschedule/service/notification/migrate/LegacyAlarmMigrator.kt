package com.shangkeschedule.service.notification.migrate

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.shangkeschedule.service.PermissionNoticeNotifier

/**
 * 升级迁移：清理旧版通知子系统遗留在系统里的闹钟与通知。
 *
 * 为什么必须做：本版本重写了通知架构，闹钟请求码从旧的 `50000–50110` 区间
 * 迁到新命名空间（`61000+` / `63000+`），接收器也换了类名与 action。
 * 但 **PendingIntent 与已排闹钟是存在系统里的、跟随应用包名的持久状态**：
 * 升级安装后，旧版排下的闹钟依然存在，且其指定的接收器类
 * （旧 `CourseAlarmReceiver`）仍在 Manifest 里，于是旧闹钟会按**旧逻辑**触发——
 * 用户会同时收到新旧两套提醒，或看到旧版遗留通知。
 *
 * 因此首次运行时（幂等标记）：
 *  1. 注销旧区间全部闹钟（课程提醒 50010–50110 + 自动模式 50001/50002）；
 *  2. 取消旧版槽位号充当通知 ID 的那些通知（50000–50200，但**跳过当前实现仍在用的
 *     50190/50191 权限缺失提示**——旧槽位号与它们落在同一区间只是历史巧合）；
 *  3. 清掉旧的 `alarm_ids_prefs` 死数据（旧实现登记后从未真正读取生效）。
 */
object LegacyAlarmMigrator {

    private const val TAG = "LegacyAlarmMigrator"
    private const val PREFS_NAME = "notification_migration"
    private const val KEY_MIGRATED = "legacy_alarms_cleared"

    /** 旧版课程提醒通知 action（用于精确匹配后注销）。 */
    private const val LEGACY_ACTION_COURSE_REMIND = "com.shangkeschedule.ACTION_COURSE_REMIND"

    /** 旧版课程提醒闹钟槽位区间。 */
    private val LEGACY_SLOT_RANGE = 50_000..50_200

    /** 旧版登记簿 prefs（死数据）。 */
    private const val LEGACY_ALARM_IDS_PREFS = "alarm_ids_prefs"

    /**
     * 执行迁移（幂等）。
     *
     * @return true 表示本次真的执行了清理
     */
    fun migrateIfNeeded(context: Context): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return false

        return runCatching {
            val clearedAlarms = clearLegacyAlarms(context)
            val clearedNotifications = clearLegacyNotifications(context)
            clearLegacyPrefs(context)

            prefs.edit { putBoolean(KEY_MIGRATED, true) }
            Log.i(
                TAG,
                "旧版通知迁移完成：注销 $clearedAlarms 个旧闹钟、$clearedNotifications 条旧通知"
            )
            true
        }.onFailure { Log.e(TAG, "旧版通知迁移失败（不影响新排程）", it) }.getOrDefault(false)
    }

    /**
     * 注销旧区间闹钟。
     *
     * 按旧实现的 Intent 构造方式还原（同一 action + 同一 requestCode ⇒
     * `FLAG_NO_CREATE` 能命中已存在的 PendingIntent 并注销）。
     * 旧接收器类仍留在 Manifest（冻结一版做兜底），故此处用类名反射构造 Intent，
     * 避免直接依赖一个计划移除的类。
     */
    private fun clearLegacyAlarms(context: Context): Int {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return 0
        val legacyReceiver = resolveLegacyReceiver(context) ?: return 0
        var cleared = 0
        for (requestCode in LEGACY_SLOT_RANGE) {
            val intent = Intent().apply {
                setClassName(context.packageName, legacyReceiver)
                action = LEGACY_ACTION_COURSE_REMIND
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
                cleared++
            }
        }
        return cleared
    }

    /** 旧接收器是否仍在（冻结期内）。缺失则说明已彻底移除，无需清理闹钟。 */
    private fun resolveLegacyReceiver(context: Context): String? {
        val candidate = "com.shangkeschedule.service.CourseAlarmReceiver"
        return runCatching {
            Class.forName(candidate)
            candidate
        }.getOrNull()
    }

    /**
     * 取消旧版「槽位号当通知 ID」的遗留通知，返回真正被清掉的数量。
     *
     * 旧区间里绝大多数 id 从未投递过通知，因此不能无条件累加计数
     * （否则日志恒报 201 条，掩盖真实数量），先取活跃通知再计数。
     */
    private fun clearLegacyNotifications(context: Context): Int {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return 0
        val live = runCatching { nm.activeNotifications.map { it.id }.toSet() }
            .getOrDefault(emptySet())
        var cleared = 0
        for (id in LEGACY_SLOT_RANGE) {
            // 旧槽位号与当前权限缺失提示的固定 ID 撞在同一区间（50190/50191），
            // 但它们仍由 PermissionNoticeNotifier 在用，误清会让「权限缺失」提示消失。
            if (id == PermissionNoticeNotifier.NOTICE_ID_EXACT_ALARM ||
                id == PermissionNoticeNotifier.NOTICE_ID_DND
            ) {
                continue
            }
            runCatching { nm.cancel(id) }
            if (id in live) cleared++
        }
        return cleared
    }

    /** 清掉旧版从未真正生效的闹钟登记簿。 */
    private fun clearLegacyPrefs(context: Context) {
        runCatching {
            context.getSharedPreferences(LEGACY_ALARM_IDS_PREFS, Context.MODE_PRIVATE)
                .edit { clear() }
        }.onFailure { Log.w(TAG, "清理旧闹钟登记簿失败", it) }
    }
}
