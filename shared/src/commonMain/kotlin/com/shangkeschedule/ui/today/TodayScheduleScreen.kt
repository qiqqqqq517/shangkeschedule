package com.shangkeschedule.ui.today

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.shangkeschedule.ui.components.ThemedLoadingIndicator
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.data.model.schedule_style.BorderTypeProto
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shangkeschedule.Destination
import com.shangkeschedule.data.db.main.ScheduleCategory
import com.shangkeschedule.data.db.main.ScheduleEvent
import com.shangkeschedule.data.db.main.TodoItem
import com.shangkeschedule.data.model.DualColor
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.ui.components.AdaptiveNavigationScaffold
import com.shangkeschedule.ui.components.AppCard
import com.shangkeschedule.ui.components.AppCheckboxIndicator
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppFab
import com.shangkeschedule.ui.components.AppHeroMotif
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import com.shangkeschedule.ui.components.AppLoading
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.NativeNumberPicker
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.shangkeschedule.ui.schedule.components.adaptiveTextColor
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.softGlow
import com.shangkeschedule.ui.theme.softTexture
import com.shangkeschedule.ui.theme.softFeatherRim
import com.shangkeschedule.ui.theme.softShadow
import com.shangkeschedule.ui.theme.iosGlassRim
import com.shangkeschedule.ui.theme.iosUiSans
import com.shangkeschedule.ui.theme.appType
import com.shangkeschedule.ui.theme.claudeDisplaySerif
import com.shangkeschedule.ui.theme.claudeReadingSerif
import com.shangkeschedule.ui.theme.claudeUiSans
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.MotionPressMode
import com.shangkeschedule.ui.theme.appColorTokens
import com.shangkeschedule.ui.theme.appColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_todo_add
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.close_24px
import shangkeschedule.shared.generated.resources.confirm_delete
import shangkeschedule.shared.generated.resources.course_position_prefix
import shangkeschedule.shared.generated.resources.course_teacher_prefix
import shangkeschedule.shared.generated.resources.date_format_year_month_day
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.label_crush_course
import shangkeschedule.shared.generated.resources.label_remark
import shangkeschedule.shared.generated.resources.location_on_24px
import shangkeschedule.shared.generated.resources.person_24px
import shangkeschedule.shared.generated.resources.status_semester_ended
import shangkeschedule.shared.generated.resources.text_countdown_remaining
import shangkeschedule.shared.generated.resources.text_countdown_start
import shangkeschedule.shared.generated.resources.text_course_in_progress
import shangkeschedule.shared.generated.resources.text_courses_count
import shangkeschedule.shared.generated.resources.text_courses_finished
import shangkeschedule.shared.generated.resources.action_week_view
import shangkeschedule.shared.generated.resources.text_no_courses_today
import shangkeschedule.shared.generated.resources.title_current_week
import shangkeschedule.shared.generated.resources.title_semester_not_set
import shangkeschedule.shared.generated.resources.title_today_courses
import shangkeschedule.shared.generated.resources.title_today_schedule
import shangkeschedule.shared.generated.resources.title_tomorrow_courses
import shangkeschedule.shared.generated.resources.title_vacation_until_start
import shangkeschedule.shared.generated.resources.todo_add
import shangkeschedule.shared.generated.resources.todo_delete_message
import shangkeschedule.shared.generated.resources.todo_delete_title
import shangkeschedule.shared.generated.resources.todo_edit
import shangkeschedule.shared.generated.resources.todo_note_label
import shangkeschedule.shared.generated.resources.todo_time_label
import shangkeschedule.shared.generated.resources.todo_title_label
import shangkeschedule.shared.generated.resources.agenda_category_activity
import shangkeschedule.shared.generated.resources.agenda_category_exam
import shangkeschedule.shared.generated.resources.agenda_category_homework
import shangkeschedule.shared.generated.resources.agenda_category_other
import shangkeschedule.shared.generated.resources.agenda_category_todo
import shangkeschedule.shared.generated.resources.agenda_count_format

