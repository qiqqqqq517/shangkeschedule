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
            val specs = providers.map { info ->
                WidgetSpec(
                    // 类名做键：只在本进程内往返，不需要人可读，也就不会随文案改版失效
                    key = info.provider.className,
                    // loadLabel 取的是系统选择器里那个标题（见 XML 的 android:label）。
                    // 拿不到就退回类名尾部 —— 宁可难看也不能让界面上出现空白的一行。
                    label = runCatching { info.loadLabel(context.packageManager).toString() }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: info.provider.className.substringAfterLast('.'),
                    placedCount = appWidgetManager.getAppWidgetIds(info.provider).size,
                )
            }
            WidgetPlacement(
                providerCount = providers.size,
                placedCount = specs.sumOf { it.placedCount },
                specs = specs,
            )
        } catch (t: Throwable) {
            AppLog.w(TAG, "读取小组件放置情况失败：${t.message}", t)
            WidgetPlacement(providerCount = 0, placedCount = 0)
        }
    }

    /**
     * 请求固定到桌面（v4.66.6 / XL-015）。
     *
     * 键到 provider 的映射每次现查，不缓存：`installedProviders` 会随安装/卸载变，
     * 缓存下来的列表迟早指向一个已经不存在的类。
     */
    actual fun requestPin(key: String): WidgetPinOutcome {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return WidgetPinOutcome.UNSUPPORTED
        return try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val target = ownProviders(appWidgetManager).firstOrNull { it.provider.className == key }
                ?: return WidgetPinOutcome.REJECTED
            // 不传 callback：固定成功与否由系统弹窗自己交代，这里只报「请求有没有被受理」。
            // 用 requestPinAppWidget 而不是 ACTION_APPWIDGET_BIND —— 后者要用户输密码，
            // 那是对「绑定到启动器」的要求，与「放一个组件到桌面」不是一回事。
            if (appWidgetManager.requestPinAppWidget(target.provider, null, null)) {
                WidgetPinOutcome.REQUESTED
            } else {
                AppLog.w(TAG, "系统拒绝了固定请求：$key")
                WidgetPinOutcome.REJECTED
            }
        } catch (t: Throwable) {
            AppLog.w(TAG, "请求固定小组件到桌面失败：${t.message}", t)
            WidgetPinOutcome.REJECTED
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
