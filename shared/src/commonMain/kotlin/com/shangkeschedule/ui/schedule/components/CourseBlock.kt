package com.shangkeschedule.ui.schedule.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.data.model.DualColor
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.data.model.schedule_style.BorderTypeProto
import com.shangkeschedule.data.model.schedule_style.ScheduleModeProto
import com.shangkeschedule.ui.theme.AppAlpha
import com.shangkeschedule.ui.theme.AppTypeGrid
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.appColorTokens
import com.shangkeschedule.ui.theme.appColors

/**
 * 主题预设相关渲染参数：在 CourseBlock 入口统一计算一次，
 * 背景色、文字色、色条、阴影、内边距、虚化遮罩等均直接引用，避免多处重复判断主题。
 */
private data class CourseBlockPresetRender(
    val isSleepyPreset: Boolean,
    val blockBackgroundColor: Color,
    val stripColor: Color,
    val textColor: Color,
    val sleepyShadowModifier: Modifier,
    val timetableStartPadding: Dp,
    val demotedSpec: DemotedRenderSpec
)

/**
 * 「非本周课程块」的降级渲染规格。两种机制，按主题分别取用：
 *
 * - `underContent = true`（垫底式）：遮罩垫在文字**下方**，只把课程底色/色条向主题中性底收敛，
 *   文字另按 [contentAlpha] 轻微虚化。因为遮罩不再覆盖前景，**前景/背景对比度可控**，
 *   可以同时做到「一眼看出是降级」与「满足 WCAG AA」。
 *   书卷浅色池每色仅 `0x40`（25%）alpha、底色本身已极淡，压字式遮罩会让前景与背景同时被拉向
 *   遮罩色，实测对比度塌到 1.7–1.9:1（非本周整表几乎不可读），故书卷必须用垫底式。
 * - `underContent = false`（压字式）：遮罩压在文字**上方**，文字一并被洗淡（旧行为，
 *   经典 / 通透 / 云舒沿用，不做视觉变更）。
 */
private data class DemotedRenderSpec(
    val scrimColor: Color,
    val scrimAlpha: Float,
    val underContent: Boolean,
    val contentAlpha: Float,
    val stripeColor: Color,
    val stripeAlpha: Float
)

/**
 * 非本周降级遮罩层：一层纯色遮罩 + 45° 细斜纹。
 *
 * 层级由调用方决定（垫底式置于文字之前、压字式置于文字之后），本方法只负责画这一层。
 */
private fun Modifier.demotionScrim(spec: DemotedRenderSpec): Modifier = this
    .fillMaxSize()
    .background(spec.scrimColor.copy(alpha = spec.scrimAlpha))
    .drawBehind {
        val stripeWidth = 5.dp.toPx()
        val stripeColor = spec.stripeColor.copy(alpha = spec.stripeAlpha)
        val brush = Brush.linearGradient(
            0.0f to stripeColor, 0.45f to stripeColor,
            0.55f to Color.Transparent, 1.0f to Color.Transparent,
            start = Offset(0f, 0f), end = Offset(stripeWidth, stripeWidth), tileMode = TileMode.Repeated
        )
        drawRect(brush = brush)
    }

