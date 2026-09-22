package com.shangkeschedule.ui.today

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import com.shangkeschedule.ui.components.ThemedLoadingIndicator
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.alpha
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
import com.shangkeschedule.data.model.NextCardMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import com.shangkeschedule.data.db.main.TodoItem
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.ui.components.AdaptiveNavigationScaffold
import com.shangkeschedule.ui.components.AppCheckboxIndicator
import com.shangkeschedule.ui.components.AppFab
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import com.shangkeschedule.ui.components.AppLoading
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.softFeatherRim
import com.shangkeschedule.ui.theme.softShadow
import com.shangkeschedule.ui.theme.iosGlassRim
import com.shangkeschedule.ui.theme.iosUiSans
import com.shangkeschedule.ui.theme.claudeReadingSerif
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.rememberStatusFadeAlpha
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.MotionPressMode
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.components.rememberAppHaptics
import com.shangkeschedule.ui.schedule.components.resolveCourseBlockColors
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.date_format_year_month_day
import shangkeschedule.shared.generated.resources.location_on_24px
import shangkeschedule.shared.generated.resources.person_24px
import shangkeschedule.shared.generated.resources.status_semester_ended
import shangkeschedule.shared.generated.resources.text_courses_count
import shangkeschedule.shared.generated.resources.text_no_courses_today
import shangkeschedule.shared.generated.resources.title_current_week
import shangkeschedule.shared.generated.resources.title_semester_not_set
import shangkeschedule.shared.generated.resources.title_today_courses
import shangkeschedule.shared.generated.resources.title_today_schedule
import shangkeschedule.shared.generated.resources.title_vacation_until_start
import shangkeschedule.shared.generated.resources.todo_count_format
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
import shangkeschedule.shared.generated.resources.today_claude_countdown_label
import shangkeschedule.shared.generated.resources.today_claude_meta_credit
import shangkeschedule.shared.generated.resources.today_claude_meta_room
import shangkeschedule.shared.generated.resources.today_claude_meta_teacher
import shangkeschedule.shared.generated.resources.today_claude_meta_time
import shangkeschedule.shared.generated.resources.today_claude_next_label
import shangkeschedule.shared.generated.resources.next_card_label_agenda
import shangkeschedule.shared.generated.resources.next_card_today_ended
import shangkeschedule.shared.generated.resources.today_claude_note_hours
import shangkeschedule.shared.generated.resources.today_claude_note_type
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
import shangkeschedule.shared.generated.resources.today_todos_section
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
        // v3.49.1：复用脚手架录制的背景快照，不再自建第二份（避免全页重复录制 / 玻璃取样玻璃）。
        // v3.51.2 诊断实验：置 null 断开「页内玻璃件采样包含自身的页面 backdrop」这条边。
        val pageGlassBackdrop = null // v3.51.2 诊断实验：断开页内玻璃件采样页面 backdrop 的边
        Scaffold(
            // 应用外层底部导航预留的内边距，避免内容被底栏遮挡
            modifier = Modifier,
            // P2-1 内外 Scaffold 系统栏 inset 双计数修复：
            // 底部导航栏 inset 已由外层 AdaptiveNavigationScaffold（barInsetBottom）统一预留，
            // 内层再套默认 contentWindowInsets=navigationBars 会导致底部内边距叠加，列表偏高。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // 无 topBar / FAB：三主题今日页均自带页头（周次胶囊 + 日期大字），M3 顶栏与
            // 待办 FAB 自 v3.41.1（最后一个无自带页头的主题下线）起便不再渲染；
            // 待办的创建/编辑统一由「日程」页承载，此处仅只读展示 + 勾选当日待办。
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .hazeSource(hazeState)
            ) {
                // Loading→Success 溶解过渡（v3.54.0）：contentKey 按状态类型区分，
                // Success 内部更新（每分钟 tick）不触发转场
                val stateFadeMs = LocalAppMotion.current.tokens.statusFadeMs
                AnimatedContent(
                    targetState = uiState,
                    contentKey = { it::class },
                    transitionSpec = {
                        fadeIn(tween(stateFadeMs)) togetherWith fadeOut(tween(stateFadeMs))
                    },
                    label = "todayStateSwap"
                ) { todayState ->
                    when (val state = todayState) {
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
                                // 显隐走 AnimatedVisibility（v3.54.0）：缩放+淡入淡出，
                                // 取代瞬现瞬失；减弱动态时 AnimatedVisibility 的入场按全局令牌自动退化
                                AnimatedVisibility(
                                    visible = refreshing || pullProgress > 0.01f,
                                    enter = fadeIn() + scaleIn(initialScale = 0.6f),
                                    exit = fadeOut() + scaleOut(targetScale = 0.6f)
                                ) {
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
                                onToggleEventDone = viewModel::toggleEventDone,
                                // 待办的创建/编辑统一由「日程」页承载（今日页只读展示 + 勾选）
                                onEditTodo = { onNavigate(Destination.Schedule) },
                                onNavigateWeekly = { onNavigate(Destination.CourseSchedule) },
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
    }
}

@Composable
fun TodayContent(
    state: TodayUiState.Success,
    bottomInset: Dp,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    onToggleTodo: (String, Boolean) -> Unit,
    onToggleEventDone: (String, Boolean) -> Unit,
    onEditTodo: (TodoItem) -> Unit,
    onNavigateWeekly: () -> Unit = {},
    onEditCourse: (String) -> Unit = {},
    // 仅用于视觉回归预览：覆盖主题预设，避免预览宿主必须走完整 CompositionLocal 链
    presetOverride: AppThemePreset? = null,
    /** 视觉回归专用：冻结「现在」。非 null 时跳过系统时钟与每分钟刷新（下节课卡/已结束态不再随真实时间漂移，像素比对才可复现）。 */
    nowOverride: LocalTime? = null
) {
    var currentTime by remember(nowOverride) {
        mutableStateOf(
            nowOverride ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
        )
    }

    // 每分钟刷新一次，让「下节课」卡片的倒计时保持更新
    // （倒计时精度为分钟级，60s 足够；此前 30s 会使 TodayContent 全作用域每 30s 重组一次）
    LaunchedEffect(nowOverride) {
        if (nowOverride != null) return@LaunchedEffect
        while (true) {
            delay(60_000)
            currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
        }
    }

    val scrollState = rememberLazyListState()
    val themePreset = presetOverride ?: LocalThemePreset.current

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

        // ===== 三主题共用骨架（v3.56.0 骨架统一）：页头 + 下节课卡 + 课程时间轴 +
        // 日程事件 + 明日预览。三主题信息架构逐项一致（同字段、同顺序、同位置），
        // 材质与少量文案差异全部经 [TodaySkin] 注入；书卷分支对齐设计包 pages/今日日程.html。
        val skin = when (themePreset) {
            AppThemePreset.SOFT -> SoftTodaySkin
            AppThemePreset.IOS -> Ios26TodaySkin
            AppThemePreset.CLAUDE -> ClaudeTodaySkin
        }
        TodayThemeContent(
            skin = skin,
            state = state,
            gridStyle = gridStyle,
            isDark = isDark,
            now = currentTime,
            bottomInset = bottomInset,
            statusText = subTitle,
            dateText = dateStr,
            scrollState = scrollState,
            onOpenWeeklySchedule = onNavigateWeekly,
            onEditCourse = onEditCourse,
            onToggleEventDone = onToggleEventDone,
            onToggleTodo = onToggleTodo,
            onEditTodo = onEditTodo
        )
        return@Column

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
private val TodayTomorrowAccentWidth = 4.dp

private val ClaudeLilac50 = Color(0xFFF5F2FF)
private val ClaudeLilac100 = Color(0xFFEBE5FF)
private val ClaudeLilac500 = Color(0xFF9C87F5)
private val ClaudeLilac700 = Color(0xFF6B5BB5)
private val ClaudeLilac800 = Color(0xFF524491)
private val ClaudePeach50 = Color(0xFFFFF0ED)
private val ClaudePeach500 = Color(0xFFE88672)
private val ClaudePeach700 = Color(0xFFC45F4A)
private val ClaudeBrand400 = Color(0xFFD6866A)

private data class TodayTimelinePalette(
    val gradient: List<Color>,
    val border: Color,
    val title: Color,
    val meta: Color,
    val badgeBg: Color,
    val badgeFg: Color,
    val dot: Color
)

@Composable
private fun todayClaudePalette(peach: Boolean): TodayTimelinePalette {
    val colors = appColors()
    val isDark = LocalIsDarkTheme.current
    return when {
        peach && isDark -> TodayTimelinePalette(
            gradient = listOf(Color(0xFF3A2622), Color(0xFF31211E)),
            border = Color(0x26E88672),
            title = Color(0xFFF7B6A8),
            meta = Color(0xFFC79A90),
            badgeBg = Color(0x33E88672),
            badgeFg = Color(0xFFF7B6A8),
            dot = ClaudePeach500
        )

        peach -> TodayTimelinePalette(
            gradient = listOf(ClaudePeach50, Color(0xFFFFF8F6)),
            border = Color(0x1FE88672),
            title = colors.textPrimary,
            meta = colors.textSecondary,
            badgeBg = Color(0x26E88672),
            badgeFg = ClaudePeach700,
            dot = ClaudePeach500
        )

        isDark -> TodayTimelinePalette(
            gradient = listOf(Color(0xFF2E2749), Color(0xFF272240)),
            border = Color(0x269C87F5),
            title = Color(0xFFC4B8E8),
            meta = Color(0xFF9A93B8),
            badgeBg = Color(0x339C87F5),
            badgeFg = Color(0xFFC4B8E8),
            dot = ClaudeBrand400
        )

        else -> TodayTimelinePalette(
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

/**
 * 今日页三主题差异参数（v3.56.0 部件统一）。
 *
 * 今日页骨架与全部卡面部件只有一套实现（Today* 系列共享函数）；书卷/柔绘/通透
 * 的渲染差异全部收敛为本类的显式字段：点缀色梯、材质开关（柔绘 softShadow /
 * softFeatherRim）、通透玻璃描边、事件语义色、字体、文案资源与时间轴调色板。
 * 任何字段的取值都与旧三份拷贝逐位对应——像素回归以此为准。
 */
private class TodayCardStyle(
    /** 下节课卡亮色档点缀色梯（50/100/500/700/800）。暗色档不在此类：TodayNextClassCard 内三主题共用硬编码书卷紫。 */
    val accent50: Color,
    val accent100: Color,
    val accent500: Color,
    val accent700: Color,
    val accent800: Color,
    /** 柔绘材质：阴影走 softShadow、描边走 softFeatherRim（其余主题 shadow/border）。 */
    val softMaterial: Boolean,
    /** 通透专属：时间轴卡描边走 iosGlassRim（玻璃高光内描边）。 */
    val timelineRimGlass: Boolean,
    /** 事件分类色走主题语义色（柔绘），否则沿用 Material 500 历史值（书卷/通透）。 */
    val semanticEventColors: Boolean,
    /** 时间轴卡形状走 appShapes().heroCard（柔绘/通透），否则 20dp 圆角（书卷）。 */
    val timelineCardHeroShape: Boolean,
    /** 课程详情面板标题字体 / 分区标题字体。 */
    val titleFont: FontFamily,
    val sectionLabelFont: FontFamily,
    /** 时间轴调色板（palettePeach 用于奇数行；三主题字段同构、取值不同）。 */
    val palettePlain: TodayTimelinePalette,
    val palettePeach: TodayTimelinePalette,
    // —— 文案资源（书卷一套 today_claude_*，柔绘/通透共用 today_ios_*）——
    val statusLive: StringResource,
    val statusDone: StringResource,
    val statusUpcoming: StringResource,
    val metaTime: StringResource,
    val metaRoom: StringResource,
    val metaTeacher: StringResource,
    val metaCredit: StringResource,
    val sheetClose: StringResource,
    val sheetEdit: StringResource,
    val countdownLabel: StringResource,
    val sectionsFormat: StringResource,
    val noteType: StringResource,
    val noteHours: StringResource,
    val typeLab: StringResource,
    val typeTheory: StringResource,
)

/**
 * 今日页骨架的三主题差异注入点（v3.56.0 骨架统一）。
 *
 * 三主题信息架构逐项一致（同字段、同顺序、同位置），部件实现只有一份；
 * 主题差异 = [cardStyle]（卡面材质/配色/文案）+ 三个骨架级文案。
 * 新增主题 = 提供一个 [TodaySkin] 实现。
 */
private interface TodaySkin {
    /**
     * 主题卡面差异参数。必须为 @Composable：字体族（书卷衬线 Lora / 通透系统体）
     * 与时间轴调色板都走 Res.font / CompositionLocal 资源，只能在合成期求值。
     * 实现方用 remember 缓存，避免每次重组重建样式对象。
     */
    @Composable
    fun cardStyle(): TodayCardStyle

    /** 「明天 M 月 d 日」明日区标题文案（各主题措辞不同）。 */
    @Composable
    fun tomorrowDateText(state: TodayUiState.Success): String

    /** 下节课卡角标文案。 */
    @Composable
    fun nextLabelText(): String

    /** 明日区「查看全部」按钮文案。 */
    @Composable
    fun viewAllText(): String
}

/** 书卷（CLAUDE）：暖紫点缀梯 + 纸感实边框 + 衬线标题 + today_claude_* 文案。 */
private object ClaudeTodaySkin : TodaySkin {
    @Composable
    override fun cardStyle(): TodayCardStyle {
        val styleFont = claudeReadingSerif()
        val palettePlain = todayClaudePalette(peach = false)
        val palettePeach = todayClaudePalette(peach = true)
        return remember(styleFont, palettePlain, palettePeach) {
            TodayCardStyle(
                accent50 = ClaudeLilac50,
                accent100 = ClaudeLilac100,
                accent500 = ClaudeLilac500,
                accent700 = ClaudeLilac700,
                accent800 = ClaudeLilac800,
                softMaterial = false,
                timelineRimGlass = false,
                semanticEventColors = false,
                timelineCardHeroShape = false,
                titleFont = styleFont,
                sectionLabelFont = styleFont,
                palettePlain = palettePlain,
                palettePeach = palettePeach,
                statusLive = Res.string.today_claude_status_live,
                statusDone = Res.string.today_claude_status_done,
                statusUpcoming = Res.string.today_claude_status_upcoming,
                metaTime = Res.string.today_claude_meta_time,
                metaRoom = Res.string.today_claude_meta_room,
                metaTeacher = Res.string.today_claude_meta_teacher,
                metaCredit = Res.string.today_claude_meta_credit,
                sheetClose = Res.string.today_claude_sheet_close,
                sheetEdit = Res.string.today_claude_sheet_edit,
                countdownLabel = Res.string.today_claude_countdown_label,
                sectionsFormat = Res.string.today_claude_sections_format,
                noteType = Res.string.today_claude_note_type,
                noteHours = Res.string.today_claude_note_hours,
                typeLab = Res.string.today_claude_type_lab,
                typeTheory = Res.string.today_claude_type_theory,
            )
        }
    }

    @Composable
    override fun tomorrowDateText(state: TodayUiState.Success): String =
        stringResource(Res.string.today_claude_tomorrow_format, state.today.month.number, state.today.day)

    @Composable
    override fun nextLabelText(): String = stringResource(Res.string.today_claude_next_label)

    @Composable
    override fun viewAllText(): String = stringResource(Res.string.today_claude_view_all)
}

/** 柔绘（SOFT）：马卡龙点缀梯 + softShadow/softFeatherRim 材质 + 语义事件色；文案与通透共用。 */
private object SoftTodaySkin : TodaySkin {
    @Composable
    override fun cardStyle(): TodayCardStyle {
        val styleFont = iosUiSans()
        val palettePlain = todaySoftPalette(peach = false)
        val palettePeach = todaySoftPalette(peach = true)
        return remember(styleFont, palettePlain, palettePeach) {
            TodayCardStyle(
                accent50 = SoftAccentAlt50,
                accent100 = SoftAccentAlt100,
                accent500 = SoftAccentAlt500,
                accent700 = SoftAccentAlt700,
                accent800 = SoftAccentAlt800,
                softMaterial = true,
                timelineRimGlass = false,
                semanticEventColors = true,
                timelineCardHeroShape = true,
                titleFont = styleFont,
                sectionLabelFont = styleFont,
                palettePlain = palettePlain,
                palettePeach = palettePeach,
                statusLive = Res.string.today_ios_status_live,
                statusDone = Res.string.today_ios_status_done,
                statusUpcoming = Res.string.today_ios_status_upcoming,
                metaTime = Res.string.today_ios_meta_time,
                metaRoom = Res.string.today_ios_meta_room,
                metaTeacher = Res.string.today_ios_meta_teacher,
                metaCredit = Res.string.today_ios_meta_credit,
                sheetClose = Res.string.today_ios_sheet_close,
                sheetEdit = Res.string.today_ios_sheet_edit,
                countdownLabel = Res.string.today_ios_countdown_label,
                sectionsFormat = Res.string.today_ios_sections_format,
                noteType = Res.string.today_ios_note_type,
                noteHours = Res.string.today_ios_note_hours,
                typeLab = Res.string.today_ios_type_lab,
                typeTheory = Res.string.today_ios_type_theory,
            )
        }
    }

    @Composable
    override fun tomorrowDateText(state: TodayUiState.Success): String =
        stringResource(Res.string.today_ios_tomorrow_format, state.today.month.number, state.today.day)

    @Composable
    override fun nextLabelText(): String = stringResource(Res.string.today_ios_next_label)

    @Composable
    override fun viewAllText(): String = stringResource(Res.string.today_ios_view_all)
}

/** 通透（IOS26）：系统蓝点缀梯 + 玻璃高光内描边（iosGlassRim）+ heroCard 卡形。 */
private object Ios26TodaySkin : TodaySkin {
    @Composable
    override fun cardStyle(): TodayCardStyle {
        val styleFont = iosUiSans()
        val palettePlain = todayIos26Palette(peach = false)
        val palettePeach = todayIos26Palette(peach = true)
        return remember(styleFont, palettePlain, palettePeach) {
            TodayCardStyle(
                accent50 = Ios26AccentAlt50,
                accent100 = Ios26AccentAlt100,
                accent500 = Ios26AccentAlt500,
                accent700 = Ios26AccentAlt700,
                accent800 = Ios26AccentAlt800,
                softMaterial = false,
                timelineRimGlass = true,
                semanticEventColors = false,
                timelineCardHeroShape = true,
                titleFont = styleFont,
                sectionLabelFont = styleFont,
                palettePlain = palettePlain,
                palettePeach = palettePeach,
                statusLive = Res.string.today_ios_status_live,
                statusDone = Res.string.today_ios_status_done,
                statusUpcoming = Res.string.today_ios_status_upcoming,
                metaTime = Res.string.today_ios_meta_time,
                metaRoom = Res.string.today_ios_meta_room,
                metaTeacher = Res.string.today_ios_meta_teacher,
                metaCredit = Res.string.today_ios_meta_credit,
                sheetClose = Res.string.today_ios_sheet_close,
                sheetEdit = Res.string.today_ios_sheet_edit,
                countdownLabel = Res.string.today_ios_countdown_label,
                sectionsFormat = Res.string.today_ios_sections_format,
                noteType = Res.string.today_ios_note_type,
                noteHours = Res.string.today_ios_note_hours,
                typeLab = Res.string.today_ios_type_lab,
                typeTheory = Res.string.today_ios_type_theory,
            )
        }
    }

    @Composable
    override fun tomorrowDateText(state: TodayUiState.Success): String =
        stringResource(Res.string.today_ios_tomorrow_format, state.today.month.number, state.today.day)

    @Composable
    override fun nextLabelText(): String = stringResource(Res.string.today_ios_next_label)

    @Composable
    override fun viewAllText(): String = stringResource(Res.string.today_ios_view_all)
}

/**
 * 三主题共用的今日页骨架（原 Claude/Soft/Ios26 三份逐行相同的 Content 收敛）：
 * 页头 + 下节课卡 + 课程时间轴 + 日程事件 + 明日预览 + 自建待办 + 课程详情面板。
 * 分钟级 now 由 TodayContent 统一供给，滚动状态单点持有（主题切换保位）。
 */
@Composable
private fun TodayThemeContent(
    skin: TodaySkin,
    state: TodayUiState.Success,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    bottomInset: Dp,
    statusText: String,
    dateText: String,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    onOpenWeeklySchedule: () -> Unit,
    onEditCourse: (String) -> Unit,
    onToggleEventDone: (String, Boolean) -> Unit,
    onToggleTodo: (String, Boolean) -> Unit,
    onEditTodo: (TodoItem) -> Unit
) {
    val colors = appColors()
    val style = skin.cardStyle()
    var detailCourse by remember { mutableStateOf<CourseDisplayModel?>(null) }

    val nextSlot = remember(state, now) { resolveNextCardSlot(state, now, state.nextCardMode) }
    val tomorrowDate = skin.tomorrowDateText(state)
    // 列表位移动画受「减弱动态效果」门控（同 QuickDeleteScreen 写法）；
    // animateItem 是 LazyItemScope 成员，须在 item 作用域内求值，故传 reduceMotion 布尔。
    val reduceMotion = LocalAppMotion.current.reduceMotion

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = bottomInset + 100.dp)
    ) {
        item {
            TodayHeader(
                weekIndex = state.weekIndex,
                status = state.status,
                statusText = statusText,
                dateText = dateText,
            )
        }

        when (val slot = nextSlot) {
            is NextCardSlot.Course -> item {
                TodayNextClassCard(
                    data = NextCardData(
                        label = skin.nextLabelText(),
                        title = slot.model.course.name,
                        location = slot.model.course.position.takeIf { !gridStyle.hideLocation && it.isNotBlank() },
                        teacher = slot.model.course.teacher.takeIf { !gridStyle.hideTeacher && it.isNotBlank() },
                        timeRange = todayTimeRange(slot.model),
                        minutesUntil = todayMinutesUntil(slot.model, now)
                    ),
                    isDark = isDark,
                    onClick = { detailCourse = slot.model },
                    style = style
                )
            }
            is NextCardSlot.Event -> item {
                TodayNextClassCard(
                    data = eventCardData(slot.event, state.today, now, stringResource(Res.string.next_card_label_agenda)),
                    isDark = isDark,
                    onClick = null,
                    style = style
                )
            }
            NextCardSlot.Ended -> item {
                TodayNextClassCard(
                    data = endedCardData(stringResource(Res.string.next_card_today_ended)),
                    isDark = isDark,
                    onClick = null,
                    style = style
                )
            }
            NextCardSlot.None -> Unit
        }

        item {
            TodayTimelineHeader(
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
                // key 必须带课表维度：开启情侣叠加后 state.courses 是「本人 + 情侣」拼接列表，
                // 跨表同 id（导入保留原 id）会产生重复 key → LazyColumn 抛
                // IllegalArgumentException: Key was already used，整页崩溃。
                // 下方明日列表已用 "tomorrow-" 前缀隔离，此处保持一致口径。
                key = { _, model -> "${model.course.courseTableId}-${model.course.id}" }
            ) { index, model ->
                Box(modifier = if (reduceMotion) Modifier else Modifier.animateItem()) {
                    TodayTimelineItem(
                        model = model,
                        index = index,
                        isLast = index == state.courses.lastIndex,
                        gridStyle = gridStyle,
                        isDark = isDark,
                        now = now,
                        onClick = { detailCourse = model },
                        style = style
                    )
                }
            }
        }

        // 今日日程事件（来自「日程」页新建的日程）
        if (state.events.isNotEmpty()) {
            item {
                TodayEventsSection(events = state.events, onToggleDone = onToggleEventDone, style = style)
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
                        TodaySectionLabelRow(
                            label = tomorrowDate,
                            trailing = stringResource(
                                Res.string.text_courses_count,
                                state.tomorrowCourses.size.toString()
                            ),
                            fillWidth = false,
                            labelFont = style.sectionLabelFont
                        )
                    }
                    TodayGhostButton(
                        text = skin.viewAllText(),
                        onClick = onOpenWeeklySchedule
                    )
                }
            }
            itemsIndexed(
                state.tomorrowCourses,
                key = { _, model -> "tomorrow-${model.course.id}" }
            ) { _, model ->
                Box(modifier = if (reduceMotion) Modifier else Modifier.animateItem()) {
                    TodayTomorrowCard(model = model, gridStyle = gridStyle, isDark = isDark, style = style)
                }
            }
        }

        // 今日自建待办（「今日」页 + 新建的 todo_items）：排在明日预览之后；
        // 课程为空时本区自然成为页面底部内容（沿用旧死区实现「待办排最后」的语义）。
        // 整区聚合为单个 item（与 TodayEventsSection 同构）：LazyColumn 的 spacedBy(16dp)
        // 只在区段之间生效，区段内部行距由本区自己的 6dp 决定——与日程事件区保持一致；
        // 若拆成每行一个 item，行距会被列表级 spacedBy 放大到 16dp（比事件区疏 2.7 倍）。
        if (state.todos.isNotEmpty()) {
            item(key = "today-todos") {
                TodayTodosSection(
                    todos = state.todos,
                    onToggle = onToggleTodo,
                    onEdit = onEditTodo,
                    style = style
                )
            }
        }
    }

    detailCourse?.let { model ->
        TodayCourseDetailSheet(
            model = model,
            gridStyle = gridStyle,
            isDark = isDark,
            now = now,
            onDismiss = { detailCourse = null },
            onEdit = {
                detailCourse = null
                onEditCourse(model.course.id)
            },
            style = style
        )
    }
}

/** 课程时间区间文本（三主题统一）：优先自定义时间，否则用节次时间。 */
private fun todayTimeRange(model: CourseDisplayModel): String {
    val start = model.startTime?.takeIf { it.isNotBlank() }
    val end = model.endTime?.takeIf { it.isNotBlank() }
    return when {
        start != null && end != null -> "$start - $end"
        start != null -> start
        end != null -> end
        else -> EMPTY_TIME_PLACEHOLDER
    }
}

/** 距开课分钟数（三主题统一，已开课为负值）；无法解析时为 null。 */
private fun todayMinutesUntil(model: CourseDisplayModel, now: LocalTime): Int? {
    val startText = model.startTime?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val start = LocalTime.parse(startText)
        (start.toSecondOfDay() - now.toSecondOfDay()) / 60
    } catch (e: Exception) {
        null
    }
}


/** 页头：周次胶囊置于左侧 + 日期居中大字，同一行。 */
@Composable
private fun TodayHeader(
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
private fun TodaySectionLabelRow(
    label: String,
    trailing: String?,
    fillWidth: Boolean = true,
    labelFont: FontFamily
) {
    val colors = appColors()
    Row(
        modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = labelFont,
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
private fun TodayTimelineHeader(title: String, count: String) {
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
private fun TodayTimelineItem(
    model: CourseDisplayModel,
    index: Int,
    isLast: Boolean,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    onClick: () -> Unit,
    style: TodayCardStyle
) {
    val colors = appColors()
    val palette = if (index % 2 == 1) style.palettePeach else style.palettePlain
    val isFinished = todayIsCourseFinished(model, now)
    val isCurrent = !isFinished && todayIsCourseOngoing(model, now)
    val startTime = model.startTime?.takeIf { it.isNotBlank() }
        ?: todayTimeRange(model).substringBefore(" - ")

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
            // 进行中圆点：状态色经 colorDurationMs 渐变；进行中带呼吸脉冲（兑现设计注释承诺，
            // v3.54.0）。脉冲值在 graphicsLayer 内延迟读取，呼吸期间不重组 item
            val dotMotion = LocalAppMotion.current
            val dotColor by animateColorAsState(
                targetValue = if (isCurrent) palette.dot else colors.pageBg,
                animationSpec = tween(
                    dotMotion.tokens.colorDurationMs,
                    easing = dotMotion.tokens.entranceEasing
                ),
                label = "currentDotColor"
            )
            val dotPulseState: State<Float>? = if (isCurrent && !dotMotion.reduceMotion) {
                rememberInfiniteTransition(label = "currentDotPulse").animateFloat(
                    initialValue = 1f,
                    targetValue = 0.55f,
                    animationSpec = infiniteRepeatable(
                        tween(dotMotion.tokens.pulseDurationMs.coerceAtLeast(1)),
                        RepeatMode.Reverse
                    ),
                    label = "currentDotPulseAlpha"
                )
            } else {
                null
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = 18.dp)
                    .graphicsLayer { alpha = dotPulseState?.value ?: 1f }
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(2.5.dp, palette.dot, CircleShape)
            )
        }

        TodayTimelineCard(
            model = model,
            index = index,
            palette = palette,
            gridStyle = gridStyle,
            isFinished = isFinished,
            onClick = onClick,
            style = style,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 时间轴课程卡：设计稿 .timeline-card（20dp 圆角 + 渐变底 + 名称 + N 节徽标 + 元信息 + 信息胶囊）。 */
@Composable
private fun TodayTimelineCard(
    model: CourseDisplayModel,
    index: Int,
    palette: TodayTimelinePalette,
    gridStyle: ScheduleGridStyle,
    isFinished: Boolean,
    onClick: () -> Unit,
    style: TodayCardStyle,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    val shape = if (style.timelineCardHeroShape) appShapes().heroCard else RoundedCornerShape(20.dp)
    val sectionCount = todaySectionCount(model)
    val cardMotion = rememberTodayCardMotion(index, isFinished)
    Box(
        modifier = modifier
            .graphicsLayer(
                alpha = cardMotion.alpha,
                scaleX = cardMotion.scale,
                scaleY = cardMotion.scale,
                translationY = cardMotion.translationYPx
            )
            .then(
                if (style.softMaterial) Modifier.softShadow(shape = shape, elevation = 8.dp)
                else Modifier.shadow(
                    elevation = 1.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = colors.shadow,
                    spotColor = colors.shadow
                )
            )
            .clip(shape)
            .background(Brush.linearGradient(palette.gradient))
            .then(
                when {
                    style.timelineRimGlass -> Modifier.iosGlassRim(shape)
                    style.softMaterial -> Modifier.softFeatherRim(shape)
                    else -> Modifier.border(1.dp, palette.border, shape)
                }
            )
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
                            style.sectionsFormat,
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
                    TodayMetaRow(
                        icon = Res.drawable.location_on_24px,
                        text = model.course.position,
                        tint = palette.meta
                    )
                }
                if (!gridStyle.hideTeacher && model.course.teacher.isNotBlank()) {
                    TodayMetaRow(
                        icon = Res.drawable.person_24px,
                        text = model.course.teacher,
                        tint = palette.meta
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TodayFactText(
                    label = stringResource(style.noteType),
                    value = stringResource(
                        if (model.course.isLab) {
                            style.typeLab
                        } else {
                            style.typeTheory
                        }
                    )
                )
                TodayFactText(
                    label = stringResource(style.noteHours),
                    value = sectionCount.toString()
                )
            }
        }
    }
}

/**
 * 课程事实文本：`标签：值`（标签 SemiBold、值常规），**无底色、无描边**。
 *
 * ⚠️ 由来（设计走查 E1 · P0）：原实现是 `bg-100 底 + border-200 描边` 的胶囊
 * （`TodayNotePill`），与全站筛选 chip 同形 ⇒ 静态信息被读成"可点控件"，
 * 且浅灰描边 + 浅灰字观感接近 disabled 禁态。静态信息不应使用可点控件的外形，
 * 故退为纯文字事实行，并把字号从 10.5sp 提到 11.5sp 以保证无底色时的可读性。
 *
 * 说明：节数已由卡片右上角徽章给出，此处的 [noteHours] 与之重复（见走查 E2，待办）。
 */
@Composable
private fun TodayFactText(label: String, value: String) {
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
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 15.sp
        ),
        color = colors.textSecondary.copy(alpha = 0.9f),
        maxLines = 1
    )
}

/** 元信息行：13dp 图标 + 12sp 文字（设计稿 .course-meta-item）。 */
@Composable
private fun TodayMetaRow(
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

/** 下节课卡要展示的内容（v3.47.0「个性化显示 → 下节课卡」）。 */
private sealed interface NextCardSlot {
    /** 展示某节课（今日进行中/即将开始，或明日首节）。 */
    data class Course(val model: CourseDisplayModel, val isTomorrow: Boolean) : NextCardSlot
    /** 今日课程已结束后，展示下一次日程。 */
    data class Event(val event: ScheduleEvent) : NextCardSlot
    /** 展示「今日课程已结束，自由探索吧」提示（变淡）。 */
    data object Ended : NextCardSlot
    /** 不展示。 */
    data object None : NextCardSlot
}

/** 下节课卡渲染数据（三套主题同构卡共用）：标识 / 标题 / 地点 / 教师 / 时间区间 / 倒计时 / 变淡。 */
private data class NextCardData(
    val label: String,
    val title: String,
    val location: String?,
    val teacher: String?,
    val timeRange: String,
    val minutesUntil: Int?,
    val faded: Boolean = false
)

private fun parseTimeOrNull(text: String?): LocalTime? =
    text?.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

/**
 * 统一解析「下节课卡」应展示什么（三套主题共用，避免各写一套）。
 *
 * 优先级：今日进行中课程 → 今日即将开始课程 → （今日课程全部结束后按设置）：
 * - [NextCardMode.AUTO_NEXT]：下一次日程 → 明日首节课 → 都不存在则隐藏；
 * - [NextCardMode.TODAY_ENDED]：展示「今日课程已结束」提示；
 * - [NextCardMode.HIDE]：隐藏（旧行为）。
 */
private fun resolveNextCardSlot(
    state: TodayUiState.Success,
    now: LocalTime,
    mode: NextCardMode
): NextCardSlot {
    val ongoing = state.courses.firstOrNull { m ->
        val s = parseTimeOrNull(m.startTime)
        val e = parseTimeOrNull(m.endTime)
        s != null && e != null && s <= now && now < e
    }
    val upcoming = state.courses.firstOrNull { m ->
        val s = parseTimeOrNull(m.startTime)
        s != null && s > now
    }
    (ongoing ?: upcoming)?.let { return NextCardSlot.Course(it, isTomorrow = false) }

    when (mode) {
        NextCardMode.HIDE -> return NextCardSlot.None
        NextCardMode.TODAY_ENDED -> return NextCardSlot.Ended
        NextCardMode.AUTO_NEXT -> Unit
    }
    // AUTO_NEXT：在「未来日程（含跨天）」与「明日首节课」之间取时间最近的一个
    val todayStr = state.today.toString()
    val tomorrowStr = state.today.plus(1, DateTimeUnit.DAY).toString()
    fun keyOf(date: String, time: String?): Pair<String, Int> =
        date to (parseTimeOrNull(time)?.toSecondOfDay() ?: Int.MAX_VALUE)

    val nextEvent = state.upcomingEvents
        .asSequence()
        // 已完成的日程（含待办）不再进入「下一节课」卡片
        .filter { !it.done }
        .filter { !it.isAllDay }
        .filter { ev ->
            val s = parseTimeOrNull(ev.startTime) ?: return@filter false
            ev.date > todayStr || (ev.date == todayStr && s > now)
        }
        .minWithOrNull(
            compareBy({ it.date }, { parseTimeOrNull(it.startTime)?.toSecondOfDay() ?: Int.MAX_VALUE })
        )
    val tomorrowFirst = state.tomorrowCourses.firstOrNull()

    val eventKey = nextEvent?.let { keyOf(it.date, it.startTime) }
    val courseKey = tomorrowFirst?.let { keyOf(tomorrowStr, it.startTime) }
    val eventSooner = when {
        eventKey == null -> false
        courseKey == null -> true
        else -> eventKey.first < courseKey.first ||
            (eventKey.first == courseKey.first && eventKey.second <= courseKey.second)
    }
    if (eventSooner && nextEvent != null) return NextCardSlot.Event(nextEvent)
    if (tomorrowFirst != null) return NextCardSlot.Course(tomorrowFirst, isTomorrow = true)
    return NextCardSlot.None
}

/** 由日程构造下节课卡数据（跨天时时间徽标带日期；非当日不显示倒计时）。 */
private fun eventCardData(event: ScheduleEvent, today: LocalDate, now: LocalTime, label: String): NextCardData {
    val isToday = event.date == today.toString()
    val start = event.startTime?.takeIf { it.isNotBlank() }
    val end = event.endTime?.takeIf { it.isNotBlank() }
    val range = if (isToday) {
        listOfNotNull(start, end).joinToString(" - ")
    } else {
        listOfNotNull(event.date.takeIf { it.length >= 10 }?.substring(5)?.replace('-', '/'), start)
            .joinToString(" ")
    }
    val minutes = if (isToday) {
        start?.let { parseTimeOrNull(it) }?.let { (it.toSecondOfDay() - now.toSecondOfDay()) / 60 }
    } else {
        null
    }
    return NextCardData(
        label = label,
        title = event.title,
        location = event.location?.takeIf { it.isNotBlank() },
        teacher = null,
        timeRange = range,
        minutesUntil = minutes
    )
}

/** 今日课程已结束提示卡数据（变淡）。 */
private fun endedCardData(message: String): NextCardData = NextCardData(
    label = "",
    title = message,
    location = null,
    teacher = null,
    timeRange = "",
    minutesUntil = null,
    faded = true
)

@Composable
private fun TodayNextClassCard(
    data: NextCardData,
    isDark: Boolean,
    onClick: (() -> Unit)? = null,
    style: TodayCardStyle
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val labelColor = if (isDarkTheme) Color(0xFFC4B8E8) else style.accent700
    val nameColor = if (isDarkTheme) Color(0xFFEBE5FF) else style.accent800
    val badgeBg = if (isDarkTheme) Color(0x1FFFFFFF) else Color(0x99FFFFFF)
    val gradient = if (isDarkTheme) {
        listOf(Color(0xFF3A2F5C), Color(0xFF2E2749))
    } else {
        listOf(style.accent100, style.accent50)
    }
    val border = if (isDarkTheme) Color(0x339C87F5) else Color(0x269C87F5)
    val shape = RoundedCornerShape(28.dp)
    val minutesUntil = data.minutesUntil
    // 淡出/恢复随分钟变化：经 statusFadeMs 渐变，取代二值硬跳（v3.54.0）
    val nextCardAlpha by rememberStatusFadeAlpha(data.faded, 0.62f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = nextCardAlpha }
            .then(
                if (style.softMaterial) Modifier.softShadow(shape = shape, elevation = 8.dp)
                else Modifier.shadow(
                    elevation = 8.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = style.accent500.copy(alpha = 0.25f),
                    spotColor = style.accent500.copy(alpha = 0.25f)
                )
            )
            .clip(shape)
            .background(Brush.linearGradient(gradient))
            .then(if (style.softMaterial) Modifier.softFeatherRim(shape) else Modifier.border(1.dp, border, shape))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
                            .background(style.accent500)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = data.label,
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
                    text = data.title,
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
                    data.location?.let { loc ->
                        TodayMetaRow(
                            icon = Res.drawable.location_on_24px,
                            text = loc,
                            tint = labelColor
                        )
                    }
                    data.teacher?.let { who ->
                        TodayMetaRow(
                            icon = Res.drawable.person_24px,
                            text = who,
                            tint = labelColor
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (data.timeRange.isNotBlank()) {
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
                            text = data.timeRange,
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
                            text = stringResource(style.countdownLabel),
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

private fun todaySectionCount(model: CourseDisplayModel): Int {
    val start = model.course.startSection
    val end = model.course.endSection
    return if (start != null && end != null) (end - start + 1).coerceAtLeast(1) else 1
}


/** 明日预览卡片：设计稿 .tomorrow-card（凹陷卡 + 4dp 色条 + 名称 + 时间·地点）。 */
@Composable
private fun TodayTomorrowCard(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    style: TodayCardStyle
) {
    val colors = appColors()
    val accent = todayAccentColor(
        model = model,
        gridStyle = gridStyle,
        isDark = isDark,
        themePreset = LocalThemePreset.current
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(appShapes().card)
            .background(colors.cardBg)
            .then(
                if (style.softMaterial) Modifier.softFeatherRim(appShapes().card)
                else Modifier.border(1.dp, colors.divider, appShapes().card)
            )
    ) {
        Box(
            modifier = Modifier
                .width(TodayTomorrowAccentWidth)
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
                    text = todayTimeRange(model),
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

/**
 * 日程区段标签：按当天事件的类型去重拼接（如「待办 · 活动 · 考试」），
 * 不再写死「今日课表」——来自「日程」页的待办/活动/考试等同步到今日页后，
 * 标签应如实反映当天日程的类型构成；无事件时回落「今日课表」。
 */
@Composable
private fun todayEventsSectionLabel(events: List<ScheduleEvent>): String {
    if (events.isEmpty()) return stringResource(Res.string.title_today_schedule)
    return events.map { event ->
        stringResource(
            when (ScheduleCategory.fromKey(event.category)) {
                ScheduleCategory.TODO -> Res.string.agenda_category_todo
                ScheduleCategory.ACTIVITY -> Res.string.agenda_category_activity
                ScheduleCategory.EXAM -> Res.string.agenda_category_exam
                ScheduleCategory.HOMEWORK -> Res.string.agenda_category_homework
                ScheduleCategory.OTHER -> Res.string.agenda_category_other
            }
        )
    }.distinct().joinToString(separator = " · ")
}

/**
 * 事件行标题：三主题共用的第一行 —— 「待办」分类在标题前显示可点击的完成小方格，
 * 点击翻转完成状态；已完成待办标题划线（整卡降透明由各行内容卡自行处理）。
 */
@Composable
private fun EventTitleRow(event: ScheduleEvent, onToggleDone: (String, Boolean) -> Unit) {
    val colors = appColors()
    val isTodo = ScheduleCategory.fromKey(event.category) == ScheduleCategory.TODO
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isTodo) {
            AppCheckboxIndicator(
                checked = event.done,
                modifier = Modifier.clickable { onToggleDone(event.id, !event.done) }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = event.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
            ),
            color = colors.textPrimary,
            textDecoration = if (event.done) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 今日日程事件区段：来自「日程」页新建的日程，展示在课程时间轴之后。 */
@Composable
private fun TodayEventsSection(
    events: List<ScheduleEvent>,
    onToggleDone: (String, Boolean) -> Unit,
    style: TodayCardStyle
) {
    val colors = appColors()
    Spacer(modifier = Modifier.height(8.dp))
    TodaySectionLabelRow(
        label = todayEventsSectionLabel(events),
        trailing = stringResource(Res.string.agenda_count_format, events.size.toString()),
        fillWidth = true,
        labelFont = style.sectionLabelFont
    )
    Spacer(modifier = Modifier.height(6.dp))
    events.forEachIndexed { index, event ->
        TodayEventRow(event = event, index = index, isLast = index == events.lastIndex, onToggleDone = onToggleDone, style = style)
        if (index < events.lastIndex) Spacer(modifier = Modifier.height(6.dp))
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun TodayEventRow(
    event: ScheduleEvent,
    index: Int,
    isLast: Boolean,
    onToggleDone: (String, Boolean) -> Unit,
    style: TodayCardStyle
) {
    val colors = appColors()
    val categoryColor = when (ScheduleCategory.fromKey(event.category)) {
        ScheduleCategory.TODO ->
            if (style.semanticEventColors) colors.success else Color(0xFF4CAF50)
        ScheduleCategory.ACTIVITY ->
            if (style.semanticEventColors) colors.warning else Color(0xFFFF9800)
        ScheduleCategory.EXAM ->
            if (style.semanticEventColors) colors.danger else Color(0xFFF44336)
        ScheduleCategory.HOMEWORK ->
            if (style.semanticEventColors) colors.info else Color(0xFF2196F3)
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
        // 无时间条目（默认待办 / 全天）不渲染时间占位列，内容卡铺满整行
        if (!event.startTime.isNullOrBlank()) {
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
        }
        // 右侧内容卡
        Box(
            modifier = Modifier
                .weight(1f)
                .alpha(rememberStatusFadeAlpha(event.done, 0.5f).value)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.cardBg)
                .then(
                if (style.softMaterial) Modifier.softFeatherRim(RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, colors.divider, RoundedCornerShape(12.dp))
            )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                EventTitleRow(event = event, onToggleDone = onToggleDone)
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

/**
 * 今日自建待办区段（`todo_items`）：与 [TodayEventsSection] 同构——整区一个 LazyColumn
 * item，区段内自行排 8dp/6dp 间距，避免列表级 spacedBy(16dp) 把区内行距放大。
 * 计数走待办专属文案（`todo_items` 不是日程，用 agenda_count_format 会显示成「N 个日程」）。
 */
@Composable
private fun TodayTodosSection(
    todos: List<TodoItem>,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (TodoItem) -> Unit,
    style: TodayCardStyle
) {
    Spacer(modifier = Modifier.height(8.dp))
    TodaySectionLabelRow(
        label = stringResource(Res.string.today_todos_section),
        trailing = stringResource(Res.string.todo_count_format, todos.size.toString()),
        fillWidth = true,
        labelFont = style.sectionLabelFont
    )
    Spacer(modifier = Modifier.height(6.dp))
    todos.forEachIndexed { index, todo ->
        TodayTodoRow(
            todo = todo,
            isLast = index == todos.lastIndex,
            onToggle = onToggle,
            onEdit = onEdit,
            style = style
        )
        if (index < todos.lastIndex) Spacer(modifier = Modifier.height(6.dp))
    }
    Spacer(modifier = Modifier.height(8.dp))
}

/**
 * 自建待办行：卡面语言与 [TodayEventRow] 一致（cardBg + 分隔描边 / 柔绘 featherRim），
 * 刻意不复用旧课程条的青色块语言（旧实现随三主题分发死区一并废弃）。
 * 复选框点击切换完成态（渐变降透明 + 标题删除线），整行点击进入编辑；
 * 无时间待办不渲染时间轴列，内容卡铺满整行。
 */
@Composable
private fun TodayTodoRow(
    todo: TodoItem,
    isLast: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (TodoItem) -> Unit,
    style: TodayCardStyle
) {
    val colors = appColors()
    val haptics = rememberAppHaptics()
    // 待办与日程事件的 TODO 分类同色（柔绘走语义色，书卷/通透沿用 Material 500 历史值）
    val todoAccent = if (style.semanticEventColors) colors.success else Color(0xFF4CAF50)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onEdit(todo) },
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 有时间的待办渲染时间轴列（圆点+连线，同事件行）；默认待办整列不渲染
        if (!todo.time.isNullOrBlank()) {
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
                        .background(todoAccent)
                )
            }
        }
        // 右侧内容卡：已完成渐变降透明（同事件行 0.5f）
        Box(
            modifier = Modifier
                .weight(1f)
                .alpha(rememberStatusFadeAlpha(todo.done, 0.5f).value)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.cardBg)
                .then(
                    if (style.softMaterial) Modifier.softFeatherRim(RoundedCornerShape(12.dp))
                    else Modifier.border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppCheckboxIndicator(
                        checked = todo.done,
                        modifier = Modifier.clickable {
                            haptics.tick()
                            onToggle(todo.id, !todo.done)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = todo.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 18.sp
                        ),
                        color = colors.textPrimary,
                        textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                todo.time?.takeIf { it.isNotBlank() }?.let { time ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 14.sp
                        ),
                        color = colors.textSecondary,
                        maxLines = 1
                    )
                }
                todo.note?.takeIf { it.isNotBlank() }?.let { note ->
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
private fun TodayGhostButton(text: String, onClick: () -> Unit) {
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
private fun TodayCourseDetailSheet(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    now: LocalTime,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    style: TodayCardStyle
) {
    val colors = appColors()
    val isFinished = todayIsCourseFinished(model, now)
    val isCurrent = !isFinished && todayIsCourseOngoing(model, now)
    val statusText = stringResource(
        when {
            isCurrent -> style.statusLive
            isFinished -> style.statusDone
            else -> style.statusUpcoming
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
                    fontFamily = style.titleFont,
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
                    .then(
                        if (style.softMaterial) Modifier.softFeatherRim(appShapes().chip)
                        else Modifier.border(1.dp, colors.divider, appShapes().chip)
                    )
            ) {
                TodayDetailRow(
                    label = stringResource(style.metaTime),
                    value = todayTimeRange(model)
                )
                HorizontalDivider(thickness = 1.dp, color = colors.divider)
                if (model.course.position.isNotBlank()) {
                    TodayDetailRow(
                        label = stringResource(style.metaRoom),
                        value = model.course.position
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                if (model.course.teacher.isNotBlank()) {
                    TodayDetailRow(
                        label = stringResource(style.metaTeacher),
                        value = model.course.teacher
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                model.course.credit?.takeIf { it.isNotBlank() }?.let { credit ->
                    TodayDetailRow(
                        label = stringResource(style.metaCredit),
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
                        text = stringResource(style.sheetClose),
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
                        text = stringResource(style.sheetEdit),
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
private fun TodayDetailRow(label: String, value: String) {
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

private fun todayIsCourseFinished(model: CourseDisplayModel, now: LocalTime): Boolean {
    val endText = model.endTime?.takeIf { it.isNotBlank() } ?: return false
    return try {
        LocalTime.parse(endText) < now
    } catch (e: Exception) {
        false
    }
}

private fun todayIsCourseOngoing(model: CourseDisplayModel, now: LocalTime): Boolean {
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
private fun todayAccentColor(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    isDark: Boolean,
    themePreset: AppThemePreset
): Color {
    val colorPair = gridStyle.courseColorMaps.getOrNull(model.course.colorInt)
        ?: gridStyle.courseColorMaps.firstOrNull()
        ?: ScheduleGridStyle.DEFAULT_COLOR_MAPS.firstOrNull()
    return resolveCourseBlockColors(
        themePreset = themePreset,
        isDarkTheme = isDark,
        colorPair = colorPair,
        blockAlpha = 1f,
        fallbackContent = Color.Unspecified
    ).accent
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

// iOS 26 卡片渐变底（Apple 系统色的极淡档）：偶数行用系统蓝系，奇数行用系统靛系。
private val SoftAccentAlt50 = Color(0xFFF7F6FC)
private val SoftAccentAlt100 = Color(0xFFEDEBF7)
private val SoftAccentAlt500 = Color(0xFF9A93B8)
private val SoftAccentAlt700 = Color(0xFF5F5A80)
private val SoftAccentAlt800 = Color(0xFF4A4760)
private val SoftAccentWarm50 = Color(0xFFFBF7F2)
private val SoftAccentWarm500 = Color(0xFFD9A97E)
private val SoftAccentWarm700 = Color(0xFF9A7550)

/**
 * 时间轴卡片配色（偶数行 = 系统蓝系，奇数行 = 系统橙系）。
 *
 * 与书卷同结构（两个交替色系 + 深浅两套），色相换成 Apple 系统色：
 * 书卷是「紫罗兰 / 赤陶」暖冷交替，通透换成「系统蓝 / 系统橙」——
 * 这是 iOS 图标与图表的标准双色搭配，保证在浅色白卡与深色炭卡上都清晰。
 */
@Composable
private fun todaySoftPalette(peach: Boolean): TodayTimelinePalette {
    val colors = appColors()
    val isDark = LocalIsDarkTheme.current
    return when {
        peach && isDark -> TodayTimelinePalette(
            gradient = listOf(Color(0xFF2E2820), Color(0xFF272219)),
            border = Color(0x33E0BB96),
            title = Color(0xFFE6C9A8),
            meta = Color(0xFFBFAE96),
            badgeBg = Color(0x3DE0BB96),
            badgeFg = Color(0xFFE6C9A8),
            dot = Color(0xFFD9A97E)
        )

        peach -> TodayTimelinePalette(
            gradient = listOf(Color(0xFFFFFBF5), SoftAccentWarm50),
            // 柔绘暖调描边：用柔绘警示色 #D9A97E（此处曾误用 iOS 系统橙 #FF9500）
            border = Color(0x1FD9A97E),
            title = colors.textPrimary,
            meta = colors.textSecondary,
            badgeBg = Color(0x1FD9A97E),
            badgeFg = SoftAccentWarm700,
            dot = SoftAccentWarm500
        )

        isDark -> TodayTimelinePalette(
            gradient = listOf(Color(0xFF262431), Color(0xFF201E28)),
            border = Color(0x339AA3DC),
            title = Color(0xFFC3C8EC),
            meta = Color(0xFFA29FB4),
            badgeBg = Color(0x3D9AA3DC),
            badgeFg = Color(0xFFC3C8EC),
            dot = Color(0xFF9AA3DC)
        )

        else -> TodayTimelinePalette(
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

// iOS 26 卡片渐变底（Apple 系统色的极淡档）：偶数行用系统蓝系，奇数行用系统靛系。
private val Ios26AccentAlt50 = Color(0xFFF2F7FF)
private val Ios26AccentAlt100 = Color(0xFFE5F0FF)
private val Ios26AccentAlt500 = Color(0xFF5856D6)
private val Ios26AccentAlt700 = Color(0xFF2B2A78)
private val Ios26AccentAlt800 = Color(0xFF1B1B4B)
private val Ios26AccentWarm50 = Color(0xFFFFF6EE)
private val Ios26AccentWarm500 = Color(0xFFFF9500)
private val Ios26AccentWarm700 = Color(0xFFB36800)

/**
 * 时间轴卡片配色（偶数行 = 系统蓝系，奇数行 = 系统橙系）。
 *
 * 与书卷同结构（两个交替色系 + 深浅两套），色相换成 Apple 系统色：
 * 书卷是「紫罗兰 / 赤陶」暖冷交替，通透换成「系统蓝 / 系统橙」——
 * 这是 iOS 图标与图表的标准双色搭配，保证在浅色白卡与深色炭卡上都清晰。
 */
@Composable
private fun todayIos26Palette(peach: Boolean): TodayTimelinePalette {
    val colors = appColors()
    val isDark = LocalIsDarkTheme.current
    return when {
        peach && isDark -> TodayTimelinePalette(
            gradient = listOf(Color(0xFF33270F), Color(0xFF2A2010)),
            border = Color(0x33FF9F0A),
            title = Color(0xFFFFCB8A),
            meta = Color(0xFFC9A87C),
            badgeBg = Color(0x3DFF9F0A),
            badgeFg = Color(0xFFFFCB8A),
            dot = Color(0xFFFF9F0A)
        )

        peach -> TodayTimelinePalette(
            gradient = listOf(Color(0xFFFFFBF5), Ios26AccentWarm50),
            border = Color(0x1FFF9500),
            title = colors.textPrimary,
            meta = colors.textSecondary,
            badgeBg = Color(0x24FF9500),
            badgeFg = Ios26AccentWarm700,
            dot = Ios26AccentWarm500
        )

        isDark -> TodayTimelinePalette(
            gradient = listOf(Color(0xFF16213A), Color(0xFF121A2C)),
            border = Color(0x330A84FF),
            title = Color(0xFFA9CDFF),
            meta = Color(0xFF8FA5C4),
            badgeBg = Color(0x3D0A84FF),
            badgeFg = Color(0xFFA9CDFF),
            dot = Color(0xFF0A84FF)
        )

        else -> TodayTimelinePalette(
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

