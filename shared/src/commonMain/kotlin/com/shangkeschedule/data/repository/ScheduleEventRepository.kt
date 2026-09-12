package com.shangkeschedule.data.repository

import androidx.room3.withWriteTransaction
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.db.main.ScheduleCategory
import com.shangkeschedule.data.db.main.ScheduleEvent
import com.shangkeschedule.data.db.main.ScheduleEventDao
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 日程数据仓库，负责与「日程」页相关的业务逻辑与数据操作。
 *
 * 日程为全局数据（不随课表切换），按日期归属，供月历圆点与当日日程列表展示。
 */
@OptIn(ExperimentalUuidApi::class)
@Single
class ScheduleEventRepository(
    private val database: MainAppDatabase,
    private val scheduleEventDao: ScheduleEventDao
) {
    /**
     * 获取指定日期（yyyy-MM-dd）的所有日程，返回数据流（实时响应）。
     */
    fun getEventsByDate(date: String): Flow<List<ScheduleEvent>> {
        return scheduleEventDao.getEventsByDate(date)
    }

    /**
     * 获取日期范围 [startDate, endDate] 内的所有日程（含边界），返回数据流。
     * 供月历圆点指示按日聚合。
     */
    fun getEventsBetweenDates(startDate: String, endDate: String): Flow<List<ScheduleEvent>> {
        return scheduleEventDao.getEventsBetweenDates(startDate, endDate)
    }

    /**
     * 新增一条日程。
     *
     * @param date 归属日期 "yyyy-MM-dd"。
     * @param title 日程标题（自动截断到 300 字）。
     * @param category 分类（key 存库）。
     * @param isAllDay 是否全天。
     * @param startTime 开始时间 "HH:mm"（全天为 null）。
     * @param endTime 结束时间 "HH:mm"（全天为 null）。
     * @param location 地点（自动截断到 200 字，空白归一为 null）。
     * @param note 备注（自动截断到 500 字，空白归一为 null）。
     * @return 已入库的 [ScheduleEvent]。
     */
    suspend fun addEvent(
        date: String,
        title: String,
        category: ScheduleCategory,
        isAllDay: Boolean,
        startTime: String?,
        endTime: String?,
        location: String?,
        note: String?
    ): ScheduleEvent {
        val now = Clock.System.now().toEpochMilliseconds()
        val event = ScheduleEvent(
            id = Uuid.random().toString(),
            date = date,
            title = title.take(300),
            category = category.key,
            isAllDay = isAllDay,
            startTime = if (isAllDay) null else startTime?.takeIf { it.isNotBlank() },
            endTime = if (isAllDay) null else endTime?.takeIf { it.isNotBlank() },
            location = location?.take(200)?.ifBlank { null },
            note = note?.take(500)?.ifBlank { null },
            createdAt = now,
            updatedAt = now
        )
        database.withWriteTransaction {
            scheduleEventDao.insertAll(listOf(event))
        }
        return event
    }

    /**
     * 更新一条日程。不存在时回退为插入。
     */
    suspend fun updateEvent(event: ScheduleEvent) {
        val updated = event.copy(
            title = event.title.take(300),
            category = ScheduleCategory.fromKey(event.category).key,
            location = event.location?.take(200)?.ifBlank { null },
            note = event.note?.take(500)?.ifBlank { null },
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )
        database.withWriteTransaction {
            if (scheduleEventDao.exists(updated.id)) {
                scheduleEventDao.update(updated)
            } else {
                scheduleEventDao.insertAll(listOf(updated))
            }
        }
    }

    /**
     * 设置日程的完成状态（仅「待办」分类的日程在今日页使用）。
     */
    suspend fun setDone(eventId: String, done: Boolean) {
        database.withWriteTransaction {
            scheduleEventDao.updateDone(eventId, done)
        }
    }

    /**
     * 删除指定日程。
     */
    suspend fun deleteEvent(eventId: String) {
        database.withWriteTransaction {
            scheduleEventDao.deleteById(eventId)
        }
    }
}
