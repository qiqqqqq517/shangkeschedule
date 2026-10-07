package com.shangkeschedule.data.db.main

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

/**
 * 培养方案课程表的数据访问对象（v4.75.0）。
 *
 * 约定与仓库内其它 DAO 一致：只读列表返回 [Flow]，一次性读取返回 `*Once()` 挂起函数；
 * 分别提供 [insertAll]（ABORT）与 [upsertAll]（REPLACE）。
 */
@Dao
interface CurriculumCourseDao {

    /** 全部培养方案课程，按类别、课程名排序。 */
    @Query("SELECT * FROM curriculum_courses ORDER BY category ASC, courseName ASC")
    fun getAll(): Flow<List<CurriculumCourse>>

    /** 全部培养方案课程的一次性快照（导出 / 学业汇总计算用）。 */
    @Query("SELECT * FROM curriculum_courses ORDER BY category ASC, courseName ASC")
    suspend fun getAllOnce(): List<CurriculumCourse>

    /** 按 id 查询。 */
    @Query("SELECT * FROM curriculum_courses WHERE id = :courseId")
    suspend fun getByIdOnce(courseId: String): CurriculumCourse?

    /** 按课程名查询（去重判定用）。 */
    @Query("SELECT * FROM curriculum_courses WHERE courseName = :courseName LIMIT 1")
    suspend fun findByNameOnce(courseName: String): CurriculumCourse?

    /** 批量插入；主键冲突时抛异常。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<CurriculumCourse>)

    /** 批量插入或覆盖（粘贴 / 教务导入重复执行时应覆盖旧值而不是报错）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CurriculumCourse>)

    /** 更新单条。 */
    @Update
    suspend fun update(item: CurriculumCourse)

    /** 删除单条。 */
    @Query("DELETE FROM curriculum_courses WHERE id = :courseId")
    suspend fun deleteById(courseId: String)

    /** 清空全部（UI 侧必须二次确认）。 */
    @Query("DELETE FROM curriculum_courses")
    suspend fun deleteAll()
}
