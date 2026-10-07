package com.shangkeschedule.ui.study

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.Destination
import com.shangkeschedule.WebPagePurpose
import com.shangkeschedule.data.db.main.CurriculumCourse
import com.shangkeschedule.data.model.CreditRequirement
import com.shangkeschedule.data.model.PendingCurriculumCourse
import com.shangkeschedule.data.model.StudyCategoryProgress
import com.shangkeschedule.data.model.StudyProgress
import com.shangkeschedule.data.repository.ParsedCurriculum
import com.shangkeschedule.data.repository.ParsedCurriculumCourse
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.grade.formatNumber
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.edit_24px
import shangkeschedule.shared.generated.resources.school_24px
import shangkeschedule.shared.generated.resources.grade_cancel
import shangkeschedule.shared.generated.resources.grade_category_label
import shangkeschedule.shared.generated.resources.grade_delete
import shangkeschedule.shared.generated.resources.grade_save
import shangkeschedule.shared.generated.resources.grade_saved
import shangkeschedule.shared.generated.resources.study_category_add
import shangkeschedule.shared.generated.resources.study_category_add_hint
import shangkeschedule.shared.generated.resources.study_category_course_plan
import shangkeschedule.shared.generated.resources.study_category_courses
import shangkeschedule.shared.generated.resources.study_category_display_label
import shangkeschedule.shared.generated.resources.study_category_display_placeholder
import shangkeschedule.shared.generated.resources.study_category_edit
import shangkeschedule.shared.generated.resources.study_category_empty
import shangkeschedule.shared.generated.resources.study_category_failed
import shangkeschedule.shared.generated.resources.study_category_key_label
import shangkeschedule.shared.generated.resources.study_category_key_placeholder
import shangkeschedule.shared.generated.resources.study_category_key_readonly
import shangkeschedule.shared.generated.resources.study_category_key_required
import shangkeschedule.shared.generated.resources.study_category_missing
import shangkeschedule.shared.generated.resources.study_category_no_requirement
import shangkeschedule.shared.generated.resources.study_category_plan_unknown
import shangkeschedule.shared.generated.resources.study_category_ratio
import shangkeschedule.shared.generated.resources.study_category_section
import shangkeschedule.shared.generated.resources.study_category_suggestions
import shangkeschedule.shared.generated.resources.study_curriculum_add
import shangkeschedule.shared.generated.resources.study_curriculum_add_hint
import shangkeschedule.shared.generated.resources.study_curriculum_clear
import shangkeschedule.shared.generated.resources.study_curriculum_cleared
import shangkeschedule.shared.generated.resources.study_curriculum_count
import shangkeschedule.shared.generated.resources.study_curriculum_credit_label
import shangkeschedule.shared.generated.resources.study_curriculum_edit
import shangkeschedule.shared.generated.resources.study_curriculum_empty
import shangkeschedule.shared.generated.resources.study_curriculum_name_label
import shangkeschedule.shared.generated.resources.study_curriculum_name_placeholder
import shangkeschedule.shared.generated.resources.study_curriculum_name_required
import shangkeschedule.shared.generated.resources.study_curriculum_paste
import shangkeschedule.shared.generated.resources.study_curriculum_paste_empty
import shangkeschedule.shared.generated.resources.study_curriculum_paste_hint
import shangkeschedule.shared.generated.resources.study_curriculum_paste_imported
import shangkeschedule.shared.generated.resources.study_curriculum_paste_parse
import shangkeschedule.shared.generated.resources.study_curriculum_paste_placeholder
import shangkeschedule.shared.generated.resources.study_curriculum_paste_result
import shangkeschedule.shared.generated.resources.study_curriculum_paste_title
import shangkeschedule.shared.generated.resources.study_curriculum_section
import shangkeschedule.shared.generated.resources.study_curriculum_summary
import shangkeschedule.shared.generated.resources.study_curriculum_summary_school
import shangkeschedule.shared.generated.resources.study_curriculum_term_label
import shangkeschedule.shared.generated.resources.study_curriculum_term_placeholder
import shangkeschedule.shared.generated.resources.study_footnote
import shangkeschedule.shared.generated.resources.study_import_entry
import shangkeschedule.shared.generated.resources.study_import_entry_desc
import shangkeschedule.shared.generated.resources.study_metric_courses
import shangkeschedule.shared.generated.resources.study_metric_earned
import shangkeschedule.shared.generated.resources.study_metric_missing
import shangkeschedule.shared.generated.resources.study_metric_pending
import shangkeschedule.shared.generated.resources.study_metric_planned
import shangkeschedule.shared.generated.resources.study_need_requirement_hint
import shangkeschedule.shared.generated.resources.study_no_grade_hint
import shangkeschedule.shared.generated.resources.study_open_grades
import shangkeschedule.shared.generated.resources.study_overview_percent
import shangkeschedule.shared.generated.resources.study_overview_title
import shangkeschedule.shared.generated.resources.study_overview_value
import shangkeschedule.shared.generated.resources.study_pending_more
import shangkeschedule.shared.generated.resources.study_pending_section
import shangkeschedule.shared.generated.resources.study_required_courses_invalid
import shangkeschedule.shared.generated.resources.study_required_courses_label
import shangkeschedule.shared.generated.resources.study_required_courses_placeholder
import shangkeschedule.shared.generated.resources.study_required_credits_invalid
import shangkeschedule.shared.generated.resources.study_required_credits_label
import shangkeschedule.shared.generated.resources.study_required_credits_placeholder
import shangkeschedule.shared.generated.resources.study_requirement_delete
import shangkeschedule.shared.generated.resources.study_requirement_removed
import shangkeschedule.shared.generated.resources.study_uncategorized
import shangkeschedule.shared.generated.resources.title_study_progress

