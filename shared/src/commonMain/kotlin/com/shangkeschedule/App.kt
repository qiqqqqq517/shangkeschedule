package com.shangkeschedule

import shangkeschedule.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.shangkeschedule.data.repository.CourseConversionRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
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
    val state by viewModel.uiState.collectAsState()

    if (state.isReady) {
        ShangKeScheduleTheme(settings = state.appSettings) {
            val startDest = remember(state.appSettings.startScreen) {
                when (state.appSettings.startScreen) {
                    StartScreen.COURSE_SCHEDULE -> Destination.CourseSchedule
                    StartScreen.TODAY_SCHEDULE -> Destination.TodaySchedule
                }
            }
            AppNavigation(startDestination = startDest)
        }
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {}
    }
}

@Composable
fun AppNavigation(startDestination: Destination) {
    val backStack = rememberNavBackStack(
        configuration = navSavedStateConfig,
        startDestination
    )

    val onNavigate: (Destination) -> Unit = remember(backStack) {
        { dest ->
            if (dest.isMainScreen) {
                if (backStack.lastOrNull() != dest) {
                    // P2-5 切 Tab 状态保持：若目标主屏根已在栈中（此前到访过），
                    // 弹回至它而不是清空重建，保留其 LazyColumn 滚动/子导航等组合状态；
                    // 仅当目标主屏尚未入栈时才追加一条新根。
                    val existingRootIndex = backStack.indexOfFirst { it == dest }
                    if (existingRootIndex >= 0) {
                        while (backStack.lastIndex > existingRootIndex) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                    } else {
                        backStack.add(dest)
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
            if (backStack.size > 1) {
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
        tween<Float>(navDurationMs, easing = navEasing)
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