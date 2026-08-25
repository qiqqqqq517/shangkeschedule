package com.shangkeschedule.ui.settings.conversion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.model.CourseImportExport
import com.shangkeschedule.data.repository.CourseConversionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okio.BufferedSource
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.KoinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.*

/**
 * 课表导入/导出界面的 ViewModel。
 * 负责处理跨平台课表数据的导入、导出、日历同步等业务逻辑，并通过状态与一次性事件与 UI 交互。
 */
@KoinViewModel
class CourseTableConversionViewModel(
    private val courseConversionRepository: CourseConversionRepository
) : ViewModel() {

    // UI 状态流：维护界面加载状态及各类对话框的显隐控制
    private val _uiState = MutableStateFlow(ConversionUiState())
    val uiState = _uiState.asStateFlow()

    // UI 事件通道：用于向前端发送一次性副作用事件（如拉起文件选择器、弹出提示消息等）
    private val _events = Channel<ConversionEvent>()
    val events = _events.receiveAsFlow()

    /**
     * 点击导入按钮：显示导入课表选择对话框
     */
    fun onImportClick() {
        _uiState.value = _uiState.value.copy(showImportTableDialog = true)
    }

    /**
     * 点击导出 JSON 按钮：显示导出选择对话框并指定类型为 JSON
     */
    fun onExportClick() {
        _uiState.value = _uiState.value.copy(
            showExportTableDialog = true,
            exportType = ExportType.JSON
        )
    }

    /**
     * 点击导出 ICS 按钮：显示导出选择对话框并指定类型为 ICS
     */
    fun onExportIcsClick() {
        _uiState.value = _uiState.value.copy(
            showExportTableDialog = true,
            exportType = ExportType.ICS
        )
    }

    /**
     * 关闭所有弹窗对话框
     */
    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(
            showImportTableDialog = false,
            showExportTableDialog = false,
            showCrushImportDialog = false
        )
    }

    /**
     * 当用户在弹窗中选择具体某张课表进行导入时触发
     */
    fun onImportTableSelected(tableId: String) {
        viewModelScope.launch {
            _events.send(ConversionEvent.LaunchImportFilePicker(tableId))
            dismissDialog()
        }
    }

    /**
     * 当用户在弹窗中确认导出课表时触发（根据当前 exportType 区分 JSON 或 ICS）
     */
    fun onExportTableSelected(tableId: String, alarmMinutes: Int?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                if (_uiState.value.exportType == ExportType.JSON) {
                    val jsonModel = courseConversionRepository.exportCourseTableToJson(tableId)
                    if (jsonModel != null) {
                        val jsonString = CourseImportExport.json.encodeToString(
                            CourseImportExport.CourseTableExportModel.serializer(),
                            jsonModel
                        )
                        _events.send(ConversionEvent.LaunchExportFileCreator(jsonString))
                    } else {
                        val message = getString(Res.string.error_export_table_not_found)
                        _events.send(ConversionEvent.ShowMessage(message))
                    }
                } else if (_uiState.value.exportType == ExportType.ICS) {
                    val icsContent = courseConversionRepository.exportToIcsString(tableId, alarmMinutes)
                    if (icsContent != null) {
                        _events.send(ConversionEvent.LaunchExportIcsFileCreator(icsContent))
                    } else {
                        val message = getString(Res.string.error_ics_export_data_failed)
                        _events.send(ConversionEvent.ShowMessage(message))
                    }
                }
            } catch (e: Exception) {
                val errorMessage = e.message ?: ""
                val message = getString(Res.string.error_export_failed, errorMessage)
                _events.send(ConversionEvent.ShowMessage(message))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
                dismissDialog()
            }
        }
    }

    /**
     * 处理文件导入逻辑：通过 Okio 的 BufferedSource 读取文件文本并解析入库
     */
    fun handleFileImport(tableId: String, source: BufferedSource) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val jsonString = source.readUtf8()
                val importModel = CourseImportExport.json.decodeFromString<CourseImportExport.CourseTableImportModel>(jsonString)
                courseConversionRepository.importCourseTableFromJson(tableId, importModel)

                val message = getString(Res.string.toast_import_success)
                _events.send(ConversionEvent.ShowMessage(message))
            } catch (_: Exception) {
                val message = getString(Res.string.error_import_failed)
                _events.send(ConversionEvent.ShowMessage(message))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 点击「一键导入 crush 课表」按钮：显示导入方式选择对话框
     */
    fun onImportCrushClick() {
        _uiState.value = _uiState.value.copy(showCrushImportDialog = true)
    }

    /**
     * 关闭 crush 导入对话框
     */
    fun dismissCrushImportDialog() {
        _uiState.value = _uiState.value.copy(showCrushImportDialog = false)
    }

    /**
     * 选择通过教务系统导入 crush 课表：跳转到学校选择页
     */
    fun onCrushImportViaSchool() {
        viewModelScope.launch {
            _events.send(ConversionEvent.NavigateToCrushSchoolImport)
            dismissCrushImportDialog()
        }
    }

    /**
     * 选择通过 JSON 文件导入 crush 课表：拉起文件选择器
     */
    fun onCrushImportViaJson() {
        viewModelScope.launch {
            _events.send(ConversionEvent.LaunchCrushImportFilePicker)
            dismissCrushImportDialog()
        }
    }

    /**
     * 处理 crush 课表 JSON 文件导入逻辑。
     * 注意：crush 课表挂在当前选中的课表 ID 下，通过 isCrush 标记隔离。
     */
    fun handleCrushFileImport(source: BufferedSource) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val tableId = courseConversionRepository.getCurrentTableId()
                val jsonString = source.readUtf8()
                val importModel = CourseImportExport.json.decodeFromString<CourseImportExport.CourseTableImportModel>(jsonString)
                courseConversionRepository.importCrushCourseTableFromJson(tableId, importModel)

                val message = getString(Res.string.toast_import_success)
                _events.send(ConversionEvent.ShowMessage(message))
            } catch (_: Exception) {
                val message = getString(Res.string.error_import_failed)
                _events.send(ConversionEvent.ShowMessage(message))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 删除 crush 课表：清空当前课表下的所有 crush 课程
     */
    fun onDeleteCrushClick() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                courseConversionRepository.deleteCrushCourses(currentTableId())
                _events.send(ConversionEvent.ShowMessage("crush 课表已删除"))
            } catch (e: Exception) {
                _events.send(ConversionEvent.ShowMessage("删除 crush 课表失败: ${e.message}"))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 获取当前选中的课表 ID。
     */
    private suspend fun currentTableId(): String {
        val settings = courseConversionRepository.getCurrentTableId()
        return settings
    }

    /**
     * 点击同步到系统日历按钮触发的逻辑
     */
    fun onSyncToCalendarClick() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val success = courseConversionRepository.syncCurrentTableToSystemCalendar()
                val message = if (success) {
                    getString(Res.string.toast_sync_calendar_success)
                } else {
                    getString(Res.string.error_sync_calendar_failed)
                }
                _events.send(ConversionEvent.ShowMessage(message))
            } catch (_: Exception) {
                val message = getString(Res.string.error_sync_calendar_failed)
                _events.send(ConversionEvent.ShowMessage(message))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}

/**
 * 课表转换界面的 UI 状态数据类
 */
data class ConversionUiState(
    val isLoading: Boolean = false,
    val showImportTableDialog: Boolean = false,
    val showExportTableDialog: Boolean = false,
    val showCrushImportDialog: Boolean = false,
    val exportType: ExportType = ExportType.NONE
)

/**
 * 导出类型枚举
 */
enum class ExportType {
    NONE,
    JSON,
    ICS
}

/**
 * 界面一次性副作用事件密封类
 */
sealed class ConversionEvent {
    data class LaunchImportFilePicker(val tableId: String) : ConversionEvent()
    data class LaunchExportFileCreator(val jsonContent: String) : ConversionEvent()
    data class LaunchExportIcsFileCreator(val icsContent: String) : ConversionEvent()
    data class ShowMessage(val message: String) : ConversionEvent()
    data object NavigateToCrushSchoolImport : ConversionEvent()
    data object LaunchCrushImportFilePicker : ConversionEvent()
}