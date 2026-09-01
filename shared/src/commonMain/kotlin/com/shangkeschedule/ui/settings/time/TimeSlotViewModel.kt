package com.shangkeschedule.ui.settings.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.main.TimeSlotScheme
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import com.shangkeschedule.data.repository.TimeSlotRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/**
 * ViewModel，用于管理时间段设置界面的 UI 状态和业务逻辑。
 * 支持多套作息方案（如夏令时/冬令时）的切换、新建与删除。
 */
@KoinViewModel
class TimeSlotViewModel(
    private val timeSlotRepository: TimeSlotRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository
) : ViewModel() {

    // 获取应用设置的流，包括当前课表ID
    private val appSettingsFlow = appSettingsRepository.getAppSettings()

    // 拦截逻辑相关变量（备份点）
    private var initialTimeSlots: List<TimeSlot> = emptyList()
    private var initialClassDuration: Int = 45
    private var initialBreakDuration: Int = 10
    private var initialSchemeId: String = TimeSlot.DEFAULT_SCHEME_ID
    private var isDataInitialized = false

    /**
     * 将时间段列表、方案列表、默认时长组合成单一的 UI 状态流。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val timeSlotsUiState: StateFlow<TimeSlotUiState> =
        appSettingsFlow
            .flatMapLatest { appSettings ->
                val currentTableId = appSettings.currentCourseTableId
                val schemeIdsFlow = timeSlotRepository.getSchemeIdsByCourseTableId(currentTableId)
                val schemeMetasFlow = timeSlotRepository.getSchemeMetasByCourseTableId(currentTableId)

                appSettingsRepository.getCourseTableConfigFlow(currentTableId).flatMapLatest { config ->
                    val schemeId = config?.currentSchemeId ?: TimeSlot.DEFAULT_SCHEME_ID
                    val timeSlotsFlow = timeSlotRepository.getTimeSlotsByCourseTableId(currentTableId, schemeId)

                    combine(timeSlotsFlow, schemeIdsFlow, schemeMetasFlow) { timeSlots, schemeIds, schemeMetas ->
                        val classDuration = config?.defaultClassDuration ?: 45
                        val breakDuration = config?.defaultBreakDuration ?: 10

                        // 首次加载或切换方案后，重新建立备份点
                        if (!isDataInitialized || initialSchemeId != schemeId) {
                            initialTimeSlots = timeSlots.map { it.copy(courseTableId = "") }.sortedBy { it.startTime }
                            initialClassDuration = classDuration
                            initialBreakDuration = breakDuration
                            initialSchemeId = schemeId
                            isDataInitialized = true
                        }

                        TimeSlotUiState(
                            timeSlots = timeSlots,
                            defaultClassDuration = classDuration,
                            defaultBreakDuration = breakDuration,
                            currentSchemeId = schemeId,
                            schemeIds = schemeIds,
                            isDataLoaded = true,
                            schemeMetas = schemeMetas,
                            autoSwitchScheme = config?.autoSwitchScheme ?: false
                        )
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = TimeSlotUiState(
                    timeSlots = emptyList(),
                    defaultClassDuration = 45,
                    defaultBreakDuration = 10
                )
            )

    /**
     * 供 UI 调用：比对当前内存中的数据与进入页面时的初始数据是否有差异
     */
    fun hasUnsavedChanges(
        currentTimeSlots: List<TimeSlot>,
        currentClassDuration: Int,
        currentBreakDuration: Int
    ): Boolean {
        if (!isDataInitialized) return false

        if (currentClassDuration != initialClassDuration) return true
        if (currentBreakDuration != initialBreakDuration) return true

        if (currentTimeSlots.size != initialTimeSlots.size) return true

        val normalizedCurrent = currentTimeSlots.map { it.copy(courseTableId = "") }.sortedBy { it.startTime }

        return normalizedCurrent != initialTimeSlots
    }

    /**
     * 保存成功后更新备份点，这样点击返回就不会再触发拦截弹窗
     */
    private fun updateBackupPoint(timeSlots: List<TimeSlot>, classDuration: Int, breakDuration: Int) {
        initialTimeSlots = timeSlots.map { it.copy(courseTableId = "") }.sortedBy { it.startTime }
        initialClassDuration = classDuration
        initialBreakDuration = breakDuration
    }

    /**
     * UI 事件：一次性保存当前方案的所有设置
     */
    fun onSaveAllSettings(
        timeSlots: List<TimeSlot>,
        classDuration: Int,
        breakDuration: Int,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val currentTableId = appSettingsRepository.getAppSettings().first().currentCourseTableId
            val allTables = courseTableRepository.getAllCourseTables().first()
            val allTableIds = allTables.map { it.id }

            val tableExists = allTableIds.contains(currentTableId)

            if (tableExists) {
                val currentConfig = appSettingsRepository.getCourseConfigOnce(currentTableId)
                    ?: CourseTableConfig(courseTableId = currentTableId)
                val schemeId = currentConfig.currentSchemeId

                // 确保时间段关联正确的课表 ID 与方案 ID 后写入数据库
                val timeSlotsWithCorrectId = timeSlots.map {
                    it.copy(courseTableId = currentTableId, schemeId = schemeId)
                }

                // 原子保存当前方案时间段与课表默认时长配置
                val updatedConfig = currentConfig.copy(
                    defaultClassDuration = classDuration,
                    defaultBreakDuration = breakDuration
                )
                timeSlotRepository.saveSchemeSettings(
                    courseTableId = currentTableId,
                    timeSlots = timeSlotsWithCorrectId,
                    schemeId = schemeId,
                    config = updatedConfig
                )

                // 3. 同步备份状态
                updateBackupPoint(timeSlotsWithCorrectId, classDuration, breakDuration)

                onSuccess()
            }
        }
    }

    /**
     * 切换当前课表生效的作息方案。
     */
    fun onSwitchScheme(schemeId: String) {
        viewModelScope.launch {
            val currentTableId = appSettingsRepository.getAppSettings().first().currentCourseTableId
            val currentConfig = appSettingsRepository.getCourseConfigOnce(currentTableId)
                ?: CourseTableConfig(courseTableId = currentTableId)
            if (currentConfig.currentSchemeId != schemeId) {
                appSettingsRepository.insertOrUpdateCourseConfig(currentConfig.copy(currentSchemeId = schemeId))
            }
        }
    }

    /**
     * 新建作息方案：复制当前方案的时间段为新方案，并切换到新方案。
     * @param onError 失败时回调，返回错误码："empty" 名称为空 / "duplicate" 名称重复。
     */
    fun onCreateScheme(name: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val currentTableId = appSettingsRepository.getAppSettings().first().currentCourseTableId
            val schemeId = name.trim()
            if (schemeId.isEmpty()) {
                onError("empty")
                return@launch
            }
            val existingIds = timeSlotRepository.getSchemeIdsByCourseTableId(currentTableId).first()
            if (schemeId in existingIds) {
                onError("duplicate")
                return@launch
            }

            val currentConfig = appSettingsRepository.getCourseConfigOnce(currentTableId)
                ?: CourseTableConfig(courseTableId = currentTableId)
            val templateSchemeId = currentConfig.currentSchemeId

            // 原子创建：复制当前方案时间段并切换到新方案
            val templateSlots = timeSlotRepository.getTimeSlotsByCourseTableId(currentTableId, templateSchemeId).first()
            timeSlotRepository.createScheme(
                courseTableId = currentTableId,
                schemeId = schemeId,
                templateSlots = templateSlots,
                currentConfig = currentConfig
            )
            onSuccess()
        }
    }

    /**
     * 删除作息方案（默认方案不可删除）。
     */
    fun onDeleteScheme(schemeId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            if (schemeId == TimeSlot.DEFAULT_SCHEME_ID) return@launch

            val currentTableId = appSettingsRepository.getAppSettings().first().currentCourseTableId
            timeSlotRepository.deleteScheme(currentTableId, schemeId)
            onSuccess()
        }
    }

    /**
     * 切换“根据日期自动切换作息方案（夏令时/冬令时）”开关。
     */
    fun onToggleAutoSwitch(enabled: Boolean) {
        viewModelScope.launch {
            val currentTableId = appSettingsRepository.getAppSettings().first().currentCourseTableId
            val currentConfig = appSettingsRepository.getCourseConfigOnce(currentTableId)
                ?: CourseTableConfig(courseTableId = currentTableId)
            if (currentConfig.autoSwitchScheme != enabled) {
                appSettingsRepository.insertOrUpdateCourseConfig(currentConfig.copy(autoSwitchScheme = enabled))
            }
        }
    }

    /**
     * 保存某套作息方案的生效日期范围（月-日，支持跨年）。
     */
    fun onSaveSchemeDates(schemeId: String, startMonthDay: String?, endMonthDay: String?) {
        viewModelScope.launch {
            val currentTableId = appSettingsRepository.getAppSettings().first().currentCourseTableId
            timeSlotRepository.upsertSchemeMeta(
                TimeSlotScheme(
                    courseTableId = currentTableId,
                    schemeId = schemeId,
                    startMonthDay = startMonthDay,
                    endMonthDay = endMonthDay
                )
            )
        }
    }
}

data class TimeSlotUiState(
    val timeSlots: List<TimeSlot>,
    val defaultClassDuration: Int,
    val defaultBreakDuration: Int,
    val currentSchemeId: String = TimeSlot.DEFAULT_SCHEME_ID,
    val schemeIds: List<String> = emptyList(),
    val isDataLoaded: Boolean = false,
    val schemeMetas: List<TimeSlotScheme> = emptyList(),
    val autoSwitchScheme: Boolean = false
)
