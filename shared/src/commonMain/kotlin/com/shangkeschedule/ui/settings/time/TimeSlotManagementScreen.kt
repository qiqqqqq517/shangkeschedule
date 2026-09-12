package com.shangkeschedule.ui.settings.time

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.main.TimeSlotScheme
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.data.repository.DEFAULT_TIME_SLOTS
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.AppCard
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppEmptyState
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.NativeNumberPicker
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.iosGlassRim
import com.shangkeschedule.ui.theme.softSurface
import com.shangkeschedule.ui.theme.claudeDisplaySerif
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_add_time_slot
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.a11y_delete_scheme
import shangkeschedule.shared.generated.resources.action_restore_default
import shangkeschedule.shared.generated.resources.auto_switch_item_title
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.confirm_delete
import shangkeschedule.shared.generated.resources.dialog_text_confirm_delete_scheme
import shangkeschedule.shared.generated.resources.dialog_title_confirm_delete_course
import shangkeschedule.shared.generated.resources.a11y_delete_time_slot
import shangkeschedule.shared.generated.resources.a11y_save_all_settings
import shangkeschedule.shared.generated.resources.action_add
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_delete
import shangkeschedule.shared.generated.resources.action_new_scheme
import shangkeschedule.shared.generated.resources.action_save_changes
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.check_24px
import shangkeschedule.shared.generated.resources.common_action_continue_editing
import shangkeschedule.shared.generated.resources.common_action_exit_without_save
import shangkeschedule.shared.generated.resources.common_dialog_msg_unsaved_changes
import shangkeschedule.shared.generated.resources.common_dialog_title_abandon_changes
import shangkeschedule.shared.generated.resources.default_scheme_name
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.edit_24px
import shangkeschedule.shared.generated.resources.dialog_title_add_time_slot
import shangkeschedule.shared.generated.resources.dialog_title_edit_time_slot
import shangkeschedule.shared.generated.resources.dialog_title_new_scheme
import shangkeschedule.shared.generated.resources.hint_scheme_dates_cross_year
import shangkeschedule.shared.generated.resources.hint_scheme_in_use
import shangkeschedule.shared.generated.resources.hint_scheme_name
import shangkeschedule.shared.generated.resources.label_break_duration_minutes
import shangkeschedule.shared.generated.resources.label_class_duration_minutes
import shangkeschedule.shared.generated.resources.label_scheme_name
import shangkeschedule.shared.generated.resources.label_time_picker_end
import shangkeschedule.shared.generated.resources.label_time_picker_hour
import shangkeschedule.shared.generated.resources.label_time_picker_minute
import shangkeschedule.shared.generated.resources.label_time_picker_start
import shangkeschedule.shared.generated.resources.label_time_slot_alias
import shangkeschedule.shared.generated.resources.save_24px
import shangkeschedule.shared.generated.resources.text_no_time_slots_hint
import shangkeschedule.shared.generated.resources.time_slot_section_number
import shangkeschedule.shared.generated.resources.title_time_slot_list
import shangkeschedule.shared.generated.resources.title_default_duration_settings
import shangkeschedule.shared.generated.resources.title_scheme_selector
import shangkeschedule.shared.generated.resources.title_time_slot_management
import shangkeschedule.shared.generated.resources.toast_break_duration_non_negative
import shangkeschedule.shared.generated.resources.toast_restored_default
import shangkeschedule.shared.generated.resources.toast_class_duration_positive
import shangkeschedule.shared.generated.resources.toast_end_time_must_be_later
import shangkeschedule.shared.generated.resources.toast_scheme_name_duplicate
import shangkeschedule.shared.generated.resources.toast_scheme_name_empty
import shangkeschedule.shared.generated.resources.toast_settings_saved
import shangkeschedule.shared.generated.resources.toast_slot_added_unsaved
import shangkeschedule.shared.generated.resources.toast_slot_modified_unsaved
import shangkeschedule.shared.generated.resources.toast_slot_removed_unsaved
import shangkeschedule.shared.generated.resources.toast_time_conflict
import shangkeschedule.shared.generated.resources.a11y_edit_scheme_dates
import shangkeschedule.shared.generated.resources.action_clear_dates
import shangkeschedule.shared.generated.resources.action_edit_scheme_dates
import shangkeschedule.shared.generated.resources.desc_auto_switch_scheme
import shangkeschedule.shared.generated.resources.dialog_title_scheme_dates
import shangkeschedule.shared.generated.resources.label_day
import shangkeschedule.shared.generated.resources.label_end_month_day
import shangkeschedule.shared.generated.resources.label_month
import shangkeschedule.shared.generated.resources.label_start_month_day
import shangkeschedule.shared.generated.resources.text_scheme_dates_range
import shangkeschedule.shared.generated.resources.text_scheme_dates_range_cross_year
import shangkeschedule.shared.generated.resources.text_scheme_dates_unset
import shangkeschedule.shared.generated.resources.title_auto_switch_scheme
import shangkeschedule.shared.generated.resources.toast_scheme_dates_incomplete

