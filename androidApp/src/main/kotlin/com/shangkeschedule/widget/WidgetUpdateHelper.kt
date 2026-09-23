package com.shangkeschedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.datastore.core.DataStore
import com.shangkeschedule.R
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.data.model.schedule_style.ScheduleGridStyleProto
import com.shangkeschedule.data.model.toProto
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.widget.compact.CompactNativeProvider
import com.shangkeschedule.widget.compact.CompactNativeRenderer
import com.shangkeschedule.widget.double_days.DoubleDaysNativeProvider
import com.shangkeschedule.widget.double_days.DoubleDaysNativeRenderer
import com.shangkeschedule.widget.list_vertical.ListVerticalNativeProvider
import com.shangkeschedule.widget.list_vertical.ListVerticalNativeRenderer
import com.shangkeschedule.widget.tiny.TinyNativeProvider
import com.shangkeschedule.widget.tiny.TinyNativeRenderer
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
}

/** 组件规格，决定「可容纳课程条数」的计算口径。 */
private enum class WidgetKind { TINY, COMPACT, DOUBLE_DAYS, LIST_VERTICAL }

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
     * 系统会为 4 个 receiver **同时**各发一次广播，而每次回调都会遍历全部 4 种组件——即同一份
     * 数据在同一时刻被渲染 16 次。这类提醒的语义只是「组件可能需要重绘」，若已有渲染正在进行，
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
 * 负责从 Repository 提取数据并分发给所有 4 种规格的原生 Renderer
 *
 * 并发语义（v3.66.3）：同一时刻只允许一次渲染，其余请求按 [WidgetRefreshReason] 分流——
 * `SYSTEM_NUDGE` 合并丢弃（消除 4 个 receiver 齐发造成的重复渲染），`REQUIRED` 登记补跑
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

        // 读取超时时直接跳过本次渲染（保留旧快照）——清成空列表会让组件整体空白到下个 15min tick
        val dbCourses = withTimeoutOrNull(3.seconds) {
            repository.getWidgetCoursesByDateRange(today.toString(), tomorrow.toString()).first()
        } ?: run {
            Log.w("WidgetSync", "widget 数据读取超时，跳过本次渲染以保留旧快照")
            return
        }

        // 周次读取超时必须与课程数据同策略：跳过本次渲染、保留旧快照。
        // 旧实现为 `?: 0`，而四个 Renderer 一律以 `current_week <= 0` 判定假期——Room 读取
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
        }

        // 3. 构造数据快照 (Protobuf)
        val courseProtoList = dbCourses.map { course ->
            WidgetCourseProto(
                id = course.id,
                name = course.name,
                teacher = course.teacher,
                position = course.position,
                start_time = course.startTime,
                end_time = course.endTime,
                color_int = course.colorInt,
                is_skipped = course.isSkipped,
                date = course.date
            )
        }

        val snapshot = WidgetSnapshot(
            current_week = currentWeek,
            style = finalStyleToSync,
            courses = courseProtoList
        )

        // 4. 定义所有原生尺寸的映射列表（同时携带各自规格，用于按类型推算可容纳条数）
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val nativeConfigs = listOf(
            Triple(TinyNativeProvider::class.java, WidgetKind.TINY, TinyNativeRenderer::render),
            Triple(CompactNativeProvider::class.java, WidgetKind.COMPACT, CompactNativeRenderer::render),
            Triple(DoubleDaysNativeProvider::class.java, WidgetKind.DOUBLE_DAYS, DoubleDaysNativeRenderer::render),
            Triple(ListVerticalNativeProvider::class.java, WidgetKind.LIST_VERTICAL, ListVerticalNativeRenderer::render)
        )

        // 4.5 实测 ListVertical 条目高度（每次刷新测一次，4 个规格共用）。
        //     仅 ListVertical 消费该值，其余规格的条数是固定档位。
        val density = context.resources.displayMetrics.density
        val listRowHeightPx = measureListRowHeightPx(context)

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
                        val remoteViews = renderFunc(
                            context,
                            snapshot,
                            resolveMaxCourseCount(kind, appWidgetManager, widgetId, listRowHeightPx, density)
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
 * 实测 ListVertical 条目（`widget_item_course_list_node`）在当前配置下的高度，单位 px。
 *
 * 背景（v3.66.5 修复）：v3.66.4 用常量 `LIST_ROW_DP = 36` 估算条数，而该条目实际占位约 49dp
 * （课程名 13sp + 地点 10sp + 教师 10sp + 2dp/1dp 间隔 + paddingVertical 3dp×2）⇒ 条数偏高约 36%，
 * 4×N 被拉高后列表溢出、末条被静默裁切（LinearLayout 不滚动，超出部分直接不可见）。
 *
 * 固定常量无法适配 fontScale / 字体 / 语言差异，故改为**实测**：在 App 进程 inflate 条目布局并测量。
 * 渲染发生在 App 侧，不违反 RemoteViews「不能自绘」的限制。测量失败时回落 [LIST_ROW_FALLBACK_DP]。
 */
private fun measureListRowHeightPx(context: Context): Int = runCatching {
    val density = context.resources.displayMetrics.density
    val view = LayoutInflater.from(context)
        .inflate(R.layout.widget_item_course_list_node, null, false)
    val widthPx = (LIST_MEASURE_WIDTH_DP * density).toInt().coerceAtLeast(1)
    view.measure(
        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val measured = view.measuredHeight
    if (measured > 0) measured else (LIST_ROW_FALLBACK_DP * density).toInt()
}.getOrElse { (LIST_ROW_FALLBACK_DP * context.resources.displayMetrics.density).toInt() }

/**
 * 按**组件类型**分别计算可容纳的课程条数。
 *
 * 历史：
 * - v3.66.3 之前：四种规格共用一套 `minHeight` 阈值并硬顶 3 条，废掉了 ListVertical 的「4×N」。
 * - v3.66.4：改为按类型分别计算，但 ListVertical 用了偏小的行高常量（36dp），条数偏高 ⇒ 溢出。
 * - v3.66.5（本次）：行高改为**实测**；高度基准改用 `MAX_HEIGHT`；并扣除卡片 padding 与头部占位。
 *
 * `OPTION_APPWIDGET_MIN_HEIGHT` 是可缩放的**下界**，代表不了当前可用高度，故改用
 * `OPTION_APPWIDGET_MAX_HEIGHT`（当前高度上界）；缺失时回落 MIN，再回落 110dp。
 */
private fun resolveMaxCourseCount(
    kind: WidgetKind,
    appWidgetManager: AppWidgetManager,
    widgetId: Int,
    listRowHeightPx: Int,
    density: Float
): Int {
    val options = appWidgetManager.getAppWidgetOptions(widgetId)
    val heightDp = options.getInt(
        AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
    )

    return when (kind) {
        // 单节课展示，不消费条数上限
        WidgetKind.TINY -> 1
        WidgetKind.COMPACT, WidgetKind.DOUBLE_DAYS -> when {
            heightDp < 90 -> 1
            heightDp < 150 -> 2
            else -> 3
        }
        // 4×N：可用高度 = 组件高度 − 卡片 padding 与头部占位；向下取整，宁可少显示不可裁切。
        // 公式与边界由 WidgetListCapacity 持有并单测覆盖（v3.66.4 的「算多导致裁切」事故即在此防回归）。
        WidgetKind.LIST_VERTICAL -> WidgetListCapacity.rowsFor(
            heightDp = heightDp,
            chromeDp = WidgetListCapacity.CHROME_DP,
            rowHeightPx = listRowHeightPx,
            density = density,
            maxRows = WidgetListCapacity.MAX_ROWS
        )
    }
}
