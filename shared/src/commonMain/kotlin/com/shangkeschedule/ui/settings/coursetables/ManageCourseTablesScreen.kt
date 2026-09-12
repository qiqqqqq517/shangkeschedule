package com.shangkeschedule.ui.settings.coursetables

import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.iosGlassRim
import com.shangkeschedule.ui.theme.claudeGroupBg
import com.shangkeschedule.ui.theme.claudeGroupBorder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shangkeschedule.Destination
import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.ui.components.AppCard
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.claudeReadingSerif
import com.shangkeschedule.ui.theme.softSurface
import com.shangkeschedule.ui.theme.softTexture
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.couple_badge
import shangkeschedule.shared.generated.resources.manage_add_couple
import shangkeschedule.shared.generated.resources.manage_add_couple_desc
import shangkeschedule.shared.generated.resources.favorite_24px
import shangkeschedule.shared.generated.resources.a11y_new_semester
import shangkeschedule.shared.generated.resources.a11y_save
import shangkeschedule.shared.generated.resources.action_add
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_delete
import shangkeschedule.shared.generated.resources.action_semester_settings
import shangkeschedule.shared.generated.resources.action_view
import shangkeschedule.shared.generated.resources.action_view_timetable
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.archive_24px
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.arrow_drop_down_24px
import shangkeschedule.shared.generated.resources.calendar_today_24px
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.class_24px
import shangkeschedule.shared.generated.resources.confirm_delete
import shangkeschedule.shared.generated.resources.courses_count_format
import shangkeschedule.shared.generated.resources.dialog_text_confirm_delete
import shangkeschedule.shared.generated.resources.dialog_title_add_table
import shangkeschedule.shared.generated.resources.dialog_title_edit_table
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.edit_24px
import shangkeschedule.shared.generated.resources.eyebrow_new_semester
import shangkeschedule.shared.generated.resources.label_table_name
import shangkeschedule.shared.generated.resources.no_semester_configured
import shangkeschedule.shared.generated.resources.option_create_semester
import shangkeschedule.shared.generated.resources.option_create_semester_desc
import shangkeschedule.shared.generated.resources.option_import_semester
import shangkeschedule.shared.generated.resources.option_import_semester_desc
import shangkeschedule.shared.generated.resources.option_restore_backup
import shangkeschedule.shared.generated.resources.option_restore_backup_desc
import shangkeschedule.shared.generated.resources.progress_percent_format
import shangkeschedule.shared.generated.resources.schedule_24px
import shangkeschedule.shared.generated.resources.school_year_format
import shangkeschedule.shared.generated.resources.semester_archive_eyebrow
import shangkeschedule.shared.generated.resources.semester_count_short_format
import shangkeschedule.shared.generated.resources.semester_progress_label
import shangkeschedule.shared.generated.resources.status_current_semester
import shangkeschedule.shared.generated.resources.status_semester_completed
import shangkeschedule.shared.generated.resources.check_circle_24px
import shangkeschedule.shared.generated.resources.stat_courses_unit
import shangkeschedule.shared.generated.resources.stat_teaching_weeks_unit
import shangkeschedule.shared.generated.resources.text_no_semesters_hint
import shangkeschedule.shared.generated.resources.title_add_semester_ways
import shangkeschedule.shared.generated.resources.title_semester_management
import shangkeschedule.shared.generated.resources.toast_add_table_success
import shangkeschedule.shared.generated.resources.toast_delete_last_table_failed
import shangkeschedule.shared.generated.resources.toast_delete_table_success
import shangkeschedule.shared.generated.resources.toast_edit_table_success
import shangkeschedule.shared.generated.resources.toast_name_empty
import shangkeschedule.shared.generated.resources.toast_switch_table_success
import shangkeschedule.shared.generated.resources.total_tables_count_format
import shangkeschedule.shared.generated.resources.tune_24px
import shangkeschedule.shared.generated.resources.upload_24px
import shangkeschedule.shared.generated.resources.view_earlier_semesters
import shangkeschedule.shared.generated.resources.view_week_24px
import shangkeschedule.shared.generated.resources.visibility_24px
import shangkeschedule.shared.generated.resources.week_progress_format

