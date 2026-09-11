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
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shangkeschedule.data.model.AppThemePreset
import org.jetbrains.compose.resources.StringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.anim_group_bar_hide
import shangkeschedule.shared.generated.resources.anim_group_bar_hide_desc
import shangkeschedule.shared.generated.resources.anim_group_bottom_sheet
import shangkeschedule.shared.generated.resources.anim_group_bottom_sheet_desc
import shangkeschedule.shared.generated.resources.anim_group_course_cell
import shangkeschedule.shared.generated.resources.anim_group_course_cell_desc
import shangkeschedule.shared.generated.resources.anim_group_dialog
import shangkeschedule.shared.generated.resources.anim_group_dialog_desc
import shangkeschedule.shared.generated.resources.anim_group_glass_floating
import shangkeschedule.shared.generated.resources.anim_group_glass_floating_desc
import shangkeschedule.shared.generated.resources.anim_group_nav_transition
import shangkeschedule.shared.generated.resources.anim_group_nav_transition_desc
import shangkeschedule.shared.generated.resources.anim_group_page_entrance
import shangkeschedule.shared.generated.resources.anim_group_page_entrance_desc
import shangkeschedule.shared.generated.resources.anim_group_tab_switch
import shangkeschedule.shared.generated.resources.anim_group_tab_switch_desc
import shangkeschedule.shared.generated.resources.anim_group_week_pager
import shangkeschedule.shared.generated.resources.anim_group_week_pager_desc
import shangkeschedule.shared.generated.resources.anim_speed_fast
import shangkeschedule.shared.generated.resources.anim_speed_relaxed
import shangkeschedule.shared.generated.resources.anim_speed_standard
import shangkeschedule.shared.generated.resources.anim_style_gentle
import shangkeschedule.shared.generated.resources.anim_style_gentle_desc
import shangkeschedule.shared.generated.resources.anim_style_glass
import shangkeschedule.shared.generated.resources.anim_style_glass_desc
import shangkeschedule.shared.generated.resources.anim_style_snappy
import shangkeschedule.shared.generated.resources.anim_style_snappy_desc

/**
 * 全局动效系统（v3.26.0 · v3.27 Apple HIG 重构 · v3.43.0 主题分档）。
 *
 * 设计目标：把散落在各调用点、各自写死的时长/缓动收口成一套「风格 × 主题 × 分组」的
 * 统一令牌，经 [LocalAppMotion] 注入全 App。一处设定，全端同步。
 *
 * v3.43.0（《交互动效审查_三主题》P0/P1 修复）新增**主题覆盖层**：
 * 此前三套主题的动效级分支全库只有 1 处，交互一旦发生（点击 / 切 Tab / 开弹窗 / 拖拽），
 * 三套主题立刻收敛到同一套 iOS 26 + Material 混合语言上。现在：
 * - [MotionPressMode]：按主题决定按压反馈语言（缩放 / 浓度 / 底色加深）；
 * - [NavMotionMode]：按主题决定导航转场形态（整屏横推 / 淡入上浮 / 纸页层叠）；
 * - [ThemeMotionProfile] + `MotionTokens.withThemePreset`：时长 / 曲线 / 幅度 / 弹簧阻尼
 *   按主题分档（柔绘最慢且零缩放、书卷零过冲、通透保留 iOS 弹簧）；
 * - 新增触摸指示、Tab 切换、面板/对话框、卡片按压、状态渐变等角色令牌。
 *
 * 三个维度：
 * - [AnimationStyle]：三档全局风格，决定整体快慢与弹簧手感；
 * - [AppThemePreset]（外部注入）：主题覆盖层，决定"用什么语言表达"（见上）；
 * - [AnimationGroup]：九个可独立开关的动画分组。关掉某组 ⇒ [resolveMotion] 把该组令牌
 *   降级为「瞬切」；周翻页这类无法靠时长归零关闭的，用 [AppMotion.isEnabled] 显式分支。
 */

/**
 * 「动效速度」——全局时长缩放倍率（v3.44.0「动画效果」新增 · v3.45.1 重新标定）。
 *
 * **倍率是「速度」不是「时长」**：数值越大越快，实际时长 = 基线时长 ÷ 倍率。
 * `1.0` = 基线原速。用户入口在「个性化显示 → 动画效果 → 动效速度」。
 *
 * v3.45.1 按用户反馈（「动效速度感知不强，整体调低一点」）重新标定：
 * - **整体调低**：默认档 1.6× → **2.0×**，全站时长再压约 20%（等价于"默认更快"）；
 * - **档位差距拉开**：旧 1.3 / 1.6 / 2.0 相邻只差 1.19~1.25 倍，切换几乎看不出快慢；
 *   新 1.45 / 2.0 / 2.8 相邻差 1.38~1.4 倍（时长 −28% / −29%），一档一档按下去手感差别明确。
 */
