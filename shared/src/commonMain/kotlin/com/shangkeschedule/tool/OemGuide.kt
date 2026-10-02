package com.shangkeschedule.tool

import org.jetbrains.compose.resources.StringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.widget_oem_guide_step_autostart
import shangkeschedule.shared.generated.resources.widget_oem_guide_step_battery
import shangkeschedule.shared.generated.resources.widget_oem_guide_step_background
import shangkeschedule.shared.generated.resources.widget_oem_guide_step_related
import shangkeschedule.shared.generated.resources.widget_oem_guide_step_lock

/**
 * 厂商后台限制的分步引导（XL-013）。
 *
 * ## 为什么要有
 *
 * 国产 ROM 普遍会杀后台：电池优化白名单、应用自启动、后台活动、关联启动、锁屏清理……
 * 每一项都可能被关掉，于是小组件不刷新、提醒不响铃，而**用户完全不知道是哪一环卡住了**。
 *
 * 既有 [WidgetTroubleshootBridge] 只做「检测与报告」（有没有权限、是否被限制），
 * 停在「告诉用户有问题」；本模型补上「**告诉用户怎么一步步解决**」。
 *
 * ## 为什么用「枚举 + 参数」而不是「文案表」
 *
 * 直接在数据类里塞 `List<String>` 的文案表会把中文硬编码进 `commonMain` ——
 * 这正是自动审查 R1-026 判为 P0 的坏味道，而且四语翻译无从维护。
 * 所以这里只描述「**要做哪几步**」，由 UI 层把枚举映射到 `StringResource`，
 * 翻译资源仍在 `composeResources` 里，与项目其余文案一致。
 *
 * 这样本文件是纯逻辑、可 JVM 单测、不含任何文案。
 */

/** 已知需要额外引导的厂商。 */
enum class OemVendor(val key: String) {
    XIAOMI("xiaomi"),
    HUAWEI("huawei"),
    HONOR("honor"),
    VIVO("vivo"),
    OPPO("oppo"),
    ONEPLUS("oneplus"),
    REALME("realme"),
    SAMSUNG("samsung"),
    OTHER("other")
}

/** 引导中的一步。UI 层按枚举取对应文案。 */
enum class OemGuideStep {
    /** 允许应用自启动。 */
    ALLOW_AUTOSTART,

    /** 电池优化设为「不限制 / 无限制」。 */
    UNRESTRICT_BATTERY,

    /** 允许后台活动 / 后台高耗电。 */
    ALLOW_BACKGROUND_ACTIVITY,

    /**
     * 允许关联启动（被系统回收后仍可被拉起）。
     * 对提醒类应用最关键 —— 没有它，进程被回收后系统闹钟就再也拉不起本应用。
     */
    ALLOW_RELATED_LAUNCH,

    /** 从「最近任务」下拉加锁，避免一键清理。 */
    LOCK_IN_RECENT_TASKS
}

/**
 * 一个厂商的引导方案。
 *
 * @param steps           需要用户依次完成的步骤（顺序即建议操作顺序）
 * @param hasStartupPage  该厂商是否有独立的「应用启动管理」页面可跳转；
 *                        为 false 时应回退到应用详情页
 */
data class OemGuide(
    val vendor: OemVendor,
    val steps: List<OemGuideStep>,
    val hasStartupPage: Boolean
)

/**
 * 按 `Build.MANUFACTURER` 解析出引导方案。
 *
 * 识别用「前缀包含」而非精确相等 —— 同一品牌下有多个子型号字符串
 * （如 `Xiaomi` / `Redmi` / `POCO` 由不同渠道写入），精确相等会漏掉一部分。
 *
 * [OTHER] 恒返回空步骤：未知厂商的具体开关位置无从查证，
 * 给出猜测步骤比不给更糟 —— 用户会照着找不到的路径反复尝试并最终放弃。
 */
object OemGuideResolver {

