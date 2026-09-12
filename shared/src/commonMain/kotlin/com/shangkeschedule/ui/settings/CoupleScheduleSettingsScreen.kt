package com.shangkeschedule.ui.settings

import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.theme.AccentTone
import com.shangkeschedule.ui.theme.appSpacing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.LocalAppMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppSnackbarHost
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.ToastManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shangkeschedule.Destination
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.toast_couple_delete_failed
import shangkeschedule.shared.generated.resources.couple_manage_import_file
import shangkeschedule.shared.generated.resources.couple_manage_import_file_desc
import shangkeschedule.shared.generated.resources.couple_manage_import_school_desc
import shangkeschedule.shared.generated.resources.edit_24px
import shangkeschedule.shared.generated.resources.favorite_24px
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.confirm_delete
import shangkeschedule.shared.generated.resources.couple_need_create_first
import shangkeschedule.shared.generated.resources.couple_count_format
import shangkeschedule.shared.generated.resources.couple_delete_confirm_message
import shangkeschedule.shared.generated.resources.couple_manage_create
import shangkeschedule.shared.generated.resources.couple_manage_create_desc
import shangkeschedule.shared.generated.resources.couple_manage_created_desc
import shangkeschedule.shared.generated.resources.couple_manage_edit_timetable
import shangkeschedule.shared.generated.resources.couple_manage_link_title
import shangkeschedule.shared.generated.resources.couple_manage_not_created
import shangkeschedule.shared.generated.resources.couple_manage_rename
import shangkeschedule.shared.generated.resources.couple_manage_view
import shangkeschedule.shared.generated.resources.couple_overlay_desc
import shangkeschedule.shared.generated.resources.couple_overlay_disabled_solo
import shangkeschedule.shared.generated.resources.couple_overlay_switch
import shangkeschedule.shared.generated.resources.couple_rename_dialog_title
import shangkeschedule.shared.generated.resources.couple_solo_back
import shangkeschedule.shared.generated.resources.couple_solo_banner
import shangkeschedule.shared.generated.resources.desc_crush_course_color
import shangkeschedule.shared.generated.resources.desc_delete_couple_table
import shangkeschedule.shared.generated.resources.desc_import_crush_schedule
import shangkeschedule.shared.generated.resources.desc_self_course_color
import shangkeschedule.shared.generated.resources.delete_couple_table
import shangkeschedule.shared.generated.resources.item_crush_course_color
import shangkeschedule.shared.generated.resources.item_import_crush_schedule
import shangkeschedule.shared.generated.resources.item_self_course_color
import shangkeschedule.shared.generated.resources.label_table_name
import shangkeschedule.shared.generated.resources.section_title_couple_schedule
import shangkeschedule.shared.generated.resources.toast_couple_created
import shangkeschedule.shared.generated.resources.toast_couple_deleted
import shangkeschedule.shared.generated.resources.toast_couple_renamed
import shangkeschedule.shared.generated.resources.toast_name_empty
import shangkeschedule.shared.generated.resources.toast_switch_to_couple
import shangkeschedule.shared.generated.resources.toast_switch_to_self
import shangkeschedule.shared.generated.resources.tune_24px
import shangkeschedule.shared.generated.resources.upload_24px
import shangkeschedule.shared.generated.resources.visibility_24px
import shangkeschedule.shared.generated.resources.delete_24px

