package com.shangkeschedule.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangkeschedule.Destination
import com.shangkeschedule.data.model.DualColor
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.ui.components.AdaptiveNavigationScaffold
import com.shangkeschedule.ui.components.DatePickerModal
import com.shangkeschedule.ui.components.NativeNumberPicker
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.desc_couple_schedule
import shangkeschedule.shared.generated.resources.desc_crush_course_color
import shangkeschedule.shared.generated.resources.desc_self_course_color
import shangkeschedule.shared.generated.resources.item_couple_schedule
import shangkeschedule.shared.generated.resources.item_crush_course_color
import shangkeschedule.shared.generated.resources.item_self_course_color
import shangkeschedule.shared.generated.resources.check_24px
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.date_format_year_month_day
import shangkeschedule.shared.generated.resources.day_of_week_monday
import shangkeschedule.shared.generated.resources.day_of_week_sunday
import shangkeschedule.shared.generated.resources.desc_course_conversion
import shangkeschedule.shared.generated.resources.desc_course_management
import shangkeschedule.shared.generated.resources.desc_current_week_manual
import shangkeschedule.shared.generated.resources.desc_first_day_of_week
import shangkeschedule.shared.generated.resources.desc_manage_course_tables
import shangkeschedule.shared.generated.resources.desc_more_options
import shangkeschedule.shared.generated.resources.desc_notification_settings
import shangkeschedule.shared.generated.resources.desc_personalization
import shangkeschedule.shared.generated.resources.desc_quick_actions
import shangkeschedule.shared.generated.resources.desc_set_start_date
import shangkeschedule.shared.generated.resources.desc_show_non_current_week
import shangkeschedule.shared.generated.resources.desc_show_weekends
import shangkeschedule.shared.generated.resources.desc_theme_settings
import shangkeschedule.shared.generated.resources.desc_time_slot_customization
import shangkeschedule.shared.generated.resources.desc_total_weeks
import shangkeschedule.shared.generated.resources.desc_update_repo
import shangkeschedule.shared.generated.resources.dialog_title_manual_set_week
import shangkeschedule.shared.generated.resources.dialog_title_select_total_weeks
import shangkeschedule.shared.generated.resources.dialog_title_set_first_day_of_week
import shangkeschedule.shared.generated.resources.item_course_conversion
import shangkeschedule.shared.generated.resources.item_course_management
import shangkeschedule.shared.generated.resources.item_current_week
import shangkeschedule.shared.generated.resources.item_first_day_of_week
import shangkeschedule.shared.generated.resources.item_more_options
import shangkeschedule.shared.generated.resources.item_personalization
import shangkeschedule.shared.generated.resources.item_quick_actions
import shangkeschedule.shared.generated.resources.item_set_start_date
import shangkeschedule.shared.generated.resources.item_show_non_current_week
import shangkeschedule.shared.generated.resources.item_show_weekends
import shangkeschedule.shared.generated.resources.item_time_slot_customization
import shangkeschedule.shared.generated.resources.item_total_weeks
import shangkeschedule.shared.generated.resources.item_update_repo
import shangkeschedule.shared.generated.resources.theme_settings_title
import shangkeschedule.shared.generated.resources.more_horiz_24px
import shangkeschedule.shared.generated.resources.section_title_advanced_features
import shangkeschedule.shared.generated.resources.section_title_general_settings
import shangkeschedule.shared.generated.resources.status_current_week_format
import shangkeschedule.shared.generated.resources.status_not_set
import shangkeschedule.shared.generated.resources.status_set_start_date_first
import shangkeschedule.shared.generated.resources.status_total_weeks_format
import shangkeschedule.shared.generated.resources.title_course_notification_settings
import shangkeschedule.shared.generated.resources.title_manage_course_tables
import shangkeschedule.shared.generated.resources.title_schedule_settings
import shangkeschedule.shared.generated.resources.title_vacation

