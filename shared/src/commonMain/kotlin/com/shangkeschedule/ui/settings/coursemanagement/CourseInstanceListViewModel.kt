package com.shangkeschedule.ui.settings.coursemanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.model.DualColor
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import com.shangkeschedule.data.repository.StyleSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel


@KoinViewModel
class CourseInstanceListViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository,
    private val styleSettingsRepository: StyleSettingsRepository
) : ViewModel() {

    private val _courseNameFlow = MutableStateFlow<String?>(null)

    fun initCourseName(name: String) {
        if (_courseNameFlow.value == name) return
        _courseNameFlow.value = name
    }

    private val currentTableIdFlow = appSettingsRepository.getAppSettings()
        .map { it.currentCourseTableId }

    /**
     * 课程实例列表流
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val courseInstances: StateFlow<List<CourseWithWeeks>> = combine(
        currentTableIdFlow,
        _courseNameFlow
    ) { tableId, name ->
        tableId to name
    }
        .flatMapLatest { (tableId, name) ->
            if (tableId.isEmpty() || name.isNullOrEmpty()) {
                flowOf(emptyList())
            } else {
                courseTableRepository.getCoursesWithWeeksByTableId(tableId)
                    .map { allCourses ->
                        allCourses.filter { it.course.name == name }
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedCourseIds = MutableStateFlow(emptySet<String>())

    /**
     * 核心修改：封装 UI 状态流，包含动态颜色池
     */
    val uiState: StateFlow<CourseInstanceUiState> = combine(
        _isSelectionMode,
        _selectedCourseIds,
        styleSettingsRepository.styleFlow
    ) { isSelection, selectedIds, currentStyle ->
        CourseInstanceUiState(
            isSelectionMode = isSelection,
            selectedCourseIds = selectedIds,
            courseColorMaps = currentStyle.courseColorMaps
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CourseInstanceUiState()
    )

    // 常用操作函数保持不变
    fun toggleSelectionMode() {
        _isSelectionMode.update { !it }
        if (!_isSelectionMode.value) _selectedCourseIds.value = emptySet()
    }

    fun toggleCourseSelection(courseId: String) {
        _selectedCourseIds.update { currentIds ->
            if (currentIds.contains(courseId)) currentIds - courseId else currentIds + courseId
        }
        if (_selectedCourseIds.value.isNotEmpty() && !_isSelectionMode.value) {
            _isSelectionMode.value = true
        }
    }

    fun toggleSelectAll() {
        val allIds = courseInstances.value.map { it.course.id }.toSet()
        if (_selectedCourseIds.value.size == allIds.size && allIds.isNotEmpty()) {
            _selectedCourseIds.value = emptySet()
        } else {
            _selectedCourseIds.value = allIds
            _isSelectionMode.value = true
        }
    }

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    fun deleteSelectedCourses() {
        // 防重入：确认按钮可连点，而原实现在协程启动**前**读取集合并于 suspend 之后才清空，
        // 因此连点两次会读到同一份非空集合 → 对同一批 ID 重复执行删除事务。
        if (_isDeleting.value) return
        val idsToDelete = _selectedCourseIds.value.toList()
        if (idsToDelete.isEmpty()) return
        _isDeleting.value = true
        viewModelScope.launch {
            try {
                courseTableRepository.deleteCoursesByIds(idsToDelete)
                _selectedCourseIds.value = emptySet()
                _isSelectionMode.value = false
            } finally {
                _isDeleting.value = false
            }
        }
    }

    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    val selectedCourseIds: StateFlow<Set<String>> = _selectedCourseIds.asStateFlow()
}

/**
 * UI 状态包装类
 */
data class CourseInstanceUiState(
    val isSelectionMode: Boolean = false,
    val selectedCourseIds: Set<String> = emptySet(),
    val courseColorMaps: List<DualColor> = emptyList()
)