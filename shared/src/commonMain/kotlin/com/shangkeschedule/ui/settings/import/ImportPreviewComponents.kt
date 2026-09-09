package com.shangkeschedule.ui.settings.import

import com.shangkeschedule.ui.components.AppCard
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing

import androidx.compose.material3.IconButton
import org.jetbrains.compose.resources.vectorResource
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.import_preview_meta_fmt
import shangkeschedule.shared.generated.resources.import_preview_action_overwrite_selected
import shangkeschedule.shared.generated.resources.import_preview_pick_table_placeholder
import shangkeschedule.shared.generated.resources.import_preview_action_overwrite
import shangkeschedule.shared.generated.resources.import_preview_action_save_as_new
import shangkeschedule.shared.generated.resources.import_preview_action_import_new
import shangkeschedule.shared.generated.resources.import_status_processing
import shangkeschedule.shared.generated.resources.import_preview_new_table_label
import shangkeschedule.shared.generated.resources.import_preview_remark_fmt
import shangkeschedule.shared.generated.resources.import_preview_credit_fmt
import shangkeschedule.shared.generated.resources.import_preview_count_fmt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.model.CourseImportExport
import com.shangkeschedule.data.model.CourseImportExport.CourseTableImportModel
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.CourseTablePickerDialog
import shangkeschedule.shared.generated.resources.dialog_title_confirm_overwrite_import
import shangkeschedule.shared.generated.resources.dialog_text_confirm_overwrite_import
import shangkeschedule.shared.generated.resources.action_overwrite_import
// P1-6 预览条目可编辑
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import shangkeschedule.shared.generated.resources.edit_24px
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.import_edit_entry_title
import shangkeschedule.shared.generated.resources.import_edit_label_name
import shangkeschedule.shared.generated.resources.import_edit_label_teacher
import shangkeschedule.shared.generated.resources.import_edit_label_location
import shangkeschedule.shared.generated.resources.import_edit_label_day
import shangkeschedule.shared.generated.resources.import_edit_label_start_section
import shangkeschedule.shared.generated.resources.import_edit_label_end_section
import shangkeschedule.shared.generated.resources.import_edit_label_weeks
import shangkeschedule.shared.generated.resources.import_edit_error_invalid
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.a11y_edit
import shangkeschedule.shared.generated.resources.a11y_delete
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment

/**
 * 导入预览组件：解析结果课程列表（各导入二级页共用）。
 *
 * P1-6 预览可编辑：传入 onCoursesChanged 后，每条预览提供「编辑/删除」操作——
 * 可修正识别错误的课名/教师/地点/星期/节次/周次，或直接移除误识别条目；
 * 编辑结果经回调回写 ViewModel（TextImportViewModel.updateParsedCourses），
 * 保证最终导入的数据与用户确认的预览一致。不传回调则保持只读（兼容旧调用）。
 */
