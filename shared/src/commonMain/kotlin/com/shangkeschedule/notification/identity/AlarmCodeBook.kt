package com.shangkeschedule.notification.identity

/**
 * 一次课程 occurrence 的稳定身份 → 闹钟请求码映射表。
 *
 * 请求码只需「本轮无碰撞 + 可全量注销」，不要求跨轮稳定；但为了让「同一门课在课表
 * 微调后仍命中同一槽位」（减少无谓的闹钟取消/重建），这里按 occurrence 首次出现顺序
 * 顺序分配，并在每轮 [reset] 后重新编号。
 *
 * ## 与 [NotificationIds] 的分工
 * 两者都是「课程 occurrence 的标识」，但作用不同，刻意分离：
 *  - [NotificationIds]：**通知身份**——由 occurrence 内容派生，跨轮稳定，
 *    保证同一次课幂等更新、不同次课并存不顶替；
 *  - 本类：**调度身份**——每轮全量重置后顺序分配，只求本轮无碰撞、可精确清理。
 *
 * 旧实现把两者合成一个槽位号，导致课表一变同一槽位就换了课程、通知互相顶替
 * （详见 [NotificationIds] 的 KDoc）。
 */
class AlarmCodeBook(private val base: Int = 60_000, private val capacity: Int = 200) {

    private val assigned = LinkedHashMap<String, Int>()

    /** 为 occurrence 分配（或复用本轮已有的）请求码；超出容量返回 null。 */
    fun codeFor(key: String): Int? {
        assigned[key]?.let { return it }
        if (assigned.size >= capacity) return null
        val code = base + assigned.size
        assigned[key] = code
        return code
    }

    /** 本轮已分配的数量。 */
    val size: Int get() = assigned.size

    /** 已分配的请求码集合（供全量注销使用）。 */
    fun codes(): List<Int> = assigned.values.toList()

    /** 开始新一轮排程：清空分配表。 */
    fun reset() = assigned.clear()

    /** 上限，供调用方判断是否需要分页/截断。 */
    val maxCodes: Int get() = capacity
}