import shangkeschedule.shared.generated.resources.schedule_24px
import shangkeschedule.shared.generated.resources.today_ios_countdown_label
import shangkeschedule.shared.generated.resources.today_ios_meta_credit
import shangkeschedule.shared.generated.resources.today_ios_meta_room
import shangkeschedule.shared.generated.resources.today_ios_meta_teacher
import shangkeschedule.shared.generated.resources.today_ios_meta_time
import shangkeschedule.shared.generated.resources.today_ios_next_label
import shangkeschedule.shared.generated.resources.today_ios_note_hours
import shangkeschedule.shared.generated.resources.today_ios_note_type
import shangkeschedule.shared.generated.resources.today_ios_sections_format
import shangkeschedule.shared.generated.resources.today_ios_sheet_close
import shangkeschedule.shared.generated.resources.today_ios_sheet_edit
import shangkeschedule.shared.generated.resources.today_ios_status_done
import shangkeschedule.shared.generated.resources.today_ios_status_live
import shangkeschedule.shared.generated.resources.today_ios_status_upcoming
import shangkeschedule.shared.generated.resources.today_ios_tomorrow_format
import shangkeschedule.shared.generated.resources.today_ios_type_lab
import shangkeschedule.shared.generated.resources.today_ios_type_theory
import shangkeschedule.shared.generated.resources.today_ios_view_all
import shangkeschedule.shared.generated.resources.today_claude_badge_lab
import shangkeschedule.shared.generated.resources.today_claude_badge_required
import shangkeschedule.shared.generated.resources.today_claude_countdown_label
import shangkeschedule.shared.generated.resources.today_claude_date_format
import shangkeschedule.shared.generated.resources.today_claude_meta_credit
import shangkeschedule.shared.generated.resources.today_claude_meta_room
import shangkeschedule.shared.generated.resources.today_claude_meta_teacher
import shangkeschedule.shared.generated.resources.today_claude_meta_time
import shangkeschedule.shared.generated.resources.today_claude_next_label
import shangkeschedule.shared.generated.resources.today_claude_note_hours
import shangkeschedule.shared.generated.resources.today_claude_note_type
import shangkeschedule.shared.generated.resources.today_claude_remaining
import shangkeschedule.shared.generated.resources.today_claude_sections_format
import shangkeschedule.shared.generated.resources.today_claude_sheet_close
import shangkeschedule.shared.generated.resources.today_claude_sheet_edit
import shangkeschedule.shared.generated.resources.today_claude_status_done
import shangkeschedule.shared.generated.resources.today_claude_status_live
import shangkeschedule.shared.generated.resources.today_claude_status_upcoming
import shangkeschedule.shared.generated.resources.today_claude_tomorrow_format
import shangkeschedule.shared.generated.resources.today_claude_type_lab
import shangkeschedule.shared.generated.resources.today_claude_type_theory
import shangkeschedule.shared.generated.resources.today_claude_view_all
import shangkeschedule.shared.generated.resources.widget_title_today
import shangkeschedule.shared.generated.resources.week_days_full_names
import kotlin.time.Clock

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
    // 书卷 / 通透两套主题的今日页都自带页头（书卷：日期 eyebrow + 大标题 + 图标钮；
    // 通透：周次胶囊 + 日期大字），因此都不叠加 M3 CenterAlignedTopAppBar；
    // 右下角待办 FAB 在两套主题下同样隐藏，待办改由「日程」页访问。
    val themePreset = LocalThemePreset.current
    val isClaudePreset = themePreset == AppThemePreset.CLAUDE
    val isIosPreset = themePreset == AppThemePreset.IOS
    val isSoftPreset = themePreset == AppThemePreset.SOFT
    // 柔绘同样自带页头（周次胶囊 + 日期大字），必须与书卷/通透一样屏蔽 M3 顶栏与待办 FAB，
    // 否则柔绘今日页会多出一条「今日课表」标题栏 + FAB，信息架构与另两套主题不一致。
    val hasCustomHeader = isClaudePreset || isIosPreset || isSoftPreset

    // 待办弹窗状态提升到页面层，供右下角悬浮「+」号触发
    var showTodoDialog by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<TodoItem?>(null) }
    var deletingTodo by remember { mutableStateOf<TodoItem?>(null) }

    // 下拉刷新状态（v3.43.0 ·《交互动效审查》P2）：列表已是 DB Flow 驱动，刷新 = 立刻重读一次
    var refreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    // 下拉进度状态：自定义指示器需据此判断显隐（M3 不再托管自定义 indicator 的定位）
    val pullToRefreshState = rememberPullToRefreshState()

    // 悬浮面板玻璃：主内容 hazeSource，底部弹窗背板模糊
    val hazeState = rememberHazeState()

    // v3.23.5 修复：hazeSource 不能标在包含玻璃 AppFab 的外层 Box 上（FAB 位于
    // Scaffold floatingActionButton slot，是 hazeSource 子树的一部分），否则 haze 库
    // 记录 backdrop 内容层时遇到子树内的 hazeEffect 节点直接抛
    // "Modifier.haze nodes can not draw Modifier.hazeChild nodes" 崩溃。
    // 移到内层内容 Box 上（与设置页玻璃 FAB 同构：FAB 在 hazeSource 之外）。
    Box(modifier = Modifier.fillMaxSize()) {
        AdaptiveNavigationScaffold(
            currentDestination = Destination.TodaySchedule,
            onTabSelected = { dest -> onNavigate(dest) }
        ) { outerPadding ->
        Scaffold(
            // 应用外层底部导航预留的内边距，避免 FAB 被底栏遮挡
            modifier = Modifier,
            // P2-1 内外 Scaffold 系统栏 inset 双计数修复：
            // 底部导航栏 inset 已由外层 AdaptiveNavigationScaffold（barInsetBottom）统一预留，
            // 内层再套默认 contentWindowInsets=navigationBars 会导致底部内边距叠加，列表/FAB 偏高。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (!hasCustomHeader) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = stringResource(Res.string.title_today_schedule),
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = appType().pageTitle),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors()
                    )
                }
            },
            floatingActionButton = {
                // 书卷 / 通透两套主题均隐藏待办 FAB，待办改由「日程」页承载
                if (!hasCustomHeader) {
                    AppFab(
                        onClick = {
                            editingTodo = null
                            showTodoDialog = true
                        },
                        icon = vectorResource(Res.drawable.add_24px),
                        contentDescription = stringResource(Res.string.a11y_todo_add),
                        // 液态玻璃 FAB：复用页面既有 hazeState（内容已 hazeSource）
                        hazeState = hazeState,
                        modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding())
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding).hazeSource(hazeState)) {
                when (val state = uiState) {
                    is TodayUiState.Loading -> AppLoading()
                    is TodayUiState.Success -> {
                        PullToRefreshBox(
                            isRefreshing = refreshing,
                            onRefresh = {
                                refreshScope.launch {
                                    refreshing = true
                                    viewModel.refresh()
                                    // 本地 DB 重读极快，留一拍可见反馈再收起（避免指示器一闪而过）
                                    delay(520)
                                    refreshing = false
                                }
                            },
                            state = pullToRefreshState,
                            modifier = Modifier.fillMaxSize(),
                            indicator = {
                                // 主题化下拉指示：复用与 AppLoading 同一枚主题指示器
                                // （柔绘三点呼吸 / 书卷墨点晕开 / 通透八段旋转），
                                // 取代 M3 默认的 Material 箭头（与三套主题语言都不符）。
                                // 注意：自定义 indicator 不再受 M3 的定位逻辑托管，
                                // 必须自行按下拉进度显隐，否则会在页头常驻一枚圆形指示器。
                                val pullProgress = pullToRefreshState.distanceFraction
                                if (refreshing || pullProgress > 0.01f) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 14.dp)
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(appColors().cardBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ThemedLoadingIndicator()
                                    }
                                }
                            }
                        ) {
                            TodayContent(
                                state = state,
                                bottomInset = outerPadding.calculateBottomPadding(),
                                gridStyle = gridStyle,
                                isDark = isDark,
                                onToggleTodo = viewModel::toggleTodo,
                                onEditTodo = { todo ->
                                    editingTodo = todo
                                    showTodoDialog = true
                                },
                                onNavigateWeekly = { onNavigate(Destination.CourseSchedule) },
                                onOpenSettings = { onNavigate(Destination.Settings) },
                                onEditCourse = { courseId ->
                                    onNavigate(Destination.AddEditCourse(courseId))
                                }
                            )
                        }
                    }
                }
            }
        }
        }
    }

    // 待办添加/编辑弹窗
    if (showTodoDialog) {
        TodoEditDialog(
            existing = editingTodo,
            hazeState = hazeState,
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
    bottomInset: Dp,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    onToggleTodo: (String, Boolean) -> Unit,
    onEditTodo: (TodoItem) -> Unit,
    onNavigateWeekly: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onEditCourse: (String) -> Unit = {},
    // 仅用于视觉回归预览：覆盖主题预设，避免预览宿主必须走完整 CompositionLocal 链
    presetOverride: AppThemePreset? = null
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
    val themePreset = presetOverride ?: LocalThemePreset.current
    val isIosPreset = themePreset == AppThemePreset.IOS
    val isSoftPreset = themePreset == AppThemePreset.SOFT
    val isClaudePreset = themePreset == AppThemePreset.CLAUDE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = appSpacing().pageHorizontal)
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

        if (isSoftPreset) {
            // ===== 柔绘主题：页头 + 下节课卡 + 课程时间轴 + 日程事件 + 明日预览 =====
            // 与「书卷」「通透」共用同一份信息架构（同字段、同顺序、同位置），
            // 只把材质换成柔绘：晕染渐变 + 虚化圆角 + 薄涂底 + 漫射柔光 + 软模糊投影。
            SoftTodayContent(
                state = state,
                gridStyle = gridStyle,
                isDark = isDark,
                now = currentTime,
                bottomInset = bottomInset,
                statusText = subTitle,
                dateText = dateStr,
                scrollState = scrollState,
                onOpenWeeklySchedule = onNavigateWeekly,
                onOpenSettings = onOpenSettings,
                onEditCourse = onEditCourse
            )
            return@Column
        }

        if (isIosPreset) {
            // ===== 通透主题（iOS 26）：页头 + 下节课卡 + 课程时间轴 + 日程事件 + 明日预览 =====
            // 信息架构与下面的「书卷」分支逐项一致（同一批字段、同一顺序、同一位置），
            // 只把暖砂渐变卡片换成 Apple 系统色 + 玻璃高光描边的 iOS 26 形态。
            Ios26TodayContent(
                state = state,
                gridStyle = gridStyle,
                isDark = isDark,
                now = currentTime,
                bottomInset = bottomInset,
                statusText = subTitle,
                dateText = dateStr,
                scrollState = scrollState,
                onOpenWeeklySchedule = onNavigateWeekly,
                onOpenSettings = onOpenSettings,
                onEditCourse = onEditCourse
            )
            return@Column
        }

        if (isClaudePreset) {
            // ===== Claude 主题：页头 + 周次条 + 课程卡片流 + 明日预览（对齐设计包 pages/今日日程.html） =====
            ClaudeTodayContent(
                state = state,
                gridStyle = gridStyle,
                isDark = isDark,
                now = currentTime,
                bottomInset = bottomInset,
                statusText = subTitle,
                dateText = dateStr,
                scrollState = scrollState,
                onOpenWeeklySchedule = onNavigateWeekly,
                onOpenSettings = onOpenSettings,
                onEditCourse = onEditCourse
            )
            return@Column
        }

        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = appType().bigNumber),
                fontWeight = FontWeight.Bold,
                color = appColors().textPrimary
            )
            Text(
                text = subTitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = appType().caption),
                color = appColors().textSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.courses.isNotEmpty() && state.status == TodayStatus.Normal && !isIosPreset) {
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
            // v3.26.0 C+.17 今日页入场错峰：在 LazyColumn 外读取 LocalAppMotion
            // （LazyListScope 不是 composable 作用域）
            val todayEntranceMotion = LocalAppMotion.current
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (isIosPreset) 28.dp else appSpacing().listGap),
                // iOS 主题：宽屏（平板/桌面）内容限宽 640dp 居中
                horizontalAlignment = if (isIosPreset) Alignment.CenterHorizontally else Alignment.Start,
                contentPadding = PaddingValues(bottom = bottomInset + appSpacing().listGap)
            ) {
                // iOS 主题：「今日课程」大标题 + 课程数
                if (isIosPreset) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
                            IosSectionTitle(
                                title = stringResource(Res.string.title_today_courses),
                                subtitle = stringResource(Res.string.text_courses_count, state.courses.size.toString())
                            )
                        }
                    }

                    // iOS 主题：今日课程扁平列表
                    item {
                        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
                            IosCourseGroup(
                                courses = state.courses,
                                gridStyle = gridStyle,
                                isDark = isDark,
                                now = currentTime
                            )
                        }
                    }
                    // iOS 主题：明日课程区块
                    if (state.tomorrowCourses.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
                                Spacer(modifier = Modifier.height(24.dp))
                                IosGroupHeader(
                                    title = stringResource(Res.string.title_tomorrow_courses),
                                    action = stringResource(Res.string.action_week_view)
                                )
                            }
                        }
                        item {
                            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
                                IosCourseGroup(
                                    courses = state.tomorrowCourses,
                                    gridStyle = gridStyle,
                                    isDark = isDark,
                                    now = currentTime,
                                    showFinishedState = false
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(state.courses) { index, model ->
                        CourseTimelineItem(
                            model,
                            gridStyle,
                            isDark,
                            now = currentTime,
                            entranceDelayMs = todayEntranceMotion.tokens.entranceStaggerMs * index
                        )
                    }
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

// ============================================================================
// Claude 主题 · 今日课表页
//
// 严格对齐设计包 claude-schedule-page.design/pages/今日日程.html：
//   页头（日期 eyebrow + 28sp 标题 + 课程数徽标 + 圆形图标钮）
//   → 周次条（左右翻页 + 周次 / 日期区间）
//   → 课程卡片流（5dp 色条 + 课程名 + 类型徽标 + 时间/地点/教师三行元信息；
//      已结束降透明度 + 删除线，进行中主色描边 + 顶部 2dp 主色线 + 呼吸圆点）
//   → 明日预览（4dp 色条 + 名称 + 时间·地点一行）
//
// 设计稿的「状态栏」「底部导航」由系统与 App 底栏承担，不在页面内重复绘制；
// 设计稿的「课程详情弹层」在此实现为 AppGlassBottomSheet，字段与设计稿一致。
// ============================================================================

/** 设计稿 .timeline-time-col 宽 56dp；明日预览色条 4dp。 */
private val ClaudeTomorrowAccentWidth = 4.dp

private val ClaudeLilac50 = Color(0xFFF5F2FF)
private val ClaudeLilac100 = Color(0xFFEBE5FF)
private val ClaudeLilac500 = Color(0xFF9C87F5)
private val ClaudeLilac700 = Color(0xFF6B5BB5)
private val ClaudeLilac800 = Color(0xFF524491)
private val ClaudePeach50 = Color(0xFFFFF0ED)
private val ClaudePeach500 = Color(0xFFE88672)
private val ClaudePeach700 = Color(0xFFC45F4A)
private val ClaudeBrand400 = Color(0xFFD6866A)

private data class ClaudeTimelinePalette(
    val gradient: List<Color>,
    val border: Color,
    val title: Color,
    val meta: Color,
    val badgeBg: Color,
    val badgeFg: Color,
    val dot: Color
)

@Composable
private fun claudeTimelinePalette(peach: Boolean): ClaudeTimelinePalette {
    val colors = appColors()
    val isDark = LocalIsDarkTheme.current
    return when {
        peach && isDark -> ClaudeTimelinePalette(
            gradient = listOf(Color(0xFF3A2622), Color(0xFF31211E)),
            border = Color(0x26E88672),
            title = Color(0xFFF7B6A8),
            meta = Color(0xFFC79A90),
            badgeBg = Color(0x33E88672),
            badgeFg = Color(0xFFF7B6A8),
            dot = ClaudePeach500
        )

        peach -> ClaudeTimelinePalette(
            gradient = listOf(ClaudePeach50, Color(0xFFFFF8F6)),
            border = Color(0x1FE88672),
            title = colors.textPrimary,
            meta = colors.textSecondary,
            badgeBg = Color(0x26E88672),
            badgeFg = ClaudePeach700,
            dot = ClaudePeach500
        )

        isDark -> ClaudeTimelinePalette(
            gradient = listOf(Color(0xFF2E2749), Color(0xFF272240)),
            border = Color(0x269C87F5),
            title = Color(0xFFC4B8E8),
            meta = Color(0xFF9A93B8),
            badgeBg = Color(0x339C87F5),
            badgeFg = Color(0xFFC4B8E8),
            dot = ClaudeBrand400
        )

        else -> ClaudeTimelinePalette(
            gradient = listOf(ClaudeLilac50, Color(0xFFF8F6FF)),
            border = Color(0x1F9C87F5),
            title = colors.textPrimary,
            meta = colors.textSecondary,
            badgeBg = Color(0x269C87F5),
            badgeFg = ClaudeLilac700,
            dot = ClaudeBrand400
        )
    }
}

@Composable
private fun ClaudeTodayContent(
    state: TodayUiState.Success,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    bottomInset: Dp,
    statusText: String,
    dateText: String,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    onOpenWeeklySchedule: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditCourse: (String) -> Unit
) {
    val colors = appColors()
    var detailCourse by remember { mutableStateOf<CourseDisplayModel?>(null) }

    val nextCourse = remember(state.courses, now) {
        val upcoming = state.courses.firstOrNull { model ->
            val start = model.startTime?.takeIf { it.isNotBlank() }?.let {
                runCatching { LocalTime.parse(it) }.getOrNull()
            }
            start != null && start > now
        }
        upcoming ?: state.courses.firstOrNull { !isClaudeCourseFinished(it, now) }
    }
    val tomorrowDate = stringResource(
        Res.string.today_claude_tomorrow_format,
        state.today.month.number,
        state.today.day
    )

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = bottomInset + 100.dp)
    ) {
        item {
            ClaudeTodayHeader(
                weekIndex = state.weekIndex,
                status = state.status,
                statusText = statusText,
                dateText = dateText,
            )
        }

        if (nextCourse != null) {
            item {
                ClaudeNextClassCard(
                    model = nextCourse,
                    gridStyle = gridStyle,
                    isDark = isDark,
                    now = now,
                    onClick = { detailCourse = nextCourse }
                )
            }
        }

        item {
            ClaudeTimelineHeader(
                title = stringResource(Res.string.title_today_courses),
                count = stringResource(Res.string.text_courses_count, state.courses.size.toString())
            )
        }

        if (state.courses.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.text_no_courses_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            itemsIndexed(
                state.courses,
                key = { _, model -> model.course.id }
            ) { index, model ->
                ClaudeTimelineItem(
                    model = model,
                    index = index,
                    isLast = index == state.courses.lastIndex,
                    gridStyle = gridStyle,
                    isDark = isDark,
                    now = now,
                    onClick = { detailCourse = model }
                )
            }
        }

        // 今日日程事件（来自「日程」页新建的日程）
        if (state.events.isNotEmpty()) {
            item {
                ClaudeEventsSection(events = state.events)
            }
        }

        if (state.tomorrowCourses.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        ClaudeSectionLabelRow(
                            label = tomorrowDate,
                            trailing = stringResource(
                                Res.string.text_courses_count,
                                state.tomorrowCourses.size.toString()
                            ),
                            fillWidth = false
                        )
                    }
                    ClaudeGhostButton(
                        text = stringResource(Res.string.today_claude_view_all),
                        onClick = onOpenWeeklySchedule
                    )
                }
            }
            itemsIndexed(
                state.tomorrowCourses,
                key = { _, model -> "tomorrow-${model.course.id}" }
            ) { _, model ->
                ClaudeTomorrowCard(model = model, gridStyle = gridStyle, isDark = isDark)
            }
        }
    }

    detailCourse?.let { model ->
        ClaudeCourseDetailSheet(
            model = model,
            gridStyle = gridStyle,
            isDark = isDark,
            now = now,
            onDismiss = { detailCourse = null },
            onEdit = {
                detailCourse = null
                onEditCourse(model.course.id)
            }
        )
    }
}

/** 页头：周次胶囊置于左侧 + 日期居中大字，同一行。 */
@Composable
private fun ClaudeTodayHeader(
    weekIndex: Int,
    status: TodayStatus,
    statusText: String,
    dateText: String,
) {
    val colors = appColors()
    val weekLabel = if (status == TodayStatus.Normal) {
        stringResource(Res.string.title_current_week, weekIndex.toString())
    } else {
        statusText
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 12.dp)
    ) {
        // 周次胶囊：方形块，靠左
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.primarySoft)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = weekLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.02.em,
                    lineHeight = 16.sp
                ),
                color = colors.primary,
                maxLines = 1
            )
        }
        // 日期：行内居中大字
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp,
                letterSpacing = (-0.01).em
            ),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 84.dp)
        )
    }
}

/** 分区标题行：Lora 12sp 大写字距标签 + 右侧辅助文案。 */
@Composable
private fun ClaudeSectionLabelRow(
    label: String,
    trailing: String?,
    fillWidth: Boolean = true
) {
    val colors = appColors()
    Row(
        modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = claudeReadingSerif(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.14.em,
                lineHeight = 16.sp
            ),
            color = colors.textSecondary
        )
        if (trailing != null) {
            if (fillWidth) Spacer(modifier = Modifier.weight(1f))
            else Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                ),
                color = colors.textSecondary
            )
        }
    }
}

/** 时间轴标题：设计稿 .timeline-header（左「今日课程」13sp 600 + 右「共 N 节」12sp）。 */
@Composable
private fun ClaudeTimelineHeader(title: String, count: String) {
    val colors = appColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.02.em,
                lineHeight = 17.sp
            ),
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = count,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            ),
            color = colors.textSecondary.copy(alpha = 0.72f)
        )
    }
}