/**
 * 学期管理页（原「管理课表」）：对齐设计稿 pages/学期管理.html。
 *
 * 结构：页头（eyebrow + 大标题 + 学期总数 + 新建按钮）
 *   → 当前学期高亮卡（主色描边 + 顶部主色条 + 学期进度条 + 查看/设置操作）
 *   → 历史学期按学年分组（已完成徽标 + 课程/教学周统计 + 查看/删除）
 *   → 「查看更早的学期」折叠展开
 *   → 新建学期方式卡（导入 / 手动创建 / 备份恢复）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCourseTablesScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit = {},
    viewModel: ManageCourseTablesViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // 更早学年折叠：默认只展示最近 2 个学年组
    var showAllGroups by remember { mutableStateOf(false) }

    // --- 对话框状态管理 ---
    var showAddTableDialog by remember { mutableStateOf(false) }
    var newTableName by remember { mutableStateOf("") }

    var showEditTableDialog by remember { mutableStateOf(false) }
    var editingTableInfo by remember { mutableStateOf<CourseTable?>(null) }
    var editedTableName by remember { mutableStateOf("") }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var tableToDelete by remember { mutableStateOf<CourseTable?>(null) }

    // --- 资源字符串 ---
    val a11yBack = stringResource(Res.string.a11y_back)
    val dialogTitleAddTable = stringResource(Res.string.dialog_title_add_table)
    val labelTableName = stringResource(Res.string.label_table_name)
    val actionAdd = stringResource(Res.string.action_add)
    val actionCancel = stringResource(Res.string.action_cancel)
    val toastNameEmpty = stringResource(Res.string.toast_name_empty)
    val toastEditSuccess = stringResource(Res.string.toast_edit_table_success)
    val dialogTitleEditTable = stringResource(Res.string.dialog_title_edit_table)
    val a11ySave = stringResource(Res.string.a11y_save)
    val dialogTitleConfirmDelete = stringResource(Res.string.confirm_delete)
    val actionDelete = stringResource(Res.string.action_delete)
    val toastDeleteLastFailed = stringResource(Res.string.toast_delete_last_table_failed)

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(stringResource(Res.string.title_semester_management)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = a11yBack)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                // 正在单独显示情侣课表时，当前学期卡展示的是本人表：
                // 「查看课表 / 学期设置」先切回本人表，保证卡面与操作目标一致
                val activeTableIsCouple = uiState.currentSemester?.let { semester ->
                    uiState.currentActiveTableId != null && uiState.currentActiveTableId != semester.table.id
                } ?: false
                val goSelfTimetable: () -> Unit = {
                    if (activeTableIsCouple) {
                        uiState.currentSemester?.let { viewModel.switchCourseTable(it.table.id) }
                    }
                    onNavigate(Destination.CourseSchedule)
                }
                val goSelfSemesterSettings: () -> Unit = {
                    if (activeTableIsCouple) {
                        uiState.currentSemester?.let { viewModel.switchCourseTable(it.table.id) }
                    }
                    onNavigate(Destination.SemesterSettings)
                }
                SemesterArchiveList(
                    uiState = uiState,
                    showAllGroups = showAllGroups,
                    onToggleShowAll = { showAllGroups = !showAllGroups },
                    onNewSemester = { showAddTableDialog = true },
                    onViewTimetable = goSelfTimetable,
                    onSemesterSettings = goSelfSemesterSettings,
                    onRenameSemester = { table ->
                        editingTableInfo = table
                        editedTableName = table.name
                        showEditTableDialog = true
                    },
                    onSwitchSemester = { table ->
                        viewModel.switchCourseTable(table.id)
                        onNavigate(Destination.CourseSchedule)
                    },
                    onDeleteSemester = { table ->
                        tableToDelete = table
                        showDeleteConfirmDialog = true
                    },
                    onViewCouple = { table ->
                        viewModel.switchCourseTable(table.id)
                        onNavigate(Destination.CourseSchedule)
                    },
                    onDeleteCouple = { table ->
                        tableToDelete = table
                        showDeleteConfirmDialog = true
                    },
                    onAddCouple = { selfTableId ->
                        viewModel.createCoupleTableFor(selfTableId)
                    },
                    onImportSemester = { onNavigate(Destination.SchoolSelectionListScreen) },
                    onRestoreBackup = { onNavigate(Destination.BackupAndRestore) }
                )
            }
        }

        // --- Add Dialog ---
        if (showAddTableDialog) {
            val addSuccessMsg = stringResource(Res.string.toast_add_table_success, newTableName)
            AppAlertDialog(
                onDismissRequest = {
                    showAddTableDialog = false
                    newTableName = ""
                },
                title = { Text(dialogTitleAddTable) },
                text = {
                    AppTextField(
                        value = newTableName,
                        onValueChange = { newTableName = it },
                        label = labelTableName,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    AppDialogActions(
                        confirmText = actionAdd,
                        onConfirm = {
                            if (newTableName.isNotBlank()) {
                                viewModel.createNewCourseTable(newTableName)
                                ToastManager.show(addSuccessMsg)
                                showAddTableDialog = false
                                newTableName = ""
                            } else {
                                ToastManager.show(toastNameEmpty)
                            }
                        },
                        dismissText = actionCancel,
                        onDismiss = {
                            showAddTableDialog = false
                            newTableName = ""
                        }
                    )
                },
                dismissButton = {}
            )
        }

        // --- Edit（重命名）Dialog ---
        if (showEditTableDialog && editingTableInfo != null) {
            AppAlertDialog(
                onDismissRequest = {
                    showEditTableDialog = false
                    editingTableInfo = null
                    editedTableName = ""
                },
                title = { Text(dialogTitleEditTable) },
                text = {
                    AppTextField(
                        value = editedTableName,
                        onValueChange = { editedTableName = it },
                        label = labelTableName,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    AppDialogActions(
                        confirmText = a11ySave,
                        onConfirm = {
                            if (editedTableName.isNotBlank()) {
                                editingTableInfo?.let { tableToEdit ->
                                    viewModel.updateCourseTable(tableToEdit.copy(name = editedTableName))
                                    ToastManager.show(toastEditSuccess)
                                    showEditTableDialog = false
                                    editingTableInfo = null
                                    editedTableName = ""
                                }
                            } else {
                                ToastManager.show(toastNameEmpty)
                            }
                        },
                        dismissText = actionCancel,
                        onDismiss = {
                            showEditTableDialog = false
                            editingTableInfo = null
                            editedTableName = ""
                        }
                    )
                },
                dismissButton = {}
            )
        }

        // --- Delete Dialog ---
        if (showDeleteConfirmDialog && tableToDelete != null) {
            val confirmDeleteText = stringResource(Res.string.dialog_text_confirm_delete, tableToDelete?.name ?: "")
            val deleteSuccessMsg = stringResource(Res.string.toast_delete_table_success, tableToDelete?.name ?: "")

            // 危险操作统一走 AppDangerDialog
            AppDangerDialog(
                onDismissRequest = {
                    showDeleteConfirmDialog = false
                    tableToDelete = null
                },
                title = dialogTitleConfirmDelete,
                text = confirmDeleteText,
                confirmText = actionDelete,
                onConfirm = {
                    val target = tableToDelete
                    showDeleteConfirmDialog = false
                    tableToDelete = null
                    if (target != null) {
                        coroutineScope.launch {
                            val deleted = viewModel.deleteCourseTable(target)
                            ToastManager.show(if (deleted) deleteSuccessMsg else toastDeleteLastFailed)
                        }
                    }
                },
                dismissText = actionCancel,
                onDismiss = {
                    showDeleteConfirmDialog = false
                    tableToDelete = null
                }
            )
        }
    }
}

// ============================================================================
// 页面列表
// ============================================================================

@Composable
private fun SemesterArchiveList(
    uiState: ManageCourseTablesUiState,
    showAllGroups: Boolean,
    onToggleShowAll: () -> Unit,
    onNewSemester: () -> Unit,
    onViewTimetable: () -> Unit,
    onSemesterSettings: () -> Unit,
    onRenameSemester: (CourseTable) -> Unit,
    onSwitchSemester: (CourseTable) -> Unit,
    onDeleteSemester: (CourseTable) -> Unit,
    onViewCouple: (CourseTable) -> Unit,
    onDeleteCouple: (CourseTable) -> Unit,
    onAddCouple: (String) -> Unit,
    onImportSemester: () -> Unit,
    onRestoreBackup: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = appSpacing().pageHorizontal),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 页头：eyebrow + 标题 + 新建按钮 + 概览行
        item(key = "header") {
            SemesterArchiveHeader(
                totalCount = uiState.courseTables.size,
                hasCurrent = uiState.currentSemester != null,
                onNewSemester = onNewSemester
            )
        }

        // 当前学期高亮卡
        val currentSemester = uiState.currentSemester
        if (currentSemester != null) {
            item(key = "current-semester") {
                SemesterSwipeRow(
                    selfCard = {
                        CurrentSemesterCard(
                            semester = currentSemester,
                            currentWeek = uiState.currentWeek,
                            weekPercent = uiState.currentWeekPercent,
                            onViewTimetable = onViewTimetable,
                            onSemesterSettings = onSemesterSettings,
                            onRename = { onRenameSemester(currentSemester.table) }
                        )
                    },
                    coupleCard = {
                        CoupleSwipePage(
                            couple = currentSemester.couple,
                            selfTableId = currentSemester.table.id,
                            onViewCouple = onViewCouple,
                            onDeleteCouple = onDeleteCouple,
                            onAddCouple = onAddCouple
                        )
                    }
                )
            }
        } else {
            item(key = "empty-hint") {
                Text(
                    text = stringResource(Res.string.text_no_semesters_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // 历史学期：按学年分组
        if (uiState.historyGroups.isNotEmpty()) {
            item(key = "history-section") {
                val visibleGroups = if (showAllGroups) uiState.historyGroups else uiState.historyGroups.take(2)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    visibleGroups.forEach { group ->
                        HistoryYearGroup(
                            group = group,
                            onSwitch = onSwitchSemester,
                            onRename = onRenameSemester,
                            onDelete = onDeleteSemester,
                            onViewCouple = onViewCouple,
                            onDeleteCouple = onDeleteCouple,
                            onAddCouple = onAddCouple
                        )
                    }

                    // 更多学年折叠按钮
                    if (!showAllGroups && uiState.historyGroups.size > 2) {
                        MoreSemestersButton(onClick = onToggleShowAll)
                    }
                }
            }
        }

        // 新建学期方式卡
        item(key = "new-semester-actions") {
            NewSemesterActionsCard(
                onCreateManually = onNewSemester,
                onImport = onImportSemester,
                onRestoreBackup = onRestoreBackup
            )
        }
    }
}

// ============================================================================
// 页头
// ============================================================================

@Composable
private fun SemesterArchiveHeader(
    totalCount: Int,
    hasCurrent: Boolean,
    onNewSemester: () -> Unit
) {
    val colors = appColors()
    val a11yNewSemester = stringResource(Res.string.a11y_new_semester)

    Column(modifier = Modifier.fillMaxWidth()) {
        // eyebrow（Lora 衬线，对齐设计稿 --font-serif）
        Text(
            text = stringResource(Res.string.semester_archive_eyebrow),
            style = TextStyle(
                fontFamily = claudeReadingSerif(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.em,
                lineHeight = 14.sp
            ),
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))

        // 标题 + 新建按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.title_semester_management),
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            // 新建学期：44dp 主色圆角方钮
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary)
                    .clickable(onClick = onNewSemester),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.add_24px),
                    contentDescription = a11yNewSemester,
                    tint = colors.textOnPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 概览行：共 N 个学期 · 当前学期徽标
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val countText = totalCount.toString()
            val totalText = stringResource(Res.string.total_tables_count_format, totalCount)
            val annotated = buildAnnotatedString {
                val idx = totalText.indexOf(countText)
                if (idx >= 0) {
                    append(totalText.substring(0, idx))
                    withStyle(
                        SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    ) { append(countText) }
                    append(totalText.substring(idx + countText.length))
                } else {
                    append(totalText)
                }
            }
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            Text(
                text = "·",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
            if (hasCurrent) {
                SuccessStatusBadge(
                    text = stringResource(Res.string.status_current_semester),
                    icon = vectorResource(Res.drawable.schedule_24px)
                )
            }
        }
    }
}

/** 设计稿 .badge.success：success 实底胶囊 + 白字 + 12dp 图标。 */
@Composable
private fun SuccessStatusBadge(
    text: String,
    icon: ImageVector
) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(appShapes().capsule)
            .background(colors.success)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textOnPrimary,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textOnPrimary
        )
    }
}

