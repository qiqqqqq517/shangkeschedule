package com.shangkeschedule.ui.settings.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.api.date.ApiDateImporter
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.data.model.AutoControlMode
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.notification.plan.MorningAlarmPlan
import com.shangkeschedule.tool.HolidayRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.koin.core.annotation.KoinViewModel

/**
 * 通知设置页面弹窗状态
 */
sealed interface NotificationDialogType {
    data object None : NotificationDialogType
    data object EditRemindMinutes : NotificationDialogType
    data object AutoModeSelection : NotificationDialogType
    data object ClearConfirmation : NotificationDialogType

    /** 管理跳过日期：手动增删放假 / 停课日期。 */
    data object ManageSkippedDates : NotificationDialogType

    /** 编辑早八闹钟的提前分钟数。 */
    data object EditMorningAlarmLead : NotificationDialogType
}

/**
 * 通知设置页面 UI 状态
 *
 * @property reminderEnabled 课程提醒开关
 * @property remindBeforeMinutes 提前提醒分钟数
 * @property skippedDates 跳过的节假日日期集合
 * @property isLoading 是否正在加载或导入数据
 * @property exactAlarmStatus 系统精确闹钟权限允许状态
 * @property dndPermissionStatus 系统勿扰权限允许状态
 * @property autoModeEnabled 自动模式（勿扰/静音）开关
 * @property autoControlMode 自动控制模式类型
 * @property compatWearableSync 穿戴设备兼容同步开关
 * @property dynamicIslandEnabled 灵动岛（Android 16 实时更新）开关
 * @property morningAlarmEnabled 早八闹钟开关
 * @property morningAlarmLeadMinutes 早八闹钟提前分钟数
 * @property nextMorningAlarm 下一个早八闹钟预览；null = 近期无需早起
 * @property activeDialog 当前展示的弹窗类型
 */
data class NotificationSettingsUiState(
    val reminderEnabled: Boolean = false,
    val remindBeforeMinutes: Int = 15,
    val skippedDates: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val exactAlarmStatus: Boolean = false,
    val dndPermissionStatus: Boolean = false,
    val autoModeEnabled: Boolean = false,
    val autoControlMode: AutoControlMode = AutoControlMode.DND,
    val compatWearableSync: Boolean = false,
    val dynamicIslandEnabled: Boolean = false,
    val nextClassNotificationEnabled: Boolean = false,
    val examCountdownReminderEnabled: Boolean = false,
    val morningAlarmEnabled: Boolean = false,
    val morningAlarmLeadMinutes: Int = 45,
    val nextMorningAlarm: MorningAlarmPlan.MorningAlarm? = null,
    val activeDialog: NotificationDialogType = NotificationDialogType.None
)

/**
 * 通知设置 ViewModel
 *
 * 负责管理通知设置页面的 UI 状态及与配置存储库的数据持久化交互。
 *
 * 相对旧实现的两点重构：
 *  1. 所有写操作改走 `AppSettingsRepository` 的**单字段原子更新**方法，
 *     不再 `first()` 读→`copy()`→整写 30+ 键（既有读写竞态，又会连带重写无关设置）；
 *  2. 新增早八闹钟的状态与「下一个闹钟」预览（读课程表算第一节课）。
 */
