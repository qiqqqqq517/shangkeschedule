package com.shangkeschedule.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseTableConfigDao
import com.shangkeschedule.data.db.main.CourseTableDao
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.AppThemeMode
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.data.model.AutoControlMode
import com.shangkeschedule.data.model.CertCredential
import com.shangkeschedule.data.model.CreditRequirement
import com.shangkeschedule.data.model.AdapterSyncRecord
import com.shangkeschedule.data.model.GpaScale
import com.shangkeschedule.data.model.NextCardMode
import com.shangkeschedule.data.model.RefreshRateMode
import com.shangkeschedule.tool.AppLog
import com.shangkeschedule.ui.schedule.ScheduleViewMode
import com.shangkeschedule.ui.theme.MotionSpeed
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.AnimationStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

private const val TAG = "AppSettingsRepository"

/**
 * 历史上住在 `app_settings.preferences_pb` 里的「只在本机」键（v4.66.0–v4.67.15）。
 *
 * v4.67.16 起这些值改落已被备份规则排除的 `api_config.preferences_pb`；这里只保留旧键名，
 * 供一次性惰性迁移读取并删除。新增或读取密钥请用 [ApiConfigRepository.ApiKeys.Secrets]。
 */
private val LEGACY_AI_API_KEY = stringPreferencesKey("ai_api_key")
private const val LEGACY_CERT_NAME_PREFIX = "cert_name_"
private const val LEGACY_CERT_TICKET_PREFIX = "cert_ticket_"

/**
 * 从旧存储的 Preferences 快照里挑出需要搬家的「只在本机」键值（v4.67.16 迁移用）。
 *
 * 纯函数，便于单测：只认全名 / 前缀匹配，且值必须是 String（其它类型一律不动）。
 */