/**
 * 今日页三套主题时间轴课程卡的统一动效（v3.43.0 ·《交互动效审查_三主题》P0-3）。
 *
 * 修复两条动效断链：
 * - **入场**：三套 `*TimelineCard` 此前完全没有入场动画（`TodayContent` 三个主题分支提前
 *   `return@Column`，带入场 / 按压的 `CourseTimelineItem` 落进了不可达的兜底分支）。现改为
 *   错峰淡入 + 轻微上移；错峰量被 [MotionTokens.entranceStaggerCapMs] 钳制，满周 10 节课
 *   不再排队 1.2s+ 才出现。
 * - **「已结束」状态**：此前是 `graphicsLayer(alpha = if (isFinished) 0.6f else 1f)` 的二值硬跳，
 *   课程刚结束的一帧由 1f 直接跳到 0.6f；现按 [MotionTokens.statusFadeMs] 渐变。
 *
 * 按压反馈：通透（[MotionPressMode.SCALE]）在本函数里返回一个缩放值；柔绘 / 书卷不做任何
 * 形变（缩放会破坏软投影 / 羽化环与纸卡描边的静止感），反馈统一交给全局 `LocalIndication`
 * 以「浓度渗开 / 底色加深」表达。
 *
 * 返回的 [TodayCardMotion.interactionSource] 必须同时交给 `Modifier.clickable`，
 * 否则按压状态恒为 false（通透的缩放读不到按下）。
 */
private data class TodayCardMotion(
    val interactionSource: MutableInteractionSource,
    val alpha: Float,
    val scale: Float,
    val translationYPx: Float
)

@Composable
private fun rememberTodayCardMotion(index: Int, isFinished: Boolean): TodayCardMotion {
    val motion = LocalAppMotion.current
    val tokens = motion.tokens

    // 入场：rememberSaveable 记住 —— LazyColumn 会回收滚出视口的 item，普通 remember 会导致重播
    val entranceEnabled = motion.isEnabled(AnimationGroup.PAGE_ENTRANCE) && tokens.entranceDurationMs > 0
    var entered by rememberSaveable { mutableStateOf(!entranceEnabled) }
    val staggerMs = if (entranceEnabled) {
        (index * tokens.entranceStaggerMs).coerceAtMost(tokens.entranceStaggerCapMs)
    } else {
        0
    }
    LaunchedEffect(entranceEnabled) {
        if (!entered) {
            delay(staggerMs.toLong())
            entered = true
        }
    }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(tokens.entranceDurationMs, easing = tokens.entranceEasing),
        label = "todayCardEntrance"
    )
    val entranceFactor = if (entranceEnabled) entrance else 1f

    // 「已结束」状态渐变（取代二值硬跳）
    val finishedAlpha by animateFloatAsState(
        targetValue = if (isFinished) 0.6f else 1f,
        animationSpec = tween(tokens.statusFadeMs, easing = tokens.entranceEasing),
        label = "todayCardStatusFade"
    )

    // 通透：轻微缩放按压；柔绘 / 书卷零形变（交由全局 LocalIndication）
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressTarget = if (pressed && motion.profile.pressMode == MotionPressMode.SCALE) {
        tokens.cellPressScale
    } else {
        1f
    }
    val pressScale by animateFloatAsState(
        targetValue = pressTarget,
        animationSpec = tokens.cellPressSpec,
        label = "todayCardPressScale"
    )

    val initialAlpha = tokens.entranceInitialAlpha
    val translationPx = with(LocalDensity.current) {
        (tokens.entranceSlideDp * (1f - entranceFactor)).toPx()
    }
    return TodayCardMotion(
        interactionSource = interactionSource,
        alpha = finishedAlpha * (initialAlpha + (1f - initialAlpha) * entranceFactor),
        scale = pressScale,
        translationYPx = translationPx
    )
}

/** 时间轴条目：设计稿 .timeline-item（左时间列 56dp + 圆点连线 + 右侧课程卡）。 */
@Composable
private fun ClaudeTimelineItem(
    model: CourseDisplayModel,
    index: Int,
    isLast: Boolean,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    onClick: () -> Unit
) {
    val colors = appColors()
    val palette = claudeTimelinePalette(peach = index % 2 == 1)
    val isFinished = isClaudeCourseFinished(model, now)
    val isCurrent = !isFinished && isClaudeCourseOngoing(model, now)
    val startTime = model.startTime?.takeIf { it.isNotBlank() }
        ?: claudeTimeRange(model).substringBefore(" - ")

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.width(56.dp).fillMaxHeight()) {
            Text(
                text = startTime,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp
                ),
                color = colors.textPrimary,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = 30.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(colors.divider)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = 18.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) palette.dot else colors.pageBg)
                    .border(2.5.dp, palette.dot, CircleShape)
            )
        }

        ClaudeTimelineCard(
            model = model,
            index = index,
            palette = palette,
            gridStyle = gridStyle,
            isFinished = isFinished,
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 时间轴课程卡：设计稿 .timeline-card（20dp 圆角 + 渐变底 + 名称 + N 节徽标 + 元信息 + 信息胶囊）。 */
@Composable
private fun ClaudeTimelineCard(
    model: CourseDisplayModel,
    index: Int,
    palette: ClaudeTimelinePalette,
    gridStyle: ScheduleGridStyle,
    isFinished: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    val shape = RoundedCornerShape(20.dp)
    val sectionCount = claudeSectionCount(model)
    val cardMotion = rememberTodayCardMotion(index, isFinished)
    Box(
        modifier = modifier
            .graphicsLayer(
                alpha = cardMotion.alpha,
                scaleX = cardMotion.scale,
                scaleY = cardMotion.scale,
                translationY = cardMotion.translationYPx
            )
            .shadow(
                elevation = 1.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow
            )
            .clip(shape)
            .background(Brush.linearGradient(palette.gradient))
            .border(1.dp, palette.border, shape)
            .clickable(
                interactionSource = cardMotion.interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = model.course.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp,
                        textDecoration = if (isFinished) TextDecoration.LineThrough else null
                    ),
                    color = palette.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(palette.badgeBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(
                            Res.string.today_claude_sections_format,
                            sectionCount.toString()
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 13.sp
                        ),
                        color = palette.badgeFg
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!gridStyle.hideLocation && model.course.position.isNotBlank()) {
                    ClaudeMetaRow(
                        icon = Res.drawable.location_on_24px,
                        text = model.course.position,
                        tint = palette.meta
                    )
                }
                if (!gridStyle.hideTeacher && model.course.teacher.isNotBlank()) {
                    ClaudeMetaRow(
                        icon = Res.drawable.person_24px,
                        text = model.course.teacher,
                        tint = palette.meta
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ClaudeNotePill(
                    label = stringResource(Res.string.today_claude_note_type),
                    value = stringResource(
                        if (model.course.isLab) {
                            Res.string.today_claude_type_lab
                        } else {
                            Res.string.today_claude_type_theory
                        }
                    )
                )
                ClaudeNotePill(
                    label = stringResource(Res.string.today_claude_note_hours),
                    value = sectionCount.toString()
                )
            }
        }
    }
}

/** 信息胶囊：设计稿 .note-pill（bg-100 底 + border-200 描边 + 标签加粗 + 值常规）。 */
@Composable
private fun ClaudeNotePill(label: String, value: String) {
    val colors = appColors()
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.textSecondary)) {
                append(label)
            }
            append("：")
            append(value)
        },
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 13.sp
        ),
        color = colors.textSecondary.copy(alpha = 0.85f),
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.pageBg)
            .border(1.dp, colors.divider, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** 元信息行：13dp 图标 + 12sp 文字（设计稿 .course-meta-item）。 */
@Composable
private fun ClaudeMetaRow(
    icon: DrawableResource,
    text: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = tint.copy(alpha = 0.85f)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            ),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 下一节课 hero 卡：设计稿 .next-class-card（丁香紫渐变 + 时间徽标 + 倒计时）。 */
@Composable
private fun ClaudeNextClassCard(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    onClick: () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val labelColor = if (isDarkTheme) Color(0xFFC4B8E8) else ClaudeLilac700
    val nameColor = if (isDarkTheme) Color(0xFFEBE5FF) else ClaudeLilac800
    val badgeBg = if (isDarkTheme) Color(0x1FFFFFFF) else Color(0x99FFFFFF)
    val gradient = if (isDarkTheme) {
        listOf(Color(0xFF3A2F5C), Color(0xFF2E2749))
    } else {
        listOf(ClaudeLilac100, ClaudeLilac50)
    }
    val border = if (isDarkTheme) Color(0x339C87F5) else Color(0x269C87F5)
    val shape = RoundedCornerShape(28.dp)
    val minutesUntil = remember(model, now) { claudeMinutesUntil(model, now) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = shape,
                clip = false,
                ambientColor = ClaudeLilac500.copy(alpha = 0.25f),
                spotColor = ClaudeLilac500.copy(alpha = 0.25f)
            )
            .clip(shape)
            .background(Brush.linearGradient(gradient))
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(ClaudeLilac500)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(Res.string.today_claude_next_label),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.08.em,
                            lineHeight = 12.sp
                        ),
                        color = labelColor
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = model.course.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 27.sp,
                        letterSpacing = (-0.01).em
                    ),
                    color = nameColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (!gridStyle.hideLocation && model.course.position.isNotBlank()) {
                        ClaudeMetaRow(
                            icon = Res.drawable.location_on_24px,
                            text = model.course.position,
                            tint = labelColor
                        )
                    }
                    if (!gridStyle.hideTeacher && model.course.teacher.isNotBlank()) {
                        ClaudeMetaRow(
                            icon = Res.drawable.person_24px,
                            text = model.course.teacher,
                            tint = labelColor
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.schedule_24px),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = nameColor
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = claudeTimeRange(model),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp
                        ),
                        color = nameColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (minutesUntil != null && minutesUntil > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = minutesUntil.toString(),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 26.sp,
                                letterSpacing = (-0.02).em
                            ),
                            color = nameColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(Res.string.today_claude_countdown_label),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 13.sp
                            ),
                            color = labelColor,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun claudeSectionCount(model: CourseDisplayModel): Int {
    val start = model.course.startSection
    val end = model.course.endSection
    return if (start != null && end != null) (end - start + 1).coerceAtLeast(1) else 1
}

private fun claudeMinutesUntil(model: CourseDisplayModel, now: LocalTime): Int? {
    val startText = model.startTime?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val start = LocalTime.parse(startText)
        (start.toSecondOfDay() - now.toSecondOfDay()) / 60
    } catch (e: Exception) {
        null
    }
}