@KoinViewModel
class NotificationSettingsViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val widgetRepository: WidgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * 加载本地通知设置配置
     */
    private fun loadSettings() {
        viewModelScope.launch {
            appSettingsRepository.getAppSettings().collect { settings ->
                _uiState.value = _uiState.value.copy(
                    reminderEnabled = settings.reminderEnabled,
                    remindBeforeMinutes = settings.remindBeforeMinutes,
                    skippedDates = settings.skippedDates,
                    autoModeEnabled = settings.autoModeEnabled,
                    autoControlMode = settings.autoControlMode,
                    compatWearableSync = settings.compatWearableSync,
                    dynamicIslandEnabled = settings.dynamicIslandEnabled,
                    nextClassNotificationEnabled = settings.nextClassNotificationEnabled,
                    examCountdownReminderEnabled = settings.examCountdownReminderEnabled,
                    morningAlarmEnabled = settings.morningAlarmEnabled,
                    morningAlarmLeadMinutes = settings.morningAlarmLeadMinutes
                )
                refreshMorningAlarmPreview()
            }
        }
    }

    /**
     * 重算「下一个早八闹钟」预览。
     *
     * 读课程表里未来 7 天的课，取每天最早的一节按提前量回推，
     * 展示最近的一条（或 null 表示近期无需早起）。
     */
    fun refreshMorningAlarmPreview() {
        viewModelScope.launch {
            val preview = runCatching {
                withContext(Dispatchers.IO) {
                    val state = _uiState.value
                    val today = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                    val courses: List<WidgetCourse> = widgetRepository
                        .getWidgetCoursesByDateRange(today.toString(), today.plusDaysCompat(7).toString())
                        .first()
                    MorningAlarmPlan.plan(
                        courses = courses,
                        skippedDates = state.skippedDates,
                        today = today,
                        days = 7,
                        leadMinutes = state.morningAlarmLeadMinutes
                    ).firstOrNull()
                }
            }.getOrNull()
            _uiState.value = _uiState.value.copy(nextMorningAlarm = preview)
        }
    }

    private fun LocalDate.plusDaysCompat(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

    /**
     * 显示指定的弹窗
     */
    fun showDialog(type: NotificationDialogType) {
        _uiState.value = _uiState.value.copy(activeDialog = type)
    }

    /**
     * 关闭当前弹窗
     */
    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(activeDialog = NotificationDialogType.None)
    }

    /**
     * 更新精确闹钟权限允许状态
     */
    fun updateExactAlarmStatus(hasPermission: Boolean) {
        _uiState.value = _uiState.value.copy(exactAlarmStatus = hasPermission)
    }

    /**
     * 更新勿扰权限允许状态
     */
    fun updateDndPermissionStatus(hasPermission: Boolean) {
        _uiState.value = _uiState.value.copy(dndPermissionStatus = hasPermission)
    }

    /** 更新课程提醒开关状态。 */
    fun updateReminderEnabled(isEnabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.updateReminderEnabled(isEnabled) }
    }

    /** 更新穿戴设备兼容同步开关状态。 */
    fun updateCompatWearableSync(isEnabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.updateCompatWearableSync(isEnabled) }
    }

    /** 更新状态栏「灵动岛」开关（Android 16 实时更新）。 */
    fun updateDynamicIslandEnabled(isEnabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.updateDynamicIslandEnabled(isEnabled) }
    }

    /**
     * 更新「下一节课常驻通知」开关。
     *
     * 不需要在这里手动触发重排：该字段已进 [SyncManager] 的
     * `NotificationSettingsSignature`，开关一变就会走 `NotificationScheduler.reschedule()`
     * 立刻投递/撤下通知并（反）注册 15 分钟刷新任务。
     */
    fun updateNextClassNotificationEnabled(isEnabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.updateNextClassNotificationEnabled(isEnabled) }
    }

    /** 更新「考试倒计时提醒」开关（同样由设置签名驱动重排）。 */
    fun updateExamCountdownReminderEnabled(isEnabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.updateExamCountdownReminderEnabled(isEnabled) }
    }

    /** 更新早八闹钟开关。 */
    fun updateMorningAlarmEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.updateMorningAlarmEnabled(isEnabled)
            refreshMorningAlarmPreview()
        }
    }

    /** 保存早八闹钟提前分钟数并关闭弹窗。 */
    fun updateMorningAlarmLeadMinutes(minutes: Int) {
        viewModelScope.launch {
            appSettingsRepository.updateMorningAlarmLeadMinutes(minutes)
            refreshMorningAlarmPreview()
            dismissDialog()
        }
    }

    /**
     * 保存提前提醒分钟数并关闭弹窗
     */
    fun updateRemindBeforeMinutes(minutes: Int) {
        viewModelScope.launch {
            appSettingsRepository.updateRemindBeforeMinutes(minutes)
            dismissDialog()
        }
    }

    /**
     * 更新自动模式开关状态及控制类型，更新完成后关闭弹窗。
     * 开关与模式一起原子写入（避免「已开启但模式仍是旧值」的中间态被调度器读到）。
     */
    fun updateAutoMode(isEnabled: Boolean, newControlMode: AutoControlMode) {
        viewModelScope.launch {
            appSettingsRepository.updateAutoMode(isEnabled, newControlMode)
            dismissDialog()
        }
    }

    /**
     * 从网络同步并更新节假日跳过日期
     *
     * @param onResult 导入结果回调
     */
    fun updateHolidays(onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    ApiDateImporter.importAndSaveSkippedDates(appSettingsRepository)
                }
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
            refreshMorningAlarmPreview()
            onResult(result)
        }
    }

    /**
     * 清除所有已跳过的节假日日期
     *
     * @param onResult 清除结果回调
     */
    fun clearSkippedDates(onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { appSettingsRepository.updateSkippedDates(emptySet()) }
            if (result.isSuccess) {
                refreshMorningAlarmPreview()
                dismissDialog()
            }
            onResult(result)
        }
    }

    /**
     * 手动新增一个跳过日期（放假 / 停课）。
     *
     * 与「联网更新节假日」共用同一份集合，写入即生效：设置变更经 DataStore Flow
     * 传导到提醒排程、早八闹钟与小组件课表，无需额外触发同步。
     */
    fun addSkippedDate(date: LocalDate) {
        viewModelScope.launch {
            runCatching { appSettingsRepository.addSkippedDates(listOf(date.toString())) }
            refreshMorningAlarmPreview()
        }
    }

    /** 手动移除一个跳过日期。 */
    fun removeSkippedDate(date: String) {
        viewModelScope.launch {
            runCatching { appSettingsRepository.removeSkippedDate(date) }
            refreshMorningAlarmPreview()
        }
    }

    /**
     * 按区间批量新增跳过日期（寒假、国庆等连续假期）。
     *
     * @param onResult 回调 false 表示区间非法（结束早于开始，或超过
     *                 [HolidayRange.MAX_DAYS] 天）而**未写入任何日期**。
     */
    fun addSkippedDateRange(start: LocalDate, end: LocalDate, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val dates = HolidayRange.expand(start, end)
            if (dates == null) {
                onResult(false)
                return@launch
            }
            val result = runCatching { appSettingsRepository.addSkippedDates(dates) }
            refreshMorningAlarmPreview()
            onResult(result.isSuccess)
        }
    }
}