/** 设计稿 .badge.outline.completed：白底描边胶囊 + success 对勾图标。 */
@Composable
private fun CompletedBadge() {
    val colors = appColors()
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(appShapes().capsule)
            .background(colors.cardBgElevated)
            .border(1.dp, colors.divider, appShapes().capsule)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = vectorResource(Res.drawable.check_circle_24px),
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = stringResource(Res.string.status_semester_completed),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary
        )
    }
}

// ============================================================================
// 卡片容器（当前学期 / 历史学期通用底座）
// ============================================================================

/**
 * 学期卡容器：复刻 AppCard 的 Claude 形态（14dp 圆角 + 分组底 + 0.5dp 描边 + 1dp 投影），
 * 额外支持主色高亮描边（当前学期卡）与长按（重命名）。
 */
@Composable
private fun SemesterCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    highlightBorder: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = appColors()
    val isClaude = LocalThemePreset.current == AppThemePreset.CLAUDE
    val isSoft = LocalThemePreset.current == AppThemePreset.SOFT
    // 书卷 14dp / 通透 iOS 26 16dp / 柔绘 24dp 虚化圆角，均来自 [appShapes] 分发
    // （柔绘的 appShapes().card 已是 24dp「虚化圆角」，无需像书卷那样覆盖）
    val shape = if (isClaude) RoundedCornerShape(14.dp) else appShapes().card
    val bg = containerColor ?: when {
        isClaude -> claudeGroupBg()
        else -> tokens.cardBg
    }
    // 柔绘：薄涂卡材质（软模糊投影 + 漫射柔光 + 羽化描边）+ 手绘柔绘纹理（学期卡是大卡面）；
    // 底色沿用上面已算好的 bg（含「当前学期卡」的 cardBgElevated 覆盖）
    val surfaceModifier = if (isSoft) {
        Modifier
            .softSurface(shape = shape, containerColor = bg, elevation = 10.dp)
            .softTexture(shape)
    } else {
        Modifier
            .shadow(
                elevation = (if (isClaude) 1 else 2).dp,
                shape = shape,
                clip = false,
                ambientColor = tokens.shadow,
                spotColor = tokens.shadow
            )
            .clip(shape)
            .background(bg)
    }

    Column(
        modifier = modifier
            .then(surfaceModifier)
            .then(
                when {
                    onLongClick != null ->
                        Modifier.combinedClickable(
                            onClick = { onClick?.invoke() },
                            onLongClick = onLongClick
                        )
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                }
            )
            .then(
                when {
                    // 柔绘：无实色描边（含当前学期高亮）——高亮由主色薄涂底表达；
                    // 羽化描边环已由 softSurface 内含，此处不再重复叠加（与 AppCard 收口一致）
                    isSoft -> Modifier
                    highlightBorder -> Modifier.border(1.dp, tokens.primary, shape)
                    isClaude -> Modifier.border(0.5.dp, claudeGroupBorder(), shape)
                    // 通透（iOS 26）：白卡 + 玻璃高光内描边
                    else -> Modifier.iosGlassRim(shape)
                }
            )
    ) { content() }
}

