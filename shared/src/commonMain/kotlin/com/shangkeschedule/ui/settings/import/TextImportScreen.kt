package com.shangkeschedule.ui.settings.import

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shangkeschedule.ui.components.ToastManager
import org.koin.compose.viewmodel.koinViewModel

/**
 * 文本/文件导入页面
 * 支持格式：WakeUp分享文本、JSON、ICS、CSV、HTML表格、纯文本
 * 流程：输入文本 → 解析预览 → 输入课表名 → 导入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextImportScreen(
    onBack: () -> Unit,
    onImportSuccess: (String) -> Unit,
    viewModel: TextImportViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var tableName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文本/文件导入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 格式说明
            Text(
                text = "支持格式：WakeUp分享文本、JSON、ICS日历、CSV、HTML表格、纯文本（一行一课）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 文本输入框
            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = viewModel::updateInputText,
                label = { Text("粘贴课表内容") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                maxLines = 8
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 解析按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::parseInput) {
                    Text("解析预览")
                }
                if (uiState.detectedFormat.isNotBlank()) {
                    Text(
                        text = "识别格式：${uiState.detectedFormat}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 错误提示
            uiState.error?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = err, color = MaterialTheme.colorScheme.error)
            }

            // 预览区域
            uiState.parseResult?.let { model ->
                Spacer(modifier = Modifier.height(16.dp))
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
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Text(
                                    text = "${course.teacher} · ${course.position} · 周${course.day} · " +
                                            "${course.startSection}-${course.endSection}节 · " +
                                            "${course.weeks.size}周",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

                // 课表名称输入
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = tableName,
                    onValueChange = { tableName = it },
                    label = { Text("新课表名称") },
                    modifier = Modifier.fillMaxWidth()
                )

                // 导入按钮
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.importToNewTable(
                            tableName = tableName,
                            onSuccess = { newTableId ->
                                ToastManager.show("导入成功！")
                                viewModel.reset()
                                onImportSuccess(newTableId)
                            },
                            onError = { err -> ToastManager.show(err) }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("导入为新课表")
                }
            }
        }
    }
}
