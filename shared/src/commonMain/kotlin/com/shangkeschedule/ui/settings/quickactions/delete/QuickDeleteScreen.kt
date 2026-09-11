package com.shangkeschedule.ui.settings.quickactions.delete

import com.shangkeschedule.ui.components.AppTopAppBar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.ui.components.AppCard
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.action_deselect_all
import shangkeschedule.shared.generated.resources.action_select_all
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.calendar_today_24px
import shangkeschedule.shared.generated.resources.close_24px
import shangkeschedule.shared.generated.resources.confirm_delete
import shangkeschedule.shared.generated.resources.course_time_day_section_details_tweak
import shangkeschedule.shared.generated.resources.course_time_day_time_details_tweak
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.dialog_delete_confirm_msg
import shangkeschedule.shared.generated.resources.filter_list_24px
import shangkeschedule.shared.generated.resources.hint_affected_count
import shangkeschedule.shared.generated.resources.hint_no_selection
import shangkeschedule.shared.generated.resources.item_quick_delete
import shangkeschedule.shared.generated.resources.label_day_of_week
import shangkeschedule.shared.generated.resources.label_dimension_dates
import shangkeschedule.shared.generated.resources.label_dimension_weeks_days
import shangkeschedule.shared.generated.resources.label_none
import shangkeschedule.shared.generated.resources.label_weeks_format
import shangkeschedule.shared.generated.resources.quick_delete_dialog_select_date_title
import shangkeschedule.shared.generated.resources.quick_delete_filter_date_range_hint
import shangkeschedule.shared.generated.resources.quick_delete_filter_weeks_days_hint
import shangkeschedule.shared.generated.resources.quick_delete_label_days_prefix
import shangkeschedule.shared.generated.resources.title_current_week
import shangkeschedule.shared.generated.resources.title_select_weeks
import shangkeschedule.shared.generated.resources.week_days_full_names
import kotlin.time.Instant

/**
 * 将 UiTextRes 转化为 Composable 的字符串
 */
@Composable
fun UiTextRes.asString(): String {
    return stringResource(resource, *args.toTypedArray())
}

