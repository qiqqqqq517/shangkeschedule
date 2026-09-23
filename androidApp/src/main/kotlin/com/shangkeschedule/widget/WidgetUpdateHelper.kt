package com.shangkeschedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
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
                            resolveMaxCourseCount(kind, appWidgetManager, widgetId)
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

/** ListVertical 头部（周次 / 日期行 + 上边距）占用的高度。 */
private const val LIST_HEADER_DP = 24

/** ListVertical 单条课程行的高度（含内边距与分割线）。 */
private const val LIST_ROW_DP = 36

/** ListVertical 单次渲染的条数上限，避免异常尺寸下构造过大的 RemoteViews。 */
private const val LIST_MAX_ROWS = 12

/**
 * 按**组件类型**分别计算可容纳的课程条数。
 *
 * 修复（v3.66.3）：原实现是四种规格共用同一套 `minHeight` 阈值并硬顶 3 条。而
 * `shangkeschedule_list_vertical_widget.xml` 声明了 `minResizeHeight="70dp"`，设计意图是
 * 「4×N，能撑多少显示多少」——硬顶 3 条直接废掉了 N；Tiny 更是拿到该值后完全不使用。
 */
private fun resolveMaxCourseCount(
    kind: WidgetKind,
    appWidgetManager: AppWidgetManager,
    widgetId: Int
): Int {
    val minHeight = appWidgetManager.getAppWidgetOptions(widgetId)
        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

    return when (kind) {
        // 单节课展示，不消费条数上限
        WidgetKind.TINY -> 1
        WidgetKind.COMPACT, WidgetKind.DOUBLE_DAYS -> when {
            minHeight < 90 -> 1
            minHeight < 150 -> 2
            else -> 3
        }
        // 4×N 列表按实际高度动态推算
        WidgetKind.LIST_VERTICAL ->
            ((minHeight - LIST_HEADER_DP) / LIST_ROW_DP).coerceIn(1, LIST_MAX_ROWS)
    }
}
