package com.shangkeschedule.ui.settings.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri

// --- 权限检查逻辑 ---

/**
 * 检查是否拥有精确闹钟调度权限
 */
fun hasExactAlarmPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService<AlarmManager>()
        alarmManager?.canScheduleExactAlarms() ?: false
    } else {
        true
    }
}

/**
 * 检查是否拥有发送通知权限 (Android 13+)
 */
fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

/**
 * 检查是否拥有勿扰模式 (DND) 控制权限
 */
fun hasDndPermission(context: Context): Boolean {
    val notificationManager = context.getSystemService<NotificationManager>()
    return notificationManager?.isNotificationPolicyAccessGranted ?: false
}

// --- 系统设置页面跳转 ---

/**
 * 打开精确闹钟权限设置页
 */
fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        safelyStartActivity(context, intent) { openAppSettings(context) }
    } else {
        openAppSettings(context)
    }
}

/**
 * 打开勿扰权限设置页
 */
fun openDndSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    safelyStartActivity(context, intent) { openAppSettings(context) }
}

/**
 * 打开当前应用的系统设置详情页
 */
fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${context.packageName}".toUri()
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    safelyStartActivity(context, intent)
}

/**
 * 打开忽略电池优化设置页
 *
 * 走 [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS]（直接弹白名单申请对话框），
 * 依赖 `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`（见 AndroidManifest）——
 * 缺该权限时系统会拒绝此 Intent。
 *
 * 部分 ROM（以及白名单已在册时）无此 Activity，此时回退到「电池优化策略」列表页，
 * 再不行才回应用详情页。
 */
fun openIgnoreBatteryOptimizationSettings(context: Context) {
    val requestIntent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        "package:${context.packageName}".toUri()
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    safelyStartActivity(context, requestIntent) {
        // 回退：只打开「优化策略」列表页（不需要该权限），用户仍可自行找到本应用。
        val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        safelyStartActivity(context, listIntent) { openAppSettings(context) }
    }
}

/**
 * 打开系统闹钟列表页（早八闹钟的「管理入口」）。
 *
 * 早八闹钟写入的是**系统时钟应用**的闹钟，用户在那里修改/停用/删除都生效，
 * 本应用不会在下一轮同步时把它改回来（见 `MorningAlarmDiff`：标签未变则不重写）。
 * 部分精简 ROM 无该 Activity，此时回退到应用详情页。
 */
fun openSystemAlarmList(context: Context) {
    val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    safelyStartActivity(context, intent) { openAppSettings(context) }
}

/**
 * 安全启动 Activity，防止由于系统裁剪导致 ActivityNotFoundException
 */
private inline fun safelyStartActivity(
    context: Context,
    intent: Intent,
    onFallback: () -> Unit = {}
) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        onFallback()
    }
}