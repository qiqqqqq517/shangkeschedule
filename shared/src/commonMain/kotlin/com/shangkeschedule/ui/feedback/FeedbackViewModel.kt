package com.shangkeschedule.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import com.shangkeschedule.data.repository.SchoolHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/**
 * 反馈页「附带排查信息」的数据（v4.66.0）。
 *
 * 全是**本机已有的结构性配置**（课表数量、当前作息方案 ID、最近的教务适配记录），
 * 没有账号、设备标识、IP、位置或使用行为 —— 与页面上的说明文案严格一致。
 */
data class FeedbackDiagnosticsInfo(
    val courseTableCount: Int = 0,
    val currentTableName: String = "",
    val currentSchemeId: String = "",
    val schoolNames: List<String> = emptyList()
)

/**
 * 反馈页 ViewModel（v4.66.0 新增）。
 *
 * 只在进入页面时**一次性**读取（`first()`），不做持续观察：
 * 排查信息是「快照」，用户在反馈页停留期间切后台改课表也不会让正文半路变样。
 * 读取失败（例如数据库尚未初始化）不抛给 UI，保持 [info] 的空值形态，
 * 屏幕上对应字段显示「未知」。
 */
@KoinViewModel
class FeedbackViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository,
    private val schoolHistoryRepository: SchoolHistoryRepository
) : ViewModel() {

    private val _info = MutableStateFlow(FeedbackDiagnosticsInfo())

    /** 排查信息快照；未就绪时为空值（UI 显示「未知」）。 */
    val info: StateFlow<FeedbackDiagnosticsInfo> = _info

    init {
        viewModelScope.launch {
            runCatching {
                val settings = appSettingsRepository.getAppSettingsOnce()
                val tableId = settings.currentCourseTableId
                val tables = courseTableRepository.getAllCourseTables().first()
                val config = if (tableId.isBlank()) {
                    null
                } else {
                    appSettingsRepository.getCourseTableConfigFlow(tableId).first()
                }
                val history = schoolHistoryRepository.historyFlow.first()
                FeedbackDiagnosticsInfo(
                    courseTableCount = tables.size,
                    currentTableName = tables.firstOrNull { it.id == tableId }?.name.orEmpty(),
                    currentSchemeId = config?.currentSchemeId.orEmpty(),
                    schoolNames = listOf(history.bachelor, history.postgraduate, history.general)
                        .filter { !it.isEmpty }
                        .map { it.name }
                )
            }.onSuccess { _info.value = it }
        }
    }
}
