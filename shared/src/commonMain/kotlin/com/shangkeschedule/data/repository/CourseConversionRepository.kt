package com.shangkeschedule.data.repository

import androidx.room3.withWriteTransaction
import com.shangkeschedule.data.db.main.Course
import com.shangkeschedule.data.db.main.CourseDao
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseWeek
import com.shangkeschedule.data.db.main.CourseWeekDao
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.main.TimeSlotScheme
import com.shangkeschedule.data.db.main.TimeSlotDao
import com.shangkeschedule.data.model.CourseImportExport.CourseConfigJsonModel
import com.shangkeschedule.data.model.CourseImportExport.CourseTableExportModel
import com.shangkeschedule.data.model.CourseImportExport.CourseTableImportModel
import com.shangkeschedule.data.model.CourseImportExport.ExportCourseJsonModel
import com.shangkeschedule.data.model.CourseImportExport.SchemeMetaJsonModel
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
 * 解析导入后应沿用的作息方案ID。
 *
 * [CourseConfigJsonModel] 是 v2 备份模型，携带 `currentSchemeId`；但教务端下发的课表配置
 * 不含该字段，会落到默认值 `"default"`。因此把「等于默认值」视为**未指定**，
 * 保留用户既有设置；只有显式带了一个非默认方案名的备份，才采纳它。
 *
 * 独立成顶层函数，便于在不启动数据库的前提下做回归测试。
 */
internal fun resolveImportedSchemeId(
    incoming: String,
    current: CourseTableConfig?
): String {
    val isUnspecified = incoming.isBlank() || incoming == TimeSlot.DEFAULT_SCHEME_ID
    return if (isUnspecified) {
        current?.currentSchemeId ?: TimeSlot.DEFAULT_SCHEME_ID
    } else {
        incoming
    }
}

/**
 * 解析导入后应沿用的「冬夏作息自动切换」开关。
 *
 * 教务端下发的配置同样不含该字段，恒为 `false`。关掉自动切换是**用户可感知的设置**，
 * 不能被一次无关的导入悄悄改掉；v2 备份里 `true` 才代表用户显式开启过，
 * 故取「两者之一为 true 即为 true」。
 */
internal fun resolveImportedAutoSwitch(
    incoming: Boolean,
    current: CourseTableConfig?
): Boolean {
    return incoming || (current?.autoSwitchScheme ?: false)
}

/**
 * 课表转换仓库，负责处理课程数据的导入、导出以及 ICS 生成等逻辑。
 */
