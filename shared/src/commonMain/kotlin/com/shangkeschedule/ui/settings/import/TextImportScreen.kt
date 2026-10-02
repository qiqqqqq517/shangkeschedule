package com.shangkeschedule.ui.settings.import

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ThemedLoadingIndicator
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing

import org.jetbrains.compose.resources.vectorResource
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.import_error_no_file
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.import_text_preview
import shangkeschedule.shared.generated.resources.import_text_paste_label
import shangkeschedule.shared.generated.resources.import_text_any_desc
import shangkeschedule.shared.generated.resources.import_text_any_title
import shangkeschedule.shared.generated.resources.import_detected_fmt
import shangkeschedule.shared.generated.resources.import_ai_text_hint
import shangkeschedule.shared.generated.resources.import_external_text_hint
import shangkeschedule.shared.generated.resources.import_toast_success
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shangkeschedule.data.parser.TextImportFormat
import com.shangkeschedule.tool.ExternalTextImport
import com.shangkeschedule.tool.ExternalTextSource
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.ToastManager
import org.koin.compose.viewmodel.koinViewModel

/**
 * 文本粘贴导入页面（按格式类别拆分的二级页）。
 *
 * @param format 强制的格式类别（来自文本导入分类页）；null = 自动嗅探全部格式
 *
 * 流程：输入文本 → 解析预览 → 输入课表名 → 导入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextImportScreen(
    onBack: () -> Unit,
    onImportSuccess: (String) -> Unit,
    format: TextImportFormat? = null,
    viewModel: TextImportViewModel = koinViewModel()
) {
    // Toast 文案在非组合式回调中使用：提前在组合式作用域解析（stringResource 限定）
    val toastNoFile = stringResource(Res.string.import_error_no_file)
    val toastSuccess = stringResource(Res.string.import_toast_success)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // v4.66.0（L1 / J1）：外部文本（系统文本选择工具栏的「上课」，或 AI 识别导入的结果）
    // 一次性带入并预填；消费后立刻 clear，之后返回/重进本页不会重复灌入。
    val pendingExternalText by ExternalTextImport.pending.collectAsStateWithLifecycle()
    val externalTextSource by ExternalTextImport.source.collectAsStateWithLifecycle()
    var fromExternalText by remember { mutableStateOf(false) }
    LaunchedEffect(pendingExternalText) {
        val external = pendingExternalText ?: return@LaunchedEffect
        viewModel.updateInputText(external)
        fromExternalText = true
        ExternalTextImport.clear()
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(format?.screenTitle() ?: stringResource(Res.string.import_text_any_title)) },
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
                .padding(horizontal = appSpacing().pageHorizontal, vertical = appSpacing().pageTop)
        ) {
            // 格式说明
            Text(
                text = if (format != null) {
                    stringResource(format.hintRes())
                } else {
                    stringResource(Res.string.import_text_any_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary
            )
            // 外部文本带入提示（v4.66.0 · L1 选中文本 / J1 AI 识别）
            if (fromExternalText) {
                Spacer(modifier = Modifier.height(appSpacing().listGap))
                Text(
                    text = if (externalTextSource == ExternalTextSource.AI) {
                        stringResource(Res.string.import_ai_text_hint)
                    } else {
                        stringResource(Res.string.import_external_text_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(appSpacing().listGap))

            // 文本输入框
            AppTextField(
                value = uiState.inputText,
                onValueChange = viewModel::updateInputText,
                label = stringResource(Res.string.import_text_paste_label),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                maxLines = 8
            )
            Spacer(modifier = Modifier.height(appSpacing().listGap))

            // 解析按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.parseInput(format) }, enabled = !uiState.isLoading) {
                    Text(stringResource(Res.string.import_text_preview))
                }
                if (uiState.isLoading) {
                    ThemedLoadingIndicator()
                }
                if (uiState.detectedFormat.isNotBlank()) {
                    Text(
                        text = stringResource(Res.string.import_detected_fmt, uiState.detectedFormat),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 错误提示
            uiState.error?.let { err ->
                Spacer(modifier = Modifier.height(appSpacing().listGap))
                Text(text = err, color = appColors().danger)
            }

            // 预览区域
            uiState.parseResult?.let { model ->
                Spacer(modifier = Modifier.height(appSpacing().cardGap))
                ImportPreviewSection(
                    model = model,
                    // P1-6 预览可编辑：编辑/删除结果回写 VM，保证导入数据与预览一致
                    onCoursesChanged = viewModel::updateParsedCourses
                )

                // 选择导入目标：另存为新课表 / 覆盖已有课表
                Spacer(modifier = Modifier.height(appSpacing().cardGap))
                ImportDestinationForm(
                    isLoading = uiState.isLoading,
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
