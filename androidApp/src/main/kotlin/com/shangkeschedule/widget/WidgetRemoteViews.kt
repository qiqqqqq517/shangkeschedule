package com.shangkeschedule.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.shangkeschedule.MainActivity
import com.shangkeschedule.R

/**
 * 四个 Renderer 此前逐字重复了同一段「点击整块组件跳转 MainActivity」的 PendingIntent 构造，
 * 此处收敛为单一实现（v3.66.3，同功能冗余清理）。
 *
 * 注意：该 PendingIntent 的身份是 (requestCode = 0, filterEquals)，与 CourseAlarmReceiver
 * 的课程闹钟通知、DynamicIslandService 的灵动岛「打开应用」共用同一个身份（filterEquals
 * 不比较 Intent.flags）；FLAG_UPDATE_CURRENT 只替换 extras，因此三处的 Intent.flags
 * 必须完全一致，否则谁先创建谁生效、后创建者的 flags 会被静默丢弃。
 */
internal fun bindWidgetClickIntent(context: Context, rv: RemoteViews) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    rv.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
}
