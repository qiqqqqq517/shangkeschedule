package com.shangkeschedule.ui.settings.notification

import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.desc_compat_wearable_sync
import shangkeschedule.shared.generated.resources.item_auto_mode
import shangkeschedule.shared.generated.resources.item_background_and_autostart
import shangkeschedule.shared.generated.resources.item_compat_wearable_sync
import shangkeschedule.shared.generated.resources.item_course_reminder
import shangkeschedule.shared.generated.resources.item_dnd_permission
import shangkeschedule.shared.generated.resources.item_exact_alarm_permission
import shangkeschedule.shared.generated.resources.item_ignore_battery_optimization
import shangkeschedule.shared.generated.resources.item_remind_time_before
import shangkeschedule.shared.generated.resources.remind_time_minutes_format
import shangkeschedule.shared.generated.resources.section_title_general
import shangkeschedule.shared.generated.resources.status_authorized
import shangkeschedule.shared.generated.resources.status_disabled
import shangkeschedule.shared.generated.resources.status_enabled
import shangkeschedule.shared.generated.resources.status_unauthorized
import shangkeschedule.shared.generated.resources.text_auto_mode_dependency
import shangkeschedule.shared.generated.resources.text_permission_importance_detail
import shangkeschedule.shared.generated.resources.text_permission_importance_title

/**
 * 常规设置卡片 UI 组件 (Android 专属)
 */
@Composable
fun GeneralSettingsCard(
    uiState: NotificationSettingsUiState,
    currentModeText: String?,
    onReminderToggle: (Boolean) -> Unit,
    onCompatWearableToggle: (Boolean) -> Unit,
    onAutoModeClick: () -> Unit,
    onRemindTimeClick: () -> Unit,
    onAppSettingsClick: () -> Unit,
    onBatteryOptimizationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 仅在开启提醒开关但无权限时弹窗引导
    var showExactAlarmDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.section_title_general),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.text_permission_importance_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.text_permission_importance_detail),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SectionCard {
            // 1. 上课提醒主开关
            SettingItem(
                title = stringResource(Res.string.item_course_reminder),
                trailingContent = {
                    Switch(
                        checked = uiState.reminderEnabled,
                        onCheckedChange = { targetState ->
                            if (targetState) {
                                if (hasExactAlarmPermission(context)) {
                                    onReminderToggle(true)
                                } else {
                                    showExactAlarmDialog = true
                                }
                            } else {
                                onReminderToggle(false)
                            }
                        }
                    )
                }
            )
            SectionDivider()
            // 2. 兼容穿戴设备同步通知开关
            SettingItem(
                title = stringResource(Res.string.item_compat_wearable_sync),
                subtitle = stringResource(Res.string.desc_compat_wearable_sync),
                trailingContent = {
                    Switch(
                        checked = uiState.compatWearableSync,
                        onCheckedChange = onCompatWearableToggle
                    )
                }
            )
            SectionDivider()
            // 3. 上课自动模式
            SettingItem(
                title = stringResource(Res.string.item_auto_mode),
                onClick = onAutoModeClick,
                trailingContent = {
                    currentModeText?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            )
            SectionDivider()
            // 4. 提前提醒时间
            SettingItem(
                title = stringResource(Res.string.item_remind_time_before),
                onClick = onRemindTimeClick,
                trailingContent = {
                    Text(
                        text = stringResource(Res.string.remind_time_minutes_format, uiState.remindBeforeMinutes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            )
            // 5. 精确闹钟权限 (Android 12+)：直接跳转页面
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val statusText = if (uiState.exactAlarmStatus)
                    stringResource(Res.string.status_enabled)
                else
                    stringResource(Res.string.status_disabled)
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_exact_alarm_permission),
                    onClick = { openExactAlarmSettings(context) },
                    trailingContent = {
                        Text(statusText, style = MaterialTheme.typography.bodyMedium)
                    }
                )
            }
            SectionDivider()
            // 6. 勿扰模式权限：直接跳转页面
            val dndStatusText = if (uiState.dndPermissionStatus)
                stringResource(Res.string.status_authorized)
            else
                stringResource(Res.string.status_unauthorized)
            SettingItem(
                title = stringResource(Res.string.item_dnd_permission),
                onClick = { openDndSettings(context) },
                trailingContent = {
                    Text(dndStatusText, style = MaterialTheme.typography.bodyMedium)
                }
            )
            SectionDivider()
            // 7. 后台与自启动
            SettingItem(
                title = stringResource(Res.string.item_background_and_autostart),
                onClick = onAppSettingsClick
            )
            SectionDivider()
            // 8. 忽略电池优化
            SettingItem(
                title = stringResource(Res.string.item_ignore_battery_optimization),
                onClick = onBatteryOptimizationClick
            )
        }
        if (!uiState.reminderEnabled) {
            Text(
                text = stringResource(Res.string.text_auto_mode_dependency),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }

    // 点击开启上课提醒无权限时显示的弹窗
    if (showExactAlarmDialog) {
        ExactAlarmPermissionGuideDialog(
            onDismiss = { showExactAlarmDialog = false }
        )
    }
}