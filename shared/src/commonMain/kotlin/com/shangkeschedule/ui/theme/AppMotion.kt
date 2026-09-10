package com.shangkeschedule.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.anim_group_bar_hide
import shangkeschedule.shared.generated.resources.anim_group_bar_hide_desc
import shangkeschedule.shared.generated.resources.anim_group_course_cell
import shangkeschedule.shared.generated.resources.anim_group_course_cell_desc
import shangkeschedule.shared.generated.resources.anim_group_glass_floating
import shangkeschedule.shared.generated.resources.anim_group_glass_floating_desc
import shangkeschedule.shared.generated.resources.anim_group_nav_transition
import shangkeschedule.shared.generated.resources.anim_group_nav_transition_desc
import shangkeschedule.shared.generated.resources.anim_group_page_entrance
import shangkeschedule.shared.generated.resources.anim_group_page_entrance_desc
import shangkeschedule.shared.generated.resources.anim_group_week_pager
import shangkeschedule.shared.generated.resources.anim_group_week_pager_desc
import shangkeschedule.shared.generated.resources.anim_style_gentle
import shangkeschedule.shared.generated.resources.anim_style_gentle_desc
import shangkeschedule.shared.generated.resources.anim_style_glass
import shangkeschedule.shared.generated.resources.anim_style_glass_desc
import shangkeschedule.shared.generated.resources.anim_style_snappy
import shangkeschedule.shared.generated.resources.anim_style_snappy_desc
import shangkeschedule.shared.generated.resources.touch_feedback_ripple
import shangkeschedule.shared.generated.resources.touch_feedback_ripple_desc
import shangkeschedule.shared.generated.resources.touch_feedback_halo
import shangkeschedule.shared.generated.resources.touch_feedback_halo_desc
import shangkeschedule.shared.generated.resources.touch_feedback_none
import shangkeschedule.shared.generated.resources.touch_feedback_none_desc

/**
 * 全局动效系统（v3.26.0 · v3.27 Apple HIG 重构）。
 *
 * 设计目标：把此前散落在各调用点、各自写死的时长/缓动收口成一套「风格 × 分组」的
 * 统一令牌，经 [LocalAppMotion] 注入全 App。与 [LocalGlassBlurRadius] 同源思路：
 * 一处设定，全端同步，不再各件各值。
 *
 * v3.27 重构：默认 GLASS 风格对齐 Apple HIG 动效规范——
 * 无过冲 ease-out 曲线（cubic-bezier(0.32, 0.72, 0, 1)）、平滑 tween 替代弹簧、
 * 时带对齐 150/250/350ms，幅度克制，强调「润而不跳」的精致感。
 *
 * 两个维度：
 * - [AnimationStyle]：三档全局风格（琉璃流畅 / 舒缓轻移 / 灵动跟手），决定所有动画的
 *   时长、缓动、手感。枚举可扩展——加一档只需新增枚举项 + 一套 [MotionTokens]。
 * - [AnimationGroup]：六个可独立开关的动画分组。关掉某组 ⇒ [resolveMotion] 把该组对应
 *   的令牌降级为「瞬切」（duration=0 / snap / 缩放归 1），该项无动画；周翻页这类无法靠
 *   时长归零关闭的，用 [AppMotion.isEnabled] 在调用点显式分支。分组同样可扩展。
 */

/** 全局动画风格。三档对应三种性格，均可被用户选用，结构上预留后续新增。 */
enum class AnimationStyle(
    val value: String,
    val labelRes: StringResource,
    val descRes: StringResource
) {
    /** 琉璃流畅：Apple HIG 风格——ease-out 平滑曲线、克制幅度，通透主题默认（推荐）。 */
    GLASS("GLASS", Res.string.anim_style_glass, Res.string.anim_style_glass_desc),

    /** 舒缓轻移：克制精致——淡入 + 微位移，更慢更柔，几乎无存在感。 */
    GENTLE("GENTLE", Res.string.anim_style_gentle, Res.string.anim_style_gentle_desc),

    /** 灵动跟手：短促 snappy——反馈强、跟手，年轻有活力。 */
    SNAPPY("SNAPPY", Res.string.anim_style_snappy, Res.string.anim_style_snappy_desc);

    /** 该风格对应的动效令牌（未叠加任何分组开关）。 */
    val tokens: MotionTokens
        get() = when (this) {
            GLASS -> GlassTokens
            GENTLE -> GentleTokens
            SNAPPY -> SnappyTokens
        }

    companion object {
        fun fromString(value: String?): AnimationStyle =
            entries.find { it.value == value } ?: GLASS
    }
}