private val SETTING_PADDING = 16.dp
private val SECTION_SPACING = 16.dp
private val ITEM_SPACING = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    AdaptiveNavigationScaffold(
        currentDestination = Destination.Settings,
        onTabSelected = { dest -> onNavigate(dest) }
    ) { navPadding ->
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(Res.string.title_schedule_settings)) },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
            if (!uiState.isReady) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding))
            } else {
                val appSettings = uiState.appSettings
                val courseTableConfig = uiState.courseConfig
                val displayCurrentWeek = uiState.currentWeek

                val showWeekends = courseTableConfig?.showWeekends ?: false
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

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = SETTING_PADDING),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
                    contentPadding = PaddingValues(bottom = navPadding.calculateBottomPadding() + 16.dp)
                ) {
                    item {
                        GeneralSettingsSection(
                            showNonCurrentWeek = appSettings.showNonCurrentWeekCourses,
                            onShowNonCurrentWeekChanged = { isChecked -> viewModel.onShowNonCurrentWeekChanged(isChecked) },
                            showWeekends = showWeekends,
                            onShowWeekendsChanged = { isChecked -> viewModel.onShowWeekendsChanged(isChecked) },
                            semesterStartDate = semesterStartDate,
                            semesterTotalWeeks = semesterTotalWeeks,
                            firstDayOfWeekInt = firstDayOfWeekInt,
                            displayCurrentWeek = displayCurrentWeek,
                            onSemesterStartDateClick = { showDatePickerModal = true },
                            onSemesterTotalWeeksClick = { showTotalWeeksDialog = true },
                            onManualWeekClick = { showManualWeekDialog = true },
                            onFirstDayOfWeekClick = { showFirstDayOfWeekDialog = true },
                            onQuickActionsClick = { onNavigate(Destination.QuickActions) },
                            coupleScheduleEnabled = appSettings.coupleScheduleEnabled,
                            onCoupleScheduleEnabledChanged = { viewModel.onCoupleScheduleEnabledChanged(it) },
                            selfCourseColorIndex = appSettings.selfCourseColorIndex,
                            crushCourseColorIndex = appSettings.crushCourseColorIndex,
                            onSelfCourseColorIndexChanged = { viewModel.onSelfCourseColorIndexChanged(it) },
                            onCrushCourseColorIndexChanged = { viewModel.onCrushCourseColorIndexChanged(it) }
                        )
                    }
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    item {
                        AdvancedSettingsSection(onNavigate = onNavigate)
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
        }
    }
}

/**
 * 通用设置卡片
 */
