package com.shangkeschedule.ui.cert

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.Destination
import com.shangkeschedule.WebPagePurpose
import com.shangkeschedule.data.model.CertCredential
import com.shangkeschedule.tool.copyToClipboard
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
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
import shangkeschedule.shared.generated.resources.action_close
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.cert_credential_copied
import shangkeschedule.shared.generated.resources.cert_credential_copy
import shangkeschedule.shared.generated.resources.cert_credential_empty
import shangkeschedule.shared.generated.resources.cert_credential_name
import shangkeschedule.shared.generated.resources.cert_credential_ticket
import shangkeschedule.shared.generated.resources.cert_credential_title
import shangkeschedule.shared.generated.resources.cert_open_external
import shangkeschedule.shared.generated.resources.cert_open_external_region
import shangkeschedule.shared.generated.resources.cert_open_web
import shangkeschedule.shared.generated.resources.cert_open_web_region
import shangkeschedule.shared.generated.resources.cert_page_intro
import shangkeschedule.shared.generated.resources.cert_region_picker_hint
import shangkeschedule.shared.generated.resources.cert_region_picker_title
import shangkeschedule.shared.generated.resources.cert_saved
import shangkeschedule.shared.generated.resources.content_copy_24px
import shangkeschedule.shared.generated.resources.link_24px
import shangkeschedule.shared.generated.resources.school_24px
import shangkeschedule.shared.generated.resources.title_cert_exam

