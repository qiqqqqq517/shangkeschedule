package com.shangkeschedule.data.db.main

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * 课堂笔记 DAO（v4.66.0）。
 *
 * 所有查询都按「课次日期倒序 + 创建时间倒序」排列：最近一次课排在最前，符合课后补记的习惯。
 */
@Dao
interface CourseNoteDao {

    @Query("SELECT * FROM course_notes ORDER BY date DESC, createdAt DESC")
    fun getAllNotes(): Flow<List<CourseNote>>

    @Query("SELECT * FROM course_notes ORDER BY date DESC, createdAt DESC")
    suspend fun getAllNotesOnce(): List<CourseNote>

    @Query("SELECT * FROM course_notes WHERE courseId = :courseId ORDER BY date DESC, createdAt DESC")
    fun getNotesForCourse(courseId: String): Flow<List<CourseNote>>

    @Query("SELECT * FROM course_notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteOnce(noteId: String): CourseNote?

    @Query("SELECT COUNT(*) FROM course_notes")
    fun countFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: CourseNote)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<CourseNote>)

    @Query("DELETE FROM course_notes WHERE id = :noteId")
    suspend fun deleteById(noteId: String)

    @Query("DELETE FROM course_notes")
    suspend fun deleteAll()
}
