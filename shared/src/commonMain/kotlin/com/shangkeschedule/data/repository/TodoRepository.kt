package com.shangkeschedule.data.repository

import androidx.room3.withWriteTransaction
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.db.main.TodoDao
import com.shangkeschedule.data.db.main.TodoItem
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 待办数据仓库，负责与「今日课表」页待办相关的业务逻辑与数据操作。
 *
 * 待办为全局数据（不随课表切换），按日期归属，供今日页内嵌展示当日待办。
 */
@OptIn(ExperimentalUuidApi::class)
@Single
class TodoRepository(
    private val database: MainAppDatabase,
    private val todoDao: TodoDao
) {
    /**
     * 获取指定日期（yyyy-MM-dd）的所有待办，返回数据流（实时响应）。
     */
    fun getTodosByDate(date: String): Flow<List<TodoItem>> {
        return todoDao.getTodosByDate(date)
    }

    /**
     * 获取全部待办（按日期倒序）。预留用于未来「全部待办」视图。
     */
    fun getAllTodos(): Flow<List<TodoItem>> {
        return todoDao.getAllTodos()
    }

    /**
     * 新增一条待办。
     *
     * @param date 归属日期 "yyyy-MM-dd"。
     * @param title 待办内容（自动截断到 300 字）。
     * @param note 备注（自动截断到 500 字，空白归一为 null）。
     * @param time 可选时间 "HH:mm"。
     * @return 已入库的 [TodoItem]。
     */
    suspend fun addTodo(date: String, title: String, note: String?, time: String?): TodoItem {
        val now = Clock.System.now().toEpochMilliseconds()
        val todo = TodoItem(
            id = Uuid.random().toString(),
            date = date,
            title = title.take(300),
            note = note?.take(500)?.ifBlank { null },
            time = time?.takeIf { it.isNotBlank() },
            done = false,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now
        )
        database.withWriteTransaction {
            todoDao.insertAll(listOf(todo))
        }
        return todo
    }

    /**
     * 更新一条待办（编辑标题/备注/时间）。不存在时回退为插入。
     */
    suspend fun updateTodo(todo: TodoItem) {
        val updated = todo.copy(
            title = todo.title.take(300),
            note = todo.note?.take(500)?.ifBlank { null },
            time = todo.time?.takeIf { it.isNotBlank() },
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )
        database.withWriteTransaction {
            if (todoDao.exists(updated.id)) {
                todoDao.update(updated)
            } else {
                todoDao.insertAll(listOf(updated))
            }
        }
    }

    /**
     * 翻转指定待办的完成状态。
     */
    suspend fun setDone(todoId: String, done: Boolean) {
        database.withWriteTransaction {
            todoDao.updateDone(todoId, done)
        }
    }

    /**
     * 删除指定待办。
     */
    suspend fun deleteTodo(todoId: String) {
        database.withWriteTransaction {
            todoDao.deleteById(todoId)
        }
    }
}
