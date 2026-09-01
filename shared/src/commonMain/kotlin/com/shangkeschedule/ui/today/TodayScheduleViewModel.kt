package com.shangkeschedule.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.Course
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import com.shangkeschedule.data.repository.StyleSettingsRepository
import com.shangkeschedule.data.repository.TimeSlotRepository
import com.shangkeschedule.data.time.currentDateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class TodayScheduleViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository,
    private val styleSettingsRepository: StyleSettingsRepository,
    private val timeSlotRepository: TimeSlotRepository
) : ViewModel() {

    companion object {
        private const val DEFAULT_SEMESTER_TOTAL_WEEKS = 20
        private const val MAX_TIME_SORT_KEY = "99:99"
    }

    val gridStyle: StateFlow<ScheduleGridStyle> = styleSettingsRepository.styleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleGridStyle())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TodayUiState> = combine(
        appSettingsRepository.getAppSettings(),
        // 跨天自动重算：today 不再是流装配期的一次性快照，午夜后状态机与课程查询全部刷新
        currentDateFlow()
    ) { settings, today ->
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
                    if (snapshot.status == TodayStatus.Normal && snapshot.weekIndex != null && !snapshot.isSkippedDay) {
                        val selfCoursesFlow = courseTableRepository.getCoursesForDay(tableId, snapshot.weekIndex, dayOfWeek)

                        // 情侣课表模式：合并本人课程与 crush 课程，并统一着色
                        val combinedFlow = if (settings.coupleScheduleEnabled) {
                            combine(
                                selfCoursesFlow,
                                courseTableRepository.getCrushCoursesForDay(tableId, snapshot.weekIndex, dayOfWeek)
                            ) { selfCourses, crushCourses ->
                                val selfColored = selfCourses.map { cw ->
                                    cw.copy(course = cw.course.copy(colorInt = settings.selfCourseColorIndex))
                                }
                                val crushColored = crushCourses.map { cw ->
                                    cw.copy(course = cw.course.copy(colorInt = settings.crushCourseColorIndex))
                                }
                                selfColored + crushColored
                            }
                        } else {
                            selfCoursesFlow
                        }

                        combinedFlow.map { courses ->
                            createSuccessState(courses, snapshot, today)
                        }
                    } else {
                        // 如果是跳过日期或非正常学期状态，直接返回空课程列表
                        flowOf(createSuccessState(emptyList(), snapshot, today))
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState.Loading)

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
        snapshot: DataSnapshot,
        today: LocalDate
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

        return TodayUiState.Success(
            courses = displayModels,
            weekIndex = snapshot.weekIndex ?: 0,
            today = today,
            status = snapshot.status,
            startDate = snapshot.startDate,
            totalWeeks = snapshot.totalWeeks,
            firstDayOfWeek = snapshot.firstDayOfWeek
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
        val weekIndex: Int,
        val today: LocalDate,
        val status: TodayStatus,
        val startDate: LocalDate?,
        val totalWeeks: Int,
        val firstDayOfWeek: Int
    ) : TodayUiState()
}