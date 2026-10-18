package com.shangkeschedule.ui.settings.conversion

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.theme.appSpacing

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shangkeschedule.Destination
import com.shangkeschedule.data.di.AppStorage
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.ShareDialog
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.settings.import.ImportSource
import shangkeschedule.shared.generated.resources.conversion_import_entry_desc
import shangkeschedule.shared.generated.resources.import_entry_title
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.SYSTEM
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.desc_ai_import
import shangkeschedule.shared.generated.resources.desc_backup_restore
import shangkeschedule.shared.generated.resources.desc_course_share
import shangkeschedule.shared.generated.resources.desc_export_ics_with_alarm
import shangkeschedule.shared.generated.resources.desc_export_json_with_config
import shangkeschedule.shared.generated.resources.desc_school_import_quick
import shangkeschedule.shared.generated.resources.desc_sync_to_system_calendar
import shangkeschedule.shared.generated.resources.item_backup_restore
import shangkeschedule.shared.generated.resources.item_export_course_file
import shangkeschedule.shared.generated.resources.item_export_ics_file
import shangkeschedule.shared.generated.resources.item_school_system_import
import shangkeschedule.shared.generated.resources.item_sync_to_system_calendar
import shangkeschedule.shared.generated.resources.section_file_conversion
import shangkeschedule.shared.generated.resources.section_school_import
import shangkeschedule.shared.generated.resources.section_share
import shangkeschedule.shared.generated.resources.section_sync
import shangkeschedule.shared.generated.resources.snackbar_file_save_canceled
import shangkeschedule.shared.generated.resources.title_ai_import
import shangkeschedule.shared.generated.resources.title_conversion
import shangkeschedule.shared.generated.resources.title_course_share
import shangkeschedule.shared.generated.resources.upload_24px
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 系统日历同步前先检查/申请权限（平台 expect/actual，非 Android 直接放行）
    val requestCalendarSync = rememberCalendarSyncGate(onSync = { viewModel.onSyncToCalendarClick() })

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
                AppTopAppBar(
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
        snackbarHost = { com.shangkeschedule.ui.components.AppSnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = appSpacing().pageHorizontal)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(appSpacing().pageTop))

            // v4.80.0 导入体系整合：文件导入与文本粘贴导入合并为唯一的「导入课表」入口，
            // 来源（文件 / 粘贴）在页面内切换；格式由解析器自动嗅探，不再让用户先选格式。
            AppSectionHeader(stringResource(Res.string.section_file_conversion), modifier = Modifier.fillMaxWidth())
            SectionCard {
                SettingItem(
                    title = stringResource(Res.string.import_entry_title),
                    subtitle = stringResource(Res.string.conversion_import_entry_desc),
                    leadingIcon = vectorResource(Res.drawable.upload_24px),
                    onClick = { onNavigate(Destination.ImportEntry(ImportSource.AUTO)) }
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

            Spacer(Modifier.height(appSpacing().sectionGap))

            AppSectionHeader(stringResource(Res.string.section_school_import), modifier = Modifier.fillMaxWidth())
            SectionCard {
                SettingItem(
                    title = stringResource(Res.string.item_school_system_import),
                    subtitle = stringResource(Res.string.desc_school_import_quick),
                    onClick = { onNavigate(Destination.SchoolSelectionListScreen()) }
                )
                // AI 识别导入：提示词法（默认）+ 接口法同页，识别结果同样进统一导入页预览。
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.title_ai_import),
                    subtitle = stringResource(Res.string.desc_ai_import),
                    onClick = { onNavigate(Destination.AiImport) }
                )
            }

            Spacer(Modifier.height(appSpacing().sectionGap))

            AppSectionHeader(stringResource(Res.string.section_sync), modifier = Modifier.fillMaxWidth())
            SectionCard {
                SettingItem(
                    title = stringResource(Res.string.item_sync_to_system_calendar),
                    subtitle = stringResource(Res.string.desc_sync_to_system_calendar),
                    onClick = requestCalendarSync
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_backup_restore),
                    subtitle = stringResource(Res.string.desc_backup_restore),
                    onClick = { onNavigate(Destination.BackupAndRestore) }
                )
            }

            Spacer(Modifier.height(appSpacing().sectionGap))

            // v4.66.1（IA 再排）：「课程分享」原先挂在「文件转换 / 导出」卡里，但分享
            // **不做任何格式转换**——它生成的是分享卡片与二维码，与上面两条「导出成
            // JSON / ICS 文件」不同族。挂在导出卡里会让用户以为点进去能导出文件。
            // 故独立成「分享」分区，既归位又不给它硬凑同族。
            AppSectionHeader(stringResource(Res.string.section_share), modifier = Modifier.fillMaxWidth())
            SectionCard {
                SettingItem(
                    title = stringResource(Res.string.title_course_share),
                    subtitle = stringResource(Res.string.desc_course_share),
                    onClick = { onNavigate(Destination.CourseShare) }
                )
            }
            Spacer(Modifier.height(appSpacing().contentBottom))
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
