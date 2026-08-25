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
import shangkeschedule.shared.generated.resources.home_24px
import shangkeschedule.shared.generated.resources.item_github_repo
import shangkeschedule.shared.generated.resources.item_language_settings
import shangkeschedule.shared.generated.resources.item_open_source_licenses
import shangkeschedule.shared.generated.resources.item_start_screen_settings
import shangkeschedule.shared.generated.resources.item_update_repo
import shangkeschedule.shared.generated.resources.label_version_prefix
import shangkeschedule.shared.generated.resources.language_24px
import shangkeschedule.shared.generated.resources.list_alt_24px
import shangkeschedule.shared.generated.resources.title_more_options
import shangkeschedule.shared.generated.resources.update_24px

private const val GITHUB_REPO_URL = "https://github.com/XingHeYuZhuan/shangkeschedule"

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

            // 设置列表卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {

                    // 开发者模式设置项
                    DeveloperModeSettingItem(
                        isDeveloperModeEnabled = isDeveloperModeEnabled,
                        onDeveloperModeChanged = { viewModel.onDeveloperModeChanged(it) }
                    )

                    // 语言切换 (导航至独立页面)
                    SettingListItem(
                        icon = vectorResource(Res.drawable.language_24px),
                        title = stringResource(Res.string.item_language_settings),
                        onClick = { onNavigate(Destination.LanguageSettings) }
                    )

                    // 启动页面设置
                    SettingListItem(
                        icon = vectorResource(Res.drawable.home_24px),
                        title = stringResource(Res.string.item_start_screen_settings),
                        onClick = { showStartScreenDialog = true },
                        trailingContent = {
                            Text(
                                text = stringResource(uiState.appSettings.startScreen.labelRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )

                    // GitHub 仓库
                    SettingListItem(
                        icon = vectorResource(Res.drawable.code_24px),
                        title = stringResource(Res.string.item_github_repo),
                        onClick = { uriHandler.openUri(GITHUB_REPO_URL) }
                    )

                    // 开源许可证
                    SettingListItem(
                        icon = vectorResource(Res.drawable.list_alt_24px),
                        title = stringResource(Res.string.item_open_source_licenses),
                        onClick = { onNavigate(Destination.OpenSourceLicenses) }
                    )

                    // 更新适配仓库
                    SettingListItem(
                        icon = vectorResource(Res.drawable.update_24px),
                        title = stringResource(Res.string.item_update_repo),
                        onClick = { onNavigate(Destination.UpdateRepo) },
                        showDivider = false
                    )

                    // 鸣谢内容
                    AcknowledgmentContent()
                }
            }
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