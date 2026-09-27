package com.shangkeschedule.service.notification

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * 「本应用当前是否处于前台可见态」判定。
 *
 * ## 为什么必须有这个判断（真机实证）
 *
 * 早八闹钟写入系统时钟依赖 `context.startActivity(AlarmClock.ACTION_SET_ALARM)`，
 * 而 Android 10+ 的后台启动限制（BAL）会拦下后台进程发起的 Activity 启动。
 * 关键在于：**被拦时 `startActivity` 并不抛异常，只是什么也没发生**
 * （本机 Xiaomi 22041216UC / MIUI V816 / Android 14 实测：日志里连
 * `Background activity start ... blocked` 都可能不出现，闹钟也确实没建出来）。
 *
 * 因此「能不能真正写进去」只能靠**应用自身可见性**来判断，绝不能靠捕获异常——
 * 旧实现正是靠 `try/catch` 判断成功，于是把被静默拦下的失败登记成了成功，
 * 登记簿被污染后再也不补写（用户症状：开关开着、系统时钟里一条都没有）。
 *
 * ## 实现选择
 *
 * 用 `ActivityManager.getMyMemoryState` 读**本进程**的 importance：
 *  - 不需要任何权限（对比：遍历 `getRunningAppProcesses` 在 Android 5.1+ 已被限制）；
 *  - 不引入 `lifecycle-process` 依赖（本项目当前没有该依赖，
 *    因此无法用 `ProcessLifecycleOwner`）。
 *
 * ## 阈值为什么是 FOREGROUND(100) 而不是 FOREGROUND_SERVICE(125)
 *
 * `IMPORTANCE_FOREGROUND_SERVICE`(125) **不能**用：后台跑 WorkManager/JobService 时
 * 进程同样会被提到 125，用它当阈值等于把「后台」判成「前台」，
 * 于是又回到「被静默拦下却登记成成功」的老坑。
 * 100 只在「本应用有已恢复的前台界面」时出现，正是 BAL 放行
 * （`BAL_ALLOW_VISIBLE_WINDOW`）的充要时机。
 *
 * 本对象的两个调用点都发生在前台交互时刻：`MainActivity.onResume()`，
 * 以及设置页改开关后由 `SyncManager` 就地内联排程。
 */
internal object ForegroundGate {

    private const val TAG = "ForegroundGate"

    /** @return true 表示应用处于前台可交互态，此时发起 Activity 启动才会被系统放行。 */
    fun isAppVisible(context: Context): Boolean = runCatching {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        val visible = info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        Log.d(TAG, "importance=${info.importance} → 前台可见=$visible")
        visible
    }.getOrDefault(false)
}
