package com.shangkeschedule.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.ScheduleCategory
import com.shangkeschedule.data.db.main.ScheduleEvent
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.main.TodoItem
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import com.shangkeschedule.data.repository.ScheduleEventRepository
import com.shangkeschedule.data.repository.TimeSlotRepository
import com.shangkeschedule.data.repository.TodoRepository
import com.shangkeschedule.tool.LunarCalendar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Clock

/**
 * 「日程」页视图模型。
 *
 * 数据来源三条：
 * 1. 课表课程：按选中日期换算周次后取当日课程（教师 / 地点来自课程实体）；
 * 2. 自建日程：schedule_events 表按选中日期过滤；
 * 3. 今日待办：todo_items 表按选中日期过滤（与今日页「待办」列表同一数据源，
 *    勾选完成状态双向同步）。
 * 三者合并为同一条时间轴，按开始时间排序，供日程列表展示。
 */
@KoinViewModel
class AgendaViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val scheduleEventRepository: ScheduleEventRepository,
    private val todoRepository: TodoRepository
) : ViewModel() {

    companion object {
        private const val DEFAULT_SEMESTER_TOTAL_WEEKS = 20
        private const val TIME_SORT_FALLBACK = "99:99"

        /** 整月日历固定 6 行 × 7 列 = 42 格，保证上下月切换时高度不跳动。 */
        private const val MONTH_GRID_CELLS = 42

        /**
         * 顶部日期滚轴：以选中日为中线、前后各取的天数（共 2×N+1 天）。
         *
         * v3.46.0 起顶部日期由「翻页式周条」改为**滚动式日期轴**（连续滚动 + 按天吸附），
         * 因此需要一次给出足够长的日期序列；用户滚到窗口边缘也无妨 —— 吸附落定后
         * 选中日会变化，状态随之以新的选中日为中线重建，等效于"无限滚动"。
         */
        private const val STRIP_HALF_SPAN_DAYS = 120

        /** 事件查询在月份前后各多取的天数：周条与整月网格都会显示相邻月份的日期。 */
        private const val EVENT_QUERY_PADDING_DAYS = 7
    }

    private val todayDate: LocalDate
        get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private val selectedDate = MutableStateFlow(todayDate)

    private val visibleMonth = MutableStateFlow(
        MonthKey(todayDate.year, todayDate.month.number)
    )

    /** 选中日期变化（供新建日程默认日期使用）。 */
    val currentSelectedDate: StateFlow<LocalDate> = selectedDate

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AgendaUiState> = combine(
        appSettingsRepository.getAppSettings(),
        selectedDate,
        visibleMonth
    ) { settings, date, month -> Triple(settings, date, month) }
        .flatMapLatest { (settings, date, month) ->
            val tableId = settings.currentCourseTableId
            // 查询范围要同时覆盖：① 整月日历含月外补白格 → 前后各多取 7 天；
            // ② 顶部日期滚轴以选中日为中心前后各 120 天 → 取两者并集（以选中日为基准放宽即可，
            //    因为 month 与 date 始终同步，月份区间必然落在该范围内）。
            val queryPadding = STRIP_HALF_SPAN_DAYS + EVENT_QUERY_PADDING_DAYS
            val monthEventsFlow = scheduleEventRepository.getEventsBetweenDates(
                date.minus(queryPadding, DateTimeUnit.DAY).toString(),
                date.plus(queryPadding, DateTimeUnit.DAY).toString()
            )

            appSettingsRepository.getCourseTableConfigFlow(tableId).flatMapLatest { config ->
                timeSlotRepository.getActiveTimeSlotsByConfigFlow(tableId, flowOf(config))
                    .flatMapLatest { slots ->
                        val firstDayOfWeek = config?.firstDayOfWeek ?: DayOfWeek.MONDAY.isoDayNumber
                        val totalWeeks = config?.semesterTotalWeeks ?: DEFAULT_SEMESTER_TOTAL_WEEKS
                        val weekIndex = appSettingsRepository.getWeekIndexAtDate(
                            targetDate = date,
                            startDateStr = config?.semesterStartDate,
                            firstDayOfWeekInt = firstDayOfWeek
                        )
                        val dayOfWeek = date.dayOfWeek.isoDayNumber

                        val coursesFlow: kotlinx.coroutines.flow.Flow<List<CourseWithWeeks>> =
                            if (weekIndex != null && weekIndex in 1..totalWeeks) {
                                val selfFlow = courseTableRepository.getCoursesForDay(tableId, weekIndex, dayOfWeek)
                                if (settings.coupleScheduleEnabled) {
                                    // 情侣叠加：合并配对情侣课表课程，并按 TA 课表自己的作息
                                    // 把节次时间烘焙进副本 customStart/EndTime（不落库），
                                    // 保证 buildState 用当前表 slotMap 解析时 TA 课程时间正确。
                                    courseTableRepository.getCoupleTableFor(tableId)
                                        .flatMapLatest { coupleTable ->
                                            if (coupleTable == null) return@flatMapLatest selfFlow
                                            val coupleConfigFlow =
                                                appSettingsRepository.getCourseTableConfigFlow(coupleTable.id)
                                            combine(
                                                selfFlow,
                                                courseTableRepository.getCoursesForDay(coupleTable.id, weekIndex, dayOfWeek),
                                                timeSlotRepository.getActiveTimeSlotsByConfigFlow(coupleTable.id, coupleConfigFlow)
                                            ) { selfCourses, coupleCourses, coupleSlots ->
                                                val slotByNumber = coupleSlots.associateBy { it.number }
                                                selfCourses + coupleCourses.map { cw ->
                                                    val course = cw.course
                                                    cw.copy(
                                                        course = course.copy(
                                                            customStartTime = course.customStartTime
                                                                ?: slotByNumber[course.startSection]?.startTime,
                                                            customEndTime = course.customEndTime
                                                                ?: slotByNumber[course.endSection]?.endTime
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                } else {
                                    selfFlow
                                }
                            } else {
                                flowOf(emptyList())
                            }

                        combine(monthEventsFlow, coursesFlow, todoRepository.getTodosByDate(date.toString())) { monthEvents, courses, todos ->
                            buildState(
                                selected = date,
                                month = month,
                                firstDayOfWeek = firstDayOfWeek,
                                slots = slots,
                                monthEvents = monthEvents,
                                courses = courses,
                                todos = todos
                            )
                        }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AgendaUiState())

    /** 选中某一天。 */
    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        val month = MonthKey(date.year, date.month.number)
        if (visibleMonth.value != month) visibleMonth.value = month
    }

    /** 上一月（同时把选中日期与周条带进上一个月，见 [applyMonth]）。 */
    fun previousMonth() {
        applyMonth(visibleMonth.value.shift(-1))
    }

    /** 下一月（同时把选中日期与周条带进下一个月，见 [applyMonth]）。 */
    fun nextMonth() {
        applyMonth(visibleMonth.value.shift(1))
    }

    /**
     * 直接跳到指定年月（月份选择器 / 「点击月份」入口）。
     *
     * 与 [previousMonth] / [nextMonth] 共用 [applyMonth]，保证「切月份 ⇒ 下面的日期一起对应过去」。
     */
    fun selectMonth(year: Int, month: Int) {
        val target = MonthKey(year, month)
        val current = visibleMonth.value
        val selected = selectedDate.value
        val alreadyThere = current == target &&
            selected.year == year && selected.month.number == month
        if (!alreadyThere) applyMonth(target)
    }

    /**
     * 切换可见月份，并把**选中的日期一起带过去**。
     *
     * 修复「月份变了、下面的日期还停在上个月」：日期按「同月同日」平移，
     * 目标月没有该日（如 10/31 → 9 月）则收敛到月末（修改为 9/30）。
     */
    private fun applyMonth(target: MonthKey) {
        val day = selectedDate.value.day.coerceAtMost(daysInMonth(target))
        selectedDate.value = LocalDate(target.year, target.month, day)
        visibleMonth.value = target
    }

    /** 目标月份的天数。 */
    private fun daysInMonth(month: MonthKey): Int =
        LocalDate(month.year, month.month, 1)
            .plus(1, DateTimeUnit.MONTH)
            .minus(1, DateTimeUnit.DAY)
            .day

    /** 回到今天（同时把可见月份与选中日期拉回今天）。 */
    fun goToToday() {
        val today = todayDate
        selectedDate.value = today
        visibleMonth.value = MonthKey(today.year, today.month.number)
    }

    /** 新增日程。 */
    fun addEvent(
        date: LocalDate,
        title: String,
        category: ScheduleCategory,
        isAllDay: Boolean,
        startTime: String?,
        endTime: String?,
        location: String?,
        note: String?
    ) {
        viewModelScope.launch {
            scheduleEventRepository.addEvent(
                date = date.toString(),
                title = title,
                category = category,
                isAllDay = isAllDay,
                startTime = startTime,
                endTime = endTime,
                location = location,
                note = note
            )
        }
    }

    /** 删除条目：待办删 todo_items 表，自建日程删 schedule_events 表（课程不可删）。 */
    fun deleteEntry(entry: AgendaEntry) {
        viewModelScope.launch {
            when (entry.source) {
                AgendaEntrySource.TODO -> todoRepository.deleteTodo(entry.id)
                AgendaEntrySource.EVENT -> scheduleEventRepository.deleteEvent(entry.id)
            }
        }
    }

    /** 按条目来源切换完成状态（待办 → todo_items，自建日程 → schedule_events）。 */
    fun toggleEntryDone(entry: AgendaEntry) {
        when (entry.source) {
            AgendaEntrySource.TODO -> setTodoDone(entry.id, !entry.done)
            AgendaEntrySource.EVENT -> setEventDone(entry.id, !entry.done)
        }
    }

    /** 设置自建日程的完成状态（仅「待办」分类的日程在 UI 上展示勾选）。 */
    fun setEventDone(eventId: String, done: Boolean) {
        viewModelScope.launch {
            scheduleEventRepository.setDone(eventId, done)
        }
    }

    /** 设置今日待办（todo_items）的完成状态，与今日页勾选同一数据源。 */
    fun setTodoDone(todoId: String, done: Boolean) {
        viewModelScope.launch {
            todoRepository.setDone(todoId, done)
        }
    }

    private fun buildState(
        selected: LocalDate,
        month: MonthKey,
        firstDayOfWeek: Int,
        slots: List<TimeSlot>,
        monthEvents: List<ScheduleEvent>,
        courses: List<CourseWithWeeks>,
        todos: List<TodoItem>
    ): AgendaUiState {
        val today = todayDate
        val slotMap = slots.associateBy { it.number }
        // 日历上的「事件点」：自建日程覆盖整段查询范围；待办只按选中日查询，
        // 因此仅选中日会因待办而标记事件点。
        val eventDates = monthEvents.map { it.date }.toSet() +
            todos.map { it.date }.toSet()

        // 顶部日期滚轴：以选中日为中线的前后各 STRIP_HALF_SPAN_DAYS 天。
        // UI 侧是 LazyRow 连续滚动 + 按天吸附，中线那天即当前选中日。
        val stripDays = (-STRIP_HALF_SPAN_DAYS..STRIP_HALF_SPAN_DAYS).map { offset ->
            val date = selected.plus(offset, DateTimeUnit.DAY)
            AgendaDayCell(
                date = date,
                lunarLabel = LunarCalendar.dayLabel(date),
                isSelected = offset == 0,
                isToday = date == today,
                hasEvents = eventDates.contains(date.toString()),
                isInMonth = isSameMonth(date, month)
            )
        }

        // 整月日历（下拉展开）：固定 6×7 = 42 格，首格对齐 firstDayOfWeek，
        // 月外的补白格（上月末 / 下月初）同样渲染，但标记 isInMonth = false 供 UI 淡化。
        val monthStart = LocalDate(month.year, month.month, 1)
        val offsetFromMonthStart = (monthStart.dayOfWeek.isoDayNumber - firstDayOfWeek + 7) % 7
        val gridStart = monthStart.minus(offsetFromMonthStart, DateTimeUnit.DAY)
        val monthCells = (0 until MONTH_GRID_CELLS).map { index ->
            val date = gridStart.plus(index, DateTimeUnit.DAY)
            AgendaDayCell(
                date = date,
                lunarLabel = LunarCalendar.dayLabel(date),
                isSelected = date == selected,
                isToday = date == today,
                hasEvents = eventDates.contains(date.toString()),
                isInMonth = isSameMonth(date, month)
            )
        }

        val dayEvents = monthEvents.filter { it.date == selected.toString() }

        val entries = buildList {
            courses.forEach { item ->
                val course = item.course
                add(
                    AgendaEntry(
                        id = course.id,
                        title = course.name,
                        isCourse = true,
                        categoryKey = null,
                        location = course.position.takeIf { it.isNotBlank() },
                        teacher = course.teacher.takeIf { it.isNotBlank() },
                        note = null,
                        startTime = course.customStartTime
                            ?: course.startSection?.let { slotMap[it]?.startTime },
                        endTime = course.customEndTime
                            ?: course.endSection?.let { slotMap[it]?.endTime },
                        isAllDay = false,
                        isCrush = course.isCrush
                    )
                )
            }
            dayEvents.forEach { event ->
                add(
                    AgendaEntry(
                        id = event.id,
                        title = event.title,
                        isCourse = false,
                        categoryKey = event.category,
                        location = event.location,
                        teacher = null,
                        note = event.note,
                        startTime = event.startTime,
                        endTime = event.endTime,
                        isAllDay = event.isAllDay,
                        isCrush = false,
                        done = event.done,
                        source = AgendaEntrySource.EVENT
                    )
                )
            }
            todos.forEach { todo ->
                add(
                    AgendaEntry(
                        id = todo.id,
                        title = todo.title,
                        isCourse = false,
                        categoryKey = ScheduleCategory.TODO.key,
                        location = null,
                        teacher = null,
                        note = todo.note,
                        startTime = todo.time?.takeIf { it.isNotBlank() },
                        endTime = null,
                        isAllDay = todo.time.isNullOrBlank(),
                        isCrush = false,
                        done = todo.done,
                        source = AgendaEntrySource.TODO
                    )
                )
            }
        }.sortedWith(
            compareBy<AgendaEntry> { if (it.isAllDay) 0 else 1 }
                .thenBy { it.startTime ?: TIME_SORT_FALLBACK }
                .thenBy { it.title }
        )

        return AgendaUiState(
            isLoaded = true,
            today = today,
            selectedDate = selected,
            month = month,
            firstDayOfWeek = firstDayOfWeek,
            stripDays = stripDays,
            stripCenterIndex = STRIP_HALF_SPAN_DAYS,
            monthCells = monthCells,
            entries = entries,
            lunarText = LunarCalendar.lunarText(selected)
        )
    }

    /** 判断某天是否落在可见月份内（用于淡化月外补白格）。 */
    private fun isSameMonth(date: LocalDate, month: MonthKey): Boolean =
        date.year == month.year && date.month.number == month.month
}

/** 月份键（年 + 月）。 */
data class MonthKey(val year: Int, val month: Int) {
    fun shift(delta: Int): MonthKey {
        val total = year * 12 + (month - 1) + delta
        return MonthKey(total / 12, total % 12 + 1)
    }
}

/** 周条 / 日期滚轴单元格。整月日历复用同一模型（[isInMonth] 区分月内 / 月外补白格）。 */
data class AgendaDayCell(
    val date: LocalDate,
    val lunarLabel: String,
    val isSelected: Boolean,
    val isToday: Boolean,
    val hasEvents: Boolean,
    /** 是否属于当前可见月份。月外补白格（上月末 / 下月初）为 false，UI 淡化显示。 */
    val isInMonth: Boolean = true
)

/** 日程条目的数据来源（决定勾选 / 删除落到哪张表）。 */
enum class AgendaEntrySource {
    /** 自建日程（schedule_events）。 */
    EVENT,

    /** 今日待办（todo_items，与今日页「待办」列表同源）。 */
    TODO
}

/** 日程条目（课程、自建日程与今日待办统一展示模型）。 */
data class AgendaEntry(
    val id: String,
    val title: String,
    val isCourse: Boolean,
    val categoryKey: String?,
    val location: String?,
    val teacher: String?,
    val note: String?,
    val startTime: String?,
    val endTime: String?,
    val isAllDay: Boolean,
    val isCrush: Boolean,
    /** 完成状态（仅「待办」类条目使用，课程恒为 false）。 */
    val done: Boolean = false,
    /** 数据来源，默认自建日程。 */
    val source: AgendaEntrySource = AgendaEntrySource.EVENT
)

/** 日程页 UI 状态。 */
data class AgendaUiState(
    val isLoaded: Boolean = false,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val selectedDate: LocalDate = LocalDate(2000, 1, 1),
    val month: MonthKey = MonthKey(2000, 1),
    val firstDayOfWeek: Int = DayOfWeek.MONDAY.isoDayNumber,
    /**
     * 顶部日期滚轴（v3.46.0）：以选中日为中线、前后各 120 天，UI 侧连续滚动 + 按天吸附。
     */
    val stripDays: List<AgendaDayCell> = emptyList(),
    /** [stripDays] 中「选中日」所在下标（中线）。 */
    val stripCenterIndex: Int = 0,
    /** 整月日历单元格（下拉展开时显示，固定 6×7 = 42 格，含月外补白格）。 */
    val monthCells: List<AgendaDayCell> = emptyList(),
    val entries: List<AgendaEntry> = emptyList(),
    val lunarText: String = ""
)