enum class MotionSpeed(
    val value: String,
    /** 速度倍率：实际时长 = 基线时长 ÷ factor。 */
    val factor: Float,
    val labelRes: StringResource
) {
    /** 舒缓：比基线快约 31%（1.45×）——比默认档慢约 38%，慢得能看出来。 */
    RELAXED("RELAXED", 1.45f, Res.string.anim_speed_relaxed),

    /** 标准（默认）：**快一倍**（2.0×）——全站时长减半，日常手感基准。 */
    STANDARD("STANDARD", 2.00f, Res.string.anim_speed_standard),

    /** 快：比基线快约 1.8 倍（2.8×）——干脆利落，几乎没有等待感。 */
    FAST("FAST", 2.80f, Res.string.anim_speed_fast);

    companion object {
        fun fromString(value: String?): MotionSpeed =
            entries.find { it.value == value } ?: STANDARD
    }
}

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

    /** 该风格对应的动效令牌（未叠加主题覆盖与分组开关）。 */
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
 * 按压反馈语言（按主题分档，v3.43.0）。
 * 决定课程卡 / 列表行 / 卡片被按下时"用什么方式回应"。
 */
enum class MotionPressMode {
    /** 缩放 + 微抬起——通透（iOS 26）保留 HIG 允许的轻微形变。 */
    SCALE,

    /** 浓度 / 不透明度响应，零缩放、零位移——柔绘：保证软投影与羽化环全程零形变。 */
    CONCENTRATION,

    /** 底色 + 描边加深，零缩放——书卷：不透明纸卡不移动，避免"会动的色斑"。 */
    COLOR_DARKEN,
}

/** 导航转场形态（按主题分档，v3.43.0）。 */
enum class NavMotionMode {
    /** 整屏横推 + 尾随视差——通透（iOS push/pop 原生方向感）。 */
    SLIDE,

    /** 淡入 + 轻微上浮，禁止横向位移——柔绘：横移会打断晕染画面的连续性。 */
    FADE_UP,

    /** 新页自右缘短距淡入、旧页原地仅降不透明度——书卷：留白边距必须稳定。 */
    LAYER_PUSH,
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
    BAR_HIDE("BAR_HIDE", Res.string.anim_group_bar_hide, Res.string.anim_group_bar_hide_desc),

    /** 底部面板（悬浮面板的升起 / 收起 + 遮罩渐入）。v3.43.0 新增——此前不受任何开关控制。 */
    BOTTOM_SHEET("BOTTOM_SHEET", Res.string.anim_group_bottom_sheet, Res.string.anim_group_bottom_sheet_desc),

    /** 对话框（确认弹窗的淡入 / 收起的缩放回弹）。v3.43.0 新增——此前不受任何开关控制。 */
    DIALOG("DIALOG", Res.string.anim_group_dialog, Res.string.anim_group_dialog_desc),

    /** Tab 切换（底栏选中胶囊迁移 + 图标变体切换 + 页面内容淡入）。v3.43.0 新增。 */
    TAB_SWITCH("TAB_SWITCH", Res.string.anim_group_tab_switch, Res.string.anim_group_tab_switch_desc);

    companion object {
        fun fromString(value: String?): AnimationGroup? =
            entries.find { it.value == value }
    }
}

/**
 * 一套动效令牌：按语义角色给出时长 / 缓动 / 弹簧 / 幅度。
 *
 * 「弹簧类」角色（悬浮件缩放、按压、课程格按压）直接存已构建的 [FiniteAnimationSpec]，
 * 因为其种类随风格与主题而变；其余角色恒为 tween，存 duration+easing 由调用点就地构建。
 */
