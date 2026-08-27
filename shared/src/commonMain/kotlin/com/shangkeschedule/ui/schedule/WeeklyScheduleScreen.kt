package com.shangkeschedule.ui.schedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.shangkeschedule.Destination
import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.model.schedule_style.ScheduleModeProto
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.navigation.AddEditCourseChannel
import com.shangkeschedule.navigation.PresetCourseData
import com.shangkeschedule.ui.components.AdaptiveNavigationScaffold
import com.shangkeschedule.ui.components.CourseTablePickerDialog
import com.shangkeschedule.ui.schedule.components.CourseDetailBottomSheet
import com.shangkeschedule.ui.schedule.components.FloatingCourseBar
import com.shangkeschedule.ui.schedule.components.ScheduleGrid
import com.shangkeschedule.ui.schedule.components.ScheduleGridActions
import com.shangkeschedule.ui.schedule.components.ScheduleGridStyleComposed
import com.shangkeschedule.ui.schedule.components.ScheduleGridViewState
import com.shangkeschedule.ui.schedule.components.WeekSelectorBottomSheet
import com.shangkeschedule.ui.schedule.components.rememberScheduleGridState
import com.shangkeschedule.ui.schedule.components.adaptiveTextColor
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.LocalThemePreset
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_select_table
import shangkeschedule.shared.generated.resources.arrow_drop_down_24px
import shangkeschedule.shared.generated.resources.format_week_display
import shangkeschedule.shared.generated.resources.label_view_mode_list
import shangkeschedule.shared.generated.resources.label_view_mode_week
import shangkeschedule.shared.generated.resources.course_section_range
import shangkeschedule.shared.generated.resources.snackbar_add_course_within_semester
import shangkeschedule.shared.generated.resources.swap_horiz_24px
import shangkeschedule.shared.generated.resources.title_current_week
import shangkeschedule.shared.generated.resources.title_semester_not_set
import shangkeschedule.shared.generated.resources.title_vacation
import shangkeschedule.shared.generated.resources.title_vacation_until_start
import shangkeschedule.shared.generated.resources.view_agenda_24px
import shangkeschedule.shared.generated.resources.view_week_24px
import shangkeschedule.shared.generated.resources.week_days_full_names
import kotlin.time.Clock

/**
 * 无限时间轴的中值锚点。
 */
private const val INFINITE_PAGER_CENTER = Int.MAX_VALUE / 2

