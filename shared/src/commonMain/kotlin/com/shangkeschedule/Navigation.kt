package com.shangkeschedule

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

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
    @Serializable
    data class TimeSlotSettings(
        /** 显式指定编辑的课表（情侣课表设置页跳转时传情侣课表 ID）；null = 当前选中课表。 */
        val targetCourseTableId: String? = null
    ) : Destination
    @Serializable data object ManageCourseTables : Destination
    /**
     * 教务系统学校列表（选学校 → 选适配方案 → 打开教务系统内嵌页）。
     *
     * [purpose] 决定内嵌页注入哪段脚本、抓取结果如何回写：
     * COURSE = 导入课表（默认，走适配仓库的适配脚本）；GRADE = 抓取成绩（走内置通用成绩识别脚本）。
     */
    @Serializable data class SchoolSelectionListScreen(
        val purpose: String = WebPagePurpose.COURSE
    ) : Destination
    @Serializable data object CourseTableConversion : Destination
    @Serializable data object NotificationSettings : Destination
    @Serializable data object SemesterSettings : Destination
    @Serializable data object CoupleScheduleSettings : Destination

    /**
     * 找共同空闲页（v4.66.0 · D2）。
     *
     * 复用既有情侣课表（本机两张普通课表，`CourseTable.isCouple` + `pairedCourseTableId`），
     * 按周次合并当天占用求空档；不涉及账号/协作体系，也不需要对方安装应用。
     */
    @Serializable data object CoupleFreeTime : Destination
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
    /** 意见反馈页（v4.65.0）：填写后经邮件 / GitHub Issue / 剪贴板三种出口提交，不采集任何标识符。 */
    @Serializable data object Feedback : Destination
    /** 法律文档页（v4.65.0）：type 传 LegalDocumentType 名称（PRIVACY / TERMS）。 */
    @Serializable data class LegalDocument(val type: String) : Destination
    /** 成绩与绩点页（v4.66.0）：汇总 GPA / 平均分 / 学分，支持手动录入、粘贴解析导入与教务抓取导入。 */
    @Serializable data object Grade : Destination
    /** 考证查分入口页（v4.66.0）：四六级 / 计算机等级 / 教师资格 / 普通话，直达官网查分并按需保存凭据。 */
    @Serializable data object CertExam : Destination
    /**
     * 教务适配状态页（v4.66.0）：只展示本机真实数据 —— 本地适配脚本数、学校索引学校数、
     * 已选学校各自命中的适配器、最近一次远程同步结果，并给出适配申请入口。
     * 不伪造「已支持 / 适配中」进度：适配仓库清单（index.json）目前没有学校维度的状态字段。
     */
    @Serializable data object AdapterStatus : Destination
    /**
     * 学业情况页（v4.66.0）：按课程类别汇总的培养方案学分完成度。
     * 类别要求由用户自己填写（各校各专业培养方案不同），已获学分只统计及格课程；
     * 数据全部来自本机成绩表，不联网。
     */
    @Serializable data object StudyProgress : Destination
    /**
     * 课堂笔记页（v4.66.0）：按课次记录听课内容，可附板书照片。
     * 笔记挂在课程下（外键 CASCADE），删除课程时随课一起删除。
     */
    @Serializable
    data class CourseNote(
        val courseId: String,
        val courseName: String,
        val sectionLabel: String = ""
    ) : Destination

    /**
     * 课表分享页（v4.66.0）：把一张课表压成自包含分享串（`SK1:` 前缀 + CRC 校验）
     * 与二维码，对方复制粘贴或扫码即可导入；不依赖网盘/文件传输。
     */
    @Serializable data object CourseShare : Destination

    /**
     * 主题分享页（v4.66.0，D3）：把当前主题预设、深浅色模式与整份课表样式压成一条
     * `SKT1:` 分享串（CRC 校验 + 二维码），对方粘贴即可得到同样的外观。
     * 载荷只含外观，不含课程、成绩与账号信息；应用端保留本机壁纸路径与时间轴模式。
     */
    @Serializable data object ThemeShare : Destination

    /**
     * 小组件排障页（v4.66.0，K7）：只在开发者模式下可见。
     * 用于排查桌面小组件不刷新：看已放置数量与快照概况、重建快照并请求重绘、跳系统设置放开后台限制。
     */
    @Serializable data object WidgetTroubleshoot : Destination

    // --- 导入分类二级页 ---
    @Serializable data object FileImportHub : Destination
    @Serializable data object ExcelImport : Destination
    @Serializable data object JsonFileImport : Destination
    /** 文本类文件导入；format 传 TextImportFormat 名称，空/AUTO = 自动识别（CSV/ICS/HTML/TXT/JSON） */
    @Serializable data class TextFileImport(val format: String = "AUTO") : Destination
    @Serializable data object TextImportHub : Destination
    @Serializable data class TextImportFormatPage(val format: String) : Destination

    /**
     * 外部选中文本导入页（v4.66.0 · L1）。
     *
     * 由系统文本选择工具栏的「上课」触发（Android `ACTION_PROCESS_TEXT`）：选中文本
     * 经 `com.shangkeschedule.tool.ExternalTextImport` 一次性交接，导航层推到栈顶后
     * 由 `TextImportScreen` 预填并清空。页面本体与文本粘贴导入页一致（自动嗅探格式）。
     */
    @Serializable data object ShareTextImport : Destination

    /**
     * 「AI 识别导入」页（v4.66.0，J1）。
     *
     * 把课表截图或文字交给用户自己填写的接口（OpenAI 兼容 `/chat/completions`）识别成纯文本，
     * 再走与「文本粘贴导入」完全相同的预览与导入链路；默认关闭，首次开启前有数据外发明示。
     */
    @Serializable data object AiImport : Destination

    // --- 动态传参页面 ---
    @Serializable
    data class AdapterSelection(
        val schoolId: String,
        val schoolName: String,
        val categoryNumber: Int,
        val resourceFolder: String,
        /** 用途，原样透传给 [WebView.mode]，保证「导入课表」与「抓取成绩」走各自的脚本。 */
        val purpose: String = WebPagePurpose.COURSE
    ) : Destination

    @Serializable
    data class WebView(
        val initialUrl: String? = "about:blank",
        val assetJsPath: String? = null,
        val forceDesktopMode: Boolean = false,
        /**
         * 内嵌页用途：
         * - [WebPagePurpose.COURSE]（默认）：注入适配仓库里该学校的适配脚本，抓取课表；
         * - [WebPagePurpose.GRADE]：注入应用内置的通用成绩表格识别脚本，抓取成绩；
         * - [WebPagePurpose.EMPTY_CLASSROOM]：注入应用内置的通用空教室表格识别脚本，
         *   定位并读取教务页里的空教室查询结果。
         */
        val mode: String = WebPagePurpose.COURSE
    ) : Destination

    @Serializable
    data class AddEditCourse(
        val courseId: String? = null,
        /** 显式指定编辑目标课表（叠加模式下编辑情侣课程时传其所在情侣课表 ID）。 */
        val targetCourseTableId: String? = null
    ) : Destination

    @Serializable
    data class CourseManagementDetail(
        val courseName: String
    ) : Destination
}

