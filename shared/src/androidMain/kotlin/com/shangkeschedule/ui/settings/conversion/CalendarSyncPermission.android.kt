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

/** 检查是否拥有系统日历读写权限。 */
private fun hasCalendarPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Android 端真实实现：在同步前检查/申请 WRITE_CALENDAR 权限。
 * 已授权 → 直接同步；未授权 → 弹出系统权限请求，授权后同步，拒绝时 Toast 提示。
 */
@Composable
actual fun rememberCalendarSyncGate(onSync: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val deniedMessage = stringResource(Res.string.toast_calendar_permission_denied)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onSync() else ToastManager.show(deniedMessage)
    }
    return remember {
        {
            if (hasCalendarPermission(context)) onSync() else launcher.launch(Manifest.permission.WRITE_CALENDAR)
        }
    }
}