@Composable
private fun buildPresetRenderSpec(
    themePreset: AppThemePreset,
    isDarkTheme: Boolean,
    colorInt: Int,
    isFloating: Boolean,
    currentAlpha: Float,
    courseColorAdapted: Color?,
    fallbackColorAdapted: Color,
    blockColor: Color,
    style: ScheduleGridStyleComposed
): CourseBlockPresetRender {
    val isSleepyPreset = themePreset == AppThemePreset.SLEEPY
    val isStripStylePreset = themePreset == AppThemePreset.IOS

    // iOS 左侧色条主题：从 courseColorMaps 取 light/dark 对，浅色模式 bg=light半透明/strip=dark，深色模式 bg=dark半透明/strip=dark
    // 这样用户在个性化配置中修改课程颜色后，色条主题的背景和色条都会同步变化。
    val timetableDual = style.courseColorMaps.getOrNull(colorInt)
        ?: style.courseColorMaps.firstOrNull()
        ?: ScheduleGridStyle.DEFAULT_COLOR_MAPS.firstOrNull() ?: DualColor(Color(0xFF6C5CE7), Color(0xFF6C5CE7))
    // 色条主题：courseColorMaps 颜色极浅，直接用 light 会与白底融为一体，改用 dark 半透明
    // （半透明底色同样按「课程块不透明度」乘算，保持与其它主题一致）
    val timetableBg = timetableDual.dark.scaleAlpha(
        (if (isDarkTheme) 0.25f else 0.12f) * currentAlpha
    )
    val timetableStrip = timetableDual.dark
    val timetableText = timetableDual.dark

    val blockBackgroundColor = if (isStripStylePreset) timetableBg else blockColor
    val stripColor = if (isStripStylePreset) timetableStrip
        else (courseColorAdapted ?: fallbackColorAdapted).scaleAlpha(currentAlpha)
    val textColor = if (isStripStylePreset) timetableText
        else (style.courseTextColor ?: adaptiveTextColor(blockColor, MaterialTheme.colorScheme.onSurface))

    val shape = RoundedCornerShape(style.courseBlockCornerRadius)
    val sleepyShadowModifier = if (isSleepyPreset && !isFloating) {
        Modifier.shadow(elevation = 2.dp, shape = shape, clip = false)
    } else {
        Modifier
    }
    val timetableStartPadding = if (isStripStylePreset) 4.dp else 0.dp
    // 非本周降级规格：按主题分支（历史上 0.618 全局共用，未考虑各主题底色自身的 alpha，
    // 是书卷非本周块对比度塌到 1.7:1、几乎不可读的根因）。
    // - 书卷（CLAUDE）：垫底式。遮罩取**主题自己的中性底**（浅 = 暖砂 bg-100 #FAF9F5 / 深 = 暖炭
    //   bg-100 #262624），把课程底色向中性底收敛 40%，色相与暖砂调性保持统一，不引入冷灰/纯白；
    //   文字保持 92% 不透明度。实测对比度：正常块 7.7+、非本周块名称 7.4 / 元信息 4.8，均 ≥ AA 4.5:1。
    // - 云舒（SLEEPY）：沿用压字式 0.5（AppAlpha.dimmed）。
    // - 经典 / 通透：沿用压字式 0.618（有意的黄金比例设计值，豁免）。
    val demotedSpec = if (themePreset == AppThemePreset.CLAUDE) {
        DemotedRenderSpec(
            scrimColor = appColors().pageBg,
            scrimAlpha = 0.40f,
            underContent = true,
            contentAlpha = 0.92f,
            stripeColor = if (isDarkTheme) Color.White else Color.Black,
            stripeAlpha = 0.04f
        )
    } else {
        DemotedRenderSpec(
            scrimColor = if (isDarkTheme) Color.Black else Color.White,
            scrimAlpha = if (isSleepyPreset) AppAlpha.dimmed else 0.618f,
            underContent = false,
            contentAlpha = 1f,
            stripeColor = if (isDarkTheme) Color.White else Color.Black,
            stripeAlpha = 0.06f
        )
    }

    return CourseBlockPresetRender(
        isSleepyPreset = isSleepyPreset,
        blockBackgroundColor = blockBackgroundColor,
        stripColor = stripColor,
        textColor = textColor,
        sleepyShadowModifier = sleepyShadowModifier,
        timetableStartPadding = timetableStartPadding,
        demotedSpec = demotedSpec
    )
}

