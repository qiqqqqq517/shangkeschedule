package com.shangkeschedule.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.AppThemeMode
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.data.model.DualColor
import com.shangkeschedule.data.model.NextCardMode
import com.shangkeschedule.data.model.RefreshRateMode
import com.shangkeschedule.data.model.StartScreen
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.StyleSettingsRepository
import com.shangkeschedule.tool.AppLog
import com.shangkeschedule.ui.glass.GlassRefractionSettings
import com.shangkeschedule.ui.theme.MotionSpeed
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.AnimationStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Named
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "AppSettingsViewModel"

/**
 * 界面原子状态类：包含设置页渲染所需的全部数据包
 */
data class SettingsUiState(
    val appSettings: AppSettingsModel = AppSettingsModel(),
    val courseConfig: CourseTableConfig? = null,
    val currentWeek: Int? = null,
    val isReady: Boolean = false
)

/**
 * 启动门控窄状态：App() 根部只依赖「是否就绪 + 起始页」两个字段（v3.54.0）。
 * 此前根部 collect 整个 uiState，DataStore 任意键写入都会触达根组合；
 * 收窄后无关设置变化在根部被 distinct 掉，主题树重组只由真正消费设置的层驱动。
 */
data class AppStartGateState(
    val isReady: Boolean = false,
    val startScreen: StartScreen = StartScreen.COURSE_SCHEDULE
)

