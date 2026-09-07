package com.shangkeschedule.ui.settings.import

import org.jetbrains.compose.resources.vectorResource
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.import_excel_desc_support
import shangkeschedule.shared.generated.resources.import_status_parsing
import shangkeschedule.shared.generated.resources.import_detected_fmt
import shangkeschedule.shared.generated.resources.import_selected_fmt
import shangkeschedule.shared.generated.resources.import_excel_action_select
import shangkeschedule.shared.generated.resources.import_toast_success
import shangkeschedule.shared.generated.resources.import_error_no_file
import shangkeschedule.shared.generated.resources.import_cat_excel
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

    // Toast 文案在非组合式回调中使用：提前在组合式作用域解析（stringResource 限定）
    val toastNoFile = stringResource(Res.string.import_error_no_file)
    val toastSuccess = stringResource(Res.string.import_toast_success)
    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onFileImported = { bytes, fileName ->
                if (bytes == null) {
                    ToastManager.show(toastNoFile)
                } else {
                    viewModel.parseFileBytes(bytes, fileName)
                }
            }
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.import_cat_excel)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = stringResource(Res.string.a11y_back))
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
                text = stringResource(Res.string.import_excel_desc_support),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.clearFile(); fileManager.importFile(listOf("xlsx")) },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.import_excel_action_select))
            }

            uiState.fileName?.let { name ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.import_selected_fmt, name),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.detectedFormat.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.import_detected_fmt, uiState.detectedFormat),
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
                    Text(stringResource(Res.string.import_status_parsing))
                }
            }

            // 预览 + 导入
            uiState.parseResult?.let { model ->
                Spacer(modifier = Modifier.height(16.dp))
                ImportPreviewSection(
                    model = model,
                    // P1-6 预览可编辑：编辑/删除结果回写 VM，保证导入数据与预览一致
                    onCoursesChanged = viewModel::updateParsedCourses
                )

                Spacer(modifier = Modifier.height(16.dp))
                ImportDestinationForm(
                    isLoading = uiState.isLoading,
                    // 默认表名取文件名（去扩展名）
                    defaultName = uiState.fileName?.substringBeforeLast('.')?.take(20) ?: "",
                    onImportToNewTable = { tableName ->
                        viewModel.importToNewTable(
                            tableName = tableName,
                            onSuccess = { newTableId ->
                                ToastManager.show(toastSuccess)
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
                                ToastManager.show(toastSuccess)
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
