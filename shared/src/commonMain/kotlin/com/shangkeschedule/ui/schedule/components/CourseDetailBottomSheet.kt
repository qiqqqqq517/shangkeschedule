package com.shangkeschedule.ui.schedule.components

import com.shangkeschedule.tool.TimeTextUtils
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.components.ThemedLoadingIndicator
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.schedule.MergedCourseBlock
import com.shangkeschedule.ui.settings.course.AddEditCourseViewModel
import com.shangkeschedule.ui.settings.course.ColorPickerBottomSheet
import com.shangkeschedule.ui.settings.course.CourseScheme
import com.shangkeschedule.ui.settings.course.CourseTimePickerBottomSheet
import com.shangkeschedule.ui.settings.course.CustomTimeRangePickerBottomSheet
import com.shangkeschedule.ui.settings.course.DayPickerDialog
import com.shangkeschedule.ui.settings.course.UiEvent
import com.shangkeschedule.ui.settings.course.WeekSelectorBottomSheet
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_double_week
import shangkeschedule.shared.generated.resources.action_save_changes
import shangkeschedule.shared.generated.resources.action_single_week
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.confirm_delete
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.dialog_text_delete_occurrence
import shangkeschedule.shared.generated.resources.dialog_title_delete_occurrence
import shangkeschedule.shared.generated.resources.common_action_continue_editing
import shangkeschedule.shared.generated.resources.common_action_exit_without_save
import shangkeschedule.shared.generated.resources.common_dialog_msg_unsaved_changes
import shangkeschedule.shared.generated.resources.common_dialog_title_abandon_changes
import shangkeschedule.shared.generated.resources.label_assessment_method
import shangkeschedule.shared.generated.resources.label_course_color
import shangkeschedule.shared.generated.resources.label_credit
import shangkeschedule.shared.generated.resources.label_custom_time
import shangkeschedule.shared.generated.resources.label_day_of_week
import shangkeschedule.shared.generated.resources.label_is_lab
import shangkeschedule.shared.generated.resources.label_remark
import shangkeschedule.shared.generated.resources.label_section_range_suffix
import shangkeschedule.shared.generated.resources.toast_name_empty
import shangkeschedule.shared.generated.resources.toast_save_success
import shangkeschedule.shared.generated.resources.toast_time_invalid
import shangkeschedule.shared.generated.resources.today_meta_room
import shangkeschedule.shared.generated.resources.today_meta_teacher
import shangkeschedule.shared.generated.resources.today_meta_time
import shangkeschedule.shared.generated.resources.today_meta_weeks
import shangkeschedule.shared.generated.resources.today_sheet_close
import shangkeschedule.shared.generated.resources.today_sheet_delete_occurrence
import shangkeschedule.shared.generated.resources.today_sheet_edit
import shangkeschedule.shared.generated.resources.week_days_full_names