// ============================================================================
// 当前学期高亮卡
// ============================================================================

@Composable
private fun CurrentSemesterCard(
    semester: SemesterInfo?,
    currentWeek: Int?,
    weekPercent: Int?,
    onViewTimetable: () -> Unit,
    onSemesterSettings: () -> Unit,
    onRename: () -> Unit
) {
    val colors = appColors()
    val semesterInfo = semester ?: return

    SemesterCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = colors.cardBgElevated,
        highlightBorder = true,
        onLongClick = onRename
    ) {
        // 顶部 3dp 主色条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(colors.primary)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态徽标
            SuccessStatusBadge(
                text = stringResource(Res.string.status_current_semester),
                icon = vectorResource(Res.drawable.schedule_24px)
            )

            // 学期名（展示衬线 22sp）
            Text(
                text = semesterInfo.table.name,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 22.sp,
                    lineHeight = 27.sp,
                    letterSpacing = (-0.01).em
                ),
                color = colors.textPrimary
            )

            // 元信息：日期区间 / 课程数
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SemesterMetaRow(
                    icon = vectorResource(Res.drawable.calendar_today_24px),
                    text = semesterInfo.dateRangeText()
                )
                SemesterMetaRow(
                    icon = vectorResource(Res.drawable.class_24px),
                    text = stringResource(Res.string.courses_count_format, semesterInfo.courseCount)
                )
            }

            // 学期进度
            if (weekPercent != null && currentWeek != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.semester_progress_label),
                            style = TextStyle(
                                fontFamily = claudeReadingSerif(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.1.em,
                                lineHeight = 13.sp
                            ),
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(
                                Res.string.week_progress_format,
                                currentWeek,
                                semesterInfo.totalWeeks
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )
                    }
                    // 8dp 进度条：secondary 底 + success 填充
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(appShapes().capsule)
                            .background(MaterialTheme.colorScheme.secondary)
                    ) {
                        val fraction = (weekPercent.coerceIn(0, 100)) / 100f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(appShapes().capsule)
                                .background(colors.success)
                        )
                    }
                    Text(
                        text = stringResource(Res.string.progress_percent_format, weekPercent),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.success,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            } else {
                // 未设置开学日期：进度区降级为提示
                Text(
                    text = stringResource(Res.string.no_semester_configured),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }

            // 操作按钮：查看课表 / 学期设置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroFilledButton(
                    text = stringResource(Res.string.action_view_timetable),
                    icon = vectorResource(Res.drawable.view_week_24px),
                    onClick = onViewTimetable,
                    modifier = Modifier.weight(1f)
                )
                HeroSecondaryButton(
                    text = stringResource(Res.string.action_semester_settings),
                    icon = vectorResource(Res.drawable.tune_24px),
                    onClick = onSemesterSettings,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 当前学期卡元信息行：14dp 图标 + 13sp 文案。 */
@Composable
private fun SemesterMetaRow(
    icon: ImageVector,
    text: String
) {
    val colors = appColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
    }
}

