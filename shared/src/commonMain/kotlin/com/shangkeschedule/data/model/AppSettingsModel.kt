package com.shangkeschedule.data.model

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.jetbrains.compose.resources.StringResource
import shangkeschedule.shared.generated.resources.*
import com.shangkeschedule.ui.schedule.ScheduleViewMode
import com.shangkeschedule.ui.theme.MotionSpeed
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.AnimationStyle

/**
 * 上课时的自动化控制模式枚举
 */
enum class AutoControlMode(val value: String) {
    /** 请勿打扰模式 */
    DND("DND"),

    /** 静音模式 */
    SILENT("SILENT");

    companion object {
        /**
         * 根据字符串获取对应的枚举值，如果匹配失败则返回默认的 DND 模式
         */
        fun fromString(value: String?): AutoControlMode {
            return entries.find { it.value == value } ?: DND
        }
    }
}

/**
 * 可选的启动页面枚举
 */
enum class StartScreen(val value: String, val labelRes: StringResource) {
    /** 周课表 */
    COURSE_SCHEDULE("COURSE_SCHEDULE", Res.string.nav_course_schedule),

    /** 今日课表 */
    TODAY_SCHEDULE("TODAY_SCHEDULE", Res.string.nav_today_schedule);

    companion object {
        fun fromString(value: String?): StartScreen {
            return entries.find { it.value == value } ?: COURSE_SCHEDULE
        }
    }
}

/**
 * 应用主题模式枚举
 */
enum class AppThemeMode(val value: String, val labelRes: StringResource) {
    /** 跟随系统 */
    FOLLOW_SYSTEM("FOLLOW_SYSTEM", Res.string.theme_follow_system),

    /** 浅色模式 */
    LIGHT("LIGHT", Res.string.theme_light),

    /** 深色模式 */
    DARK("DARK", Res.string.theme_dark);

    companion object {
        fun fromString(value: String?): AppThemeMode? {
            return entries.find { it.value == value }
        }
    }
}

/**
 * 应用全局设置业务模型（DataStore 专用）
 * 集中管理业务字段、存储键 (Keys) 以及默认值。
 */
