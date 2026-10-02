package com.shangkeschedule.notification.identity

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
     *  - 599001/599002：「下一节课」常驻通知 / 「考试倒计时」通知（单例，见下）
     *  - 700100      ：早八闹钟兜底通知（MorningAlarmNotifier）
     *  - 20240904    ：灵动岛前台服务通知
     *
     * 本命名空间取 **100 万** 起，容量 100 万，足以覆盖 7 天 × 每天数十节的极端课表。
     * v4.66.0 由 600000 上移到 1000000：容量在 v4.64.21 扩到 100 万后，原区间
     * 600000–1599999 会把早八闹钟的 700100 圈进来（碰撞概率百万分之一，但后果是
     * 课程提醒顶掉早八兜底通知、任一方 dismiss 会连带把另一方一起关掉）。
     */
    private const val NAMESPACE_BASE = 1_000_000

    /**
     * 命名空间容量（取模基数）。
     *
     * v4.64.21 由 100_000 提到 1_000_000：取模碰撞率随槽位数的平方上升，
     * 10 万槽下「7 天窗口 70 节课」实测约 2.2% 会撞（30 个窗口累计约 48%），
     * 提到 100 万后降到约 0.22%。碰撞后果是两条课程共用一个通知 ID ——
     * 后投递的顶替先投递的，且 dismiss PendingIntent 会连带把另一条一起关掉。
     *
     * 换基数**不影响**已投递通知的 dismiss 对应关系：dismiss 的 requestCode /
     * extra / identifier 与通知 ID 在同一次 `build()` 内派生（CourseReminderNotifier），
     * 而登记簿的键是 occurrenceKey 原文、不是 ID 派生值（PostedNotificationRegistry）。
     * 升级前已投递的通知仍是旧 ID，靠自身 `setTimeoutAfter` 到上课时刻自清。
     *
     * 区间 1_000_000–1_999_999 仍与 [RESERVED_ID_RANGE]（50_000–50_200）、
     * 早八闹钟的 700_100 和 [DYNAMIC_ISLAND_ID]（2_024_0904）完全隔离。
     */
    private const val NAMESPACE_SIZE = 1_000_000

    /** 旧实现的通知 ID 区间下界（含自动模式与权限提示）。 */
    val RESERVED_ID_RANGE = 50_000..50_200

    /** 灵动岛通知 ID（属另一命名空间，此处仅作断言用常量）。 */
    const val DYNAMIC_ISLAND_ID = 2_024_0904

    /**
     * 「下一节课」常驻通知 ID（单例）。
     *
     * 取 599_xxx 段落：避开旧闹钟槽位 50_000–50_200，也避开课程 occurrence 命名空间
     * （1_000_000 起）。单例通知**不走**注册簿回收（注册簿是给「一课程一条」用的），
     * 关闭/无课时显式 `cancel()` 即可。
     */
    const val NEXT_CLASS_PERSISTENT_ID = 599_001

    /** 「考试倒计时提醒」通知 ID（同为单例，与上一节各占一条）。 */
    const val EXAM_COUNTDOWN_ID = 599_002

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