data class MotionTokens(
    // --- NAV_TRANSITION：二级页 push/pop（IntOffset，调用点 tween<IntOffset>） ---
    val navDurationMs: Int,
    val navEasing: Easing,
    /** 旧页尾随位移比例（SLIDE 用 1/3；其余形态为 0）。 */
    val navTrailFraction: Float,
    /** FADE_UP / LAYER_PUSH 的位移量（新页从该偏移处淡入）。 */
    val navOffsetDp: Dp,

    /**
     * 退场（淡出）时长与曲线——**独立于入场**，遵循「慢进快出」。
     *
     * 入场可以慢（柔绘的"化开"感来自入场），但**旧页淡出必须快**：退场期间整屏处于
     * 半透明态，拖得越久越像「卡住 / 磨蹭」。
     * v3.43.1 修掉「退场复用入场时长」的根因；v3.45.1 再按用户反馈把柔绘档的
     * 入场 600→420、退场 380→220（叠加默认倍率后实际 210 / 110ms）。
     */
    val navExitDurationMs: Int,
    val navExitEasing: Easing,

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
    /** 错峰总延迟上限：stagger × 序号 被钳到这个值，避免满周 30 块时后排 2s 才出现。 */
    val entranceStaggerCapMs: Int,
    /** 入场起始不透明度：0 = 完全不可见（旧行为），0.35 = 一开始就有轮廓。 */
    val entranceInitialAlpha: Float,

    // --- 新增角色（v3.43.0）：触摸指示 / Tab / 面板 / 对话框 / 卡片按压 / 状态渐变 ---
    /** 触摸指示扩散时长（局部按压反馈，非全局 LocalIndication）。 */
    val touchExpandMs: Int,
    /** 触摸指示淡出时长。 */
    val touchFadeMs: Int,
    val touchEasing: Easing,
    /** Tab 选中胶囊迁移时长。 */
    val tabIndicatorMs: Int,
    /** Tab 图标变体切换时长。 */
    val tabIconMs: Int,
    /** 底部面板升起 / 收起 / 遮罩时长与升起位移。 */
    val sheetEnterMs: Int,
    val sheetExitMs: Int,
    val sheetScrimMs: Int,
    val sheetSlideDp: Dp,
    /** 对话框进入 / 退出时长。 */
    val dialogEnterMs: Int,
    val dialogExitMs: Int,
    /** 课程卡按下 / 抬起（浓度或底色响应）时长。 */
    val cardPressMs: Int,
    val cardReleaseMs: Int,
    /** 「已结束课程降透明」等状态渐变时长（取代二值跳变）。 */
    val statusFadeMs: Int,

    // --- 次要（不随分组开关，仅随风格 / 主题）：设置页展开 / 变色 / 预览尺寸 / hero 呼吸 ---
    val expandDurationMs: Int,
    val expandEasing: Easing,
    val colorDurationMs: Int,
    val resizeDurationMs: Int,
    val resizeEasing: Easing,
    val pulseDurationMs: Int,
)

/** 缓动曲线（自定义 CubicBezier，避免依赖较新的 Ease* 顶层常量）。 */
private val IosEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val IosSheetEase = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
private val GentleEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val SnappyEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** 对称 ease-in-out：柔绘专用——两端等速、中段最缓，没有任何"折点"。 */
private val SymmetricEase = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/**
 * 柔绘**入场**曲线（v3.45.1）：起步快、收尾柔。
 *
 * [SymmetricEase] 两端等速，前 30% 几乎不动 —— 观感像「先愣一下再化开」，
 * 这正是用户反馈的「进入 / 退出磨蹭」的来源之一。改为陡起步 + 长收尾：
 * 触发即响应、落定仍柔软，且依旧单调、零过冲（不产生折点）。
 */
private val SoftEnterEase = CubicBezierEasing(0.22f, 0.55f, 0.24f, 1f)

/**
 * iOS 26 物理弹簧（SwiftUI `.smooth` / `.snappy` / `.bouncy`）。
 * dampingRatio / stiffness 按上述语义取值（dampingRatio ≥1 = 无过冲）。
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

/** 临界阻尼弹簧（零过冲）——书卷 / 柔绘专用，替换一切会过冲的弹簧。 */
private val CriticallyDampedSpring = spring<Float>(
    dampingRatio = 1.0f,
    stiffness = Spring.StiffnessMedium
)
private val CriticallyDampedLowSpring = spring<Float>(
    dampingRatio = 1.0f,
    stiffness = Spring.StiffnessMediumLow
)