/**
 * 课程详情弹窗（v4.62.0 起支持弹窗内就地编辑；v4.63.0 起编辑态可「只删本次」）。
 *
 * 打开即只读预览，版式与原本完全一致：「星期」小节标题 + 课程名大标题，圆角容器内
 * label/value 分隔行，底部「关闭 / 编辑课程」。点「编辑课程」后同一弹窗切换为编辑态：
 * 课程名/地点/教师/学分/考核方式/备注为行内输入框，上课时间/周次/颜色/星期为可点行，
 * 实验课与自定义时间为开关行，底部「取消 / 保存更改」。crush 课程恒为只读，仅「关闭」。
 *
 * v4.63.0 起编辑态右上角（课程名输入框同一行）多一个「只删本次」圆形图标按钮：只删除本课程在
 * **当前展示这一周**的这一次课（切断该周次关联），课程在其它周次的排课照常保留；若这已是它最后一次
 * 出现，则整条课程记录一并删除。该按钮仅在课程确实排在被展示的这一周时出现（弱化的非本周课程
 * 块不显示），crush 课程仍为只读、没有编辑态、同样不显示。
 *
 * 复用 [AddEditCourseViewModel]，多方案、周次、时间、配色、学分等字段与整页编辑
 * （[com.shangkeschedule.ui.settings.course.AddEditCourseScreen]）走同一套保存逻辑；
 * 本弹窗编辑的是被点击的那一个方案（方案 id 即课程行 id），不再跳转整页。
 *
 * 生命周期：编辑态自带一个 [ViewModelStore]，随编辑态建立、退出编辑或关闭时
 * [ViewModelStore.clear]，因此每次进入编辑都是全新 ViewModel 实例（不复用上一次编辑态，
 * 也不会向页面级 ViewModelStore 泄漏实例，同时结束其无限 combine 收集协程）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailBottomSheet(
    block: MergedCourseBlock,
    onDismissRequest: () -> Unit,
    onSaved: () -> Unit,
    currentWeek: Int? = null,
    onDeleteOccurrence: (Int) -> Unit = {},
    hazeState: dev.chrisbanes.haze.HazeState? = null
) {
    val courseWrapper = block.courses.firstOrNull() ?: return
    val course = courseWrapper.course

    // 打开即为只读预览（与原本一致），点「编辑课程」才切到就地编辑；crush 课程恒为只读
    var isEditing by remember(course.id) { mutableStateOf(false) }

    if (course.isCrush || !isEditing) {
        // 只读预览版式：crush 课程与原详情弹窗完全一致；普通课程多一个「编辑课程」入口
        val colors = appColors()
        val weeksDisplayStr = formatWeeks(courseWrapper.weeks.map { it.weekNumber })

        val weekDaysFullNames = stringArrayResource(Res.array.week_days_full_names)
        val dayStr = remember(course.day, weekDaysFullNames) {
            weekDaysFullNames.getOrNull(course.day - 1) ?: ""
        }

        val labelTime = stringResource(Res.string.today_meta_time)
        val labelRoom = stringResource(Res.string.today_meta_room)
        val labelTeacher = stringResource(Res.string.today_meta_teacher)
        val labelWeeks = stringResource(Res.string.today_meta_weeks)
        val labelCredit = stringResource(Res.string.label_credit)
        val labelAssessment = stringResource(Res.string.label_assessment_method)
        val labelRemark = stringResource(Res.string.label_remark)
        val labelIsLab = stringResource(Res.string.label_is_lab)
        val textClose = stringResource(Res.string.today_sheet_close)
        val textEdit = stringResource(Res.string.today_sheet_edit)

        val sectionSuffix = stringResource(Res.string.label_section_range_suffix)
        val timeStr = if (course.isCustomTime) {
            "${course.customStartTime} - ${course.customEndTime}"
        } else {
            "${course.startSection ?: 0}-${course.endSection ?: 0} $sectionSuffix"
        }

        AppGlassBottomSheet(
            hazeState = hazeState,
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 40.dp)
            ) {
                Text(
                    text = dayStr,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.1.em
                    ),
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(appSpacing().cardGap))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(appShapes().chip)
                        .border(1.dp, colors.divider, appShapes().chip)
                ) {
                    DetailRow(label = labelTime, value = timeStr)
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                    if (course.position.isNotBlank()) {
                        DetailRow(label = labelRoom, value = course.position)
                        HorizontalDivider(thickness = 1.dp, color = colors.divider)
                    }
                    if (course.teacher.isNotBlank()) {
                        DetailRow(label = labelTeacher, value = course.teacher)
                        HorizontalDivider(thickness = 1.dp, color = colors.divider)
                    }
                    if (weeksDisplayStr.isNotEmpty()) {
                        DetailRow(label = labelWeeks, value = weeksDisplayStr)
                        HorizontalDivider(thickness = 1.dp, color = colors.divider)
                    }
                    if (!course.credit.isNullOrBlank()) {
                        DetailRow(label = labelCredit, value = course.credit)
                        HorizontalDivider(thickness = 1.dp, color = colors.divider)
                    }
                    if (!course.assessmentMethod.isNullOrBlank()) {
                        DetailRow(label = labelAssessment, value = course.assessmentMethod)
                    }
                    if (course.isLab) {
                        DetailRow(label = labelIsLab, value = "✓")
                    }
                    if (!course.remark.isNullOrBlank()) {
                        DetailRow(label = labelRemark, value = course.remark)
                    }
                }

                Spacer(modifier = Modifier.height(appSpacing().sectionGap))

                if (course.isCrush) {
                    // crush 课程无编辑入口，仅「关闭」
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(appShapes().chip)
                            .background(colors.inputBg)
                            .clickable(onClick = onDismissRequest),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = textClose,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colors.textPrimary
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(appShapes().chip)
                                .background(colors.inputBg)
                                .clickable(onClick = onDismissRequest),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = textClose,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colors.textPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(appShapes().chip)
                                .background(colors.primary)
                                .clickable { isEditing = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = textEdit,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colors.textOnPrimary
                            )
                        }
                    }
                }
            }
        }
    } else {
        EditableCourseDetailSheet(
            courseId = course.id,
            fallbackName = course.name,
            fallbackDay = course.day,
            currentWeek = currentWeek,
            onDismissRequest = onDismissRequest,
            onSaved = onSaved,
            onDeleteOccurrence = onDeleteOccurrence,
            onCancelEditing = { isEditing = false }
        )
    }
}

/**
 * 可编辑详情弹窗外壳：仅负责弹层专属 ViewModelStore 与 ViewModel 解析。
 */
