package com.shangkeschedule.ui.grade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.Destination
import com.shangkeschedule.WebPagePurpose
import com.shangkeschedule.data.db.main.Grade
import com.shangkeschedule.data.model.GpaScale
import com.shangkeschedule.data.repository.ParsedGrade
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppEmptyState
import com.shangkeschedule.ui.components.AppFab
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppSegmentedControl
import com.shangkeschedule.ui.components.AppSelectableCard
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.components.rememberAppHaptics
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.theme.AccentTone
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.content_copy_24px
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.gpa_scale_4
import shangkeschedule.shared.generated.resources.gpa_scale_5
import shangkeschedule.shared.generated.resources.grade_action_add
import shangkeschedule.shared.generated.resources.grade_action_clear
import shangkeschedule.shared.generated.resources.grade_action_import
import shangkeschedule.shared.generated.resources.grade_action_paste
import shangkeschedule.shared.generated.resources.grade_add_title
import shangkeschedule.shared.generated.resources.grade_cancel
import shangkeschedule.shared.generated.resources.grade_category_label
import shangkeschedule.shared.generated.resources.grade_category_placeholder
import shangkeschedule.shared.generated.resources.grade_clear_message
import shangkeschedule.shared.generated.resources.grade_clear_title
import shangkeschedule.shared.generated.resources.grade_cleared
import shangkeschedule.shared.generated.resources.grade_course_name_label
import shangkeschedule.shared.generated.resources.grade_course_name_placeholder
import shangkeschedule.shared.generated.resources.grade_credit_invalid
import shangkeschedule.shared.generated.resources.grade_credit_label
import shangkeschedule.shared.generated.resources.grade_delete
import shangkeschedule.shared.generated.resources.grade_delete_message
import shangkeschedule.shared.generated.resources.grade_delete_title
import shangkeschedule.shared.generated.resources.grade_deleted
import shangkeschedule.shared.generated.resources.grade_edit_title
import shangkeschedule.shared.generated.resources.grade_empty_desc
import shangkeschedule.shared.generated.resources.grade_failed_badge
import shangkeschedule.shared.generated.resources.grade_import_tip
import shangkeschedule.shared.generated.resources.grade_name_required
import shangkeschedule.shared.generated.resources.grade_note_label
import shangkeschedule.shared.generated.resources.grade_page_title
import shangkeschedule.shared.generated.resources.grade_paste_empty
import shangkeschedule.shared.generated.resources.grade_paste_hint
import shangkeschedule.shared.generated.resources.grade_paste_import_count
import shangkeschedule.shared.generated.resources.grade_paste_imported
import shangkeschedule.shared.generated.resources.grade_paste_parse
import shangkeschedule.shared.generated.resources.grade_paste_placeholder
import shangkeschedule.shared.generated.resources.grade_paste_result
import shangkeschedule.shared.generated.resources.grade_paste_title
import shangkeschedule.shared.generated.resources.grade_retake_badge
import shangkeschedule.shared.generated.resources.grade_retake_label
import shangkeschedule.shared.generated.resources.grade_save
import shangkeschedule.shared.generated.resources.grade_saved
import shangkeschedule.shared.generated.resources.grade_scale_label
import shangkeschedule.shared.generated.resources.grade_credit_value_fmt
import shangkeschedule.shared.generated.resources.grade_point_value_fmt
import shangkeschedule.shared.generated.resources.grade_scale_note
import shangkeschedule.shared.generated.resources.grade_score_invalid
import shangkeschedule.shared.generated.resources.grade_score_label
import shangkeschedule.shared.generated.resources.grade_score_placeholder
import shangkeschedule.shared.generated.resources.grade_semester_label
import shangkeschedule.shared.generated.resources.grade_semester_placeholder
import shangkeschedule.shared.generated.resources.grade_semester_required
import shangkeschedule.shared.generated.resources.grade_semester_unknown
import shangkeschedule.shared.generated.resources.grade_source_import
import shangkeschedule.shared.generated.resources.grade_source_manual
import shangkeschedule.shared.generated.resources.grade_source_paste
import shangkeschedule.shared.generated.resources.grade_summary_average
import shangkeschedule.shared.generated.resources.grade_summary_count
import shangkeschedule.shared.generated.resources.grade_summary_credits
import shangkeschedule.shared.generated.resources.grade_summary_gpa
import shangkeschedule.shared.generated.resources.grade_value_none
import shangkeschedule.shared.generated.resources.school_24px
import kotlin.math.round

