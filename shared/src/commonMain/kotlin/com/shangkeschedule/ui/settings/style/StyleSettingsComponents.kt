package com.shangkeschedule.ui.settings.style

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.data.model.schedule_style.BorderTypeProto
import com.shangkeschedule.data.model.schedule_style.ScheduleModeProto
import com.shangkeschedule.ui.components.AdvancedColorPicker
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppSegmentedControl
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.ColorPickerConfig
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import dev.chrisbanes.haze.HazeState
import com.shangkeschedule.ui.schedule.MergedCourseBlock
import com.shangkeschedule.ui.schedule.WeeklyScheduleUiState
import com.shangkeschedule.ui.schedule.components.ScheduleGrid
import com.shangkeschedule.ui.schedule.components.ScheduleGridActions
import com.shangkeschedule.ui.schedule.components.ScheduleGridStyleComposed
import com.shangkeschedule.ui.schedule.components.ScheduleGridViewState
import com.shangkeschedule.ui.schedule.components.rememberScheduleGridState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.a11y_edit
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.action_reset
import shangkeschedule.shared.generated.resources.action_reset_style
import shangkeschedule.shared.generated.resources.border_type_dashed
import shangkeschedule.shared.generated.resources.border_type_solid
import shangkeschedule.shared.generated.resources.desc_wallpaper_set
import shangkeschedule.shared.generated.resources.desc_wallpaper_unset
import shangkeschedule.shared.generated.resources.dialog_reset_message
import shangkeschedule.shared.generated.resources.dialog_reset_title
import shangkeschedule.shared.generated.resources.format_week_display
import shangkeschedule.shared.generated.resources.image_24px
import shangkeschedule.shared.generated.resources.label_border_type
import shangkeschedule.shared.generated.resources.label_corner_radius
import shangkeschedule.shared.generated.resources.label_day_header_height
import shangkeschedule.shared.generated.resources.label_font_scale
import shangkeschedule.shared.generated.resources.label_hide_date_under_day
import shangkeschedule.shared.generated.resources.label_hide_grid_lines
import shangkeschedule.shared.generated.resources.label_hide_location
import shangkeschedule.shared.generated.resources.label_hide_section_time
import shangkeschedule.shared.generated.resources.label_hide_teacher
import shangkeschedule.shared.generated.resources.label_inner_padding
import shangkeschedule.shared.generated.resources.label_none
import shangkeschedule.shared.generated.resources.label_opacity
import shangkeschedule.shared.generated.resources.label_outer_padding
import shangkeschedule.shared.generated.resources.label_range
import shangkeschedule.shared.generated.resources.label_remove_location_at
import shangkeschedule.shared.generated.resources.label_schedule_mode_24h
import shangkeschedule.shared.generated.resources.label_section_height
import shangkeschedule.shared.generated.resources.label_show_start_time
import shangkeschedule.shared.generated.resources.label_text_align_center_h
import shangkeschedule.shared.generated.resources.label_text_align_center_v
import shangkeschedule.shared.generated.resources.label_time_column_width
import shangkeschedule.shared.generated.resources.label_wallpaper
import shangkeschedule.shared.generated.resources.placeholder_input_value
import shangkeschedule.shared.generated.resources.refresh_24px
import shangkeschedule.shared.generated.resources.status_not_set
import shangkeschedule.shared.generated.resources.style_category_course_block
import shangkeschedule.shared.generated.resources.style_category_grid_size
import shangkeschedule.shared.generated.resources.style_category_interface
import kotlin.math.roundToInt
import kotlin.time.Clock