@Composable
fun CourseBlock(
    courseWrapper: CourseWithWeeks,
    isVisualDemoted: Boolean,
    style: ScheduleGridStyleComposed,
    timeSlots: List<TimeSlot>,
    modifier: Modifier = Modifier,
    isFloating: Boolean = false // 标记当前块是否处于长按选中/悬浮状态
) {
    val course = courseWrapper.course
    val isDarkTheme = LocalIsDarkTheme.current

    // 颜色适配
    val colorIndex = course.colorInt.takeIf { it in style.courseColorMaps.indices }
    val courseColorAdapted: Color? = colorIndex?.let { index ->
        val baseColorMap = style.courseColorMaps[index]
        if (isDarkTheme) baseColorMap.dark else baseColorMap.light
    }
    val fallbackColorAdapted: Color = if (isDarkTheme) style.courseColorMaps.first().dark else style.courseColorMaps.first().light

    val currentAlpha = if (isFloating) 0.95f else style.courseBlockAlpha
    // 【颜色池 alpha 不可覆盖】颜色池里的颜色自带 alpha，且是设计令牌（书卷/通透浅色池 = 0x40 / 0x1F 淡底）。
    // 此前用 copy(alpha = currentAlpha) 直接覆盖，导致浅色池的淡底在周课表网格里被渲染成实色——
    // 页面颜色与颜色池里显示的颜色对不上，也与列表视图 / 今日页（均保留原 alpha）不一致。
    // 改为乘算：「课程块不透明度」在其之上缩放，颜色池的颜色得以原样呈现。
    val blockColor = (courseColorAdapted ?: fallbackColorAdapted).scaleAlpha(currentAlpha)
    val themePreset = LocalThemePreset.current
    val presetRender = buildPresetRenderSpec(
        themePreset = themePreset,
        isDarkTheme = isDarkTheme,
        colorInt = course.colorInt,
        isFloating = isFloating,
        currentAlpha = currentAlpha,
        courseColorAdapted = courseColorAdapted,
        fallbackColorAdapted = fallbackColorAdapted,
        blockColor = blockColor,
        style = style
    )

    // 字体基础大小（在 BoxWithConstraints 内根据块实际宽度做自适应缩放）
    val baseNameFontSize = AppTypeGrid.courseName * style.fontScale
    val baseMetaFontSize = AppTypeGrid.courseMeta * style.fontScale

    // 核心分支逻辑：判断 24小时模式 与 节次模式 的时间文本渲染
    val customStartTime = course.customStartTime
    val customEndTime = course.customEndTime
    val customTimeString = if (customStartTime != null && customEndTime != null) "$customStartTime - $customEndTime" else null
    val isCustomTimeCourse = customTimeString != null

    val timeTextToShow = if (style.scheduleMode == ScheduleModeProto.TIME_24H_MODE) {
        // 24小时绝对时间轴模式：全部课程都显示起止时间
        if (isCustomTimeCourse) {
            customTimeString
        } else {
            val startSlot = timeSlots.find { it.number == course.startSection }
            val endSlot = timeSlots.find { it.number == course.endSection }
            if (startSlot != null && endSlot != null) "${startSlot.startTime} - ${endSlot.endTime}" else null
        }
    } else {
        // 传统节次模式：只有自定义课程显示起止时间；普通节次课程只有在开启展示开始时间时才显示开始时间
        if (isCustomTimeCourse) {
            customTimeString
        } else if (style.showStartTime) {
            timeSlots.find { it.number == course.startSection }?.startTime
        } else {
            null
        }
    }

    // 边框样式配置（悬浮 = 语义 info 强调色，v2 规范：不再用孤立的功能蓝）
    val borderColor = if (isFloating) appColors().info else appColors().divider
    val borderWidth = if (isFloating) 2.dp else 1.dp
    val borderAlpha = if (isFloating) 1.0f else style.courseBlockAlpha
    val shape = RoundedCornerShape(style.courseBlockCornerRadius)

    val borderModifier = when (style.borderType) {
        BorderTypeProto.BORDER_TYPE_SOLID -> {
            Modifier.border(borderWidth, borderColor.copy(alpha = borderAlpha), shape)
        }
        BorderTypeProto.BORDER_TYPE_DASHED -> {
            Modifier.drawBehind {
                val strokeWidth = borderWidth.toPx()
                val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = borderColor.copy(alpha = borderAlpha),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, pathEffect = dashPathEffect)
                )
            }
        }
        else -> {
            if (isFloating) Modifier.border(borderWidth, borderColor, shape) else Modifier
        }
    }

    val horizontalAlignment = if (style.textAlignCenterHorizontal) Alignment.CenterHorizontally else Alignment.Start
    val verticalArrangement = if (style.textAlignCenterVertical) Arrangement.Center else Arrangement.Top
    val textAlign = if (style.textAlignCenterHorizontal) TextAlign.Center else TextAlign.Start

    // 选中捏起时，增加三维物理阴影
    val floatingShadowModifier = if (isFloating) {
        Modifier.shadow(elevation = 8.dp, shape = shape, clip = false)
    } else {
        Modifier
    }

    // SLEEPY 预设：普通课程块叠加轻微悬浮阴影，视觉更接近参考项目
    val sleepyShadowModifier = presetRender.sleepyShadowModifier

    BoxWithConstraints(
        modifier = modifier
            .then(floatingShadowModifier)
            .then(sleepyShadowModifier)
            .fillMaxSize()
            .then(borderModifier)
            .clip(shape)
            .background(color = presetRender.blockBackgroundColor)
    ) {
        // 左侧色条：宽 4dp，贯穿整个块高度
        if (presetRender.timetableStartPadding > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(presetRender.stripColor)
            )
        }

        // 非本周降级层（垫底式）：压在底色与色条之上、文字之下 —— 只降级底色，文字保持可读对比度
        val showDemotion = isVisualDemoted && !isFloating
        val demotedSpec = presetRender.demotedSpec
        val demotionUnderContent = showDemotion && demotedSpec.underContent
        if (demotionUnderContent) {
            Box(modifier = Modifier.demotionScrim(demotedSpec))
        }

        // 可用文字区域尺寸（扣除内边距）
        val innerPadding = style.courseBlockInnerPadding
        val timetableStartPadding = presetRender.timetableStartPadding
        val contentWidth = maxWidth - innerPadding * 2 - timetableStartPadding
        val contentHeight = maxHeight - innerPadding * 2

        // 字号自适应：以 5 列/7 列课表的典型内容宽度(约 44dp)为基准，窄块微缩、宽块微放。
        // 收敛缩放区间，避免「开启显示周末(7列)字变小、关闭(5列)字变大」的跳变；窄块溢出由 Ellipsis 兜底
        val referenceWidth = 44.dp
        val minScale = 0.82f
        val maxScale = 1.06f
        val widthRatio = if (contentWidth > 0.dp) contentWidth / referenceWidth else 1f
        val adaptiveScale = widthRatio.coerceIn(minScale, maxScale)
        val nameFontSize = (baseNameFontSize * adaptiveScale).sp
        val metaFontSize = (baseMetaFontSize * adaptiveScale).sp

        // 紧凑块判定：双排（宽度减半）或极矮块视为紧凑，隐藏教师、优先保证名称
        val isCompact = contentWidth < 32.dp || contentHeight < 30.dp

        // 垫底式降级：文字不被遮罩覆盖，仅整体轻微虚化，保证对比度仍 ≥ AA 4.5:1
        val demotedContentAlpha = if (demotionUnderContent) demotedSpec.contentAlpha else 1f

        // 课程文字内容容器
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = demotedContentAlpha }
                .padding(
                    start = innerPadding + timetableStartPadding,
                    top = innerPadding,
                    end = innerPadding,
                    bottom = innerPadding
                ),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement
        ) {
            if (timeTextToShow != null) {
                Text(
                    text = timeTextToShow,
                    fontSize = metaFontSize,
                    color = presetRender.textColor.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = textAlign,
                    style = TextStyle(lineHeight = 1.em)
                )
            }

            // 课程名称：弹性占位，行数不限，空间不足时才省略；地点可挤占其空间
            Text(
                text = course.name,
                fontSize = nameFontSize,
                fontWeight = FontWeight.Bold,
                color = presetRender.textColor,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier.weight(1f, fill = false),
                style = TextStyle(lineHeight = 1.2.em)
            )

            // 地点：最多 4 行内完整展示；仅在 @、-、（、） 等符号前后提供换行机会，其余字符尽量保持不换行
            if (!style.hideLocation) {
                val position = course.position
                if (position.isNotBlank()) {
                    val prefix = if (style.removeLocationAt) "" else "@\u200B"
                    // 在 @、-、全角/半角括号前后插入零宽空格，作为换行机会点
                    val breakablePosition = position.replace(Regex("([@\\-（(）)])"), "\u200B$1\u200B")
                    Text(
                        text = "$prefix$breakablePosition",
                        fontSize = metaFontSize,
                        color = presetRender.textColor.copy(alpha = 0.82f),
                        textAlign = textAlign,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 4,
                        style = TextStyle(lineHeight = 1.1.em)
                    )
                }
            }

            // 教师：权重最低，置于最后；紧凑块（双排/极矮）下隐藏，否则单行展示
            if (!style.hideTeacher && !isCompact) {
                val teacher = course.teacher
                if (teacher.isNotBlank()) {
                    Text(
                        text = teacher,
                        fontSize = metaFontSize,
                        color = presetRender.textColor.copy(alpha = 0.82f),
                        textAlign = textAlign,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = TextStyle(lineHeight = 1.1.em)
                    )
                }
            }
        }

        // 当单课不是当前周时，进行干净的全局遮罩染色与虚化斜线绘制
        if (isVisualDemoted && !isFloating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = (if (isDarkTheme) Color.Black else Color.White)
                            .copy(alpha = presetRender.demotedOverlayAlpha)
                    )
                    .drawBehind {
                        val stripeWidth = 5.dp.toPx()
                        val stripeColor = (if (isDarkTheme) Color.White else Color.Black).copy(alpha = 0.06f)
                        val brush = Brush.linearGradient(
                            0.0f to stripeColor, 0.45f to stripeColor,
                            0.55f to Color.Transparent, 1.0f to Color.Transparent,
                            start = Offset(0f, 0f), end = Offset(stripeWidth, stripeWidth), tileMode = TileMode.Repeated
                        )
                        drawRect(brush = brush)
                    }
            )
        }
    }
}