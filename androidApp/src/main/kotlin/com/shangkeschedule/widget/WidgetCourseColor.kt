package com.shangkeschedule.widget

import android.content.Context
import android.widget.RemoteViews
import com.shangkeschedule.R
import com.shangkeschedule.data.model.schedule_style.DualColorProto

/**
 * 课程色条 / Tiny 气泡的安全取色与落地。
 *
 * v4.63.2 起小组件课程色与主题解耦（见 `WidgetCoursePalette.kt`）：
 * `WidgetUpdateHelper` 在构造快照时已用 12 档固定色板覆盖主题色板，
 * 因此这里收到的 `maps` 恒为固定色板。取色用 floorMod 回绕 —— 主题侧
 * 色板长度不一（书卷 20 色、其余 12 色），任何主题分配下来的序号都
 * 确定性落到某一档；只有色板缺失（null / 空）时才回落兜底色。
 */

/** 取色；色池为空时返回 `null`（调用方回落兜底色），否则序号回绕。 */
internal fun resolveCourseColor(maps: List<DualColorProto>?, colorInt: Int): DualColorProto? =
    widgetCourseColorAt(maps, colorInt)

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