/**
 * 考证查分页（v4.66.0）。
 *
 * 每个模块一张卡，四行：模块说明（点开填凭据）/ 复制准考证号 / 应用内打开查分页 / 用浏览器打开。
 * 「应用内打开」复用已有的 [WebPagePurpose.CERT] 纯浏览器 WebView：共享同一套容器与
 * 网页态，且不显示课表导入、成绩识别按钮（那两个按钮对本页无意义）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertExamScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: CertExamViewModel = koinViewModel()
) {
    val credentials by viewModel.credentials.collectAsStateWithLifecycle()
    var editingModule by remember { mutableStateOf<CertExamModule?>(null) }
    // 需要按省份分流的模块（专升本）：先选省份，再决定应用内还是浏览器打开
    var regionModule by remember { mutableStateOf<CertExamModule?>(null) }
    var regionOpenInApp by remember { mutableStateOf(true) }
    val uriHandler = LocalUriHandler.current

    // 文案在 Compose 上下文里先取好：onClick 里不能调 stringResource
    val copiedText = stringResource(Res.string.cert_credential_copied)
    val savedText = stringResource(Res.string.cert_saved)
    val copyLabel = stringResource(Res.string.cert_credential_copy)
    val emptyCredentialText = stringResource(Res.string.cert_credential_empty)
    val openWebLabel = stringResource(Res.string.cert_open_web)
    val openExternalLabel = stringResource(Res.string.cert_open_external)
    val openWebRegionLabel = stringResource(Res.string.cert_open_web_region)
    val openExternalRegionLabel = stringResource(Res.string.cert_open_external_region)

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(text = stringResource(Res.string.title_cert_exam)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = appSpacing().pageHorizontal,
                vertical = appSpacing().cardGap
            )
        ) {
            item(key = "cert-intro") {
                Text(
                    text = stringResource(Res.string.cert_page_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            items(CertExamRegistry.modules, key = { it.id }) { module ->
                val credential = credentials[module.id] ?: CertCredential()

                SectionCard(
                    modifier = Modifier.padding(top = appSpacing().listGap)
                ) {
                    SettingItem(
                        title = stringResource(module.titleRes),
                        subtitle = stringResource(module.descRes),
                        leadingIcon = vectorResource(Res.drawable.school_24px),
                        onClick = { editingModule = module }
                    )
                    SectionDivider()
                    SettingItem(
                        title = copyLabel,
                        subtitle = credential.ticket.ifBlank { emptyCredentialText },
                        leadingIcon = vectorResource(Res.drawable.content_copy_24px),
                        onClick = {
                            if (credential.ticket.isBlank()) {
                                // 还没存过凭据：直接把人引到填写对话框，而不是弹一个「没得复制」的提示
                                editingModule = module
                            } else if (copyToClipboard(credential.ticket)) {
                                ToastManager.show(copiedText)
                            }
                        }
                    )
                    SectionDivider()
                    // 全国统一入口 vs 省级分流（专升本）：后者先弹省份选择，再按用户选择落地
                    if (module.regions.isEmpty()) {
                        SettingItem(
                            title = openWebLabel,
                            leadingIcon = vectorResource(Res.drawable.link_24px),
                            onClick = {
                                onNavigate(
                                    Destination.WebView(
                                        initialUrl = module.entryUrl,
                                        mode = WebPagePurpose.CERT
                                    )
                                )
                            }
                        )
                        SectionDivider()
                        SettingItem(
                            title = openExternalLabel,
                            leadingIcon = vectorResource(Res.drawable.link_24px),
                            onClick = { runCatching { uriHandler.openUri(module.entryUrl) } }
                        )
                    } else {
                        SettingItem(
                            title = openWebRegionLabel,
                            leadingIcon = vectorResource(Res.drawable.link_24px),
                            onClick = {
                                regionOpenInApp = true
                                regionModule = module
                            }
                        )
                        SectionDivider()
                        SettingItem(
                            title = openExternalRegionLabel,
                            leadingIcon = vectorResource(Res.drawable.link_24px),
                            onClick = {
                                regionOpenInApp = false
                                regionModule = module
                            }
                        )
                    }
                }
            }
        }
    }

    editingModule?.let { module ->
        CertCredentialDialog(
            module = module,
            initial = credentials[module.id] ?: CertCredential(),
            onDismiss = { editingModule = null },
            onSave = { name, ticket ->
                viewModel.saveCredential(module.id, name, ticket)
                ToastManager.show(savedText)
                editingModule = null
            }
        )
    }

    regionModule?.let { module ->
        CertRegionPickerDialog(
            module = module,
            onDismiss = { regionModule = null },
            onPick = { region ->
                regionModule = null
                if (regionOpenInApp) {
                    onNavigate(
                        Destination.WebView(
                            initialUrl = region.url,
                            mode = WebPagePurpose.CERT
                        )
                    )
                } else {
                    runCatching { uriHandler.openUri(region.url) }
                }
            }
        )
    }
}

/**
 * 省份选择对话框（v4.66.0，B5 专升本）。
 *
 * 专升本由各省自主组织，全国没有统一查分站，所以列出各省级教育考试院的**官网根地址**让用户选。
 * 列表固定 31 项，用滚动列而不是 LazyColumn：对话框内高度有限，且不需要复用。
 */
@Composable
private fun CertRegionPickerDialog(
    module: CertExamModule,
    onDismiss: () -> Unit,
    onPick: (CertExamRegion) -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.cert_region_picker_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.cert_region_picker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    module.regions.forEachIndexed { index, region ->
                        if (index > 0) SectionDivider()
                        Text(
                            text = stringResource(region.nameRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(region) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppDialogActions(
                confirmText = stringResource(Res.string.action_close),
                onConfirm = onDismiss
            )
        }
    )
}

/**
 * 凭据填写对话框。
 *
 * 不校验格式：各考试的准考证号长度 / 构成不同（有的还要身份证号），
 * 应用只需要「原样记住并复制」，校验反而会挡住正常输入。
 */
@Composable
private fun CertCredentialDialog(
    module: CertExamModule,
    initial: CertCredential,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var ticket by remember { mutableStateOf(initial.ticket) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.cert_credential_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(module.titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors().textPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.cert_credential_name)
                )
                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = ticket,
                    onValueChange = { ticket = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.cert_credential_ticket)
                )
            }
        },
        confirmButton = {
            AppDialogActions(
                confirmText = stringResource(Res.string.action_confirm),
                onConfirm = { onSave(name, ticket) },
                dismissText = stringResource(Res.string.action_close),
                onDismiss = onDismiss
            )
        }
    )
}
