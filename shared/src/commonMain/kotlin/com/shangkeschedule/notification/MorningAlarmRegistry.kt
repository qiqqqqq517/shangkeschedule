package com.shangkeschedule.notification

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus

/**
 * 早八闹钟**登记簿**的编解码与回收（纯逻辑，无平台依赖）。
 *
 * ## 登记簿回答什么
 *
 * 「这个**课程日期**的闹钟，我们已经往系统时钟里写过一条什么标签的内容，
 * 以及它应该在**什么时候响**。」
 *
 * 之所以必须记下真实响铃时刻，是因为系统时钟 API 无法表达日期
 * （见 [MorningAlarmPlan.nextUpcoming]）：系统建出的是一次性闹钟，响过即被系统删除。
 * 只有能判断出「上一条已经响过了」，下一天的闹钟才会被补写进去；
 * 否则登记簿会一直认为「已写入」，用户从第二天起再也收不到早八闹钟。
 *
 * ## 编码格式
 *
 * - **v2（当前）**：`课程日期 \u0003 响铃时刻(ISO) \u0003 标签`
 * - **v1（修复前遗留）**：`课程日期 \u0003 标签`
 *
 * v1 一律视为**不可信**并由 [prune] 丢弃，原因有二：
 *  1. 旧版本只登记「intent 已发出」而非「闹钟已存在」，写入可能被系统静默拦下；
 *  2. 旧版本写出的闹钟**日期是错的**（无日期语义，见 [MorningAlarmPlan.nextUpcoming]）。
 * 留着它只会把错误永久固化，丢弃后由本轮重新写一条正确的。
 */
object MorningAlarmRegistry {

    /**
     * 一条登记项。
     *
     * @param firedAt 该闹钟的真实响铃时刻；null 表示旧格式 v1（不可信，见类注释）
     * @param label 写入系统时钟时使用的标签
     */
    data class Entry(
        val firedAt: LocalDateTime?,
        val label: String
    )

    /** 字段分隔符。用 \u0003 而非中文标点，避免与课程名里的字符冲突。 */
    private const val FIELD_SEPARATOR = "\u0003"

    /** 编码一条登记项：`课程日期 \u0003 响铃时刻 \u0003 标签`。 */
    fun encode(courseDate: String, firedAt: LocalDateTime, label: String): String =
        "$courseDate$FIELD_SEPARATOR$firedAt$FIELD_SEPARATOR$label"

    /**
     * 解析一条登记项；脏数据返回 null（调用方直接丢弃）。
     *
     * 兼容 v1（只有两段）：解析为 `firedAt = null`，由 [prune] 淘汰。
     */
    fun decode(raw: String): Pair<String, Entry>? {
        val parts = raw.split(FIELD_SEPARATOR, limit = 3)
        if (parts.size < 2 || parts[0].isEmpty()) return null
        val key = parts[0]
        if (parts.size == 2) return key to Entry(firedAt = null, label = parts[1])
        val firedAt = runCatching { LocalDateTime.parse(parts[1]) }.getOrNull()
            ?: return key to Entry(firedAt = null, label = parts[2])
        return key to Entry(firedAt = firedAt, label = parts[2])
    }

    /**
     * 回收登记簿，返回应当保留的条目。两条规则：
     *
     *  1. **响铃时刻已过（或不可信）→ 丢弃**。系统时钟建的是一次性闹钟，响过就被
     *     系统删掉了；登记簿若继续记着「已写入」，后续日期永远不会补位
     *     —— 真机症状就是「只有第一条闹钟，之后每天早上都没有」。
     *     v1 条目（[Entry.firedAt] 为 null）同样丢弃，理由见类注释。
     *  2. **课程日期早于回收窗口 → 丢弃**，避免登记簿无界增长。
     *     回收窗口必须明显长于计划前瞻窗口，否则系统里那条删不掉的旧闹钟
     *     会被当成「未登记」重复写一条。
     */
    fun prune(
        registry: Map<String, Entry>,
        now: LocalDateTime,
        retentionDays: Int
    ): Map<String, Entry> {
        val retentionStart = now.date
            .minus(retentionDays, DateTimeUnit.DAY)
            .toString()
        return registry.filter { (key, entry) ->
            val firedAt = entry.firedAt ?: return@filter false
            firedAt > now && key >= retentionStart
        }
    }
}
