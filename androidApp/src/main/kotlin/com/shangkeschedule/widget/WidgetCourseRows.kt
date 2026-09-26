package com.shangkeschedule.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.shangkeschedule.R

/**
 * 四个 Renderer 共用的「课程行构造 / 列表插入 + 分隔线 / 空态与周次判定」。
 *
 * 此前 Tiny / Compact / DoubleDays / ListVertical 各自逐字重复了这三段逻辑：
 * ① 课程行内的老师可见性与色条取色（三处逐字相同，仅行布局 ID 不同）；
 * ② `forEachIndexed` 插入行 + 「非最后一项补横向分隔线」（三处逐字相同）；
 * ③ `current_week <= 0 → null` 与「今日无课 / 今日已结课」文案二选一（四处逐字相同）。
 * 这里收敛为单一实现（v3.69.5，同功能冗余清理，与 v3.66.3 的
 * [com.shangkeschedule.widget.applyCourseColor]、[bindWidgetClickIntent] 同一路数）。
 *
 * 注意：只抽取行**内容**，不抽取行**布局**——各尺寸的行布局与 ID 本就不同，
 * 强行统一会改变组件外观。
 */

/** 当前周次；`<= 0` 视为「假期 / 未开学」。四个 Renderer 的统一口径。 */
internal fun WidgetSnapshot.currentWeekOrNull(): Int? =
    if (current_week <= 0) null else current_week

/**
 * 空态文案：今天整天没课 → `text_no_courses_today`；今天有课但都已结束 → `widget_today_courses_finished`。
 */
internal fun todayEmptyTip(context: Context, courses: List<WidgetCourseProto>, todayStr: String): String =
    if (WidgetCourseSelection.allToday(courses, todayStr).isEmpty()) {
        context.getString(R.string.text_no_courses_today)
    } else {
        context.getString(R.string.widget_today_courses_finished)
    }

/**
 * 课程行的公共部分：老师可见性 + 色条取色。
 * 三种行布局（common / list_node / 各尺寸自有布局）共用，调用方只负责各自的时间与名称字段。
 */
internal fun RemoteViews.bindCourseRowBody(
    context: Context,
    course: WidgetCourseProto,
    snapshot: WidgetSnapshot
) {
    if (course.teacher.isNotBlank()) {
        setViewVisibility(R.id.tv_course_teacher, View.VISIBLE)
        setTextViewText(R.id.tv_course_teacher, course.teacher)
    } else {
        setViewVisibility(R.id.tv_course_teacher, View.GONE)
    }

    // 颜色渲染（取色越界 / 缺色时回落到 widget_course_fallback）
    applyCourseColor(
        context,
        lightViewId = R.id.course_indicator,
        darkViewId = R.id.course_indicator_dark,
        maps = snapshot.style?.course_color_maps,
        colorInt = course.color_int
    )
}

/** `widget_item_course_common` 课程行（Compact / DoubleDays 共用的行布局）。 */
internal fun commonCourseRow(
    context: Context,
    course: WidgetCourseProto,
    snapshot: WidgetSnapshot
): RemoteViews = RemoteViews(context.packageName, R.layout.widget_item_course_common).apply {
    setTextViewText(R.id.tv_course_name, course.name)
    setTextViewText(R.id.tv_course_position, course.position)
    setTextViewText(R.id.tv_course_time, "${course.start_time.take(5)}-${course.end_time.take(5)}")
    bindCourseRowBody(context, course, snapshot)
}

/** 把课程行依次插入容器，行间补横向分隔线（最后一行之后不补）。 */
internal fun addCourseRows(
    rootRv: RemoteViews,
    containerId: Int,
    context: Context,
    courses: List<WidgetCourseProto>,
    rowFactory: (WidgetCourseProto) -> RemoteViews
) {
    courses.forEachIndexed { index, course ->
        rootRv.addView(containerId, rowFactory(course))

        if (index < courses.size - 1) {
            rootRv.addView(containerId, RemoteViews(context.packageName, R.layout.widget_divider_horizontal))
        }
    }
}