/**
 * 周课表主屏幕组件
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WeeklyScheduleScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: WeeklyScheduleViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    val coroutineScope = rememberCoroutineScope()

    val snackbarMsg = stringResource(Res.string.snackbar_add_course_within_semester)

    val pagerState = rememberPagerState(
        initialPage = INFINITE_PAGER_CENTER,
        pageCount = { Int.MAX_VALUE }
    )

    // 辅助函数：计算基于目标星期几的上一周/本周对应日期偏移
    fun getPreviousOrSameDay(date: LocalDate, targetDayOfWeek: DayOfWeek): LocalDate {
        var current = date
        while (current.dayOfWeek != targetDayOfWeek) {
            current = current.plus(-1, DateTimeUnit.DAY)
        }
        return current
    }

    LaunchedEffect(pagerState.currentPage, uiState.firstDayOfWeek) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { pageIndex ->
                val offsetWeeks = (pageIndex - INFINITE_PAGER_CENTER).toLong()
                val firstDay = DayOfWeek(uiState.firstDayOfWeek)
                val thisMonday = getPreviousOrSameDay(today, firstDay)
                val targetMonday = thisMonday.plus(offsetWeeks * 7, DateTimeUnit.DAY)
                viewModel.updatePagerDate(targetMonday)
            }
    }

    // UI 交互控制弹窗标志位
    var showWeekSelector by remember { mutableStateOf(false) }
    var showTableSwitcher by remember { mutableStateOf(false) }
    var isGridHolding by remember { mutableStateOf(false) }
    var selectedBlockForDetail by remember { mutableStateOf<MergedCourseBlock?>(null) }

    val composedStyle = uiState.style

    val floatingCourse = uiState.floatingCourse

    val floatingDuration by remember(floatingCourse, composedStyle.scheduleMode) {
        derivedStateOf {
            if (floatingCourse != null) {
                val start = floatingCourse.course.startSection?.toFloat() ?: 1f
                val end = floatingCourse.course.endSection?.toFloat() ?: 1f

                if (composedStyle.scheduleMode == ScheduleModeProto.TIME_24H_MODE) {
                    (end - start).coerceAtLeast(1.0f)
                } else {
                    (end - start + 1f).coerceAtLeast(1.0f)
                }
            } else {
                1.0f
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val gridScrollState = rememberScrollState()

    val customTextColor = composedStyle.pageTextColor ?: MaterialTheme.colorScheme.onSurface
    val customSubTextColor = customTextColor.copy(alpha = 0.7f)

    val displayTitle = when {
        !uiState.isSemesterSet || uiState.semesterStartDate == null -> {
            stringResource(Res.string.title_semester_not_set)
        }
        uiState.daysUntilStart > 0 -> {
            stringResource(Res.string.title_vacation_until_start, uiState.daysUntilStart.toString())
        }
        uiState.weekIndexInPager != null && uiState.weekIndexInPager!! in 1..uiState.totalWeeks -> {
            stringResource(Res.string.title_current_week, uiState.weekIndexInPager.toString())
        }
        else -> {
            stringResource(Res.string.title_vacation)
        }
    }

    val collapseFraction = scrollBehavior.state.collapsedFraction
    val scheduleViewMode by viewModel.scheduleViewModeState.collectAsStateWithLifecycle()

    AdaptiveNavigationScaffold(
        currentDestination = Destination.CourseSchedule,
        onTabSelected = { dest -> onNavigate(dest) },
        showNavigation = floatingCourse == null,
        isTransparent = composedStyle.backgroundImagePath.isNotEmpty(),
        contentColor = customTextColor,
        navigationModifier = Modifier.graphicsLayer {
            translationY = size.height * collapseFraction
            alpha = 1f - collapseFraction
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (composedStyle.backgroundImagePath.isNotEmpty()) {
                AsyncImage(
                    model = composedStyle.backgroundImagePath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = Color.Transparent,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        if (!uiState.isSemesterSet || uiState.semesterStartDate == null) {
                                            onNavigate(Destination.Settings)
                                        } else {
                                            showWeekSelector = true
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = customTextColor
                                )
                                Icon(
                                    imageVector = vectorResource(Res.drawable.arrow_drop_down_24px),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .offset(y = (-4).dp),
                                    tint = customSubTextColor
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                val newMode = if (scheduleViewMode == ScheduleViewMode.WEEK) {
                                    ScheduleViewMode.LIST
                                } else {
                                    ScheduleViewMode.WEEK
                                }
                                viewModel.updateScheduleViewMode(newMode)
                            }) {
                                Icon(
                                    imageVector = vectorResource(
                                        if (scheduleViewMode == ScheduleViewMode.WEEK) {
                                            Res.drawable.view_agenda_24px
                                        } else {
                                            Res.drawable.view_week_24px
                                        }
                                    ),
                                    contentDescription = stringResource(
                                        if (scheduleViewMode == ScheduleViewMode.WEEK) {
                                            Res.string.label_view_mode_list
                                        } else {
                                            Res.string.label_view_mode_week
                                        }
                                    ),
                                    tint = customTextColor
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { showTableSwitcher = true }) {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.swap_horiz_24px),
                                    contentDescription = stringResource(Res.string.action_select_table),
                                    tint = customTextColor
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                        scrollBehavior = scrollBehavior
                    )
                },
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
            ) { scaffoldInnerPadding ->

                val dynamicBottomPadding = remember(innerPadding, collapseFraction, floatingCourse) {
                    if (floatingCourse != null) {
                        0.dp
                    } else {
                        val bottomBarHeight = innerPadding.calculateBottomPadding()
                        val systemWindowInsetBottom = scaffoldInnerPadding.calculateBottomPadding()
                        val baseBottom = systemWindowInsetBottom.coerceAtLeast(0.dp)
                        baseBottom + (bottomBarHeight * (1f - collapseFraction))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = scaffoldInnerPadding.calculateStartPadding(LayoutDirection.Ltr),
                            top = scaffoldInnerPadding.calculateTopPadding(),
                            end = scaffoldInnerPadding.calculateEndPadding(LayoutDirection.Ltr),
                            bottom = dynamicBottomPadding
                        )
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        userScrollEnabled = !isGridHolding
                    ) { pageIndex ->

                    val pageMondayDate = remember(pageIndex, uiState.firstDayOfWeek) {
                        val offsetWeeks = (pageIndex - INFINITE_PAGER_CENTER).toLong()
                        val firstDay = DayOfWeek(uiState.firstDayOfWeek)
                        getPreviousOrSameDay(today, firstDay).plus(offsetWeeks * 7, DateTimeUnit.DAY)
                    }

                    val pageYearString = remember(pageMondayDate) {
                        pageMondayDate.year.toString()
                    }

                    val pageDateStrings = remember(pageMondayDate) {
                        (0..6).map { i ->
                            val d = pageMondayDate.plus(i.toLong(), DateTimeUnit.DAY)
                            val month = d.month.number.toString().padStart(2, '0')
                            val day = d.day.toString().padStart(2, '0')
                            "$month-$day"
                        }
                    }

                    val pageTodayIndex = remember(pageMondayDate) {
                        val weekDates = (0..6).map { pageMondayDate.plus(it.toLong(), DateTimeUnit.DAY) }
                        weekDates.indexOf(today)
                    }

                    val pageCourses = uiState.courseCache[pageMondayDate.toString()] ?: emptyList()

                    if (scheduleViewMode == ScheduleViewMode.LIST) {
                        ScheduleListView(
                            pageCourses = pageCourses,
                            pageMondayDate = pageMondayDate,
                            timeSlots = uiState.timeSlots,
                            showWeekends = uiState.showWeekends,
                            firstDayOfWeek = uiState.firstDayOfWeek,
                            composedStyle = composedStyle,
                            onClickedBlock = { block -> selectedBlockForDetail = block },
                            onLongClickedBlock = { block ->
                                val targetCourseWrapper = block.courses.firstOrNull()
                                val currentWeek = uiState.weekIndexInPager ?: uiState.currentWeekNumber
                                if (targetCourseWrapper != null && currentWeek != null) {
                                    viewModel.enterFloatingMode(
                                        course = targetCourseWrapper,
                                        sourceWeek = currentWeek
                                    )
                                }
                            }
                        )
                    } else {
                        val gridState = rememberScheduleGridState(gridScrollState = gridScrollState)

                        val weekIndex = uiState.weekIndexInPager
                        val totalWeeks = uiState.totalWeeks
                        val weekStr = if (weekIndex != null && weekIndex in 1..totalWeeks) {
                            stringResource(Res.string.format_week_display, weekIndex)
                        } else {
                            null
                        }

                        val gridViewState = remember(pageDateStrings, pageYearString, uiState, pageCourses, pageTodayIndex, weekStr) {
                            ScheduleGridViewState(
                                dates = pageDateStrings,
                                currentYear = pageYearString,
                                currentWeek = weekStr,
                                timeSlots = uiState.timeSlots,
                                mergedCourses = pageCourses,
                                showWeekends = uiState.showWeekends,
                                todayIndex = pageTodayIndex,
                                firstDayOfWeek = uiState.firstDayOfWeek,
                                currentSectionIndex = if (pageTodayIndex >= 0) uiState.currentSectionIndex else -1
                            )
                        }

                        val gridActions = remember(uiState, floatingDuration, snackbarMsg) {
                        object : ScheduleGridActions {
                            override fun onCourseBlockClicked(block: MergedCourseBlock) {
                                selectedBlockForDetail = block
                            }

                            override fun onGridCellClicked(day: Int, section: Int) {
                                if (floatingCourse != null) {
                                    val targetWeek = uiState.weekIndexInPager ?: uiState.currentWeekNumber ?: return
                                    val startSec = section.toFloat()
                                    val endSec = if (composedStyle.scheduleMode == ScheduleModeProto.TIME_24H_MODE) {
                                        startSec + floatingDuration
                                    } else {
                                        startSec + floatingDuration - 1f
                                    }

                                    coroutineScope.launch {
                                        viewModel.updateCourseTimeByFloatingGesture(
                                            targetWeek = targetWeek,
                                            targetDay = day,
                                            startSection = startSec,
                                            endSection = endSec
                                        )
                                    }
                                } else {
                                    val currentWeek = uiState.weekIndexInPager ?: 0
                                    val isCurrentPageValid = currentWeek in 1..uiState.totalWeeks

                                    if (isCurrentPageValid) {
                                        coroutineScope.launch {
                                            val currentWeekSet = setOf(currentWeek)
                                            val presetData = if (composedStyle.scheduleMode == ScheduleModeProto.TIME_24H_MODE) {
                                                val startHour = section.coerceIn(0, 23)
                                                val endHour = (startHour + 1) % 24

                                                val startTimeStr = "${startHour.toString().padStart(2, '0')}:00"
                                                val endTimeStr = "${endHour.toString().padStart(2, '0')}:00"

                                                PresetCourseData(
                                                    day = day,
                                                    isCustomTime = true,
                                                    customStartTime = startTimeStr,
                                                    customEndTime = endTimeStr,
                                                    presetWeeks = currentWeekSet
                                                )
                                            } else {
                                                PresetCourseData(
                                                    day = day,
                                                    startSection = section,
                                                    endSection = section,
                                                    isCustomTime = false,
                                                    presetWeeks = currentWeekSet
                                                )
                                            }

                                            AddEditCourseChannel.sendEvent(presetData)
                                            onNavigate(Destination.AddEditCourse())
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(snackbarMsg)
                                        }
                                    }
                                }
                            }

                            override fun onTimeSlotClicked() {
                                onNavigate(Destination.TimeSlotSettings)
                            }

                            override fun onHoldStateChanged(isHolding: Boolean) {
                                isGridHolding = isHolding
                            }

                            override fun onCourseMovedWithinGrid(
                                block: MergedCourseBlock,
                                newDay: Int,
                                newStartSection: Float,
                                newEndSection: Float
                            ) {
                                val currentWeek = uiState.weekIndexInPager ?: 0
                                if (currentWeek in 1..uiState.totalWeeks) {
                                    block.courses.firstOrNull()?.course?.id?.let { courseId ->
                                        coroutineScope.launch {
                                            viewModel.updateCourseTimeByGesture(
                                                courseId = courseId,
                                                targetDay = newDay,
                                                startSection = newStartSection,
                                                endSection = newEndSection
                                            )
                                        }
                                    }
                                } else {
                                    coroutineScope.launch { snackbarHostState.showSnackbar(snackbarMsg) }
                                }
                            }

                            override fun onCourseTimeAdjusted(
                                block: MergedCourseBlock,
                                newStart: Float,
                                newEnd: Float
                            ) {
                                val currentWeek = uiState.weekIndexInPager ?: 0
                                if (currentWeek in 1..uiState.totalWeeks) {
                                    block.courses.firstOrNull()?.course?.id?.let { courseId ->
                                        coroutineScope.launch {
                                            viewModel.updateCourseTimeByGesture(
                                                courseId = courseId,
                                                targetDay = block.day,
                                                startSection = newStart,
                                                endSection = newEnd
                                            )
                                        }
                                    }
                                } else {
                                    coroutineScope.launch { snackbarHostState.showSnackbar(snackbarMsg) }
                                }
                            }

                            override fun onInitiateFloatingMode(block: MergedCourseBlock) {
                                val targetCourseWrapper = block.courses.firstOrNull()
                                val currentWeek = uiState.weekIndexInPager ?: uiState.currentWeekNumber
                                if (targetCourseWrapper != null && currentWeek != null) {
                                    viewModel.enterFloatingMode(
                                        course = targetCourseWrapper,
                                        sourceWeek = currentWeek
                                    )
                                }
                            }
                        }
                    }

                    ScheduleGrid(
                        state = gridState,
                        viewState = gridViewState,
                        actions = gridActions,
                        style = composedStyle,
                        modifier = Modifier
                    )
                    } // end HorizontalPager page lambda
                    } // end else
                } // end Column
            } // end Scaffold content
            FloatingCourseBar(
                floatingCourse = floatingCourse,
                onCancelClick = { viewModel.exitFloatingMode() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }

    // 周次选择弹窗
    if (showWeekSelector) {
        WeekSelectorBottomSheet(
            totalWeeks = uiState.totalWeeks,
            currentWeek = uiState.currentWeekNumber ?: 1,
            selectedWeek = uiState.weekIndexInPager ?: (uiState.currentWeekNumber ?: 1),
            onWeekSelected = { week ->
                val currentWeekAtPage = uiState.weekIndexInPager ?: 1
                val offset = week - currentWeekAtPage
                coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + offset)
                }
                showWeekSelector = false
            },
            onDismissRequest = { showWeekSelector = false }
        )
    }

    // 课表切换弹窗
    if (showTableSwitcher) {
        CourseTablePickerDialog(
            title = stringResource(Res.string.action_select_table),
            onDismissRequest = { showTableSwitcher = false },
            onTableSelected = { table: CourseTable ->
                viewModel.switchCourseTable(table.id)
                showTableSwitcher = false
            }
        )
    }

    // 课程详情弹窗
    if (selectedBlockForDetail != null) {
        CourseDetailBottomSheet(
            block = selectedBlockForDetail!!,
            onDismissRequest = { selectedBlockForDetail = null },
            onEditClick = { courseId ->
                selectedBlockForDetail = null
                onNavigate(Destination.AddEditCourse(courseId = courseId))
            }
        )
    }
}


/**
 * 参考 sleepy 的列表视图：按天分组展示本周课程。
 *
 * 直接接收当前 pager 页的课程与日期，与 [HorizontalPager] 共用同一周次位置，
 * 因此列表视图同样支持左右滑动切周。仅渲染有课的天，空天不显示占位文案。
 */