@OptIn(ExperimentalUuidApi::class)
@KoinViewModel
class SettingsViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val styleSettingsRepository: StyleSettingsRepository,
    private val fileSystem: FileSystem,
    @Named("FilesDir") private val filesDir: Path
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
     * 启动门控：isReady + startScreen 单独 map 出来供 App() 根部订阅（v3.54.0）。
     * 从 [uiState] 派生（而非直接 map appSettingsFlow）：保留旧门控「DataStore + Room 配置
     * 双源到齐才放行」的时序，避免门控打开时主题仍用默认设置渲染一到两帧（复审 P2）。
     */
    val startGate: StateFlow<AppStartGateState> = uiState
        .map { AppStartGateState(isReady = it.isReady, startScreen = it.appSettings.startScreen) }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = AppStartGateState()
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
                    currentConfig.copy(showWeekends = false)
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
     * 液态玻璃模糊半径（v3.25.0「个性化显示」）：单位 dp，0f = 关闭模糊。
     * 一处设置同时作用于底栏胶囊 / 回到本周圆钮 / 课程挂起条 / 玻璃 AppFab——
     * 它们都读同一个 LocalGlassBlurRadius，不存在各件分叉。
     */
    fun onGlassBlurRadiusChanged(radiusDp: Float) {
        viewModelScope.launch {
            appSettingsRepository.updateGlassBlurRadius(radiusDp)
        }
    }

    /**
     * 液态玻璃**边缘折射**（v3.47.0「外观与样式 → 玻璃模糊」新增）。
     *
     * 与 [onGlassBlurRadiusChanged] 是两个独立维度：模糊强度照旧由上面那个方法控制，
     * 这里只负责"边缘透镜"层（开关 / 折射高度 / 折射强度 / 色散 / 厚度感）。
     * 整组参数一次写入，避免滑杆拖动时产生多条 DataStore 事务。
     */
    fun onGlassRefractionChanged(settings: GlassRefractionSettings) {
        viewModelScope.launch {
            val sanitized = settings.sanitized()
            appSettingsRepository.updateGlassRefractionAll(
                enabled = sanitized.enabled,
                heightDp = sanitized.heightDp,
                amountDp = sanitized.amountDp,
                dispersion = sanitized.dispersion,
                depthEffect = sanitized.depthEffect
            )
        }
    }

    /**
     * 全局动画风格（v3.26.0「个性化显示 → 动画效果」）。
     * 一处设置经 LocalAppMotion 注入，全 App 动画手感同步切换。
     */
    fun onAnimationStyleChanged(style: AnimationStyle) {
        viewModelScope.launch {
            appSettingsRepository.updateAnimationStyle(style)
        }
    }

    /**
     * 开/关某个动画分组（v3.26.0）。enabled=false ⇒ 该类动画瞬切无动效。
     */
    fun onToggleAnimationGroup(group: AnimationGroup, enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setAnimationGroupEnabled(group, enabled)
        }
    }

    /**
     * 「减弱动态效果」开关（v3.43.0 无障碍降级）。
     * 开启后位移 / 缩放 / 错峰入场全部归零，只保留短促的不透明度溶解。
     */
    fun onReduceMotionChanged(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.updateReduceMotionEnabled(enabled)
        }
    }

    /**
     * 「动效速度」倍率（v3.44.0）：数值越大越快，统一缩放全部毫秒级时长。
     */
    fun onMotionSpeedChanged(speed: MotionSpeed) {
        viewModelScope.launch {
            appSettingsRepository.updateMotionSpeed(speed)
        }
    }

    /**
     * 屏幕刷新率偏好（v3.56.0）：写入后由 MainActivity 订阅并应用到窗口显示模式。
     */
    fun onRefreshRateModeChanged(mode: RefreshRateMode) {
        viewModelScope.launch {
            appSettingsRepository.updateRefreshRateMode(mode)
        }
    }

    /**
     * 下节课卡「今日课程结束后」行为（v3.47.0「个性化显示 → 下节课卡」）。
     */
    fun onNextCardModeChanged(mode: NextCardMode) {
        viewModelScope.launch {
            appSettingsRepository.updateNextCardMode(mode)
        }
    }

    // ========================================================================
    // 「我的信息」页（v3.49.0）
    // 「我的」页顶部身份卡点击进入，可设置头像 / 昵称 / 学校 / 学院 / 专业 / 年级 / 个性签名。
    // ========================================================================

    /**
     * 保存个人资料（昵称 / 学校 / 学院 / 专业 / 年级 / 个性签名）。
     * 逐键写入 DataStore，不改动其它设置项。
     */
    fun onProfileInfoChanged(
        nickname: String,
        school: String,
        college: String,
        major: String,
        grade: String,
        signature: String
    ) {
        viewModelScope.launch {
            // withContext(NonCancellable)：即使返回页面导致本条目的 ViewModel 被清空，
            // 本次写入仍会落盘，避免「输入后立即返回」丢失最后内容（v3.49.0 持久化缺陷修复）。
            withContext(NonCancellable) {
                appSettingsRepository.updateProfileInfo(
                    nickname = nickname.trim(),
                    school = school.trim(),
                    college = college.trim(),
                    major = major.trim(),
                    grade = grade.trim(),
                    signature = signature.trim()
                )
            }
        }
    }

    /**
     * 保存裁切后的头像：写入私有目录新文件 → 删除旧文件 → 更新路径。
     * 与壁纸同一套"先写新、再删旧"的垃圾回收顺序，避免中途失败丢失头像。
     */
    fun saveProfileAvatar(imageBytes: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val oldPathStr = uiState.value.appSettings.profileAvatarPath
                val newFile = filesDir / "avatar_${Uuid.random()}.jpg"
                fileSystem.write(newFile) { write(imageBytes) }

                appSettingsRepository.updateProfileAvatarPath(newFile.toString())

                if (oldPathStr.isNotEmpty()) {
                    val oldPath = oldPathStr.toPath()
                    if (fileSystem.exists(oldPath)) fileSystem.delete(oldPath)
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "保存头像失败", e)
            }
        }
    }

    /**
     * 移除头像：删除物理文件并清空路径（「我的」页回落为首字母圆形头像）。
     */
    fun removeProfileAvatar() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pathStr = uiState.value.appSettings.profileAvatarPath
                if (pathStr.isNotEmpty()) {
                    val path = pathStr.toPath()
                    if (fileSystem.exists(path)) fileSystem.delete(path)
                }
                appSettingsRepository.updateProfileAvatarPath("")
            } catch (e: Exception) {
                AppLog.e(TAG, "移除头像失败", e)
            }
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
