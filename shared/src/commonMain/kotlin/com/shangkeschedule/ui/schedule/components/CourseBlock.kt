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
    val blockBackgroundColor: Color,
    val stripColor: Color,
    val textColor: Color,
    val sleepyShadowModifier: Modifier,
    val timetableStartPadding: Dp,
    val demotedOverlayAlpha: Float
)

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
    // 云舒（SLEEPY）主题已删除，其专属投影分支不再存在；isStripStylePreset = 通透（iOS 26）
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
    // 课程块投影：通透（iOS 26）与书卷均不加投影，靠材质与留白分层；
    // 悬浮态由 floatingShadowModifier 单独抬升。
    val sleepyShadowModifier = Modifier
    val timetableStartPadding = if (isStripStylePreset) 4.dp else 0.dp
    // 非当前周降级遮罩：0.618 为书卷主题有意的黄金比例设计值（豁免），通透沿用同一档位。
    val demotedOverlayAlpha = 0.618f

    return CourseBlockPresetRender(
        blockBackgroundColor = blockBackgroundColor,
        stripColor = stripColor,
        textColor = textColor,
        sleepyShadowModifier = sleepyShadowModifier,
        timetableStartPadding = timetableStartPadding,
        demotedOverlayAlpha = demotedOverlayAlpha
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

        // 课程文字内容容器
        Column(
            modifier = Modifier
                .fillMaxSize()
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