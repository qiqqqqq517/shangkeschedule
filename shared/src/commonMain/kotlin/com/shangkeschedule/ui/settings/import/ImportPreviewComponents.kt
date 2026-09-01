package com.shangkeschedule.ui.settings.import

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.shangkeschedule.data.model.CourseImportExport.CourseTableImportModel
import com.shangkeschedule.ui.components.CourseTablePickerDialog

/**
 * 导入预览组件：解析结果课程列表（各导入二级页共用）。
 */
@Composable
internal fun ImportPreviewSection(model: CourseTableImportModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "预览（共 ${model.courses.size} 门课程）",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            items(model.courses.take(50)) { course ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = course.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${course.teacher} · ${course.position} · 周${course.day} · " +
                                    "${course.startSection ?: "?"}-${course.endSection ?: "?"}节 · " +
                                    "${course.weeks.size}周",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        course.credit?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = "学分: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        course.remark?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = "备注: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
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
        OutlinedTextField(
            value = tableName,
            onValueChange = { tableName = it },
            label = { Text("新课表名称") },
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
                    Text("处理中…")
                }
            } else {
                Text("导入为新课表")
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
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Text("另存为新课表") }
            Button(
                onClick = { mode = ImportDestinationMode.EXISTING_TABLE },
                enabled = !isLoading,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mode == ImportDestinationMode.EXISTING_TABLE) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (mode == ImportDestinationMode.EXISTING_TABLE) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Text("覆盖已有课表") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (mode) {
            ImportDestinationMode.NEW_TABLE -> {
                OutlinedTextField(
                    value = tableName,
                    onValueChange = { tableName = it },
                    label = { Text("新课表名称") },
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
                            Text("处理中…")
                        }
                    } else {
                        Text("导入为新课表")
                    }
                }
            }
            ImportDestinationMode.EXISTING_TABLE -> {
                OutlinedButton(
                    onClick = { showPicker = true },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedTable?.name ?: "选择要覆盖的课表")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { selectedTable?.let { onImportToExistingTable(it.id) } },
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
                            Text("处理中…")
                        }
                    } else {
                        Text("覆盖导入到所选课表")
                    }
                }
            }
        }
    }

    if (showPicker) {
        CourseTablePickerDialog(
            title = "选择要覆盖的课表",
            onDismissRequest = { showPicker = false },
            onTableSelected = { table ->
                selectedTable = table
                showPicker = false
            }
        )
    }
}
