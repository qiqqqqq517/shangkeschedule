package com.shangkeschedule.ui.settings.time

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.main.TimeSlotScheme
import com.shangkeschedule.ui.components.NativeNumberPicker
import com.shangkeschedule.ui.components.ToastManager
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_add_time_slot
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.a11y_delete_scheme
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
import shangkeschedule.shared.generated.resources.title_default_duration_settings
import shangkeschedule.shared.generated.resources.title_scheme_selector
import shangkeschedule.shared.generated.resources.title_time_slot_management
import shangkeschedule.shared.generated.resources.toast_break_duration_non_negative
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
    timeSlotViewModel: TimeSlotViewModel = koinViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by timeSlotViewModel.timeSlotsUiState.collectAsState()

    val localTimeSlots = remember {
        mutableStateListOf<TimeSlot>().apply { addAll(uiState.timeSlots.sortedBy { it.number }) }
    }
    var localDefaultClassDuration by remember { mutableIntStateOf(uiState.defaultClassDuration) }
    var localDefaultBreakDuration by remember { mutableIntStateOf(uiState.defaultBreakDuration) }

    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showCreateSchemeDialog by remember { mutableStateOf(false) }
    var showSchemeDatesDialog by remember { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            TopAppBar(
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                HorizontalDivider()
                SchemeSelector(
                    currentSchemeId = uiState.currentSchemeId,
                    schemeIds = uiState.schemeIds,
                    schemeMetas = uiState.schemeMetas,
                    onSwitch = { schemeId -> timeSlotViewModel.onSwitchScheme(schemeId) },
                    onCreate = { showCreateSchemeDialog = true },
                    onDelete = { schemeId -> timeSlotViewModel.onDeleteScheme(schemeId) },
                    onEditDates = { schemeId ->
                        editingSchemeForDates = schemeId
                        showSchemeDatesDialog = true
                    }
                )
                AutoSwitchToggle(
                    enabled = uiState.autoSwitchScheme,
                    onToggle = { timeSlotViewModel.onToggleAutoSwitch(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                DefaultDurationSettings(
                    defaultClassDuration = localDefaultClassDuration,
                    onClassDurationChange = { newValue -> localDefaultClassDuration = newValue },
                    defaultBreakDuration = localDefaultBreakDuration,
                    onBreakDurationChange = { newValue -> localDefaultBreakDuration = newValue }
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                if (localTimeSlots.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(textNoTimeSlotsHint)
                    }
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

            ModalBottomSheet(
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
            AlertDialog(
                onDismissRequest = { showExitConfirmDialog = false },
                title = { Text(text = stringResource(Res.string.common_dialog_title_abandon_changes)) },
                text = { Text(text = stringResource(Res.string.common_dialog_msg_unsaved_changes)) },
                confirmButton = {
                    TextButton(onClick = {
                        showExitConfirmDialog = false
                        onBack()
                    }) {
                        Text(text = stringResource(Res.string.common_action_exit_without_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirmDialog = false }) {
                        Text(text = stringResource(Res.string.common_action_continue_editing))
                    }
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

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(titleSchemeSelector, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(displayName(currentSchemeId))
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                HorizontalDivider()
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitleNewScheme) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(labelSchemeName) },
                placeholder = { Text(hintSchemeName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }) {
                Text(actionNewScheme)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(actionCancel)
            }
        }
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

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(titleDefaultDurationSettings, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = if (defaultClassDuration == 0) "" else defaultClassDuration.toString(),
                onValueChange = { newValueStr ->
                    val newIntValue = newValueStr.toIntOrNull()
                    if (newValueStr.isEmpty()) {
                        onClassDurationChange(0)
                    } else if (newIntValue != null && newIntValue > 0) {
                        onClassDurationChange(newIntValue)
                    } else if (newIntValue != null){
                        ToastManager.show(toastClassDurationPositive)
                    }
                },
                label = { Text(labelClassDuration) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedTextField(
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
                label = { Text(labelBreakDuration) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 单个时间段列表项的 UI 组件
 */
@Composable
fun TimeSlotItem(
    timeSlot: TimeSlot,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val a11yDeleteTimeSlot = stringResource(Res.string.a11y_delete_time_slot)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEditClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.time_slot_section_number, timeSlot.number.toString()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(72.dp),
                maxLines = 1
            )
            Text(
                text = timeSlot.alias ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${timeSlot.startTime} - ${timeSlot.endTime}",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false
            )
            IconButton(onClick = onDeleteClick) {
                Icon(vectorResource(Res.drawable.delete_24px), contentDescription = a11yDeleteTimeSlot)
            }
        }
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
            val nextSlot = if (isEditing) existingTimeSlots.find { it.number == initialNumber + 1 } else null
            nextSlot?.startTime?.let { parseLocalTimeSafely(it) } ?: LocalTime(23, 59)
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

        OutlinedTextField(
            value = aliasState,
            onValueChange = { if (it.length <= 5) aliasState = it },
            label = { Text(stringResource(Res.string.label_time_slot_alias)) },
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
                .padding(horizontal = 16.dp),
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
                .padding(horizontal = 16.dp),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(actionCancel) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val startTimeObj = LocalTime(startHourState, startMinuteState)
                        val endTimeObj = LocalTime(endHourState, endMinuteState)

                        // 1. 核心校验：结束时间必须大于开始时间
                        if (endTimeObj <= startTimeObj) {
                            ToastManager.show(toastEndTimeMustBeLater)
                            return@Button
                        }

                        // 2. 边界校验：不能侵占上一个课时或下一个课时
                        if (startTimeObj < minAllowedTime || endTimeObj > maxAllowedTime) {
                            ToastManager.show(toastTimeConflict)
                            return@Button
                        }

                        onConfirm(
                            initialNumber,
                            formatTime(startTimeObj),
                            formatTime(endTimeObj),
                            aliasState.ifBlank { null }
                        )
                    }) {
                        Text(if (isEditing) actionSaveChanges else actionAdd)
                    }
                }
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
 * 自动切换作息方案开关。
 */
@Composable
fun AutoSwitchToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val titleAutoSwitchScheme = stringResource(Res.string.title_auto_switch_scheme)
    val descAutoSwitchScheme = stringResource(Res.string.desc_auto_switch_scheme)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(titleAutoSwitchScheme, style = MaterialTheme.typography.titleMedium)
            Text(
                text = descAutoSwitchScheme,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle
        )
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

    var startMonth by remember { mutableIntStateOf(initialStart?.first ?: 3) }
    var startDay by remember { mutableIntStateOf(initialStart?.second ?: 1) }
    var endMonth by remember { mutableIntStateOf(initialEnd?.first ?: 10) }
    var endDay by remember { mutableIntStateOf(initialEnd?.second ?: 1) }

    val staticMonths = remember { (1..12).map { formatTwoDigits(it) } }
    val staticDays = remember { (1..31).map { formatTwoDigits(it) } }

    AlertDialog(
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
                    days = staticDays,
                    labelMonth = labelMonth,
                    labelDay = labelDay,
                    onMonthChange = { startMonth = it },
                    onDayChange = { startDay = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
                DateRangePickerRow(
                    label = labelEndMonthDay,
                    month = endMonth,
                    day = endDay,
                    months = staticMonths,
                    days = staticDays,
                    labelMonth = labelMonth,
                    labelDay = labelDay,
                    onMonthChange = { endMonth = it },
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
                TextButton(onClick = { onConfirm(null, null) }) {
                    Text(actionClearDates)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!isValidMonthDay(startMonth, startDay) || !isValidMonthDay(endMonth, endDay)) {
                    ToastManager.show(toastIncomplete)
                    return@TextButton
                }
                onConfirm(
                    formatMonthDay(startMonth, startDay),
                    formatMonthDay(endMonth, endDay)
                )
            }) {
                Text(actionSaveChanges)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(actionCancel)
            }
        }
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