/** 柔和顺滑（iOS 26 默认）：物理弹簧驱动，无过冲、丝滑收束。 */
private val GlassTokens = MotionTokens(
    navDurationMs = 350, navEasing = IosSheetEase, navTrailFraction = 1f / 3f, navOffsetDp = 0.dp,
    navExitDurationMs = 240, navExitEasing = IosEase,
    hideDurationMs = 240, hideEasing = IosEase,
    emphasisScaleSpec = IosBouncySpring,
    emphasisFadeSpec = tween(220, easing = IosEase),
    emphasisInitialScale = 0.88f, emphasisTargetScale = 1f,
    pressSpec = IosSnappySpring,
    pressScale = 0.97f,
    cellPressSpec = IosSnappySpring,
    cellPressScale = 0.97f, cellLiftDp = 1.dp,
    entranceDurationMs = 320, entranceEasing = IosEase, entranceStaggerMs = 40, entranceSlideDp = 10.dp,
    entranceStaggerCapMs = 240, entranceInitialAlpha = 0.35f,
    touchExpandMs = 400, touchFadeMs = 220, touchEasing = IosSheetEase,
    tabIndicatorMs = 220, tabIconMs = 200,
    sheetEnterMs = 320, sheetExitMs = 220, sheetScrimMs = 320, sheetSlideDp = 0.dp,
    dialogEnterMs = 260, dialogExitMs = 180,
    cardPressMs = 140, cardReleaseMs = 240, statusFadeMs = 400,
    expandDurationMs = 280, expandEasing = IosSheetEase,
    colorDurationMs = 320,
    resizeDurationMs = 300, resizeEasing = IosSheetEase,
    pulseDurationMs = 1200,
)

/** 轻盈舒缓：更慢更柔的弹簧，淡入 + 微位移，安静无弹跳。 */
private val GentleTokens = MotionTokens(
    navDurationMs = 420, navEasing = GentleEase, navTrailFraction = 1f / 3f, navOffsetDp = 0.dp,
    navExitDurationMs = 300, navExitEasing = GentleEase,
    hideDurationMs = 320, hideEasing = GentleEase,
    emphasisScaleSpec = IosSmoothSpring,
    emphasisFadeSpec = tween(320, easing = GentleEase),
    emphasisInitialScale = 0.94f, emphasisTargetScale = 1f,
    pressSpec = IosSmoothSpring,
    pressScale = 0.95f,
    cellPressSpec = IosSmoothSpring,
    cellPressScale = 0.985f, cellLiftDp = 2.dp,
    entranceDurationMs = 460, entranceEasing = GentleEase, entranceStaggerMs = 70, entranceSlideDp = 8.dp,
    entranceStaggerCapMs = 240, entranceInitialAlpha = 0.35f,
    touchExpandMs = 400, touchFadeMs = 220, touchEasing = IosSheetEase,
    tabIndicatorMs = 260, tabIconMs = 220,
    sheetEnterMs = 380, sheetExitMs = 260, sheetScrimMs = 360, sheetSlideDp = 8.dp,
    dialogEnterMs = 320, dialogExitMs = 220,
    cardPressMs = 180, cardReleaseMs = 300, statusFadeMs = 520,
    expandDurationMs = 420, expandEasing = GentleEase,
    colorDurationMs = 520,
    resizeDurationMs = 420, resizeEasing = GentleEase,
    pulseDurationMs = 1400,
)

/** 灵动跟手（iOS 26 `.snappy` / `.bouncy`）：短促弹回，反馈强、跟手。 */
private val SnappyTokens = MotionTokens(
    navDurationMs = 240, navEasing = SnappyEase, navTrailFraction = 1f / 3f, navOffsetDp = 0.dp,
    navExitDurationMs = 160, navExitEasing = SnappyEase,
    hideDurationMs = 160, hideEasing = SnappyEase,
    emphasisScaleSpec = IosSnappySpring,
    emphasisFadeSpec = tween(140, easing = LinearOutSlowInEasing),
    emphasisInitialScale = 0.72f, emphasisTargetScale = 1f,
    pressSpec = IosSnappySpring,
    pressScale = 0.88f,
    cellPressSpec = IosSnappySpring,
    cellPressScale = 0.94f, cellLiftDp = 3.dp,
    entranceDurationMs = 240, entranceEasing = SnappyEase, entranceStaggerMs = 25, entranceSlideDp = 16.dp,
    entranceStaggerCapMs = 200, entranceInitialAlpha = 0.35f,
    touchExpandMs = 260, touchFadeMs = 160, touchEasing = SnappyEase,
    tabIndicatorMs = 160, tabIconMs = 140,
    sheetEnterMs = 240, sheetExitMs = 160, sheetScrimMs = 240, sheetSlideDp = 0.dp,
    dialogEnterMs = 200, dialogExitMs = 140,
    cardPressMs = 110, cardReleaseMs = 180, statusFadeMs = 260,
    expandDurationMs = 220, expandEasing = SnappyEase,
    colorDurationMs = 260,
    resizeDurationMs = 240, resizeEasing = SnappyEase,
    pulseDurationMs = 800,
)

