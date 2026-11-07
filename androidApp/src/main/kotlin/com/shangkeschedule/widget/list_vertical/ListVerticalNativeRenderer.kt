package com.shangkeschedule.widget.list_vertical

import android.content.Context
import android.content.Intent
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
import com.shangkeschedule.widget.currentWeekOrNull
import com.shangkeschedule.widget.headerSizeSp
import com.shangkeschedule.widget.todayEmptyTip
import com.shangkeschedule.widget.widgetClickPendingIntent
import com.shangkeschedule.widget.courselist.CourseListRemoteViewsService
import java.time.LocalDate
import java.time.LocalTime

/**
 * 「今日课程」小组件渲染（4×N）。
 *
 * ## v4.83.0：课程多时可滑动
 *
 * 行容器由静态 LinearLayout 改为 ListView（collection widget）。此前课程数超出
 * 可视容量（WidgetListCapacity.rowsFor，上限 12 行）时直接截断不可见；现在把
 * **全部**课程交给 [CourseListRemoteViewsService]，系统按可视范围惰性绑定行，
 * 组件内即可上下滑动查看。
 *
 * 静态插行路径（[renderStaticRows]）仅剩一个消费场景：**列数据通知失败时的降级**。
 * `setRemoteAdapter` + `notifyAppWidgetViewDataChanged` 由本类与 WidgetUpdateHelper
 * 两处触发；适配器读库失败时返回 0 行，ListView 呈空——此时头部的「还有 N 节课」
 * 计数仍在（来自静态渲染），用户可打开 App 查看详情，数据由 15 分钟 tick 自愈。
 */
object ListVerticalNativeRenderer {

    fun render(
        context: Context,
        snapshot: WidgetSnapshot,
        maxCourseCount: Int,
        space: WidgetSpaceClass = WidgetSpaceClass.S
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_list_vertical_native)
        rv.setWidgetCardBackground(R.id.inner_content_card, R.id.container_full_status)

        resetWidgetState(rv)

        bindWidgetClickIntent(context, rv, ListVerticalNativeProvider::class.java)

        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val currentWeek = snapshot.currentWeekOrNull()

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

        // 星期取法与 Compact / DoubleDays 统一走 `DateTimeFormatter("E", Locale.getDefault())`：
        // 原先读 `R.array.week_days_full_names`，而该数组只在 values / zh-rCN / zh-rTW / en
        // 四档定义 —— 系统语言为日/法/韩等的用户会在这里看到中文「周一」，
        // 而同屏 Compact 组件显示本地化「月」。
        val dayOfWeekStr = today.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.SHORT,
            java.util.Locale.getDefault()
        )

        when {
            todayRemaining.isNotEmpty() -> {
                // 与 Compact / DoubleDays 统一使用 status_current_week_format（第 %1$d 周），
                // 弃用语义重复的 title_current_week（第 %1$s 周）——v3.66.3
                val weekText = context.getString(R.string.status_current_week_format, currentWeek)
                rv.setTextViewText(R.id.tv_header_title, "$weekText  $dayOfWeekStr")
                rv.setTextViewText(R.id.tv_header_count_summary, context.getString(R.string.widget_remaining_courses_format_today, todayRemaining.size))
                attachScrollableList(context, rv, todayRemaining, maxCourseCount, space) {
                    renderCourseContent(context, rv, todayRemaining.take(maxCourseCount), snapshot, space)
                }
            }
            tomorrowCourses.isNotEmpty() -> {
                rv.setTextViewText(R.id.tv_header_title, context.getString(R.string.widget_tomorrow_course_preview))
                rv.setTextViewText(R.id.tv_header_count_summary, context.getString(R.string.widget_remaining_courses_format_tomorrow, tomorrowCourses.size))
                attachScrollableList(context, rv, tomorrowCourses, maxCourseCount, space) {
                    renderCourseContent(context, rv, tomorrowCourses.take(maxCourseCount), snapshot, space)
                }
            }
            else -> {
                val tip = todayEmptyTip(context, snapshot.courses, todayStr)
                val weekText = context.getString(R.string.status_current_week_format, currentWeek)
                rv.setTextViewText(R.id.tv_header_title, "$weekText  $dayOfWeekStr")
                showInnerStatus(rv, tip)
                rv.setTextViewText(R.id.tv_header_count_summary, "")
            }
        }
        // 头部随空间档放大（与 Compact/DoubleDays 同口径）：此前本组件只放大行内文字、
        // 头部固定 14sp，结果 S 档下 Compact 头 12sp < ListVertical 头 14sp，层级反转。
        rv.setTextViewTextSize(
            R.id.tv_header_title,
            TypedValue.COMPLEX_UNIT_SP,
            headerSizeSp(space)
        )
        return rv
    }

    /**
     * 挂载可滑动列表。
     *
     * 策略：**全部课程**走 RemoteViewsService（滑动可见，不再受 maxCourseCount 截断）；
     * count <= 1 时退回静态插行——单行场景不值得引入 service 绑定开销，也与旧行为一致。
     * [staticFallback] 仅在降级分支执行。
     */
    private fun attachScrollableList(
        context: Context,
        rv: RemoteViews,
        courses: List<WidgetCourseProto>,
        maxCourseCount: Int,
        @Suppress("UNUSED_PARAMETER") space: WidgetSpaceClass,
        staticFallback: () -> Unit
    ) {
        if (courses.size <= 1) {
            staticFallback()
            return
        }
        rv.setRemoteAdapter(
            R.id.container_courses,
            CourseListRemoteViewsService.intent(context, CourseListRemoteViewsService.Mode.TODAY)
        )
        rv.setPendingIntentTemplate(
            R.id.container_courses,
            widgetClickPendingIntent(context, ListVerticalNativeProvider::class.java)
        )
        rv.setViewVisibility(R.id.container_courses, View.VISIBLE)
    }

    private fun resetWidgetState(rv: RemoteViews) {
        rv.setViewVisibility(R.id.container_full_status, View.GONE)
        rv.setViewVisibility(R.id.inner_content_card, View.VISIBLE)
        rv.setViewVisibility(R.id.container_courses, View.VISIBLE)
        rv.setViewVisibility(R.id.container_status, View.GONE)
        // ListView 路径下行由 Factory 提供，不允许 removeAllViews（不支持该操作会抛异常）。
        // 静态路径在插行前由调用方保证容器为空（每次都是全新 RemoteViews）。
    }

    private fun renderCourseContent(
        context: Context,
        rv: RemoteViews,
        courses: List<WidgetCourseProto>,
        snapshot: WidgetSnapshot,
        space: WidgetSpaceClass
    ) {
        rv.setViewVisibility(R.id.container_courses, View.VISIBLE)

        // 行内容与分隔线统一走 WidgetCourseRows；仅行布局不同
        addCourseRows(rv, R.id.container_courses, context, courses) { course ->
            com.shangkeschedule.widget.courseListRow(context, course, snapshot, space)
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