/** 明日预览卡片：设计稿 .tomorrow-card（凹陷卡 + 4dp 色条 + 名称 + 时间·地点）。 */
@Composable
private fun ClaudeTomorrowCard(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean
) {
    val colors = appColors()
    val accent = claudeAccentColor(model, gridStyle, isDark)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(appShapes().card)
            .background(colors.cardBg)
            .border(1.dp, colors.divider, appShapes().card)
    ) {
        Box(
            modifier = Modifier
                .width(ClaudeTomorrowAccentWidth)
                .fillMaxHeight()
                .background(accent)
        )
        Column(modifier = Modifier.weight(1f).padding(14.dp)) {
            Text(
                text = model.course.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = claudeTimeRange(model),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = colors.textSecondary
                )
                if (!gridStyle.hideLocation && model.course.position.isNotBlank()) {
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = colors.divider
                    )
                    Text(
                        text = model.course.position,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 今日日程事件区段：来自「日程」页新建的日程，展示在课程时间轴之后。 */
@Composable
private fun ClaudeEventsSection(events: List<ScheduleEvent>) {
    val colors = appColors()
    Spacer(modifier = Modifier.height(8.dp))
    ClaudeSectionLabelRow(
        label = stringResource(Res.string.title_today_schedule),
        trailing = stringResource(Res.string.agenda_count_format, events.size.toString()),
        fillWidth = true
    )
    Spacer(modifier = Modifier.height(6.dp))
    events.forEachIndexed { index, event ->
        ClaudeEventRow(event = event, index = index, isLast = index == events.lastIndex)
        if (index < events.lastIndex) Spacer(modifier = Modifier.height(6.dp))
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ClaudeEventRow(
    event: ScheduleEvent,
    index: Int,
    isLast: Boolean
) {
    val colors = appColors()
    val categoryColor = when (ScheduleCategory.fromKey(event.category)) {
        ScheduleCategory.TODO -> Color(0xFF4CAF50)
        ScheduleCategory.ACTIVITY -> Color(0xFFFF9800)
        ScheduleCategory.EXAM -> Color(0xFFF44336)
        ScheduleCategory.HOMEWORK -> Color(0xFF2196F3)
        ScheduleCategory.OTHER -> colors.textSecondary
    }
    val metaLine = listOfNotNull(
        stringResource(when (ScheduleCategory.fromKey(event.category)) {
            ScheduleCategory.TODO -> Res.string.agenda_category_todo
            ScheduleCategory.ACTIVITY -> Res.string.agenda_category_activity
            ScheduleCategory.EXAM -> Res.string.agenda_category_exam
            ScheduleCategory.HOMEWORK -> Res.string.agenda_category_homework
            ScheduleCategory.OTHER -> Res.string.agenda_category_other
        }),
        event.location
    ).joinToString(" · ")

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 左侧时间占位（与课程时间列对齐）
        Box(modifier = Modifier.width(56.dp).fillMaxHeight()) {
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = 18.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(colors.divider)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = 6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(categoryColor)
            )
        }
        // 右侧内容卡
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    ),
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (metaLine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 14.sp
                        ),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                event.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 14.sp
                        ),
                        color = colors.textSecondary.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 幽灵小按钮：设计稿 .btn.ghost.view-all-btn（32dp 高 + 12sp + chevron）。 */
@Composable
private fun ClaudeGhostButton(text: String, onClick: () -> Unit) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .clip(appShapes().chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = vectorResource(Res.drawable.chevron_right_24px),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = colors.textPrimary
        )
    }
}

/** 课程详情弹层：字段与设计包 pages/今日日程.html 的详情弹层一致。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClaudeCourseDetailSheet(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val colors = appColors()
    val isFinished = isClaudeCourseFinished(model, now)
    val isCurrent = !isFinished && isClaudeCourseOngoing(model, now)
    val statusText = stringResource(
        when {
            isCurrent -> Res.string.today_claude_status_live
            isFinished -> Res.string.today_claude_status_done
            else -> Res.string.today_claude_status_upcoming
        }
    )

    AppGlassBottomSheet(
        hazeState = null,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 40.dp)
        ) {
            Text(
                text = statusText,
                style = TextStyle(
                    fontFamily = claudeReadingSerif(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.1.em
                ),
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = model.course.name,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(appShapes().chip)
                    .border(1.dp, colors.divider, appShapes().chip)
            ) {
                ClaudeDetailRow(
                    label = stringResource(Res.string.today_claude_meta_time),
                    value = claudeTimeRange(model)
                )
                HorizontalDivider(thickness = 1.dp, color = colors.divider)
                if (model.course.position.isNotBlank()) {
                    ClaudeDetailRow(
                        label = stringResource(Res.string.today_claude_meta_room),
                        value = model.course.position
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                if (model.course.teacher.isNotBlank()) {
                    ClaudeDetailRow(
                        label = stringResource(Res.string.today_claude_meta_teacher),
                        value = model.course.teacher
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                model.course.credit?.takeIf { it.isNotBlank() }?.let { credit ->
                    ClaudeDetailRow(
                        label = stringResource(Res.string.today_claude_meta_credit),
                        value = credit
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(appShapes().chip)
                        .background(colors.inputBg)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.today_claude_sheet_close),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.textPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(appShapes().chip)
                        .background(colors.primary)
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.today_claude_sheet_edit),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.textOnPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ClaudeDetailRow(label: String, value: String) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cardBgElevated)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.textPrimary,
            textAlign = TextAlign.End
        )
    }
}

/** 时间区间文本：优先自定义时间，否则用节次时间。 */
private fun claudeTimeRange(model: CourseDisplayModel): String {
    val start = model.startTime?.takeIf { it.isNotBlank() }
    val end = model.endTime?.takeIf { it.isNotBlank() }
    return when {
        start != null && end != null -> "$start - $end"
        start != null -> start
        end != null -> end
        else -> EMPTY_TIME_PLACEHOLDER
    }
}

private fun isClaudeCourseFinished(model: CourseDisplayModel, now: LocalTime): Boolean {
    val endText = model.endTime?.takeIf { it.isNotBlank() } ?: return false
    return try {
        LocalTime.parse(endText) < now
    } catch (e: Exception) {
        false
    }
}

private fun isClaudeCourseOngoing(model: CourseDisplayModel, now: LocalTime): Boolean {
    val startText = model.startTime?.takeIf { it.isNotBlank() } ?: return false
    val endText = model.endTime?.takeIf { it.isNotBlank() } ?: return false
    return try {
        val start = LocalTime.parse(startText)
        val end = LocalTime.parse(endText)
        now >= start && now < end
    } catch (e: Exception) {
        false
    }
}

/**
 * 课程色条颜色：设计稿用 `--chart-1…5` 实色作色条。
 * CLAUDE 课表样式的 light 档是 12% 透明度的 chart 色（等价于设计稿实色叠在卡片白底上的观感），
 * dark 档是 chart 实色，正好对应设计稿浅色 / 深色两套写法。
 */
private fun claudeAccentColor(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean
): Color {
    val pair = gridStyle.courseColorMaps.getOrElse(model.course.colorInt) {
        ScheduleGridStyle.DEFAULT_COLOR_MAPS[0]
    }
    return if (isDark) pair.dark else pair.light
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
    // 待办采用固定青色系，与课程配色区分；渲染逻辑与课程条（CourseTimelineItem）保持一致
    val colorPair = DualColor(light = Color(0xFFB2EBF2), dark = Color(0xFF0097A7))
    val themeColor = if (isDark) colorPair.dark else colorPair.light
    val textColor = gridStyle.courseTextColorLong?.let { Color(it) }
        ?: adaptiveTextColor(themeColor, MaterialTheme.colorScheme.onSurface)

    val cornerRadius = gridStyle.courseBlockCornerRadiusDp.dp
    val shape = RoundedCornerShape(cornerRadius)
    // 云舒主题已删除，其专属投影分支不再存在；通透与书卷的课程块均不加投影
    val cardShadowModifier = Modifier

    // 与课程条一致的边框样式
    val borderColor = appColors().divider
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
    val blockAlpha = if (todo.done) 0.5f else gridStyle.courseBlockAlphaFloat

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
                        fontSize = appType().timeLabel,
                        textDecoration = if (todo.done) TextDecoration.LineThrough else null
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        val itemStripDrawModifier = Modifier
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
            val nameFontSize = (13f * gridStyle.courseBlockFontScale).sp
            val metaFontSize = (10f * gridStyle.courseBlockFontScale).sp
            Column(
                modifier = Modifier.padding(
                    start = innerPadding,
                    top = innerPadding,
                    end = innerPadding,
                    bottom = innerPadding
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 复选框放在色块内容最前
                    AppCheckboxIndicator(
                        checked = todo.done,
                        modifier = Modifier.clickable {
                            onToggle(todo.id, !todo.done)
                        }
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
    onDeleteRequest: () -> Unit,
    hazeState: HazeState? = null
) {
    var title by remember(existing) { mutableStateOf(existing?.title ?: "") }
    var time by remember(existing) { mutableStateOf(existing?.time ?: "") }
    var note by remember(existing) { mutableStateOf(existing?.note ?: "") }
    var showTimePicker by remember { mutableStateOf(false) }

    AppAlertDialog(
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
                AppTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(Res.string.todo_title_label),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                // 时间选择式：点击输入框弹出 TimePicker，右侧 × 清除已选时间
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppTextField(
                            value = time,
                            onValueChange = {},
                            label = stringResource(Res.string.todo_time_label),
                            placeholder = stringResource(Res.string.todo_time_label),
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = if (time.isNotBlank()) {
                                {
                                    IconButton(onClick = { time = "" }) {
                                        Icon(
                                            vectorResource(Res.drawable.close_24px),
                                            contentDescription = stringResource(Res.string.action_cancel),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showTimePicker = true }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = stringResource(Res.string.todo_note_label),
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            AppDialogActions(
                confirmText = stringResource(Res.string.action_confirm),
                onConfirm = {
                    if (title.isNotBlank()) {
                        onConfirm(
                            title.trim(),
                            note.trim().takeIf { it.isNotBlank() },
                            time.trim().takeIf { it.isNotBlank() }
                        )
                    }
                },
                dismissText = stringResource(Res.string.action_cancel),
                onDismiss = onDismiss
            )
        },
        dismissButton = {}
    )

    if (showTimePicker) {
        TodoTimePickerSheet(
            initialTime = time,
            onDismissRequest = { showTimePicker = false },
            onTimeSelected = { time = it },
            hazeState = hazeState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoTimePickerSheet(
    initialTime: String?,
    onDismissRequest: () -> Unit,
    onTimeSelected: (String) -> Unit,
    hazeState: HazeState? = null
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

    // 毛玻璃面板：实色内容列由 AppGlassBottomSheet 统一处理
    AppGlassBottomSheet(
        hazeState = hazeState,
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
            // 主色胶囊确认钮（AppDialogActions，与其他弹窗操作区同语言）
            AppDialogActions(
                confirmText = stringResource(Res.string.action_confirm),
                onConfirm = {
                    val hh = hour.toString().padStart(2, '0')
                    val mm = minute.toString().padStart(2, '0')
                    onTimeSelected("$hh:$mm")
                    onDismissRequest()
                }
            )
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
    // 危险操作统一走 AppDangerDialog：危险色胶囊确认钮（v2 规范 §4 对话框统一）
    AppDangerDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.todo_delete_title),
        text = stringResource(Res.string.todo_delete_message, todo.title),
        confirmText = stringResource(Res.string.confirm_delete),
        onConfirm = onConfirm,
        dismissText = stringResource(Res.string.action_cancel),
        onDismiss = onDismiss
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
        // 今日课程已结束提示卡：基线白卡样式
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.text_courses_finished),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = appColors().textSecondary
            )
        }
        return
    }

    val colorPair = gridStyle.courseColorMaps.getOrElse(target.course.colorInt) {
        ScheduleGridStyle.DEFAULT_COLOR_MAPS[0]
    }

    val themeColor = if (isDark) colorPair.dark else colorPair.light
    val textColor = gridStyle.courseTextColorLong?.let { Color(it) } ?: adaptiveTextColor(themeColor, MaterialTheme.colorScheme.onSurface)

    val start = parseOrNull(target.startTime)
    val end = parseOrNull(target.endTime)

    val cornerRadius = gridStyle.courseBlockCornerRadiusDp.dp
    val shape = RoundedCornerShape(cornerRadius)
    // 云舒主题已删除，其专属投影分支不再存在；通透与书卷的课程块均不加投影
    val shadowModifier = Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(shadowModifier)
            .clip(shape)
            .background(color = themeColor)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = target.course.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = appType().hero),
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
                            .background(textColor.copy(alpha = 0.15f), MaterialTheme.shapes.extraSmall)
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
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = appType().hero),
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
    now: LocalTime,
    entranceDelayMs: Int = 0
) {
    // 用 TodayContent 里每分钟跳动的 now，而不是组合期固定快照，
    // 否则「已结束」状态（删除线/透明度）在页面停留期间永不更新。
    val isFinished = remember(model.endTime, now) {
        val endText = model.endTime?.takeIf { it.isNotBlank() }
        if (endText == null) {
            false
        } else {
            try {
                LocalTime.parse(endText) < now
            } catch (e: Exception) { false }
        }
    }

    val colorPair = gridStyle.courseColorMaps.getOrElse(model.course.colorInt) {
        ScheduleGridStyle.DEFAULT_COLOR_MAPS[0]
    }

    val themeColor = if (isDark) colorPair.dark else colorPair.light
    val textColor = gridStyle.courseTextColorLong?.let { Color(it) } ?: adaptiveTextColor(themeColor, MaterialTheme.colorScheme.onSurface)

    val cornerRadius = gridStyle.courseBlockCornerRadiusDp.dp
    val shape = RoundedCornerShape(cornerRadius)
    // 云舒主题已删除，其专属投影分支不再存在；通透与书卷的课程块均不加投影
    val cardShadowModifier = Modifier

    // 与主课表 CourseBlock 一致：边框样式 + 课程块透明度
    val borderColor = appColors().divider
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
    // 已结束课程整体降透明
    val blockAlpha = if (isFinished) 0.5f else gridStyle.courseBlockAlphaFloat

    // v3.26.0 C+.15 今日页课程卡点按反馈：按压缩放（读全局令牌；
    // 关掉「课程格反馈」分组 ⇒ snap 到原样）。今日页课程卡此前纯展示无任何反馈。
    val cellMotion = LocalAppMotion.current
    var cellPressed by remember { mutableStateOf(false) }
    val cellPressFraction by animateFloatAsState(
        targetValue = if (cellPressed) 1f else 0f,
        animationSpec = cellMotion.tokens.cellPressSpec,
        label = "todayCellPress"
    )

    // v3.26.0 C+.17 今日页入场错峰淡入：rememberSaveable 记住——LazyColumn 回收
    // 滚出视口的 item，普通 remember 会导致滚回来重播；关掉「页面入场」⇒ 直接显示
    val entranceEnabled = cellMotion.isEnabled(AnimationGroup.PAGE_ENTRANCE) &&
        cellMotion.tokens.entranceDurationMs > 0
    var entranceEntered by rememberSaveable { mutableStateOf(!entranceEnabled) }
    LaunchedEffect(Unit) {
        if (!entranceEntered) {
            delay(entranceDelayMs.toLong())
            entranceEntered = true
        }
    }
    val entranceFraction by animateFloatAsState(
        targetValue = if (entranceEntered) 1f else 0f,
        animationSpec = tween(
            durationMillis = cellMotion.tokens.entranceDurationMs,
            easing = cellMotion.tokens.entranceEasing
        ),
        label = "todayCardEntrance"
    )
    val entranceSlidePx = with(LocalDensity.current) { cellMotion.tokens.entranceSlideDp.toPx() }

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
                    fontSize = appType().timeLabel,
                    textDecoration = if (isFinished) TextDecoration.LineThrough else null
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = model.endTime ?: EMPTY_TIME_PLACEHOLDER,
                style = MaterialTheme.typography.labelSmall,
                color = appColors().divider
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            val itemStripDrawModifier = Modifier
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = (blockAlpha * entranceFraction).coerceIn(0f, 1f)
                        val pressScale =
                            1f + (cellMotion.tokens.cellPressScale - 1f) * cellPressFraction
                        scaleX = pressScale
                        scaleY = pressScale
                        translationY = entranceSlidePx * (1f - entranceFraction)
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                cellPressed = true
                                try {
                                    awaitRelease()
                                } finally {
                                    cellPressed = false
                                }
                            }
                        )
                    }
                    .then(cardShadowModifier)
                    .then(borderModifier)
                    .clip(shape)
                    .background(color = themeColor)
                    .then(itemStripDrawModifier)
            ) {
                // 与主课表 CourseBlock 一致的内边距和字号
                val innerPadding = gridStyle.courseBlockInnerPaddingDp.dp
                val nameFontSize = (13f * gridStyle.courseBlockFontScale).sp
                val metaFontSize = (10f * gridStyle.courseBlockFontScale).sp
                val horizontalAlignment = if (gridStyle.textAlignCenterHorizontal) Alignment.CenterHorizontally else Alignment.Start
                val textAlign = if (gridStyle.textAlignCenterHorizontal) TextAlign.Center else TextAlign.Start
                Column(
                    modifier = Modifier.padding(
                        start = innerPadding,
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
                // 备注卡：聊天气泡形（18dp 圆角 + 左上 6dp 收尾，v2 规范 §4.6）
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, start = 4.dp)
                        .fillMaxWidth()
                        .background(
                            color = appColors().inputBg,
                            shape = appShapes().bubble
                        )
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(Res.string.label_remark),
                            style = MaterialTheme.typography.labelSmall,
                            color = appColors().divider,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = remark,
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * iOS 风格分区大标题：22sp Bold + 13sp 次级文字
 * 对齐画布 .section-header（今日课程 / 共 X 节）
 */
@Composable
private fun IosSectionTitle(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.01).sp
            ),
            color = appColors().textPrimary
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = appColors().textSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * iOS 风格小组标题：13sp Semibold uppercase + 主色链接
 * 对齐画布 .group-header（明日课程）
 */
@Composable
private fun IosGroupHeader(
    title: String,
    action: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            ),
            color = appColors().textSecondary
        )
        if (action != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = action,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = appColors().primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
@Composable
private fun IosCourseGroup(
    courses: List<CourseDisplayModel>,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    showFinishedState: Boolean = true
) {
    // iOS 风格：扁平列表，每行课程左侧色条 + 内容 + 右箭头，行间半透明分隔线
    Column(modifier = Modifier.fillMaxWidth()) {
        courses.forEachIndexed { index, model ->
            IosCourseCard(
                model = model,
                gridStyle = gridStyle,
                isDark = isDark,
                now = now,
                showFinishedState = showFinishedState
            )
            if (index != courses.lastIndex) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = appColors().divider,
                    modifier = Modifier.padding(start = 56.dp)
                )
            }
        }
    }
}