/**
 * 情侣课表二级页（独立课表形态）。
 *
 * 情侣课表是一张与本人课表并排管理的独立课表（有自己的课程、作息与学期配置）：
 * - 未创建时提供创建入口；
 * - 已创建时支持查看（切换单独显示）/ 重命名 / 作息设置 / 删除；
 * - 「双人同显」开关控制叠加显示（当前正在单独显示情侣课表时自动失效）；
 * - 导入与编辑复用主课表能力（教务 / Excel / JSON / 文本，导入时选择目标课表）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleScheduleSettingsScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: CoupleScheduleViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSelfColorDialog by remember { mutableStateOf(false) }
    var showCrushColorDialog by remember { mutableStateOf(false) }

    // 操作结果提示（预取字符串，回调里用 ToastManager 展示）
    val toastCreated = stringResource(Res.string.toast_couple_created)
    val toastRenamed = stringResource(Res.string.toast_couple_renamed)
    val toastDeleted = stringResource(Res.string.toast_couple_deleted)
    val toastDeleteFailed = stringResource(Res.string.toast_couple_delete_failed)
    val toastToCouple = stringResource(Res.string.toast_switch_to_couple)
    val toastToSelf = stringResource(Res.string.toast_switch_to_self)
    val toastNameEmpty = stringResource(Res.string.toast_name_empty)
    val needCoupleFirst = stringResource(Res.string.couple_need_create_first)

    if (!uiState.isReady) {
        Scaffold(
            topBar = {
                AppTopAppBar(
                    title = { Text(stringResource(Res.string.section_title_couple_schedule)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.arrow_back_24px),
                                contentDescription = stringResource(Res.string.a11y_back)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) { }
        }
        return
    }

    val coupleTable = uiState.coupleTable
    val isViewingCoupleSolo = uiState.currentTable?.isCouple == true

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(stringResource(Res.string.section_title_couple_schedule)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { AppSnackbarHost(hostState = remember { androidx.compose.material3.SnackbarHostState() }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = appSpacing().pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- 关联情侣课表 ---
            SectionCard {
                SettingItem(
                    title = stringResource(Res.string.couple_manage_link_title),
                    leadingIcon = vectorResource(Res.drawable.favorite_24px),
                    onClick = null
                )

                if (coupleTable == null) {
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.couple_manage_create),
                        subtitle = stringResource(Res.string.couple_manage_create_desc),
                        leadingIcon = vectorResource(Res.drawable.favorite_24px),
                        onClick = {
                            coroutineScope.launch {
                                val created = viewModel.createCoupleTable()
                                if (created) ToastManager.show(toastCreated)
                            }
                        }
                    )
                } else {
                    SectionDivider()
                    SettingItem(
                        title = coupleTable.name,
                        subtitle = stringResource(Res.string.couple_manage_created_desc)
                    )
                    if (!isViewingCoupleSolo) {
                        SectionDivider()
                        SettingItem(
                            title = stringResource(Res.string.couple_manage_view),
                            subtitle = stringResource(Res.string.couple_count_format, uiState.coupleCourseCount),
                            leadingIcon = vectorResource(Res.drawable.visibility_24px),
                            onClick = {
                                coroutineScope.launch {
                                    if (viewModel.switchToCouple()) ToastManager.show(toastToCouple)
                                }
                            }
                        )
                    }
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.couple_manage_rename),
                        subtitle = coupleTable.name,
                        leadingIcon = vectorResource(Res.drawable.edit_24px),
                        onClick = { showRenameDialog = true }
                    )
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.couple_manage_edit_timetable),
                        subtitle = stringResource(Res.string.couple_manage_created_desc),
                        leadingIcon = vectorResource(Res.drawable.tune_24px),
                        onClick = {
                            onNavigate(Destination.TimeSlotSettings(targetCourseTableId = coupleTable.id))
                        }
                    )
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.delete_couple_table),
                        subtitle = stringResource(Res.string.desc_delete_couple_table),
                        leadingIcon = vectorResource(Res.drawable.delete_24px),
                        accent = AccentTone.DANGER,
                        onClick = { showDeleteConfirm = true }
                    )
                }
            }

            // --- 正在单独显示情侣课表：提供返回本人课表 ---
            val soloMotion = LocalAppMotion.current
            val soloAnim = soloMotion.isEnabled(AnimationGroup.PAGE_ENTRANCE)
            AnimatedVisibility(
                visible = isViewingCoupleSolo,
                enter = if (soloAnim) expandVertically() + fadeIn(tween(soloMotion.tokens.statusFadeMs)) else EnterTransition.None,
                exit = if (soloAnim) shrinkVertically() + fadeOut(tween(soloMotion.tokens.statusFadeMs)) else ExitTransition.None
            ) {
                SectionCard {
                    SettingItem(
                        title = stringResource(Res.string.couple_solo_banner),
                        subtitle = stringResource(Res.string.couple_solo_back),
                        onClick = {
                            coroutineScope.launch {
                                if (viewModel.switchToSelf()) ToastManager.show(toastToSelf)
                            }
                        }
                    )
                }
            }

            // --- 显示设置 ---
            SectionCard {
                val hasCouple = uiState.coupleTable != null
                SettingItem(
                    title = stringResource(Res.string.couple_overlay_switch),
                    subtitle = when {
                        isViewingCoupleSolo -> stringResource(Res.string.couple_overlay_disabled_solo)
                        !hasCouple -> stringResource(Res.string.couple_manage_not_created)
                        else -> stringResource(Res.string.couple_overlay_desc)
                    }
                ) {
                    AppSwitch(
                        checked = uiState.coupleScheduleEnabled && !isViewingCoupleSolo && hasCouple,
                        enabled = !isViewingCoupleSolo && hasCouple,
                        onCheckedChange = { viewModel.onCoupleScheduleEnabledChanged(it) }
                    )
                }
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_self_course_color),
                    subtitle = stringResource(Res.string.desc_self_course_color),
                    onClick = { showSelfColorDialog = true }
                ) {
                    ColorPreviewDot(
                        colorIndex = uiState.selfCourseColorIndex,
                        colorMaps = uiState.courseColorMaps
                    )
                }
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_crush_course_color),
                    subtitle = stringResource(Res.string.desc_crush_course_color),
                    onClick = { showCrushColorDialog = true }
                ) {
                    ColorPreviewDot(
                        colorIndex = uiState.crushCourseColorIndex,
                        colorMaps = uiState.courseColorMaps
                    )
                }
            }

            // --- 导入（复用主课表多形式导入，目标课表在导入流程中选择） ---
            SectionCard {
                val importGuard: () -> Unit = {
                    if (coupleTable == null) {
                        ToastManager.show(needCoupleFirst)
                    } else {
                        onNavigate(Destination.SchoolSelectionListScreen)
                    }
                }
                val importFileGuard: () -> Unit = {
                    if (coupleTable == null) {
                        ToastManager.show(needCoupleFirst)
                    } else {
                        onNavigate(Destination.FileImportHub)
                    }
                }
                SettingItem(
                    title = stringResource(Res.string.item_import_crush_schedule),
                    subtitle = stringResource(Res.string.couple_manage_import_school_desc),
                    leadingIcon = vectorResource(Res.drawable.upload_24px),
                    onClick = importGuard
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.couple_manage_import_file),
                    subtitle = stringResource(Res.string.couple_manage_import_file_desc),
                    leadingIcon = vectorResource(Res.drawable.upload_24px),
                    onClick = importFileGuard
                )
            }
        }
    }

    // 颜色弹窗
    if (showSelfColorDialog) {
        ColorPickerDialog(
            title = stringResource(Res.string.item_self_course_color),
            selectedIndex = uiState.selfCourseColorIndex,
            colorMaps = uiState.courseColorMaps,
            onDismiss = { showSelfColorDialog = false },
            onSelect = { index ->
                viewModel.onSelfCourseColorIndexChanged(index)
                showSelfColorDialog = false
            }
        )
    }
    if (showCrushColorDialog) {
        ColorPickerDialog(
            title = stringResource(Res.string.item_crush_course_color),
            selectedIndex = uiState.crushCourseColorIndex,
            colorMaps = uiState.courseColorMaps,
            onDismiss = { showCrushColorDialog = false },
            onSelect = { index ->
                viewModel.onCrushCourseColorIndexChanged(index)
                showCrushColorDialog = false
            }
        )
    }

    // 重命名弹窗
    if (showRenameDialog && coupleTable != null) {
        var newName by remember(coupleTable.id, coupleTable.name) { mutableStateOf(coupleTable.name) }
        AppAlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(Res.string.couple_rename_dialog_title)) },
            text = {
                AppTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = stringResource(Res.string.label_table_name),
                    singleLine = true
                )
            },
            confirmButton = {
                AppDialogActions(
                    confirmText = stringResource(Res.string.action_confirm),
                    onConfirm = {
                        val name = newName
                        showRenameDialog = false
                        if (name.isNotBlank()) {
                            coroutineScope.launch {
                                if (viewModel.renameCoupleTable(name)) {
                                    ToastManager.show(toastRenamed)
                                }
                            }
                        } else {
                            ToastManager.show(toastNameEmpty)
                        }
                    },
                    dismissText = stringResource(Res.string.action_cancel),
                    onDismiss = { showRenameDialog = false }
                )
            },
            dismissButton = {}
        )
    }

    // 删除确认
    if (showDeleteConfirm && coupleTable != null) {
        AppDangerDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = stringResource(Res.string.delete_couple_table),
            text = stringResource(Res.string.couple_delete_confirm_message, coupleTable.name),
            confirmText = stringResource(Res.string.confirm_delete),
            onConfirm = {
                showDeleteConfirm = false
                coroutineScope.launch {
                    val deleted = viewModel.deleteCoupleTable()
                    viewModel.resetOverlaySwitch()
                    ToastManager.show(if (deleted) toastDeleted else toastDeleteFailed)
                }
            },
            dismissText = stringResource(Res.string.action_cancel),
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
