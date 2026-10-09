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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.ScrollState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
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
    scrollable: Boolean = true,
    /**
     * 当前被按住的滑块 id（v4.75.12）。非 null 时：其余条目 `alpha = 0f` 隐藏。
     *
     * ★为什么用 alpha 而不是 `if` 条件移除：按下瞬间会切换可见性，若条件移除节点，
     * 正在拖动的 Slider 节点会被 dispose，手势当场中断、值跳变。这里只改不透明度、
     * **完全不动布局尺寸**，保证拖动手感与滑块位置零变化。
     */
    activeSliderId: String? = null,
    /** 滑块按下 / 松开回调（v4.75.12）：true = 按下，false = 松开或手势取消。 */
    onSliderDragState: ((String, Boolean) -> Unit)? = null
) {
    var showResetDialog by remember { mutableStateOf(false) }

    // 非当前滑块时全部隐藏；按下期间仅 alpha 变化，尺寸恒定。
    //
    // ★ alpha(0f) 的条目**仍然会被命中并响应点击**（Compose 的 alpha 不影响命中测试），
    // 若只靠透明隐藏，用户在专注预览态点「看不见」的位置仍会误触发那一项
    // （实测：在圆角滑块全屏时点下方内部填充所在位置，会跳去调节内部填充）。
    //
    // ★ 修法不是在这里挂 pointerInput 吞事件：Compose 指针事件是**叶子优先**分发，
    // 挂在父级（SliderItem 根 Column）上的 pointerInput 永远晚于内部 Slider 收到事件，
    // 拦不住任何东西，还会因"父级消费"打断当前滑块（实测滑块直接拖不动）。
    // 正确位置见 StyleSliderItem 内部：把「是否允许响应按下」作为参数传下去，
    // 由 Slider **自己的** pointerInput 判断，隐藏项直接不上报、不响应。
    val dimmed = Modifier.alpha(if (activeSliderId == null) 1f else 0f)
    fun visOf(id: String): Modifier = if (activeSliderId == null || activeSliderId == id) {
        Modifier
    } else {
        Modifier.alpha(0f)
    }

    /** v4.76.3：是否为专注预览态下的「当前滑块」（决定是否加毛玻璃底衬）。 */
    fun isFocused(id: String): Boolean = activeSliderId == id

    /**
     * v4.76.4：预览态下只有当前滑块可交互；隐藏滑块必须停用，否则落在其位置的点击
     * 会被它接走并把activeSliderId 切过去。无预览态时全部可交互。
     */
    fun interactiveOf(id: String): Boolean = activeSliderId == null || activeSliderId == id

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
            modifier = Modifier.fillMaxWidth().then(dimmed),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors().danger),
            border = BorderStroke(1.dp, appColors().danger.copy(alpha = 0.5f))
        ) {
            Text(stringResource(Res.string.action_reset_style))
        }

        AppSectionHeader(stringResource(Res.string.style_category_interface), modifier = dimmed)
        WallpaperItem(
            path = currentStyle.backgroundImagePath,
            onClick = onWallpaperClick,
            onLongClick = { viewModel.removeWallpaper() },
            modifier = dimmed
        )
        StyleSwitchItem(
            label = stringResource(Res.string.label_schedule_mode_24h),
            checked = currentStyle.scheduleMode == ScheduleModeProto.TIME_24H_MODE,
            modifier = dimmed
        ) { isChecked ->
            val targetMode = if (isChecked) {
                ScheduleModeProto.TIME_24H_MODE
            } else {
                ScheduleModeProto.SECTION_MODE
            }
            viewModel.updateScheduleMode(targetMode)
        }
        StyleSwitchItem(stringResource(Res.string.label_hide_section_time), currentStyle.hideSectionTime, modifier = dimmed) { viewModel.updateHideSectionTime(it) }
        StyleSwitchItem(stringResource(Res.string.label_hide_date_under_day), currentStyle.hideDateUnderDay, modifier = dimmed) { viewModel.updateHideDateUnderDay(it) }
        StyleSwitchItem(label = stringResource(Res.string.label_hide_grid_lines), checked = currentStyle.hideGridLines, modifier = dimmed) { viewModel.updateHideGridLines(it) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp).then(dimmed), color = appColors().divider, thickness = 0.5.dp)

        AppSectionHeader(stringResource(Res.string.style_category_grid_size), modifier = dimmed)
        StyleSliderItem(
            stringResource(Res.string.label_section_height), currentStyle.sectionHeight.value, 40f..120f,
            sliderId = "section_height",
            onDragStateChange = onSliderDragState?.let { cb -> { active: Boolean -> cb("section_height", active) } },
            modifier = visOf("section_height"),
            isFocused = isFocused("section_height"),
            isInteractive = interactiveOf("section_height")
        ) { viewModel.updateSectionHeight(it) }
        StyleSliderItem(
            stringResource(Res.string.label_time_column_width), currentStyle.timeColumnWidth.value, 20f..80f,
            sliderId = "time_column_width",
            onDragStateChange = onSliderDragState?.let { cb -> { active: Boolean -> cb("time_column_width", active) } },
            modifier = visOf("time_column_width"),
            isFocused = isFocused("time_column_width"),
            isInteractive = interactiveOf("time_column_width")
        ) { viewModel.updateTimeColumnWidth(it) }
        StyleSliderItem(
            stringResource(Res.string.label_day_header_height), currentStyle.dayHeaderHeight.value, 30f..80f,
            sliderId = "day_header_height",
            onDragStateChange = onSliderDragState?.let { cb -> { active: Boolean -> cb("day_header_height", active) } },
            modifier = visOf("day_header_height"),
            isFocused = isFocused("day_header_height"),
            isInteractive = interactiveOf("day_header_height")
        ) { viewModel.updateDayHeaderHeight(it) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp).then(dimmed), color = appColors().divider, thickness = 0.5.dp)

        AppSectionHeader(stringResource(Res.string.style_category_course_block), modifier = dimmed)
        StyleSwitchItem(stringResource(Res.string.label_show_start_time), currentStyle.showStartTime, modifier = dimmed) { viewModel.updateShowStartTime(it) }
        StyleSwitchItem(stringResource(Res.string.label_hide_location), currentStyle.hideLocation, modifier = dimmed) { viewModel.updateHideLocation(it) }
        StyleSwitchItem(stringResource(Res.string.label_hide_teacher), currentStyle.hideTeacher, modifier = dimmed) { viewModel.updateHideTeacher(it) }
        StyleSwitchItem(stringResource(Res.string.label_remove_location_at), currentStyle.removeLocationAt, modifier = dimmed) { viewModel.updateRemoveLocationAt(it) }
        StyleSwitchItem(stringResource(Res.string.label_text_align_center_h), currentStyle.textAlignCenterHorizontal, modifier = dimmed) { viewModel.updateTextAlignCenterHorizontal(it) }
        StyleSwitchItem(stringResource(Res.string.label_text_align_center_v), currentStyle.textAlignCenterVertical, modifier = dimmed) { viewModel.updateTextAlignCenterVertical(it) }
        BorderTypeSelector(currentStyle.borderType, modifier = dimmed) { viewModel.updateBorderType(it) }

        StyleSliderItem(
            stringResource(Res.string.label_font_scale), currentStyle.fontScale, 0.5f..2.0f, 0.1f,
            sliderId = "font_scale",
            onDragStateChange = onSliderDragState?.let { cb -> { active: Boolean -> cb("font_scale", active) } },
            modifier = visOf("font_scale"),
            isFocused = isFocused("font_scale"),
            isInteractive = interactiveOf("font_scale")
        ) { viewModel.updateCourseBlockFontScale(it) }
        StyleSliderItem(
            stringResource(Res.string.label_corner_radius), currentStyle.courseBlockCornerRadius.value, 0f..24f, 1f,
            sliderId = "corner_radius",
            onDragStateChange = onSliderDragState?.let { cb -> { active: Boolean -> cb("corner_radius", active) } },
            modifier = visOf("corner_radius"),
            isFocused = isFocused("corner_radius"),
            isInteractive = interactiveOf("corner_radius")
        ) { viewModel.updateCornerRadius(it) }
        StyleSliderItem(
            stringResource(Res.string.label_inner_padding), currentStyle.courseBlockInnerPadding.value, 0f..12f, 1f,
            sliderId = "inner_padding",
            onDragStateChange = onSliderDragState?.let { cb -> { active: Boolean -> cb("inner_padding", active) } },
            modifier = visOf("inner_padding"),
            isFocused = isFocused("inner_padding"),
            isInteractive = interactiveOf("inner_padding")
        ) { viewModel.updateInnerPadding(it) }
        StyleSliderItem(
            stringResource(Res.string.label_outer_padding), currentStyle.courseBlockOuterPadding.value, 0f..8f, 1f,
            sliderId = "outer_padding",
            onDragStateChange = onSliderDragState?.let { cb -> { active: Boolean -> cb("outer_padding", active) } },
            modifier = visOf("outer_padding"),
            isFocused = isFocused("outer_padding"),
            isInteractive = interactiveOf("outer_padding")
        ) { viewModel.updateOuterPadding(it) }
        // 下限 = MIN_BLOCK_ALPHA（0.5）：再低会让浅色模式的方块填充掉到「透明」（见该常量注释），
        // 与读取侧 toCompose() 的夹取保持同一口径。
        StyleSliderItem(
            stringResource(Res.string.label_opacity),
            currentStyle.courseBlockAlpha,
            ScheduleGridStyle.MIN_BLOCK_ALPHA..1f,
            0.05f,
            sliderId = "opacity",
            onDragStateChange = onSliderDragState?.let { cb -> { active: Boolean -> cb("opacity", active) } },
            modifier = visOf("opacity"),
            isFocused = isFocused("opacity"),
            isInteractive = interactiveOf("opacity")
        ) { viewModel.updateAlpha(it) }
    }
}