/**
 * 触摸反馈风格（v3.27.2 新增）。
 * 手指按下时从触点扩散的视觉反馈，两种苹果风格可选。
 */
enum class TouchFeedbackStyle(
    val value: String,
    val labelRes: StringResource,
    val descRes: StringResource
) {
    /** 涟漪式：从触点向外扩散的柔和圆形涟漪，克制低透明度，类似 iOS 列表 cell 按压。 */
    RIPPLE("RIPPLE", Res.string.touch_feedback_ripple, Res.string.touch_feedback_ripple_desc),

    /** 光晕式：触点周围柔和的光韵扩散，模糊边缘，更梦幻更「玻璃」。 */
    HALO("HALO", Res.string.touch_feedback_halo, Res.string.touch_feedback_halo_desc),

    /** 关闭：无触摸视觉反馈（仅保留按压缩放）。 */
    NONE("NONE", Res.string.touch_feedback_none, Res.string.touch_feedback_none_desc);

    companion object {
        fun fromString(value: String?): TouchFeedbackStyle =
            entries.find { it.value == value } ?: RIPPLE
    }
}

/** 可独立开关的动画分组。关掉即该项瞬切无动画。可扩展。 */
enum class AnimationGroup(
    val value: String,
    val labelRes: StringResource,
    val descRes: StringResource
) {
    /** 课程格点按交互反馈（周页/今日页课程块）。 */
    COURSE_CELL("COURSE_CELL", Res.string.anim_group_course_cell, Res.string.anim_group_course_cell_desc),

    /** 周切换翻页（滑周 / 跳周的玻璃滑动过渡）。 */
    WEEK_PAGER("WEEK_PAGER", Res.string.anim_group_week_pager, Res.string.anim_group_week_pager_desc),

    /** 玻璃悬浮件（底栏胶囊 / 回到本周圆钮 / 挂起条 / 玻璃 FAB 的出现·隐藏·按压）。 */
    GLASS_FLOATING("GLASS_FLOATING", Res.string.anim_group_glass_floating, Res.string.anim_group_glass_floating_desc),

    /** 页面入场（今日页 / 周页首次进入时课程块错峰淡入）。 */
    PAGE_ENTRANCE("PAGE_ENTRANCE", Res.string.anim_group_page_entrance, Res.string.anim_group_page_entrance_desc),

    /** 导航转场（二级页 push / pop 滑动过渡）。 */
    NAV_TRANSITION("NAV_TRANSITION", Res.string.anim_group_nav_transition, Res.string.anim_group_nav_transition_desc),

    /** 底栏隐藏（滚动时底栏 / 圆钮的下滑淡出）。 */
    BAR_HIDE("BAR_HIDE", Res.string.anim_group_bar_hide, Res.string.anim_group_bar_hide_desc);

    companion object {
        fun fromString(value: String?): AnimationGroup? =
            entries.find { it.value == value }
    }
}

/**
 * 一套动效令牌：按语义角色给出时长 / 缓动 / 弹簧 / 幅度。
 *
 * 「弹簧类」角色（悬浮件缩放、按压、课程格按压）直接存已构建的 [FiniteAnimationSpec]，
 * 因为其种类随风格而变（琉璃流畅=tween ease-out / 舒缓轻移=tween / 灵动跟手=硬 spring），
 * 存原始 duration+easing 无法表达。其余角色（导航、底栏隐藏、入场、展开、变色、呼吸）
 * 恒为 tween，存 duration+easing 由调用点 `tween<T>(...)` 就地构建（避免泛型擦除麻烦）。
 */
data class MotionTokens(
    // --- NAV_TRANSITION：二级页 push/pop 滑动（IntOffset，调用点 tween<IntOffset>） ---
    val navDurationMs: Int,
    val navEasing: Easing,

    // --- BAR_HIDE：底栏 / 圆钮下滑淡出（Float translationY + alpha） ---
    val hideDurationMs: Int,
    val hideEasing: Easing,

    // --- GLASS_FLOATING：悬浮件淡入缩放 enter/exit + 按压回弹 ---
    val emphasisScaleSpec: FiniteAnimationSpec<Float>,
    val emphasisFadeSpec: FiniteAnimationSpec<Float>,
    val emphasisInitialScale: Float,
    val emphasisTargetScale: Float,
    val pressSpec: FiniteAnimationSpec<Float>,
    val pressScale: Float,

    // --- COURSE_CELL：课程格点按反馈 ---
    val cellPressSpec: FiniteAnimationSpec<Float>,
    val cellPressScale: Float,
    val cellLiftDp: Dp,

    // --- PAGE_ENTRANCE：课程块错峰淡入（fade + 轻微上移） ---
    val entranceDurationMs: Int,
    val entranceEasing: Easing,
    val entranceStaggerMs: Int,
    val entranceSlideDp: Dp,

    // --- 次要（不随分组开关，仅随风格）：设置页展开 / 变色 / 预览尺寸 / hero 呼吸 ---
    val expandDurationMs: Int,
    val expandEasing: Easing,
    val colorDurationMs: Int,
    val resizeDurationMs: Int,
    val resizeEasing: Easing,
    val pulseDurationMs: Int,
)

