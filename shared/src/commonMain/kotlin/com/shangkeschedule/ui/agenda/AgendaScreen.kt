package com.shangkeschedule.ui.agenda

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangkeschedule.Destination
import com.shangkeschedule.data.db.main.ScheduleCategory
import com.shangkeschedule.ui.components.AdaptiveNavigationScaffold
import com.shangkeschedule.ui.components.AppCard
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppEmptyState
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import com.shangkeschedule.ui.components.AppLoading
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.DatePickerModal
import com.shangkeschedule.ui.components.NativeNumberPicker
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_agenda_add
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.agenda_add_location
import shangkeschedule.shared.generated.resources.agenda_add_note
import shangkeschedule.shared.generated.resources.agenda_ai_coming_soon
import shangkeschedule.shared.generated.resources.agenda_ai_hint
import shangkeschedule.shared.generated.resources.agenda_all_day
import shangkeschedule.shared.generated.resources.agenda_back_to_today
import shangkeschedule.shared.generated.resources.agenda_category_activity
import shangkeschedule.shared.generated.resources.agenda_category_course
import shangkeschedule.shared.generated.resources.agenda_category_exam
import shangkeschedule.shared.generated.resources.agenda_category_homework
import shangkeschedule.shared.generated.resources.agenda_category_other
import shangkeschedule.shared.generated.resources.agenda_category_todo
import shangkeschedule.shared.generated.resources.agenda_count_format
import shangkeschedule.shared.generated.resources.agenda_create
import shangkeschedule.shared.generated.resources.agenda_day_title_format
import shangkeschedule.shared.generated.resources.agenda_delete_message
import shangkeschedule.shared.generated.resources.agenda_delete_title
import shangkeschedule.shared.generated.resources.agenda_duration_hours
import shangkeschedule.shared.generated.resources.agenda_duration_minutes
import shangkeschedule.shared.generated.resources.agenda_empty
import shangkeschedule.shared.generated.resources.agenda_end_label
import shangkeschedule.shared.generated.resources.agenda_group_afternoon
import shangkeschedule.shared.generated.resources.agenda_group_allday
import shangkeschedule.shared.generated.resources.agenda_group_evening
import shangkeschedule.shared.generated.resources.agenda_group_morning
import shangkeschedule.shared.generated.resources.agenda_lunar_format
import shangkeschedule.shared.generated.resources.agenda_new_title
import shangkeschedule.shared.generated.resources.agenda_start_label
import shangkeschedule.shared.generated.resources.agenda_status_finished
import shangkeschedule.shared.generated.resources.agenda_status_ongoing
import shangkeschedule.shared.generated.resources.agenda_status_upcoming
import shangkeschedule.shared.generated.resources.agenda_teacher_prefix
import shangkeschedule.shared.generated.resources.agenda_title_hint
import shangkeschedule.shared.generated.resources.agenda_today_badge
import shangkeschedule.shared.generated.resources.arrow_drop_down_24px
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.close_24px
import shangkeschedule.shared.generated.resources.confirm_delete
import shangkeschedule.shared.generated.resources.edit_24px
import shangkeschedule.shared.generated.resources.list_alt_24px
import shangkeschedule.shared.generated.resources.location_on_24px
import shangkeschedule.shared.generated.resources.month_names
import shangkeschedule.shared.generated.resources.more_vert_24px
import shangkeschedule.shared.generated.resources.schedule_24px
import shangkeschedule.shared.generated.resources.week_days_full_names
import shangkeschedule.shared.generated.resources.week_days_short_names
import kotlin.time.Clock

private val TIME_COLUMN_WIDTH = 56.dp
private val TIMELINE_COLUMN_WIDTH = 26.dp

/** 时间轴分组：0 全天 / 1 上午 / 2 下午 / 3 晚上。 */
private const val GROUP_ALL_DAY = 0
private const val GROUP_MORNING = 1
private const val GROUP_AFTERNOON = 2
private const val GROUP_EVENING = 3

/** 日程状态：0 已结束 / 1 进行中 / 2 未开始。 */
private const val STATUS_FINISHED = 0
private const val STATUS_ONGOING = 1
private const val STATUS_UPCOMING = 2

private enum class DateTimeTarget { START, END }

/**
 * 「日程」页：月历周条 + 当日课程/自建日程时间轴。
 *
 * 与原「今日课表」页共享同一套玻璃底栏与主题 tokens，视觉语言完全一致。
 */
