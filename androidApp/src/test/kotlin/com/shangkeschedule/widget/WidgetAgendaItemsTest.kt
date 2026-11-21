package com.shangkeschedule.widget

import com.shangkeschedule.data.db.main.ScheduleEvent
import com.shangkeschedule.data.db.main.TodoItem
import org.junit.Assert.*
import org.junit.Test

class WidgetAgendaItemsTest {
    @Test fun todosAndEventsShareSortingFilteringAndCapacity() {
        val event = ScheduleEvent("same", "2026-11-22", "考试", "exam", startTime = "10:00", createdAt = 1, updatedAt = 1)
        val todo = TodoItem("same", "2026-11-22", "待办", time = "09:00", createdAt = 1, updatedAt = 1)
        val result = buildWidgetAgenda(listOf(event, event.copy(id = "done", done = true)),
            listOf(todo, todo.copy(id = "past", date = "2026-11-21"), todo.copy(id = "done", done = true)),
            "2026-11-22", 2)
        assertEquals(listOf("待办", "考试"), result.map { it.title })
        assertEquals(2, result.map { it.id }.toSet().size)
        assertEquals("todo", result.first().category)
        assertFalse(result.first().is_all_day)
        assertEquals(listOf("待办"), buildWidgetAgenda(listOf(event), listOf(todo), "2026-11-22", 1).map { it.title })
    }

    @Test fun untimedTodoUsesAllDayPresentation() {
        val todo = TodoItem("todo", "2026-11-22", "全天待办", createdAt = 1, updatedAt = 1)
        assertTrue(buildWidgetAgenda(emptyList(), listOf(todo), "2026-11-22", 8).single().is_all_day)
    }
}
