package com.shangkeschedule.data.db.main

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * 日程分类。
 *
 * 数据库中以 key 字符串存储，UI 层负责映射名称与颜色；未知 key 回落为 [OTHER]。
 */
enum class ScheduleCategory(val key: String) {
    TODO("todo"),
    ACTIVITY("activity"),
    EXAM("exam"),
    HOMEWORK("homework"),
    OTHER("other");

    companion object {
        fun fromKey(key: String?): ScheduleCategory =
            entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/**
 * Room 实体类，代表“日程事件”数据表。
 *
 * 日程为全局数据（不随课表切换），按日期（yyyy-MM-dd）归属，可附带开始/结束时间（HH:mm）；
 * 全天日程不携带时间。供「日程」页月历与日程列表展示。
 *
 * @param id 唯一标识符（UUID 字符串）。
 * @param date 归属日期，格式 "yyyy-MM-dd"。
 * @param title 日程标题，限 300 字以内。
 * @param category 分类 key，取值见 [ScheduleCategory]。
 * @param isAllDay 是否全天日程。
 * @param startTime 开始时间，格式 "HH:mm"；全天日程为 null。
 * @param endTime 结束时间，格式 "HH:mm"；全天日程为 null。
 * @param location 地点，限 200 字以内。
 * @param note 备注，限 500 字以内。
 * @param createdAt 创建时间戳（毫秒）。
 * @param updatedAt 最近更新时间戳（毫秒）。
 */
@Entity(
    tableName = "schedule_events",
    indices = [Index(value = ["date"])]
)
data class ScheduleEvent(
    @PrimaryKey
    val id: String,
    val date: String,
    val title: String,
    val category: String,
    val isAllDay: Boolean = false,
    val startTime: String? = null,
    val endTime: String? = null,
    val location: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
