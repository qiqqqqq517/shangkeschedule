package com.shangkeschedule

import shangkeschedule.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import com.shangkeschedule.ui.components.AppDialogActions
import shangkeschedule.shared.generated.resources.webview_semester_prompt_title
import shangkeschedule.shared.generated.resources.webview_semester_prompt_message
import shangkeschedule.shared.generated.resources.webview_semester_prompt_later
import shangkeschedule.shared.generated.resources.action_go_to_settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
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
import com.shangkeschedule.ui.settings.appearance.PersonalizedDisplayScreen
import com.shangkeschedule.ui.settings.appearance.GlassBlurScreen
import com.shangkeschedule.ui.settings.appearance.AnimationSettingsScreen
import com.shangkeschedule.ui.settings.time.TimeSlotManagementScreen
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.LocalAppMotion
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

    // v3.26.0 动效收口：导航转场时长/缓动读全局动效令牌（LocalAppMotion），
    // 关掉「导航转场」分组 ⇒ 直接瞬切（无转场动画）。
    val motion = LocalAppMotion.current
    val slideAnimSpec = remember(motion) {
        tween<IntOffset>(motion.tokens.navDurationMs, easing = motion.tokens.navEasing)
    }
    val fadeAnimSpec = remember(motion) {
        tween<Float>(motion.tokens.navDurationMs, easing = motion.tokens.navEasing)
    }
    val navAnimEnabled = motion.isEnabled(AnimationGroup.NAV_TRANSITION)

    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        transitionSpec = {
            val fromMain = initialState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false
            val toMain = targetState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false

            if ((fromMain && toMain) || !navAnimEnabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                // Apple HIG 风格 push：新页从右侧滑入 + 淡入，旧页左移 1/3 + 淡出
                // ease-out 曲线确保平滑无过冲，消除「翻页感」
                slideInHorizontally(initialOffsetX = { it }, animationSpec = slideAnimSpec) +
                    fadeIn(animationSpec = fadeAnimSpec) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = slideAnimSpec) +
                    fadeOut(animationSpec = fadeAnimSpec)
            }
        },
        popTransitionSpec = {
            val fromMain = initialState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false
            val toMain = targetState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false

            if ((fromMain && toMain) || !navAnimEnabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                // Apple HIG 风格 pop：旧页向右滑出 + 淡出，上一页从左侧 1/3 滑入 + 淡入
                slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = slideAnimSpec) +
                    fadeIn(animationSpec = fadeAnimSpec) togetherWith
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = slideAnimSpec) +
                    fadeOut(animationSpec = fadeAnimSpec)
            }
        },
        predictivePopTransitionSpec = {
            // 与 popTransitionSpec 同口径：关掉「导航转场」分组 ⇒ 预测性返回也瞬切
            if (!navAnimEnabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = slideAnimSpec) +
                    fadeIn(animationSpec = fadeAnimSpec) togetherWith
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = slideAnimSpec) +
                    fadeOut(animationSpec = fadeAnimSpec)
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
        Destination.TimeSlotSettings -> TimeSlotManagementScreen(onBack)
        Destination.SemesterSettings -> SemesterSettingsScreen(onBack)
        Destination.CoupleScheduleSettings -> CoupleScheduleSettingsScreen(onNavigate, onBack)
        Destination.ManageCourseTables -> ManageCourseTablesScreen(onBack, onNavigate)
        is Destination.SchoolSelectionListScreen -> SchoolSelectionListScreen(onNavigate, onBack, targetDest.isCrushImport)
        Destination.CourseTableConversion -> CourseTableConversionScreen(onNavigate, onBack)
        Destination.NotificationSettings -> NotificationSettingsScreen(onBack)
        Destination.MoreOptions -> MoreOptionsScreen(onNavigate, onBack)
        Destination.OpenSourceLicenses -> OpenSourceLicensesScreen(onBack)
        Destination.TweakSchedule -> TweakScheduleScreen(onBack)
        Destination.CourseManagementList -> CourseNameListScreen(onNavigate, onBack)
        Destination.AppearanceSettings -> AppearanceSettingsScreen(onBack, onNavigate)
        Destination.ThemeSettings -> ThemeSettingsScreen(onBack)
        Destination.ScheduleStyleSettings -> ScheduleStyleSettingsScreen(onBack)
        Destination.PersonalizedDisplay -> PersonalizedDisplayScreen(onBack, onNavigate)
        Destination.GlassBlurSettings -> GlassBlurScreen(onBack)
        Destination.AnimationSettings -> AnimationSettingsScreen(onBack)
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
            onNavigate, onBack, targetDest.schoolId, targetDest.schoolName, targetDest.categoryNumber, targetDest.resourceFolder, targetDest.isCrushImport
        )
        is Destination.WebView -> WebViewScreen(
            onNavigate, onBack, targetDest.initialUrl, targetDest.assetJsPath, targetDest.isCrushImport, targetDest.forceDesktopMode
        )
        is Destination.AddEditCourse -> AddEditCourseScreen(
            onBack, targetDest.courseId
        )
        is Destination.CourseManagementDetail -> CourseInstanceListScreen(
            targetDest.courseName, onBack, onNavigate
        )
    }

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