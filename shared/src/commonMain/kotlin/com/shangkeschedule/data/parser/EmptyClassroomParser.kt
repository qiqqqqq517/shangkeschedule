package com.shangkeschedule.data.parser

import kotlinx.serialization.Serializable

/**
 * 空教室查询结果条目。
 *
 * 字段名与注入脚本 [com.shangkeschedule.ui.schoolselection.web.JS_SCAN_EMPTY_CLASSROOMS]
 * 返回的 JSON 键一一对应（`{"room":"教三305","campus":"","building":"三教","capacity":60,"freeSlots":"1-2节"}`）。
 *
 * 教务系统的空教室结果表结构差异极大（列名、列顺序、合并单元格各不相同），因此除
 * [room] 外的字段都允许缺失：拿不到就留空，界面与复制文本会自动跳过空值，
 * 而不是把「未知」之类的占位词塞进结果里。
 */
@Serializable
data class EmptyClassroomRoom(
    /** 教室名（如「教三305」「A-201」）。 */
    val room: String = "",
    /** 校区（结果表里带「校区」列时才有）。 */
    val campus: String = "",
    /** 教学楼 / 楼栋。 */
    val building: String = "",
    /** 座位数（结果表里带「座位数 / 容量」列时才有）。 */
    val capacity: Int? = null,
    /** 空闲节次 / 空闲时段原文（如「1-2节」），学校怎么写就怎么保留。 */
    val freeSlots: String = ""
)

/**
 * 组装单条空教室结果行，形如「教三305 · 三教 · 60 人 · 1-2节」。
 *
 * [capacityPattern] 是本地化的座位数模板（如 `%1$d 人` / `%1$d seats`），
 * 传 null 时退化为纯数字。空值字段一律不参与拼接，不会留下多余的分隔符。
 */
fun formatEmptyClassroomLine(
    room: EmptyClassroomRoom,
    capacityPattern: String? = null
): String {
    val parts = mutableListOf<String>()

    val name = room.room.trim()
    if (name.isNotEmpty()) parts += name

    // 教室名里通常已经含楼栋（「教三305」），重复时不再拼一次
    room.building.trim().takeIf { it.isNotEmpty() && it != name }?.let { parts += it }
    room.campus.trim().takeIf { it.isNotEmpty() }?.let { parts += it }

    val capacity = room.capacity
    if (capacity != null && capacity > 0) parts += formatCapacity(capacity, capacityPattern)

    room.freeSlots.trim().takeIf { it.isNotEmpty() }?.let { parts += it }

    return parts.joinToString(" · ")
}

/**
 * 把识别结果整段拼成可复制的多行文本（每行一条，供剪贴板 / 分享使用），
 * 与界面里展示的行文案完全一致。
 */
fun formatEmptyClassroomText(
    rooms: List<EmptyClassroomRoom>,
    capacityPattern: String? = null
): String = rooms
    .map { formatEmptyClassroomLine(it, capacityPattern) }
    .filter { it.isNotBlank() }
    .joinToString("\n")

private fun formatCapacity(capacity: Int, pattern: String?): String {
    val raw = pattern?.trim().orEmpty()
    if (raw.isEmpty()) return capacity.toString()
    // 兼容位置参数（%1$d）与普通占位符（%d）两种写法
    return raw.replace("%1\$d", capacity.toString()).replace("%d", capacity.toString())
}
