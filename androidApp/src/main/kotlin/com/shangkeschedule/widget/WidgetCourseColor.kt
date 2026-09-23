package com.shangkeschedule.widget

import android.content.Context
import android.widget.RemoteViews
import com.shangkeschedule.R
import com.shangkeschedule.data.model.schedule_style.DualColorProto

/**
 * 课程色条 / Tiny 气泡的安全取色与落地。
 *
 * 修复（v3.66.3）：
 * ① **越界崩溃**——原四个 Renderer 的守卫均写作 `colorInt < maps.size`，**只判上界不判负值**。
 *    `colorInt` 来自 `WidgetCourse.colorInt`（DB 裸 Int，无约束），且导入课表时直接透传
 *    （见 `CourseConversionRepository` 的 `color = colorIndex`）。一旦为负，
 *    `maps[colorInt]` 抛 `IndexOutOfBoundsException`，异常被 `updateAllWidgets` 的 try/catch
 *    吞掉后组件**静默停更**。主 App 侧早已统一使用安全写法（`CourseBlock.kt` 的
 *    `in style.courseColorMaps.indices`、`TodayScheduleScreen.kt` 的 `getOrNull`），此处对齐。
 * ② **缺色无兜底**——`@color/widget_course_fallback` 在 `values/colors.xml` 与
 *    `values-night/colors.xml` 都有定义（注释写明「数据异常时的默认灰色」），但全仓库**零引用**。
 *    取色失败时不加 colorFilter，指示条保持 `widget_indicator_shape` 的 `#FFFFFF`（日间浅底
 *    `#FEF7FF` 上几乎不可见），Tiny 气泡则保持 `widget_shape_circle_base` 的硬编码 `#3498DB` 纯蓝。
 *
 * 注：浅色 / 深色分别落到两套 View ID 上（`layout-night/` 使用 `*_dark` 后缀那套），
 * 这是既有的日夜双 ID 设计——同一份 RemoteViews 只有一个 ID 会命中，另一个被静默忽略。
 */

/** 取色；色池为空或索引非法（负值 / 越界）时返回 `null`。 */
internal fun resolveCourseColor(maps: List<DualColorProto>?, colorInt: Int): DualColorProto? =
    maps?.getOrNull(colorInt)

/** 把课程色落到「浅色视图 + 深色视图」两个 ID 上；取色失败时回落到兜底色。 */
internal fun RemoteViews.applyCourseColor(
    context: Context,
    lightViewId: Int,
    darkViewId: Int,
    maps: List<DualColorProto>?,
    colorInt: Int
) {
    val pair = resolveCourseColor(maps, colorInt)
    val fallback = context.getColor(R.color.widget_course_fallback)
    setInt(lightViewId, "setColorFilter", pair?.light_color?.toInt() ?: fallback)
    setInt(darkViewId, "setColorFilter", pair?.dark_color?.toInt() ?: fallback)
}
