package com.shangkeschedule.widget.courselist

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.widget.WidgetCourseProto
import com.shangkeschedule.widget.WidgetCourseSelection
import com.shangkeschedule.widget.WidgetSnapshot
import com.shangkeschedule.widget.WidgetSpaceClass
import com.shangkeschedule.widget.courseListRow
import com.shangkeschedule.widget.weekCourseRow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 列表型小组件的 ListView 数据源（v4.83.0）：让「今日课程 / 周课程」在课程超出
 * 可视高度时可在组件内滑动查看，替代此前「LinearLayout 静态插行 + 超容量截断」。
 *
 * ## 为什么是 RemoteViewsService
 *
 * ListView 的行内容在 RemoteViews 里只能经
 * `RemoteViewsService.RemoteViewsFactory` 提供（collection widget 三件套之一：
 * ListView 布局 + Service + Factory）。Factory 运行在系统拉起的绑定回调里（App 进程的
 * binder 线程，非主线程，`runBlocking` 安全），与 App 的渲染链（WidgetUpdateHelper）
 * 解耦：桌面滑动时按需取行，不必整份重渲染。
 *
 * ## 数据口径（与渲染链同源，不做第二个事实来源）
 *
 * 直接读主库（[WidgetRepository]），窗口「今天起 7 天」，与 WidgetUpdateHelper 同源：
 * - 「今日课程」= `WidgetCourseSelection.remainingToday`（未结束、未跳过）；
 * - 「周课程」= 自然周截断 + 过滤已跳过 +（日期, 开始时间）升序，与
 *   WeekCoursesNativeRenderer 逐字段一致；
 * - 周次同步重读——假期判定由 Renderer 静态分支负责，本 Factory 只供行。
 *
 * 读库失败 / 超时（[DATA_TIMEOUT]）按 0 行处理：ListView 空列表由 15 分钟 tick 与
 * 下次 `notifyAppWidgetViewDataChanged` 自愈，与渲染链「超时保留旧快照」的取向一致
 * ——空列表比过期列表误导小（同 WidgetUpdateHelper 日程读取的注释口径）。
 *
 * ## 刷新触发
 *
 * WidgetUpdateHelper 完成静态渲染后对存在实例的列表组件调
 * `notifyAppWidgetViewDataChanged`；系统在组件首次绑定 / 桌面重启时也会重新
 * `onCreate` → `reloadData`。两条路都拿到最新课程。
 */
class CourseListRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CourseListFactory(applicationContext, intent)

    /** Factory 模式：「今日课程」或「周课程」。 */
    enum class Mode { TODAY, WEEK }

    companion object {
        /** intent 上的模式键；缺失或非法值按 [Mode.TODAY] 处理。 */
        const val EXTRA_MODE = "com.shangkeschedule.extra.WIDGET_COURSE_LIST_MODE"

        /** 单次取数预算（秒）：与渲染链 3s 课程读取同量级，超时按空列表自愈。 */
        private val DATA_TIMEOUT = 3.seconds

        fun intent(context: Context, mode: Mode): Intent =
            Intent(context, CourseListRemoteViewsService::class.java).apply {
                putExtra(EXTRA_MODE, mode.name)
            }
    }

    /**
     * 行提供器。构造必须轻量（系统绑定时 new）；数据在 [onCreate] 与每次
     * [onDataSetChanged]（notifyAppWidgetViewDataChanged 后回调）重取。
     */
    class CourseListFactory(
        private val context: Context,
        intent: Intent
    ) : RemoteViewsFactory, KoinComponent {

        private val widgetRepository: WidgetRepository by inject()

        private val mode: Mode = runCatching {
            Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: Mode.TODAY.name)
        }.getOrDefault(Mode.TODAY)

        /** 本轮展示的行（已按各模式的过滤/排序口径就绪）；读库失败为空列表。 */
        private var rows: List<WidgetCourseProto> = emptyList()

        /** 周次；`<= 0` 视为假期（Renderer 静态分支不会挂适配器），仅用于样式壳。 */
        private var currentWeek: Int = 0

        override fun onCreate() {
            reloadData()
        }

        override fun onDataSetChanged() {
            reloadData()
        }

        override fun onDestroy() {
            rows = emptyList()
        }

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews? {
            val course = rows.getOrNull(position) ?: return null
            return when (mode) {
                Mode.TODAY -> courseListRow(context, course, snapshotStub, WidgetSpaceClass.S)
                Mode.WEEK -> weekCourseRow(context, course, snapshotStub)
            }
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 2

        override fun getItemId(position: Int): Long =
            (rows.getOrNull(position)?.id ?: "$mode-$position").hashCode().toLong()

        override fun hasStableIds(): Boolean = false

        private fun reloadData() {
            val today = LocalDate.now()
            val result = runCatching {
                runBlocking {
                    val week = withTimeoutOrNull(DATA_TIMEOUT) {
                        widgetRepository.getCurrentWeekFlow().first()
                    } ?: 0
                    val courses = withTimeoutOrNull(DATA_TIMEOUT) {
                        widgetRepository
                            .getWidgetCoursesByDateRange(today.toString(), today.plusDays(6L).toString())
                            .first()
                    }.orEmpty()
                    week to courses.map { it.toRowProto() }
                }
            }.getOrElse {
                android.util.Log.w("CourseListFactory", "列表组件行数据读取失败，按空列表处理", it)
                0 to emptyList<WidgetCourseProto>()
            }
            currentWeek = result.first
            rows = when (mode) {
                Mode.TODAY -> {
                    val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
                    WidgetCourseSelection.remainingToday(result.second, today.toString(), nowMinutes)
                }
                Mode.WEEK -> {
                    // 与 WeekCoursesNativeRenderer 同口径：自然周（周一为一周之始）截断，
                    // 已跳过的课不出现，按（日期, 开始时间）升序。
                    val weekEndStr = today.plusDays(7L - today.dayOfWeek.value).toString()
                    result.second.asSequence()
                        .filter { !it.is_skipped }
                        .filter { course ->
                            when {
                                course.date > today.toString() -> course.date <= weekEndStr
                                course.date == today.toString() ->
                                    WidgetCourseSelection.minutesOf(course.end_time)
                                        ?.let { it > nowMinutes() } ?: true
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
                }
            }
        }

        private fun nowMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

        /**
         * 行渲染用的样式壳。颜色映射由 courseListRow / weekCourseRow 消费
         * `style?.course_color_maps`——Factory 侧暂不读样式 DataStore（行内色条在
         * 样式缺失时回落 widget_course_fallback 默认色，与渲染链空样式分支一致）；
         * 后续若要跟随主题色，在这里补一次 styleDataStore 读取即可，签名不变。
         */
        private val snapshotStub: WidgetSnapshot
            get() = WidgetSnapshot(current_week = currentWeek)
    }
}

/**
 * 主库实体 → 行渲染 proto：与 WidgetUpdateHelper 的私有 toProto 同字段映射
 * （该函数是 private，本处复制一份并在此注明来源；字段漂移时两处需同步）。
 */
private fun com.shangkeschedule.data.db.widget.WidgetCourse.toRowProto(): WidgetCourseProto =
    WidgetCourseProto(
        id = id,
        name = name,
        teacher = teacher,
        position = position,
        start_time = startTime,
        end_time = endTime,
        color_int = colorInt,
        is_skipped = isSkipped,
        date = date
    )
