package com.shangkeschedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.datastore.core.DataStore
import com.shangkeschedule.R
import com.shangkeschedule.data.db.main.ScheduleCategory
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.data.model.schedule_style.ScheduleGridStyleProto
import com.shangkeschedule.data.model.toProto
import com.shangkeschedule.data.repository.ScheduleEventRepository
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.widget.agenda_list.AgendaListNativeProvider
import com.shangkeschedule.widget.agenda_list.AgendaListNativeRenderer
import com.shangkeschedule.widget.compact.CompactNativeProvider
import com.shangkeschedule.widget.compact.CompactNativeRenderer
import com.shangkeschedule.widget.double_days.DoubleDaysNativeProvider
import com.shangkeschedule.widget.double_days.DoubleDaysNativeRenderer
import com.shangkeschedule.widget.exam_countdown.ExamCountdownNativeProvider
import com.shangkeschedule.widget.exam_countdown.ExamCountdownNativeRenderer
import com.shangkeschedule.widget.list_vertical.ListVerticalNativeProvider
import com.shangkeschedule.widget.list_vertical.ListVerticalNativeRenderer
import com.shangkeschedule.widget.next_course.NextCourseNativeProvider
import com.shangkeschedule.widget.next_course.NextCourseNativeRenderer
import com.shangkeschedule.widget.tiny.TinyNativeProvider
import com.shangkeschedule.widget.tiny.TinyNativeRenderer
import com.shangkeschedule.widget.week_courses.WeekCoursesNativeProvider
import com.shangkeschedule.widget.week_courses.WeekCoursesNativeRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// 创建一个局部的注入代理中心，用于在全局顶层方法中安全提取注入实例
private object WidgetDependencyContainer : KoinComponent {
    val repository: WidgetRepository by inject()
    val styleDataStore: DataStore<ScheduleGridStyleProto> by inject()

    /** v4.67.0：「考试倒计时」组件的数据源（主库日程表）。 */
    val scheduleEventRepository: ScheduleEventRepository by inject()
}

/** 组件规格，决定「可容纳课程条数」的计算口径。 */
private enum class WidgetKind {
    TINY, COMPACT, DOUBLE_DAYS, LIST_VERTICAL, NEXT_COURSE, EXAM_COUNTDOWN,
    // v4.67.0：C3「周课程」/ C4「日程清单」两个列表型规格
    WEEK_COURSES, AGENDA_LIST
}

/** 写入快照的未来考试场上限（v4.67.0）：渲染只消费第一场，其余仅用于「还有 N 场」计数。 */
private const val MAX_WIDGET_EXAMS = 5

/** 写入快照的未来日程条数上限（v4.67.0）：「日程清单」组件的清单长度。 */
private const val MAX_WIDGET_AGENDA = 8

/** 「周课程」组件的数据窗口（天，含今天，v4.67.0）：今天起连续 7 天。 */
private const val WIDGET_WEEK_WINDOW_DAYS = 7

/**
 * 周次读取结果的包装。
 *
 * `getCurrentWeekFlow()` 本身会发射 `Int?`（`null` 表示假期），而 `withTimeoutOrNull` 超时同样
 * 返回 `null`——两者无法区分，故用一层包装把「读到假期」与「没读到」分开。
 */
private class WeekRead(val value: Int?)

/**
 * 刷新触发原因，决定「已有渲染正在进行中」时本次请求是否允许被合并丢弃。
 */
enum class WidgetRefreshReason {
    /**
     * 系统提醒（`AppWidgetProvider.onUpdate`）。
     *
     * 系统会为 8 个 receiver **同时**各发一次广播，而每次回调都会遍历全部 8 种组件——即同一份
     * 数据在同一时刻被渲染 64 次。这类提醒的语义只是「组件可能需要重绘」，若已有渲染正在进行，
     * 它已经被满足，可安全丢弃。
     */
    SYSTEM_NUDGE,

    /**
     * 必须落到界面上：数据/样式变更、周期时间推进（15 分钟 tick）、组件尺寸变化。
     * 渲染进行中收到时**登记一次补跑**，绝不丢弃（v3.66.3）。
     */
    REQUIRED
}

private val updateMutex = Mutex()

/** 渲染进行中收到的 [WidgetRefreshReason.REQUIRED] 请求：本次渲染结束后必须补跑一次。 */
@Volatile
private var requiredRefreshPending = false

