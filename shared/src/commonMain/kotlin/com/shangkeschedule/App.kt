package com.shangkeschedule

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import shangkeschedule.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppToastHost
import com.shangkeschedule.ui.components.ThemedLoadingIndicator
import androidx.compose.ui.Alignment
import shangkeschedule.shared.generated.resources.webview_semester_prompt_title
import shangkeschedule.shared.generated.resources.webview_semester_prompt_message
import shangkeschedule.shared.generated.resources.webview_semester_prompt_later
import shangkeschedule.shared.generated.resources.action_go_to_settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.shangkeschedule.data.repository.CourseConversionRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.shangkeschedule.data.model.StartScreen
import com.shangkeschedule.ui.agenda.AgendaScreen
import com.shangkeschedule.ui.schedule.WeeklyScheduleScreen
import com.shangkeschedule.ui.schoolselection.list.AdapterSelectionScreen
import com.shangkeschedule.ui.schoolselection.list.SchoolSelectionListScreen
import com.shangkeschedule.ui.schoolselection.web.WebViewScreen
import com.shangkeschedule.ui.settings.SettingsScreen
import com.shangkeschedule.ui.settings.SemesterSettingsScreen
import com.shangkeschedule.ui.settings.CoupleScheduleSettingsScreen
import com.shangkeschedule.ui.settings.SettingsViewModel
import com.shangkeschedule.ui.settings.additional.LanguageSettingScreen
import com.shangkeschedule.ui.settings.additional.MoreOptionsScreen
import com.shangkeschedule.ui.settings.additional.OpenSourceLicensesScreen
import com.shangkeschedule.ui.settings.backup.BackupScreen
import com.shangkeschedule.ui.settings.conversion.CourseTableConversionScreen
import com.shangkeschedule.ui.settings.course.AddEditCourseScreen
import com.shangkeschedule.ui.settings.coursemanagement.CourseInstanceListScreen
import com.shangkeschedule.ui.settings.coursemanagement.CourseNameListScreen
import com.shangkeschedule.ui.settings.coursetables.ManageCourseTablesScreen
import com.shangkeschedule.ui.settings.import.ExcelImportScreen
import com.shangkeschedule.ui.settings.import.FileImportHubScreen
import com.shangkeschedule.ui.settings.import.JsonFileImportScreen
import com.shangkeschedule.ui.settings.import.TextFileImportScreen
import com.shangkeschedule.ui.settings.import.TextImportHubScreen
import com.shangkeschedule.ui.settings.import.TextImportScreen
import com.shangkeschedule.data.parser.TextImportFormat
import com.shangkeschedule.ui.settings.notification.NotificationSettingsScreen
import com.shangkeschedule.ui.settings.quickactions.delete.QuickDeleteScreen
import com.shangkeschedule.ui.settings.quickactions.tweaks.TweakScheduleScreen
import com.shangkeschedule.ui.settings.appearance.AppearanceSettingsScreen
import com.shangkeschedule.ui.settings.appearance.ThemeSettingsScreen
import com.shangkeschedule.ui.settings.appearance.ScheduleStyleSettingsScreen
import com.shangkeschedule.ui.settings.appearance.CourseColorSettingsScreen
import com.shangkeschedule.ui.settings.appearance.PersonalizedDisplayScreen
import com.shangkeschedule.ui.settings.appearance.GlassBlurScreen
import com.shangkeschedule.ui.settings.appearance.AnimationSettingsScreen
import com.shangkeschedule.ui.settings.appearance.NextCardSettingsScreen
import com.shangkeschedule.ui.settings.profile.ProfileInfoScreen
import com.shangkeschedule.ui.settings.time.TimeSlotManagementScreen
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.NavMotionMode
import com.shangkeschedule.ui.theme.ShangKeScheduleTheme
import com.shangkeschedule.ui.today.TodayScheduleScreen
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    val viewModel: SettingsViewModel = koinViewModel()
    // 门控收窄（v3.54.0）：根部就绪态/起始页单独订阅，DataStore 无关写入不再触达根组合；
    // 主题设置仍经 uiState 传入 ShangKeScheduleTheme（主题树内部自行消费）
    val gate by viewModel.startGate.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (gate.isReady) {
        ShangKeScheduleTheme(settings = state.appSettings) {
            val startDest = remember(gate.startScreen) {
                when (gate.startScreen) {
                    StartScreen.COURSE_SCHEDULE -> Destination.CourseSchedule
                    StartScreen.TODAY_SCHEDULE -> Destination.TodaySchedule
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                AppNavigation(startDestination = startDest)
                // 全局反馈横幅（v3.54.0）：ToastManager.show 的主题化应用内呈现
                AppToastHost()
            }
        }
    } else {
        // 冷启动 DB 初始化期间的加载占位（v3.54.0）：不再是无内容白/黑屏
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ThemedLoadingIndicator()
            }
        }
    }
}

