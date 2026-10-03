package com.shangkeschedule.widget.exam_countdown

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.shangkeschedule.R
import com.shangkeschedule.widget.WidgetExamProto
import com.shangkeschedule.widget.WidgetSnapshot
import com.shangkeschedule.widget.WidgetSpaceClass
import com.shangkeschedule.widget.bindWidgetClickIntent
import com.shangkeschedule.widget.courseNameSizeSp
import com.shangkeschedule.widget.setWidgetCardBackground
import com.shangkeschedule.widget.spaceDeltaSp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 「考试倒计时」小组件渲染器（v4.67.0）。
 *
 * 数据源为主库日程表（`schedule_events` 中 `category = "exam"`）：
 * `WidgetUpdateHelper` 取「今天及以后、按日期升序最多 5 场」写入
 * `WidgetSnapshot.exams`，本渲染器只消费其中的**第一场**（最近一场），
 * 剩余场次仅用一行「还有 N 场考试」提示。
 *
 * 三点与其它组件**刻意不同**的口径：
 * 1. **不看 `current_week`**：其它组件以 `current_week <= 0` 判定假期并显示「假期中」，
 *    但考试（尤其补考、缓考）恰恰可能落在假期里；把假期当空态会藏掉唯一一条真正要紧的信息。
 * 2. **不做跨天兜底**：本组件没有「明日第一节」这种降级——没有未来考试就是空态。
 * 3. **色条固定红色**（`widget_exam_accent`），不参与课程 12 档色板：考试是红色语义，
 *    与日程页 EXAM 分类的 danger 色一致（见 WidgetCoursePalette 注释）。
 *
 * 倒计时按**天**计算（`D-3` → 中文「3 天后」），不显示时分秒：
 * 小组件最小刷新粒度是 15 分钟，且考试粒度本来就是「天」。
 */
object ExamCountdownNativeRenderer {

    /** 考试名基准字号（sp）：与「下一节课」同名号，比列表条目大一号。 */
    private const val EXAM_NAME_BASE_SP = 18

    /** 日期 / 地点基准字号（sp）。 */
    private const val EXAM_META_BASE_SP = 12

    fun render(
        context: Context,
        snapshot: WidgetSnapshot,
        maxCourseCount: Int,
        space: WidgetSpaceClass = WidgetSpaceClass.S
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_exam_countdown_native)
        rv.setWidgetCardBackground(R.id.inner_content_card, R.id.container_status)
        resetWidgetState(rv)
        bindWidgetClickIntent(context, rv, ExamCountdownNativeProvider::class.java)

        // 快照侧已按日期升序，这里再排一次：protobuf 的 repeated 顺序是契约的一部分，
        // 但渲染器不假设上游一定守约（与 ListVertical 的防御口径一致）。
        val exams = snapshot.exams.sortedWith(
            compareBy({ it.date }, { it.start_time })
        )

        val nearest = exams.firstOrNull()
        if (nearest == null) {
            showStatus(
                rv,
                context.getString(R.string.widget_exam_none),
                context.getString(R.string.widget_exam_none_hint)
            )
            return rv
        }

        bindExam(context, rv, nearest, space, exams.size)
        return rv
    }

    private fun bindExam(
        context: Context,
        rv: RemoteViews,
        exam: WidgetExamProto,
        space: WidgetSpaceClass,
        totalCount: Int
    ) {
        rv.setViewVisibility(R.id.container_info, View.VISIBLE)
        rv.setViewVisibility(R.id.container_status, View.GONE)

        rv.setTextViewText(R.id.tv_exam_label, context.getString(R.string.widget_exam_label))

        // 倒计时胶囊：解析失败（脏数据）时不显示胶囊，宁可少一行也不显示「NaN 天后」
        val countdown = countdownText(context, exam.date)
        if (countdown == null) {
            rv.setViewVisibility(R.id.tv_exam_countdown, View.GONE)
        } else {
            rv.setViewVisibility(R.id.tv_exam_countdown, View.VISIBLE)
            rv.setTextViewText(R.id.tv_exam_countdown, countdown)
        }

        rv.setTextViewText(R.id.tv_exam_name, exam.title)
        rv.setTextViewTextSize(
            R.id.tv_exam_name,
            TypedValue.COMPLEX_UNIT_SP,
            courseNameSizeSp(space, EXAM_NAME_BASE_SP)
        )

        val metaSp = (EXAM_META_BASE_SP + spaceDeltaSp(space)).toFloat()
        rv.setTextViewTextSize(R.id.tv_exam_time, TypedValue.COMPLEX_UNIT_SP, metaSp)
        rv.setTextViewTextSize(R.id.tv_exam_position, TypedValue.COMPLEX_UNIT_SP, metaSp)

        // 全天考试（无开始时间）只显示日期
        val start = exam.start_time.take(5)
        rv.setTextViewText(
            R.id.tv_exam_time,
            if (start.isBlank()) exam.date else "${exam.date}  $start"
        )

        val place = exam.location.trim()
        if (place.isBlank()) {
            rv.setViewVisibility(R.id.tv_exam_position, View.GONE)
        } else {
            rv.setViewVisibility(R.id.tv_exam_position, View.VISIBLE)
            rv.setTextViewText(R.id.tv_exam_position, place)
        }

        if (totalCount > 1) {
            rv.setViewVisibility(R.id.tv_exam_more, View.VISIBLE)
            rv.setTextViewText(
                R.id.tv_exam_more,
                context.getString(R.string.widget_exam_more, totalCount - 1)
            )
        } else {
            rv.setViewVisibility(R.id.tv_exam_more, View.GONE)
        }

        // 考试色条：固定红色，浅色/深色两份资源各自声明颜色项（values / values-night）
        val accent = context.getColor(R.color.widget_exam_accent)
        rv.setInt(R.id.course_bar_light, "setColorFilter", accent)
        rv.setInt(R.id.course_bar_dark, "setColorFilter", accent)
    }

    /**
     * 倒计时文案：今天 / 明天 / N 天后；日期无法解析时返回 `null`（调用方隐藏胶囊）。
     *
     * 已过期的考试理论上不会进快照（上游按 `date >= today` 过滤），
     * 但渲染器不依赖该契约：负天数一律落到「今天」，避免出现「-2 天后」。
     */
    private fun countdownText(context: Context, date: String): String? {
        val examDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        val days = ChronoUnit.DAYS.between(LocalDate.now(), examDate).toInt()
        return when {
            days <= 0 -> context.getString(R.string.widget_exam_today)
            days == 1 -> context.getString(R.string.widget_exam_tomorrow)
            else -> context.getString(R.string.widget_exam_days, days)
        }
    }

    /** 每次渲染前强制归零可见性，消除跨状态残留（与其余五个组件同一纪律）。 */
    private fun resetWidgetState(rv: RemoteViews) {
        rv.setViewVisibility(R.id.container_info, View.GONE)
        rv.setViewVisibility(R.id.container_status, View.GONE)
        rv.setViewVisibility(R.id.tv_exam_countdown, View.VISIBLE)
        rv.setViewVisibility(R.id.tv_exam_position, View.VISIBLE)
        rv.setViewVisibility(R.id.tv_exam_more, View.VISIBLE)
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