@Composable
private fun IosCourseCard(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    showFinishedState: Boolean = true
) {
    val isFinished = if (showFinishedState) {
        remember(model.endTime, now) {
            val endText = model.endTime?.takeIf { it.isNotBlank() }
            if (endText == null) {
                false
            } else {
                try {
                    LocalTime.parse(endText) < now
                } catch (e: Exception) { false }
            }
        }
    } else false

    val colorPair = gridStyle.courseColorMaps.getOrElse(model.course.colorInt) {
        ScheduleGridStyle.DEFAULT_COLOR_MAPS[0]
    }

    // iOS 风格：背景色 = 系统色 12% 透明度，色条色 = 系统色
    val bgColor = colorPair.dark.copy(alpha = if (isDark) 0.2f else 0.12f)
    val accentColor = colorPair.dark
    val textPrimary = appColors().textPrimary
    val textSecondary = appColors().textSecondary

    val contentAlpha = if (isFinished) 0.5f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = contentAlpha)
            .padding(end = 8.dp)
            .clickable { /* 预留点击跳转 */ },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧色条：4dp 宽，上下各留 8dp 间距
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(4.dp)
                .height(40.dp)
                .background(
                    color = accentColor,
                    shape = RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)
                )
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 中间内容区
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 14.dp)
        ) {
            // 顶部：课程名 + 时间徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = model.course.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp
                    ),
                    color = textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val timeText = listOfNotNull(
                    model.startTime?.takeIf { it.isNotBlank() },
                    model.endTime?.takeIf { it.isNotBlank() }
                ).joinToString("-")
                if (timeText.isNotBlank()) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = textSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 元信息：地点 + 教师
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!gridStyle.hideLocation && model.course.position.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.location_on_24px),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = textSecondary.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = model.course.position,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!gridStyle.hideTeacher && model.course.teacher.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.person_24px),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = textSecondary.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = model.course.teacher,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 右侧 chevron
        Icon(
            imageVector = vectorResource(Res.drawable.chevron_right_24px),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = appColors().divider
        )
    }
}

@Composable
private fun EmptyStateView() {
    // 空态：淡灰胶囊底 + 课程格纸母题插画 + 居中辅助文案（v2 规范 §3「空态插画底统一」）。
    // v3.43.0：补主题化入场——此前空态整页瞬现，与课程列表的错峰淡入不同步。
    val appear = rememberTodayCardMotion(index = 0, isFinished = false)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer(alpha = appear.alpha, translationY = appear.translationYPx)
                .clip(appShapes().card)
                .background(appColors().inputBg.copy(alpha = 0.55f))
                .padding(horizontal = 28.dp, vertical = 22.dp)
        ) {
            // 空态母题：迷你课程格纸（主色 tint，静态无呼吸）
            AppHeroMotif(
                tint = appColors().primary,
                pulse = false,
                blockWidth = 40.dp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Text(
                text = stringResource(Res.string.text_no_courses_today),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors().textSecondary
            )
        }
    }
}

// ===== 通透主题（iOS 26）· 今日页专用组件 =====

// ===== 柔绘主题 · 今日页专用组件 =====
// ============================================================================
// 柔绘主题 · 今日课表页
//
// 信息架构与「书卷」主题逐项一致（页头日期/周次 → 下节课卡 → 今日课程时间轴
//   → 日程事件 → 明日预览 → 课程详情玻璃面板），位置、间距节奏、字段集合完全不变。
//
// 视觉按 iOS 26「Liquid Glass」重做：
//   · 卡片 = 白卡（深色 #1C1C1E）+ 16~20dp 连续圆角 + 玻璃高光内描边，代替书卷的
//     暖色渐变 + 1dp 暖色描边
//   · 强调色 = systemBlue / systemIndigo / systemOrange / systemGreen / systemRed，
//     代替书卷的赤陶 + 紫罗兰双色交替
//   · 圆点与连线 = 更细的 1.5dp 环 + 系统蓝，进行中圆点带呼吸动画
//   · 时间轴左侧时间列、节点圆点、连线几何位置与书卷完全一致
//
// 状态栏 / 底部导航由系统与 App 底栏承担，不在页面内重复绘制。
// ============================================================================

/** 时间轴时间列宽 56dp；明日预览色条 4dp。 */
private val SoftTomorrowAccentWidth = 4.dp

// iOS 26 卡片渐变底（Apple 系统色的极淡档）：偶数行用系统蓝系，奇数行用系统靛系。
private val SoftAccentAlt50 = Color(0xFFF7F6FC)
private val SoftAccentAlt100 = Color(0xFFEDEBF7)
private val SoftAccentAlt500 = Color(0xFF9A93B8)
private val SoftAccentAlt700 = Color(0xFF5F5A80)
private val SoftAccentAlt800 = Color(0xFF4A4760)
private val SoftAccentWarm50 = Color(0xFFFBF7F2)
private val SoftAccentWarm500 = Color(0xFFD9A97E)
private val SoftAccentWarm700 = Color(0xFF9A7550)
private val SoftAccentWarm400 = Color(0xFFE0BB96)

private data class SoftTimelinePalette(
    val gradient: List<Color>,
    val border: Color,
    val title: Color,
    val meta: Color,
    val badgeBg: Color,
    val badgeFg: Color,
    val dot: Color
)

/**
 * 时间轴卡片配色（偶数行 = 系统蓝系，奇数行 = 系统橙系）。
 *
 * 与书卷同结构（两个交替色系 + 深浅两套），色相换成 Apple 系统色：
 * 书卷是「紫罗兰 / 赤陶」暖冷交替，通透换成「系统蓝 / 系统橙」——
 * 这是 iOS 图标与图表的标准双色搭配，保证在浅色白卡与深色炭卡上都清晰。
 */
@Composable
private fun softTimelinePalette(peach: Boolean): SoftTimelinePalette {
    val colors = appColors()
    val isDark = LocalIsDarkTheme.current
    return when {
        peach && isDark -> SoftTimelinePalette(
            gradient = listOf(Color(0xFF2E2820), Color(0xFF272219)),
            border = Color(0x33E0BB96),
            title = Color(0xFFE6C9A8),
            meta = Color(0xFFBFAE96),
            badgeBg = Color(0x3DE0BB96),
            badgeFg = Color(0xFFE6C9A8),
            dot = Color(0xFFD9A97E)
        )

        peach -> SoftTimelinePalette(
            gradient = listOf(Color(0xFFFFFBF5), SoftAccentWarm50),
            // 柔绘暖调描边：用柔绘警示色 #D9A97E（此处曾误用 iOS 系统橙 #FF9500）
            border = Color(0x1FD9A97E),
            title = colors.textPrimary,
            meta = colors.textSecondary,
            badgeBg = Color(0x1FD9A97E),
            badgeFg = SoftAccentWarm700,
            dot = SoftAccentWarm500
        )

        isDark -> SoftTimelinePalette(
            gradient = listOf(Color(0xFF262431), Color(0xFF201E28)),
            border = Color(0x339AA3DC),
            title = Color(0xFFC3C8EC),
            meta = Color(0xFFA29FB4),
            badgeBg = Color(0x3D9AA3DC),
            badgeFg = Color(0xFFC3C8EC),
            dot = Color(0xFF9AA3DC)
        )

        else -> SoftTimelinePalette(
            gradient = listOf(Color(0xFFFBFDFF), SoftAccentAlt50),
            border = Color(0x1F7C86C9),
            title = colors.textPrimary,
            meta = colors.textSecondary,
            badgeBg = Color(0x1F7C86C9),
            badgeFg = SoftAccentAlt700,
            dot = Color(0xFF7C86C9)
        )
    }
}

@Composable
private fun SoftTodayContent(
    state: TodayUiState.Success,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    bottomInset: Dp,
    statusText: String,
    dateText: String,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    onOpenWeeklySchedule: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditCourse: (String) -> Unit
) {
    val colors = appColors()
    var detailCourse by remember { mutableStateOf<CourseDisplayModel?>(null) }

    val nextCourse = remember(state.courses, now) {
        val upcoming = state.courses.firstOrNull { model ->
            val start = model.startTime?.takeIf { it.isNotBlank() }?.let {
                runCatching { LocalTime.parse(it) }.getOrNull()
            }
            start != null && start > now
        }
        upcoming ?: state.courses.firstOrNull { !isSoftCourseFinished(it, now) }
    }
    val tomorrowDate = stringResource(
        Res.string.today_ios_tomorrow_format,
        state.today.month.number,
        state.today.day
    )

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = bottomInset + 100.dp)
    ) {
        item {
            SoftTodayHeader(
                weekIndex = state.weekIndex,
                status = state.status,
                statusText = statusText,
                dateText = dateText,
            )
        }

        if (nextCourse != null) {
            item {
                SoftNextClassCard(
                    model = nextCourse,
                    gridStyle = gridStyle,
                    isDark = isDark,
                    now = now,
                    onClick = { detailCourse = nextCourse }
                )
            }
        }

        item {
            SoftTimelineHeader(
                title = stringResource(Res.string.title_today_courses),
                count = stringResource(Res.string.text_courses_count, state.courses.size.toString())
            )
        }

        if (state.courses.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.text_no_courses_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            itemsIndexed(
                state.courses,
                key = { _, model -> model.course.id }
            ) { index, model ->
                SoftTimelineItem(
                    model = model,
                    index = index,
                    isLast = index == state.courses.lastIndex,
                    gridStyle = gridStyle,
                    isDark = isDark,
                    now = now,
                    onClick = { detailCourse = model }
                )
            }
        }

        // 今日日程事件（来自「日程」页新建的日程）
        if (state.events.isNotEmpty()) {
            item {
                SoftEventsSection(events = state.events)
            }
        }

        if (state.tomorrowCourses.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SoftSectionLabelRow(
                            label = tomorrowDate,
                            trailing = stringResource(
                                Res.string.text_courses_count,
                                state.tomorrowCourses.size.toString()
                            ),
                            fillWidth = false
                        )
                    }
                    SoftGhostButton(
                        text = stringResource(Res.string.today_ios_view_all),
                        onClick = onOpenWeeklySchedule
                    )
                }
            }
            itemsIndexed(
                state.tomorrowCourses,
                key = { _, model -> "tomorrow-${model.course.id}" }
            ) { _, model ->
                SoftTomorrowCard(model = model, gridStyle = gridStyle, isDark = isDark)
            }
        }
    }

    detailCourse?.let { model ->
        SoftCourseDetailSheet(
            model = model,
            gridStyle = gridStyle,
            isDark = isDark,
            now = now,
            onDismiss = { detailCourse = null },
            onEdit = {
                detailCourse = null
                onEditCourse(model.course.id)
            }
        )
    }
}

