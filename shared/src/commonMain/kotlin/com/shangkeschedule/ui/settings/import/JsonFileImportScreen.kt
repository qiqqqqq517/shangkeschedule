package com.shangkeschedule.ui.settings.import

import org.jetbrains.compose.resources.vectorResource
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.import_json_desc_support
import shangkeschedule.shared.generated.resources.import_json_dialog_title
import shangkeschedule.shared.generated.resources.import_status_importing
import shangkeschedule.shared.generated.resources.import_json_action_select_and_import
import shangkeschedule.shared.generated.resources.import_json_step2
import shangkeschedule.shared.generated.resources.import_json_pick_table_placeholder
import shangkeschedule.shared.generated.resources.import_json_step1
import shangkeschedule.shared.generated.resources.import_json_title
import shangkeschedule.shared.generated.resources.import_error_no_table
import shangkeschedule.shared.generated.resources.import_toast_success
import shangkeschedule.shared.generated.resources.import_error_no_file
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangkeschedule.ui.components.CourseTablePickerDialog
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.dialog_title_confirm_overwrite_import
import shangkeschedule.shared.generated.resources.dialog_text_confirm_overwrite_import
import shangkeschedule.shared.generated.resources.action_overwrite_import

/**
 * JSON 文件导入二级页。
 * 流程：选择目标课表 → 选择 .json 文件 → 解析入库（本 App 导出的 JSON 完整保留配置/时间段；
 * 兼容 WakeUp JSON）。会覆盖所选课表的课程数据。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonFileImportScreen(
    onBack: () -> Unit,
    onImportSuccess: (String) -> Unit,
    viewModel: TextImportViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showTablePicker by remember { mutableStateOf(false) }
    var selectedTableId by remember { mutableStateOf<String?>(null) }
    var selectedTableName by remember { mutableStateOf<String?>(null) }
    // 文件回调发生时使用的目标课表 id（选择文件前暂存）
    var pendingTableId by remember { mutableStateOf<String?>(null) }
    // 已选文件 + 目标课表，等待用户确认覆盖后真正入库
    var pendingImport by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }

    // Toast 文案在非组合式回调中使用：提前在组合式作用域解析（stringResource 限定）
    val toastNoFile = stringResource(Res.string.import_error_no_file)
    val toastSuccess = stringResource(Res.string.import_toast_success)
    val toastNoTable = stringResource(Res.string.import_error_no_table)
    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onFileImported = { bytes, fileName ->
                val tableId = pendingTableId
                pendingTableId = null
                if (bytes == null) {
                    ToastManager.show(toastNoFile)
                } else if (tableId == null) {
                    ToastManager.show(toastNoTable)
                } else {
                    // 覆盖导入会清空目标课表课程数据：先二次确认
                    pendingImport = bytes to tableId
                }
            }
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.import_json_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back)
                        )
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
                text = stringResource(Res.string.import_json_desc_support),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 第一步：选择目标课表
            Text(
                text = stringResource(Res.string.import_json_step1),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showTablePicker = true },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedTableName ?: stringResource(Res.string.import_json_pick_table_placeholder))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 第二步：选择 JSON 文件并导入
            Text(
                text = stringResource(Res.string.import_json_step2),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    pendingTableId = selectedTableId
                    fileManager.importFile(listOf("json"))
                },
                enabled = selectedTableId != null && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.import_json_action_select_and_import))
            }

            if (uiState.isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.padding(start = 12.dp))
                    Text(stringResource(Res.string.import_status_importing))
                }
            }

            uiState.error?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = err, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showTablePicker) {
        CourseTablePickerDialog(
            title = stringResource(Res.string.import_json_dialog_title),
            onDismissRequest = { showTablePicker = false },
            onTableSelected = { table ->
                selectedTableId = table.id
                selectedTableName = table.name
                showTablePicker = false
            }
        )
    }

    pendingImport?.let { (bytes, tableId) ->
        AppDangerDialog(
            onDismissRequest = { pendingImport = null },
            title = stringResource(Res.string.dialog_title_confirm_overwrite_import),
            text = stringResource(Res.string.dialog_text_confirm_overwrite_import, selectedTableName ?: ""),
            confirmText = stringResource(Res.string.action_overwrite_import),
            onConfirm = {
                pendingImport = null
                viewModel.importJsonFileIntoTable(
                    bytes = bytes,
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
