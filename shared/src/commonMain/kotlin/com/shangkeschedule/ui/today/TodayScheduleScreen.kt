package com.shangkeschedule.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.shangkeschedule.data.model.schedule_style.BorderTypeProto
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shangkeschedule.Destination
import com.shangkeschedule.data.db.main.TodoItem
import com.shangkeschedule.data.model.DualColor
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.ui.components.AdaptiveNavigationScaffold
import com.shangkeschedule.ui.components.NativeNumberPicker
import com.shangkeschedule.ui.schedule.components.adaptiveTextColor
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.LocalThemePreset
import kotlinx.coroutines.delay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_todo_add
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.close_24px
import shangkeschedule.shared.generated.resources.confirm_delete
import shangkeschedule.shared.generated.resources.course_position_prefix
import shangkeschedule.shared.generated.resources.course_teacher_prefix
import shangkeschedule.shared.generated.resources.date_format_year_month_day
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.label_crush_course
import shangkeschedule.shared.generated.resources.label_remark
import shangkeschedule.shared.generated.resources.status_semester_ended
import shangkeschedule.shared.generated.resources.text_countdown_remaining
import shangkeschedule.shared.generated.resources.text_countdown_start
import shangkeschedule.shared.generated.resources.text_course_in_progress
import shangkeschedule.shared.generated.resources.text_courses_finished
import shangkeschedule.shared.generated.resources.text_no_courses_today
import shangkeschedule.shared.generated.resources.title_current_week
import shangkeschedule.shared.generated.resources.title_semester_not_set
import shangkeschedule.shared.generated.resources.title_today_schedule
import shangkeschedule.shared.generated.resources.title_vacation_until_start
import shangkeschedule.shared.generated.resources.todo_add
import shangkeschedule.shared.generated.resources.todo_delete_message
import shangkeschedule.shared.generated.resources.todo_delete_title
import shangkeschedule.shared.generated.resources.todo_edit
import shangkeschedule.shared.generated.resources.todo_note_label
import shangkeschedule.shared.generated.resources.todo_time_label
import shangkeschedule.shared.generated.resources.todo_title_label
import shangkeschedule.shared.generated.resources.week_days_full_names
import kotlin.time.Clock