@Composable
private fun GeneralSettingsSection(
    showNonCurrentWeek: Boolean,
    onShowNonCurrentWeekChanged: (Boolean) -> Unit,
    showWeekends: Boolean,
    onShowWeekendsChanged: (Boolean) -> Unit,
    semesterStartDate: LocalDate?,
    semesterTotalWeeks: Int,
    firstDayOfWeekInt: Int,
    displayCurrentWeek: Int?,
    onSemesterStartDateClick: () -> Unit,
    onSemesterTotalWeeksClick: () -> Unit,
    onManualWeekClick: () -> Unit,
    onFirstDayOfWeekClick: () -> Unit,
    onQuickActionsClick: () -> Unit,
    coupleScheduleEnabled: Boolean,
    onCoupleScheduleEnabledChanged: (Boolean) -> Unit,
    selfCourseColorIndex: Int,
    crushCourseColorIndex: Int,
    onSelfCourseColorIndexChanged: (Int) -> Unit,
    onCrushCourseColorIndexChanged: (Int) -> Unit
) {
    var showSelfColorDialog by remember { mutableStateOf(false) }
    var showCrushColorDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(SETTING_PADDING),
            verticalArrangement = Arrangement.spacedBy(ITEM_SPACING)
        ) {
            Text(
                stringResource(Res.string.section_title_general_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            SettingItem(
                title = stringResource(Res.string.item_show_non_current_week),
                subtitle = stringResource(Res.string.desc_show_non_current_week)
            ) {
                Switch(checked = showNonCurrentWeek, onCheckedChange = onShowNonCurrentWeekChanged)
            }

            SettingItem(
                title = stringResource(Res.string.item_show_weekends),
                subtitle = stringResource(Res.string.desc_show_weekends)
            ) {
                Switch(checked = showWeekends, onCheckedChange = onShowWeekendsChanged)
            }

            SettingItem(
                title = stringResource(Res.string.item_set_start_date),
                subtitle = stringResource(Res.string.desc_set_start_date),
                onClick = onSemesterStartDateClick
            ) {
                val formattedDate = semesterStartDate?.let {
                    stringResource(
                        Res.string.date_format_year_month_day,
                        it.year.toString(),
                        it.month.number.toString(),
                        it.day.toString()
                    )
                } ?: stringResource(Res.string.status_not_set)

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SettingItem(
                title = stringResource(Res.string.item_total_weeks),
                subtitle = stringResource(Res.string.desc_total_weeks),
                onClick = onSemesterTotalWeeksClick
            ) {
                Text(
                    text = stringResource(Res.string.status_total_weeks_format, semesterTotalWeeks),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SettingItem(
                title = stringResource(Res.string.item_current_week),
                subtitle = stringResource(Res.string.desc_current_week_manual),
                onClick = onManualWeekClick
            ) {
                val weekStatusText = when {
                    semesterStartDate == null -> stringResource(Res.string.status_set_start_date_first)
                    displayCurrentWeek == null -> stringResource(Res.string.title_vacation)
                    else -> stringResource(Res.string.status_current_week_format, displayCurrentWeek)
                }
                Text(
                    text = weekStatusText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SettingItem(
                title = stringResource(Res.string.item_first_day_of_week),
                subtitle = stringResource(Res.string.desc_first_day_of_week),
                onClick = onFirstDayOfWeekClick
            ) {
                val dayText = when (firstDayOfWeekInt) {
                    DayOfWeek.MONDAY.isoDayNumber -> stringResource(Res.string.day_of_week_monday)
                    DayOfWeek.SUNDAY.isoDayNumber -> stringResource(Res.string.day_of_week_sunday)
                    else -> stringResource(Res.string.day_of_week_monday)
                }
                Text(
                    text = dayText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingItem(
                title = stringResource(Res.string.item_couple_schedule),
                subtitle = stringResource(Res.string.desc_couple_schedule)
            ) {
                Switch(checked = coupleScheduleEnabled, onCheckedChange = onCoupleScheduleEnabledChanged)
            }

            if (coupleScheduleEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingItem(
                    title = stringResource(Res.string.item_self_course_color),
                    subtitle = stringResource(Res.string.desc_self_course_color),
                    onClick = { showSelfColorDialog = true }
                ) {
                    ColorPreviewDot(colorIndex = selfCourseColorIndex)
                }

                SettingItem(
                    title = stringResource(Res.string.item_crush_course_color),
                    subtitle = stringResource(Res.string.desc_crush_course_color),
                    onClick = { showCrushColorDialog = true }
                ) {
                    ColorPreviewDot(colorIndex = crushCourseColorIndex)
                }
            }

            SettingItem(
                title = stringResource(Res.string.item_quick_actions),
                subtitle = stringResource(Res.string.desc_quick_actions),
                onClick = onQuickActionsClick
            )
        }
    }

    if (showSelfColorDialog) {
        ColorPickerDialog(
            title = stringResource(Res.string.item_self_course_color),
            selectedIndex = selfCourseColorIndex,
            onDismiss = { showSelfColorDialog = false },
            onSelect = { index ->
                onSelfCourseColorIndexChanged(index)
                showSelfColorDialog = false
            }
        )
    }

    if (showCrushColorDialog) {
        ColorPickerDialog(
            title = stringResource(Res.string.item_crush_course_color),
            selectedIndex = crushCourseColorIndex,
            onDismiss = { showCrushColorDialog = false },
            onSelect = { index ->
                onCrushCourseColorIndexChanged(index)
                showCrushColorDialog = false
            }
        )
    }
}

/**
 * 高级功能卡片
 */
@Composable
private fun AdvancedSettingsSection(onNavigate: (Destination) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(SETTING_PADDING),
            verticalArrangement = Arrangement.spacedBy(ITEM_SPACING)
        ) {
            Text(
                stringResource(Res.string.section_title_advanced_features),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            SettingItem(
                title = stringResource(Res.string.item_course_conversion),
                subtitle = stringResource(Res.string.desc_course_conversion),
                onClick = { onNavigate(Destination.CourseTableConversion) }
            )
            SettingItem(
                title = stringResource(Res.string.title_course_notification_settings),
                subtitle = stringResource(Res.string.desc_notification_settings),
                onClick = { onNavigate(Destination.NotificationSettings) }
            )
            SettingItem(
                title = stringResource(Res.string.title_manage_course_tables),
                subtitle = stringResource(Res.string.desc_manage_course_tables),
                onClick = { onNavigate(Destination.ManageCourseTables) }
            )
            SettingItem(
                title = stringResource(Res.string.item_course_management),
                subtitle = stringResource(Res.string.desc_course_management),
                onClick = { onNavigate(Destination.CourseManagementList) }
            )
            SettingItem(
                title = stringResource(Res.string.item_time_slot_customization),
                subtitle = stringResource(Res.string.desc_time_slot_customization),
                onClick = { onNavigate(Destination.TimeSlotSettings) }
            )
            SettingItem(
                title = stringResource(Res.string.item_personalization),
                subtitle = stringResource(Res.string.desc_personalization),
                onClick = { onNavigate(Destination.StyleSettings) }
            )
            SettingItem(
                title = stringResource(Res.string.theme_settings_title),
                subtitle = stringResource(Res.string.desc_theme_settings),
                onClick = { onNavigate(Destination.ThemeSettings) }
            )
            SettingItem(
                title = stringResource(Res.string.item_update_repo),
                subtitle = stringResource(Res.string.desc_update_repo),
                onClick = { onNavigate(Destination.UpdateRepo) }
            )
            SettingItem(
                title = stringResource(Res.string.item_more_options),
                subtitle = stringResource(Res.string.desc_more_options),
                onClick = { onNavigate(Destination.MoreOptions) },
                icon = vectorResource(Res.drawable.more_horiz_24px)
            )
        }
    }
}

/**
 * 颜色预览圆点。
 * 展示当前选中的课程颜色（浅色背景 + 深色描边），点击可弹出颜色选择器。
 */
@Composable
private fun ColorPreviewDot(colorIndex: Int) {
    val dualColor = ScheduleGridStyle.DEFAULT_COLOR_MAPS.getOrNull(colorIndex) ?: return
    val isDarkTheme = LocalIsDarkTheme.current
    val bgColor = if (isDarkTheme) dualColor.dark else dualColor.light
    val borderColor = if (isDarkTheme) dualColor.light.copy(alpha = 0.7f) else dualColor.dark.copy(alpha = 0.6f)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = CircleShape
            )
    )
}

/**
 * 课程颜色选择对话框。
 * 以 4 列网格展示颜色池，选中项用对勾 + 主色边框标记。
 */
@Composable
private fun ColorPickerDialog(
    title: String,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val colors = ScheduleGridStyle.DEFAULT_COLOR_MAPS
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                colors.chunked(4).forEachIndexed { rowIndex, rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowColors.forEachIndexed { colIndex, dualColor ->
                            val index = rowIndex * 4 + colIndex
                            ColorSwatch(
                                dualColor = dualColor,
                                selected = index == selectedIndex,
                                onClick = { onSelect(index) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

/**
 * 单个颜色色块，用于颜色选择对话框。
 */
@Composable
private fun ColorSwatch(
    dualColor: DualColor,
    selected: Boolean,
    onClick: () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val bgColor = if (isDarkTheme) dualColor.dark else dualColor.light
    val checkColor = if (isDarkTheme) dualColor.light else dualColor.dark
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else dualColor.dark.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = vectorResource(Res.drawable.check_24px),
                contentDescription = null,
                tint = checkColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 封装单个设置项的可组合函数，提高代码复用性
 */
@Composable
private fun SettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector = vectorResource(Res.drawable.chevron_right_24px),
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit = { Icon(icon, contentDescription = null) }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        trailingContent()
    }
}

/**
 * 手动周数选择器对话框
 */
@Composable
fun ManualWeekPickerDialog(
    totalWeeks: Int,
    currentWeek: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    val optionOnVacationText = stringResource(Res.string.title_vacation)

    val weekOptions = listOf(optionOnVacationText) + (1..totalWeeks).map {
        stringResource(Res.string.status_current_week_format, it)
    }

    val initialSelectedValue = when (currentWeek) {
        null -> optionOnVacationText
        else -> stringResource(Res.string.status_current_week_format, currentWeek)
    }

    var dialogSelectedValue by remember { mutableStateOf(initialSelectedValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_title_manual_set_week)) },
        text = {
            NativeNumberPicker(
                values = weekOptions,
                selectedValue = dialogSelectedValue,
                onValueChange = { newValue ->
                    dialogSelectedValue = newValue
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                val weekNumber = if (dialogSelectedValue == optionOnVacationText) {
                    null
                } else {
                    dialogSelectedValue.filter { it.isDigit() }.toIntOrNull()
                }
                onConfirm(weekNumber)
            }) {
                Text(stringResource(Res.string.action_confirm))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

/**
 * 每周起始日选择器对话框
 */
@Composable
fun DayOfWeekPickerDialog(
    initialDayOfWeekInt: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val dayOfWeekMondayText = stringResource(Res.string.day_of_week_monday)
    val dayOfWeekSundayText = stringResource(Res.string.day_of_week_sunday)

    val dayOptionsMap = mapOf(
        dayOfWeekMondayText to DayOfWeek.MONDAY.isoDayNumber,
        dayOfWeekSundayText to DayOfWeek.SUNDAY.isoDayNumber
    )
    val dayOptions = dayOptionsMap.keys.toList()

    val initialSelectedDayText = dayOptionsMap.entries.firstOrNull { it.value == initialDayOfWeekInt }?.key
        ?: dayOfWeekMondayText

    var dialogSelectedText by remember { mutableStateOf(initialSelectedDayText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_title_set_first_day_of_week)) },
        text = {
            NativeNumberPicker(
                values = dayOptions,
                selectedValue = dialogSelectedText,
                onValueChange = { newValue ->
                    dialogSelectedText = newValue
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                val selectedDayInt = dayOptionsMap[dialogSelectedText] ?: DayOfWeek.MONDAY.isoDayNumber
                onConfirm(selectedDayInt)
            }) {
                Text(stringResource(Res.string.action_confirm))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

/**
 * 数字选择器对话框
 */
@Composable
private fun NumberPickerDialog(
    title: String,
    range: IntRange,
    initialValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var dialogSelectedValue by remember { mutableIntStateOf(initialValue.coerceIn(range)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            NativeNumberPicker(
                values = range.toList(),
                selectedValue = initialValue.coerceIn(range),
                onValueChange = { newValue ->
                    dialogSelectedValue = newValue
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(dialogSelectedValue) }) {
                Text(stringResource(Res.string.action_confirm))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}