/** 未修课程区最多列出的课程数（其余引导用户看完整清单，避免长列表淹没页面）。 */
private const val MAX_PENDING_PREVIEW = 30

/**
 * 学业情况（培养方案完成度）。
 *
 * 与「成绩与绩点」页的分工：成绩页是**逐条成绩**的录入与查询（含绩点换算、教务导入），
 * 本页是**按课程类别汇总**的学分与门数完成度视图。
 *
 * 三类数据来源，页面上刻意分开呈现，不混成一个数字（v4.75.0）：
 * 1. **已出分门数**：本机成绩表的记录数（回答「考过了几门」）；
 * 2. **应修门数**：学校培养方案给的应修门数（手填，或适配脚本从教务「学业情况」页读出）；
 * 3. **课程清单**：培养方案里的具体课程，与成绩按课程名匹配后得出「已修 / 未修」，
 *    这才回答了「还差哪些课」——此前没有这份清单，「还没修的课」在结构上根本不存在。
 *
 * 所有数字都来自本地：成绩表 + 本页设置的类别要求 + 课程清单；不联网、不推断。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyProgressScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: StudyProgressViewModel = koinViewModel()
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val curriculum by viewModel.curriculum.collectAsStateWithLifecycle()
    val tokens = appColors()
    val coroutineScope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StudyCategoryProgress?>(null) }
    var addingCourse by remember { mutableStateOf(false) }
    var editingCourse by remember { mutableStateOf<CurriculumCourse?>(null) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val savedText = stringResource(Res.string.grade_saved)
    val removedText = stringResource(Res.string.study_requirement_removed)
    val clearedText = stringResource(Res.string.study_curriculum_cleared)

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(text = stringResource(Res.string.title_study_progress)) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = appSpacing().pageHorizontal,
                vertical = appSpacing().cardGap
            ),
            verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap)
        ) {
            item(key = "study-overview") {
                StudyOverviewCard(
                    progress = progress,
                    onOpenGrades = { onNavigate(Destination.Grade) }
                )
            }
            item(key = "study-category-title") {
                Text(
                    text = stringResource(Res.string.study_category_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.textPrimary,
                    modifier = Modifier.padding(top = appSpacing().sectionTitleGap)
                )
            }
            item(key = "study-category-list") {
                SectionCard {
                    if (progress.categories.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.study_category_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary,
                            modifier = Modifier.padding(vertical = appSpacing().cardGap)
                        )
                    } else {
                        progress.categories.forEachIndexed { index, category ->
                            if (index > 0) SectionDivider()
                            StudyCategoryRow(
                                item = category,
                                onClick = { editing = category }
                            )
                        }
                    }
                }
            }

            // 未修课程（v4.75.0）：只在本机有课程清单时出现 —— 没有清单就无从知道「还差哪些课」
            if (progress.hasAnyCurriculum) {
                item(key = "study-pending-title") {
                    Text(
                        text = stringResource(Res.string.study_pending_section),
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary,
                        modifier = Modifier.padding(top = appSpacing().sectionTitleGap)
                    )
                }
                item(key = "study-pending-list") {
                    PendingCoursesCard(progress = progress)
                }
            }

            item(key = "study-category-add") {
                SettingItem(
                    title = stringResource(Res.string.study_category_add),
                    subtitle = stringResource(Res.string.study_category_add_hint),
                    leadingIcon = vectorResource(Res.drawable.add_24px),
                    onClick = { adding = true }
                )
            }
            // v4.73.0：从教务一键读培养方案学分要求（走适配脚本钩子 shangkeScanStudy）。
            // 排在手填入口之后：手填是永远可用的兜底，教务导入只在适配过的学校可用。
            item(key = "study-curriculum-import") {
                SettingItem(
                    title = stringResource(Res.string.study_import_entry),
                    subtitle = stringResource(Res.string.study_import_entry_desc),
                    leadingIcon = vectorResource(Res.drawable.school_24px),
                    onClick = {
                        onNavigate(
                            Destination.SchoolSelectionListScreen(purpose = WebPagePurpose.STUDY)
                        )
                    }
                )
            }

            // 培养方案课程清单（v4.75.0）：手填 / 粘贴 / 清空三个入口，全部本机完成、不联网
            item(key = "study-curriculum-title") {
                Text(
                    text = stringResource(Res.string.study_curriculum_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.textPrimary,
                    modifier = Modifier.padding(top = appSpacing().sectionTitleGap)
                )
            }
            item(key = "study-curriculum-manage") {
                SectionCard {
                    if (curriculum.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.study_curriculum_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary,
                            modifier = Modifier.padding(vertical = appSpacing().cardGap)
                        )
                    } else {
                        Text(
                            text = stringResource(
                                Res.string.study_curriculum_count,
                                curriculum.size
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }
            item(key = "study-curriculum-add") {
                SettingItem(
                    title = stringResource(Res.string.study_curriculum_add),
                    subtitle = stringResource(Res.string.study_curriculum_add_hint),
                    leadingIcon = vectorResource(Res.drawable.add_24px),
                    onClick = { addingCourse = true }
                )
            }
            item(key = "study-curriculum-paste") {
                SettingItem(
                    title = stringResource(Res.string.study_curriculum_paste),
                    subtitle = stringResource(Res.string.study_curriculum_paste_hint),
                    leadingIcon = vectorResource(Res.drawable.edit_24px),
                    onClick = { showPasteDialog = true }
                )
            }
            if (curriculum.isNotEmpty()) {
                item(key = "study-curriculum-clear") {
                    SettingItem(
                        title = stringResource(Res.string.study_curriculum_clear),
                        leadingIcon = vectorResource(Res.drawable.delete_24px),
                        onClick = { showClearConfirm = true }
                    )
                }
            }
            // 清单逐条管理（点条目即编辑）：条目多时这里是唯一的删改入口
            if (curriculum.isNotEmpty()) {
                item(key = "study-curriculum-list") {
                    SectionCard {
                        curriculum.forEachIndexed { index, course ->
                            if (index > 0) SectionDivider()
                            CurriculumCourseRow(
                                item = course,
                                onClick = { editingCourse = course }
                            )
                        }
                    }
                }
            }

            item(key = "study-footnote") {
                Text(
                    text = stringResource(Res.string.study_footnote),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary
                )
            }
        }
    }

    val editingItem = editing
    if (adding || editingItem != null) {
        StudyRequirementDialog(
            initial = editingItem,
            knownCategories = progress.categories
                .filter { it.courseCount > 0 && it.category.isNotBlank() }
                .map { it.category.trim() },
            onDismiss = {
                adding = false
                editing = null
            },
            onConfirm = { category, credits, requiredCourses, displayName ->
                viewModel.upsertRequirement(category, credits, requiredCourses, displayName)
                adding = false
                editing = null
                ToastManager.show(savedText)
            },
            onDelete = editingItem?.let { item ->
                {
                    viewModel.removeRequirement(item.category)
                    editing = null
                    ToastManager.show(removedText)
                }
            }
        )
    }

    if (addingCourse || editingCourse != null) {
        CurriculumCourseDialog(
            initial = editingCourse,
            knownCategories = progress.categories.map { it.category }.filter { it.isNotBlank() },
            onDismiss = {
                addingCourse = false
                editingCourse = null
            },
            onConfirm = { name, category, credit, term ->
                val target = editingCourse
                if (target == null) {
                    viewModel.addCurriculumCourse(name, category, credit, term)
                } else {
                    viewModel.updateCurriculumCourse(
                        target.copy(
                            courseName = name,
                            category = category,
                            credit = credit,
                            suggestedTerm = term
                        )
                    )
                }
                addingCourse = false
                editingCourse = null
                ToastManager.show(savedText)
            },
            onDelete = editingCourse?.let { item ->
                {
                    viewModel.deleteCurriculumCourse(item.id)
                    editingCourse = null
                    ToastManager.show(removedText)
                }
            }
        )
    }

    if (showPasteDialog) {
        CurriculumPasteDialog(
            onDismiss = { showPasteDialog = false },
            onParse = { text -> viewModel.parsePastedCurriculum(text) },
            onImport = { parsed ->
                coroutineScope.launch {
                    val (requirementCount, courseCount) = viewModel.importParsedCurriculum(parsed)
                    ToastManager.show(
                        getString(
                            Res.string.study_curriculum_paste_imported,
                            requirementCount,
                            courseCount
                        )
                    )
                    showPasteDialog = false
                }
            }
        )
    }

    if (showClearConfirm) {
        AppAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(text = stringResource(Res.string.study_curriculum_clear)) },
            text = { Text(text = stringResource(Res.string.study_curriculum_empty)) },
            confirmButton = {
                AppDialogActions(
                    confirmText = stringResource(Res.string.study_curriculum_clear),
                    onConfirm = {
                        viewModel.deleteAllCurriculumCourses()
                        showClearConfirm = false
                        ToastManager.show(clearedText)
                    },
                    dismissText = stringResource(Res.string.grade_cancel),
                    onDismiss = { showClearConfirm = false }
                )
            }
        )
    }
}

// ==================== 总览卡 ====================

@Composable
private fun StudyOverviewCard(
    progress: StudyProgress,
    onOpenGrades: () -> Unit
) {
    val tokens = appColors()
    val earnedText = formatNumber(progress.totalEarnedCredits)
    val requiredText = formatNumber(progress.totalRequiredCredits)
    val percentText = "${(progress.ratio * 100f).roundToInt()}%"

    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = appSpacing().cardGap)
        ) {
            Text(
                text = stringResource(Res.string.study_overview_title),
                style = MaterialTheme.typography.labelMedium,
                color = tokens.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(
                        Res.string.study_overview_value,
                        earnedText,
                        requiredText
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(Res.string.study_overview_percent, percentText),
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            StudyProgressBar(ratio = progress.ratio)
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StudyMetricCell(
                    modifier = Modifier.weight(1f),
                    value = earnedText,
                    label = stringResource(Res.string.study_metric_earned)
                )
                StudyMetricCell(
                    modifier = Modifier.weight(1f),
                    value = formatNumber(progress.totalMissingCredits),
                    label = stringResource(Res.string.study_metric_missing)
                )
                StudyMetricCell(
                    modifier = Modifier.weight(1f),
                    value = progress.totalCourses.toString(),
                    label = stringResource(Res.string.study_metric_courses)
                )
            }
            // 门数进度（v4.75.0）：只有拿到「应修门数」或导入了课程清单才出现
            progress.displayTargetCourses?.let { target ->
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StudyMetricCell(
                        modifier = Modifier.weight(1f),
                        value = target.toString(),
                        label = stringResource(Res.string.study_metric_planned)
                    )
                    StudyMetricCell(
                        modifier = Modifier.weight(1f),
                        value = progress.totalRemainingCourses.toString(),
                        label = stringResource(Res.string.study_metric_pending)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (progress.hasAnyCurriculum) {
                        stringResource(
                            Res.string.study_curriculum_summary,
                            progress.totalPlannedCourses,
                            (progress.totalPlannedCourses - progress.totalRemainingCourses)
                                .coerceAtLeast(0),
                            progress.totalRemainingCourses
                        )
                    } else {
                        stringResource(
                            Res.string.study_curriculum_summary_school,
                            target,
                            progress.totalCourses,
                            progress.totalRemainingCourses
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.primary
                )
            }
            if (!progress.hasAnyRequirement) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.study_need_requirement_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary
                )
            }
            if (!progress.hasAnyGrade) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.study_no_grade_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.study_open_grades),
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.primary,
                    modifier = Modifier.clickable(onClick = onOpenGrades)
                )
            }
        }
    }
}

@Composable
private fun StudyMetricCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val tokens = appColors()
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textSecondary
        )
    }
}

// ==================== 类别行 ====================

@Composable
private fun StudyCategoryRow(
    item: StudyCategoryProgress,
    onClick: () -> Unit
) {
    val tokens = appColors()
    val rawName = item.displayName.trim().ifBlank { item.category.trim() }
    val name = rawName.ifBlank { stringResource(Res.string.study_uncategorized) }
    val creditsLabel = if (item.hasRequirement) {
        stringResource(
            Res.string.study_category_ratio,
            formatNumber(item.earnedCredits),
            formatNumber(item.requiredCredits)
        )
    } else {
        stringResource(Res.string.study_category_no_requirement)
    }
    val details = buildList {
        if (item.hasRequirement && item.missingCredits > 0.0) {
            add(stringResource(Res.string.study_category_missing, formatNumber(item.missingCredits)))
        }
        if (item.failedCount > 0) {
            add(stringResource(Res.string.study_category_failed, item.failedCount))
        }
        // 门数：能统计「应修 / 已修 / 未修」时给完整三段，否则退回只报已出分门数
        val target = item.targetCourses
        when {
            target != null && target > 0 -> add(
                stringResource(
                    Res.string.study_category_course_plan,
                    target,
                    item.doneCourses,
                    item.remainingCourses
                )
            )

            item.courseCount > 0 -> add(
                stringResource(Res.string.study_category_courses, item.courseCount)
            )

            else -> add(stringResource(Res.string.study_category_plan_unknown, item.courseCount))
        }
    }.joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary
            )
            Text(
                text = creditsLabel,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = vectorResource(Res.drawable.edit_24px),
                contentDescription = null,
                tint = tokens.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        StudyProgressBar(ratio = item.ratio)
        if (details.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = details,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textSecondary
            )
        }
    }
}

// ==================== 未修课程 ====================

@Composable
private fun PendingCoursesCard(progress: StudyProgress) {
    val tokens = appColors()
    val pendingGroups = progress.categories
        .map { it to it.pendingCourseList }
        .filter { it.second.isNotEmpty() }
    val totalPending = pendingGroups.sumOf { it.second.size }

    SectionCard {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = appSpacing().cardGap)) {
            if (totalPending == 0) {
                Text(
                    text = stringResource(Res.string.study_curriculum_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
                return@Column
            }
            var remaining = MAX_PENDING_PREVIEW
            pendingGroups.forEach { (category, courses) ->
                if (remaining <= 0) return@forEach
                val shown = courses.take(remaining)
                remaining -= shown.size
                Text(
                    text = category.displayName.trim()
                        .ifBlank { category.category.trim() }
                        .ifBlank { stringResource(Res.string.study_uncategorized) },
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.primary
                )
                shown.forEach { course ->
                    PendingCourseRow(course = course)
                }
            }
            if (totalPending > MAX_PENDING_PREVIEW) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.study_pending_more, MAX_PENDING_PREVIEW),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary
                )
            }
        }
    }
}

@Composable
private fun PendingCourseRow(course: PendingCurriculumCourse) {
    val tokens = appColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = course.courseName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textPrimary
        )
        val meta = buildList {
            course.credit?.let { add(formatNumber(it)) }
            course.suggestedTerm?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textSecondary
            )
        }
    }
}

// ==================== 培养方案课程行 ====================

@Composable
private fun CurriculumCourseRow(
    item: CurriculumCourse,
    onClick: () -> Unit
) {
    val tokens = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.courseName,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary
            )
            val meta = buildList {
                item.category?.takeIf { it.isNotBlank() }?.let { add(it) }
                item.credit?.let { add(formatNumber(it)) }
                item.suggestedTerm?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary
                )
            }
        }
        Icon(
            imageVector = vectorResource(Res.drawable.edit_24px),
            contentDescription = null,
            tint = tokens.textSecondary,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun StudyProgressBar(
    ratio: Float,
    modifier: Modifier = Modifier
) {
    val tokens = appColors()
    LinearProgressIndicator(
        progress = { ratio },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = tokens.primary,
        trackColor = tokens.primary.copy(alpha = 0.15f)
    )
}

// ==================== 类别要求编辑弹窗 ====================

@Composable
private fun StudyRequirementDialog(
    initial: StudyCategoryProgress?,
    knownCategories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (category: String, credits: Double, requiredCourses: Int?, displayName: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    val tokens = appColors()
    val isNew = initial == null
    val initialCredits = initial?.requiredCredits ?: 0.0
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var displayName by remember { mutableStateOf(initial?.displayName.orEmpty()) }
    var creditText by remember { mutableStateOf(if (initialCredits > 0.0) formatNumber(initialCredits) else "") }
    var courseCountText by remember {
        mutableStateOf(initial?.requiredCourses?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var categoryError by remember { mutableStateOf(false) }
    var creditError by remember { mutableStateOf(false) }
    var courseCountError by remember { mutableStateOf(false) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isNew) Res.string.study_category_add else Res.string.study_category_edit
                )
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isNew) {
                    AppTextField(
                        value = category,
                        onValueChange = {
                            category = it
                            categoryError = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.study_category_key_label),
                        placeholder = stringResource(Res.string.study_category_key_placeholder),
                        isError = categoryError
                    )
                    if (categoryError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.study_category_key_required),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.danger
                        )
                    }
                    if (knownCategories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                Res.string.study_category_suggestions,
                                knownCategories.joinToString(" / ")
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary
                        )
                    }
                } else {
                    Text(
                        text = stringResource(
                            Res.string.study_category_key_readonly,
                            initial.category.ifBlank {
                                stringResource(Res.string.study_uncategorized)
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.study_category_display_label),
                    placeholder = stringResource(Res.string.study_category_display_placeholder)
                )
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = creditText,
                    onValueChange = {
                        creditText = it
                        creditError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.study_required_credits_label),
                    placeholder = stringResource(Res.string.study_required_credits_placeholder),
                    isError = creditError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (creditError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.study_required_credits_invalid),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.danger
                    )
                }
                // 应修门数（v4.75.0）：只填学分时页面只能报「已出分几门」，
                // 填了它（或导入课程清单）才能报「应修 / 已修 / 未修」
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = courseCountText,
                    onValueChange = {
                        courseCountText = it
                        courseCountError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.study_required_courses_label),
                    placeholder = stringResource(Res.string.study_required_courses_placeholder),
                    isError = courseCountError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (courseCountError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.study_required_courses_invalid),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.danger
                    )
                }
                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.study_requirement_delete),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.danger,
                        modifier = Modifier.clickable(onClick = onDelete)
                    )
                }
            }
        },
        confirmButton = {
            AppDialogActions(
                confirmText = stringResource(Res.string.grade_save),
                onConfirm = {
                    val key = category.trim()
                    val rawCredits = creditText.trim()
                    val credits = rawCredits.toDoubleOrNull()
                    val rawCourseCount = courseCountText.trim()
                    val courseCount = rawCourseCount.toIntOrNull()
                    categoryError = isNew && key.isEmpty()
                    // 留空 = 0 学分（允许「无学分要求」的类别）；填了却解析不出来才算错误
                    creditError = when {
                        rawCredits.isEmpty() -> false
                        credits == null -> true
                        else -> credits < 0.0 || credits > CreditRequirement.MAX_REQUIRED_CREDITS
                    }
                    courseCountError = when {
                        rawCourseCount.isEmpty() -> false
                        courseCount == null -> true
                        else -> courseCount < 0 || courseCount > CreditRequirement.MAX_REQUIRED_COURSES
                    }
                    if (!categoryError && !creditError && !courseCountError) {
                        onConfirm(key, credits ?: 0.0, courseCount?.takeIf { it > 0 }, displayName)
                    }
                },
                dismissText = stringResource(Res.string.grade_cancel),
                onDismiss = onDismiss
            )
        }
    )
}

// ==================== 培养方案课程编辑弹窗 ====================

@Composable
private fun CurriculumCourseDialog(
    initial: CurriculumCourse?,
    knownCategories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String?, credit: Double?, term: String?) -> Unit,
    onDelete: (() -> Unit)?
) {
    val tokens = appColors()
    var name by remember { mutableStateOf(initial?.courseName.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var creditText by remember { mutableStateOf(initial?.credit?.let { formatNumber(it) }.orEmpty()) }
    var term by remember { mutableStateOf(initial?.suggestedTerm.orEmpty()) }
    var nameError by remember { mutableStateOf(false) }
    var creditError by remember { mutableStateOf(false) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (initial == null) Res.string.study_curriculum_add else Res.string.study_curriculum_edit
                )
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.study_curriculum_name_label),
                    placeholder = stringResource(Res.string.study_curriculum_name_placeholder),
                    isError = nameError
                )
                if (nameError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.study_curriculum_name_required),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.danger
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.grade_category_label),
                    placeholder = stringResource(Res.string.study_category_key_placeholder)
                )
                if (knownCategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            Res.string.study_category_suggestions,
                            knownCategories.joinToString(" / ")
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = creditText,
                    onValueChange = {
                        creditText = it
                        creditError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.study_curriculum_credit_label),
                    isError = creditError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (creditError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.study_required_credits_invalid),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.danger
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = term,
                    onValueChange = { term = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.study_curriculum_term_label),
                    placeholder = stringResource(Res.string.study_curriculum_term_placeholder)
                )
                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.grade_delete),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.danger,
                        modifier = Modifier.clickable(onClick = onDelete)
                    )
                }
            }
        },
        confirmButton = {
            AppDialogActions(
                confirmText = stringResource(Res.string.grade_save),
                onConfirm = {
                    val rawCredit = creditText.trim()
                    val credit = rawCredit.toDoubleOrNull()
                    nameError = name.trim().isEmpty()
                    creditError = when {
                        rawCredit.isEmpty() -> false
                        credit == null -> true
                        else -> credit < 0.0 || credit > 30.0
                    }
                    if (!nameError && !creditError) {
                        onConfirm(
                            name.trim(),
                            category.trim().ifBlank { null },
                            credit?.takeIf { it > 0.0 },
                            term.trim().ifBlank { null }
                        )
                    }
                },
                dismissText = stringResource(Res.string.grade_cancel),
                onDismiss = onDismiss
            )
        }
    )
}

// ==================== 粘贴导入培养方案 ====================

/**
 * 粘贴导入对话框：粘贴教务的培养方案 / 学业情况页文本 → 本地解析 → 可编辑草稿 → 导入。
 *
 * 解析是启发式（各校培养方案页格式千差万别），所以草稿的课程名 / 类别 / 学分都能就地改，
 * 确认前不会写库。类别要求与课程清单一并展示，用户能看出这次会写进什么。
 */
