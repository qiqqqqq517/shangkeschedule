package com.shangkeschedule.data.repository

import androidx.room3.withWriteTransaction
import com.shangkeschedule.data.db.main.Course
import com.shangkeschedule.data.db.main.CourseDao
import com.shangkeschedule.data.db.main.CourseTableConfigDao
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.main.TimeSlotDao
import com.shangkeschedule.data.db.main.TimeSlotScheme
import com.shangkeschedule.data.db.main.TimeSlotSchemeDao
import com.shangkeschedule.data.time.currentDateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.Clock

/**
 * 时间段数据仓库。
 *
 * 除基础的时间段 CRUD 外，还负责“作息方案（夏令时/冬令时）”的元信息管理，
 * 以及根据当前日期自动解析应生效的作息方案。
 */
@Single
class TimeSlotRepository(
    private val database: MainAppDatabase,
    private val timeSlotDao: TimeSlotDao,
    private val timeSlotSchemeDao: TimeSlotSchemeDao,
    private val courseTableConfigDao: CourseTableConfigDao,
    private val courseDao: CourseDao
) {
    /**
     * 获取指定课表、指定作息方案的所有时间段，返回一个数据流。
     */
    fun getTimeSlotsByCourseTableId(courseTableId: String, schemeId: String = TimeSlot.DEFAULT_SCHEME_ID): Flow<List<TimeSlot>> {
        return timeSlotDao.getTimeSlotsByCourseTableId(courseTableId, schemeId)
    }

    /**
     * 获取指定课表下所有不重复的作息方案ID，返回一个数据流。
     */
    fun getSchemeIdsByCourseTableId(courseTableId: String): Flow<List<String>> {
        return timeSlotDao.getSchemeIdsByCourseTableId(courseTableId)
    }

    /**
     * 插入或更新所有时间段。
     */
    suspend fun insertAll(timeSlots: List<TimeSlot>) {
        timeSlotDao.insertAll(timeSlots)
    }

    /**
     * 完全替换指定课表、指定作息方案下的所有时间段。
     */
    suspend fun replaceAllForCourseTable(courseTableId: String, timeSlots: List<TimeSlot>, schemeId: String = TimeSlot.DEFAULT_SCHEME_ID) {
        database.withWriteTransaction {
            timeSlotDao.deleteTimeSlotsByScheme(courseTableId, schemeId)
            if (timeSlots.isNotEmpty()) {
                timeSlotDao.insertAll(timeSlots.map { it.copy(courseTableId = courseTableId, schemeId = schemeId) })
            }
        }
    }

    /** 保存当前方案的时间段及课表默认时长，保证两部分同时成功。 */
    suspend fun saveSchemeSettings(
        courseTableId: String,
        timeSlots: List<TimeSlot>,
        schemeId: String,
        config: CourseTableConfig
    ) {
        database.withWriteTransaction {
            // 事务内先读取旧时间段（供删除后迁移课程节次引用）
            val oldSlots = timeSlotDao.getTimeSlotsOnce(courseTableId, schemeId)

            timeSlotDao.deleteTimeSlotsByScheme(courseTableId, schemeId)
            if (timeSlots.isNotEmpty()) {
                timeSlotDao.insertAll(timeSlots.map { it.copy(courseTableId = courseTableId, schemeId = schemeId) })
            }
            courseTableConfigDao.insertOrUpdate(config)

            // 时间段删除/重编号后，把受影响的课程节次引用迁移到新编号，避免课程静默错位
            migrateCourseSections(courseTableId, oldSlots, timeSlots)
        }
    }

    /**
     * 时间段保存后迁移课程节次引用。
     *
     * 时间段删除一个中间节次后会在 UI 层按 startTime 重排编号（number），但数据库里
     * Course.startSection / endSection 仍指向旧编号。此处按 startTime 匹配新旧时间段，
     * 算出旧编号 → 新编号映射，把受影响课程（标准节次、非 crush）的节次引用一并前移，
     * 防止删除中间节次后课程静默对齐到错误的时间。
     */
    private suspend fun migrateCourseSections(
        courseTableId: String,
        oldSlots: List<TimeSlot>,
        newSlots: List<TimeSlot>
    ) {
        // 首次创建或旧时间段为空：无需迁移
        if (oldSlots.isEmpty()) return

        val newByStartTime = newSlots.associateBy { it.startTime }
        // 旧编号 → 新编号（按 startTime 匹配）；被删除的旧编号收集到 removed 集合
        val mapping = mutableMapOf<Int, Int>()
        val removed = mutableSetOf<Int>()
        for (old in oldSlots) {
            val matched = newByStartTime[old.startTime]
            if (matched != null) mapping[old.number] = matched.number else removed.add(old.number)
        }

        // 编号完全没变且无删除：无需迁移
        val anyChange = removed.isNotEmpty() || mapping.any { (k, v) -> k != v }
        if (!anyChange) return

        val courses = courseDao.getCoursesOnce(courseTableId)
        for (course in courses) {
            // 只迁移标准节次、非 crush 课程；自定义时间课程不依赖节次编号
            if (course.isCustomTime || course.startSection == null) continue
            val newStart = mapping[course.startSection] ?: course.startSection
            val newEnd = course.endSection?.let { mapping[it] ?: it }
            if (newStart == course.startSection && newEnd == course.endSection) continue
            courseDao.update(course.copy(startSection = newStart, endSection = newEnd))
        }
    }

    /** 创建新方案：复制时间段并切换当前方案，全部在同一事务内提交。 */
    suspend fun createScheme(
        courseTableId: String,
        schemeId: String,
        templateSlots: List<TimeSlot>,
        currentConfig: CourseTableConfig
    ) {
        database.withWriteTransaction {
            if (templateSlots.isNotEmpty()) {
                timeSlotDao.insertAll(templateSlots.map { it.copy(courseTableId = courseTableId, schemeId = schemeId) })
            }
            courseTableConfigDao.insertOrUpdate(currentConfig.copy(currentSchemeId = schemeId))
        }
    }

    /** 删除方案：必要时先切回默认方案，再删除时间段和元数据，全部原子化。 */
    suspend fun deleteScheme(courseTableId: String, schemeId: String) {
        database.withWriteTransaction {
            val currentConfig = courseTableConfigDao.getConfigOnce(courseTableId)
            if (currentConfig?.currentSchemeId == schemeId) {
                courseTableConfigDao.insertOrUpdate(currentConfig.copy(currentSchemeId = TimeSlot.DEFAULT_SCHEME_ID))
            }
            timeSlotDao.deleteTimeSlotsByScheme(courseTableId, schemeId)
            timeSlotSchemeDao.deleteScheme(courseTableId, schemeId)
        }
    }

    // ─── 作息方案元信息（夏令时/冬令时生效日期范围） ───

    /**
     * 获取指定课表下所有作息方案的元信息（生效日期范围），返回数据流。
     */
    fun getSchemeMetasByCourseTableId(courseTableId: String): Flow<List<TimeSlotScheme>> {
        return timeSlotSchemeDao.getSchemesByCourseTableId(courseTableId)
    }

    /**
     * 一次性获取指定课表下所有作息方案的元信息。
     */
    suspend fun getSchemeMetasOnce(courseTableId: String): List<TimeSlotScheme> {
        return timeSlotSchemeDao.getSchemesOnce(courseTableId)
    }

    /**
     * 插入或更新某套作息方案的元信息（生效日期范围）。
     */
    suspend fun upsertSchemeMeta(scheme: TimeSlotScheme) {
        timeSlotSchemeDao.insertOrUpdate(scheme)
    }

    /**
     * 根据课表配置与方案元信息，解析当前日期应生效的作息方案 ID。
     *
     * 当未开启自动切换、或没有任何方案匹配当前日期时，回退到手动选择的方案
     * （config.currentSchemeId）；仍为空时回退到默认方案。
     */
    fun resolveActiveSchemeId(
        config: CourseTableConfig?,
        schemeMetas: List<TimeSlotScheme>,
        today: LocalDate
    ): String {
        val fallback = config?.currentSchemeId ?: TimeSlot.DEFAULT_SCHEME_ID
        if (config?.autoSwitchScheme != true) return fallback

        val monthDay = today.monthNumber * 100 + today.dayOfMonth
        val matched = schemeMetas.firstOrNull { it.isActiveAt(monthDay) }
        return matched?.schemeId ?: fallback
    }

    /**
     * 统一入口：根据课表配置流，实时返回“当前应生效作息方案”下的时间段列表。
     *
     * 开启自动切换时，会同时监听方案元信息变化并按当天日期自动解析方案；
     * 日期来自 [currentDateFlow]，跨天（午夜）自动重算，夏/冬令时切换不再滞留到下一次配置变化。
     * 未开启时直接使用手动选择的方案。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getActiveTimeSlotsByConfigFlow(
        courseTableId: String,
        configFlow: Flow<CourseTableConfig?>
    ): Flow<List<TimeSlot>> {
        if (courseTableId.isEmpty()) return flowOf(emptyList())
        return configFlow.flatMapLatest { config ->
            if (config?.autoSwitchScheme == true) {
                combine(
                    timeSlotSchemeDao.getSchemesByCourseTableId(courseTableId),
                    currentDateFlow()
                ) { schemes, today ->
                    resolveActiveSchemeId(config, schemes, today)
                }
                    .distinctUntilChanged()
                    .flatMapLatest { schemeId ->
                        timeSlotDao.getTimeSlotsByCourseTableId(courseTableId, schemeId)
                    }
            } else {
                timeSlotDao.getTimeSlotsByCourseTableId(
                    courseTableId,
                    config?.currentSchemeId ?: TimeSlot.DEFAULT_SCHEME_ID
                )
            }
        }
    }

    /**
     * 一次性获取“当前应生效作息方案”下的时间段列表。
     */
    suspend fun getActiveTimeSlotsOnce(courseTableId: String, config: CourseTableConfig?): List<TimeSlot> {
        if (courseTableId.isEmpty()) return emptyList()
        val schemeId = if (config?.autoSwitchScheme == true) {
            val schemes = timeSlotSchemeDao.getSchemesOnce(courseTableId)
            resolveActiveSchemeId(config, schemes, today())
        } else {
            config?.currentSchemeId ?: TimeSlot.DEFAULT_SCHEME_ID
        }
        return timeSlotDao.getTimeSlotsByCourseTableId(courseTableId, schemeId).first()
    }

    private fun today(): LocalDate {
        return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
}