data class AppSettingsModel(
    /** 当前正在使用的课表 ID */
    val currentCourseTableId: String = "",

    /** 是否开启上课前提醒 */
    val reminderEnabled: Boolean = false,

    /** 提前提醒的时间（分钟） */
    val remindBeforeMinutes: Int = 15,

    /** 需要跳过的日期集合 (例如: "2024-03-15") */
    val skippedDates: Set<String> = emptySet(),

    /** 自动化模式的总开关 */
    val autoModeEnabled: Boolean = false,

    /** 自动化控制的具体模式，限定为 [AutoControlMode] */
    val autoControlMode: AutoControlMode = AutoControlMode.DND,

    /**
     * 兼容穿戴设备同步通知的开关
     * true: 开启兼容模式（关闭 Ongoing，方便手环抓取）
     * false: 关闭兼容模式（默认，使用 Android 16 实时更新特性）
     */
    val compatWearableSync: Boolean = false,

    /**
     * 状态栏「灵动岛」开关（Android 16 实时更新 / Promoted Ongoing）
     * true: 通过前台服务在状态栏常驻显示当前/下一节课状态
     * false: 关闭（默认）
     */
    val dynamicIslandEnabled: Boolean = false,

    /**
     * 「下一节课」常驻通知开关。
     *
     * true  → 状态栏常驻显示下一节课（课程名 + 地点 + 剩余分钟），当天课上完即撤下；
     * false → 关闭（默认，与 [reminderEnabled] 同默认：没授权就不占状态栏）。
     *
     * 与 [reminderEnabled] 的关系：两者互不依赖——课前提醒是「到点响一声」，
     * 常驻通知是「一直看得见」，用户完全可以只开后者。
     */
    val nextClassNotificationEnabled: Boolean = false,

    /**
     * 考试倒计时提醒开关。
     *
     * true  → 最近的考试进入提醒窗口（当前 7 天，见 `ExamCountdownNotifier.REMIND_WINDOW_DAYS`）
     *         后，每天更新一条提醒；
     * false → 关闭（默认）。
     *
     * 提醒内容取自「日程」页里分类为「考试」的条目，不新增数据结构。
     */
    val examCountdownReminderEnabled: Boolean = false,

    /**
     * 「GitHub Star 引导」是否已展示过（K5，v4.66.0）。
     *
     * true  → 不再自动提示（用户已看过，或已点了去 Star）；
     * false → 满足「最早一张课表建满 7 天」后自动提示一次。
     */
    val starPromptShown: Boolean = false,

    /**
     * 每日「早八闹钟」开关。
     * true ⇒ 按每天第一节课时间 − [morningAlarmLeadMinutes] 写入系统时钟应用，
     * 用户可在系统闹钟页直接修改/停用。
     */
    val morningAlarmEnabled: Boolean = false,

    /**
     * 早八闹钟提前量（分钟），0–180 自由设置。
     * **独立于** [remindBeforeMinutes]：「叫我起床」与「课前提醒」是两个不同的时间点需求。
     */
    val morningAlarmLeadMinutes: Int = 45,

    /** 是否显示非本周课程 */
    val showNonCurrentWeekCourses: Boolean = false,

    /** 应用启动时显示的页面 */
    val startScreen: StartScreen = StartScreen.COURSE_SCHEDULE,

    /** 应用主题模式 */
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,

    /** 应用主题预设：同时决定全局配色种子色与课表视觉样式 */
    val themePreset: AppThemePreset = AppThemePreset.default,

    /** 开发者功能总开关（默认关闭） */
    val developerModeEnabled: Boolean = false,

    /** 情侣课表开关：开启后主页面同时渲染本人课表 + crush 课表 */
    val coupleScheduleEnabled: Boolean = false,

    /** 情侣叠加时是否在课程卡上显示起止时间（默认关闭，时间文本会挤占课程卡空间） */
    val coupleShowTimeRanges: Boolean = false,

    /** 本人课表课程颜色索引（默认 5 = 蓝色） */
    val selfCourseColorIndex: Int = DEFAULT_SELF_COLOR_INDEX,

    /** crush 课表课程颜色索引（默认 1 = 粉色） */
    val crushCourseColorIndex: Int = DEFAULT_CRUSH_COLOR_INDEX,

    /** 周课表视图模式（默认周视图） */
    val scheduleViewMode: ScheduleViewMode = ScheduleViewMode.WEEK,

    /**
     * 液态玻璃（底栏胶囊 / 悬浮圆钮 / 挂起条 / 玻璃 AppFab）统一的高斯模糊半径，单位 dp。
     * v3.25.0 起由「外观与样式 → 个性化显示」调节；0f = 关闭模糊（只保留表面 tint 与边缘光学）。
     * 默认 4dp 对应原编译期常量 LiquidGlassBlurRadius；全局一处生效，不存在各件分叉。
     */
    val glassBlurRadiusDp: Float = 8f,

    /**
     * 液态玻璃**边缘折射**总开关（v3.47.0）。
     *
     * true ⇒ 底栏玻璃切到自带引擎：录一份背景快照 → 按 [glassBlurRadiusDp] 模糊 →
     * 在形状边缘做 SDF 折射（透镜）位移；false（默认）⇒ 沿用原有 Haze 模糊路径，
     * 观感与旧版逐像素一致。**模糊强度始终由 [glassBlurRadiusDp] 决定**，
     * 本开关只增加/移除折射这一光学层。
     */
    val glassRefractionEnabled: Boolean = false,

    /** 折射带宽度（dp）：从形状边缘向内多少距离内发生位移，默认 16。 */
    val glassRefractionHeightDp: Float = 16f,

    /** 折射位移量（dp）：边缘像素被拉向中心的距离，默认 24。 */
    val glassRefractionAmountDp: Float = 24f,

    /** 彩虹色散：在四个圆角处产生棱镜彩边（每像素 7 次采样），默认关闭。 */
    val glassRefractionDispersion: Boolean = false,

    /** 厚度感：把 SDF 梯度与径向梯度混合，使形状内部也有轻微汇聚，默认开启。 */
    val glassRefractionDepthEffect: Boolean = true,

    /**
     * 全局动画风格（v3.26.0「个性化显示 → 动画效果」）。
     * 三档：琉璃轻弹(GLASS，默认) / 舒缓轻移(GENTLE) / 灵动跟手(SNAPPY)。
     * 经 LocalAppMotion 注入，全 App 一处生效。
     */
    val animationStyle: AnimationStyle = AnimationStyle.GLASS,

    /**
     * 已关闭的动画分组集合（默认空 = 全部开启）。
     * 关掉某组 ⇒ 该类动画瞬切无动效。分组见 [AnimationGroup]。
     */
    val disabledAnimationGroups: Set<AnimationGroup> = emptySet(),

    /**
     * 「减弱动态效果」开关（v3.43.0 无障碍降级）。
     * true ⇒ 位移 / 缩放 / 错峰入场全部归零，只保留短促的不透明度溶解，
     * 对齐系统 Reduce Motion 语义（KMP 无统一系统 API，故由应用内开关驱动）。
     */
    val reduceMotionEnabled: Boolean = false,

    /**
     * 「动效速度」倍率（v3.44.0「动画效果」新增）。
     * **数值越大越快**：实际时长 = 基线时长 ÷ 倍率；1.0 = 基线原速，默认 STANDARD = 1.55。
     * 经 LocalAppMotion 注入，全 App 一处生效。
     */
    val motionSpeed: MotionSpeed = MotionSpeed.STANDARD,

    /**
     * 下节课卡在「今日课程结束后」的行为（v3.47.0「个性化显示 → 下节课卡」）。
     * - [NextCardMode.AUTO_NEXT]：自动显示下一次课程或日程（默认，卡片不再消失）
     * - [NextCardMode.TODAY_ENDED]：显示「今日课程已结束，自由探索吧」提示（变淡）
     * - [NextCardMode.HIDE]：课程结束后整卡消失（旧行为）
     */
    val nextCardMode: NextCardMode = NextCardMode.AUTO_NEXT,

    /**
     * 屏幕刷新率偏好（v3.56.0「动画效果 → 屏幕刷新率」）。
     * 默认 AUTO：不钉任何显示模式（preferredDisplayModeId 置 0），交还系统按场景自适应升降频；
     * 手动档（120/90/60）钉到目标档，机型不支持时向下回落。
     * 由 MainActivity 应用到 `preferredDisplayModeId`（比 preferredRefreshRate 提示更可靠）。
     */
    val refreshRateMode: RefreshRateMode = RefreshRateMode.AUTO,

    // --- 「我的」页个人信息（v3.49.0「我的信息」页）---
    /** 昵称；为空时界面回落到应用名（`app_name`）。 */
    val profileNickname: String = "",
    /** 学校名称；为空时「我的」页副标题回落到品牌语（`hero_subtitle`）。 */
    val profileSchool: String = "",
    /** 学院。 */
    val profileCollege: String = "",
    /** 专业。 */
    val profileMajor: String = "",
    /** 年级（如「2024 级」）。 */
    val profileGrade: String = "",
    /** 个性签名。 */
    val profileSignature: String = "",
    /** 头像图片在私有目录下的绝对路径；空串表示未设置（回落到首字母圆形头像）。 */
    val profileAvatarPath: String = "",
    /** 成绩页使用的绩点换算制式（4.0 制 / 5.0 制），默认 4.0 制。 */
    val gpaScale: GpaScale = GpaScale.SCALE_4,

    /**
     * 培养方案的类别学分要求（v4.66.0「学业情况」）。
     *
     * 只存「哪一类要求多少学分 + 可改写的显示名」，已获学分由成绩表现算。
     * 空列表 = 用户还没设置，学业情况页会引导先加一条。
     */
    val creditRequirements: List<CreditRequirement> = emptyList(),

    // --- AI 识别导入（v4.66.0 J1）---
    /**
     * AI 识别导入总开关（默认关闭）。
     *
     * 关闭时「AI 识别导入」页只展示开关与说明，**不发起任何网络请求**；
     * 既有本地导入路径（文本粘贴 / 文件 / Excel / 分享串）完全不受影响 ——
     * AI 只是新增一条可选路径，不是导入的必要条件。
     */
    val aiImportEnabled: Boolean = false,

    /**
     * 用户是否已确认过「数据外发说明」。
     *
     * 首次开启 [aiImportEnabled] 前必须确认一次：说清上传什么（截图或文字）、
     * 发给谁（用户自己填的接口）、不含什么（课表以外的本机数据一律不发）。
     */
    val aiImportNoticeAccepted: Boolean = false,

    /** OpenAI 兼容接口基地址（如 `https://api.example.com/v1`）；空串 = 未配置。 */
    val aiApiBaseUrl: String = "",

    /** 模型名（如 `gpt-4o-mini`）；空串 = 未配置。 */
    val aiApiModel: String = "",
    // API Key 刻意不在这里：它是「只在本机、不参与云备份」的密钥，
    // 落在已排除备份的 api_config 存储（ApiConfigRepository.ApiKeys.Secrets），
    // 读取入口是 AppSettingsRepository.getAiApiKey()（v4.67.16 之前的字段 aiApiKey 已移除）。
) {
    /**
     * 将 DataStore 的 Key 定义在伴生对象中。
     * 这样在 Repository 中可以直接通过 AppSettingsModel.KEY_xxx 访问，
     * 避免了修改一处逻辑需要动多个文件的问题。
     */
    companion object {
        // 情侣课表默认颜色索引（对应 ScheduleGridStyle.DEFAULT_COLOR_MAPS）
        const val DEFAULT_SELF_COLOR_INDEX = 5   // 蓝色
        const val DEFAULT_CRUSH_COLOR_INDEX = 1  // 粉色

        val KEY_CURRENT_COURSE_TABLE_ID = stringPreferencesKey("current_course_table_id")
        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_REMIND_BEFORE_MINUTES = intPreferencesKey("remind_before_minutes")
        val KEY_SKIPPED_DATES = stringSetPreferencesKey("skipped_dates")
        val KEY_AUTO_MODE_ENABLED = booleanPreferencesKey("auto_mode_enabled")
        val KEY_AUTO_CONTROL_MODE = stringPreferencesKey("auto_control_mode")
        val KEY_COMPAT_WEARABLE_SYNC = booleanPreferencesKey("compat_wearable_sync")
        val KEY_DYNAMIC_ISLAND_ENABLED = booleanPreferencesKey("dynamic_island_enabled")
        val KEY_NEXT_CLASS_NOTIFICATION_ENABLED = booleanPreferencesKey("next_class_notification_enabled")
        val KEY_EXAM_COUNTDOWN_REMINDER_ENABLED = booleanPreferencesKey("exam_countdown_reminder_enabled")
        val KEY_MORNING_ALARM_ENABLED = booleanPreferencesKey("morning_alarm_enabled")
        val KEY_MORNING_ALARM_LEAD_MINUTES = intPreferencesKey("morning_alarm_lead_minutes")
        val KEY_SHOW_NON_CURRENT_WEEK_COURSES = booleanPreferencesKey("show_non_current_week_courses")
        val KEY_START_SCREEN = stringPreferencesKey("start_screen")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_THEME_PRESET = stringPreferencesKey("theme_preset")
        val KEY_DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        val KEY_COUPLE_SCHEDULE_ENABLED = booleanPreferencesKey("couple_schedule_enabled")
        val KEY_COUPLE_SHOW_TIME_RANGES = booleanPreferencesKey("couple_show_time_ranges")
        val KEY_SELF_COURSE_COLOR_INDEX = intPreferencesKey("self_course_color_index")
        val KEY_CRUSH_COURSE_COLOR_INDEX = intPreferencesKey("crush_course_color_index")
        val KEY_SCHEDULE_VIEW_MODE = stringPreferencesKey("schedule_view_mode")
        val KEY_GLASS_BLUR_RADIUS_DP = floatPreferencesKey("glass_blur_radius_dp")
        val KEY_GLASS_REFRACTION_ENABLED = booleanPreferencesKey("glass_refraction_enabled")
        val KEY_GLASS_REFRACTION_HEIGHT_DP = floatPreferencesKey("glass_refraction_height_dp")
        val KEY_GLASS_REFRACTION_AMOUNT_DP = floatPreferencesKey("glass_refraction_amount_dp")
        val KEY_GLASS_REFRACTION_DISPERSION = booleanPreferencesKey("glass_refraction_dispersion")
        val KEY_GLASS_REFRACTION_DEPTH_EFFECT = booleanPreferencesKey("glass_refraction_depth_effect")
        val KEY_ANIMATION_STYLE = stringPreferencesKey("animation_style")
        val KEY_DISABLED_ANIMATION_GROUPS = stringSetPreferencesKey("disabled_animation_groups")
        val KEY_REDUCE_MOTION_ENABLED = booleanPreferencesKey("reduce_motion_enabled")
        val KEY_MOTION_SPEED = stringPreferencesKey("motion_speed")
        val KEY_NEXT_CARD_MODE = stringPreferencesKey("next_card_mode")
        val KEY_REFRESH_RATE_MODE = stringPreferencesKey("refresh_rate_mode")

        /**
         * 「GitHub Star 引导」是否已经展示过（K5，v4.66.0）。
         *
         * 语义：**一次性**——无论用户点了「去点个 Star」还是「不再提示」，都会置为 true，
         * 之后永不再自动弹出（不打扰是上课的既有定位）。常驻入口仍在「更多 → 联系作者」卡里。
         * 因此这是一个纯界面状态标记、不是用户数据，**刻意不参与备份**（重装后再提示一次无害）。
         */
        val KEY_STAR_PROMPT_SHOWN = booleanPreferencesKey("star_prompt_shown")

        // 「我的」页个人信息（v3.49.0）
        val KEY_PROFILE_NICKNAME = stringPreferencesKey("profile_nickname")
        val KEY_PROFILE_SCHOOL = stringPreferencesKey("profile_school")
        val KEY_PROFILE_COLLEGE = stringPreferencesKey("profile_college")
        val KEY_PROFILE_MAJOR = stringPreferencesKey("profile_major")
        val KEY_PROFILE_GRADE = stringPreferencesKey("profile_grade")
        val KEY_PROFILE_SIGNATURE = stringPreferencesKey("profile_signature")
        val KEY_PROFILE_AVATAR_PATH = stringPreferencesKey("profile_avatar_path")

        // 成绩 / GPA（v4.66.0）
        val KEY_GPA_SCALE = stringPreferencesKey("gpa_scale")

        // 学业情况：类别学分要求（v4.66.0），JSON 文本存 DataStore
        val KEY_CREDIT_REQUIREMENTS = stringPreferencesKey("credit_requirements_json")

        // 考证查分凭据（v4.66.0）：按模块 ID 隔离，仅存本机、不上传。
        // 键定义不在本存储：它们是「只在本机」的凭据，见 ApiConfigRepository.ApiKeys.Secrets
        // （v4.67.16 起迁到已被备份规则排除的 api_config 存储）。

        // 教务适配远程同步记录（v4.66.0）：适配状态页展示「上次检查」
        val KEY_ADAPTER_SYNC_AT = stringPreferencesKey("adapter_sync_at")
        val KEY_ADAPTER_SYNC_KIND = stringPreferencesKey("adapter_sync_kind")
        val KEY_ADAPTER_SYNC_COUNT = stringPreferencesKey("adapter_sync_count")

        // AI 识别导入（v4.66.0 J1）：默认关闭，配置项全部由用户自己填
        val KEY_AI_IMPORT_ENABLED = booleanPreferencesKey("ai_import_enabled")
        val KEY_AI_IMPORT_NOTICE_ACCEPTED = booleanPreferencesKey("ai_import_notice_accepted")
        val KEY_AI_API_BASE_URL = stringPreferencesKey("ai_api_base_url")
        val KEY_AI_API_MODEL = stringPreferencesKey("ai_api_model")

        // AI API Key 同样不在此存储：键定义见 ApiConfigRepository.ApiKeys.Secrets.AI_API_KEY
        // （v4.67.16 起迁到已被备份规则排除的 api_config 存储）。

        /**
         * 从 Preferences 中解析出 AppSettingsModel
         */
        fun fromPreferences(prefs: Preferences, fallbackTableId: String): AppSettingsModel {
            val d = AppSettingsModel() // 默认值模板
            return AppSettingsModel(
                currentCourseTableId = prefs[KEY_CURRENT_COURSE_TABLE_ID] ?: fallbackTableId.ifEmpty { d.currentCourseTableId },
                reminderEnabled = prefs[KEY_REMINDER_ENABLED] ?: d.reminderEnabled,
                remindBeforeMinutes = prefs[KEY_REMIND_BEFORE_MINUTES] ?: d.remindBeforeMinutes,
                skippedDates = prefs[KEY_SKIPPED_DATES] ?: d.skippedDates,
                autoModeEnabled = prefs[KEY_AUTO_MODE_ENABLED] ?: d.autoModeEnabled,
                autoControlMode = AutoControlMode.fromString(prefs[KEY_AUTO_CONTROL_MODE]),
                compatWearableSync = prefs[KEY_COMPAT_WEARABLE_SYNC] ?: d.compatWearableSync,
                dynamicIslandEnabled = prefs[KEY_DYNAMIC_ISLAND_ENABLED] ?: d.dynamicIslandEnabled,
                nextClassNotificationEnabled = prefs[KEY_NEXT_CLASS_NOTIFICATION_ENABLED] ?: d.nextClassNotificationEnabled,
                examCountdownReminderEnabled = prefs[KEY_EXAM_COUNTDOWN_REMINDER_ENABLED] ?: d.examCountdownReminderEnabled,
                starPromptShown = prefs[KEY_STAR_PROMPT_SHOWN] ?: d.starPromptShown,
                morningAlarmEnabled = prefs[KEY_MORNING_ALARM_ENABLED] ?: d.morningAlarmEnabled,
                morningAlarmLeadMinutes = prefs[KEY_MORNING_ALARM_LEAD_MINUTES] ?: d.morningAlarmLeadMinutes,
                showNonCurrentWeekCourses = prefs[KEY_SHOW_NON_CURRENT_WEEK_COURSES] ?: d.showNonCurrentWeekCourses,
                startScreen = prefs[KEY_START_SCREEN]?.let { StartScreen.fromString(it) } ?: d.startScreen,
                themeMode = prefs[KEY_THEME_MODE]?.let { AppThemeMode.fromString(it) } ?: d.themeMode,
                themePreset = AppThemePreset.fromString(prefs[KEY_THEME_PRESET]),
                developerModeEnabled = prefs[KEY_DEVELOPER_MODE_ENABLED] ?: d.developerModeEnabled,
                coupleScheduleEnabled = prefs[KEY_COUPLE_SCHEDULE_ENABLED] ?: d.coupleScheduleEnabled,
                coupleShowTimeRanges = prefs[KEY_COUPLE_SHOW_TIME_RANGES] ?: d.coupleShowTimeRanges,
                selfCourseColorIndex = prefs[KEY_SELF_COURSE_COLOR_INDEX] ?: d.selfCourseColorIndex,
                crushCourseColorIndex = prefs[KEY_CRUSH_COURSE_COLOR_INDEX] ?: d.crushCourseColorIndex,
                scheduleViewMode = ScheduleViewMode.fromString(prefs[KEY_SCHEDULE_VIEW_MODE]),
                glassBlurRadiusDp = prefs[KEY_GLASS_BLUR_RADIUS_DP] ?: d.glassBlurRadiusDp,
                glassRefractionEnabled = prefs[KEY_GLASS_REFRACTION_ENABLED] ?: d.glassRefractionEnabled,
                glassRefractionHeightDp = prefs[KEY_GLASS_REFRACTION_HEIGHT_DP] ?: d.glassRefractionHeightDp,
                glassRefractionAmountDp = prefs[KEY_GLASS_REFRACTION_AMOUNT_DP] ?: d.glassRefractionAmountDp,
                glassRefractionDispersion = prefs[KEY_GLASS_REFRACTION_DISPERSION] ?: d.glassRefractionDispersion,
                glassRefractionDepthEffect = prefs[KEY_GLASS_REFRACTION_DEPTH_EFFECT] ?: d.glassRefractionDepthEffect,
                animationStyle = AnimationStyle.fromString(prefs[KEY_ANIMATION_STYLE]),
                disabledAnimationGroups = prefs[KEY_DISABLED_ANIMATION_GROUPS]
                    ?.mapNotNull { AnimationGroup.fromString(it) }
                    ?.toSet()
                    ?: d.disabledAnimationGroups,
                reduceMotionEnabled = prefs[KEY_REDUCE_MOTION_ENABLED] ?: d.reduceMotionEnabled,
                motionSpeed = MotionSpeed.fromString(prefs[KEY_MOTION_SPEED]),
                nextCardMode = NextCardMode.fromString(prefs[KEY_NEXT_CARD_MODE]),
                refreshRateMode = RefreshRateMode.fromString(prefs[KEY_REFRESH_RATE_MODE]),
                profileNickname = prefs[KEY_PROFILE_NICKNAME] ?: d.profileNickname,
                profileSchool = prefs[KEY_PROFILE_SCHOOL] ?: d.profileSchool,
                profileCollege = prefs[KEY_PROFILE_COLLEGE] ?: d.profileCollege,
                profileMajor = prefs[KEY_PROFILE_MAJOR] ?: d.profileMajor,
                profileGrade = prefs[KEY_PROFILE_GRADE] ?: d.profileGrade,
                profileSignature = prefs[KEY_PROFILE_SIGNATURE] ?: d.profileSignature,
                profileAvatarPath = prefs[KEY_PROFILE_AVATAR_PATH] ?: d.profileAvatarPath,
                gpaScale = GpaScale.fromString(prefs[KEY_GPA_SCALE]),
                creditRequirements = CreditRequirement.decode(prefs[KEY_CREDIT_REQUIREMENTS]),
                aiImportEnabled = prefs[KEY_AI_IMPORT_ENABLED] ?: d.aiImportEnabled,
                aiImportNoticeAccepted = prefs[KEY_AI_IMPORT_NOTICE_ACCEPTED] ?: d.aiImportNoticeAccepted,
                aiApiBaseUrl = prefs[KEY_AI_API_BASE_URL] ?: d.aiApiBaseUrl,
                aiApiModel = prefs[KEY_AI_API_MODEL] ?: d.aiApiModel,
            )
        }
    }
}