/**
 * 主题动效档位：由 [AppThemePreset] 决定"用什么语言表达"。
 *
 * 这是 v3.43.0 的**总纲性修复**——此前全库动效级主题分支只有 1 处，
 * 三套主题的动效语言实际共用一套 iOS 26 + Material 混合值。
 */
data class ThemeMotionProfile(
    /** 按压反馈语言。 */
    val pressMode: MotionPressMode,
    /** 导航转场形态。 */
    val navMode: NavMotionMode,
    /** 全局触摸指示形态（喂给 LocalIndication）。 */
    val indicationStyle: IndicationStyle,
    /** 触摸指示的峰值色强度（alpha 部分在 [resolveMotion] 里乘到主题色上）。 */
    val indicationAlpha: Float,
)

private val IosProfile = ThemeMotionProfile(
    pressMode = MotionPressMode.SCALE,
    navMode = NavMotionMode.SLIDE,
    indicationStyle = IndicationStyle.HIG_HIGHLIGHT,
    indicationAlpha = 0.06f,
)

/** 柔绘：慢、无声、零缩放零位移；触摸反馈是"软边渗开"；导航不横移。 */
private val SoftProfile = ThemeMotionProfile(
    pressMode = MotionPressMode.CONCENTRATION,
    navMode = NavMotionMode.FADE_UP,
    indicationStyle = IndicationStyle.SOFT_RADIAL,
    indicationAlpha = 0.12f,
)

/** 书卷：克制、端庄、零过冲；触摸反馈是"底色加深"；导航是"纸页层叠"。 */
private val ClaudeProfile = ThemeMotionProfile(
    pressMode = MotionPressMode.COLOR_DARKEN,
    navMode = NavMotionMode.LAYER_PUSH,
    indicationStyle = IndicationStyle.COLOR_DARKEN,
    indicationAlpha = 0.055f,
)

/** 按主题预设取动效档位（未知预设回退通透）。 */
fun themeMotionProfile(preset: AppThemePreset): ThemeMotionProfile = when (preset) {
    AppThemePreset.SOFT -> SoftProfile
    AppThemePreset.CLAUDE -> ClaudeProfile
    else -> IosProfile
}

/**
 * 主题覆盖层：在风格令牌之上按主题改写时长 / 曲线 / 弹簧 / 幅度。
 *
 * 设计原则（对齐《交互动效审查_三主题》）：
 * - 弹簧阻尼：柔绘 / 书卷一律临界阻尼（零过冲），通透保留 iOS 三档弹簧语义；
 * - 课程卡按压：柔绘 / 书卷禁用缩放与位移（`cellPressScale=1`、`cellLiftDp=0`），
 *   反馈改由 [MotionPressMode] 在调用点以浓度 / 底色表达；
 * - 悬浮件：柔绘取消居中缩放（纯位移淡入），书卷压到 0.94 且零过冲；
 * - 时长：面板 / 对话框 / Tab / 触摸 / 卡片按压 / 状态渐变按主题整体错开一档；
 * **但入场的「慢」不传染给退场**——退场一律走 [MotionTokens.navExitDurationMs] 等独立令牌（慢进快出）。
 */
