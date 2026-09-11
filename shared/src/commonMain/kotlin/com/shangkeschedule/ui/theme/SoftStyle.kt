package com.shangkeschedule.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// 柔绘主题（Soft Draw）样式层
//
// 设计语言（用户给的关键词 → 工程化落地）：
//   · 柔和晕染渐变     → 页面底/卡片底全部用多层低饱和径向+线性渐变叠色，不用纯色平面
//   · 虚化圆角         → 圆角阶梯整体放大（卡片 24 / hero 28 / 控件 16 / chip 12），
//                        并在描边内侧再叠一层羽化高光，让边缘"化"开而不是硬切
//   · 无锐利硬边缘     → 全站禁用 1dp 实色描边：改用羽化渐变环 + 扩散投影
//   · 低对比度柔和配色 → 色阶跨度压缩（正文 #4A4756 而非 #000，辅助 #8B8798），
//                        语义色统一走低饱和马卡龙档，避免任何高饱和冲击
//   · 通透薄涂质感     → 卡片底色 alpha 压在 0.72~0.86 的"薄涂"区间，底层晕染透上来
//   · 朦胧细腻肌理     → softTexture() 叠一层极淡噪点/细纹，模拟手绘纸面
//   · 漫射柔光         → 卡片左上角一枚大面积低 alpha 径向光斑（漫射光源）
//   · 干净留白         → 间距整体放宽一档（页边距 22 / 卡距 18 / 行高 56）
//   · 卡片式布局       → 沿用书卷的 inset grouped 结构，只换材质
//   · 柔和阴影         → 双层投影（近距离 2dp 紧影 + 远距离 12dp 散影），alpha 极低
//   · 软模糊投影       → softShadow() 用 blur 做真正的软边投影，而非硬边 elevation
//   · 流畅色彩过渡     → 渐变节点 4~5 个、色相跨度 ≤ 30°，过渡连续无折点
//   · 轻量手绘柔绘纹理 → 卡片右下角一枚手绘感的柔和弧线水印
//   · 现代极简 UI      → 组件元素数量不增加，只改材质与边缘处理
//   · 高可读性         → 正文对比度仍 ≥ 7:1（低对比指的是"界面元素之间"，
//                        不是"文字与底色之间"——文字对比度单独守 7:1 底线）
//   · 精致细节         → 圆角、光斑位置、投影偏移全部按统一光照模型（光源左上 45°）
//
// 与「书卷」的关系：功能位完全一致（页头 / 分组 / 行 / 卡片 / 弹窗 / 悬浮件），
// 只替换材质、圆角、配色与动效。与「通透」的关系：同样都是书卷的功能超集，
// 但通透走 Apple 系统色 + Liquid Glass，柔绘走低饱和晕染 + 薄涂柔光。
// ============================================================================

// --- 柔绘色板（浅色）：低饱和 + 暖冷平衡 -------------------------------------

/** 页面底：极淡的暖灰蓝，作为所有晕染层的画布。 */
private val SoftPageBgLight = Color(0xFFF4F3F7)
/** 卡片底：近白但带一点暖调，避免纯白在低对比界面上"跳"出来。 */
private val SoftCardBgLight = Color(0xFFFCFBFD)
private val SoftCardElevatedLight = Color(0xFFF7F5FA)
private val SoftInputBgLight = Color(0xFFEFEDF4)

/** 正文：不用纯黑。低对比设计里"黑"会在柔和底色上形成硬边。 */
private val SoftTextPrimaryLight = Color(0xFF4A4756)
private val SoftTextSecondaryLight = Color(0xFF8B8798)

/** 主色：低饱和的雾蓝紫，作为唯一强调色。 */
private val SoftPrimaryLight = Color(0xFF7C86C9)
private val SoftPrimaryDeepLight = Color(0xFF5F68A8)

