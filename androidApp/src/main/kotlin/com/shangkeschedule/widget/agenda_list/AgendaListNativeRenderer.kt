package com.shangkeschedule.widget.agenda_list

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.shangkeschedule.R
import com.shangkeschedule.data.db.main.ScheduleCategory
import com.shangkeschedule.widget.WidgetSnapshot
import com.shangkeschedule.widget.WidgetSpaceClass
import com.shangkeschedule.widget.addRows
import com.shangkeschedule.widget.bindWidgetClickIntent
import com.shangkeschedule.widget.courseNameSizeSp
import com.shangkeschedule.widget.headerSizeSp
import com.shangkeschedule.widget.rowMetaSizeSp
import com.shangkeschedule.widget.setWidgetCardBackground
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 「日程清单」小组件渲染（v4.67.0，C4）。
 *
 * 数据取 `snapshot.agenda`（`WidgetUpdateHelper` 从主库 `schedule_events` 读到的
 * 「日期 >= 今天」的全部类别日程，按（日期, 开始时间）升序、最多 8 条）。
 *
 * 与「考试倒计时」的分工：考试倒计时只盯**最近一场考试**并给天数倒计时；
 * 本组件是**清单**，待办 / 活动 / 作业 / 考试一视同仁，用左侧色条区分类别。
 * 没有日程时不显示整卡空态之外的任何信息（避免出现「暂无」但占着一整屏的情况，
 * 空态文案会引导用户去「日程」页添加）。
 */
object AgendaListNativeRenderer {

    fun render(
        context: Context,
        snapshot: WidgetSnapshot,
        maxCourseCount: Int,
        space: WidgetSpaceClass = WidgetSpaceClass.S
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_agenda_list_native)
        rv.setWidgetCardBackground(R.id.inner_content_card, R.id.container_full_status)

        rv.setViewVisibility(R.id.container_full_status, View.GONE)
        rv.setViewVisibility(R.id.inner_content_card, View.VISIBLE)
        rv.setViewVisibility(R.id.container_courses, View.GONE)
        rv.setViewVisibility(R.id.container_status, View.GONE)
        rv.removeAllViews(R.id.container_courses)

        bindWidgetClickIntent(context, rv)

        val today = LocalDate.now()
        val todayStr = today.toString()

        val agenda = snapshot.agenda.asSequence()
            .sortedWith(compareBy({ it.date }, { it.start_time }))
            .toList()

        rv.setTextViewTextSize(R.id.tv_header_title, TypedValue.COMPLEX_UNIT_SP, headerSizeSp(space))

        if (agenda.isEmpty()) {
            rv.setTextViewText(R.id.tv_header_title, context.getString(R.string.widget_agenda_upcoming))
            rv.setTextViewText(R.id.tv_header_count_summary, "")
            rv.setViewVisibility(R.id.container_status, View.VISIBLE)
            rv.setTextViewText(R.id.tv_status_title, context.getString(R.string.widget_agenda_none))
            rv.setTextViewText(R.id.tv_status_msg, context.getString(R.string.widget_agenda_none_hint))
            return rv
        }

        val hasToday = agenda.any { it.date == todayStr }
        rv.setTextViewText(
            R.id.tv_header_title,
            context.getString(
                if (hasToday) R.string.widget_agenda_today else R.string.widget_agenda_upcoming
            )
        )
        rv.setTextViewText(
            R.id.tv_header_count_summary,
            context.getString(R.string.widget_agenda_count, agenda.size)
        )
        rv.setViewVisibility(R.id.container_courses, View.VISIBLE)

        val metaSp = rowMetaSizeSp(space)
        val nameSp = courseNameSizeSp(space)
        addRows(rv, R.id.container_courses, context, agenda.take(maxCourseCount)) { event ->
            RemoteViews(context.packageName, R.layout.widget_item_agenda_node).apply {
                setTextViewText(R.id.tv_agenda_day, dayLabel(context, event.date, today))
                setTextViewText(
                    R.id.tv_agenda_time,
                    if (event.is_all_day || event.start_time.isBlank()) {
                        context.getString(R.string.widget_agenda_all_day)
                    } else {
                        event.start_time.take(5)
                    }
                )
                setTextViewText(R.id.tv_agenda_title, event.title)
                setTextViewText(
                    R.id.tv_agenda_meta,
                    buildMeta(context, event.category, event.location)
                )
                setTextViewTextSize(R.id.tv_agenda_title, TypedValue.COMPLEX_UNIT_SP, nameSp)
                setTextViewTextSize(R.id.tv_agenda_day, TypedValue.COMPLEX_UNIT_SP, metaSp)
                setTextViewTextSize(R.id.tv_agenda_time, TypedValue.COMPLEX_UNIT_SP, metaSp)
                setTextViewTextSize(R.id.tv_agenda_meta, TypedValue.COMPLEX_UNIT_SP, metaSp)
                // 类别色条：色条沿用 include widget_course_color_bar 的深浅双 ID 约定
                val accent = context.getColor(categoryColorRes(event.category))
                setInt(R.id.course_bar_light, "setColorFilter", accent)
                setInt(R.id.course_bar_dark, "setColorFilter", accent)
            }
        }
        return rv
    }

    /** 行首日期：今天 / 明天用相对文案，其余用「周X」（跟随系统语言，不硬编码中文）。 */
    private fun dayLabel(context: Context, date: String, today: LocalDate): String {
        val target = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
        return when (ChronoUnit.DAYS.between(today, target)) {
            0L -> context.getString(R.string.widget_title_today)
            1L -> context.getString(R.string.widget_title_tomorrow)
            else -> target.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }

    /** 副标题 = 「类别 · 地点」，两者都为空时留空（而不是留下一个孤零零的分隔点）。 */
    private fun buildMeta(context: Context, category: String, location: String): String {
        val label = context.getString(categoryLabelRes(category))
        return if (location.isBlank()) label else "$label · $location"
    }

    private fun categoryLabelRes(category: String): Int =
        when (ScheduleCategory.fromKey(category)) {
            ScheduleCategory.TODO -> R.string.widget_agenda_cat_todo
            ScheduleCategory.EXAM -> R.string.widget_agenda_cat_exam
            ScheduleCategory.ACTIVITY -> R.string.widget_agenda_cat_activity
            ScheduleCategory.HOMEWORK -> R.string.widget_agenda_cat_homework
            else -> R.string.widget_agenda_cat_other
        }

    /**
     * 类别色条：考试沿用「考试倒计时」的红（语义一致），其余四类各一色。
     * 这些颜色只服务于小组件，不参与课程 12 档色板。
     */
    private fun categoryColorRes(category: String): Int =
        when (ScheduleCategory.fromKey(category)) {
            ScheduleCategory.EXAM -> R.color.widget_exam_accent
            ScheduleCategory.TODO -> R.color.widget_agenda_todo
            ScheduleCategory.ACTIVITY -> R.color.widget_agenda_activity
            ScheduleCategory.HOMEWORK -> R.color.widget_agenda_homework
            else -> R.color.widget_agenda_other
        }
}
