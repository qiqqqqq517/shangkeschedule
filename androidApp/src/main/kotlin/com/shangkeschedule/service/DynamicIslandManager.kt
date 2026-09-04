package com.shangkeschedule.service

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.WidgetRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 状态栏「灵动岛」服务调度器（Android 端）。
 *
 * 采用「时间窗口启停」策略（轻量、不常驻）：
 *  - 每天「第一节课开始 − 提前量」→「最后一节课结束」为一个显示窗口；
 *  - 窗口开始时刻：AlarmManager 精确闹钟启动 [DynamicIslandService]（前台服务）；
 *  - 窗口结束时刻：AlarmManager 精确闹钟停止服务并移除通知，并顺带排程下一个未来窗口；
 *  - 窗口之外（夜间 / 无课 / 未到点）没有任何服务常驻，只保留闹钟，兼顾实时更新与省电。
 *
 * 由 [com.shangkeschedule.data.sync.SyncManager] 在共享层同步完成（含设置变更 / 开机 / 数据变更）
 * 时调用 [sync]，重新计算最近一个未来窗口并重排闹钟。
 */
@Single
class DynamicIslandManager(
    private val appContext: Context,
    private val appSettingsRepository: AppSettingsRepository,
    private val widgetRepository: WidgetRepository
) {

    /**
     * 依据当前设置、通知权限与时间窗口，重排闹钟并按需启停服务。
     */
    suspend fun sync() {
        val settings = appSettingsRepository.getAppSettingsOnce()
        if (!settings.dynamicIslandEnabled || !hasNotificationPermission()) {
            cancelAlarms()
            DynamicIslandService.stop(appContext)
            return
        }

        val window = findNextWindow(settings.remindBeforeMinutes) ?: run {
            cancelAlarms()
            DynamicIslandService.stop(appContext)
            return
        }

        scheduleWindow(window)
        val now = System.currentTimeMillis()
        if (now in window.startMillis until window.endMillis) {
            // 当前正处于窗口内（例如开机 / 设置变更时正在上课）→ 立即启动
            DynamicIslandService.start(appContext)
        } else {
            // 尚未到窗口开始，或窗口已过（此时排程的是未来的窗口）→ 不常驻
            DynamicIslandService.stop(appContext)
        }
    }

    /**
     * 停服务后重新排程下一个未来窗口（供 STOP 闹钟与窗口外自停兜底调用）。
     */
    suspend fun scheduleNextWindow() {
        val settings = appSettingsRepository.getAppSettingsOnce()
        if (!settings.dynamicIslandEnabled || !hasNotificationPermission()) {
            cancelAlarms()
            return
        }
        val window = findNextWindow(settings.remindBeforeMinutes) ?: run {
            cancelAlarms()
            return
        }
        scheduleWindow(window)
    }

    // ---------------------------------------------------------------------
    // 窗口计算
    // ---------------------------------------------------------------------

    data class DynamicIslandWindow(
        val date: LocalDate,
        val startMillis: Long,
        val endMillis: Long
    )

    /** 从今天起向后找最近一个「尚未结束」的显示窗口（widget 表预计算未来 7 天课程）。 */
    private suspend fun findNextWindow(leadMinutes: Int): DynamicIslandWindow? {
        val today = LocalDate.now()
        val courses = runCatching {
            widgetRepository
                .getWidgetCoursesByDateRange(today.toString(), today.plusDays(6).toString())
                .first()
        }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        for (i in 0L..6L) {
            val date = today.plusDays(i)
            val window = computeWindow(courses, leadMinutes, date) ?: continue
            if (window.endMillis > now) return window
        }
        return null
    }

    companion object {
        const val ACTION_DYNAMIC_ISLAND_START = "com.shangkeschedule.ACTION_DYNAMIC_ISLAND_START"
        const val ACTION_DYNAMIC_ISLAND_STOP = "com.shangkeschedule.ACTION_DYNAMIC_ISLAND_STOP"

        private const val REQUEST_CODE_START = 60001
        private const val REQUEST_CODE_STOP = 60002
        private const val TAG = "DynamicIslandManager"

        private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE

        /**
         * 依据某天课程计算显示窗口：第一节课开始 − lead → 最后一节课结束。
         * 当天无有效课程时返回 null。
         */
        fun computeWindow(
            courses: List<WidgetCourse>,
            leadMinutes: Int,
            date: LocalDate
        ): DynamicIslandWindow? {
            val valid = courses
                .filter { it.date == date.toString() }
                .filter { !it.isSkipped && it.startTime.isNotBlank() && it.endTime.isNotBlank() }
            if (valid.isEmpty()) return null
            val firstStart = valid.minByOrNull { it.startTime }?.startTime?.let(::parseTime) ?: return null
            val lastEnd = valid.maxByOrNull { it.endTime }?.endTime?.let(::parseTime) ?: return null
            val zone = ZoneId.systemDefault()
            val start = LocalDateTime.of(date, firstStart)
                .minusMinutes(leadMinutes.toLong())
                .atZone(zone).toInstant().toEpochMilli()
            val end = LocalDateTime.of(date, lastEnd)
                .atZone(zone).toInstant().toEpochMilli()
            return DynamicIslandWindow(date, start, end)
        }

        fun parseTime(value: String): LocalTime? {
            return runCatching { LocalTime.parse(value) }.getOrNull()
                ?: runCatching {
                    val parts = value.split(":")
                    LocalTime.of(parts[0].toInt(), parts[1].toInt())
                }.getOrNull()
        }
    }

    // ---------------------------------------------------------------------
    // 闹钟调度
    // ---------------------------------------------------------------------

    private fun scheduleWindow(window: DynamicIslandWindow) {
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        // vivo/OPPO 等厂商默认关闭「精确闹钟」权限；无权限时降级为非精确闹钟
        // （窗口开始/结束可能延迟几分钟，但提前量足够缓冲，功能始终可用）
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        // 注意：Intent 必须保持「常量」（只有 action，不携带 data Uri）。
        // PendingIntent 按 filterEquals（含 action+data）匹配：若把窗口毫秒值编码进 data，
        // 窗口变化时会生成新 PI 而旧闹钟仍存活（新旧并存），且 cancelAlarms 永远匹配不上。
        // Receiver 并不需要窗口数据：START 走 sync()、STOP 走 scheduleNextWindow()，均重新查库计算。
        val startPi = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_START,
            Intent(appContext, DynamicIslandAlarmReceiver::class.java).apply {
                action = ACTION_DYNAMIC_ISLAND_START
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_STOP,
            Intent(appContext, DynamicIslandAlarmReceiver::class.java).apply {
                action = ACTION_DYNAMIC_ISLAND_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (exactAllowed) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, window.startMillis, startPi)
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, window.endMillis, stopPi)
            } catch (e: SecurityException) {
                Log.w(TAG, "精确闹钟被拒，降级为非精确调度", e)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, window.startMillis, startPi)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, window.endMillis, stopPi)
            }
        } else {
            Log.w(TAG, "无精确闹钟权限，使用非精确闹钟调度灵动岛窗口（可能有少量延迟）")
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, window.startMillis, startPi)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, window.endMillis, stopPi)
        }
    }

    private fun cancelAlarms() {
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        listOf(
            REQUEST_CODE_START to ACTION_DYNAMIC_ISLAND_START,
            REQUEST_CODE_STOP to ACTION_DYNAMIC_ISLAND_STOP
        ).forEach { (code, action) ->
            // Intent 必须与 scheduleWindow 注册时 filterEquals 匹配（同组件+同 action），
            // 否则 FLAG_NO_CREATE 拿不到已存在的 PendingIntent，取消会变成空操作
            val pi = PendingIntent.getBroadcast(
                appContext,
                code,
                Intent(appContext, DynamicIslandAlarmReceiver::class.java).apply {
                    this.action = action
                },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let {
                am.cancel(it)
                it.cancel()
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }
}
