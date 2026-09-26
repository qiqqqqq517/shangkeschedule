package com.shangkeschedule.widget.tiny

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.shangkeschedule.R
import com.shangkeschedule.widget.WidgetCourseSelection
import com.shangkeschedule.widget.WidgetSnapshot
import com.shangkeschedule.widget.applyCourseColor
import com.shangkeschedule.widget.bindWidgetClickIntent
import com.shangkeschedule.widget.currentWeekOrNull
import com.shangkeschedule.widget.todayEmptyTip
import java.time.LocalDate
import java.time.LocalTime

object TinyNativeRenderer {

    fun render(context: Context, snapshot: WidgetSnapshot, maxCourseCount: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_tiny_native)

        // 状态彻底重置
        resetWidgetState(rv)

        // 设置点击跳转
        bindWidgetClickIntent(context, rv)

        // 数据准备
        val currentWeek = snapshot.currentWeekOrNull()
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val todayStr = LocalDate.now().toString()

        // 情况 A：假期处理
        if (currentWeek == null) {
            showStatus(rv, context.getString(R.string.title_vacation), context.getString(R.string.widget_vacation_expecting))
            return rv
        }

        // 情况 B：开学期间数据过滤（排序与过滤统一走 WidgetCourseSelection，与其余三个组件一致）
        val todayRemaining = WidgetCourseSelection.remainingToday(snapshot.courses, todayStr, nowMinutes)
        val nextCourse = todayRemaining.firstOrNull()

        if (nextCourse != null) {
            // 有课显示逻辑
            rv.setViewVisibility(R.id.container_info, View.VISIBLE)
            rv.setViewVisibility(R.id.bubble_frame, View.VISIBLE)
            rv.setViewVisibility(R.id.container_status, View.GONE)

            rv.setTextViewText(R.id.tv_course_name, nextCourse.name)

            val timeText = "${nextCourse.start_time.take(5)} - ${nextCourse.end_time.take(5)}"
            rv.setTextViewText(R.id.tv_course_time, timeText)
            rv.setTextViewText(R.id.tv_course_position, nextCourse.position)

            // 剩余课程数：直接取已过滤列表条数。
            // 旧实现为 `todayAllCourses.size - indexOf(nextCourse)`，而该列表含已跳过的课，
            // 只要下一节课之后存在被跳过的课，计数就会偏大（4 节中第 3 节被跳过 → 显示 3、实际 2）。
            rv.setTextViewText(R.id.tv_remaining_count, todayRemaining.size.toString())

            // 颜色渲染（取色越界 / 缺色时回落到 widget_course_fallback）
            rv.applyCourseColor(
                context,
                lightViewId = R.id.bubble_bg_image,
                darkViewId = R.id.bubble_bg_image_dark,
                maps = snapshot.style?.course_color_maps,
                colorInt = nextCourse.color_int
            )
        } else {
            // 无课状态
            showStatus(rv, todayEmptyTip(context, snapshot.courses, todayStr))
        }

        return rv
    }

    /**
     * 核心优化：每次渲染前强制归零可见性，消除跨状态残留
     */
    private fun resetWidgetState(rv: RemoteViews) {
        rv.setViewVisibility(R.id.container_info, View.GONE)
        rv.setViewVisibility(R.id.bubble_frame, View.GONE)
        rv.setViewVisibility(R.id.container_status, View.GONE)
    }

    private fun showStatus(rv: RemoteViews, title: String, message: String? = null) {
        rv.setViewVisibility(R.id.container_status, View.VISIBLE)
        rv.setTextViewText(R.id.tv_status_title, title)

        if (!message.isNullOrBlank()) {
            rv.setTextViewText(R.id.tv_status_msg, message)
            rv.setViewVisibility(R.id.tv_status_msg, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.tv_status_msg, View.GONE)
        }
    }
}
