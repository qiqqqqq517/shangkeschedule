package com.shangkeschedule.ui.settings.import

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.ToastManager
import org.koin.compose.viewmodel.koinViewModel

/**
 * Excel 导入二级页（.xlsx）。
 * 支持两种形态自动识别：
 * 1. 网格课表：行=节次、列=星期（超级课程表/QQ群课表/教务导出），单元格 ◇ 分隔格式
 * 2. 列表课表：一行一门课（表头：课程/教师/教室/星期/节次/周次）
 * 流程：选择文件 → 解析预览 → 输入课表名 → 导入为新课表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelImportScreen(
    onBack: () -> Unit,
    onImportSuccess: (String) -> Unit,
    viewModel: TextImportViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onFileImported = { bytes, fileName ->
                if (bytes == null) {
                    ToastManager.show("未选择文件")
                } else {
                    viewModel.parseFileBytes(bytes, fileName)
                }
            }
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Excel 导入") },
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "支持 .xlsx 文件，自动识别两种课表形态：\n" +
                        "① 网格课表：行=节次、列=星期（超级课程表 / QQ群课表 / 教务系统导出）\n" +
                        "② 列表课表：一行一门课（表头含 课程/教师/教室/星期/节次/周次）\n" +
                        "旧版 .xls 请先在 Office/WPS 中另存为 .xlsx",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.clearFile(); fileManager.importFile(listOf("xlsx")) },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("选择 Excel 文件")
            }

            uiState.fileName?.let { name ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "已选择：$name",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.detectedFormat.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "识别形态：${uiState.detectedFormat}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            uiState.error?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = err, color = MaterialTheme.colorScheme.error)
            }

            if (uiState.isLoading && uiState.parseResult == null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.padding(start = 12.dp))
                    Text("正在解析文件…")
                }
            }

            // 预览 + 导入
            uiState.parseResult?.let { model ->
                Spacer(modifier = Modifier.height(16.dp))
                ImportPreviewSection(model = model)

                Spacer(modifier = Modifier.height(16.dp))
                ImportDestinationForm(
                    isLoading = uiState.isLoading,
                    // 默认表名取文件名（去扩展名）
                    defaultName = uiState.fileName?.substringBeforeLast('.')?.take(20) ?: "",
                    onImportToNewTable = { tableName ->
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
                    onImportToExistingTable = { tableId ->
                        viewModel.importToExistingTable(
                            tableId = tableId,
                            onSuccess = { id ->
                                ToastManager.show("导入成功！")
                                viewModel.reset()
                                onImportSuccess(id)
                            },
                            onError = { err -> ToastManager.show(err) }
                        )
                    }
                )
            }
        }
    }
}
