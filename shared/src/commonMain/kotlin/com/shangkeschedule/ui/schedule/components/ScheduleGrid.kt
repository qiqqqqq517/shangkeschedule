package com.shangkeschedule.ui.schedule.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.shangkeschedule.data.model.schedule_style.ScheduleModeProto
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.MotionPressMode
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.jetbrains.compose.resources.stringArrayResource
import shangkeschedule.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.a11y_course_location_fmt
import shangkeschedule.shared.generated.resources.a11y_course_not_this_week
import shangkeschedule.shared.generated.resources.a11y_course_sections_fmt
import shangkeschedule.shared.generated.resources.a11y_course_teacher_fmt
import shangkeschedule.shared.generated.resources.a11y_list_separator
import shangkeschedule.shared.generated.resources.week_days_short_names
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** 节次文本：整数节显示整数（"1"），24 小时制的 0.25 步长保留小数（"6.25"） */
private fun Float.toA11ySectionText(): String =
    if (this % 1f == 0f) this.toInt().toString() else this.toString()

@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
@Composable
fun ScheduleGrid(
    state: ScheduleGridState,
    viewState: ScheduleGridViewState,
    actions: ScheduleGridActions,
    style: ScheduleGridStyleComposed,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp
) {
    Box(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // 拖拽落位吸附的协程宿主（v3.43.0）
        val snapScope = rememberCoroutineScope()

        LaunchedEffect(viewState.mergedCourses) {
            state.resetAllStates()
            actions.onHoldStateChanged(false)
        }

        val pageTextColor = style.pageTextColor ?: MaterialTheme.colorScheme.onSurface
        val pageSubTextColor = pageTextColor.copy(alpha = 0.7f)
        val weekDays = stringArrayResource(Res.array.week_days_short_names).toList()
        val reorderedWeekDays = rearrangeDays(weekDays, viewState.firstDayOfWeek)
        val displayDays = if (viewState.showWeekends) reorderedWeekDays else reorderedWeekDays.take(5)

        val displayDaysCount = displayDays.size
        val is24HourMode = style.scheduleMode == ScheduleModeProto.TIME_24H_MODE
        val maxGridSections = if (is24HourMode) 24 else viewState.timeSlots.size

        val totalGridHeight = style.sectionHeight * maxGridSections
        val gridLineColor = appColors().divider.copy(alpha = 0.2f)
        val strokeWidthPx = 1f

        val singleSchedulables = remember(viewState.mergedCourses, viewState.firstDayOfWeek, viewState.showWeekends) {
            calculateSingleSchedulables(viewState.mergedCourses, viewState.firstDayOfWeek, viewState.showWeekends)
        }
        val sectionHeightPx = with(density) { style.sectionHeight.toPx() }

        var activeDragHour by remember { mutableStateOf<Int?>(null) }
        var activeDragMinuteStr by remember { mutableStateOf<String?>(null) }

        if (is24HourMode && state.expandedItem != null) {
            val currentTargetSection = when {
                state.isTopHandleDragging -> {
                    val minGap = 0.25f
                    val deltaSection = state.topHandleDragOffsetY / sectionHeightPx
                    var proposedStart = state.expandedItem!!.startSection + deltaSection
                    proposedStart = (proposedStart / 0.25f).roundToInt() * 0.25f
                    proposedStart.coerceIn(0f, state.expandedItem!!.endSection - minGap)
                }
                state.isBottomHandleDragging -> {
                    val minGap = 0.25f
                    val deltaSection = state.bottomHandleDragOffsetY / sectionHeightPx
                    var proposedEnd = state.expandedItem!!.endSection + deltaSection
                    proposedEnd = (proposedEnd / 0.25f).roundToInt() * 0.25f
                    proposedEnd.coerceIn(state.expandedItem!!.startSection + minGap, maxGridSections.toFloat())
                }
                state.activeMoveIntent != null -> {
                    val duration = state.activeMoveIntent!!.duration
                    var targetStart = state.activeMoveIntent!!.initialStartSection + (state.bodyDragOffsetY / sectionHeightPx)
                    targetStart = (targetStart / 0.25f).roundToInt() * 0.25f
                    targetStart.coerceIn(0f, maxGridSections - duration)
                }
                else -> null
            }

            if (currentTargetSection != null) {
                val hour = kotlin.math.floor(currentTargetSection).toInt().coerceIn(0, maxGridSections - 1)
                val minuteFraction = currentTargetSection - kotlin.math.floor(currentTargetSection)
                val minute = (minuteFraction * 60).roundToInt() % 60

                activeDragHour = hour
                activeDragMinuteStr = minute.toString().padStart(2, '0')
            } else {
                activeDragHour = null
                activeDragMinuteStr = null
            }
        } else {
            activeDragHour = null
            activeDragMinuteStr = null
        }

        // 自动边缘滚动监测线程
        LaunchedEffect(state.isEditingActive) {
            if (state.isEditingActive) {
                while (true) {
                    val item = state.expandedItem ?: break
                    val threshold = with(density) { 40.dp.toPx() }
                    val scrollSpeed = with(density) { 8.dp.toPx() }

                    var relativeTop: Float? = null
                    var relativeBottom: Float? = null

                    if (state.activeMoveIntent != null) {
                        val currentTopInGrid = item.startSection * sectionHeightPx + state.bodyDragOffsetY
                        val currentBottomInGrid = item.endSection * sectionHeightPx + state.bodyDragOffsetY
                        relativeTop = currentTopInGrid - state.gridScrollState.value
                        relativeBottom = currentBottomInGrid - state.gridScrollState.value
                    } else if (state.isTopHandleDragging) {
                        val currentTopInGrid = item.startSection * sectionHeightPx + state.topHandleDragOffsetY
                        relativeTop = currentTopInGrid - state.gridScrollState.value
                    } else if (state.isBottomHandleDragging) {
                        val currentBottomInGrid = item.endSection * sectionHeightPx + state.bottomHandleDragOffsetY
                        relativeBottom = currentBottomInGrid - state.gridScrollState.value
                    }

                    var scrollAmount = 0f
                    if (relativeTop != null && relativeTop < threshold) {
                        if (state.gridScrollState.value > 0) {
                            scrollAmount = -scrollSpeed
                            if (state.gridScrollState.value + scrollAmount < 0) scrollAmount = -state.gridScrollState.value.toFloat()
                        }
                    } else if (relativeBottom != null && state.viewportHeightPx > 0f && relativeBottom > state.viewportHeightPx - threshold) {
                        val maxScroll = state.gridScrollState.maxValue
                        if (state.gridScrollState.value < maxScroll) {
                            scrollAmount = scrollSpeed
                            if (state.gridScrollState.value + scrollAmount > maxScroll) scrollAmount = (maxScroll - state.gridScrollState.value).toFloat()
                        }
                    }

                    if (scrollAmount != 0f) {
                        state.gridScrollState.scrollBy(scrollAmount)
                        if (state.activeMoveIntent != null) {
                            state.bodyDragOffsetY += scrollAmount
                        } else if (state.isTopHandleDragging) {
                            state.topHandleDragOffsetY += scrollAmount
                        } else if (state.isBottomHandleDragging) {
                            state.bottomHandleDragOffsetY += scrollAmount
                        }
                    }
                    delay(16.milliseconds)
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            DayHeader(style, displayDays, viewState.dates, viewState.currentYear, viewState.currentWeek, viewState.todayIndex, gridLineColor, pageTextColor, pageSubTextColor, strokeWidthPx)

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { state.viewportHeightPx = it.height.toFloat() }
                    .verticalScroll(state = state.gridScrollState, enabled = state.expandedItem == null)
                    // 底部留白放进滚动内容内部：网格视口延伸到玻璃底栏之下，
                    // 末节课程可滚动到导航条上方（对齐「我的」页内容穿越形态）
                    .padding(bottom = bottomInset)
            ) {
                TimeColumn(
                    style = style, timeSlots = viewState.timeSlots, maxGridSections = maxGridSections,
                    is24HourMode = is24HourMode, onTimeSlotClicked = { actions.onTimeSlotClicked() },
                    modifier = Modifier.height(totalGridHeight), lineColor = gridLineColor,
                    currentSectionIndex = viewState.currentSectionIndex, textColor = pageTextColor,
                    subTextColor = pageSubTextColor, strokeWidthPx = strokeWidthPx,
                    activeDragHour = activeDragHour,
                    activeDragMinuteStr = activeDragMinuteStr
                )

            Layout(
                content = {
                    singleSchedulables.forEachIndexed { blockIndex, item ->
                        val isExpanded = state.expandedItem != null && state.expandedItem?.parentBlock === item.parentBlock
                        val isCrushBlock = item.parentBlock.courses.any { it.course.isCrush }

                        // v3.26.0 C+.15 课程格点按反馈：按压缩放 + 微抬起（读全局令牌；
                        // 关掉「课程格反馈」分组 ⇒ snap 到原样，无动画）
                        val cellMotion = LocalAppMotion.current
                        var cellPressed by remember(item) { mutableStateOf(false) }
                        val cellPressFraction by animateFloatAsState(
                            targetValue = if (cellPressed) 1f else 0f,
                            animationSpec = cellMotion.tokens.cellPressSpec,
                            label = "courseCellPress"
                        )

                        // v3.43.0 主题化按压反馈：按 MotionPressMode 决定"用什么语言回应"——
                        // 通透（SCALE）= 缩放 + 微抬起；柔绘（CONCENTRATION）= 软边径向浓度，零形变；
                        // 书卷（COLOR_DARKEN）= 整块底色均匀加深，零形变。
                        // 这样柔绘的 softShadow 双层软投影 / 羽化环、书卷的不透明纸卡描边
                        // 在按压全程保持静止，不会随缩放抖出厚度不均的边缘。
                        val pressMode = cellMotion.profile.pressMode
                        val pressFeedbackEnabled = cellMotion.isEnabled(AnimationGroup.COURSE_CELL)
                        var cellPressPosition by remember(item) { mutableStateOf(Offset.Zero) }
                        val cellPressTint = if (pressMode == MotionPressMode.CONCENTRATION) {
                            appColors().primary.copy(alpha = 0.14f)
                        } else if (LocalIsDarkTheme.current) {
                            Color.White.copy(alpha = 0.05f)
                        } else {
                            Color.Black.copy(alpha = 0.05f)
                        }
                        // 入场起始不透明度：0.35 起（旧行为 0 会让满周后排课程在 1~2s 内完全不可见）
                        val entranceInitialAlpha = cellMotion.tokens.entranceInitialAlpha

                        // v3.26.0 C+.17 页面入场错峰淡入：仅首次组合触发（remember 记住，
                        // 课程数据刷新不重播）；关掉「页面入场」分组 ⇒ 直接显示
                        val entranceEnabled = cellMotion.isEnabled(AnimationGroup.PAGE_ENTRANCE) &&
                            cellMotion.tokens.entranceDurationMs > 0
                        var entranceEntered by remember { mutableStateOf(!entranceEnabled) }
                        LaunchedEffect(item) {
                            if (!entranceEntered) {
                                // v3.43.0：错峰总延迟设上限——满周约 30 块时不再让最后一块等到
                                // 1.2s(GLASS)/2.1s(GENTLE) 才出现，全部收敛到 cap 内
                                val stagger = (blockIndex * cellMotion.tokens.entranceStaggerMs)
                                    .coerceAtMost(cellMotion.tokens.entranceStaggerCapMs)
                                delay(stagger.toLong())
                                entranceEntered = true
                            }
                        }
                        val entranceFraction by animateFloatAsState(
                            targetValue = if (entranceEntered) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = cellMotion.tokens.entranceDurationMs,
                                easing = cellMotion.tokens.entranceEasing
                            ),
                            label = "courseCellEntrance"
                        )

                            // P1-19 TalkBack 语义：课程块整体播报（名称/节次或时间/地点/教师/非本周），
                            // 并暴露点击/长按动作（pointerInput 不产生语义节点，无此则读屏完全无法操作课表）
                            val a11ySeparator = stringResource(Res.string.a11y_list_separator)
                            val a11yCourse = item.courseWrapper.course
                            val blockA11yParts = mutableListOf<String>()
                            blockA11yParts.add(a11yCourse.name)
                            if (a11yCourse.customStartTime != null && a11yCourse.customEndTime != null) {
                                blockA11yParts.add("${a11yCourse.customStartTime} - ${a11yCourse.customEndTime}")
                            } else {
                                blockA11yParts.add(
                                    stringResource(
                                        Res.string.a11y_course_sections_fmt,
                                        item.startSection.toA11ySectionText(),
                                        item.endSection.toA11ySectionText()
                                    )
                                )
                            }
                            if (a11yCourse.position.isNotBlank()) {
                                blockA11yParts.add(stringResource(Res.string.a11y_course_location_fmt, a11yCourse.position))
                            }
                            if (a11yCourse.teacher.isNotBlank()) {
                                blockA11yParts.add(stringResource(Res.string.a11y_course_teacher_fmt, a11yCourse.teacher))
                            }
                            if (item.parentBlock.isVisualDemoted) {
                                blockA11yParts.add(stringResource(Res.string.a11y_course_not_this_week))
                            }
                            val blockA11yDescription = blockA11yParts.joinToString(a11ySeparator)

                            Box(
                                modifier = Modifier
                                    .padding(style.courseBlockOuterPadding)
                                    .graphicsLayer {
                                        // 只有 SCALE 档（通透）允许形变；柔绘 / 书卷走零缩放零位移，
                                        // 反馈改由下方 courseCellPressFeedback 以浓度 / 底色表达
                                        if (pressMode == MotionPressMode.SCALE) {
                                            val pressScale =
                                                1f + (cellMotion.tokens.cellPressScale - 1f) * cellPressFraction
                                            scaleX = pressScale
                                            scaleY = pressScale
                                            translationY =
                                                -cellMotion.tokens.cellLiftDp.toPx() * cellPressFraction +
                                                    cellMotion.tokens.entranceSlideDp.toPx() * (1f - entranceFraction)
                                        } else {
                                            translationY =
                                                cellMotion.tokens.entranceSlideDp.toPx() * (1f - entranceFraction)
                                        }
                                        // 入场从 entranceInitialAlpha 起，而不是从完全不可见起（P1 修复）
                                        alpha = entranceInitialAlpha +
                                            (1f - entranceInitialAlpha) * entranceFraction
                                    }
                                    // v3.43.0 主题化按压反馈：绘制在课程块内容之上
                                    .courseCellPressFeedback(
                                        pressMode = pressMode,
                                        pressFraction = if (pressFeedbackEnabled) cellPressFraction else 0f,
                                        pressPosition = cellPressPosition,
                                        tint = cellPressTint
                                    )
                                    .zIndex(
                                        when {
                                            isExpanded -> 2f
                                            !item.parentBlock.isVisualDemoted -> 1f
                                            else -> 0f
                                        }
                                    )
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = blockA11yDescription
                                        if (!isExpanded) {
                                            onClick {
                                                actions.onCourseBlockClicked(item.parentBlock)
                                                true
                                            }
                                            onLongClick {
                                                // crush 课程仅展示，禁止进入编辑态
                                                if (!isCrushBlock) {
                                                    state.expandedItem = item
                                                    actions.onHoldStateChanged(true)
                                                }
                                                true
                                            }
                                        } else {
                                            onClick {
                                                state.expandedItem = null
                                                actions.onHoldStateChanged(false)
                                                true
                                            }
                                        }
                                    }
                                    .then(
                                        if (!isExpanded) {
                                            Modifier.pointerInput(item) {
                                                detectTapGestures(
                                                    onPress = { offset ->
                                                        // 按压反馈的按下/抬起信号源（带触点位置，供软边渗开定位）
                                                        cellPressPosition = offset
                                                        cellPressed = true
                                                        try {
                                                            awaitRelease()
                                                        } finally {
                                                            cellPressed = false
                                                        }
                                                    },
                                                    onTap = { actions.onCourseBlockClicked(item.parentBlock) },
                                                    onLongPress = {
                                                        // crush 课程仅展示，禁止进入编辑态
                                                        if (!isCrushBlock) {
                                                            state.expandedItem = item
                                                            actions.onHoldStateChanged(true)
                                                        }
                                                    }
                                                )
                                            }
                                        } else {
                                            Modifier.pointerInput(item) {
                                                detectTapGestures(onTap = { state.expandedItem = null; actions.onHoldStateChanged(false) })
                                            }
                                        }
                                    )
                            ) {
                                CourseBlock(
                                    courseWrapper = item.courseWrapper,
                                    isVisualDemoted = item.parentBlock.isVisualDemoted,
                                    style = style,
                                    timeSlots = viewState.timeSlots,
                                    isFloating = isExpanded,
                                    modifier = if (isExpanded) {
                                        if (!item.parentBlock.isVisualDemoted) {
                                            Modifier.pointerInput(item, state.gridWidthPx) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        state.activeMoveIntent = CourseMoveIntent(item.parentBlock, item.parentBlock.day, item.startSection, item.endSection - item.startSection)
                                                        state.bodyDragOffsetX = 0f; state.bodyDragOffsetY = 0f
                                                    },
                                                    onDragEnd = {
                                                        val intent = state.activeMoveIntent
                                                        if (intent != null && state.gridWidthPx > 0f) {
                                                            val cellWidth = state.gridWidthPx / displayDaysCount

                                                            val initialX = item.columnIndex * cellWidth + (item.subColumnIndex * (cellWidth / item.subColumnCount))

                                                            val currentAbsoluteX = initialX + state.bodyDragOffsetX
                                                            val blockWidth = cellWidth / item.subColumnCount

                                                            val strictThresholdPx = with(density) { (-6.18).dp.toPx() }

                                                            val touchLeftEdge = currentAbsoluteX <= strictThresholdPx
                                                            val touchRightEdge = (currentAbsoluteX + blockWidth) >= (state.gridWidthPx - strictThresholdPx)

                                                            if (touchLeftEdge || touchRightEdge) {
                                                                actions.onInitiateFloatingMode(intent.parentBlock)
                                                                state.resetAllStates()
                                                                actions.onHoldStateChanged(false)
                                                            } else {
                                                                val deltaCols = (state.bodyDragOffsetX / cellWidth).roundToInt()
                                                                val targetDisplayIdx = (item.columnIndex + deltaCols).coerceIn(0, displayDaysCount - 1)
                                                                val targetDay = mapDisplayIndexToDay(targetDisplayIdx, viewState.firstDayOfWeek)
                                                                var targetStart = intent.initialStartSection + (state.bodyDragOffsetY / sectionHeightPx)
                                                                targetStart = if (is24HourMode) (targetStart / 0.25f).roundToInt() * 0.25f else targetStart.roundToInt().toFloat()
                                                                targetStart = targetStart.coerceIn(0f, maxGridSections - intent.duration)
                                                                val targetEnd = targetStart + intent.duration

                                                                val targetX = targetDisplayIdx * cellWidth
                                                                state.bodyDragOffsetX = targetX - initialX
                                                                state.bodyDragOffsetY = (targetStart - intent.initialStartSection) * sectionHeightPx

                                                                actions.onCourseMovedWithinGrid(intent.parentBlock, targetDay, targetStart, targetEnd)
                                                            }
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        state.resetAllStates()
                                                        actions.onHoldStateChanged(false)
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        state.bodyDragOffsetX += dragAmount.x
                                                        state.bodyDragOffsetY += dragAmount.y
                                                    }
                                                )
                                            }
                                        } else {
                                            Modifier
                                        }
                                    } else Modifier
                                )

                                if (isExpanded && state.activeMoveIntent == null && !item.parentBlock.isVisualDemoted) {
                                    CourseEditHandles(
                                        onDragStart = { isTop ->
                                            if (isTop) {
                                                state.isTopHandleDragging = true
                                                state.isBottomHandleDragging = false
                                                state.topHandleDragOffsetY = 0f
                                            } else {
                                                state.isTopHandleDragging = false
                                                state.isBottomHandleDragging = true
                                                state.bottomHandleDragOffsetY = 0f
                                            }
                                        },
                                        onDragging = { deltaY ->
                                            if (state.isTopHandleDragging) {
                                                val minGap = if (is24HourMode) 0.25f else 1f
                                                val maxAllowedDragY = ((item.endSection - item.startSection) - minGap) * sectionHeightPx

                                                val tentativeOffsetY = state.topHandleDragOffsetY + deltaY
                                                if (is24HourMode) {
                                                    val proposedStart = item.startSection + (tentativeOffsetY / sectionHeightPx)
                                                    state.topHandleDragOffsetY = if (proposedStart > item.endSection - minGap) {
                                                        maxAllowedDragY
                                                    } else {
                                                        tentativeOffsetY
                                                    }
                                                } else {
                                                    state.topHandleDragOffsetY = tentativeOffsetY.coerceAtMost(maxAllowedDragY)
                                                }
                                            } else if (state.isBottomHandleDragging) {
                                                val minGap = if (is24HourMode) 0.25f else 1f
                                                val maxAllowedDragUpY = -(((item.endSection - item.startSection) - minGap) * sectionHeightPx)

                                                val tentativeOffsetY = state.bottomHandleDragOffsetY + deltaY
                                                if (is24HourMode) {
                                                    val proposedEnd = item.endSection + (tentativeOffsetY / sectionHeightPx)
                                                    state.bottomHandleDragOffsetY = if (proposedEnd < item.startSection + minGap) {
                                                        maxAllowedDragUpY
                                                    } else {
                                                        tentativeOffsetY
                                                    }
                                                } else {
                                                    state.bottomHandleDragOffsetY = tentativeOffsetY.coerceAtLeast(maxAllowedDragUpY)
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            val currentItem = state.expandedItem
                                            if (currentItem != null) {
                                                val minGap = if (is24HourMode) 0.25f else 1f
                                                var finalStart = currentItem.startSection
                                                var finalEnd = currentItem.endSection

                                                if (state.isTopHandleDragging) {
                                                    val deltaSection = state.topHandleDragOffsetY / sectionHeightPx
                                                    var proposedStart = currentItem.startSection + deltaSection
                                                    proposedStart = if (is24HourMode) (proposedStart / 0.25f).roundToInt() * 0.25f else proposedStart.roundToInt().toFloat()
                                                    finalStart = proposedStart.coerceIn(0f, finalEnd - minGap)
                                                } else if (state.isBottomHandleDragging) {
                                                    val deltaSection = state.bottomHandleDragOffsetY / sectionHeightPx
                                                    var proposedEnd = currentItem.endSection + deltaSection
                                                    proposedEnd = if (is24HourMode) (proposedEnd / 0.25f).roundToInt() * 0.25f else proposedEnd.roundToInt().toFloat()
                                                    finalEnd = proposedEnd.coerceIn(finalStart + minGap, maxGridSections.toFloat())
                                                }
                                                // v3.43.0 尺寸把手吸附：先把拖出的偏移弹簧吸回整格，再提交时间变更
                                                val isTopHandle = state.isTopHandleDragging
                                                val targetOffsetY = if (isTopHandle) {
                                                    (finalStart - currentItem.startSection) * sectionHeightPx
                                                } else {
                                                    (finalEnd - currentItem.endSection) * sectionHeightPx
                                                }
                                                val fromOffsetY = if (isTopHandle) {
                                                    state.topHandleDragOffsetY
                                                } else {
                                                    state.bottomHandleDragOffsetY
                                                }
                                                snapScope.launch {
                                                    animateSpringSettle { t ->
                                                        val y = fromOffsetY + (targetOffsetY - fromOffsetY) * t
                                                        if (isTopHandle) {
                                                            state.topHandleDragOffsetY = y
                                                        } else {
                                                            state.bottomHandleDragOffsetY = y
                                                        }
                                                    }
                                                    if (finalStart != currentItem.startSection || finalEnd != currentItem.endSection) {
                                                        actions.onCourseTimeAdjusted(currentItem.parentBlock, finalStart, finalEnd)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .height(totalGridHeight).weight(1f)
                        .onSizeChanged { state.gridWidthPx = it.width.toFloat() }
                        .drawBehind {
                            if (!style.hideGridLines) {
                                val cellWidth = size.width / displayDaysCount
                                for (i in 1..displayDaysCount) {
                                    val x = i * cellWidth
                                    drawLine(gridLineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = strokeWidthPx)
                                }
                                for (i in 1..maxGridSections) {
                                    val y = i * sectionHeightPx
                                    drawLine(gridLineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = strokeWidthPx)
                                }
                            }


                        }
                        .pointerInput(displayDaysCount, sectionHeightPx, viewState.firstDayOfWeek, maxGridSections, is24HourMode, state.expandedItem) {
                            detectTapGestures { offset ->
                                if (state.expandedItem != null) {
                                    state.expandedItem = null
                                    actions.onHoldStateChanged(false)
                                    return@detectTapGestures
                                }
                                val dayIdx = (offset.x / (size.width / displayDaysCount)).toInt().coerceIn(0, displayDaysCount - 1)
                                val secIdx = (offset.y / sectionHeightPx).toInt().coerceIn(0, maxGridSections - 1)
                                actions.onGridCellClicked(mapDisplayIndexToDay(dayIdx, viewState.firstDayOfWeek), if (is24HourMode) secIdx else (secIdx + 1))
                            }
                        }
                ) { measurables, constraints ->
                    val cellWidth = constraints.maxWidth / displayDaysCount
                    val minGapPx = if (is24HourMode) 0f else with(density) { 30.dp.toPx() }

                    val placeables = measurables.mapIndexed { index, measurable ->
                        val item = singleSchedulables[index]
                        val isExpanded = state.expandedItem != null && state.expandedItem?.parentBlock === item.parentBlock

                        val originalHeightPx = ((item.endSection - item.startSection) * sectionHeightPx).toInt()

                        var calculatedHeightPx = originalHeightPx
                        if (isExpanded) {
                            if (state.isTopHandleDragging || state.topHandleDragOffsetY != 0f) {
                                calculatedHeightPx = (originalHeightPx - state.topHandleDragOffsetY.roundToInt()).coerceAtLeast(minGapPx.toInt())
                            } else if (state.isBottomHandleDragging || state.bottomHandleDragOffsetY != 0f) {
                                calculatedHeightPx = (originalHeightPx + state.bottomHandleDragOffsetY.roundToInt()).coerceAtLeast(minGapPx.toInt())
                            }
                        }

                        measurable.measure(
                            Constraints.fixed(
                                (cellWidth / if (isExpanded) 1 else item.subColumnCount),
                                calculatedHeightPx
                            )
                        )
                    }

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeables.forEachIndexed { index, placeable ->
                            val item = singleSchedulables[index]
                            val isExpanded = state.expandedItem != null && state.expandedItem?.parentBlock === item.parentBlock
                            val isMoving = state.activeMoveIntent != null && state.activeMoveIntent?.parentBlock === item.parentBlock

                            val originalX = item.columnIndex * cellWidth + (if (isExpanded) 0 else item.subColumnIndex * (cellWidth / item.subColumnCount))
                            val originalY = (item.startSection * sectionHeightPx).toInt()

                            var xPosition = originalX
                            var yPosition = originalY

                            if (isMoving) {
                                val intent = state.activeMoveIntent
                                if (intent != null) {
                                    val intentOriginalX = item.columnIndex * cellWidth
                                    val intentOriginalY = (intent.initialStartSection * sectionHeightPx).toInt()
                                    xPosition = (intentOriginalX + state.bodyDragOffsetX).toInt()
                                    yPosition = (intentOriginalY + state.bodyDragOffsetY).toInt()
                                }
                            } else if (isExpanded) {
                                if (state.isTopHandleDragging || state.topHandleDragOffsetY != 0f) {
                                    val originalHeightPx = ((item.endSection - item.startSection) * sectionHeightPx).toInt()
                                    yPosition = if (originalHeightPx - state.topHandleDragOffsetY >= minGapPx) {
                                        (originalY + state.topHandleDragOffsetY).toInt()
                                    } else {
                                        (originalY + (originalHeightPx - minGapPx)).toInt()
                                    }
                                }
                            }

                            placeable.placeRelative(xPosition, yPosition)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// v3.43.0 新增：主题化按压反馈 + 拖拽落位吸附
// ============================================================================

/**
 * 课程格按压反馈（按主题分档）。
 *
 * - [MotionPressMode.SCALE]（通透）：反馈由 `graphicsLayer` 的缩放 / 抬起承担，此处不绘制；
 * - [MotionPressMode.CONCENTRATION]（柔绘）：从触点向外做**软边径向浓度**——
 *   没有环状外缘、没有中心高光点，边缘缓慢衰减到透明，与"化开"一致；
 * - [MotionPressMode.COLOR_DARKEN]（书卷）：整块底色**均匀加深**，无方向、无扩散，
 *   静态纸面上不会出现"水波"。
 *
 * 两种非缩放档都只影响颜色通道，元素几何完全不动——这是柔绘软投影 / 羽化环与
 * 书卷不透明纸卡描边在按压时保持静止的前提。
 */
private fun Modifier.courseCellPressFeedback(
    pressMode: MotionPressMode,
    pressFraction: Float,
    pressPosition: Offset,
    tint: Color
): Modifier {
    if (pressMode == MotionPressMode.SCALE || pressFraction <= 0.001f) return this
    return this.drawWithContent {
        drawContent()
        when (pressMode) {
            MotionPressMode.CONCENTRATION -> {
                val center = if (pressPosition == Offset.Zero) {
                    Offset(size.width / 2f, size.height / 2f)
                } else {
                    pressPosition
                }
                val radius = maxOf(size.width, size.height) * 1.05f
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to tint.copy(alpha = tint.alpha * pressFraction),
                            0.5f to tint.copy(alpha = tint.alpha * 0.58f * pressFraction),
                            1f to Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }

            MotionPressMode.COLOR_DARKEN -> drawRect(
                color = tint.copy(alpha = tint.alpha * pressFraction)
            )

            MotionPressMode.SCALE -> Unit
        }
    }
}

/**
 * 临界阻尼弹簧补间一个 0→1 进度，逐帧回调。
 *
 * 用于拖拽落位 / 尺寸调整的「吸附」：位移曲线是零过冲的临界阻尼收束，
 * 相比此前的直接赋值（硬跳）有明确的方向感与落定感。
 */
private suspend fun animateSpringSettle(onFrame: (Float) -> Unit) {
    val anim = Animatable(0f)
    anim.animateTo(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = 1f,
            stiffness = Spring.StiffnessMediumLow
        )
    ) {
        onFrame(value)
    }
    onFrame(1f)
}