package com.shangkeschedule.data.repository

import androidx.room3.withWriteTransaction
import com.shangkeschedule.data.db.main.Course
import com.shangkeschedule.data.db.main.CourseDao
import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseTableDao
import com.shangkeschedule.data.db.main.CourseWeek
import com.shangkeschedule.data.db.main.CourseWeekDao
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.db.main.TimeSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 课表数据仓库，负责处理所有与课表、课程相关的业务逻辑和数据操作。
 */
@OptIn(ExperimentalUuidApi::class)
@Single
class CourseTableRepository(
    private val database: MainAppDatabase,
    private val courseTableDao: CourseTableDao,
    private val courseDao: CourseDao,
    private val courseWeekDao: CourseWeekDao,
    private val timeSlotRepository: TimeSlotRepository,
    private val appSettingsRepository: AppSettingsRepository
) {
    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        // 当仓库被依赖注入创建时，自动触发保护/种子数据填充逻辑
        repositoryScope.launch {
            seedDefaultData()
        }
    }

    /**
     * 种入初始默认数据（当不存在任何课表时触发）
     */
    private suspend fun seedDefaultData() {
        if (courseTableDao.getAllCourseTables().first().isNotEmpty()) {
            return
        }

        val tableId = Uuid.random().toString()
        val defaultCourseTable = CourseTable(
            id = tableId,
            name = "我的课表",
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
        val defaultConfig = CourseTableConfig(
            courseTableId = tableId,
            showWeekends = false,
            semesterTotalWeeks = 20,
            defaultClassDuration = 45,
            defaultBreakDuration = 10,
            firstDayOfWeek = 1
        )

        val defaultTimeSlotsForNewTable = DEFAULT_TIME_SLOTS.map {
            it.copy(courseTableId = tableId)
        }

        // 真事务：三张表的初始化写入要么全部成功，要么全部回滚
        database.withWriteTransaction {
            courseTableDao.insert(defaultCourseTable)
            appSettingsRepository.insertOrUpdateCourseConfig(defaultConfig)
            timeSlotRepository.insertAll(defaultTimeSlotsForNewTable)
        }

        println("数据库初始化数据已完成写入")
    }

    /**
     * 获取所有课表，返回一个数据流。
     */
    fun getAllCourseTables(): Flow<List<CourseTable>> {
        return courseTableDao.getAllCourseTables()
    }

    /**
     * 获取指定课表ID的完整课程（包含周数）。
     */
    fun getCoursesWithWeeksByTableId(tableId: String): Flow<List<CourseWithWeeks>> {
        return courseDao.getCoursesWithWeeksByTableId(tableId)
    }

    /** 按 ID 获取课表实体（一次性）。 */
    suspend fun getCourseTableById(tableId: String): CourseTable? {
        return courseTableDao.getCourseTableById(tableId)
    }

    /** 按课程 ID 反查所属课表 ID（不存在时返回 null）。 */
    suspend fun findTableIdOfCourse(courseId: String): String? {
        return courseDao.getCourseTableIdById(courseId)
    }

    /**
     * 获取与本人课表配对的情侣课表（数据流）。
     * 情侣课表是独立的 CourseTable（isCouple=true），拥有自己的课程、作息与学期配置。
     */
    fun getCoupleTableFor(selfTableId: String): Flow<CourseTable?> {
        return courseTableDao.getCoupleTableByPairedId(selfTableId)
    }

    /**
     * 获取与本人课表配对的情侣课表（一次性）。
     */
    suspend fun getCoupleTableForOnce(selfTableId: String): CourseTable? {
        return courseTableDao.getCoupleTableByPairedIdOnce(selfTableId)
    }

    /**
     * 为本人课表创建配对情侣课表。
     * 复制本人课表的学期配置与默认作息方案作为初始值（之后可独立修改）。
     *
     * @param selfTableId 本人课表 ID
     * @param name 情侣课表名称；缺省用「<本人课表名> · 情侣」
     * @return 新情侣课表 ID；已存在配对情侣表时返回其 ID（幂等）
     */
    suspend fun createCoupleTable(selfTableId: String, name: String? = null): String {
        getCoupleTableForOnce(selfTableId)?.let { return it.id }

        val selfTable = courseTableDao.getCourseTableById(selfTableId)
            ?: throw IllegalArgumentException("本人课表不存在: $selfTableId")
        val coupleId = Uuid.random().toString()
        val coupleTable = CourseTable(
            id = coupleId,
            name = name ?: "${selfTable.name} · 情侣",
            createdAt = Clock.System.now().toEpochMilliseconds(),
            isCouple = true,
            pairedCourseTableId = selfTableId
        )

        // 复制本人课表的学期配置；无配置时用默认值
        val selfConfig = appSettingsRepository.getCourseTableConfigFlow(selfTableId).first()
        val coupleConfig = (selfConfig ?: CourseTableConfig(courseTableId = selfTableId))
            .copy(courseTableId = coupleId)

        // 复制本人课表**全部方案**的作息（config.currentSchemeId 可能指向非 default 方案）；
        // 本人表任何方案都没有 slots 时回落到出厂默认模板
        val schemeIds = timeSlotRepository.getSchemeIdsByCourseTableId(selfTableId).first()
        val coupleSlots = schemeIds.flatMap { schemeId ->
            timeSlotRepository.getTimeSlotsByCourseTableId(selfTableId, schemeId).first()
                .map { it.copy(courseTableId = coupleId) }
        }.ifEmpty {
            DEFAULT_TIME_SLOTS.map { it.copy(courseTableId = coupleId) }
        }

        database.withWriteTransaction {
            courseTableDao.insert(coupleTable)
            appSettingsRepository.insertOrUpdateCourseConfig(coupleConfig)
            timeSlotRepository.insertAll(coupleSlots)
            // 复制作息方案元信息（生效日期范围）
            timeSlotRepository.getSchemeMetasOnce(selfTableId).forEach { meta ->
                timeSlotRepository.upsertSchemeMeta(
                    meta.copy(courseTableId = coupleId)
                )
            }
        }
        return coupleId
    }

    /**
     * 创建一个新的课表。
     * 负责生成 ID 并执行插入操作，并**同步**为新课表创建默认时间段和配置。
     *
     * @param name 新课表的名称
     */
    suspend fun createNewCourseTable(name: String): String {
        val newTable = CourseTable(
            id = Uuid.random().toString(),
            name = name,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )

        val defaultTimeSlotsForNewTable = DEFAULT_TIME_SLOTS.map {
            it.copy(courseTableId = newTable.id)
        }
        val newConfig = CourseTableConfig(courseTableId = newTable.id)

        // 真事务：课表 + 默认时段 + 默认配置原子写入
        database.withWriteTransaction {
            courseTableDao.insert(newTable)
            timeSlotRepository.insertAll(defaultTimeSlotsForNewTable)
            appSettingsRepository.insertOrUpdateCourseConfig(newConfig)
        }

        return newTable.id
    }

    /**
     * 更新一个课表。
     */
    suspend fun updateCourseTable(courseTable: CourseTable) {
        courseTableDao.update(courseTable)
    }

    /**
     * 删除课表并确保当前课表指针始终指向仍存在的备用课表。
     *
     * DataStore 与 Room 不能跨存储事务，因此先持久化一个已经存在的备用 ID；
     * 若随后 Room 删除失败，用户只会停留在备用课表，而不会出现悬空 currentCourseTableId。
     *
     * 删除本人课表时级联删除其配对情侣课表（情侣课表依附于配对关系，不独立存活）。
     */
    suspend fun deleteCourseTableAndResolveCurrent(courseTable: CourseTable): Boolean {
        val allTables = courseTableDao.getAllCourseTables().first()
        if (allTables.size <= 1 || allTables.none { it.id == courseTable.id }) return false

        // 待删除集合：本人表 + 其配对情侣表（若有）
        val coupleOfSelf = if (!courseTable.isCouple) {
            courseTableDao.getCoupleTableByPairedIdOnce(courseTable.id)
        } else null
        val tablesToDelete = listOfNotNull(courseTable, coupleOfSelf)
        val deletedIds = tablesToDelete.map { it.id }.toSet()

        // 删除后必须至少剩余一张课表（例如仅剩「本人表+其情侣表」时删本人表应拒绝）
        // 删除情侣表时优先回退到其配对本人表，避免落到无关的历史学期
        val fallbackTable = if (courseTable.isCouple && courseTable.pairedCourseTableId != null &&
            allTables.any { it.id == courseTable.pairedCourseTableId && it.id !in deletedIds }
        ) {
            allTables.first { it.id == courseTable.pairedCourseTableId && it.id !in deletedIds }
        } else {
            allTables.firstOrNull { it.id !in deletedIds } ?: return false
        }
        val currentSettings = appSettingsRepository.getAppSettingsOnce()
        if (currentSettings.currentCourseTableId in deletedIds) {
            appSettingsRepository.insertOrUpdateAppSettings(
                currentSettings.copy(currentCourseTableId = fallbackTable.id)
            )
        }

        database.withWriteTransaction {
            tablesToDelete.forEach { courseTableDao.delete(it) }
        }
        return true
    }

    /**
     * 删除一个课表，并确保至少保留一个。
     *
     * @deprecated 请使用 [deleteCourseTableAndResolveCurrent]，避免 currentCourseTableId 悬空。
     */
    @Deprecated("Use deleteCourseTableAndResolveCurrent")
    suspend fun deleteCourseTable(courseTable: CourseTable): Boolean =
        deleteCourseTableAndResolveCurrent(courseTable)

    /**
     * 专门用于根据课程ID更新其颜色索引。
     * 这是实现无效颜色自动修复机制所需的关键方法（用于历史数据迁移）。
     *
     * @param courseId 课程的唯一ID。
     * @param newColorInt 新的颜色索引值 (0 到 11)。
     */
    suspend fun updateCourseColor(courseId: String, newColorInt: Int) {
        courseDao.updateCourseColorById(courseId, newColorInt)
    }

    /**
     * 插入或更新一个课程，并同时更新其对应的周数列表。
     */
    suspend fun upsertCourse(course: Course, weekNumbers: List<Int>) {
        // take(300) 会截取前 300 个字符，如果为 null 则返回 null
        val safeRemark = course.remark?.take(300)?.ifBlank { null }

        // 使用 copy 创建一个处理过备注的新对象
        val processedCourse = course.copy(remark = safeRemark)

        val courseWeeks = weekNumbers.map { week ->
            CourseWeek(courseId = processedCourse.id, weekNumber = week)
        }

        // 真事务：课程主体与周次关联原子更新
        database.withWriteTransaction {
            // 逻辑判断：如果数据库里已经有这个 ID
            if (courseDao.exists(processedCourse.id)) {
                // 使用 @Update 精准修改字段。
                courseDao.update(processedCourse)
            } else {
                // 如果是新 ID，则执行插入。
                courseDao.insertAll(listOf(processedCourse))
            }
            // 更新周次关联（保持不变）
            courseWeekDao.updateCourseWeeks(processedCourse.id, courseWeeks)
        }
    }

    /**
     * 删除一个课程。
     */
    suspend fun deleteCourse(course: Course) {
        courseDao.delete(course)
    }

    /**
     * 批量删除指定 ID 列表的课程实例。
     *
     * @param courseIds 要删除的课程的唯一ID列表。
     */
    suspend fun deleteCoursesByIds(courseIds: List<String>) {
        if (courseIds.isEmpty()) return
        courseDao.deleteCoursesByIds(courseIds)
    }

    /**
     * 批量删除指定课表下、指定名称的所有课程实例及其关联的周次记录。
     *
     * 依赖 Room 的 ForeignKey.CASCADE (在 CourseWeek 实体中定义)，
     * 此方法只需删除 Course 记录，CourseWeek 记录将自动被清理。
     *
     * @param tableId 课表的唯一ID。
     * @param courseNames 需要删除的课程名称列表。
     */
    suspend fun deleteCoursesByNames(tableId: String, courseNames: List<String>) {
        if (courseNames.isEmpty() || tableId.isBlank()) return
        courseDao.deleteCoursesByNames(tableId, courseNames)
    }

    // 在类内部定义枚举
    enum class TweakMode {
        MERGE,      // 数据合并：A -> B (单箭头)
        OVERWRITE,  // 数据覆盖：A >> B (双箭头)
        EXCHANGE    // 数据交换：A <-> B (双向箭头)
    }

    /**
     * 执行调课操作
     * @param mode 传入 TweakMode.MERGE, TweakMode.OVERWRITE 或 TweakMode.EXCHANGE
     */
    suspend fun tweakCoursesOnDate(
        mode: TweakMode,
        courseTableId: String,
        fromWeek: Int,
        fromDay: Int,
        toWeek: Int,
        toDay: Int
    ) {
        // 1. 获取来源(A)和目标(B)的数据快照（读操作放事务外）
        val sourceList = courseDao.getCoursesWithWeeksByDayAndWeek(courseTableId, fromDay, fromWeek).first()
        val targetList = courseDao.getCoursesWithWeeksByDayAndWeek(courseTableId, toDay, toWeek).first()

        // 真事务：调课涉及的多表多行写入全部原子化
        database.withWriteTransaction {
            when (mode) {
                TweakMode.MERGE -> {
                    // 直接移动 A -> B
                    executeMoveInternal(sourceList, toWeek, toDay, fromWeek)
                }

                TweakMode.OVERWRITE -> {
                    // 先删目标日期的周次，再移动 A -> B
                    if (targetList.isNotEmpty()) {
                        val targetIds = targetList.map { it.course.id }
                        courseWeekDao.deleteCourseWeeksForCourseAndWeek(targetIds, toWeek)
                    }
                    executeMoveInternal(sourceList, toWeek, toDay, fromWeek)
                }

                TweakMode.EXCHANGE -> {
                    // 互换：先切断双方现有周次联系
                    if (sourceList.isNotEmpty()) {
                        courseWeekDao.deleteCourseWeeksForCourseAndWeek(sourceList.map { it.course.id }, fromWeek)
                    }
                    if (targetList.isNotEmpty()) {
                        courseWeekDao.deleteCourseWeeksForCourseAndWeek(targetList.map { it.course.id }, toWeek)
                    }
                    // 执行交叉移动 (originalWeek = -1 表示不重复执行删除逻辑)
                    executeMoveInternal(sourceList, toWeek, toDay, -1)
                    executeMoveInternal(targetList, fromWeek, fromDay, -1)
                }
            }
        }
    }

    /**
     * 内部辅助函数：处理课程的物理移动或多周拆分逻辑
     */
    private suspend fun executeMoveInternal(
        items: List<CourseWithWeeks>,
        targetWeek: Int,
        targetDay: Int,
        originalWeek: Int
    ) {
        if (items.isEmpty()) return

        val newCourses = mutableListOf<Course>()
        val newWeeks = mutableListOf<CourseWeek>()
        val oldIdsToRemove = mutableListOf<String>()

        for (item in items) {
            val course = item.course
            // 判断是否为单周课程
            val isSingleWeek = item.weeks.size <= 1

            if (isSingleWeek) {
                // 优化：只有一周的课，直接更新 Course 表的 Day 字段
                courseDao.update(course.copy(day = targetDay))
                if (originalWeek != -1) {
                    courseWeekDao.deleteCourseWeeksForCourseAndWeek(listOf(course.id), originalWeek)
                }
                courseWeekDao.insertAll(listOf(CourseWeek(courseId = course.id, weekNumber = targetWeek)))
            } else {
                // 优化：多周课，克隆新 ID 专门用于目标日期
                val newId = Uuid.random().toString()
                newCourses.add(course.copy(id = newId, day = targetDay))
                newWeeks.add(CourseWeek(courseId = newId, weekNumber = targetWeek))
                if (originalWeek != -1) oldIdsToRemove.add(course.id)
            }
        }

        // 批量执行数据库变更
        if (oldIdsToRemove.isNotEmpty()) {
            courseWeekDao.deleteCourseWeeksForCourseAndWeek(oldIdsToRemove, originalWeek)
        }
        if (newCourses.isNotEmpty()) {
            courseDao.insertAll(newCourses)
            courseWeekDao.insertAll(newWeeks)
        }
    }

    /**
     * 快速删除：仅移除特定周次的记录，不物理删除课程定义。
     * * @param tableId 课表唯一 ID
     * @param weekDayPairs 周次与星期的组合列表 (Pair<周次, 星期>)
     */
    suspend fun deleteCoursesOnDates(
        tableId: String,
        weekDayPairs: List<Pair<Int, Int>>
    ) {
        // 先收集所有待删除的 (课程ID列表, 周次)，读操作放事务外
        val deletions = mutableListOf<Pair<List<String>, Int>>()
        weekDayPairs.forEach { (week, day) ->
            // 查找在该课表、该周、该天下的所有课程记录
            val coursesToDelete = getCoursesForDay(tableId, week, day).first()

            if (coursesToDelete.isNotEmpty()) {
                deletions += coursesToDelete.map { it.course.id } to week
            }
        }

        if (deletions.isEmpty()) return

        // 真事务：整批周次关联删除原子化
        database.withWriteTransaction {
            deletions.forEach { (ids, week) ->
                // 仅仅从 course_weeks 表中移除对应周次的关联，不触动 course 表
                courseWeekDao.deleteCourseWeeksForCourseAndWeek(ids, week)
            }
        }
    }

    /**
     * 获取指定课表、周次和星期下的课程，并以数据流形式返回。
     * 这个方法专为 UI 层提供实时更新的数据。
     */
    fun getCoursesForDay(
        courseTableId: String,
        weekNumber: Int,
        day: Int
    ): Flow<List<CourseWithWeeks>> {
        // 使用简单查询获取全部课程，再在代码中过滤周次和星期
        // 避免子查询/EXISTS + @Relation 在 Room 3.0.1 下可能导致的结果丢失问题
        return courseDao.getCoursesWithWeeksByTableId(courseTableId).map { allCourses ->
            allCourses.filter { cw ->
                cw.course.day == day && cw.weeks.any { it.weekNumber == weekNumber }
            }.sortedWith(
                compareBy<CourseWithWeeks> {
                    if (it.course.isCustomTime) 99 else it.course.startSection ?: 99
                }.thenBy {
                    if (it.course.isCustomTime) it.course.customStartTime ?: "99:99" else "99:99"
                }
            )
        }
    }

    /**
     * 根据物理日期和配置，获取该周的所有课程。
     * 此函数是重构“真日历”模式的关键，它实现了从“日期”到“课程数据”的直接映射。
     */
    fun getCoursesWithWeeksByDate(
        courseTableId: String,
        targetDate: LocalDate,
        config: CourseTableConfig
    ): Flow<List<CourseWithWeeks>> {
        val weekNumber = appSettingsRepository.getWeekIndexAtDate(
            targetDate = targetDate,
            startDateStr = config.semesterStartDate,
            firstDayOfWeekInt = config.firstDayOfWeek
        )

        // 如果周次为 null（说明没设开学日期）
        if (weekNumber == null) {
            return flowOf(emptyList())
        }

        // 使用简单查询获取全部课程，再在代码中过滤周次（返回整周课程，不按天过滤）
        // 避免子查询/EXISTS + @Relation 在 Room 3.0.1 下可能导致的结果丢失问题
        return courseDao.getCoursesWithWeeksByTableId(courseTableId).map { allCourses ->
            allCourses.filter { cw ->
                cw.weeks.any { it.weekNumber == weekNumber }
            }
        }
    }
}