private fun MotionTokens.withThemePreset(preset: AppThemePreset): MotionTokens = when (preset) {
    AppThemePreset.SOFT -> copy(
        // 柔绘身份：仍是三套里最慢的一档 —— 但 v3.45.1 按用户反馈（「进入和退出动画还是有点磨蹭」）
        // 把「进入 / 退出」两类时长整体收紧，并把入场曲线由两端等速改为陡起步：
        // 导航入场 600 → 420、退场 380 → 220；面板 620/380 → 420/220；弹窗 620/380 → 400/220。
        // 叠加默认倍率（1.6 → 2.0）后，柔绘导航入场实际 375 → 210ms、退场 238 → 110ms。
        navDurationMs = 420, navEasing = SoftEnterEase, navTrailFraction = 0f, navOffsetDp = 6.dp,
        // 慢进快出：入场仍比退场长（柔绘的"化开"感来自入场），退场只留一次快速收束
        navExitDurationMs = 220, navExitEasing = IosEase,
        emphasisScaleSpec = CriticallyDampedLowSpring,
        emphasisFadeSpec = tween(260, easing = SoftEnterEase),
        // 柔绘悬浮件：纯位移淡入，取消居中缩放（缩放会强调硬轮廓，与虚化边缘冲突）
        emphasisInitialScale = 1f,
        pressSpec = CriticallyDampedSpring,
        pressScale = 1f,
        cellPressSpec = CriticallyDampedSpring,
        cellPressScale = 1f, cellLiftDp = 0.dp,
        entranceEasing = SoftEnterEase, entranceSlideDp = 0.dp,
        entranceStaggerCapMs = 220,
        touchExpandMs = 380, touchFadeMs = 240, touchEasing = SoftEnterEase,
        tabIndicatorMs = 320, tabIconMs = 200,
        sheetEnterMs = 420, sheetExitMs = 220, sheetScrimMs = 320, sheetSlideDp = 0.dp,
        dialogEnterMs = 400, dialogExitMs = 220,
        cardPressMs = 160, cardReleaseMs = 320, statusFadeMs = 300,
        // 尺寸变化（展开 / 收起这类"体积"过渡）保留对称曲线：它不是"进入"，两端等速才不像被推
        resizeEasing = SymmetricEase,
    )

    AppThemePreset.CLAUDE -> copy(
        navDurationMs = 380, navEasing = IosEase, navTrailFraction = 0f, navOffsetDp = 14.dp,
        navExitDurationMs = 240, navExitEasing = IosEase,
        emphasisScaleSpec = CriticallyDampedLowSpring,
        emphasisFadeSpec = tween(300, easing = IosEase),
        emphasisInitialScale = 0.94f,
        pressSpec = CriticallyDampedSpring,
        pressScale = 1f,
        cellPressSpec = CriticallyDampedSpring,
        cellPressScale = 1f, cellLiftDp = 0.dp,
        entranceStaggerCapMs = 240,
        touchExpandMs = 180, touchFadeMs = 240, touchEasing = IosEase,
        tabIndicatorMs = 280, tabIconMs = 160,
        sheetEnterMs = 380, sheetExitMs = 280, sheetScrimMs = 320, sheetSlideDp = 24.dp,
        dialogEnterMs = 280, dialogExitMs = 220,
        cardPressMs = 160, cardReleaseMs = 240, statusFadeMs = 400,
    )

    // 通透（及其它未来预设）：iOS 26 原生语言，风格令牌原样保留
    else -> this
}

/**
 * 「动效速度」统一缩放：把所有**以毫秒计的时长**除以速度倍率（1.0 = 基线原速）。
 *
 * 与 [reduced] / [gatedBy] 的分工 —— 调用顺序是
 * `withThemePreset → reduced → speedScaled → gatedBy`：
 * 本函数是**用户可调的整体快慢旋钮**，作用在主题分档之后、分组归零之前，
 * 所以被关掉的分组仍然是 0（0 不参与除法，不会变成 1ms）。
 *
 * 刻意不缩放两类：
 * ① 弹簧类（`*Spring` / `*Spec`）——物理参数不是时长，改时长无从谈起；
 * ② [pulseDurationMs]（hero 呼吸等**环境循环动画**，不是过渡，缩放会改掉主题气质）。
 */
private fun MotionTokens.speedScaled(factor: Float): MotionTokens {
    if (factor <= 0f || factor == 1f) return this
    fun Int.s(): Int = if (this <= 0) this else (this / factor).roundToInt().coerceAtLeast(1)
    return copy(
        navDurationMs = navDurationMs.s(),
        navExitDurationMs = navExitDurationMs.s(),
        hideDurationMs = hideDurationMs.s(),
        entranceDurationMs = entranceDurationMs.s(),
        entranceStaggerMs = entranceStaggerMs.s(),
        entranceStaggerCapMs = entranceStaggerCapMs.s(),
        touchExpandMs = touchExpandMs.s(),
        touchFadeMs = touchFadeMs.s(),
        tabIndicatorMs = tabIndicatorMs.s(),
        tabIconMs = tabIconMs.s(),
        sheetEnterMs = sheetEnterMs.s(),
        sheetExitMs = sheetExitMs.s(),
        sheetScrimMs = sheetScrimMs.s(),
        dialogEnterMs = dialogEnterMs.s(),
        dialogExitMs = dialogExitMs.s(),
        cardPressMs = cardPressMs.s(),
        cardReleaseMs = cardReleaseMs.s(),
        statusFadeMs = statusFadeMs.s(),
        expandDurationMs = expandDurationMs.s(),
        colorDurationMs = colorDurationMs.s(),
        resizeDurationMs = resizeDurationMs.s(),
    )
}

