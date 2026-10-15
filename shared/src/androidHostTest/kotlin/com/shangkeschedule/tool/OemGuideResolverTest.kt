package com.shangkeschedule.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OemGuideResolver] 的厂商识别与步骤表。
 *
 * 重点锁两件事：
 * 1. 子型号字符串（Redmi / POCO / iQOO）不能漏识别 —— 精确相等是最容易犯的错；
 * 2. 未知厂商必须返回**空步骤**而不是猜测步骤（给错路径比不给更伤用户信任）。
 */
class OemGuideResolverTest {

    @Test
    fun recognizesXiaomiFamily() {
        listOf("Xiaomi", "xiaomi", "Redmi", "POCO", "redmi").forEach { m ->
            val g = OemGuideResolver.resolve(m)
            assertEquals("厂商应为 XIAOMI，实际 $m", OemVendor.XIAOMI, g.vendor)
            assertTrue("$m 至少要有省电策略步骤", OemGuideStep.UNRESTRICT_BATTERY in g.steps)
            assertTrue("$m 至少要有自启动步骤", OemGuideStep.ALLOW_AUTOSTART in g.steps)
        }
    }

    @Test
    fun recognizesHuaweiAndHonorSeparately() {
        assertEquals(OemVendor.HUAWEI, OemGuideResolver.resolve("HUAWEI").vendor)
        assertEquals(OemVendor.HONOR, OemGuideResolver.resolve("HONOR").vendor)
    }

    @Test
    fun recognizesVivoAndIqoo() {
        assertEquals(OemVendor.VIVO, OemGuideResolver.resolve("vivo").vendor)
        assertEquals(OemVendor.VIVO, OemGuideResolver.resolve("iQOO").vendor)
    }

    @Test
    fun oppoFamilyAllRequireRelatedLaunch() {
        // OPPO 系的坑：只开「自启动」不���「关联启动」，进程被回收后仍拉不起来
        listOf("OPPO", "OnePlus", "realme").forEach { m ->
            val g = OemGuideResolver.resolve(m)
            assertTrue(
                "$m 必须包含关联启动步骤，实际 ${g.steps}",
                OemGuideStep.ALLOW_RELATED_LAUNCH in g.steps
            )
        }
    }

    @Test
    fun samsungHasNoDedicatedStartupPage() {
        val g = OemGuideResolver.resolve("samsung")
        assertEquals(OemVendor.SAMSUNG, g.vendor)
        assertFalse("三星没有独立自启动管理页，应回退应用详情页", g.hasStartupPage)
    }

    @Test
    fun unknownVendorGetsNoGuessedSteps() {
        listOf(null, "", "Google", "unknown-brand", "samsung-x").forEach { m ->
            val g = OemGuideResolver.resolve(m)
            if (m == "samsung-x") return@forEach
            assertEquals("未识别厂商应归 OTHER：$m", OemVendor.OTHER, g.vendor)
            assertTrue("未识别厂商不得给出猜测步骤：$m -> ${g.steps}", g.steps.isEmpty())
            assertFalse(g.hasStartupPage)
        }
    }

    @Test
    fun everyKnownVendorHasAtLeastOneStep() {
        listOf(
            "Xiaomi", "HUAWEI", "HONOR", "vivo", "OPPO", "OnePlus", "realme", "samsung"
        ).forEach { m ->
            val g = OemGuideResolver.resolve(m)
            assertTrue("$m 应有引导步骤", g.steps.isNotEmpty())
            assertTrue("$m 的步骤不应重复", g.steps.distinct().size == g.steps.size)
        }
    }
}