@Composable
fun BorderTypeSelector(
    currentType: BorderTypeProto,
    modifier: Modifier = Modifier,
    onTypeChange: (BorderTypeProto) -> Unit
) {
    val types = listOf(
        BorderTypeProto.BORDER_TYPE_NONE to stringResource(Res.string.label_none),
        BorderTypeProto.BORDER_TYPE_SOLID to stringResource(Res.string.border_type_solid),
        BorderTypeProto.BORDER_TYPE_DASHED to stringResource(Res.string.border_type_dashed)
    )

    Column(modifier = modifier.fillMaxWidth()) {
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

/**
 * v4.76.9：进入全屏预览所需的**累计水平位移**阈值（px）。
 *
 * 按下不立即进入全屏——只有手指真的左右移动、累计水平位移越过此值，才认定
 * 「用户在调这个滑块」。这样单纯按住或上下滑动的意图仍归页面滚动，不会被预览打断。
 *
 * 取值约等于一次轻微滑动，远小于一次正常拖动，故不会让人察觉延迟。
 */
private const val HORIZONTAL_DRAG_ENTER_THRESHOLD = 14f

/**
 * v4.76.10：判定「水平意图」时，累计水平量须是累计竖直量的多少倍才算调滑块。
 *
 * 只看累计水平量是不够的——斜向滑动时水平分量同样会累积，用户本意是滚动页面却会误进全屏。
 * 取 1.4 留出余量：正常拖动滑块时水平远大于竖直；手指略带偏摆的上下滑动则不会满足。
 */
private const val HORIZONTAL_DOMINANCE_RATIO = 1.4f

/**
 * v4.76.12：竖直滚动代理的启动阈值（px）与方向优势比。
 *
 * 手指在滑块上移动超过这个累计竖直量、且竖直明显占优（1.2 倍）时，才认定「用户想滚页面」，
 * 由本组件代为滚动并消费事件。取略大于触摸 slop 的值，避免水平拖动时的手指抖动误启代理。
 */
private const val VERTICAL_SCROLL_SLOP = 12f
private const val VERTICAL_DOMINANCE_RATIO = 1.2f

/**
 * v4.76.12：设置列表的滚动状态，供滑块做「竖直滚动代理」。
 *
 * 用 CompositionLocal 而非逐级传参：调用点有 8 处以上，传参会污染签名且易漏；
 * 这里由设置页在根部 provide 一次即可。未 provide 时为 null ⇒ 代理自动关闭。
 */
val LocalSettingsScrollState = staticCompositionLocalOf<ScrollState?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleSliderItem(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    stepValue: Float = 1f,
    /** 拖动/输入结束回调（v3.54.0）：供调用方做「拖动中本地预览、结束才落库」的节流。 */
    onValueChangeFinished: (() -> Unit)? = null,
    /** 整行修饰符（v4.75.12）：调用方用 alpha 在拖动期间隐藏其它滑块（不改布局尺寸）。 */
    modifier: Modifier = Modifier,
    /**
     * 是否为专注预览态下的「当前滑块」（v4.76.3）。为 true 时给整行加半透明毛玻璃底衬，
     * 避免标题与数值直接叠在全屏课表的课程块上导致难以辨认。
     */
    isFocused: Boolean = false,
    /**
     * 是否响应触摸（v4.76.4）。预览态下**非当前**的滑块传 false：
     * 隐藏项（alpha=0f）仍会参与命中测试，若不显式关闭，落在其位置上的点击会被它接走
     * ——既会误切换到该滑块，也会把 M3 的 press/drag 手势引到看不见的滑块上。
     *
     * ★ 不能靠父级 pointerInput 拦截：Compose 指针事件叶子优先分发，父级拦截永远晚于子节点。
     */
    isInteractive: Boolean = true,
    /**
     * 本滑块的身份（v4.75.12）。调用方据此判断「当前被按下的滑块」并做隔离显示，
     * 传 null 表示不参与该交互（玻璃模糊页等旧调用点保持原行为）。
     */
    sliderId: String? = null,
    /**
     * 按下 / 松开回调（v4.75.12）。`true` = 手指按下，`false` = 松开或手势被取消。
     *
     * 用 `awaitFirstDown(requireUnconsumed = false)` **旁听**按下事件：Slider 自身会消费
     * 指针，这里不消费也不抢夺，纯粹做状态上报，因此不影响原有拖动手感。
     */
    onDragStateChange: ((Boolean) -> Unit)? = null,
    /**
     * 外层设置列表的滚动状态（v4.76.12）。
     *
     * 用途：**竖直滚动代理**。M3 Slider 的 `sliderTapModifier` 用 `detectTapGestures`，
     * 它会在按下时 `consume()` 掉 down 事件，导致外层 `verticalScroll` 无法启动 ——
     * 表现为「手指按在滑块上时页面滑不动，只有按在空白处才能滚」（用户实测反馈，
     * 且已用 logcat 证实：我方 `consumed=0` 但页面仍不滚动，即非我方拦截所致）。
     *
     * 既然滚动容器收不到事件，就由本组件在 **Initial pass 抢先**判定竖直意图，
     * 命中时自行驱动滚动（`dispatchRawDelta`）并消费事件，让 Slider 收不到竖直手势；
     * 水平意图则不干预，Slider 的拖动完全不受影响。
     *
     * 传 null 表示不启用该代理（玻璃模糊页等旧调用点保持原行为）。
     */
    scrollState: ScrollState? = LocalSettingsScrollState.current,
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

    // v4.76.3：专注预览态下给「当前滑块」加一层半透明毛玻璃底衬。
    // 此前标题与数值直接浮在全屏课表上、与课程块重叠，可读性差。
    // 用 Surface 而非 Box.background：Surface 自带形状裁剪与色调，且能沿用主题的 appSurface 观感。
    val rowContent: @Composable () -> Unit = {
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
        // 滑块几何（v4.75.5 修复 thumb 溢出 → v4.75.6 自绘轨道 → v4.75.7 取消计数点 → v4.75.8 修正坐标系）：
        //
        // Material3 1.9 的 Slider 采用 M3 Expressive 布局。本组件弃用 M3 的轨道绘制，
        // 改为 Canvas 自绘「底色整条 + 已选段 + thumb」，并满足两条硬要求：
        //   - thumb 与轨道等高（thumb 半径 = 轨道高 / 2）；
        //   - thumb 滑到两端时**恰好贴齐**轨道端点；已选段与 thumb **无缝相接**。
        //
        // 几何全部来自纯函数 computeSliderTrackGeometry（见 SliderTrackGeometry.kt），
        // 并把 M3 的 thumb 设为透明占位、自己绘制 thumb ⇒ 三者坐标同源，构造上不可能错位。
        val thumbSize = 22.dp
        val trackHeight = 22.dp
        // v4.76.0：按下 / 松开信号。
        //
        // ★ 必须用 rememberUpdatedState 包住回调，且**不以回调身份作为 pointerInput 的 key**：
        // 拖动中 value 每帧变化都会重组，调用点每次重组都新建 lambda；若把该 lambda 当作
        // pointerInput 的 key，pointerInput 会在手势进行中被重启，正在等待的 awaitEachGesture
        // 被取消 ⇒ onDragStateChange(false) 可能永不触发，预览会卡在全屏态回不来。
        // key 只取恒定不变的 sliderId，回调通过 current 读取最新值。
        val currentDragStateChange by rememberUpdatedState(onDragStateChange)
        // v4.76.5：交互开关也走 rememberUpdatedState，避免其变化重启 pointerInput（见下方注释）。
        val currentInteractive by rememberUpdatedState(isInteractive)
        Slider(
            value = value,
            onValueChange = onValueChange,
            // ★ 预览态下非当前滑块整体停用：alpha=0 的滑块仍参与命中测试，
            // 落在其位置上的点击会被它接走并把 activeSliderId 切过去（实测会跳到别的滑块）。
            // 用 M3 自带的 enabled 关停最干净——它同时停掉 press/drag 两套手势与语义。
            enabled = isInteractive,
            // v4.76.9：**退出全屏的信号只由下方 pointerInput 负责**（它在「确实进入过全屏」时才发）。
            //
            // 这里不再并联 invoke(false)：该回调在**拖动结束瞬间**也会触发，而那时全屏正需要
            // 保持显示；一旦在此发出退出，滑块刚开始调就会被立刻关掉全屏。
            onValueChangeFinished = { onValueChangeFinished?.invoke() },
            valueRange = range,
            steps = if (steps > 0) steps else 0,
            modifier = Modifier
                .height(32.dp)
                // ★ v4.76.12：竖直滚动代理（必须放在最前，且用 Initial pass）。
                //
                // 背景：M3 Slider 的 sliderTapModifier 用 detectTapGestures，它在按下时
                // consume() 掉 down，外层 verticalScroll 因此收不到、无法启动滚动 ⇒
                // 「手指按在滑块上时页面滑不动，只有按空白处才能滚」。
                // 该结论已由 logcat 证实：我方 consumed=0、entered=false，页面仍不滚。
                //
                // 做法：Initial pass 是「父 → 子」方向，比 Slider 的 Main pass 更早拿到事件。
                // 判定为竖直意图（累计竖直量过阈值且明显占优）时，自己驱动滚动并消费事件，
                // 让 Slider 收不到竖直手势；判定为水平时完全不干预，Slider 拖动不受影响。
                // 一旦进入滚动模式，本次手势内不再切回，避免在「滚动 / 调滑块」之间抖动。
                .pointerInput(sliderId, scrollState) {
                    val st = scrollState
                    if (sliderId != null && st != null && currentInteractive) {
                        awaitPointerEventScope {
                            var accX = 0f
                            var accY = 0f
                            var scrolling = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: continue
                                if (!change.pressed) {
                                    // 手指抬起：复位，准备下一次手势
                                    accX = 0f; accY = 0f; scrolling = false
                                    continue
                                }
                                val delta = change.position - change.previousPosition
                                if (delta == Offset.Zero) continue
                                accX += abs(delta.x)
                                accY += abs(delta.y)
                                if (!scrolling &&
                                    accY > VERTICAL_SCROLL_SLOP &&
                                    accY > accX * VERTICAL_DOMINANCE_RATIO
                                ) {
                                    scrolling = true
                                }
                                if (scrolling) {
                                    // dispatchRawDelta 的参数是「内容位移」：手指上移(delta.y<0)
                                    // 对应内容上滚，故取负值。
                                    st.dispatchRawDelta(-delta.y)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
                // 旁听按下 / 松开：requireUnconsumed=false 使 Slider 消费事件后本监听仍能收到，
                // 且本监听自身不消费事件，故不影响 Slider 的拖动手感与点击语义。
                    .pointerInput(sliderId) {
                    if (sliderId != null && currentInteractive) {
                        // 负责「按下进入全屏」与「手指真正抬起退出全屏」两个信号。
                        //
                        // ★ v4.76.7 依据模拟器 logcat + 截图实测定案（勿再改）：
                        //   按住不动(点击路径)：down → onValueChangeFinished → up，能看到全屏；
                        //   拖动(拖动路径)：      down → up 正常配对，全程可见全屏；
                        //   退出信号以本处「等所有指针抬起」为准 —— 不用 waitForUpOrCancellation
                        //   （它一遇 consumed 就提前返回，见下），也不单独依赖
                        //   onValueChangeFinished（它在拖动路径下根本不触发）。
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // v4.76.9：**进入全屏的时机由「按下」改为「检测到水平拖动」**。
                            // 用户诉求：单纯按住 / 上下滑动的意图是滚动页面，不该被全屏预览打断。
                            //
                            // 做法：按下后先累计两个方向的位移，越过阈值且**水平显著占优**才认定
                            // 「用户在调这个滑块」并进入全屏；在此之前不置位状态、也**不消费任何
                            // 位移**，页面滚动完全不受影响（按在滑块上也能正常上下滚）。
                            // 水平位移全部放行给 Slider 自己的 draggable。
                            //
                            // v4.76.10 修正：进入判定必须比较**累计水平与累计竖直**，只看累计
                            // 水平绝对值是错的——斜向滑动时水平分量同样会累积，手指本意是上下滚
                            // 页面、只要带点左右偏摆就会越过阈值误进全屏（用户实测反馈）。
                            // 现改为：水平累计量**明显超过**竖直累计量（且本身已越过阈值）才算调滑块。
                            var entered = false
                            var accX = 0f
                            var accY = 0f
                            var pointerDown = true
                            while (pointerDown) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change != null) {
                                    pointerDown = change.pressed
                                    val delta = change.position - change.previousPosition
                                    if (delta != Offset.Zero) {
                                        accX += abs(delta.x)
                                        accY += abs(delta.y)
                                        // 水平意图判定：水平累计已过阈值，且比竖直累计**显著更多**
                                        // （1.4 倍余量，足以排除斜向滑动时的水平分量干扰）。
                                        if (!entered &&
                                            accX > HORIZONTAL_DRAG_ENTER_THRESHOLD &&
                                            accX > accY * HORIZONTAL_DOMINANCE_RATIO
                                        ) {
                                            entered = true
                                            currentDragStateChange?.invoke(true)
                                        }
                                        // v4.76.11 修正：竖直位移**只在已进入水平拖动后才拦**。
                                        //
                                        // v4.76.8~4.76.10 是无条件拦竖直——目的是防「拖滑块时页面跟着
                                        // 上下窜动」，但这同时把页面滚动也挡死了：手指按在滑块上想上下
                                        // 滚页面时滑不动，只有按在非滑块区域才能滚（用户实测反馈）。
                                        //
                                        // 正解是分两阶段：
                                        //   · 未进入水平模式（!entered）：**完全不干预**，竖直位移照常
                                        //     传给外层 verticalScroll ⇒ 按在滑块上也能正常上下滚页面。
                                        //   · 已进入水平模式（entered）：才拦竖直位移 ⇒ 调滑块时页面不窜动。
                                        // 这样两个诉求同时满足，且一旦判定为水平拖动，本次手势内不再回头，
                                        // 避免中途在「滚动 / 调滑块」之间来回抖动。
                                        // （positionChange 是内部 API，故用 previousPosition 自算。）
                                        if (entered && abs(delta.y) > abs(delta.x)) change.consume()
                                    }
                                } else {
                                    pointerDown = event.changes.any { it.pressed }
                                }
                            }
                            // 只有真的进过全屏才发退出，避免无谓的状态写回。
                            if (entered) currentDragStateChange?.invoke(false)
                        }
                    }
                },
            // M3 的 thumb 设为**透明占位**：真正的 thumb 由下方 track 的 Canvas 自行绘制。
            // 这样轨道 / 已选段 / thumb 三者出自同一个纯函数（computeSliderTrackGeometry），
            // 构造上不可能错位，也不再依赖 M3 内部的 thumb 摆放公式。
            thumb = {
                Surface(
                    modifier = Modifier.size(thumbSize).alpha(0f),
                    shape = CircleShape,
                    color = Color.Transparent
                ) {}
            },
            track = { _ ->
                val density = LocalDensity.current
                // Canvas 的绘制 lambda 不是 @Composable，颜色必须在外面取好再捕获。
                val trackBase = MaterialTheme.colorScheme.primary
                val inactiveColor = trackBase.copy(alpha = 0.18f)
                val activeColor = trackBase.copy(alpha = 0.55f)
                val thumbFill = tokens.cardBg
                val thumbBorder = tokens.divider

                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().height(trackHeight)
                ) {
                    val fraction = if (range.endInclusive > range.start) {
                        ((value - range.start) / (range.endInclusive - range.start))
                            .coerceIn(0f, 1f)
                    } else 0f

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val g = computeSliderTrackGeometry(
                            canvasWidthPx = size.width,
                            trackHeightPx = size.height,
                            fraction = fraction
                        )
                        val corner = CornerRadius(g.thumbRadius)

                        // 1) 底色整条轨道
                        drawRoundRect(
                            color = inactiveColor,
                            topLeft = Offset(g.trackLeft, 0f),
                            size = Size(g.trackRight - g.trackLeft, size.height),
                            cornerRadius = corner
                        )

                        // 2) 已选段（深蓝）：左端 → thumb 圆心再延伸一个半径，与 thumb 右半圆重合
                        drawRoundRect(
                            color = activeColor,
                            topLeft = Offset(g.trackLeft, 0f),
                            size = Size(g.fillEnd - g.trackLeft, size.height),
                            cornerRadius = corner
                        )

                        // 3) thumb（白）：与轨道等高，两端恰好贴齐轨道端点
                        drawCircle(
                            color = thumbFill,
                            radius = g.thumbRadius,
                            center = Offset(g.thumbCenter, size.height / 2f)
                        )
                        drawCircle(
                            color = thumbBorder,
                            radius = g.thumbRadius,
                            center = Offset(g.thumbCenter, size.height / 2f),
                            style = Stroke(width = with(density) { 0.5.dp.toPx() })
                        )
                    }
                }
            }
        )
        }
    }

    // 专注预览态：加半透明毛玻璃底衬，让标题 / 数值 / 滑轨从课表内容中"浮起来"。
    // 颜色用 colorScheme.surface 配中等透明度（不透明色会完全挡住背后的课程块，
    // 等于把预览变成一块补丁；过透明又失去底衬作用）。
    //
    // ★ v4.76.7 关键修正（真机实测定位）：此前写成
    //     if (isFocused) Surface { ... } else Column { ... }
    // 按下滑块的瞬间 isFocused 由 false 翻成 true ⇒ **组件类型切换 ⇒ 整棵滑块子树被销毁
    // 重建** ⇒ 正在进行的拖动手势当场被取消，且新建的 Slider 立刻把状态复原。
    // 症状正是用户实测到的「按下后全屏闪一下就没了、滑块也拖不动」。
    //
    // 现在组件结构恒定（永远是 Box + Surface + 内容），底衬只靠 color 的 alpha 切换，
    // 节点不增删、不换类型 ⇒ 拖动全程不被打断。
    val backdropAlpha = if (isFocused) 0.88f else 0f
    Box(modifier = modifier) {
        // 底衬：透明时不可见也不占位（Box 叠层不改变布局尺寸）。
        Surface(
            modifier = Modifier.matchParentSize(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface.copy(alpha = backdropAlpha),
            tonalElevation = 0.dp,
            shadowElevation = if (isFocused) 6.dp else 0.dp
        ) {}
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            rowContent()
        }
    }
}

@Composable
fun StyleSwitchItem(
    label: String,
    checked: Boolean,
    /** 整行修饰符（v4.75.12）：供调用方在拖动滑块期间以 alpha 隐藏本项（不改布局尺寸）。 */
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
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
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasWallpaper = path.isNotEmpty()

    Row(
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
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
