package com.shangkeschedule.data.db.main

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room 数据访问对象 (DAO)，用于操作时间段 (TimeSlot) 数据表。
 */
@Dao
interface TimeSlotDao {
    /**
     * 获取指定课表、指定作息方案的所有时间段，并按节次编号升序排列。
     */
    @Query("SELECT * FROM time_slots WHERE courseTableId = :courseTableId AND schemeId = :schemeId ORDER BY number ASC")
    fun getTimeSlotsByCourseTableId(courseTableId: String, schemeId: String): Flow<List<TimeSlot>>

    /**
     * 获取指定课表下所有不重复的作息方案ID。
     */
    @Query("SELECT DISTINCT schemeId FROM time_slots WHERE courseTableId = :courseTableId")
    fun getSchemeIdsByCourseTableId(courseTableId: String): Flow<List<String>>

    /**
     * 根据节次编号、课表ID和作息方案ID获取单个时间段。
     */
    @Query("SELECT * FROM time_slots WHERE number = :number AND courseTableId = :courseTableId AND schemeId = :schemeId LIMIT 1")
    suspend fun getTimeSlot(number: Int, courseTableId: String, schemeId: String): TimeSlot?

    /**
     * 插入一个或多个时间段。如果发生主键冲突，则替换旧数据。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(timeSlots: List<TimeSlot>)

    /**
     * 更新一个现有时间段。
     */
    @Update
    suspend fun update(timeSlot: TimeSlot)

    /**
     * 删除一个或多个时间段。
     */
    @Delete
    suspend fun delete(timeSlot: TimeSlot)

    /**
     * 根据课表ID删除所有时间段。
     */
    @Query("DELETE FROM time_slots WHERE courseTableId = :courseTableId")
    suspend fun deleteAllTimeSlotsByCourseTableId(courseTableId: String)

    /**
     * 根据课表ID和作息方案ID删除该方案下的所有时间段。
     */
    @Query("DELETE FROM time_slots WHERE courseTableId = :courseTableId AND schemeId = :schemeId")
    suspend fun deleteTimeSlotsByScheme(courseTableId: String, schemeId: String)
}