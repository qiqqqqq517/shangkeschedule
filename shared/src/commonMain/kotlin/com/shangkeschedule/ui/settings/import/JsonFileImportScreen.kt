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
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import org.koin.compose.viewmodel.koinViewModel

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

    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onFileImported = { bytes, fileName ->
                val tableId = pendingTableId
                pendingTableId = null
                if (bytes == null) {
                    ToastManager.show("未选择文件")
                } else if (tableId == null) {
                    ToastManager.show("请先选择目标课表")
                } else {
                    viewModel.importJsonFileIntoTable(
                        bytes = bytes,
                        tableId = tableId,
                        onSuccess = { id ->
                            ToastManager.show("导入成功！")
                            viewModel.reset()
                            onImportSuccess(id)
                        },
                        onError = { err -> ToastManager.show(err) }
                    )
                }
            }
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JSON 文件导入") },
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
                text = "将 .json 课表文件导入到指定课表（会覆盖该课表的课程数据）：\n" +
                        "· 本 App 导出的 JSON：完整保留课程、时间段与学期配置\n" +
                        "· WakeUp JSON（含 courses 数组）：按课程数据导入",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 第一步：选择目标课表
            Text(
                text = "第一步：选择导入到的课表",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showTablePicker = true },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedTableName ?: "选择目标课表")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 第二步：选择 JSON 文件并导入
            Text(
                text = "第二步：选择 JSON 文件",
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
                Text("选择 JSON 文件并导入")
            }

            if (uiState.isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.padding(start = 12.dp))
                    Text("正在导入…")
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
            title = "选择导入到的课表",
            onDismissRequest = { showTablePicker = false },
            onTableSelected = { table ->
                selectedTableId = table.id
                selectedTableName = table.name
                showTablePicker = false
            }
        )
    }
}
