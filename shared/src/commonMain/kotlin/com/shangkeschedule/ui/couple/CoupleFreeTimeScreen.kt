package com.shangkeschedule.ui.couple

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.data.logic.CoupleFreeTimeCalculator
import com.shangkeschedule.tool.copyToClipboard
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppEmptyState
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.action_close
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.content_copy_24px
import shangkeschedule.shared.generated.resources.couple_free_copied
import shangkeschedule.shared.generated.resources.couple_free_copy
import shangkeschedule.shared.generated.resources.couple_free_copy_desc
import shangkeschedule.shared.generated.resources.couple_free_copy_failed
import shangkeschedule.shared.generated.resources.couple_free_copy_title
import shangkeschedule.shared.generated.resources.couple_free_duration_format
import shangkeschedule.shared.generated.resources.couple_free_empty
import shangkeschedule.shared.generated.resources.couple_free_min_duration_hint
import shangkeschedule.shared.generated.resources.couple_free_no_couple
import shangkeschedule.shared.generated.resources.couple_free_no_couple_desc
import shangkeschedule.shared.generated.resources.couple_free_source_format
import shangkeschedule.shared.generated.resources.couple_free_this_week
import shangkeschedule.shared.generated.resources.couple_free_week_item
import shangkeschedule.shared.generated.resources.couple_free_week_picker_title
import shangkeschedule.shared.generated.resources.couple_free_window_format
import shangkeschedule.shared.generated.resources.schedule_24px
import shangkeschedule.shared.generated.resources.title_couple_free_time
import shangkeschedule.shared.generated.resources.title_current_week
import shangkeschedule.shared.generated.resources.week_days_full_names

/**
 * D2「找共同空闲」（v4.66.0）。
 *
 * 复习/约自习场景：双方课表都在本机，选定一个周次后把两张表的占用合并，
 * 列出**双方都没课、且不少于 30 分钟**的时间段，可一键复制发出去。
 *
 * 关于「共同空闲」的语义边界（不夸大）：课表里的课 ≠ 一个人的全部时间安排，
 * 结果只代表「两张课表上都没排课」，界面上只用「都没课」的说法，不写「都有空」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleFreeTimeScreen(
    onBack: () -> Unit,
    viewModel: CoupleFreeTimeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val weekDays = stringArrayResource(Res.array.week_days_full_names)
    var showWeekPicker by remember { mutableStateOf(false) }

    val toastCopied = stringResource(Res.string.couple_free_copied)
    val toastCopyFailed = stringResource(Res.string.couple_free_copy_failed)
    val copyTitle = stringResource(Res.string.couple_free_copy_title, uiState.selectedWeek)

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(stringResource(Res.string.title_couple_free_time)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = appSpacing().pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(appSpacing().listGap)
        ) {
            when {
                !uiState.isReady -> Unit

                !uiState.hasCoupleTable -> {
                    AppSectionHeader(text = stringResource(Res.string.couple_free_no_couple))
                    AppEmptyState(hint = stringResource(Res.string.couple_free_no_couple_desc))
                }

                else -> {
                    // --- 周次 + 数据来源 ---
                    SectionCard {
                        SettingItem(
                            title = stringResource(Res.string.couple_free_week_item),
                            subtitle = weekLabel(uiState),
                            leadingIcon = vectorResource(Res.drawable.schedule_24px),
                            onClick = { showWeekPicker = true }
                        )
                        SectionDivider()
                        SettingItem(
                            title = stringResource(
                                Res.string.couple_free_source_format,
                                uiState.selfTableName,
                                uiState.selfCourseCount,
                                uiState.coupleTableName,
                                uiState.coupleCourseCount
                            ),
                            subtitle = stringResource(
                                Res.string.couple_free_window_format,
                                CoupleFreeTimeCalculator.formatMinutes(uiState.windowStartMinutes),
                                CoupleFreeTimeCalculator.formatMinutes(uiState.windowEndMinutes)
                            ),
                            onClick = null,
                            trailingContent = {}
                        )
                    }

                    // --- 结果 ---
                    if (uiState.days.isEmpty()) {
                        AppEmptyState(hint = stringResource(Res.string.couple_free_empty))
                    } else {
                        uiState.days.forEach { day ->
                            AppSectionHeader(text = weekDays.getOrNull(day.day - 1).orEmpty())
                            SectionCard {
                                day.blocks.forEachIndexed { index, block ->
                                    if (index > 0) SectionDivider()
                                    SettingItem(
                                        title = "${CoupleFreeTimeCalculator.formatMinutes(block.startMinutes)}" +
                                            "–${CoupleFreeTimeCalculator.formatMinutes(block.endMinutes)}",
                                        subtitle = stringResource(
                                            Res.string.couple_free_duration_format,
                                            block.durationMinutes
                                        ),
                                        onClick = null,
                                        trailingContent = {}
                                    )
                                }
                            }
                        }
                    }

                    // --- 复制 ---
                    SectionCard {
                        SettingItem(
                            title = stringResource(Res.string.couple_free_copy),
                            subtitle = stringResource(Res.string.couple_free_copy_desc),
                            leadingIcon = vectorResource(Res.drawable.content_copy_24px),
                            onClick = {
                                val text = buildCopyText(copyTitle, weekDays, uiState.days)
                                ToastManager.show(
                                    if (copyToClipboard(text)) toastCopied else toastCopyFailed
                                )
                            }
                        )
                    }

                    // 阈值说明放页尾：说明「为什么只列这些」，不占结果区注意力
                    Text(
                        text = stringResource(Res.string.couple_free_min_duration_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textSecondary,
                        modifier = Modifier.padding(start = 4.dp, bottom = appSpacing().listGap)
                    )
                }
            }
        }
    }

    if (showWeekPicker) {
        CoupleFreeWeekPickerDialog(
            totalWeeks = uiState.totalWeeks,
            selectedWeek = uiState.selectedWeek,
            currentWeek = uiState.currentWeek,
            onDismiss = { showWeekPicker = false },
            onPick = { week ->
                showWeekPicker = false
                viewModel.selectWeek(week)
            }
        )
    }
}

/** 周次副标题：`第 3 周`，恰好是数据库推算出的当前周时再补一句「本周」。 */
@Composable
private fun weekLabel(uiState: CoupleFreeTimeUiState): String {
    val label = stringResource(Res.string.title_current_week, uiState.selectedWeek.toString())
    return if (uiState.currentWeek == uiState.selectedWeek) {
        "$label · ${stringResource(Res.string.couple_free_this_week)}"
    } else {
        label
    }
}

