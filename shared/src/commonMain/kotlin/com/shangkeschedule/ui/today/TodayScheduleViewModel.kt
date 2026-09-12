package com.shangkeschedule.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.Course
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.ScheduleEvent
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.main.TodoItem
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.data.model.NextCardMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import com.shangkeschedule.data.repository.ScheduleEventRepository
import com.shangkeschedule.data.repository.StyleSettingsRepository
import com.shangkeschedule.data.repository.TimeSlotRepository
import com.shangkeschedule.data.repository.TodoRepository
import com.shangkeschedule.data.time.currentDateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Clock

@KoinViewModel
class TodayScheduleViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository,
    private val styleSettingsRepository: StyleSettingsRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val todoRepository: TodoRepository,
    private val scheduleEventRepository: ScheduleEventRepository
) : ViewModel() {

    /**
     * 某日课程流（含情侣叠加）：本人课程 +（开关开启且存在配对情侣课表时）情侣课表课程。
     *
     * 情侣课程按情侣课表自己的作息解析节次时间并烘焙进副本的 customStart/EndTime
     * （不落库），后续列表页统一按当前表 slotMap 解析时间时，TA 的课程用的就是
     * TA 自己的作息而非本人的。
     */
    private fun mergedDayCoursesFlow(
        settings: AppSettingsModel,
        tableId: String,
        weekIndex: Int,
        dayOfWeek: Int
    ): Flow<List<CourseWithWeeks>> {
        val selfFlow = courseTableRepository.getCoursesForDay(tableId, weekIndex, dayOfWeek)
        if (!settings.coupleScheduleEnabled) return selfFlow

        // 当前表本身就是情侣课表（单独显示）时查不到配对表 → 自动回落为仅本人（即 TA）课程
        return courseTableRepository.getCoupleTableFor(tableId).flatMapLatest { coupleTable ->
            if (coupleTable == null) return@flatMapLatest selfFlow

            val coupleConfigFlow = appSettingsRepository.getCourseTableConfigFlow(coupleTable.id)
            combine(
                selfFlow,
                courseTableRepository.getCoursesForDay(coupleTable.id, weekIndex, dayOfWeek),
                timeSlotRepository.getActiveTimeSlotsByConfigFlow(coupleTable.id, coupleConfigFlow)
            ) { selfCourses, coupleCourses, coupleSlots ->
                val slotByNumber = coupleSlots.associateBy { it.number }
                val selfColored = selfCourses.map { cw ->
                    cw.copy(course = cw.course.copy(colorInt = settings.selfCourseColorIndex))
                }
                val coupleColored = coupleCourses.map { cw ->
                    val course = cw.course
                    val startSlot = slotByNumber[course.startSection]
                    val endSlot = slotByNumber[course.endSection]
                    cw.copy(
                        course = course.copy(
                            colorInt = settings.crushCourseColorIndex,
                            customStartTime = course.customStartTime ?: startSlot?.startTime,
                            customEndTime = course.customEndTime ?: endSlot?.endTime
                        )
                    )
                }
                selfColored + coupleColored
            }
        }
    }

    companion object {
        private const val DEFAULT_SEMESTER_TOTAL_WEEKS = 20
        private const val MAX_TIME_SORT_KEY = "99:99"

        /** 「下一次日程」跨天查询窗口：今天起的天数。 */
        private const val EVENT_LOOKAHEAD_DAYS = 30
    }

    val gridStyle: StateFlow<ScheduleGridStyle> = styleSettingsRepository.styleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleGridStyle())

    /**
     * 手动刷新触发器（v3.43.0 ·《交互动效审查》P2「下拉刷新」）。
     *
     * 每次 +1 都会让 [uiState] 的整条查询链重新装配，从而强制从数据库重读一遍。
     * 本页数据本是 DB Flow 驱动（自动推送），因此刷新的语义是「立刻重读一次」，
     * 而不是「首次加载」——用来消解用户「数据是不是没更新」的疑虑。
     */
    private val refreshTrigger = MutableStateFlow(0)

    /** 下拉刷新入口：重读今日课程 / 日程 / 待办。 */
    fun refresh() {
        refreshTrigger.value += 1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TodayUiState> = combine(
        appSettingsRepository.getAppSettings(),
        // 跨天自动重算：today 不再是流装配期的一次性快照，午夜后状态机与课程查询全部刷新
        currentDateFlow(),
        // 下拉刷新：作为第三个源，值一变即触发下方 flatMapLatest 重新装配整条查询
        refreshTrigger
    ) { settings, today, _ ->
        settings to today
    }.flatMapLatest { (settings, today) ->
            val tableId = settings.currentCourseTableId
            val todayStr = today.toString()
            val dayOfWeek = today.dayOfWeek.isoDayNumber

            appSettingsRepository.getCourseTableConfigFlow(tableId).flatMapLatest { config: CourseTableConfig? ->
                combine(
                    appSettingsRepository.calculateCurrentWeekFromDb(),
                    timeSlotRepository.getActiveTimeSlotsByConfigFlow(tableId, flowOf(config))
                ) { weekIndex: Int?, timeSlots: List<TimeSlot> ->

                    val startDate = config?.semesterStartDate?.let {
                        try { LocalDate.parse(it) } catch (e: Exception) { null }
                    }

                    val totalWeeks = config?.semesterTotalWeeks ?: DEFAULT_SEMESTER_TOTAL_WEEKS
                    val firstDayOfWeek = config?.firstDayOfWeek ?: DayOfWeek.MONDAY.isoDayNumber

                    // 判定今天是否在跳过日期集合中
                    val isSkippedDay = settings.skippedDates.contains(todayStr)

                    val status = when {
                        config?.semesterStartDate == null -> TodayStatus.NoSemesterConfig
                        startDate != null && today < startDate -> TodayStatus.Vacation
                        weekIndex == null -> TodayStatus.SemesterEnded
                        else -> TodayStatus.Normal
                    }

                    // 记录状态与精准计算所需的物理配置
                    DataSnapshot(
                        status = status,
                        weekIndex = weekIndex,
                        timeSlots = timeSlots,
                        startDate = startDate,
                        totalWeeks = totalWeeks,
                        firstDayOfWeek = firstDayOfWeek,
                        isSkippedDay = isSkippedDay
                    )
                }.flatMapLatest { snapshot ->
                    // 只有 Normal 状态且不是跳过日期时才查询数据库
                    val coursesFlow: Flow<List<CourseWithWeeks>> =
                        if (snapshot.status == TodayStatus.Normal && snapshot.weekIndex != null && !snapshot.isSkippedDay) {
                            mergedDayCoursesFlow(settings, tableId, snapshot.weekIndex, dayOfWeek)
                        } else {
                            // 如果是跳过日期或非正常学期状态，直接返回空课程列表
                            flowOf(emptyList())
                        }

                    // 明日课程（iOS 主题用）
                    val tomorrowDayOfWeek = (dayOfWeek % 7) + 1
                    val tomorrowCoursesFlow: Flow<List<CourseWithWeeks>> =
                        if (snapshot.status == TodayStatus.Normal && snapshot.weekIndex != null) {
                            mergedDayCoursesFlow(settings, tableId, snapshot.weekIndex, tomorrowDayOfWeek)
                        } else {
                            flowOf(emptyList())
                        }
                    // 今日待办与课程并行组合进同一状态；待办不受学期状态影响，跨天随 currentDateFlow 自动重算
                    // 今日日程事件与待办同源，按日期过滤
                    // 下节课卡「下一次日程」需要跨天：额外取今天起 EVENT_LOOKAHEAD_DAYS 天内的日程
                    val upcomingEndStr = today.plus(EVENT_LOOKAHEAD_DAYS, DateTimeUnit.DAY).toString()
                    combine(
                        coursesFlow,
                        tomorrowCoursesFlow,
                        todoRepository.getTodosByDate(todayStr),
                        scheduleEventRepository.getEventsByDate(todayStr),
                        scheduleEventRepository.getEventsBetweenDates(todayStr, upcomingEndStr)
                    ) { courses, tomorrowCourses, todos, events, upcomingEvents ->
                        createSuccessState(courses, tomorrowCourses, snapshot, today, todos, events, settings.nextCardMode, upcomingEvents)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState.Loading)

    // ─── 待办操作 ───

    /** 新增一条今日待办（日期取系统当天）。 */
    fun addTodo(title: String, note: String?, time: String?) {
        viewModelScope.launch {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            todoRepository.addTodo(today.toString(), title, note, time)
        }
    }

    /** 编辑一条待办。 */
    fun updateTodo(todo: TodoItem) {
        viewModelScope.launch {
            todoRepository.updateTodo(todo)
        }
    }

    /** 翻转一条待办的完成状态。 */
    fun toggleTodo(todoId: String, done: Boolean) {
        viewModelScope.launch {
            todoRepository.setDone(todoId, done)
        }
    }

    /** 翻转一条日程事件（「待办」分类）的完成状态。 */
    fun toggleEventDone(eventId: String, done: Boolean) {
        viewModelScope.launch {
            scheduleEventRepository.setDone(eventId, done)
        }
    }

    /** 删除一条待办。 */
    fun deleteTodo(todoId: String) {
        viewModelScope.launch {
            todoRepository.deleteTodo(todoId)
        }
    }

    /**
     * 内部辅助快照类
     */
    private data class DataSnapshot(
        val status: TodayStatus,
        val weekIndex: Int?,
        val timeSlots: List<TimeSlot>,
        val startDate: LocalDate?,
        val totalWeeks: Int,
        val firstDayOfWeek: Int,
        val isSkippedDay: Boolean
    )

    private fun createSuccessState(
        courses: List<CourseWithWeeks>,
        tomorrowCourses: List<CourseWithWeeks>,
        snapshot: DataSnapshot,
        today: LocalDate,
        todos: List<TodoItem> = emptyList(),
        events: List<ScheduleEvent> = emptyList(),
        nextCardMode: NextCardMode = NextCardMode.AUTO_NEXT,
        upcomingEvents: List<ScheduleEvent> = emptyList()
    ): TodayUiState.Success {
        val slotMap = snapshot.timeSlots.associateBy { it.number }

        val displayModels = courses.map { item ->
            val startSlot = slotMap[item.course.startSection]
            val endSlot = slotMap[item.course.endSection]
            CourseDisplayModel(
                course = item.course,
                startTime = item.course.customStartTime ?: startSlot?.startTime,
                endTime = item.course.customEndTime ?: endSlot?.endTime
            )
        }.sortedWith(
            compareBy<CourseDisplayModel> { it.startTime ?: MAX_TIME_SORT_KEY }
                .thenBy { it.endTime ?: MAX_TIME_SORT_KEY }
        )

        val tomorrowDisplayModels = tomorrowCourses.map { item ->
            val startSlot = slotMap[item.course.startSection]
            val endSlot = slotMap[item.course.endSection]
            CourseDisplayModel(
                course = item.course,
                startTime = item.course.customStartTime ?: startSlot?.startTime,
                endTime = item.course.customEndTime ?: endSlot?.endTime
            )
        }.sortedWith(
            compareBy<CourseDisplayModel> { it.startTime ?: MAX_TIME_SORT_KEY }
                .thenBy { it.endTime ?: MAX_TIME_SORT_KEY }
        )

        return TodayUiState.Success(
            courses = displayModels,
            tomorrowCourses = tomorrowDisplayModels,
            todos = todos,
            events = events,
            weekIndex = snapshot.weekIndex ?: 0,
            today = today,
            status = snapshot.status,
            startDate = snapshot.startDate,
            totalWeeks = snapshot.totalWeeks,
            firstDayOfWeek = snapshot.firstDayOfWeek,
            nextCardMode = nextCardMode,
            upcomingEvents = upcomingEvents
        )
    }
}

data class CourseDisplayModel(
    val course: Course,
    val startTime: String?,
    val endTime: String?
)

enum class TodayStatus { Normal, NoSemesterConfig, SemesterEnded, Vacation }

sealed class TodayUiState {
    data object Loading : TodayUiState()
    data class Success(
        val courses: List<CourseDisplayModel>,
        val tomorrowCourses: List<CourseDisplayModel>,
        val todos: List<TodoItem>,
        val events: List<ScheduleEvent> = emptyList(),
        val weekIndex: Int,
        val today: LocalDate,
        val status: TodayStatus,
        val startDate: LocalDate?,
        val totalWeeks: Int,
        val firstDayOfWeek: Int,
        /** 下节课卡「今日课程结束后」行为（v3.47.0）。 */
        val nextCardMode: NextCardMode = NextCardMode.AUTO_NEXT,
        /** 今天起未来若干天的日程（供「下一次日程」跨天解析，按日期升序）。 */
        val upcomingEvents: List<ScheduleEvent> = emptyList()
    ) : TodayUiState()
}