/**
 * 「减弱动态效果」降级：位移 / 缩放 / 错峰全部归零，只保留短促的不透明度溶解。
 *
 * 对齐《交互动效审查》P3 无障碍缺口——系统级 Reduce Motion 在 KMP 无统一 API，
 * 因此由应用内开关（「动画效果 → 减弱动态效果」）驱动同一条降级路径。
 */
private fun MotionTokens.reduced(): MotionTokens = copy(
    // 导航：只保留淡入淡出，取消一切位移与视差
    navDurationMs = 180, navTrailFraction = 0f, navOffsetDp = 0.dp,
    navExitDurationMs = 140,
    // 悬浮件：不缩放，仅淡入
    emphasisScaleSpec = snap(),
    emphasisInitialScale = 1f,
    pressSpec = snap(), pressScale = 1f,
    // 课程格：不缩放不抬起
    cellPressSpec = snap(), cellPressScale = 1f, cellLiftDp = 0.dp,
    // 入场：无错峰、无位移、直接可见（仅保留整体淡入）
    entranceStaggerMs = 0, entranceSlideDp = 0.dp,
    entranceStaggerCapMs = 0, entranceInitialAlpha = 1f,
    // 触摸指示：只保留极短的颜色变化
    touchExpandMs = 90, touchFadeMs = 120,
    // 面板 / 对话框 / Tab：短促溶解
    tabIndicatorMs = 120, tabIconMs = 120,
    sheetEnterMs = 160, sheetExitMs = 120, sheetScrimMs = 160, sheetSlideDp = 0.dp,
    dialogEnterMs = 140, dialogExitMs = 110,
    resizeDurationMs = 0,
    // 颜色 / 状态渐变属于"无位移"的溶解，保留（无障碍规范亦允许）
    statusFadeMs = 260,
    pulseDurationMs = 0,
)

private fun ThemeMotionProfile.reduced(): ThemeMotionProfile = copy(
    // 通透的 SCALE 按压含形变 ⇒ 降级为 pure highlight；柔绘 / 书卷本就是无位移反馈，保留
    pressMode = if (pressMode == MotionPressMode.SCALE) MotionPressMode.COLOR_DARKEN else pressMode,
    navMode = NavMotionMode.FADE_UP,
)

/**
 * 已解析的全局动效配置：风格 + 叠加了主题覆盖与分组开关后的令牌 + 关闭分组集合。
 * 调用点读 [LocalAppMotion].current 得到本对象，用 [tokens] 取参数、用 [profile] 取
 * 主题档位、用 [isEnabled] 判断某组是否需要动画。
 */
data class AppMotion(
    val style: AnimationStyle,
    val tokens: MotionTokens,
    val disabledGroups: Set<AnimationGroup>,
    val profile: ThemeMotionProfile,
    /** 是否开启「减弱动态效果」（无障碍降级）。 */
    val reduceMotion: Boolean = false,
) {
    fun isEnabled(group: AnimationGroup): Boolean = group !in disabledGroups && !reduceMotion
}

/**
 * 把令牌按「关闭的分组」降级为瞬切：duration→0、弹簧→snap()、缩放→1、位移→0。
 */