@Composable
private fun EditableCourseDetailSheet(
    courseId: String,
    fallbackName: String,
    fallbackDay: Int,
    currentWeek: Int?,
    onDismissRequest: () -> Unit,
    onSaved: () -> Unit,
    onDeleteOccurrence: (Int) -> Unit,
    onCancelEditing: () -> Unit
) {
    // 弹层专属 ViewModelStore：实例随编辑态开关而生灭，避免与整页编辑共用实例导致状态串味
    val sheetStore = remember { ViewModelStore() }
    DisposableEffect(sheetStore) {
        onDispose { sheetStore.clear() }
    }
    val sheetStoreOwner = remember(sheetStore) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = sheetStore
        }
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides sheetStoreOwner) {
        val viewModel: AddEditCourseViewModel = koinViewModel()
        EditableCourseDetailContent(
            courseId = courseId,
            fallbackName = fallbackName,
            fallbackDay = fallbackDay,
            currentWeek = currentWeek,
            viewModel = viewModel,
            onDismissRequest = onDismissRequest,
            onSaved = onSaved,
            onDeleteOccurrence = onDeleteOccurrence,
            onCancelEditing = onCancelEditing
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableCourseDetailContent(
    courseId: String,
    fallbackName: String,
    fallbackDay: Int,
    currentWeek: Int?,
    viewModel: AddEditCourseViewModel,
    onDismissRequest: () -> Unit,
    onSaved: () -> Unit,
    onDeleteOccurrence: (Int) -> Unit,
    onCancelEditing: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 打开即回到数据库最新值（保存后新增方案会拿到新的 dbId，避免二次保存写入重复课程行）
    LaunchedEffect(courseId) { viewModel.reload(courseId) }

    val colors = appColors()
    val dayNames = stringArrayResource(Res.array.week_days_full_names)
    val sectionSuffix = stringResource(Res.string.label_section_range_suffix)
    val saveSuccessText = stringResource(Res.string.toast_save_success)
    val nameEmptyText = stringResource(Res.string.toast_name_empty)
    val timeInvalidText = stringResource(Res.string.toast_time_invalid)

    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var activeSchemeId by remember { mutableStateOf<String?>(null) }
    var showWeekSelector by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 被点击的课程就是本弹层的编辑对象。故意不做「找不到就退回第一个方案」的兜底：
    // 那会在 id 不匹配时静默编辑另一门课，属于数据错改；数据已加载却找不到该课程时直接关闭弹层
    val scheme: CourseScheme? = uiState.schemes.find { it.id == courseId }

    LaunchedEffect(uiState.isDataLoaded, scheme) {
        if (uiState.isDataLoaded && scheme == null) onDismissRequest()
    }

    // 「只删本次」的可行周次（v4.63.0）：只删除当前展示这一周的这一次课。
    // 仅当该课程（按正在编辑的周次）确实排在被展示这一周时才提供删除，否则点下去删不掉任何东西；
    // crush 课程没有编辑态，自然不会走到这里。
    val occurrenceWeek = currentWeek?.takeIf { week -> scheme?.weeks?.contains(week) == true }
    var showDeleteOccurrenceConfirm by remember(courseId, occurrenceWeek) { mutableStateOf(false) }

    fun timeLabel(s: CourseScheme): String {
        if (s.isCustomTime) {
            return "${s.customStartTime.ifBlank { "00:00" }}-${s.customEndTime.ifBlank { "00:00" }}"
        }
        val startAlias = uiState.timeSlots.find { it.number == s.startSection }?.alias
            ?: s.startSection.toString()
        val endAlias = uiState.timeSlots.find { it.number == s.endSection }?.alias
            ?: s.endSection.toString()
        val day = dayNames.getOrNull(s.day - 1).orEmpty()
        val range = if (startAlias == endAlias) "$startAlias $sectionSuffix" else "$startAlias-$endAlias $sectionSuffix"
        return if (day.isBlank()) range else "$day $range"
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                UiEvent.SaveSuccess -> {
                    ToastManager.show(saveSuccessText)
                    onSaved()
                }
                // 本弹层的「只删本次」不经过该事件（直接调用课表页的仓储删除）；此处仅为兜底
                UiEvent.DeleteSuccess -> onSaved()
                UiEvent.Cancel -> onCancelEditing()
            }
        }
    }

    // 未保存时手势/遮罩关闭：M3 会先把面板收起再回调 onDismissRequest，
    // 故此处把面板回弹回去并弹出确认框，避免用户误触丢失编辑内容
    LaunchedEffect(showExitConfirmDialog) {
        if (showExitConfirmDialog) sheetState.show()
    }

    // 编辑态下「取消」/手势关闭：有改动先确认，确认后丢弃改动回到只读预览；无改动直接回预览
    val requestCancelEditing = {
        if (uiState.isDataLoaded && viewModel.hasUnsavedChanges()) {
            showExitConfirmDialog = true
        } else {
            onCancelEditing()
        }
    }

    val requestSave = {
        if (uiState.name.isBlank()) {
            ToastManager.show(nameEmptyText)
        } else {
            val allValid = uiState.schemes.all { s ->
                if (s.isCustomTime) {
                    s.customStartTime.isNotBlank() && s.customEndTime.isNotBlank() && s.customStartTime < s.customEndTime
                } else {
                    s.startSection <= s.endSection
                }
            }
            if (allValid) viewModel.onSave() else ToastManager.show(timeInvalidText)
        }
    }

    AppGlassBottomSheet(
        // 弹层内没有 hazeSource 背板，传 null 退化为实色面板（与今日日程详情弹窗同一处理，
        // 避免玻璃分支内层蒙层未裁剪到 sheetTop 圆角）
        hazeState = null,
        onDismissRequest = requestCancelEditing,
        sheetState = sheetState
    ) {
        // 高度随内容自适应：内容少时面板自然收拢（不再固定 0.88 屏高，下方不留空白），
        // 内容超过上限时行区域内部滚动，底部按钮始终可见
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            val dayIndex = (scheme?.day ?: fallbackDay) - 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dayNames.getOrNull(dayIndex).orEmpty(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.1.em
                        ),
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // 大标题即课程名输入框（与只读版式的标题同一版式，暗色无边框）
                    BasicTextField(
                        value = if (uiState.isDataLoaded) uiState.name else fallbackName,
                        onValueChange = viewModel::onNameChange,
                        enabled = uiState.isDataLoaded,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        ),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // 「只删本次」按钮：编辑态右上角的实心圆形图标按钮（不是文字说明）。
                // 点按后先二次确认，确认即删除本课程在当前展示这一周的这一次课。
                if (occurrenceWeek != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.dangerSoft)
                            .clickable { showDeleteOccurrenceConfirm = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.delete_24px),
                            contentDescription = stringResource(
                                Res.string.today_sheet_delete_occurrence
                            ),
                            modifier = Modifier.size(18.dp),
                            tint = colors.danger
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(appSpacing().cardGap))

            if (!uiState.isDataLoaded || scheme == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ThemedLoadingIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // fill = false：只占内容高度，剩余空间不再撑开面板造成底部留白
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(appShapes().chip)
                            .border(1.dp, colors.divider, appShapes().chip)
                    ) {
                        DetailActionRow(
                            label = stringResource(Res.string.today_meta_time),
                            onClick = {
                                activeSchemeId = scheme.id
                                showTimePicker = true
                            }
                        ) {
                            DetailValueText(text = timeLabel(scheme))
                        }
                        HorizontalDivider(thickness = 1.dp, color = colors.divider)

                        EditableDetailRow(
                            label = stringResource(Res.string.today_meta_room),
                            value = scheme.position,
                            onValueChange = { newValue ->
                                viewModel.updateScheme(scheme.id) { it.copy(position = newValue) }
                            }
                        )
                        HorizontalDivider(thickness = 1.dp, color = colors.divider)

                        EditableDetailRow(
                            label = stringResource(Res.string.today_meta_teacher),
                            value = scheme.teacher,
                            onValueChange = { newValue ->
                                viewModel.updateScheme(scheme.id) { it.copy(teacher = newValue) }
                            }
                        )
                        HorizontalDivider(thickness = 1.dp, color = colors.divider)

                        DetailActionRow(
                            label = stringResource(Res.string.today_meta_weeks),
                            onClick = {
                                activeSchemeId = scheme.id
                                showWeekSelector = true
                            }
                        ) {
                            DetailValueText(text = formatWeeks(scheme.weeks.sorted()))
                        }
                        HorizontalDivider(thickness = 1.dp, color = colors.divider)

                        EditableDetailRow(
                            label = stringResource(Res.string.label_credit),
                            value = uiState.credit,
                            onValueChange = viewModel::onCreditChange
                        )
                        HorizontalDivider(thickness = 1.dp, color = colors.divider)

                        EditableDetailRow(
                            label = stringResource(Res.string.label_assessment_method),
                            value = uiState.assessmentMethod,
                            onValueChange = viewModel::onAssessmentMethodChange
                        )
                        HorizontalDivider(thickness = 1.dp, color = colors.divider)

                        DetailSwitchRow(
                            label = stringResource(Res.string.label_is_lab),
                            checked = uiState.isLab,
                            onCheckedChange = viewModel::onIsLabChange
                        )
                        HorizontalDivider(thickness = 1.dp, color = colors.divider)

                        EditableDetailRow(
                            label = stringResource(Res.string.label_remark),
                            value = scheme.remark,
                            onValueChange = { newRemark ->
                                viewModel.onSchemeRemarkChange(scheme.id, newRemark)
                            },
                            singleLine = false,
                            maxLines = 5
                        )
                        // 备注字数上限（与整页编辑同一 300 字约束）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.cardBgElevated)
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${scheme.remark.length} / 300",
                                modifier = Modifier.align(Alignment.CenterEnd),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = colors.divider)

                        DetailActionRow(
                            label = stringResource(Res.string.label_course_color),
                            onClick = {
                                activeSchemeId = scheme.id
                                showColorPicker = true
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        uiState.courseColorMaps.getOrNull(scheme.colorIndex)?.light
                                            ?: Color.Transparent
                                    )
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = colors.divider)

                        DetailSwitchRow(
                            label = stringResource(Res.string.label_custom_time),
                            checked = scheme.isCustomTime,
                            onCheckedChange = { isCustom ->
                                viewModel.toggleCustomTime(scheme.id, isCustom)
                            }
                        )

                        // 自定义时间模式下，星期单独成行（节次行已由时间选择器承担）
                        if (scheme.isCustomTime) {
                            HorizontalDivider(thickness = 1.dp, color = colors.divider)
                            DetailActionRow(
                                label = stringResource(Res.string.label_day_of_week),
                                onClick = {
                                    activeSchemeId = scheme.id
                                    showDayPicker = true
                                }
                            ) {
                                DetailValueText(text = dayNames.getOrNull(scheme.day - 1).orEmpty())
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(appSpacing().sectionGap))
                }
            }

            // 底部操作区：与只读版式同一套按钮语言
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = appSpacing().cardGap),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(appShapes().chip)
                        .background(colors.inputBg)
                        .clickable(onClick = requestCancelEditing),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.textPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(appShapes().chip)
                        .background(colors.primary)
                        .clickable(onClick = requestSave),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.action_save_changes),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.textOnPrimary
                    )
                }
            }
        }
    }

    // --- 选择器：与本弹层正在编辑的方案 id 绑定 ---
    val activeScheme = uiState.schemes.find { it.id == activeSchemeId }
    if (activeScheme != null) {
        if (showWeekSelector) {
            WeekSelectorBottomSheet(
                totalWeeks = uiState.semesterTotalWeeks,
                selectedWeeks = activeScheme.weeks,
                onDismissRequest = { showWeekSelector = false },
                onConfirm = { weeks: Set<Int> ->
                    viewModel.updateScheme(activeScheme.id) { it.copy(weeks = weeks) }
                    showWeekSelector = false
                },
                hazeState = null
            )
        }
        if (showColorPicker) {
            ColorPickerBottomSheet(
                colorMaps = uiState.courseColorMaps,
                selectedIndex = activeScheme.colorIndex,
                onDismissRequest = { showColorPicker = false },
                onConfirm = { index: Int ->
                    viewModel.updateScheme(activeScheme.id) { it.copy(colorIndex = index) }
                    showColorPicker = false
                },
                hazeState = null
            )
        }
        if (showTimePicker) {
            if (activeScheme.isCustomTime) {
                CustomTimeRangePickerBottomSheet(
                    initialStartTime = activeScheme.customStartTime.ifBlank { TimeTextUtils.DEFAULT_CUSTOM_START_TIME },
                    initialEndTime = activeScheme.customEndTime.ifBlank { "09:45" },
                    onDismissRequest = { showTimePicker = false },
                    onTimeRangeSelected = { start, end ->
                        viewModel.updateScheme(activeScheme.id) {
                            it.copy(customStartTime = start, customEndTime = end)
                        }
                        showTimePicker = false
                    },
                    hazeState = null
                )
            } else {
                CourseTimePickerBottomSheet(
                    selectedDay = activeScheme.day,
                    onDaySelected = { d -> viewModel.updateScheme(activeScheme.id) { it.copy(day = d) } },
                    startSection = activeScheme.startSection,
                    onStartSectionChange = { s ->
                        viewModel.updateScheme(activeScheme.id) { it.copy(startSection = s) }
                    },
                    endSection = activeScheme.endSection,
                    onEndSectionChange = { e ->
                        viewModel.updateScheme(activeScheme.id) { it.copy(endSection = e) }
                    },
                    timeSlots = uiState.timeSlots,
                    onDismissRequest = { showTimePicker = false },
                    hazeState = null
                )
            }
        }
        if (showDayPicker) {
            DayPickerDialog(
                selectedDay = activeScheme.day,
                onDismissRequest = { showDayPicker = false },
                onDaySelected = { newDay ->
                    viewModel.updateScheme(activeScheme.id) { it.copy(day = newDay) }
                    showDayPicker = false
                }
            )
        }
    }

    if (showExitConfirmDialog) {
        AppAlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = {
                Text(text = stringResource(Res.string.common_dialog_title_abandon_changes))
            },
            text = {
                Text(text = stringResource(Res.string.common_dialog_msg_unsaved_changes))
            },
            confirmButton = {
                AppDialogActions(
                    confirmText = stringResource(Res.string.common_action_exit_without_save),
                    onConfirm = {
                        showExitConfirmDialog = false
                        onCancelEditing()
                    },
                    dismissText = stringResource(Res.string.common_action_continue_editing),
                    onDismiss = { showExitConfirmDialog = false },
                    danger = true
                )
            },
            dismissButton = {}
        )
    }

    // 「只删本次」二次确认（删除不可撤销）：只影响当前展示这一周的这一次课
    if (showDeleteOccurrenceConfirm && occurrenceWeek != null) {
        AppDangerDialog(
            onDismissRequest = { showDeleteOccurrenceConfirm = false },
            title = stringResource(Res.string.dialog_title_delete_occurrence),
            text = stringResource(
                Res.string.dialog_text_delete_occurrence,
                uiState.name.ifBlank { fallbackName },
                occurrenceWeek
            ),
            confirmText = stringResource(Res.string.confirm_delete),
            onConfirm = {
                showDeleteOccurrenceConfirm = false
                onDeleteOccurrence(occurrenceWeek)
            },
            dismissText = stringResource(Res.string.action_cancel),
            onDismiss = { showDeleteOccurrenceConfirm = false }
        )
    }
}