/**
 * 时间段管理界面的 Compose UI。
 *
 * @param onBack 返回上一页的回调。
 * @param timeSlotViewModel ViewModel，负责管理 UI 状态和业务逻辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSlotManagementScreen(
    onBack: () -> Unit,
    targetCourseTableId: String? = null,
    timeSlotViewModel: TimeSlotViewModel = koinViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by timeSlotViewModel.timeSlotsUiState.collectAsState()

    LaunchedEffect(targetCourseTableId) {
        timeSlotViewModel.initWithTargetTable(targetCourseTableId)
    }

    val localTimeSlots = remember {
        mutableStateListOf<TimeSlot>().apply { addAll(uiState.timeSlots.sortedBy { it.number }) }
    }
    var localDefaultClassDuration by remember { mutableIntStateOf(uiState.defaultClassDuration) }
    var localDefaultBreakDuration by remember { mutableIntStateOf(uiState.defaultBreakDuration) }

    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showCreateSchemeDialog by remember { mutableStateOf(false) }
    var showSchemeDatesDialog by remember { mutableStateOf(false) }
    // 删除作息方案二次确认（删除不可撤销）
    var pendingDeleteSchemeId by remember { mutableStateOf<String?>(null) }
    var editingSchemeForDates by remember { mutableStateOf<String?>(null) }

    val titleTimeSlotManagement = stringResource(Res.string.title_time_slot_management)
    val a11yBack = stringResource(Res.string.a11y_back)
    val a11yAddTimeSlot = stringResource(Res.string.a11y_add_time_slot)
    val a11ySaveAllSettings = stringResource(Res.string.a11y_save_all_settings)
    val toastSettingsSaved = stringResource(Res.string.toast_settings_saved)
    val toastSlotRemovedUnsaved = stringResource(Res.string.toast_slot_removed_unsaved)
    val textNoTimeSlotsHint = stringResource(Res.string.text_no_time_slots_hint)
    val toastSlotModifiedUnsaved = stringResource(Res.string.toast_slot_modified_unsaved)
    val toastSlotAddedUnsaved = stringResource(Res.string.toast_slot_added_unsaved)
    val toastSchemeNameEmpty = stringResource(Res.string.toast_scheme_name_empty)
    val toastSchemeNameDuplicate = stringResource(Res.string.toast_scheme_name_duplicate)

    // 数据加载同步逻辑
    LaunchedEffect(uiState) {
        if (uiState.isDataLoaded) {
            localTimeSlots.clear()
            localTimeSlots.addAll(uiState.timeSlots.sortedBy { it.number })
            localDefaultClassDuration = uiState.defaultClassDuration
            localDefaultBreakDuration = uiState.defaultBreakDuration
        }
    }

    /**
     * 核心拦截逻辑：判断是否有变更，决定直接返回还是弹窗
     */
    val handleBackPress = {
        val hasChanged = timeSlotViewModel.hasUnsavedChanges(
            currentTimeSlots = localTimeSlots.toList(),
            currentClassDuration = localDefaultClassDuration,
            currentBreakDuration = localDefaultBreakDuration
        )
        if (hasChanged) {
            showExitConfirmDialog = true
        } else {
            onBack()
        }
    }

    val navEventState = rememberNavigationEventState(
        currentInfo = NavigationEventInfo.None
    )

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = true,
        onBackCompleted = {
            handleBackPress()
        }
    )

    var showEditBottomSheet by remember { mutableStateOf(false) }
    var editingTimeSlot by remember { mutableStateOf<TimeSlot?>(null) }

    // 悬浮面板玻璃：主内容 hazeSource，编辑面板背板模糊
    val hazeState = rememberHazeState()

    Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(titleTimeSlotManagement) },
                navigationIcon = {
                    IconButton(onClick = handleBackPress) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = a11yBack)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingTimeSlot = null
                        showEditBottomSheet = true
                    }) {
                        Icon(vectorResource(Res.drawable.add_24px), contentDescription = a11yAddTimeSlot)
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val sortedAndNumberedSlots = localTimeSlots
                                .sortedBy { parseLocalTimeSafely(it.startTime) }
                                .mapIndexed { index, slot -> slot.copy(number = index + 1) }

                            timeSlotViewModel.onSaveAllSettings(
                                timeSlots = sortedAndNumberedSlots,
                                classDuration = localDefaultClassDuration,
                                breakDuration = localDefaultBreakDuration,
                                onSuccess = {
                                    ToastManager.show(toastSettingsSaved)
                                }
                            )
                        }
                    }) {
                        Icon(vectorResource(Res.drawable.save_24px), contentDescription = a11ySaveAllSettings)
                    }
                }
            )
        }
    ) { paddingValues ->
        // 宽屏限宽：内容列最大 640dp 居中（对齐设置页多端适配约定）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = appSpacing().pageHorizontal, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SchemeSelector(
                    currentSchemeId = uiState.currentSchemeId,
                    schemeIds = uiState.schemeIds,
                    schemeMetas = uiState.schemeMetas,
                    onSwitch = { schemeId -> timeSlotViewModel.onSwitchScheme(schemeId) },
                    onCreate = { showCreateSchemeDialog = true },
                    onDelete = { schemeId -> pendingDeleteSchemeId = schemeId },
                    onEditDates = { schemeId ->
                        editingSchemeForDates = schemeId
                        showSchemeDatesDialog = true
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
                AutoSwitchToggle(
                    enabled = uiState.autoSwitchScheme,
                    onToggle = { timeSlotViewModel.onToggleAutoSwitch(it) }
                )
                Spacer(modifier = Modifier.height(24.dp))
                DefaultDurationSettings(
                    defaultClassDuration = localDefaultClassDuration,
                    onClassDurationChange = { newValue -> localDefaultClassDuration = newValue },
                    defaultBreakDuration = localDefaultBreakDuration,
                    onBreakDurationChange = { newValue -> localDefaultBreakDuration = newValue }
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (localTimeSlots.isEmpty()) {
                    // 统一空状态
                    AppEmptyState(hint = textNoTimeSlotsHint)
                } else {
                    AppSectionHeader(stringResource(Res.string.title_time_slot_list))
                }
            }

            itemsIndexed(localTimeSlots, key = { _, slot -> "${slot.number}-${slot.startTime}" }) { _, timeSlot ->
                TimeSlotItem(
                    timeSlot = timeSlot,
                    onEditClick = {
                        editingTimeSlot = timeSlot
                        showEditBottomSheet = true
                    },
                    onDeleteClick = {
                        localTimeSlots.removeAll { it.number == timeSlot.number }
                        val renumberedList = localTimeSlots
                            .sortedBy { parseLocalTimeSafely(it.startTime) }
                            .mapIndexed { i, slot -> slot.copy(number = i + 1) }
                        localTimeSlots.clear()
                        localTimeSlots.addAll(renumberedList)
                        ToastManager.show(toastSlotRemovedUnsaved)
                    }
                )
            }

            // 底部「恢复默认」按钮：将本地节次列表替换为默认 13 节模板（保存后生效）
            item {
                val toastRestoredDefault = stringResource(Res.string.toast_restored_default)
                Spacer(modifier = Modifier.height(12.dp))
                RestoreDefaultButton(
                    onClick = {
                        localTimeSlots.clear()
                        localTimeSlots.addAll(
                            DEFAULT_TIME_SLOTS.map {
                                TimeSlot(
                                    number = it.number,
                                    startTime = it.startTime,
                                    endTime = it.endTime,
                                    courseTableId = ""
                                )
                            }
                        )
                        ToastManager.show(toastRestoredDefault)
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
        }

        if (showEditBottomSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val isEditing = editingTimeSlot != null
            val (initialStart, initialEnd) = calculateInitialTimes(
                isEditing,
                editingTimeSlot,
                localTimeSlots,
                localDefaultBreakDuration,
                localDefaultClassDuration
            )

            AppGlassBottomSheet(
                hazeState = hazeState,
                onDismissRequest = {
                    showEditBottomSheet = false
                    editingTimeSlot = null
                },
                sheetState = sheetState
            ) {
                TimeSlotEditContent(
                    existingTimeSlots = localTimeSlots.toList(),
                    initialNumber = editingTimeSlot?.number ?: (localTimeSlots.maxOfOrNull { it.number }?.plus(1) ?: 1),
                    initialStartTime = initialStart,
                    initialEndTime = initialEnd,
                    initialAlias = editingTimeSlot?.alias,
                    isEditing = isEditing,
                    onDismiss = {
                        showEditBottomSheet = false
                        editingTimeSlot = null
                    },
                    onConfirm = { number, startTime, endTime, alias ->
                        val newOrUpdatedSlot = TimeSlot(number, startTime, endTime, courseTableId = "", alias = alias)

                        val updatedList = localTimeSlots.toMutableList()
                        if (isEditing) {
                            val targetIdx = updatedList.indexOfFirst { it.number == number }
                            if (targetIdx != -1) {
                                updatedList[targetIdx] = newOrUpdatedSlot

                                // 结束时间有变化时，后方节次按「上一节结束 + 下课休息 → 开始，
                                // 开始 + 默认上课时长 → 结束」整体顺延（不改变别名），至 23:59 为止
                                val originalEnd = editingTimeSlot?.endTime
                                if (originalEnd != endTime) {
                                    val breakMin = localDefaultBreakDuration.coerceAtLeast(0)
                                    val classMin = localDefaultClassDuration.coerceAtLeast(1)
                                    val dayEnd = LocalTime(23, 59)
                                    var lastEnd = parseLocalTimeSafely(endTime)
                                    for (i in targetIdx + 1 until updatedList.size) {
                                        val newStart = plusMinutesClamped(lastEnd, breakMin)
                                        val newEnd = plusMinutesClamped(newStart, classMin)
                                        updatedList[i] = updatedList[i].copy(
                                            startTime = formatTime(newStart),
                                            endTime = formatTime(newEnd)
                                        )
                                        lastEnd = newEnd
                                        // 不提前 break：超出 23:59 的节次统一钳到 23:59，
                                        // 保持尾部时间序单调（重排按 startTime 稳定排序不倒挂）
                                    }
                                }

                                ToastManager.show(toastSlotModifiedUnsaved)
                            }
                        } else {
                            updatedList.add(newOrUpdatedSlot)
                            ToastManager.show(toastSlotAddedUnsaved)
                        }

                        val finalSorted = updatedList
                            .sortedBy { parseLocalTimeSafely(it.startTime) }
                            .mapIndexed { i, slot -> slot.copy(number = i + 1) }

                        localTimeSlots.clear()
                        localTimeSlots.addAll(finalSorted)

                        showEditBottomSheet = false
                        editingTimeSlot = null
                    }
                )
            }
        }

        if (showExitConfirmDialog) {
            // 退出确认（统一操作区语言：危险色「不保存」+ 灰字「继续编辑」）
            AppAlertDialog(
                onDismissRequest = { showExitConfirmDialog = false },
                title = { Text(text = stringResource(Res.string.common_dialog_title_abandon_changes)) },
                text = { Text(text = stringResource(Res.string.common_dialog_msg_unsaved_changes)) },
                confirmButton = {
                    AppDialogActions(
                        confirmText = stringResource(Res.string.common_action_exit_without_save),
                        onConfirm = {
                            showExitConfirmDialog = false
                            onBack()
                        },
                        dismissText = stringResource(Res.string.common_action_continue_editing),
                        onDismiss = { showExitConfirmDialog = false },
                        danger = true
                    )
                },
                dismissButton = {}
            )
        }

        // 删除作息方案二次确认（删除不可撤销）
        if (pendingDeleteSchemeId != null) {
            AppDangerDialog(
                onDismissRequest = { pendingDeleteSchemeId = null },
                title = stringResource(Res.string.dialog_title_confirm_delete_course),
                text = stringResource(
                    Res.string.dialog_text_confirm_delete_scheme,
                    pendingDeleteSchemeId ?: ""
                ),
                confirmText = stringResource(Res.string.confirm_delete),
                onConfirm = {
                    val schemeId = pendingDeleteSchemeId
                    pendingDeleteSchemeId = null
                    if (schemeId != null) timeSlotViewModel.onDeleteScheme(schemeId)
                }
            )
        }

        if (showCreateSchemeDialog) {
            CreateSchemeDialog(
                onDismiss = { showCreateSchemeDialog = false },
                onCreate = { name ->
                    timeSlotViewModel.onCreateScheme(
                        name = name,
                        onSuccess = { showCreateSchemeDialog = false },
                        onError = { code ->
                            when (code) {
                                "empty" -> ToastManager.show(toastSchemeNameEmpty)
                                "duplicate" -> ToastManager.show(toastSchemeNameDuplicate)
                            }
                        }
                    )
                }
            )
        }

        if (showSchemeDatesDialog && editingSchemeForDates != null) {
            val editingSchemeId = editingSchemeForDates!!
            val currentMeta = uiState.schemeMetas.firstOrNull { it.schemeId == editingSchemeId }
            SchemeDateRangeDialog(
                schemeId = editingSchemeId,
                currentMeta = currentMeta,
                onDismiss = {
                    showSchemeDatesDialog = false
                    editingSchemeForDates = null
                },
                onConfirm = { startMonthDay, endMonthDay ->
                    timeSlotViewModel.onSaveSchemeDates(editingSchemeId, startMonthDay, endMonthDay)
                    showSchemeDatesDialog = false
                    editingSchemeForDates = null
                }
            )
        }
    }
    }
}

/**
 * 作息方案选择器：展示当前方案，支持切换、新建与删除方案。
 */
@Composable
fun SchemeSelector(
    currentSchemeId: String,
    schemeIds: List<String>,
    schemeMetas: List<TimeSlotScheme>,
    onSwitch: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onEditDates: (String) -> Unit
) {
    val titleSchemeSelector = stringResource(Res.string.title_scheme_selector)
    val defaultSchemeName = stringResource(Res.string.default_scheme_name)
    val actionNewScheme = stringResource(Res.string.action_new_scheme)
    val a11yDeleteScheme = stringResource(Res.string.a11y_delete_scheme)
    val a11yEditSchemeDates = stringResource(Res.string.a11y_edit_scheme_dates)
    val textSchemeDatesUnset = stringResource(Res.string.text_scheme_dates_unset)

    val sortedSchemes = remember(schemeIds) {
        schemeIds.sortedWith(
            compareBy<String> { it != TimeSlot.DEFAULT_SCHEME_ID }.thenBy { it }
        )
    }
    var expanded by remember { mutableStateOf(false) }

    fun displayName(schemeId: String): String =
        if (schemeId == TimeSlot.DEFAULT_SCHEME_ID) defaultSchemeName else schemeId

    Column(modifier = Modifier.fillMaxWidth()) {
        AppSectionHeader(titleSchemeSelector)
        AppCard(modifier = Modifier.fillMaxWidth()) {
        // 设计稿 .list-item + .scheme-content：方案名描边胶囊 + 「当前使用」提示 + 右箭头
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 方案名胶囊（描边）
                Box(
                    modifier = Modifier
                        .clip(appShapes().capsule)
                        .border(1.dp, appColors().divider, appShapes().capsule)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = displayName(currentSchemeId),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = appColors().textPrimary
                    )
                }
                // 「当前使用」提示
                Text(
                    text = stringResource(Res.string.hint_scheme_in_use),
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors().textSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = vectorResource(Res.drawable.chevron_right_24px),
                    contentDescription = null,
                    tint = appColors().textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .alpha(0.7f)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                sortedSchemes.forEach { scheme ->
                    val meta = schemeMetas.firstOrNull { it.schemeId == scheme }
                    val start = meta?.startMonthDay
                    val end = meta?.endMonthDay
                    val rangeText = if (start != null && end != null) {
                        val crossYear = TimeSlotScheme.parseMonthDay(start) > TimeSlotScheme.parseMonthDay(end)
                        if (crossYear) {
                            stringResource(Res.string.text_scheme_dates_range_cross_year, start, end)
                        } else {
                            stringResource(Res.string.text_scheme_dates_range, start, end)
                        }
                    } else {
                        textSchemeDatesUnset
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expanded = false
                                if (scheme != currentSchemeId) onSwitch(scheme)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName(scheme),
                                style = if (scheme == currentSchemeId) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = rangeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textSecondary
                            )
                        }
                        if (scheme == currentSchemeId) {
                            Icon(
                                vectorResource(Res.drawable.check_24px),
                                contentDescription = null,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        IconButton(onClick = {
                            expanded = false
                            onEditDates(scheme)
                        }) {
                            Icon(vectorResource(Res.drawable.edit_24px), contentDescription = a11yEditSchemeDates)
                        }
                        if (scheme != TimeSlot.DEFAULT_SCHEME_ID) {
                            IconButton(onClick = {
                                expanded = false
                                onDelete(scheme)
                            }) {
                                Icon(vectorResource(Res.drawable.delete_24px), contentDescription = a11yDeleteScheme)
                            }
                        }
                    }
                }
                HorizontalDivider(color = appColors().divider, thickness = 0.5.dp)
                DropdownMenuItem(
                    text = { Text(actionNewScheme) },
                    onClick = {
                        expanded = false
                        onCreate()
                    }
                )
            }
        }
        }
    }
}