/** 复制出去的文本：首行标题（含周次），其后每天一行「周一 09:00–11:30、13:00–17:00」。 */
private fun buildCopyText(header: String, weekDays: List<String>, days: List<DayFreeTime>): String {
    val builder = StringBuilder(header)
    days.forEach { day ->
        val ranges = day.blocks.joinToString("、") {
            "${CoupleFreeTimeCalculator.formatMinutes(it.startMinutes)}–" +
                CoupleFreeTimeCalculator.formatMinutes(it.endMinutes)
        }
        builder.append('\n')
            .append(weekDays.getOrNull(day.day - 1).orEmpty())
            .append(' ')
            .append(ranges)
    }
    return builder.toString()
}

/**
 * 周次选择对话框。
 *
 * 周数上限取本人课表的 `semesterTotalWeeks`（两边学期周数不一致时以本人表为准，
 * 因为「共同空闲」是给自己看的）。当前自然周用主色标出，方便一眼回到本周。
 */
@Composable
private fun CoupleFreeWeekPickerDialog(
    totalWeeks: Int,
    selectedWeek: Int,
    currentWeek: Int?,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.couple_free_week_picker_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val weeks = (1..maxOf(totalWeeks, 1)).toList()
                weeks.forEachIndexed { index, week ->
                    if (index > 0) SectionDivider()
                    val suffix = if (week == currentWeek) {
                        " · ${stringResource(Res.string.couple_free_this_week)}"
                    } else {
                        ""
                    }
                    Text(
                        text = stringResource(Res.string.title_current_week, week.toString()) + suffix,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (week == selectedWeek) appColors().primary else appColors().textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(week) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            AppDialogActions(
                confirmText = stringResource(Res.string.action_close),
                onConfirm = onDismiss
            )
        }
    )
}
