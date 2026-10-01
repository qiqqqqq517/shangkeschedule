package com.shangkeschedule.ui.settings.additional

import com.shangkeschedule.ui.theme.appType
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.shangkeschedule.Destination
import com.shangkeschedule.tool.AdapterRemoteUpdater
import com.shangkeschedule.tool.AdapterSyncResult
import com.shangkeschedule.tool.AppExternalLinks
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.settings.SettingValueTrailing
import com.shangkeschedule.ui.settings.SettingsViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.qualifier.named
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.app_name
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.code_24px
import shangkeschedule.shared.generated.resources.contact_author_email
import shangkeschedule.shared.generated.resources.contact_author_hint
import shangkeschedule.shared.generated.resources.desc_contact_author
import shangkeschedule.shared.generated.resources.email_24px
import shangkeschedule.shared.generated.resources.item_contact_author
import shangkeschedule.shared.generated.resources.home_24px
import shangkeschedule.shared.generated.resources.item_github_repo
import shangkeschedule.shared.generated.resources.item_language_settings
import shangkeschedule.shared.generated.resources.item_official_website
import shangkeschedule.shared.generated.resources.item_open_source_licenses
import shangkeschedule.shared.generated.resources.item_start_screen_settings
import shangkeschedule.shared.generated.resources.label_version_prefix
import shangkeschedule.shared.generated.resources.language_24px
import shangkeschedule.shared.generated.resources.link_24px
import shangkeschedule.shared.generated.resources.list_alt_24px
import shangkeschedule.shared.generated.resources.adapter_remote_update_failed
import shangkeschedule.shared.generated.resources.desc_auto_sync_adapter
import shangkeschedule.shared.generated.resources.item_auto_sync_adapter
import shangkeschedule.shared.generated.resources.school_24px
import shangkeschedule.shared.generated.resources.sync_status_disabled
import shangkeschedule.shared.generated.resources.sync_status_failed
import shangkeschedule.shared.generated.resources.sync_status_syncing
import shangkeschedule.shared.generated.resources.sync_status_up_to_date
import shangkeschedule.shared.generated.resources.sync_status_updated
import shangkeschedule.shared.generated.resources.title_more_options
import shangkeschedule.shared.generated.resources.adapter_request_form_unavailable
import shangkeschedule.shared.generated.resources.desc_feedback
import shangkeschedule.shared.generated.resources.desc_legal_offline
import shangkeschedule.shared.generated.resources.desc_request_adapter
import shangkeschedule.shared.generated.resources.edit_24px
import shangkeschedule.shared.generated.resources.info_24px
import shangkeschedule.shared.generated.resources.item_feedback
import shangkeschedule.shared.generated.resources.item_privacy_policy
import shangkeschedule.shared.generated.resources.item_request_adapter
import shangkeschedule.shared.generated.resources.item_user_agreement
import shangkeschedule.shared.generated.resources.sticky_note_2_24px

