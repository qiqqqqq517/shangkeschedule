package com.shangkeschedule.data.db.main

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

/**
 * 成绩表的数据访问对象。
 *
 * 约定与仓库内其它 DAO 保持一致：
 * - 只读列表返回 [Flow]，一次性读取返回 `*Once()` 挂起函数；
 * - 主键重复时由调用方决定策略，故分别提供 [insertAll]（ABORT，手动新增时暴露冲突）
 *   与 [upsertAll]（REPLACE，教务抓取/粘贴导入时覆盖同名同学期记录）。
 */
@Dao
interface GradeDao {

    /** 全部成绩，按学期倒序、同学期内按课程名排序。 */
    @Query("SELECT * FROM grades ORDER BY semester DESC, courseName ASC")
    fun getAllGrades(): Flow<List<Grade>>

    /** 全部成绩的一次性快照（导出/GPA 汇总计算用）。 */
    @Query("SELECT * FROM grades ORDER BY semester DESC, courseName ASC")
    suspend fun getAllGradesOnce(): List<Grade>

    /** 指定学期的成绩，按课程名排序。 */
    @Query("SELECT * FROM grades WHERE semester = :semester ORDER BY courseName ASC")
    fun getGradesBySemester(semester: String): Flow<List<Grade>>

    /** 按学期查询，用于导入前查重。 */
    @Query("SELECT * FROM grades WHERE semester = :semester AND courseName = :courseName LIMIT 1")
    suspend fun findByNameOnce(semester: String, courseName: String): Grade?

    /** 按 id 查询。 */
    @Query("SELECT * FROM grades WHERE id = :gradeId")
    suspend fun getByIdOnce(gradeId: String): Grade?

    /** 是否存在指定 id（缓存/查重场景）。 */
    @Query("SELECT EXISTS(SELECT 1 FROM grades WHERE id = :gradeId)")
    suspend fun exists(gradeId: String): Boolean

    /** 批量插入；主键冲突时抛异常。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(grades: List<Grade>)

    /** 批量插入或覆盖（教务抓取、粘贴导入重复执行时应覆盖旧值而不是报错）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(grades: List<Grade>)

    /** 更新单条成绩。 */
    @Update
    suspend fun update(grade: Grade)

    /** 删除单条成绩。 */
    @Query("DELETE FROM grades WHERE id = :gradeId")
    suspend fun deleteById(gradeId: String)

    /** 清空全部成绩（危险操作，UI 侧必须二次确认）。 */
    @Query("DELETE FROM grades")
    suspend fun deleteAll()

    /** 已有成绩的学期列表（倒序），用于分组与筛选。 */
    @Query("SELECT DISTINCT semester FROM grades ORDER BY semester DESC")
    fun getSemesters(): Flow<List<String>>
}
