package com.shangkeschedule.widget

import com.shangkeschedule.data.db.main.ScheduleCategory
import com.shangkeschedule.data.db.main.ScheduleEvent
import com.shangkeschedule.data.db.main.TodoItem

/** 两种来源统一排序后截取，完成或过期条目不占小组件名额。 */
internal fun buildWidgetAgenda(
    events: List<ScheduleEvent>, todos: List<TodoItem>, today: String, limit: Int
): List<WidgetAgendaProto> {
    val items = events.filter { !it.done && it.date >= today }.map {
        WidgetAgendaProto(id = "event:${it.id}", title = it.title, date = it.date,
            start_time = it.startTime.orEmpty(), category = it.category,
            location = it.location.orEmpty(), is_all_day = it.isAllDay)
    } + todos.filter { !it.done && it.date >= today }.map {
        WidgetAgendaProto(id = "todo:${it.id}", title = it.title, date = it.date,
            start_time = it.time.orEmpty(), category = ScheduleCategory.TODO.key,
            is_all_day = it.time.isNullOrBlank())
    }
    return items.sortedWith(compareBy({ it.date }, { it.start_time }, { it.id })).take(limit)
}