@Composable
fun AppNavigation(startDestination: Destination) {
    val backStack = rememberNavBackStack(
        configuration = navSavedStateConfig,
        startDestination
    )

    // ── per-tab 导航栈（v3.55.0）──────────────────────────────────────────────
    // 单条 backStack 内按「段」管理：每个一级 Tab 的段（根 entry + 它推入的子页）
    // 连续存放，且**选中 Tab 的段恒为列表尾部**。切 Tab = 把目标段整体搬到尾部：
    // entry 的 key 从不离开列表，SaveableStateHolderNavEntryDecorator 的
    // removeState 对账（DecoratedNavEntriesKt.PrepareBackStack）因此不会触发，
    // 各 Tab 的滚动位置 / 子页栈 / ViewModel 全部原地保留；返回只弹当前段内的
    // 子页，到根即止（不再跨 Tab 弹回）。
    val onNavigate: (Destination) -> Unit = remember(backStack) {
        { dest ->
            if (dest.isMainScreen) {
                val rootIndex = backStack.indexOfFirst { it == dest }
                if (rootIndex < 0) {
                    // 该 Tab 首次到访：根入栈成为新尾段
                    backStack.add(dest)
                } else {
                    val segEnd = tabSegmentEndIndex(backStack, rootIndex)
                    if (segEnd == backStack.size) {
                        // 已是选中 Tab：再点底栏图标 = 弹回该 Tab 根（保留旧习惯；
                        // 根本身在顶时循环体不执行，与旧行为一致）
                        while (backStack.lastIndex > rootIndex) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                    } else {
                        // 切 Tab：目标段从中间原位取出后追加到尾部。原子快照内完成，
                        // 组合不会观察到「段已被移除」的中间态。
                        val segment = backStack.subList(rootIndex, segEnd).toList()
                        Snapshot.withMutableSnapshot {
                            backStack.subList(rootIndex, segEnd).clear()
                            backStack.addAll(segment)
                        }
                    }
                }
            } else {
                if (backStack.lastOrNull() != dest) {
                    backStack.add(dest)
                }
            }
        }
    }

    val onBack: () -> Unit = remember(backStack) {
        {
            // 只弹当前 Tab 段内的子页：主屏根只作段起点入栈（不变量），
            // 栈顶是根即到段底——不跨 Tab 弹回；栈只剩起点根时 NavDisplay
            // 的返回处理本就未启用（size == 1），系统默认行为不受影响。
            //
            // 已知副作用（装机验收项）：栈中存在 ≥2 个 Tab 段时，返回键/预测性返回
            // 手势在 Tab 根被吞——系统无法用返回退出 App（旧行为会跨 Tab 弹回直至退出）；
            // 且 Nav3 的 predictivePopTransitionSpec 仍会预览「上一段尾页」，
            // 松手 commit 后无出栈动作（预览与结果不一致）。如需恢复可退到段底后
            // 交还系统处理，但那会重新引入跨 Tab 返回。
            // 空栈防御：不变量下不可达（起点根恒在），仅作兜底。
            if (backStack.isNotEmpty() && backStack.lastOrNull() !is Destination.MainDestination) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
    }

    // v3.26.0 动效收口 + v3.43.0 主题分档：导航转场时长/曲线/形态读全局动效令牌与主题档位，
    // 关掉「导航转场」分组 ⇒ 直接瞬切（无转场动画）。
    //
    // 形态（《交互动效审查_三主题》P1）：
    // - 通透 SLIDE     ：整屏横推 + 尾随视差，且改为**物理弹簧**（松手自然收束，可中途反向）；
    // - 柔绘 FADE_UP   ：新页淡入 + 6dp 上浮，零横向位移（横移会打断晕染画面的连续性）；
    // - 书卷 LAYER_PUSH：新页自右缘 14dp 淡入、旧页原地仅降 6% 不透明度（留白边距必须稳定）。
    val motion = LocalAppMotion.current
    val navMode = motion.profile.navMode
    val navAnimEnabled = motion.isEnabled(AnimationGroup.NAV_TRANSITION)
    val navDurationMs = motion.tokens.navDurationMs
    val navEasing = motion.tokens.navEasing
    val navTrail = motion.tokens.navTrailFraction
    val navOffsetPx = with(LocalDensity.current) { motion.tokens.navOffsetDp.roundToPx() }

    val slideAnimSpec = remember(motion) {
        if (navMode == NavMotionMode.SLIDE) {
            // 通透：物理弹簧（零过冲、刚度中低）——比 tween 更接近 iOS push/pop 的收束手感
            spring<IntOffset>(
                dampingRatio = 1f,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = IntOffset(1, 1)
            )
        } else {
            tween<IntOffset>(navDurationMs, easing = navEasing)
        }
    }
    val fadeAnimSpec = remember(motion) {
        if (navMode == NavMotionMode.SLIDE) {
            // 通透：淡入淡出与位移共用同参数物理弹簧（v3.54.0）——此前位移走弹簧、
            // 透明度走 tween，两者收束时长天然不同步（位移未落定 alpha 已到 1）
            spring<Float>(
                dampingRatio = 1f,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.001f
            )
        } else {
            tween<Float>(navDurationMs, easing = navEasing)
        }
    }

    // 退场（淡出）单独一套：慢进快出——旧页不再陪新页一起走 600ms。
    val navExitAnimSpec = remember(motion) {
        tween<Float>(motion.tokens.navExitDurationMs, easing = motion.tokens.navExitEasing)
    }

    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        transitionSpec = {
            val fromMain = initialState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false
            val toMain = targetState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false

            if ((fromMain && toMain) || !navAnimEnabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val (enter, exit) = when (navMode) {
                    // 通透：整屏横推 + 1/3 尾随视差
                    NavMotionMode.SLIDE ->
                        (slideInHorizontally(initialOffsetX = { it }, animationSpec = slideAnimSpec) +
                            fadeIn(animationSpec = fadeAnimSpec)) to
                            (slideOutHorizontally(
                                targetOffsetX = { -(it * navTrail).toInt() },
                                animationSpec = slideAnimSpec
                            ) + fadeOut(animationSpec = navExitAnimSpec))

                    // 柔绘：新页淡入 + 上浮，旧页慢速降透明度后淡出（零横移）
                    NavMotionMode.FADE_UP ->
                        (slideInVertically(
                            initialOffsetY = { navOffsetPx },
                            animationSpec = slideAnimSpec
                        ) + fadeIn(animationSpec = fadeAnimSpec)) to
                            fadeOut(animationSpec = navExitAnimSpec, targetAlpha = 0.85f)

                    // 书卷：纸页层叠——新页自右缘 14dp 淡入，旧页原地仅降 6% 不透明度
                    NavMotionMode.LAYER_PUSH ->
                        (slideInHorizontally(
                            initialOffsetX = { navOffsetPx },
                            animationSpec = slideAnimSpec
                        ) + fadeIn(animationSpec = fadeAnimSpec)) to
                            fadeOut(animationSpec = navExitAnimSpec, targetAlpha = 0.94f)
                }
                // 主页面（承载玻璃底栏的那一屏）不参与转场动画：底栏「不动不淡」，
                // 二级页从主页面之上滑入 / 向主页面之外滑出（对齐 iOS tab bar 的观感）。
                (if (toMain) EnterTransition.None else enter) togetherWith
                    (if (fromMain) ExitTransition.None else exit)
            }
        },
        popTransitionSpec = {
            val fromMain = initialState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false
            val toMain = targetState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false

            if ((fromMain && toMain) || !navAnimEnabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val (enter, exit) = when (navMode) {
                    NavMotionMode.SLIDE ->
                        (slideInHorizontally(
                            initialOffsetX = { -(it * navTrail).toInt() },
                            animationSpec = slideAnimSpec
                        ) + fadeIn(animationSpec = fadeAnimSpec)) to
                            (slideOutHorizontally(targetOffsetX = { it }, animationSpec = slideAnimSpec) +
                                fadeOut(animationSpec = navExitAnimSpec))

                    NavMotionMode.FADE_UP ->
                        (slideInVertically(
                            initialOffsetY = { -navOffsetPx },
                            animationSpec = slideAnimSpec
                        ) + fadeIn(animationSpec = fadeAnimSpec)) to
                            fadeOut(animationSpec = navExitAnimSpec)

                    NavMotionMode.LAYER_PUSH ->
                        fadeIn(animationSpec = fadeAnimSpec) to
                            (slideOutHorizontally(
                                targetOffsetX = { navOffsetPx },
                                animationSpec = slideAnimSpec
                            ) + fadeOut(animationSpec = navExitAnimSpec))
                }
                // 同 push：主页面（底栏那一屏）不参与转场 ⇒ 底栏在返回时也不淡入 / 不位移。
                (if (toMain) EnterTransition.None else enter) togetherWith
                    (if (fromMain) ExitTransition.None else exit)
            }
        },
        predictivePopTransitionSpec = {
            // 与 popTransitionSpec 同口径：关掉「导航转场」分组 ⇒ 预测性返回也瞬切。
            // 通透档走物理弹簧，手势中途反向时弹簧会自然重定向并收束。
            val fromMain = initialState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false
            val toMain = targetState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false
            if (!navAnimEnabled || (fromMain && toMain)) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val (enter, exit) = when (navMode) {
                    NavMotionMode.SLIDE ->
                        (slideInHorizontally(
                            initialOffsetX = { -(it * navTrail).toInt() },
                            animationSpec = slideAnimSpec
                        ) + fadeIn(animationSpec = fadeAnimSpec)) to
                            (slideOutHorizontally(targetOffsetX = { it }, animationSpec = slideAnimSpec) +
                                fadeOut(animationSpec = navExitAnimSpec))

                    NavMotionMode.FADE_UP, NavMotionMode.LAYER_PUSH ->
                        fadeIn(animationSpec = fadeAnimSpec) to
                            fadeOut(animationSpec = navExitAnimSpec)
                }
                // 手势返回时主页面同样不动不淡（底栏保持原样，仅二级页滑走）。
                (if (toMain) EnterTransition.None else enter) togetherWith
                    (if (fromMain) ExitTransition.None else exit)
            }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        )
    ) { key ->
        val destination = key as Destination

        NavEntry(
            key = key,
            metadata = metadata {
                put(ShangKeNavMetadata.IsMainScreenKey, destination.isMainScreen)
            }
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                ScreenContent(
                    targetDest = destination,
                    onNavigate = onNavigate,
                    onBack = onBack
                )
            }
        }
    }

    // Tab 根接管系统返回（v3.56.0）：栈顶为主屏根且栈中还有其他段时，吞掉返回但不做
    // 任何事。两个目的：①维持「Tab 根不跨 Tab 返回、不退出应用」的既定行为；
    // ②屏蔽 NavDisplay 的预测性返回预览——否则手势会先闪现上一 Tab 段尾页、松手又
    // 无动作（预览与结果不一致，真机上与系统自带手势动画相冲突，装机反馈已确认）。
    // 注册在 NavDisplay 之后（同链后注册者优先），子页返回不受影响，仍走正常弹栈动画。
    // 栈只剩起点根时（size == 1）不接管：NavDisplay 本就未启用返回处理，交还系统
    // 默认行为（退出应用），与历史版本一致。
    val tabRootBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = tabRootBackState,
        isBackEnabled = backStack.size > 1 && backStack.lastOrNull() is Destination.MainDestination,
        onBackCompleted = { /* Tab 根：吞掉返回 */ }
    )
}

