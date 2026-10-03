package com.shangkeschedule.ui.schoolselection.web

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import com.shangkeschedule.ui.components.ThemedLoadingIndicator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shangkeschedule.Destination
import com.shangkeschedule.WebPagePurpose
import com.shangkeschedule.data.db.main.Grade
import com.shangkeschedule.data.parser.EmptyClassroomRoom
import com.shangkeschedule.data.parser.formatEmptyClassroomLine
import com.shangkeschedule.data.parser.formatEmptyClassroomText
import com.shangkeschedule.data.repository.CourseConversionRepository
import com.shangkeschedule.data.repository.GradeRepository
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppErrorState
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.CourseTablePickerDialog
import com.shangkeschedule.ui.components.TelegramMenu
import com.shangkeschedule.ui.components.TelegramMenuItem
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.tool.copyToClipboard
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.webview_load_error_generic
import shangkeschedule.shared.generated.resources.webview_load_error_fmt
import shangkeschedule.shared.generated.resources.webview_load_error_detail
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.a11y_cancel_editing
import shangkeschedule.shared.generated.resources.a11y_devtools
import shangkeschedule.shared.generated.resources.a11y_enter_url
import shangkeschedule.shared.generated.resources.a11y_load
import shangkeschedule.shared.generated.resources.a11y_more_options
import shangkeschedule.shared.generated.resources.action_execute_import
import shangkeschedule.shared.generated.resources.action_navigate_to_timetable
import shangkeschedule.shared.generated.resources.action_refresh
import shangkeschedule.shared.generated.resources.action_retry
import shangkeschedule.shared.generated.resources.action_switch_to_desktop_mode
import shangkeschedule.shared.generated.resources.action_switch_to_phone_mode
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.arrow_forward_24px
import shangkeschedule.shared.generated.resources.build_24px
import shangkeschedule.shared.generated.resources.desktop_windows_24px
import shangkeschedule.shared.generated.resources.action_close
import shangkeschedule.shared.generated.resources.empty_classroom_capacity
import shangkeschedule.shared.generated.resources.empty_classroom_copied
import shangkeschedule.shared.generated.resources.empty_classroom_copy
import shangkeschedule.shared.generated.resources.empty_classroom_dialog_hint
import shangkeschedule.shared.generated.resources.empty_classroom_dialog_title
import shangkeschedule.shared.generated.resources.empty_classroom_found
import shangkeschedule.shared.generated.resources.empty_classroom_locate
import shangkeschedule.shared.generated.resources.empty_classroom_locate_found
import shangkeschedule.shared.generated.resources.empty_classroom_locate_not_found
import shangkeschedule.shared.generated.resources.empty_classroom_no_result
import shangkeschedule.shared.generated.resources.empty_classroom_scan
import shangkeschedule.shared.generated.resources.empty_classroom_scanning
import shangkeschedule.shared.generated.resources.grade_import_no_result
import shangkeschedule.shared.generated.resources.grade_import_recognize
import shangkeschedule.shared.generated.resources.grade_import_success
import shangkeschedule.shared.generated.resources.grade_semester_unknown
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
    forceDesktopMode: Boolean = false,
    /**
     * 内嵌页面用途，见 [WebPagePurpose]：
     * - `COURSE`：底部显示「执行导入 / 跳到课表」（原有行为）；
     * - `GRADE`：底部显示「识别本页成绩」，用内置通用脚本抓成绩；
     * - `CERT`：纯浏览器（考证查分），底部不显示任何业务按钮；
     * - `EMPTY_CLASSROOM`：底部显示「定位空教室页 / 读取本页空教室」，
     *   用内置通用脚本读取本页空教室查询结果。
     */
    mode: String = WebPagePurpose.COURSE,
    // 导入真的落库成功后才通知调用方：本页（连同它下面的学校列表/适配器选择）可以
    // 从返回栈摘掉了。v4.64.27 之前导入完成后本页一直留在栈上，用户从结果页返回
    // 会回到「已经导完」的教务页，WebView 也连带保活整页网页与 JS 上下文。
    onImportSucceeded: () -> Unit = {},
    viewModel: WebViewModel = koinViewModel()
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    val isDeveloperModeEnabled by viewModel.isDeveloperModeEnabled.collectAsStateWithLifecycle()
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
    val loadErrorGeneric = stringResource(Res.string.webview_load_error_generic)
    val loadErrorDetail = stringResource(Res.string.webview_load_error_detail)
    val loadErrorFmt = stringResource(Res.string.webview_load_error_fmt, "%s")
    val statusEnabled = stringResource(Res.string.status_enabled)
    val statusDisabled = stringResource(Res.string.status_disabled)
    val toastDevToolsEnabled = stringResource(Res.string.toast_devtools_enabled_format, statusEnabled)
    val toastDevToolsDisabled = stringResource(Res.string.toast_devtools_enabled_format, statusDisabled)

    // 用途分流：COURSE = 课表导入（原有行为），GRADE = 成绩识别，CERT = 纯浏览器，
    // EMPTY_CLASSROOM = 空教室查询（定位空教室页 / 读取本页结果）
    val isCourseMode = mode == WebPagePurpose.COURSE
    val isGradeMode = mode == WebPagePurpose.GRADE
    val isEmptyClassroomMode = mode == WebPagePurpose.EMPTY_CLASSROOM
    val toastGradeNoResult = stringResource(Res.string.grade_import_no_result)
    val toastEmptyNoResult = stringResource(Res.string.empty_classroom_no_result)
    val toastEmptyLocateFound = stringResource(Res.string.empty_classroom_locate_found)
    val toastEmptyLocateNotFound = stringResource(Res.string.empty_classroom_locate_not_found)
    val toastEmptyCopied = stringResource(Res.string.empty_classroom_copied)
    val emptyDialogTitle = stringResource(Res.string.empty_classroom_dialog_title)
    val emptyDialogHint = stringResource(Res.string.empty_classroom_dialog_hint)
    val emptyCopyText = stringResource(Res.string.empty_classroom_copy)
    val emptyCloseText = stringResource(Res.string.action_close)
    // 座位数模板（如「%1$d 人」）：不带参数取回原始模板，交给 data.parser 拼行
    val emptyCapacityPattern = stringResource(Res.string.empty_classroom_capacity)
    val statusReadingEmpty = stringResource(Res.string.empty_classroom_scanning)

    var currentUrl by remember { mutableStateOf(initialUrl ?: "about:blank") }
    var inputUrl by remember { mutableStateOf(if (startedEmpty) "" else (initialUrl ?: "")) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var pageTitle by remember { mutableStateOf(if (startedEmpty) titleEnterUrl else titleLoading) }
    // 加载看门狗计数器：每次发起加载（onSearch / 重试 / 初始）时 +1，重新计时

    var expanded by remember { mutableStateOf(false) }
    var isDesktopMode by remember { mutableStateOf(forceDesktopMode) }
    var isEditingUrl by remember { mutableStateOf(startedEmpty) }
    var isDevToolsEnabled by remember { mutableStateOf(false) }
    var showCourseTablePicker by remember { mutableStateOf(false) }
    // 一次导入的运行状态：Running 期间禁用「执行导入」，避免重复注入脚本造成并发落库
    var importRunState by remember { mutableStateOf<ImportRunState>(ImportRunState.Idle) }
    // 已通过前置校验并读入内存的适配脚本源码，选定课表后直接注入，不再二次读盘
    var pendingAdapterJsCode by remember { mutableStateOf<String?>(null) }
    // 成绩识别运行状态：Running 期间禁用按钮，避免重复注入扫描脚本
    var gradeScanRunning by remember { mutableStateOf(false) }
    // 空教室读取运行状态与结果：结果只在内存里（弹窗展示 + 一键复制），不落库
    var emptyScanRunning by remember { mutableStateOf(false) }
    var emptyRooms by remember { mutableStateOf<List<EmptyClassroomRoom>>(emptyList()) }
    var showEmptyRooms by remember { mutableStateOf(false) }
    // 加载失败的应用内错误提示（v4.64.26）：平台层会回传失败原因，此前这里把回调吞成空实现，
    // Android 上加载失败只会留下一片空白 WebView，用户既不知道发生了什么也没有重试入口。
    var loadErrorMessage by remember { mutableStateOf<String?>(null) }
    val webViewController = rememberWebViewController()

    val coroutineScope = rememberCoroutineScope()
    val courseConversionRepository: CourseConversionRepository = koinInject()
    val gradeRepository: GradeRepository = koinInject()
    val uiEventChannel = remember { Channel<WebUiEvent>(Channel.UNLIMITED) }
    val uiEventsFlow = remember(uiEventChannel) { uiEventChannel.receiveAsFlow() }

    val bridgeHandler = remember(coroutineScope, courseConversionRepository, webViewController) {
        WebBridgeHandler(
            coroutineScope = coroutineScope,
            uiEventChannel = uiEventChannel,
            courseConversionRepository = courseConversionRepository,
            onTaskCompleted = {
                coroutineScope.launch {
                    when (importRunState) {
                        is ImportRunState.Succeeded -> {
                            // 真的落库成功：跳结果页，并把本页（连同它下面的学校列表/适配器选择）
                            // 摘出返回栈 —— 否则用户从结果页返回会回到「已经导完」的教务页，
                            // WebView 还连带保活整页网页与 JS 上下文。
                            if (courseConversionRepository.isSemesterStartDateSet()) {
                                onNavigate(Destination.CourseSchedule)
                            } else {
                                onNavigate(Destination.SemesterSettings)
                            }
                            onImportSucceeded()
                        }
                        // 失败/超时：留在本页，由下方渲染区的失败条给出原因与重试入口。
                        // notifyTaskCompletion 在脚本收尾时一律触发（WebBridgeHandler.kt:495-497
                        // 只把 Running 复位为 Idle），此前失败也会被当作成功跳走。
                        is ImportRunState.Failed -> Unit
                        else -> {
                            // 脚本既没回传成功也没回传失败就直接收尾：没有可展示的原因，
                            // 沿用旧行为跳走，不把用户晾在教务页。
                            if (courseConversionRepository.isSemesterStartDateSet()) {
                                onNavigate(Destination.CourseSchedule)
                            } else {
                                onNavigate(Destination.SemesterSettings)
                            }
                        }
                    }
                }
            },
            evaluateJs = { script, callback ->
                webViewController.evaluateJavascript(script, callback)
            },
            onImportStateChanged = { importRunState = it }
        )
    }

    /**
     * 成绩识别：把内置通用扫描脚本注入当前页面，取回 Base64(JSON) 结果后落库并回到成绩页。
     *
     * 结果为空（页面不是成绩表 / 表结构识别不了）只提示，不落库 —— 用户可直接退回
     * 成绩页用「粘贴导入」兜底，不会因为自动识别失败而卡住。
     */
    val startGradeScan: () -> Unit = {
        if (!gradeScanRunning) {
            gradeScanRunning = true
            webViewController.evaluateJavascript(JS_SCAN_GRADES) { result ->
                coroutineScope.launch {
                    gradeScanRunning = false
                    val scanned = decodeScannedGrades(result)
                    if (scanned.isEmpty()) {
                        ToastManager.show(toastGradeNoResult)
                    } else {
                        // 学校成绩页普遍不在一张表里写学期，统一落到「未标注学期」，
                        // 用户可在成绩页逐条改学期（比猜错学期更安全）
                        val semester = getString(Res.string.grade_semester_unknown)
                        gradeRepository.importGrades(
                            scanned.map { item ->
                                gradeRepository.buildGrade(
                                    semester = semester,
                                    courseName = item.courseName,
                                    credit = item.credit,
                                    scoreText = item.scoreText,
                                    source = Grade.SOURCE_IMPORT,
                                    category = item.category
                                )
                            }
                        )
                        ToastManager.show(getString(Res.string.grade_import_success, scanned.size))
                        onNavigate(Destination.Grade)
                    }
                }
            }
        }
    }

    /**
     * 读取本页空教室：把内置通用空教室识别脚本注入当前页面，取回 Base64(JSON) 结果后
     * 弹窗展示并支持一键复制。
     *
     * 与成绩识别不同，这里**不落库**：空教室是即时信息（换一周就作废），
     * 识别为空只提示，用户可在教务页改条件后直接再读一次，不会写脏本机数据。
     */
    val startEmptyClassroomScan: () -> Unit = {
        if (!emptyScanRunning) {
            emptyScanRunning = true
            webViewController.evaluateJavascript(JS_SCAN_EMPTY_CLASSROOMS) { result ->
                coroutineScope.launch {
                    emptyScanRunning = false
                    val scanned = decodeEmptyClassrooms(result)
                    if (scanned.isEmpty()) {
                        ToastManager.show(toastEmptyNoResult)
                    } else {
                        emptyRooms = scanned
                        showEmptyRooms = true
                        ToastManager.show(getString(Res.string.empty_classroom_found, scanned.size))
                    }
                }
            }
        }
    }

    // 「前置校验适配脚本 → 选课表 → 注入脚本」的唯一入口：导入按钮与失败重试共用，
    // 避免两处各写一遍读盘/校验（重试时重新读一遍脚本，适配脚本被更新过也能立刻生效）。
    fun beginImport() {
        val jsPath = assetJsPath
        if (jsPath == null) {
            ToastManager.show(toastNoManualImport)
            return
        }
        // 前置校验：先确认适配脚本确实读得到，再让用户去选课表，
        // 避免「选完课表才发现脚本不存在」的倒置体验
        val jsFilePath = viewModel.filesDir / "repo" / "schools" / "resources" / jsPath
        if (!viewModel.fileSystem.exists(jsFilePath)) {
            ToastManager.show(toastImportNotFoundFmt.replace("%s", jsFilePath.toString()))
            return
        }
        try {
            pendingAdapterJsCode = viewModel.fileSystem.read(jsFilePath) { readUtf8() }
            showCourseTablePicker = true
        } catch (e: Exception) {
            ToastManager.show(toastLoadImportFailedFmt.replace("%s", e.message ?: ""))
        }
    }

    val handleBackAction: () -> Unit = {
        if (loadErrorMessage != null) {
            // 加载失败是全屏覆盖（见下方渲染区）：此刻「返回」的语义是离开这个出错的页面，
            // 而不是去操作被盖住、看不见的网页历史。
            onBack()
        } else if (isEditingUrl) {
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
            loadErrorMessage = null
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = appSpacing().pageHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isCourseMode) {
                            Button(
                                onClick = { beginImport() },
                                enabled = assetJsPath != null && importRunState !is ImportRunState.Running,
                                modifier = Modifier.weight(1f)
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
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(Res.string.action_navigate_to_timetable))
                            }
                        }

                        if (isGradeMode) {
                            Button(
                                onClick = startGradeScan,
                                enabled = !gradeScanRunning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(Res.string.grade_import_recognize))
                            }
                        }

                        if (isEmptyClassroomMode) {
                            OutlinedButton(
                                onClick = {
                                    webViewController.evaluateJavascript(JS_NAVIGATE_TO_EMPTY_CLASSROOM) { result ->
                                        when (result?.trim('"')) {
                                            "found" -> ToastManager.show(toastEmptyLocateFound)
                                            "notfound" -> ToastManager.show(toastEmptyLocateNotFound)
                                            else -> Unit
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(Res.string.empty_classroom_locate))
                            }

                            Button(
                                onClick = startEmptyClassroomScan,
                                enabled = !emptyScanRunning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(Res.string.empty_classroom_scan))
                            }
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
                    loadErrorMessage = if (description.isBlank()) {
                        loadErrorGeneric
                    } else {
                        loadErrorFmt.replace("%s", description)
                    }
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


            if ((isCourseMode && importRunState is ImportRunState.Running) ||
                (isGradeMode && gradeScanRunning) ||
                (isEmptyClassroomMode && emptyScanRunning)
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = appSpacing().pageHorizontal, vertical = appSpacing().listGap),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemedLoadingIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = if (isEmptyClassroomMode) statusReadingEmpty else toastExecutingImport,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = appType().body),
                        color = appColors().textSecondary
                    )
                }
            }

            // X2 三态：导入失败（脚本报错 / 落库失败 / 无响应超时）→ 常驻失败条 + 重新导入。
            // 此前失败只留一条一闪而过的 toast（CONFLATED，还会被跳转顶掉），原因不留痕、
            // 也没有重试入口。这里刻意用底部条而不是全屏覆盖：看门狗超时**不代表脚本没在跑**
            // （WebBridgeHandler.kt:143-148 刻意保留 importTableId，等晚到的落库），
            // 网页保持可见可交互，晚到的成功也会由状态变化自动把这条顶掉。
            (importRunState as? ImportRunState.Failed)?.let { failed ->
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = appSpacing().pageHorizontal, vertical = appSpacing().listGap),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = failed.reason,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = appType().body),
                        color = appColors().textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            // 复位运行状态后重走「选课表 → 注入脚本」；脚本源码重新读一遍，
                            // 适配脚本在两次尝试之间被更新过也能立刻生效
                            importRunState = ImportRunState.Idle
                            beginImport()
                        }
                    ) {
                        Text(stringResource(Res.string.action_retry))
                    }
                }
            }

            // X2 三态：错误 → AppErrorState（v4.64.26）。盖住白屏 WebView，给出原因与重试入口。
            loadErrorMessage?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    AppErrorState(
                        hint = "$message\n$loadErrorDetail",
                        fillScreen = true,
                        onRetry = {
                            loadErrorMessage = null
                            webViewController.reload()
                        }
                    )
                }
            }

            if (showCourseTablePicker && assetJsPath != null) {
                CourseTablePickerDialog(
                    title = stringResource(Res.string.dialog_title_select_table_for_import),
                    onDismissRequest = {
                        showCourseTablePicker = false
                        pendingAdapterJsCode = null
                    },
                    onTableSelected = { selectedTable ->
                        showCourseTablePicker = false
                        val tableId = selectedTable.id
                        val jsCode = pendingAdapterJsCode
                        pendingAdapterJsCode = null

                        if (jsCode != null) {
                            bridgeHandler.setImportTableId(tableId)

                            webViewController.executeScript(buildImportScript(tableId, jsCode))

                            // 不再发 toast：底部状态条已经用同一句「正在执行导入脚本…」常驻显示，
                            // 两条同文案提示会互相覆盖（ToastManager 是 CONFLATED 语义）
                        }
                    }
                )
            }
            if (showEmptyRooms) {
                EmptyClassroomResultDialog(
                    title = emptyDialogTitle,
                    hint = emptyDialogHint,
                    rows = emptyRooms.map { formatEmptyClassroomLine(it, emptyCapacityPattern) },
                    copyText = emptyCopyText,
                    closeText = emptyCloseText,
                    onCopy = {
                        copyToClipboard(formatEmptyClassroomText(emptyRooms, emptyCapacityPattern))
                        ToastManager.show(toastEmptyCopied)
                    },
                    onDismissRequest = { showEmptyRooms = false }
                )
            }
            WebDialogHost(uiEvents = uiEventsFlow)


        }
    }
}