@Composable
internal fun ImportPreviewSection(
    model: CourseTableImportModel,
    modifier: Modifier = Modifier,
    onCoursesChanged: ((List<CourseImportExport.ImportCourseJsonModel>) -> Unit)? = null
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.import_preview_count_fmt, model.courses.size),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            itemsIndexed(model.courses.take(50)) { index, course ->
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = appSpacing().cardInner, vertical = appSpacing().cardInner),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = course.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(
                                    Res.string.import_preview_meta_fmt,
                                    course.teacher.toString(), course.position.toString(), course.day,
                                    (course.startSection ?: "?").toString(), (course.endSection ?: "?").toString(),
                                    course.weeks.size
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textSecondary
                            )
                            course.credit?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = stringResource(Res.string.import_preview_credit_fmt, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textSecondary
                                )
                            }
                            course.remark?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = stringResource(Res.string.import_preview_remark_fmt, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textSecondary
                                )
                            }
                        }
                        if (onCoursesChanged != null) {
                            IconButton(onClick = { editingIndex = index }) {
                                Icon(
                                    vectorResource(Res.drawable.edit_24px),
                                    contentDescription = stringResource(Res.string.a11y_edit),
                                    modifier = Modifier.size(18.dp),
                                    tint = appColors().textSecondary
                                )
                            }
                            IconButton(onClick = {
                                onCoursesChanged(model.courses.filterIndexed { i, _ -> i != index })
                            }) {
                                Icon(
                                    vectorResource(Res.drawable.delete_24px),
                                    contentDescription = stringResource(Res.string.a11y_delete),
                                    modifier = Modifier.size(18.dp),
                                    tint = appColors().danger
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editingIndex?.let { index ->
        val entry = model.courses.getOrNull(index) ?: run {
            editingIndex = null
            null
        }
        if (entry != null) {
            ImportEntryEditDialog(
                entry = entry,
                onDismiss = { editingIndex = null },
                onConfirm = { updated ->
                    editingIndex = null
                    val newList = model.courses.toMutableList()
                    newList[index] = updated
                    onCoursesChanged?.invoke(newList)
                }
            )
        }
    }
}

/** 周次文本解析（"1-16,19"）：非法返回 null；范围闭合区间；最多 60 周 */
private fun parseWeeksText(text: String): List<Int>? {
    val result = mutableListOf<Int>()
    text.split(',', '，', '、', ' ', ';', '；')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { token ->
            val range = Regex("^(\\d+)[-–~](\\d+)$").find(token)
            if (range != null) {
                val start = range.groupValues[1].toIntOrNull() ?: return null
                val end = range.groupValues[2].toIntOrNull() ?: return null
                if (start < 1 || end < start || end > 60) return null
                result.addAll(start..end)
            } else {
                val n = token.toIntOrNull() ?: return null
                if (n < 1 || n > 60) return null
                result.add(n)
            }
        }
    return if (result.isEmpty()) null else result.distinct().sorted()
}

/**
 * P1-6 编辑单条导入预览条目：课名/教师/地点/星期/节次/周次，
 * 其余字段（自定义时间、颜色等）原样保留。
 */
@Composable
private fun ImportEntryEditDialog(
    entry: CourseImportExport.ImportCourseJsonModel,
    onDismiss: () -> Unit,
    onConfirm: (CourseImportExport.ImportCourseJsonModel) -> Unit
) {
    var nameText by remember(entry) { mutableStateOf(entry.name) }
    var teacherText by remember(entry) { mutableStateOf(entry.teacher) }
    var locationText by remember(entry) { mutableStateOf(entry.position) }
    var dayText by remember(entry) { mutableStateOf(entry.day.toString()) }
    var startText by remember(entry) { mutableStateOf(entry.startSection?.toString() ?: "") }
    var endText by remember(entry) { mutableStateOf(entry.endSection?.toString() ?: "") }
    var weeksText by remember(entry) { mutableStateOf(entry.weeks.joinToString(",")) }
    var showInvalid by remember(entry) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.import_edit_entry_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    placeholder = stringResource(Res.string.import_edit_label_name),
                    singleLine = true
                )
                AppTextField(
                    value = teacherText,
                    onValueChange = { teacherText = it },
                    placeholder = stringResource(Res.string.import_edit_label_teacher),
                    singleLine = true
                )
                AppTextField(
                    value = locationText,
                    onValueChange = { locationText = it },
                    placeholder = stringResource(Res.string.import_edit_label_location),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = dayText,
                        onValueChange = { dayText = it },
                        placeholder = stringResource(Res.string.import_edit_label_day),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    AppTextField(
                        value = startText,
                        onValueChange = { startText = it },
                        placeholder = stringResource(Res.string.import_edit_label_start_section),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    AppTextField(
                        value = endText,
                        onValueChange = { endText = it },
                        placeholder = stringResource(Res.string.import_edit_label_end_section),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                AppTextField(
                    value = weeksText,
                    onValueChange = { weeksText = it },
                    placeholder = stringResource(Res.string.import_edit_label_weeks),
                    singleLine = true
                )
                if (showInvalid) {
                    Text(
                        text = stringResource(Res.string.import_edit_error_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().danger
                    )
                }
            }
        },
        confirmButton = {
            AppDialogActions(
                confirmText = stringResource(Res.string.action_confirm),
                onConfirm = {
                    val day = dayText.trim().toIntOrNull()
                    val start = startText.trim().toIntOrNull()
                    val end = endText.trim().toIntOrNull()
                    val weeks = parseWeeksText(weeksText)
                    val sectionsValid = (start == null && end == null) ||
                        (start != null && end != null && start in 1..30 && end >= start)
                    if (day == null || day !in 1..7 || !sectionsValid || weeks == null ||
                        nameText.isBlank()
                    ) {
                        showInvalid = true
                    } else {
                        onConfirm(
                            entry.copy(
                                name = nameText.trim(),
                                teacher = teacherText.trim(),
                                position = locationText.trim(),
                                day = day,
                                startSection = start,
                                endSection = end,
                                weeks = weeks
                            )
                        )
                    }
                },
                dismissText = stringResource(Res.string.action_cancel),
                onDismiss = onDismiss
            )
        }
    )
}

/**
 * 导入为新课表表单：课表名输入 + 导入按钮（各导入二级页共用）。
 */
@Composable
internal fun ImportNewTableForm(
    isLoading: Boolean,
    defaultName: String = "",
    onImport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tableName by remember { mutableStateOf(defaultName) }

    Column(modifier = modifier) {
        AppTextField(
            value = tableName,
            onValueChange = { tableName = it },
            label = stringResource(Res.string.import_preview_new_table_label),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onImport(tableName) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                Row {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text(stringResource(Res.string.import_status_processing))
                }
            } else {
                Text(stringResource(Res.string.import_preview_action_import_new))
            }
        }
    }
}

/**
 * 文件导入目标选择表单：另存为新课表 / 覆盖已有课表 二选一（Excel / 文本文件导入页共用）。
 */
internal enum class ImportDestinationMode { NEW_TABLE, EXISTING_TABLE }

@Composable
internal fun ImportDestinationForm(
    isLoading: Boolean,
    defaultName: String = "",
    onImportToNewTable: (String) -> Unit,
    onImportToExistingTable: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(ImportDestinationMode.NEW_TABLE) }
    var tableName by remember { mutableStateOf(defaultName) }
    var selectedTable by remember { mutableStateOf<CourseTable?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    // 覆盖导入前待确认的目标课表 id（覆盖会清空该课表课程数据，需二次确认）
    var pendingOverwriteId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        // 导入方式切换
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { mode = ImportDestinationMode.NEW_TABLE },
                enabled = !isLoading,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mode == ImportDestinationMode.NEW_TABLE) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (mode == ImportDestinationMode.NEW_TABLE) MaterialTheme.colorScheme.onPrimary
                    else appColors().textSecondary
                )
            ) { Text(stringResource(Res.string.import_preview_action_save_as_new)) }
            Button(
                onClick = { mode = ImportDestinationMode.EXISTING_TABLE },
                enabled = !isLoading,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mode == ImportDestinationMode.EXISTING_TABLE) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (mode == ImportDestinationMode.EXISTING_TABLE) MaterialTheme.colorScheme.onPrimary
                    else appColors().textSecondary
                )
            ) { Text(stringResource(Res.string.import_preview_action_overwrite)) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (mode) {
            ImportDestinationMode.NEW_TABLE -> {
                AppTextField(
                    value = tableName,
                    onValueChange = { tableName = it },
                    label = stringResource(Res.string.import_preview_new_table_label),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onImportToNewTable(tableName) },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        Row {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            Text(stringResource(Res.string.import_status_processing))
                        }
                    } else {
                        Text(stringResource(Res.string.import_preview_action_import_new))
                    }
                }
            }
            ImportDestinationMode.EXISTING_TABLE -> {
                OutlinedButton(
                    onClick = { showPicker = true },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedTable?.name ?: stringResource(Res.string.import_preview_pick_table_placeholder))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { selectedTable?.let { pendingOverwriteId = it.id } },
                    enabled = selectedTable != null && !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        Row {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            Text(stringResource(Res.string.import_status_processing))
                        }
                    } else {
                        Text(stringResource(Res.string.import_preview_action_overwrite_selected))
                    }
                }
            }
        }
    }

    if (showPicker) {
        CourseTablePickerDialog(
            title = stringResource(Res.string.import_preview_pick_table_placeholder),
            onDismissRequest = { showPicker = false },
            onTableSelected = { table ->
                selectedTable = table
                showPicker = false
            }
        )
    }

    pendingOverwriteId?.let { tableId ->
        val targetName = selectedTable?.name ?: ""
        AppDangerDialog(
            onDismissRequest = { pendingOverwriteId = null },
            title = stringResource(Res.string.dialog_title_confirm_overwrite_import),
            text = stringResource(Res.string.dialog_text_confirm_overwrite_import, targetName),
            confirmText = stringResource(Res.string.action_overwrite_import),
            onConfirm = {
                pendingOverwriteId = null
                onImportToExistingTable(tableId)
            }
        )
    }
}
