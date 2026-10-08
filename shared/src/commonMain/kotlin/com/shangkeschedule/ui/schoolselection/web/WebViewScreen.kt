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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
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
import com.shangkeschedule.data.db.main.CurriculumCourse
import com.shangkeschedule.data.db.main.Grade
import com.shangkeschedule.data.parser.EmptyClassroomRoom
import com.shangkeschedule.data.parser.formatEmptyClassroomLine
import com.shangkeschedule.data.parser.formatEmptyClassroomText
import com.shangkeschedule.data.repository.CourseConversionRepository
import com.shangkeschedule.data.repository.CurriculumRepository
import com.shangkeschedule.data.repository.GradeRepository
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.model.CreditRequirement
import com.shangkeschedule.tool.AppLog
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
import shangkeschedule.shared.generated.resources.action_use_current_url
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
import shangkeschedule.shared.generated.resources.study_import_no_adapter
import shangkeschedule.shared.generated.resources.study_import_no_result
import shangkeschedule.shared.generated.resources.study_import_reading
import shangkeschedule.shared.generated.resources.study_import_recognize
import shangkeschedule.shared.generated.resources.study_import_success
import shangkeschedule.shared.generated.resources.study_import_success_with_courses
import shangkeschedule.shared.generated.resources.study_scan_timeout
import shangkeschedule.shared.generated.resources.study_page_not_found
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
    // v4.72.0：原先的 `showAddressBarToggleButton = startedEmpty || isDeveloperModeEnabled`
    // 已删除 —— 顶栏的地址栏编辑按钮现在对**所有用户、所有教务用途**常驻可见，
    // 不再有条件分支。放开的原因与风险见顶栏 actions 里「进入编辑态」处的注释。

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
    val isStudyMode = mode == WebPagePurpose.STUDY
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
    val toastStudyNoAdapter = stringResource(Res.string.study_import_no_adapter)
    val toastStudyNoResult = stringResource(Res.string.study_import_no_result)
    val toastStudyTimeout = stringResource(Res.string.study_scan_timeout)
    val toastStudyPageNotFound = stringResource(Res.string.study_page_not_found)
    val statusReadingStudy = stringResource(Res.string.study_import_reading)

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
    // 适配脚本源码：与课表导入共用同一个 assetJsPath，但成绩 / 空教室 / 学业三个
    // 用途**在打开页面时就读进内存**（导入是点击后才读），因为钩子探测要在用户
    // 点按钮时立刻可用，不能让读盘失败卡在点击路径上。
    var adapterJsCode by remember { mutableStateOf<String?>(null) }
    // 学业识别（培养方案学分要求）运行状态
    var studyScanRunning by remember { mutableStateOf(false) }
    /**
     * 已点了「学业情况」入口、等页面加载完再读（v4.75.3）。
     *
     * 点击菜单后页面还在跳转，此刻读 DOM 必定读到 0 条 —— 这正是此前「导入不进去」
     * 的直接原因。`onPageLoaded` 消费掉它并调钩子。
     */
    var pendingStudyRescan by remember { mutableStateOf(false) }
    // 回落用的脚本引用：钩子结果为空 / 钩子抛错时要能立刻走通用脚本，而通用脚本
    // 又需要用到挂在下面的状态与仓库实例。用 ref 而不是把逻辑内联进回调，
    // 是为了让「钩子失败」与「没有钩子」两条路径共用同一段回落代码。
    val runGenericGradeScanRef = remember { mutableStateOf<((String?) -> Unit)?>(null) }
    val runGenericEmptyClassroomScanRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    val commitEmptyClassroomsRef = remember { mutableStateOf<suspend (List<EmptyClassroomRoom>) -> Unit>({}) }
    LaunchedEffect(assetJsPath) {
        val jsPath = assetJsPath ?: return@LaunchedEffect
        val jsFilePath = viewModel.filesDir / "repo" / "schools" / "resources" / jsPath
        adapterJsCode = runCatching {
            if (viewModel.fileSystem.exists(jsFilePath)) {
                viewModel.fileSystem.read(jsFilePath) { readUtf8() }
            } else {
                null
            }
        }.getOrNull()
    }
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
    // 学业钩子还可能带回培养方案**课程清单**（v4.75.0）
    val curriculumRepository: CurriculumRepository = koinInject()
    // 学业情况钩子回传的培养方案学分要求要写进应用设置（与学业情况页同源）
    val appSettingsRepository: AppSettingsRepository = koinInject()
    val uiEventChannel = remember { Channel<WebUiEvent>(Channel.UNLIMITED) }
    val uiEventsFlow = remember(uiEventChannel) { uiEventChannel.receiveAsFlow() }

    // 成绩落库：三个调用点（钩子回调、通用回落、钩子失败回落）都在协程里，
    // 且本函数声明在它们之前，因此直接调用即可，不需要 ref 转发。
    suspend fun commitScannedGrades(
        items: List<ScannedGrade>,
        defaultSemester: String
    ) {
        gradeRepository.importGrades(
            items.map { item ->
                gradeRepository.buildGrade(
                    semester = item.semester?.trim().takeUnless { it.isNullOrEmpty() } ?: defaultSemester,
                    courseName = item.courseName,
                    credit = item.credit,
                    scoreText = item.scoreText,
                    source = Grade.SOURCE_IMPORT,
                    // 本校绩点：有就带上，汇总页优先用它，不再拿内置换算表硬算
                    gradePoint = item.gradePoint,
                    category = item.category
                )
            }
        )
    }

    // 适配钩子投递结果的处理器：handler 是 remember 出来的、构造时就得拿到回调，
    // 而回调里要用到的状态（成绩落库 / 弹窗 / 导航）在每次重组都可能变化。
    // 这里用 rememberUpdatedState 持有**最新的那一份**，既避免重建 handler
    // （重建会丢掉导入会话状态），又保证回调不读到旧闭包。
    val onAdapterScanDeliveredState by rememberUpdatedState(
        newValue = { action: String, base64Json: String ->
            when (action) {
                AdapterScanActions.GRADES -> {
                    coroutineScope.launch {
                        gradeScanRunning = false
                        val scanned = decodeScannedGrades(base64Json)
                        if (scanned.isEmpty()) {
                            // 钩子返回空（页面没打开成绩页 / 学校接口变了）→ 回落通用脚本，
                            // 而不是直接报「没识别到」：通用表头解析往往仍能救回来。
                            runGenericGradeScanRef.value?.invoke("钩子返回空")
                        } else {
                            commitScannedGrades(scanned, getString(Res.string.grade_semester_unknown))
                            ToastManager.show(getString(Res.string.grade_import_success, scanned.size))
                            onNavigate(Destination.Grade)
                        }
                    }
                }

                AdapterScanActions.EMPTY_CLASSROOMS -> {
                    coroutineScope.launch {
                        val scanned = decodeEmptyClassrooms(base64Json)
                        if (scanned.isEmpty()) {
                            runGenericEmptyClassroomScanRef.value?.invoke()
                        } else {
                            commitEmptyClassroomsRef.value(scanned)
                        }
                    }
                }

                AdapterScanActions.STUDY -> {
                    coroutineScope.launch {
                        studyScanRunning = false
                        val payload = decodeScannedStudy(base64Json)
                        // v4.75.0：学业钩子现在还可能带回**培养方案课程清单**，
                        // 因此「拿到课程但没拿到学分要求」也该算成功，不能只判 requirements。
                        if (payload == null ||
                            (payload.requirements.isEmpty() && payload.courses.isEmpty())
                        ) {
                            ToastManager.show(toastStudyNoResult)
                        } else {
                            appSettingsRepository.mutateCreditRequirements { current ->
                                // 合并而不是覆盖：用户可能已经手工填过某些类别的要求，
                                // 学校培养方案里同类别以学校为准，其余保留用户的设置。
                                val incoming = payload.requirements
                                    .filter { it.category.isNotBlank() }
                                val incomingKeys = incoming.map { it.category.trim() }.toSet()
                                val kept = current.filter { it.category.trim() !in incomingKeys }
                                kept + incoming.map {
                                    CreditRequirement(
                                        category = it.category.trim(),
                                        requiredCredits = it.requiredCredits
                                            .coerceIn(0.0, CreditRequirement.MAX_REQUIRED_CREDITS),
                                        requiredCourses = it.requiredCourses
                                            ?.takeIf { count -> count > 0 }
                                            ?.coerceAtMost(CreditRequirement.MAX_REQUIRED_COURSES)
                                    )
                                }
                            }
                            // 课程清单是可选能力：抓到了就导入（按课程名去重），没抓到不影响学分要求落库
                            val courses = payload.courses.filter { it.courseName.isNotBlank() }
                            if (courses.isNotEmpty()) {
                                curriculumRepository.importCourses(
                                    courses.map {
                                        curriculumRepository.buildCourse(
                                            courseName = it.courseName,
                                            category = it.category,
                                            credit = it.credit,
                                            suggestedTerm = it.suggestedTerm,
                                            source = CurriculumCourse.SOURCE_IMPORT
                                        )
                                    }
                                )
                            }
                            ToastManager.show(
                                if (courses.isEmpty()) {
                                    getString(Res.string.study_import_success, payload.requirements.size)
                                } else {
                                    getString(
                                        Res.string.study_import_success_with_courses,
                                        payload.requirements.size,
                                        courses.size
                                    )
                                }
                            )
                            onNavigate(Destination.StudyProgress)
                        }
                    }
                }
            }
        }
    )
    val onAdapterScanFailedState by rememberUpdatedState(
        newValue = { action: String ->
            when (action) {
                AdapterScanActions.GRADES -> runGenericGradeScanRef.value?.invoke("钩子抛错")
                AdapterScanActions.EMPTY_CLASSROOMS -> runGenericEmptyClassroomScanRef.value?.invoke()
                // 学业没有通用回落：钩子失败只能如实告知
                AdapterScanActions.STUDY -> {
                    studyScanRunning = false
                    ToastManager.show(toastStudyNoResult)
                }
                else -> Unit
            }
        }
    )

    /**
     * 扫描超时（v4.75.2）：与「读了但为空」分开提示。
     *
     * 超时几乎总是「教务页还没加载出内容 / 没登录 / 适配脚本没注入成功」，
     * 提示用户去确认这些，而不是让人以为是「这页没有培养方案信息」。
     */
    val onAdapterScanTimeoutState by rememberUpdatedState(
        newValue = { action: String ->
            studyScanRunning = false
            when (action) {
                AdapterScanActions.STUDY -> ToastManager.show(toastStudyTimeout)
                else -> Unit
            }
        }
    )

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
            onImportStateChanged = { importRunState = it },
            onAdapterScanDelivered = { action, base64Json ->
                onAdapterScanDeliveredState(action, base64Json)
            },
            onAdapterScanFailed = { action -> onAdapterScanFailedState(action) },
            onAdapterScanTimeout = { action -> onAdapterScanTimeoutState(action) }
        )
    }

    /**
     * 成绩识别：**优先调用适配脚本钩子** `window.shangkeScanGrades()`，
     * 钩子缺失 / 抛错 / 返回空时回落到内置通用扫描脚本。
     *
     * 为什么要有钩子：通用脚本只能按表头猜列，而适配脚本知道本校用的哪个接口——
     * 南通大学（正方 V9）可以直接按学期调 `xskccjxxhcx` 接口，拿回**真实学期**与
     * 课程性质，比在 DOM 上猜可靠得多；也顺带解决了「成绩页要逐页翻」的问题。
     *
     * 结果为空（页面不是成绩表 / 钩子与通用脚本都认不出）只提示，不落库 ——
     * 用户可直接退回成绩页用「粘贴导入」兜底，不会因为自动识别失败而卡住。
     */
    val runGenericGradeScan: (String?) -> Unit = { fallbackReason ->
        // fallbackReason 只为调试留痕：钩子失败的原因已在 reportAdapterError 里记过日志
        if (fallbackReason != null) {
            AppLog.w("WebViewScreen", "成绩钩子不可用（$fallbackReason），回落通用脚本")
        }
        webViewController.evaluateJavascript(JS_SCAN_GRADES) { result ->
            coroutineScope.launch {
                gradeScanRunning = false
                val scanned = decodeScannedGrades(result)
                if (scanned.isEmpty()) {
                    ToastManager.show(toastGradeNoResult)
                } else {
                    commitScannedGrades(scanned, getString(Res.string.grade_semester_unknown))
                    ToastManager.show(getString(Res.string.grade_import_success, scanned.size))
                    onNavigate(Destination.Grade)
                }
            }
        }
    }

    val startGradeScan: () -> Unit = {
        if (!gradeScanRunning) {
            gradeScanRunning = true
            val adapterCode = adapterJsCode
            if (adapterCode == null) {
                runGenericGradeScan("无适配脚本")
            } else {
                webViewController.evaluateJavascript(
                    buildAdapterHookProbeScript(adapterCode, AdapterHooks.SCAN_GRADES)
                ) { probe ->
                    if (probe?.trim('"') == "present") {
                        bridgeHandler.beginAdapterScan(AdapterScanActions.GRADES)
                        webViewController.executeScript(
                            buildAdapterHookInvokeScript(
                                adapterCode,
                                AdapterHooks.SCAN_GRADES,
                                AdapterScanActions.GRADES
                            )
                        )
                    } else {
                        runGenericGradeScan("适配脚本未提供钩子")
                    }
                }
            }
        }
    }

    /**
     * 读取本页空教室：**优先调用适配脚本钩子** `window.shangkeScanEmptyClassrooms()`，
     * 钩子缺失 / 抛错 / 返回空时回落到内置通用识别脚本。
     *
     * 钩子能做的事比通用脚本多一整档：通用脚本只能读**用户已经查出来的结果表**，
     * 而适配脚本可以直接按校区 / 楼栋 / 周次 / 节次调本校接口查询（如南通大学
     * 正方 V9 的 `kxjsjxqxx` 接口），用户不必先自己在教务页点一遍查询。
     * 钩子没适配时旧路径完全不变，不构成回归。
     *
     * 与成绩识别不同，这里**不落库**：空教室是即时信息（换一周就作废），
     * 识别为空只提示，用户可在教务页改条件后直接再读一次，不会写脏本机数据。
     */
    suspend fun commitEmptyClassrooms(scanned: List<EmptyClassroomRoom>) {
        emptyScanRunning = false
        if (scanned.isEmpty()) {
            ToastManager.show(toastEmptyNoResult)
        } else {
            emptyRooms = scanned
            showEmptyRooms = true
            ToastManager.show(getString(Res.string.empty_classroom_found, scanned.size))
        }
    }
    LaunchedEffect(commitEmptyClassroomsRef) { commitEmptyClassroomsRef.value = ::commitEmptyClassrooms }

    val runGenericEmptyClassroomScan: () -> Unit = {
        webViewController.evaluateJavascript(JS_SCAN_EMPTY_CLASSROOMS) { result ->
            coroutineScope.launch { commitEmptyClassrooms(decodeEmptyClassrooms(result)) }
        }
    }
    val startEmptyClassroomScan: () -> Unit = {
        if (!emptyScanRunning) {
            emptyScanRunning = true
            val adapterCode = adapterJsCode
            if (adapterCode == null) {
                runGenericEmptyClassroomScan()
            } else {
                webViewController.evaluateJavascript(
                    buildAdapterHookProbeScript(adapterCode, AdapterHooks.SCAN_EMPTY_CLASSROOMS)
                ) { probe ->
                    if (probe?.trim('"') == "present") {
                        bridgeHandler.beginAdapterScan(AdapterScanActions.EMPTY_CLASSROOMS)
                        webViewController.executeScript(
                            buildAdapterHookInvokeScript(
                                adapterCode,
                                AdapterHooks.SCAN_EMPTY_CLASSROOMS,
                                AdapterScanActions.EMPTY_CLASSROOMS
                            )
                        )
                    } else {
                        runGenericEmptyClassroomScan()
                    }
                }
            }
        }
    }

    /**
     * 学业情况：**只走适配脚本钩子** `window.shangkeScanStudy()`。
     *
     * 与成绩 / 空教室不同，这里没有通用回落脚本：培养方案「各类别应修学分」不存在
     * 可猜的通用表结构，硬猜出来的数字比没有更糟（用户会照着错的要求规划选课）。
     * 因此钩子缺失时明确提示「本校暂未适配」，不做假动作。
     *
     * 钩子只回传**要求**，已修学分由本机成绩表现算（见 GradeRepository.computeStudyProgress）。
     */
    /** 真正调用适配脚本钩子读培养方案（已确认当前页有数据时）。 */
    fun runStudyHook(adapterCode: String) {
        webViewController.evaluateJavascript(
            buildAdapterHookProbeScript(adapterCode, AdapterHooks.SCAN_STUDY)
        ) { probe ->
            if (probe?.trim('"') == "present") {
                bridgeHandler.beginAdapterScan(AdapterScanActions.STUDY)
                webViewController.executeScript(
                    buildAdapterHookInvokeScript(
                        adapterCode,
                        AdapterHooks.SCAN_STUDY,
                        AdapterScanActions.STUDY
                    )
                )
            } else {
                studyScanRunning = false
                ToastManager.show(toastStudyNoAdapter)
            }
        }
    }

    /**
     * v4.75.3：**先走到学业情况页，再读**。
     *
     * App 打开的是学校配置的首页（`import_url`），而钩子要在学业情况页里
     * 找培养方案数据（实测：首页 `p.title1` 命中 0、学业页命中 14）。
     * 此前没有这一步 ⇒ 用户在首页点「读取」必然读到 0 条，表现为「导入不进去」。
     *
     * `found` → 点击入口后等 `onPageLoaded` 再读（`pendingStudyRescan` 承接）；
     * `notfound` → 页面不是学业页且找不到入口，如实提示让用户自己去地址栏进。
     */
    val startStudyScan: () -> Unit = {
        if (!studyScanRunning) {
            val adapterCode = adapterJsCode
            if (adapterCode == null) {
                ToastManager.show(toastStudyNoAdapter)
            } else {
                studyScanRunning = true
                webViewController.evaluateJavascript(JS_NAVIGATE_TO_STUDY) { navResult ->
                    when (navResult?.trim('"')) {
                        // 已在学业情况页：直接读，不要再走一轮「点菜单 → 等加载」
                        "here" -> runStudyHook(adapterCode)
                        "found" -> pendingStudyRescan = true
                        else -> {
                            studyScanRunning = false
                            ToastManager.show(toastStudyPageNotFound)
                        }
                    }
                }
            }
        }
    }

    /**
     * 页面加载完成后，若有「等学业页加载好再读」的待办，就在这里调钩子。
     *
     * 与 [startStudyScan] 分开：前者负责发起跳转，这里负责在正确的时机消费。
     * `onPageFinished` 早于本文档提及的「进度 1.0」但晚于 DOM 可读，所以放在这里。
     */
    val onPageLoaded: () -> Unit = {
        if (pendingStudyRescan) {
            pendingStudyRescan = false
            val adapterCode = adapterJsCode
            if (adapterCode == null || !studyScanRunning) {
                studyScanRunning = false
            } else {
                runStudyHook(adapterCode)
            }
        }
    }

    // 把回落逻辑挂到 ref 上：钩子路径（在 bridgeHandler 回调里）与无钩子路径共用同一份实现
    runGenericGradeScanRef.value = runGenericGradeScan
    runGenericEmptyClassroomScanRef.value = runGenericEmptyClassroomScan

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
            // v4.72.0：取消编辑**保留用户刚输入的内容**。
            // 此前这里无条件把 inputUrl 覆盖成 WebView 当前地址，用户输了一半按返回键，
            // 输入被静默冲掉；地址栏放开给普通用户后，这个「输了白输」更容易踩到。
            isEditingUrl = false
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


    // 注：v4.73.0 曾在此实现「页面加载完成后自动导航到课表页」，但按用户要求已**整体撤回**，
    // 连带撤掉了为此新增的 `onPageLoadFinished` 钩子（commonMain/androidMain/jvmMain 三处
    // 的签名与实现都已复原）。撤回理由：教务导入涉及账号密码与验证码，页面何时跳转、
    // 点开哪个菜单必须完全交给用户决定，App 不应替用户自动点击或跳转登录/教务页面。
    // 「一键导航到课表」仍是底部栏的**手动**按钮，行为与撤回前完全一致。
    //
    // 后续若还要在此加自动化，只允许做**只读**能力（如「是否停在登录页」的提示）；
    // 任何自动跳转 / 自动点击一律不得引入。

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
                                // v4.72.0：原先只挡了 http:// 与 https:// 两种空壳，用户输入
                                // "https:// " 这类（只有协议头）时按钮可点但必然加载失败。
                                // 改成「协议头之后还得有主机名」才允许提交。
                                enabled = inputUrl.trim().let { t ->
                                    t.isNotBlank() && !t.startsWith("about:") &&
                                            !Regex("^https?:///?$", RegexOption.IGNORE_CASE).matches(t)
                                }
                            ) {
                                Icon(vectorResource(Res.drawable.arrow_forward_24px), contentDescription = stringResource(Res.string.a11y_load))
                            }
                        } else {
                            // v4.72.0：地址栏编辑对所有用户、所有教务用途（课表 / 成绩 / 空教室）
                            // 常驻可用。此前它被限制在「通用平台入口（startedEmpty）」或
                            // 「开发者模式」—— 选具体学校后按钮直接消失，用户卡在索引给的
                            // 地址上却无处可改；而索引每所学校只能记一个入口，实际使用中
                            // 校内直连 / 校外 WebVPN / 门户跳转后教务 往往只对其中一条通。
                            //
                            // ⚠️ 前提：改地址**不换脚本**。改的只是入口地址，注入的仍是所选
                            // 学校的适配脚本（assetJsPath 由选校页决定，与本页地址无关）。
                            // 用户若把地址改到另一套教务系统上，脚本多半对不上，导入会以
                            // 「未找到导入入口 / 未解析出课程」明确失败，而非静默导入错数据。
                            IconButton(onClick = {
                                isEditingUrl = true
                                // 预填「所选学校/平台的注册地址」（initialUrl），而不是 WebView
                                // 当前地址：教务系统登录后往往已被框架页接管（如
                                // /jsxsd/framework/xsMainV.htmlx），预填当前地址会让用户误以为
                                // 那是学校入口地址，照着改反而把地址改坏。
                                // 当前地址仍可通过「⋯ → 使用当前地址」一键带入。
                                inputUrl = (initialUrl ?: "")
                                    .takeIf { it.isNotBlank() && it != "about:blank" }
                                    ?: run {
                                        val rawUrl = webViewController.currentUrl
                                        if (rawUrl.isBlank() || rawUrl == "about:blank") "" else rawUrl
                                    }
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

                            // v4.72.0：地址栏预填的是「注册地址」，用户若想接着当前页
                            // （比如已经从门户跳转到了教务子页，想固化这个可用地址），
                            // 给一条一键带入的入口，不必手抄长 URL。
                            TelegramMenuItem(
                                icon = vectorResource(Res.drawable.link_24px),
                                text = stringResource(Res.string.action_use_current_url),
                                onClick = {
                                    val rawUrl = webViewController.currentUrl
                                    inputUrl = if (rawUrl.isBlank() || rawUrl == "about:blank") {
                                        inputUrl
                                    } else {
                                        rawUrl
                                    }
                                    isEditingUrl = true
                                    expanded = false
                                    keyboardController?.show()
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
                        if (isStudyMode) {
                            Button(
                                onClick = startStudyScan,
                                enabled = !studyScanRunning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(Res.string.study_import_recognize))
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
                onPageLoaded = onPageLoaded,
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
                (isEmptyClassroomMode && emptyScanRunning) ||
                (isStudyMode && studyScanRunning)
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
                        text = when {
                            isEmptyClassroomMode -> statusReadingEmpty
                            isStudyMode -> statusReadingStudy
                            else -> toastExecutingImport
                        },
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
 *
 * [semester] 只有**适配脚本钩子**会回传（通用脚本一律留空，由调用方落到「未标注学期」）：
 * 本校成绩页的学期信息只有懂本校表结构的钩子拿得到。
 */
@Serializable
private data class ScannedGrade(
    val courseName: String = "",
    val credit: Double? = null,
    val scoreText: String = "",
    val semester: String? = null,
    val category: String? = null,
    /** 本校绩点（v4.75.0）：教务给出的既成事实，优先进汇总口径。 */
    val gradePoint: Double? = null
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

private val scannedEmptyRoomJson = Json { ignoreUnknownKeys = true }/**
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
 * 解析学业情况钩子回传的 `Base64(JSON)` 培养方案学分要求（编码理由同 [decodeScannedGrades]）。
 *
 * 解析失败返回 null：学业钩子**没有通用回落**，调用方据此提示「本校暂未适配」，
 * 而不是把一个半解析的对象写进应用设置。
 */
@OptIn(ExperimentalEncodingApi::class)
private fun decodeScannedStudy(raw: String?): ScannedStudyPayload? {
    val base64 = raw?.trim()?.trim('"')?.trim().orEmpty()
    if (base64.isEmpty()) return null
    return try {
        scannedGradeJson.decodeFromString<ScannedStudyPayload>(
            Base64.decode(base64).decodeToString()
        )
    } catch (e: Exception) {
        null
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
