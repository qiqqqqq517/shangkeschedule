package com.shangkeschedule.ui.settings.notification

import com.shangkeschedule.notification.live.LiveUpdateSupport
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.settings.SettingValueTrailing

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import kotlinx.datetime.isoDayNumber
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.week_days_full_names
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.desc_auto_mode
import shangkeschedule.shared.generated.resources.desc_compat_wearable_sync
import shangkeschedule.shared.generated.resources.desc_course_reminder
import shangkeschedule.shared.generated.resources.desc_dynamic_island
import shangkeschedule.shared.generated.resources.desc_exam_countdown_reminder
import shangkeschedule.shared.generated.resources.desc_live_update_capability_supported
import shangkeschedule.shared.generated.resources.desc_live_update_capability_unsupported
import shangkeschedule.shared.generated.resources.desc_live_update_capability_vendor
import shangkeschedule.shared.generated.resources.desc_next_class_notification
import shangkeschedule.shared.generated.resources.desc_morning_alarm
import shangkeschedule.shared.generated.resources.desc_morning_alarm_managed_in_clock
import shangkeschedule.shared.generated.resources.item_auto_mode
import shangkeschedule.shared.generated.resources.item_background_and_autostart
import shangkeschedule.shared.generated.resources.item_compat_wearable_sync
import shangkeschedule.shared.generated.resources.item_course_reminder
import shangkeschedule.shared.generated.resources.item_dnd_permission
import shangkeschedule.shared.generated.resources.item_dynamic_island
import shangkeschedule.shared.generated.resources.item_exam_countdown_reminder
import shangkeschedule.shared.generated.resources.item_live_update_capability
import shangkeschedule.shared.generated.resources.item_next_class_notification
import shangkeschedule.shared.generated.resources.item_exact_alarm_permission
import shangkeschedule.shared.generated.resources.item_ignore_battery_optimization
import shangkeschedule.shared.generated.resources.item_morning_alarm
import shangkeschedule.shared.generated.resources.item_morning_alarm_lead
import shangkeschedule.shared.generated.resources.item_remind_time_before
import shangkeschedule.shared.generated.resources.morning_alarm_preview_format
import shangkeschedule.shared.generated.resources.morning_alarm_preview_none
import shangkeschedule.shared.generated.resources.morning_alarm_preview_title
import shangkeschedule.shared.generated.resources.remind_time_minutes_format
import shangkeschedule.shared.generated.resources.section_title_morning_alarm
import shangkeschedule.shared.generated.resources.section_title_notification_display
import shangkeschedule.shared.generated.resources.section_title_permission_background
import shangkeschedule.shared.generated.resources.section_title_reminder
import shangkeschedule.shared.generated.resources.status_authorized
import shangkeschedule.shared.generated.resources.status_unauthorized
import shangkeschedule.shared.generated.resources.text_auto_mode_dependency
import shangkeschedule.shared.generated.resources.text_permission_importance_detail
import shangkeschedule.shared.generated.resources.text_permission_importance_title

/**
 * 常规设置卡片 UI 组件 (Android 专属)。
 *
 * 排版（v3.72.4 重排）：原先 13 个设置项挤在一张「常规」大卡里，
 * 通知展示、课程提醒、早八闹钟、系统权限四类混在一起，用户得逐行读完才知道哪项管什么。
 * 现按**作用**拆成四组，每组一个 [AppSectionHeader] + 一张 [SectionCard]：
 *
 * | 分组 | 回答的问题 | 成员 |
 * |------|-----------|------|
 * | 课程提醒 | 提醒本身怎么发 | 课程提醒、课前提醒时间、上课自动模式 |
 * | 通知显示 | 提醒长什么样 | 状态栏「灵动岛」、兼容穿戴设备同步通知 |
 * | 早八闹钟 | 怎么把我叫醒 | 早八闹钟、闹钟提前量、下一个闹钟 |
 * | 权限与后台 | 为什么没准时到 | 精确闹钟权限、勿扰模式权限、后台运行和自启、忽略电池优化 |
 *
 * 顺带修掉的排版缺陷：
 * 1. 精确闹钟权限那一行前面曾连着两条分割线（`SDK >= S` 分支内外各一条）；
 * 2. 「提前提醒时间」与「闹钟提前量」文案完全相同，无法区分是哪个提前量；
 * 3. 带当前值的行直接覆盖 `trailingContent` 成裸 `Text`，连带丢掉了可点暗示
 *    （见 [SettingValueTrailing] 的设计走查 E1），现统一走 `SettingValueTrailing`；
 * 4. 权限状态原先只写「已开启 / 未授权」且同为灰色，未授权时不显眼，现授权/未授权分色；
 * 5. 「下一个闹钟」预览行原本在行尾挂「打开系统闹钟」文字，把预览挤成两行并拆断课名（「实/验」），现只留 chevron。
 */