@Composable
fun SettingsListContent(
    currentStyle: ScheduleGridStyleComposed,
    viewModel: StyleSettingsViewModel,
    onWallpaperClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    scrollable: Boolean = true
) {
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        // 危险操作统一走 AppDangerDialog（危险色胶囊确认钮）
        AppDangerDialog(
            onDismissRequest = { showResetDialog = false },
            title = stringResource(Res.string.dialog_reset_title),
            text = stringResource(Res.string.dialog_reset_message),
            confirmText = stringResource(Res.string.action_confirm),
            onConfirm = {
                viewModel.resetStyleSettings()
                showResetDialog = false
            },
            dismissText = stringResource(Res.string.action_cancel),
            onDismiss = { showResetDialog = false }
        )
    }

    val contentModifier = if (scrollable) {
        modifier.verticalScroll(rememberScrollState())
    } else {
        modifier
    }

    Column(
        modifier = contentModifier.padding(appSpacing().pageHorizontal),
        verticalArrangement = Arrangement.spacedBy(appSpacing().listGap)
    ) {
        OutlinedButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors().danger),
            border = BorderStroke(1.dp, appColors().danger.copy(alpha = 0.5f))
        ) {
            Text(stringResource(Res.string.action_reset_style))
        }

        AppSectionHeader(stringResource(Res.string.style_category_interface))
        WallpaperItem(
            path = currentStyle.backgroundImagePath,
            onClick = onWallpaperClick,
            onLongClick = { viewModel.removeWallpaper() }
        )
        StyleSwitchItem(
            label = stringResource(Res.string.label_schedule_mode_24h),
            checked = currentStyle.scheduleMode == ScheduleModeProto.TIME_24H_MODE
        ) { isChecked ->
            val targetMode = if (isChecked) {
                ScheduleModeProto.TIME_24H_MODE
            } else {
                ScheduleModeProto.SECTION_MODE
            }
            viewModel.updateScheduleMode(targetMode)
        }
        StyleSwitchItem(stringResource(Res.string.label_hide_section_time), currentStyle.hideSectionTime) { viewModel.updateHideSectionTime(it) }
        StyleSwitchItem(stringResource(Res.string.label_hide_date_under_day), currentStyle.hideDateUnderDay) { viewModel.updateHideDateUnderDay(it) }
        StyleSwitchItem(label = stringResource(Res.string.label_hide_grid_lines), checked = currentStyle.hideGridLines) { viewModel.updateHideGridLines(it) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = appColors().divider, thickness = 0.5.dp)

        AppSectionHeader(stringResource(Res.string.style_category_grid_size))
        StyleSliderItem(stringResource(Res.string.label_section_height), currentStyle.sectionHeight.value, 40f..120f) { viewModel.updateSectionHeight(it) }
        StyleSliderItem(stringResource(Res.string.label_time_column_width), currentStyle.timeColumnWidth.value, 20f..80f) { viewModel.updateTimeColumnWidth(it) }
        StyleSliderItem(stringResource(Res.string.label_day_header_height), currentStyle.dayHeaderHeight.value, 30f..80f) { viewModel.updateDayHeaderHeight(it) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = appColors().divider, thickness = 0.5.dp)

        AppSectionHeader(stringResource(Res.string.style_category_course_block))
        StyleSwitchItem(stringResource(Res.string.label_show_start_time), currentStyle.showStartTime) { viewModel.updateShowStartTime(it) }
        StyleSwitchItem(stringResource(Res.string.label_hide_location), currentStyle.hideLocation) { viewModel.updateHideLocation(it) }
        StyleSwitchItem(stringResource(Res.string.label_hide_teacher), currentStyle.hideTeacher) { viewModel.updateHideTeacher(it) }
        StyleSwitchItem(stringResource(Res.string.label_remove_location_at), currentStyle.removeLocationAt) { viewModel.updateRemoveLocationAt(it) }
        StyleSwitchItem(stringResource(Res.string.label_text_align_center_h), currentStyle.textAlignCenterHorizontal) { viewModel.updateTextAlignCenterHorizontal(it) }
        StyleSwitchItem(stringResource(Res.string.label_text_align_center_v), currentStyle.textAlignCenterVertical) { viewModel.updateTextAlignCenterVertical(it) }
        BorderTypeSelector(currentStyle.borderType) { viewModel.updateBorderType(it) }

        StyleSliderItem(stringResource(Res.string.label_font_scale), currentStyle.fontScale, 0.5f..2.0f, 0.1f) { viewModel.updateCourseBlockFontScale(it) }
        StyleSliderItem(stringResource(Res.string.label_corner_radius), currentStyle.courseBlockCornerRadius.value, 0f..24f, 1f) { viewModel.updateCornerRadius(it) }
        StyleSliderItem(stringResource(Res.string.label_inner_padding), currentStyle.courseBlockInnerPadding.value, 0f..12f, 1f) { viewModel.updateInnerPadding(it) }
        StyleSliderItem(stringResource(Res.string.label_outer_padding), currentStyle.courseBlockOuterPadding.value, 0f..8f, 1f) { viewModel.updateOuterPadding(it) }
        // 下限 = MIN_BLOCK_ALPHA（0.5）：再低会让浅色模式的方块填充掉到「透明」（见该常量注释），
        // 与读取侧 toCompose() 的夹取保持同一口径。
        StyleSliderItem(
            stringResource(Res.string.label_opacity),
            currentStyle.courseBlockAlpha,
            ScheduleGridStyle.MIN_BLOCK_ALPHA..1f,
            0.05f
        ) { viewModel.updateAlpha(it) }
    }
}