/** 页头：周次胶囊置于左侧 + 日期居中大字，同一行。 */
@Composable
private fun SoftTodayHeader(
    weekIndex: Int,
    status: TodayStatus,
    statusText: String,
    dateText: String,
) {
    val colors = appColors()
    val weekLabel = if (status == TodayStatus.Normal) {
        stringResource(Res.string.title_current_week, weekIndex.toString())
    } else {
        statusText
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 12.dp)
    ) {
        // 周次胶囊：方形块，靠左
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.primarySoft)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = weekLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.02.em,
                    lineHeight = 16.sp
                ),
                color = colors.primary,
                maxLines = 1
            )
        }
        // 日期：行内居中大字
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp,
                letterSpacing = (-0.01).em
            ),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 84.dp)
        )
    }
}

/** 分区标题行：Lora 12sp 大写字距标签 + 右侧辅助文案。 */
@Composable
private fun SoftSectionLabelRow(
    label: String,
    trailing: String?,
    fillWidth: Boolean = true
) {
    val colors = appColors()
    Row(
        modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = iosUiSans(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.14.em,
                lineHeight = 16.sp
            ),
            color = colors.textSecondary
        )
        if (trailing != null) {
            if (fillWidth) Spacer(modifier = Modifier.weight(1f))
            else Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                ),
                color = colors.textSecondary
            )
        }
    }
}

/** 时间轴标题：设计稿 .timeline-header（左「今日课程」13sp 600 + 右「共 N 节」12sp）。 */
@Composable
private fun SoftTimelineHeader(title: String, count: String) {
    val colors = appColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.02.em,
                lineHeight = 17.sp
            ),
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = count,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            ),
            color = colors.textSecondary.copy(alpha = 0.72f)
        )
    }
}

/** 时间轴条目：设计稿 .timeline-item（左时间列 56dp + 圆点连线 + 右侧课程卡）。 */
@Composable
private fun SoftTimelineItem(
    model: CourseDisplayModel,
    index: Int,
    isLast: Boolean,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    onClick: () -> Unit
) {
    val colors = appColors()
    val palette = softTimelinePalette(peach = index % 2 == 1)
    val isFinished = isSoftCourseFinished(model, now)
    val isCurrent = !isFinished && isSoftCourseOngoing(model, now)
    val startTime = model.startTime?.takeIf { it.isNotBlank() }
        ?: softTimeRange(model).substringBefore(" - ")

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.width(56.dp).fillMaxHeight()) {
            Text(
                text = startTime,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp
                ),
                color = colors.textPrimary,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = 30.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(colors.divider)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = 18.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) palette.dot else colors.pageBg)
                    .border(2.5.dp, palette.dot, CircleShape)
            )
        }

        SoftTimelineCard(
            model = model,
            index = index,
            palette = palette,
            gridStyle = gridStyle,
            isFinished = isFinished,
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 时间轴课程卡：设计稿 .timeline-card（20dp 圆角 + 渐变底 + 名称 + N 节徽标 + 元信息 + 信息胶囊）。 */
@Composable
private fun SoftTimelineCard(
    model: CourseDisplayModel,
    index: Int,
    palette: SoftTimelinePalette,
    gridStyle: ScheduleGridStyle,
    isFinished: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    val shape = appShapes().heroCard
    val sectionCount = softSectionCount(model)
    val cardMotion = rememberTodayCardMotion(index, isFinished)
    Box(
        modifier = modifier
            .graphicsLayer(
                alpha = cardMotion.alpha,
                scaleX = cardMotion.scale,
                scaleY = cardMotion.scale,
                translationY = cardMotion.translationYPx
            )
            .softShadow(shape = shape, elevation = 8.dp)
            .clip(shape)
            .background(Brush.linearGradient(palette.gradient))
            // iOS 26：卡片底色本身已是极淡系统色，描边改为玻璃高光内描边
            // （书卷是 1dp 暖色实边框；玻璃描边与 Liquid Glass 语言一致）
            .softFeatherRim(shape)
            .clickable(
                interactionSource = cardMotion.interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = model.course.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp,
                        textDecoration = if (isFinished) TextDecoration.LineThrough else null
                    ),
                    color = palette.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(palette.badgeBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(
                            Res.string.today_ios_sections_format,
                            sectionCount.toString()
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 13.sp
                        ),
                        color = palette.badgeFg
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!gridStyle.hideLocation && model.course.position.isNotBlank()) {
                    SoftMetaRow(
                        icon = Res.drawable.location_on_24px,
                        text = model.course.position,
                        tint = palette.meta
                    )
                }
                if (!gridStyle.hideTeacher && model.course.teacher.isNotBlank()) {
                    SoftMetaRow(
                        icon = Res.drawable.person_24px,
                        text = model.course.teacher,
                        tint = palette.meta
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SoftNotePill(
                    label = stringResource(Res.string.today_ios_note_type),
                    value = stringResource(
                        if (model.course.isLab) {
                            Res.string.today_ios_type_lab
                        } else {
                            Res.string.today_ios_type_theory
                        }
                    )
                )
                SoftNotePill(
                    label = stringResource(Res.string.today_ios_note_hours),
                    value = sectionCount.toString()
                )
            }
        }
    }
}

/** 信息胶囊：设计稿 .note-pill（bg-100 底 + border-200 描边 + 标签加粗 + 值常规）。 */
@Composable
private fun SoftNotePill(label: String, value: String) {
    val colors = appColors()
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.textSecondary)) {
                append(label)
            }
            append("：")
            append(value)
        },
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 13.sp
        ),
        color = colors.textSecondary.copy(alpha = 0.85f),
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.pageBg)
            .softFeatherRim(CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** 元信息行：13dp 图标 + 12sp 文字（设计稿 .course-meta-item）。 */
@Composable
private fun SoftMetaRow(
    icon: DrawableResource,
    text: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = tint.copy(alpha = 0.85f)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            ),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 下一节课 hero 卡：设计稿 .next-class-card（丁香紫渐变 + 时间徽标 + 倒计时）。 */
@Composable
private fun SoftNextClassCard(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    onClick: () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val labelColor = if (isDarkTheme) Color(0xFFC4B8E8) else SoftAccentAlt700
    val nameColor = if (isDarkTheme) Color(0xFFEBE5FF) else SoftAccentAlt800
    val badgeBg = if (isDarkTheme) Color(0x1FFFFFFF) else Color(0x99FFFFFF)
    val gradient = if (isDarkTheme) {
        listOf(Color(0xFF3A2F5C), Color(0xFF2E2749))
    } else {
        listOf(SoftAccentAlt100, SoftAccentAlt50)
    }
    val shape = RoundedCornerShape(28.dp)
    val minutesUntil = remember(model, now) { softMinutesUntil(model, now) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(shape = shape, elevation = 8.dp)
            .clip(shape)
            .background(Brush.linearGradient(gradient))
            .softFeatherRim(shape)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(SoftAccentAlt500)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(Res.string.today_ios_next_label),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.08.em,
                            lineHeight = 12.sp
                        ),
                        color = labelColor
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = model.course.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 27.sp,
                        letterSpacing = (-0.01).em
                    ),
                    color = nameColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (!gridStyle.hideLocation && model.course.position.isNotBlank()) {
                        SoftMetaRow(
                            icon = Res.drawable.location_on_24px,
                            text = model.course.position,
                            tint = labelColor
                        )
                    }
                    if (!gridStyle.hideTeacher && model.course.teacher.isNotBlank()) {
                        SoftMetaRow(
                            icon = Res.drawable.person_24px,
                            text = model.course.teacher,
                            tint = labelColor
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.schedule_24px),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = nameColor
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = softTimeRange(model),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp
                        ),
                        color = nameColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (minutesUntil != null && minutesUntil > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = minutesUntil.toString(),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 26.sp,
                                letterSpacing = (-0.02).em
                            ),
                            color = nameColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(Res.string.today_ios_countdown_label),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 13.sp
                            ),
                            color = labelColor,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun softSectionCount(model: CourseDisplayModel): Int {
    val start = model.course.startSection
    val end = model.course.endSection
    return if (start != null && end != null) (end - start + 1).coerceAtLeast(1) else 1
}

private fun softMinutesUntil(model: CourseDisplayModel, now: LocalTime): Int? {
    val startText = model.startTime?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val start = LocalTime.parse(startText)
        (start.toSecondOfDay() - now.toSecondOfDay()) / 60
    } catch (e: Exception) {
        null
    }
}

