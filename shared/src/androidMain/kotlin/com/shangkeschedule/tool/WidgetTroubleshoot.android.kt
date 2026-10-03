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

    /**
     * 打开厂商「自启动 / 后台运行」管理页（v4.67.36 / XL-013）。
     *
     * 三段式：**私有候选逐个试探 → 系统级电池优化页 → 应用详情页 → 系统设置根页**。
     *
     * 为什么不能只给一个 Intent：这些组件名是各家的**非公开约定**，同一品牌在不同
     * 版本 / 渠道 ROM 上也会变（如 OPPO 从 `com.oppo.safe` 迁到了 `com.coloros.safecenter`）。
     * 写死一个等于赌 —— 赌输的表现是「点了没反应」，用户只会认定这个引导是假的。
     *
     * 最后一级回退到应用详情页（`openSystemSettings`）是有意义的：AOSP 保证它一定存在，
     * 用户在那里至少能找到「电池」与「应用信息」。
     */
    actual fun openOemStartupSettings(): Boolean {
        val candidates = oemStartupCandidates(Build.MANUFACTURER.orEmpty().lowercase())
        for (intent in candidates) {
            if (tryStart(intent)) return true
        }
        // 私有入口全探不到：先退到「电池优化策略」列表页（不需要任何权限，AOSP 保证存在），
        // 再退应用详情页。让用户有路可走，而不是引导到一半断掉。
        if (tryStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            return true
        }
        return openSystemSettings()
    }

    /** 该厂商的私有入口候选，按「命中率从高到低」排列。 */
    private fun oemStartupCandidates(brand: String): List<Intent> = when {
        brand.containsAny("xiaomi", "redmi", "poco") -> listOf(
            component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            component("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
        )

        brand.containsAny("huawei") -> listOf(
            component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )

        brand.containsAny("honor") -> listOf(
            component("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        )

        brand.containsAny("vivo", "iqoo") -> listOf(
            component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        )

        brand.containsAny("oppo") -> listOf(
            component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        )

        brand.containsAny("oneplus") -> listOf(
            component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        )

        brand.containsAny("realme") -> listOf(
            component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            component("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
        )

        brand.containsAny("meizu") -> listOf(
            component("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC"),
        )

        // 三星没有独立的自启动页，用「应用电池用量」页代替（语义最接近）。
        brand.containsAny("samsung") -> listOf(
            component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )

        else -> emptyList()
    }

    /**
     * 构造「打开指定包下指定 Activity」的显式 [Intent]。
     *
     * 这些是**跨应用显式意图**，是否可达完全由目标系统应用自己的 `exported` 声明决定，
     * 本应用既不需要权限、也无法保证它一直可达 —— 故上面才要按优先级多个候选。
     */
    private fun component(pkg: String, cls: String): Intent =
        Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 试探拉起一页；探不到（组件不存在 / 未导出 / 被 ROM 拦截）只记 W 并继续下一个候选。 */
    private fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (t: Throwable) {
        AppLog.w(TAG, "该入口不可用，换下一个：${intent.component ?: intent.action}", t)
        false
    }

    private fun String.containsAny(vararg needles: String) = needles.any { contains(it) }

    actual fun manufacturer(): String? = Build.MANUFACTURER
}