/** 缓动曲线（自定义 CubicBezier，避免依赖较新的 Ease* 顶层常量）。 */
// iOS 26 过渡基准曲线：cubic-bezier(0.25, 0.1, 0.25, 1)（CSS ease 的精确值），
// 用于「物理弹簧不适合、必须给时长」的过渡（导航转场 / 底栏隐藏 / 入场）。
private val IosEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
// iOS 26 sheet / 面板过渡：cubic-bezier(0.32, 0.72, 0, 1)，快速启动、长尾收束
private val IosSheetEase = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
private val GentleEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)        // Material 标准，平滑
private val SnappyEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)          // 快出，跟手

/**
 * iOS 26 物理弹簧（SwiftUI 的 `.smooth` / `.snappy` / `.bouncy` 预设）。
 *
 * iOS 26 的动效语言是**物理弹簧**：位移、缩放、转场都带轻微过冲并自然收束，
 * 而不是纯粹的 ease-out tween。三个预设的公开语义（Apple 文档）：
 * - `.smooth`  ：无过冲、丝滑收束 —— 用于大多数状态变化与入场
 * - `.snappy`  ：轻微过冲、短促跟手 —— 用于按压反馈、开关、分段控件
 * - `.bouncy`  ：明显过冲、弹性回弹 —— 用于拖拽落位、FAB 出现、强调元素
 *
 * 这里的 dampingRatio / stiffness 按上述语义取值（dampingRatio ≥1 = 无过冲）。
 */
private val IosSmoothSpring = spring<Float>(
    dampingRatio = 1.0f,
    stiffness = Spring.StiffnessMediumLow
)
private val IosSnappySpring = spring<Float>(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessMedium
)
private val IosBouncySpring = spring<Float>(
    dampingRatio = 0.62f,
    stiffness = Spring.StiffnessMediumLow
)

/** 柔和顺滑（iOS 26 默认）：物理弹簧驱动，无过冲、丝滑收束。
 *  时长型过渡对齐 iOS 26 时带：导航 350ms / 隐藏 240ms / 入场 320ms / 展开 280ms。 */
private val GlassTokens = MotionTokens(
    navDurationMs = 350, navEasing = IosSheetEase,
    hideDurationMs = 240, hideEasing = IosEase,
    emphasisScaleSpec = IosBouncySpring,
    emphasisFadeSpec = tween(220, easing = IosEase),
    emphasisInitialScale = 0.88f, emphasisTargetScale = 1f,
    pressSpec = IosSnappySpring,
    pressScale = 0.97f,
    cellPressSpec = IosSnappySpring,
    cellPressScale = 0.97f, cellLiftDp = 1.dp,
    entranceDurationMs = 320, entranceEasing = IosEase, entranceStaggerMs = 40, entranceSlideDp = 10.dp,
    expandDurationMs = 280, expandEasing = IosSheetEase,
    colorDurationMs = 320,
    resizeDurationMs = 300, resizeEasing = IosSheetEase,
    pulseDurationMs = 1200,
)

/** 轻盈舒缓：更慢更柔的弹簧，淡入 + 微位移，安静无弹跳。 */
private val GentleTokens = MotionTokens(
    navDurationMs = 420, navEasing = GentleEase,
    hideDurationMs = 320, hideEasing = GentleEase,
    emphasisScaleSpec = IosSmoothSpring,
    emphasisFadeSpec = tween(320, easing = GentleEase),
    emphasisInitialScale = 0.94f, emphasisTargetScale = 1f,
    pressSpec = IosSmoothSpring,
    pressScale = 0.95f,
    cellPressSpec = IosSmoothSpring,
    cellPressScale = 0.985f, cellLiftDp = 2.dp,
    entranceDurationMs = 460, entranceEasing = GentleEase, entranceStaggerMs = 70, entranceSlideDp = 8.dp,
    expandDurationMs = 420, expandEasing = GentleEase,
    colorDurationMs = 520,
    resizeDurationMs = 420, resizeEasing = GentleEase,
    pulseDurationMs = 1400,
)