/**
 * 新课表的默认时间段模板（13 节）。
 * 同时供「时间段管理 → 恢复默认」读取，保证恢复结果与新建课表种子一致。
 */
val DEFAULT_TIME_SLOTS: List<TimeSlot> = listOf(
    TimeSlot(number = 1, startTime = "08:00", endTime = "08:45", courseTableId = "placeholder"),
    TimeSlot(number = 2, startTime = "08:50", endTime = "09:35", courseTableId = "placeholder"),
    TimeSlot(number = 3, startTime = "09:50", endTime = "10:35", courseTableId = "placeholder"),
    TimeSlot(number = 4, startTime = "10:40", endTime = "11:25", courseTableId = "placeholder"),
    TimeSlot(number = 5, startTime = "11:30", endTime = "12:15", courseTableId = "placeholder"),
    TimeSlot(number = 6, startTime = "14:00", endTime = "14:45", courseTableId = "placeholder"),
    TimeSlot(number = 7, startTime = "14:50", endTime = "15:35", courseTableId = "placeholder"),
    TimeSlot(number = 8, startTime = "15:45", endTime = "16:30", courseTableId = "placeholder"),
    TimeSlot(number = 9, startTime = "16:35", endTime = "17:20", courseTableId = "placeholder"),
    TimeSlot(number = 10, startTime = "18:30", endTime = "19:15", courseTableId = "placeholder"),
    TimeSlot(number = 11, startTime = "19:20", endTime = "20:05", courseTableId = "placeholder"),
    TimeSlot(number = 12, startTime = "20:10", endTime = "20:55", courseTableId = "placeholder"),
    TimeSlot(number = 13, startTime = "21:10", endTime = "21:55", courseTableId = "placeholder")
)