/** 主色实底按钮（查看课表）。 */
@Composable
private fun HeroFilledButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(appShapes().chip)
            .background(colors.primary)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textOnPrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.textOnPrimary
        )
    }
}

/** 次级按钮（学期设置）：secondary 底 + 描边。 */
@Composable
private fun HeroSecondaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(appShapes().chip)
            .background(MaterialTheme.colorScheme.secondary)
            .border(1.dp, colors.divider, appShapes().chip)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.textPrimary
        )
    }
}

// ============================================================================
// 历史学期（按学年分组）
// ============================================================================

@Composable
private fun HistoryYearGroup(
    group: YearGroup,
    onSwitch: (CourseTable) -> Unit,
    onRename: (CourseTable) -> Unit,
    onDelete: (CourseTable) -> Unit,
    onViewCouple: (CourseTable) -> Unit,
    onDeleteCouple: (CourseTable) -> Unit,
    onAddCouple: (String) -> Unit
) {
    val colors = appColors()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 学年标题行：N-M 学年 + 学期数
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.school_year_format, group.startYear, group.startYear + 1),
                style = TextStyle(
                    fontFamily = claudeReadingSerif(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.14.em,
                    lineHeight = 15.sp
                ),
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.semester_count_short_format, group.semesters.size),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
        }

        // 学期卡列表（本人卡 + 情侣卡并排，左右滑动查看）
        group.semesters.forEach { semesterInfo ->
            SemesterSwipeRow(
                selfCard = {
                    HistorySemesterCard(
                        semesterInfo = semesterInfo,
                        onSwitch = { onSwitch(semesterInfo.table) },
                        onRename = { onRename(semesterInfo.table) },
                        onDelete = { onDelete(semesterInfo.table) }
                    )
                },
                coupleCard = {
                    CoupleSwipePage(
                        couple = semesterInfo.couple,
                        selfTableId = semesterInfo.table.id,
                        onViewCouple = onViewCouple,
                        onDeleteCouple = onDeleteCouple,
                        onAddCouple = onAddCouple
                    )
                }
            )
        }
    }
}

