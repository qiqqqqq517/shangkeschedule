package com.shangkeschedule.ui.settings.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.di.AppStorage
import com.shangkeschedule.data.model.CourseImportExport
import com.shangkeschedule.data.model.CourseImportExport.CourseTableImportModel
import com.shangkeschedule.data.parser.ExcelScheduleParser
import com.shangkeschedule.data.parser.TextImportFormat
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
 * 支持：粘贴文本、选择文件（Excel/JSON/CSV/ICS/HTML/TXT）、预览、导入新课表/已有课表
 * 保留 remark 字段以便自动提取学分/考核方式/实验课
 */
@KoinViewModel
class TextImportViewModel(
    private val courseConversionRepository: CourseConversionRepository,
    private val courseTableRepository: CourseTableRepository,
    private val appStorage: AppStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(TextImportUiState())
    val uiState: StateFlow<TextImportUiState> = _uiState.asStateFlow()

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text, parseResult = null, error = null, detectedFormat = "")
    }

    /** 清除已解析的文件（重新选择文件前的状态复位） */
    fun clearFile() {
        _uiState.value = _uiState.value.copy(fileName = null, parseResult = null, error = null, detectedFormat = "")
    }

    /**
     * 解析粘贴文本。
     * @param forcedFormat 二级分类页强制格式；null 走自动嗅探（含多格式回退链）
     */
    fun parseInput(forcedFormat: TextImportFormat? = null) {
        val text = _uiState.value.inputText
        if (text.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请输入或粘贴课表内容")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = withContext(Dispatchers.Default) {
                UniversalScheduleParser.parseWithFormat(text, forcedFormat)
            }
            applyParseResult(result)
        }
    }

    /**
     * 解析选择的文件。
     * - .xlsx（ZIP 魔数）→ ExcelScheduleParser 提取网格 → 网格/列表双策略识别
     * - 其他 → UTF-8 解码文本（含 BOM 处理），按 forcedFormat 或文件扩展名/自动嗅探解析
     */
    fun parseFileBytes(bytes: ByteArray, fileName: String?, forcedFormat: TextImportFormat? = null) {
        if (bytes.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "文件内容为空", parseResult = null)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, fileName = fileName, parseResult = null, error = null)

            val result = withContext(Dispatchers.IO) {
                try {
                    val lowerName = (fileName ?: "").lowercase()
                    val isOldXls = lowerName.endsWith(".xls") && !lowerName.endsWith(".xlsx")

                    when {
                        // 旧版二进制 .xls 明确提示转换
                        isOldXls && !ExcelScheduleParser.isZipBytes(bytes) ->
                            UniversalScheduleParser.ParseResult.Error("旧版 .xls 为二进制格式，请在 Office/WPS 中另存为 .xlsx 后再导入")

                        // xlsx（ZIP 容器）
                        ExcelScheduleParser.isZipBytes(bytes) -> {
                            val grid = ExcelScheduleParser.extractGrid(bytes, appStorage.cacheDir)
                            UniversalScheduleParser.parseExcelGrid(grid)
                        }

                        // 文本类文件
                        else -> {
                            val text = bytes.decodeToString().removePrefix("\uFEFF")
                            if (text.contains('\uFFFD')) {
                                UniversalScheduleParser.ParseResult.Error("文件不是 UTF-8 编码（可能为 GBK/ANSI），请另存为 UTF-8 或改用 Excel(.xlsx) 导入")
                            } else {
                                UniversalScheduleParser.parseWithFormat(text, forcedFormat ?: TextImportFormat.forFileName(fileName))
                            }
                        }
                    }
                } catch (e: Exception) {
                    UniversalScheduleParser.ParseResult.Error("文件解析失败：${e.message ?: e.javaClass.simpleName}")
                }
            }
            applyParseResult(result)
        }
    }

    /**
     * 导入 JSON 文件内容到指定已有课表（JSON 文件导入页用）。
     * 优先按本 App 导出格式严格解析（保留 id/配置），失败则回退 WakeUp JSON/通用解析。
     */
    fun importJsonFileIntoTable(
        bytes: ByteArray,
        tableId: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (bytes.isEmpty()) {
            onError("文件内容为空")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val imported = withContext(Dispatchers.IO) {
                    val text = bytes.decodeToString().removePrefix("\uFEFF")
                    if (text.contains('\uFFFD')) throw IllegalArgumentException("文件不是 UTF-8 编码")

                    // 1) 本 App 导出格式严格解析
                    try {
                        val model = CourseImportExport.json.decodeFromString<CourseTableImportModel>(text)
                        courseConversionRepository.importCourseTableFromJson(tableId, model)
                        return@withContext true
                    } catch (_: Exception) {
                    }

                    // 2) WakeUp / 通用 JSON 解析
                    when (val r = UniversalScheduleParser.parseWithFormat(text, TextImportFormat.JSON)) {
                        is UniversalScheduleParser.ParseResult.Success -> {
                            courseConversionRepository.importCourseTableFromJson(tableId, r.model)
                            true
                        }
                        is UniversalScheduleParser.ParseResult.Error -> false
                    }
                }
                if (imported) {
                    onSuccess(tableId)
                } else {
                    onError("JSON 解析失败：未找到有效课程")
                }
            } catch (e: Exception) {
                onError("导入失败: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /** 导入到新表 */
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
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val tableId = courseTableRepository.createNewCourseTable(tableName)
                courseConversionRepository.importCourseTableFromJson(tableId, model)
                _uiState.value = _uiState.value.copy(importSuccess = true)
                onSuccess(tableId)
            } catch (e: Exception) {
                onError("导入失败: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /** 导入到已有表（预览确认后） */
    fun importToExistingTable(tableId: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val model = _uiState.value.parseResult
        if (model == null) {
            onError("请先解析并预览")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                courseConversionRepository.importCourseTableFromJson(tableId, model)
                _uiState.value = _uiState.value.copy(importSuccess = true)
                onSuccess(tableId)
            } catch (e: Exception) {
                onError("导入失败: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun applyParseResult(result: UniversalScheduleParser.ParseResult) {
        _uiState.value = when (result) {
            is UniversalScheduleParser.ParseResult.Success -> _uiState.value.copy(
                parseResult = result.model,
                detectedFormat = result.format,
                error = null,
                isLoading = false
            )
            is UniversalScheduleParser.ParseResult.Error -> _uiState.value.copy(
                error = result.message,
                parseResult = null,
                detectedFormat = "",
                isLoading = false
            )
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
    val importSuccess: Boolean = false,
    val isLoading: Boolean = false,
    val fileName: String? = null
)