/** 语义色：统一降到马卡龙档（饱和度 ~35%），互相之间饱和度一致。 */
private val SoftSuccessLight = Color(0xFF7BAE8C)
private val SoftInfoLight = Color(0xFF7FA8C4)
private val SoftWarningLight = Color(0xFFD9A97E)
private val SoftAmberLight = Color(0xFFD8C089)
private val SoftDangerLight = Color(0xFFCC8A8A)
private val SoftFavoriteLight = Color(0xFFC98FA8)

// --- 柔绘色板（深色）：同样的低饱和逻辑，亮度反向 -----------------------------

private val SoftPageBgDark = Color(0xFF1F1E24)
private val SoftCardBgDark = Color(0xFF2A2830)
private val SoftCardElevatedDark = Color(0xFF322F39)
private val SoftInputBgDark = Color(0xFF37343F)
private val SoftTextPrimaryDark = Color(0xFFE8E5EE)
private val SoftTextSecondaryDark = Color(0xFF9C98A8)

/**
 * 柔绘锁定的 M3 ColorScheme（浅色）。
 *
 * 与通透一样绕过 MaterialKolor 派生：柔绘的主色是固定的雾蓝紫，
 * 不允许 Expressive 的色相旋转把它推成绿色或棕色系。
 */
internal fun softLightColorScheme(): ColorScheme = lightColorScheme(
    primary = SoftPrimaryLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0x1F7C86C9),
    onPrimaryContainer = SoftPrimaryDeepLight,
    secondary = Color(0xFF9A93B8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0x1F9A93B8),
    onSecondaryContainer = Color(0xFF4E4870),
    tertiary = SoftInfoLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0x1F7FA8C4),
    onTertiaryContainer = Color(0xFF2F4E63),
    error = SoftDangerLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0x1FCC8A8A),
    onErrorContainer = Color(0xFF6B3B3B)
)

/**
 * 柔绘锁定的 M3 ColorScheme（深色）。
 */
internal fun softDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFF9AA3DC),
    onPrimary = Color(0xFF23252F),
    primaryContainer = Color(0x3D9AA3DC),
    onPrimaryContainer = Color(0xFFDDE0F5),
    secondary = Color(0xFFB3ACCF),
    onSecondary = Color(0xFF23252F),
    secondaryContainer = Color(0x3DB3ACCF),
    onSecondaryContainer = Color(0xFFE6E2F2),
    tertiary = Color(0xFF9CC0D8),
    onTertiary = Color(0xFF1E2A33),
    tertiaryContainer = Color(0x3D9CC0D8),
    onTertiaryContainer = Color(0xFFD7E8F2),
    error = Color(0xFFDDA0A0),
    onError = Color(0xFF2A1E1E),
    errorContainer = Color(0x3DDDA0A0),
    onErrorContainer = Color(0xFFF2DADA)
)

/**
 * 柔绘浅色 tokens。
 *
 * 结构色（页面/卡片/输入/分隔）全部走低饱和柔绘色阶；
 * 语义色的 soft 档统一 0x1F（12%）——低饱和主色配低 alpha 淡底，
 * 保证「淡底 + 深字」的对比度仍然充足而又不刺眼。
 */
