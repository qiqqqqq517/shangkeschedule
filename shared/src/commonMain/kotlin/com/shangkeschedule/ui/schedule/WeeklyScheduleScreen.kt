package com.shangkeschedule.ui.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.shangkeschedule.Destination
import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.model.schedule_style.ScheduleModeProto
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.data.time.currentDateFlow
import com.shangkeschedule.navigation.AddEditCourseChannel
import com.shangkeschedule.navigation.PresetCourseData
import com.shangkeschedule.ui.components.AdaptiveNavigationScaffold
import com.shangkeschedule.ui.components.AppSnackbarHost
import com.shangkeschedule.ui.components.CourseTablePickerDialog
import com.shangkeschedule.ui.components.TelegramMenu
import com.shangkeschedule.ui.components.TelegramMenuDivider
import com.shangkeschedule.ui.components.TelegramMenuItem
import com.shangkeschedule.ui.components.rememberFabPressedScale
import com.shangkeschedule.ui.schedule.components.CourseDetailBottomSheet
import com.shangkeschedule.ui.schedule.components.FloatingCourseBar
import com.shangkeschedule.ui.schedule.components.ScheduleGrid
import com.shangkeschedule.ui.schedule.components.ScheduleGridActions
import com.shangkeschedule.ui.schedule.components.ScheduleGridStyleComposed
import com.shangkeschedule.ui.schedule.components.ScheduleGridViewState
import com.shangkeschedule.ui.schedule.components.WeekSelectorBottomSheet
import com.shangkeschedule.ui.schedule.components.rememberScheduleGridState
import com.shangkeschedule.ui.schedule.components.adaptiveTextColor
import com.shangkeschedule.ui.schedule.components.scaleAlpha
import com.shangkeschedule.ui.theme.AppAlpha
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.LocalIsDarkTheme

