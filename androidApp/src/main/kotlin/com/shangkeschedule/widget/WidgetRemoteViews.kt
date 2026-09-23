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