@Composable
private fun HistorySemesterCard(
    semesterInfo: SemesterInfo,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = appColors()
    // 切换成功的 Toast 文案需在组合上下文预构建（名字在卡片内已知）
    val switchSuccessMsg = stringResource(Res.string.toast_switch_table_success, semesterInfo.table.name)

    SemesterCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            ToastManager.show(switchSuccessMsg)
            onSwitch()
        },
        onLongClick = onRename
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 标题行：学期名 + 已完成徽标；下行日期区间
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = semesterInfo.table.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    CompletedBadge()
                }
                Text(
                    text = semesterInfo.dateRangeText(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }

            // 统计行：课程数 / 教学周（上下分隔线）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = colors.divider,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = colors.divider,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatMini(
                    value = semesterInfo.courseCount.toString(),
                    label = stringResource(Res.string.stat_courses_unit),
                    modifier = Modifier.weight(1f)
                )
                // 竖分隔线
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(colors.divider)
                )
                StatMini(
                    value = semesterInfo.totalWeeks.toString(),
                    label = stringResource(Res.string.stat_teaching_weeks_unit),
                    modifier = Modifier.weight(1f)
                )
            }

            // 操作行：查看 / 删除
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GhostActionButton(
                    text = stringResource(Res.string.action_view),
                    icon = vectorResource(Res.drawable.visibility_24px),
                    danger = false,
                    onClick = onSwitch,
                    modifier = Modifier.weight(1f)
                )
                GhostActionButton(
                    text = stringResource(Res.string.action_delete),
                    icon = vectorResource(Res.drawable.delete_24px),
                    danger = true,
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ============================================================================
// 情侣课表并排卡（本人卡右侧，左右滑动查看）
// ============================================================================

/**
 * 学期卡并排容器：第 0 页 = 本人学期卡，第 1 页 = 情侣课表卡（或「添加情侣课表」）。
 * 左右滑动切换，下方圆点指示当前页。
 */
@Composable
private fun SemesterSwipeRow(
    selfCard: @Composable () -> Unit,
    coupleCard: @Composable () -> Unit
) {
    val colors = appColors()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (page == 0) selfCard() else coupleCard()
            }
        }
        // 页点指示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (active) colors.primary else colors.divider)
                )
            }
        }
    }
}

