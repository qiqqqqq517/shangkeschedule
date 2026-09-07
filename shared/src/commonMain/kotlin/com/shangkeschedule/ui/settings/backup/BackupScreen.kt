package com.shangkeschedule.ui.settings.backup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.AppCard
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.theme.AccentTone
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.AppSpacing
import com.shangkeschedule.ui.settings.SettingItem
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import okio.Buffer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.action_reset
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.backup_target_local_zip
import shangkeschedule.shared.generated.resources.backup_target_webdav
import shangkeschedule.shared.generated.resources.cloud_24px
import shangkeschedule.shared.generated.resources.desc_backup_data
import shangkeschedule.shared.generated.resources.desc_restore_data
import shangkeschedule.shared.generated.resources.desc_webdav_connected
import shangkeschedule.shared.generated.resources.desc_webdav_path_hint
import shangkeschedule.shared.generated.resources.desc_webdav_unconfigured
import shangkeschedule.shared.generated.resources.dialog_title_backup_target
import shangkeschedule.shared.generated.resources.dialog_title_config_webdav
import shangkeschedule.shared.generated.resources.dialog_title_confirm_restore
import shangkeschedule.shared.generated.resources.dialog_title_confirm_webdav_disconnect
import shangkeschedule.shared.generated.resources.dialog_text_confirm_restore
import shangkeschedule.shared.generated.resources.dialog_text_confirm_webdav_disconnect
import shangkeschedule.shared.generated.resources.dialog_title_restore_source
import shangkeschedule.shared.generated.resources.action_restore
import shangkeschedule.shared.generated.resources.download_24px
import shangkeschedule.shared.generated.resources.error_stream_open_failed
import shangkeschedule.shared.generated.resources.error_webdav_unconfigured
import shangkeschedule.shared.generated.resources.item_backup_data
import shangkeschedule.shared.generated.resources.item_backup_restore
import shangkeschedule.shared.generated.resources.item_restore_data
import shangkeschedule.shared.generated.resources.item_webdav_config
import shangkeschedule.shared.generated.resources.label_webdav_account
import shangkeschedule.shared.generated.resources.label_webdav_path
import shangkeschedule.shared.generated.resources.label_webdav_pwd_empty
import shangkeschedule.shared.generated.resources.label_webdav_pwd_saved
import shangkeschedule.shared.generated.resources.label_webdav_url
import shangkeschedule.shared.generated.resources.section_data_maintenance
import shangkeschedule.shared.generated.resources.section_service_config
import shangkeschedule.shared.generated.resources.title_loading
import shangkeschedule.shared.generated.resources.toast_operation_failed
import shangkeschedule.shared.generated.resources.toast_operation_success
import shangkeschedule.shared.generated.resources.upload_24px
import kotlin.time.Clock

/**
 * 备份/恢复的目标媒介枚举
 */
enum class BackupTarget(val stringRes: StringResource) {
    WEBDAV(Res.string.backup_target_webdav),
    LOCAL_ZIP(Res.string.backup_target_local_zip)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showConfigDialog by remember { mutableStateOf(false) }
    var showBackupTargetDialog by remember { mutableStateOf(false) }
    var showRestoreTargetDialog by remember { mutableStateOf(false) }
    // 等用户确认后才真正执行的恢复目标（WebDAV / 本地 Zip）
    var pendingRestoreTarget by remember { mutableStateOf<BackupTarget?>(null) }
    // 解除 WebDAV 绑定的二次确认
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    val streamOpenFailedMsg = stringResource(Res.string.error_stream_open_failed)
    val webdavUnconfiguredMsg = stringResource(Res.string.error_webdav_unconfigured)
    val opSuccessMsg = stringResource(Res.string.toast_operation_success)
    val opFailedPrefix = stringResource(Res.string.toast_operation_failed, "")

    // 初始化 KMP 平台的 FileManager 回调
    val fileManager = rememberFileManager(
        FileManagerCallbacks(
            onFileImported = { bytes, _ ->
                if (bytes != null) {
                    scope.launch {
                        try {
                            // 将 ByteArray 转化为 Okio 的 Buffer/BufferedSource
                            val buffer = Buffer().write(bytes)
                            viewModel.importFromLocalZip(buffer)
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar(streamOpenFailedMsg)
                        }
                    }
                }
            },
            onFileExported = { success ->
                if (!success) {
                    scope.launch { snackbarHostState.showSnackbar(streamOpenFailedMsg) }
                }
            }
        )
    )