internal fun collectLegacyLocalSecrets(prefs: Preferences): Map<Preferences.Key<String>, String> {
    val result = mutableMapOf<Preferences.Key<String>, String>()
    prefs.asMap().forEach { (key, value) ->
        val name = key.name
        val isLocalOnly = name == LEGACY_AI_API_KEY.name ||
            name.startsWith(LEGACY_CERT_NAME_PREFIX) ||
            name.startsWith(LEGACY_CERT_TICKET_PREFIX)
        if (isLocalOnly && value is String) {
            @Suppress("UNCHECKED_CAST")
            result[key as Preferences.Key<String>] = value
        }
    }
    return result
}

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
    /**
     * 「只在本机」的密钥 / 凭据存储（v4.67.16）。
     *
     * 与 [dataStore] 分开的唯一原因是备份规则：这份文件已被 `backup_rules.xml` 与
     * `data_extraction_rules.xml` 排除，云备份与换机迁移都不带它。
     */
    @Named("ApiConfig") private val secretsStore: DataStore<Preferences>,
    private val courseTableDao: CourseTableDao,
    private val courseTableConfigDao: CourseTableConfigDao
) {
    // ------------------------------------------------------------------
    // 「只在本机」数据的存储边界（v4.67.16）
    //
    // 凡是声明过「只存本机 / 不参与云备份」的数据都必须落 secretsStore：
    // dataStore 对应的 app_settings.preferences_pb 会被 Android 自动备份上云。
    // ------------------------------------------------------------------

    private val secretsMigrationMutex = Mutex()
    private var secretsMigrated = false

    /**
     * 一次性把历史上落在 app_settings 存储里的密钥 / 凭据搬到 secretsStore。
     *
     * 幂等：同一进程只跑一次；目标存储已有非空值时不覆盖（以新存储为准）；
     * 搬完**立刻从旧存储删除** —— 不删的话旧值仍会随云备份上传，等于没修。
     */
    private suspend fun ensureLocalSecretsMigrated() {
        secretsMigrationMutex.withLock {
            if (secretsMigrated) return
            val legacy = collectLegacyLocalSecrets(dataStore.data.first())
            if (legacy.isNotEmpty()) {
                secretsStore.edit { secrets ->
                    legacy.forEach { (key, value) ->
                        if (secrets[key].isNullOrEmpty() && value.isNotEmpty()) {
                            secrets[key] = value
                        }
                    }
                }
                dataStore.edit { prefs ->
                    legacy.keys.forEach { prefs.remove(it) }
                }
            }
            secretsMigrated = true
        }
    }

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

    /** 首表 ID 数据流：与 [CourseTableDao.getFirstTableOnce] 同序，表增删时自动刷新。 */
    private val firstTableIdFlow: Flow<String> =
        courseTableDao.getFirstTableFlow().map { it?.id ?: "" }

    /**
     * 共享热流：全仓 20+ 处订阅 [getAppSettings]，若各自订阅冷流，任意一个设置键写入
     * 都会让每个订阅者重放 DataStore 且各查一次 Room；shareIn 收敛为单上游，
     * 订阅归零 5s 后自动停止（v3.54.0）。
     */
    private val sharedAppSettings: Flow<AppSettingsModel> = combine(
        dataStore.data,
        firstTableIdFlow
    ) { prefs, dbFirstTableId ->
        AppSettingsModel.fromPreferences(prefs, dbFirstTableId)
    }.shareIn(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        started = SharingStarted.WhileSubscribed(5000),
        replay = 1
    )

    /**
     * 获取应用设置数据流。
     */
    fun getAppSettings(): Flow<AppSettingsModel> = sharedAppSettings

    /**
     * 获取一次性的应用设置快照。
     * 走冷读（直接读 DataStore 最新落盘值），不经 [sharedAppSettings] 热流——
     * 热流 replay 可能落后于刚完成的写入，破坏「写后立即读」语义（v3.54.0 复审 P2）。
     */
    suspend fun getAppSettingsOnce(): AppSettingsModel {
        val prefs = dataStore.data.first()
        val dbFirstTableId = courseTableDao.getFirstTableOnce()?.id ?: ""
        return AppSettingsModel.fromPreferences(prefs, dbFirstTableId)
    }

    /**
     * PF4（v3.69.0）：只读取「深浅模式」一个键，供启动期同步系统夜间模式使用。
     *
     * 单独拆出来是因为调用方在 `Application.onCreate`（androidMain 的
     * `syncNightModeFromStoredThemeMode`），此时只关心这一个键，不必等完整的
     * [AppSettingsModel] 组合流（它会连带 Room 查询）。
     */
    suspend fun currentThemeMode(): AppThemeMode =
        dataStore.data.first()[AppSettingsModel.KEY_THEME_MODE]
            ?.let { AppThemeMode.fromString(it) }
            ?: AppThemeMode.FOLLOW_SYSTEM

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
            prefs[AppSettingsModel.KEY_DEVELOPER_MODE_ENABLED] = newSettings.developerModeEnabled
            prefs[AppSettingsModel.KEY_COUPLE_SCHEDULE_ENABLED] = newSettings.coupleScheduleEnabled
            prefs[AppSettingsModel.KEY_COUPLE_SHOW_TIME_RANGES] = newSettings.coupleShowTimeRanges
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
            prefs[AppSettingsModel.KEY_REFRESH_RATE_MODE] = newSettings.refreshRateMode.value
            prefs[AppSettingsModel.KEY_STAR_PROMPT_SHOWN] = newSettings.starPromptShown
        }
    }

    /** 单独持久化应用主题预设。 */
    suspend fun updateThemePreset(preset: AppThemePreset) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_THEME_PRESET] = preset.value
        }
    }

    /** 单独持久化屏幕刷新率偏好（v3.56.0「动画效果 → 屏幕刷新率」）。 */
    suspend fun updateRefreshRateMode(mode: RefreshRateMode) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_REFRESH_RATE_MODE] = mode.value
        }
    }

    /** 单独持久化周课表视图模式。 */
    suspend fun updateScheduleViewMode(mode: ScheduleViewMode) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_SCHEDULE_VIEW_MODE] = mode.value
        }
    }

    // ------------------------------------------------------------------
    // 通知 / 自动化设置的单字段原子更新
    //
    // 背景：旧 NotificationSettingsViewModel 的每个开关都是
    //   `getAppSettings().first()` → `copy(字段=值)` → `insertOrUpdateAppSettings(整模型)`
    // 而 insertOrUpdateAppSettings 会一次性写回**全部 30+ 个键**。两个后果：
    //  1. **读改写竞态**：两次并发开关操作会以各自陈旧快照互相覆盖；
    //  2. **无关字段被重写**：通知页改动一个开关，会连带把主题/动画/情侣课表等
    //     全部字段重写一遍（其中任一字段若在别处刚被修改，就会被这份陈旧快照回退）。
    // 这里按「只写自己要改的键」的方式拆分，与既有 updateThemePreset 同属一个模式。
    // ------------------------------------------------------------------

    /** 单独持久化课程提醒总开关。 */
    suspend fun updateReminderEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[AppSettingsModel.KEY_REMINDER_ENABLED] = enabled }
    }

    /** 单独持久化提前提醒分钟数。 */
    suspend fun updateRemindBeforeMinutes(minutes: Int) {
        dataStore.edit { prefs -> prefs[AppSettingsModel.KEY_REMIND_BEFORE_MINUTES] = minutes }
    }

    /** 单独持久化跳过日期集合。 */
    suspend fun updateSkippedDates(dates: Set<String>) {
        dataStore.edit { prefs -> prefs[AppSettingsModel.KEY_SKIPPED_DATES] = dates }
    }

    /**
     * 原子新增一批跳过日期（手动设定的节假日 / 停课）。
     *
     * 读改写全程在 `dataStore.edit` 内完成：手动添加与「联网更新节假日」
     * 可能前后脚触发，若在编辑外先 `getAppSettings().first()` 取快照再整写，
     * 两者会各自以旧快照覆盖对方（与本文件开头记录的读写竞态同源）。
     */
    suspend fun addSkippedDates(dates: Collection<String>) {
        if (dates.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[AppSettingsModel.KEY_SKIPPED_DATES] ?: emptySet()
            prefs[AppSettingsModel.KEY_SKIPPED_DATES] = current + dates
        }
    }

    /** 原子移除一个跳过日期（手动取消某天的放假 / 停课标记）。 */
    suspend fun removeSkippedDate(date: String) {
        dataStore.edit { prefs ->
            val current = prefs[AppSettingsModel.KEY_SKIPPED_DATES] ?: emptySet()
            prefs[AppSettingsModel.KEY_SKIPPED_DATES] = current - date
        }
    }

    /** 单独持久化自动模式开关。 */
    suspend fun updateAutoModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[AppSettingsModel.KEY_AUTO_MODE_ENABLED] = enabled }
    }

    /** 单独持久化自动控制模式（勿扰/静音）。 */
    suspend fun updateAutoControlMode(mode: AutoControlMode) {
        dataStore.edit { prefs -> prefs[AppSettingsModel.KEY_AUTO_CONTROL_MODE] = mode.value }
    }

    /**
     * 一次性原子写入「自动模式开关 + 模式类型」。
     * 两者总是一起被用户选择（下弹窗选模式即开启），拆成两次写入会出现
     * 「已开启但模式还是旧值」的中间态，而调度器可能恰好读到该中间态。
     */
    suspend fun updateAutoMode(enabled: Boolean, mode: AutoControlMode) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_AUTO_MODE_ENABLED] = enabled
            prefs[AppSettingsModel.KEY_AUTO_CONTROL_MODE] = mode.value
        }
    }

    /** 单独持久化「兼容穿戴设备同步通知」开关。 */
    suspend fun updateCompatWearableSync(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[AppSettingsModel.KEY_COMPAT_WEARABLE_SYNC] = enabled }
    }

    /** 单独持久化「灵动岛」开关。 */
    suspend fun updateDynamicIslandEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[AppSettingsModel.KEY_DYNAMIC_ISLAND_ENABLED] = enabled }
    }

    /** 单独持久化「下一节课常驻通知」开关。 */
    suspend fun updateNextClassNotificationEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[AppSettingsModel.KEY_NEXT_CLASS_NOTIFICATION_ENABLED] = enabled }
    }

    /** 单独持久化「考试倒计时提醒」开关。 */
    suspend fun updateExamCountdownReminderEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[AppSettingsModel.KEY_EXAM_COUNTDOWN_REMINDER_ENABLED] = enabled }
    }

    /**
     * 标记「GitHub Star 引导」已展示（K5，一次性）。
     *
     * 无论用户点了「去点个 Star」还是「不再提示」都写 true：这是**一次性**提示，
     * 不做「以后再说」反复弹窗（不打扰）。刻意不参与备份——纯界面状态，重装后再提示一次无害。
     */
    suspend fun updateStarPromptShown(shown: Boolean) {
        dataStore.edit { prefs -> prefs[AppSettingsModel.KEY_STAR_PROMPT_SHOWN] = shown }
    }

    /** 单独持久化早八闹钟开关。 */
    suspend fun updateMorningAlarmEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[AppSettingsModel.KEY_MORNING_ALARM_ENABLED] = enabled }
    }

    /**
     * 单独持久化早八闹钟提前量；越界值收敛到 0–180（与 `MorningAlarmPlan` 同口径，
     * 避免脏配置写进存储后每次计算都要额外防御）。
     */
    suspend fun updateMorningAlarmLeadMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(0, 180)
        dataStore.edit { prefs -> prefs[AppSettingsModel.KEY_MORNING_ALARM_LEAD_MINUTES] = clamped }
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
     * 液态玻璃边缘折射（v3.47.0）：一次性写入整组折射参数（预设档位用，减少 5 次 DataStore 事务）。
     *
     * 注意 [glassRefractionEnabled] 与模糊半径是两个独立维度：
     * 折射只增加"边缘透镜"这一层，雾度始终由 KEY_GLASS_BLUR_RADIUS_DP 决定。
     */
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

    /**
     * 单独持久化成绩页的绩点制式（v4.66.0）。
     *
     * 只写这一个键：绩点制式会影响成绩页上所有课程的绩点展示，
     * 但切换它不应触发整份设置写回（避免覆盖并发修改的其它设置项）。
     */
    suspend fun updateGpaScale(scale: GpaScale) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_GPA_SCALE] = scale.value
        }
    }

    /**
     * 整体覆盖学业情况的类别学分要求（v4.66.0「学业情况」）。
     *
     * 供备份恢复链路使用；界面上的增删改请走 [mutateCreditRequirements]，
     * 那条路径把「读—改—写」放在同一个 `dataStore.edit` 里，天然免疫并发覆盖。
     */
    suspend fun updateCreditRequirements(requirements: List<CreditRequirement>) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_CREDIT_REQUIREMENTS] = CreditRequirement.encode(requirements)
        }
    }

    /**
     * 原子地增删改一条学分要求。
     *
     * 与 [addSkippedDates] 同源：先 `getAppSettings().first()` 取快照再整份写回的做法，
     * 会让「连续快速编辑两条类别」各自以旧快照覆盖对方，故这里把解析、变换、序列化
     * 全部放进 `edit` 事务内。
     */
    suspend fun mutateCreditRequirements(
        transform: (List<CreditRequirement>) -> List<CreditRequirement>
    ) {
        dataStore.edit { prefs ->
            val current = CreditRequirement.decode(prefs[AppSettingsModel.KEY_CREDIT_REQUIREMENTS])
            prefs[AppSettingsModel.KEY_CREDIT_REQUIREMENTS] =
                CreditRequirement.encode(transform(current))
        }
    }

    /**
     * 考证查分凭据（v4.66.0）。
     *
     * 一次读出全部模块的凭据（模块数量固定且极少），页面用一个 Map 渲染，
     * 不必为每一行各建一个 flow。
     *
     * **只落本机凭据存储**（v4.67.16 起，见 [ApiConfigRepository.ApiKeys.Secrets]）：
     * 姓名与准考证号是个人凭据，不该进云备份或随换机迁移。
     */
    fun getCertCredentials(moduleIds: List<String>): Flow<Map<String, CertCredential>> =
        secretsStore.data
            .onStart { ensureLocalSecretsMigrated() }
            .map { prefs ->
                moduleIds.associateWith { moduleId ->
                    CertCredential(
                        name = prefs[ApiConfigRepository.ApiKeys.Secrets.certName(moduleId)].orEmpty(),
                        ticket = prefs[ApiConfigRepository.ApiKeys.Secrets.certTicket(moduleId)].orEmpty()
                    )
                }
            }

    /**
     * 保存某个考证模块的查询凭据（姓名 / 准考证号）。
     *
     * 只写这两个键：与 [updateGpaScale] 同理，避免整份设置写回覆盖并发修改的其它设置项。
     * 存储位置同 [getCertCredentials]（只在本机凭据存储）。
     */
    suspend fun updateCertCredential(moduleId: String, name: String, ticket: String) {
        ensureLocalSecretsMigrated()
        secretsStore.edit { prefs ->
            prefs[ApiConfigRepository.ApiKeys.Secrets.certName(moduleId)] = name.trim()
            prefs[ApiConfigRepository.ApiKeys.Secrets.certTicket(moduleId)] = ticket.trim()
        }
    }

    /**
     * 最近一次教务适配远程同步的记录（v4.66.0）。
     *
     * 从未同步过时返回 null（而不是造一个 0 值），页面据此区分「尚未检查过」与真实结果。
     */
    fun getAdapterSyncRecord(): Flow<AdapterSyncRecord?> = dataStore.data.map { prefs ->
        val atMillis = prefs[AppSettingsModel.KEY_ADAPTER_SYNC_AT]?.toLongOrNull()
        if (atMillis == null || atMillis <= 0L) {
            null
        } else {
            AdapterSyncRecord(
                atMillis = atMillis,
                kind = prefs[AppSettingsModel.KEY_ADAPTER_SYNC_KIND].orEmpty(),
                updatedCount = prefs[AppSettingsModel.KEY_ADAPTER_SYNC_COUNT]?.toIntOrNull() ?: 0
            )
        }
    }

    /** 写入最近一次适配同步记录（只写这三个键，不动其它设置项）。 */
    suspend fun updateAdapterSyncRecord(kind: String, updatedCount: Int, atMillis: Long) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_ADAPTER_SYNC_AT] = atMillis.toString()
            prefs[AppSettingsModel.KEY_ADAPTER_SYNC_KIND] = kind
            prefs[AppSettingsModel.KEY_ADAPTER_SYNC_COUNT] = updatedCount.toString()
        }
    }

    // ------------------------------------------------------------------
    // AI 识别导入（v4.66.0 J1）
    //
    // 全部走「只写自己要改的键」的单字段更新：AI 配置页改一个输入框，
    // 不该连带重写主题 / 动画 / 情侣课表等无关字段（同 updateThemePreset 的模式）。
    // ------------------------------------------------------------------

    /** 单独持久化 AI 识别导入总开关（默认关闭）。 */
    suspend fun updateAiImportEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_AI_IMPORT_ENABLED] = enabled
        }
    }

    /** 单独持久化「数据外发说明」的确认标记。 */
    suspend fun updateAiImportNoticeAccepted(accepted: Boolean) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_AI_IMPORT_NOTICE_ACCEPTED] = accepted
        }
    }

    /**
     * 单独持久化 AI 接口配置（基地址 / 模型名 / API Key）。
     *
     * 三项一起写：它们是同一份配置的三个部分，分开写会留下
     * 「地址换了、Key 还没换」的中间态，下一次识别就会带着旧 Key 打新地址。
     *
     * 但**存储分成两处**：
     * - 基地址与模型名是普通设置，落 [dataStore]，随设置一起参与云备份（换机后重新填 Key 即可用）；
     * - **API Key 只落本机凭据存储**（[secretsStore]，v4.67.16 起），
     *   与「密钥不该跟着备份文件走」的声明一致（见 [ApiConfigRepository.ApiKeys.Secrets.AI_API_KEY]）。
     */
    suspend fun updateAiApiConfig(baseUrl: String, model: String, apiKey: String) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_AI_API_BASE_URL] = baseUrl.trim()
            prefs[AppSettingsModel.KEY_AI_API_MODEL] = model.trim()
        }
        ensureLocalSecretsMigrated()
        secretsStore.edit { secrets ->
            secrets[ApiConfigRepository.ApiKeys.Secrets.AI_API_KEY] = apiKey.trim()
        }
    }

    /**
     * 读取只在本机的 AI 接口 Key（v4.67.16）。
     *
     * 单独开一个挂起读取入口，而不是放回 [AppSettingsModel]：模型每处订阅都会拿到它，
     * 而密钥只有 AI 识别页要用。空串 = 未配置。
     */
    suspend fun getAiApiKey(): String {
        ensureLocalSecretsMigrated()
        return secretsStore.data.first()[ApiConfigRepository.ApiKeys.Secrets.AI_API_KEY].orEmpty()
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
            AppLog.e(TAG, "计算目标日期周次失败", e)
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