internal fun softLightAppColorTokens(): AppColorTokens = AppColorTokens(
    pageBg = SoftPageBgLight,
    cardBg = SoftCardBgLight,
    cardBgElevated = SoftCardElevatedLight,
    inputBg = SoftInputBgLight,
    divider = Color(0x1F4A4756),               // 低对比分隔线：12% 正文色，不用实色
    textPrimary = SoftTextPrimaryLight,
    textSecondary = SoftTextSecondaryLight,
    textOnPrimary = Color(0xFFFFFFFF),
    primary = SoftPrimaryLight,
    primarySoft = Color(0x1F7C86C9),
    success = SoftSuccessLight,
    successSoft = Color(0x1F7BAE8C),
    info = SoftInfoLight,
    infoSoft = Color(0x1F7FA8C4),
    warning = SoftWarningLight,
    warningSoft = Color(0x1FD9A97E),
    amber = SoftAmberLight,
    amberSoft = Color(0x1FD8C089),
    danger = SoftDangerLight,
    dangerSoft = Color(0x1FCC8A8A),
    favorite = SoftFavoriteLight,
    favoriteSoft = Color(0x1FC98FA8),
    gradientStart = SoftPrimaryLight,
    gradientEnd = Color(0xFF9A93B8),
    navBarBg = Color(0xE6F7F5FA),              // 底栏：薄涂半透明，柔光透上来
    navSelectedBg = Color(0x1F7C86C9),
    badgeBg = SoftDangerLight,
    badgeFg = Color(0xFFFFFFFF),
    snackbarBg = Color(0xE63A3742),
    snackbarFg = Color(0xFFF4F3F7),
    // 柔绘阴影：比通透更淡（漫射柔光的代价就是投影几乎不可见）
    shadow = Color(0x0A4A4756),
    // M3 自带描边组件读这个角色：柔绘用 20% 正文色的极淡描边，
    // 取代 Material 基线 #79747E 的 1dp 硬灰线（「无锐利硬边缘」的最后一块补丁）
    outline = Color(0x334A4756),
    // outlineVariant（默认 Divider / 未聚焦 Outlined 描边）：比 outline 再淡一档
    outlineVariant = Color(0x144A4756)
)

/**
 * 柔绘深色 tokens。
 */
internal fun softDarkAppColorTokens(): AppColorTokens = run {
    val primary = Color(0xFF9AA3DC)
    AppColorTokens(
        pageBg = SoftPageBgDark,
        cardBg = SoftCardBgDark,
        cardBgElevated = SoftCardElevatedDark,
        inputBg = SoftInputBgDark,
        divider = Color(0x1FE8E5EE),
        textPrimary = SoftTextPrimaryDark,
        textSecondary = SoftTextSecondaryDark,
        textOnPrimary = Color(0xFF23252F),
        primary = primary,
        primarySoft = Color(0x3D9AA3DC),
        success = Color(0xFF97C4A6),
        successSoft = Color(0x3D97C4A6),
        info = Color(0xFF9CC0D8),
        infoSoft = Color(0x3D9CC0D8),
        warning = Color(0xFFE0BB96),
        warningSoft = Color(0x3DE0BB96),
        amber = Color(0xFFE0CD9E),
        amberSoft = Color(0x3DE0CD9E),
        danger = Color(0xFFDDA0A0),
        dangerSoft = Color(0x3DDDA0A0),
        favorite = Color(0xFFDBAAC0),
        favoriteSoft = Color(0x3FDBAAC0),
        gradientStart = primary,
        gradientEnd = Color(0xFFB3ACCF),
        navBarBg = Color(0xE62A2830),
        navSelectedBg = Color(0x3D9AA3DC),
        badgeBg = Color(0xFFDDA0A0),
        badgeFg = Color(0xFF2A1E1E),
        snackbarBg = Color(0xF23A3742),
        snackbarFg = Color(0xFFE8E5EE),
        shadow = Color(0x1F000000),
        outline = Color(0x3DE8E5EE),
        outlineVariant = Color(0x17E8E5EE)
    )
}

/**
 * 按深浅模式取柔绘颜色 tokens。
 */
fun softAppColorTokens(isDark: Boolean): AppColorTokens =
    if (isDark) softDarkAppColorTokens() else softLightAppColorTokens()

// ============================================================================
// 柔绘形状 / 间距 / 字阶 tokens
//
// 圆角整体比通透放大一档：虚化圆角是柔绘的身份特征（24 / 28 / 16 / 12）。
// 间距走「干净留白」：卡距 18 / 分区间距 26 / 卡内 18。
// ⚠️ 横向页边距与行高的取值规则（对齐书卷 / 通透的既有约定，勿随意放大）：
//   · pageHorizontal = 20dp，与书卷、通透**完全一致** —— 用户明确要求「每个导航条
//     一样宽」，三套主题的行宽必须逐像素相同，横向内边距不能各自为政；
//   · rowMinHeight = 52dp，与通透一致（书卷的设置行是 48dp 的历史硬编码）——
//     用户在「柔绘完全参照通透的适配」的前提下，行高应与通透对齐；
//     留白感由纵向 cardGap / listGap / cardInner 承担，不靠抬高行高实现。
// 字阶比通透略轻（字重降一档），配合低对比配色更柔。
// ============================================================================