@Composable
fun BorderTypeSelector(
    currentType: BorderTypeProto,
    onTypeChange: (BorderTypeProto) -> Unit
) {
    val types = listOf(
        BorderTypeProto.BORDER_TYPE_NONE to stringResource(Res.string.label_none),
        BorderTypeProto.BORDER_TYPE_SOLID to stringResource(Res.string.border_type_solid),
        BorderTypeProto.BORDER_TYPE_DASHED to stringResource(Res.string.border_type_dashed)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.label_border_type), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
        // 复用全局分段控件（与 24h 模式切换等分段语言一致）
        AppSegmentedControl(
            options = types.map { it.second },
            selectedIndex = types.indexOfFirst { it.first == currentType }.coerceAtLeast(0),
            onSelect = { index -> onTypeChange(types[index].first) }
        )
    }
}

@Composable
fun ColorSchemeSection(
    title: String,
    bgColor: Color,
    isDarkSection: Boolean,
    colors: List<Color>,
    onEditColor: (Int) -> Unit
) {
    // 功能色（豁免声明）：本区块模拟固定浅色/深色取色池背景，文字用纯黑/纯白
    // 是对池底色的对照色，不随主题 token 变化，属有意的功能性固定色。
    val contentColor = if (isDarkSection) Color.White else Color.Black

    Column(modifier = Modifier.fillMaxWidth().clip(appShapes().chip).background(bgColor).padding(appSpacing().cardInner)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = contentColor)
        Spacer(modifier = Modifier.height(appSpacing().cardGap))

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(appSpacing().sectionTitleGap)) {
            colors.forEachIndexed { index, color ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // AC2（v3.69.2）：色块可点击打开取色器，但块内没有文字 ——
                    // TalkBack 此前读到「未加标签，可点击」。补「编辑 + 序号」描述与按钮角色，
                    // 序号与下方可见的编号标签一致，用户才能知道自己点的是第几个颜色。
                    val editLabel = stringResource(Res.string.a11y_edit)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .semantics {
                                contentDescription = "$editLabel ${index + 1}"
                                role = Role.Button
                            }
                            .clickable { onEditColor(index) }
                    )
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(0.6f), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun ScheduleGridContent(
    style: ScheduleGridStyleComposed,
    demoUiState: WeeklyScheduleUiState
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val localDates = remember(demoUiState.firstDayOfWeek) {
        val targetDayOfWeek = DayOfWeek.entries.getOrNull(demoUiState.firstDayOfWeek - 1) ?: DayOfWeek.MONDAY
        var startOfWeek = today
        while (startOfWeek.dayOfWeek != targetDayOfWeek) {
            startOfWeek = startOfWeek.minus(1, DateTimeUnit.DAY)
        }
        (0..6).map { startOfWeek.plus(it, DateTimeUnit.DAY) }
    }
    val currentYearString = remember(today) { today.year.toString() }
    val dummyDates = remember(localDates) {
        localDates.map {
            val month = it.month.number.toString().padStart(2, '0')
            val day = it.day.toString().padStart(2, '0')
            "$month/$day"
        }
    }
    val dynamicTodayIndex = remember(localDates) { localDates.indexOf(today) }
    val previewWeekStr = stringResource(Res.string.format_week_display, 1)
    val previewScrollState = rememberScrollState()
    val gridState = rememberScheduleGridState(gridScrollState = previewScrollState)
    val gridViewState = remember(dummyDates, currentYearString, demoUiState, dynamicTodayIndex, previewWeekStr) {
        ScheduleGridViewState(
            dates = dummyDates,
            currentYear = currentYearString,
            currentWeek = previewWeekStr,
            timeSlots = demoUiState.timeSlots,
            mergedCourses = demoUiState.currentMergedCourses,
            showWeekends = demoUiState.showWeekends,
            todayIndex = dynamicTodayIndex,
            firstDayOfWeek = demoUiState.firstDayOfWeek,
            currentSectionIndex = -1
        )
    }

    val gridActions = remember {
        object : ScheduleGridActions {
            override fun onCourseBlockClicked(block: MergedCourseBlock) {}
            override fun onGridCellClicked(day: Int, section: Int) {}
            override fun onTimeSlotClicked() {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (style.backgroundImagePath.isNotEmpty()) {
            AsyncImage(
                model = style.backgroundImagePath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )
        }

        ScheduleGrid(
            state = gridState,
            viewState = gridViewState,
            actions = gridActions,
            style = style,
            modifier = Modifier
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrollable(
                    orientation = Orientation.Vertical,
                    state = ScrollableState { delta ->
                        previewScrollState.dispatchRawDelta(-delta)
                        delta
                    },
                    flingBehavior = ScrollableDefaults.flingBehavior()
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {},
                        onTap = {}
                    )
                }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleSliderItem(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    stepValue: Float = 1f,
    /** 拖动/输入结束回调（v3.54.0）：供调用方做「拖动中本地预览、结束才落库」的节流。 */
    onValueChangeFinished: (() -> Unit)? = null,
    onValueChange: (Float) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val isIntegerStep = stepValue >= 1f

    fun formatValue(v: Float): String {
        return if (isIntegerStep) {
            "${v.toInt()}"
        } else {
            val rounded = (v * 10).roundToInt() / 10.0
            if (rounded % 1.0 == 0.0) "${rounded.toInt()}.0" else "$rounded"
        }
    }

    val steps = remember(range, stepValue) {
        if (stepValue > 0f) {
            ((range.endInclusive - range.start) / stepValue).toInt() - 1
        } else 0
    }

    if (showDialog) {
        var textFieldValue by remember { mutableStateOf(formatValue(value)) }

        AppAlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Column {
                    Text(
                        text = "${stringResource(Res.string.label_range)}: ${formatValue(range.start)} - ${formatValue(range.endInclusive)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    // 柔和填充输入框（AppTextField，与其他弹窗输入一致）
                    AppTextField(
                        value = textFieldValue,
                        onValueChange = { input ->
                            if (isIntegerStep) {
                                if (input.all { it.isDigit() }) textFieldValue = input
                            } else {
                                if (input.all { it.isDigit() || it == '.' }) textFieldValue = input
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (isIntegerStep) KeyboardType.Number
                            else KeyboardType.Decimal
                        ),
                        placeholder = stringResource(Res.string.placeholder_input_value)
                    )
                }
            },
            confirmButton = {
                // 主色胶囊确认 + 灰字取消（AppDialogActions）
                AppDialogActions(
                    confirmText = stringResource(Res.string.action_confirm),
                    onConfirm = {
                        val newValue = textFieldValue.toFloatOrNull()
                        if (newValue != null) {
                            val clampedValue = newValue.coerceIn(range.start, range.endInclusive)
                            val steppedValue = if (stepValue > 0f) {
                                val count = ((clampedValue - range.start) / stepValue).roundToInt()
                                range.start + count * stepValue
                            } else clampedValue

                            onValueChange(steppedValue)
                            onValueChangeFinished?.invoke()
                            showDialog = false
                        }
                    },
                    dismissText = stringResource(Res.string.action_cancel),
                    onDismiss = { showDialog = false }
                )
            },
            dismissButton = {}
        )
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable { showDialog = true }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatValue(value),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        val tokens = appColors()
        // 滑块几何（v4.75.5 修复 thumb 溢出轨道 → v4.75.6 改为自绘轨道、刻度封顶）：
        //
        // Material3 1.9 的 Slider 采用 M3 Expressive 布局，源码（Slider.kt）实测：
        //   1. 轨道 placeable 被 thumb 宽度压缩测量：measure(constraints.offset(horizontal = -thumbPlaceable.width))；
        //   2. 轨道被摆放于 trackOffsetX = thumbPlaceable.width / 2，即**两端各让出半个 thumb**；
        //   3. thumb 行程为 thumbOffsetX = trackPlaceable.width * fraction，即 thumb **压在轨道两端之上**；
        //   4. M3 自带的 tick 数量 = steps+1，range/step 较大时（如 40~120 步长 1）会画出上百个点，
        //      且其轨道绘制只覆盖被压缩后的 placeable，导致两端露出空白。
        //
        // 因此 v4.75.6 弃用 M3 的 track 绘制（SliderDefaults.Track），改为**自绘整条轨道**：
        //   - thumb 与轨道等高（thumbSize = trackHeight）；
        //   - 可见轨道左右各外扩半个 thumb，覆盖 thumb 完整行程，两端恰好齐平、不溢出；
        //   - 刻度点自绘、均匀铺满整条轨道（无两端空白），且总数封顶 13，避免可选值多时过密。
        //
        // 几何要点：轨道本地 x=0 对应 thumb 中心的**最左**位置（滑块左缘贴齐轨道左端），
        // thumb 圆心 = left + 半径 + fraction * (totalLen - 2*半径)，与 M3 的位移公式一致。
        val trackHeight = 22.dp
        val thumbSize = trackHeight
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = { onValueChangeFinished?.invoke() },
            valueRange = range,
            steps = if (steps > 0) steps else 0,
            modifier = Modifier.height(32.dp),
            thumb = {
                Surface(
                    modifier = Modifier.size(thumbSize),
                    shape = CircleShape,
                    color = tokens.cardBg,
                    shadowElevation = 1.dp,
                    border = BorderStroke(0.5.dp, tokens.divider)
                ) {}
            },
            track = { _ ->
                // 轨道几何（v4.75.6）：thumb 与轨道等高；可见轨道覆盖 thumb 的完整行程
                // （M3 把轨道压缩了 thumbWidth 并右移半个 thumb，故向两端各外扩半个 thumb），
                // 保证 thumb 到达两端时整枚落在轨道内、不会溢出。
                val density = LocalDensity.current
                val thumbPx = with(density) { thumbSize.toPx() }
                val tickRadiusPx = with(density) { 1.5.dp.toPx() }
                val cornerPx = with(density) { trackHeight.toPx() / 2f }
                // Canvas 的绘制 lambda 不是 @Composable，颜色必须在外面取好再捕获。
                val trackBase = MaterialTheme.colorScheme.primary
                val inactiveColor = trackBase.copy(alpha = 0.18f)
                val activeColor = trackBase.copy(alpha = 0.55f)
                val tickActive = trackBase.copy(alpha = 0.85f)
                val tickInactive = trackBase.copy(alpha = 0.38f)

                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().height(trackHeight)
                ) {
                    // thumb 圆心在 M3 轨道坐标系里的位置：fraction*(trackWidth)，
                    // 轨道本地 x=0 对应 thumb 中心的最左位置（滑块左缘贴齐轨道左端）。
                    val fraction = if (range.endInclusive > range.start) {
                        ((value - range.start) / (range.endInclusive - range.start))
                            .coerceIn(0f, 1f)
                    } else 0f

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val trackWidth = size.width
                        val trackHeightPx = size.height
                        // 可见轨道左右各外扩半个 thumb，恰好容纳位于两端的整枚 thumb。
                        val left = -thumbPx / 2f
                        val right = trackWidth + thumbPx / 2f
                        val totalLen = right - left
                        val corner = CornerRadius(cornerPx)

                        // 底色整条轨道
                        drawRoundRect(
                            color = inactiveColor,
                            topLeft = Offset(left, 0f),
                            size = Size(totalLen, trackHeightPx),
                            cornerRadius = corner
                        )

                        // thumb 圆心位置：从 left+半径 到 right-半径
                        val thumbCenter = left + trackHeightPx / 2f +
                            fraction * (totalLen - trackHeightPx)

                        // 已选段：left → thumb 圆心
                        drawRoundRect(
                            color = activeColor,
                            topLeft = Offset(left, 0f),
                            size = Size(thumbCenter - left, trackHeightPx),
                            cornerRadius = corner
                        )

                        // 刻度点：均匀铺满整条可见轨道（消除两端空白），并封顶总数防过密。
                        val tickCount = minOf((steps + 1).coerceAtLeast(2), 13)
                        repeat(tickCount) { i ->
                            val x = left + (totalLen * i / (tickCount - 1f))
                            drawCircle(
                                color = if (x <= thumbCenter) tickActive else tickInactive,
                                radius = tickRadiusPx,
                                center = Offset(x, trackHeightPx / 2f)
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun StyleSwitchItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        com.shangkeschedule.ui.components.AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun WallpaperItem(
    path: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val hasWallpaper = path.isNotEmpty()

    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.label_wallpaper), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (hasWallpaper) stringResource(Res.string.desc_wallpaper_set)
                else stringResource(Res.string.desc_wallpaper_unset),
                style = MaterialTheme.typography.labelSmall,
                color = if (hasWallpaper) MaterialTheme.colorScheme.primary else appColors().textSecondary
            )
        }
        Icon(
            imageVector = vectorResource(Res.drawable.image_24px),
            contentDescription = null,
            tint = if (hasWallpaper) MaterialTheme.colorScheme.primary else appColors().divider.copy(alpha = 0.5f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerItem(
    label: String,
    currentColor: Color?,
    onColorChanged: (Color) -> Unit,
    onReset: () -> Unit,
    hazeState: HazeState? = null
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { showSheet = true }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)

        if (currentColor != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    // 批 3：描边色改读 `dividerSoft` token（原 `divider.copy(alpha = 0.2f)` 就地降浓度）
                    .border(1.dp, appColors().dividerSoft, CircleShape)
            )
        } else {
            Text(
                text = stringResource(Res.string.status_not_set),
                style = MaterialTheme.typography.labelMedium,
                color = appColors().textSecondary.copy(alpha = 0.6f)
            )
        }
    }

    if (showSheet) {
        AppGlassBottomSheet(
            hazeState = hazeState,
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 40.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    TextButton(onClick = {
                        onReset()
                        showSheet = false
                    }) {
                        Icon(vectorResource(Res.drawable.refresh_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.action_reset))
                    }
                }
                val pickerInitialColor = currentColor ?: MaterialTheme.colorScheme.primary

                AdvancedColorPicker(
                    initialColor = pickerInitialColor,
                    onColorChanged = onColorChanged,
                    config = ColorPickerConfig(
                        showAlpha = false,
                        showInputMode = true
                    )
                )
            }
        }
    }
}
