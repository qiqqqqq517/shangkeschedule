package com.shangkeschedule.ui.settings.additional

import androidx.compose.foundation.clickable
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
import com.shangkeschedule.Destination
import com.shangkeschedule.data.api.UpdateCheckClient
import com.shangkeschedule.data.api.UpdateCheckResult
import com.shangkeschedule.data.api.UpdateFailureKind
import com.shangkeschedule.data.api.UpdateManifest
import com.shangkeschedule.tool.AppExternalLinks
import com.shangkeschedule.tool.copyToClipboard
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.tool.AdapterRemoteUpdater
import com.shangkeschedule.tool.AdapterSyncResult
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.settings.SettingValueTrailing
import com.shangkeschedule.ui.settings.SettingsViewModel
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.qualifier.named
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.account_circle_24px
import shangkeschedule.shared.generated.resources.adapter_request_form_unavailable
import shangkeschedule.shared.generated.resources.app_name
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.code_24px
import shangkeschedule.shared.generated.resources.community_qq_copied
import shangkeschedule.shared.generated.resources.community_qq_copy_failed
import shangkeschedule.shared.generated.resources.contact_author_email
import shangkeschedule.shared.generated.resources.contact_author_hint
import shangkeschedule.shared.generated.resources.desc_check_update
import shangkeschedule.shared.generated.resources.desc_community_qq
import shangkeschedule.shared.generated.resources.desc_community_xiaohongshu
import shangkeschedule.shared.generated.resources.desc_contact_author
import shangkeschedule.shared.generated.resources.desc_feedback
import shangkeschedule.shared.generated.resources.desc_legal_offline
import shangkeschedule.shared.generated.resources.desc_user_agreement
import shangkeschedule.shared.generated.resources.desc_request_adapter
import shangkeschedule.shared.generated.resources.desc_star_project
import shangkeschedule.shared.generated.resources.edit_24px
import shangkeschedule.shared.generated.resources.email_24px
import shangkeschedule.shared.generated.resources.favorite_24px
import shangkeschedule.shared.generated.resources.home_24px
import shangkeschedule.shared.generated.resources.info_24px
import shangkeschedule.shared.generated.resources.item_check_update
import shangkeschedule.shared.generated.resources.item_community_qq
import shangkeschedule.shared.generated.resources.item_community_xiaohongshu
import shangkeschedule.shared.generated.resources.item_contact_author
import shangkeschedule.shared.generated.resources.item_feedback
import shangkeschedule.shared.generated.resources.item_github_repo
import shangkeschedule.shared.generated.resources.item_language_settings
import shangkeschedule.shared.generated.resources.item_official_website
import shangkeschedule.shared.generated.resources.item_open_source_licenses
import shangkeschedule.shared.generated.resources.item_privacy_policy
import shangkeschedule.shared.generated.resources.item_request_adapter
import shangkeschedule.shared.generated.resources.item_star_project
import shangkeschedule.shared.generated.resources.item_start_screen_settings
import shangkeschedule.shared.generated.resources.item_auto_sync_adapter
import shangkeschedule.shared.generated.resources.item_user_agreement
import shangkeschedule.shared.generated.resources.settings_sub_adapter_status
import shangkeschedule.shared.generated.resources.settings_sub_auto_sync_adapter
import shangkeschedule.shared.generated.resources.sync_alt_24px
import shangkeschedule.shared.generated.resources.sync_status_disabled
import shangkeschedule.shared.generated.resources.sync_status_failed
import shangkeschedule.shared.generated.resources.sync_status_syncing
import shangkeschedule.shared.generated.resources.sync_status_up_to_date
import shangkeschedule.shared.generated.resources.sync_status_updated
import shangkeschedule.shared.generated.resources.title_adapter_status
import shangkeschedule.shared.generated.resources.label_version_prefix
import shangkeschedule.shared.generated.resources.language_24px
import shangkeschedule.shared.generated.resources.link_24px
import shangkeschedule.shared.generated.resources.list_alt_24px
import shangkeschedule.shared.generated.resources.refresh_24px
import shangkeschedule.shared.generated.resources.school_24px
import shangkeschedule.shared.generated.resources.adapter_remote_update_failed
import shangkeschedule.shared.generated.resources.build_24px
import shangkeschedule.shared.generated.resources.section_more_about
import shangkeschedule.shared.generated.resources.section_more_adapter
import shangkeschedule.shared.generated.resources.section_more_contact
import shangkeschedule.shared.generated.resources.section_more_feedback
import shangkeschedule.shared.generated.resources.share_copy_failed
import shangkeschedule.shared.generated.resources.star_24px
import shangkeschedule.shared.generated.resources.sticky_note_2_24px
import shangkeschedule.shared.generated.resources.title_more_options
import shangkeschedule.shared.generated.resources.update_check_available
import shangkeschedule.shared.generated.resources.update_check_checking
import shangkeschedule.shared.generated.resources.update_check_failed_http
import shangkeschedule.shared.generated.resources.update_check_failed_network
import shangkeschedule.shared.generated.resources.update_check_failed_parse
import shangkeschedule.shared.generated.resources.update_check_failed_payload
import shangkeschedule.shared.generated.resources.update_check_up_to_date
import shangkeschedule.shared.generated.resources.update_dialog_copy_token
import shangkeschedule.shared.generated.resources.update_dialog_current
import shangkeschedule.shared.generated.resources.update_dialog_notes
import shangkeschedule.shared.generated.resources.update_dialog_open_download
import shangkeschedule.shared.generated.resources.update_dialog_title
import shangkeschedule.shared.generated.resources.update_token_copied

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

    // 教务适配「自动同步」（v4.66.2 从「我的」页搬来）：状态机整段随行搬迁。
    // 同步结果写回该行副标题并弹一次 toast —— 与搬走前在「我的」页的行为逐字一致，
    // 用户感知不到入口换了位置，只是不再占据「我的」页首屏。
    val adapterRemoteUpdater: AdapterRemoteUpdater = koinInject()
    val adapterSyncScope = rememberCoroutineScope()
    var adapterSyncing by remember { mutableStateOf(false) }
    var adapterSyncStatusText by remember { mutableStateOf<String?>(null) }
    fun triggerAdapterSync() {
        if (adapterSyncing) return
        adapterSyncing = true
        adapterSyncStatusText = null
        adapterSyncScope.launch {
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
            adapterSyncing = false
            adapterSyncStatusText = text
            ToastManager.show(text)
        }
    }

    // 「检查更新」（v4.66.0，K4）：版本号来自官网静态 version.json，网盘只做下载落点。
    // 只在用户点击时请求一次——不后台轮询、不开机自检；失败按原因明确提示，不静默。
    // 历史提示：官网更新日志 v4.64.x 记录过「清理从未接入界面的『检查更新』脚手架」，
    // 本次是有真实版本源与下载落点后的正式接入，请勿再当死代码删除。
    val updateCheckClient: UpdateCheckClient = koinInject()
    val updateScope = rememberCoroutineScope()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf<String?>(null) }
    var pendingUpdate by remember { mutableStateOf<UpdateManifest?>(null) }
    val updateTokenCopiedText = stringResource(Res.string.update_token_copied)
    val updateCopyFailedText = stringResource(Res.string.share_copy_failed)
    fun triggerUpdateCheck() {
        if (checkingUpdate) return
        checkingUpdate = true
        updateStatusText = null
        updateScope.launch {
            val text = when (val result = updateCheckClient.check()) {
                is UpdateCheckResult.UpToDate ->
                    getString(Res.string.update_check_up_to_date, result.versionName)
                is UpdateCheckResult.UpdateAvailable -> {
                    pendingUpdate = result.manifest
                    getString(Res.string.update_check_available, result.manifest.versionName)
                }
                is UpdateCheckResult.Failed -> when (result.kind) {
                    UpdateFailureKind.NETWORK -> getString(Res.string.update_check_failed_network)
                    UpdateFailureKind.HTTP ->
                        getString(Res.string.update_check_failed_http, result.httpCode)
                    UpdateFailureKind.PARSE -> getString(Res.string.update_check_failed_parse)
                    UpdateFailureKind.INCOMPLETE -> getString(Res.string.update_check_failed_payload)
                }
            }
            checkingUpdate = false
            updateStatusText = text
            ToastManager.show(text)
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
            // v4.66.0（K7）：同组追加「小组件排障」入口，排障页只在开发者模式打开后出现。
            DeveloperModeSettingItem(
                isDeveloperModeEnabled = isDeveloperModeEnabled,
                onDeveloperModeChanged = { viewModel.onDeveloperModeChanged(it) },
                modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal),
                onWidgetTroubleshootClick = { onNavigate(Destination.WidgetTroubleshoot) }
            )

            // v4.66.1（IA 再排）：本页三张卡原先**都没有分区标题**（与「课表导入/导出」页
            // 有AppSectionHeader 的节奏不一致），用户看不出三块各是什么。
            // 补标题后：三段语义一眼可辨，且与本页其余页面的分区节奏对齐。
            AppSectionHeader(
                stringResource(Res.string.section_more_about),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = appSpacing().pageHorizontal)
            )
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
                // 检查更新（K4，v4.66.0）：与「上课官网 / GitHub 仓库 / 开源许可证」同属「关于本应用」的信息组，
                // 放在该组末尾（微信/QQ 等同类应用都把「检查更新」放在关于列表底部）。
                // 星链课表的检查更新也在「个人中心 → 关于我们」里，而不是首页或顶部独立卡片。
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_check_update),
                    subtitle = when {
                        checkingUpdate -> stringResource(Res.string.update_check_checking)
                        updateStatusText != null -> updateStatusText!!
                        else -> stringResource(Res.string.desc_check_update)
                    },
                    leadingIcon = vectorResource(Res.drawable.refresh_24px),
                    onClick = ::triggerUpdateCheck
                )
            }

            Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))

            // v4.66.0（信息架构搬迁 · 用户 m05093 / 裁决 A）：原先这里的
            // 「教务适配状态 / 自动同步教务系统 / 空教室查询」「考证查分」「成绩与绩点 + 学业情况」
            // 三张卡已上移到「我的」页（SettingsScreen.kt 的「课表」分组与新增「学习」分组），
            // 本页只保留关于本应用 / 反馈与协议 / 联系作者 —— 避免新功能堆在「更多」里。

            // 教务适配维护（v4.66.2 从「我的」页搬回）：抓取脚本的**维护 / 诊断**入口——
            // 「适配状态」看各校脚本能不能用、「自动同步」手动拉最新脚本。
            // 对普通用户没有实际作用（教务导入失败时适配器会自动兜底），
            // 故放「更多」而非「我的」页首屏；与下方「申请适配教务系统」相邻，
            // 让"适配"相关的三件事在一屏内连续可读。
            AppSectionHeader(
                stringResource(Res.string.section_more_adapter),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = appSpacing().pageHorizontal)
            )
            SectionCard(
                modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal)
            ) {
                SettingItem(
                    title = stringResource(Res.string.title_adapter_status),
                    subtitle = stringResource(Res.string.settings_sub_adapter_status),
                    leadingIcon = vectorResource(Res.drawable.build_24px),
                    onClick = { onNavigate(Destination.AdapterStatus) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_auto_sync_adapter),
                    // 同步中 / 同步结果写回副标题位：与本文件「检查更新」同一套约定
                    // （那边是 checkingUpdate / updateStatusText 三态），不另加一行，
                    // 免得行高随状态跳变。
                    subtitle = if (adapterSyncing) {
                        stringResource(Res.string.sync_status_syncing)
                    } else {
                        adapterSyncStatusText ?: stringResource(Res.string.settings_sub_auto_sync_adapter)
                    },
                    leadingIcon = vectorResource(Res.drawable.sync_alt_24px),
                    onClick = ::triggerAdapterSync
                )
            }

            Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))

            // 意见反馈 / 教务适配申请 / 应用内协议（v4.65.0）
            AppSectionHeader(
                stringResource(Res.string.section_more_feedback),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = appSpacing().pageHorizontal)
            )
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
                    // v4.66.1：原先与「隐私政策」共用 desc_legal_offline（"全文离线，无需联网"），
                    // 两行副标题一模一样，读者无法从列表区分这两份不同的文档。给条款单独的说明。
                    subtitle = stringResource(Res.string.desc_user_agreement),
                    leadingIcon = vectorResource(Res.drawable.sticky_note_2_24px),
                    onClick = { onNavigate(Destination.LegalDocument(LegalDocumentType.TERMS.name)) }
                )
            }

            Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))

            // 联系作者（邮件直连；功能建议与问题反馈走上面的「意见反馈」页，那里能带上类型与联系方式）
            AppSectionHeader(
                stringResource(Res.string.section_more_contact),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = appSpacing().pageHorizontal)
            )
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
                SectionDivider()
                // K6（v4.66.0）：社群入口。群号 / 主页链接在 AppExternalLinks 里留空即整行不渲染
                // （与「申请适配教务系统」表单同一约定），这样没开通社群时页面上不留空入口。
                if (AppExternalLinks.COMMUNITY_QQ_GROUP.isNotBlank()) {
                    // Compose 资源的 getString 是 suspend，不能在 onClick 里直接调；
                    // 这里在组合期先把两条提示取出来，点击时只做同步的复制 + 显示。
                    val copiedText = stringResource(Res.string.community_qq_copied)
                    val copyFailedText = stringResource(Res.string.community_qq_copy_failed)
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.item_community_qq),
                        subtitle = stringResource(Res.string.desc_community_qq),
                        leadingIcon = vectorResource(Res.drawable.account_circle_24px),
                        onClick = {
                            val copied = copyToClipboard(AppExternalLinks.COMMUNITY_QQ_GROUP)
                            ToastManager.show(if (copied) copiedText else copyFailedText)
                        }
                    )
                }
                if (AppExternalLinks.COMMUNITY_XIAOHONGSHU_URL.isNotBlank()) {
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.item_community_xiaohongshu),
                        subtitle = stringResource(Res.string.desc_community_xiaohongshu),
                        leadingIcon = vectorResource(Res.drawable.favorite_24px),
                        onClick = { uriHandler.openUri(AppExternalLinks.COMMUNITY_XIAOHONGSHU_URL) }
                    )
                }
                SectionDivider()
                // K5：GitHub Star 常驻入口（与一次性弹窗同一目标；参考星链把社区类入口放在列表最后一张卡）
                SettingItem(
                    title = stringResource(Res.string.item_star_project),
                    subtitle = stringResource(Res.string.desc_star_project),
                    leadingIcon = vectorResource(Res.drawable.star_24px),
                    onClick = { uriHandler.openUri(AppExternalLinks.GITHUB_REPO) }
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
    // 发现新版本弹窗（v4.66.0，K4）：内容全部来自官网 version.json；
    // 下载落点是夸克网盘分享页，其口令需要在夸克 App 里打开，所以另给「复制分享口令」。
    pendingUpdate?.let { manifest ->
        AppAlertDialog(
            onDismissRequest = { pendingUpdate = null },
            title = {
                Text(text = stringResource(Res.string.update_dialog_title, manifest.versionName))
            },
            text = {
                Column {
                    Text(
                        text = stringResource(
                            Res.string.update_dialog_current,
                            updateCheckClient.currentVersionName,
                            updateCheckClient.currentVersionCode
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColors().textSecondary
                    )
                    if (manifest.releaseNotesUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(appSpacing().listGap))
                        Text(
                            text = stringResource(Res.string.update_dialog_notes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                pendingUpdate = null
                                uriHandler.openUri(manifest.releaseNotesUrl)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                AppDialogActions(
                    confirmText = stringResource(Res.string.update_dialog_open_download),
                    onConfirm = {
                        pendingUpdate = null
                        uriHandler.openUri(manifest.downloadUrl)
                    },
                    // 口令为空（例如换用其它下载方式）时不显示次按钮，避免给一个点了没用的入口
                    dismissText = if (manifest.shareToken.isBlank()) {
                        null
                    } else {
                        stringResource(Res.string.update_dialog_copy_token)
                    },
                    onDismiss = if (manifest.shareToken.isBlank()) {
                        null
                    } else {
                        {
                            val copied = copyToClipboard(manifest.shareToken)
                            pendingUpdate = null
                            ToastManager.show(
                                if (copied) updateTokenCopiedText else updateCopyFailedText
                            )
                        }
                    }
                )
            }
        )
    }

}