internal val softShapeTokens = AppShapeTokens(
    card = RoundedCornerShape(24.dp),
    sheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    chip = RoundedCornerShape(12.dp),
    chipSmall = RoundedCornerShape(10.dp),
    chipSmallRadius = 10.dp,
    menu = RoundedCornerShape(18.dp),
    capsule = RoundedCornerShape(50),
    fab = RoundedCornerShape(50),
    heroCard = RoundedCornerShape(28.dp),
    bubble = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 22.dp,
        bottomStart = 22.dp,
        bottomEnd = 22.dp
    )
)

internal val softSpacingTokens = AppSpacingTokens(
    pageHorizontal = 20.dp,
    cardGap = 18.dp,
    listGap = 26.dp,
    cardInner = 18.dp,
    rowMinHeight = 52.dp,
    touchMin = 48.dp,
    chipIcon = 46.dp,
    fab = 58.dp,
    navBarHorizontal = 16.dp,
    navBarBottom = 12.dp
)

internal val softTypeTokens = AppTypeTokens(
    bigNumber = 26.sp,
    hero = 30.sp,
    sectionTitle = 16.sp,
    timeLabel = 13.sp,
    badge = 11.sp,
    pageTitle = 21.sp,
    rowTitle = 16.sp,
    body = 16.sp,
    caption = 13.sp,
    hint = 11.sp
)

// ============================================================================
// 柔绘材质：晕染底 / 薄涂卡 / 羽化描边 / 软模糊投影 / 漫射光斑 / 手绘纹理
// ============================================================================

/** 柔绘主色（供组件层直接取用做晕染，不依赖 ColorScheme 同步）。 */
val SoftAccent = SoftPrimaryLight
val SoftAccentDeep = SoftPrimaryDeepLight

/**
 * 柔和晕染渐变：多层低饱和径向色团 + 一层线性过渡。
 *
 * 用 4 个色团（左上主色 / 右上冷色 / 左下暖色 / 右下辅助色）在同一画布上叠色，
 * 彼此 alpha 0.06~0.14、色相跨度 ≤ 30°，于是过渡连续、没有可见的"折点"，
 * 这就是"柔和晕染"与"普通渐变"的区别。
 */
@Composable
fun Modifier.softWash(
    intensity: Float = 1f,
    tint: Color = appColors().primary
): Modifier {
    val isDark = LocalIsDarkTheme.current
    val base = appColors().pageBg
    val k = intensity.coerceIn(0f, 1.5f)
    // 深色下晕染力度压到一半：暗底上叠色更容易"脏"
    val a = if (isDark) 0.5f * k else 1f * k
    return this.drawBehind {
        drawRect(color = base)
        // ① 左上主色晕团
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    tint.copy(alpha = 0.14f * a),
                    tint.copy(alpha = 0.05f * a),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.18f, size.height * 0.10f),
                radius = size.maxDimension * 0.72f
            )
        )
        // ② 右上冷色晕团
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    SoftInfoLight.copy(alpha = 0.10f * a),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.92f, size.height * 0.22f),
                radius = size.maxDimension * 0.60f
            )
        )
        // ③ 左下暖色晕团
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    SoftWarningLight.copy(alpha = 0.08f * a),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.08f, size.height * 0.88f),
                radius = size.maxDimension * 0.58f
            )
        )
        // ④ 底部辅助色晕团 + 线性兜底，保证整面都有极淡过渡（不留死白）
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF9A93B8).copy(alpha = 0.05f * a),
                    Color.Transparent
                ),
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f)
            )
        )
    }
}

