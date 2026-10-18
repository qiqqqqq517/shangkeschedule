package com.shangkeschedule.ui.settings.import

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.Destination
import com.shangkeschedule.data.api.AI_IMPORT_SYSTEM_PROMPT
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.copyToClipboard
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppSegmentedControl
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.ai_image_import_copied_hint
import shangkeschedule.shared.generated.resources.ai_image_import_copy_prompt
import shangkeschedule.shared.generated.resources.ai_image_import_copy_prompt_desc
import shangkeschedule.shared.generated.resources.ai_image_import_format_line
import shangkeschedule.shared.generated.resources.ai_image_import_go_paste
import shangkeschedule.shared.generated.resources.ai_image_import_intro
import shangkeschedule.shared.generated.resources.ai_image_import_paste_desc
import shangkeschedule.shared.generated.resources.ai_image_import_prompt_desc
import shangkeschedule.shared.generated.resources.ai_image_import_section_paste
import shangkeschedule.shared.generated.resources.ai_image_import_section_prompt
import shangkeschedule.shared.generated.resources.ai_image_import_section_steps
import shangkeschedule.shared.generated.resources.ai_image_import_step1
import shangkeschedule.shared.generated.resources.ai_image_import_step2
import shangkeschedule.shared.generated.resources.ai_image_import_step3
import shangkeschedule.shared.generated.resources.ai_image_import_toast_copied
import shangkeschedule.shared.generated.resources.ai_image_import_toast_copy_failed
import shangkeschedule.shared.generated.resources.ai_import_api_key
import shangkeschedule.shared.generated.resources.ai_import_api_key_hint
import shangkeschedule.shared.generated.resources.ai_import_base_url
import shangkeschedule.shared.generated.resources.ai_import_base_url_hint
import shangkeschedule.shared.generated.resources.ai_import_clear_image
import shangkeschedule.shared.generated.resources.ai_import_error_detail_format
import shangkeschedule.shared.generated.resources.ai_import_error_empty_input
import shangkeschedule.shared.generated.resources.ai_import_error_empty_result
import shangkeschedule.shared.generated.resources.ai_import_error_http
import shangkeschedule.shared.generated.resources.ai_import_error_image_too_large
import shangkeschedule.shared.generated.resources.ai_import_error_network
import shangkeschedule.shared.generated.resources.ai_import_error_not_configured
import shangkeschedule.shared.generated.resources.ai_import_error_parse
import shangkeschedule.shared.generated.resources.ai_import_error_unsupported_image
import shangkeschedule.shared.generated.resources.ai_import_image_selected
import shangkeschedule.shared.generated.resources.ai_import_method_api
import shangkeschedule.shared.generated.resources.ai_import_method_prompt
import shangkeschedule.shared.generated.resources.ai_import_model
import shangkeschedule.shared.generated.resources.ai_import_model_hint
import shangkeschedule.shared.generated.resources.ai_import_need_config
import shangkeschedule.shared.generated.resources.ai_import_notice_cancel
import shangkeschedule.shared.generated.resources.ai_import_notice_confirm
import shangkeschedule.shared.generated.resources.ai_import_notice_message
import shangkeschedule.shared.generated.resources.ai_import_notice_title
import shangkeschedule.shared.generated.resources.ai_import_paste_label
import shangkeschedule.shared.generated.resources.ai_import_paste_placeholder
import shangkeschedule.shared.generated.resources.ai_import_pick_image
import shangkeschedule.shared.generated.resources.ai_import_pick_image_desc
import shangkeschedule.shared.generated.resources.ai_import_recognize
import shangkeschedule.shared.generated.resources.ai_import_save_config
import shangkeschedule.shared.generated.resources.ai_import_save_config_desc
import shangkeschedule.shared.generated.resources.ai_import_section_config
import shangkeschedule.shared.generated.resources.ai_import_section_input
import shangkeschedule.shared.generated.resources.ai_import_section_switch
import shangkeschedule.shared.generated.resources.ai_import_tip
import shangkeschedule.shared.generated.resources.ai_import_toast_config_saved
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.content_copy_24px
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.desc_ai_import
import shangkeschedule.shared.generated.resources.image_24px
import shangkeschedule.shared.generated.resources.save_24px
import shangkeschedule.shared.generated.resources.title_ai_import