    LaunchedEffect(state.testResult) {
        when (val result = state.testResult) {
            is TestResult.Error -> {
                snackbarHostState.showSnackbar(
                    message = opFailedPrefix + result.message,
                    duration = SnackbarDuration.Short
                )
            }
            is TestResult.PartialSuccess -> {
                // P1-15 静默降级显性化：部分模块缺失时明确提示用户
                snackbarHostState.showSnackbar(
                    message = result.message,
                    duration = SnackbarDuration.Long
                )
            }
            is TestResult.Success -> {
                snackbarHostState.showSnackbar(
                    message = opSuccessMsg,
                    duration = SnackbarDuration.Short
                )
            }
            TestResult.Idle -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.item_backup_restore)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { com.shangkeschedule.ui.components.AppSnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.listGap)
        ) {
            if (state.isBusy || state.isTesting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            CardGroup(title = stringResource(Res.string.section_data_maintenance)) {
                MenuActionItem(
                    title = stringResource(Res.string.item_backup_data),
                    subtitle = stringResource(Res.string.desc_backup_data),
                    icon = vectorResource(Res.drawable.upload_24px),
                    enabled = !state.isBusy,
                    onClick = { showBackupTargetDialog = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = appColors().divider
                )
                MenuActionItem(
                    title = stringResource(Res.string.item_restore_data),
                    subtitle = stringResource(Res.string.desc_restore_data),
                    icon = vectorResource(Res.drawable.download_24px),
                    enabled = !state.isBusy,
                    onClick = { showRestoreTargetDialog = true }
                )
            }

            CardGroup(title = stringResource(Res.string.section_service_config)) {
                MenuActionItem(
                    title = stringResource(Res.string.item_webdav_config),
                    subtitle = if (state.baseUrl.isBlank()) {
                        stringResource(Res.string.desc_webdav_unconfigured)
                    } else {
                        stringResource(Res.string.desc_webdav_connected, state.baseUrl)
                    },
                    icon = vectorResource(Res.drawable.cloud_24px),
                    onClick = { showConfigDialog = true }
                )
            }
        }
    }

    if (showConfigDialog) {
        WebDavConfigDialog(
            state = state,
            onDismiss = { showConfigDialog = false },
            onSave = { u, n, p, r ->
                viewModel.testWebDavConnection(u, n, p, r)
                showConfigDialog = false
            },
            onDisconnect = {
                // 先关闭配置弹窗，再弹出解除绑定的二次确认
                showConfigDialog = false
                showDisconnectConfirm = true
            }
        )
    }

    if (showBackupTargetDialog) {
        TargetSelectionDialog(
            title = stringResource(Res.string.dialog_title_backup_target),
            onDismiss = { showBackupTargetDialog = false },
            onTargetSelected = { target ->
                showBackupTargetDialog = false
                when (target) {
                    BackupTarget.WEBDAV -> {
                        if (state.baseUrl.isNotBlank()) {
                            viewModel.backupToWebDav()
                        } else {
                            scope.launch { snackbarHostState.showSnackbar(webdavUnconfiguredMsg) }
                        }
                    }
                    BackupTarget.LOCAL_ZIP -> {
                        scope.launch {
                            val buffer = Buffer()
                            val isSuccess = viewModel.exportToLocalZip(buffer)

                            if (isSuccess) {
                                val bytes = buffer.readByteArray()

                                if (bytes.isNotEmpty()) {
                                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                    val year = now.year
                                    val month = now.month.number.toString().padStart(2, '0')
                                    val day = now.day.toString().padStart(2, '0')
                                    val defaultFileName = "ShangKe_Backup_${year}${month}${day}.zip"
                                    fileManager.exportFile(defaultFileName, bytes)
                                } else {
                                    snackbarHostState.showSnackbar(streamOpenFailedMsg)
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (showRestoreTargetDialog) {
        TargetSelectionDialog(
            title = stringResource(Res.string.dialog_title_restore_source),
            onDismiss = { showRestoreTargetDialog = false },
            onTargetSelected = { target ->
                showRestoreTargetDialog = false
                when (target) {
                    BackupTarget.WEBDAV -> {
                        if (state.baseUrl.isNotBlank()) {
                            pendingRestoreTarget = target
                        } else {
                            scope.launch { snackbarHostState.showSnackbar(webdavUnconfiguredMsg) }
                        }
                    }
                    BackupTarget.LOCAL_ZIP -> {
                        // 恢复会覆盖当前数据：先统一确认，确认后再打开本地文件选择器
                        pendingRestoreTarget = target
                    }
                }
            }
        )
    }

    // 恢复前的二次确认（WebDAV / 本地 Zip 共用，覆盖当前课表数据不可撤销）
    pendingRestoreTarget?.let { target ->
        AppDangerDialog(
            onDismissRequest = { pendingRestoreTarget = null },
            title = stringResource(Res.string.dialog_title_confirm_restore),
            text = stringResource(Res.string.dialog_text_confirm_restore),
            confirmText = stringResource(Res.string.action_restore),
            onConfirm = {
                pendingRestoreTarget = null
                when (target) {
                    BackupTarget.WEBDAV -> viewModel.restoreFromWebDav()
                    BackupTarget.LOCAL_ZIP -> fileManager.importFile(listOf("zip"))
                }
            }
        )
    }

    // 解除 WebDAV 绑定的二次确认
    if (showDisconnectConfirm) {
        AppDangerDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = stringResource(Res.string.dialog_title_confirm_webdav_disconnect),
            text = stringResource(Res.string.dialog_text_confirm_webdav_disconnect),
            confirmText = stringResource(Res.string.action_confirm),
            onConfirm = {
                viewModel.disconnectWebDav()
                showConfigDialog = false
                showDisconnectConfirm = false
            }
        )
    }
}

@Composable
fun CardGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    // 统一分区语言：AppSectionHeader + 白色 AppCard（与设置二级页 SectionCard 同构）
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSectionHeader(title)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
fun MenuActionItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: AccentTone = AccentTone.PRIMARY,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    // 行组件统一委托 SettingItem（标题 16sp SemiBold + 副标题 13sp + 语义 IconChip + chevron）
    SettingItem(
        title = title,
        subtitle = subtitle,
        leadingIcon = icon,
        accent = accent,
        onClick = if (enabled) onClick else null
    )
}

@Composable
fun TargetSelectionDialog(
    title: String,
    onDismiss: () -> Unit,
    onTargetSelected: (BackupTarget) -> Unit
) {
    var selectedTarget by remember { mutableStateOf(BackupTarget.entries.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                BackupTarget.entries.forEach { target ->
                    val isSelected = target == selectedTarget

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTarget = target }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedTarget = target }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(target.stringRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            // 统一操作区：取消灰字 + 确认主色胶囊（AppDialogActions）
            AppDialogActions(
                confirmText = stringResource(Res.string.action_confirm),
                onConfirm = { onTargetSelected(selectedTarget) },
                dismissText = stringResource(Res.string.action_cancel),
                onDismiss = onDismiss
            )
        },
        dismissButton = {}
    )
}

@Composable
fun WebDavConfigDialog(
    state: BackupUiState,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
    onDisconnect: () -> Unit
) {
    var inputUrl by remember { mutableStateOf(state.baseUrl) }
    var inputUsername by remember { mutableStateOf(state.username) }
    var inputPassword by remember { mutableStateOf("") }
    var inputRootPath by remember { mutableStateOf(state.rootPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_title_config_webdav)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 统一柔和填充输入框（AppTextField）
                AppTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = stringResource(Res.string.label_webdav_url),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = inputUsername,
                    onValueChange = { inputUsername = it },
                    label = stringResource(Res.string.label_webdav_account),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = inputPassword,
                    onValueChange = { inputPassword = it },
                    label = if (state.hasSavedPassword) stringResource(Res.string.label_webdav_pwd_saved)
                    else stringResource(Res.string.label_webdav_pwd_empty),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = inputRootPath,
                    onValueChange = { inputRootPath = it },
                    label = stringResource(Res.string.label_webdav_path),
                    supportingText = { Text(stringResource(Res.string.desc_webdav_path_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            // 统一操作区：确认主色胶囊；「解除绑定」为破坏性次要操作，以灰字呈现避免误触
            AppDialogActions(
                confirmText = if (state.isTesting) stringResource(Res.string.title_loading)
                else stringResource(Res.string.action_confirm),
                onConfirm = { onSave(inputUrl, inputUsername, inputPassword, inputRootPath) },
                confirmEnabled = !state.isTesting,
                dismissText = stringResource(Res.string.action_reset),
                onDismiss = {
                    onDisconnect()
                    onDismiss()
                }
            )
        },
        dismissButton = {}
    )
}