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
import com.shangkeschedule.data.parser.TextImportFormat
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.ToastManager
import org.koin.compose.viewmodel.koinViewModel

/**
 * 文本类文件导入页：CSV / ICS / HTML / TXT / JSON 文件。
 * 读取文件内容后走通用解析回退链（按扩展名优先定向），先预览再导入为新课表。
 *
 * @param forcedFormat 指定格式类别（来自文件导入分类页）；null/自动 = 按扩展名或自动嗅探
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextFileImportScreen(
    onBack: () -> Unit,
    onImportSuccess: (String) -> Unit,
    forcedFormat: TextImportFormat? = null,
    viewModel: TextImportViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 按格式定制：允许的文件扩展名
    val allowedExtensions: List<String> = when (forcedFormat) {
        TextImportFormat.ICS -> listOf("ics", "ical", "ifb")
        TextImportFormat.CSV -> listOf("csv")
        TextImportFormat.HTML -> listOf("html", "htm")
        TextImportFormat.JSON -> listOf("json")
        else -> listOf("csv", "ics", "html", "txt", "json")
    }

    // 按格式定制：说明文案
    val hintText = forcedFormat?.let { fmt ->
        "导入 ${fmt.label} 文件：\n${fmt.hint}"
    } ?: "支持文本类课表文件，按扩展名优先识别，失败自动回退其他格式：\n" +
            "CSV（表头：课程,教师,教室,星期,节次,周次）\n" +
            "ICS 日历（BEGIN:VCALENDAR）\n" +
            "HTML 表格（<table>）\n" +
            "TXT 纯文本（一行一课）"

    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onFileImported = { bytes, fileName ->
                if (bytes == null) {
                    ToastManager.show("未选择文件")
                } else {
                    viewModel.parseFileBytes(bytes, fileName, forcedFormat)
                }
            }
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(forcedFormat?.screenTitle ?: "文本文件导入") },
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
                text = hintText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.clearFile(); fileManager.importFile(allowedExtensions) },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("选择课表文件")
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
                    text = "识别格式：${uiState.detectedFormat}",
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