/**
 * 「AI 识别导入」页（v4.66.0 起；v4.80.0 合并原「AI 图片导入」页）。
 *
 * 两条识别路线前提完全不同，此前是**两个独立入口、两套说明**，用户必须先分清「我有没有
 * 接口 Key」才能选对页面。现在合并成一页，用分段选择器切换：
 *
 * - **提示词法**（默认）：复制固定提示词交给用户自己的 AI，再把输出粘回来。不联网、
 *   不需要任何配置，覆盖绝大多数学生；
 * - **接口法**：用户在应用内填 OpenAI 兼容地址 / 模型 / Key，由应用代发。
 *
 * 两条路线产出的都是同一种「一行一门课」纯文本（共用 [AI_IMPORT_SYSTEM_PROMPT]），
 * 最终都交给统一导入页 [ImportEntryScreen] 预览、改错、再入库 —— 不直接写课表。
 *
 * 接口法沿用三条硬约束：默认关闭、首次开启前明示外发、Key 只存本机不进备份。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiImportScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
    viewModel: AiImportViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedToast = stringResource(Res.string.ai_import_toast_config_saved)
    val toastCopied = stringResource(Res.string.ai_image_import_toast_copied)
    val toastCopyFailed = stringResource(Res.string.ai_image_import_toast_copy_failed)

    // 默认走提示词法：不需要任何配置，先给能立刻用起来的路线
    var method by remember { mutableStateOf(0) }
    var promptCopied by remember { mutableStateOf(false) }

    // 选图走 importFile（拿原始字节交给接口），不用 pickImage（那是给界面显示用的 ImageBitmap）
    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onFileImported = { bytes, fileName -> viewModel.onImagePicked(bytes, fileName) }
        )
    )

    LaunchedEffect(uiState.toast) {
        when (uiState.toast) {
            AiImportToast.CONFIG_SAVED -> {
                ToastManager.show(savedToast)
                viewModel.consumeToast()
            }

            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.title_ai_import),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = appSpacing().pageHorizontal,
                    vertical = appSpacing().pageTop
                ),
            verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap)
        ) {
            AppSegmentedControl(
                options = listOf(
                    stringResource(Res.string.ai_import_method_prompt),
                    stringResource(Res.string.ai_import_method_api)
                ),
                selectedIndex = method,
                onSelect = { method = it },
                modifier = Modifier.fillMaxWidth()
            )

            if (method == 0) {
                PromptMethodContent(
                    promptCopied = promptCopied,
                    onCopyPrompt = {
                        val copied = copyToClipboard(AI_IMPORT_SYSTEM_PROMPT)
                        promptCopied = copied
                        ToastManager.show(if (copied) toastCopied else toastCopyFailed)
                    },
                    onGoPaste = { onNavigate(Destination.ImportEntry(ImportSource.TEXT)) }
                )
            } else {
                ApiMethodContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    fileManager = fileManager,
                    onRecognized = { onNavigate(Destination.ImportEntry(ImportSource.TEXT)) }
                )
            }

            Spacer(Modifier.height(appSpacing().contentBottom))
        }
    }

    // 首次开启前的数据外发确认：确认后才真正写入开关
    if (uiState.showNoticeDialog) {
        AppAlertDialog(
            onDismissRequest = viewModel::onNoticeDismiss,
            confirmButton = {
                AppDialogActions(
                    confirmText = stringResource(Res.string.ai_import_notice_confirm),
                    onConfirm = viewModel::onNoticeAccepted,
                    dismissText = stringResource(Res.string.ai_import_notice_cancel),
                    onDismiss = viewModel::onNoticeDismiss
                )
            },
            title = { Text(stringResource(Res.string.ai_import_notice_title)) },
            text = { Text(stringResource(Res.string.ai_import_notice_message)) }
        )
    }
}

/**
 * 提示词法：复制固定提示词 → 用自己的 AI 识别 → 粘回统一导入页。
 * 全程不发网络请求、不保存任何配置。
 */