/**
 * 下节课卡在「今日课程结束后」的行为（v3.47.0）。
 *
 * 卡片内容：下节课/下一次标识 · 倒计时（分钟）· 标题 · 时间区间 → 点击开详情。
 */
enum class NextCardMode(val value: String) {
    /** 自动显示下一次课程或日程（默认）——卡片不再随今日课程结束而消失。 */
    AUTO_NEXT("AUTO_NEXT"),

    /** 显示「今日课程已结束，自由探索吧」提示（变淡）。 */
    TODAY_ENDED("TODAY_ENDED"),

    /** 课程结束后整卡消失（旧行为）。 */
    HIDE("HIDE");

    companion object {
        fun fromString(value: String?): NextCardMode =
            entries.find { it.value == value } ?: AUTO_NEXT
    }
}

/**
 * 屏幕刷新率偏好（v3.56.0「动画效果 → 屏幕刷新率」）。
 * 三档手动（120/90/60）+ AUTO 交还系统自适应：手动档按机型实际支持的刷新率档位，
 * 取「不超过目标档」中最接近的一档（如 120 档在 90Hz 机型上回落 90，60 档恒可满足）。
 * AUTO = 不钉任何显示模式（preferredDisplayModeId 置 0），由系统按场景自行升降频。
 */
enum class RefreshRateMode(val value: String, val labelRes: StringResource) {
    /** 交还系统自适应：解除窗口对显示模式的钉定，静止/滚动由系统自行升降频（省电）。 */
    AUTO("auto", Res.string.refresh_rate_auto),

