package com.shangkeschedule.widget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 桌面组件点击路由的跨平台交接点（v4.67.39）。
 *
 * 场景：用户在桌面点「考试倒计时」组件 → `MainActivity` 从 Intent extras 取到
 * [WidgetRoute.AGENDA] → 调用 [offer] → 导航层观察到非空值就把「日程」页推到前台。
 *
 * ## 为什么不直接用导航参数
 *
 * 与 [com.shangkeschedule.tool.ExternalTextImport] 同理：组件点击可能在 App **已被杀**时发生，
 * 此时导航栈尚不存在，路由参数无处可挂。用一次性交接位则两侧解耦：
 * 平台层只负责「把意图投递进来」，导航层只负责「谁在什么时候消费」。
 *
 * ## 为什么必须一次性消费
 *
 * 不消费会导致：用户点组件 → 跳到日程页 → 手动切回今日页 → 再次点组件跳不动（值还在）。
 * 故 [clear] 与 [offer] 成对，且导航层在完成跳转后立即清空。
 */
object WidgetRouteHandoff {

    private val _pending = MutableStateFlow<String?>(null)

    /** 待处理的跳转路由；null = 无。导航层消费后置回 null。 */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /**
     * 投递一个路由请求。
     *
     * @param raw 来自 Intent extras 的原始值；未识别时由 [WidgetRoute.sanitize] 收敛到回退页，
     *            **不会**产生「点了没反应」的空投递。
     */
    fun offer(raw: String?) {
        val route = WidgetRoute.sanitize(raw)
        // 与空值不同：即使 raw 非法也要投递 FALLBACK —— 用户点了组件就该有反馈。
        _pending.value = route
    }

    /** 导航层已消费，清空交接位。 */
    fun clear() {
        _pending.value = null
    }
}