@Composable
private fun PromptMethodContent(
    promptCopied: Boolean,
    onCopyPrompt: () -> Unit,
    onGoPaste: () -> Unit
) {
    Text(
        text = stringResource(Res.string.ai_image_import_intro),
        style = MaterialTheme.typography.bodySmall,
        color = appColors().textSecondary
    )

    AppSectionHeader(stringResource(Res.string.ai_image_import_section_prompt))
    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(appSpacing().cardInner)
        ) {
            Text(
                text = stringResource(Res.string.ai_image_import_prompt_desc),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary
            )
            Spacer(modifier = Modifier.height(appSpacing().listGap))
            // 提示词原文：可选中（复制）以便剪贴板不可用时手动选取，
            // 底用 inputBg 与卡片区分，明确「这是要复制走的内容」。
            Surface(
                color = appColors().inputBg,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                SelectionContainer {
                    Text(
                        text = AI_IMPORT_SYSTEM_PROMPT,
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textPrimary,
                        modifier = Modifier.padding(appSpacing().cardInner)
                    )
                }
            }
        }
        SectionDivider()
        SettingItem(
            title = stringResource(Res.string.ai_image_import_copy_prompt),
            subtitle = if (promptCopied) {
                stringResource(Res.string.ai_image_import_copied_hint)
            } else {
                stringResource(Res.string.ai_image_import_copy_prompt_desc)
            },
            leadingIcon = vectorResource(Res.drawable.content_copy_24px),
            trailingContent = {},
            onClick = onCopyPrompt
        )
    }

    AppSectionHeader(stringResource(Res.string.ai_image_import_section_steps))
    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(appSpacing().cardInner),
            verticalArrangement = Arrangement.spacedBy(appSpacing().listGap)
        ) {
            StepRow(number = "1", text = stringResource(Res.string.ai_image_import_step1))
            StepRow(number = "2", text = stringResource(Res.string.ai_image_import_step2))
            StepRow(number = "3", text = stringResource(Res.string.ai_image_import_step3))
            Text(
                text = stringResource(Res.string.ai_image_import_format_line),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    AppSectionHeader(stringResource(Res.string.ai_image_import_section_paste))
    SectionCard {
        SettingItem(
            title = stringResource(Res.string.ai_image_import_go_paste),
            subtitle = stringResource(Res.string.ai_image_import_paste_desc),
            leadingIcon = vectorResource(Res.drawable.image_24px),
            trailingContent = {},
            onClick = onGoPaste
        )
    }
}

/** 接口法：应用内填 OpenAI 兼容配置并代发识别。 */
@Composable
private fun ApiMethodContent(
    uiState: AiImportUiState,
    viewModel: AiImportViewModel,
    fileManager: com.shangkeschedule.tool.FileManager,
    onRecognized: () -> Unit
) {
    AppSectionHeader(stringResource(Res.string.ai_import_section_switch))
    SectionCard {
        SettingItem(
            title = stringResource(Res.string.title_ai_import),
            subtitle = stringResource(Res.string.desc_ai_import),
            leadingIcon = vectorResource(Res.drawable.image_24px),
            trailingContent = {
                AppSwitch(
                    checked = uiState.enabled,
                    onCheckedChange = viewModel::onToggleEnabled
                )
            }
        )
    }

    // 常驻明示：这几句无论开关与否都留在页面上，不靠弹窗一次性告知
    Text(
        text = stringResource(Res.string.ai_import_tip),
        style = MaterialTheme.typography.bodySmall,
        color = appColors().textSecondary
    )

    // 配置与识别区只在开关打开后出现 —— 关着的时候连输入框都不给，避免误以为已经在用
    if (!uiState.enabled) return

    AppSectionHeader(stringResource(Res.string.ai_import_section_config))
    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(appSpacing().cardInner),
            verticalArrangement = Arrangement.spacedBy(appSpacing().listGap)
        ) {
            AppTextField(
                value = uiState.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                label = stringResource(Res.string.ai_import_base_url),
                placeholder = stringResource(Res.string.ai_import_base_url_hint)
            )
            AppTextField(
                value = uiState.model,
                onValueChange = viewModel::onModelChange,
                label = stringResource(Res.string.ai_import_model),
                placeholder = stringResource(Res.string.ai_import_model_hint)
            )
            AppTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = stringResource(Res.string.ai_import_api_key),
                placeholder = stringResource(Res.string.ai_import_api_key_hint),
                visualTransformation = PasswordVisualTransformation()
            )
        }
        SectionDivider()
        SettingItem(
            title = stringResource(Res.string.ai_import_save_config),
            subtitle = stringResource(Res.string.ai_import_save_config_desc),
            leadingIcon = vectorResource(Res.drawable.save_24px),
            trailingContent = {},
            onClick = viewModel::onSaveConfig
        )
    }

    AppSectionHeader(stringResource(Res.string.ai_import_section_input))
    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(appSpacing().cardInner)
        ) {
            AppTextField(
                value = uiState.inputText,
                onValueChange = viewModel::onInputTextChange,
                label = stringResource(Res.string.ai_import_paste_label),
                placeholder = stringResource(Res.string.ai_import_paste_placeholder),
                singleLine = false,
                minLines = 3,
                maxLines = 6
            )
        }
        SectionDivider()
        SettingItem(
            title = stringResource(Res.string.ai_import_pick_image),
            subtitle = uiState.imageName?.let {
                stringResource(Res.string.ai_import_image_selected, it)
            } ?: stringResource(Res.string.ai_import_pick_image_desc),
            leadingIcon = vectorResource(Res.drawable.image_24px),
            trailingContent = {},
            onClick = { fileManager.importFile(listOf("png", "jpg", "jpeg", "webp")) }
        )
        if (uiState.imageBytes != null) {
            SectionDivider()
            SettingItem(
                title = stringResource(Res.string.ai_import_clear_image),
                leadingIcon = vectorResource(Res.drawable.delete_24px),
                trailingContent = {},
                onClick = viewModel::onClearImage
            )
        }
    }

    Button(
        onClick = { viewModel.onRecognize(onRecognized) },
        enabled = uiState.canRecognize,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(Res.string.ai_import_recognize))
    }

    if (!uiState.isConfigured) {
        Text(
            text = stringResource(Res.string.ai_import_need_config),
            style = MaterialTheme.typography.bodySmall,
            color = appColors().textSecondary
        )
    }

    if (uiState.busy) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    uiState.failure?.let { failure ->
        Text(
            text = failureText(failure),
            style = MaterialTheme.typography.bodyMedium,
            color = appColors().danger
        )
    }
}