/**
 * 「识别本页成绩」脚本回传的成绩条目（字段名与注入脚本里的 JSON 键一一对应）。
 */
@Serializable
private data class ScannedGrade(
    val courseName: String = "",
    val credit: Double? = null,
    val scoreText: String = "",
    val category: String? = null
)

private val scannedGradeJson = Json { ignoreUnknownKeys = true }

/**
 * 解析注入脚本回传的 `Base64(JSON)` 成绩列表。
 *
 * 为什么走 Base64：`evaluateJavascript` 回调拿到的是**再 JSON 编码一次**的字符串
 * （脚本返回 JSON 字符串时内部引号会被转义），直接 `trim('"')` 解不出嵌套引号；
 * 让脚本先编码成单行安全字符串，原生侧解一次即可。
 * 结果为空 / 解码失败 / 当前页不是成绩表，一律返回空列表，由调用方提示用户手动录入。
 */
@OptIn(ExperimentalEncodingApi::class)
private fun decodeScannedGrades(raw: String?): List<ScannedGrade> {
    val base64 = raw?.trim()?.trim('"')?.trim().orEmpty()
    if (base64.isEmpty()) return emptyList()
    return try {
        scannedGradeJson.decodeFromString<List<ScannedGrade>>(Base64.decode(base64).decodeToString())
    } catch (e: Exception) {
        emptyList()
    }
}

