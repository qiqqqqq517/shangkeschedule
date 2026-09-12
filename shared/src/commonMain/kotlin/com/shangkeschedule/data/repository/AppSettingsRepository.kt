package com.shangkeschedule.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseTableConfigDao
import com.shangkeschedule.data.db.main.CourseTableDao
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.data.model.NextCardMode
import com.shangkeschedule.ui.schedule.ScheduleViewMode
import com.shangkeschedule.ui.theme.MotionSpeed
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.AnimationStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import kotlin.time.Clock

/**
 * 应用配置领域仓库
 *
 * 核心职责：
 * 1. 协调全局偏好设置 (DataStore) 与课表物理配置 (Room) 之间的数据流。
 * 2. 提供时间维度计算算法（周次偏移、日期回溯）。
 */
@Single
class AppSettingsRepository(
    @Named("AppSettings") private val dataStore: DataStore<Preferences>,
    private val courseTableDao: CourseTableDao,
    private val courseTableConfigDao: CourseTableConfigDao
) {
    private val DATE_FORMATTER = LocalDate.Format {
        year()
        char('-')
        monthNumber()
        char('-')
        day()
    }

    /**
     * 课表配置模板
     * 当 DataStore 选中的课表在数据库中尚未初始化配置时，以此模板为基础进行创建。
     */
    private val COURSE_CONFIG_TEMPLATE = CourseTableConfig(
        courseTableId = "",
        showWeekends = false,
        semesterStartDate = null,
        semesterTotalWeeks = 20,
        defaultClassDuration = 45,
        defaultBreakDuration = 10,
        firstDayOfWeek = DayOfWeek.MONDAY.isoDayNumber
    )

    // 应用全局设置 (DataStore)

    /**
     * 获取应用设置数据流。
     */
    fun getAppSettings(): Flow<AppSettingsModel> = dataStore.data.map { prefs ->
        val dbFirstTableId = courseTableDao.getFirstTableOnce()?.id ?: ""

        AppSettingsModel.fromPreferences(prefs, dbFirstTableId)
    }

    /**
     * 获取一次性的应用设置快照。
     */
    suspend fun getAppSettingsOnce(): AppSettingsModel {
        return getAppSettings().first()
    }

    /**
     * 更新应用设置。
     * 将对象解构并原子化地写入 DataStore。
     */
    suspend fun insertOrUpdateAppSettings(newSettings: AppSettingsModel) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_CURRENT_COURSE_TABLE_ID] = newSettings.currentCourseTableId
            prefs[AppSettingsModel.KEY_REMINDER_ENABLED] = newSettings.reminderEnabled
            prefs[AppSettingsModel.KEY_REMIND_BEFORE_MINUTES] = newSettings.remindBeforeMinutes
            prefs[AppSettingsModel.KEY_SKIPPED_DATES] = newSettings.skippedDates
            prefs[AppSettingsModel.KEY_AUTO_MODE_ENABLED] = newSettings.autoModeEnabled
            prefs[AppSettingsModel.KEY_AUTO_CONTROL_MODE] = newSettings.autoControlMode.value
            prefs[AppSettingsModel.KEY_COMPAT_WEARABLE_SYNC] = newSettings.compatWearableSync
            prefs[AppSettingsModel.KEY_DYNAMIC_ISLAND_ENABLED] = newSettings.dynamicIslandEnabled
            prefs[AppSettingsModel.KEY_SHOW_NON_CURRENT_WEEK_COURSES] = newSettings.showNonCurrentWeekCourses
            prefs[AppSettingsModel.KEY_START_SCREEN] = newSettings.startScreen.value
            prefs[AppSettingsModel.KEY_THEME_MODE] = newSettings.themeMode.value
            prefs[AppSettingsModel.KEY_THEME_PRESET] = newSettings.themePreset.value
            prefs[AppSettingsModel.KEY_USE_DYNAMIC_COLOR] = newSettings.useDynamicColor
            prefs[AppSettingsModel.KEY_CUSTOM_LIGHT_PRIMARY] = newSettings.customLightPrimary
            prefs[AppSettingsModel.KEY_CUSTOM_DARK_PRIMARY] = newSettings.customDarkPrimary
            prefs[AppSettingsModel.KEY_DEVELOPER_MODE_ENABLED] = newSettings.developerModeEnabled
            prefs[AppSettingsModel.KEY_COUPLE_SCHEDULE_ENABLED] = newSettings.coupleScheduleEnabled
            prefs[AppSettingsModel.KEY_SELF_COURSE_COLOR_INDEX] = newSettings.selfCourseColorIndex
            prefs[AppSettingsModel.KEY_CRUSH_COURSE_COLOR_INDEX] = newSettings.crushCourseColorIndex
            prefs[AppSettingsModel.KEY_SCHEDULE_VIEW_MODE] = newSettings.scheduleViewMode.value
            prefs[AppSettingsModel.KEY_GLASS_BLUR_RADIUS_DP] = newSettings.glassBlurRadiusDp
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_ENABLED] = newSettings.glassRefractionEnabled
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_HEIGHT_DP] = newSettings.glassRefractionHeightDp
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_AMOUNT_DP] = newSettings.glassRefractionAmountDp
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_DISPERSION] = newSettings.glassRefractionDispersion
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_DEPTH_EFFECT] = newSettings.glassRefractionDepthEffect
            prefs[AppSettingsModel.KEY_ANIMATION_STYLE] = newSettings.animationStyle.value
            prefs[AppSettingsModel.KEY_DISABLED_ANIMATION_GROUPS] =
                newSettings.disabledAnimationGroups.map { it.value }.toSet()
            prefs[AppSettingsModel.KEY_REDUCE_MOTION_ENABLED] = newSettings.reduceMotionEnabled
            prefs[AppSettingsModel.KEY_MOTION_SPEED] = newSettings.motionSpeed.value
            prefs[AppSettingsModel.KEY_NEXT_CARD_MODE] = newSettings.nextCardMode.value
        }
    }

    /** 单独持久化应用主题预设。 */
    suspend fun updateThemePreset(preset: AppThemePreset) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_THEME_PRESET] = preset.value
        }
    }

    /** 单独持久化周课表视图模式。 */
    suspend fun updateScheduleViewMode(mode: ScheduleViewMode) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_SCHEDULE_VIEW_MODE] = mode.value
        }
    }

    /**
     * 单独持久化液态玻璃模糊半径（v3.25.0）。
     * 只写这一个键：避免整份 copy 写回时与其他并发修改互相覆盖。
     */
    suspend fun updateGlassBlurRadius(radiusDp: Float) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_GLASS_BLUR_RADIUS_DP] = radiusDp
        }
    }

    /**
     * 液态玻璃边缘折射（v3.47.0）：五个键各自独立写入，避免整份 copy 写回时
     * 覆盖别的并发修改（与 [updateGlassBlurRadius] 同一约定）。
     *
     * 注意 [glassRefractionEnabled] 与模糊半径是两个独立维度：
     * 折射只增加"边缘透镜"这一层，雾度始终由 KEY_GLASS_BLUR_RADIUS_DP 决定。
     */
    suspend fun updateGlassRefractionEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_ENABLED] = enabled
        }
    }

    suspend fun updateGlassRefractionHeight(heightDp: Float) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_HEIGHT_DP] = heightDp
        }
    }

    suspend fun updateGlassRefractionAmount(amountDp: Float) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_AMOUNT_DP] = amountDp
        }
    }

    suspend fun updateGlassRefractionDispersion(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_DISPERSION] = enabled
        }
    }

    suspend fun updateGlassRefractionDepthEffect(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_DEPTH_EFFECT] = enabled
        }
    }

    /** 一次性写入整组折射参数（预设档位用，减少 5 次 DataStore 事务）。 */
    suspend fun updateGlassRefractionAll(
        enabled: Boolean,
        heightDp: Float,
        amountDp: Float,
        dispersion: Boolean,
        depthEffect: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_ENABLED] = enabled
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_HEIGHT_DP] = heightDp
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_AMOUNT_DP] = amountDp
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_DISPERSION] = dispersion
            prefs[AppSettingsModel.KEY_GLASS_REFRACTION_DEPTH_EFFECT] = depthEffect
        }
    }

    /**
     * 单独持久化全局动画风格（v3.26.0）。只写这一个键，避免整份 copy 覆盖并发修改。
     */
    suspend fun updateAnimationStyle(style: AnimationStyle) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_ANIMATION_STYLE] = style.value
        }
    }

    /**
     * 单独开/关某个动画分组（v3.26.0）。读取当前「已关闭分组」集合，增删该项后只写这一个键。
     * enabled=true ⇒ 从关闭集合移除（恢复动画）；false ⇒ 加入关闭集合（瞬切）。
     */
    suspend fun setAnimationGroupEnabled(group: AnimationGroup, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[AppSettingsModel.KEY_DISABLED_ANIMATION_GROUPS] ?: emptySet()
            val updated = if (enabled) current - group.value else current + group.value
            prefs[AppSettingsModel.KEY_DISABLED_ANIMATION_GROUPS] = updated
        }
    }

    /**
     * 单独持久化「减弱动态效果」开关（v3.43.0）。只写这一个键。
     */
    suspend fun updateReduceMotionEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_REDUCE_MOTION_ENABLED] = enabled
        }
    }

    /**
     * 单独持久化「动效速度」倍率（v3.44.0）。只写这一个键，避免整份 copy 覆盖并发修改。
     */
    suspend fun updateMotionSpeed(speed: MotionSpeed) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_MOTION_SPEED] = speed.value
        }
    }

    /**
     * 单独持久化「下节课卡」结束后行为（v3.47.0）。只写这一个键，避免整份 copy 覆盖并发修改。
     */
    suspend fun updateNextCardMode(mode: NextCardMode) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_NEXT_CARD_MODE] = mode.value
        }
    }

    /**
     * 保存「我的信息」页的个人资料（v3.49.0）。
     *
     * 逐键写入（不整份 copy），避免与其它设置项的并发修改互相覆盖。
     */
    suspend fun updateProfileInfo(
        nickname: String,
        school: String,
        college: String,
        major: String,
        grade: String,
        signature: String
    ) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_PROFILE_NICKNAME] = nickname
            prefs[AppSettingsModel.KEY_PROFILE_SCHOOL] = school
            prefs[AppSettingsModel.KEY_PROFILE_COLLEGE] = college
            prefs[AppSettingsModel.KEY_PROFILE_MAJOR] = major
            prefs[AppSettingsModel.KEY_PROFILE_GRADE] = grade
            prefs[AppSettingsModel.KEY_PROFILE_SIGNATURE] = signature
        }
    }

    /**
     * 单独持久化头像文件路径（v3.49.0）。空串表示未设置头像。
     */
    suspend fun updateProfileAvatarPath(path: String) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_PROFILE_AVATAR_PATH] = path
        }
    }

    // 课表具体物理配置 (Room)

    /**
     * 根据课表ID获取一次性配置快照。
     */
    suspend fun getCourseConfigOnce(tableId: String): CourseTableConfig? {
        return courseTableConfigDao.getConfigOnce(tableId)
    }

    /**
     * 根据课表ID实时获取配置数据流。
     */
    fun getCourseTableConfigFlow(courseTableId: String): Flow<CourseTableConfig?> {
        return courseTableConfigDao.getConfigById(courseTableId)
    }

    /**
     * 更新或插入特定课表的物理配置。
     */
    suspend fun insertOrUpdateCourseConfig(newConfig: CourseTableConfig) {
        val constrainedConfig = when {
            newConfig.firstDayOfWeek == DayOfWeek.SUNDAY.isoDayNumber -> {
                newConfig.copy(showWeekends = true)
            }
            !newConfig.showWeekends -> {
                newConfig.copy(firstDayOfWeek = DayOfWeek.MONDAY.isoDayNumber)
            }
            else -> newConfig
        }
        courseTableConfigDao.insertOrUpdate(constrainedConfig)
    }

    // 业务算法 (时间、周次计算)

    /**
     * 核心周次偏移算法。
     */
    fun getWeekIndexAtDate(
        targetDate: LocalDate,
        startDateStr: String?,
        firstDayOfWeekInt: Int
    ): Int? {
        if (startDateStr.isNullOrEmpty() || firstDayOfWeekInt !in 1..7) return null
        return try {
            val targetFirstDayOfWeek = DayOfWeek(firstDayOfWeekInt)
            val parsedStartDate = LocalDate.parse(startDateStr, DATE_FORMATTER)

            val alignedStartDate = getPreviousOrSameDayOfWeek(parsedStartDate, targetFirstDayOfWeek)
            val alignedTargetDate = getPreviousOrSameDayOfWeek(targetDate, targetFirstDayOfWeek)

            val diffDays = alignedTargetDate.toEpochDays() - alignedStartDate.toEpochDays()
            val diffWeeks = (diffDays / 7).toInt()
            diffWeeks + 1
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 基于当前数据库/DataStore状态计算当前自然周次。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun calculateCurrentWeekFromDb(): Flow<Int?> = getAppSettings().flatMapLatest { appSettings ->
        val currentCourseId = appSettings.currentCourseTableId.ifEmpty {
            return@flatMapLatest flowOf(null)
        }
        courseTableConfigDao.getConfigById(currentCourseId).map { config ->
            if (config == null) return@map null
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val rawWeek = getWeekIndexAtDate(
                targetDate = today,
                startDateStr = config.semesterStartDate,
                firstDayOfWeekInt = config.firstDayOfWeek
            ) ?: return@map null
            if (rawWeek in 1..config.semesterTotalWeeks) rawWeek else null
        }
    }

    /**
     * 根据目标周数反推开学日期。
     */
    suspend fun setSemesterStartDateFromWeek(week: Int?) {
        val appSettings = getAppSettingsOnce()
        val currentCourseId = appSettings.currentCourseTableId.ifEmpty { return }

        val currentConfig = courseTableConfigDao.getConfigOnce(currentCourseId)
            ?: COURSE_CONFIG_TEMPLATE.copy(courseTableId = currentCourseId)

        val newStartDate = if (week != null) {
            calculateSemesterStartDate(week, currentConfig.firstDayOfWeek)
        } else {
            null
        }

        val updatedConfig = currentConfig.copy(semesterStartDate = newStartDate)
        courseTableConfigDao.insertOrUpdate(updatedConfig)
    }

    /**
     * 辅助函数：根据目标周数反推开学日期。
     */
    private fun calculateSemesterStartDate(week: Int, firstDayOfWeekInt: Int): String {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val firstDayOfWeek = DayOfWeek(firstDayOfWeekInt.coerceIn(1, 7))
        val startOfThisWeek = getPreviousOrSameDayOfWeek(today, firstDayOfWeek)
        val daysToSubtract = (week - 1) * 7
        val semesterStartDate = LocalDate.fromEpochDays(startOfThisWeek.toEpochDays() - daysToSubtract)
        return semesterStartDate.format(DATE_FORMATTER)
    }

    /**
     * 对齐日期到指定每周首日的指定星期几。
     */
    private fun getPreviousOrSameDayOfWeek(date: LocalDate, targetDayOfWeek: DayOfWeek): LocalDate {
        val currentDay = date.dayOfWeek.isoDayNumber
        val targetDay = targetDayOfWeek.isoDayNumber
        val daysToSubtract = if (currentDay >= targetDay) {
            currentDay - targetDay
        } else {
            7 - (targetDay - currentDay)
        }
        return LocalDate.fromEpochDays(date.toEpochDays() - daysToSubtract)
    }
}