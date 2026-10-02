package com.shangkeschedule.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.codec.CourseShareCodec
import com.shangkeschedule.data.repository.CourseConversionRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel

/** 可分享的课表（下拉选择用）。 */
data class ShareTableOption(val id: String, val name: String)

data class CourseShareUiState(
    val isLoading: Boolean = true,
    val tables: List<ShareTableOption> = emptyList(),
    val selectedTableId: String = "",
    val mode: CourseShareCodec.Mode = CourseShareCodec.Mode.FULL,
    val fullCode: String = "",
    val compactCode: String = "",
    val courseCount: Int = 0,
    /** 整张表没有课程，没什么可分享的。 */
    val isEmptyTable: Boolean = false,
    /** 连课表都没有（理论上进不来，兜底用）。 */
    val hasNoTable: Boolean = false,
    val encodeFailed: Boolean = false
) {
    val selectedTableName: String
        get() = tables.firstOrNull { it.id == selectedTableId }?.name.orEmpty()

    val code: String
        get() = if (mode == CourseShareCodec.Mode.COMPACT) compactCode else fullCode
}

/**
 * 「课表分享串 / 二维码」页。
 *
 * 两种编码同时算好放在状态里，切换时无需重算：完整串用于复制、发文件；
 * 精简串用于二维码容量不足时降级。导出与编码都在 [Dispatchers.Default] 上做，
 * 分享串本身只有几 KB，回到主线程直接渲染。
 */
@KoinViewModel
class CourseShareViewModel(
    private val courseConversionRepository: CourseConversionRepository,
    private val courseTableRepository: CourseTableRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseShareUiState())
    val uiState: StateFlow<CourseShareUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, encodeFailed = false) }
            val tables = runCatching { courseTableRepository.getAllCourseTables().first() }
                .getOrDefault(emptyList())
            if (tables.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        tables = emptyList(),
                        selectedTableId = "",
                        hasNoTable = true,
                        fullCode = "",
                        compactCode = ""
                    )
                }
                return@launch
            }
            val options = tables.map { ShareTableOption(it.id, it.name) }
            val currentId = runCatching { courseConversionRepository.getCurrentTableId() }.getOrNull()
            val selected = _uiState.value.selectedTableId.takeIf { id -> options.any { it.id == id } }
                ?: currentId?.takeIf { id -> options.any { it.id == id } }
                ?: options.first().id
            _uiState.update { it.copy(tables = options, selectedTableId = selected, hasNoTable = false) }
            generate(selected)
        }
    }

    fun selectTable(tableId: String) {
        if (tableId == _uiState.value.selectedTableId) return
        _uiState.update { it.copy(selectedTableId = tableId, isLoading = true, encodeFailed = false) }
        viewModelScope.launch { generate(tableId) }
    }

    fun setMode(mode: CourseShareCodec.Mode) {
        _uiState.update { it.copy(mode = mode) }
    }

    private suspend fun generate(tableId: String) {
        val export = runCatching { courseConversionRepository.exportCourseTableToJson(tableId) }.getOrNull()
        if (export == null || export.courses.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isEmptyTable = true,
                    encodeFailed = false,
                    fullCode = "",
                    compactCode = "",
                    courseCount = 0
                )
            }
            return
        }
        val tableName = _uiState.value.tables.firstOrNull { it.id == tableId }?.name
        val codes = withContext(Dispatchers.Default) {
            val full = runCatching {
                CourseShareCodec.encode(export, CourseShareCodec.Mode.FULL, tableName)
            }.getOrNull()
            val compact = runCatching {
                CourseShareCodec.encode(export, CourseShareCodec.Mode.COMPACT, tableName)
            }.getOrNull()
            full to compact
        }
        val full = codes.first
        if (full == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isEmptyTable = false,
                    encodeFailed = true,
                    fullCode = "",
                    compactCode = "",
                    courseCount = export.courses.size
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                isEmptyTable = false,
                encodeFailed = false,
                fullCode = full,
                compactCode = codes.second ?: full,
                courseCount = export.courses.size
            )
        }
    }
}
