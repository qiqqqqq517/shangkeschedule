package com.shangkeschedule.notification

import com.shangkeschedule.data.db.widget.WidgetCourse

/**
 * 通知身份：由「课程 occurrence」派生的稳定通知 ID。
 *
 * 背景（旧实现病根）：`CourseAlarmReceiver` 直接拿闹钟槽位（50010–50110）当通知 ID，
 * 而槽位是**按未来课程列表序号**分配的。课表一变，同一个槽位就换了另一门课/另一天，
 * 于是：
 *  - 新提醒会原地覆盖旧提醒（用户看不到两条并存）；
 *  - 「关闭」按钮的 PendingIntent requestCode 也用同一个槽位号，可能关掉别人的通知。
 *
 * 修复思路：通知身份与调度身份彻底分离。
 *  - **调度身份**（闹钟请求码）：顺序分配、每轮全量注销，只求无碰撞、可精确清理；
 *  - **通知身份**（本文件）：从 occurrence 内容派生，跨重排稳定——同一次课幂等更新，
 *    不同次课并存不顶替，「关闭」恒指向自己。
 *
 * occurrence 定义为 (courseId, date, startTime) 三元组：
 * `widget_courses` 的行粒度就是「课程 × 日期」（见 WidgetCourse：id 为主键且含 date/startTime），
 * 同一门课在不同日期、或同一天不同节次都是不同 occurrence，理应各自有独立通知。
 */
object NotificationIds {

    /**
     * 通知 ID 命名空间基址。
     *
     * 与既有占用区间隔离，避免互相覆盖：
     *  - 50000–50200：旧版闹钟槽位（含自动模式 50001/50002、权限提示 50190/50191）
     *  - 20240904    ：灵动岛前台服务通知
     *
     * 本命名空间取 600000 起，容量 10 万，足以覆盖 7 天 × 每天数十节的极端课表。
     */
    private const val NAMESPACE_BASE = 600_000

    /** 命名空间容量（单字节量级之外的取模安全边界）。 */
    private const val NAMESPACE_SIZE = 100_000

    /** 旧实现的通知 ID 区间下界（含自动模式与权限提示）。 */
    val RESERVED_ID_RANGE = 50_000..50_200

    /** 灵动岛通知 ID（属另一命名空间，此处仅作断言用常量）。 */
    const val DYNAMIC_ISLAND_ID = 2_024_0904

    /**
     * occurrence 稳定键：`courseId|date|startTime`。
     *
     * 用不可见分隔符而非 `:` 之类的可见字符，避免 courseId 自身含分隔符时出现歧义键。
     */
    fun occurrenceKey(course: WidgetCourse): String =
        occurrenceKey(course.id, course.date, course.startTime)

    fun occurrenceKey(courseId: String, date: String, startTime: String): String =
        listOf(courseId, date, startTime).joinToString("\u0001")

    /**
     * 由 occurrence 键派生稳定通知 ID。
     *
     * 用 FNV-1a 32 位哈希（而非 `String.hashCode()`）：
     *  - hashCode 在短相似键上分布较差，且不同 JVM 之间的稳定性无契约保证；
     *  - FNV-1a 实现简单、无依赖、跨平台结果一致，便于单元测试锁定取值。
     *
     * 取模后落在 [NAMESPACE_BASE, NAMESPACE_BASE + NAMESPACE_SIZE)，
     * 与 [RESERVED_ID_RANGE] 和 [DYNAMIC_ISLAND_ID] 天然隔离。
     */
    fun forOccurrence(course: WidgetCourse): Int = forOccurrence(occurrenceKey(course))

    /** 直接由三元组派生，省去调用方先拼 key（与 [occurrenceKey] 三参重载对称）。 */
    fun forOccurrence(courseId: String, date: String, startTime: String): Int =
        forOccurrence(occurrenceKey(courseId, date, startTime))

    fun forOccurrence(key: String): Int {
        val hash = fnv1a32(key)
        return NAMESPACE_BASE + (hash % NAMESPACE_SIZE)
    }

    /** 判断一个通知 ID 是否属于本命名空间（供迁移清理与测试断言使用）。 */
    fun isOwned(id: Int): Boolean =
        id >= NAMESPACE_BASE && id < NAMESPACE_BASE + NAMESPACE_SIZE

    /**
     * FNV-1a 32 位哈希（偏移基数与质数取标准值）。
     * 结果保证非负（对 2^31 取模），避免 Kotlin `%` 对负数取模得到负值。
     */
    internal fun fnv1a32(input: String): Int {
        var hash = 0x811C9DC5.toInt()
        for (byte in input.encodeToByteArray()) {
            hash = hash xor (byte.toInt() and 0xFF)
            hash *= 0x01000193
        }
        return hash and 0x7FFFFFFF
    }
}

/**
 * 一次课程 occurrence 的稳定身份 → 闹钟请求码映射表。
 *
 * 请求码只需「本轮无碰撞 + 可全量注销」，不要求跨轮稳定；但为了让「同一门课在课表
 * 微调后仍命中同一槽位」（减少无谓的闹钟取消/重建），这里按 occurrence 首次出现顺序
 * 顺序分配，并在每轮 [reset] 后重新编号。
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
