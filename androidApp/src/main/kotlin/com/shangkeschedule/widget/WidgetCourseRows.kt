package com.shangkeschedule.widget

import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import com.shangkeschedule.R
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 七个规格共用（仅「考试倒计时」不消费本文件）的「课程行构造 / 列表插入 + 分隔线 / 空态与周次判定」。
 *
 * 此前 Tiny / Compact / DoubleDays / ListVertical 各自逐字重复了这三段逻辑：
 * ① 课程行内的色条取色（三处逐字相同，仅行布局 ID 不同；v4.61.0 起教师不再显示）；
 * ② `forEachIndexed` 插入行 + 「非最后一项补横向分隔线」（三处逐字相同）；
 * ③ `current_week <= 0 → null` 与「今日无课 / 今日已结课」文案二选一（四处逐字相同）。
 * 这里收敛为单一实现（v3.69.5，同功能冗余清理，与 v3.66.3 的
 * [com.shangkeschedule.widget.applyCourseColor]、[bindWidgetClickIntent] 同一路数）。
 *
 * 注意：只抽取行**内容**，不抽取行**布局**——各尺寸的行布局与 ID 本就不同，
 * 强行统一会改变组件外观。
 */

/** 当前周次；`<= 0` 视为「假期 / 未开学」。六个课程类 Renderer 的统一口径。 */
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
 * 课程行的公共部分：色条取色。
 *
 * v4.61.0 起不再显示教师（时间/地点/课程名优先，省出的约 13dp/行用于放大字号）；
 * `teacher` 字段保留在 proto 内，零数据层改动。
 */
internal fun RemoteViews.bindCourseRowBody(
    context: Context,
    course: WidgetCourseProto,
    snapshot: WidgetSnapshot
) {
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
    snapshot: WidgetSnapshot,
    space: WidgetSpaceClass = WidgetSpaceClass.S
): RemoteViews = RemoteViews(context.packageName, R.layout.widget_item_course_common).apply {
    setTextViewText(R.id.tv_course_name, course.name)
    setTextViewText(R.id.tv_course_position, course.position)
    setTextViewText(R.id.tv_course_time, "${course.start_time.take(5)}-${course.end_time.take(5)}")
    // 按空间放大 + 按字数自适应（v4.61.0，见 WidgetTextScale）
    setTextViewTextSize(R.id.tv_course_name, TypedValue.COMPLEX_UNIT_SP, courseNameSizeSp(space))
    val metaSp = rowMetaSizeSp(space)
    setTextViewTextSize(R.id.tv_course_position, TypedValue.COMPLEX_UNIT_SP, metaSp)
    setTextViewTextSize(R.id.tv_course_time, TypedValue.COMPLEX_UNIT_SP, metaSp)
    bindCourseRowBody(context, course, snapshot)
}

/**
 * 把任意行依次插入容器，行间补横向分隔线（最后一行之后不补）。
 *
 * 泛型化（v4.67.0）：C3「周课程」消费 `WidgetCourseProto`、C4「日程清单」消费
 * `WidgetAgendaProto`，而「插行 + 分隔线」这段逻辑与元素类型无关，故收敛为
 * [addRows]；[addCourseRows] 保留为课程行的薄封装，既有 6 个调用点零改动。
 */
internal fun <T> addRows(
    rootRv: RemoteViews,
    containerId: Int,
    context: Context,
    items: List<T>,
    rowFactory: (T) -> RemoteViews
) {
    items.forEachIndexed { index, item ->
        rootRv.addView(containerId, rowFactory(item))

        if (index < items.size - 1) {
            rootRv.addView(containerId, RemoteViews(context.packageName, R.layout.widget_divider_horizontal))
        }
    }
}

/** 把课程行依次插入容器，行间补横向分隔线（最后一行之后不补）。 */
internal fun addCourseRows(
    rootRv: RemoteViews,
    containerId: Int,
    context: Context,
    courses: List<WidgetCourseProto>,
    rowFactory: (WidgetCourseProto) -> RemoteViews
) = addRows(rootRv, containerId, context, courses, rowFactory)

