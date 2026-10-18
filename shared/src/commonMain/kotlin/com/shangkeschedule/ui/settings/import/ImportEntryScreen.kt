package com.shangkeschedule.ui.settings.import

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.tool.ExternalTextImport
import com.shangkeschedule.tool.ExternalTextSource
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.AppSegmentedControl
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ThemedLoadingIndicator
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.import_ai_text_hint
import shangkeschedule.shared.generated.resources.import_detected_fmt
import shangkeschedule.shared.generated.resources.import_entry_file_hint
import shangkeschedule.shared.generated.resources.import_entry_file_pick
import shangkeschedule.shared.generated.resources.import_entry_intro
import shangkeschedule.shared.generated.resources.import_entry_source_file
import shangkeschedule.shared.generated.resources.import_entry_source_text
import shangkeschedule.shared.generated.resources.import_entry_title
import shangkeschedule.shared.generated.resources.import_error_no_file
import shangkeschedule.shared.generated.resources.import_external_text_hint
import shangkeschedule.shared.generated.resources.import_selected_fmt
import shangkeschedule.shared.generated.resources.import_status_parsing
import shangkeschedule.shared.generated.resources.import_text_any_desc
import shangkeschedule.shared.generated.resources.import_text_paste_label
import shangkeschedule.shared.generated.resources.import_text_preview
import shangkeschedule.shared.generated.resources.import_toast_success

/** 统一导入页的来源方式（同时作为导航参数）。 */
object ImportSource {
    /** 粘贴文本（默认）。 */
    const val TEXT = "TEXT"

    /** 选择文件。 */
    const val FILE = "FILE"

    /** 由页面自行决定（默认落在「粘贴文本」）。 */
    const val AUTO = "AUTO"

    fun normalize(raw: String?): String = when (raw) {
        FILE -> FILE
        else -> TEXT
    }
}

/**
 * 统一导入页（导入体系整合）。
 *
 * 改造前，除教务系统导入外还有两条并行链路：**文件导入**（Excel / JSON / ICS / CSV / 文本文件
 * 五个二级页）与**文本粘贴导入**（WakeUp / 纯文本 / JSON / CSV / ICS 五个二级页），
 * 最深要连点四层才能到达输入框，而用户在选择格式之前并不知道自己的文件算哪一类
 * —— 解析本来就有自动嗅探与失败回退链（`UniversalScheduleParser.parseAuto`）。
 *
 * 现在只保留真正的差异：**内容从哪来**。选文件还是粘文本，其余（格式识别、预览、
 * 逐条改错、另存新课表 / 覆盖已有课表、覆盖二次确认）完全共用同一条链路。
 *
 * @param source 初始来源（[ImportSource]）；见 [ImportSource.normalize]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportEntryScreen(
    onBack: () -> Unit,
    onImportSuccess: (String) -> Unit,
    source: String = ImportSource.AUTO,
    viewModel: TextImportViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Toast 文案在非组合式回调中使用：提前在组合式作用域解析（stringResource 限定）
    val toastNoFile = stringResource(Res.string.import_error_no_file)
    val toastSuccess = stringResource(Res.string.import_toast_success)

    var mode by remember(source) { mutableStateOf(ImportSource.normalize(source)) }

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

    // 外部文本（系统文本选择工具栏的「上课」，或 AI 识别结果）一次性带入并预填；
    // 消费后立刻 clear，之后返回/重进本页不会重复灌入。
    val pendingExternalText by ExternalTextImport.pending.collectAsStateWithLifecycle()
    val externalTextSource by ExternalTextImport.source.collectAsStateWithLifecycle()
    var fromExternalText by remember { mutableStateOf(false) }
    LaunchedEffect(pendingExternalText) {
        val external = pendingExternalText ?: return@LaunchedEffect
        mode = ImportSource.TEXT
        viewModel.updateInputText(external)
        fromExternalText = true
        ExternalTextImport.clear()
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(stringResource(Res.string.import_entry_title)) },
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
                .padding(horizontal = appSpacing().pageHorizontal, vertical = appSpacing().pageTop)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(Res.string.import_entry_intro),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary
            )
            Spacer(Modifier.height(appSpacing().listGap))

            // 来源二选一：粘贴文本 / 选择文件 —— 其余流程完全一致
            AppSegmentedControl(
                options = listOf(
                    stringResource(Res.string.import_entry_source_text),
                    stringResource(Res.string.import_entry_source_file)
                ),
                selectedIndex = if (mode == ImportSource.FILE) 1 else 0,
                onSelect = { index ->
                    mode = if (index == 1) ImportSource.FILE else ImportSource.TEXT
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(appSpacing().cardGap))

            // 外部文本带入提示（系统选中文本 / AI 识别结果）
            if (fromExternalText && mode == ImportSource.TEXT) {
                Text(
                    text = if (externalTextSource == ExternalTextSource.AI) {
                        stringResource(Res.string.import_ai_text_hint)
                    } else {
                        stringResource(Res.string.import_external_text_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(appSpacing().listGap))
            }

            if (mode == ImportSource.TEXT) {
                AppTextField(
                    value = uiState.inputText,
                    onValueChange = viewModel::updateInputText,
                    label = stringResource(Res.string.import_text_paste_label),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 8
                )
                Spacer(Modifier.height(appSpacing().listGap))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.parseInput() },
                        enabled = !uiState.isLoading
                    ) {
                        Text(stringResource(Res.string.import_text_preview))
                    }
                    if (uiState.isLoading) ThemedLoadingIndicator()
                }
                Spacer(Modifier.height(appSpacing().listGap))
                Text(
                    text = stringResource(Res.string.import_text_any_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary
                )
            } else {
                Button(
                    onClick = {
                        viewModel.clearFile()
                        fileManager.importFile(
                            listOf("xlsx", "xls", "json", "csv", "ics", "ical", "html", "htm", "txt")
                        )
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.import_entry_file_pick))
                }
                Spacer(Modifier.height(appSpacing().listGap))
                Text(
                    text = stringResource(Res.string.import_entry_file_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary
                )
                uiState.fileName?.let { name ->
                    Spacer(Modifier.height(appSpacing().listGap))
                    Text(
                        text = stringResource(Res.string.import_selected_fmt, name),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (uiState.detectedFormat.isNotBlank()) {
                Spacer(Modifier.height(appSpacing().listGap))
                Text(
                    text = stringResource(Res.string.import_detected_fmt, uiState.detectedFormat),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            uiState.error?.let { err ->
                Spacer(Modifier.height(appSpacing().listGap))
                Text(text = err, color = appColors().danger)
            }

            if (uiState.isLoading && uiState.parseResult == null) {
                Spacer(Modifier.height(appSpacing().sectionGap))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ThemedLoadingIndicator()
                    Spacer(Modifier.width(appSpacing().sectionTitleGap))
                    Text(stringResource(Res.string.import_status_parsing))
                }
            }

            // 预览 + 目标选择（两种来源共用）
            uiState.parseResult?.let { model ->
                Spacer(Modifier.height(appSpacing().cardGap))
                ImportPreviewSection(
                    model = model,
                    // P1-6 预览可编辑：编辑/删除结果回写 VM，保证导入数据与预览一致
                    onCoursesChanged = viewModel::updateParsedCourses
                )

                Spacer(Modifier.height(appSpacing().cardGap))
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