@Composable
fun AgendaScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: AgendaViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedDate by viewModel.currentSelectedDate.collectAsState()

    var showCreateSheet by remember { mutableStateOf(false) }
    var deletingEntry by remember { mutableStateOf<AgendaEntry?>(null) }

    val hazeState = rememberHazeState()

    Box(modifier = Modifier.fillMaxSize()) {
        AdaptiveNavigationScaffold(
            currentDestination = Destination.Schedule,
            onTabSelected = { dest -> onNavigate(dest) }
        ) { outerPadding ->
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .hazeSource(hazeState)
                ) {
                    if (!uiState.isLoaded) {
                        AppLoading()
                    } else {
                        AgendaContent(
                            state = uiState,
                            bottomInset = outerPadding.calculateBottomPadding(),
                            onCreate = { showCreateSheet = true },
                            onSelectDate = viewModel::selectDate,
                            onPreviousMonth = viewModel::previousMonth,
                            onNextMonth = viewModel::nextMonth,
                            onGoToToday = viewModel::goToToday,
                            onDeleteEntry = { deletingEntry = it }
                        )
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        AgendaCreateSheet(
            initialDate = selectedDate,
            hazeState = hazeState,
            onDismiss = { showCreateSheet = false },
            onCreate = { date, title, category, allDay, start, end, location, note ->
                viewModel.addEvent(date, title, category, allDay, start, end, location, note)
                showCreateSheet = false
            }
        )
    }

    deletingEntry?.let { entry ->
        AppDangerDialog(
            onDismissRequest = { deletingEntry = null },
            title = stringResource(Res.string.agenda_delete_title),
            text = stringResource(Res.string.agenda_delete_message, entry.title),
            confirmText = stringResource(Res.string.confirm_delete),
            onConfirm = {
                viewModel.deleteEvent(entry.id)
                deletingEntry = null
            }
        )
    }
}

@Composable
private fun AgendaContent(
    state: AgendaUiState,
    bottomInset: Dp,
    onCreate: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToToday: () -> Unit,
    onDeleteEntry: (AgendaEntry) -> Unit
) {
    val tokens = appColors()
    val spacing = appSpacing()

    val monthNames = stringArrayResource(Res.array.month_names)
    val weekShortNames = stringArrayResource(Res.array.week_days_short_names)
    val weekFullNames = stringArrayResource(Res.array.week_days_full_names)

    // 每 30 秒刷新当前分钟，用于「已结束 / 进行中」状态实时翻转
    var nowMinutes by remember { mutableIntStateOf(currentMinutesOfDay()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMinutes = currentMinutesOfDay()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        AgendaMonthHeader(
            monthLabel = monthNames.getOrNull(state.month.month - 1).orEmpty(),
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onGoToToday = onGoToToday
        )

        AgendaWeekStrip(
            weekDays = state.weekDays,
            weekShortNames = weekShortNames,
            onSelectDate = onSelectDate,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        AgendaDayHeader(
            state = state,
            weekFullNames = weekFullNames,
            onCreate = onCreate,
            modifier = Modifier.padding(
                start = spacing.pageHorizontal,
                end = spacing.pageHorizontal,
                top = 10.dp
            )
        )

        Text(
            text = stringResource(Res.string.agenda_lunar_format, state.lunarText) +
                " · " + stringResource(Res.string.agenda_count_format, state.entries.size),
            fontSize = appType().caption,
            color = tokens.textSecondary,
            modifier = Modifier.padding(
                start = spacing.pageHorizontal,
                end = spacing.pageHorizontal,
                top = 4.dp,
                bottom = 8.dp
            )
        )

        if (state.entries.isEmpty()) {
            AppEmptyState(
                hint = stringResource(Res.string.agenda_empty),
                modifier = Modifier.padding(top = 64.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = spacing.pageHorizontal,
                    end = spacing.pageHorizontal,
                    top = 4.dp,
                    bottom = bottomInset + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val grouped = state.entries.groupBy(::entryGroup).toSortedMap()
                grouped.forEach { (group, entries) ->
                    item(key = "group_$group") {
                        AgendaGroupHeader(group = group, count = entries.size)
                    }
                    items(
                        items = entries,
                        key = { entry -> if (entry.isCourse) "course_${entry.id}" else "event_${entry.id}" }
                    ) { entry ->
                        AgendaEntryRow(
                            entry = entry,
                            selectedDate = state.selectedDate,
                            today = state.today,
                            nowMinutes = nowMinutes,
                            onDelete = { onDeleteEntry(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaMonthHeader(
    monthLabel: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToToday: () -> Unit
) {
    val tokens = appColors()
    val shapes = appShapes()
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = appSpacing().pageHorizontal, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = monthLabel,
            fontSize = appType().hero,
            fontWeight = FontWeight.Bold,
            color = tokens.textPrimary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier
                .clip(shapes.capsule)
                .background(tokens.inputBg)
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = vectorResource(Res.drawable.chevron_right_24px),
                    contentDescription = null,
                    tint = tokens.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = 180f }
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(tokens.divider)
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = vectorResource(Res.drawable.chevron_right_24px),
                    contentDescription = null,
                    tint = tokens.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = vectorResource(Res.drawable.more_vert_24px),
                    contentDescription = null,
                    tint = tokens.textSecondary
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.agenda_back_to_today)) },
                    onClick = {
                        menuExpanded = false
                        onGoToToday()
                    }
                )
            }
        }
    }
}

@Composable
private fun AgendaWeekStrip(
    weekDays: List<AgendaDayCell>,
    weekShortNames: List<String>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = appColors()
    val shapes = appShapes()
    var isSwiping by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { isSwiping = false }
                ) { _, dragAmount ->
                    if (dragAmount.x < -50f && !isSwiping) {
                        isSwiping = true
                        val current = weekDays.firstOrNull { it.isSelected }?.date
                        if (current != null) onSelectDate(current.plus(1, DateTimeUnit.DAY))
                    } else if (dragAmount.x > 50f && !isSwiping) {
                        isSwiping = true
                        val current = weekDays.firstOrNull { it.isSelected }?.date
                        if (current != null) onSelectDate(current.minus(1, DateTimeUnit.DAY))
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        weekDays.forEach { cell ->
            val selected = cell.isSelected
            val numberColor = when {
                selected -> tokens.textOnPrimary
                cell.isToday -> tokens.primary
                else -> tokens.textPrimary
            }
            val labelColor = if (selected) tokens.textOnPrimary.copy(alpha = 0.85f) else tokens.textSecondary
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shapes.chipSmall)
                    .background(if (selected) tokens.primary else Color.Transparent)
                    .clickable(enabled = !isSwiping) { onSelectDate(cell.date) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = weekShortNames.getOrNull(cell.date.dayOfWeek.isoDayNumber - 1).orEmpty(),
                    fontSize = appType().hint,
                    color = labelColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = cell.date.day.toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = numberColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cell.lunarLabel,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = labelColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !cell.hasEvents -> Color.Transparent
                                selected -> tokens.textOnPrimary
                                else -> tokens.primary
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun AgendaDayHeader(
    state: AgendaUiState,
    weekFullNames: List<String>,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = appColors()
    val shapes = appShapes()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.selectedDate == state.today) {
            Box(
                modifier = Modifier
                    .clip(shapes.capsule)
                    .background(tokens.primarySoft)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(Res.string.agenda_today_badge),
                    fontSize = appType().caption,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = stringResource(
                Res.string.agenda_day_title_format,
                state.selectedDate.month.number,
                state.selectedDate.day,
                weekFullNames.getOrNull(state.selectedDate.dayOfWeek.isoDayNumber - 1).orEmpty()
            ),
            fontSize = appType().pageTitle,
            fontWeight = FontWeight.Bold,
            color = tokens.textPrimary
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tokens.primarySoft)
                .clickable { onCreate() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.add_24px),
                contentDescription = stringResource(Res.string.a11y_agenda_add),
                tint = tokens.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun AgendaGroupHeader(group: Int, count: Int) {
    val tokens = appColors()
    val label = when (group) {
        GROUP_ALL_DAY -> stringResource(Res.string.agenda_group_allday)
        GROUP_MORNING -> stringResource(Res.string.agenda_group_morning)
        GROUP_AFTERNOON -> stringResource(Res.string.agenda_group_afternoon)
        else -> stringResource(Res.string.agenda_group_evening)
    }
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = vectorResource(Res.drawable.schedule_24px),
            contentDescription = null,
            tint = tokens.textSecondary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = appType().caption,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textSecondary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = count.toString(),
            fontSize = appType().caption,
            color = tokens.textSecondary
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgendaEntryRow(
    entry: AgendaEntry,
    selectedDate: LocalDate,
    today: LocalDate,
    nowMinutes: Int,
    onDelete: () -> Unit
) {
    val tokens = appColors()
    val status = entryStatus(entry, selectedDate, today, nowMinutes)
    val statusLabel = when (status) {
        STATUS_FINISHED -> stringResource(Res.string.agenda_status_finished)
        STATUS_ONGOING -> stringResource(Res.string.agenda_status_ongoing)
        else -> stringResource(Res.string.agenda_status_upcoming)
    }
    val statusColor = when (status) {
        STATUS_FINISHED -> tokens.textSecondary
        STATUS_ONGOING -> tokens.success
        else -> tokens.primary
    }
    val categoryLabel = if (entry.isCourse) {
        stringResource(Res.string.agenda_category_course)
    } else {
        categoryLabel(entry.categoryKey)
    }
    val metaLine = listOfNotNull(categoryLabel, entry.location).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(
            modifier = Modifier
                .width(TIME_COLUMN_WIDTH)
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = entry.startTime ?: "--:--",
                fontSize = appType().caption,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary
            )
            if (!entry.isAllDay && !entry.endTime.isNullOrBlank()) {
                Text(
                    text = entry.endTime,
                    fontSize = appType().caption,
                    color = tokens.textSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .width(TIMELINE_COLUMN_WIDTH)
                .fillMaxHeight()
                .drawBehind {
                    val centerX = size.width / 2f
                    val dotY = 12.dp.toPx()
                    drawLine(
                        color = tokens.divider,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, size.height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawCircle(
                        color = tokens.primary,
                        radius = 4.dp.toPx(),
                        center = Offset(centerX, dotY)
                    )
                }
        ) {}

        AppCard(
            modifier = Modifier.weight(1f),
            elevation = 1
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { if (!entry.isCourse) onDelete() }
                    )
                    .padding(appSpacing().cardInner)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        fontSize = appType().rowTitle,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (metaLine.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = metaLine,
                            fontSize = appType().caption,
                            color = tokens.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    entry.teacher?.let { teacher ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.agenda_teacher_prefix, teacher),
                            fontSize = appType().caption,
                            color = tokens.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (entry.teacher == null) {
                        entry.note?.let { note ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note,
                                fontSize = appType().caption,
                                color = tokens.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusLabel,
                    fontSize = appType().hint,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun categoryLabel(key: String?): String = when (ScheduleCategory.fromKey(key)) {
    ScheduleCategory.TODO -> stringResource(Res.string.agenda_category_todo)
    ScheduleCategory.ACTIVITY -> stringResource(Res.string.agenda_category_activity)
    ScheduleCategory.EXAM -> stringResource(Res.string.agenda_category_exam)
    ScheduleCategory.HOMEWORK -> stringResource(Res.string.agenda_category_homework)
    ScheduleCategory.OTHER -> stringResource(Res.string.agenda_category_other)
}

@Composable
private fun categoryColor(category: ScheduleCategory): Color {
    val tokens = appColors()
    return when (category) {
        ScheduleCategory.TODO -> tokens.success
        ScheduleCategory.ACTIVITY -> tokens.warning
        ScheduleCategory.EXAM -> tokens.danger
        ScheduleCategory.HOMEWORK -> tokens.info
        ScheduleCategory.OTHER -> tokens.textSecondary
    }
}

/**
 * 新建日程底部弹窗：标题 / 分类 / 全天 / 起止时间 / 地点 / 备注。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaCreateSheet(
    initialDate: LocalDate,
    hazeState: HazeState?,
    onDismiss: () -> Unit,
    onCreate: (
        date: LocalDate,
        title: String,
        category: ScheduleCategory,
        isAllDay: Boolean,
        startTime: String?,
        endTime: String?,
        location: String?,
        note: String?
    ) -> Unit
) {
    val tokens = appColors()
    val shapes = appShapes()
    val spacing = appSpacing()
    val type = appType()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val weekFullNames = stringArrayResource(Res.array.week_days_full_names)
    val aiComingSoonText = stringResource(Res.string.agenda_ai_coming_soon)

    val nowHour = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour }
    val defaultStartHour = (nowHour + 1).coerceIn(0, 23)
    val defaultEndHour = (defaultStartHour + 1).coerceAtMost(23)

    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ScheduleCategory.TODO) }
    var allDay by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(initialDate) }
    var endDate by remember { mutableStateOf(initialDate) }
    var startTime by remember { mutableStateOf(hourText(defaultStartHour, defaultStartHour == 23)) }
    var endTime by remember { mutableStateOf(hourText(defaultEndHour, defaultEndHour == 23)) }
    var location by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var timePickerTarget by remember { mutableStateOf<DateTimeTarget?>(null) }
    var datePickerTarget by remember { mutableStateOf<DateTimeTarget?>(null) }

    AppGlassBottomSheet(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.pageHorizontal)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.agenda_new_title),
                    fontSize = type.sectionTitle,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.close_24px),
                        contentDescription = stringResource(Res.string.action_cancel),
                        tint = tokens.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shapes.chipSmall)
                    .background(tokens.inputBg)
                    .clickable { ToastManager.show(aiComingSoonText) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.edit_24px),
                    contentDescription = null,
                    tint = tokens.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(Res.string.agenda_ai_hint),
                    fontSize = type.body,
                    color = tokens.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = vectorResource(Res.drawable.arrow_drop_down_24px),
                    contentDescription = null,
                    tint = tokens.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            AppTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = stringResource(Res.string.agenda_title_hint),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScheduleCategory.entries.forEach { item ->
                    AgendaCategoryChip(
                        category = item,
                        selected = item == category,
                        onClick = { category = item }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = tokens.divider)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.schedule_24px),
                    contentDescription = null,
                    tint = tokens.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(Res.string.agenda_all_day),
                    fontSize = type.body,
                    color = tokens.textPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                AppSwitch(checked = allDay, onCheckedChange = { allDay = it })
            }

            Spacer(modifier = Modifier.height(6.dp))

            AgendaDateTimeRow(
                label = stringResource(Res.string.agenda_start_label),
                date = startDate,
                weekNames = weekFullNames,
                time = if (allDay) null else startTime,
                trailingText = null,
                onDateClick = { datePickerTarget = DateTimeTarget.START },
                onTimeClick = { timePickerTarget = DateTimeTarget.START }
            )
            AgendaDateTimeRow(
                label = stringResource(Res.string.agenda_end_label),
                date = endDate,
                weekNames = weekFullNames,
                time = if (allDay) null else endTime,
                trailingText = if (allDay) null else durationText(startTime, endTime),
                onDateClick = { datePickerTarget = DateTimeTarget.END },
                onTimeClick = { timePickerTarget = DateTimeTarget.END }
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = tokens.divider)
            Spacer(modifier = Modifier.height(14.dp))

            AppTextField(
                value = location,
                onValueChange = { location = it },
                placeholder = stringResource(Res.string.agenda_add_location),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.location_on_24px),
                        contentDescription = null,
                        tint = tokens.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            AppTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = stringResource(Res.string.agenda_add_note),
                singleLine = false,
                minLines = 2,
                leadingIcon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.list_alt_24px),
                        contentDescription = null,
                        tint = tokens.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    onCreate(
                        startDate,
                        title.trim(),
                        category,
                        allDay,
                        if (allDay) null else startTime,
                        if (allDay) null else endTime,
                        location.trim().takeIf { it.isNotBlank() },
                        note.trim().takeIf { it.isNotBlank() }
                    )
                },
                enabled = title.isNotBlank(),
                shape = shapes.chip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.textOnPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = stringResource(Res.string.agenda_create),
                    fontSize = type.rowTitle,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    timePickerTarget?.let { target ->
        AgendaTimePickerDialog(
            title = stringResource(
                if (target == DateTimeTarget.START) Res.string.agenda_start_label
                else Res.string.agenda_end_label
            ),
            initialTime = if (target == DateTimeTarget.START) startTime else endTime,
            onDismiss = { timePickerTarget = null },
            onConfirm = { value ->
                if (target == DateTimeTarget.START) startTime = value else endTime = value
            }
        )
    }

    datePickerTarget?.let { target ->
        DatePickerModal(
            onDateSelected = { millis ->
                if (millis != null) {
                    val date = Instant.fromEpochMilliseconds(millis)
                        .toLocalDateTime(TimeZone.UTC)
                        .date
                    if (target == DateTimeTarget.START) startDate = date else endDate = date
                }
            },
            onDismiss = { datePickerTarget = null }
        )
    }
}

@Composable
private fun AgendaCategoryChip(
    category: ScheduleCategory,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tokens = appColors()
    val shapes = appShapes()
    val dotColor = categoryColor(category)
    val container = if (selected) tokens.primary else tokens.inputBg
    val contentColor = if (selected) tokens.textOnPrimary else tokens.textPrimary

    Row(
        modifier = Modifier
            .clip(shapes.capsule)
            .background(container)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (selected) tokens.textOnPrimary else dotColor)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = categoryLabel(category.key),
            fontSize = appType().body,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun AgendaDateTimeRow(
    label: String,
    date: LocalDate,
    weekNames: List<String>,
    time: String?,
    trailingText: String?,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    val tokens = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = appType().body,
            color = tokens.textSecondary,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = stringResource(
                Res.string.agenda_day_title_format,
                date.month.number,
                date.day,
                weekNames.getOrNull(date.dayOfWeek.isoDayNumber - 1).orEmpty()
            ),
            fontSize = appType().body,
            color = tokens.textPrimary,
            modifier = Modifier
                .clip(appShapes().chipSmall)
                .clickable { onDateClick() }
                .padding(horizontal = 6.dp, vertical = 6.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (time != null) {
            Text(
                text = time,
                fontSize = appType().body,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
                modifier = Modifier
                    .clip(appShapes().chipSmall)
                    .clickable { onTimeClick() }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }
        if (trailingText != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailingText,
                fontSize = appType().hint,
                color = tokens.textSecondary
            )
        }
    }
}

@Composable
private fun AgendaTimePickerDialog(
    title: String,
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val parsed = initialTime.split(":")
    var hour by remember {
        mutableIntStateOf((parsed.getOrNull(0)?.toIntOrNull() ?: 12).coerceIn(0, 23))
    }
    var minute by remember {
        mutableIntStateOf((parsed.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59))
    }
    val hours = remember { (0..23).map { it.toString().padStart(2, '0') } }
    val minutes = remember { (0..59).map { it.toString().padStart(2, '0') } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NativeNumberPicker(
                    values = hours,
                    selectedValue = hour.toString().padStart(2, '0'),
                    onValueChange = { hour = it.toInt() },
                    modifier = Modifier.weight(1f)
                )
                Text(":", style = MaterialTheme.typography.titleMedium)
                NativeNumberPicker(
                    values = minutes,
                    selectedValue = minute.toString().padStart(2, '0'),
                    onValueChange = { minute = it.toInt() },
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            AppDialogActions(
                confirmText = stringResource(Res.string.action_confirm),
                onConfirm = {
                    onConfirm(
                        hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
                    )
                    onDismiss()
                },
                dismissText = stringResource(Res.string.action_cancel),
                onDismiss = onDismiss
            )
        },
        dismissButton = {}
    )
}

/** 时间轴分组：全天 / 上午 / 下午 / 晚上。 */
private fun entryGroup(entry: AgendaEntry): Int {
    if (entry.isAllDay) return GROUP_ALL_DAY
    val hour = entry.startTime?.substringBefore(":")?.toIntOrNull() ?: return GROUP_MORNING
    return when {
        hour < 12 -> GROUP_MORNING
        hour < 18 -> GROUP_AFTERNOON
        else -> GROUP_EVENING
    }
}

/** 计算条目状态：已结束 / 进行中 / 未开始。 */
private fun entryStatus(
    entry: AgendaEntry,
    selectedDate: LocalDate,
    today: LocalDate,
    nowMinutes: Int
): Int {
    if (selectedDate < today) return STATUS_FINISHED
    if (selectedDate > today) return STATUS_UPCOMING
    if (entry.isAllDay) return STATUS_ONGOING
    val start = parseMinutes(entry.startTime) ?: return STATUS_UPCOMING
    val end = parseMinutes(entry.endTime) ?: start
    return when {
        nowMinutes > end -> STATUS_FINISHED
        nowMinutes >= start -> STATUS_ONGOING
        else -> STATUS_UPCOMING
    }
}

private fun parseMinutes(time: String?): Int? {
    if (time.isNullOrBlank()) return null
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return hour * 60 + minute
}

private fun currentMinutesOfDay(): Int {
    val time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
    return time.hour * 60 + time.minute
}

private fun hourText(hour: Int, lastHour: Boolean): String =
    hour.toString().padStart(2, '0') + if (lastHour) ":59" else ":00"

@Composable
private fun durationText(startTime: String, endTime: String): String {
    val start = parseMinutes(startTime) ?: 0
    val end = parseMinutes(endTime) ?: 0
    val diff = (end - start).coerceAtLeast(0)
    val hours = diff / 60
    val minutes = diff % 60
    return when {
        hours > 0 && minutes > 0 ->
            stringResource(Res.string.agenda_duration_hours, hours) +
                stringResource(Res.string.agenda_duration_minutes, minutes)
        hours > 0 -> stringResource(Res.string.agenda_duration_hours, hours)
        else -> stringResource(Res.string.agenda_duration_minutes, minutes)
    }
}
