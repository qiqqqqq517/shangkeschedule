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
    @Serializable data object QuickActions : Destination
    @Serializable data object TweakSchedule : Destination
    @Serializable data object QuickDelete : Destination
    @Serializable data object CourseManagementList : Destination
    @Serializable data object AppearanceSettings : Destination
    @Serializable data object BackupAndRestore : Destination
    @Serializable data object LanguageSettings : Destination
    @Serializable data object TextImport : Destination

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
        val isCrushImport: Boolean = false
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
        subclass(Destination.QuickActions::class)
        subclass(Destination.TweakSchedule::class)
        subclass(Destination.QuickDelete::class)
        subclass(Destination.CourseManagementList::class)
        subclass(Destination.AppearanceSettings::class)
        subclass(Destination.BackupAndRestore::class)
        subclass(Destination.LanguageSettings::class)
        subclass(Destination.TextImport::class)

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