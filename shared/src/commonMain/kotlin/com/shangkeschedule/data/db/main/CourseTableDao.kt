package com.shangkeschedule.data.db.main

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Delete
import kotlinx.coroutines.flow.Flow

/**
 * Room 数据访问对象 (DAO)，用于操作课表 (CourseTable) 数据表。
 */
@Dao
interface CourseTableDao {
    /**
     * 获取所有课表，并按创建时间倒序排列。
     */
    @Query("SELECT * FROM course_tables ORDER BY createdAt DESC")
    fun getAllCourseTables(): Flow<List<CourseTable>>

    /**
     * 根据 ID 获取单个课表。
     */
    @Query("SELECT * FROM course_tables WHERE id = :tableId LIMIT 1")
    suspend fun getCourseTableById(tableId: String): CourseTable?

    /**
     * 插入一个新的课表。
     *
     * 冲突策略必须是 **ABORT**（不得改回 REPLACE）：`course_tables` 是根表，
     * `Course` / `CourseWeek` / `TimeSlot` / `TimeSlotScheme` / `CourseTableConfig`
     * 均以 `onDelete = ForeignKey.CASCADE` 挂在它下面。SQLite 的
     * `INSERT OR REPLACE` 语义是 **先 DELETE 再 INSERT**，一旦 id 冲突，
     * 会连带把该课表下的**全部课程、周次、作息**静默删光（无异常、无日志），
     * 而 `insert` 的调用点全部在最常用的建表 / 导入 / 备份恢复入口上。
     *
     * 所有调用点都是「先查后插新 ID」或「先 delete 再插」的形式，正常路径不依赖
     * REPLACE 的覆盖语义；改用 ABORT 后，真出现 id 冲突会显式抛错（而非静默丢数据），
     * 由调用点所在的事务回滚并上报。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(courseTable: CourseTable)

    /**
     * 更新一个现有课表。
     */
    @Update
    suspend fun update(courseTable: CourseTable)

    /**
     * 删除一个或多个课表。
     * 由于外键设置为 `onDelete = ForeignKey.CASCADE`，
     * 删除课表时，其下的所有课程也会被自动删除。
     */
    @Delete
    suspend fun delete(courseTable: CourseTable)

    /**
     * 根据课表ID删除单个课表。
     */
    @Query("DELETE FROM course_tables WHERE id = :tableId")
    suspend fun deleteById(tableId: String)


    /**
     * 获取最早创建的一张课表。
     * 用于在 DataStore 迁移或初始化时，确定默认显示的课表 ID。
     */
    @Query("SELECT * FROM course_tables ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirstTableOnce(): CourseTable?

    /**
     * 获取最早创建的一张课表（数据流）。
     * 与 [getFirstTableOnce] 同序（createdAt ASC），供全局设置热流响应表增删（v3.54.0）。
     */
    @Query("SELECT * FROM course_tables ORDER BY createdAt ASC LIMIT 1")
    fun getFirstTableFlow(): Flow<CourseTable?>

    /**
     * 获取与本人课表配对的情侣课表（数据流）。
     */
    @Query("SELECT * FROM course_tables WHERE isCouple = 1 AND pairedCourseTableId = :selfTableId LIMIT 1")
    fun getCoupleTableByPairedId(selfTableId: String): Flow<CourseTable?>

    /**
     * 获取与本人课表配对的情侣课表（一次性）。
     */
    @Query("SELECT * FROM course_tables WHERE isCouple = 1 AND pairedCourseTableId = :selfTableId LIMIT 1")
    suspend fun getCoupleTableByPairedIdOnce(selfTableId: String): CourseTable?
}