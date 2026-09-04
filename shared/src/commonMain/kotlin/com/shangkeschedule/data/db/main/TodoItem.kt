package com.shangkeschedule.data.db.main

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Room 实体类，代表“待办事项”数据表。
 *
 * 待办按日期（yyyy-MM-dd）归属，可附带可选时间（HH:mm）用于当日排序与展示。
 * 独立于课表/课程表，供「今日课表」页内嵌展示当日待办。
 *
 * @param id 唯一标识符（UUID 字符串）。
 * @param date 归属日期，格式 "yyyy-MM-dd"。
 * @param title 待办内容，限 300 字以内。
 * @param note 备注，限 500 字以内。
 * @param time 可选时间，格式 "HH:mm"，用于同日排序与展示。
 * @param done 是否已完成（未完成排前，已完成沉底）。
 * @param sortOrder 排序权重（越小越靠前）。
 * @param createdAt 创建时间戳（毫秒）。
 * @param updatedAt 最近更新时间戳（毫秒）。
 */
@Entity(
    tableName = "todo_items",
    indices = [Index(value = ["date"])]
)
data class TodoItem(
    @PrimaryKey
    val id: String,
    val date: String,
    val title: String,
    val note: String? = null,
    val time: String? = null,
    @ColumnInfo(defaultValue = "0")
    val done: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