/** 步骤行：序号圆点 + 说明文字。仅为视觉引导，不可点击。 */
@Composable
private fun StepRow(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(appSpacing().listGap)) {
        Surface(
            color = appColors().primarySoft,
            shape = CircleShape,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    color = appColors().primary
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = appColors().textPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 失败原因 → 本地化文案；HTTP 失败额外拼上服务端原话（「HTTP 401」本身没有可操作性）。
 */
@Composable
private fun failureText(failure: AiImportFailure): String {
    val base = when (failure.error) {
        AiImportError.NOT_CONFIGURED -> stringResource(Res.string.ai_import_error_not_configured)
        AiImportError.EMPTY_INPUT -> stringResource(Res.string.ai_import_error_empty_input)
        AiImportError.UNSUPPORTED_IMAGE -> stringResource(Res.string.ai_import_error_unsupported_image)
        AiImportError.IMAGE_TOO_LARGE -> stringResource(Res.string.ai_import_error_image_too_large)
        AiImportError.NETWORK -> stringResource(Res.string.ai_import_error_network)
        AiImportError.HTTP -> stringResource(Res.string.ai_import_error_http, failure.httpCode)
        AiImportError.PARSE -> stringResource(Res.string.ai_import_error_parse)
        AiImportError.EMPTY_RESULT -> stringResource(Res.string.ai_import_error_empty_result)
    }
    return if (failure.detail.isBlank()) {
        base
    } else {
        base + " · " + stringResource(Res.string.ai_import_error_detail_format, failure.detail)
    }
}
