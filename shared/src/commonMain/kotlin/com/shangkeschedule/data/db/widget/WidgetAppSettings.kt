package com.shangkeschedule.data.db.widget

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

@Entity(tableName = "widget_semester_start_date")
data class WidgetAppSettings(
    @PrimaryKey
    val id: Int = 1,
    val semesterStartDate: String? = null,  //第一周的时间
    val semesterTotalWeeks: Int = 20, // 最大周数
    val firstDayOfWeek: Int = DayOfWeek.MONDAY.isoDayNumber, // 一周的起始日，1=周一，7=周日

    /**
     * 快照版本戳（v4.64.23 起）：每次 `WidgetRepository.replaceSnapshot` 真正写入时 +1。
     *
     * 存在意义：`replaceSnapshotIfChanged` 用「内容比对」决定是否跳过写库，而
     * 「无有效配置」路径写下的正是「空课程 + 默认 settings」。若 widget 库被清空
     * （destructive migration / 系统回收 / 手动清理），库中读回的也是这同一组值 ⇒
     * 判定「无需更新」⇒ 组件永久空白，且此后每轮同步都命中同一分支，永不自愈。
     *
     * 版本戳让「内容相同但库刚被清空」必被识别为需要更新：库里被清空后版本戳归 0，
     * 而入参携带的是上次的递增值 ⇒ 立即重建。
     *
     * ⚠️ 本字段使 widget 库 schema 变化 → 需同步提升 `WidgetDatabase.version`
     * （该库是纯派生缓存，`fallbackToDestructiveMigration(true)` 下的清库正是
     *   本机制要自愈的场景，故无需写迁移）。
     */
    val snapshotVersion: Long = 0
)