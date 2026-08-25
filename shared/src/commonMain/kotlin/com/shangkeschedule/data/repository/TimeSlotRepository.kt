package com.shangkeschedule.data.repository

import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.main.TimeSlotDao
import com.shangkeschedule.data.db.main.TimeSlotScheme
import com.shangkeschedule.data.db.main.TimeSlotSchemeDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
    private val timeSlotDao: TimeSlotDao,
    private val timeSlotSchemeDao: TimeSlotSchemeDao
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
     * 完全替换指定课表、指定作息方案下的所有时间段数据。
     */
    suspend fun replaceAllForCourseTable(courseTableId: String, timeSlots: List<TimeSlot>, schemeId: String = TimeSlot.DEFAULT_SCHEME_ID) {
        timeSlotDao.deleteTimeSlotsByScheme(courseTableId, schemeId)
        if (timeSlots.isNotEmpty()) {
            timeSlotDao.insertAll(timeSlots)
        }
    }

    /**
     * 删除指定课表、指定作息方案下的所有时间段。
     */
    suspend fun deleteScheme(courseTableId: String, schemeId: String) {
        timeSlotDao.deleteTimeSlotsByScheme(courseTableId, schemeId)
        timeSlotSchemeDao.deleteScheme(courseTableId, schemeId)
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
                timeSlotSchemeDao.getSchemesByCourseTableId(courseTableId).flatMapLatest { schemes ->
                    val schemeId = resolveActiveSchemeId(config, schemes, today())
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
