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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.shangkeschedule.ui.settings.SettingsViewModel
import com.shangkeschedule.ui.settings.additional.LanguageSettingScreen
import com.shangkeschedule.ui.settings.additional.MoreOptionsScreen
import com.shangkeschedule.ui.settings.additional.OpenSourceLicensesScreen
import com.shangkeschedule.ui.settings.backup.BackupScreen
import com.shangkeschedule.ui.settings.contribution.ContributionScreen
import com.shangkeschedule.ui.settings.conversion.CourseTableConversionScreen
import com.shangkeschedule.ui.settings.course.AddEditCourseScreen
import com.shangkeschedule.ui.settings.coursemanagement.CourseInstanceListScreen
import com.shangkeschedule.ui.settings.coursemanagement.CourseNameListScreen
import com.shangkeschedule.ui.settings.coursetables.ManageCourseTablesScreen
import com.shangkeschedule.ui.settings.notification.NotificationSettingsScreen
import com.shangkeschedule.ui.settings.quickactions.QuickActionsScreen
import com.shangkeschedule.ui.settings.quickactions.delete.QuickDeleteScreen
import com.shangkeschedule.ui.settings.quickactions.tweaks.TweakScheduleScreen
import com.shangkeschedule.ui.settings.style.StyleSettingsScreen
import com.shangkeschedule.ui.settings.themesettings.ThemeSettingsScreen
import com.shangkeschedule.ui.settings.time.TimeSlotManagementScreen
import com.shangkeschedule.ui.settings.update.UpdateRepoScreen
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

    val animSpec = tween<IntOffset>(300)

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
    when (targetDest) {
        Destination.CourseSchedule -> WeeklyScheduleScreen(onNavigate, onBack)
        Destination.Settings -> SettingsScreen(onNavigate, onBack)
        Destination.TodaySchedule -> TodayScheduleScreen(onNavigate, onBack)
        Destination.TimeSlotSettings -> TimeSlotManagementScreen(onBack)
        Destination.ManageCourseTables -> ManageCourseTablesScreen(onBack)
        is Destination.SchoolSelectionListScreen -> SchoolSelectionListScreen(onNavigate, onBack, targetDest.isCrushImport)
        Destination.CourseTableConversion -> CourseTableConversionScreen(onNavigate, onBack)
        Destination.NotificationSettings -> NotificationSettingsScreen(onBack)
        Destination.MoreOptions -> MoreOptionsScreen(onNavigate, onBack)
        Destination.OpenSourceLicenses -> OpenSourceLicensesScreen(onBack)
        Destination.UpdateRepo -> UpdateRepoScreen(onBack)
        Destination.QuickActions -> QuickActionsScreen(onNavigate, onBack)
        Destination.TweakSchedule -> TweakScheduleScreen(onBack)
        Destination.ContributionList -> ContributionScreen(onBack)
        Destination.CourseManagementList -> CourseNameListScreen(onNavigate, onBack)
        Destination.StyleSettings -> StyleSettingsScreen(onBack)
        Destination.QuickDelete -> QuickDeleteScreen(onBack)
        Destination.ThemeSettings -> ThemeSettingsScreen(onBack)
        Destination.BackupAndRestore -> BackupScreen(onBack)
        Destination.LanguageSettings -> LanguageSettingScreen(onBack)

        is Destination.AdapterSelection -> AdapterSelectionScreen(
            onNavigate, onBack, targetDest.schoolId, targetDest.schoolName, targetDest.categoryNumber, targetDest.resourceFolder, targetDest.isCrushImport
        )
        is Destination.WebView -> WebViewScreen(
            onNavigate, onBack, targetDest.initialUrl, targetDest.assetJsPath, targetDest.isCrushImport
        )
        is Destination.AddEditCourse -> AddEditCourseScreen(
            onBack, targetDest.courseId
        )
        is Destination.CourseManagementDetail -> CourseInstanceListScreen(
            targetDest.courseName, onBack, onNavigate
        )
    }
}