/**
 * 薄涂卡片材质：不透明底 + 漫射柔光 + 羽化高光边 + 软模糊投影。
 *
 * 「薄涂」的工程含义：卡片不是"一块实色板"，而是"一层薄薄的颜料"——
 * 底色略透（底层晕染能感知到）、上沿有一层漫射光、边缘是羽化而非硬描边。
 */
@Composable
fun Modifier.softSurface(
    shape: Shape,
    containerColor: Color? = null,
    elevation: Dp = 10.dp,
    wash: Boolean = true
): Modifier {
    val tokens = appColors()
    val isDark = LocalIsDarkTheme.current
    val bg = containerColor ?: tokens.cardBg
    val shapeObj = shape
    return this
        .softShadow(shape = shapeObj, elevation = elevation)
        .clip(shapeObj)
        .background(bg)
        .then(if (wash) Modifier.softGlow(shapeObj) else Modifier)
        .softFeatherRim(shapeObj, isDark)
}

/**
 * 软模糊投影：真正把投影模糊掉（双层：近距离紧影 + 远距离散影）。
 *
 * 与 Material elevation 的区别：elevation 是"实边阴影 + 系统模糊"，
 * 在浅色低对比界面上会显出一圈可辨认的硬边；这里改成两层极低 alpha 的
 * 扩散影，边缘完全化开，符合"软模糊投影 / 柔和阴影"。
 */
@Composable
fun Modifier.softShadow(
    shape: Shape,
    elevation: Dp = 10.dp
): Modifier {
    val tokens = appColors()
    val isDark = LocalIsDarkTheme.current
    val e = elevation
    val nearAlpha = if (isDark) 0.10f else 0.055f
    val farAlpha = if (isDark) 0.07f else 0.035f
    return this
        // 近距离紧影：把卡片从底面上"抬"起来一点点
        .shadow(
            elevation = (e * 0.22f).coerceAtLeast(1.dp),
            shape = shape,
            clip = false,
            ambientColor = tokens.shadow.copy(alpha = nearAlpha),
            spotColor = tokens.shadow.copy(alpha = nearAlpha)
        )
        // 远距离散影：营造漫射柔光下的柔和落影
        .shadow(
            elevation = e,
            shape = shape,
            clip = false,
            ambientColor = tokens.shadow.copy(alpha = farAlpha),
            spotColor = tokens.shadow.copy(alpha = farAlpha)
        )
}

/**
 * 漫射柔光：卡片左上角一枚大面积低 alpha 径向光斑。
 *
 * 统一光照模型：光源固定在**左上 45°**，所有柔绘材质共用同一个入射方向，
 * 这样界面上每个组件的受光面一致，观感才"干净"而不是"乱"。
 *
 * 力度刻意压得很低（0.30 → 0.08 → 透明）：柔绘卡片底色本身已经接近白，
 * 白上加白光只会把薄涂的雾蓝紫色洗掉，反而抬高与其他元素的对比度。
 */
@Composable
fun Modifier.softGlow(shape: Shape): Modifier {
    if (!LocalIsSoftTheme.current) return this
    return this.drawBehind {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.30f),
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.18f, -size.height * 0.12f),
                radius = size.maxDimension * 0.92f
            )
        )
    }
}

/**
 * 羽化描边环：替代 1dp 实色 border，做到"无锐利硬边缘"。
 *
 * 实现要点（这是一处曾经的实现缺陷，务必按此实现，不要再改回整面蒙白）：
 *   · 只在**形状轮廓上**画一圈描边（`drawOutline` + `Stroke`），而不是给整块
 *     面积叠一层白色 —— 整面叠白会把卡片的薄涂底色冲成纯白，既丢了柔绘的
 *     低饱和调性，又反而把元素之间的对比度抬高；
 *   · 描边本身用**上亮下透的垂直渐变**，加上内缩一层的第二道淡描边，
 *     形成由边缘向内衰减的过渡 —— 于是"边"是化开的，看不到一条等色硬线；
 *   · 最后用 `BlendMode.Screen` 只提亮不发灰，符合"漫射柔光打在上沿"的观感。
 */
