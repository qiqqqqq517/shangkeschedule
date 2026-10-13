package com.shangkeschedule.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CalendarBrandColor]：系统日历标识色的品牌迁移判据。
 *
 * 锁住一条**数据安全**契约：
 * 用户在系统日历里**自己改过**的日历颜色，绝不能被课表同步覆盖。
 *
 * 回归背景：`Calendars.CALENDAR_COLOR` 只在日历首次创建时写一次，
 * 品牌色换过之后老用户那条日历永远停在旧色（系统日历里这条日历的唯一视觉标识），
 * 表现为「新图标没有替换完成」。修法是补一次迁移，但迁移必须**只认我方旧值** ——
 * 若写成「与品牌色不同就覆盖」，就会静默抹掉用户的颜色偏好，
 * 与本仓 v4.68.2 修掉的「删掉用户手工事件」是同一类错误。
 */
class CalendarBrandColorTest {

    /** 我方旧版本（v3.4.0 起）写下的 Google 蓝 → 必须迁移。 */
    @Test
    fun legacyOwnColorIsMigrated() {
        assertTrue(
            "本应用历史版本写下的标识色必须被迁移到当前品牌",
            CalendarBrandColor.shouldMigrate(0xFF4285F4.toInt())
        )
    }

    /** 从未设过（列为 NULL）→ 迁移：此时不存在任何用户偏好。 */
    @Test
    fun nullColorIsMigrated() {
        assertTrue(CalendarBrandColor.shouldMigrate(null))
    }

    /**
     * 关键回归：**用户自己改的颜色不得被覆盖**。
     *
     * 取几个与品牌色、历史值都不同的常见取值（红 / 绿 / 紫），逐一断言不迁移。
     */
    @Test
    fun userChosenColorIsNeverOverwritten() {
        val userColors = listOf(0xFFE5484D.toInt(), 0xFF22A45D.toInt(), 0xFF6C5CE7.toInt())
        for (c in userColors) {
            assertFalse(
                "用户在系统日历里自选的颜色绝不能被同步覆盖：#%08X".format(c),
                CalendarBrandColor.shouldMigrate(c)
            )
        }
    }

    /** 已是当前品牌色 → 不需要再写（幂等，避免每次同步都产生无谓的 provider 变更）。 */
    @Test
    fun alreadyBrandColorIsNotMigrated() {
        assertFalse(CalendarBrandColor.shouldMigrate(CalendarBrandColor.BRAND))
    }

    /**
     * 品牌色与历史值必须**互不相同**：若相同，迁移判据会退化成「永远不迁移」或
     * 「把品牌色当成旧值反复写」，两者都掩盖真实缺陷。
     */
    @Test
    fun brandColorIsNotInLegacyWhitelist() {
        assertFalse(
            "当前品牌色不得出现在迁移白名单里",
            CalendarBrandColor.BRAND in CalendarBrandColor.LEGACY
        )
    }

    /**
     * 品牌色必须与 `androidApp` 的 `ic_launcher_background` 同值（`#73CAF8`）。
     *
     * 跨模块读不到 `R.color`，故在此把该契约以字面量钉死：改品牌色时
     * 这条断言会失败，提醒同步 `ic_launcher_background.xml` 与
     * `DeveloperAndIconComponents.kt` 的 `NormalIconBgColor`。
     */
    @Test
    fun brandColorMatchesLauncherBackground() {
        assertEquals(0xFF73CAF8.toInt(), CalendarBrandColor.BRAND)
    }
}
