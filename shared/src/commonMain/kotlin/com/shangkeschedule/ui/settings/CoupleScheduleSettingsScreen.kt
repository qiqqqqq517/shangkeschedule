package com.shangkeschedule.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.shangkeschedule.Destination
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.settings.conversion.ConversionEvent
import com.shangkeschedule.ui.settings.conversion.CourseTableConversionViewModel
import com.shangkeschedule.ui.settings.conversion.CrushImportDialog
import kotlinx.coroutines.launch
import okio.Buffer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.desc_couple_schedule
import shangkeschedule.shared.generated.resources.desc_crush_course_color
import shangkeschedule.shared.generated.resources.desc_delete_crush_schedule
import shangkeschedule.shared.generated.resources.desc_import_crush_schedule
import shangkeschedule.shared.generated.resources.desc_self_course_color
import shangkeschedule.shared.generated.resources.item_couple_schedule
import shangkeschedule.shared.generated.resources.item_crush_course_color
import shangkeschedule.shared.generated.resources.item_delete_crush_schedule
import shangkeschedule.shared.generated.resources.item_import_crush_schedule
import shangkeschedule.shared.generated.resources.item_self_course_color
import shangkeschedule.shared.generated.resources.section_title_couple_schedule
import shangkeschedule.shared.generated.resources.snackbar_file_selection_canceled

/**
 * 情侣课表二级页。
 *
 * 承载情侣课表开关、本人 / TA 课程颜色设置，以及情侣课表的导入 / 删除管理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleScheduleSettingsScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
    conversionViewModel: CourseTableConversionViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val courseColorMaps by viewModel.courseColorMaps.collectAsState()
    val conversionUiState by conversionViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val snackbarFileSelectionCanceled = stringResource(Res.string.snackbar_file_selection_canceled)

    var pendingCrushImport by remember { mutableStateOf(false) }
    var showDeleteCrushConfirm by remember { mutableStateOf(false) }

    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onFileImported = { bytes, _ ->
                if (bytes == null) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(snackbarFileSelectionCanceled)
                    }
                    pendingCrushImport = false
                    return@FileManagerCallbacks
                }
                val source = Buffer().write(bytes)
                if (pendingCrushImport) {
                    conversionViewModel.handleCrushFileImport(source)
                }
                pendingCrushImport = false
            },
            onFileExported = { }
        )
    )

    LaunchedEffect(Unit) {
        conversionViewModel.events.collect { event ->
            when (event) {
                is ConversionEvent.NavigateToCrushSchoolImport -> {
                    onNavigate(Destination.SchoolSelectionListScreen(isCrushImport = true))
                }
                is ConversionEvent.LaunchCrushImportFilePicker -> {
                    pendingCrushImport = true
                    fileManager.importFile(listOf("json"))
                }
                is ConversionEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> { }
            }
        }
    }

    if (!uiState.isReady) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.section_title_couple_schedule)) },
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
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) { }
        }
        return
    }

    val appSettings = uiState.appSettings
    var showSelfColorDialog by remember { mutableStateOf(false) }
    var showCrushColorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.section_title_couple_schedule)) },
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionCard {
                // 情侣课表开关
                SettingItem(
                    title = stringResource(Res.string.item_couple_schedule),
                    subtitle = stringResource(Res.string.desc_couple_schedule)
                ) {
                    Switch(
                        checked = appSettings.coupleScheduleEnabled,
                        onCheckedChange = { viewModel.onCoupleScheduleEnabledChanged(it) }
                    )
                }

                // 情侣课表颜色（开关开启后显示）
                if (appSettings.coupleScheduleEnabled) {
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.item_self_course_color),
                        subtitle = stringResource(Res.string.desc_self_course_color),
                        onClick = { showSelfColorDialog = true }
                    ) {
                        ColorPreviewDot(
                            colorIndex = appSettings.selfCourseColorIndex,
                            colorMaps = courseColorMaps
                        )
                    }
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.item_crush_course_color),
                        subtitle = stringResource(Res.string.desc_crush_course_color),
                        onClick = { showCrushColorDialog = true }
                    ) {
                        ColorPreviewDot(
                            colorIndex = appSettings.crushCourseColorIndex,
                            colorMaps = courseColorMaps
                        )
                    }
                }

                // 情侣课表导入 / 删除管理
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_import_crush_schedule),
                    subtitle = stringResource(Res.string.desc_import_crush_schedule),
                    onClick = { conversionViewModel.onImportCrushClick() }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_delete_crush_schedule),
                    subtitle = stringResource(Res.string.desc_delete_crush_schedule),
                    onClick = { showDeleteCrushConfirm = true }
                )
            }
        }
    }

    if (showSelfColorDialog) {
        ColorPickerDialog(
            title = stringResource(Res.string.item_self_course_color),
            selectedIndex = appSettings.selfCourseColorIndex,
            colorMaps = courseColorMaps,
            onDismiss = { showSelfColorDialog = false },
            onSelect = { index ->
                viewModel.onSelfCourseColorIndexChanged(index)
                showSelfColorDialog = false
            }
        )
    }
    if (showCrushColorDialog) {
        ColorPickerDialog(
            title = stringResource(Res.string.item_crush_course_color),
            selectedIndex = appSettings.crushCourseColorIndex,
            colorMaps = courseColorMaps,
            onDismiss = { showCrushColorDialog = false },
            onSelect = { index ->
                viewModel.onCrushCourseColorIndexChanged(index)
                showCrushColorDialog = false
            }
        )
    }

    if (conversionUiState.showCrushImportDialog) {
        CrushImportDialog(
            onDismissRequest = { conversionViewModel.dismissDialog() },
            onImportViaSchool = { conversionViewModel.onCrushImportViaSchool() },
            onImportViaJson = { conversionViewModel.onCrushImportViaJson() }
        )
    }

    if (showDeleteCrushConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteCrushConfirm = false },
            title = { Text(stringResource(Res.string.item_delete_crush_schedule)) },
            text = { Text("确定要删除情侣课表吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteCrushConfirm = false
                    conversionViewModel.onDeleteCrushClick()
                }) {
                    Text("确定删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCrushConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
