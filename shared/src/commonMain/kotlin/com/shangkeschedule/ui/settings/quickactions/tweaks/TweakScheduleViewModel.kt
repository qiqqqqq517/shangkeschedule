package com.shangkeschedule.ui.settings.quickactions.tweaks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.until
import kotlinx.datetime.todayIn
import com.shangkeschedule.ui.settings.quickactions.UiTextRes
import org.koin.core.annotation.KoinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.error_tweak_failed
import shangkeschedule.shared.generated.resources.error_tweak_no_table_or_semester
import shangkeschedule.shared.generated.resources.error_tweak_same_day
import shangkeschedule.shared.generated.resources.toast_tweak_success
import kotlin.time.Clock

/**
 * 获取当前系统本地日期的辅助函数
 */
private fun todayLocalDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/**
 * 调课页面 UI 状态。
 */
data class TweakScheduleUiState(
    // UI 显示所需的数据
    val allCourseTables: List<CourseTable> = emptyList(),
    val selectedCourseTable: CourseTable? = null,
    val fromDate: LocalDate = todayLocalDate(),
    val toDate: LocalDate = todayLocalDate(),
    val fromCourses: List<CourseWithWeeks> = emptyList(),
    val toCourses: List<CourseWithWeeks> = emptyList(),
    val tweakMode: CourseTableRepository.TweakMode = CourseTableRepository.TweakMode.MERGE,

    // 业务逻辑和状态管理所需的数据
    val isSemesterSet: Boolean = false,
    val semesterStartDate: LocalDate? = null,
    val isLoading: Boolean = false,
    val errorMessage: UiTextRes? = null,
    val successMessage: UiTextRes? = null
)

/**
 * 课程调动页面的 ViewModel。
 */
