package com.shangkeschedule.widget.double_days

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.shangkeschedule.R
import com.shangkeschedule.widget.WidgetCourseProto
import com.shangkeschedule.widget.WidgetCourseSelection
import com.shangkeschedule.widget.WidgetSnapshot
import com.shangkeschedule.widget.addCourseRows
import com.shangkeschedule.widget.bindWidgetClickIntent
import com.shangkeschedule.widget.commonCourseRow
import com.shangkeschedule.widget.currentWeekOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DoubleDaysNativeRenderer {

    fun render(context: Context, snapshot: WidgetSnapshot, maxCourseCount: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_double_days_native)

        // 状态彻底重置
        resetWidgetState(rv)

        // 点击跳转逻辑
        bindWidgetClickIntent(context, rv)

        // 全局状态判断
        val currentWeek = snapshot.currentWeekOrNull()

        if (currentWeek == null) {
            rv.setViewVisibility(R.id.inner_content_card, View.GONE)
            rv.setViewVisibility(R.id.container_vacation, View.VISIBLE)
            rv.setTextViewText(R.id.tv_vacation_title, context.getString(R.string.title_vacation))
            rv.setTextViewText(R.id.tv_vacation_msg, context.getString(R.string.widget_vacation_expecting))
            return rv
        }

        rv.setViewVisibility(R.id.inner_content_card, View.VISIBLE)
        rv.setViewVisibility(R.id.container_vacation, View.GONE)
        rv.setTextViewText(R.id.tv_current_week, context.getString(R.string.status_current_week_format, currentWeek))

        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        // 渲染左侧：今日
        val remainingToday = WidgetCourseSelection.remainingToday(snapshot.courses, today.toString(), nowMinutes)

        renderColumn(
            context, rv,
            R.id.container_today, R.id.tv_today_date, R.id.tv_today_footer,
            R.id.empty_today_container,
            today, remainingToday.take(maxCourseCount), remainingToday.size,
            true, snapshot
        )

        // 渲染右侧：明日
        val effectiveTomorrow = WidgetCourseSelection.tomorrow(snapshot.courses, tomorrow.toString())

        renderColumn(
            context, rv,
            R.id.container_tomorrow, R.id.tv_tomorrow_date, R.id.tv_tomorrow_footer,
            R.id.empty_tomorrow_container,
            tomorrow, effectiveTomorrow.take(maxCourseCount), effectiveTomorrow.size,
            false, snapshot
        )

        return rv
    }

    private fun resetWidgetState(rv: RemoteViews) {
        rv.setViewVisibility(R.id.inner_content_card, View.VISIBLE)
        rv.setViewVisibility(R.id.container_vacation, View.GONE)
        rv.removeAllViews(R.id.container_today)
        rv.removeAllViews(R.id.container_tomorrow)
        rv.setViewVisibility(R.id.empty_today_container, View.GONE)
        rv.setViewVisibility(R.id.empty_tomorrow_container, View.GONE)
    }

    private fun renderColumn(
        context: Context,
        rootRv: RemoteViews,
        containerId: Int,
        dateId: Int,
        footerId: Int,
        emptyContainerId: Int,
        date: LocalDate,
        displayCourses: List<WidgetCourseProto>,
        totalCount: Int,
        isToday: Boolean,
        snapshot: WidgetSnapshot
    ) {
        // 设置日期标题
        val prefix = if (isToday) {
            context.getString(R.string.widget_title_today)
        } else {
            context.getString(R.string.widget_title_tomorrow)
        }
        val datePattern = date.format(DateTimeFormatter.ofPattern("M.dd E", Locale.getDefault()))
        rootRv.setTextViewText(dateId, "$prefix $datePattern")

        if (totalCount == 0) {
            rootRv.setViewVisibility(containerId, View.GONE)
            rootRv.setViewVisibility(emptyContainerId, View.VISIBLE)
            rootRv.setViewVisibility(footerId, View.GONE)
            val emptyTextViewId = if (isToday) R.id.empty_today else R.id.empty_tomorrow
            rootRv.setTextViewText(emptyTextViewId, context.getString(R.string.text_no_course))
        } else {
            rootRv.setViewVisibility(containerId, View.VISIBLE)
            rootRv.setViewVisibility(emptyContainerId, View.GONE)
            rootRv.setViewVisibility(footerId, View.VISIBLE)

            // 设置统计文案：今日显示“剩余”，其他显示“共有”
            val countRes = if (isToday) R.string.widget_course_remaining_count else R.string.widget_course_total_count
            rootRv.setTextViewText(footerId, context.getString(countRes, totalCount))

            // 循环渲染所有课程（行内容与分隔线统一走 WidgetCourseRows）
            addCourseRows(rootRv, containerId, context, displayCourses) { commonCourseRow(context, it, snapshot) }
        }
    }
}
