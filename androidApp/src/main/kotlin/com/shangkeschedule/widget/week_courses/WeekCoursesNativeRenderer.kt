package com.shangkeschedule.widget.week_courses

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.shangkeschedule.R
import com.shangkeschedule.widget.WidgetCourseSelection
import com.shangkeschedule.widget.WidgetSnapshot
import com.shangkeschedule.widget.WidgetSpaceClass
import com.shangkeschedule.widget.addRows
import com.shangkeschedule.widget.bindWidgetClickIntent
import com.shangkeschedule.widget.currentWeekOrNull
import com.shangkeschedule.widget.headerSizeSp
import com.shangkeschedule.widget.setWidgetCardBackground
import com.shangkeschedule.widget.weekCourseRow
import com.shangkeschedule.widget.widgetClickPendingIntent
import com.shangkeschedule.widget.courselist.CourseListRemoteViewsService
import java.time.LocalDate
import java.time.LocalTime

/**
 * 「周课程」小组件渲染（v4.67.0，C3）。
 *
 * 口径（与其余组件一致，刻意不做第二个事实来源）：
 * - 数据取 `snapshot.week_courses`（`WidgetUpdateHelper` 读到「今天起 7 天」的全部课程）；
 * - **本周剩余** = 今天尚未结束的课 + 之后 6 天，已跳过的课不出现（与
 *   [WidgetCourseSelection.remainingToday] 同口径）；
 * - 假期（`current_week <= 0`）显示整卡假期态，与 Tiny/Compact/DoubleDays/ListVertical 一致；
 * - 本周已无课（例如周日晚上）显示空态，而不是把下周的课混进「本周」。
 *
 * 与 `docs` 里「周课程」理想形态（7 天全矩阵）的差异：矩阵在 RemoteViews 下只能靠
 * 固定行列的窄格子实现，字号会被压到 10sp 以下且无法自适应；这里改用
 * 「日期 + 时间 + 课程名」的纵向列表，信息更完整，也与既有列表组件视觉统一。
 *
 * ## v4.83.0：课程多时可滑动
 *
 * 行容器改为 ListView，「本周剩余」**全部**课程交给
 * [CourseListRemoteViewsService]（Mode.WEEK），超出可视高度时组件内滑动查看，
 * 不再被 [WidgetListCapacity 的容量] 截断。课程 ≤ 1 时退回静态插行（单行不值得
 * service 绑定开销）；行构造统一走 WidgetCourseRows 的 [weekCourseRow]，
 * 与 Factory 侧外观逐字段一致。
 */
object WeekCoursesNativeRenderer {

    fun render(
        context: Context,
        snapshot: WidgetSnapshot,
        maxCourseCount: Int,
        space: WidgetSpaceClass = WidgetSpaceClass.S
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_week_courses_native)
        rv.setWidgetCardBackground(R.id.inner_content_card, R.id.container_full_status)

        rv.setViewVisibility(R.id.container_full_status, View.GONE)
        rv.setViewVisibility(R.id.inner_content_card, View.VISIBLE)
        rv.setViewVisibility(R.id.container_courses, View.VISIBLE)
        rv.setViewVisibility(R.id.container_status, View.GONE)

        bindWidgetClickIntent(context, rv, WeekCoursesNativeProvider::class.java)

        val currentWeek = snapshot.currentWeekOrNull()
        if (currentWeek == null) {
            rv.setViewVisibility(R.id.inner_content_card, View.GONE)
            rv.setViewVisibility(R.id.container_full_status, View.VISIBLE)
            rv.setTextViewText(R.id.tv_full_status_title, context.getString(R.string.title_vacation))
            rv.setTextViewText(R.id.tv_full_status_msg, context.getString(R.string.widget_vacation_expecting))
            return rv
        }

        val today = LocalDate.now()
        val todayStr = today.toString()
        val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
        // 快照数据是「今天起 7 天」的滚动窗口（见 WidgetUpdateHelper），周末必然跨到下个
        // 自然周；这里按自然周（周一为一周之始）截断，保证表头「本周还有 N 节课」真的只
        // 数本周，周日晚上也能正确落到「本周课程已结束」空态（与文件顶部口径一致）。
        val weekEndStr = today.plusDays(7L - today.dayOfWeek.value).toString()

        val remaining = snapshot.week_courses.asSequence()
            .filter { !it.is_skipped }
            .filter { course ->
                when {
                    course.date > todayStr -> course.date <= weekEndStr
                    // 结束时间无法解析时按「尚未结束」处理（与 remainingToday 同口径）
                    course.date == todayStr ->
                        WidgetCourseSelection.minutesOf(course.end_time)?.let { it > nowMinutes } ?: true
                    else -> false
                }
            }
            .sortedWith(
                compareBy(
                    { it.date },
                    { WidgetCourseSelection.minutesOf(it.start_time) ?: Int.MAX_VALUE }
                )
            )
            .toList()

        rv.setTextViewText(
            R.id.tv_header_title,
            context.getString(R.string.status_current_week_format, currentWeek)
        )
        rv.setTextViewTextSize(R.id.tv_header_title, TypedValue.COMPLEX_UNIT_SP, headerSizeSp(space))

        if (remaining.isEmpty()) {
            rv.setTextViewText(R.id.tv_header_count_summary, "")
            rv.setViewVisibility(R.id.container_courses, View.GONE)
            rv.setViewVisibility(R.id.container_status, View.VISIBLE)
            rv.setTextViewText(R.id.tv_status_title, context.getString(R.string.widget_week_finished))
            rv.setTextViewText(R.id.tv_status_msg, context.getString(R.string.widget_week_finished_hint))
            return rv
        }

        rv.setTextViewText(
            R.id.tv_header_count_summary,
            context.getString(R.string.widget_week_remaining, remaining.size)
        )

        if (remaining.size > 1) {
            // 多行：全部课程交给 ListView（滑动可见，不截断）。
            rv.setRemoteAdapter(
                R.id.container_courses,
                CourseListRemoteViewsService.intent(context, CourseListRemoteViewsService.Mode.WEEK)
            )
            rv.setViewVisibility(R.id.container_courses, View.VISIBLE)
            rv.setPendingIntentTemplate(
                R.id.container_courses,
                widgetClickPendingIntent(context, WeekCoursesNativeProvider::class.java)
            )
        } else {
            // 单行：静态插行，与旧行为一致，省一次 service 绑定。
            rv.setViewVisibility(R.id.container_courses, View.VISIBLE)
            addRows(rv, R.id.container_courses, context, remaining) { course ->
                weekCourseRow(context, course, snapshot, space, today)
            }
        }
        return rv
    }
}
