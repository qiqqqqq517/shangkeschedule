package com.shangkeschedule.tool

/**
 * 系统日历同步的**归属判定**（纯逻辑，无 Android 依赖，v4.68.2）。
 *
 * ## 要解决什么
 *
 * 差分同步的删除分支原先判据是「本日历里凡是不在期望集合中的，一律删掉」——
 * 它问的是「**这一刻有没有课**」，而不是「**这条事件是不是本应用写的**」。
 * 两者不等价，于是用户在这个日历里**手工添加**的事件（生日、社团、临时提醒），
 * 只要开始时刻恰好没有对应的课，下一次同步就会被静默删掉。
 *
 * 用户既不知道是谁删的，也看不到任何报错（从代码看这是「预期行为」）。
 *
 * ## 做法
 *
 * 写入时在 `DESCRIPTION` 开头加一个**行内哨兵前缀**（见 [OWNER_MARK_PREFIX]），
 * 删除时只删「带前缀」的。手工事件没有这个前缀，于是被完整保留。
 *
 * 选 `DESCRIPTION` 而非星链用的 `customAppUri` 的理由见本仓
 * `CalendarAccountManager` 里 [OWNER_MARK_PREFIX] 的 KDoc（minSdk 26 覆盖面优先）。
 *
 * ## 为什么放在 commonMain
 *
 * 这是纯字符串判定，不依赖 `ContentResolver`，故可在 JVM 直接单测 ——
 * 「不能误删手工事件」是一条必须用测试钉死、而不是靠注释承诺的契约。
 */
object CalendarOwnerMark {

    /** 归属标记前缀，与 `CalendarAccountManager.OWNER_MARK_PREFIX` 保持一致。 */
    const val OWNER_MARK_PREFIX: String = "[上课:"

    /** 归属标记的结束符。用明确的收尾，避免前缀与正文互相吞并。 */
    private const val OWNER_MARK_SUFFIX = "]"

    /**
     * 生成带归属标记的描述文本。
     *
     * 标记放最前，保证**前缀匹配**能稳定识别（若把标记放末尾，用户在日历 App 里
     * 编辑正文时很容易把它删掉）。
     */
    fun stamp(body: String): String = "$OWNER_MARK_PREFIX${body.trim()}$OWNER_MARK_SUFFIX"

    /**
     * 该事件是否是本应用写入的。
     *
     * null / 空描述一律判为**非本应用所有**，宁可漏删也不误删 —— 漏删的后果
     * 只是下次同步多留一条陈旧事件（再下一次就会被清掉），而误删不可恢复。
     */
    fun isOwnedBy(description: String?): Boolean =
        description != null && description.startsWith(OWNER_MARK_PREFIX)

    /** 从带标记的描述里还原出正文（去掉前缀与收尾）。 */
    fun unmark(description: String?): String? {
        if (!isOwnedBy(description)) return null
        return description!!.removePrefix(OWNER_MARK_PREFIX)
            .removeSuffix(OWNER_MARK_SUFFIX)
    }
}