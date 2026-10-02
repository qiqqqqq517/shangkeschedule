package com.shangkeschedule.ui.settings.import

import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.import_fmt_share_code
import shangkeschedule.shared.generated.resources.tivm_error_empty_file
import shangkeschedule.shared.generated.resources.tivm_error_empty_input
import shangkeschedule.shared.generated.resources.tivm_error_no_courses
import shangkeschedule.shared.generated.resources.tivm_error_not_utf8
import shangkeschedule.shared.generated.resources.tivm_error_not_utf8_guidance
import shangkeschedule.shared.generated.resources.tivm_error_old_xls
import shangkeschedule.shared.generated.resources.tivm_error_parse_first
import shangkeschedule.shared.generated.resources.tivm_error_share_code_invalid
import shangkeschedule.shared.generated.resources.tivm_error_table_name_empty
import shangkeschedule.shared.generated.resources.tivm_import_failed_fmt
import shangkeschedule.shared.generated.resources.tivm_json_parse_failed

import org.jetbrains.compose.resources.getString

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.di.AppStorage
import com.shangkeschedule.data.model.CourseImportExport
import com.shangkeschedule.data.model.CourseImportExport.CourseTableImportModel
import com.shangkeschedule.data.codec.CourseShareCodec
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
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(error = getString(Res.string.tivm_error_empty_input))
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = withContext(Dispatchers.Default) {
                // 「课表分享串」优先：`SK1:` 是自包含编码，交给通用格式嗅探只会被当成乱文本。
                if (CourseShareCodec.looksLikeCode(text)) {
                    when (val decoded = CourseShareCodec.decode(text)) {
                        is CourseShareCodec.DecodeResult.Success -> UniversalScheduleParser.ParseResult.Success(
                            model = decoded.model,
                            format = getString(Res.string.import_fmt_share_code)
                        )
                        CourseShareCodec.DecodeResult.NotAShareCode ->
                            UniversalScheduleParser.parseWithFormat(text, forcedFormat)
                        CourseShareCodec.DecodeResult.Invalid ->
                            UniversalScheduleParser.ParseResult.Error(
                                getString(Res.string.tivm_error_share_code_invalid)
                            )
                    }
                } else {
                    UniversalScheduleParser.parseWithFormat(text, forcedFormat)
                }
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
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(error = getString(Res.string.tivm_error_empty_file), parseResult = null)
            }
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
                            UniversalScheduleParser.ParseResult.Error(getString(Res.string.tivm_error_old_xls))

                        // xlsx（ZIP 容器）
                        ExcelScheduleParser.isZipBytes(bytes) -> {
                            val grid = ExcelScheduleParser.extractGrid(bytes, appStorage.cacheDir)
                            UniversalScheduleParser.parseExcelGrid(grid)
                        }

                        // 文本类文件
                        else -> {
                            val text = bytes.decodeToString().removePrefix("\uFEFF")
                            if (text.contains('\uFFFD')) {
                                UniversalScheduleParser.ParseResult.Error(getString(Res.string.tivm_error_not_utf8_guidance))
                            } else {
                                UniversalScheduleParser.parseWithFormat(text, forcedFormat ?: TextImportFormat.forFileName(fileName))
                            }
                        }
                    }
                } catch (e: Exception) {
                    UniversalScheduleParser.ParseResult.Error(getString(Res.string.tivm_import_failed_fmt, e.message ?: e.javaClass.simpleName))
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
        viewModelScope.launch {
            if (bytes.isEmpty()) {
                onError(getString(Res.string.tivm_error_empty_file))
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 空结果保护（v4.64.26）：importCourseTableFromJson 是「先清空目标表再插入」，
                // 0 门课会把已有课表静默删光并返回成功。此前预览页可逐条删空（P1-6）后仍能走到这里，
                // 因此把「解析成功但没有任何课程」当失败上报。守卫放在 VM 层而非 repository：
                // BackupRepository 的回滚快照分支允许合法空表（见 importCourseTableFromJson 调用点）。
                val failure: String? = withContext(Dispatchers.IO) {
                    val text = bytes.decodeToString().removePrefix("\uFEFF")
                    if (text.contains('\uFFFD')) throw IllegalArgumentException(getString(Res.string.tivm_error_not_utf8))

                    // 1) 本 App 导出格式严格解析
                    try {
                        val model = CourseImportExport.json.decodeFromString<CourseTableImportModel>(text)
                        if (model.courses.isEmpty()) {
                            throw IllegalArgumentException(getString(Res.string.tivm_error_no_courses))
                        }
                        courseConversionRepository.importCourseTableFromJson(tableId, model)
                        return@withContext null
                    } catch (_: Exception) {
                    }

                    // 2) WakeUp / 通用 JSON 解析
                    when (val r = UniversalScheduleParser.parseWithFormat(text, TextImportFormat.JSON)) {
                        is UniversalScheduleParser.ParseResult.Success -> {
                            if (r.model.courses.isEmpty()) {
                                getString(Res.string.tivm_error_no_courses)
                            } else {
                                courseConversionRepository.importCourseTableFromJson(tableId, r.model)
                                null
                            }
                        }
                        is UniversalScheduleParser.ParseResult.Error -> getString(Res.string.tivm_json_parse_failed)
                    }
                }
                if (failure == null) {
                    onSuccess(tableId)
                } else {
                    onError(failure)
                }
            } catch (e: Exception) {
                onError(getString(Res.string.tivm_import_failed_fmt, e.message ?: ""))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * P1-6 预览可编辑：预览页删除/修改误识别条目后，把编辑后的完整课程列表
     * 回写到解析结果。后续「导入新课表 / 覆盖已有课表」都使用编辑后的数据。
     */
    fun updateParsedCourses(courses: List<CourseImportExport.ImportCourseJsonModel>) {
        val current = _uiState.value.parseResult ?: return
        _uiState.value = _uiState.value.copy(parseResult = current.copy(courses = courses))
    }

    /** 导入到新表 */
    fun importToNewTable(tableName: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val model = _uiState.value.parseResult
            if (model == null) {
                onError(getString(Res.string.tivm_error_parse_first))
                return@launch
            }
            // 空结果保护：预览页可逐条删除条目（P1-6），删空后不许再落库
            if (model.courses.isEmpty()) {
                onError(getString(Res.string.tivm_error_no_courses))
                return@launch
            }
            if (tableName.isBlank()) {
                onError(getString(Res.string.tivm_error_table_name_empty))
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val tableId = courseTableRepository.createNewCourseTable(tableName)
                courseConversionRepository.importCourseTableFromJson(tableId, model)
                _uiState.value = _uiState.value.copy(importSuccess = true)
                onSuccess(tableId)
            } catch (e: Exception) {
                onError(getString(Res.string.tivm_import_failed_fmt, e.message ?: ""))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /** 导入到已有表（预览确认后） */
    fun importToExistingTable(tableId: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val model = _uiState.value.parseResult
            if (model == null) {
                onError(getString(Res.string.tivm_error_parse_first))
                return@launch
            }
            // 空结果保护：预览页可逐条删除条目（P1-6），删空后不许再落库（会清空目标表）
            if (model.courses.isEmpty()) {
                onError(getString(Res.string.tivm_error_no_courses))
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                courseConversionRepository.importCourseTableFromJson(tableId, model)
                _uiState.value = _uiState.value.copy(importSuccess = true)
                onSuccess(tableId)
            } catch (e: Exception) {
                onError(getString(Res.string.tivm_import_failed_fmt, e.message ?: ""))
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