import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.appColorTokens
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.liquidGlass
import com.shangkeschedule.ui.theme.softFeatherRim
import com.shangkeschedule.ui.theme.softShadow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
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
import shangkeschedule.shared.generated.resources.a11y_back_to_current_week
import shangkeschedule.shared.generated.resources.action_select_table
import shangkeschedule.shared.generated.resources.arrow_drop_down_24px
import shangkeschedule.shared.generated.resources.calendar_today_24px
import shangkeschedule.shared.generated.resources.format_week_display
import shangkeschedule.shared.generated.resources.label_view_mode_list
import shangkeschedule.shared.generated.resources.label_view_mode_week
import shangkeschedule.shared.generated.resources.more_vert_24px
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.item_more_options
import shangkeschedule.shared.generated.resources.class_24px
import shangkeschedule.shared.generated.resources.item_school_system_import
import shangkeschedule.shared.generated.resources.palette_24px
import shangkeschedule.shared.generated.resources.school_24px
import shangkeschedule.shared.generated.resources.schedule_24px
import shangkeschedule.shared.generated.resources.title_manage_course_tables
import shangkeschedule.shared.generated.resources.title_add_course
import shangkeschedule.shared.generated.resources.item_appearance_settings
import shangkeschedule.shared.generated.resources.item_time_slot_customization
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
import kotlin.math.sin
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

    // today 是可刷新状态：订阅 currentDateFlow，跨天（午夜）自动更新，
    // 修复长驻页面跨午夜后「今日高亮 / 周次标题 / Pager 锚定周」停留在昨天的问题
    var today by remember {
        mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date)
    }
    LaunchedEffect(Unit) {
        currentDateFlow().collect { newDate -> today = newDate }
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

    LaunchedEffect(pagerState.currentPage, uiState.firstDayOfWeek, today) {
        fun syncPagerDate(pageIndex: Int) {
            val offsetWeeks = (pageIndex - INFINITE_PAGER_CENTER).toLong()
            val firstDay = DayOfWeek(uiState.firstDayOfWeek)
            val thisMonday = getPreviousOrSameDay(today, firstDay)
            val targetMonday = thisMonday.plus(offsetWeeks * 7, DateTimeUnit.DAY)
            viewModel.updatePagerDate(targetMonday)
        }

        // effect 启动时立即同步，不能只依赖 snapshotFlow 的后续发射
        syncPagerDate(pagerState.currentPage)
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { pageIndex -> syncPagerDate(pageIndex) }
    }

    // UI 交互控制弹窗标志位
    var showWeekSelector by remember { mutableStateOf(false) }
    var showTableSwitcher by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var isGridHolding by remember { mutableStateOf(false) }
    var selectedBlockForDetail by remember { mutableStateOf<MergedCourseBlock?>(null) }

    // 悬浮圆钮与玻璃底栏同步隐藏（同一套下滑手势语义；v3.26.0 起时长/缓动读全局动效令牌，
    // 与底栏同源同值——关掉「底栏隐藏」分组时两端一起瞬切）
    val motion = LocalAppMotion.current
    var isNavBarHidden by remember { mutableStateOf(false) }
    val backToWeekHideSpec = remember(motion) {
        tween<Float>(durationMillis = motion.tokens.hideDurationMs, easing = motion.tokens.hideEasing)
    }
    val backToWeekHideFraction by animateFloatAsState(
        targetValue = if (isNavBarHidden) 1f else 0f,
        animationSpec = backToWeekHideSpec,
        label = "backToCurrentWeekHide"
    )

    // 圆钮停靠位：紧贴玻璃底栏容器上沿之上（语义与 AdaptiveNavigationScaffold 的
    // barInsetBottom 一致：胶囊高 touchMin+14 + 上下 navBarBottom + 系统手势区）；
    val density = LocalDensity.current
    // 宽屏 Rail 形态无底部胶囊栏，直接贴屏幕右下，无需顶起预留高度。
    val isRailLayout = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
        currentWindowAdaptiveInfo()
    ) == NavigationSuiteType.NavigationRail
    val navBarReserve = if (isRailLayout) {
        0.dp
    } else {
        appSpacing().touchMin + 14.dp + appSpacing().navBarBottom * 2 +
            (WindowInsets.navigationBars.getBottom(density) / density.density).dp
    }

    val composedStyle = uiState.style

    val floatingCourse = uiState.floatingCourse

    // 「回到本周」判定：无限分页以 INFINITE_PAGER_CENTER 承载「本周」，
    // 因此页码离开中心页即代表用户已滑到非本周课程（拖动过程中即时显隐）。
    // 课程挂起态（floatingCourse）下让位给 FloatingCourseBar，不显示圆钮。
    val isOnCurrentWeekPage by remember {
        derivedStateOf { pagerState.currentPage == INFINITE_PAGER_CENTER }
    }
    val showBackToCurrentWeek = floatingCourse == null && !isOnCurrentWeekPage

    // 悬浮面板玻璃：主内容 hazeSource（含壁纸），周选择面板背板模糊
    val hazeState = rememberHazeState()

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

    val scheduleViewMode by viewModel.scheduleViewModeState.collectAsStateWithLifecycle()

    AdaptiveNavigationScaffold(
        currentDestination = Destination.CourseSchedule,
        onTabSelected = { dest -> onNavigate(dest) },
        showNavigation = floatingCourse == null,
        isTransparent = composedStyle.backgroundImagePath.isNotEmpty(),
        contentColor = customTextColor,
        onNavBarHiddenChange = { hidden -> isNavBarHidden = hidden },
        // P2-6 去除独立的 navigationModifier 滚动隐藏：AdaptiveNavigationScaffold 已内置
        // 统一的滚动隐藏连接（下滑累积隐藏 / 上滑 / 切 Tab / 顶部动画恢复），
        // 此处再叠加 collapseFraction 平移+alpha 会和内置隐藏产生双重位移/淡出叠加。
        // 统一由 Scaffold 的 NestedScrollConnection 接管，避免底栏位移叠加异常。
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
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
                // 白块修复：无壁纸时此页底色走 pageBg（原 Transparent 透出窗口白色，
                // 底栏下方留白带呈现为「白块」）；壁纸模式保持透明以透出壁纸。
                containerColor = if (composedStyle.backgroundImagePath.isEmpty()) {
                    appColors().pageBg
                } else {
                    Color.Transparent
                },
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            // 周切换入口：Telegram 胶囊形态（浅灰胶囊底，含标题 + 下拉箭头）
                            val hasBackgroundImage = composedStyle.backgroundImagePath.isNotEmpty()
                            val weekChipBg = if (hasBackgroundImage) {
                                // 功能色（豁免声明）：壁纸上的半透明黑遮罩，保证周次胶囊文字可读，不随主题
                                Color.Black.copy(alpha = 0.25f)
                            } else {
                                appColors().inputBg
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(appShapes().capsule)
                                    .background(weekChipBg)
                                    .clickable {
                                        if (!uiState.isSemesterSet || uiState.semesterStartDate == null) {
                                            onNavigate(Destination.Settings)
                                        } else {
                                            showWeekSelector = true
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = appType().sectionTitle),
                                    fontWeight = FontWeight.SemiBold,
                                    color = customTextColor
                                )
                                Icon(
                                    imageVector = vectorResource(Res.drawable.arrow_drop_down_24px),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .offset(y = (-2).dp),
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
                            // ⋮ 溢出菜单（Telegram 形态）：承载既有操作入口，不新增流程
                            IconButton(onClick = { showOverflowMenu = !showOverflowMenu }) {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.more_vert_24px),
                                    contentDescription = stringResource(Res.string.item_more_options),
                                    tint = customTextColor
                                )
                                TelegramMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    TelegramMenuItem(
                                        icon = vectorResource(Res.drawable.school_24px),
                                        text = stringResource(Res.string.item_school_system_import),
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigate(Destination.SchoolSelectionListScreen())
                                        }
                                    )
                                    TelegramMenuDivider()
                                    TelegramMenuItem(
                                        icon = vectorResource(Res.drawable.add_24px),
                                        text = stringResource(Res.string.title_add_course),
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigate(Destination.AddEditCourse())
                                        }
                                    )
                                    TelegramMenuDivider()
                                    TelegramMenuItem(
                                        icon = vectorResource(Res.drawable.class_24px),
                                        text = stringResource(Res.string.title_manage_course_tables),
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigate(Destination.ManageCourseTables)
                                        }
                                    )
                                    TelegramMenuItem(
                                        icon = vectorResource(Res.drawable.palette_24px),
                                        text = stringResource(Res.string.item_appearance_settings),
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigate(Destination.AppearanceSettings)
                                        }
                                    )
                                    TelegramMenuItem(
                                        icon = vectorResource(Res.drawable.schedule_24px),
                                        text = stringResource(Res.string.item_time_slot_customization),
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigate(Destination.TimeSlotSettings)
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                        scrollBehavior = scrollBehavior
                    )
                },
                snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) }
            ) { scaffoldInnerPadding ->

                val dynamicBottomPadding = remember(innerPadding, floatingCourse) {
                    if (floatingCourse != null) {
                        0.dp
                    } else {
                        // 底部占位修复：innerPadding.bottom = 玻璃底栏完整占位（已含系统手势区，
                        // 且随底栏隐藏动画同步收缩）。原实现再叠加 scaffoldInnerPadding.bottom
                        // 造成系统 inset 双算（底部留白带偏高），又与顶栏 collapseFraction 联动——
                        // 底栏不再随 collapse 平移后会出现玻璃条压住网格末行。
                        innerPadding.calculateBottomPadding()
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = scaffoldInnerPadding.calculateStartPadding(LayoutDirection.Ltr),
                            top = scaffoldInnerPadding.calculateTopPadding(),
                            end = scaffoldInnerPadding.calculateEndPadding(LayoutDirection.Ltr)
                            // 底部遮挡修复：不再整体缩进——网格视口延伸到玻璃底栏之下（与「我的」页
                            // 内容穿越形态一致），底部留白改由各页滚动内容内部承担（bottomInset）
                        )
                ) {
                    // v3.26.0 C+.16：切周玻璃滑动过渡层与 Pager 同容器叠放（不拦截触摸）
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1,
                            userScrollEnabled = !isGridHolding
                        ) { pageIndex ->

                    val pageMondayDate = remember(pageIndex, uiState.firstDayOfWeek, today) {
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

                    val pageTodayIndex = remember(pageMondayDate, today) {
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
                            bottomInset = dynamicBottomPadding,
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
                                                // 23 点点格不再回绕到次日 00:00（会产生跨天负时长课）：
                                                // 末小时收在 23:00–23:59
                                                val endHour = if (startHour >= 23) 23 else startHour + 1

                                                val startTimeStr = "${startHour.toString().padStart(2, '0')}:00"
                                                val endTimeStr = if (startHour >= 23) "23:59" else "${endHour.toString().padStart(2, '0')}:00"

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
                        bottomInset = dynamicBottomPadding,
                        modifier = Modifier
                    )
                    } // end HorizontalPager page lambda
                    } // end else

                        // C+.16 周切换玻璃滑动过渡：页码落定时一道玻璃光泽斜扫课程区
                        WeekPagerGlassSheen(pagerState = pagerState, modifier = Modifier.fillMaxSize())
                    } // end 叠放 Box
                } // end Column
            } // end Scaffold content
        } // end 背板内容层（hazeSource）

        // 悬浮玻璃件层：必须与上面的 hazeSource **平级**，不能写成它的子节点。
        // haze 计算背板区域时会按「area.zIndex < 祖先 source 的 zIndex」过滤：玻璃件若位于
        // 自身 source 子树内，唯一的背板 area 会被判为不通过（Included=false），于是 blur 与
        // tint 一并跳过绘制——表现为「底栏雾化、圆钮清晰」，即使 blurRadius 完全相同。
        // 真机日志实锤：底栏 Included=true / Size(132) 圆钮 Included=false。
        Box(modifier = Modifier.fillMaxSize()) {
            FloatingCourseBar(
                floatingCourse = floatingCourse,
                onCancelClick = { viewModel.exitFloatingMode() },
                hazeState = hazeState,
                // 玻璃上的文字/图标跟随页面自定义文字色（壁纸模式下保证可读）
                contentColor = customTextColor,
                isTransparent = composedStyle.backgroundImagePath.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
            BackToCurrentWeekFab(
                visible = showBackToCurrentWeek,
                hideFraction = backToWeekHideFraction,
                hideRange = navBarReserve + appSpacing().cardGap + appSpacing().touchMin,
                hazeState = hazeState,
                hasWallpaper = composedStyle.backgroundImagePath.isNotEmpty(),
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(INFINITE_PAGER_CENTER)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = appSpacing().navBarHorizontal,
                        bottom = navBarReserve + appSpacing().cardGap
                    )
            )
        }
    }

    // 周次选择弹窗（毛玻璃面板）
    if (showWeekSelector) {
        WeekSelectorBottomSheet(
            hazeState = hazeState,
            totalWeeks = uiState.totalWeeks,
            currentWeek = uiState.currentWeekNumber ?: 1,
            selectedWeek = uiState.weekIndexInPager ?: (uiState.currentWeekNumber ?: 1),
            onWeekSelected = { week ->
                // P1-10 周次选择器错位修复：weekIndexInPager 尚未就绪时，不能假定当前页是第 1 周；
                // 应按无限分页模型换算：周次 = 当前周 + (页码 - 中心页)，否则从非首页选择会跳错周。
                val currentWeekAtPage = uiState.weekIndexInPager
                    ?: (uiState.currentWeekNumber?.plus(pagerState.currentPage - INFINITE_PAGER_CENTER) ?: 1)
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
 * 「回到本周」悬浮圆钮：仅在用户滑动到非本周课程时出现（右下角小圆圈），
 * 点击回到本周所处页。
 *
 * 隐藏行为与玻璃底栏一致：[hideFraction] 由 AdaptiveNavigationScaffold 的滚动隐藏
 * 状态驱动，圆钮随之下沉 + 淡出：[hideRange] 覆盖「底栏预留高度 + 自身高度」，
 * 保证隐藏动画结束时完全移出屏幕，不会残留半截圆钮。
 */
@Composable
private fun BackToCurrentWeekFab(
    visible: Boolean,
    hideFraction: Float,
    hideRange: Dp,
    hazeState: HazeState,
    hasWallpaper: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hideRangePx = with(LocalDensity.current) { hideRange.toPx() }
    // v3.26.0 动效收口：出现/消失读全局令牌；关掉「玻璃悬浮件」分组 ⇒ 瞬切
    val fabMotion = LocalAppMotion.current
    val glassAnimEnabled = fabMotion.isEnabled(AnimationGroup.GLASS_FLOATING)
    Box(
        modifier = modifier.graphicsLayer {
            translationY = hideFraction * hideRangePx
            // 过冲缓动会让 hideFraction 短暂 >1，alpha 必须钳制（负 padding 崩溃同源教训）
            alpha = (1f - hideFraction).coerceIn(0f, 1f)
        }
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = if (glassAnimEnabled) {
                fadeIn(fabMotion.tokens.emphasisFadeSpec) +
                    scaleIn(fabMotion.tokens.emphasisScaleSpec, initialScale = fabMotion.tokens.emphasisInitialScale)
            } else {
                EnterTransition.None
            },
            exit = if (glassAnimEnabled) {
                fadeOut(fabMotion.tokens.emphasisFadeSpec) +
                    scaleOut(fabMotion.tokens.emphasisScaleSpec, targetScale = fabMotion.tokens.emphasisInitialScale)
            } else {
                ExitTransition.None
            }
        ) {
            val interaction = remember { MutableInteractionSource() }
            val pressedScale = rememberFabPressedScale(interaction)
            Box(
                modifier = Modifier
                    .size(appSpacing().touchMin)
                    .graphicsLayer {
                        scaleX = pressedScale
                        scaleY = pressedScale
                    }
                    // 液态玻璃：与玻璃底栏胶囊同源（连续统一的玻璃语言）。
                    // v3.24.7：blur 统一取 LiquidGlassBlurRadius，此处不再单独写死。
                    .liquidGlass(
                        hazeState = hazeState,
                        shape = CircleShape,
                        containerColor = appColors().inputBg,
                        // 壁纸模式下玻璃透出壁纸并补一层暗 scrim，保证图标可读
                        isTransparent = hasWallpaper,
                        shadowElevation = 8.dp
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(bounded = true),
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.calendar_today_24px),
                    contentDescription = stringResource(Res.string.a11y_back_to_current_week),
                    tint = appColors().primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * v3.26.0 C+.16 周切换玻璃滑动过渡：
 * 页码落定（ currentPage 变化）时，一道斜向玻璃光泽扫过课程区，
 * 用「光」而不是位移来强化翻页的方向感——不干扰 Pager 原生手势。
 *
 * 规则：
 * - 仅页码变化触发（首次组合不触发），一次变化只扫一遍；
 * - 时长/缓动读全局令牌 entranceDurationMs/entranceEasing（随风格变化）；
 * - 关掉「周翻页」分组或时长为 0 ⇒ 整层不组合，零开销。
 */
@Composable
private fun WeekPagerGlassSheen(
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val motion = LocalAppMotion.current
    val enabled = motion.isEnabled(AnimationGroup.WEEK_PAGER) &&
        motion.tokens.entranceDurationMs > 0
    if (!enabled) return

    // 柔绘（SOFT）：斜向扫光是镜面/玻璃语言（明确方向 + 明确边界 + 瞬时高亮），
    // 落在雾面薄涂底上会切出一条可见"锋面"，与柔绘「化开」的材质定义直接对立
    // （详见《交互动效审查_三主题》P0）。柔绘改为**无方向的整屏换气**：
    // 落定后整体亮度做一次 600ms 正弦式起伏，幅度 3%，没有任何边界与方向。
    val isSoft = LocalThemePreset.current == AppThemePreset.SOFT
    // 深色档换气不叠白（见下方绘制分支）
    val isDark = LocalIsDarkTheme.current

    // 首次组合（App 启动落在本周页）不算「切周」，不扫光
    var hasSettledOnce by rememberSaveable { mutableStateOf(false) }
    val sheenFraction = remember { Animatable(0f) }
    var sheenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        if (!hasSettledOnce) {
            hasSettledOnce = true
            return@LaunchedEffect
        }
        sheenVisible = true
        sheenFraction.snapTo(0f)
        sheenFraction.animateTo(
            1f,
            tween(
                durationMillis = if (isSoft) 600 else motion.tokens.entranceDurationMs,
                // 柔绘：fraction 必须走**线性**——sin(π·f) 本身已是单峰曲线，外面再套一条
                // ease-in-out 会把亮度峰值压进中段极窄区间，观感变成"停顿—突亮—停顿"
                // 三段（正是柔绘规格明令禁止的"折点"）。
                easing = if (isSoft) LinearEasing else motion.tokens.entranceEasing
            )
        )
        sheenVisible = false
    }

    if (sheenVisible) {
        val fraction = sheenFraction.value
        Box(
            modifier = modifier
                // 纯绘制层：不消费任何指针事件，手势完全穿透到 Pager / 课程格
                .drawBehind {
                    if (isSoft) {
                        // 柔绘档：整屏换气 —— sin 曲线一次明度起伏，无方向、无边界。
                        // 深色档**不叠白**：暖炭薄涂底上一层白蒙版会被读成"打闪"，
                        // 改为极淡的压暗；浅色档提亮幅度也从 0.03 降到 0.018
                        // （柔绘底色本身已接近白，白上加白的容忍度更低）。
                        val breathe = sin(fraction * Math.PI).toFloat()
                        val breatheColor = if (isDark) {
                            Color.Black.copy(alpha = 0.020f * breathe)
                        } else {
                            Color.White.copy(alpha = 0.018f * breathe)
                        }
                        drawRect(color = breatheColor)
                    } else {
                        // Apple HIG 风格：收窄光带（屏幕宽 28%）、降低峰值透明度（0.07），
                        // 效果克制如镜面反光，而非扫光特效
                        val bandWidth = size.width * 0.28f
                        val travel = size.width + bandWidth * 2f
                        val x = -bandWidth + travel * fraction
                        drawRect(
                            brush = Brush.linearGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.5f to Color.White.copy(alpha = 0.07f),
                                    1f to Color.Transparent
                                ),
                                start = Offset(x, 0f),
                                end = Offset(x + bandWidth, size.height)
                            )
                        )
                    }
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
    bottomInset: Dp = 0.dp,
    onClickedBlock: (MergedCourseBlock) -> Unit,
    onLongClickedBlock: (MergedCourseBlock) -> Unit
) {
    val weekDays = stringArrayResource(Res.array.week_days_full_names)
    val dayCount = if (showWeekends) 7 else 5
    val firstDay = firstDayOfWeek.coerceIn(1, 7)
    val orderedDays = (0 until dayCount).map { offset ->
        (firstDay - 1 + offset) % 7 + 1
    }
    // v3.26.0 C+.17 列表块入场错峰：按「天序 + 天内序」递增延迟
    // （在 LazyColumn 外读取 LocalAppMotion：LazyListScope 不是 composable 作用域）
    val listEntranceMotion = LocalAppMotion.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // 底部留白走 contentPadding：列表视口延伸到玻璃底栏之下，末项可滚动到导航条上方
        contentPadding = PaddingValues(bottom = bottomInset),
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
                        color = appColors().primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${dayDate.month.number.toString().padStart(2, '0')}-${dayDate.day.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.labelMedium,
                        color = appColors().textSecondary
                    )
                }
            }

            // v3.26.0 C+.17 列表块入场错峰：按「天序 + 天内序」递增延迟
            itemsIndexed(items = dayBlocks, key = { _, block -> block.hashCode() }) { blockIdx, block ->
                ScheduleListViewBlock(
                    block = block,
                    timeSlots = timeSlots,
                    composedStyle = composedStyle,
                    // v3.43.0：错峰总延迟设上限，避免长列表后排条目等待过久
                    entranceDelayMs = (listEntranceMotion.tokens.entranceStaggerMs * (dayOffset * 3 + blockIdx))
                        .coerceAtMost(listEntranceMotion.tokens.entranceStaggerCapMs),
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
    entranceDelayMs: Int = 0,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val isSoft = LocalThemePreset.current == AppThemePreset.SOFT
    val firstCourse = block.courses.firstOrNull()?.course
    val colorIndex = firstCourse?.colorInt ?: 0
    val colorPair = composedStyle.courseColorMaps.getOrElse(colorIndex) {
        composedStyle.courseColorMaps.firstOrNull() ?: ScheduleGridStyle.DEFAULT_COLOR_MAPS[0]
    }

    // 背景按「课程块不透明度」乘算：颜色池自带 alpha（淡底令牌）必须保留，只在其上缩放，
    // 与网格视图 CourseBlock 的取色方式保持一致。
    val bg = (if (isDark) colorPair.dark else colorPair.light).scaleAlpha(composedStyle.courseBlockAlpha)
    val stripColor = colorPair.dark
    val textColor = adaptiveTextColor(bg, MaterialTheme.colorScheme.onSurface)
    val demotedAlpha = if (block.isVisualDemoted) AppAlpha.dimmed else 1f
    val cornerRadius = composedStyle.courseBlockCornerRadius
    val shape = RoundedCornerShape(cornerRadius)

    // 课程块投影：通透（iOS 26）与书卷均不加投影，靠材质与留白分层；
    // 柔绘：列表行改用软模糊投影（elevation 8dp）替代 elevation 硬边投影，并加羽化描边环。
    val shadowModifier = if (isSoft) {
        Modifier.softShadow(shape = shape, elevation = 8.dp)
    } else {
        Modifier
    }

    // 左侧色条用 drawBehind 绘制，不参与测量
    // （fillMaxHeight 子 Box 在宽松/无限高度约束下会失效或撑爆父容器）
    // 已删除利落主题色条支持，iOS 主题由 CourseBlock 统一处理
    val stripDrawModifier = Modifier

    // v3.26.0 C+.17 页面入场错峰淡入：rememberSaveable 记住结果——LazyColumn 会回收
    // 滚出视口的 item，普通 remember 会导致每次滚回来都重播淡入；存入 saveable 后
    // 滚动往返与页面重进都不重播。关掉「页面入场」分组 ⇒ 直接显示不动画
    val entranceMotion = LocalAppMotion.current
    val entranceEnabled = entranceMotion.isEnabled(AnimationGroup.PAGE_ENTRANCE) &&
        entranceMotion.tokens.entranceDurationMs > 0
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
            durationMillis = entranceMotion.tokens.entranceDurationMs,
            easing = entranceMotion.tokens.entranceEasing
        ),
        label = "listBlockEntrance"
    )
    val entranceSlidePx = with(LocalDensity.current) { entranceMotion.tokens.entranceSlideDp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(shadowModifier)
            .graphicsLayer {
                val entranceAlpha = entranceMotion.tokens.entranceInitialAlpha +
                    (1f - entranceMotion.tokens.entranceInitialAlpha) * entranceFraction
                alpha = (demotedAlpha * entranceAlpha).coerceIn(0f, 1f)
                translationY = entranceSlidePx * (1f - entranceFraction)
            }
            .clip(shape)
            .background(color = bg)
            .then(stripDrawModifier)
            // 柔绘：羽化描边环替代任何实色描边（无锐利硬边缘）
            .then(if (isSoft) Modifier.softFeatherRim(shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {

        val startPadding = 12.dp
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
                        // 柔绘：块内分隔线降到 0.5dp 且更淡（低对比），避免在晕染底上出现硬线
                        thickness = if (isSoft) 0.5.dp else 1.dp,
                        color = textColor.copy(alpha = if (isSoft) 0.10f else 0.15f)
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
