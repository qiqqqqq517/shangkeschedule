package com.shangkeschedule.ui.settings.conversion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.shangkeschedule.Destination
import com.shangkeschedule.data.di.AppStorage
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.ShareDialog
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.SYSTEM
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.a11y_details
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.desc_backup_restore
import shangkeschedule.shared.generated.resources.desc_export_ics_with_alarm
import shangkeschedule.shared.generated.resources.desc_export_json_with_config
import shangkeschedule.shared.generated.resources.desc_import_json
import shangkeschedule.shared.generated.resources.desc_school_import_quick
import shangkeschedule.shared.generated.resources.desc_sync_to_system_calendar
import shangkeschedule.shared.generated.resources.item_backup_restore
import shangkeschedule.shared.generated.resources.item_export_course_file
import shangkeschedule.shared.generated.resources.item_export_ics_file
import shangkeschedule.shared.generated.resources.item_import_course_file
import shangkeschedule.shared.generated.resources.item_school_system_import
import shangkeschedule.shared.generated.resources.item_sync_to_system_calendar
import shangkeschedule.shared.generated.resources.section_file_conversion
import shangkeschedule.shared.generated.resources.section_school_import
import shangkeschedule.shared.generated.resources.section_sync
import shangkeschedule.shared.generated.resources.snackbar_file_save_canceled
import shangkeschedule.shared.generated.resources.snackbar_file_selection_canceled
import shangkeschedule.shared.generated.resources.title_conversion
import kotlin.time.Clock

/**
 * 课表导入导出与转换设置主界面。
 * 整合了跨平台文件导入导出、教务系统导入、系统日历同步等功能。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseTableConversionScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: CourseTableConversionViewModel = koinViewModel(),
    appStorage: AppStorage = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val snackbarFileSaveCanceled = stringResource(Res.string.snackbar_file_save_canceled)

    var pendingShareFilePath by remember { mutableStateOf<String?>(null) }
    var shareFilePath by remember { mutableStateOf<String?>(null) }
    var shareFileMimeType by remember { mutableStateOf("application/json") }

    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onFileExported = { success ->
                if (success) {
                    shareFilePath = pendingShareFilePath
                } else {
                    pendingShareFilePath = null
                    coroutineScope.launch { snackbarHostState.showSnackbar(snackbarFileSaveCanceled) }
                }
            }
        )
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ConversionEvent.LaunchExportFileCreator -> {
                    val timestamp = Clock.System.now().toEpochMilliseconds()
                    val fileName = "ShangKe_$timestamp.json"
                    val bytes = event.jsonContent.encodeToByteArray()

                    val shareTempDir = appStorage.cacheDir / "share_temp"
                    val tempFilePath = shareTempDir / fileName
                    FileSystem.SYSTEM.createDirectories(shareTempDir)
                    FileSystem.SYSTEM.write(tempFilePath) {
                        write(bytes)
                    }
                    pendingShareFilePath = tempFilePath.toString()
                    shareFileMimeType = "application/json"
                    fileManager.exportFile(fileName, bytes)
                }
                is ConversionEvent.LaunchExportIcsFileCreator -> {
                    val timestamp = Clock.System.now().toEpochMilliseconds()
                    val fileName = "ShangKe_$timestamp.ics"
                    val bytes = event.icsContent.encodeToByteArray()
                    val shareTempDir = appStorage.cacheDir / "share_temp"
                    val tempFilePath = shareTempDir / fileName
                    FileSystem.SYSTEM.createDirectories(shareTempDir)
                    FileSystem.SYSTEM.write(tempFilePath) {
                        write(bytes)
                    }
                    pendingShareFilePath = tempFilePath.toString()
                    shareFileMimeType = "text/calendar"

                    fileManager.exportFile(fileName, bytes)
                }
                is ConversionEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> { }
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(Res.string.title_conversion)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.arrow_back_24px),
                                contentDescription = stringResource(Res.string.a11y_back)
                            )
                        }
                    }
                )
                if (uiState.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            Text(stringResource(Res.string.section_file_conversion), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SectionCard {
                SettingItem(
                    title = "文件导入",
                    subtitle = "Excel / JSON / 文本文件，分类导入",
                    onClick = { onNavigate(Destination.FileImportHub) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_export_course_file),
                    subtitle = stringResource(Res.string.desc_export_json_with_config),
                    onClick = { viewModel.onExportClick() }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_export_ics_file),
                    subtitle = stringResource(Res.string.desc_export_ics_with_alarm),
                    onClick = { viewModel.onExportIcsClick() }
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(stringResource(Res.string.section_school_import), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SectionCard {
                SettingItem(
                    title = stringResource(Res.string.item_school_system_import),
                    subtitle = stringResource(Res.string.desc_school_import_quick),
                    onClick = { onNavigate(Destination.SchoolSelectionListScreen()) }
                )
                SectionDivider()
                SettingItem(
                    title = "文本粘贴导入",
                    subtitle = "WakeUp文本/纯文本/JSON/CSV/ICS 分类导入，先预览再导入",
                    onClick = { onNavigate(Destination.TextImportHub) }
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(stringResource(Res.string.section_sync), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SectionCard {
                SettingItem(
                    title = stringResource(Res.string.item_sync_to_system_calendar),
                    subtitle = stringResource(Res.string.desc_sync_to_system_calendar),
                    onClick = { viewModel.onSyncToCalendarClick() }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_backup_restore),
                    subtitle = stringResource(Res.string.desc_backup_restore),
                    onClick = { onNavigate(Destination.BackupAndRestore) }
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    ConversionDialogOverlay(
        uiState = uiState,
        onDismiss = { viewModel.dismissDialog() },
        onConfirmImport = { viewModel.onImportTableSelected(it) },
        onConfirmExport = { id, mins -> viewModel.onExportTableSelected(id, mins) }
    )

    shareFilePath?.let { path ->
        ShareDialog(
            filePath = path,
            mimeType = shareFileMimeType,
            onDismiss = {
                shareFilePath = null
                pendingShareFilePath = null
            }
        )
    }
}