@KoinViewModel
class TweakScheduleViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository
) : ViewModel() {

    // UI 暴露的状态
    private val _uiState = MutableStateFlow(TweakScheduleUiState())
    val uiState: StateFlow<TweakScheduleUiState> = _uiState.asStateFlow()

    // 内部存储用户选择的私有 Flow
    private val _fromDate = MutableStateFlow(todayLocalDate())
    private val _toDate = MutableStateFlow(todayLocalDate())
    private val _selectedCourseTableByUser = MutableStateFlow<CourseTable?>(null)

    init {
        viewModelScope.launch {
            refreshUiState(isInitialLoad = true)
        }
    }

    /**
     * 刷新 UI 状态：加载配置、课表以及预览区域的课程。
     */
    private suspend fun refreshUiState(isInitialLoad: Boolean = false) {
        val settings = appSettingsRepository.getAppSettings().first()
        val allTables = courseTableRepository.getAllCourseTables().first()

        val selectedTable = if (isInitialLoad) {
            val defaultSelectedTable = allTables.find { it.id == settings.currentCourseTableId }
            _selectedCourseTableByUser.value = defaultSelectedTable
            defaultSelectedTable
        } else {
            _selectedCourseTableByUser.value
        }

        val currentFromDate = _fromDate.value
        val currentToDate = _toDate.value

        val currentTableId = selectedTable?.id
        val courseConfig = if (currentTableId != null) {
            appSettingsRepository.getCourseConfigOnce(currentTableId)
        } else {
            null
        }

        val semesterStartDateString = courseConfig?.semesterStartDate
        val semesterStartDate: LocalDate? = try {
            semesterStartDateString?.let { LocalDate.parse(it) }
        } catch (_: Exception) {
            null
        }
        val isSemesterSet = semesterStartDate != null

        var fromCourses = emptyList<CourseWithWeeks>()
        var toCourses = emptyList<CourseWithWeeks>()

        if (isSemesterSet && selectedTable != null) {
            val firstDayOfWeekInt = courseConfig?.firstDayOfWeek ?: DayOfWeek.MONDAY.isoDayNumber
            // R46-01 / R47-01：此前用 `semesterStartDate.until(date, DateTimeUnit.WEEK) + 1`
            // 自算周次，**未对齐**到全仓统一的 `getWeekIndexAtDate`（该算法把开始日与目标日
            // 双方都回退到同一周起始日，并支持「周日为一周起始」的用户设置）。
            // 后果：学期开始日落在周五 / 周六 / 周日 时，本页算出的周号比统一算法小 1 ⇒
            // ① 预览展示的是上一周的课；② moveCourses() 用同一错误公式**写库**，把上一周的
            // 课移进目标周；而 ICS 导出 / 小组件 / 今日页都按统一算法消费 weekNumber ⇒
            // 用户在课表页看到「课被移到了错误的周」，且三处显示互相矛盾，全程无任何提示。
            // 实测 15 组日期不一致，且每次查询与写入都发生，不是偶发。
            // 修法：收敛到统一算法（全仓其余 7 个调用方都走它）。
            val fromWeekNumber = appSettingsRepository.getWeekIndexAtDate(
                targetDate = currentFromDate,
                startDateStr = semesterStartDateString,
                firstDayOfWeekInt = firstDayOfWeekInt
            )
            val toWeekNumber = appSettingsRepository.getWeekIndexAtDate(
                targetDate = currentToDate,
                startDateStr = semesterStartDateString,
                firstDayOfWeekInt = firstDayOfWeekInt
            )
            val fromDay = currentFromDate.dayOfWeek.ordinal + 1
            val toDay = currentToDate.dayOfWeek.ordinal + 1

            if (fromWeekNumber != null && toWeekNumber != null) {
                fromCourses = courseTableRepository.getCoursesForDay(selectedTable.id, fromWeekNumber, fromDay).first()
                toCourses = courseTableRepository.getCoursesForDay(selectedTable.id, toWeekNumber, toDay).first()
            }
        }

        _uiState.update {
            it.copy(
                allCourseTables = allTables,
                isSemesterSet = isSemesterSet,
                selectedCourseTable = selectedTable,
                fromDate = currentFromDate,
                toDate = currentToDate,
                fromCourses = fromCourses,
                toCourses = toCourses,
                semesterStartDate = semesterStartDate,
                isLoading = false
            )
        }
    }

    // 响应 UI 层更改调课模式
    fun onTweakModeChanged(mode: CourseTableRepository.TweakMode) {
        _uiState.update { it.copy(tweakMode = mode) }
    }

    fun onCourseTableSelected(courseTable: CourseTable) {
        _selectedCourseTableByUser.value = courseTable
        viewModelScope.launch { refreshUiState() }
    }

    fun onFromDateSelected(date: LocalDate) {
        _fromDate.value = date
        viewModelScope.launch { refreshUiState() }
    }

    fun onToDateSelected(date: LocalDate) {
        _toDate.value = date
        viewModelScope.launch { refreshUiState() }
    }

    /**
     * 执行课程调动操作。
     */
    fun moveCourses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

            val state = _uiState.value

            if (state.selectedCourseTable == null || state.semesterStartDate == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = UiTextRes(Res.string.error_tweak_no_table_or_semester)
                    )
                }
                return@launch
            }

            if (state.fromDate == state.toDate) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = UiTextRes(Res.string.error_tweak_same_day)
                    )
                }
                return@launch
            }

            try {
                val semesterStartDate = state.semesterStartDate
                // R46-01 / R47-01：写入侧必须与预览侧同一口径，同样收敛到统一算法。
                // 只改预览不改写入，会让「看到的」与「写入的」继续互相矛盾。
                val courseConfig = appSettingsRepository.getCourseConfigOnce(state.selectedCourseTable.id)
                val firstDayOfWeekInt = courseConfig?.firstDayOfWeek ?: DayOfWeek.MONDAY.isoDayNumber
                val fromWeek = appSettingsRepository.getWeekIndexAtDate(
                    targetDate = state.fromDate,
                    startDateStr = courseConfig?.semesterStartDate,
                    firstDayOfWeekInt = firstDayOfWeekInt
                )
                val toWeek = appSettingsRepository.getWeekIndexAtDate(
                    targetDate = state.toDate,
                    startDateStr = courseConfig?.semesterStartDate,
                    firstDayOfWeekInt = firstDayOfWeekInt
                )
                val fromDay = state.fromDate.dayOfWeek.ordinal + 1
                val toDay = state.toDate.dayOfWeek.ordinal + 1
                if (fromWeek == null || toWeek == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = UiTextRes(Res.string.error_tweak_no_table_or_semester)
                        )
                    }
                    return@launch
                }

                courseTableRepository.tweakCoursesOnDate(
                    mode = state.tweakMode, // 传入当前选中的模式
                    courseTableId = state.selectedCourseTable.id,
                    fromWeek = fromWeek,
                    fromDay = fromDay,
                    toWeek = toWeek,
                    toDay = toDay
                )

                // 操作成功后刷新预览
                refreshUiState()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = UiTextRes(Res.string.toast_tweak_success)
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = UiTextRes(Res.string.error_tweak_failed, listOf(e.message ?: ""))
                    )
                }
            }
        }
    }

    fun resetMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