/**
 * 新建作息方案弹窗。
 */
@Composable
fun CreateSchemeDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    val dialogTitleNewScheme = stringResource(Res.string.dialog_title_new_scheme)
    val labelSchemeName = stringResource(Res.string.label_scheme_name)
    val hintSchemeName = stringResource(Res.string.hint_scheme_name)
    val actionCancel = stringResource(Res.string.action_cancel)
    val actionNewScheme = stringResource(Res.string.action_new_scheme)

    var name by remember { mutableStateOf("") }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitleNewScheme) },
        text = {
            // 统一柔和填充输入框
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = labelSchemeName,
                placeholder = hintSchemeName,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            // 统一操作区：取消灰字 + 确认主色胶囊
            AppDialogActions(
                confirmText = actionNewScheme,
                onConfirm = { onCreate(name) },
                dismissText = actionCancel,
                onDismiss = onDismiss
            )
        },
        dismissButton = {}
    )
}

private fun calculateInitialTimes(
    isEditing: Boolean,
    editingTimeSlot: TimeSlot?,
    localTimeSlots: List<TimeSlot>,
    breakDur: Int,
    classDur: Int
): Pair<String, String> {
    if (isEditing && editingTimeSlot != null) return Pair(editingTimeSlot.startTime, editingTimeSlot.endTime)

    return if (localTimeSlots.isNotEmpty()) {
        val lastEndTimeStr = localTimeSlots.maxOf { it.endTime }
        val lastEndTime = parseLocalTimeSafely(lastEndTimeStr, fallback = LocalTime(8, 0))
        val start = lastEndTime.addMinutes(breakDur)
        val end = start.addMinutes(classDur)
        Pair(formatTime(start), formatTime(end))
    } else {
        val start = LocalTime(8, 0)
        Pair(formatTime(start), formatTime(start.addMinutes(classDur)))
    }
}