/** 灵动跟手（iOS 26 `.snappy` / `.bouncy`）：短促弹回，反馈强、跟手。 */
private val SnappyTokens = MotionTokens(
    navDurationMs = 240, navEasing = SnappyEase,
    hideDurationMs = 160, hideEasing = SnappyEase,
    emphasisScaleSpec = IosSnappySpring,
    emphasisFadeSpec = tween(140, easing = LinearOutSlowInEasing),
    emphasisInitialScale = 0.72f, emphasisTargetScale = 1f,
    pressSpec = IosSnappySpring,
    pressScale = 0.88f,
    cellPressSpec = IosSnappySpring,
    cellPressScale = 0.94f, cellLiftDp = 3.dp,
    entranceDurationMs = 240, entranceEasing = SnappyEase, entranceStaggerMs = 25, entranceSlideDp = 16.dp,
    expandDurationMs = 220, expandEasing = SnappyEase,
    colorDurationMs = 260,
    resizeDurationMs = 240, resizeEasing = SnappyEase,
    pulseDurationMs = 800,
)

/**
 * 已解析的全局动效配置：风格 + 叠加了分组开关后的令牌 + 关闭分组集合。
 * 调用点读 [LocalAppMotion].current 得到本对象，用 [tokens] 取参数、用 [isEnabled]
 * 判断某组是否需要动画（周翻页这类无法靠时长归零关闭的走显式分支）。
 */
data class AppMotion(
    val style: AnimationStyle,
    val tokens: MotionTokens,
    val disabledGroups: Set<AnimationGroup>,
) {
    fun isEnabled(group: AnimationGroup): Boolean = group !in disabledGroups
}

/**
 * 把风格的令牌按「关闭的分组」降级为瞬切：
 * duration→0、弹簧→snap()、缩放→1、位移→0，使该项无可见动画。
 */
fun resolveMotion(style: AnimationStyle, disabledGroups: Set<AnimationGroup>): AppMotion {
    val base = style.tokens
    val tokens = base.copy(
        // NAV_TRANSITION
        navDurationMs = if (AnimationGroup.NAV_TRANSITION in disabledGroups) 0 else base.navDurationMs,
        // BAR_HIDE
        hideDurationMs = if (AnimationGroup.BAR_HIDE in disabledGroups) 0 else base.hideDurationMs,
        // GLASS_FLOATING
        emphasisScaleSpec = if (AnimationGroup.GLASS_FLOATING in disabledGroups) snap() else base.emphasisScaleSpec,
        emphasisFadeSpec = if (AnimationGroup.GLASS_FLOATING in disabledGroups) snap() else base.emphasisFadeSpec,
        emphasisInitialScale = if (AnimationGroup.GLASS_FLOATING in disabledGroups) 1f else base.emphasisInitialScale,
        pressSpec = if (AnimationGroup.GLASS_FLOATING in disabledGroups) snap() else base.pressSpec,
        pressScale = if (AnimationGroup.GLASS_FLOATING in disabledGroups) 1f else base.pressScale,
        // COURSE_CELL
        cellPressSpec = if (AnimationGroup.COURSE_CELL in disabledGroups) snap() else base.cellPressSpec,
        cellPressScale = if (AnimationGroup.COURSE_CELL in disabledGroups) 1f else base.cellPressScale,
        cellLiftDp = if (AnimationGroup.COURSE_CELL in disabledGroups) 0.dp else base.cellLiftDp,
        // PAGE_ENTRANCE 不在此归零：入场时长/错峰/位移同时被「周翻页扫光」复用，
        // 归零会误杀扫光。入场调用点已用 motion.isEnabled(PAGE_ENTRANCE) 自行门控，
        // 扫光则只看 WEEK_PAGER——两组互不牵连。
    )
    return AppMotion(style, tokens, disabledGroups)
}

/**
 * 当前生效的全局动效配置。由 `ShangKeScheduleTheme` 从
 * `AppSettingsModel.animationStyle` + `disabledAnimationGroups` 注入；
 * 调节入口在「外观与样式 → 个性化显示 → 动画效果」。所有动画共读这一个 Local，
 * 用户改一次，全端同步。
 */
val LocalAppMotion = compositionLocalOf { resolveMotion(AnimationStyle.GLASS, emptySet()) }