/**
 * 小组件统一分发中心
 * 负责从 Repository 提取数据并分发给所有 8 种规格的原生 Renderer
 *
 * 并发语义（v3.66.3）：同一时刻只允许一次渲染，其余请求按 [WidgetRefreshReason] 分流——
 * `SYSTEM_NUDGE` 合并丢弃（消除 8 个 receiver 齐发造成的重复渲染），`REQUIRED` 登记补跑
 * （保证任何一次数据变更都不会被吞掉）。
 *
 * 这里刻意**不使用「距上次刷新 < N 毫秒就跳过」的时间窗口**：`WidgetDataSynchronizer` 上游
 * 恰好也有 500ms 防抖，两者会形成隐式的时间对齐契约，一旦上游改动就会静默丢刷新。
 */
suspend fun updateAllWidgets(
    context: Context,
    reason: WidgetRefreshReason = WidgetRefreshReason.REQUIRED
) {
    while (true) {
        if (!updateMutex.tryLock()) {
            // 已有渲染在进行中：仅「必须刷新」的请求登记补跑。
            if (reason == WidgetRefreshReason.REQUIRED) {
                requiredRefreshPending = true
            }
            return
        }
        try {
            requiredRefreshPending = false
            performUpdate(context)
        } finally {
            updateMutex.unlock()
        }
        // 解锁后再读一次：若期间（含「读标志」与「解锁」之间的窄窗口）登记了补跑则继续下一轮。
        if (!requiredRefreshPending) return
    }
}