/**
 * 成绩 / GPA 二级页（v4.66.0 新增，对齐星链课表的成绩模块）。
 *
 * 三条录入通道，按「离线优先」排序：
 * 1. **手动添加**：不依赖任何外部系统；
 * 2. **粘贴导入**：把教务成绩页文本粘进来，本地解析成草稿，用户改完再入库（不联网）；
 * 3. **从教务导入**：走已有的学校选择 → 内嵌 WebView 流程，在成绩页识别表格。
 *
 * 绩点制（4.0 / 5.0）全局持久化，切换后本页所有绩点与汇总实时重算。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: GradeViewModel = koinViewModel()
) {
    val groups by viewModel.groupedGrades.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val scale by viewModel.gpaScale.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val tokens = appColors()

    // 新增 / 编辑对话框的数据源（null = 不显示；isEditing 由 id 是否为 null 决定）
    var editingGrade by remember { mutableStateOf<Grade?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Grade?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val noneText = stringResource(Res.string.grade_value_none)
    // 提示文案在 Compose 上下文里先取好（协程里不能调 stringResource）
    val savedText = stringResource(Res.string.grade_saved)
    val deletedText = stringResource(Res.string.grade_deleted)
    val clearedText = stringResource(Res.string.grade_cleared)

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(text = stringResource(Res.string.grade_page_title)) },
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
        floatingActionButton = {
            AppFab(
                onClick = { showAddDialog = true },
                icon = vectorResource(Res.drawable.add_24px),
                contentDescription = stringResource(Res.string.grade_action_add)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = appSpacing().pageHorizontal,
                vertical = appSpacing().cardGap
            ),
            verticalArrangement = Arrangement.spacedBy(appSpacing().listGap)
        ) {
            // 汇总卡：绩点 / 平均分 / 已修学分 / 门数 + 绩点制切换
            item(key = "grade-summary") {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SummaryStat(
                            label = stringResource(Res.string.grade_summary_gpa),
                            value = summary.gpa?.let { formatNumber(it) } ?: noneText,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStat(
                            label = stringResource(Res.string.grade_summary_average),
                            value = summary.averageScore?.let { formatNumber(it) } ?: noneText,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStat(
                            label = stringResource(Res.string.grade_summary_credits),
                            value = formatNumber(summary.totalCredits),
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStat(
                            label = stringResource(Res.string.grade_summary_count),
                            value = summary.courseCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))
                    SectionDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.grade_scale_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    AppSegmentedControl(
                        options = listOf(
                            stringResource(Res.string.gpa_scale_4),
                            stringResource(Res.string.gpa_scale_5)
                        ),
                        selectedIndex = if (scale == GpaScale.SCALE_5) 1 else 0,
                        onSelect = { index ->
                            haptics.tick()
                            coroutineScope.launch {
                                viewModel.setGpaScale(
                                    if (index == 1) GpaScale.SCALE_5 else GpaScale.SCALE_4
                                )
                            }
                        }
                    )
                    Text(
                        text = stringResource(Res.string.grade_scale_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                    )
                }
            }

            // 录入通道
            item(key = "grade-actions") {
                SectionCard {
                    SettingItem(
                        title = stringResource(Res.string.grade_action_import),
                        subtitle = stringResource(Res.string.grade_import_tip),
                        leadingIcon = vectorResource(Res.drawable.school_24px),
                        onClick = {
                            haptics.tick()
                            onNavigate(
                                Destination.SchoolSelectionListScreen(WebPagePurpose.GRADE)
                            )
                        }
                    )
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.grade_action_paste),
                        leadingIcon = vectorResource(Res.drawable.content_copy_24px),
                        onClick = {
                            haptics.tick()
                            showPasteDialog = true
                        }
                    )
                    if (groups.isNotEmpty()) {
                        SectionDivider()
                        SettingItem(
                            title = stringResource(Res.string.grade_action_clear),
                            leadingIcon = vectorResource(Res.drawable.delete_24px),
                            accent = AccentTone.DANGER,
                            onClick = { showClearConfirm = true }
                        )
                    }
                }
            }

            if (groups.isEmpty()) {
                item(key = "grade-empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillParentMaxHeight(0.5f)
                    ) {
                        AppEmptyState(
                            hint = stringResource(Res.string.grade_empty_desc),
                            fillScreen = true,
                            actionLabel = stringResource(Res.string.grade_action_add),
                            onAction = { showAddDialog = true }
                        )
                    }
                }
            }

            groups.forEach { group ->
                item(key = "grade-semester-${group.semester}") {
                    AppSectionHeader(
                        text = group.semester.ifBlank {
                            stringResource(Res.string.grade_semester_unknown)
                        }
                    )
                }
                items(group.grades, key = { it.id }) { grade ->
                    GradeRow(
                        grade = grade,
                        pointText = viewModel.pointOf(grade, scale)?.let { formatNumber(it) },
                        failed = viewModel.isFailed(grade),
                        onClick = { editingGrade = grade }
                    )
                }
            }

            item(key = "grade-bottom-gap") {
                Spacer(modifier = Modifier.height(appSpacing().sectionGap))
            }
        }
    }

    // 新增 / 编辑对话框
    if (showAddDialog || editingGrade != null) {
        GradeEditDialog(
            initial = editingGrade,
            onDismiss = {
                showAddDialog = false
                editingGrade = null
            },
            onSave = { payload ->
                // 先把目标记录取出来：协程真正执行时 editingGrade 可能已被置空（对话框关闭）
                val target = editingGrade
                coroutineScope.launch {
                    if (target == null) {
                        viewModel.addGrade(
                            semester = payload.semester,
                            courseName = payload.courseName,
                            credit = payload.credit,
                            scoreText = payload.scoreText,
                            category = payload.category,
                            isRetake = payload.isRetake,
                            note = payload.note
                        )
                    } else {
                        viewModel.updateGrade(
                            target.copy(
                                semester = payload.semester,
                                courseName = payload.courseName,
                                credit = payload.credit,
                                scoreText = payload.scoreText,
                                category = payload.category,
                                isRetake = payload.isRetake,
                                note = payload.note
                            )
                        )
                    }
                    haptics.confirm()
                    ToastManager.show(savedText)
                    showAddDialog = false
                    editingGrade = null
                }
            },
            onDelete = { target ->
                editingGrade = null
                pendingDelete = target
            }
        )
    }

    // 粘贴导入对话框
    if (showPasteDialog) {
        GradePasteDialog(
            onDismiss = { showPasteDialog = false },
            onParse = { text -> viewModel.parsePastedGrades(text) },
            onImport = { semester, drafts ->
                coroutineScope.launch {
                    val items = viewModel.buildPastedGrades(semester, drafts)
                    viewModel.importGrades(items)
                    haptics.confirm()
                    // 带参数的文案用挂起版 getString（协程内可用）
                    ToastManager.show(getString(Res.string.grade_paste_imported, items.size))
                    showPasteDialog = false
                }
            }
        )
    }

    // 删除单条确认
    pendingDelete?.let { target ->
        AppDangerDialog(
            onDismissRequest = { pendingDelete = null },
            title = stringResource(Res.string.grade_delete_title),
            text = stringResource(Res.string.grade_delete_message, target.courseName),
            confirmText = stringResource(Res.string.grade_delete),
            onConfirm = {
                pendingDelete = null
                coroutineScope.launch {
                    viewModel.deleteGrade(target.id)
                    ToastManager.show(deletedText)
                }
            },
            dismissText = stringResource(Res.string.grade_cancel),
            onDismiss = { pendingDelete = null }
        )
    }

    // 清空全部确认
    if (showClearConfirm) {
        AppDangerDialog(
            onDismissRequest = { showClearConfirm = false },
            title = stringResource(Res.string.grade_clear_title),
            // 清空会连重修记录一起删，所以这里用「全部记录数」，不能用排除重修后的 summary.courseCount
            text = stringResource(Res.string.grade_clear_message, viewModel.grades.value.size),
            confirmText = stringResource(Res.string.grade_delete),
            onConfirm = {
                showClearConfirm = false
                coroutineScope.launch {
                    viewModel.deleteAllGrades()
                    ToastManager.show(clearedText)
                }
            },
            dismissText = stringResource(Res.string.grade_cancel),
            onDismiss = { showClearConfirm = false }
        )
    }
}

/** 汇总卡里的单个指标（数值 + 标签）。 */
@Composable
private fun SummaryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val tokens = appColors()
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = tokens.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * 成绩行：课程名 + 成绩（右侧突出）+ 学分 / 性质 / 绩点 + 重修与不及格标记。
 * 点击进入编辑对话框（删除入口也在里面），因此列表本身不需要滑动菜单。
 */