private val scannedEmptyRoomJson = Json { ignoreUnknownKeys = true }

/**
 * 解析注入脚本回传的 `Base64(JSON)` 空教室列表（编码理由同 [decodeScannedGrades]）。
 * 结果为空 / 解码失败 / 当前页不是空教室结果表，一律返回空列表，由调用方提示用户。
 */
@OptIn(ExperimentalEncodingApi::class)
private fun decodeEmptyClassrooms(raw: String?): List<EmptyClassroomRoom> {
    val base64 = raw?.trim()?.trim('"')?.trim().orEmpty()
    if (base64.isEmpty()) return emptyList()
    return try {
        scannedEmptyRoomJson.decodeFromString<List<EmptyClassroomRoom>>(
            Base64.decode(base64).decodeToString()
        )
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 空教室识别结果弹窗：列表展示 + 一键复制全部。
 *
 * 用弹窗而不是新页面：空教室是「看一眼、复制给同学」的一次性信息，
 * 复制完即关，不必在导航栈里多留一层。
 */
@Composable
private fun EmptyClassroomResultDialog(
    title: String,
    hint: String,
    rows: List<String>,
    copyText: String,
    closeText: String,
    onCopy: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = appType().body),
                    color = appColors().textSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = appType().body),
                            color = appColors().textPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppDialogActions(
                confirmText = copyText,
                onConfirm = onCopy,
                dismissText = closeText,
                onDismiss = onDismissRequest
            )
        }
    )
}
