package com.shangkeschedule.widget

import com.shangkeschedule.data.model.schedule_style.DualColorProto

/**
 * 小组件固定课程色板（v4.63.2，产品决策：**主题不影响小组件显示**）。
 *
 * 此前小组件直接复用主题快照的 `course_color_maps`（半联动，见
 * `docs/widget-display-optimization.md` §1.3）：用户一切换主题，
 * 组件上的色条 / Tiny 气泡底就跟着变 —— 通透主题浅色档仅 12% alpha，
 * 在浅色卡上几乎看不见；各主题深色档的饱和色又与固定气泡数字撞亮度
 * （v4.63.1 修的是数字端）。现改为 12 档固定色，与主题彻底解耦：
 * 任何主题、任何用户看到的同一课程色序号都是同一色相，可读性与辨认性
 * 不再随主题浮动。
 *
 * 设计（实测值见 `WidgetCoursePaletteTest` 与本次工作日志）：
 * - 12 档同色相排序（红→橙），浅 / 深两档同色相不同明度；
 * - 浅色档取饱和色（色条在浅卡 `#FEF7FF` 上 ≥ 3.2:1）；
 * - 深色档取亮 tint（色条在深卡 `#141218` 上 ≥ 3.2:1）；
 * - 气泡数字由 [bubbleTextColorFor] 按亮度二选一，24 档全部 ≥ 4.5:1；
 * - 全部实色（alpha = FF）：半透明在色条 / 气泡上都会被底色稀释，
 *   是之前「看不见」的直接来源之一，此处禁用。
 *
 * 索引：[widgetCourseColorAt] 用 floorMod 回绕 —— 主题侧色板长度不一
 * （书卷 20 色、其余 12 色），任何主题分配下来的 `colorInt` 都确定性落到
 * 某一档，不再出现「越界变灰」（12 档人眼可辨的上限，不必追 20 色）。
 */
data class WidgetCourseColor(val light: Int, val dark: Int)

val WIDGET_COURSE_COLORS: List<WidgetCourseColor> = listOf(
    WidgetCourseColor(light = 0xFFD32F2F.toInt(), dark = 0xFFEF9A9A.toInt()), // 红
    WidgetCourseColor(light = 0xFFC2185B.toInt(), dark = 0xFFF48FB1.toInt()), // 粉
    WidgetCourseColor(light = 0xFF7B1FA2.toInt(), dark = 0xFFCE93D8.toInt()), // 紫
    WidgetCourseColor(light = 0xFF512DA8.toInt(), dark = 0xFFB39DDB.toInt()), // 蓝紫
    WidgetCourseColor(light = 0xFF303F9F.toInt(), dark = 0xFF9FA8DA.toInt()), // 靛蓝
    WidgetCourseColor(light = 0xFF1976D2.toInt(), dark = 0xFF90CAF9.toInt()), // 蓝
    WidgetCourseColor(light = 0xFF00838F.toInt(), dark = 0xFF80DEEA.toInt()), // 青
    WidgetCourseColor(light = 0xFF00796B.toInt(), dark = 0xFF80CBC4.toInt()), // 青绿
    WidgetCourseColor(light = 0xFF388E3C.toInt(), dark = 0xFFA5D6A7.toInt()), // 绿
    WidgetCourseColor(light = 0xFF558B2F.toInt(), dark = 0xFFC5E1A5.toInt()), // 黄绿
    WidgetCourseColor(light = 0xFF87791B.toInt(), dark = 0xFFFFF59D.toInt()), // 黄（橄榄金，亮黄在浅卡上不可辨）
    WidgetCourseColor(light = 0xFFE65100.toInt(), dark = 0xFFFFCC80.toInt()), // 橙
)

/** 确定性取色：空池返回 null（调用方回落兜底色），否则序号回绕。 */
fun widgetCourseColorAt(maps: List<DualColorProto>?, colorInt: Int): DualColorProto? {
    if (maps.isNullOrEmpty()) return null
    return maps[Math.floorMod(colorInt, maps.size)]
}

/** 固定色板转快照格式（`WidgetUpdateHelper` 用它覆盖主题色板）。 */
fun widgetCoursePaletteProto(): List<DualColorProto> = WIDGET_COURSE_COLORS.map {
    DualColorProto(
        light_color = it.light.toLong(),
        dark_color = it.dark.toLong()
    )
}
