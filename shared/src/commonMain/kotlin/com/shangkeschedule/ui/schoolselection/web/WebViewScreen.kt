package com.shangkeschedule.ui.schoolselection.web

import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shangkeschedule.Destination
import com.shangkeschedule.data.repository.CourseConversionRepository
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.CourseTablePickerDialog
import com.shangkeschedule.ui.components.TelegramMenu
import com.shangkeschedule.ui.components.TelegramMenuItem
import com.shangkeschedule.ui.components.ToastManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import com.shangkeschedule.ui.components.AppDialogActions
import shangkeschedule.shared.generated.resources.action_go_to_settings
import shangkeschedule.shared.generated.resources.webview_semester_prompt_later
import shangkeschedule.shared.generated.resources.webview_semester_prompt_message
import shangkeschedule.shared.generated.resources.webview_semester_prompt_title
import shangkeschedule.shared.generated.resources.webview_load_error_generic
import shangkeschedule.shared.generated.resources.webview_load_error_fmt
import shangkeschedule.shared.generated.resources.webview_load_error_detail
import shangkeschedule.shared.generated.resources.webview_load_error_retry
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.a11y_cancel_editing
import shangkeschedule.shared.generated.resources.a11y_devtools
import shangkeschedule.shared.generated.resources.a11y_enter_url
import shangkeschedule.shared.generated.resources.a11y_load
import shangkeschedule.shared.generated.resources.a11y_more_options
import shangkeschedule.shared.generated.resources.a11y_refresh
import shangkeschedule.shared.generated.resources.action_execute_import
import shangkeschedule.shared.generated.resources.action_navigate_to_timetable
import shangkeschedule.shared.generated.resources.action_refresh
import shangkeschedule.shared.generated.resources.action_switch_to_desktop_mode
import shangkeschedule.shared.generated.resources.action_switch_to_phone_mode
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.arrow_forward_24px
import shangkeschedule.shared.generated.resources.build_24px
import shangkeschedule.shared.generated.resources.desktop_windows_24px
import shangkeschedule.shared.generated.resources.dialog_title_select_table_for_import
import shangkeschedule.shared.generated.resources.item_devtools_debug
import shangkeschedule.shared.generated.resources.link_24px
import shangkeschedule.shared.generated.resources.more_vert_24px
import shangkeschedule.shared.generated.resources.phone_android_24px
import shangkeschedule.shared.generated.resources.placeholder_enter_url_full
import shangkeschedule.shared.generated.resources.refresh_24px
import shangkeschedule.shared.generated.resources.status_disabled
import shangkeschedule.shared.generated.resources.status_enabled
import shangkeschedule.shared.generated.resources.title_enter_url
import shangkeschedule.shared.generated.resources.title_loading
import shangkeschedule.shared.generated.resources.toast_devtools_enabled_format
import shangkeschedule.shared.generated.resources.toast_executing_import_script
import shangkeschedule.shared.generated.resources.toast_import_script_not_found
import shangkeschedule.shared.generated.resources.toast_load_import_script_failed
import shangkeschedule.shared.generated.resources.toast_navigating_to_timetable
import shangkeschedule.shared.generated.resources.toast_no_script_manual_import
import shangkeschedule.shared.generated.resources.toast_switched_to_desktop
import shangkeschedule.shared.generated.resources.toast_switched_to_phone
import shangkeschedule.shared.generated.resources.toast_timetable_entry_not_found

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    initialUrl: String?,
    assetJsPath: String?,
    isCrushImport: Boolean = false,
    forceDesktopMode: Boolean = false,
    viewModel: WebViewModel = koinViewModel()
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    val isDeveloperModeEnabled by viewModel.isDeveloperModeEnabled.collectAsState()
    val startedEmpty = remember { initialUrl.isNullOrBlank() || initialUrl == "about:blank" }
    val showAddressBarToggleButton = startedEmpty || isDeveloperModeEnabled

    val titleEnterUrl = stringResource(Res.string.title_enter_url)
    val titleLoading = stringResource(Res.string.title_loading)
    val toastSwitchedToDesktop = stringResource(Res.string.toast_switched_to_desktop)
    val toastSwitchedToPhone = stringResource(Res.string.toast_switched_to_phone)
    val toastNoManualImport = stringResource(Res.string.toast_no_script_manual_import)
    val toastExecutingImport = stringResource(Res.string.toast_executing_import_script)
    val toastImportNotFoundFmt = stringResource(Res.string.toast_import_script_not_found, "%s")
    val toastLoadImportFailedFmt = stringResource(Res.string.toast_load_import_script_failed, "%s")
    val toastNavigatingToTimetable = stringResource(Res.string.toast_navigating_to_timetable)
    val toastTimetableEntryNotFound = stringResource(Res.string.toast_timetable_entry_not_found)
    val statusEnabled = stringResource(Res.string.status_enabled)
    val statusDisabled = stringResource(Res.string.status_disabled)
    val toastDevToolsEnabled = stringResource(Res.string.toast_devtools_enabled_format, statusEnabled)
    val toastDevToolsDisabled = stringResource(Res.string.toast_devtools_enabled_format, statusDisabled)

    var currentUrl by remember { mutableStateOf(initialUrl ?: "about:blank") }
    var inputUrl by remember { mutableStateOf(if (startedEmpty) "" else (initialUrl ?: "")) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var pageTitle by remember { mutableStateOf(if (startedEmpty) titleEnterUrl else titleLoading) }
    // WebView 加载失败反馈（P1-4）：webViewLoadFailed 为 true 时覆盖全屏错误页
    var webViewLoadFailed by remember { mutableStateOf(false) }
    var loadErrorDescription by remember { mutableStateOf("") }
    // 加载看门狗计数器：每次发起加载（onSearch / 重试 / 初始）时 +1，重新计时
    var watchdogNonce by remember { mutableStateOf(0) }

    var expanded by remember { mutableStateOf(false) }
    var isDesktopMode by remember { mutableStateOf(forceDesktopMode) }
    var isEditingUrl by remember { mutableStateOf(startedEmpty) }
    var isDevToolsEnabled by remember { mutableStateOf(false) }
    var showCourseTablePicker by remember { mutableStateOf(false) }
    var showSemesterStartPrompt by remember { mutableStateOf(false) }

    val webViewController = rememberWebViewController()

    val coroutineScope = rememberCoroutineScope()
    val courseConversionRepository: CourseConversionRepository = koinInject()
    val uiEventChannel = remember { Channel<WebUiEvent>(Channel.UNLIMITED) }
    val uiEventsFlow = remember(uiEventChannel) { uiEventChannel.receiveAsFlow() }

    val bridgeHandler = remember(coroutineScope, courseConversionRepository, webViewController, isCrushImport) {
        WebBridgeHandler(
            coroutineScope = coroutineScope,
            uiEventChannel = uiEventChannel,
            courseConversionRepository = courseConversionRepository,
            isCrushImport = isCrushImport,
            onTaskCompleted = {
                coroutineScope.launch {
                    if (courseConversionRepository.isSemesterStartDateSet()) {
                        onNavigate(Destination.CourseSchedule)
                    } else {
                        showSemesterStartPrompt = true
                    }
                }
            },
            evaluateJs = { script, callback ->
                webViewController.evaluateJavascript(script, callback)
            }
        )
    }

    val handleBackAction: () -> Unit = {
        if (isEditingUrl) {
            isEditingUrl = false
            val rawUrl = webViewController.currentUrl
            inputUrl = if (rawUrl.isBlank() || rawUrl == "about:blank") "" else rawUrl
            keyboardController?.hide()
        } else {
            if (webViewController.canGoBack()) {
                webViewController.goBack()
            } else {
                onBack()
            }
        }
    }

    PlatformBackHandler(enabled = true, onBack = handleBackAction)

    // 加载超时看门狗（P1-5）：发起加载 30s 后仍未完成（onPageFinished 未触发）则提示失败，
    // 避免校园网不稳 / 页面假死时用户面对空白页 + 永久卡住的进度条。
    LaunchedEffect(watchdogNonce) {
        if (webViewLoadFailed) return@LaunchedEffect
        delay(30_000)
        if (!webViewLoadFailed && loadingProgress < 1.0f) {
            webViewLoadFailed = true
            loadErrorDescription = ""
        }
    }

    val onSearch: (String) -> Unit = { query ->
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            val formattedUrl = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }
            keyboardController?.hide()
            currentUrl = formattedUrl
            isEditingUrl = false
            pageTitle = titleLoading
            webViewLoadFailed = false
            loadErrorDescription = ""
            watchdogNonce += 1
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = handleBackAction) {
                        Icon(
                            vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(
                                if (isEditingUrl) Res.string.a11y_cancel_editing else Res.string.a11y_back
                            )
                        )
                    }
                },
                title = {
                    if (isEditingUrl) {
                        AppTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            placeholder = stringResource(Res.string.placeholder_enter_url_full),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    onSearch(inputUrl)
                                }
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    } else {
                        Text(
                            text = pageTitle,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isEditingUrl) {
                            IconButton(
                                onClick = { onSearch(inputUrl) },
                                enabled = inputUrl.trim().isNotBlank() && inputUrl.trim() != "https://"
                            ) {
                                Icon(vectorResource(Res.drawable.arrow_forward_24px), contentDescription = stringResource(Res.string.a11y_load))
                            }
                        } else if (showAddressBarToggleButton) {
                            IconButton(onClick = {
                                isEditingUrl = true
                                val rawUrl = webViewController.currentUrl
                                inputUrl = if (rawUrl.isBlank() || rawUrl == "about:blank") "" else rawUrl
                                keyboardController?.show()
                            }) {
                                Icon(vectorResource(Res.drawable.link_24px), contentDescription = stringResource(Res.string.a11y_enter_url))
                            }
                        }

                        IconButton(onClick = { expanded = true }) {
                            Icon(vectorResource(Res.drawable.more_vert_24px), contentDescription = stringResource(Res.string.a11y_more_options))
                        }

                        TelegramMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            TelegramMenuItem(
                                icon = vectorResource(Res.drawable.refresh_24px),
                                text = stringResource(Res.string.action_refresh),
                                onClick = {
                                    webViewController.reload()
                                    expanded = false
                                }
                            )

                            if (!isDesktopPlatform) {
                                val switchTextId = if (isDesktopMode) Res.string.action_switch_to_phone_mode else Res.string.action_switch_to_desktop_mode
                                val switchIcon = vectorResource(if (isDesktopMode) Res.drawable.phone_android_24px else Res.drawable.desktop_windows_24px)

                                TelegramMenuItem(
                                    icon = switchIcon,
                                    text = stringResource(switchTextId),
                                    onClick = {
                                        val realUrl = webViewController.currentUrl
                                        if (realUrl.isNotBlank() && realUrl != "about:blank") {
                                            currentUrl = realUrl
                                        }
                                        isDesktopMode = !isDesktopMode
                                        val tText = if (isDesktopMode) toastSwitchedToDesktop else toastSwitchedToPhone
                                        ToastManager.show(tText)
                                        expanded = false
                                    }
                                )
                            }

                            if (isDeveloperModeEnabled) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .clickable {
                                            isDevToolsEnabled = !isDevToolsEnabled
                                            webViewController.setDevToolsEnabled(isDevToolsEnabled)
                                            val tText = if (isDevToolsEnabled) toastDevToolsEnabled else toastDevToolsDisabled
                                            ToastManager.show(tText)
                                            expanded = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = vectorResource(Res.drawable.build_24px),
                                            contentDescription = stringResource(Res.string.a11y_devtools),
                                            tint = appColors().textSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = stringResource(Res.string.item_devtools_debug),
                                            modifier = Modifier.padding(start = 14.dp),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = appType().body),
                                            fontWeight = FontWeight.Medium,
                                            color = appColors().textPrimary
                                        )
                                    }
                                    AppSwitch(
                                        checked = isDevToolsEnabled,
                                        onCheckedChange = null
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = appSpacing().pageHorizontal),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (assetJsPath != null) {
                                    showCourseTablePicker = true
                                } else {
                                    ToastManager.show(toastNoManualImport)
                                }
                            },
                            enabled = assetJsPath != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(Res.string.action_execute_import))
                        }

                        OutlinedButton(
                            onClick = {
                                webViewController.evaluateJavascript(JS_NAVIGATE_TO_TIMETABLE) { result ->
                                    when (result?.trim('"')) {
                                        "found" -> ToastManager.show(toastNavigatingToTimetable)
                                        "notfound" -> ToastManager.show(toastTimetableEntryNotFound)
                                        else -> Unit
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(Res.string.action_navigate_to_timetable))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            PlatformWebView(
                modifier = Modifier.fillMaxSize(),
                url = currentUrl,
                isDesktopMode = isDesktopMode,
                isDevToolsEnabled = isDevToolsEnabled,
                controller = webViewController,
                bridgeHandler = bridgeHandler,
                onProgressChange = { loadingProgress = it },
                onTitleChange = { pageTitle = it },
                onNavigateToSchedule = { onNavigate(Destination.CourseSchedule) },
                onWebViewLoadError = { description ->
                    webViewLoadFailed = true
                    loadErrorDescription = description
                }
            )

            if (loadingProgress < 1.0f) {
                LinearProgressIndicator(
                    progress = { loadingProgress },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }

            // WebView 加载失败全屏错误页（P1-4）：网络不可用 / 4xx5xx / SSL 错误时给出出口（重试/返回）
            if (webViewLoadFailed && !isEditingUrl) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (loadErrorDescription.isBlank()) {
                            stringResource(Res.string.webview_load_error_generic)
                        } else {
                            stringResource(Res.string.webview_load_error_fmt, loadErrorDescription)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(Res.string.webview_load_error_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColors().textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                webViewLoadFailed = false
                                loadErrorDescription = ""
                                onBack()
                            }
                        ) {
                            Text(stringResource(Res.string.a11y_back))
                        }
                        Button(
                            onClick = {
                                webViewLoadFailed = false
                                loadErrorDescription = ""
                                loadingProgress = 0f
                                watchdogNonce += 1
                                webViewController.reload()
                            }
                        ) {
                            Text(stringResource(Res.string.webview_load_error_retry))
                        }
                    }
                }
            }

            if (showCourseTablePicker && assetJsPath != null) {
                CourseTablePickerDialog(
                    title = stringResource(Res.string.dialog_title_select_table_for_import),
                    onDismissRequest = { showCourseTablePicker = false },
                    onTableSelected = { selectedTable ->
                        showCourseTablePicker = false
                        val tableId = selectedTable.id

                        try {
                            val fileSystem = viewModel.fileSystem
                            val jsFilePath = viewModel.filesDir / "repo" / "schools" / "resources" / assetJsPath

                            if (fileSystem.exists(jsFilePath)) {
                                val jsCode = fileSystem.read(jsFilePath) { readUtf8() }
                                bridgeHandler.setImportTableId(tableId)

                                webViewController.executeScript(buildImportScript(tableId, jsCode))

                                ToastManager.show(toastExecutingImport)
                            } else {
                                ToastManager.show(toastImportNotFoundFmt.replace("%s", jsFilePath.toString()))
                            }
                        } catch (e: Exception) {
                            ToastManager.show(toastLoadImportFailedFmt.replace("%s", e.message ?: ""))
                        }
                    }
                )
            }
            WebDialogHost(uiEvents = uiEventsFlow)

            if (showSemesterStartPrompt) {
                AlertDialog(
                    onDismissRequest = { showSemesterStartPrompt = false },
                    title = { Text(stringResource(Res.string.webview_semester_prompt_title)) },
                    text = { Text(stringResource(Res.string.webview_semester_prompt_message)) },
                    confirmButton = {
                        AppDialogActions(
                            confirmText = stringResource(Res.string.action_go_to_settings),
                            onConfirm = {
                                showSemesterStartPrompt = false
                                onNavigate(Destination.SemesterSettings)
                            },
                            dismissText = stringResource(Res.string.webview_semester_prompt_later),
                            onDismiss = { showSemesterStartPrompt = false }
                        )
                    },
                    dismissButton = {}
                )
            }
        }
    }
}