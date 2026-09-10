package com.shangkeschedule.ui.settings.coursetables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Clock

/**
 * 负责管理课表管理界面的所有状态和业务逻辑。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class ManageCourseTablesViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository
) : ViewModel() {

    // 组合课表列表 + 当前选中 ID + 每张课表的配置/课程数，产出一个完整的 UI 状态
    val uiState: StateFlow<ManageCourseTablesUiState> = combine(
        courseTableRepository.getAllCourseTables(),
        appSettingsRepository.getAppSettings()
    ) { courseTables, appSettings ->
        courseTables to appSettings.currentCourseTableId
    }.flatMapLatest { (courseTables, activeTableId) ->
        if (courseTables.isEmpty()) {
            flowOf(buildUiState(courseTables, activeTableId, emptyList()))
        } else {
            combine(
                courseTables.map { table ->
                    combine(
                        appSettingsRepository.getCourseTableConfigFlow(table.id),
                        courseTableRepository.getCoursesWithWeeksByTableId(table.id)
                    ) { config, courses ->
                        SemesterInfo(table = table, config = config, courseCount = courses.size)
                    }
                }
            ) { infos ->
                buildUiState(courseTables, activeTableId, infos.toList())
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ManageCourseTablesUiState()
    )

    /**
     * 根据课表列表、当前选中 ID 与各课表信息，组装学年分组与学期进度。
     */
    private fun buildUiState(
        courseTables: List<CourseTable>,
        activeTableId: String?,
        infos: List<SemesterInfo>
    ): ManageCourseTablesUiState {
        val current = infos.firstOrNull { it.table.id == activeTableId }
            ?: infos.firstOrNull()

        // 历史学期 = 除当前学期外的所有课表，按学年分组（组内保持创建时间倒序）
        val historyCurrentId = current?.table?.id
        val history = infos.filter { it.table.id != historyCurrentId }
        val groups = history
            .groupBy { it.schoolYearStart() }
            .map { (startYear, semesters) ->
                YearGroup(startYear = startYear, semesters = semesters)
            }
            .sortedByDescending { it.startYear }

        // 当前学期进度（未设置开学日期时为 null）
        var currentWeek: Int? = null
        var currentWeekPercent: Int? = null
        val config = current?.config
        if (current != null && config != null && !config.semesterStartDate.isNullOrEmpty()) {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val rawWeek = appSettingsRepository.getWeekIndexAtDate(
                targetDate = today,
                startDateStr = config.semesterStartDate,
                firstDayOfWeekInt = config.firstDayOfWeek
            )
            if (rawWeek != null) {
                val totalWeeks = current.totalWeeks.coerceAtLeast(1)
                val displayWeek = rawWeek.coerceIn(1, totalWeeks)
                currentWeek = displayWeek
                currentWeekPercent = (displayWeek * 100) / totalWeeks
            }
        }

        return ManageCourseTablesUiState(
            courseTables = courseTables,
            currentActiveTableId = activeTableId,
            currentSemester = current,
            currentWeek = currentWeek,
            currentWeekPercent = currentWeekPercent,
            historyGroups = groups
        )
    }

    /**
     * 创建一个新的课表。
     * @param newTableName 新课表的名称。
     */
    fun createNewCourseTable(newTableName: String) {
        viewModelScope.launch {
            courseTableRepository.createNewCourseTable(newTableName)
        }
    }

    /**
     * 更新一个课表。
     * @param updatedCourseTable 包含新信息的课表对象。
     */
    fun updateCourseTable(updatedCourseTable: CourseTable) {
        viewModelScope.launch {
            courseTableRepository.updateCourseTable(updatedCourseTable)
        }
    }

    /**
     * 切换当前激活的课表。
     * @param tableId 要切换到的课表ID。
     */
    fun switchCourseTable(tableId: String) {
        viewModelScope.launch {
            val currentSettings = appSettingsRepository.getAppSettings().first()
            val newSettings = currentSettings.copy(currentCourseTableId = tableId)
            appSettingsRepository.insertOrUpdateAppSettings(newSettings)
        }
    }

    /**
     * 删除一个课表。
     * @param courseTable 要删除的课表对象。
     */
    fun deleteCourseTable(courseTable: CourseTable) {
        viewModelScope.launch {
            courseTableRepository.deleteCourseTableAndResolveCurrent(courseTable)
        }
    }
}

/**
 * 单个学期（课表）的展示信息：实体 + 配置 + 课程数。
 */
data class SemesterInfo(
    val table: CourseTable,
    val config: CourseTableConfig?,
    val courseCount: Int
) {
    /** 学期总周数（未配置时回退到默认 20）。 */
    val totalWeeks: Int get() = config?.semesterTotalWeeks ?: 20

    /** 学期开始日期（未设置为 null）。 */
    val startDate: LocalDate?
        get() = config?.semesterStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** 学期结束日期（开始日期 + 总周数 - 1 天；未设置开始日期为 null）。 */
    val endDate: LocalDate?
        get() = startDate?.let { LocalDate.fromEpochDays(it.toEpochDays() + totalWeeks * 7 - 1) }

    /**
     * 所属学年起始年：优先按学期开始日期推算（9 月及以后归入下一学年），
     * 未设置开始日期时回退到课表创建时间。
     */
    fun schoolYearStart(): Int {
        val base = startDate ?: runCatching {
            Instant.fromEpochMilliseconds(table.createdAt)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
        }.getOrNull()
        return if (base == null) {
            0
        } else if (base.monthNumber >= 9) {
            base.year
        } else {
            base.year - 1
        }
    }
}

/**
 * 按学年分组的历史学期。
 */
data class YearGroup(
    val startYear: Int,
    val semesters: List<SemesterInfo>
)

/**
 * 封装 UI 状态的数据类，减少 UI 层的复杂性。
 */
data class ManageCourseTablesUiState(
    val courseTables: List<CourseTable> = emptyList(),
    val currentActiveTableId: String? = null,
    /** 当前学期（激活课表）信息。 */
    val currentSemester: SemesterInfo? = null,
    /** 当前周（1..总周数）；未设置开学日期时为 null。 */
    val currentWeek: Int? = null,
    /** 学期进度百分比（0..100）；未设置开学日期时为 null。 */
    val currentWeekPercent: Int? = null,
    /** 历史学期学年分组（学年起始年倒序）。 */
    val historyGroups: List<YearGroup> = emptyList()
)
