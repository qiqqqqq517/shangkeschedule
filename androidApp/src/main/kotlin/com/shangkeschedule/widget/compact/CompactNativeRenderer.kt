package com.shangkeschedule.widget.compact

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.shangkeschedule.R
import com.shangkeschedule.widget.WidgetCourseProto
import com.shangkeschedule.widget.WidgetCourseSelection
import com.shangkeschedule.widget.WidgetSnapshot
import com.shangkeschedule.widget.WidgetSpaceClass
import com.shangkeschedule.widget.addCourseRows
import com.shangkeschedule.widget.bindWidgetClickIntent
import com.shangkeschedule.widget.setWidgetCardBackground
import com.shangkeschedule.widget.commonCourseRow
import com.shangkeschedule.widget.courseNameSizeSp
import com.shangkeschedule.widget.currentWeekOrNull
import com.shangkeschedule.widget.headerSizeSp
import com.shangkeschedule.widget.todayEmptyTip
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object CompactNativeRenderer {

    fun render(
        context: Context,
        snapshot: WidgetSnapshot,
        maxCourseCount: Int,
        space: WidgetSpaceClass = WidgetSpaceClass.S
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_today_compact_native)
        // 卡片背景按单夜色感知 ID 显式指定（reapply 重放时按当前配置重解，见 setWidgetCardBackground）
        rv.setWidgetCardBackground(R.id.inner_content_card, R.id.container_full_status)

        // 状态彻底重置
        resetWidgetState(rv)

        // 设置点击跳转
        bindWidgetClickIntent(context, rv)

        // 栏头按空间放大（v4.61.0）
        rv.setTextViewTextSize(R.id.tv_header_title, TypedValue.COMPLEX_UNIT_SP, headerSizeSp(space))

        // 数据准备
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val currentWeek = snapshot.currentWeekOrNull()

        // 头部基础信息渲染
        val dateFormatter = DateTimeFormatter.ofPattern("E", Locale.getDefault())
        rv.setTextViewText(R.id.tv_header_title, today.format(dateFormatter))

        // 渲染周数
        if (currentWeek != null) {
            rv.setViewVisibility(R.id.tv_current_week, View.VISIBLE)
            rv.setTextViewText(R.id.tv_current_week, context.getString(R.string.status_current_week_format, currentWeek))
        } else {
            rv.setViewVisibility(R.id.tv_current_week, View.GONE)
        }

        // 情况 A：假期处理 (直接显示全屏遮罩)
        if (currentWeek == null) {
            showStatus(rv, context, context.getString(R.string.title_vacation), context.getString(R.string.widget_vacation_expecting), isFullCover = true)
            return rv
        }

        // 核心调度逻辑
        val todayStr = today.toString()
        val tomorrowStr = tomorrow.toString()

        val todayRemaining = WidgetCourseSelection.remainingToday(snapshot.courses, todayStr, nowMinutes)
        val tomorrowCourses = WidgetCourseSelection.tomorrow(snapshot.courses, tomorrowStr)

        // 决定渲染路径
        when {
            todayRemaining.isNotEmpty() -> {
                // 状态 1：今日剩余
                renderCourseContent(
                    context,
                    rv,
                    todayRemaining.take(maxCourseCount),
                    snapshot,
                    todayRemaining.size,
                    false,
                    space
                )
            }
            tomorrowCourses.isNotEmpty() -> {
                // 状态 2：明日预告
                rv.setTextViewText(R.id.tv_header_title, context.getString(R.string.widget_tomorrow_course_preview))
                renderCourseContent(
                    context,
                    rv,
                    tomorrowCourses.take(maxCourseCount),
                    snapshot,
                    tomorrowCourses.size,
                    true,
                    space
                )
            }
            else -> {
                // 状态 3：今明无课
                showStatus(rv, context, todayEmptyTip(context, snapshot.courses, todayStr), "", isFullCover = false)
            }
        }

        return rv
    }

    /**
     * 重置所有 View 的可见性。
     */
    private fun resetWidgetState(rv: RemoteViews) {
        rv.setViewVisibility(R.id.container_full_status, View.GONE)
        rv.setViewVisibility(R.id.inner_content_card, View.VISIBLE)
        rv.setViewVisibility(R.id.container_courses, View.GONE)
        rv.setViewVisibility(R.id.container_status, View.GONE)
        rv.setViewVisibility(R.id.tv_footer, View.GONE)
        rv.removeAllViews(R.id.container_courses)
    }

    /**
     * 渲染具体的课程列表
     */
    private fun renderCourseContent(
        context: Context,
        rv: RemoteViews,
        courses: List<WidgetCourseProto>,
        snapshot: WidgetSnapshot,
        totalCount: Int,
        isTomorrow: Boolean,
        space: WidgetSpaceClass
    ) {
        rv.setViewVisibility(R.id.container_courses, View.VISIBLE)
        rv.setViewVisibility(R.id.container_status, View.GONE)
        rv.setViewVisibility(R.id.tv_footer, View.VISIBLE)

        addCourseRows(rv, R.id.container_courses, context, courses) { commonCourseRow(context, it, snapshot, space) }

        val footerRes = if (isTomorrow) R.string.widget_course_total_count else R.string.widget_course_remaining_count
        rv.setTextViewText(R.id.tv_footer, context.getString(footerRes, totalCount))
    }

    /**
     * 状态层渲染。
     *
     * @param msg 仅在 [isFullCover] 时使用（`container_full_status` 确有 `tv_full_status_msg`）；
     *   非全屏形态的 `container_status` 只有标题一个 TextView，[msg] 被忽略。
     */
    private fun showStatus(rv: RemoteViews, context: Context, title: String, msg: String?, isFullCover: Boolean) {
        if (isFullCover) {
            rv.setViewVisibility(R.id.inner_content_card, View.GONE)
            rv.setViewVisibility(R.id.container_status, View.GONE)
            rv.setViewVisibility(R.id.container_full_status, View.VISIBLE)
            rv.setTextViewText(R.id.tv_full_status_title, title)
            if (!msg.isNullOrBlank()) {
                rv.setTextViewText(R.id.tv_full_status_msg, msg)
                rv.setViewVisibility(R.id.tv_full_status_msg, View.VISIBLE)
            }
        } else {
            rv.setViewVisibility(R.id.inner_content_card, View.VISIBLE)
            rv.setViewVisibility(R.id.container_courses, View.GONE)
            rv.setViewVisibility(R.id.tv_footer, View.GONE)
            rv.setViewVisibility(R.id.container_status, View.VISIBLE)
            rv.setViewVisibility(R.id.container_full_status, View.GONE)
            // `widget_today_compact_native.xml` 的 `container_status` **只有** `tv_status_title`，
            // 没有 `tv_status_msg`。此前这里对 msg 的写入是恒 no-op（RemoteViews 对不存在的
            // id 静默跳过），且唯一调用点（:107）本来就传空串 —— 属于埋雷式死代码：
            // 将来若有人给非空 msg，文字会无声消失。改为显式忽略并在签名上标注。
            rv.setTextViewText(R.id.tv_status_title, title)
        }
    }
}