@Composable
fun GeneralSettingsCard(
    uiState: NotificationSettingsUiState,
    currentModeText: String?,
    onReminderToggle: (Boolean) -> Unit,
    onDynamicIslandToggle: (Boolean) -> Unit,
    onNextClassNotificationToggle: (Boolean) -> Unit,
    onExamCountdownReminderToggle: (Boolean) -> Unit,
    onCompatWearableToggle: (Boolean) -> Unit,
    onAutoModeClick: () -> Unit,
    onRemindTimeClick: () -> Unit,
    onMorningAlarmToggle: (Boolean) -> Unit,
    onMorningAlarmLeadClick: () -> Unit,
    onOpenSystemAlarm: () -> Unit,
    onAppSettingsClick: () -> Unit,
    onBatteryOptimizationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 仅在开启提醒开关但无权限时弹窗引导
    var showExactAlarmDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // ── 一、课程提醒：提醒本身，以及与提醒联动的自动模式 ──────────────────
        AppSectionHeader(stringResource(Res.string.section_title_reminder))
        SectionCard {
            SettingItem(
                title = stringResource(Res.string.item_course_reminder),
                subtitle = stringResource(Res.string.desc_course_reminder),
                trailingContent = {
                    AppSwitch(
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
            // 课前提醒时间（课程提醒的提前量）
            SettingItem(
                title = stringResource(Res.string.item_remind_time_before),
                onClick = onRemindTimeClick,
                trailingContent = {
                    SettingValueTrailing(
                        stringResource(
                            Res.string.remind_time_minutes_format,
                            uiState.remindBeforeMinutes
                        )
                    )
                }
            )
            SectionDivider()
            // 上课自动模式（依赖课程提醒开关，关闭时在本组卡片下方给出原因）
            SettingItem(
                title = stringResource(Res.string.item_auto_mode),
                subtitle = stringResource(Res.string.desc_auto_mode),
                onClick = onAutoModeClick,
                trailingContent = {
                    currentModeText?.let { SettingValueTrailing(it) }
                }
            )
        }
        if (!uiState.reminderEnabled) {
            CardNote(
                text = stringResource(Res.string.text_auto_mode_dependency),
                color = appColors().warning
            )
        }

        Spacer(modifier = Modifier.height(appSpacing().sectionGap))

        // ── 二、通知显示：提醒以什么形态出现在系统里 ──────────────────────
        AppSectionHeader(stringResource(Res.string.section_title_notification_display))
        SectionCard {
            SettingItem(
                title = stringResource(Res.string.item_dynamic_island),
                subtitle = stringResource(Res.string.desc_dynamic_island),
                trailingContent = {
                    AppSwitch(
                        checked = uiState.dynamicIslandEnabled,
                        onCheckedChange = onDynamicIslandToggle
                    )
                }
            )
            SectionDivider()
            // 「下一节课」常驻通知：与灵动岛同属「提醒以什么形态出现」，
            // 但它是独立开关——灵动岛只在课中/课前亮，常驻通知是一整天都看得见。
            SettingItem(
                title = stringResource(Res.string.item_next_class_notification),
                subtitle = stringResource(Res.string.desc_next_class_notification),
                trailingContent = {
                    AppSwitch(
                        checked = uiState.nextClassNotificationEnabled,
                        onCheckedChange = onNextClassNotificationToggle
                    )
                }
            )
            SectionDivider()
            // 考试倒计时提醒：数据来自「日程」页的考试条目，不依赖课程表
            SettingItem(
                title = stringResource(Res.string.item_exam_countdown_reminder),
                subtitle = stringResource(Res.string.desc_exam_countdown_reminder),
                trailingContent = {
                    AppSwitch(
                        checked = uiState.examCountdownReminderEnabled,
                        onCheckedChange = onExamCountdownReminderToggle
                    )
                }
            )
            SectionDivider()
            SettingItem(
                title = stringResource(Res.string.item_compat_wearable_sync),
                subtitle = stringResource(Res.string.desc_compat_wearable_sync),
                trailingContent = {
                    AppSwitch(
                        checked = uiState.compatWearableSync,
                        onCheckedChange = onCompatWearableToggle
                    )
                }
            )
            SectionDivider()
            // §G2：实况通知胶囊能力探测。这里只回答「本机现在能不能把常驻通知显示成胶囊」，
            // 探测结果不参与投递决策（通知一律先按公版能力投递，不支持时系统自然降级）。
            // 厂商实况区（小米「超级岛」）没有公开接入协议，故只做一句如实说明，
            // **不**伪造厂商 extras —— 它由系统按通知自行呈现（见 [LiveUpdateSupport]）。
            val liveUpdateSupported = LiveUpdateSupport.supportsLiveUpdate(context)
            val capabilityText = stringResource(
                if (liveUpdateSupported) {
                    Res.string.desc_live_update_capability_supported
                } else {
                    Res.string.desc_live_update_capability_unsupported
                }
            )
            val vendorText = if (LiveUpdateSupport.isXiaomiDevice()) {
                stringResource(Res.string.desc_live_update_capability_vendor)
            } else {
                null
            }
            SettingItem(
                title = stringResource(Res.string.item_live_update_capability),
                subtitle = listOfNotNull(capabilityText, vendorText).joinToString(" "),
                trailingContent = {}
            )
        }

        Spacer(modifier = Modifier.height(appSpacing().sectionGap))

        // ── 三、早八闹钟：独立能力，只写最近一次未过期的一条 ────────────────
        AppSectionHeader(stringResource(Res.string.section_title_morning_alarm))
        SectionCard {
            SettingItem(
                title = stringResource(Res.string.item_morning_alarm),
                subtitle = stringResource(Res.string.desc_morning_alarm),
                trailingContent = {
                    AppSwitch(
                        checked = uiState.morningAlarmEnabled,
                        onCheckedChange = onMorningAlarmToggle
                    )
                }
            )
            if (uiState.morningAlarmEnabled) {
                SectionDivider()
                // 闹钟提前量（独立于课程提醒的课前提醒时间）
                SettingItem(
                    title = stringResource(Res.string.item_morning_alarm_lead),
                    onClick = onMorningAlarmLeadClick,
                    trailingContent = {
                        SettingValueTrailing(
                            stringResource(
                                Res.string.remind_time_minutes_format,
                                uiState.morningAlarmLeadMinutes
                            )
                        )
                    }
                )
                SectionDivider()
                // 下一个闹钟预览（点击直达系统闹钟页管理）
                val preview = uiState.nextMorningAlarm
                // 星期文案取自既有本地化数组（与 AgendaScreen / ScheduleGrid 同一口径），不要手写中文
                val weekDayNames = stringArrayResource(Res.array.week_days_full_names)
                val previewText = if (preview == null) {
                    stringResource(Res.string.morning_alarm_preview_none)
                } else {
                    stringResource(
                        Res.string.morning_alarm_preview_format,
                        weekdayLabel(preview.courseDate.dayOfWeek, weekDayNames),
                        formatHhMm(preview.alarmTime.hour, preview.alarmTime.minute),
                        formatHhMm(preview.courseStart.hour, preview.courseStart.minute),
                        preview.courseName
                    )
                }
                // 预览行只留 chevron：行尾再挂「打开系统闹钟」字样会把预览文字挤到换行
                // （实测「大学物理实验（一）」被拆成「实/验」），而跳转意图由 chevron 与下方注记承载；
                // 另按 [SettingValueTrailing] 的设计走查 E1，文字位只该放「当前值」，本行 value 位放的是动作。
                SettingItem(
                    title = stringResource(Res.string.morning_alarm_preview_title),
                    subtitle = previewText,
                    onClick = onOpenSystemAlarm
                )
            }
        }
        if (uiState.morningAlarmEnabled) {
            // 如实告知：闹钟本体在系统时钟里，本应用写入后不再回改
            // （系统时钟普遍不支持按标签删除，避免重写造成重复堆积）
            CardNote(stringResource(Res.string.desc_morning_alarm_managed_in_clock))
        }

        Spacer(modifier = Modifier.height(appSpacing().sectionGap))

        // ── 四、权限与后台：为什么提醒可能没准时到 ────────────────────────
        AppSectionHeader(stringResource(Res.string.section_title_permission_background))
        Text(
            text = stringResource(Res.string.text_permission_importance_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.text_permission_importance_detail),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SectionCard {
            // 精确闹钟权限 (Android 12+)：用于「时间窗口启停」调度
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingItem(
                    title = stringResource(Res.string.item_exact_alarm_permission),
                    onClick = { openExactAlarmSettings(context) },
                    trailingContent = {
                        PermissionStatusTrailing(granted = uiState.exactAlarmStatus)
                    }
                )
                SectionDivider()
            }
            // 勿扰模式权限：用于自动模式切换
            SettingItem(
                title = stringResource(Res.string.item_dnd_permission),
                onClick = { openDndSettings(context) },
                trailingContent = {
                    PermissionStatusTrailing(granted = uiState.dndPermissionStatus)
                }
            )
            SectionDivider()
            // 后台与自启动
            SettingItem(
                title = stringResource(Res.string.item_background_and_autostart),
                onClick = onAppSettingsClick
            )
            SectionDivider()
            // 忽略电池优化
            SettingItem(
                title = stringResource(Res.string.item_ignore_battery_optimization),
                onClick = onBatteryOptimizationClick
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

/**
 * 权限状态尾参：已授权取语义绿、未授权取警示黄，两者都带 chevron 提示「可点跳转」。
 *
 * 不用 [SettingValueTrailing] 是因为它把值统一压成 `textSecondary`——
 * 权限未开是**待办**而非普通当前值，需要颜色提示，否则整列灰色扫过去看不出缺哪项。
 */
@Composable
private fun PermissionStatusTrailing(granted: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(
                if (granted) Res.string.status_authorized else Res.string.status_unauthorized
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted) appColors().success else appColors().warning
        )
        Icon(
            vectorResource(Res.drawable.chevron_right_24px),
            contentDescription = null,
            tint = appColors().textSecondary
        )
    }
}

/**
 * 卡片下方的说明注记：与卡片内文左对齐
 * （[SectionCard] 自身带 `pageHorizontal` 内边距，注记在卡外，需补同样一份量）；
 * 上下留白比卡内行距略紧，视觉上归属上方那张卡而不是自成一段。
 */
@Composable
private fun CardNote(
    text: String,
    color: androidx.compose.ui.graphics.Color = appColors().textSecondary
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        color = color,
        modifier = Modifier.padding(start = appSpacing().pageHorizontal, top = 6.dp)
    )
}

/**
 * 周几标签（闹钟预览文案用）。
 *
 * v4.66.0 修复：此前这里手写死中文「周一…周日」，英文 / 繁中界面会显示简体中文；
 * 现改为读既有本地化数组 [Res.array.week_days_full_names]。
 */
private fun weekdayLabel(
    dayOfWeek: kotlinx.datetime.DayOfWeek,
    weekDays: List<String>
): String = weekDays.getOrElse(dayOfWeek.isoDayNumber - 1) { weekDays.firstOrNull().orEmpty() }

/** HH:mm 格式化（避免为预览专门引格式化器）。 */
private fun formatHhMm(hour: Int, minute: Int): String {
    val h = if (hour < 10) "0$hour" else hour.toString()
    val m = if (minute < 10) "0$minute" else minute.toString()
    return "$h:$m"
}