@Composable
private fun GradeRow(
    grade: Grade,
    pointText: String?,
    failed: Boolean,
    onClick: () -> Unit
) {
    val tokens = appColors()
    val noneText = stringResource(Res.string.grade_value_none)
    val sourceLabel = when (grade.source) {
        Grade.SOURCE_PASTE -> stringResource(Res.string.grade_source_paste)
        Grade.SOURCE_IMPORT -> stringResource(Res.string.grade_source_import)
        else -> stringResource(Res.string.grade_source_manual)
    }
    AppSelectableCard(
        selected = false,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = appSpacing().cardInner, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = grade.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 「学分」与「绩点」是用户可见文案，走本地化格式串（英文 credits / GPA，繁中 學分 / 績點）
                    val creditText = grade.credit?.let {
                        stringResource(Res.string.grade_credit_value_fmt, formatNumber(it))
                    }
                    if (creditText != null) {
                        Text(
                            text = creditText,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                    grade.category?.let { category ->
                        Text(
                            text = category,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                    if (grade.isRetake) {
                        GradeBadge(
                            text = stringResource(Res.string.grade_retake_badge),
                            color = tokens.textSecondary
                        )
                    }
                    if (failed) {
                        GradeBadge(
                            text = stringResource(Res.string.grade_failed_badge),
                            color = tokens.danger
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = grade.scoreText ?: noneText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.primary
                )
                Text(
                    text = pointText?.let { stringResource(Res.string.grade_point_value_fmt, it) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** 小胶囊标记（重修 / 不及格等）。 */
@Composable
private fun GradeBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/** 编辑对话框回传的字段集合。 */
private data class GradeFormPayload(
    val semester: String,
    val courseName: String,
    val credit: Double?,
    val scoreText: String?,
    val category: String?,
    val isRetake: Boolean,
    val note: String?
)

/**
 * 新增 / 编辑成绩对话框。
 *
 * 只有课程名必填；学期在新增时必填（否则成绩会落进「未标注学期」组，
 * 而手动录入本来就有学期信息，没填多半是漏填）。学分与成绩都做本地校验，
 * 校验不通过不发请求。
 */
@Composable
private fun GradeEditDialog(
    initial: Grade?,
    onDismiss: () -> Unit,
    onSave: (GradeFormPayload) -> Unit,
    onDelete: ((Grade) -> Unit)? = null
) {
    val tokens = appColors()
    var semester by remember { mutableStateOf(initial?.semester ?: "") }
    var courseName by remember { mutableStateOf(initial?.courseName ?: "") }
    var credit by remember { mutableStateOf(initial?.credit?.let { formatNumber(it) } ?: "") }
    var scoreText by remember { mutableStateOf(initial?.scoreText ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var isRetake by remember { mutableStateOf(initial?.isRetake ?: false) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var validateAttempted by remember { mutableStateOf(false) }

    val isNameMissing = validateAttempted && courseName.isBlank()
    val isSemesterMissing = validateAttempted && initial == null && semester.isBlank()
    val parsedCredit = credit.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val isCreditInvalid = credit.isNotBlank() && (parsedCredit == null || parsedCredit < 0.0 || parsedCredit > 30.0)
    val isScoreInvalid = scoreText.isNotBlank() && !isValidScoreText(scoreText)

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (initial == null) Res.string.grade_add_title else Res.string.grade_edit_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AppTextField(
                    value = semester,
                    onValueChange = { semester = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.grade_semester_label),
                    placeholder = stringResource(Res.string.grade_semester_placeholder),
                    isError = isSemesterMissing
                )
                if (isSemesterMissing) {
                    FieldError(stringResource(Res.string.grade_semester_required))
                }
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.grade_course_name_label),
                    placeholder = stringResource(Res.string.grade_course_name_placeholder),
                    isError = isNameMissing
                )
                if (isNameMissing) {
                    FieldError(stringResource(Res.string.grade_name_required))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = credit,
                        onValueChange = { credit = it },
                        modifier = Modifier.weight(1f),
                        label = stringResource(Res.string.grade_credit_label),
                        isError = isCreditInvalid
                    )
                    AppTextField(
                        value = scoreText,
                        onValueChange = { scoreText = it },
                        modifier = Modifier.weight(1f),
                        label = stringResource(Res.string.grade_score_label),
                        placeholder = stringResource(Res.string.grade_score_placeholder),
                        isError = isScoreInvalid
                    )
                }
                if (isCreditInvalid) {
                    FieldError(stringResource(Res.string.grade_credit_invalid))
                }
                if (isScoreInvalid) {
                    FieldError(stringResource(Res.string.grade_score_invalid))
                }
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.grade_category_label),
                    placeholder = stringResource(Res.string.grade_category_placeholder)
                )
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.grade_note_label)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.grade_retake_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary
                    )
                    AppSwitch(checked = isRetake, onCheckedChange = { isRetake = it })
                }
                // 编辑态提供删除入口（删除前仍有二次确认）
                if (initial != null && onDelete != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.grade_delete),
                        leadingIcon = vectorResource(Res.drawable.delete_24px),
                        accent = AccentTone.DANGER,
                        onClick = { onDelete(initial) }
                    )
                }
            }
        },
        confirmButton = {
            AppDialogActions(
                confirmText = stringResource(Res.string.grade_save),
                onConfirm = {
                    if (courseName.isBlank() || (initial == null && semester.isBlank()) ||
                        isCreditInvalid || isScoreInvalid
                    ) {
                        validateAttempted = true
                    } else {
                        onSave(
                            GradeFormPayload(
                                semester = semester.trim(),
                                courseName = courseName.trim(),
                                credit = parsedCredit,
                                scoreText = scoreText.trim().ifBlank { null },
                                category = category.trim().ifBlank { null },
                                isRetake = isRetake,
                                note = note.trim().ifBlank { null }
                            )
                        )
                    }
                },
                dismissText = stringResource(Res.string.grade_cancel),
                onDismiss = onDismiss
            )
        }
    )
}

