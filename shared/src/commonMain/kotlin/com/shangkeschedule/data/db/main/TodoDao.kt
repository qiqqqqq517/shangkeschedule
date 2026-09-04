package com.shangkeschedule.data.db.main

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room 数据访问对象 (DAO)，用于操作待办事项 (TodoItem) 数据表。
 *
 * 按日期查询（当日待办）是「今日课表」页的核心路径；其余为编辑/删除操作。
 */
@Dao
interface TodoDao {

    /**
     * 获取指定日期的所有待办，返回数据流（实时响应）。
     * 排序：未完成在前、已完成沉底；同组内先按 sortOrder，再按可选时间。
     */
    @Query(
        """
        SELECT * FROM todo_items
        WHERE date = :date
        ORDER BY done ASC, (time IS NULL OR time = '') ASC, time ASC, sortOrder ASC
        """
    )
    fun getTodosByDate(date: String): Flow<List<TodoItem>>

    /**
     * 获取全部待办（按日期倒序），返回数据流。预留用于未来「全部待办」视图。
     */
    @Query(
        """
        SELECT * FROM todo_items
        ORDER BY date DESC, done ASC, (time IS NULL OR time = '') ASC, time ASC, sortOrder ASC
        """
    )
    fun getAllTodos(): Flow<List<TodoItem>>

    /**
     * 检查指定 ID 的待办是否存在，用于 Repository 判断插入还是更新。
     */
    @Query("SELECT EXISTS(SELECT 1 FROM todo_items WHERE id = :todoId)")
    suspend fun exists(todoId: String): Boolean

    /**
     * 插入待办。策略 ABORT，配合 Repository 的 exists 检查避免意外覆盖。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(todos: List<TodoItem>)

    /**
     * 更新待办（仅改字段，不删除行）。
     */
    @Update
    suspend fun update(todo: TodoItem)

    /**
     * 按 ID 删除单个待办。
     */
    @Query("DELETE FROM todo_items WHERE id = :todoId")
    suspend fun deleteById(todoId: String)

    /**
     * 仅翻转完成状态（不触发整行更新，避免更新时间戳抖动）。
     */
    @Query("UPDATE todo_items SET done = :done WHERE id = :todoId")
    suspend fun updateDone(todoId: String, done: Boolean)
}