private suspend fun performUpdate(context: Context) {
    try {
        // 1. 从 Koin 容器中动态获取单例化的仓库与 DataStore
        val repository = WidgetDependencyContainer.repository
        val styleDataStore = WidgetDependencyContainer.styleDataStore

        // 2. 准备基础数据
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        // 「周课程」要连续 7 天（含今天），故读取窗口放宽到一周；其余七个规格仍只消费
        // 今天 + 明天（下方 dbCourses 过滤），数据面与 v4.67.0 之前逐字段一致。
        val weekEnd = today.plusDays((WIDGET_WEEK_WINDOW_DAYS - 1).toLong())

        // 读取超时时直接跳过本次渲染（保留旧快照）——清成空列表会让组件整体空白到下个 15min tick
        val allCourses = withTimeoutOrNull(3.seconds) {
            repository.getWidgetCoursesByDateRange(today.toString(), weekEnd.toString()).first()
        } ?: run {
            Log.w("WidgetSync", "widget 数据读取超时，跳过本次渲染以保留旧快照")
            return
        }
        val dbCourses = allCourses.filter { it.date <= tomorrow.toString() }

        // 周次读取超时必须与课程数据同策略：跳过本次渲染、保留旧快照。
        // 旧实现为 `?: 0`，而六个课程类 Renderer 一律以 `current_week <= 0` 判定假期——Room 读取
        // 一旦超过 2s，组件会误显示「假期中 / 期待新学期」，与上方「保留旧快照」的注释自相矛盾。
        val weekRead = withTimeoutOrNull(2.seconds) {
            WeekRead(repository.getCurrentWeekFlow().first())
        } ?: run {
            Log.w("WidgetSync", "widget 周次读取超时，跳过本次渲染以保留旧快照")
            return
        }
        // WidgetSnapshot.current_week 是非空 int32，用 0 表示「假期 / 未知」（Renderer 按 <= 0 处理）
        val currentWeek = weekRead.value ?: 0

        val currentStyle = withTimeoutOrNull(2.seconds) {
            styleDataStore.data.first()
        }

        val finalStyleToSync = if (currentStyle == null || currentStyle.course_color_maps.isEmpty()) {
            ScheduleGridStyle.DEFAULT.toProto()
        } else {
            currentStyle
        }.copy(
            // 小组件课程色与主题解耦（v4.63.2）：12 档固定色板覆盖主题色板，
            // 主题切换不再改变组件外观（见 WidgetCoursePalette.kt）。
            course_color_maps = widgetCoursePaletteProto()
        )

        // 2.5 未来日程（v4.67.0）：「考试倒计时」与「日程清单」的共同数据源，取自主库日程表。
        //     日期 >= 今天、按（日期, 开始时间）升序；已勾选的条目不进快照——勾掉的考试不该继续
        //     倒计时，被划掉的待办也不该继续占清单（口径与 G3 通知的 ExamCountdownNotifier 一致）。
        //     读取超时按「暂无」渲染：日程表是小表，2s 内读不到属异常，下一个 15 分钟 tick 会自愈；
        //     这里不做「保留旧快照」，因为与「宁可显示空态也不显示过期倒计时」的取向冲突
        //     （过期倒计时比空态更误导）。
        val upcomingEvents = withTimeoutOrNull(2.seconds) {
            WidgetDependencyContainer.scheduleEventRepository.getAllEvents().first()
                .asSequence()
                .filter { !it.done }
                .filter { it.date >= today.toString() }
                .sortedWith(compareBy({ it.date }, { it.startTime ?: "" }))
                .toList()
        } ?: run {
            Log.w("WidgetSync", "日程读取超时，本次按「暂无考试 / 暂无日程」渲染")
            emptyList()
        }

        // 「考试倒计时」只取最近若干场考试：渲染消费第一场，其余用于「还有 N 场考试」计数。
        val examProtoList = upcomingEvents.asSequence()
            .filter { ScheduleCategory.fromKey(it.category) == ScheduleCategory.EXAM }
            .take(MAX_WIDGET_EXAMS)
            .map { event ->
                WidgetExamProto(
                    id = event.id,
                    title = event.title,
                    date = event.date,
                    start_time = event.startTime ?: "",
                    location = event.location ?: ""
                )
            }
            .toList()

        // 「日程清单」消费全部类别（待办 / 活动 / 作业 / 考试 / 其他），仅取前 MAX_WIDGET_AGENDA 条。
        val agendaProtoList = upcomingEvents.asSequence()
            .take(MAX_WIDGET_AGENDA)
            .map { event ->
                WidgetAgendaProto(
                    id = event.id,
                    title = event.title,
                    date = event.date,
                    start_time = event.startTime ?: "",
                    category = event.category,
                    location = event.location ?: "",
                    is_all_day = event.isAllDay
                )
            }
            .toList()

        // 3. 构造数据快照 (Protobuf)
        val courseProtoList = dbCourses.map { it.toProto() }
        // 「周课程」消费完整一周（含今天/明天，与 courses 同源同映射，不引入第二个事实来源）
        val weekCourseProtoList = allCourses.map { it.toProto() }

        val snapshot = WidgetSnapshot(
            current_week = currentWeek,
            style = finalStyleToSync,
            courses = courseProtoList,
            exams = examProtoList,
            week_courses = weekCourseProtoList,
            agenda = agendaProtoList
        )

        // 4. 定义所有原生尺寸的映射列表（同时携带各自规格，用于按类型推算可容纳条数）
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val nativeConfigs = listOf(
            Triple(TinyNativeProvider::class.java, WidgetKind.TINY, TinyNativeRenderer::render),
            Triple(CompactNativeProvider::class.java, WidgetKind.COMPACT, CompactNativeRenderer::render),
            Triple(DoubleDaysNativeProvider::class.java, WidgetKind.DOUBLE_DAYS, DoubleDaysNativeRenderer::render),
            Triple(ListVerticalNativeProvider::class.java, WidgetKind.LIST_VERTICAL, ListVerticalNativeRenderer::render),
            Triple(NextCourseNativeProvider::class.java, WidgetKind.NEXT_COURSE, NextCourseNativeRenderer::render),
            Triple(ExamCountdownNativeProvider::class.java, WidgetKind.EXAM_COUNTDOWN, ExamCountdownNativeRenderer::render),
            Triple(WeekCoursesNativeProvider::class.java, WidgetKind.WEEK_COURSES, WeekCoursesNativeRenderer::render),
            Triple(AgendaListNativeProvider::class.java, WidgetKind.AGENDA_LIST, AgendaListNativeRenderer::render)
        )

        // 4.5 实测列表型组件的条目高度（每次刷新至多测一次，各规格按需测量）。
        //     仅三个列表型规格消费该值，其余规格的条数是固定档位。
        //     v4.61.0 起条目带放大字号渲染，测量必须用「空间档最大字号」（L 档），
        //     否则条数算多溢出。
        //
        //     `by lazy`（v4.64.22）：测量会 inflate + measure 一份行布局，有实测开销；
        //     而桌面没有该规格组件时算出的值永远不被消费 —— 无探针即可证伪的纯浪费。
        //     [RowHeights] 保持「首次需要时才测」，测量本身纯读 density + inflate、无副作用。
        val density = context.resources.displayMetrics.density
        // 头部文字高度随系统字体缩放，故 chrome 不能取常量（v4.67.29）。
        val fontScale = context.resources.configuration.fontScale
        val rowHeights = RowHeights(context)

        // 5. 统一分发更新
        nativeConfigs.forEachIndexed { index, (providerClass, kind, renderFunc) ->
            val componentName = ComponentName(context, providerClass)
            val ids = appWidgetManager.getAppWidgetIds(componentName)

            if (ids.isNotEmpty()) {
                if (index > 0) {
                    delay(300.milliseconds)
                }

                ids.forEach { widgetId ->
                    try {
                        // v4.61.0：按组件宽度定空间档，字号按空间放大（Tiny 内部强制 S 档）。
                        val space = resolveSpaceClass(appWidgetManager, widgetId)
                        val remoteViews = renderFunc(
                            context,
                            snapshot,
                            resolveMaxCourseCount(kind, appWidgetManager, widgetId, rowHeights, density, fontScale),
                            space
                        )
                        appWidgetManager.updateAppWidget(widgetId, remoteViews)
                        Log.d("WidgetUpdateHelper", "成功刷新组件 $widgetId (${providerClass.simpleName})")
                    } catch (e: Exception) {
                        Log.e("WidgetUpdateHelper", "组件 $widgetId 渲染失败 (${providerClass.simpleName})", e)
                    }
                }
            }
        }

    } catch (e: Exception) {
        Log.e("WidgetUpdateHelper", "更新流程异常: ${e.stackTraceToString()}")
    }
}

