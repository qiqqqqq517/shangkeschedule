package com.shangkeschedule.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.shangkeschedule.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 系统深浅色切换即时刷新小组件。
 *
 * 根因（Android 10 实机反馈：切换深色/白天模式后「只有字体变色，背景不变色」）：
 * 系统切换 uiMode（深浅色）时**不会回调任何 `AppWidgetProvider` 方法**
 * （`onUpdate` / `onAppWidgetOptionsChanged` 均不触发），而小组件的周期刷新
 * 完全交给 WorkManager（`updatePeriodMillis = 0` + 15 分钟 tick）。于是切换后
 * 桌面停留的是旧模式下推送的 RemoteViews：宿主（launcher）侧对新旧配置的
 * 重解析是部分性的 —— 文字色跟随新模式，`widget_bg_rounded` 卡片背景仍停留
 * 在旧模式（浅 `#FEF7FF` / 深 `#141218`），形成深浅错配。
 * 小组件自身的 `-night` 资源（`values-night/colors.xml`、`layout-night/` 行布局）
 * 只有在**切换后新推送**的 RemoteViews 上才会被宿主以新配置完整解析
 * （手动触发一次刷新即恢复正常，已验证该语义）。
 *
 * 实现：`ACTION_CONFIGURATION_CHANGED` 不能静态注册（系统文档明确写了
 * manifest 组件收不到），故在 `MyApplication.onCreate` 做运行时注册；
 * 收到后只比较夜间位，真正发生深浅切换才推一次全量渲染（REQUIRED），
 * 语言/字体/方向等其它配置变化不打扰。进程在切换时刻已死则本次收不到，
 * 由 15 分钟 tick / 下次打开 App 的同步刷新兜底。
 */
private const val WIDGET_NIGHT_PREFS = "widget_night_mode"
private const val KEY_LAST_NIGHT = "last_night_mode"

/** uiMode 夜间位提取（纯函数，可进 JVM 单测；常量编译期折叠，不依赖真机）。 */
internal fun isNightUiMode(uiMode: Int): Boolean =
    (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/**
 * 当前**系统**深浅（而非 App 进程配置）。
 *
 * 必须走 `Resources.getSystem()`：App 内允许「强制深色 / 强制浅色」
 * （`AppCompatDelegate` 会改写 App 进程资源的夜间位），而宿主（launcher）
 * 解析小组件 XML 颜色用的是**系统**配置（v4.63.2 起小组件与 App 主题解耦，
 * 跟系统不跟 App）。若用 App 进程的 uiMode 定背景，会在
 * 「App 强制浅色 + 系统深色」时配出浅底 + 浅字（宿主按系统出浅字），直接不可读。
 */
internal fun isSystemNight(): Boolean =
    isNightUiMode(Resources.getSystem().configuration.uiMode)

/** 卡片背景固定资源 ID（纯函数，可进 JVM 单测锁定：必须是单夜色感知 ID，禁止再按推送时刻快照二选一）。 */
internal fun widgetCardBackgroundRes(): Int = R.drawable.widget_bg_rounded

/**
 * 把卡片背景**显式**压进 RemoteViews —— 单 ID 夜色感知式。
 *
 * 语义（v4.64.1 起，替代推送时刻快照二选一）：一律记录
 * `R.drawable.widget_bg_rounded` 这一个夜色感知 ID（内引 `@color/widget_bg`，
 * `values/colors.xml` / `values-night/colors.xml` 两档）。宿主重放该动作时调的是
 * `view.setBackgroundResource(id)`，按视图**当前**配置实时解析，等价于嵌套课程行
 * 在重放时按当前配置重 inflate 选 `layout-night/` 的行为 —— 同一趟重放里背景与文字
 * 一起跟新系统深浅，**进程已死也能跟**（`ACTION_CONFIGURATION_CHANGED`
 * 不允许静态注册，死进程收不到广播推新 RemoteViews，快照式在此必冻住）。
 * 活进程切主题时运行时监听仍会推一次全量重渲染，结果一致。
 */
internal fun RemoteViews.setWidgetCardBackground(vararg viewIds: Int) {
    val bg = widgetCardBackgroundRes()
    viewIds.forEach { setInt(it, "setBackgroundResource", bg) }
}

/**
 * 深浅是否真的翻转：首次观察（`lastNight == null`）只记录基线不刷新，
 * 避免每次进程启动多推一次。
 */
internal fun nightChanged(lastNight: Boolean?, nowNight: Boolean): Boolean =
    lastNight != null && lastNight != nowNight

/**
 * 是否需要在 App 侧监听深浅切换（纯函数，可进 JVM 单测）。
 *
 * API 36（Android 16）起桌面宿主在深浅切换时会完整重建小组件视图并按新配置
 * 重解析（实测直接切换正常），App 侧补推只是重复渲染（读库 + 四套全量渲染），
 * 故只在旧版本监听；旧版本切后台兜底仍是 15 分钟 tick。
 */
internal fun shouldWatchNightMode(sdkInt: Int): Boolean =
    sdkInt < Build.VERSION_CODES.BAKLAVA

class WidgetNightModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_CONFIGURATION_CHANGED) return

        val appContext = context.applicationContext
        // 注意：比较系统夜间位（同渲染侧口径），App 内强制主题不影响判定。
        val nowNight = isSystemNight()
        val prefs = appContext.getSharedPreferences(WIDGET_NIGHT_PREFS, Context.MODE_PRIVATE)
        val lastNight = if (prefs.contains(KEY_LAST_NIGHT)) {
            prefs.getBoolean(KEY_LAST_NIGHT, nowNight)
        } else {
            null
        }
        if (!nightChanged(lastNight, nowNight)) {
            // 首次观察顺手把基线写下；非深浅变化直接忽略。
            if (lastNight == null) prefs.edit().putBoolean(KEY_LAST_NIGHT, nowNight).apply()
            return
        }
        prefs.edit().putBoolean(KEY_LAST_NIGHT, nowNight).apply()

        // 运行时注册的接收器：进程是自己的，无需 goAsync，直接后台协程推送。
        Log.d("WidgetNightMode", "系统深浅色切换，立即刷新全部小组件...")
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                updateAllWidgets(appContext, WidgetRefreshReason.REQUIRED)
            } catch (e: Exception) {
                Log.e("WidgetNightMode", "深浅切换后刷新小组件失败", e)
            }
        }
    }
}

/** Application 启动期调用一次（进程生命周期内有效，与进程同寿，无需反注册）。 */
fun registerWidgetNightModeWatcher(context: Context) {
    // 新系统宿主自理，不注册：连广播唤醒都省掉，不只是省渲染。
    if (!shouldWatchNightMode(Build.VERSION.SDK_INT)) return
    runCatching {
        ContextCompat.registerReceiver(
            context,
            WidgetNightModeReceiver(),
            IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }.onFailure { Log.e("WidgetNightMode", "注册深浅切换监听失败", it) }
}