@OptIn(ExperimentalUuidApi::class)
@Single
class CourseConversionRepository(
    private val database: MainAppDatabase,
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

        // v2 起备份把全部作息方案拍平在同一列表（每套方案编号各自从 1 起），
        // 编号连续性与时间重叠都必须**按 schemeId 分组内**校验——全局校验会让多方案备份恢复必然失败
        timeSlots.groupBy { it.schemeId }.forEach { (_, schemeSlots) ->
            val sortedSlots = schemeSlots.sortedBy { it.number }
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

    private fun validateCourseConfigOrThrow(config: CourseConfigJsonModel) {
        config.semesterStartDate?.let { startDate ->
            try {
                LocalDate.parse(startDate)
            } catch (_: Exception) {
                throw IllegalArgumentException("学期开始日期格式错误，应为 yyyy-MM-dd")
            }
        }
        if (config.semesterTotalWeeks !in 1..100) {
            throw IllegalArgumentException("学期总周数必须在 1 到 100 之间")
        }
        if (config.defaultClassDuration !in 1..240) {
            throw IllegalArgumentException("默认上课时长必须在 1 到 240 分钟之间")
        }
        if (config.defaultBreakDuration !in 0..180) {
            throw IllegalArgumentException("默认休息时长必须在 0 到 180 分钟之间")
        }
        if (config.firstDayOfWeek !in 1..7) {
            throw IllegalArgumentException("每周起始日必须在 1 到 7 之间")
        }
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
     * @param conflictingIds 库中属于**其它课表**的课程 ID。`Course.id` 是全局主键，
     *   命中其中任一即重生 UUID，避免 `CourseDao` 的 ABORT 约束把整次导入炸掉。
     * @return Pair(课程实体列表, 周次实体列表)
     */
    private fun buildCourseEntities(
        coursesJsonModel: List<ImportCourseJsonModel>,
        tableId: String,
        colorSize: Int,
        isCrush: Boolean,
        preserveId: Boolean,
        conflictingIds: Set<String> = emptySet()
    ): Pair<List<Course>, List<CourseWeek>> {
        val courseEntities = ArrayList<Course>(coursesJsonModel.size)
        val courseWeekEntities = mutableListOf<CourseWeek>()

        val nameToColorMap = mutableMapOf<String, Int>()
        var colorOffset = if (colorSize > 0) Random.nextInt(colorSize) else 0

        coursesJsonModel.forEach { jsonCourse ->
            val courseId = if (preserveId && jsonCourse.id != null && jsonCourse.id !in conflictingIds) {
                jsonCourse.id
            } else {
                Uuid.random().toString()
            }

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

    suspend fun importCoursesFromList(
        tableId: String,
        coursesJsonModel: List<ImportCourseJsonModel>
    ) {
        // 空结果保护：下方事务会先 deleteCoursesByTableId 再插入，
        // 若解析结果为 0 门课（例如未登录页/错误页被当作成功解析），原课表会被静默清空。
        // 这里直接拒绝，由上层（WebBridgeHandler）提示用户并保留原有课表。
        require(coursesJsonModel.isNotEmpty()) { "解析结果为空，已保留原有课表" }
        coursesJsonModel.forEach { validateCustomCourseTimeOrThrow(it) }

        // v4.75.14：**丢弃周次为空的课程**（落库侧兜底，不依赖各适配脚本自觉）。
        //
        // 课表 UI 的显示判据是 `weeks.any { it.weekNumber == currentWeek }`，周次为空的
        // 课程**任何一周都不显示**。若放任它落库，用户会看到「导入成功 N 门课」却在
        // 课表上找不到那几门 —— 这正是「导入不成功」最难排查的一类：数据确实进库了，
        // 只是永远不可见（且会占用门数统计，让「共导入 N 门」与实际可见数对不上）。
        //
        // 适配脚本侧已同步修正（`zhengfang.js` / `hbeu.js` 的 `if (weeks.length === 0) weeks = [];`
        // 是空操作，已改为丢弃并计数提示）。这里再加一层：脚本是 OTA 分发的、存量版本
        // 仍可能是旧写法，落库侧必须自己守得住。
        val importableCourses = coursesJsonModel.filter { it.weeks.isNotEmpty() }
        // 全部为空时按「解析结果为空」处理，避免把课表清空。
        require(importableCourses.isNotEmpty()) {
            "解析出的课程均缺少周次信息，已保留原有课表"
        }

        val currentStyle = styleSettingsRepository.styleFlow.first()
        val colorSize = currentStyle.courseColorMaps.size

        val (courseEntities, courseWeekEntities) = buildCourseEntities(
            coursesJsonModel = importableCourses,
            tableId = tableId,
            colorSize = colorSize,
            isCrush = false,
            preserveId = false
        )

        // 真事务：清空旧课程与新课程写入原子化，中途失败不会留下"课程被清空但新数据没进库"的状态
        database.withWriteTransaction {
            courseDao.deleteCoursesByTableId(tableId)
            if (courseEntities.isNotEmpty()) courseDao.insertAll(courseEntities)
            if (courseWeekEntities.isNotEmpty()) courseWeekDao.insertAll(courseWeekEntities)
        }
    }

    /**
     * 从一个完整的 JSON 模型导入课表数据。
     * 逻辑说明：
     * 1. 课程数据（courses）：始终清空并重新导入。
     * 2. 时间段数据（timeSlots）：仅在 JSON 包含有效数据时覆盖，否则保留本地现状。
     * 3. 配置信息（config）：仅在 JSON 包含有效数据时覆盖，且会保留本地的 showWeekends 设置。
     */
    suspend fun importCourseTableFromJson(
        tableId: String,
        courseTableJsonModel: CourseTableImportModel,
        restoreMode: Boolean = false
    ) {
        courseTableJsonModel.courses.forEach { validateCustomCourseTimeOrThrow(it) }
        courseTableJsonModel.timeSlots?.let { validateTimeSlotsOrThrow(it) }
        courseTableJsonModel.config?.let { validateCourseConfigOrThrow(it) }

        val currentStyle = styleSettingsRepository.styleFlow.first()
        val colorSize = currentStyle.courseColorMaps.size

        // v4.64.23：`preserveId=true` 会复用 JSON 里的课程 id，而 `Course.id` 是**全局主键、
        // 不按 tableId 分域**。于是「A 表导入成功 → 同一份 JSON 再导 B 表」必然撞
        // `CourseDao` 的 ABORT 约束，用户只看到一句裸 SQL 错误。
        // 这里先取出库里已有的全部课程 id，凡与**其它课表**冲突的一律重生 UUID；
        // 同表重复导入时仍复用原 id，保持幂等（全删全插，结果一致）。
        val conflictingIds: Set<String> = courseDao.getAllCourseIds()
            .toSet() - courseDao.getCourseIdsByTableId(tableId).toSet()

        val (courseEntities, courseWeekEntities) = buildCourseEntities(
            coursesJsonModel = courseTableJsonModel.courses,
            tableId = tableId,
            colorSize = colorSize,
            isCrush = false,
            preserveId = true,
            conflictingIds = conflictingIds
        )

        val jsonTimeSlots = courseTableJsonModel.timeSlots
        val timeSlotEntities = jsonTimeSlots?.map { jsonTimeSlot ->
            TimeSlot(
                number = jsonTimeSlot.number,
                startTime = jsonTimeSlot.startTime,
                endTime = jsonTimeSlot.endTime,
                courseTableId = tableId,
                alias = jsonTimeSlot.alias?.take(5),
                schemeId = jsonTimeSlot.schemeId
            )
        }

        val configJson = courseTableJsonModel.config
        val updatedConfig = configJson?.let {
            val currentConfig = appSettingsRepository.getCourseConfigOnce(tableId)
            CourseTableConfig(
                courseTableId = tableId,
                // 恢复模式（整表备份恢复）下扩展字段随备份走；普通 JSON 文件导入保留本地值，
                // 避免旧 JSON 缺省值（"default"/false）覆盖本地夏冬令时配置
                showWeekends = if (restoreMode) it.showWeekends else (currentConfig?.showWeekends ?: false),
                semesterStartDate = it.semesterStartDate,
                semesterTotalWeeks = it.semesterTotalWeeks,
                defaultClassDuration = it.defaultClassDuration,
                defaultBreakDuration = it.defaultBreakDuration,
                firstDayOfWeek = it.firstDayOfWeek,
                currentSchemeId = if (restoreMode) it.currentSchemeId else (currentConfig?.currentSchemeId ?: "default"),
                autoSwitchScheme = if (restoreMode) it.autoSwitchScheme else (currentConfig?.autoSwitchScheme ?: false)
            )
        }

        // 真事务：课程清空重建 + 时段覆盖 + 配置覆盖全部原子化
        database.withWriteTransaction {
            // 处理课程数据（始终清空原有课程）
            courseDao.deleteCoursesByTableId(tableId)

            // 处理时间段数据（仅在有数据时覆盖）
            if (!jsonTimeSlots.isNullOrEmpty()) {
                // 只替换 JSON 中出现的作息方案，保留其它方案（夏/冬令时等）的原有时间段。
                // 原实现按 courseTableId 整表删除（deleteAllTimeSlotsByCourseTableId），
                // 会把用户配置的其它方案时间段一并清空且不可恢复；旧 JSON 的 schemeId 缺省为
                // "default"，因此只影响 default 方案。
                val affectedSchemeIds = timeSlotEntities.orEmpty().map { it.schemeId }.toSet()
                if (affectedSchemeIds.isEmpty()) {
                    timeSlotDao.deleteAllTimeSlotsByCourseTableId(tableId)
                } else {
                    affectedSchemeIds.forEach { schemeId ->
                        timeSlotDao.deleteTimeSlotsByScheme(tableId, schemeId)
                    }
                }
                timeSlotDao.insertAll(timeSlotEntities.orEmpty())
            }

            // 作息方案元信息（仅当数据源携带时重建，旧 JSON 文件缺省空列表 → 跳过）
            if (courseTableJsonModel.timeSlotSchemes.isNotEmpty()) {
                timeSlotRepository.deleteSchemeMetasByCourseTableId(tableId)
                courseTableJsonModel.timeSlotSchemes.forEach { meta ->
                    timeSlotRepository.upsertSchemeMeta(
                        TimeSlotScheme(
                            courseTableId = tableId,
                            schemeId = meta.schemeId,
                            startMonthDay = meta.startMonthDay,
                            endMonthDay = meta.endMonthDay
                        )
                    )
                }
            }

            // 统一执行课程数据插入
            if (courseEntities.isNotEmpty()) courseDao.insertAll(courseEntities)
            if (courseWeekEntities.isNotEmpty()) courseWeekDao.insertAll(courseWeekEntities)

            // 配置也在同一 Room 事务内写入，避免课程已提交但配置失败
            if (updatedConfig != null) {
                appSettingsRepository.insertOrUpdateCourseConfig(updatedConfig)
            }
        }
    }

    /**
     * 导入预设时间段
     */
    suspend fun importTimeSlots(
        tableId: String,
        timeSlots: List<TimeSlotJsonModel>
    ) {
        // v4.64.23：空列表此前被 validateTimeSlotsOrThrow 直接放过（:77 的 early return），
        // 于是下面 `deleteAllTimeSlotsByCourseTableId` 照样执行 —— 净效果是
        // **清空该课表的全部作息并返回成功**。适配脚本只要算出 0 个时段（NIIT / BUPT
        // 等已实测会走到）就会触发，用户表现是「导入成功但作息全没了、课表空白」。
        // 空列表对「整体替换作息」这个语义没有任何合法解释，故显式拒绝。
        require(timeSlots.isNotEmpty()) { "时间段列表为空，已拒绝导入（否则会清空该课表的全部作息）" }
        validateTimeSlotsOrThrow(timeSlots)

        val timeSlotEntities = timeSlots.map { jsonModel ->
            TimeSlot(
                number = jsonModel.number,
                startTime = jsonModel.startTime,
                endTime = jsonModel.endTime,
                courseTableId = tableId,
                // R41-01：此前未透传 schemeId，全部落进默认方案 default。
                schemeId = jsonModel.schemeId
            )
        }

        // 真事务：时段清空与写入原子化。
        // 空列表已在入口被 require 拦下，这里的 isNotEmpty 是双保险（防御后续调用点）。
        database.withWriteTransaction {
            // R41-01：此前按 courseTableId **整表删除**，会把用户配置的其它作息方案
            // （夏令时 / 冬令时等）一并抹掉且不可恢复 —— 表现为「导入作息后课表渲染为空」。
            // 改为只替换本次数据实际涉及的方案，语义与同文件 importCourseTableFromJson 的
            // :398-412 一致；该处 affectedSchemeIds 为空时（仅在数据源无任何方案时）才整表清。
            val affectedSchemeIds = timeSlotEntities.map { it.schemeId }.toSet()
            if (affectedSchemeIds.isEmpty()) {
                timeSlotDao.deleteAllTimeSlotsByCourseTableId(tableId)
            } else {
                affectedSchemeIds.forEach { schemeId ->
                    timeSlotDao.deleteTimeSlotsByScheme(tableId, schemeId)
                }
            }
            timeSlotDao.insertAll(timeSlotEntities)
        }
    }

    /**
     * 从 JSON 模型更新指定课表的配置。
     */
    suspend fun importCourseConfig(
        tableId: String,
        configJsonModel: CourseConfigJsonModel
    ) {
        validateCourseConfigOrThrow(configJsonModel)
        val currentConfig = appSettingsRepository.getCourseConfigOnce(tableId)

        val updatedConfig = CourseTableConfig(
            courseTableId = tableId,
            showWeekends = currentConfig?.showWeekends ?: false,
            semesterStartDate = configJsonModel.semesterStartDate,
            semesterTotalWeeks = configJsonModel.semesterTotalWeeks,
            defaultClassDuration = configJsonModel.defaultClassDuration,
            defaultBreakDuration = configJsonModel.defaultBreakDuration,
            firstDayOfWeek = configJsonModel.firstDayOfWeek,
            // 作息方案：insertOrUpdateCourseConfig 是整行 REPLACE（未列出的字段回落为实体默认值）。
            // 本函数此前只列了 6 个字段，漏掉 currentSchemeId / autoSwitchScheme 两项
            // ⇒ 每次从教务导入课表配置，用户的作息方案被无声重置为「default」、冬夏自动切换被无声关掉。
            //
            // 注意：CourseConfigJsonModel 是 v2 备份模型，**本身就带这两个字段**
            // （currentSchemeId / autoSwitchScheme），所以这里不能一律沿用旧配置 ——
            // 导入备份时应当采纳文件里的值；只有 v1 旧备份/教务端不提供该字段时才回落旧配置。
            // 判据：教务端下发的课表配置 JSON 不含这两项（走序列化默认值 "default"/false），
            // 因此「与当前配置相同或为默认值」一律视为「未指定」，保留用户既有设置。
            currentSchemeId = resolveImportedSchemeId(
                configJsonModel.currentSchemeId, currentConfig
            ),
            autoSwitchScheme = resolveImportedAutoSwitch(
                configJsonModel.autoSwitchScheme, currentConfig
            )
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

        // v2：导出**全部方案**的作息（情侣课表独立作息/夏冬令时多方案在恢复后不丢）
        val schemeIds = timeSlotRepository.getSchemeIdsByCourseTableId(tableId).first()
        val exportTimeSlots = schemeIds.flatMap { schemeId ->
            timeSlotRepository.getTimeSlotsByCourseTableId(tableId, schemeId).first().map { timeSlot ->
                TimeSlotJsonModel(
                    number = timeSlot.number,
                    startTime = timeSlot.startTime,
                    endTime = timeSlot.endTime,
                    alias = timeSlot.alias?.take(5),
                    schemeId = timeSlot.schemeId
                )
            }
        }

        val exportSchemeMetas = timeSlotRepository.getSchemeMetasOnce(tableId).map { meta ->
            SchemeMetaJsonModel(
                schemeId = meta.schemeId,
                startMonthDay = meta.startMonthDay,
                endMonthDay = meta.endMonthDay
            )
        }

        val exportConfig = CourseConfigJsonModel(
            semesterStartDate = configToExport.semesterStartDate,
            semesterTotalWeeks = configToExport.semesterTotalWeeks,
            defaultClassDuration = configToExport.defaultClassDuration,
            defaultBreakDuration = configToExport.defaultBreakDuration,
            firstDayOfWeek = configToExport.firstDayOfWeek,
            currentSchemeId = configToExport.currentSchemeId,
            autoSwitchScheme = configToExport.autoSwitchScheme,
            showWeekends = configToExport.showWeekends
        )

        return CourseTableExportModel(
            courses = exportCourses,
            timeSlots = exportTimeSlots,
            config = exportConfig,
            timeSlotSchemes = exportSchemeMetas
        )
    }

    /**
     * 获取当前选中的课表 ID。
     */
    suspend fun getCurrentTableId(): String {
        return appSettingsRepository.getAppSettingsOnce().currentCourseTableId
    }

    /**
     * 当前课表是否已设置开学日期。
     * 用于导入新课表成功后引导用户补齐学期配置（未设置时弹窗跳转开学日期设置）。
     */
    suspend fun isSemesterStartDateSet(): Boolean {
        val tableId = getCurrentTableId()
        return !appSettingsRepository.getCourseConfigOnce(tableId)?.semesterStartDate.isNullOrBlank()
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