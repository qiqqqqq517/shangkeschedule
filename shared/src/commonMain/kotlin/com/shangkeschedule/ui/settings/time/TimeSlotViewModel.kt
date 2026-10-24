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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.getString
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.timeslot_error_in_use
import shangkeschedule.shared.generated.resources.toast_save_failed
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
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

    /** 显式目标课表（情侣课表设置页跳转时传入）；null 时用当前选中课表。 */
    private val targetTableId = MutableStateFlow<String?>(null)

    fun initWithTargetTable(tableId: String?) {
        targetTableId.value = tableId
    }

    /** 有效目标课表：显式 target > 当前选中表（读写路径必须统一走本解析，防跨表误写）。 */
    private suspend fun resolveEffectiveTableId(): String {
        return targetTableId.value
            ?: appSettingsRepository.getAppSettingsOnce().currentCourseTableId
    }

    // 拦截逻辑相关变量（备份点）
    private var initialTimeSlots: List<TimeSlot> = emptyList()
    private var initialClassDuration: Int = 45
    private var initialBreakDuration: Int = 10
    private var initialSchemeId: String = TimeSlot.DEFAULT_SCHEME_ID
    private var initialTableId: String? = null
    private var isDataInitialized = false
    private val saving = MutableStateFlow(false)
    val isSaving = saving.asStateFlow()

    /** 所属课表/方案由编辑页面确定，未保存判断只比较用户实际编辑的字段。 */
    private fun normalizeSlots(slots: List<TimeSlot>) = slots
        .map { it.copy(courseTableId = "", schemeId = "") }.sortedBy { it.number }

    /**
     * 将时间段列表、方案列表、默认时长组合成单一的 UI 状态流。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val timeSlotsUiState: StateFlow<TimeSlotUiState> =
        combine(appSettingsFlow, targetTableId) { appSettings, target ->
            target ?: appSettings.currentCourseTableId
        }
            .flatMapLatest { currentTableId ->
                val schemeIdsFlow = timeSlotRepository.getSchemeIdsByCourseTableId(currentTableId)
                val schemeMetasFlow = timeSlotRepository.getSchemeMetasByCourseTableId(currentTableId)

                appSettingsRepository.getCourseTableConfigFlow(currentTableId).flatMapLatest { config ->
                    val schemeId = config?.currentSchemeId ?: TimeSlot.DEFAULT_SCHEME_ID
                    val timeSlotsFlow = timeSlotRepository.getTimeSlotsByCourseTableId(currentTableId, schemeId)

                    combine(timeSlotsFlow, schemeIdsFlow, schemeMetasFlow) { timeSlots, schemeIds, schemeMetas ->
                        val classDuration = config?.defaultClassDuration ?: 45
                        val breakDuration = config?.defaultBreakDuration ?: 10

                        // 首次加载或切换方案后，重新建立备份点
                        if (!isDataInitialized || initialTableId != currentTableId || initialSchemeId != schemeId) {
                            initialTimeSlots = normalizeSlots(timeSlots)
                            initialClassDuration = classDuration
                            initialBreakDuration = breakDuration
                            initialSchemeId = schemeId
                            initialTableId = currentTableId
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
                            autoSwitchScheme = config?.autoSwitchScheme ?: false,
                            // 自动切换开启时，当日实际生效方案可能与正在编辑的手动方案不同
                            effectiveSchemeId = timeSlotRepository.resolveActiveSchemeId(
                                config, schemeMetas, kotlin.time.Clock.System.now()
                                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
                            )
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

        val normalizedCurrent = normalizeSlots(currentTimeSlots)

        return normalizedCurrent != initialTimeSlots
    }

    /**
     * 保存成功后更新备份点，这样点击返回就不会再触发拦截弹窗
     */
    private fun updateBackupPoint(timeSlots: List<TimeSlot>, classDuration: Int, breakDuration: Int) {
        initialTimeSlots = normalizeSlots(timeSlots)
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
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
        onSaved: (List<TimeSlot>) -> Unit = {},
    ) {
        if (!saving.compareAndSet(false, true)) return
        val requestedSchemeId = timeSlotsUiState.value.currentSchemeId
        viewModelScope.launch {
            var savedSlots: List<TimeSlot>? = null
            try {
                val currentTableId = resolveEffectiveTableId()
                check(courseTableRepository.getAllCourseTables().first().any { it.id == currentTableId })
                val currentConfig = appSettingsRepository.getCourseConfigOnce(currentTableId)
                    ?: CourseTableConfig(courseTableId = currentTableId)
                check(currentConfig.currentSchemeId == requestedSchemeId)
                val normalized = timeSlots.map {
                    it.copy(courseTableId = currentTableId, schemeId = requestedSchemeId)
                }
                timeSlotRepository.saveSchemeSettings(
                    courseTableId = currentTableId,
                    timeSlots = normalized,
                    schemeId = requestedSchemeId,
                    config = currentConfig.copy(
                        defaultClassDuration = classDuration,
                        defaultBreakDuration = breakDuration,
                    ),
                )
                updateBackupPoint(normalized, classDuration, breakDuration)
                savedSlots = normalized
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(if (e.message == "TIMESLOT_IN_USE") getString(Res.string.timeslot_error_in_use)
                    else getString(Res.string.toast_save_failed))
            } finally {
                saving.value = false
            }
            savedSlots?.let { onSaved(it); onSuccess() }
        }
    }

    /** 与保存共用在途保护，避免方案切换或配置写入与草稿保存交叉覆盖。 */
    private fun changeSettings(onError: (String) -> Unit = {}, change: suspend () -> Unit) {
        if (!saving.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                change()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onError(getString(Res.string.toast_save_failed))
            } finally {
                saving.value = false
            }
        }
    }

    /**
     * 切换当前课表生效的作息方案。
     */
    fun onSwitchScheme(schemeId: String, onError: (String) -> Unit = {}) {
        changeSettings(onError) {
            val currentTableId = resolveEffectiveTableId()
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
        changeSettings(onError = { onError("failed") }) {
            val currentTableId = resolveEffectiveTableId()
            val schemeId = name.trim()
            if (schemeId.isEmpty()) {
                onError("empty")
                return@changeSettings
            }
            val existingIds = timeSlotRepository.getSchemeIdsByCourseTableId(currentTableId).first()
            if (schemeId in existingIds) {
                onError("duplicate")
                return@changeSettings
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
    fun onDeleteScheme(schemeId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (schemeId == TimeSlot.DEFAULT_SCHEME_ID) return
        changeSettings(onError) {

            val currentTableId = resolveEffectiveTableId()
            timeSlotRepository.deleteScheme(currentTableId, schemeId)
            onSuccess()
        }
    }

    /**
     * 切换“根据日期自动切换作息方案（夏令时/冬令时）”开关。
     */
    fun onToggleAutoSwitch(enabled: Boolean, onError: (String) -> Unit = {}) {
        changeSettings(onError) {
            val currentTableId = resolveEffectiveTableId()
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
    fun onSaveSchemeDates(schemeId: String, startMonthDay: String?, endMonthDay: String?, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        changeSettings(onError) {
            val currentTableId = resolveEffectiveTableId()
            timeSlotRepository.upsertSchemeMeta(
                TimeSlotScheme(
                    courseTableId = currentTableId,
                    schemeId = schemeId,
                    startMonthDay = startMonthDay,
                    endMonthDay = endMonthDay
                )
            )
            onSuccess()
        }
    }
}

data class TimeSlotUiState(
    val timeSlots: List<TimeSlot>,
    val defaultClassDuration: Int,
    val defaultBreakDuration: Int,
    val currentSchemeId: String = TimeSlot.DEFAULT_SCHEME_ID,
    val schemeIds: List<String> = emptyList(),
    /** 当日实际生效的作息方案（自动切换解析结果）；与 currentSchemeId 不同时页头提示。 */
    val effectiveSchemeId: String? = null,
    val isDataLoaded: Boolean = false,
    val schemeMetas: List<TimeSlotScheme> = emptyList(),
    val autoSwitchScheme: Boolean = false
)
