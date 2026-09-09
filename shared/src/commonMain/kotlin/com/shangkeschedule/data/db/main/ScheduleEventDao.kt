package com.shangkeschedule.data.db.main

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room 数据访问对象 (DAO)，用于操作日程事件 (ScheduleEvent) 数据表。
 *
 * 按日期查询（当日日程列表）与按范围查询（月历圆点指示）是「日程」页的核心路径；
 * 其余为编辑/删除操作。
 */
@Dao
interface ScheduleEventDao {

    /**
     * 获取指定日期（yyyy-MM-dd）的所有日程，返回数据流（实时响应）。
     * 排序：全天日程在最前，其余按开始时间，无时间日程按创建顺序沉底。
     */
    @Query(
        """
        SELECT * FROM schedule_events
        WHERE date = :date
        ORDER BY isAllDay DESC, (startTime IS NULL OR startTime = '') ASC, startTime ASC, createdAt ASC
        """
    )
    fun getEventsByDate(date: String): Flow<List<ScheduleEvent>>

    /**
     * 获取日期范围 [startDate, endDate] 内的所有日程（含边界），返回数据流。
     * 日期字符串为 yyyy-MM-dd，字典序即时间序；供月历圆点指示按日聚合。
     */
    @Query(
        """
        SELECT * FROM schedule_events
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date ASC, isAllDay DESC, (startTime IS NULL OR startTime = '') ASC, startTime ASC, createdAt ASC
        """
    )
    fun getEventsBetweenDates(startDate: String, endDate: String): Flow<List<ScheduleEvent>>

    /**
     * 检查指定 ID 的日程是否存在，用于 Repository 判断插入还是更新。
     */
    @Query("SELECT EXISTS(SELECT 1 FROM schedule_events WHERE id = :eventId)")
    suspend fun exists(eventId: String): Boolean

    /**
     * 插入日程。策略 ABORT，配合 Repository 的 exists 检查避免意外覆盖。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(events: List<ScheduleEvent>)

    /**
     * 更新日程（仅改字段，不删除行）。
     */
    @Update
    suspend fun update(event: ScheduleEvent)

    /**
     * 按 ID 删除单个日程。
     */
    @Query("DELETE FROM schedule_events WHERE id = :eventId")
    suspend fun deleteById(eventId: String)
}