/** 情侣页内容：已有情侣课表 → 情侣卡；未创建 → 「添加情侣课表」虚线卡。 */
@Composable
private fun CoupleSwipePage(
    couple: SemesterInfo?,
    selfTableId: String,
    onViewCouple: (CourseTable) -> Unit,
    onDeleteCouple: (CourseTable) -> Unit,
    onAddCouple: (String) -> Unit
) {
    val coupleInfo = couple
    if (coupleInfo == null) {
        AddCoupleCard(onAdd = { onAddCouple(selfTableId) })
    } else {
        CoupleSemesterCard(
            couple = coupleInfo,
            onView = { onViewCouple(coupleInfo.table) },
            onDelete = { onDeleteCouple(coupleInfo.table) }
        )
    }
}

/** 情侣徽标：爱心图标 + 「情侣」文字胶囊。 */
@Composable
private fun CoupleBadge() {
    val colors = appColors()
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(appShapes().capsule)
            .background(colors.dangerSoft)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = vectorResource(Res.drawable.favorite_24px),
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = stringResource(Res.string.couple_badge),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.danger
        )
    }
}

/** 情侣课表卡：徽标 + 名称 + 日期区间 + 统计 + 查看 / 删除。 */
@Composable
private fun CoupleSemesterCard(
    couple: SemesterInfo,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = appColors()
    val switchSuccessMsg = stringResource(Res.string.toast_switch_table_success, couple.table.name)

    SemesterCard(modifier = Modifier.fillMaxWidth(), onClick = onView) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CoupleBadge()
                Spacer(modifier = Modifier.weight(1f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = couple.table.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = couple.dateRangeText(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }

            // 统计行：课程数 / 教学周（沿用情侣课表自己的配置）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = colors.divider,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = colors.divider,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatMini(
                    value = couple.courseCount.toString(),
                    label = stringResource(Res.string.stat_courses_unit),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(colors.divider)
                )
                StatMini(
                    value = couple.totalWeeks.toString(),
                    label = stringResource(Res.string.stat_teaching_weeks_unit),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GhostActionButton(
                    text = stringResource(Res.string.action_view),
                    icon = vectorResource(Res.drawable.visibility_24px),
                    danger = false,
                    onClick = {
                        ToastManager.show(switchSuccessMsg)
                        onView()
                    },
                    modifier = Modifier.weight(1f)
                )
                GhostActionButton(
                    text = stringResource(Res.string.action_delete),
                    icon = vectorResource(Res.drawable.delete_24px),
                    danger = true,
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 未创建情侣课表时的占位卡：虚线描边 + 爱心 + 「添加情侣课表」。 */
@Composable
private fun AddCoupleCard(onAdd: () -> Unit) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .dashedBorder(colors.divider, cornerRadius = 14.dp)
            .clickable(onClick = onAdd)
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = vectorResource(Res.drawable.favorite_24px),
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(Res.string.manage_add_couple),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary
        )
        Text(
            text = stringResource(Res.string.manage_add_couple_desc),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** 统计项：18sp 展示衬线数值 + 11sp 单位标签。 */
@Composable
private fun StatMini(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 18.sp,
                lineHeight = 20.sp
            ),
            color = colors.textPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary
        )
    }
}

/** 幽灵操作按钮（查看 / 删除）：透明底 + 描边，删除为 danger 色。 */
@Composable
private fun GhostActionButton(
    text: String,
    icon: ImageVector,
    danger: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    val contentColor = if (danger) colors.danger else colors.textPrimary
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(appShapes().chip)
            .border(1.dp, colors.divider, appShapes().chip)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = contentColor
        )
    }
}

