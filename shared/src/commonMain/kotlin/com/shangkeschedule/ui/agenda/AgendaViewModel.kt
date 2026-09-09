package com.shangkeschedule.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.ScheduleCategory
import com.shangkeschedule.data.db.main.ScheduleEvent
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import com.shangkeschedule.data.repository.ScheduleEventRepository
import com.shangkeschedule.data.repository.TimeSlotRepository
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
 * 数据来源两条：
 * 1. 课表课程：按选中日期换算周次后取当日课程（教师 / 地点来自课程实体）；
 * 2. 自建日程：schedule_events 表按选中日期过滤。
 * 两者合并为同一条时间轴，按开始时间排序，供日程列表展示。
 */
@KoinViewModel
class AgendaViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val scheduleEventRepository: ScheduleEventRepository
) : ViewModel() {

    companion object {
        private const val DEFAULT_SEMESTER_TOTAL_WEEKS = 20
        private const val TIME_SORT_FALLBACK = "99:99"
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
            val monthStart = LocalDate(month.year, month.month, 1)
            val monthEnd = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            val monthEventsFlow = scheduleEventRepository.getEventsBetweenDates(
                monthStart.toString(),
                monthEnd.toString()
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
                                    combine(
                                        selfFlow,
                                        courseTableRepository.getCrushCoursesForDay(tableId, weekIndex, dayOfWeek)
                                    ) { selfCourses, crushCourses -> selfCourses + crushCourses }
                                } else {
                                    selfFlow
                                }
                            } else {
                                flowOf(emptyList())
                            }

                        combine(monthEventsFlow, coursesFlow) { monthEvents, courses ->
                            buildState(
                                selected = date,
                                month = month,
                                firstDayOfWeek = firstDayOfWeek,
                                slots = slots,
                                monthEvents = monthEvents,
                                courses = courses
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

    /** 上一月。 */
    fun previousMonth() {
        visibleMonth.value = visibleMonth.value.shift(-1)
    }

    /** 下一月。 */
    fun nextMonth() {
        visibleMonth.value = visibleMonth.value.shift(1)
    }

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

    /** 删除日程。 */
    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            scheduleEventRepository.deleteEvent(eventId)
        }
    }

    private fun buildState(
        selected: LocalDate,
        month: MonthKey,
        firstDayOfWeek: Int,
        slots: List<TimeSlot>,
        monthEvents: List<ScheduleEvent>,
        courses: List<CourseWithWeeks>
    ): AgendaUiState {
        val today = todayDate
        val slotMap = slots.associateBy { it.number }
        val eventDates = monthEvents.map { it.date }.toSet()

        // 周条：以 firstDayOfWeek 对齐的 7 天
        val offsetFromWeekStart = (selected.dayOfWeek.isoDayNumber - firstDayOfWeek + 7) % 7
        val weekStart = selected.minus(offsetFromWeekStart, DateTimeUnit.DAY)
        val weekDays = (0 until 7).map { index ->
            val date = weekStart.plus(index, DateTimeUnit.DAY)
            AgendaDayCell(
                date = date,
                lunarLabel = LunarCalendar.dayLabel(date),
                isSelected = date == selected,
                isToday = date == today,
                hasEvents = eventDates.contains(date.toString())
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
                        isCrush = false
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
            weekDays = weekDays,
            entries = entries,
            lunarText = LunarCalendar.lunarText(selected)
        )
    }
}

/** 月份键（年 + 月）。 */
data class MonthKey(val year: Int, val month: Int) {
    fun shift(delta: Int): MonthKey {
        val total = year * 12 + (month - 1) + delta
        return MonthKey(total / 12, total % 12 + 1)
    }
}

/** 周条单元格。 */
data class AgendaDayCell(
    val date: LocalDate,
    val lunarLabel: String,
    val isSelected: Boolean,
    val isToday: Boolean,
    val hasEvents: Boolean
)

/** 日程条目（课程与自建日程统一展示模型）。 */
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
    val isCrush: Boolean
)

/** 日程页 UI 状态。 */
data class AgendaUiState(
    val isLoaded: Boolean = false,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val selectedDate: LocalDate = LocalDate(2000, 1, 1),
    val month: MonthKey = MonthKey(2000, 1),
    val firstDayOfWeek: Int = DayOfWeek.MONDAY.isoDayNumber,
    val weekDays: List<AgendaDayCell> = emptyList(),
    val entries: List<AgendaEntry> = emptyList(),
    val lunarText: String = ""
)
