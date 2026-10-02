package com.shangkeschedule.tool

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android 端实现（v4.66.0，K7 小组件排障）。
 *
 * 刻意**不引用 androidApp 里的小组件类**（`ScheduledWidgetProvider` / `WidgetUpdateHelper` 都在
 * androidApp，shared 不能反向依赖它），改成用 `AppWidgetManager.installedProviders` 按包名筛：
 * `provider.provider.packageName == context.packageName` 的就是上课自己注册的小组件，
 * 桌面端不区分类型，正好覆盖 8 类 provider。
 *
 * 「刷新」走的是 `AppWidgetManager.ACTION_APPWIDGET_UPDATE` 显式广播 —— 与系统定时刷新、
 * 桌面重挂组件是同一条链路（最终都会落到 provider 的 `onUpdate`），因此不会绕过既有的渲染节流，
 * 也不会伪造「已刷新」的结论：一个组件都没放置时直接返回 false，由界面提示用户。
 */
actual object WidgetTroubleshootBridge : KoinComponent {

    private const val TAG = "WidgetTroubleshootBridge"

    private val context: Context by inject()

    private fun ownProviders(appWidgetManager: AppWidgetManager) =
        appWidgetManager.installedProviders.filter { it.provider.packageName == context.packageName }

    actual fun placement(): WidgetPlacement {
        return try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val providers = ownProviders(appWidgetManager)
            WidgetPlacement(
                providerCount = providers.size,
                placedCount = providers.sumOf { appWidgetManager.getAppWidgetIds(it.provider).size },
            )
        } catch (t: Throwable) {
            AppLog.w(TAG, "读取小组件放置情况失败：${t.message}", t)
            WidgetPlacement(providerCount = 0, placedCount = 0)
        }
    }

    actual fun requestRefresh(): Boolean {
        return try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            var sent = false
            ownProviders(appWidgetManager).forEach { provider ->
                val ids = appWidgetManager.getAppWidgetIds(provider.provider)
                if (ids.isEmpty()) return@forEach
                context.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                        component = ComponentName(provider.provider.packageName, provider.provider.className)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
                sent = true
            }
            sent
        } catch (t: Throwable) {
            AppLog.w(TAG, "请求小组件重绘失败：${t.message}", t)
            false
        }
    }

    actual fun openSystemSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            // Android 12+ 部分 ROM 会拦截「跳到应用详情」，退化成打开系统设置根页。
            AppLog.w(TAG, "打开应用详情页失败，尝试系统设置根页：${t.message}", t)
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (fallback: Throwable) {
                AppLog.w(TAG, "打开系统设置也失败：${fallback.message}", fallback)
                false
            }
        }
    }

    actual fun manufacturer(): String? = Build.MANUFACTURER
}