/**
 * 只读样式的 label/value 行（crush 详情版式沿用）。
 */
@Composable
private fun DetailRow(label: String, value: String) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cardBgElevated)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.textPrimary,
            textAlign = TextAlign.End
        )
    }
}

/**
 * 可编辑的 label/value 行：整行可点，点击即聚焦右侧输入框；版式与 [DetailRow] 一致
 * （同底色、同内边距、同字号），因此弹窗观感保持不变。
 */
@Composable
private fun EditableDetailRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5
) {
    val colors = appColors()
    val focusRequester = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cardBgElevated)
            .clickable { focusRequester.requestFocus() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = "—",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary
                                )
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
    }
}

/**
 * 可点行（上课时间 / 上课周次 / 课程颜色 / 星期）：右侧值 + 箭头，点击展开对应选择器。
 */
@Composable
private fun DetailActionRow(
    label: String,
    onClick: () -> Unit,
    value: @Composable () -> Unit
) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cardBgElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.weight(1f))
        value()
        Spacer(modifier = Modifier.size(6.dp))
        Icon(
            imageVector = vectorResource(Res.drawable.chevron_right_24px),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.textSecondary
        )
    }
}

/**
 * 开关行（实验课 / 自定义时间）。
 */
@Composable
private fun DetailSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(colors.cardBgElevated)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
        // 开关单元格压到与普通行等高：M3 默认 48dp 触摸靶在详情行里过高，
        // 用固定行高 + scale 收视觉，触摸区域随行高收缩
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.85f)
        )
    }
}

