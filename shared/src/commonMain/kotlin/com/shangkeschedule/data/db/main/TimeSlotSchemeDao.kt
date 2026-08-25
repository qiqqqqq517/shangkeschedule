package com.shangkeschedule.data.db.main

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room 数据访问对象 (DAO)，用于操作作息方案元信息 (TimeSlotScheme) 数据表。
 */
@Dao
interface TimeSlotSchemeDao {

    /**
     * 获取指定课表下所有作息方案的元信息（生效日期范围），返回数据流。
     */
    @Query("SELECT * FROM time_slot_schemes WHERE courseTableId = :courseTableId ORDER BY schemeId ASC")
    fun getSchemesByCourseTableId(courseTableId: String): Flow<List<TimeSlotScheme>>

    /**
     * 一次性获取指定课表下所有作息方案的元信息。
     */
    @Query("SELECT * FROM time_slot_schemes WHERE courseTableId = :courseTableId ORDER BY schemeId ASC")
    suspend fun getSchemesOnce(courseTableId: String): List<TimeSlotScheme>

    /**
     * 插入或更新一条作息方案元信息。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(scheme: TimeSlotScheme)

    /**
     * 删除指定课表下某个作息方案的元信息。
     */
    @Query("DELETE FROM time_slot_schemes WHERE courseTableId = :courseTableId AND schemeId = :schemeId")
    suspend fun deleteScheme(courseTableId: String, schemeId: String)
}
