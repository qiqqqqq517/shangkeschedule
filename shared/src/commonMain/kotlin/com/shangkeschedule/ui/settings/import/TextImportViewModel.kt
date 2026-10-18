package com.shangkeschedule.ui.settings.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.codec.CourseShareCodec
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
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.KoinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.import_fmt_share_code
import shangkeschedule.shared.generated.resources.import_fmt_strict_json
import shangkeschedule.shared.generated.resources.parser_err_csv_empty
import shangkeschedule.shared.generated.resources.parser_err_csv_failed
import shangkeschedule.shared.generated.resources.parser_err_csv_no_courses
import shangkeschedule.shared.generated.resources.parser_err_empty_content
import shangkeschedule.shared.generated.resources.parser_err_excel_empty
import shangkeschedule.shared.generated.resources.parser_err_excel_no_courses
import shangkeschedule.shared.generated.resources.parser_err_grid_no_courses
import shangkeschedule.shared.generated.resources.parser_err_grid_not_timetable
import shangkeschedule.shared.generated.resources.parser_err_html_failed
import shangkeschedule.shared.generated.resources.parser_err_html_no_courses
import shangkeschedule.shared.generated.resources.parser_err_html_no_rows
import shangkeschedule.shared.generated.resources.parser_err_ics_failed
import shangkeschedule.shared.generated.resources.parser_err_ics_no_events
import shangkeschedule.shared.generated.resources.parser_err_json_failed
import shangkeschedule.shared.generated.resources.parser_err_json_no_courses
import shangkeschedule.shared.generated.resources.parser_err_text_empty
import shangkeschedule.shared.generated.resources.parser_err_text_failed
import shangkeschedule.shared.generated.resources.parser_err_text_format_unknown
import shangkeschedule.shared.generated.resources.parser_err_unrecognized_format
import shangkeschedule.shared.generated.resources.parser_err_wakeup_no_json
import shangkeschedule.shared.generated.resources.parser_err_wakeup_text_failed
import shangkeschedule.shared.generated.resources.parser_fmt_csv
import shangkeschedule.shared.generated.resources.parser_fmt_excel_grid
import shangkeschedule.shared.generated.resources.parser_fmt_excel_list
import shangkeschedule.shared.generated.resources.parser_fmt_excel_position_map
import shangkeschedule.shared.generated.resources.parser_fmt_html_table
import shangkeschedule.shared.generated.resources.parser_fmt_ics_calendar
import shangkeschedule.shared.generated.resources.parser_fmt_plain_text
import shangkeschedule.shared.generated.resources.tivm_error_empty_file
import shangkeschedule.shared.generated.resources.tivm_error_empty_input
import shangkeschedule.shared.generated.resources.tivm_error_no_courses
import shangkeschedule.shared.generated.resources.tivm_error_not_utf8_guidance
import shangkeschedule.shared.generated.resources.tivm_error_old_xls
import shangkeschedule.shared.generated.resources.tivm_error_parse_first
import shangkeschedule.shared.generated.resources.tivm_error_share_code_invalid
import shangkeschedule.shared.generated.resources.tivm_error_table_name_empty
import shangkeschedule.shared.generated.resources.tivm_import_failed_fmt

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
     *
     * 改造前按二级分类页传 `forcedFormat` 强制某一种解析器；导入体系整合后不再让用户
     * 先选格式 —— 格式由 [parseTextContent] 嗅探，只有内部调用方（如 AI 提示词约定
     * 输出纯文本）才显式指定。
     */
    fun parseInput() {
        val text = _uiState.value.inputText
        if (text.isBlank()) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(error = getString(Res.string.tivm_error_empty_input))
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = withContext(Dispatchers.Default) { parseTextContent(text, forcedFormat = null) }
            applyParseResult(result)
        }
    }

    /**
     * 文本内容的统一解析入口（粘贴 / 文件文本 / AI 输出共用）。
     *
     * 顺序即优先级，与旧「自动嗅探」链一致，只是把两条**高保真**分支提到前面：
     * 1. `SK1:` 课表分享串 —— 自包含编码，交给通用嗅探只会被当成乱文本；
     * 2. 本 App 导出的 JSON —— 严格解码可保留时间段、学期配置与作息方案元信息，
     *    通用 `parseWakeUpJson` 只取 `courses` 数组，会把作息一起丢掉；
     * 3. 其余交给 `UniversalScheduleParser` 的自动嗅探与失败回退链。
     */
    private suspend fun parseTextContent(text: String, forcedFormat: TextImportFormat?): UniversalScheduleParser.ParseResult {
        // 1) 课表分享串
        if (CourseShareCodec.looksLikeCode(text)) {
            return when (val decoded = CourseShareCodec.decode(text)) {
                is CourseShareCodec.DecodeResult.Success -> UniversalScheduleParser.ParseResult.Success(
                    model = decoded.model,
                    format = getString(Res.string.import_fmt_share_code)
                )
                CourseShareCodec.DecodeResult.NotAShareCode -> continueAfterShareCode(text, forcedFormat)
                CourseShareCodec.DecodeResult.Invalid -> UniversalScheduleParser.ParseResult.Error(
                    getString(Res.string.tivm_error_share_code_invalid)
                )
            }
        }
        return continueAfterShareCode(text, forcedFormat)
    }

    private suspend fun continueAfterShareCode(
        text: String,
        forcedFormat: TextImportFormat?
    ): UniversalScheduleParser.ParseResult = strictJsonOrNull(text)
        ?: UniversalScheduleParser.parseWithFormat(text, forcedFormat)

    /**
     * 按本 App 导出格式严格解码（保留时间段 / 学期配置 / 作息方案）。
     *
     * 返回 null 表示「不是本 App 的完整导出 JSON」，交给通用解析链处理 ——
     * 不能返回 Error，否则会把合法的 WakeUp JSON 挡在门外。
     */
    private suspend fun strictJsonOrNull(text: String): UniversalScheduleParser.ParseResult? {
        val trimmed = text.trim().removePrefix("\uFEFF").trimStart()
        if (!trimmed.startsWith("{")) return null
        return try {
            val model = CourseImportExport.json.decodeFromString<CourseTableImportModel>(trimmed)
            if (model.courses.isEmpty()) null
            else UniversalScheduleParser.ParseResult.Success(
                model = model,
                format = getString(Res.string.import_fmt_strict_json)
            )
        } catch (_: Exception) {
            // 缺字段 / 不是该结构 → 交回通用解析
            null
        }
    }

    /**
     * 解析选择的文件。
     * - .xlsx（ZIP 魔数）→ ExcelScheduleParser 提取网格 → 网格/列表双策略识别
     * - 其他 → UTF-8 解码文本（含 BOM 处理），走 [parseTextContent] 统一解析
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
                                parseTextContent(text, forcedFormat ?: TextImportFormat.forFileName(fileName))
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
     * P1-6 预览可编辑：预览页删除/修改误识别条目后，把编辑后的完整课程列表
     * 回写到解析结果。后续「导入新课表 / 覆盖已有课表」都使用编辑后的数据。
     */
    fun updateParsedCourses(courses: List<CourseImportExport.ImportCourseJsonModel>) {
        val current = _uiState.value.parseResult ?: return
        _uiState.value = _uiState.value.copy(parseResult = current.copy(courses = courses))
    }

    /**
     * 导入到新表。
     *
     * [tableName] 留空时用 [fallbackTableName] 兜底 —— 粘贴来源没有文件名，
     * 输入框默认是空的，此前用户点了「导入为新课表」只会收到一句「请输入课表名称」，
     * 看起来与「按钮失灵」无异。默认名由 UI 传入（当前学期 / 文件名去扩展名）。
     */
    fun importToNewTable(
        tableName: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        fallbackTableName: String = ""
    ) {
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
            val resolvedName = tableName.trim().ifBlank { fallbackTableName.trim() }
            if (resolvedName.isBlank()) {
                onError(getString(Res.string.tivm_error_table_name_empty))
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val tableId = courseTableRepository.createNewCourseTable(resolvedName)
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

    /**
     * 把解析结果写入 UI 状态。
     *
     * 无编号-4 / P0-3：解析层原先直接给出中文文案（`Error.message` / `Success.format`），
     * 这里原样赋值给 `uiState.error` / `uiState.detectedFormat` 渲染 ⇒
     * **英文 / 繁体用户看到简体中文**。
     * 现改为：解析层只给**码**，本函数（suspend，调用点都在 `viewModelScope.launch` 内）
     * 用 `getString` 解析成当前语言文案；码为 null 时（如分享串路径已自行本地化）
     * 才回落到文案本身。
     */
    private suspend fun applyParseResult(result: UniversalScheduleParser.ParseResult) {
        _uiState.value = when (result) {
            is UniversalScheduleParser.ParseResult.Success -> _uiState.value.copy(
                parseResult = result.model,
                detectedFormat = result.formatCode?.let { getString(formatResOf(it)) } ?: result.format,
                error = null,
                isLoading = false
            )
            is UniversalScheduleParser.ParseResult.Error -> _uiState.value.copy(
                error = result.code?.let { getString(errorResOf(it)) } ?: result.message,
                parseResult = null,
                detectedFormat = "",
                isLoading = false
            )
        }
    }

    private fun errorResOf(code: UniversalScheduleParser.ParseErrorCode) = when (code) {
        UniversalScheduleParser.ParseErrorCode.EMPTY_CONTENT -> Res.string.parser_err_empty_content
        UniversalScheduleParser.ParseErrorCode.UNRECOGNIZED_FORMAT -> Res.string.parser_err_unrecognized_format
        UniversalScheduleParser.ParseErrorCode.EXCEL_EMPTY -> Res.string.parser_err_excel_empty
        UniversalScheduleParser.ParseErrorCode.WAKEUP_NO_JSON -> Res.string.parser_err_wakeup_no_json
        UniversalScheduleParser.ParseErrorCode.WAKEUP_TEXT_FAILED -> Res.string.parser_err_wakeup_text_failed
        UniversalScheduleParser.ParseErrorCode.JSON_NO_COURSES -> Res.string.parser_err_json_no_courses
        UniversalScheduleParser.ParseErrorCode.JSON_FAILED -> Res.string.parser_err_json_failed
        UniversalScheduleParser.ParseErrorCode.ICS_NO_EVENTS -> Res.string.parser_err_ics_no_events
        UniversalScheduleParser.ParseErrorCode.ICS_FAILED -> Res.string.parser_err_ics_failed
        UniversalScheduleParser.ParseErrorCode.CSV_EMPTY -> Res.string.parser_err_csv_empty
        UniversalScheduleParser.ParseErrorCode.CSV_NO_COURSES -> Res.string.parser_err_csv_no_courses
        UniversalScheduleParser.ParseErrorCode.CSV_FAILED -> Res.string.parser_err_csv_failed
        UniversalScheduleParser.ParseErrorCode.HTML_NO_ROWS -> Res.string.parser_err_html_no_rows
        UniversalScheduleParser.ParseErrorCode.HTML_NO_COURSES -> Res.string.parser_err_html_no_courses
        UniversalScheduleParser.ParseErrorCode.HTML_FAILED -> Res.string.parser_err_html_failed
        UniversalScheduleParser.ParseErrorCode.TEXT_EMPTY -> Res.string.parser_err_text_empty
        UniversalScheduleParser.ParseErrorCode.TEXT_FORMAT_UNKNOWN -> Res.string.parser_err_text_format_unknown
        UniversalScheduleParser.ParseErrorCode.TEXT_FAILED -> Res.string.parser_err_text_failed
        UniversalScheduleParser.ParseErrorCode.GRID_NOT_TIMETABLE -> Res.string.parser_err_grid_not_timetable
        UniversalScheduleParser.ParseErrorCode.GRID_NO_COURSES -> Res.string.parser_err_grid_no_courses
        UniversalScheduleParser.ParseErrorCode.EXCEL_NO_COURSES -> Res.string.parser_err_excel_no_courses
    }

    private fun formatResOf(code: UniversalScheduleParser.ParseFormatCode) = when (code) {
        UniversalScheduleParser.ParseFormatCode.ICS_CALENDAR -> Res.string.parser_fmt_ics_calendar
        UniversalScheduleParser.ParseFormatCode.CSV -> Res.string.parser_fmt_csv
        UniversalScheduleParser.ParseFormatCode.HTML_TABLE -> Res.string.parser_fmt_html_table
        UniversalScheduleParser.ParseFormatCode.PLAIN_TEXT -> Res.string.parser_fmt_plain_text
        UniversalScheduleParser.ParseFormatCode.EXCEL_GRID -> Res.string.parser_fmt_excel_grid
        UniversalScheduleParser.ParseFormatCode.EXCEL_LIST -> Res.string.parser_fmt_excel_list
        UniversalScheduleParser.ParseFormatCode.EXCEL_POSITION_MAP -> Res.string.parser_fmt_excel_position_map
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