// v4.65.0：三个地址常量收敛到 com.shangkeschedule.tool.AppExternalLinks，
// 因为「申请适配教务系统」需要「更多选项」与「学校选择」两处共用同一个表单地址。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current

    // 教务适配申请表单（WPS 表单，第三方页面）：不支持 URL 预填，这里只负责打开入口；
    // 表单地址在 AppExternalLinks 里置空即视为下线，此时只提示不跳转。
    val adapterFormUnavailableText = stringResource(Res.string.adapter_request_form_unavailable)
    fun openAdapterRequestForm() {
        if (AppExternalLinks.ADAPTER_REQUEST_FORM.isBlank()) {
            ToastManager.show(adapterFormUnavailableText)
        } else {
            uriHandler.openUri(AppExternalLinks.ADAPTER_REQUEST_FORM)
        }
    }

    // 从 Koin 动态获取注入的版本号
    val appVersionName: String = koinInject(named("AppVersionName"))

    // 状态观察
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDeveloperModeEnabled = uiState.appSettings.developerModeEnabled

    // 「自动同步教务系统」：手动触发远程适配同步，并展示同步结果
    val adapterRemoteUpdater: AdapterRemoteUpdater = koinInject()
    val syncScope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var syncStatusText by remember { mutableStateOf<String?>(null) }
    fun triggerAdapterSync() {
        if (syncing) return
        syncing = true
        syncStatusText = null
        syncScope.launch {
            val result = adapterRemoteUpdater.sync()
            val text = when (result) {
                is AdapterSyncResult.Updated ->
                    getString(Res.string.sync_status_updated, result.fileCount)
                AdapterSyncResult.UpToDate -> getString(Res.string.sync_status_up_to_date)
                AdapterSyncResult.Disabled -> getString(Res.string.sync_status_disabled)
                is AdapterSyncResult.VerificationFailed ->
                    getString(Res.string.adapter_remote_update_failed)
                is AdapterSyncResult.Failed -> getString(Res.string.sync_status_failed)
            }
            syncing = false
            syncStatusText = text
        }
    }

    // 弹窗可见性控制
    var showStartScreenDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(text = stringResource(Res.string.title_more_options)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 应用信息头部
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = appSpacing().contentBottom),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DynamicAppIconHeader(
                    isDeveloperModeEnabled = isDeveloperModeEnabled,
                    onTriggerDeveloperMode = { viewModel.onDeveloperModeChanged(true) }
                )
                Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = appType().hero
                )
                Text(
                    text = stringResource(Res.string.label_version_prefix, appVersionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors().textSecondary
                )
            }

            Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))

            // 开发者模式设置项（隐藏项，保留原有动画逻辑）
            DeveloperModeSettingItem(
                isDeveloperModeEnabled = isDeveloperModeEnabled,
                onDeveloperModeChanged = { viewModel.onDeveloperModeChanged(it) },
                modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal)
            )

            // 语言/启动页/官网/GitHub/开源许可证（分区大卡，组内分割）
            SectionCard(
                modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal)
            ) {
                SettingItem(
                    title = stringResource(Res.string.item_language_settings),
                    leadingIcon = vectorResource(Res.drawable.language_24px),
                    onClick = { onNavigate(Destination.LanguageSettings) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_start_screen_settings),
                    leadingIcon = vectorResource(Res.drawable.home_24px),
                    onClick = { showStartScreenDialog = true },
                    trailingContent = {
                        SettingValueTrailing(
                            stringResource(uiState.appSettings.startScreen.labelRes)
                        )
                    }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_official_website),
                    subtitle = AppExternalLinks.OFFICIAL_WEBSITE_DISPLAY,
                    leadingIcon = vectorResource(Res.drawable.link_24px),
                    onClick = { uriHandler.openUri(AppExternalLinks.OFFICIAL_WEBSITE) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_github_repo),
                    leadingIcon = vectorResource(Res.drawable.code_24px),
                    onClick = { uriHandler.openUri(AppExternalLinks.GITHUB_REPO) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_open_source_licenses),
                    leadingIcon = vectorResource(Res.drawable.list_alt_24px),
                    onClick = { onNavigate(Destination.OpenSourceLicenses) }
                )
            }

            Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))

            // 自动同步教务系统：手动触发远程适配同步，并轮询展示同步结果
            SectionCard(
                modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal)
            ) {
                SettingItem(
                    title = stringResource(Res.string.item_auto_sync_adapter),
                    subtitle = when {
                        syncing -> stringResource(Res.string.sync_status_syncing)
                        syncStatusText != null -> syncStatusText!!
                        else -> stringResource(Res.string.desc_auto_sync_adapter)
                    },
                    leadingIcon = vectorResource(Res.drawable.school_24px),
                    onClick = ::triggerAdapterSync
                )
            }

            Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))

            // 意见反馈 / 教务适配申请 / 应用内协议（v4.65.0）
            SectionCard(
                modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal)
            ) {
                SettingItem(
                    title = stringResource(Res.string.item_request_adapter),
                    subtitle = stringResource(Res.string.desc_request_adapter),
                    leadingIcon = vectorResource(Res.drawable.school_24px),
                    onClick = ::openAdapterRequestForm
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_feedback),
                    subtitle = stringResource(Res.string.desc_feedback),
                    leadingIcon = vectorResource(Res.drawable.edit_24px),
                    onClick = { onNavigate(Destination.Feedback) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_privacy_policy),
                    subtitle = stringResource(Res.string.desc_legal_offline),
                    leadingIcon = vectorResource(Res.drawable.info_24px),
                    onClick = { onNavigate(Destination.LegalDocument(LegalDocumentType.PRIVACY.name)) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_user_agreement),
                    subtitle = stringResource(Res.string.desc_legal_offline),
                    leadingIcon = vectorResource(Res.drawable.sticky_note_2_24px),
                    onClick = { onNavigate(Destination.LegalDocument(LegalDocumentType.TERMS.name)) }
                )
            }

            Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))

            // 联系作者（邮件直连；功能建议与问题反馈走上面的「意见反馈」页，那里能带上类型与联系方式）
            SectionCard(
                modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal)
            ) {
                SettingItem(
                    title = stringResource(Res.string.item_contact_author),
                    subtitle = stringResource(Res.string.desc_contact_author),
                    leadingIcon = vectorResource(Res.drawable.email_24px),
                    onClick = { uriHandler.openUri("mailto:${AppExternalLinks.SUPPORT_EMAIL}") }
                )
                SectionDivider()
                Text(
                    text = stringResource(Res.string.contact_author_email),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    text = stringResource(Res.string.contact_author_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // 鸣谢内容
            AcknowledgmentContent()
            Spacer(modifier = Modifier.height(appSpacing().sectionGap))
        }
    }

    // --- 弹窗逻辑 ---

    // 启动页切换弹窗
    StartScreenSelectionDialog(
        showDialog = showStartScreenDialog,
        currentSelected = uiState.appSettings.startScreen,
        onDismiss = { showStartScreenDialog = false },
        onConfirm = {
            viewModel.onStartScreenChanged(it)
            showStartScreenDialog = false
        }
    )

}
