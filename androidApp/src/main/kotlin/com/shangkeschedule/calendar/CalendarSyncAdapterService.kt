package com.shangkeschedule.calendar

import android.accounts.Account
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder

/** 声明本应用账户支持系统日历；实际写回继续由 CalendarAccountManager 手动执行。 */
class CalendarSyncAdapterService : Service() {
    private val adapter by lazy { LocalCalendarSyncAdapter(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == "android.content.SyncAdapter") adapter.syncAdapterBinder else null
}

private class LocalCalendarSyncAdapter(context: Context) : AbstractThreadedSyncAdapter(context, false) {
    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {
        // 本地占位适配器不发网络请求，也不注册周期任务。
    }
}