/** 实测失败时的兜底条目高度（dp），偏大取值以保证「宁可少显示、不可裁切」。 */
private const val LIST_ROW_FALLBACK_DP = 52

/** 测量条目高度时使用的参考宽度（dp）——所有文本均单行 + ellipsize，故高度与宽度无关。 */
private const val LIST_MEASURE_WIDTH_DP = 250

/**
 * 三种列表型规格各自的实测条目高度（px），每项 `by lazy`：桌面没有该规格组件时永不测量。
 *
 * 为什么必须是实测：v3.66.4 曾用常量估算条目高度，而条目实际占位比分估大 ⇒ 条数算多 ⇒
 * 列表溢出、末条被静默裁切（LinearLayout 不滚动，超出部分直接不可见）。见 [WidgetListCapacity]。
 * 为什么必须 lazy：inflate + measure 有实测开销，而某规格在桌面上一件都没有时该值永不被消费。
 */
private class RowHeights(private val context: Context) {

    /** 垂直列表课表条目（`widget_item_course_list_node`）。 */
    val list: Int by lazy {
        measureRowHeightPx(context, R.layout.widget_item_course_list_node) { view, nameSp, metaSp ->
            view.setTextSizeTo(R.id.tv_course_name, nameSp)
            view.setTextSizeTo(R.id.tv_course_position, metaSp)
            view.setTextSizeTo(R.id.tv_course_start_time, metaSp)
            view.setTextSizeTo(R.id.tv_course_end_time, metaSp)
        }
    }

    /** 周课程条目（`widget_item_week_course_node`，v4.67.0 起）。 */
    val week: Int by lazy {
        measureRowHeightPx(context, R.layout.widget_item_week_course_node) { view, nameSp, metaSp ->
            view.setTextSizeTo(R.id.tv_week_name, nameSp)
            view.setTextSizeTo(R.id.tv_week_day, metaSp)
            view.setTextSizeTo(R.id.tv_week_time, metaSp)
            view.setTextSizeTo(R.id.tv_week_position, metaSp)
        }
    }

    /** 日程清单条目（`widget_item_agenda_node`，v4.67.0 起）。 */
    val agenda: Int by lazy {
        measureRowHeightPx(context, R.layout.widget_item_agenda_node) { view, nameSp, metaSp ->
            view.setTextSizeTo(R.id.tv_agenda_title, nameSp)
            view.setTextSizeTo(R.id.tv_agenda_day, metaSp)
            view.setTextSizeTo(R.id.tv_agenda_time, metaSp)
            view.setTextSizeTo(R.id.tv_agenda_meta, metaSp)
        }
    }
}

/**
 * 实测某个条目行布局在当前配置下的高度，单位 px。
 *
 * 固定常量无法适配 fontScale / 字体 / 语言差异，故改为**实测**：在 App 进程 inflate 条目布局并测量。
 * 渲染发生在 App 侧，不违反 RemoteViews「不能自绘」的限制。测量失败时回落 [LIST_ROW_FALLBACK_DP]。
 *
 * v4.61.0：一律按「最大可能字号」（L 档）测量，保证条数宁可少算不可多算。
 */