    fun resolve(manufacturer: String?): OemGuide {
        val m = manufacturer?.lowercase().orEmpty()
        return when {
            m.containsAny("xiaomi", "redmi", "poco") -> OemGuide(
                vendor = OemVendor.XIAOMI,
                steps = listOf(
                    OemGuideStep.UNRESTRICT_BATTERY,
                    OemGuideStep.ALLOW_AUTOSTART,
                    OemGuideStep.ALLOW_BACKGROUND_ACTIVITY
                ),
                hasStartupPage = true
            )

            m.containsAny("huawei") -> OemGuide(
                vendor = OemVendor.HUAWEI,
                steps = listOf(
                    OemGuideStep.ALLOW_AUTOSTART,
                    OemGuideStep.ALLOW_BACKGROUND_ACTIVITY,
                    OemGuideStep.UNRESTRICT_BATTERY,
                    OemGuideStep.LOCK_IN_RECENT_TASKS
                ),
                hasStartupPage = true
            )

            m.containsAny("honor") -> OemGuide(
                vendor = OemVendor.HONOR,
                steps = listOf(
                    OemGuideStep.ALLOW_AUTOSTART,
                    OemGuideStep.ALLOW_BACKGROUND_ACTIVITY,
                    OemGuideStep.UNRESTRICT_BATTERY
                ),
                hasStartupPage = true
            )

            m.containsAny("vivo", "iqoo") -> OemGuide(
                vendor = OemVendor.VIVO,
                steps = listOf(
                    OemGuideStep.ALLOW_BACKGROUND_ACTIVITY,
                    OemGuideStep.ALLOW_AUTOSTART,
                    OemGuideStep.UNRESTRICT_BATTERY
                ),
                hasStartupPage = true
            )

            m.containsAny("oppo") -> OemGuide(
                vendor = OemVendor.OPPO,
                steps = listOf(
                    // OPPO 的坑是「锁后台」与「自启动」是两处独立开关，只开一个无效
                    OemGuideStep.ALLOW_BACKGROUND_ACTIVITY,
                    OemGuideStep.ALLOW_AUTOSTART,
                    OemGuideStep.ALLOW_RELATED_LAUNCH,
                    OemGuideStep.UNRESTRICT_BATTERY
                ),
                hasStartupPage = true
            )

            m.containsAny("oneplus") -> OemGuide(
                vendor = OemVendor.ONEPLUS,
                steps = listOf(
                    OemGuideStep.ALLOW_BACKGROUND_ACTIVITY,
                    OemGuideStep.ALLOW_AUTOSTART,
                    OemGuideStep.ALLOW_RELATED_LAUNCH,
                    OemGuideStep.UNRESTRICT_BATTERY
                ),
                hasStartupPage = true
            )

            m.containsAny("realme") -> OemGuide(
                vendor = OemVendor.REALME,
                steps = listOf(
                    OemGuideStep.ALLOW_BACKGROUND_ACTIVITY,
                    OemGuideStep.ALLOW_AUTOSTART,
                    OemGuideStep.ALLOW_RELATED_LAUNCH,
                    OemGuideStep.UNRESTRICT_BATTERY
                ),
                hasStartupPage = true
            )

            // 三星一般没有独立的「自启动管理」页，靠电池优化 + 后台使用即可
            m.containsAny("samsung") -> OemGuide(
                vendor = OemVendor.SAMSUNG,
                steps = listOf(
                    OemGuideStep.UNRESTRICT_BATTERY,
                    OemGuideStep.ALLOW_BACKGROUND_ACTIVITY
                ),
                hasStartupPage = false
            )

            else -> OemGuide(vendor = OemVendor.OTHER, steps = emptyList(), hasStartupPage = false)
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { contains(it) }
}

/**
 * 引导步骤 -> 文案资源。
 *
 * 放在 `tool` 层而不在 UI 层，是为了让「步骤有哪些」与「步骤怎么说」解耦：
 * 步骤表是纯逻辑（可单测），文案是翻译资源（随 `composeResources` 多语言走）。
 * 这样新增一个步骤只需要在这里加一行映射 + 各语种加一条资源，逻辑侧零改动。
 */
fun OemGuideStep.textRes(): StringResource =
    when (this) {
        OemGuideStep.ALLOW_AUTOSTART ->
            Res.string.widget_oem_guide_step_autostart

        OemGuideStep.UNRESTRICT_BATTERY ->
            Res.string.widget_oem_guide_step_battery

        OemGuideStep.ALLOW_BACKGROUND_ACTIVITY ->
            Res.string.widget_oem_guide_step_background

        OemGuideStep.ALLOW_RELATED_LAUNCH ->
            Res.string.widget_oem_guide_step_related

        OemGuideStep.LOCK_IN_RECENT_TASKS ->
            Res.string.widget_oem_guide_step_lock
    }