/**
 * 粘贴导入对话框：粘贴文本 → 本地解析 → **可编辑草稿列表** → 确认导入。
 *
 * 解析只是启发式（教务页面复制出来的文本格式五花八门），所以草稿的课程名 / 学分 / 成绩
 * 都能就地改；确认前不会写库。
 */
@Composable
private fun GradePasteDialog(
    onDismiss: () -> Unit,
    onParse: (String) -> List<ParsedGrade>,
    onImport: (String, List<ParsedGrade>) -> Unit
) {
    val tokens = appColors()
    var rawText by remember { mutableStateOf("") }
    var semester by remember { mutableStateOf("") }
    var drafts = remember { mutableStateListOf<ParsedGrade>() }
    var parseAttempted by remember { mutableStateOf(false) }
    // 学期是导入的必填项：仓库层遇到空学期会整批丢弃，必须在这里拦下来
    var semesterError by remember { mutableStateOf(false) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.grade_paste_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(Res.string.grade_paste_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                AppTextField(
                    value = semester,
                    onValueChange = {
                        semester = it
                        if (it.isNotBlank()) semesterError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.grade_semester_label),
                    placeholder = stringResource(Res.string.grade_semester_placeholder)
                )
                if (semesterError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.grade_semester_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.danger
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(Res.string.grade_paste_placeholder),
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8
                )
                Spacer(modifier = Modifier.height(10.dp))
                AppDialogActions(
                    confirmText = stringResource(Res.string.grade_paste_parse),
                    onConfirm = {
                        drafts.clear()
                        drafts.addAll(onParse(rawText))
                        parseAttempted = true
                    },
                    dismissText = stringResource(Res.string.grade_cancel),
                    onDismiss = onDismiss
                )

                if (drafts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(Res.string.grade_paste_result),
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    drafts.forEachIndexed { index, draft ->
                        DraftRow(
                            draft = draft,
                            onChange = { updated -> drafts[index] = updated },
                            onRemove = { drafts.removeAt(index) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else if (parseAttempted) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.grade_paste_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.danger
                    )
                }
            }
        },
        confirmButton = {
            if (drafts.isNotEmpty()) {
                AppDialogActions(
                    confirmText = stringResource(Res.string.grade_paste_import_count, drafts.size),
                    onConfirm = {
                        if (semester.isBlank()) {
                            semesterError = true
                        } else {
                            onImport(semester.trim(), drafts.toList())
                        }
                    },
                    dismissText = stringResource(Res.string.grade_cancel),
                    onDismiss = onDismiss
                )
            }
        }
    )
}

/** 粘贴导入预览里的一行草稿（课程名 / 学分 / 成绩三格都可编辑）。 */
@Composable
private fun DraftRow(
    draft: ParsedGrade,
    onChange: (ParsedGrade) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AppTextField(
            value = draft.courseName,
            onValueChange = { onChange(draft.copy(courseName = it)) },
            modifier = Modifier.weight(1f)
        )
        AppTextField(
            value = draft.credit?.let { formatNumber(it) } ?: "",
            onValueChange = {
                onChange(draft.copy(credit = it.trim().takeIf { c -> c.isNotEmpty() }?.toDoubleOrNull()))
            },
            modifier = Modifier.width(64.dp)
        )
        AppTextField(
            value = draft.scoreText,
            onValueChange = { onChange(draft.copy(scoreText = it)) },
            modifier = Modifier.width(72.dp)
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = vectorResource(Res.drawable.delete_24px),
                contentDescription = stringResource(Res.string.grade_delete),
                tint = appColors().textSecondary
            )
        }
    }
}

