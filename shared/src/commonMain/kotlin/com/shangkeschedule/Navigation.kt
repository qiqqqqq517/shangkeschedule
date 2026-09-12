package com.shangkeschedule

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.reflect.KClass

/**
 * 导航元数据 Key 定义
 */
object ShangKeNavMetadata {
    object IsMainScreenKey : NavMetadataKey<Boolean>
}

/**
 * 应用所有目的地（页面）的定义
 */
@Serializable
sealed interface Destination : NavKey {

    /** 标记一级主界面的子接口 */
    sealed interface MainDestination : Destination

    // --- 一级导航页面（底栏对应页面） ---
    @Serializable data object CourseSchedule : MainDestination
    @Serializable data object Settings : MainDestination
    @Serializable data object TodaySchedule : MainDestination
    /** 「日程」页：月历 + 当日课程/自建日程时间轴 */
    @Serializable data object Schedule : MainDestination

    // --- 二级功能页面 ---
    @Serializable data object TimeSlotSettings : Destination
    @Serializable data object ManageCourseTables : Destination
    @Serializable
    data class SchoolSelectionListScreen(
        val isCrushImport: Boolean = false
    ) : Destination
    @Serializable data object CourseTableConversion : Destination
    @Serializable data object NotificationSettings : Destination
    @Serializable data object SemesterSettings : Destination
    @Serializable data object CoupleScheduleSettings : Destination
    @Serializable data object MoreOptions : Destination
    @Serializable data object OpenSourceLicenses : Destination
    @Serializable data object TweakSchedule : Destination
    @Serializable data object QuickDelete : Destination
    @Serializable data object CourseManagementList : Destination
    @Serializable data object AppearanceSettings : Destination
    /** 外观与样式二级页：主题（主题风格/深色模式/动态取色/自定义主色） */
    @Serializable data object ThemeSettings : Destination
    /**
     * 「我的」页二级页：我的信息（头像 / 昵称 / 学校 / 学院 / 专业 / 年级 / 个性签名）。
     * v3.49.0：原「我的」页顶部身份卡直接跳「外观与样式」，改为跳本页。
     */
    @Serializable data object ProfileInfo : Destination
    /** 外观与样式二级页：自定义课表页（壁纸/样式预览/功能色） */
    @Serializable data object ScheduleStyleSettings : Destination
    /** 外观与样式二级页：个性化显示（v3.25.0 起为两卡 hub：玻璃模糊 / 动画效果） */
    @Serializable data object PersonalizedDisplay : Destination
    /** 个性化显示三级页：玻璃模糊强度（v3.26.0 自 hub 拆出） */
    @Serializable data object GlassBlurSettings : Destination
    /** 个性化显示三级页：动画效果（三风格 + 六分组开关，v3.26.0 新增） */
    @Serializable data object AnimationSettings : Destination
    /** 个性化显示三级页：课程配色（颜色池 + 课程块/页面文字颜色 + 一键重置，自「自定义课表页」整块迁入） */
    @Serializable data object CourseColorSettings : Destination
    /** 个性化显示三级页：下节课卡（今日课程结束后的行为，v3.47.0 新增） */
    @Serializable data object NextCardSettings : Destination
    @Serializable data object BackupAndRestore : Destination
    @Serializable data object LanguageSettings : Destination

    // --- 导入分类二级页 ---
    @Serializable data object FileImportHub : Destination
    @Serializable data object ExcelImport : Destination
    @Serializable data object JsonFileImport : Destination
    /** 文本类文件导入；format 传 TextImportFormat 名称，空/AUTO = 自动识别（CSV/ICS/HTML/TXT/JSON） */
    @Serializable data class TextFileImport(val format: String = "AUTO") : Destination
    @Serializable data object TextImportHub : Destination
    @Serializable data class TextImportFormatPage(val format: String) : Destination

    // --- 动态传参页面 ---
    @Serializable
    data class AdapterSelection(
        val schoolId: String,
        val schoolName: String,
        val categoryNumber: Int,
        val resourceFolder: String,
        val isCrushImport: Boolean = false
    ) : Destination

    @Serializable
    data class WebView(
        val initialUrl: String? = "about:blank",
        val assetJsPath: String? = null,
        val isCrushImport: Boolean = false,
        val forceDesktopMode: Boolean = false
    ) : Destination

    @Serializable
    data class AddEditCourse(
        val courseId: String? = null
    ) : Destination

    @Serializable
    data class CourseManagementDetail(
        val courseName: String
    ) : Destination
}

val Destination.isMainScreen: Boolean
    get() = this is Destination.MainDestination

/**
 * 配置并生成包含所有 Destination 派生类的 SerializersModule
 */
val navSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        // 一级主界面
        subclass(Destination.CourseSchedule::class)
        subclass(Destination.Settings::class)
        subclass(Destination.TodaySchedule::class)
        subclass(Destination.Schedule::class)

        // 普通功能页面
        subclass(Destination.TimeSlotSettings::class)
        subclass(Destination.ManageCourseTables::class)
        subclass(Destination.SchoolSelectionListScreen::class)
        subclass(Destination.CourseTableConversion::class)
        subclass(Destination.NotificationSettings::class)
        subclass(Destination.SemesterSettings::class)
        subclass(Destination.CoupleScheduleSettings::class)
        subclass(Destination.MoreOptions::class)
        subclass(Destination.OpenSourceLicenses::class)
        subclass(Destination.TweakSchedule::class)
        subclass(Destination.QuickDelete::class)
        subclass(Destination.CourseManagementList::class)
        subclass(Destination.AppearanceSettings::class)
        subclass(Destination.ThemeSettings::class)
        subclass(Destination.ProfileInfo::class)
        subclass(Destination.ScheduleStyleSettings::class)
        subclass(Destination.PersonalizedDisplay::class)
        subclass(Destination.GlassBlurSettings::class)
        subclass(Destination.AnimationSettings::class)
        subclass(Destination.CourseColorSettings::class)
        subclass(Destination.NextCardSettings::class)
        subclass(Destination.BackupAndRestore::class)
        subclass(Destination.LanguageSettings::class)

        // 导入分类二级页
        subclass(Destination.FileImportHub::class)
        subclass(Destination.ExcelImport::class)
        subclass(Destination.JsonFileImport::class)
        subclass(Destination.TextFileImport::class)
        subclass(Destination.TextImportHub::class)
        subclass(Destination.TextImportFormatPage::class)

        // 带参数据类
        subclass(Destination.AdapterSelection::class)
        subclass(Destination.WebView::class)
        subclass(Destination.AddEditCourse::class)
        subclass(Destination.CourseManagementDetail::class)
    }
}

/**
 * 使用官方 DSL 构建器创建 SavedStateConfiguration
 */
val navSavedStateConfig = SavedStateConfiguration {
    serializersModule = navSerializersModule
}