val Destination.isMainScreen: Boolean
    get() = this is Destination.MainDestination

/**
 * 内嵌教务页面的用途（v4.66.0）。
 *
 * 课表导入依赖适配仓库里每个学校各自的适配脚本；成绩抓取则用应用内置的通用
 * 成绩表格识别脚本（学校页面结构千差万别，但成绩页基本都是一张 `table`）。
 * 两者共用同一套 WebView 容器与登录态，靠这个标记区分注入内容与回写目标。
 */
object WebPagePurpose {
    /** 导入课表（原有行为，默认值）。 */
    const val COURSE = "COURSE"

    /** 抓取成绩。 */
    const val GRADE = "GRADE"

    /** 考证查分等「纯浏览器」用途：不显示课表导入 / 成绩识别按钮。 */
    const val CERT = "CERT"

    /**
     * 空教室查询（v4.66.0）：底部显示「定位空教室页 / 读取本页空教室」，
     * 由应用内置的通用脚本解析教务页里的空教室结果表，识别结果可一键复制。
     */
    const val EMPTY_CLASSROOM = "EMPTY_CLASSROOM"
}

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
        subclass(Destination.CoupleFreeTime::class)
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
        subclass(Destination.Feedback::class)
        subclass(Destination.LegalDocument::class)
        subclass(Destination.Grade::class)
        subclass(Destination.CertExam::class)
        subclass(Destination.AdapterStatus::class)
        subclass(Destination.StudyProgress::class)
        subclass(Destination.CourseNote::class)
        subclass(Destination.CourseShare::class)
        subclass(Destination.ThemeShare::class)
subclass(Destination.WidgetTroubleshoot::class)

        // 导入分类二级页
        subclass(Destination.FileImportHub::class)
        subclass(Destination.ExcelImport::class)
        subclass(Destination.JsonFileImport::class)
        subclass(Destination.TextFileImport::class)
        subclass(Destination.TextImportHub::class)
        subclass(Destination.TextImportFormatPage::class)
        subclass(Destination.ShareTextImport::class)
        subclass(Destination.AiImport::class)

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
