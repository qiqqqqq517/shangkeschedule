package com.shangkeschedule.widget.compact

import android.content.Context
import com.shangkeschedule.widget.WorkManagerHelper
import com.shangkeschedule.widget.ScheduledWidgetProvider

/**
 * 今日课程（紧凑版）原生小组件接收器
 */
class CompactNativeProvider : ScheduledWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // 启动定时任务（15分钟 UI 刷新 + 24小时数据同步）
        WorkManagerHelper.schedulePeriodicWork(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // 只有在确定不再需要后台更新时才取消
        WorkManagerHelper.onWidgetDisabled(context)
    }
}
