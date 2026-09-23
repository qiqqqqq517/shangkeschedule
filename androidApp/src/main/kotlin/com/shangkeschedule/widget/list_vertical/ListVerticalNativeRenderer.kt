package com.shangkeschedule.widget.list_vertical

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.shangkeschedule.R
import com.shangkeschedule.widget.WidgetCourseProto
import com.shangkeschedule.widget.WidgetCourseSelection
import com.shangkeschedule.widget.WidgetSnapshot
import com.shangkeschedule.widget.applyCourseColor
import com.shangkeschedule.widget.bindWidgetClickIntent
import java.time.LocalDate
import java.time.LocalTime

object ListVerticalNativeRenderer {

    fun render(context: Context, snapshot: WidgetSnapshot, maxCourseCount: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_list_vertical_native)

        resetWidgetState(rv)

        bindWidgetClickIntent(context, rv)

        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val currentWeek = if (snapshot.current_week <= 0) null else snapshot.current_week

        if (currentWeek == null) {
            showFullStatus(
                rv,
                context.getString(R.string.title_vacation),
                context.getString(R.string.widget_vacation_expecting)
            )
            return rv
        }

        val todayStr = today.toString()
        val tomorrowStr = tomorrow.toString()

        val todayRemaining = WidgetCourseSelection.remainingToday(snapshot.courses, todayStr, nowMinutes)
        val tomorrowCourses = WidgetCourseSelection.tomorrow(snapshot.courses, tomorrowStr)

        val weekDaysArray = context.resources.getStringArray(R.array.week_days_full_names)
        val dayOfWeekStr = weekDaysArray[today.dayOfWeek.value - 1]

        when {
            todayRemaining.isNotEmpty() -> {
                // 与 Compact / DoubleDays 统一使用 status_current_week_format（第 %1$d 周），
                // 弃用语义重复的 title_current_week（第 %1$s 周）——v3.66.3
                val weekText = context.getString(R.string.status_current_week_format, currentWeek)
                rv.setTextViewText(R.id.tv_header_title, "$weekText  $dayOfWeekStr")
                rv.setTextViewText(R.id.tv_header_count_summary, context.getString(R.string.widget_remaining_courses_format_today, todayRemaining.size))
                renderCourseContent(context, rv, todayRemaining.take(maxCourseCount), snapshot)
            }
            tomorrowCourses.isNotEmpty() -> {
                rv.setTextViewText(R.id.tv_header_title, context.getString(R.string.widget_tomorrow_course_preview))
                rv.setTextViewText(R.id.tv_header_count_summary, context.getString(R.string.widget_remaining_courses_format_tomorrow, tomorrowCourses.size))
                renderCourseContent(context, rv, tomorrowCourses.take(maxCourseCount), snapshot)
            }
            else -> {
                val hasCoursesToday = WidgetCourseSelection.allToday(snapshot.courses, todayStr).isNotEmpty()
                val tip = if (!hasCoursesToday) context.getString(R.string.text_no_courses_today) else context.getString(R.string.widget_today_courses_finished)
                val weekText = context.getString(R.string.status_current_week_format, currentWeek)
                rv.setTextViewText(R.id.tv_header_title, "$weekText  $dayOfWeekStr")
                showInnerStatus(rv, tip)
                rv.setTextViewText(R.id.tv_header_count_summary, "")
            }
        }
        return rv
    }

    private fun resetWidgetState(rv: RemoteViews) {
        rv.setViewVisibility(R.id.container_full_status, View.GONE)
        rv.setViewVisibility(R.id.inner_content_card, View.VISIBLE)
        rv.setViewVisibility(R.id.container_courses, View.GONE)
        rv.setViewVisibility(R.id.container_status, View.GONE)
        rv.removeAllViews(R.id.container_courses)
    }

    private fun renderCourseContent(
        context: Context,
        rv: RemoteViews,
        courses: List<WidgetCourseProto>,
        snapshot: WidgetSnapshot
    ) {
        rv.setViewVisibility(R.id.container_courses, View.VISIBLE)

        courses.forEachIndexed { index, course ->
            val itemRv = RemoteViews(context.packageName, R.layout.widget_item_course_list_node)

            itemRv.setTextViewText(R.id.tv_course_name, course.name)
            itemRv.setTextViewText(R.id.tv_course_position, course.position)
            itemRv.setTextViewText(R.id.tv_course_start_time, course.start_time.take(5))
            itemRv.setTextViewText(R.id.tv_course_end_time, course.end_time.take(5))

            if (course.teacher.isNotBlank()) {
                itemRv.setViewVisibility(R.id.tv_course_teacher, View.VISIBLE)
                itemRv.setTextViewText(R.id.tv_course_teacher, course.teacher)
            } else {
                itemRv.setViewVisibility(R.id.tv_course_teacher, View.GONE)
            }

            // 颜色渲染（取色越界 / 缺色时回落到 widget_course_fallback）
            itemRv.applyCourseColor(
                context,
                lightViewId = R.id.course_indicator,
                darkViewId = R.id.course_indicator_dark,
                maps = snapshot.style?.course_color_maps,
                colorInt = course.color_int
            )

            rv.addView(R.id.container_courses, itemRv)

            if (index < courses.size - 1) {
                rv.addView(R.id.container_courses, RemoteViews(context.packageName, R.layout.widget_divider_horizontal))
            }
        }
    }

    private fun showFullStatus(rv: RemoteViews, title: String, msg: String) {
        rv.setViewVisibility(R.id.inner_content_card, View.GONE)
        rv.setViewVisibility(R.id.container_full_status, View.VISIBLE)
        rv.setTextViewText(R.id.tv_full_status_title, title)
        rv.setTextViewText(R.id.tv_full_status_msg, msg)
    }

    private fun showInnerStatus(rv: RemoteViews, title: String) {
        rv.setViewVisibility(R.id.container_courses, View.GONE)
        rv.setViewVisibility(R.id.container_status, View.VISIBLE)
        rv.setTextViewText(R.id.tv_status_title, title)
    }
}
