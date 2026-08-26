package com.shangkeschedule.ui.schedule

/**
 * 周课表视图模式。
 *
 * WEEK：传统周视图网格（默认，保留左右滑动切周）
 * LIST：按天分组的列表视图（参考 sleepy）
 * GRID：预留的绝对时间网格视图，暂未实现
 */
enum class ScheduleViewMode(val value: String) {
    WEEK("WEEK"),
    LIST("LIST"),
    GRID("GRID");

    companion object {
        /**
         * 从持久化字符串恢复；未知值回退到 WEEK。
         */
        fun fromString(value: String?): ScheduleViewMode {
            return entries.find { it.value == value } ?: WEEK
        }
    }
}
