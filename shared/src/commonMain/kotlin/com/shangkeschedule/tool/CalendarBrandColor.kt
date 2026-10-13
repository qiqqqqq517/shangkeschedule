package com.shangkeschedule.tool

/**
 * 系统日历里那条日历的**标识色**及其历史值迁移判据（纯逻辑，无 Android 依赖）。
 *
 * ## 要解决什么
 *
 * 本应用用 `ACCOUNT_TYPE_LOCAL` 的本地日历写入课表，而 `Calendars.CALENDAR_COLOR`
 * **只在日历首次创建时写一次**。于是这条日历在系统日历里的**唯一视觉标识**（那个色块）
 * 会永远停在创建那一刻的取值 —— 品牌色换过之后，老用户看到的就是旧色，
 * 且**重装 App 也不会自愈**（日历行活在系统库里，不属于应用数据）。
 *
 * ## 为什么迁移判据要单独成文件
 *
 * 「哪些值可以被我方改写」是一条**数据安全契约**：`CALENDAR_COLOR` 用户能在系统日历里
 * 自己改，若我方按「与品牌色不同就覆盖」处理，就会静默抹掉用户的偏好 ——
 * 与本仓 v4.68.2 修掉的「删掉用户手工事件」是同一类错误。
 *
 * 所以判据必须是**白名单**（只认我方历史版本写过的值），而不是「不等于期望值就写」。
 * 这条契约必须能用测试钉死，而不是只靠注释承诺，故放在 commonMain 供 JVM 直接单测。
 */
object CalendarBrandColor {

    /**
     * 当前品牌色（ARGB），必须与 `androidApp/src/main/res/values/ic_launcher_background.xml`
     * 的 `ic_launcher_background` 同值。
     *
     * 跨模块读不到 `androidApp` 的 `R.color`，故在此显式声明；`shared` 侧另有同值常量
     * `NormalIconBgColor`（`DeveloperAndIconComponents.kt`，设置页图标预览用）。
     * **改品牌色时这三处须同步。**
     */
    const val BRAND: Int = 0xFF73CAF8.toInt()

    /**
     * 本应用**历史版本自己写入过**的标识色（迁移白名单）。
     *
     * `0xFF4285F4` = 自 v3.4.0 起 `getOrCreateCalendarId` 写死的 Google 蓝，
     * 与应用品牌色无关。将来再换品牌色，把**上一个品牌色**追加进来即可，
     * 迁移会逐级补齐，无需改逻辑。
     */
    val LEGACY: Set<Int> = setOf(0xFF4285F4.toInt())

    /**
     * 该日历的当前标识色是否应迁移到 [BRAND]。
     *
     * - `null`（从未设过）→ 迁移：没有用户偏好可言；
     * - ∈ [LEGACY] → 迁移：这是我方旧版本写下的值；
     * - 其它任何值 → **不迁移**：那是用户在系统日历里自己改的颜色，绝不覆盖。
     */
    fun shouldMigrate(current: Int?): Boolean =
        current == null || current in LEGACY
}