/**
 * 可点行的值文本（只读展示，与 [DetailRow] 的值同一版式）。
 */
@Composable
private fun DetailValueText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = appColors().textPrimary,
        textAlign = TextAlign.End
    )
}

@Composable
internal fun formatWeeks(weeks: List<Int>): String {
    if (weeks.isEmpty()) return ""
    val sorted = weeks.distinct().sorted()
    val result = mutableListOf<String>()

    val singleLabel = stringResource(Res.string.action_single_week)
    val doubleLabel = stringResource(Res.string.action_double_week)

    var i = 0
    while (i < sorted.size) {
        // 识别等差序列（单双周）
        if (i + 1 < sorted.size && sorted[i + 1] - sorted[i] == 2) {
            var k = i
            while (k + 1 < sorted.size && sorted[k + 1] - sorted[k] == 2) {
                k++
            }
            val suffix = if (sorted[i] % 2 != 0) singleLabel else doubleLabel
            result.add("${sorted[i]}-${sorted[k]}($suffix)")
            i = k + 1
        }
        // 识别连续区间
        else if (i + 1 < sorted.size && sorted[i + 1] == sorted[i] + 1) {
            val start = sorted[i]
            var k = i
            while (k + 1 < sorted.size && sorted[k + 1] == sorted[k] + 1) {
                k++
            }
            result.add("${start}-${sorted[k]}")
            i = k + 1
        }
        // 孤立的周次
        else {
            result.add("${sorted[i]}")
            i++
        }
    }
    return result.joinToString(", ")
}
