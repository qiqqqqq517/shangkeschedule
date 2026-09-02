package com.shangkeschedule

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
import com.shangkeschedule.ui.settings.quickactions.QuickActionsScreen
import com.shangkeschedule.ui.settings.quickactions.delete.QuickDeleteScreen
import com.shangkeschedule.ui.settings.quickactions.tweaks.TweakScheduleScreen
import com.shangkeschedule.ui.settings.appearance.AppearanceSettingsScreen
import com.shangkeschedule.ui.settings.time.TimeSlotManagementScreen
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
                    backStack.clear()
                    backStack.add(dest)
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

    val animSpec = remember { tween<IntOffset>(300) }

    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        transitionSpec = {
            val fromMain = initialState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false
            val toMain = targetState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false

            if (fromMain && toMain) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = animSpec) togetherWith
                        slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = animSpec) + fadeOut()
            }
        },
        popTransitionSpec = {
            val fromMain = initialState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false
            val toMain = targetState.metadata[ShangKeNavMetadata.IsMainScreenKey] ?: false

            if (fromMain && toMain) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = animSpec) + fadeIn() togetherWith
                        slideOutHorizontally(targetOffsetX = { it }, animationSpec = animSpec)
            }
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = animSpec) + fadeIn() togetherWith
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = animSpec)
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
        Destination.QuickActions -> QuickActionsScreen(onNavigate, onBack)
        Destination.TweakSchedule -> TweakScheduleScreen(onBack)
        Destination.CourseManagementList -> CourseNameListScreen(onNavigate, onBack)
        Destination.AppearanceSettings -> AppearanceSettingsScreen(onBack)
        Destination.QuickDelete -> QuickDeleteScreen(onBack)
        Destination.BackupAndRestore -> BackupScreen(onBack)
        Destination.LanguageSettings -> LanguageSettingScreen(onBack)
        Destination.TextImport -> TextImportScreen(onBack, onImportSuccess = { handleImportSuccess() })

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
            title = { Text("请设置开学日期") },
            text = { Text("已导入新课表，但尚未设置开学日期。设置开学日期后才能正确显示当前周数与课表高亮。") },
            confirmButton = {
                TextButton(onClick = {
                    showSemesterStartPrompt = false
                    onNavigate(Destination.SemesterSettings)
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showSemesterStartPrompt = false }) { Text("稍后再说") }
            }
        )
    }
}