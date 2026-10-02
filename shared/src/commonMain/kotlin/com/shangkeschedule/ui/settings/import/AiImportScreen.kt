package com.shangkeschedule.ui.settings.import

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.Destination
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppSectionHeader
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
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.desc_ai_import
import shangkeschedule.shared.generated.resources.image_24px
import shangkeschedule.shared.generated.resources.save_24px
import shangkeschedule.shared.generated.resources.title_ai_import

/**
 * 「AI 识别导入」页（v4.66.0，J1）。
 *
 * 刻意只做一件事：把「课表截图或文字」交给**用户自己填的**接口，拿回一段纯文本，
 * 再交给既有的「文本粘贴导入」链路预览与导入 —— 识别结果不直接写进课表。
 *
 * 三条硬约束（用户裁决）：
 * 1. **默认关闭**：开关关着时本页不发任何网络请求；
 * 2. **明示外发**：首次开启前必须过一次确认框（上传什么 / 发给谁 / 不含什么）；
 * 3. **不绑定厂商**：地址、模型名、Key 都由用户填（OpenAI 兼容 `/chat/completions`），
 *    三项只存本机、不进备份。
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
            if (uiState.enabled) {
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
                    onClick = { viewModel.onRecognize { onNavigate(Destination.ShareTextImport) } },
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
