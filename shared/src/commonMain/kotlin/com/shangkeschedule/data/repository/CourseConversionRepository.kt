package com.shangkeschedule.data.repository

import androidx.room3.Transaction
import com.shangkeschedule.data.db.main.Course
import com.shangkeschedule.data.db.main.CourseDao
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseWeek
import com.shangkeschedule.data.db.main.CourseWeekDao
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.main.TimeSlotDao
import com.shangkeschedule.data.model.CourseImportExport.CourseConfigJsonModel
import com.shangkeschedule.data.model.CourseImportExport.CourseTableExportModel
import com.shangkeschedule.data.model.CourseImportExport.CourseTableImportModel
import com.shangkeschedule.data.model.CourseImportExport.ExportCourseJsonModel
import com.shangkeschedule.data.model.CourseImportExport.ImportCourseJsonModel
import com.shangkeschedule.data.model.CourseImportExport.TimeSlotJsonModel
import com.shangkeschedule.tool.CalendarAccountManager
import com.shangkeschedule.tool.IcsExportTool
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.koin.core.annotation.Single
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 课表转换仓库，负责处理课程数据的导入、导出以及 ICS 生成等逻辑。
 */
@OptIn(ExperimentalUuidApi::class)
@Single
class CourseConversionRepository(
    private val courseDao: CourseDao,
    private val courseWeekDao: CourseWeekDao,
    private val timeSlotDao: TimeSlotDao,
    private val appSettingsRepository: AppSettingsRepository,
    private val styleSettingsRepository: StyleSettingsRepository,
    private val timeSlotRepository: TimeSlotRepository
) {
    private val timeRegex = Regex("^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$")

    // 用于从备注文本中识别课程属性（学分 / 考核方式 / 实验课）
    // 同时支持中英文 Key：中文"学分"以及教务系统常见的英文 Key（Credit / Score / Assessment / Exam / Lab 等）
    private val creditRegex = Regex("""(?<![A-Za-z])(?:学分|Credit|credits?|Score|points?|XF|xf|kcxf)(?![A-Za-z])\s*[:：=]?\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
    private val creditSuffixRegex = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(?:学分|credit)""", RegexOption.IGNORE_CASE)
    private val assessmentRegex = Regex("""(?<![A-Za-z])(?:考核(?:方式)?|Assessment|ExamType|ExamMethod|ksfs|KSFS)(?![A-Za-z])\s*[:：=]?\s*([^\s,，;；、\n\r]+)""", RegexOption.IGNORE_CASE)

    /**
     * 从备注文本中提取学分、考核方式、实验课等结构化信息。
     * 支持类似 "学分：4"、"4学分"、"考核方式：考试"、"实验课" 等常见格式。
     */
    private fun extractCourseAttributes(remark: String?): ExtractedCourseAttributes {
        if (remark.isNullOrBlank()) return ExtractedCourseAttributes()

        val credit = creditRegex.find(remark)?.groupValues?.get(1)
            ?: creditSuffixRegex.find(remark)?.groupValues?.get(1)

        val assessmentMethod = assessmentRegex.find(remark)?.groupValues?.get(1)

        val isLab = remark.contains("实验课") || remark.contains("实验")
            || remark.contains("Lab", ignoreCase = true)
            || remark.contains("Experiment", ignoreCase = true)

        return ExtractedCourseAttributes(credit, assessmentMethod, isLab)
    }

    private fun parseToMinutes(timeStr: String): Int {
        val time = LocalTime.parse(timeStr)
        return time.hour * 60 + time.minute
    }

    private fun validateTimeSlotsOrThrow(timeSlots: List<TimeSlotJsonModel>) {
        if (timeSlots.isEmpty()) return

        val sortedSlots = timeSlots.sortedBy { it.number }
        var lastEndTimeInMinutes = -1

        sortedSlots.forEachIndexed { index, slot ->
            val expectedNumber = index + 1
            if (slot.number != expectedNumber) {
                throw IllegalArgumentException("时间段编号不连续或未从1开始")
            }

            if (!timeRegex.matches(slot.startTime) || !timeRegex.matches(slot.endTime)) {
                throw IllegalArgumentException("时间格式错误")
            }

            val startMinutes = parseToMinutes(slot.startTime)
            val endMinutes = parseToMinutes(slot.endTime)

            if (startMinutes >= endMinutes) {
                throw IllegalArgumentException("开始时间必须早于结束时间")
            }

            if (lastEndTimeInMinutes != -1 && startMinutes < lastEndTimeInMinutes) {
                throw IllegalArgumentException("时间段配置存在重叠")
            }

            lastEndTimeInMinutes = endMinutes
        }
    }

    private fun validateCustomCourseTimeOrThrow(course: ImportCourseJsonModel) {
        if (!course.isCustomTime) return

        val startTime = course.customStartTime
        val endTime = course.customEndTime

        if (startTime.isNullOrBlank() || endTime.isNullOrBlank()) {
            throw IllegalArgumentException("自定义时间不能为空")
        }

        if (!timeRegex.matches(startTime) || !timeRegex.matches(endTime)) {
            throw IllegalArgumentException("自定义时间格式错误")
        }

        val startMinutes = parseToMinutes(startTime)
        val endMinutes = parseToMinutes(endTime)

        if (startMinutes >= endMinutes) {
            throw IllegalArgumentException("自定义开始时间必须早于结束时间")
        }
    }

    private fun getOrAssignColorByName(
        jsonCourse: ImportCourseJsonModel,
        colorSize: Int,
        nameToColorMap: MutableMap<String, Int>,
        getNextAutoColor: () -> Int
    ): Int {
        val trimmedName = jsonCourse.name.trim()
        val existingColor = nameToColorMap[trimmedName]

        if (existingColor != null) return existingColor

        val importedColor = jsonCourse.color
        val finalColor = if (importedColor != null && importedColor in 0 until colorSize) {
            importedColor
        } else {
            getNextAutoColor()
        }

        nameToColorMap[trimmedName] = finalColor
        return finalColor
    }

    /**
     * 公共课程实体构建函数：消除4个导入函数中的重复逻辑。
     * 统一处理颜色分配、属性提取、课程和周次实体构建。
     *
     * @param coursesJsonModel 导入的课程 JSON 列表
     * @param tableId 课表 ID
     * @param colorSize 当前主题的颜色数量
     * @param isCrush 是否为情侣课表（crush 课程）
     * @param preserveId 是否保留 JSON 中的课程 ID（用于完整导入，false 时生成新 UUID）
     * @return Pair(课程实体列表, 周次实体列表)
     */
    private fun buildCourseEntities(
        coursesJsonModel: List<ImportCourseJsonModel>,
        tableId: String,
        colorSize: Int,
        isCrush: Boolean,
        preserveId: Boolean
    ): Pair<List<Course>, List<CourseWeek>> {
        val courseEntities = ArrayList<Course>(coursesJsonModel.size)
        val courseWeekEntities = mutableListOf<CourseWeek>()

        val nameToColorMap = mutableMapOf<String, Int>()
        var colorOffset = if (colorSize > 0) Random.nextInt(colorSize) else 0

        coursesJsonModel.forEach { jsonCourse ->
            val courseId = if (preserveId && jsonCourse.id != null) jsonCourse.id else Uuid.random().toString()

            val courseIndex = getOrAssignColorByName(
                jsonCourse = jsonCourse,
                colorSize = colorSize,
                nameToColorMap = nameToColorMap,
                getNextAutoColor = {
                    val next = if (colorSize > 0) colorOffset % colorSize else 0
                    colorOffset++
                    next
                }
            )

            val extracted = extractCourseAttributes(jsonCourse.remark)
            // 防护：钳制 day / 节次 / 周次范围，防止恶意或异常导入数据产生越界下标、
            // 负高度课程块（startSection > endSection）等隐患。
            val rawStart = jsonCourse.startSection?.coerceIn(1, 24)
            val rawEnd = jsonCourse.endSection?.coerceIn(1, 24)
            val (safeStart, safeEnd) =
                if (rawStart != null && rawEnd != null && rawStart > rawEnd) rawEnd to rawStart
                else rawStart to rawEnd
            courseEntities.add(
                Course(
                    id = courseId,
                    courseTableId = tableId,
                    name = jsonCourse.name,
                    teacher = jsonCourse.teacher,
                    position = jsonCourse.position,
                    day = jsonCourse.day.coerceIn(1, 7),
                    startSection = safeStart,
                    endSection = safeEnd,
                    isCustomTime = jsonCourse.isCustomTime,
                    customStartTime = jsonCourse.customStartTime,
                    customEndTime = jsonCourse.customEndTime,
                    colorInt = courseIndex,
                    remark = jsonCourse.remark?.take(300),
                    credit = jsonCourse.credit ?: extracted.credit,
                    assessmentMethod = jsonCourse.assessmentMethod ?: extracted.assessmentMethod,
                    isLab = jsonCourse.isLab || extracted.isLab,
                    isCrush = isCrush
                )
            )

            jsonCourse.weeks.forEach { week ->
                courseWeekEntities.add(
                    CourseWeek(courseId = courseId, weekNumber = week.coerceAtLeast(1))
                )
            }
        }

        return Pair(courseEntities, courseWeekEntities)
    }

    @Transaction
    suspend fun importCoursesFromList(
        tableId: String,
        coursesJsonModel: List<ImportCourseJsonModel>
    ) {
        coursesJsonModel.forEach { validateCustomCourseTimeOrThrow(it) }

        val currentStyle = styleSettingsRepository.styleFlow.first()
        val colorSize = currentStyle.courseColorMaps.size

        courseDao.deleteCoursesByTableId(tableId)

        val (courseEntities, courseWeekEntities) = buildCourseEntities(
            coursesJsonModel = coursesJsonModel,
            tableId = tableId,
            colorSize = colorSize,
            isCrush = false,
            preserveId = false
        )

        if (courseEntities.isNotEmpty()) courseDao.insertAll(courseEntities)
        if (courseWeekEntities.isNotEmpty()) courseWeekDao.insertAll(courseWeekEntities)
    }

    /**
     * 从一个完整的 JSON 模型导入课表数据。
     * 逻辑说明：
     * 1. 课程数据（courses）：始终清空并重新导入。
     * 2. 时间段数据（timeSlots）：仅在 JSON 包含有效数据时覆盖，否则保留本地现状。
     * 3. 配置信息（config）：仅在 JSON 包含有效数据时覆盖，且会保留本地的 showWeekends 设置。
     */
    @Transaction
    suspend fun importCourseTableFromJson(
        tableId: String,
        courseTableJsonModel: CourseTableImportModel
    ) {
        courseTableJsonModel.courses.forEach { validateCustomCourseTimeOrThrow(it) }
        courseTableJsonModel.timeSlots?.let { validateTimeSlotsOrThrow(it) }

        val currentStyle = styleSettingsRepository.styleFlow.first()
        val colorSize = currentStyle.courseColorMaps.size

        // 处理课程数据（始终清空原有课程）
        courseDao.deleteCoursesByTableId(tableId)

        val (courseEntities, courseWeekEntities) = buildCourseEntities(
            coursesJsonModel = courseTableJsonModel.courses,
            tableId = tableId,
            colorSize = colorSize,
            isCrush = false,
            preserveId = true
        )

        // 处理时间段数据（仅在有数据时覆盖）
        val jsonTimeSlots = courseTableJsonModel.timeSlots
        if (!jsonTimeSlots.isNullOrEmpty()) {
            timeSlotDao.deleteAllTimeSlotsByCourseTableId(tableId)

            val timeSlotEntities = jsonTimeSlots.map { jsonTimeSlot ->
                TimeSlot(
                    number = jsonTimeSlot.number,
                    startTime = jsonTimeSlot.startTime,
                    endTime = jsonTimeSlot.endTime,
                    courseTableId = tableId,
                    alias = jsonTimeSlot.alias?.take(5)
                )
            }
            timeSlotDao.insertAll(timeSlotEntities)
        }

        // 统一执行课程数据插入
        if (courseEntities.isNotEmpty()) courseDao.insertAll(courseEntities)
        if (courseWeekEntities.isNotEmpty()) courseWeekDao.insertAll(courseWeekEntities)

        // 处理配置数据
        val configJson = courseTableJsonModel.config
        if (configJson != null) {
            val currentConfig = appSettingsRepository.getCourseConfigOnce(tableId)
            val updatedConfig = CourseTableConfig(
                courseTableId = tableId,
                showWeekends = currentConfig?.showWeekends ?: false,
                semesterStartDate = configJson.semesterStartDate,
                semesterTotalWeeks = configJson.semesterTotalWeeks,
                defaultClassDuration = configJson.defaultClassDuration,
                defaultBreakDuration = configJson.defaultBreakDuration,
                firstDayOfWeek = configJson.firstDayOfWeek
            )
            appSettingsRepository.insertOrUpdateCourseConfig(updatedConfig)
        }
    }

    /**
     * 导入预设时间段
     */
    @Transaction
    suspend fun importTimeSlots(
        tableId: String,
        timeSlots: List<TimeSlotJsonModel>
    ) {
        validateTimeSlotsOrThrow(timeSlots)

        val timeSlotEntities = timeSlots.map { jsonModel ->
            TimeSlot(
                number = jsonModel.number,
                startTime = jsonModel.startTime,
                endTime = jsonModel.endTime,
                courseTableId = tableId
            )
        }
        timeSlotDao.deleteAllTimeSlotsByCourseTableId(tableId)
        if (timeSlotEntities.isNotEmpty()) {
            timeSlotDao.insertAll(timeSlotEntities)
        }
    }

    /**
     * 从 JSON 模型更新指定课表的配置。
     */
    @Transaction
    suspend fun importCourseConfig(
        tableId: String,
        configJsonModel: CourseConfigJsonModel
    ) {
        val currentConfig = appSettingsRepository.getCourseConfigOnce(tableId)

        val updatedConfig = CourseTableConfig(
            courseTableId = tableId,
            showWeekends = currentConfig?.showWeekends ?: false,
            semesterStartDate = configJsonModel.semesterStartDate,
            semesterTotalWeeks = configJsonModel.semesterTotalWeeks,
            defaultClassDuration = configJsonModel.defaultClassDuration,
            defaultBreakDuration = configJsonModel.defaultBreakDuration,
            firstDayOfWeek = configJsonModel.firstDayOfWeek
        )

        appSettingsRepository.insertOrUpdateCourseConfig(updatedConfig)
    }

    /**
     * 将指定课表下的所有数据导出为一个完整的 JSON 模型。
     */
    suspend fun exportCourseTableToJson(tableId: String): CourseTableExportModel? {
        val coursesWithWeeks = courseDao.getCoursesWithWeeksByTableId(tableId).first()
        if (coursesWithWeeks.isEmpty() && appSettingsRepository.getCourseConfigOnce(tableId) == null) {
            return null
        }

        val exportCourses = coursesWithWeeks.map { courseWithWeeks ->
            val course = courseWithWeeks.course
            val weeks = courseWithWeeks.weeks.map { it.weekNumber }
            val colorIndex = course.colorInt

            ExportCourseJsonModel(
                id = course.id,
                name = course.name,
                teacher = course.teacher,
                position = course.position,
                day = course.day,
                startSection = course.startSection,
                endSection = course.endSection,
                color = colorIndex,
                weeks = weeks,
                isCustomTime = course.isCustomTime,
                customStartTime = course.customStartTime,
                customEndTime = course.customEndTime,
                remark = course.remark,
                credit = course.credit,
                assessmentMethod = course.assessmentMethod,
                isLab = course.isLab
            )
        }

        val courseConfig = appSettingsRepository.getCourseConfigOnce(tableId)
        val configToExport = courseConfig ?: CourseTableConfig(courseTableId = tableId)

        val timeSlots = timeSlotRepository.getActiveTimeSlotsOnce(tableId, configToExport)
        val exportTimeSlots = timeSlots.map { timeSlot ->
            TimeSlotJsonModel(
                number = timeSlot.number,
                startTime = timeSlot.startTime,
                endTime = timeSlot.endTime,
                alias = timeSlot.alias?.take(5)
            )
        }

        val exportConfig = CourseConfigJsonModel(
            semesterStartDate = configToExport.semesterStartDate,
            semesterTotalWeeks = configToExport.semesterTotalWeeks,
            defaultClassDuration = configToExport.defaultClassDuration,
            defaultBreakDuration = configToExport.defaultBreakDuration,
            firstDayOfWeek = configToExport.firstDayOfWeek
        )

        return CourseTableExportModel(
            courses = exportCourses,
            timeSlots = exportTimeSlots,
            config = exportConfig
        )
    }

    /**
     * 一键导入 crush（情侣）课表：从 JSON 模型列表导入，标记 isCrush = true。
     * 与本人课表数据完全隔离（仅删除该课表下已有的 crush 课程，不动本人课程）。
     *
     * @param tableId 课表ID（与本人课表共用同一 ID，通过 isCrush 标记隔离）
     * @param coursesJsonModel 解析后的课程 JSON 模型列表
     */
    @Transaction
    suspend fun importCrushCoursesFromList(
        tableId: String,
        coursesJsonModel: List<ImportCourseJsonModel>
    ) {
        coursesJsonModel.forEach { validateCustomCourseTimeOrThrow(it) }

        val currentStyle = styleSettingsRepository.styleFlow.first()
        val colorSize = currentStyle.courseColorMaps.size

        // 仅删除 crush 课程，本人课程保持不变
        courseDao.deleteCrushCoursesByTableId(tableId)

        val (courseEntities, courseWeekEntities) = buildCourseEntities(
            coursesJsonModel = coursesJsonModel,
            tableId = tableId,
            colorSize = colorSize,
            isCrush = true,
            preserveId = false
        )

        if (courseEntities.isNotEmpty()) courseDao.insertAll(courseEntities)
        if (courseWeekEntities.isNotEmpty()) courseWeekDao.insertAll(courseWeekEntities)
    }

    /**
     * 从一个完整的 JSON 模型导入 crush 课表数据。
     * 逻辑与 [importCrushCoursesFromList] 一致，额外支持时间段（timeSlots）数据。
     */
    @Transaction
    suspend fun importCrushCourseTableFromJson(
        tableId: String,
        courseTableJsonModel: CourseTableImportModel
    ) {
        courseTableJsonModel.courses.forEach { validateCustomCourseTimeOrThrow(it) }
        courseTableJsonModel.timeSlots?.let { validateTimeSlotsOrThrow(it) }

        val currentStyle = styleSettingsRepository.styleFlow.first()
        val colorSize = currentStyle.courseColorMaps.size

        // 仅删除 crush 课程，本人课程保持不变
        courseDao.deleteCrushCoursesByTableId(tableId)

        val (courseEntities, courseWeekEntities) = buildCourseEntities(
            coursesJsonModel = courseTableJsonModel.courses,
            tableId = tableId,
            colorSize = colorSize,
            isCrush = true,
            preserveId = true
        )

        // crush 课表导入不覆盖时间段，避免影响本人课表的作息时间设置

        if (courseEntities.isNotEmpty()) courseDao.insertAll(courseEntities)
        if (courseWeekEntities.isNotEmpty()) courseWeekDao.insertAll(courseWeekEntities)
    }

    /**
     * 删除指定课表下的所有 crush 课程。
     * 不影响本人课表数据。
     */
    suspend fun deleteCrushCourses(tableId: String) {
        courseDao.deleteCrushCoursesByTableId(tableId)
    }

    /**
     * 获取当前选中的课表 ID。
     */
    suspend fun getCurrentTableId(): String {
        return appSettingsRepository.getAppSettingsOnce().currentCourseTableId
    }

    /**
     * 获取指定课表下是否存在 crush 课程。
     */
    suspend fun hasCrushCourses(tableId: String): Boolean {
        return courseDao.getCrushCoursesWithWeeksByTableId(tableId).first().isNotEmpty()
    }

    /**
     * 将指定课表下的所有课程数据导出为 ICS 日历文件的内容字符串。
     */
    suspend fun exportToIcsString(tableId: String, alarmMinutes: Int?): String? {
        val courses = courseDao.getCoursesWithWeeksByTableId(tableId).first()

        val appSettings = appSettingsRepository.getAppSettingsOnce()
        val courseConfig = appSettingsRepository.getCourseConfigOnce(tableId)
        val timeSlots = timeSlotRepository.getActiveTimeSlotsOnce(tableId, courseConfig)
        val semesterStartDate = courseConfig?.semesterStartDate?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        }

        if (semesterStartDate == null || courseConfig.semesterTotalWeeks <= 0) {
            return null
        }

        return IcsExportTool.generateIcsFileContent(
            courses = courses,
            timeSlots = timeSlots,
            semesterStartDate = semesterStartDate,
            semesterTotalWeeks = courseConfig.semesterTotalWeeks,
            firstDayOfWeekInt = courseConfig.firstDayOfWeek,
            alarmMinutes = alarmMinutes,
            skippedDates = appSettings.skippedDates
        )
    }

    /**
     * 一键同步当前课表到系统日历
     */
    suspend fun syncCurrentTableToSystemCalendar(): Boolean {
        val appSettings = appSettingsRepository.getAppSettingsOnce()
        val currentTableId = appSettings.currentCourseTableId
        if (currentTableId.isEmpty()) return true

        val courses = courseDao.getCoursesWithWeeksByTableId(currentTableId).first()
        val alarmMinutes = appSettings.remindBeforeMinutes
        val courseConfig = appSettingsRepository.getCourseConfigOnce(currentTableId)
        val timeSlots = timeSlotRepository.getActiveTimeSlotsOnce(currentTableId, courseConfig)

        val semesterStartDate = courseConfig?.semesterStartDate?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        } ?: return true

        if (courseConfig.semesterTotalWeeks <= 0 || courses.isEmpty()) {
            return true
        }

        return CalendarAccountManager.syncCurrentTableToSystemCalendar(
            courses = courses,
            timeSlots = timeSlots,
            semesterStartDate = semesterStartDate,
            semesterTotalWeeks = courseConfig.semesterTotalWeeks,
            firstDayOfWeekInt = courseConfig.firstDayOfWeek,
            alarmMinutes = alarmMinutes,
            skippedDates = appSettings.skippedDates
        )
    }
}

/**
 * 从备注文本中提取出的课程结构化属性。
 */
private data class ExtractedCourseAttributes(
    val credit: String? = null,
    val assessmentMethod: String? = null,
    val isLab: Boolean = false
)