private const val DEFAULT_TIME_ZERO = "00:00"
private const val EMPTY_TIME_PLACEHOLDER = "--:--"
private const val DEFAULT_DAYS_ZERO = "0"
private const val DEFAULT_OVERDUE_DAYS = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScheduleScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: TodayScheduleViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridStyle by viewModel.gridStyle.collectAsState()
    val isDark = LocalIsDarkTheme.current

    // 待办弹窗状态提升到页面层，供右下角悬浮「+」号触发
    var showTodoDialog by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<TodoItem?>(null) }
    var deletingTodo by remember { mutableStateOf<TodoItem?>(null) }

    AdaptiveNavigationScaffold(
        currentDestination = Destination.TodaySchedule,
        onTabSelected = { dest -> onNavigate(dest) }
    ) { outerPadding ->
        Scaffold(
            // 应用外层底部导航预留的内边距，避免 FAB 被底栏遮挡
            modifier = Modifier.padding(outerPadding),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.title_today_schedule),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors()
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editingTodo = null
                        showTodoDialog = true
                    }
                ) {
                    Icon(
                        vectorResource(Res.drawable.add_24px),
                        contentDescription = stringResource(Res.string.a11y_todo_add)
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (val state = uiState) {
                    is TodayUiState.Loading -> { /* 可放置圆圈加载 */ }
                    is TodayUiState.Success -> {
                        TodayContent(
                            state = state,
                            gridStyle = gridStyle,
                            isDark = isDark,
                            onToggleTodo = viewModel::toggleTodo,
                            onEditTodo = { todo ->
                                editingTodo = todo
                                showTodoDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // 待办添加/编辑弹窗
    if (showTodoDialog) {
        TodoEditDialog(
            existing = editingTodo,
            onDismiss = {
                showTodoDialog = false
                editingTodo = null
            },
            onConfirm = { title, note, time ->
                val existing = editingTodo
                if (existing != null) {
                    viewModel.updateTodo(existing.copy(title = title, note = note, time = time))
                } else {
                    viewModel.addTodo(title, note, time)
                }
                showTodoDialog = false
                editingTodo = null
            },
            onDeleteRequest = {
                val existing = editingTodo
                if (existing != null) {
                    showTodoDialog = false
                    deletingTodo = existing
                }
            }
        )
    }

    // 删除确认弹窗
    deletingTodo?.let { todo ->
        TodoDeleteDialog(
            todo = todo,
            onDismiss = { deletingTodo = null },
            onConfirm = {
                viewModel.deleteTodo(todo.id)
                deletingTodo = null
            }
        )
    }
}

@Composable
fun TodayContent(
    state: TodayUiState.Success,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    onToggleTodo: (String, Boolean) -> Unit,
    onEditTodo: (TodoItem) -> Unit
) {
    var currentTime by remember {
        mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time)
    }

    // 每分钟刷新一次，让「下节课」卡片的倒计时保持更新
    // （倒计时精度为分钟级，60s 足够；此前 30s 会使 TodayContent 全作用域每 30s 重组一次）
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
        }
    }

    val scrollState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        val weekDays = stringArrayResource(Res.array.week_days_full_names)

        val year = state.today.year.toString()
        val month = state.today.month.number.toString()
        val day = state.today.day.toString()

        val formattedDate = stringResource(Res.string.date_format_year_month_day, year, month, day)

        val dateStr = remember(state.today, weekDays, formattedDate) {
            val weekDayName = weekDays.getOrNull(state.today.dayOfWeek.isoDayNumber - 1).orEmpty()
            "$formattedDate $weekDayName"
        }

        val subTitle = when (state.status) {
            TodayStatus.NoSemesterConfig -> stringResource(Res.string.title_semester_not_set)

            TodayStatus.Vacation -> {
                val days = if (state.startDate != null) {
                    state.today.daysUntil(state.startDate).toString()
                } else DEFAULT_DAYS_ZERO
                stringResource(Res.string.title_vacation_until_start, days)
            }

            TodayStatus.SemesterEnded -> {
                val overdueDays = if (state.startDate != null) {
                    val targetDayOfWeek = DayOfWeek(state.firstDayOfWeek.coerceIn(1, 7))
                    val daysShift = (state.startDate.dayOfWeek.ordinal - targetDayOfWeek.ordinal + 7) % 7
                    val firstWeekStart = LocalDate.fromEpochDays(state.startDate.toEpochDays() - daysShift)

                    val semesterEndDate = LocalDate.fromEpochDays(
                        firstWeekStart.toEpochDays() + (state.totalWeeks * 7) - 1
                    )
                    semesterEndDate.daysUntil(state.today).coerceAtLeast(DEFAULT_OVERDUE_DAYS)
                } else {
                    DEFAULT_OVERDUE_DAYS
                }
                stringResource(Res.string.status_semester_ended, overdueDays)
            }

            TodayStatus.Normal -> stringResource(Res.string.title_current_week, state.weekIndex.toString())
        }

        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(text = dateStr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = subTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.courses.isNotEmpty() && state.status == TodayStatus.Normal) {
            NextCourseCard(
                courses = state.courses,
                gridStyle = gridStyle,
                isDark = isDark,
                now = currentTime
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.courses.isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(modifier = Modifier.weight(1f)) { EmptyStateView() }
                // 今日待办自动排到最后（空态时显示在页面底部）
                if (state.todos.isNotEmpty()) {
                    TodayTodoList(
                        todos = state.todos,
                        gridStyle = gridStyle,
                        isDark = isDark,
                        onToggle = onToggleTodo,
                        onEdit = onEditTodo
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                itemsIndexed(state.courses) { _, model ->
                    CourseTimelineItem(model, gridStyle, isDark, now = currentTime)
                }
                // 今日待办自动排到课程列表之后（页面最底部）
                if (state.todos.isNotEmpty()) {
                    item {
                        TodayTodoList(
                            todos = state.todos,
                            gridStyle = gridStyle,
                            isDark = isDark,
                            onToggle = onToggleTodo,
                            onEdit = onEditTodo
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun TodayTodoList(
    todos: List<TodoItem>,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (TodoItem) -> Unit
) {
    // 轻量行列表：无卡片外壳，待办条与课程条同款样式；待办已排至课程列表之后，直接展开
    Column(modifier = Modifier.fillMaxWidth()) {
        todos.forEachIndexed { index, todo ->
            TodoRow(
                todo = todo,
                gridStyle = gridStyle,
                isDark = isDark,
                onToggle = onToggle,
                onClick = { onEdit(todo) }
            )
            if (index != todos.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TodoRow(
    todo: TodoItem,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onClick: () -> Unit
) {
    val themePreset = LocalThemePreset.current
    val isTimetablePreset = themePreset == AppThemePreset.TIMETABLE
    val isSleepyPreset = themePreset == AppThemePreset.SLEEPY

    // 待办采用固定青色系，与课程配色区分；渲染逻辑与课程条（CourseTimelineItem）保持一致
    val colorPair = DualColor(light = Color(0xFFB2EBF2), dark = Color(0xFF0097A7))
    val themeColor = if (isTimetablePreset) {
        colorPair.dark.copy(alpha = if (isDark) 0.25f else 0.18f)
    } else {
        if (isDark) colorPair.dark else colorPair.light
    }
    val stripColor = colorPair.dark
    val textColor = if (isTimetablePreset) {
        if (isDark) Color(0xFFE0E0E0) else colorPair.dark
    } else {
        gridStyle.courseTextColorLong?.let { Color(it) }
            ?: adaptiveTextColor(themeColor, MaterialTheme.colorScheme.onSurface)
    }

    val cornerRadius = gridStyle.courseBlockCornerRadiusDp.dp
    val shape = RoundedCornerShape(cornerRadius)
    val cardShadowModifier = if (isSleepyPreset) {
        Modifier.shadow(elevation = 2.dp, shape = shape, clip = false)
    } else Modifier

    // 与课程条一致的边框样式
    val borderColor = MaterialTheme.colorScheme.outline
    val borderWidth = 1.dp
    val borderAlpha = gridStyle.courseBlockAlphaFloat
    val borderModifier = when (gridStyle.borderType) {
        BorderTypeProto.BORDER_TYPE_SOLID -> {
            Modifier.border(borderWidth, borderColor.copy(alpha = borderAlpha), shape)
        }
        BorderTypeProto.BORDER_TYPE_DASHED -> {
            Modifier.drawBehind {
                val strokeWidth = borderWidth.toPx()
                val dashPathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = borderColor.copy(alpha = borderAlpha),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, pathEffect = dashPathEffect)
                )
            }
        }
        else -> Modifier
    }
    // 已完成待办整体降透明（同课程已结束）
    val blockAlpha = when {
        todo.done && isTimetablePreset -> 0.7f
        todo.done -> 0.5f
        else -> gridStyle.courseBlockAlphaFloat
    }
    // 利落主题：左侧色条用 drawBehind 绘制（同课程条）
    val itemStripDrawModifier = if (isTimetablePreset) {
        Modifier.drawBehind {
            drawRect(
                color = stripColor,
                size = Size(width = 3.dp.toPx(), height = size.height)
            )
        }
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // 左侧时间列：与课程条一致，仅显示待办时间（无时间则留空，不显示占位符）
        Column(
            modifier = Modifier.width(65.dp).padding(top = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (!todo.time.isNullOrBlank()) {
                Text(
                    text = todo.time,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        textDecoration = if (todo.done) TextDecoration.LineThrough else null
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer(alpha = blockAlpha)
                .then(cardShadowModifier)
                .then(borderModifier)
                .clip(shape)
                .background(color = themeColor)
                .then(itemStripDrawModifier)
        ) {
            val innerPadding = gridStyle.courseBlockInnerPaddingDp.dp
            val timetableStartPad = if (isTimetablePreset) 3.dp else 0.dp
            val nameFontSize = (13f * gridStyle.courseBlockFontScale).sp
            val metaFontSize = (10f * gridStyle.courseBlockFontScale).sp
            Column(
                modifier = Modifier.padding(
                    start = innerPadding + timetableStartPad,
                    top = innerPadding,
                    end = innerPadding,
                    bottom = innerPadding
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 复选框放在色块内容最前
                    Checkbox(
                        checked = todo.done,
                        onCheckedChange = { checked -> onToggle(todo.id, checked) }
                    )
                    Text(
                        text = todo.title,
                        fontSize = nameFontSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                todo.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        fontSize = metaFontSize,
                        color = textColor.copy(alpha = 0.82f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoEditDialog(
    existing: TodoItem?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, note: String?, time: String?) -> Unit,
    onDeleteRequest: () -> Unit
) {
    var title by remember(existing) { mutableStateOf(existing?.title ?: "") }
    var time by remember(existing) { mutableStateOf(existing?.time ?: "") }
    var note by remember(existing) { mutableStateOf(existing?.note ?: "") }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (existing != null) {
                    IconButton(onClick = onDeleteRequest) {
                        Icon(
                            vectorResource(Res.drawable.delete_24px),
                            contentDescription = stringResource(Res.string.confirm_delete)
                        )
                    }
                }
                Text(
                    text = stringResource(if (existing == null) Res.string.todo_add else Res.string.todo_edit),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.todo_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 时间选择式：点击输入框弹出 TimePicker，右侧 × 清除已选时间
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = time,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(Res.string.todo_time_label)) },
                            placeholder = { Text(stringResource(Res.string.todo_time_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showTimePicker = true }
                        )
                    }
                    if (time.isNotBlank()) {
                        IconButton(onClick = { time = "" }) {
                            Icon(
                                vectorResource(Res.drawable.close_24px),
                                contentDescription = stringResource(Res.string.action_cancel)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(Res.string.todo_note_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) {
                    onConfirm(
                        title.trim(),
                        note.trim().takeIf { it.isNotBlank() },
                        time.trim().takeIf { it.isNotBlank() }
                    )
                }
            }) { Text(stringResource(Res.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        }
    )

    if (showTimePicker) {
        TodoTimePickerSheet(
            initialTime = time,
            onDismissRequest = { showTimePicker = false },
            onTimeSelected = { time = it }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoTimePickerSheet(
    initialTime: String?,
    onDismissRequest: () -> Unit,
    onTimeSelected: (String) -> Unit
) {
    // 与应用课程时间选择一致的滚轮底部弹窗（ModalBottomSheet + NativeNumberPicker）
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val parsed = initialTime?.split(":")
    val initialHour = (parsed?.getOrNull(0)?.toIntOrNull() ?: 12).coerceIn(0, 23)
    val initialMinute = (parsed?.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    val hours = remember { (0..23).map { it.toString().padStart(2, '0') } }
    val minutes = remember { (0..59).map { it.toString().padStart(2, '0') } }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.todo_time_label),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
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
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val hh = hour.toString().padStart(2, '0')
                    val mm = minute.toString().padStart(2, '0')
                    onTimeSelected("$hh:$mm")
                    onDismissRequest()
                }
            ) { Text(stringResource(Res.string.action_confirm)) }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TodoDeleteDialog(
    todo: TodoItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.todo_delete_title)) },
        text = { Text(stringResource(Res.string.todo_delete_message, todo.title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.confirm_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        }
    )
}

@Composable
private fun NextCourseCard(
    courses: List<CourseDisplayModel>,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime
) {
    fun parseOrNull(text: String?): LocalTime? = try {
        text?.let { LocalTime.parse(it) }
    } catch (e: Exception) {
        null
    }

    fun LocalTime.toMinutes(): Int = hour * 60 + minute
    fun LocalTime.toHHmm(): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    val ongoing = courses.firstOrNull { model ->
        val start = parseOrNull(model.startTime)
        val end = parseOrNull(model.endTime)
        start != null && end != null && start <= now && now < end
    }

    val next = if (ongoing == null) {
        courses.firstOrNull { model ->
            val start = parseOrNull(model.startTime)
            start != null && start > now
        }
    } else {
        null
    }

    val target = ongoing ?: next
    if (target == null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Text(
                text = stringResource(Res.string.text_courses_finished),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val colorPair = gridStyle.courseColorMaps.getOrElse(target.course.colorInt) {
        ScheduleGridStyle.DEFAULT_COLOR_MAPS[0]
    }

    val themePreset = LocalThemePreset.current
    val isTimetablePreset = themePreset == AppThemePreset.TIMETABLE
    val isSleepyPreset = themePreset == AppThemePreset.SLEEPY

    // 利落主题：courseColorMaps 颜色极浅，直接用 light 会与白底融为一体，
    // 改用 dark 半透明作为背景，保证对比度。
    val themeColor = if (isTimetablePreset) {
        colorPair.dark.copy(alpha = if (isDark) 0.25f else 0.18f)
    } else {
        if (isDark) colorPair.dark else colorPair.light
    }
    val stripColor = colorPair.dark
    val textColor = if (isTimetablePreset) {
        if (isDark) Color(0xFFE0E0E0) else colorPair.dark
    } else {
        gridStyle.courseTextColorLong?.let { Color(it) } ?: adaptiveTextColor(themeColor, MaterialTheme.colorScheme.onSurface)
    }

    val start = parseOrNull(target.startTime)
    val end = parseOrNull(target.endTime)

    val cornerRadius = gridStyle.courseBlockCornerRadiusDp.dp
    val shape = RoundedCornerShape(cornerRadius)
    val shadowModifier = if (isSleepyPreset) {
        Modifier.shadow(elevation = 3.dp, shape = shape, clip = false)
    } else Modifier

    // 利落主题：左侧色条用 drawBehind 绘制，不参与测量。
    // 若用子 Box + fillMaxHeight，在 Column 宽松 max 高度约束下会把整个卡片撑到剩余全部高度，
    // 挤掉下方课程列表（今日课表只显示一个课程的根因）。
    val stripDrawModifier = if (isTimetablePreset) {
        Modifier.drawBehind {
            drawRect(
                color = stripColor,
                size = Size(width = 3.dp.toPx(), height = size.height)
            )
        }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(shadowModifier)
            .clip(shape)
            .background(color = themeColor)
            .then(stripDrawModifier)
    ) {
        val startPadding = if (isTimetablePreset) 19.dp else 16.dp
        Column(modifier = Modifier.padding(start = startPadding, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = target.course.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                if (target.course.isCrush) {
                    Text(
                        text = stringResource(Res.string.label_crush_course),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor.copy(alpha = 0.85f),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(textColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (ongoing != null) {
                Text(
                    text = stringResource(Res.string.text_course_in_progress),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (target.course.position.isNotBlank()) {
                Text(
                    text = stringResource(Res.string.course_position_prefix, target.course.position),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.82f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (target.course.teacher.isNotBlank()) {
                Text(
                    text = stringResource(Res.string.course_teacher_prefix, target.course.teacher),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.82f)
                )
            }

            if (start != null && end != null) {
                Text(
                    text = "${start.toHHmm()} - ${end.toHHmm()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            val countdownText = if (ongoing != null && end != null) {
                val remaining = (end.toMinutes() - now.toMinutes()).coerceAtLeast(0)
                stringResource(Res.string.text_countdown_remaining, remaining.toString())
            } else if (next != null && start != null) {
                val minutesUntilStart = (start.toMinutes() - now.toMinutes()).coerceAtLeast(0)
                stringResource(Res.string.text_countdown_start, minutesUntilStart.toString())
            } else {
                null
            }

            if (countdownText != null) {
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
fun CourseTimelineItem(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime
) {
    // 用 TodayContent 里每分钟跳动的 now，而不是组合期固定快照，
    // 否则「已结束」状态（删除线/透明度）在页面停留期间永不更新。
    val isFinished = remember(model.endTime, now) {
        try {
            LocalTime.parse(model.endTime ?: DEFAULT_TIME_ZERO) < now
        } catch (e: Exception) { false }
    }

    val colorPair = gridStyle.courseColorMaps.getOrElse(model.course.colorInt) {
        ScheduleGridStyle.DEFAULT_COLOR_MAPS[0]
    }

    val themePreset = LocalThemePreset.current
    val isTimetablePreset = themePreset == AppThemePreset.TIMETABLE
    val isSleepyPreset = themePreset == AppThemePreset.SLEEPY

    // 利落主题：courseColorMaps 颜色极浅，直接用 light 会与白底融为一体，
    // 改用 dark 半透明作为背景，保证对比度。
    val themeColor = if (isTimetablePreset) {
        colorPair.dark.copy(alpha = if (isDark) 0.25f else 0.18f)
    } else {
        if (isDark) colorPair.dark else colorPair.light
    }
    val stripColor = colorPair.dark
    // 与主课表 CourseBlock 一致：利落主题用 dark，其他主题优先用自定义 courseTextColor
    val textColor = if (isTimetablePreset) {
        if (isDark) Color(0xFFE0E0E0) else colorPair.dark
    } else {
        gridStyle.courseTextColorLong?.let { Color(it) } ?: adaptiveTextColor(themeColor, MaterialTheme.colorScheme.onSurface)
    }

    val cornerRadius = gridStyle.courseBlockCornerRadiusDp.dp
    val shape = RoundedCornerShape(cornerRadius)
    val cardShadowModifier = if (isSleepyPreset) {
        Modifier.shadow(elevation = 2.dp, shape = shape, clip = false)
    } else Modifier

    // 与主课表 CourseBlock 一致：边框样式 + 课程块透明度
    val borderColor = MaterialTheme.colorScheme.outline
    val borderWidth = 1.dp
    val borderAlpha = gridStyle.courseBlockAlphaFloat
    val borderModifier = when (gridStyle.borderType) {
        BorderTypeProto.BORDER_TYPE_SOLID -> {
            Modifier.border(borderWidth, borderColor.copy(alpha = borderAlpha), shape)
        }
        BorderTypeProto.BORDER_TYPE_DASHED -> {
            Modifier.drawBehind {
                val strokeWidth = borderWidth.toPx()
                val dashPathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = borderColor.copy(alpha = borderAlpha),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, pathEffect = dashPathEffect)
                )
            }
        }
        else -> Modifier
    }
    // 已结束课程整体降透明；但利落主题背景本身 alpha=0.18，再乘 0.5 会近乎隐形，
    // 故利落主题下抬升到 0.7 只做轻微淡化。
    val blockAlpha = when {
        isFinished && isTimetablePreset -> 0.7f
        isFinished -> 0.5f
        else -> gridStyle.courseBlockAlphaFloat
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.width(65.dp).padding(top = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = model.startTime ?: EMPTY_TIME_PLACEHOLDER,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    textDecoration = if (isFinished) TextDecoration.LineThrough else null
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = model.endTime ?: EMPTY_TIME_PLACEHOLDER,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // 利落主题：左侧色条用 drawBehind 绘制，不参与测量（同 NextCourseCard，
            // 避免 fillMaxHeight 子 Box 在宽松高度约束下撑爆父容器）
            val itemStripDrawModifier = if (isTimetablePreset) {
                Modifier.drawBehind {
                    drawRect(
                        color = stripColor,
                        size = Size(width = 3.dp.toPx(), height = size.height)
                    )
                }
            } else Modifier
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(alpha = blockAlpha)
                    .then(cardShadowModifier)
                    .then(borderModifier)
                    .clip(shape)
                    .background(color = themeColor)
                    .then(itemStripDrawModifier)
            ) {

                // 与主课表 CourseBlock 一致的内边距和字号
                val innerPadding = gridStyle.courseBlockInnerPaddingDp.dp
                val timetableStartPad = if (isTimetablePreset) 3.dp else 0.dp
                val nameFontSize = (13f * gridStyle.courseBlockFontScale).sp
                val metaFontSize = (10f * gridStyle.courseBlockFontScale).sp
                val horizontalAlignment = if (gridStyle.textAlignCenterHorizontal) Alignment.CenterHorizontally else Alignment.Start
                val textAlign = if (gridStyle.textAlignCenterHorizontal) TextAlign.Center else TextAlign.Start
                Column(
                    modifier = Modifier.padding(
                        start = innerPadding + timetableStartPad,
                        top = innerPadding,
                        end = innerPadding,
                        bottom = innerPadding
                    ),
                    horizontalAlignment = horizontalAlignment
                ) {
                    Text(
                        text = model.course.name,
                        fontSize = nameFontSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        textDecoration = if (isFinished) TextDecoration.LineThrough else null,
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(lineHeight = 1.2.em)
                    )

                    if (!gridStyle.hideLocation && model.course.position.isNotBlank()) {
                        val prefix = if (gridStyle.removeLocationAt) "" else "@\u200B"
                        val breakablePos = model.course.position.replace(Regex("([@\\-（(）)])"), "\u200B$1\u200B")
                        Text(
                            text = "$prefix$breakablePos",
                            fontSize = metaFontSize,
                            color = textColor.copy(alpha = 0.82f),
                            textAlign = textAlign,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 4,
                            style = TextStyle(lineHeight = 1.1.em)
                        )
                    }

                    if (!gridStyle.hideTeacher && model.course.teacher.isNotBlank()) {
                        Text(
                            text = model.course.teacher,
                            fontSize = metaFontSize,
                            color = textColor.copy(alpha = 0.82f),
                            textAlign = textAlign,
                            maxLines = 1,
                            style = TextStyle(lineHeight = 1.1.em)
                        )
                    }
                }
            }
            model.course.remark?.takeIf { it.isNotBlank() }?.let { remark ->
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, start = 4.dp)
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(Res.string.label_remark),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = remark,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.text_no_courses_today),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