/** 明日预览卡片：设计稿 .tomorrow-card（凹陷卡 + 4dp 色条 + 名称 + 时间·地点）。 */
@Composable
private fun SoftTomorrowCard(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean
) {
    val colors = appColors()
    val accent = softAccentColor(model, gridStyle, isDark)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(appShapes().card)
            .background(colors.cardBg)
            .softFeatherRim(appShapes().card)
    ) {
        Box(
            modifier = Modifier
                .width(SoftTomorrowAccentWidth)
                .fillMaxHeight()
                .background(accent)
        )
        Column(modifier = Modifier.weight(1f).padding(14.dp)) {
            Text(
                text = model.course.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = softTimeRange(model),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = colors.textSecondary
                )
                if (!gridStyle.hideLocation && model.course.position.isNotBlank()) {
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = colors.divider
                    )
                    Text(
                        text = model.course.position,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 今日日程事件区段：来自「日程」页新建的日程，展示在课程时间轴之后。 */
@Composable
private fun SoftEventsSection(events: List<ScheduleEvent>) {
    val colors = appColors()
    Spacer(modifier = Modifier.height(8.dp))
    SoftSectionLabelRow(
        label = stringResource(Res.string.title_today_schedule),
        trailing = stringResource(Res.string.agenda_count_format, events.size.toString()),
        fillWidth = true
    )
    Spacer(modifier = Modifier.height(6.dp))
    events.forEachIndexed { index, event ->
        SoftEventRow(event = event, index = index, isLast = index == events.lastIndex)
        if (index < events.lastIndex) Spacer(modifier = Modifier.height(6.dp))
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SoftEventRow(
    event: ScheduleEvent,
    index: Int,
    isLast: Boolean
) {
    val colors = appColors()
    // 柔绘事件分类色：不再用 Material 500 档高饱和色（书卷/通透沿用各自历史值），
    // 统一走柔绘语义色 —— success/warning/danger/info 均为低饱和马卡龙档
    val categoryColor = when (ScheduleCategory.fromKey(event.category)) {
        ScheduleCategory.TODO -> colors.success
        ScheduleCategory.ACTIVITY -> colors.warning
        ScheduleCategory.EXAM -> colors.danger
        ScheduleCategory.HOMEWORK -> colors.info
        ScheduleCategory.OTHER -> colors.textSecondary
    }
    val metaLine = listOfNotNull(
        stringResource(when (ScheduleCategory.fromKey(event.category)) {
            ScheduleCategory.TODO -> Res.string.agenda_category_todo
            ScheduleCategory.ACTIVITY -> Res.string.agenda_category_activity
            ScheduleCategory.EXAM -> Res.string.agenda_category_exam
            ScheduleCategory.HOMEWORK -> Res.string.agenda_category_homework
            ScheduleCategory.OTHER -> Res.string.agenda_category_other
        }),
        event.location
    ).joinToString(" · ")

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 左侧时间占位（与课程时间列对齐）
        Box(modifier = Modifier.width(56.dp).fillMaxHeight()) {
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = 18.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(colors.divider)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = 6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(categoryColor)
            )
        }
        // 右侧内容卡
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.cardBg)
                .softFeatherRim(RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    ),
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (metaLine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 14.sp
                        ),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                event.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 14.sp
                        ),
                        color = colors.textSecondary.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 幽灵小按钮：设计稿 .btn.ghost.view-all-btn（32dp 高 + 12sp + chevron）。 */
@Composable
private fun SoftGhostButton(text: String, onClick: () -> Unit) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .clip(appShapes().chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = vectorResource(Res.drawable.chevron_right_24px),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = colors.textPrimary
        )
    }
}

/** 课程详情弹层：字段与设计包 pages/今日日程.html 的详情弹层一致。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoftCourseDetailSheet(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val colors = appColors()
    val isFinished = isSoftCourseFinished(model, now)
    val isCurrent = !isFinished && isSoftCourseOngoing(model, now)
    val statusText = stringResource(
        when {
            isCurrent -> Res.string.today_ios_status_live
            isFinished -> Res.string.today_ios_status_done
            else -> Res.string.today_ios_status_upcoming
        }
    )

    AppGlassBottomSheet(
        hazeState = null,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 40.dp)
        ) {
            Text(
                text = statusText,
                style = TextStyle(
                    fontFamily = iosUiSans(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.1.em
                ),
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = model.course.name,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(appShapes().chip)
                    .softFeatherRim(appShapes().chip)
            ) {
                SoftDetailRow(
                    label = stringResource(Res.string.today_ios_meta_time),
                    value = softTimeRange(model)
                )
                HorizontalDivider(thickness = 1.dp, color = colors.divider)
                if (model.course.position.isNotBlank()) {
                    SoftDetailRow(
                        label = stringResource(Res.string.today_ios_meta_room),
                        value = model.course.position
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                if (model.course.teacher.isNotBlank()) {
                    SoftDetailRow(
                        label = stringResource(Res.string.today_ios_meta_teacher),
                        value = model.course.teacher
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                model.course.credit?.takeIf { it.isNotBlank() }?.let { credit ->
                    SoftDetailRow(
                        label = stringResource(Res.string.today_ios_meta_credit),
                        value = credit
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(appShapes().chip)
                        .background(colors.inputBg)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.today_ios_sheet_close),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.textPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(appShapes().chip)
                        .background(colors.primary)
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.today_ios_sheet_edit),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.textOnPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SoftDetailRow(label: String, value: String) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cardBgElevated)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.textPrimary,
            textAlign = TextAlign.End
        )
    }
}

/** 时间区间文本：优先自定义时间，否则用节次时间。 */
private fun softTimeRange(model: CourseDisplayModel): String {
    val start = model.startTime?.takeIf { it.isNotBlank() }
    val end = model.endTime?.takeIf { it.isNotBlank() }
    return when {
        start != null && end != null -> "$start - $end"
        start != null -> start
        end != null -> end
        else -> EMPTY_TIME_PLACEHOLDER
    }
}

private fun isSoftCourseFinished(model: CourseDisplayModel, now: LocalTime): Boolean {
    val endText = model.endTime?.takeIf { it.isNotBlank() } ?: return false
    return try {
        LocalTime.parse(endText) < now
    } catch (e: Exception) {
        false
    }
}

private fun isSoftCourseOngoing(model: CourseDisplayModel, now: LocalTime): Boolean {
    val startText = model.startTime?.takeIf { it.isNotBlank() } ?: return false
    val endText = model.endTime?.takeIf { it.isNotBlank() } ?: return false
    return try {
        val start = LocalTime.parse(startText)
        val end = LocalTime.parse(endText)
        now >= start && now < end
    } catch (e: Exception) {
        false
    }
}

/**
 * 课程色条颜色：设计稿用 `--chart-1…5` 实色作色条。
 * CLAUDE 课表样式的 light 档是 12% 透明度的 chart 色（等价于设计稿实色叠在卡片白底上的观感），
 * dark 档是 chart 实色，正好对应设计稿浅色 / 深色两套写法。
 */
private fun softAccentColor(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean
): Color {
    val pair = gridStyle.courseColorMaps.getOrElse(model.course.colorInt) {
        ScheduleGridStyle.DEFAULT_COLOR_MAPS[0]
    }
    return if (isDark) pair.dark else pair.light
}

// ===== 通透主题（iOS 26）· 今日页专用组件 =====
// ============================================================================
// 通透主题（iOS 26）· 今日课表页
//
// 信息架构与「书卷」主题逐项一致（页头日期/周次 → 下节课卡 → 今日课程时间轴
//   → 日程事件 → 明日预览 → 课程详情玻璃面板），位置、间距节奏、字段集合完全不变。
//
// 视觉按 iOS 26「Liquid Glass」重做：
//   · 卡片 = 白卡（深色 #1C1C1E）+ 16~20dp 连续圆角 + 玻璃高光内描边，代替书卷的
//     暖色渐变 + 1dp 暖色描边
//   · 强调色 = systemBlue / systemIndigo / systemOrange / systemGreen / systemRed，
//     代替书卷的赤陶 + 紫罗兰双色交替
//   · 圆点与连线 = 更细的 1.5dp 环 + 系统蓝，进行中圆点带呼吸动画
//   · 时间轴左侧时间列、节点圆点、连线几何位置与书卷完全一致
//
// 状态栏 / 底部导航由系统与 App 底栏承担，不在页面内重复绘制。
// ============================================================================

/** 时间轴时间列宽 56dp；明日预览色条 4dp。 */
private val Ios26TomorrowAccentWidth = 4.dp

// iOS 26 卡片渐变底（Apple 系统色的极淡档）：偶数行用系统蓝系，奇数行用系统靛系。
private val Ios26AccentAlt50 = Color(0xFFF2F7FF)
private val Ios26AccentAlt100 = Color(0xFFE5F0FF)
private val Ios26AccentAlt500 = Color(0xFF5856D6)
private val Ios26AccentAlt700 = Color(0xFF2B2A78)
private val Ios26AccentAlt800 = Color(0xFF1B1B4B)
private val Ios26AccentWarm50 = Color(0xFFFFF6EE)
private val Ios26AccentWarm500 = Color(0xFFFF9500)
private val Ios26AccentWarm700 = Color(0xFFB36800)
private val Ios26AccentWarm400 = Color(0xFFD6866A)

private data class Ios26TimelinePalette(
    val gradient: List<Color>,
    val border: Color,
    val title: Color,
    val meta: Color,
    val badgeBg: Color,
    val badgeFg: Color,
    val dot: Color
)

/**
 * 时间轴卡片配色（偶数行 = 系统蓝系，奇数行 = 系统橙系）。
 *
 * 与书卷同结构（两个交替色系 + 深浅两套），色相换成 Apple 系统色：
 * 书卷是「紫罗兰 / 赤陶」暖冷交替，通透换成「系统蓝 / 系统橙」——
 * 这是 iOS 图标与图表的标准双色搭配，保证在浅色白卡与深色炭卡上都清晰。
 */
@Composable
private fun ios26TimelinePalette(peach: Boolean): Ios26TimelinePalette {
    val colors = appColors()
    val isDark = LocalIsDarkTheme.current
    return when {
        peach && isDark -> Ios26TimelinePalette(
            gradient = listOf(Color(0xFF33270F), Color(0xFF2A2010)),
            border = Color(0x33FF9F0A),
            title = Color(0xFFFFCB8A),
            meta = Color(0xFFC9A87C),
            badgeBg = Color(0x3DFF9F0A),
            badgeFg = Color(0xFFFFCB8A),
            dot = Color(0xFFFF9F0A)
        )

        peach -> Ios26TimelinePalette(
            gradient = listOf(Color(0xFFFFFBF5), Ios26AccentWarm50),
            border = Color(0x1FFF9500),
            title = colors.textPrimary,
            meta = colors.textSecondary,
            badgeBg = Color(0x24FF9500),
            badgeFg = Ios26AccentWarm700,
            dot = Ios26AccentWarm500
        )

        isDark -> Ios26TimelinePalette(
            gradient = listOf(Color(0xFF16213A), Color(0xFF121A2C)),
            border = Color(0x330A84FF),
            title = Color(0xFFA9CDFF),
            meta = Color(0xFF8FA5C4),
            badgeBg = Color(0x3D0A84FF),
            badgeFg = Color(0xFFA9CDFF),
            dot = Color(0xFF0A84FF)
        )

        else -> Ios26TimelinePalette(
            gradient = listOf(Color(0xFFFBFDFF), Ios26AccentAlt50),
            border = Color(0x1F007AFF),
            title = colors.textPrimary,
            meta = colors.textSecondary,
            badgeBg = Color(0x1F007AFF),
            badgeFg = Ios26AccentAlt700,
            dot = Color(0xFF007AFF)
        )
    }
}

@Composable
private fun Ios26TodayContent(
    state: TodayUiState.Success,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    bottomInset: Dp,
    statusText: String,
    dateText: String,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    onOpenWeeklySchedule: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditCourse: (String) -> Unit
) {
    val colors = appColors()
    var detailCourse by remember { mutableStateOf<CourseDisplayModel?>(null) }

    val nextCourse = remember(state.courses, now) {
        val upcoming = state.courses.firstOrNull { model ->
            val start = model.startTime?.takeIf { it.isNotBlank() }?.let {
                runCatching { LocalTime.parse(it) }.getOrNull()
            }
            start != null && start > now
        }
        upcoming ?: state.courses.firstOrNull { !isIos26CourseFinished(it, now) }
    }
    val tomorrowDate = stringResource(
        Res.string.today_ios_tomorrow_format,
        state.today.month.number,
        state.today.day
    )

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = bottomInset + 100.dp)
    ) {
        item {
            Ios26TodayHeader(
                weekIndex = state.weekIndex,
                status = state.status,
                statusText = statusText,
                dateText = dateText,
            )
        }

        if (nextCourse != null) {
            item {
                Ios26NextClassCard(
                    model = nextCourse,
                    gridStyle = gridStyle,
                    isDark = isDark,
                    now = now,
                    onClick = { detailCourse = nextCourse }
                )
            }
        }

        item {
            Ios26TimelineHeader(
                title = stringResource(Res.string.title_today_courses),
                count = stringResource(Res.string.text_courses_count, state.courses.size.toString())
            )
        }

        if (state.courses.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.text_no_courses_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            itemsIndexed(
                state.courses,
                key = { _, model -> model.course.id }
            ) { index, model ->
                Ios26TimelineItem(
                    model = model,
                    index = index,
                    isLast = index == state.courses.lastIndex,
                    gridStyle = gridStyle,
                    isDark = isDark,
                    now = now,
                    onClick = { detailCourse = model }
                )
            }
        }

        // 今日日程事件（来自「日程」页新建的日程）
        if (state.events.isNotEmpty()) {
            item {
                Ios26EventsSection(events = state.events)
            }
        }

        if (state.tomorrowCourses.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Ios26SectionLabelRow(
                            label = tomorrowDate,
                            trailing = stringResource(
                                Res.string.text_courses_count,
                                state.tomorrowCourses.size.toString()
                            ),
                            fillWidth = false
                        )
                    }
                    Ios26GhostButton(
                        text = stringResource(Res.string.today_ios_view_all),
                        onClick = onOpenWeeklySchedule
                    )
                }
            }
            itemsIndexed(
                state.tomorrowCourses,
                key = { _, model -> "tomorrow-${model.course.id}" }
            ) { _, model ->
                Ios26TomorrowCard(model = model, gridStyle = gridStyle, isDark = isDark)
            }
        }
    }

    detailCourse?.let { model ->
        Ios26CourseDetailSheet(
            model = model,
            gridStyle = gridStyle,
            isDark = isDark,
            now = now,
            onDismiss = { detailCourse = null },
            onEdit = {
                detailCourse = null
                onEditCourse(model.course.id)
            }
        )
    }
}

/** 页头：周次胶囊置于左侧 + 日期居中大字，同一行。 */
@Composable
private fun Ios26TodayHeader(
    weekIndex: Int,
    status: TodayStatus,
    statusText: String,
    dateText: String,
) {
    val colors = appColors()
    val weekLabel = if (status == TodayStatus.Normal) {
        stringResource(Res.string.title_current_week, weekIndex.toString())
    } else {
        statusText
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 12.dp)
    ) {
        // 周次胶囊：方形块，靠左
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.primarySoft)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = weekLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.02.em,
                    lineHeight = 16.sp
                ),
                color = colors.primary,
                maxLines = 1
            )
        }
        // 日期：行内居中大字
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp,
                letterSpacing = (-0.01).em
            ),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 84.dp)
        )
    }
}

/** 分区标题行：Lora 12sp 大写字距标签 + 右侧辅助文案。 */
@Composable
private fun Ios26SectionLabelRow(
    label: String,
    trailing: String?,
    fillWidth: Boolean = true
) {
    val colors = appColors()
    Row(
        modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = iosUiSans(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.14.em,
                lineHeight = 16.sp
            ),
            color = colors.textSecondary
        )
        if (trailing != null) {
            if (fillWidth) Spacer(modifier = Modifier.weight(1f))
            else Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                ),
                color = colors.textSecondary
            )
        }
    }
}

/** 时间轴标题：设计稿 .timeline-header（左「今日课程」13sp 600 + 右「共 N 节」12sp）。 */
@Composable
private fun Ios26TimelineHeader(title: String, count: String) {
    val colors = appColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.02.em,
                lineHeight = 17.sp
            ),
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = count,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            ),
            color = colors.textSecondary.copy(alpha = 0.72f)
        )
    }
}

