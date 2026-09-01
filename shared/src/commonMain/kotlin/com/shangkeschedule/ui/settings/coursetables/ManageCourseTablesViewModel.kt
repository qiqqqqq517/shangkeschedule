package com.shangkeschedule.ui.settings.coursetables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/**
 * 负责管理课表管理界面的所有状态和业务逻辑。
 */
@KoinViewModel
class ManageCourseTablesViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository
) : ViewModel() {

    // 组合两个数据流，提供一个包含课表列表和当前选中ID的单一UI状态
    val uiState: StateFlow<ManageCourseTablesUiState> = combine(
        courseTableRepository.getAllCourseTables(),
        appSettingsRepository.getAppSettings()
    ) { courseTables, appSettings ->
        ManageCourseTablesUiState(
            courseTables = courseTables,
            currentActiveTableId = appSettings.currentCourseTableId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ManageCourseTablesUiState()
    )

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
 * 封装 UI 状态的数据类，减少 UI 层的复杂性。
 */
data class ManageCourseTablesUiState(
    val courseTables: List<CourseTable> = emptyList(),
    val currentActiveTableId: String? = null
)