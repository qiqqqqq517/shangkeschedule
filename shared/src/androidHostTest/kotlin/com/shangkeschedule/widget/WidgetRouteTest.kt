package com.shangkeschedule.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WidgetRoute]：桌面组件点击路由。
 *
 * 锁三件事：
 * 1. **全覆盖**：`WidgetRoute.KNOWN_PROVIDERS` 必须与 AndroidManifest 里注册的 8 个 provider
 *    一一对应 —— 漏一个就意味着那个组件点进去仍落到默认页，且**没有任何报错**；
 * 2. **简单名匹配**：provider 类名可能是 `com.x..widget.next_course.NextCourseNativeProvider`
 *    或带 `$` 的嵌套形式，取「最后一段」都要能命中；
 * 3. **非法输入零失败**：未知 provider / 非法路由值一律回退，绝不抛异常。
 */
class WidgetRouteTest {

    /** 与 `AndroidManifest.xml` 中注册的 8 个 provider 简单名严格一致。 */
    private val manifestProviders = listOf(
        "TinyNativeProvider",
        "CompactNativeProvider",
        "DoubleDaysNativeProvider",
        "ListVerticalNativeProvider",
        "NextCourseNativeProvider",
        "ExamCountdownNativeProvider",
        "WeekCoursesNativeProvider",
        "AgendaListNativeProvider",
    )

    @Test
    fun everyManifestProviderHasARoute() {
        val missing = manifestProviders.filterNot { it in WidgetRoute.KNOWN_PROVIDERS }
        assertTrue("以下 provider 未登记路由（会退化为默认页）：$missing", missing.isEmpty())
        // 反向：不得有多余登记（否则说明有人删了组件却忘了清理）
        val extra = WidgetRoute.KNOWN_PROVIDERS.filterNot { it in manifestProviders }
        assertTrue("以下路由已无对应组件（死配置）：$extra", extra.isEmpty())
    }

    @Test
    fun routeSemanticsMatchComponentIntent() {
        // 「下一节课 / 双日 / 小尺寸」= 今天怎么安排
        assertEquals(WidgetRoute.TODAY, WidgetRoute.routeForProvider("com.x.TinyNativeProvider"))
        assertEquals(WidgetRoute.TODAY, WidgetRoute.routeForProvider("com.x.NextCourseNativeProvider"))
        assertEquals(WidgetRoute.TODAY, WidgetRoute.routeForProvider("com.x.DoubleDaysNativeProvider"))
        // 「课表网格 / 纵向列表 / 周课程」= 看整周
        assertEquals(WidgetRoute.SCHEDULE, WidgetRoute.routeForProvider("com.x.CompactNativeProvider"))
        assertEquals(WidgetRoute.SCHEDULE, WidgetRoute.routeForProvider("com.x.ListVerticalNativeProvider"))
        assertEquals(WidgetRoute.SCHEDULE, WidgetRoute.routeForProvider("com.x.WeekCoursesNativeProvider"))
        // 「考试倒计时 / 日程清单」= 看日程
        assertEquals(WidgetRoute.AGENDA, WidgetRoute.routeForProvider("com.x.ExamCountdownNativeProvider"))
        assertEquals(WidgetRoute.AGENDA, WidgetRoute.routeForProvider("com.x.AgendaListNativeProvider"))
    }

    @Test
    fun nestedClassNameAlsoResolves() {
        // Compose 生成的嵌套类会带 `$`：取最后一段仍应命中
        assertEquals(
            WidgetRoute.AGENDA,
            WidgetRoute.routeForProvider("com.x.glance.Outer\$ExamCountdownNativeProvider")
        )
    }

    @Test
    fun bareSimpleNameResolves() {
        assertEquals(WidgetRoute.SCHEDULE, WidgetRoute.routeForProvider("WeekCoursesNativeProvider"))
    }

    @Test
    fun unknownProviderFallsBackInsteadOfThrowing() {
        assertEquals(WidgetRoute.FALLBACK, WidgetRoute.routeForProvider("com.x.FutureProvider"))
        assertEquals(WidgetRoute.FALLBACK, WidgetRoute.routeForProvider(""))
    }

    @Test
    fun sanitizeAcceptsOnlyKnownRoutes() {
        assertEquals(WidgetRoute.TODAY, WidgetRoute.sanitize(WidgetRoute.TODAY))
        assertEquals(WidgetRoute.SCHEDULE, WidgetRoute.sanitize(WidgetRoute.SCHEDULE))
        assertEquals(WidgetRoute.AGENDA, WidgetRoute.sanitize(WidgetRoute.AGENDA))
        // 非法 / 空 / null 一律回退
        assertEquals(WidgetRoute.FALLBACK, WidgetRoute.sanitize("hacker"))
        assertEquals(WidgetRoute.FALLBACK, WidgetRoute.sanitize(""))
        assertEquals(WidgetRoute.FALLBACK, WidgetRoute.sanitize(null))
    }

    /** 交接位：投递非法值也要有反馈（落到回退页），不能静默丢弃。 */
    @Test
    fun handoffAlwaysProducesARoute() {
        WidgetRouteHandoff.clear()
        WidgetRouteHandoff.offer("完全不存在的路由")
        assertEquals(WidgetRoute.FALLBACK, WidgetRouteHandoff.pending.value)
        WidgetRouteHandoff.offer(WidgetRoute.AGENDA)
        assertEquals(WidgetRoute.AGENDA, WidgetRouteHandoff.pending.value)
        WidgetRouteHandoff.clear()
        assertEquals(null, WidgetRouteHandoff.pending.value)
    }
}