/** 时间轴条目：设计稿 .timeline-item（左时间列 56dp + 圆点连线 + 右侧课程卡）。 */
@Composable
private fun Ios26TimelineItem(
    model: CourseDisplayModel,
    index: Int,
    isLast: Boolean,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    onClick: () -> Unit
) {
    val colors = appColors()
    val palette = ios26TimelinePalette(peach = index % 2 == 1)
    val isFinished = isIos26CourseFinished(model, now)
    val isCurrent = !isFinished && isIos26CourseOngoing(model, now)
    val startTime = model.startTime?.takeIf { it.isNotBlank() }
        ?: ios26TimeRange(model).substringBefore(" - ")

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.width(56.dp).fillMaxHeight()) {
            Text(
                text = startTime,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp
                ),
                color = colors.textPrimary,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = 30.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(colors.divider)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = 18.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) palette.dot else colors.pageBg)
                    .border(2.5.dp, palette.dot, CircleShape)
            )
        }

        Ios26TimelineCard(
            model = model,
            index = index,
            palette = palette,
            gridStyle = gridStyle,
            isFinished = isFinished,
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 时间轴课程卡：设计稿 .timeline-card（20dp 圆角 + 渐变底 + 名称 + N 节徽标 + 元信息 + 信息胶囊）。 */
@Composable
private fun Ios26TimelineCard(
    model: CourseDisplayModel,
    index: Int,
    palette: Ios26TimelinePalette,
    gridStyle: ScheduleGridStyle,
    isFinished: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    val shape = appShapes().heroCard
    val sectionCount = ios26SectionCount(model)
    val cardMotion = rememberTodayCardMotion(index, isFinished)
    Box(
        modifier = modifier
            .graphicsLayer(
                alpha = cardMotion.alpha,
                scaleX = cardMotion.scale,
                scaleY = cardMotion.scale,
                translationY = cardMotion.translationYPx
            )
            .shadow(
                elevation = 1.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow
            )
            .clip(shape)
            .background(Brush.linearGradient(palette.gradient))
            // iOS 26：卡片底色本身已是极淡系统色，描边改为玻璃高光内描边
            // （书卷是 1dp 暖色实边框；玻璃描边与 Liquid Glass 语言一致）
            .iosGlassRim(shape)
            .clickable(
                interactionSource = cardMotion.interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = model.course.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp,
                        textDecoration = if (isFinished) TextDecoration.LineThrough else null
                    ),
                    color = palette.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(palette.badgeBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(
                            Res.string.today_ios_sections_format,
                            sectionCount.toString()
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 13.sp
                        ),
                        color = palette.badgeFg
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!gridStyle.hideLocation && model.course.position.isNotBlank()) {
                    Ios26MetaRow(
                        icon = Res.drawable.location_on_24px,
                        text = model.course.position,
                        tint = palette.meta
                    )
                }
                if (!gridStyle.hideTeacher && model.course.teacher.isNotBlank()) {
                    Ios26MetaRow(
                        icon = Res.drawable.person_24px,
                        text = model.course.teacher,
                        tint = palette.meta
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ios26NotePill(
                    label = stringResource(Res.string.today_ios_note_type),
                    value = stringResource(
                        if (model.course.isLab) {
                            Res.string.today_ios_type_lab
                        } else {
                            Res.string.today_ios_type_theory
                        }
                    )
                )
                Ios26NotePill(
                    label = stringResource(Res.string.today_ios_note_hours),
                    value = sectionCount.toString()
                )
            }
        }
    }
}

/** 信息胶囊：设计稿 .note-pill（bg-100 底 + border-200 描边 + 标签加粗 + 值常规）。 */
@Composable
private fun Ios26NotePill(label: String, value: String) {
    val colors = appColors()
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.textSecondary)) {
                append(label)
            }
            append("：")
            append(value)
        },
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 13.sp
        ),
        color = colors.textSecondary.copy(alpha = 0.85f),
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.pageBg)
            .border(1.dp, colors.divider, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** 元信息行：13dp 图标 + 12sp 文字（设计稿 .course-meta-item）。 */
@Composable
private fun Ios26MetaRow(
    icon: DrawableResource,
    text: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = tint.copy(alpha = 0.85f)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            ),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 下一节课 hero 卡：设计稿 .next-class-card（丁香紫渐变 + 时间徽标 + 倒计时）。 */
@Composable
private fun Ios26NextClassCard(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    onClick: () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val labelColor = if (isDarkTheme) Color(0xFFC4B8E8) else Ios26AccentAlt700
    val nameColor = if (isDarkTheme) Color(0xFFEBE5FF) else Ios26AccentAlt800
    val badgeBg = if (isDarkTheme) Color(0x1FFFFFFF) else Color(0x99FFFFFF)
    val gradient = if (isDarkTheme) {
        listOf(Color(0xFF3A2F5C), Color(0xFF2E2749))
    } else {
        listOf(Ios26AccentAlt100, Ios26AccentAlt50)
    }
    val border = if (isDarkTheme) Color(0x339C87F5) else Color(0x269C87F5)
    val shape = RoundedCornerShape(28.dp)
    val minutesUntil = remember(model, now) { ios26MinutesUntil(model, now) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = shape,
                clip = false,
                ambientColor = Ios26AccentAlt500.copy(alpha = 0.25f),
                spotColor = Ios26AccentAlt500.copy(alpha = 0.25f)
            )
            .clip(shape)
            .background(Brush.linearGradient(gradient))
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Ios26AccentAlt500)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(Res.string.today_ios_next_label),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.08.em,
                            lineHeight = 12.sp
                        ),
                        color = labelColor
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = model.course.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 27.sp,
                        letterSpacing = (-0.01).em
                    ),
                    color = nameColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (!gridStyle.hideLocation && model.course.position.isNotBlank()) {
                        Ios26MetaRow(
                            icon = Res.drawable.location_on_24px,
                            text = model.course.position,
                            tint = labelColor
                        )
                    }
                    if (!gridStyle.hideTeacher && model.course.teacher.isNotBlank()) {
                        Ios26MetaRow(
                            icon = Res.drawable.person_24px,
                            text = model.course.teacher,
                            tint = labelColor
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.schedule_24px),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = nameColor
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = ios26TimeRange(model),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp
                        ),
                        color = nameColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (minutesUntil != null && minutesUntil > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = minutesUntil.toString(),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 26.sp,
                                letterSpacing = (-0.02).em
                            ),
                            color = nameColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(Res.string.today_ios_countdown_label),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 13.sp
                            ),
                            color = labelColor,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun ios26SectionCount(model: CourseDisplayModel): Int {
    val start = model.course.startSection
    val end = model.course.endSection
    return if (start != null && end != null) (end - start + 1).coerceAtLeast(1) else 1
}

private fun ios26MinutesUntil(model: CourseDisplayModel, now: LocalTime): Int? {
    val startText = model.startTime?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val start = LocalTime.parse(startText)
        (start.toSecondOfDay() - now.toSecondOfDay()) / 60
    } catch (e: Exception) {
        null
    }
}

/** 明日预览卡片：设计稿 .tomorrow-card（凹陷卡 + 4dp 色条 + 名称 + 时间·地点）。 */
@Composable
private fun Ios26TomorrowCard(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean
) {
    val colors = appColors()
    val accent = ios26AccentColor(model, gridStyle, isDark)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(appShapes().card)
            .background(colors.cardBg)
            .border(1.dp, colors.divider, appShapes().card)
    ) {
        Box(
            modifier = Modifier
                .width(Ios26TomorrowAccentWidth)
                .fillMaxHeight()
                .background(accent)
        )
        Column(modifier = Modifier.weight(1f).padding(14.dp)) {
            Text(
                text = model.course.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ios26TimeRange(model),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = colors.textSecondary
                )
                if (!gridStyle.hideLocation && model.course.position.isNotBlank()) {
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = colors.divider
                    )
                    Text(
                        text = model.course.position,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 今日日程事件区段：来自「日程」页新建的日程，展示在课程时间轴之后。 */
@Composable
private fun Ios26EventsSection(events: List<ScheduleEvent>) {
    val colors = appColors()
    Spacer(modifier = Modifier.height(8.dp))
    Ios26SectionLabelRow(
        label = stringResource(Res.string.title_today_schedule),
        trailing = stringResource(Res.string.agenda_count_format, events.size.toString()),
        fillWidth = true
    )
    Spacer(modifier = Modifier.height(6.dp))
    events.forEachIndexed { index, event ->
        Ios26EventRow(event = event, index = index, isLast = index == events.lastIndex)
        if (index < events.lastIndex) Spacer(modifier = Modifier.height(6.dp))
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun Ios26EventRow(
    event: ScheduleEvent,
    index: Int,
    isLast: Boolean
) {
    val colors = appColors()
    val categoryColor = when (ScheduleCategory.fromKey(event.category)) {
        ScheduleCategory.TODO -> Color(0xFF4CAF50)
        ScheduleCategory.ACTIVITY -> Color(0xFFFF9800)
        ScheduleCategory.EXAM -> Color(0xFFF44336)
        ScheduleCategory.HOMEWORK -> Color(0xFF2196F3)
        ScheduleCategory.OTHER -> colors.textSecondary
    }
    val metaLine = listOfNotNull(
        stringResource(when (ScheduleCategory.fromKey(event.category)) {
            ScheduleCategory.TODO -> Res.string.agenda_category_todo
            ScheduleCategory.ACTIVITY -> Res.string.agenda_category_activity
            ScheduleCategory.EXAM -> Res.string.agenda_category_exam
            ScheduleCategory.HOMEWORK -> Res.string.agenda_category_homework
            ScheduleCategory.OTHER -> Res.string.agenda_category_other
        }),
        event.location
    ).joinToString(" · ")

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 左侧时间占位（与课程时间列对齐）
        Box(modifier = Modifier.width(56.dp).fillMaxHeight()) {
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = 18.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(colors.divider)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = 6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(categoryColor)
            )
        }
        // 右侧内容卡
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    ),
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (metaLine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 14.sp
                        ),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                event.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 14.sp
                        ),
                        color = colors.textSecondary.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 幽灵小按钮：设计稿 .btn.ghost.view-all-btn（32dp 高 + 12sp + chevron）。 */
@Composable
private fun Ios26GhostButton(text: String, onClick: () -> Unit) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .clip(appShapes().chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = vectorResource(Res.drawable.chevron_right_24px),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = colors.textPrimary
        )
    }
}

/** 课程详情弹层：字段与设计包 pages/今日日程.html 的详情弹层一致。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Ios26CourseDetailSheet(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val colors = appColors()
    val isFinished = isIos26CourseFinished(model, now)
    val isCurrent = !isFinished && isIos26CourseOngoing(model, now)
    val statusText = stringResource(
        when {
            isCurrent -> Res.string.today_ios_status_live
            isFinished -> Res.string.today_ios_status_done
            else -> Res.string.today_ios_status_upcoming
        }
    )

    AppGlassBottomSheet(
        hazeState = null,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 40.dp)
        ) {
            Text(
                text = statusText,
                style = TextStyle(
                    fontFamily = iosUiSans(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.1.em
                ),
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = model.course.name,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(appShapes().chip)
                    .border(1.dp, colors.divider, appShapes().chip)
            ) {
                Ios26DetailRow(
                    label = stringResource(Res.string.today_ios_meta_time),
                    value = ios26TimeRange(model)
                )
                HorizontalDivider(thickness = 1.dp, color = colors.divider)
                if (model.course.position.isNotBlank()) {
                    Ios26DetailRow(
                        label = stringResource(Res.string.today_ios_meta_room),
                        value = model.course.position
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                if (model.course.teacher.isNotBlank()) {
                    Ios26DetailRow(
                        label = stringResource(Res.string.today_ios_meta_teacher),
                        value = model.course.teacher
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                model.course.credit?.takeIf { it.isNotBlank() }?.let { credit ->
                    Ios26DetailRow(
                        label = stringResource(Res.string.today_ios_meta_credit),
                        value = credit
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(appShapes().chip)
                        .background(colors.inputBg)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.today_ios_sheet_close),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.textPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(appShapes().chip)
                        .background(colors.primary)
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.today_ios_sheet_edit),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.textOnPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun Ios26DetailRow(label: String, value: String) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cardBgElevated)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.textPrimary,
            textAlign = TextAlign.End
        )
    }
}

/** 时间区间文本：优先自定义时间，否则用节次时间。 */
private fun ios26TimeRange(model: CourseDisplayModel): String {
    val start = model.startTime?.takeIf { it.isNotBlank() }
    val end = model.endTime?.takeIf { it.isNotBlank() }
    return when {
        start != null && end != null -> "$start - $end"
        start != null -> start
        end != null -> end
        else -> EMPTY_TIME_PLACEHOLDER
    }
}

private fun isIos26CourseFinished(model: CourseDisplayModel, now: LocalTime): Boolean {
    val endText = model.endTime?.takeIf { it.isNotBlank() } ?: return false
    return try {
        LocalTime.parse(endText) < now
    } catch (e: Exception) {
        false
    }
}

private fun isIos26CourseOngoing(model: CourseDisplayModel, now: LocalTime): Boolean {
    val startText = model.startTime?.takeIf { it.isNotBlank() } ?: return false
    val endText = model.endTime?.takeIf { it.isNotBlank() } ?: return false
    return try {
        val start = LocalTime.parse(startText)
        val end = LocalTime.parse(endText)
        now >= start && now < end
    } catch (e: Exception) {
        false
    }
}

/**
 * 课程色条颜色：设计稿用 `--chart-1…5` 实色作色条。
 * CLAUDE 课表样式的 light 档是 12% 透明度的 chart 色（等价于设计稿实色叠在卡片白底上的观感），
 * dark 档是 chart 实色，正好对应设计稿浅色 / 深色两套写法。
 */
private fun ios26AccentColor(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean
): Color {
    val pair = gridStyle.courseColorMaps.getOrElse(model.course.colorInt) {
        ScheduleGridStyle.DEFAULT_COLOR_MAPS[0]
    }
    return if (isDark) pair.dark else pair.light
}