/**
 * 快速删除界面：支持按“周次+星期”或“日期范围”筛选并批量清理课程。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDeleteScreen(
    onBack: () -> Unit,
    viewModel: QuickDeleteViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val weekDays = stringArrayResource(Res.array.week_days_full_names)
    val snackbarHostState = remember { SnackbarHostState() }

    // 控制侧边栏和日期选择器的显示状态
    val sheetState = rememberModalBottomSheetState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    // 控制二次确认弹窗的显示
    var showConfirmDialog by remember { mutableStateOf(false) }

    // 悬浮面板玻璃：主内容 hazeSource，筛选面板背板模糊
    val hazeState = rememberHazeState()

    Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {

    // 监听 ViewModel 发送的成功或错误消息，并使用 Snackbar 展示
    val successText = uiState.successMessage?.asString()
    val errorText = uiState.errorMessage?.asString()

    LaunchedEffect(successText, errorText) {
        successText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.resetMessages()
        }
        errorText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.resetMessages()
        }
    }

    Scaffold(
        snackbarHost = { com.shangkeschedule.ui.components.AppSnackbarHost(snackbarHostState) },
        topBar = {
            AppTopAppBar(
                title = { Text(stringResource(Res.string.item_quick_delete)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), stringResource(Res.string.a11y_back))
                    }
                }
            )
        },
        bottomBar = {
            // 仅当有选中的课程受到影响时显示删除按钮（危险操作 = 危险色胶囊）
            if (uiState.affectedCourses.isNotEmpty()) {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                    Button(
                        onClick = { showConfirmDialog = true }, // 点击后弹出确认弹窗
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors().danger,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(vectorResource(Res.drawable.delete_24px), null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(Res.string.confirm_delete))
                    }
                }
            }
        }
    ) { padding ->
        // v3.43.0（《交互动效审查》P2）：列表增删补位需要读全局动效；LazyColumn 的
        // content 是 LazyListScope（非 composable 作用域），所以必须在这里读。
        val deleteMotion = LocalAppMotion.current
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = appSpacing().pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap)
        ) {
            // 维度一：周次和星期筛选卡片
            item {
                Spacer(Modifier.height(8.dp))
                AppSectionHeader(stringResource(Res.string.label_dimension_weeks_days))
                AppCard(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(vectorResource(Res.drawable.filter_list_24px), null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (uiState.selectedWeeks.isEmpty() || uiState.selectedDays.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.quick_delete_filter_weeks_days_hint),
                                    color = appColors().textSecondary
                                )
                            } else {
                                val weeksContent = uiState.selectedWeeks.sorted().joinToString(", ")
                                Text(
                                    text = stringResource(Res.string.label_weeks_format, weeksContent),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                val daysContent = uiState.selectedDays.sorted().joinToString("、") { weekDays[it - 1] }
                                Text(
                                    text = stringResource(Res.string.quick_delete_label_days_prefix, daysContent),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textSecondary
                                )
                            }
                        }
                        // 如果有选择内容，显示清除图标
                        if (uiState.selectedWeeks.isNotEmpty() || uiState.selectedDays.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearWeeksAndDays() }) {
                                Icon(vectorResource(Res.drawable.close_24px), null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // 维度二：具体日期范围筛选卡片
            item {
                AppSectionHeader(stringResource(Res.string.label_dimension_dates))
                AppCard(
                    onClick = { showDateRangePicker = true },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(vectorResource(Res.drawable.calendar_today_24px), null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        val dateText = if (uiState.startDate != null && uiState.endDate != null) {
                            "${uiState.startDate} ~ ${uiState.endDate}"
                        } else {
                            stringResource(Res.string.quick_delete_filter_date_range_hint)
                        }
                        Text(
                            text = dateText,
                            modifier = Modifier.weight(1f),
                            color = if (uiState.startDate != null) appColors().textPrimary
                            else appColors().textSecondary
                        )
                        if (uiState.startDate != null) {
                            IconButton(onClick = { viewModel.clearDateRange() }) {
                                Icon(vectorResource(Res.drawable.close_24px), null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // 预览状态提示：显示受影响的记录条数
            item {
                val count = uiState.affectedCourses.size
                Text(
                    text = if (count > 0) stringResource(Res.string.hint_affected_count, count)
                    else stringResource(Res.string.hint_no_selection),
                    color = if (count > 0) appColors().danger else appColors().textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // 平铺显示所有待删除的课程实例预览
            // v3.43.0（《交互动效审查》P2）：列表增删补位——取消勾选后条目不再"瞬间消失"，
            // 而是淡出并把下方条目平滑顶上来；「减弱动态效果」下退化为瞬切。
            items(
                items = uiState.affectedCourses,
                key = { it.courseWithWeeks.course.id + "#" + it.targetWeek }
            ) { previewItem ->
                Box(modifier = if (deleteMotion.reduceMotion) Modifier else Modifier.animateItem()) {
                    DeletePreviewCard(previewItem.courseWithWeeks, previewItem.targetWeek)
                }
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }

    // 二次确认对话框（危险操作统一 AppDangerDialog）
    if (showConfirmDialog) {
        AppDangerDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = stringResource(Res.string.confirm_delete),
            text = stringResource(Res.string.dialog_delete_confirm_msg),
            confirmText = stringResource(Res.string.action_confirm),
            onConfirm = {
                showConfirmDialog = false
                viewModel.executeDelete() // 真正执行删除
            },
            dismissText = stringResource(Res.string.action_cancel),
            onDismiss = { showConfirmDialog = false }
        )
    }

    // 底部筛选面板：选择周次（1-20）和星期（1-7）
    if (showFilterSheet) {
        FilterBottomSheet(
            hazeState = hazeState,
            sheetState = sheetState,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showFilterSheet = false }
        )
    }

    // 原生风格的日期范围选择对话框
    if (showDateRangePicker) {
        DateRangePickerModal(
            onDismiss = { showDateRangePicker = false },
            onConfirm = { start, end ->
                viewModel.setDateRange(start, end)
                showDateRangePicker = false
            }
        )
    }
    }
}

/**
 * 底部筛选抽屉，包含周次多选和星期多选。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    sheetState: SheetState,
    uiState: QuickDeleteUiState,
    viewModel: QuickDeleteViewModel,
    onDismiss: () -> Unit
) {
    val weekDays = stringArrayResource(Res.array.week_days_full_names)

    AppGlassBottomSheet(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(Res.string.title_select_weeks), style = MaterialTheme.typography.titleMedium)

            // 周次流式布局列表
            FlowRow(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..20).forEach { week ->
                    FilterChip(
                        selected = uiState.selectedWeeks.contains(week),
                        onClick = { viewModel.toggleWeek(week) },
                        label = {
                            Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                                Text(week.toString())
                            }
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

            // 星期选择栏，带全选/取消全选
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(Res.string.label_day_of_week), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    if (uiState.selectedDays.size == 7) viewModel.clearAllDays() else viewModel.selectAllDays()
                }) {
                    Text(if (uiState.selectedDays.size == 7) stringResource(Res.string.action_deselect_all) else stringResource(Res.string.action_select_all))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..7).forEach { day ->
                    FilterChip(
                        selected = uiState.selectedDays.contains(day),
                        onClick = { viewModel.toggleDay(day) },
                        label = {
                            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                                Text(weekDays[day - 1])
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            // 主色胶囊确认钮（与其他 sheet / 弹窗操作区同语言）
            AppDialogActions(
                confirmText = stringResource(Res.string.action_confirm),
                onConfirm = onDismiss
            )
        }
    }
}

/**
 * Material 3 风格的日期范围选择器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerModal(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val state = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis?.toLocalDate()
                    val end = state.selectedEndDateMillis?.toLocalDate()
                    if (start != null && end != null) {
                        onConfirm(start, end)
                    }
                },
                enabled = state.selectedEndDateMillis != null
            ) {
                Text(
                    stringResource(Res.string.action_confirm),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel), color = appColors().textSecondary)
            }
        }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(Res.string.quick_delete_dialog_select_date_title)
                )
            }
        )
    }
}

/**
 * 待删除课程的预览卡片，显示课程名称、具体周次和节次/时间信息。
 */