private fun MotionTokens.gatedBy(disabledGroups: Set<AnimationGroup>): MotionTokens = copy(
    // NAV_TRANSITION
    navDurationMs = if (AnimationGroup.NAV_TRANSITION in disabledGroups) 0 else navDurationMs,
    navOffsetDp = if (AnimationGroup.NAV_TRANSITION in disabledGroups) 0.dp else navOffsetDp,
    navExitDurationMs = if (AnimationGroup.NAV_TRANSITION in disabledGroups) 0 else navExitDurationMs,
    navTrailFraction = if (AnimationGroup.NAV_TRANSITION in disabledGroups) 0f else navTrailFraction,
    // BAR_HIDE
    hideDurationMs = if (AnimationGroup.BAR_HIDE in disabledGroups) 0 else hideDurationMs,
    // GLASS_FLOATING
    emphasisScaleSpec = if (AnimationGroup.GLASS_FLOATING in disabledGroups) snap() else emphasisScaleSpec,
    emphasisFadeSpec = if (AnimationGroup.GLASS_FLOATING in disabledGroups) snap() else emphasisFadeSpec,
    emphasisInitialScale = if (AnimationGroup.GLASS_FLOATING in disabledGroups) 1f else emphasisInitialScale,
    pressSpec = if (AnimationGroup.GLASS_FLOATING in disabledGroups) snap() else pressSpec,
    pressScale = if (AnimationGroup.GLASS_FLOATING in disabledGroups) 1f else pressScale,
    // COURSE_CELL
    cellPressSpec = if (AnimationGroup.COURSE_CELL in disabledGroups) snap() else cellPressSpec,
    cellPressScale = if (AnimationGroup.COURSE_CELL in disabledGroups) 1f else cellPressScale,
    cellLiftDp = if (AnimationGroup.COURSE_CELL in disabledGroups) 0.dp else cellLiftDp,
    cardPressMs = if (AnimationGroup.COURSE_CELL in disabledGroups) 0 else cardPressMs,
    cardReleaseMs = if (AnimationGroup.COURSE_CELL in disabledGroups) 0 else cardReleaseMs,
    // BOTTOM_SHEET
    sheetEnterMs = if (AnimationGroup.BOTTOM_SHEET in disabledGroups) 0 else sheetEnterMs,
    sheetExitMs = if (AnimationGroup.BOTTOM_SHEET in disabledGroups) 0 else sheetExitMs,
    sheetScrimMs = if (AnimationGroup.BOTTOM_SHEET in disabledGroups) 0 else sheetScrimMs,
    sheetSlideDp = if (AnimationGroup.BOTTOM_SHEET in disabledGroups) 0.dp else sheetSlideDp,
    // DIALOG
    dialogEnterMs = if (AnimationGroup.DIALOG in disabledGroups) 0 else dialogEnterMs,
    dialogExitMs = if (AnimationGroup.DIALOG in disabledGroups) 0 else dialogExitMs,
    // TAB_SWITCH
    tabIndicatorMs = if (AnimationGroup.TAB_SWITCH in disabledGroups) 0 else tabIndicatorMs,
    tabIconMs = if (AnimationGroup.TAB_SWITCH in disabledGroups) 0 else tabIconMs,
    // PAGE_ENTRANCE 不在此归零：入场时长/错峰/位移同时被「周翻页扫光」复用，
    // 归零会误杀扫光。入场调用点已用 motion.isEnabled(PAGE_ENTRANCE) 自行门控，
    // 扫光则只看 WEEK_PAGER——两组互不牵连。错峰上限同理由调用点读取。
)

/**
 * 解析最终动效配置：风格令牌 → 主题覆盖 → 减弱动效 → 分组开关。
 *
 * @param preset 当前主题预设，决定动效语言（[themeMotionProfile] + 时长分档）。
 * @param reduceMotion 「减弱动态效果」开关，true 时位移/缩放/错峰全部归零。
 * @param speed 「动效速度」倍率（越高越快），统一缩放全部毫秒级时长。
 */
fun resolveMotion(
    style: AnimationStyle,
    disabledGroups: Set<AnimationGroup>,
    preset: AppThemePreset = AppThemePreset.default,
    reduceMotion: Boolean = false,
    speed: MotionSpeed = MotionSpeed.STANDARD,
): AppMotion {
    val profile = themeMotionProfile(preset)
    var tokens = style.tokens.withThemePreset(preset)
    if (reduceMotion) {
        tokens = tokens.reduced()
    }
    tokens = tokens.speedScaled(speed.factor)
    tokens = tokens.gatedBy(disabledGroups)
    return AppMotion(
        style = style,
        tokens = tokens,
        disabledGroups = disabledGroups,
        profile = if (reduceMotion) profile.reduced() else profile,
        reduceMotion = reduceMotion,
    )
}

/**
 * 当前生效的全局动效配置。由 `ShangKeScheduleTheme` 从
 * `AppSettingsModel.animationStyle` + `disabledAnimationGroups` + `themePreset` +
 * `reduceMotionEnabled` + `motionSpeed` 注入；调节入口在「外观与样式 → 个性化显示 → 动画效果」。
 */
val LocalAppMotion = compositionLocalOf { resolveMotion(AnimationStyle.GLASS, emptySet()) }
