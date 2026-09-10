package com.shangkeschedule.ui.settings.import

import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.theme.appColors

import org.jetbrains.compose.resources.vectorResource
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.import_textfile_action_select
import shangkeschedule.shared.generated.resources.import_textfile_desc_fallback
import shangkeschedule.shared.generated.resources.import_textfile_fmt_info
import shangkeschedule.shared.generated.resources.import_status_parsing
import shangkeschedule.shared.generated.resources.import_detected_fmt
import shangkeschedule.shared.generated.resources.import_selected_fmt
import shangkeschedule.shared.generated.resources.import_toast_success
import shangkeschedule.shared.generated.resources.import_error_no_file
import shangkeschedule.shared.generated.resources.import_cat_text_file
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
 * 文本类文件导入页：CSV / ICS / HTML / TXT 文件（JSON 由独立导入流程处理）。
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
        else -> listOf("csv", "ics", "html", "txt") // AUTO 不接收 .json：JSON 走独立导入流程（带覆盖确认）
    }

    // 按格式定制：说明文案
    val hintText = forcedFormat?.let { fmt ->
        stringResource(Res.string.import_textfile_fmt_info, fmt.label, fmt.hint)
    } ?: stringResource(Res.string.import_textfile_desc_fallback)

    // Toast 文案在非组合式回调中使用：提前在组合式作用域解析（stringResource 限定）
    val toastNoFile = stringResource(Res.string.import_error_no_file)
    val toastSuccess = stringResource(Res.string.import_toast_success)
    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onFileImported = { bytes, fileName ->
                if (bytes == null) {
                    ToastManager.show(toastNoFile)
                } else {
                    viewModel.parseFileBytes(bytes, fileName, forcedFormat)
                }
            }
        )
    )

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(forcedFormat?.screenTitle ?: stringResource(Res.string.import_cat_text_file)) },
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
                text = hintText,
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.clearFile(); fileManager.importFile(allowedExtensions) },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.import_textfile_action_select))
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
                Text(text = err, color = appColors().danger)
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