@Composable
fun DeletePreviewCard(
    courseWithWeeks: CourseWithWeeks,
    targetWeek: Int
) {
    val weekDays = stringArrayResource(Res.array.week_days_full_names)
    val course = courseWithWeeks.course

    Card(
        colors = CardDefaults.cardColors(containerColor = appColors().dangerSoft.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(0.5.dp, appColors().danger.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = appColors().danger,
                    fontWeight = FontWeight.Bold
                )

                val weekText = stringResource(Res.string.title_current_week, targetWeek.toString())

                val dayString = weekDays[course.day - 1]
                val detailsText = if (course.isCustomTime) {
                    stringResource(
                        Res.string.course_time_day_time_details_tweak,
                        dayString,
                        course.customStartTime ?: stringResource(Res.string.label_none),
                        course.customEndTime ?: stringResource(Res.string.label_none)
                    )
                } else {
                    stringResource(
                        Res.string.course_time_day_section_details_tweak,
                        dayString,
                        (course.startSection ?: 0).toString(),
                        (course.endSection ?: 0).toString()
                    )
                }

                Text(
                    text = "$weekText · $detailsText",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(vectorResource(Res.drawable.delete_24px), null, tint = appColors().danger.copy(alpha = 0.5f))
        }
    }
}

/**
 * 将 DatePicker 的毫秒值转换为 kotlinx.datetime.LocalDate。
 */
private fun Long.toLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date