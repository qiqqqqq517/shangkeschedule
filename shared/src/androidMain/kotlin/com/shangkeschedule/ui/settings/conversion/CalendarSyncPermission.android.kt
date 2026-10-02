package com.shangkeschedule.ui.settings.conversion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.shangkeschedule.ui.components.ToastManager
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.toast_calendar_permission_denied

/**
 * 检查是否**同时**拥有系统日历读与写权限。
 *
 * 读与写是两条独立的权限，本功能的实际访问面两边都要：
 * - 写：`CalendarAccountManager.android.kt` 的 `getOrCreateCalendarId` / `applyBatch` / `delete` 需要 `WRITE_CALENDAR`；
 * - 读：同一文件的 4 处 `ContentResolver.query`（`Calendars` 查日历 ID、`Events` 读现状差分、
 *   `Reminders` 读提醒分钟数、写后回读计数）需要 `READ_CALENDAR`。
 *
 * 只检查 `WRITE_CALENDAR` 不能保证此后 query 的前置条件成立：缺少 READ 时 query 会抛
 * `SecurityException`，被 `syncCurrentTableToSystemCalendar` 的兜底 catch 吞掉，对外只表现为
 * 「同步失败」且没有可定位的提示。跨平台 KDoc（`CalendarSyncPermission.kt`）与 expect 契约
 * 一贯写的就是「日历**读写**权限」，这里按字面口径校验。
 */
private fun hasCalendarPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

/** 需要一并申请的日历权限：读（query）与写（applyBatch）。 */
private val CALENDAR_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
)

/**
 * Android 端真实实现：在同步前检查/申请 `READ_CALENDAR` + `WRITE_CALENDAR`。
 * 已授权 → 直接同步；未授权 → 弹出系统权限请求，授权后同步，拒绝时 Toast 提示。
 */
@Composable
actual fun rememberCalendarSyncGate(onSync: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val deniedMessage = stringResource(Res.string.toast_calendar_permission_denied)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // 不只看回调 map：同组权限在部分机型上只回授其中一项，一律复查最终授权状态。
        val fromResult = grants[Manifest.permission.READ_CALENDAR] == true &&
            grants[Manifest.permission.WRITE_CALENDAR] == true
        if (fromResult || hasCalendarPermission(context)) onSync() else ToastManager.show(deniedMessage)
    }
    return remember {
        {
            if (hasCalendarPermission(context)) onSync() else launcher.launch(CALENDAR_PERMISSIONS)
        }
    }
}