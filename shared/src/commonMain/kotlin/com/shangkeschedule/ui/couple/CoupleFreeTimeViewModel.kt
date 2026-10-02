package com.shangkeschedule.ui.couple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.logic.CoupleFreeTimeCalculator
import com.shangkeschedule.data.logic.FreeTimeBlock
import com.shangkeschedule.data.logic.MinuteRange
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import com.shangkeschedule.data.repository.TimeSlotRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.koin.core.annotation.KoinViewModel

/** 某一天的空闲结果。 */
data class DayFreeTime(
    val day: Int,
    val blocks: List<FreeTimeBlock>
)

/** D2「找共同空闲」界面状态。 */
data class CoupleFreeTimeUiState(
    val isReady: Boolean = false,
    /** 是否存在可用的情侣课表（没有就不必算）。 */
    val hasCoupleTable: Boolean = false,
    val selfTableName: String = "",
    val coupleTableName: String = "",
    val selfCourseCount: Int = 0,
    val coupleCourseCount: Int = 0,
    val totalWeeks: Int = 20,
    val selectedWeek: Int = 1,
    /** 数据库推算出的当前自然周（学期日期没设时为 null）。 */
    val currentWeek: Int? = null,
    val showWeekends: Boolean = false,
    /** 结果按星期分组（只包含至少有一段空闲的星期）。 */
    val days: List<DayFreeTime> = emptyList(),
    /** 计算窗口（双方作息的最早开始 / 最晚结束），用于界面说明。 */
    val windowStartMinutes: Int = 0,
    val windowEndMinutes: Int = 0
)

/**
 * D2「找共同空闲」（v4.66.0）。
 *
 * 数据来源完全复用既有情侣课表能力：**不新建任何协作/账号体系**——
 * 本人课表与配对情侣课表都是本机 Room 里的普通课表（`CourseTable.isCouple` +
 * `pairedCourseTableId`），这里只是把两张表的课程按周次合并求空档。
 *
 * 作息（`time_slots`）各表独立：本人表与情侣表可以有不同节次时间，
 * 所以两边各自取「当前生效方案」的撤换点（`getActiveTimeSlotsOnce` 已含夏令时/冬令时切换）。
 */
