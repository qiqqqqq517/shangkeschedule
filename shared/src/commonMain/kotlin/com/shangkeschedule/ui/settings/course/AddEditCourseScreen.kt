package com.shangkeschedule.ui.settings.course

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.theme.appColors
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.a11y_delete
import shangkeschedule.shared.generated.resources.confirm_delete
import shangkeschedule.shared.generated.resources.dialog_title_confirm_delete_course
import shangkeschedule.shared.generated.resources.dialog_text_confirm_delete_course
import shangkeschedule.shared.generated.resources.a11y_save
import shangkeschedule.shared.generated.resources.action_add
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.common_action_continue_editing
import shangkeschedule.shared.generated.resources.common_action_exit_without_save
import shangkeschedule.shared.generated.resources.common_dialog_msg_unsaved_changes
import shangkeschedule.shared.generated.resources.common_dialog_title_abandon_changes
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.check_24px
import shangkeschedule.shared.generated.resources.label_assessment_method
import shangkeschedule.shared.generated.resources.label_course_name
import shangkeschedule.shared.generated.resources.label_credit
import shangkeschedule.shared.generated.resources.label_is_lab
import shangkeschedule.shared.generated.resources.title_add_course
import shangkeschedule.shared.generated.resources.title_edit_course
import shangkeschedule.shared.generated.resources.toast_delete_success
import shangkeschedule.shared.generated.resources.toast_name_empty
import shangkeschedule.shared.generated.resources.toast_save_success
import shangkeschedule.shared.generated.resources.toast_time_invalid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCourseScreen(
    onBack: () -> Unit,
    courseId: String? = null,
    viewModel: AddEditCourseViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(courseId) {
        viewModel.initWithId(courseId)
    }

    // 状态追踪：记录当前正在操作哪一个方案
    var activeSchemeId by remember { mutableStateOf<String?>(null) }

    // 弹窗控制状态
    var showWeekSelectorDialog by remember { mutableStateOf(false) }
    var showColorSelectorDialog by remember { mutableStateOf(false) }
    var showTimePickerSelector by remember { mutableStateOf(false) }
    var showDayPickerDialog by remember { mutableStateOf(false) }

    // 拦截退出弹窗状态
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    // 删除课程二次确认（删除不可撤销）
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // 提示文本资源
    val saveSuccessText = stringResource(Res.string.toast_save_success)
    val deleteSuccessText = stringResource(Res.string.toast_delete_success)
    val nameEmptyText = stringResource(Res.string.toast_name_empty)
    val toastTimeInvalid = stringResource(Res.string.toast_time_invalid)

    // 统一的退出拦截逻辑
    val handleBackPress = {
        if (viewModel.hasUnsavedChanges()) {
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

    // 处理 ViewModel 事件
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                UiEvent.SaveSuccess -> {
                    ToastManager.show(saveSuccessText)
                    onBack()
                }
                UiEvent.DeleteSuccess -> {
                    ToastManager.show(deleteSuccessText)
                    onBack()
                }
                UiEvent.Cancel -> onBack()
            }
        }
    }

    // 悬浮面板玻璃：主内容 hazeSource，时间/周次/颜色选择面板背板模糊
    val hazeState = rememberHazeState()

    Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditing) stringResource(Res.string.title_edit_course)
                        else stringResource(Res.string.title_add_course)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBackPress) {
                        Icon(
                            vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back)
                        )
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(vectorResource(Res.drawable.delete_24px), contentDescription = stringResource(Res.string.a11y_delete))
                        }
                    }
                    IconButton(
                        onClick = {
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
                                if (allValid) viewModel.onSave()
                                else ToastManager.show(toastTimeInvalid)
                            }
                        }
                    ) {
                        Icon(vectorResource(Res.drawable.check_24px), contentDescription = stringResource(Res.string.a11y_save))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 课程名称输入
            item {
                Spacer(modifier = Modifier.height(16.dp))
                // 统一柔和填充输入框（AppTextField，取代方硬的 OutlinedTextField）
                AppTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = stringResource(Res.string.label_course_name),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 学分
                AppTextField(
                    value = uiState.credit,
                    onValueChange = viewModel::onCreditChange,
                    label = stringResource(Res.string.label_credit),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 考核方式
                AppTextField(
                    value = uiState.assessmentMethod,
                    onValueChange = viewModel::onAssessmentMethodChange,
                    label = stringResource(Res.string.label_assessment_method),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 实验课
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.label_is_lab),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    com.shangkeschedule.ui.components.AppSwitch(
                        checked = uiState.isLab,
                        onCheckedChange = viewModel::onIsLabChange
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = appColors().divider, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 方案卡片列表
            items(uiState.schemes, key = { it.id }) { scheme ->
                CourseSchemeCard(
                    scheme = scheme,
                    courseColorMaps = uiState.courseColorMaps,
                    timeSlots = uiState.timeSlots,
                    onTeacherChange = { newTeacher ->
                        viewModel.updateScheme(scheme.id) { it.copy(teacher = newTeacher) }
                    },
                    onPositionChange = { newPos ->
                        viewModel.updateScheme(scheme.id) { it.copy(position = newPos) }
                    },
                    onRemarkChange = { newRemark ->
                        viewModel.onSchemeRemarkChange(scheme.id, newRemark)
                    },
                    onColorClick = {
                        activeSchemeId = scheme.id
                        showColorSelectorDialog = true
                    },
                    onTimeClick = {
                        activeSchemeId = scheme.id
                        showTimePickerSelector = true
                    },
                    onWeeksClick = {
                        activeSchemeId = scheme.id
                        showWeekSelectorDialog = true
                    },
                    onDayClick = {
                        activeSchemeId = scheme.id
                        showDayPickerDialog = true
                    },
                    onRemoveClick = { viewModel.removeScheme(scheme.id) },
                    onToggleCustomTime = { isCustom ->
                        viewModel.toggleCustomTime(scheme.id, isCustom)
                    },
                    showRemoveButton = uiState.schemes.size > 1
                )
            }

            // 添加方案按钮（主色胶囊，与全局按钮语言一致）
            item {
                Button(
                    onClick = viewModel::addScheme,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors().primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(vectorResource(Res.drawable.add_24px), contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.action_add),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    // --- 弹窗逻辑区块 ---
    val activeScheme = uiState.schemes.find { it.id == activeSchemeId }

    if (activeScheme != null) {
        // 周次选择器
        if (showWeekSelectorDialog) {
            WeekSelectorBottomSheet(
                totalWeeks = uiState.semesterTotalWeeks,
                selectedWeeks = activeScheme.weeks,
                onDismissRequest = { showWeekSelectorDialog = false },
                onConfirm = { weeks: Set<Int> ->
                    viewModel.updateScheme(activeScheme.id) { it.copy(weeks = weeks) }
                    showWeekSelectorDialog = false
                },
                hazeState = hazeState
            )
        }

        // 颜色选择器
        if (showColorSelectorDialog) {
            ColorPickerBottomSheet(
                colorMaps = uiState.courseColorMaps,
                selectedIndex = activeScheme.colorIndex,
                onDismissRequest = { showColorSelectorDialog = false },
                onConfirm = { index: Int ->
                    viewModel.updateScheme(activeScheme.id) { it.copy(colorIndex = index) }
                    showColorSelectorDialog = false
                },
                hazeState = hazeState
            )
        }

        // 时间/节次选择器
        if (showTimePickerSelector) {
            if (activeScheme.isCustomTime) {
                CustomTimeRangePickerBottomSheet(
                    initialStartTime = activeScheme.customStartTime.ifBlank { "08:00" },
                    initialEndTime = activeScheme.customEndTime.ifBlank { "09:45" },
                    onDismissRequest = { showTimePickerSelector = false },
                    onTimeRangeSelected = { start, end ->
                        viewModel.updateScheme(activeScheme.id) { it.copy(customStartTime = start, customEndTime = end) }
                        showTimePickerSelector = false
                    },
                    hazeState = hazeState
                )
            } else {
                CourseTimePickerBottomSheet(
                    selectedDay = activeScheme.day,
                    onDaySelected = { d -> viewModel.updateScheme(activeScheme.id) { it.copy(day = d) } },
                    startSection = activeScheme.startSection,
                    onStartSectionChange = { s -> viewModel.updateScheme(activeScheme.id) { it.copy(startSection = s) } },
                    endSection = activeScheme.endSection,
                    onEndSectionChange = { e -> viewModel.updateScheme(activeScheme.id) { it.copy(endSection = e) } },
                    timeSlots = uiState.timeSlots,
                    onDismissRequest = { showTimePickerSelector = false },
                    hazeState = hazeState
                )
            }
        }

        // 星期选择器 (用于自定义模式下的简单星期切换)
        if (showDayPickerDialog) {
            DayPickerDialog(
                selectedDay = activeScheme.day,
                onDismissRequest = { showDayPickerDialog = false },
                onDaySelected = { newDay ->
                    viewModel.updateScheme(activeScheme.id) { it.copy(day = newDay) }
                    showDayPickerDialog = false
                }
            )
        }
    }
    }

    // 删除课程二次确认（危险色确认钮，删除不可撤销）
    if (showDeleteConfirmDialog) {
        AppDangerDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = stringResource(Res.string.dialog_title_confirm_delete_course),
            text = stringResource(Res.string.dialog_text_confirm_delete_course, uiState.name),
            confirmText = stringResource(Res.string.confirm_delete),
            onConfirm = {
                showDeleteConfirmDialog = false
                viewModel.onDelete()
            }
        )
    }

    // 退出确认弹窗（统一操作区语言：危险色「不保存」+ 灰字「继续编辑」）
    if (showExitConfirmDialog) {
        AlertDialog(
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
}