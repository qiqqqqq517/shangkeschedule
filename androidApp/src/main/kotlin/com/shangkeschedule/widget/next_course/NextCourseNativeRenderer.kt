package com.shangkeschedule.widget.next_course

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.shangkeschedule.R
import com.shangkeschedule.widget.WidgetCourseProto
import com.shangkeschedule.widget.WidgetCourseSelection
import com.shangkeschedule.widget.WidgetSnapshot
import com.shangkeschedule.widget.WidgetSpaceClass
import com.shangkeschedule.widget.applyCourseColor
import com.shangkeschedule.widget.bindWidgetClickIntent
import com.shangkeschedule.widget.courseNameSizeSp
import com.shangkeschedule.widget.currentWeekOrNull
import com.shangkeschedule.widget.setWidgetCardBackground
import com.shangkeschedule.widget.spaceDeltaSp
import com.shangkeschedule.widget.todayEmptyTip
import java.time.LocalDate
import java.time.LocalTime

/**
 * 「下一节课」小组件渲染器（v4.67.0）。
 *
 * 与其余五个组件的口径**完全一致**，差异只在「显示多少」：
 * - 今日剩余课程一律走 [WidgetCourseSelection.remainingToday]（已跳过的不算、已结束的不算、
 *   按开始时间显式排序），只取第一条 → 这就是「下一节课」；
 * - 今日已无课不再直接落到空态，而是看**明天第一节**（跨天场景，与 G1 常驻通知同口径）；
 *   明天也没课才显示空态（文案交给 [todayEmptyTip]，与其它组件逐字相同）；
 * - 假期（`current_week <= 0`）沿用 `title_vacation` + `widget_vacation_expecting`。
 *
 * 两种「非上课中」的语义差别只体现在右上角胶囊：
 * `N 分钟后` / `正在上课` / `明日第一节`。倒计时按**分钟**计算，不显示秒
 * ——小组件最小刷新粒度是 15 分钟，秒级倒计时只会给人「卡住了」的错觉。
 */
object NextCourseNativeRenderer {

    /** 课程名基准字号（sp）：本组件只显示一节课，比列表条目（14sp）大一号。 */
    private const val NEXT_NAME_BASE_SP = 18

    /** 时间 / 地点基准字号（sp）：比列表条目（11sp）大一号。 */
    private const val NEXT_META_BASE_SP = 12

    fun render(
        context: Context,
        snapshot: WidgetSnapshot,
        maxCourseCount: Int,
        space: WidgetSpaceClass = WidgetSpaceClass.S
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_next_course_native)
        // 卡片背景按单夜色感知 ID 显式指定（容错：空态卡也要有底色，全屏状态时两者都上背景）
        rv.setWidgetCardBackground(R.id.inner_content_card, R.id.container_status)
        resetWidgetState(rv)
        bindWidgetClickIntent(context, rv, NextCourseNativeProvider::class.java)

        val currentWeek = snapshot.currentWeekOrNull()
        if (currentWeek == null) {
            showStatus(
                rv,
                context.getString(R.string.title_vacation),
                context.getString(R.string.widget_vacation_expecting)
            )
            return rv
        }

        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val today = LocalDate.now()
        val todayStr = today.toString()

        val nextCourse = WidgetCourseSelection
            .remainingToday(snapshot.courses, todayStr, nowMinutes)
            .firstOrNull()

        if (nextCourse != null) {
            val chip = countdownText(context, nextCourse.start_time, nextCourse.end_time, nowMinutes)
            bindCourse(context, rv, snapshot, nextCourse, space, chip)
            return rv
        }

        // 今日已无课：跨天展示「明日第一节」，避免组件在傍晚后变成一块没用的空白
        val tomorrowFirst = WidgetCourseSelection
            .tomorrow(snapshot.courses, today.plusDays(1).toString())
            .firstOrNull()

        if (tomorrowFirst != null) {
            bindCourse(
                context,
                rv,
                snapshot,
                tomorrowFirst,
                space,
                context.getString(R.string.widget_next_course_tomorrow)
            )
            return rv
        }

        showStatus(rv, todayEmptyTip(context, snapshot.courses, todayStr))
        return rv
    }

    /** 右上角胶囊文案：正在上课 > 未开始（N 分钟后）> 兜底「今日」。 */
    private fun countdownText(
        context: Context,
        startTime: String,
        endTime: String,
        nowMinutes: Int
    ): String {
        val start = WidgetCourseSelection.minutesOf(startTime)
            ?: return context.getString(R.string.widget_next_course_today)
        val end = WidgetCourseSelection.minutesOf(endTime) ?: Int.MAX_VALUE
        return when {
            // 已开始且尚未结束：这一节就是「现在这节课」，倒计时无意义
            nowMinutes >= start && nowMinutes < end -> context.getString(R.string.widget_next_course_ongoing)
            start > nowMinutes -> context.getString(R.string.widget_next_course_in_minutes, start - nowMinutes)
            else -> context.getString(R.string.widget_next_course_today)
        }
    }

    private fun bindCourse(
        context: Context,
        rv: RemoteViews,
        snapshot: WidgetSnapshot,
        course: WidgetCourseProto,
        space: WidgetSpaceClass,
        chipText: String
    ) {
        rv.setViewVisibility(R.id.container_info, View.VISIBLE)
        rv.setViewVisibility(R.id.container_status, View.GONE)

        rv.setTextViewText(R.id.tv_next_label, context.getString(R.string.widget_next_course_label))
        rv.setTextViewText(R.id.tv_next_countdown, chipText)

        rv.setTextViewText(R.id.tv_course_name, course.name)
        rv.setTextViewTextSize(
            R.id.tv_course_name,
            TypedValue.COMPLEX_UNIT_SP,
            courseNameSizeSp(space, NEXT_NAME_BASE_SP)
        )

        val metaSp = (NEXT_META_BASE_SP + spaceDeltaSp(space)).toFloat()
        rv.setTextViewTextSize(R.id.tv_course_time, TypedValue.COMPLEX_UNIT_SP, metaSp)
        rv.setTextViewTextSize(R.id.tv_course_position, TypedValue.COMPLEX_UNIT_SP, metaSp)

        rv.setTextViewText(R.id.tv_course_time, "${course.start_time.take(5)} - ${course.end_time.take(5)}")

        // 地点缺失（如纯线上课 / 未填）不留一行空白；v4.61.0 起课程行不再显示教师，
        // 但本组件空间充裕，用教师名兜底比留空更有信息量。
        val place = course.position.ifBlank { course.teacher }
        if (place.isBlank()) {
            rv.setViewVisibility(R.id.tv_course_position, View.GONE)
        } else {
            rv.setViewVisibility(R.id.tv_course_position, View.VISIBLE)
            rv.setTextViewText(R.id.tv_course_position, place)
        }

        rv.applyCourseColor(
            context,
            lightViewId = R.id.course_bar_light,
            darkViewId = R.id.course_bar_dark,
            maps = snapshot.style?.course_color_maps,
            colorInt = course.color_int
        )
    }

    /** 每次渲染前强制归零可见性，消除跨状态残留（与其余五个组件同一纪律）。 */
    private fun resetWidgetState(rv: RemoteViews) {
        rv.setViewVisibility(R.id.container_info, View.GONE)
        rv.setViewVisibility(R.id.container_status, View.GONE)
        rv.setViewVisibility(R.id.tv_course_position, View.VISIBLE)
    }

    private fun showStatus(rv: RemoteViews, title: String, message: String? = null) {
        rv.setViewVisibility(R.id.container_info, View.GONE)
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