/** 「查看更早的学期」：44dp 虚线描边整宽按钮。 */
@Composable
private fun MoreSemestersButton(onClick: () -> Unit) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .dashedBorder(colors.divider, cornerRadius = 14.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = vectorResource(Res.drawable.arrow_drop_down_24px),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.view_earlier_semesters),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            color = colors.textSecondary
        )
    }
}

// ============================================================================
// 新建学期方式卡
// ============================================================================

@Composable
private fun NewSemesterActionsCard(
    onCreateManually: () -> Unit,
    onImport: () -> Unit,
    onRestoreBackup: () -> Unit
) {
    val colors = appColors()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.eyebrow_new_semester),
                style = TextStyle(
                    fontFamily = claudeReadingSerif(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.12.em,
                    lineHeight = 12.sp
                ),
                color = colors.textSecondary
            )
            Text(
                text = stringResource(Res.string.title_add_semester_ways),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SemesterOptionItem(
                    icon = vectorResource(Res.drawable.upload_24px),
                    iconBg = colors.primarySoft,
                    iconFg = colors.primary,
                    title = stringResource(Res.string.option_import_semester),
                    desc = stringResource(Res.string.option_import_semester_desc),
                    onClick = onImport
                )
                SemesterOptionItem(
                    icon = vectorResource(Res.drawable.edit_24px),
                    iconBg = colors.successSoft,
                    iconFg = colors.success,
                    title = stringResource(Res.string.option_create_semester),
                    desc = stringResource(Res.string.option_create_semester_desc),
                    onClick = onCreateManually
                )
                SemesterOptionItem(
                    icon = vectorResource(Res.drawable.archive_24px),
                    iconBg = colors.inputBg,
                    iconFg = colors.textSecondary,
                    title = stringResource(Res.string.option_restore_backup),
                    desc = stringResource(Res.string.option_restore_backup_desc),
                    onClick = onRestoreBackup
                )
            }
        }
    }
}

/** 方式选项行：40dp 图标底 + 标题/描述 + 右箭头。 */
@Composable
private fun SemesterOptionItem(
    icon: ImageVector,
    iconBg: Color,
    iconFg: Color,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    val colors = appColors()
    val optionShape = appShapes().chip
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(optionShape)
            .background(colors.inputBg)
            .border(0.5.dp, colors.divider, optionShape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(optionShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconFg,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.textSecondary
            )
        }
        Icon(
            imageVector = vectorResource(Res.drawable.chevron_right_24px),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ============================================================================
// 工具
// ============================================================================

/** 学期日期区间文本：yyyy.MM.dd - yyyy.MM.dd；未设置开学日期时返回提示文案。 */
@Composable
private fun SemesterInfo.dateRangeText(): String {
    val start = startDate
    return if (start == null) {
        stringResource(Res.string.no_semester_configured)
    } else {
        val end = endDate ?: start
        "${start.toDotDateString()} - ${end.toDotDateString()}"
    }
}

private fun LocalDate.toDotDateString(): String {
    val month = monthNumber.toString().padStart(2, '0')
    val day = dayOfMonth.toString().padStart(2, '0')
    return "$year.$month.$day"
}

/** 虚线圆角描边（「查看更早的学期」按钮）：stroke 内缩半个线宽避免被裁剪。 */
private fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.dp
): Modifier = drawBehind {
    val strokePx = strokeWidth.toPx()
    val half = strokePx / 2f
    val radiusPx = cornerRadius.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(half, half),
        size = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx - half),
        style = Stroke(
            width = strokePx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        )
    )
}