@Composable
private fun ScheduleListView(
    pageCourses: List<MergedCourseBlock>,
    pageMondayDate: LocalDate,
    timeSlots: List<com.shangkeschedule.data.db.main.TimeSlot>,
    showWeekends: Boolean,
    firstDayOfWeek: Int,
    composedStyle: ScheduleGridStyleComposed,
    onClickedBlock: (MergedCourseBlock) -> Unit,
    onLongClickedBlock: (MergedCourseBlock) -> Unit
) {
    val weekDays = stringArrayResource(Res.array.week_days_full_names)
    val dayCount = if (showWeekends) 7 else 5
    val firstDay = firstDayOfWeek.coerceIn(1, 7)
    val orderedDays = (0 until dayCount).map { offset ->
        (firstDay - 1 + offset) % 7 + 1
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        orderedDays.forEach { day ->
            val dayBlocks = pageCourses.filter { it.day == day }
            if (dayBlocks.isEmpty()) return@forEach

            val dayOffset = (day - firstDay + 7) % 7
            val dayDate = pageMondayDate.plus(dayOffset.toLong(), DateTimeUnit.DAY)

            item(key = "day-header-$day") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = weekDays.getOrNull((day - 1).coerceAtLeast(0)).orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${dayDate.month.number.toString().padStart(2, '0')}-${dayDate.day.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            items(items = dayBlocks, key = { block -> block.hashCode() }) { block ->
                ScheduleListViewBlock(
                    block = block,
                    timeSlots = timeSlots,
                    composedStyle = composedStyle,
                    onClick = { onClickedBlock(block) },
                    onLongClick = { onLongClickedBlock(block) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduleListViewBlock(
    block: MergedCourseBlock,
    timeSlots: List<com.shangkeschedule.data.db.main.TimeSlot>,
    composedStyle: ScheduleGridStyleComposed,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val themePreset = LocalThemePreset.current
    val isTimetablePreset = themePreset == AppThemePreset.TIMETABLE
    val isSleepyPreset = themePreset == AppThemePreset.SLEEPY

    val firstCourse = block.courses.firstOrNull()?.course
    val colorIndex = firstCourse?.colorInt ?: 0
    val colorPair = composedStyle.courseColorMaps.getOrElse(colorIndex) {
        composedStyle.courseColorMaps.firstOrNull()
            ?: com.shangkeschedule.data.model.DualColor(
                light = androidx.compose.ui.graphics.Color(0xFFE0F7FA),
                dark = androidx.compose.ui.graphics.Color(0xFF006064)
            )
    }

    // 利落主题：浅色背景 + 深色色条 + 深色文字；其他主题：常规彩色背景
    val bg = if (isTimetablePreset) {
        if (isDark) colorPair.dark.copy(alpha = 0.15f) else colorPair.light
    } else {
        if (isDark) colorPair.dark else colorPair.light
    }
    val stripColor = colorPair.dark
    val textColor = if (isTimetablePreset) {
        if (isDark) Color(0xFFE0E0E0) else colorPair.dark
    } else {
        adaptiveTextColor(bg, MaterialTheme.colorScheme.onSurface)
    }
    val demotedAlpha = if (block.isVisualDemoted) 0.5f else 1f
    val cornerRadius = composedStyle.courseBlockCornerRadius
    val shape = RoundedCornerShape(cornerRadius)

    // 云舒主题加阴影；利落/经典不加
    val shadowModifier = if (isSleepyPreset && !block.isVisualDemoted) {
        Modifier.shadow(elevation = 2.dp, shape = shape, clip = false)
    } else Modifier

    // 利落主题：左侧色条用 drawBehind 绘制，不参与测量
    // （fillMaxHeight 子 Box 在宽松/无限高度约束下会失效或撑爆父容器）
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
            .graphicsLayer(alpha = demotedAlpha)
            .clip(shape)
            .background(color = bg)
            .then(stripDrawModifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {

        val startPadding = if (isTimetablePreset) 15.dp else 12.dp
        Column(
            modifier = Modifier.padding(
                start = startPadding,
                end = 12.dp,
                top = 10.dp,
                bottom = 10.dp
            )
        ) {
            block.courses.forEachIndexed { index, courseWrapper ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = textColor.copy(alpha = 0.15f)
                    )
                }

                val course = courseWrapper.course
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                val timeText = buildCourseTimeText(course, timeSlots)
                if (timeText.isNotBlank()) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor.copy(alpha = 0.82f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // 节次模式显示「第 x-y 节」；自定义时间课程不显示节次
                if (course.customStartTime == null && course.startSection != null && course.endSection != null) {
                    Text(
                        text = stringResource(
                            Res.string.course_section_range,
                            course.startSection.toString(),
                            course.endSection.toString()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                val infoParts = buildList {
                    if (course.position.isNotBlank()) {
                        add(if (composedStyle.removeLocationAt) course.position else "@${course.position}")
                    }
                    if (!composedStyle.hideTeacher && course.teacher.isNotBlank()) add(course.teacher)
                }
                if (infoParts.isNotEmpty()) {
                    Text(
                        text = infoParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.78f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * 根据 TimeSlot 或自定义时间生成列表视图中的时间字符串。
 */
private fun buildCourseTimeText(
    course: com.shangkeschedule.data.db.main.Course,
    timeSlots: List<com.shangkeschedule.data.db.main.TimeSlot>
): String {
    val customStart = course.customStartTime
    val customEnd = course.customEndTime
    if (customStart != null && customEnd != null) return "$customStart - $customEnd"

    val first = course.startSection ?: return ""
    val last = course.endSection ?: return ""
    val startSlot = timeSlots.find { it.number == first }
    val endSlot = timeSlots.find { it.number == last }
    return if (startSlot != null && endSlot != null) {
        "${startSlot.startTime} - ${endSlot.endTime}"
    } else {
        ""
    }
}