private fun measureRowHeightPx(
    context: Context,
    layoutRes: Int,
    applyTextSizes: (View, Float, Float) -> Unit
): Int = runCatching {
    val density = context.resources.displayMetrics.density
    val view = LayoutInflater.from(context).inflate(layoutRes, null, false)
    applyTextSizes(
        view,
        (ROW_NAME_BASE_SP + spaceDeltaSp(WidgetSpaceClass.L)).toFloat(),
        (ROW_META_BASE_SP + spaceDeltaSp(WidgetSpaceClass.L)).toFloat()
    )
    val widthPx = (LIST_MEASURE_WIDTH_DP * density).toInt().coerceAtLeast(1)
    view.measure(
        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val measured = view.measuredHeight
    if (measured > 0) measured else (LIST_ROW_FALLBACK_DP * density).toInt()
}.getOrElse { (LIST_ROW_FALLBACK_DP * context.resources.displayMetrics.density).toInt() }

/** 把测量用字号写到布局里的某个 ID 上（ID 缺失时静默跳过，不让整个测量失败）。 */
private fun View.setTextSizeTo(viewId: Int, sp: Float) {
    findViewById<TextView>(viewId)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
}

/** `WidgetCourse` → 快照 proto：今天/明天数据面与本周数据面共用同一映射，避免两处走样。 */
private fun WidgetCourse.toProto(): WidgetCourseProto = WidgetCourseProto(
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

/**
 * 按组件宽度定空间档（v4.61.0，字号按空间放大的依据）。
 *
 * 取 `OPTION_APPWIDGET_MIN_WIDTH`（下界）：实际宽度只会比它大，用下界定档保证不溢出；
 * 取不到时回落 S 档（= 不放大，行为与 v4.60.0 一致）。
 */
private fun resolveSpaceClass(
    appWidgetManager: AppWidgetManager,
    widgetId: Int
): WidgetSpaceClass {
    val options = appWidgetManager.getAppWidgetOptions(widgetId)
    val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, -1)
    if (minWidthDp <= 0) return WidgetSpaceClass.S
    return spaceClassFor(minWidthDp)
}

/**
 * 按**组件类型**分别计算可容纳的课程条数。
 *
 * 历史：
 * - v3.66.3 之前：四种规格共用一套 `minHeight` 阈值并硬顶 3 条，废掉了 ListVertical 的「4×N」。
 * - v3.66.4：改为按类型分别计算，但 ListVertical 用了偏小的行高常量（36dp），条数偏高 ⇒ 溢出。
 * - v3.66.5（本次）：行高改为**实测**；高度基准改用 `MAX_HEIGHT`；并扣除卡片 padding 与头部占位。
 * - v4.67.29：头部占位不再恒按 fontScale = 1.0 的 39dp，改为 `chromeDpFor(fontScale)` ——
 *   条目行高带 fontScale、头部却是常量，放大字体时可用高度被高估，末条可能被静默裁切。
 *
 * `OPTION_APPWIDGET_MIN_HEIGHT` 是可缩放的**下界**，代表不了当前可用高度，故改用
 * `OPTION_APPWIDGET_MAX_HEIGHT`（当前高度上界）；缺失时回落 MIN，再回落 110dp。
 */
private fun resolveMaxCourseCount(
    kind: WidgetKind,
    appWidgetManager: AppWidgetManager,
    widgetId: Int,
    rowHeights: RowHeights,
    density: Float,
    fontScale: Float
): Int {
    val options = appWidgetManager.getAppWidgetOptions(widgetId)
    val heightDp = options.getInt(
        AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
    )

    // 三种列表型规格共用同一公式，只有实测行高不同；chrome 随 fontScale 缩放（v4.67.29）。
    fun rowsFor(rowHeightPx: Int): Int = WidgetListCapacity.rowsFor(
        heightDp = heightDp,
        chromeDp = WidgetListCapacity.chromeDpFor(fontScale),
        rowHeightPx = rowHeightPx,
        density = density,
        maxRows = WidgetListCapacity.MAX_ROWS
    )

    return when (kind) {
        // 单节课展示，不消费条数上限（Tiny 一节课、NextCourse 一节课、ExamCountdown 一场考试）
        WidgetKind.TINY, WidgetKind.NEXT_COURSE, WidgetKind.EXAM_COUNTDOWN -> 1
        WidgetKind.COMPACT, WidgetKind.DOUBLE_DAYS -> when {
            heightDp < 90 -> 1
            heightDp < 150 -> 2
            else -> 3
        }
        // 列表型规格：可用高度 = 组件高度 − 卡片 padding 与头部占位；向下取整，宁可少显示不可裁切。
        // 公式与边界由 WidgetListCapacity 持有并单测覆盖（v3.66.4 的「算多导致裁切」事故即在此防回归）。
        WidgetKind.LIST_VERTICAL -> rowsFor(rowHeights.list)
        WidgetKind.WEEK_COURSES -> rowsFor(rowHeights.week)
        WidgetKind.AGENDA_LIST -> rowsFor(rowHeights.agenda)
    }
}