@KoinViewModel
class CoupleFreeTimeViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository,
    private val timeSlotRepository: TimeSlotRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoupleFreeTimeUiState())
    val uiState: StateFlow<CoupleFreeTimeUiState> = _uiState.asStateFlow()

    // --- 一次性读入的原始数据（切换周次时只重算，不再查库） ---
    private var selfTableName: String = ""
    private var coupleTableName: String = ""
    private var selfCourses: List<CourseWithWeeks> = emptyList()
    private var coupleCourses: List<CourseWithWeeks> = emptyList()
    private var selfSlots: List<TimeSlot> = emptyList()
    private var coupleSlots: List<TimeSlot> = emptyList()
    private var windowStartMinutes: Int = 0
    private var windowEndMinutes: Int = 0

    init {
        viewModelScope.launch { load() }
    }

    fun selectWeek(week: Int) {
        val state = _uiState.value
        if (week !in 1..state.totalWeeks) return
        _uiState.value = state.copy(selectedWeek = week, days = computeDays(week, state.showWeekends))
    }

    private suspend fun load() {
        val settings = appSettingsRepository.getAppSettingsOnce()
        val currentId = settings.currentCourseTableId
        val currentTable = if (currentId.isEmpty()) null else courseTableRepository.getCourseTableById(currentId)
        val selfTableId = when {
            currentTable == null -> null
            currentTable.isCouple -> currentTable.pairedCourseTableId
            else -> currentTable.id
        }
        val selfTable = selfTableId?.let { courseTableRepository.getCourseTableById(it) }
        val coupleTable = selfTableId?.let { courseTableRepository.getCoupleTableForOnce(it) }

        if (selfTable == null || coupleTable == null) {
            _uiState.value = CoupleFreeTimeUiState(isReady = true, hasCoupleTable = false)
            return
        }

        selfTableName = selfTable.name
        coupleTableName = coupleTable.name
        selfCourses = courseTableRepository.getCoursesWithWeeksOnce(selfTable.id)
        coupleCourses = courseTableRepository.getCoursesWithWeeksOnce(coupleTable.id)
        val selfConfig = appSettingsRepository.getCourseConfigOnce(selfTable.id)
        val coupleConfig = appSettingsRepository.getCourseConfigOnce(coupleTable.id)
        selfSlots = timeSlotRepository.getActiveTimeSlotsOnce(selfTable.id, selfConfig)
        coupleSlots = timeSlotRepository.getActiveTimeSlotsOnce(coupleTable.id, coupleConfig)

        val showWeekends = (selfConfig ?: CourseTableConfig(selfTable.id)).showWeekends ||
            (coupleConfig ?: CourseTableConfig(coupleTable.id)).showWeekends
        val totalWeeks = (selfConfig ?: CourseTableConfig(selfTable.id)).semesterTotalWeeks
        val currentWeek = currentWeekOf(selfConfig)
        windowStartMinutes = (selfSlots + coupleSlots).mapNotNull {
            CoupleFreeTimeCalculator.parseToMinutes(it.startTime)
        }.minOrNull() ?: 8 * 60
        windowEndMinutes = (selfSlots + coupleSlots).mapNotNull {
            CoupleFreeTimeCalculator.parseToMinutes(it.endTime)
        }.maxOrNull() ?: 18 * 60
        val selectedWeek = (currentWeek ?: 1).coerceIn(1, maxOf(totalWeeks, 1))

        _uiState.value = CoupleFreeTimeUiState(
            isReady = true,
            hasCoupleTable = true,
            selfTableName = selfTableName,
            coupleTableName = coupleTableName,
            selfCourseCount = selfCourses.size,
            coupleCourseCount = coupleCourses.size,
            totalWeeks = totalWeeks,
            selectedWeek = selectedWeek,
            currentWeek = currentWeek,
            showWeekends = showWeekends,
            days = computeDays(selectedWeek, showWeekends),
            windowStartMinutes = windowStartMinutes,
            windowEndMinutes = windowEndMinutes
        )
    }

    /** 按「当前自然周」反推出学期开始日期；没设置学期首周时返回 null。 */
    private suspend fun currentWeekOf(config: CourseTableConfig?): Int? {
        val startDate = config?.semesterStartDate ?: return null
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        return appSettingsRepository.getWeekIndexAtDate(
            targetDate = today,
            startDateStr = startDate,
            firstDayOfWeekInt = config.firstDayOfWeek
        )?.takeIf { it in 1..config.semesterTotalWeeks }
    }

    private fun computeDays(week: Int, showWeekends: Boolean): List<DayFreeTime> {
        val busyByDay = mutableMapOf<Int, List<MinuteRange>>()
        addBusy(busyByDay, selfCourses, selfSlots, week)
        addBusy(busyByDay, coupleCourses, coupleSlots, week)
        val days = (1..7).filter { it <= 5 || showWeekends }
        val blocks = CoupleFreeTimeCalculator.compute(
            days = days,
            busyByDay = busyByDay,
            windowStartMinutes = windowStartMinutes,
            windowEndMinutes = windowEndMinutes
        )
        return days.mapNotNull { day ->
            val dayBlocks = blocks.filter { it.day == day }
            if (dayBlocks.isEmpty()) null else DayFreeTime(day, dayBlocks)
        }
    }

    /** 把一张课表在第 [week] 周的占用并入 [target]。 */
    private fun addBusy(
        target: MutableMap<Int, List<MinuteRange>>,
        courses: List<CourseWithWeeks>,
        slots: List<TimeSlot>,
        week: Int
    ) {
        val slotStart = mutableMapOf<Int, Int>()
        val slotEnd = mutableMapOf<Int, Int>()
        slots.forEach { slot ->
            CoupleFreeTimeCalculator.parseToMinutes(slot.startTime)?.let { slotStart[slot.number] = it }
            CoupleFreeTimeCalculator.parseToMinutes(slot.endTime)?.let { slotEnd[slot.number] = it }
        }
        courses.forEach { item ->
            if (item.weeks.none { it.weekNumber == week }) return@forEach
            val course = item.course
            val range = if (course.isCustomTime) {
                val start = CoupleFreeTimeCalculator.parseToMinutes(course.customStartTime)
                val end = CoupleFreeTimeCalculator.parseToMinutes(course.customEndTime)
                if (start != null && end != null && end > start) MinuteRange(start, end) else null
            } else {
                val startSection = course.startSection
                val endSection = course.endSection
                val start = startSection?.let { slotStart[it] }
                val end = endSection?.let { slotEnd[it] }
                if (start != null && end != null && end > start) MinuteRange(start, end) else null
            }
            if (range != null) {
                target[course.day] = target[course.day].orEmpty() + range
            }
        }
    }
}
