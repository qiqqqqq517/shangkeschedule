package com.shangkeschedule.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shangkeschedule.ui.components.DatePickerModal
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.date_format_year_month_day
import shangkeschedule.shared.generated.resources.day_of_week_monday
import shangkeschedule.shared.generated.resources.day_of_week_sunday
import shangkeschedule.shared.generated.resources.desc_current_week_manual
import shangkeschedule.shared.generated.resources.desc_first_day_of_week
import shangkeschedule.shared.generated.resources.desc_set_start_date
import shangkeschedule.shared.generated.resources.desc_total_weeks
import shangkeschedule.shared.generated.resources.dialog_title_manual_set_week
import shangkeschedule.shared.generated.resources.dialog_title_select_total_weeks
import shangkeschedule.shared.generated.resources.dialog_title_set_first_day_of_week
import shangkeschedule.shared.generated.resources.item_current_week
import shangkeschedule.shared.generated.resources.item_first_day_of_week
import shangkeschedule.shared.generated.resources.item_set_start_date
import shangkeschedule.shared.generated.resources.item_total_weeks
import shangkeschedule.shared.generated.resources.status_current_week_format
import shangkeschedule.shared.generated.resources.status_not_set
import shangkeschedule.shared.generated.resources.status_set_start_date_first
import shangkeschedule.shared.generated.resources.status_total_weeks_format
import shangkeschedule.shared.generated.resources.title_semester_settings
import shangkeschedule.shared.generated.resources.title_vacation

/**
 * 学期设置二级页。
 *
 * 从设置主页「学期设置」入口进入，承载开学日期、总周数、当前周数、每周起始日等明细设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    if (!uiState.isReady) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.title_semester_settings)) },
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

    val courseTableConfig = uiState.courseConfig
    val displayCurrentWeek = uiState.currentWeek

    val semesterStartDateString = courseTableConfig?.semesterStartDate
    val semesterTotalWeeks = courseTableConfig?.semesterTotalWeeks ?: 20
    val firstDayOfWeekInt = courseTableConfig?.firstDayOfWeek ?: DayOfWeek.MONDAY.isoDayNumber

    val semesterStartDate: LocalDate? = remember(semesterStartDateString) {
        semesterStartDateString?.let {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    var showTotalWeeksDialog by remember { mutableStateOf(false) }
    var showManualWeekDialog by remember { mutableStateOf(false) }
    var showDatePickerModal by remember { mutableStateOf(false) }
    var showFirstDayOfWeekDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.title_semester_settings)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionCard {
                SettingItem(
                    title = stringResource(Res.string.item_set_start_date),
                    subtitle = stringResource(Res.string.desc_set_start_date),
                    onClick = { showDatePickerModal = true }
                ) {
                    val formattedDate = semesterStartDate?.let {
                        stringResource(
                            Res.string.date_format_year_month_day,
                            it.year.toString(),
                            it.month.number.toString(),
                            it.day.toString()
                        )
                    } ?: stringResource(Res.string.status_not_set)
                    Text(text = formattedDate, style = MaterialTheme.typography.bodyMedium)
                }
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_total_weeks),
                    subtitle = stringResource(Res.string.desc_total_weeks),
                    onClick = { showTotalWeeksDialog = true }
                ) {
                    Text(
                        text = stringResource(Res.string.status_total_weeks_format, semesterTotalWeeks),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_current_week),
                    subtitle = stringResource(Res.string.desc_current_week_manual),
                    onClick = { showManualWeekDialog = true }
                ) {
                    val weekStatusText = when {
                        semesterStartDate == null -> stringResource(Res.string.status_set_start_date_first)
                        displayCurrentWeek == null -> stringResource(Res.string.title_vacation)
                        else -> stringResource(Res.string.status_current_week_format, displayCurrentWeek)
                    }
                    Text(text = weekStatusText, style = MaterialTheme.typography.bodyMedium)
                }
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.item_first_day_of_week),
                    subtitle = stringResource(Res.string.desc_first_day_of_week),
                    onClick = { showFirstDayOfWeekDialog = true }
                ) {
                    val dayText = when (firstDayOfWeekInt) {
                        DayOfWeek.MONDAY.isoDayNumber -> stringResource(Res.string.day_of_week_monday)
                        DayOfWeek.SUNDAY.isoDayNumber -> stringResource(Res.string.day_of_week_sunday)
                        else -> stringResource(Res.string.day_of_week_monday)
                    }
                    Text(text = dayText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (showDatePickerModal) {
        DatePickerModal(
            onDateSelected = { selectedDateMillis ->
                viewModel.onSemesterStartDateSelected(selectedDateMillis)
            },
            onDismiss = { showDatePickerModal = false }
        )
    }
    if (showTotalWeeksDialog) {
        NumberPickerDialog(
            title = stringResource(Res.string.dialog_title_select_total_weeks),
            range = 1..30,
            initialValue = semesterTotalWeeks,
            onDismiss = { showTotalWeeksDialog = false },
            onConfirm = { selectedWeeks ->
                viewModel.onSemesterTotalWeeksSelected(selectedWeeks)
                showTotalWeeksDialog = false
            }
        )
    }
    if (showManualWeekDialog) {
        ManualWeekPickerDialog(
            totalWeeks = semesterTotalWeeks,
            currentWeek = displayCurrentWeek,
            onDismiss = { showManualWeekDialog = false },
            onConfirm = { weekNumber ->
                viewModel.onCurrentWeekManuallySet(weekNumber)
                showManualWeekDialog = false
            }
        )
    }
    if (showFirstDayOfWeekDialog) {
        DayOfWeekPickerDialog(
            initialDayOfWeekInt = firstDayOfWeekInt,
            onDismiss = { showFirstDayOfWeekDialog = false },
            onConfirm = { selectedDayInt ->
                viewModel.onFirstDayOfWeekSelected(selectedDayInt)
                showFirstDayOfWeekDialog = false
            }
        )
    }
}
