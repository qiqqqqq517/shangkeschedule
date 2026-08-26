package com.shangkeschedule.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.AppThemeMode
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.data.model.DualColor
import com.shangkeschedule.data.model.StartScreen
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.StyleSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 界面原子状态类：包含设置页渲染所需的全部数据包
 */
data class SettingsUiState(
    val appSettings: AppSettingsModel = AppSettingsModel(),
    val courseConfig: CourseTableConfig? = null,
    val currentWeek: Int? = null,
    val isReady: Boolean = false
)

@KoinViewModel
class SettingsViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val styleSettingsRepository: StyleSettingsRepository
) : ViewModel() {

    // 1. 基础配置流 (DataStore)
    private val appSettingsFlow = appSettingsRepository.getAppSettings()

    // 2. 动态物理配置流 (Room)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val courseTableConfigFlow = appSettingsFlow.flatMapLatest { settings ->
        val id = settings.currentCourseTableId
        if (id.isNotEmpty()) appSettingsRepository.getCourseTableConfigFlow(id)
        else flowOf(null)
    }

    /**
     * 核心优化：聚合 UI 状态流
     * 使用 combine 将多个异步源合并为一个原子包，消除状态裂缝
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        appSettingsFlow,
        courseTableConfigFlow
    ) { settings, config ->
        val week = if (config != null) {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val rawWeek = appSettingsRepository.getWeekIndexAtDate(
                targetDate = today,
                startDateStr = config.semesterStartDate,
                firstDayOfWeekInt = config.firstDayOfWeek
            )
            rawWeek?.takeIf { it in 1..config.semesterTotalWeeks }
        } else null

        SettingsUiState(
            appSettings = settings,
            courseConfig = config,
            currentWeek = week,
            isReady = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = SettingsUiState()
    )

    /**
     * 当前主题的课程配色表，供情侣课表颜色选择器使用。
     * 随主题切换和个性化配置实时更新，确保选择器显示的颜色与课程块实际渲染一致。
     */
    val courseColorMaps: StateFlow<List<DualColor>> = styleSettingsRepository.styleFlow
        .map { it.courseColorMaps }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.shangkeschedule.data.model.ScheduleGridStyle.DEFAULT_COLOR_MAPS
        )

    /**
     * 是否显示非本周课程
     */
    fun onShowNonCurrentWeekChanged(show: Boolean) {
        viewModelScope.launch {
            val currentSettings = uiState.value.appSettings
            val updatedSettings = currentSettings.copy(showNonCurrentWeekCourses = show)
            appSettingsRepository.insertOrUpdateAppSettings(updatedSettings)
        }
    }

    /**
     * 更新周末显示
     */
    fun onShowWeekendsChanged(show: Boolean) {
        viewModelScope.launch {
            uiState.value.courseConfig?.let { currentConfig ->
                val update = if (!show) {
                    currentConfig.copy(showWeekends = false, firstDayOfWeek = DayOfWeek.MONDAY.isoDayNumber)
                } else {
                    currentConfig.copy(showWeekends = true)
                }
                appSettingsRepository.insertOrUpdateCourseConfig(update)
            }
        }
    }

    /**
     * 更新起始日期
     */
    fun onSemesterStartDateSelected(selectedDateMillis: Long?) {
        viewModelScope.launch {
            val dateMillis = selectedDateMillis ?: return@launch
            uiState.value.courseConfig?.let { currentConfig ->
                val selectedDate = Instant.fromEpochMilliseconds(dateMillis)
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
                val newConfig = currentConfig.copy(
                    semesterStartDate = selectedDate.toString()
                )
                appSettingsRepository.insertOrUpdateCourseConfig(newConfig)
            }
        }
    }

    /**
     * 更新总周数
     */
    fun onSemesterTotalWeeksSelected(totalWeeks: Int) {
        viewModelScope.launch {
            uiState.value.courseConfig?.let {
                appSettingsRepository.insertOrUpdateCourseConfig(it.copy(semesterTotalWeeks = totalWeeks))
            }
        }
    }

    /**
     * 手动对齐周数 (联动：反向推算开学日期)
     */
    fun onCurrentWeekManuallySet(weekNumber: Int?) {
        viewModelScope.launch {
            appSettingsRepository.setSemesterStartDateFromWeek(weekNumber)
        }
    }

    /**
     * 更新每周起始日
     */
    fun onFirstDayOfWeekSelected(dayOfWeekInt: Int) {
        viewModelScope.launch {
            uiState.value.courseConfig?.let { currentConfig ->
                appSettingsRepository.insertOrUpdateCourseConfig(currentConfig.copy(firstDayOfWeek = dayOfWeekInt))
            }
        }
    }

    /**
     * 更新应用启动时的默认主页
     */
    fun onStartScreenChanged(newScreen: StartScreen) {
        viewModelScope.launch {
            val currentSettings = uiState.value.appSettings
            val updatedSettings = currentSettings.copy(startScreen = newScreen)
            appSettingsRepository.insertOrUpdateAppSettings(updatedSettings)
        }
    }

    /**
     * 主题模式 (跟随系统/亮色/深色)
     */
    fun onThemeModeChanged(newMode: AppThemeMode) {
        viewModelScope.launch {
            val currentSettings = uiState.value.appSettings
            val updatedSettings = currentSettings.copy(themeMode = newMode)
            appSettingsRepository.insertOrUpdateAppSettings(updatedSettings)
        }
    }

    /**
     * 主题预设 (经典/云舒/利落)：保存主题选择，并把预设样式「一键应用」到个性化配置。
     * applyStylePreset 内部会保留用户壁纸与时间轴模式。
     */
    fun onThemePresetChanged(preset: AppThemePreset) {
        viewModelScope.launch {
            appSettingsRepository.updateThemePreset(preset)
            styleSettingsRepository.applyStylePreset(preset.gridStyle)
        }
    }

    /**
     * 动态取色开关 (Material You)
     */
    fun onUseDynamicColorChanged(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = uiState.value.appSettings
            val updatedSettings = currentSettings.copy(useDynamicColor = enabled)
            appSettingsRepository.insertOrUpdateAppSettings(updatedSettings)
        }
    }

    /**
     * 自定义浅色模式种子色（传 Color 则修改，传 null 则重置）
     */
    fun onCustomLightPrimaryChanged(color: Color? = null) {
        viewModelScope.launch {
            val currentSettings = uiState.value.appSettings
            val newColorArgb = color?.toArgb()?.toLong()
                ?: AppSettingsModel().customLightPrimary

            val updatedSettings = currentSettings.copy(
                customLightPrimary = newColorArgb
            )
            appSettingsRepository.insertOrUpdateAppSettings(updatedSettings)
        }
    }

    /**
     * 自定义深色模式种子色（传 Color 则修改，传 null 则重置）
     */
    fun onCustomDarkPrimaryChanged(color: Color? = null) {
        viewModelScope.launch {
            val currentSettings = uiState.value.appSettings
            val newColorArgb = color?.toArgb()?.toLong()
                ?: AppSettingsModel().customDarkPrimary

            val updatedSettings = currentSettings.copy(
                customDarkPrimary = newColorArgb
            )
            appSettingsRepository.insertOrUpdateAppSettings(updatedSettings)
        }
    }

    /**
     * 更新开发者模式开关状态
     */
    fun onDeveloperModeChanged(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = uiState.value.appSettings
            val updatedSettings = currentSettings.copy(developerModeEnabled = enabled)
            appSettingsRepository.insertOrUpdateAppSettings(updatedSettings)
        }
    }

    /**
     * 情侣课表开关
     */
    fun onCoupleScheduleEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = uiState.value.appSettings
            val updatedSettings = currentSettings.copy(coupleScheduleEnabled = enabled)
            appSettingsRepository.insertOrUpdateAppSettings(updatedSettings)
        }
    }

    /**
     * 更新本人课表颜色索引
     */
    fun onSelfCourseColorIndexChanged(colorIndex: Int) {
        viewModelScope.launch {
            val currentSettings = uiState.value.appSettings
            val updatedSettings = currentSettings.copy(selfCourseColorIndex = colorIndex)
            appSettingsRepository.insertOrUpdateAppSettings(updatedSettings)
        }
    }

    /**
     * 更新 crush 课表颜色索引
     */
    fun onCrushCourseColorIndexChanged(colorIndex: Int) {
        viewModelScope.launch {
            val currentSettings = uiState.value.appSettings
            val updatedSettings = currentSettings.copy(crushCourseColorIndex = colorIndex)
            appSettingsRepository.insertOrUpdateAppSettings(updatedSettings)
        }
    }
}