/**
 * 默认时长设置：设计稿 .duration-card —— 左右两个无框数字输入（22sp 展示衬线数值），
 * 中间 0.5dp 竖分隔线；校验逻辑保持原实现（空串回退哨兵值 + Toast 提示）。
 */
@Composable
fun DefaultDurationSettings(
    defaultClassDuration: Int,
    onClassDurationChange: (Int) -> Unit,
    defaultBreakDuration: Int,
    onBreakDurationChange: (Int) -> Unit
) {
    val titleDefaultDurationSettings = stringResource(Res.string.title_default_duration_settings)
    val labelClassDuration = stringResource(Res.string.label_class_duration_minutes)
    val toastClassDurationPositive = stringResource(Res.string.toast_class_duration_positive)
    val labelBreakDuration = stringResource(Res.string.label_break_duration_minutes)
    val toastBreakDurationNonNegative = stringResource(Res.string.toast_break_duration_non_negative)

    Column(modifier = Modifier.fillMaxWidth()) {
        AppSectionHeader(titleDefaultDurationSettings)
        AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DurationNumberField(
                label = labelClassDuration,
                value = if (defaultClassDuration == 0) "" else defaultClassDuration.toString(),
                onValueChange = { newValueStr ->
                    val newIntValue = newValueStr.toIntOrNull()
                    if (newValueStr.isEmpty()) {
                        onClassDurationChange(0)
                    } else if (newIntValue != null && newIntValue > 0) {
                        onClassDurationChange(newIntValue)
                    } else if (newIntValue != null) {
                        ToastManager.show(toastClassDurationPositive)
                    }
                },
                modifier = Modifier.weight(1f)
            )
            // 竖分隔线（0.5dp，上下留 14dp）
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .height(44.dp)
                    .background(appColors().divider)
            )
            DurationNumberField(
                label = labelBreakDuration,
                value = if (defaultBreakDuration == -1) "" else defaultBreakDuration.toString(),
                onValueChange = { newValueStr ->
                    val newIntValue = newValueStr.toIntOrNull()
                    if (newValueStr.isEmpty()) {
                        onBreakDurationChange(-1)
                    } else if (newIntValue != null && newIntValue >= 0) {
                        onBreakDurationChange(newIntValue)
                    } else if (newIntValue != null) {
                        ToastManager.show(toastBreakDurationNonNegative)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        }
    }
}

/** 无框数字输入域：13sp 标签在上 + 22sp Newsreader 数值在下（聚焦变主色）。 */
@Composable
private fun DurationNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    var isFocused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.textSecondary
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(
                fontFamily = claudeDisplaySerif(),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 26.sp,
                color = if (isFocused) colors.primary else colors.textPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
        )
    }
}