/**
 * 「今日课程」条目 B 行（`widget_item_course_list_node`）的统一构造。
 *
 * v4.83.0 起有两个消费方：[com.shangkeschedule.widget.list_vertical.ListVerticalNativeRenderer]
 * （静态路径，容量 ≤ MAX_ROWS 时仍走它，保持单次 RemoteViews 更新）与
 * [com.shangkeschedule.widget.courselist.CourseListRemoteViewsService]（ListView 路径，
 * 课程超出容量时可滑动）。行内容（字号 / 取色 / 时间截取）必须完全一致，
 * 否则同一门课在「渲染时」与「滑动后」外观不同。
 */
internal fun courseListRow(
    context: Context,
    course: WidgetCourseProto,
    snapshot: WidgetSnapshot,
    space: WidgetSpaceClass = WidgetSpaceClass.S
): RemoteViews = RemoteViews(context.packageName, R.layout.widget_item_course_list_node).apply {
    setTextViewText(R.id.tv_course_name, course.name)
    setTextViewText(R.id.tv_course_position, course.position)
    setTextViewText(R.id.tv_course_start_time, course.start_time.take(5))
    setTextViewText(R.id.tv_course_end_time, course.end_time.take(5))
    // 按空间放大 + 按字数自适应（v4.61.0，见 WidgetTextScale）
    setTextViewTextSize(R.id.tv_course_name, TypedValue.COMPLEX_UNIT_SP, courseNameSizeSp(space))
    val metaSp = rowMetaSizeSp(space)
    setTextViewTextSize(R.id.tv_course_position, TypedValue.COMPLEX_UNIT_SP, metaSp)
    setTextViewTextSize(R.id.tv_course_start_time, TypedValue.COMPLEX_UNIT_SP, metaSp)
    setTextViewTextSize(R.id.tv_course_end_time, TypedValue.COMPLEX_UNIT_SP, metaSp)
    bindCourseRowBody(context, course, snapshot)
}

/**
 * 「周课程」条目（`widget_item_week_course_node`）的统一构造。
 *
 * v4.83.0 起两个消费方同上（WeekCoursesNativeRenderer 静态路径 +
 * CourseListRemoteViewsService 的周课程模式）。[dayLabel] 的「今天/明天/周X」口径
 * 也由本函数承载，保证滑动后滚出来的行日期标签一致。
 */
internal fun weekCourseRow(
    context: Context,
    course: WidgetCourseProto,
    snapshot: WidgetSnapshot,
    space: WidgetSpaceClass = WidgetSpaceClass.S,
    today: LocalDate = LocalDate.now()
): RemoteViews = RemoteViews(context.packageName, R.layout.widget_item_week_course_node).apply {
    setTextViewText(R.id.tv_week_day, dayLabel(context, course.date, today))
    setTextViewText(R.id.tv_week_time, course.start_time.take(5))
    setTextViewText(R.id.tv_week_name, course.name)
    setTextViewText(R.id.tv_week_position, course.position)
    setTextViewTextSize(R.id.tv_week_name, TypedValue.COMPLEX_UNIT_SP, courseNameSizeSp(space))
    val metaSp = rowMetaSizeSp(space)
    setTextViewTextSize(R.id.tv_week_day, TypedValue.COMPLEX_UNIT_SP, metaSp)
    setTextViewTextSize(R.id.tv_week_time, TypedValue.COMPLEX_UNIT_SP, metaSp)
    setTextViewTextSize(R.id.tv_week_position, TypedValue.COMPLEX_UNIT_SP, metaSp)
    applyCourseColor(
        context,
        lightViewId = R.id.course_bar_light,
        darkViewId = R.id.course_bar_dark,
        maps = snapshot.style?.course_color_maps,
        colorInt = course.color_int
    )
}

/** 周课程行首日期：今天 / 明天用相对文案，其余用「周X」（原 WeekCoursesNativeRenderer 私有实现上移）。 */
internal fun dayLabel(context: Context, date: String, today: LocalDate): String {
    val target = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    return when (ChronoUnit.DAYS.between(today, target)) {
        0L -> context.getString(R.string.widget_title_today)
        1L -> context.getString(R.string.widget_title_tomorrow)
        else -> target.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}