    /** 优先 120Hz；机型不支持时回落到 ≤120 的最高可用档。 */
    HZ_120("120", Res.string.refresh_rate_120),

    /** 优先 90Hz；机型不支持时回落到 ≤90 的最高可用档。 */
    HZ_90("90", Res.string.refresh_rate_90),

    /** 固定 60Hz（省电优先）。 */
    HZ_60("60", Res.string.refresh_rate_60);

    companion object {
        fun fromString(value: String?): RefreshRateMode =
            entries.find { it.value == value } ?: AUTO
    }
}

/**
 * 绩点换算制式（v4.66.0 成绩 / GPA 功能）。
 *
 * 国内高校没有统一公式，这里提供两种最常见的「分段法」，由用户在成绩页自行切换：
 * - 4.0 制：≥90→4.0、85–89→3.7、82–84→3.3、78–81→3.0、75–77→2.7、
 *   72–74→2.3、68–71→2.0、64–67→1.5、60–63→1.0、<60→0；
 * - 5.0 制：≥90→5.0、80–89→4.0、70–79→3.0、60–69→2.0、<60→0。
 *
 * 换算规则同样适用于等级制成绩：优秀 / 良好 / 中等 / 及格 分别取该制式的高分段。
 * 「通过 / 不通过」不参与绩点计算（只保留成绩记录，不拉低或抬高 GPA），
 * 具体实现见 `GradeRepository.pointOf`。切换制式不会改写数据库里的分数，
 * 绩点始终由原始分数实时换算（成绩表刻意不落库绩点列）。
 */
enum class GpaScale(val value: String, val labelRes: StringResource) {
    /** 4.0 制（默认，国内最常见）。 */
    SCALE_4("4.0", Res.string.gpa_scale_4),

    /** 5.0 制。 */
    SCALE_5("5.0", Res.string.gpa_scale_5);

    companion object {
        fun fromString(value: String?): GpaScale =
            entries.find { it.value == value } ?: SCALE_4
    }
}