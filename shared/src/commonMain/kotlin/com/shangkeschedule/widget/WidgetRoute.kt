package com.shangkeschedule.widget

/**
 * 小组件点击跳转路由（v4.67.39，纯逻辑 + 契约）。
 *
 * ## 要解决什么
 *
 * 8 个组件此前**全部**只做一件事：`PendingIntent.getActivity(MainActivity)` ——
 * 点任何一个组件都落到 App 的默认页。后果是用户看到「桌面写着『下一节课是电工电子技术』，
 * 点进去却要自己再找一次那门课在哪一页」，桌面组件省下的那一步点击被又还了回去。
 *
 * ## 做法
 *
 * 桌面组件的点击目标**由组件类型决定**（而不是用户配置）：
 * 「下一节课」自然该去今日页看今天安排，「考试倒计时」该去日程页看考试，
 * 「周课程」该去课程表页看整周 —— 这与用户装这个组件的**意图**天然一致，
 * 因此不需要新增配置项，也就不需要 `android:configure` 那一整套配置 Activity。
 *
 * 借鉴竞品「星链课表」的 `/navigation` MethodChannel：它同样把「组件点击 → 打开到某个页面」
 * 做成一条显式路由（原生层 `MainActivity` 里有 `"收到路由请求"` / `"保存路由请求"` 日志），
 * 而不是一个无参数的 `openApp()`。
 *
 * ## 约束
 *
 * ① 本文件属 `commonMain`，**不引用任何 Android API**，路由值只能是纯字符串，
 *    以便 JVM 单测直接覆盖「8 个 provider ↔ 8 个路由」的全覆盖性。
 * ② 路由值必须与 `AppNavigation` 能识别的目标一致，故统一用 [Destination] 的稳定 key，
 *    而不是可读文案（文案会随语言变化，不能当协议）。
 */
object WidgetRoute {

    /** 路由 extra key。显式命名，避免与系统 extras（EXTRA_PROCESS_TEXT 等）撞名。 */
    const val EXTRA_ROUTE = "com.shangkeschedule.extra.WIDGET_ROUTE"

    /** 路由值：今日页（默认 / 下一节课 / 双日 / 小尺寸今日）。 */
    const val TODAY = "today"

    /** 路由值：课程表页（周课程 / 课表网格类）。 */
    const val SCHEDULE = "schedule"

    /** 路由值：日程页（考试倒计时 / 日程清单）。 */
    const val AGENDA = "agenda"

    /** 未识别的路由值回退到这里：宁可落在今日页，也不要让用户落到一个无关页面。 */
    const val FALLBACK = TODAY

    /**
     * provider 类全名 → 路由。
     *
     * 用**类名尾部**做匹配而不是完整包名：provider 的包路径在不同 source set 下可能不同，
     * 尾部（`NextCourseNativeProvider`）才是稳定标识。取「最后一个 `$` 之后」的部分再比较，
     * 对 `com.xangkeschedule.widget.next_course.NextCourseNativeProvider` 这类嵌套名同样成立。
     */
    private val ROUTES_BY_PROVIDER_SIMPLE_NAME: Map<String, String> = mapOf(
        // 今日类：点进去就是「今天怎么安排」
        "TinyNativeProvider" to TODAY,
        "NextCourseNativeProvider" to TODAY,
        "DoubleDaysNativeProvider" to TODAY,
        // 课程表类：点进去看整周网格
        "CompactNativeProvider" to SCHEDULE,
        "ListVerticalNativeProvider" to SCHEDULE,
        "WeekCoursesNativeProvider" to SCHEDULE,
        // 日程类：点进去看日程与考试
        "ExamCountdownNativeProvider" to AGENDA,
        "AgendaListNativeProvider" to AGENDA,
    )

    /** 已登记的 provider 简单名（供测试断言「一个都不漏」）。 */
    val KNOWN_PROVIDERS: Set<String> = ROUTES_BY_PROVIDER_SIMPLE_NAME.keys

    /** provider 类全名（含 `$` 嵌套）→ 简单名。 */
    private fun simpleNameOf(className: String): String =
        className.substringAfterLast('$').substringAfterLast('.')

    /**
     * 由 provider 类名取路由。
     *
     * 未知 provider（新增组件还没登记、或读到一个已卸载的类名）返回 [FALLBACK]，
     * **不抛异常** —— 桌面组件被点开时崩溃是不可接受的失败模式。
     */
    fun routeForProvider(className: String): String =
        ROUTES_BY_PROVIDER_SIMPLE_NAME[simpleNameOf(className)] ?: FALLBACK

    /**
     * 校验外部传入的路由值（PendingIntent extras 可能来自旧版本或被伪造）。
     *
     * 只认三个已知值，其余一律回 [FALLBACK]。理由同上：路由是**展示意图**，
     * 走错页面只是体验不对，不该有任何失败路径。
     */
    fun sanitize(raw: String?): String = when (raw) {
        TODAY, SCHEDULE, AGENDA -> raw
        else -> FALLBACK
    }
}