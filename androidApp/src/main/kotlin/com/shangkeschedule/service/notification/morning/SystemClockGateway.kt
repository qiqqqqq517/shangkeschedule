package com.shangkeschedule.service.notification.morning

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import com.shangkeschedule.notification.plan.MorningAlarmPlan

/**
 * 与**系统时钟应用**交互的唯一出口：探测可用性、写入、打开闹钟列表。
 *
 * ## 从 `MorningAlarmWriter` 抽出的理由
 *
 * 原 `MorningAlarmWriter` 339 行同时承担六件事：登记簿读写、回收窗口、决策分派、
 * 时钟探测、写入、迁移提示。其中只有「发起 `Activity`」这部分真正需要 Android
 * framework，其余全是可测的纯逻辑。分开后：
 *  - 本类只碰 `PackageManager` / `startActivity`，可在真机替换、可被 fake 覆盖；
 *  - `MorningAlarmWriter` 只做「读登记簿 → 算决策 → 调用本类 → 记登记簿」。
 *
 * ## API 约束（已查证本机 SDK `android-36/android/provider/AlarmClock.java`）
 *
 * 计划原稿假定可用 `AlarmClock.ACTION_DELETE_ALARM` + `EXTRA_ALARM_SEARCH_MODE_LABEL`
 * 做「每轮 delete-then-set」以保证不重复——**该常量在公版 SDK 中并不存在**
 * （`api-versions.xml` 亦无记录，`EXTRA_DELETE_AFTER_USE` 同样不存在）。
 * 真实可用的是 API 23 起的：
 *  - `ACTION_DISMISS_ALARM` + `EXTRA_ALARM_SEARCH_MODE=ALARM_SEARCH_MODE_LABEL`
 *  - `ACTION_SET_ALARM` + `EXTRA_SKIP_UI`
 *
 * ## 真机实测（Xiaomi 22041216UC / MIUI V816 / Android 14）——推翻了原设计的两条假设
 *
 * 1. `ACTION_DISMISS_ALARM` 在本机**不能删除闹钟**：`am start` 发出后只是把时钟界面调到
 *    前台（当前焦点变成 `DeskClockTabActivity`），闹钟仍在 `dumpsys alarm` 与列表中。
 *    实测 LABEL 与 TIME 两种搜索模式均如此。DeskClock 的 manifest 把 DISMISS 与 SET
 *    一起挂在 `HandleSetAlarmActivity` 上，并无「搜索并删除」实现。
 * 2. `ACTION_SET_ALARM` **非幂等**：同一时刻 + 同一标签连发 2 次，列表出现 **2 条**重复闹钟。
 *    （曾误判为幂等——起因是首次查看时列表滚动位置不同，后续滚动核对才发现是 2 条。）
 *
 * 因此**不提供删除能力**（[supportsLabelDismiss] 恒为 false），也不做删除动作：
 * 写入路径可靠，但已登记过的日期一律不重写，由设置页提示用户到系统闹钟页手动管理。
 *
 * ## 后台启动限制（BAL）
 *
 * 写入靠 `startActivity(ACTION_SET_ALARM)`，受 Android 10+ 后台启动限制约束：后台
 * （WorkManager）发起时系统**静默拦下**——`startActivity` 不抛异常、闹钟也没建出来。
 * 故**是否可写**由调用方用 `ForegroundGate` 前置判断，本类不做该判断（它只负责动作本身）。
 */
internal class SystemClockGateway(private val context: Context) {

    /**
     * 是否具备「按标签删除」能力。
     *
     * 公版 `AlarmClock.ACTION_DISMISS_ALARM` 的 LABEL 搜索语义**只是可选项**，
     * 且本机 MIUI 实测**不删除**（只把时钟调到前台）。公版 API 又**没有**
     * `ACTION_DELETE_ALARM`（经本机 SDK 与 `api-versions.xml` 核实：该常量不存在）。
     *
     * 因此这里**恒返回 false**：宁可不删，也不能重复写入造成堆积。
     */
    fun supportsLabelDismiss(): Boolean = false

    /** 系统是否有能处理 `ACTION_SET_ALARM` 的时钟应用。 */
    fun hasClockApp(): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
        return runCatching {
            context.packageManager.resolveActivity(intent, 0) != null
        }.getOrDefault(false)
    }

    /** 写入一条系统闹钟（`EXTRA_SKIP_UI` = 不弹确认页）。 */
    fun writeAlarm(alarm: MorningAlarmPlan.MorningAlarm, label: String): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, alarm.alarmTime.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarm.alarmTime.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            // SKIP_UI：不弹系统确认页直接写入（各 OEM 时钟应用支持度不一，失败会走 catch 降级）
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startQuietly(intent, "写入早八闹钟")
    }

    /**
     * 按标签删除一条此前写入的闹钟。
     *
     * 用登记簿里的**完整精确标签**做搜索（不是前缀）：
     * 系统对 LABEL 的语义是「包含该词/短语」，标签越完整越不会命中用户自己的闹钟。
     *
     * 真机实测本机不生效，故当前无调用点；保留实现是为了在确实支持搜索删除的设备上
     * 开启差分删除（见 [MorningAlarmDiff] 的 `allowDismiss`）。
     */
    fun dismissByLabel(label: String) {
        val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
            putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startQuietly(intent, "按标签删除闹钟")
    }

    /** 打开系统闹钟列表页，方便用户直接管理。 */
    fun openSystemAlarmList(): Boolean {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startQuietly(intent, "打开系统闹钟页")
    }

    /**
     * 静默发起系统闹钟 Activity。
     *
     * 该路径受 Android 10+ 后台启动限制（BAL）约束：后台可能被系统拒绝
     * （此时会抛异常或静默失败）。故所有调用点都在**前台时机**
     * （设置页操作、MainActivity onResume），后台仅做标记，下次前台补写。
     */
    private fun startQuietly(intent: Intent, what: String): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "$what 失败：系统无对应 Activity", e)
        false
    } catch (e: Exception) {
        Log.w(TAG, "$what 失败（可能受后台启动限制）", e)
        false
    }

    private companion object {
        const val TAG = "SystemClockGateway"
    }
}