/**
 * 单个时间段列表项：设计稿 .slot-card —— 左侧「第 N 节 + 别名」标题与时间两行，
 * 右侧 36dp 圆角删除钮（bg-100 底，按压 danger 态由 Toast/对话框承担）。
 */
@Composable
fun TimeSlotItem(
    timeSlot: TimeSlot,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val a11yDeleteTimeSlot = stringResource(Res.string.a11y_delete_time_slot)
    val colors = appColors()

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEditClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.time_slot_section_number, timeSlot.number.toString()),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                    if (!timeSlot.alias.isNullOrBlank()) {
                        Text(
                            text = timeSlot.alias ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // 时间（等宽数字风格：tabular nums 由字体默认保证）
                Text(
                    text = "${timeSlot.startTime} – ${timeSlot.endTime}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        letterSpacing = 0.02.sp
                    ),
                    color = colors.textSecondary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // 36dp 圆角删除钮
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.inputBg)
                    .clickable(onClick = onDeleteClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.delete_24px),
                    contentDescription = a11yDeleteTimeSlot,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 「恢复默认」按钮：整宽卡片钮，主色文字；圆角随主题卡 token
 * （书卷 14dp / 通透 iOS 26 16dp 连续圆角 / 柔绘 24dp 虚化圆角，均来自 [appShapes] 分发）。
 */
@Composable
private fun RestoreDefaultButton(
    onClick: () -> Unit
) {
    val colors = appColors()
    val isClaude = LocalThemePreset.current == AppThemePreset.CLAUDE
    val isSoft = LocalThemePreset.current == AppThemePreset.SOFT
    val shape = if (isClaude) {
        RoundedCornerShape(14.dp)
    } else {
        appShapes().card
    }
    // 柔绘：整钮换成薄涂卡材质（软模糊投影 + 漫射柔光 + 羽化描边），
    // 取代「clip + background + 0.5dp 实色描边」——无锐利硬边缘。
    val containerModifier = if (isSoft) {
        Modifier.softSurface(shape = shape, containerColor = colors.cardBgElevated, elevation = 6.dp)
    } else {
        Modifier
            .clip(shape)
            .background(colors.cardBgElevated)
            .then(
                if (isClaude) {
                    Modifier.border(0.5.dp, colors.divider, shape)
                } else {
                    // 通透（iOS 26）：玻璃高光内描边 + 发丝分隔线
                    Modifier.iosGlassRim(shape).border(0.5.dp, colors.divider, shape)
                }
            )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(containerModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(Res.string.action_restore_default),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.primary
        )
    }
}

/**
 * 编辑/添加时间段的底部弹窗内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSlotEditContent(
    existingTimeSlots: List<TimeSlot>,
    initialNumber: Int,
    initialStartTime: String,
    initialEndTime: String,
    initialAlias: String?,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (number: Int, startTime: String, endTime: String, alias: String?) -> Unit
) {
    val (initialStartHour, initialStartMinute) = parseTimeString(initialStartTime)
    val (initialEndHour, initialEndMinute) = parseTimeString(initialEndTime)

    var startHourState by remember { mutableIntStateOf(initialStartHour) }
    var startMinuteState by remember { mutableIntStateOf(initialStartMinute) }
    var endHourState by remember { mutableIntStateOf(initialEndHour) }
    var endMinuteState by remember { mutableIntStateOf(initialEndMinute) }
    var aliasState by remember { mutableStateOf(initialAlias ?: "") }

    val minAllowedTime by remember(existingTimeSlots, initialNumber, isEditing) {
        derivedStateOf {
            val targetNumber = if (isEditing) initialNumber - 1 else existingTimeSlots.maxOfOrNull { it.number } ?: 0
            val prevSlot = existingTimeSlots.find { it.number == targetNumber }
            prevSlot?.endTime?.let { parseLocalTimeSafely(it) } ?: LocalTime(0, 0)
        }
    }

    val maxAllowedTime by remember(existingTimeSlots, initialNumber, isEditing) {
        derivedStateOf {
            // 编辑模式不再以上一节原开始时间为上限：结束时间改晚时下一节会被整体顺延，
            // 旧的「不能侵占下一个课时」校验会把顺延幅度钳死在课间空隙内
            if (isEditing) LocalTime(23, 59)
            else existingTimeSlots.find { it.number == initialNumber + 1 }?.startTime?.let { parseLocalTimeSafely(it) }
                ?: LocalTime(23, 59)
        }
    }

    val staticHours = remember { (0..23).map { formatTwoDigits(it) } }
    val staticMinutes = remember { (0..59).map { formatTwoDigits(it) } }

    val dialogTitleEdit = stringResource(Res.string.dialog_title_edit_time_slot)
    val dialogTitleAdd = stringResource(Res.string.dialog_title_add_time_slot)
    val labelStart = stringResource(Res.string.label_time_picker_start)
    val labelEnd = stringResource(Res.string.label_time_picker_end)
    val labelHour = stringResource(Res.string.label_time_picker_hour)
    val labelMinute = stringResource(Res.string.label_time_picker_minute)
    val actionCancel = stringResource(Res.string.action_cancel)
    val actionSaveChanges = stringResource(Res.string.action_save_changes)
    val actionAdd = stringResource(Res.string.action_add)
    val toastEndTimeMustBeLater = stringResource(Res.string.toast_end_time_must_be_later)
    val toastTimeConflict = stringResource(Res.string.toast_time_conflict)

    val currentTimeRange by remember(startHourState, startMinuteState, endHourState, endMinuteState) {
        derivedStateOf {
            val start = LocalTime(startHourState, startMinuteState)
            val end = LocalTime(endHourState, endMinuteState)
            "${formatTime(start)} - ${formatTime(end)}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isEditing) dialogTitleEdit else dialogTitleAdd,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        AppTextField(
            value = aliasState,
            onValueChange = { if (it.length <= 5) aliasState = it },
            label = stringResource(Res.string.label_time_slot_alias),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            supportingText = {
                Text(
                    text = "${aliasState.length}/5",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = appSpacing().pageHorizontal),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            // 开始时间标题组
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(labelStart, style = MaterialTheme.typography.bodySmall)
                    Text(labelHour, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(24.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("", style = MaterialTheme.typography.bodySmall)
                    Text(labelMinute, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            // 结束时间标题组
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(labelEnd, style = MaterialTheme.typography.bodySmall)
                    Text(labelHour, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(24.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("", style = MaterialTheme.typography.bodySmall)
                    Text(labelMinute, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = appSpacing().pageHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 开始时间滚轮组
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NativeNumberPicker(
                    values = staticHours,
                    selectedValue = formatTwoDigits(startHourState),
                    onValueChange = { startHourState = it.toInt() },
                    modifier = Modifier
                        .height(150.dp)
                        .weight(1f)
                )
                Text(
                    ":",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    textAlign = TextAlign.Center
                )
                NativeNumberPicker(
                    values = staticMinutes,
                    selectedValue = formatTwoDigits(startMinuteState),
                    onValueChange = { startMinuteState = it.toInt() },
                    modifier = Modifier
                        .height(150.dp)
                        .weight(1f)
                )
            }

            Text(
                "-",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium
            )

            // 结束时间滚轮组
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NativeNumberPicker(
                    values = staticHours,
                    selectedValue = formatTwoDigits(endHourState),
                    onValueChange = { endHourState = it.toInt() },
                    modifier = Modifier
                        .height(150.dp)
                        .weight(1f)
                )
                Text(
                    ":",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    textAlign = TextAlign.Center
                )
                NativeNumberPicker(
                    values = staticMinutes,
                    selectedValue = formatTwoDigits(endMinuteState),
                    onValueChange = { endMinuteState = it.toInt() },
                    modifier = Modifier
                        .height(150.dp)
                        .weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = currentTimeRange,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(16.dp))
                // 统一操作区：取消灰字 + 确认主色胶囊（AppDialogActions）
                AppDialogActions(
                    confirmText = if (isEditing) actionSaveChanges else actionAdd,
                    onConfirm = {
                        val startTimeObj = LocalTime(startHourState, startMinuteState)
                        val endTimeObj = LocalTime(endHourState, endMinuteState)

                        // 1. 核心校验：结束时间必须大于开始时间
                        if (endTimeObj <= startTimeObj) {
                            ToastManager.show(toastEndTimeMustBeLater)
                            return@AppDialogActions
                        }

                        // 2. 边界校验：不能侵占上一个课时或下一个课时
                        if (startTimeObj < minAllowedTime || endTimeObj > maxAllowedTime) {
                            ToastManager.show(toastTimeConflict)
                            return@AppDialogActions
                        }

                        onConfirm(
                            initialNumber,
                            formatTime(startTimeObj),
                            formatTime(endTimeObj),
                            aliasState.ifBlank { null }
                        )
                    },
                    dismissText = actionCancel,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

// 跨平台时间辅助函数

private fun formatTwoDigits(value: Int): String {
    return value.toString().padStart(2, '0')
}

private fun formatTime(time: LocalTime): String {
    return "${formatTwoDigits(time.hour)}:${formatTwoDigits(time.minute)}"
}

/** LocalTime 加分钟并钳制到 23:59（kotlinx-datetime 的 LocalTime 无分钟算术）。 */
private fun plusMinutesClamped(time: LocalTime, minutes: Int): LocalTime {
    val maxSeconds = 23 * 3600 + 59 * 60
    val total = (time.toSecondOfDay() + minutes * 60).coerceAtMost(maxSeconds)
    return LocalTime(total / 3600, (total % 3600) / 60)
}

private fun parseTimeString(timeString: String): Pair<Int, Int> {
    return try {
        val time = LocalTime.parse(timeString)
        Pair(time.hour, time.minute)
    } catch (_: Exception) {
        Pair(0, 0)
    }
}

private fun parseLocalTimeSafely(timeStr: String, fallback: LocalTime = LocalTime(23, 59)): LocalTime {
    return try {
        LocalTime.parse(timeStr)
    } catch (_: Exception) {
        fallback
    }

}

private fun LocalTime.addMinutes(minutes: Int): LocalTime {
    val totalMinutes = this.hour * 60 + this.minute + minutes
    val newTotalMinutes = ((totalMinutes % (24 * 60)) + (24 * 60)) % (24 * 60)
    return LocalTime(newTotalMinutes / 60, newTotalMinutes % 60)
}

/**
 * 自动切换作息方案开关：设计稿 .list-item-static —— 标题 + 副标题 + iOS 开关。
 */
@Composable
fun AutoSwitchToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val titleAutoSwitchScheme = stringResource(Res.string.title_auto_switch_scheme)
    val itemTitle = stringResource(Res.string.auto_switch_item_title)
    val descAutoSwitchScheme = stringResource(Res.string.desc_auto_switch_scheme)
    val colors = appColors()

    Column(modifier = Modifier.fillMaxWidth()) {
        AppSectionHeader(titleAutoSwitchScheme)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = appSpacing().cardInner, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = itemTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = colors.textPrimary
                    )
                    Text(
                        text = descAutoSwitchScheme,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                AppSwitch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}

/**
 * 设置作息方案生效日期范围（月-日，支持跨年）的弹窗。
 */
@Composable
fun SchemeDateRangeDialog(
    schemeId: String,
    currentMeta: TimeSlotScheme?,
    onDismiss: () -> Unit,
    onConfirm: (startMonthDay: String?, endMonthDay: String?) -> Unit
) {
    val dialogTitleSchemeDates = stringResource(Res.string.dialog_title_scheme_dates)
    val defaultSchemeName = stringResource(Res.string.default_scheme_name)
    val labelStartMonthDay = stringResource(Res.string.label_start_month_day)
    val labelEndMonthDay = stringResource(Res.string.label_end_month_day)
    val labelMonth = stringResource(Res.string.label_month)
    val labelDay = stringResource(Res.string.label_day)
    val actionCancel = stringResource(Res.string.action_cancel)
    val actionSaveChanges = stringResource(Res.string.action_save_changes)
    val actionClearDates = stringResource(Res.string.action_clear_dates)
    val toastIncomplete = stringResource(Res.string.toast_scheme_dates_incomplete)
    val hintCrossYear = stringResource(Res.string.hint_scheme_dates_cross_year)

    val schemeDisplayName = if (schemeId == TimeSlot.DEFAULT_SCHEME_ID) defaultSchemeName else schemeId

    val initialStart = currentMeta?.startMonthDay?.let(::parseMonthDayParts)
    val initialEnd = currentMeta?.endMonthDay?.let(::parseMonthDayParts)

    val initialStartMonth = initialStart?.first ?: 3
    val initialStartDay = (initialStart?.second ?: 1).coerceIn(1, daysInMonth(initialStartMonth).coerceAtLeast(1))
    val initialEndMonth = initialEnd?.first ?: 10
    val initialEndDay = (initialEnd?.second ?: 1).coerceIn(1, daysInMonth(initialEndMonth).coerceAtLeast(1))
    var startMonth by remember { mutableIntStateOf(initialStartMonth) }
    var startDay by remember { mutableIntStateOf(initialStartDay) }
    var endMonth by remember { mutableIntStateOf(initialEndMonth) }
    var endDay by remember { mutableIntStateOf(initialEndDay) }

    val staticMonths = remember { (1..12).map { formatTwoDigits(it) } }
    // 天数滚轮随所选月份动态变化：只显示当月真实存在的日期，避免 9-31 这类不存在的日期
    val startDays = remember(startMonth) { (1..daysInMonth(startMonth)).map { formatTwoDigits(it) } }
    val endDays = remember(endMonth) { (1..daysInMonth(endMonth)).map { formatTwoDigits(it) } }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitleSchemeDates) },
        text = {
            Column {
                Text(schemeDisplayName, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                DateRangePickerRow(
                    label = labelStartMonthDay,
                    month = startMonth,
                    day = startDay,
                    months = staticMonths,
                    days = startDays,
                    labelMonth = labelMonth,
                    labelDay = labelDay,
                    onMonthChange = { newMonth ->
                        startMonth = newMonth
                        if (startDay > daysInMonth(newMonth)) startDay = daysInMonth(newMonth)
                    },
                    onDayChange = { startDay = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
                DateRangePickerRow(
                    label = labelEndMonthDay,
                    month = endMonth,
                    day = endDay,
                    months = staticMonths,
                    days = endDays,
                    labelMonth = labelMonth,
                    labelDay = labelDay,
                    onMonthChange = { newMonth ->
                        endMonth = newMonth
                        if (endDay > daysInMonth(newMonth)) endDay = daysInMonth(newMonth)
                    },
                    onDayChange = { endDay = it }
                )
                if (startMonth * 100 + startDay > endMonth * 100 + endDay) {
                    Text(
                        text = hintCrossYear,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                TextButton(
                    onClick = { onConfirm(null, null) },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(actionClearDates, color = appColors().textSecondary)
                }
            }
        },
        confirmButton = {
            // 统一操作区：取消灰字 + 保存主色胶囊（AppDialogActions）
            AppDialogActions(
                confirmText = actionSaveChanges,
                onConfirm = {
                    if (!isValidMonthDay(startMonth, startDay) || !isValidMonthDay(endMonth, endDay)) {
                        ToastManager.show(toastIncomplete)
                        return@AppDialogActions
                    }
                    onConfirm(
                        formatMonthDay(startMonth, startDay),
                        formatMonthDay(endMonth, endDay)
                    )
                },
                dismissText = actionCancel,
                onDismiss = onDismiss
            )
        },
        dismissButton = {}
    )
}

/**
 * 单个日期范围选择行（月 + 日 两个滚轮）。
 */
@Composable
private fun DateRangePickerRow(
    label: String,
    month: Int,
    day: Int,
    months: List<String>,
    days: List<String>,
    labelMonth: String,
    labelDay: String,
    onMonthChange: (Int) -> Unit,
    onDayChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(64.dp)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(labelMonth, style = MaterialTheme.typography.labelSmall)
            NativeNumberPicker(
                values = months,
                selectedValue = formatTwoDigits(month),
                onValueChange = { onMonthChange(it.toInt()) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(labelDay, style = MaterialTheme.typography.labelSmall)
            NativeNumberPicker(
                values = days,
                selectedValue = formatTwoDigits(day),
                onValueChange = { onDayChange(it.toInt()) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun parseMonthDayParts(value: String): Pair<Int, Int> {
    val parts = value.split("-")
    val month = parts.getOrNull(0)?.toIntOrNull() ?: 3
    val day = parts.getOrNull(1)?.toIntOrNull() ?: 1
    return month to day
}

private fun daysInMonth(month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> 29
    else -> 0
}

private fun isValidMonthDay(month: Int, day: Int): Boolean {
    return month in 1..12 && day in 1..daysInMonth(month)
}

private fun formatMonthDay(month: Int, day: Int): String {
    return "${formatTwoDigits(month)}-${formatTwoDigits(day)}"
}