package com.shangkeschedule.data.model

import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.jetbrains.compose.resources.StringResource
import shangkeschedule.shared.generated.resources.*
import com.shangkeschedule.ui.schedule.ScheduleViewMode
import com.shangkeschedule.ui.theme.MotionSpeed
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.AnimationStyle
import com.shangkeschedule.ui.theme.DefaultThemeColor

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

    /** 是否显示非本周课程 */
    val showNonCurrentWeekCourses: Boolean = false,

    /** 应用启动时显示的页面 */
    val startScreen: StartScreen = StartScreen.COURSE_SCHEDULE,

    /** 应用主题模式 */
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,

    /** 应用主题预设：同时决定全局配色种子色与课表视觉样式 */
    val themePreset: AppThemePreset = AppThemePreset.default,

    /** 是否开启动态取色 (Material You) */
    val useDynamicColor: Boolean = false,

    /** 自定义浅色主题主色 */
    val customLightPrimary: Long = DefaultThemeColor.toArgb().toLong(),

    /** 自定义深色主题主色 */
    val customDarkPrimary: Long = DefaultThemeColor.toArgb().toLong(),

    /** 开发者功能总开关（默认关闭） */
    val developerModeEnabled: Boolean = false,

    /** 情侣课表开关：开启后主页面同时渲染本人课表 + crush 课表 */
    val coupleScheduleEnabled: Boolean = false,

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
    val glassBlurRadiusDp: Float = 4f,

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
        val KEY_SHOW_NON_CURRENT_WEEK_COURSES = booleanPreferencesKey("show_non_current_week_courses")
        val KEY_START_SCREEN = stringPreferencesKey("start_screen")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_THEME_PRESET = stringPreferencesKey("theme_preset")
        val KEY_USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val KEY_CUSTOM_LIGHT_PRIMARY = longPreferencesKey("custom_light_primary")
        val KEY_CUSTOM_DARK_PRIMARY = longPreferencesKey("custom_dark_primary")
        val KEY_DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        val KEY_COUPLE_SCHEDULE_ENABLED = booleanPreferencesKey("couple_schedule_enabled")
        val KEY_SELF_COURSE_COLOR_INDEX = intPreferencesKey("self_course_color_index")
        val KEY_CRUSH_COURSE_COLOR_INDEX = intPreferencesKey("crush_course_color_index")
        val KEY_SCHEDULE_VIEW_MODE = stringPreferencesKey("schedule_view_mode")
        val KEY_GLASS_BLUR_RADIUS_DP = floatPreferencesKey("glass_blur_radius_dp")
        val KEY_ANIMATION_STYLE = stringPreferencesKey("animation_style")
        val KEY_DISABLED_ANIMATION_GROUPS = stringSetPreferencesKey("disabled_animation_groups")
        val KEY_REDUCE_MOTION_ENABLED = booleanPreferencesKey("reduce_motion_enabled")
        val KEY_MOTION_SPEED = stringPreferencesKey("motion_speed")

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
                showNonCurrentWeekCourses = prefs[KEY_SHOW_NON_CURRENT_WEEK_COURSES] ?: d.showNonCurrentWeekCourses,
                startScreen = prefs[KEY_START_SCREEN]?.let { StartScreen.fromString(it) } ?: d.startScreen,
                themeMode = prefs[KEY_THEME_MODE]?.let { AppThemeMode.fromString(it) } ?: d.themeMode,
                themePreset = AppThemePreset.fromString(prefs[KEY_THEME_PRESET]),
                useDynamicColor = prefs[KEY_USE_DYNAMIC_COLOR] ?: d.useDynamicColor,
                customLightPrimary = prefs[KEY_CUSTOM_LIGHT_PRIMARY] ?: d.customLightPrimary,
                customDarkPrimary = prefs[KEY_CUSTOM_DARK_PRIMARY] ?: d.customDarkPrimary,
                developerModeEnabled = prefs[KEY_DEVELOPER_MODE_ENABLED] ?: d.developerModeEnabled,
                coupleScheduleEnabled = prefs[KEY_COUPLE_SCHEDULE_ENABLED] ?: d.coupleScheduleEnabled,
                selfCourseColorIndex = prefs[KEY_SELF_COURSE_COLOR_INDEX] ?: d.selfCourseColorIndex,
                crushCourseColorIndex = prefs[KEY_CRUSH_COURSE_COLOR_INDEX] ?: d.crushCourseColorIndex,
                scheduleViewMode = ScheduleViewMode.fromString(prefs[KEY_SCHEDULE_VIEW_MODE]),
                glassBlurRadiusDp = prefs[KEY_GLASS_BLUR_RADIUS_DP] ?: d.glassBlurRadiusDp,
                animationStyle = AnimationStyle.fromString(prefs[KEY_ANIMATION_STYLE]),
                disabledAnimationGroups = prefs[KEY_DISABLED_ANIMATION_GROUPS]
                    ?.mapNotNull { AnimationGroup.fromString(it) }
                    ?.toSet()
                    ?: d.disabledAnimationGroups,
                reduceMotionEnabled = prefs[KEY_REDUCE_MOTION_ENABLED] ?: d.reduceMotionEnabled,
                motionSpeed = MotionSpeed.fromString(prefs[KEY_MOTION_SPEED]),
            )
        }
    }
}