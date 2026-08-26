package com.shangkeschedule.ui.settings.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.model.CourseImportExport.CourseTableImportModel
import com.shangkeschedule.data.parser.UniversalScheduleParser
import com.shangkeschedule.data.repository.CourseConversionRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel

/**
 * 文本/文件导入 ViewModel
 * 支持：粘贴文本、选择文件、预览、导入新课表
 * 保留 remark 字段以便自动提取学分/考核方式/实验课
 */
@KoinViewModel
class TextImportViewModel(
    private val courseConversionRepository: CourseConversionRepository,
    private val courseTableRepository: CourseTableRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TextImportUiState())
    val uiState: StateFlow<TextImportUiState> = _uiState.asStateFlow()

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text, parseResult = null, error = null, detectedFormat = "")
    }

    fun parseInput() {
        val text = _uiState.value.inputText
        if (text.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请输入或粘贴课表内容")
            return
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                UniversalScheduleParser.parseAuto(text)
            }
            when (result) {
                is UniversalScheduleParser.ParseResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        parseResult = result.model,
                        detectedFormat = result.format,
                        error = null
                    )
                }
                is UniversalScheduleParser.ParseResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message, parseResult = null, detectedFormat = "")
                }
            }
        }
    }

    fun importToNewTable(tableName: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val model = _uiState.value.parseResult
        if (model == null) {
            onError("请先解析并预览")
            return
        }
        if (tableName.isBlank()) {
            onError("请输入课表名称")
            return
        }

        viewModelScope.launch {
            try {
                val tableId = courseTableRepository.createNewCourseTable(tableName)
                courseConversionRepository.importCourseTableFromJson(tableId, model)
                _uiState.value = _uiState.value.copy(importSuccess = true)
                onSuccess(tableId)
            } catch (e: Exception) {
                onError("导入失败: ${e.message}")
            }
        }
    }

    fun reset() {
        _uiState.value = TextImportUiState()
    }
}

data class TextImportUiState(
    val inputText: String = "",
    val parseResult: CourseTableImportModel? = null,
    val detectedFormat: String = "",
    val error: String? = null,
    val importSuccess: Boolean = false
)