@Composable
private fun CurriculumPasteDialog(
    onDismiss: () -> Unit,
    onParse: (String) -> ParsedCurriculum,
    onImport: (ParsedCurriculum) -> Unit
) {
    val tokens = appColors()
    var rawText by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf(ParsedCurriculum()) }
    var parseAttempted by remember { mutableStateOf(false) }
    val draftCourses = remember { mutableStateListOf<ParsedCurriculumCourse>() }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.study_curriculum_paste_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(Res.string.study_curriculum_paste_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                AppTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(Res.string.study_curriculum_paste_placeholder),
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8
                )
                Spacer(modifier = Modifier.height(10.dp))
                AppDialogActions(
                    confirmText = stringResource(Res.string.study_curriculum_paste_parse),
                    onConfirm = {
                        val result = onParse(rawText)
                        parsed = result
                        draftCourses.clear()
                        draftCourses.addAll(result.courses)
                        parseAttempted = true
                    },
                    dismissText = stringResource(Res.string.grade_cancel),
                    onDismiss = onDismiss
                )

                if (!parsed.isEmpty) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(
                            Res.string.study_curriculum_paste_result,
                            parsed.requirements.size,
                            draftCourses.size
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    parsed.requirements.forEach { requirement ->
                        Text(
                            text = "${requirement.effectiveDisplayName} · " +
                                formatNumber(requirement.requiredCredits) +
                                requirement.requiredCourses?.let { " · $it" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    draftCourses.forEachIndexed { index, draft ->
                        CurriculumDraftRow(
                            draft = draft,
                            onChange = { updated -> draftCourses[index] = updated },
                            onRemove = { draftCourses.removeAt(index) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else if (parseAttempted) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.study_curriculum_paste_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.danger
                    )
                }
            }
        },
        confirmButton = {
            if (!parsed.isEmpty) {
                AppDialogActions(
                    confirmText = stringResource(Res.string.grade_save),
                    onConfirm = {
                        onImport(parsed.copy(courses = draftCourses.toList()))
                    },
                    dismissText = stringResource(Res.string.grade_cancel),
                    onDismiss = onDismiss
                )
            }
        }
    )
}

/** 粘贴预览里的课程草稿行（课程名 / 类别 / 学分三格都可编辑）。 */
@Composable
private fun CurriculumDraftRow(
    draft: ParsedCurriculumCourse,
    onChange: (ParsedCurriculumCourse) -> Unit,
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
            value = draft.category.orEmpty(),
            onValueChange = {
                onChange(draft.copy(category = it.trim().takeIf { c -> c.isNotEmpty() }))
            },
            modifier = Modifier.width(80.dp)
        )
        AppTextField(
            value = draft.credit?.let { formatNumber(it) } ?: "",
            onValueChange = {
                onChange(draft.copy(credit = it.trim().takeIf { c -> c.isNotEmpty() }?.toDoubleOrNull()))
            },
            modifier = Modifier.width(64.dp)
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
