package com.shangkeschedule.ui.settings.additional

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.Destination
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.settings.SettingsViewModel
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
import shangkeschedule.shared.generated.resources.item_open_source_licenses
import shangkeschedule.shared.generated.resources.item_start_screen_settings
import shangkeschedule.shared.generated.resources.label_version_prefix
import shangkeschedule.shared.generated.resources.language_24px
import shangkeschedule.shared.generated.resources.list_alt_24px
import shangkeschedule.shared.generated.resources.title_more_options

private const val GITHUB_REPO_URL = "https://github.com/qiqqqqq517/shangkeschedule"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current

    // 从 Koin 动态获取注入的版本号
    val appVersionName: String = koinInject(named("AppVersionName"))

    // 状态观察
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDeveloperModeEnabled = uiState.appSettings.developerModeEnabled

    // 弹窗可见性控制
    var showStartScreenDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
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
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DynamicAppIconHeader(
                    isDeveloperModeEnabled = isDeveloperModeEnabled,
                    onTriggerDeveloperMode = { viewModel.onDeveloperModeChanged(true) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Text(
                    text = stringResource(Res.string.label_version_prefix, appVersionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 开发者模式设置项（隐藏项，保留原有动画逻辑）
            DeveloperModeSettingItem(
                isDeveloperModeEnabled = isDeveloperModeEnabled,
                onDeveloperModeChanged = { viewModel.onDeveloperModeChanged(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // 语言/启动页/GitHub/开源许可证（分区大卡，组内分割）
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp)
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
                        Text(
                            text = stringResource(uiState.appSettings.startScreen.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_github_repo),
                    leadingIcon = vectorResource(Res.drawable.code_24px),
                    onClick = { uriHandler.openUri(GITHUB_REPO_URL) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_open_source_licenses),
                    leadingIcon = vectorResource(Res.drawable.list_alt_24px),
                    onClick = { onNavigate(Destination.OpenSourceLicenses) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 联系作者反馈（欢迎新功能建议 / 教务适配请求）
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                SettingItem(
                    title = stringResource(Res.string.item_contact_author),
                    subtitle = stringResource(Res.string.desc_contact_author),
                    leadingIcon = vectorResource(Res.drawable.email_24px),
                    onClick = { uriHandler.openUri("mailto:hhixingchen520@163.com") }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // 鸣谢内容
            AcknowledgmentContent()
            Spacer(modifier = Modifier.height(32.dp))
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