/** 字段级错误文案（danger 小字）。 */
@Composable
private fun FieldError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = appColors().danger,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/**
 * 成绩文本是否合法：百分制数字（0–100）或已知的等级词。
 * 与仓库层的解析口径保持一致（`GradeRepository.scoreValueOf` / `pointFromLevel`）。
 */
private fun isValidScoreText(text: String): Boolean {
    // 与 GradeRepository.scoreValueOf 的口径一致：允许 "92"、"92.5"、"=" 前缀与 "92分" 后缀
    val raw = text.trim().removePrefix("=").removeSuffix("分").trim()
    val value = raw.toDoubleOrNull()
    if (value != null) return value in 0.0..100.0
    val normalized = text.trim().uppercase()
    return normalized.startsWith("优秀") || normalized.startsWith("良好") ||
        normalized.startsWith("中等") || normalized.startsWith("及格") ||
        normalized.startsWith("不及格") || normalized.startsWith("合格") ||
        normalized.startsWith("不合格") || normalized.startsWith("通过") ||
        normalized.startsWith("不通过") || normalized.startsWith("达标") ||
        normalized.startsWith("优") || normalized.startsWith("良") ||
        normalized.startsWith("A") || normalized.startsWith("B") ||
        normalized.startsWith("C") || normalized.startsWith("D") ||
        normalized.startsWith("F")
}

/**
 * 两位小数（去掉多余的尾零）。commonMain 没有 `String.format`，这里自己实现，
 * 保证三端显示一致（4.0 制 3.7 → "3.7"，5.0 制 4.0 → "4"）。
 */
internal fun formatNumber(value: Double): String {
    val rounded = round(value * 100) / 100
    val intPart = rounded.toInt()
    val frac = round((rounded - intPart) * 100).toInt()
    return when {
        frac == 0 -> intPart.toString()
        frac % 10 == 0 -> "$intPart.${frac / 10}"
        frac < 10 -> "$intPart.0$frac"
        else -> "$intPart.$frac"
    }
}