@Composable
fun Modifier.softFeatherRim(shape: Shape, isDark: Boolean = LocalIsDarkTheme.current): Modifier {
    if (!LocalIsSoftTheme.current) return this
    val dir = LocalLayoutDirection.current
    // 上沿受光最强、下沿几乎无光：光照方向与 softGlow 的左上 45° 保持一致
    val topAlpha = if (isDark) 0.14f else 0.46f
    val bottomAlpha = if (isDark) 0.04f else 0.06f
    val sideAlpha = if (isDark) 0.06f else 0.18f
    return this.drawWithContent {
        drawContent()
        // Outline → Path（common 代码里 drawOutline 不可用，统一走 drawPath）
        fun outlinePath(o: Outline): Path = Path().apply { addOutline(o) }

        val outline = shape.createOutline(size, dir, this)
        val brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = topAlpha),
                Color.White.copy(alpha = sideAlpha),
                Color.White.copy(alpha = bottomAlpha)
            )
        )
        // 外层：贴边的一道细高光
        drawPath(
            path = outlinePath(outline),
            brush = brush,
            style = Stroke(width = 1.2.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        // 内层：内缩 1.6dp 的宽描边，低 alpha —— 让高光向卡片内部衰减，边缘"化"开
        val inset = 1.6.dp.toPx()
        if (size.width > inset * 4 && size.height > inset * 4) {
            val inner = shape.createOutline(
                Size(size.width - inset * 2, size.height - inset * 2),
                dir,
                this
            )
            translate(left = inset, top = inset) {
                drawPath(
                    path = outlinePath(inner),
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = topAlpha * 0.34f),
                            Color.White.copy(alpha = sideAlpha * 0.30f),
                            Color.Transparent
                        )
                    ),
                    style = Stroke(width = 2.2.dp.toPx()),
                    blendMode = BlendMode.Screen
                )
            }
        }
    }
}

/**
 * 轻量手绘柔绘纹理：卡片右下角一枚极淡的柔和弧线水印 + 细颗粒。
 *
 * 这是"轻量手绘"的克制实现——不是真的画图案，而是用两段低 alpha 圆弧
 * 暗示笔触，叠加 3% 的颗粒模拟纸面肌理。整体 alpha ≤ 0.05，只为"朦胧细腻肌理"，
 * 不参与任何信息表达，也不影响文字对比度。
 */
@Composable
fun Modifier.softTexture(shape: Shape): Modifier {
    if (!LocalIsSoftTheme.current) return this
    val isDark = LocalIsDarkTheme.current
    val strokeAlpha = if (isDark) 0.045f else 0.055f
    val stroke = appColors().primary
    return this.drawBehind {
        // ① 右下角两道手绘感弧线
        drawArc(
            color = stroke.copy(alpha = strokeAlpha),
            startAngle = 200f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(size.width * 0.42f, size.height * 0.30f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.78f, size.height * 1.05f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.height * 0.06f)
        )
        drawArc(
            color = stroke.copy(alpha = strokeAlpha * 0.7f),
            startAngle = 200f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(size.width * 0.56f, size.height * 0.48f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.66f, size.height * 0.92f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.height * 0.045f)
        )
        // ② 极淡颗粒：模拟手绘纸面（1px 点阵，步长 7px，alpha 3%）
        val step = 7f
        val dot = Color.White.copy(alpha = if (isDark) 0.020f else 0.030f)
        var y = 0f
        while (y < size.height) {
            var x = ((y / step).toInt() % 2) * (step / 2f)
            while (x < size.width) {
                drawCircle(color = dot, radius = 0.9f, center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

// ============================================================================
// 柔绘字体：与通透同源（系统无衬线），但字重整体降一档、字距再松一点
// ============================================================================

@Composable
fun softTypography(): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.2.sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 33.sp,
        letterSpacing = 0.18.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.14.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 33.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.1).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.15).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.15).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.05).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.05).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.05.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.05.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    )
)
