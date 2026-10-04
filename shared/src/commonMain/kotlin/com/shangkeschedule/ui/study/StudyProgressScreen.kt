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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.shangkeschedule.data.model.CreditRequirement
import com.shangkeschedule.data.model.StudyCategoryProgress
import com.shangkeschedule.data.model.StudyProgress
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
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.edit_24px
import shangkeschedule.shared.generated.resources.school_24px
import shangkeschedule.shared.generated.resources.study_import_entry
import shangkeschedule.shared.generated.resources.study_import_entry_desc
import shangkeschedule.shared.generated.resources.grade_cancel
import shangkeschedule.shared.generated.resources.grade_save
import shangkeschedule.shared.generated.resources.grade_saved
import shangkeschedule.shared.generated.resources.study_category_add
import shangkeschedule.shared.generated.resources.study_category_add_hint
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
import shangkeschedule.shared.generated.resources.study_category_ratio
import shangkeschedule.shared.generated.resources.study_category_section
import shangkeschedule.shared.generated.resources.study_category_suggestions
import shangkeschedule.shared.generated.resources.study_footnote
import shangkeschedule.shared.generated.resources.study_metric_courses
import shangkeschedule.shared.generated.resources.study_metric_earned
import shangkeschedule.shared.generated.resources.study_metric_missing
import shangkeschedule.shared.generated.resources.study_need_requirement_hint
import shangkeschedule.shared.generated.resources.study_no_grade_hint
import shangkeschedule.shared.generated.resources.study_open_grades
import shangkeschedule.shared.generated.resources.study_overview_percent
import shangkeschedule.shared.generated.resources.study_overview_title
import shangkeschedule.shared.generated.resources.study_overview_value
import shangkeschedule.shared.generated.resources.study_required_credits_invalid
import shangkeschedule.shared.generated.resources.study_required_credits_label
import shangkeschedule.shared.generated.resources.study_required_credits_placeholder
import shangkeschedule.shared.generated.resources.study_requirement_delete
import shangkeschedule.shared.generated.resources.study_requirement_removed
import shangkeschedule.shared.generated.resources.study_uncategorized
import shangkeschedule.shared.generated.resources.title_study_progress

/**
 * 学业情况（培养方案完成度）。
 *
 * 与「成绩与绩点」页的分工：成绩页是**逐条成绩**的录入与查询（含绩点换算、教务导入），
 * 本页是**按课程类别汇总**的学分完成度视图 —— 类别要求由用户自己填（不同学校、不同专业
 * 的培养方案各不相同，按类别名与成绩的 `Grade.category` 对齐），已获学分只统计及格课程。
 *
 * 所有数字都来自本地：成绩表 + 本页设置的类别要求；不联网、不推断。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyProgressScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: StudyProgressViewModel = koinViewModel()
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val tokens = appColors()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StudyCategoryProgress?>(null) }
    val savedText = stringResource(Res.string.grade_saved)
    val removedText = stringResource(Res.string.study_requirement_removed)

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
            onConfirm = { category, credits, displayName ->
                viewModel.upsertRequirement(category, credits, displayName)
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
        if (item.courseCount > 0) {
            add(stringResource(Res.string.study_category_courses, item.courseCount))
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
    onConfirm: (category: String, credits: Double, displayName: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    val tokens = appColors()
    val isNew = initial == null
    val initialCredits = initial?.requiredCredits ?: 0.0
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var displayName by remember { mutableStateOf(initial?.displayName.orEmpty()) }
    var creditText by remember { mutableStateOf(if (initialCredits > 0.0) formatNumber(initialCredits) else "") }
    var categoryError by remember { mutableStateOf(false) }
    var creditError by remember { mutableStateOf(false) }

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
                    categoryError = isNew && key.isEmpty()
                    // 留空 = 0 学分（允许「无学分要求」的类别）；填了却解析不出来才算错误
                    creditError = when {
                        rawCredits.isEmpty() -> false
                        credits == null -> true
                        else -> credits < 0.0 || credits > CreditRequirement.MAX_REQUIRED_CREDITS
                    }
                    if (!categoryError && !creditError) {
                        onConfirm(key, credits ?: 0.0, displayName)
                    }
                },
                dismissText = stringResource(Res.string.grade_cancel),
                onDismiss = onDismiss
            )
        }
    )
}