/**
 * [backStack] 中自 [rootIndex]（某一级 Tab 段的根）起的段尾边界：
 * 下一个主屏根的位置（其后无主屏根则为列表尾）。
 * 依赖不变量：主屏根只作为段起点入栈，段内其余 entry 均为二级页。
 */
private fun tabSegmentEndIndex(backStack: List<NavKey>, rootIndex: Int): Int {
    val nextRootOffset = backStack.drop(rootIndex + 1)
        .indexOfFirst { (it as? Destination)?.isMainScreen == true }
    return if (nextRootOffset < 0) backStack.size else rootIndex + 1 + nextRootOffset
}

@Composable
fun ScreenContent(
    targetDest: Destination,
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val courseConversionRepository: CourseConversionRepository = koinInject()
    var showSemesterStartPrompt by remember { mutableStateOf(false) }

    // 文件/文本/Excel 导入成功后：若当前课表未设置开学日期，弹窗引导去学期设置
    fun handleImportSuccess() {
        scope.launch {
            if (courseConversionRepository.isSemesterStartDateSet()) {
                onNavigate(Destination.ManageCourseTables)
            } else {
                showSemesterStartPrompt = true
            }
        }
    }

    when (targetDest) {
        Destination.CourseSchedule -> WeeklyScheduleScreen(onNavigate, onBack)
        Destination.Settings -> SettingsScreen(onNavigate, onBack)
        Destination.TodaySchedule -> TodayScheduleScreen(onNavigate, onBack)
        Destination.Schedule -> AgendaScreen(onNavigate, onBack)
        is Destination.TimeSlotSettings -> TimeSlotManagementScreen(onBack, targetDest.targetCourseTableId)
        Destination.SemesterSettings -> SemesterSettingsScreen(onBack)
        Destination.CoupleScheduleSettings -> CoupleScheduleSettingsScreen(onNavigate, onBack)
        Destination.ManageCourseTables -> ManageCourseTablesScreen(onBack, onNavigate)
        Destination.SchoolSelectionListScreen -> SchoolSelectionListScreen(onNavigate, onBack)
        Destination.CourseTableConversion -> CourseTableConversionScreen(onNavigate, onBack)
        Destination.NotificationSettings -> NotificationSettingsScreen(onBack)
        Destination.MoreOptions -> MoreOptionsScreen(onNavigate, onBack)
        Destination.OpenSourceLicenses -> OpenSourceLicensesScreen(onBack)
        Destination.TweakSchedule -> TweakScheduleScreen(onBack)
        Destination.CourseManagementList -> CourseNameListScreen(onNavigate, onBack)
        Destination.AppearanceSettings -> AppearanceSettingsScreen(onBack, onNavigate)
        Destination.ThemeSettings -> ThemeSettingsScreen(onBack)
        Destination.ProfileInfo -> ProfileInfoScreen(onBack)
        Destination.ScheduleStyleSettings -> ScheduleStyleSettingsScreen(onBack)
        Destination.PersonalizedDisplay -> PersonalizedDisplayScreen(onBack, onNavigate)
        Destination.CourseColorSettings -> CourseColorSettingsScreen(onBack)
        Destination.GlassBlurSettings -> GlassBlurScreen(onBack)
        Destination.AnimationSettings -> AnimationSettingsScreen(onBack)
        Destination.NextCardSettings -> NextCardSettingsScreen(onBack)
        Destination.QuickDelete -> QuickDeleteScreen(onBack)
        Destination.BackupAndRestore -> BackupScreen(onBack)
        Destination.LanguageSettings -> LanguageSettingScreen(onBack)

        // 导入分类二级页
        Destination.FileImportHub -> FileImportHubScreen(onNavigate, onBack)
        Destination.ExcelImport -> ExcelImportScreen(onBack, onImportSuccess = { handleImportSuccess() })
        Destination.JsonFileImport -> JsonFileImportScreen(onBack, onImportSuccess = { handleImportSuccess() })
        is Destination.TextFileImport -> TextFileImportScreen(
            onBack,
            onImportSuccess = { handleImportSuccess() },
            forcedFormat = TextImportFormat.fromName(targetDest.format)
        )
        Destination.TextImportHub -> TextImportHubScreen(onNavigate, onBack)
        is Destination.TextImportFormatPage -> TextImportScreen(
            onBack,
            onImportSuccess = { handleImportSuccess() },
            format = TextImportFormat.fromName(targetDest.format)
        )

        is Destination.AdapterSelection -> AdapterSelectionScreen(
            onNavigate, onBack, targetDest.schoolId, targetDest.schoolName, targetDest.categoryNumber, targetDest.resourceFolder
        )
        is Destination.WebView -> WebViewScreen(
            onNavigate, onBack, targetDest.initialUrl, targetDest.assetJsPath, targetDest.forceDesktopMode
        )
        is Destination.AddEditCourse -> AddEditCourseScreen(
            onBack, targetDest.courseId, targetDest.targetCourseTableId
        )
        is Destination.CourseManagementDetail -> CourseInstanceListScreen(
            targetDest.courseName, onBack, onNavigate
        )
    }

    if (showSemesterStartPrompt) {
        AppAlertDialog(
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