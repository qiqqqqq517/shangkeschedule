package com.shangkeschedule.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangkeschedule.Destination
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.data.model.DualColor
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.ui.components.AdaptiveNavigationScaffold
import com.shangkeschedule.ui.components.AppCard
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.components.GradientHeroCard
import com.shangkeschedule.ui.components.IconChip
import com.shangkeschedule.ui.components.DatePickerModal
import com.shangkeschedule.ui.components.NativeNumberPicker
import com.shangkeschedule.ui.theme.AccentTone
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.app_name
import shangkeschedule.shared.generated.resources.hero_subtitle
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
import shangkeschedule.shared.generated.resources.desc_appearance_settings
import shangkeschedule.shared.generated.resources.desc_personalization
import shangkeschedule.shared.generated.resources.desc_quick_actions
import shangkeschedule.shared.generated.resources.desc_semester_settings
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
import shangkeschedule.shared.generated.resources.item_appearance_settings
import shangkeschedule.shared.generated.resources.item_personalization
import shangkeschedule.shared.generated.resources.item_quick_actions
import shangkeschedule.shared.generated.resources.item_set_start_date
import shangkeschedule.shared.generated.resources.item_show_non_current_week
import shangkeschedule.shared.generated.resources.item_show_weekends
import shangkeschedule.shared.generated.resources.item_time_slot_customization
import shangkeschedule.shared.generated.resources.item_total_weeks
import shangkeschedule.shared.generated.resources.theme_settings_title
import shangkeschedule.shared.generated.resources.calendar_today_24px
import shangkeschedule.shared.generated.resources.schedule_24px
import shangkeschedule.shared.generated.resources.favorite_24px
import shangkeschedule.shared.generated.resources.palette_24px
import shangkeschedule.shared.generated.resources.info_24px
import shangkeschedule.shared.generated.resources.school_24px
import shangkeschedule.shared.generated.resources.class_24px
import shangkeschedule.shared.generated.resources.edit_24px
import shangkeschedule.shared.generated.resources.section_title_semester_settings
import shangkeschedule.shared.generated.resources.more_horiz_24px
import shangkeschedule.shared.generated.resources.notifications_24px
import shangkeschedule.shared.generated.resources.filter_list_24px
import shangkeschedule.shared.generated.resources.view_week_24px
import shangkeschedule.shared.generated.resources.settings_group_course
import shangkeschedule.shared.generated.resources.settings_group_tools
import shangkeschedule.shared.generated.resources.settings_group_other
import shangkeschedule.shared.generated.resources.settings_group_timetable
import shangkeschedule.shared.generated.resources.settings_group_courses
import shangkeschedule.shared.generated.resources.settings_group_preference
import shangkeschedule.shared.generated.resources.settings_group_about
import shangkeschedule.shared.generated.resources.item_backup_restore
import shangkeschedule.shared.generated.resources.desc_backup_restore
import shangkeschedule.shared.generated.resources.cloud_24px
import shangkeschedule.shared.generated.resources.section_title_advanced_features
import shangkeschedule.shared.generated.resources.section_title_general_settings
import shangkeschedule.shared.generated.resources.status_current_week_format
import shangkeschedule.shared.generated.resources.status_not_set
import shangkeschedule.shared.generated.resources.status_set_start_date_first
import shangkeschedule.shared.generated.resources.status_total_weeks_format
import shangkeschedule.shared.generated.resources.title_course_notification_settings
import shangkeschedule.shared.generated.resources.title_manage_course_tables
import shangkeschedule.shared.generated.resources.nav_settings
import shangkeschedule.shared.generated.resources.title_schedule_settings
import shangkeschedule.shared.generated.resources.title_vacation

// 页面节奏对齐全局 token（v2 规范 §2：pageHorizontal=16 / cardGap=12）
// 注意：已迁移为主题化 token，各 Composable 内用 appSpacing().pageHorizontal / appSpacing().cardGap 读取，
// 保证通透（iOS）主题下自动切换到 Apple HIG 更大留白值。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val uiState by viewModel.uiState.collectAsState()
    val themePreset = LocalThemePreset.current
    val isIosPreset = themePreset == AppThemePreset.IOS
    val isClaudePreset = themePreset == AppThemePreset.CLAUDE
    // 吸顶栏毛玻璃：内容作为 hazeSource，滚动时卡片从半透明玻璃栏后穿过（Telegram 形态）
    val hazeState = rememberHazeState()
    val glassTint = appColors().pageBg.copy(alpha = 0.72f)
    val glassFallback = appColors().pageBg

    AdaptiveNavigationScaffold(
        currentDestination = Destination.Settings,
        onTabSelected = { dest -> onNavigate(dest) }
    ) { navPadding ->
        Scaffold(
            // 书卷主题没有 TopAppBar 吸收滚动量：若仍挂 exitUntilCollapsed 的 nestedScroll 连接，
            // 滚动会被整段吞掉（列表完全无法滑动），故仅在存在顶栏的主题下挂载。
            modifier = if (isClaudePreset) {
                Modifier
            } else {
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            },
            topBar = {
                if (isClaudePreset) {
                    // 书卷主题：无独立 TopAppBar，页面大标题由内容区 ClaudePageHeader 承担
                } else if (isIosPreset) {
                    // iOS 风格：左对齐大标题（对齐设计稿 .nav-bar__title）
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(Res.string.nav_settings),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = appColors().textPrimary
                            )
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.hazeEffect(hazeState) {
                            blurRadius = 16.dp
                            noiseFactor = 0.1f
                            tints = listOf(HazeTint(glassTint))
                            fallbackTint = HazeTint(glassFallback)
                            backgroundColor = Color.Transparent
                        }
                    )
                } else {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(Res.string.nav_settings)) },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.hazeEffect(hazeState) {
                            blurRadius = 16.dp
                            noiseFactor = 0.1f
                            tints = listOf(HazeTint(glassTint))
                            fallbackTint = HazeTint(glassFallback)
                            backgroundColor = Color.Transparent
                        }
                    )
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .padding(horizontal = appSpacing().pageHorizontal),
                verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap),
                // iOS 主题：宽屏（平板/桌面）内容限宽 640dp 居中，对齐 iPad 设置 App 行为
                horizontalAlignment = if (isIosPreset) Alignment.CenterHorizontally else Alignment.Start,
                // 顶部 inset 走 contentPadding：列表内容滚动到吸顶玻璃栏后（顶部不再裁切）
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = navPadding.calculateBottomPadding() + 16.dp
                )
            ) {
                if (isClaudePreset) {
                    // ===== 书卷主题：inset grouped 分组列表（对齐设计稿 profile.html）=====
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                        ) {
                            ClaudePageHeader(
                                title = stringResource(Res.string.nav_settings),
                                subtitle = stringResource(Res.string.hero_subtitle)
                            )
                            ClaudeUserRow(
                                name = stringResource(Res.string.app_name),
                                school = stringResource(Res.string.hero_subtitle),
                                onClick = { onNavigate(Destination.AppearanceSettings) }
                            )
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                        ) {
                            ClaudeGroupLabel(stringResource(Res.string.settings_group_timetable))
                            ClaudeInsetGroup {
                                ClaudeListItem(
                                    title = stringResource(Res.string.item_course_conversion),
                                    icon = vectorResource(Res.drawable.school_24px),
                                    tone = ClaudeCellTone.PURPLE,
                                    onClick = { onNavigate(Destination.CourseTableConversion) }
                                )
                                ClaudeListItem(
                                    title = stringResource(Res.string.section_title_semester_settings),
                                    icon = vectorResource(Res.drawable.calendar_today_24px),
                                    tone = ClaudeCellTone.ORANGE,
                                    showDivider = true,
                                    onClick = { onNavigate(Destination.SemesterSettings) }
                                )
                                ClaudeListItem(
                                    title = stringResource(Res.string.item_time_slot_customization),
                                    icon = vectorResource(Res.drawable.schedule_24px),
                                    tone = ClaudeCellTone.RED,
                                    showDivider = true,
                                    onClick = { onNavigate(Destination.TimeSlotSettings) }
                                )
                                ClaudeListItem(
                                    title = stringResource(Res.string.title_manage_course_tables),
                                    icon = vectorResource(Res.drawable.class_24px),
                                    tone = ClaudeCellTone.OLIVE,
                                    showDivider = true,
                                    onClick = { onNavigate(Destination.ManageCourseTables) }
                                )
                            }
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                        ) {
                            ClaudeGroupLabel(stringResource(Res.string.settings_group_courses))
                            ClaudeInsetGroup {
                                ClaudeListItem(
                                    title = stringResource(Res.string.item_course_management),
                                    icon = vectorResource(Res.drawable.edit_24px),
                                    tone = ClaudeCellTone.MATCHA,
                                    onClick = { onNavigate(Destination.CourseManagementList) }
                                )
                                ClaudeListItem(
                                    title = stringResource(Res.string.item_couple_schedule),
                                    icon = vectorResource(Res.drawable.favorite_24px),
                                    tone = ClaudeCellTone.PINK,
                                    showDivider = true,
                                    onClick = { onNavigate(Destination.CoupleScheduleSettings) }
                                )
                            }
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                        ) {
                            ClaudeGroupLabel(stringResource(Res.string.settings_group_preference))
                            ClaudeInsetGroup {
                                ClaudeListItem(
                                    title = stringResource(Res.string.item_appearance_settings),
                                    icon = vectorResource(Res.drawable.palette_24px),
                                    tone = ClaudeCellTone.ORANGE,
                                    onClick = { onNavigate(Destination.AppearanceSettings) }
                                )
                                ClaudeListItem(
                                    title = stringResource(Res.string.title_course_notification_settings),
                                    icon = vectorResource(Res.drawable.notifications_24px),
                                    tone = ClaudeCellTone.PURPLE,
                                    showDivider = true,
                                    onClick = { onNavigate(Destination.NotificationSettings) }
                                )
                                ClaudeListItem(
                                    title = stringResource(Res.string.item_backup_restore),
                                    icon = vectorResource(Res.drawable.cloud_24px),
                                    tone = ClaudeCellTone.GREEN,
                                    showDivider = true,
                                    onClick = { onNavigate(Destination.BackupAndRestore) }
                                )
                                ClaudeListItem(
                                    title = stringResource(Res.string.item_show_non_current_week),
                                    icon = vectorResource(Res.drawable.filter_list_24px),
                                    tone = ClaudeCellTone.GRAY,
                                    showDivider = true,
                                    trailing = {
                                        AppSwitch(
                                            checked = uiState.appSettings.showNonCurrentWeekCourses,
                                            onCheckedChange = { viewModel.onShowNonCurrentWeekChanged(it) }
                                        )
                                    }
                                )
                                ClaudeListItem(
                                    title = stringResource(Res.string.item_show_weekends),
                                    icon = vectorResource(Res.drawable.view_week_24px),
                                    tone = ClaudeCellTone.AMBER,
                                    showDivider = true,
                                    trailing = {
                                        AppSwitch(
                                            checked = uiState.courseConfig?.showWeekends ?: false,
                                            onCheckedChange = { viewModel.onShowWeekendsChanged(it) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                        ) {
                            ClaudeGroupLabel(stringResource(Res.string.settings_group_about))
                            ClaudeInsetGroup {
                                ClaudeListItem(
                                    title = stringResource(Res.string.item_more_options),
                                    icon = vectorResource(Res.drawable.more_horiz_24px),
                                    tone = ClaudeCellTone.BROWN,
                                    onClick = { onNavigate(Destination.MoreOptions) }
                                )
                            }
                        }
                    }
                } else if (isIosPreset) {
                    // ===== 通透主题（iOS 26）：inset grouped 分组列表 =====
                    // 信息架构（分组数量、分组顺序、每组成员、开关位置）与上面的「书卷」分支
                    // 逐项一致：同样是 4 组【课表 / 课程 / 偏好 / 关于】，条目与跳转目标一一对应。
                    // 差别只在材质与字形——书卷是暖米色分组卡 + 0.5dp 实色描边 + 衬线字体，
                    // 通透是白卡（深色 #1C1C1E）+ 玻璃高光内描边 + SF 系统字体。
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                        ) {
                            IosPageHeader(
                                title = stringResource(Res.string.nav_settings),
                                subtitle = stringResource(Res.string.hero_subtitle)
                            )
                            IosUserRow(
                                name = stringResource(Res.string.app_name),
                                school = stringResource(Res.string.hero_subtitle),
                                onClick = { onNavigate(Destination.AppearanceSettings) }
                            )
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                        ) {
                            IosGroupLabel(stringResource(Res.string.settings_group_timetable))
                            IosSettingsGroup {
                                IosSettingCell(
                                    title = stringResource(Res.string.item_course_conversion),
                                    icon = vectorResource(Res.drawable.school_24px),
                                    tone = IosCellTone.BLUE,
                                    onClick = { onNavigate(Destination.CourseTableConversion) }
                                )
                                IosSettingCell(
                                    title = stringResource(Res.string.section_title_semester_settings),
                                    icon = vectorResource(Res.drawable.calendar_today_24px),
                                    tone = IosCellTone.ORANGE,
                                    showDivider = true,
                                    onClick = { onNavigate(Destination.SemesterSettings) }
                                )
                                IosSettingCell(
                                    title = stringResource(Res.string.item_time_slot_customization),
                                    icon = vectorResource(Res.drawable.schedule_24px),
                                    tone = IosCellTone.RED,
                                    showDivider = true,
                                    onClick = { onNavigate(Destination.TimeSlotSettings) }
                                )
                                IosSettingCell(
                                    title = stringResource(Res.string.title_manage_course_tables),
                                    icon = vectorResource(Res.drawable.class_24px),
                                    tone = IosCellTone.TEAL,
                                    showDivider = true,
                                    onClick = { onNavigate(Destination.ManageCourseTables) }
                                )
                            }
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                        ) {
                            IosGroupLabel(stringResource(Res.string.settings_group_courses))
                            IosSettingsGroup {
                                IosSettingCell(
                                    title = stringResource(Res.string.item_course_management),
                                    icon = vectorResource(Res.drawable.edit_24px),
                                    tone = IosCellTone.GREEN,
                                    onClick = { onNavigate(Destination.CourseManagementList) }
                                )
                                IosSettingCell(
                                    title = stringResource(Res.string.item_couple_schedule),
                                    icon = vectorResource(Res.drawable.favorite_24px),
                                    tone = IosCellTone.PINK,
                                    showDivider = true,
                                    onClick = { onNavigate(Destination.CoupleScheduleSettings) }
                                )
                            }
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                        ) {
                            IosGroupLabel(stringResource(Res.string.settings_group_preference))
                            IosSettingsGroup {
                                IosSettingCell(
                                    title = stringResource(Res.string.item_appearance_settings),
                                    icon = vectorResource(Res.drawable.palette_24px),
                                    tone = IosCellTone.ORANGE,
                                    onClick = { onNavigate(Destination.AppearanceSettings) }
                                )
                                IosSettingCell(
                                    title = stringResource(Res.string.title_course_notification_settings),
                                    icon = vectorResource(Res.drawable.notifications_24px),
                                    tone = IosCellTone.PURPLE,
                                    showDivider = true,
                                    onClick = { onNavigate(Destination.NotificationSettings) }
                                )
                                IosSettingCell(
                                    title = stringResource(Res.string.item_backup_restore),
                                    icon = vectorResource(Res.drawable.cloud_24px),
                                    tone = IosCellTone.GREEN,
                                    showDivider = true,
                                    onClick = { onNavigate(Destination.BackupAndRestore) }
                                )
                                IosSettingCell(
                                    title = stringResource(Res.string.item_show_non_current_week),
                                    icon = vectorResource(Res.drawable.filter_list_24px),
                                    tone = IosCellTone.GRAY,
                                    showDivider = true,
                                    trailing = {
                                        IosSwitchTrailing(
                                            checked = uiState.appSettings.showNonCurrentWeekCourses,
                                            onCheckedChange = { viewModel.onShowNonCurrentWeekChanged(it) }
                                        )
                                    }
                                )
                                IosSettingCell(
                                    title = stringResource(Res.string.item_show_weekends),
                                    icon = vectorResource(Res.drawable.view_week_24px),
                                    tone = IosCellTone.YELLOW,
                                    showDivider = true,
                                    trailing = {
                                        IosSwitchTrailing(
                                            checked = uiState.courseConfig?.showWeekends ?: false,
                                            onCheckedChange = { viewModel.onShowWeekendsChanged(it) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                        ) {
                            IosGroupLabel(stringResource(Res.string.settings_group_about))
                            IosSettingsGroup {
                                IosSettingCell(
                                    title = stringResource(Res.string.item_more_options),
                                    icon = vectorResource(Res.drawable.more_horiz_24px),
                                    tone = IosCellTone.GRAY,
                                    onClick = { onNavigate(Destination.MoreOptions) }
                                )
                            }
                        }
                    }
                } else {
                // 头部渐变卡（v2 基线「我的」页样式：紫渐变 + 半透明白图标锚点 + 白字）
                item {
                    GradientHeroCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(appSpacing().cardInner),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(appShapes().chipSmall)
                                    .background(appColors().textOnPrimary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    vectorResource(Res.drawable.calendar_today_24px),
                                    contentDescription = null,
                                    tint = appColors().textOnPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            // 文字列 weight(1f)：窄屏防挤压，与右侧课程格纸插画保持间隙
                            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(
                                    text = stringResource(Res.string.app_name),
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = appType().hero),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = appColors().textOnPrimary
                                )
                                Text(
                                    text = stringResource(Res.string.hero_subtitle),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = appType().hint),
                                    color = appColors().textOnPrimary.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                // 核心功能（高频前置，逐项独立卡片，chip 图标 + 行标题 + 副标题 + chevron）
                item {
                    SettingCard(
                        title = stringResource(Res.string.item_course_conversion),
                        subtitle = stringResource(Res.string.desc_course_conversion),
                        leadingIcon = vectorResource(Res.drawable.school_24px),
                        accent = AccentTone.INFO,
                        onClick = { onNavigate(Destination.CourseTableConversion) }
                    )
                }
                item {
                    SettingCard(
                        title = stringResource(Res.string.section_title_semester_settings),
                        subtitle = stringResource(Res.string.desc_semester_settings),
                        leadingIcon = vectorResource(Res.drawable.calendar_today_24px),
                        accent = AccentTone.PRIMARY,
                        onClick = { onNavigate(Destination.SemesterSettings) }
                    )
                }
                // 非本周课程 + 显示周末（两个开关共用一行，紧跟学期设置）
                item {
                    SectionCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 左：显示非本周课程
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(Res.string.item_show_non_current_week),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = appType().body,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                AppSwitch(
                                    checked = uiState.appSettings.showNonCurrentWeekCourses,
                                    onCheckedChange = { viewModel.onShowNonCurrentWeekChanged(it) }
                                )
                            }
                            VerticalDivider(
                                modifier = Modifier.height(28.dp),
                                color = appColors().divider
                            )
                            // 右：是否显示周末
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(Res.string.item_show_weekends),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = appType().body,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                AppSwitch(
                                    checked = uiState.courseConfig?.showWeekends ?: false,
                                    onCheckedChange = { viewModel.onShowWeekendsChanged(it) }
                                )
                            }
                        }
                    }
                }
                item {
                    SettingCard(
                        title = stringResource(Res.string.item_time_slot_customization),
                        subtitle = stringResource(Res.string.desc_time_slot_customization),
                        leadingIcon = vectorResource(Res.drawable.schedule_24px),
                        accent = AccentTone.WARNING,
                        onClick = { onNavigate(Destination.TimeSlotSettings) }
                    )
                }
                item {
                    SettingCard(
                        title = stringResource(Res.string.title_manage_course_tables),
                        subtitle = stringResource(Res.string.desc_manage_course_tables),
                        leadingIcon = vectorResource(Res.drawable.class_24px),
                        accent = AccentTone.AMBER,
                        onClick = { onNavigate(Destination.ManageCourseTables) }
                    )
                }
                item {
                    SettingCard(
                        title = stringResource(Res.string.item_course_management),
                        subtitle = stringResource(Res.string.desc_course_management),
                        leadingIcon = vectorResource(Res.drawable.edit_24px),
                        accent = AccentTone.SUCCESS,
                        onClick = { onNavigate(Destination.CourseManagementList) }
                    )
                }
                // 低频入口（逐项独立卡片）
                item {
                    SettingCard(
                        title = stringResource(Res.string.item_couple_schedule),
                        subtitle = stringResource(Res.string.desc_couple_schedule),
                        leadingIcon = vectorResource(Res.drawable.favorite_24px),
                        accent = AccentTone.FAVORITE,
                        onClick = { onNavigate(Destination.CoupleScheduleSettings) }
                    )
                }
                item {
                    SettingCard(
                        title = stringResource(Res.string.item_appearance_settings),
                        subtitle = stringResource(Res.string.desc_appearance_settings),
                        leadingIcon = vectorResource(Res.drawable.palette_24px),
                        accent = AccentTone.PRIMARY,
                        onClick = { onNavigate(Destination.AppearanceSettings) }
                    )
                }
                item {
                    SettingCard(
                        title = stringResource(Res.string.title_course_notification_settings),
                        subtitle = stringResource(Res.string.desc_notification_settings),
                        // 图标语义修正：通知类用 notifications（原 info 与语义不符）
                        leadingIcon = vectorResource(Res.drawable.notifications_24px),
                        accent = AccentTone.INFO,
                        onClick = { onNavigate(Destination.NotificationSettings) }
                    )
                }
                // P1-2 备份入口上提一级：数据安全功能原藏在 设置→课表导入/导出→同步→备份 第 3 级
                item {
                    SettingCard(
                        title = stringResource(Res.string.item_backup_restore),
                        subtitle = stringResource(Res.string.desc_backup_restore),
                        leadingIcon = vectorResource(Res.drawable.cloud_24px),
                        accent = AccentTone.SUCCESS,
                        onClick = { onNavigate(Destination.BackupAndRestore) }
                    )
                }
                item {
                    SettingCard(
                        title = stringResource(Res.string.item_more_options),
                        subtitle = stringResource(Res.string.desc_more_options),
                        leadingIcon = vectorResource(Res.drawable.more_horiz_24px),
                        accent = AccentTone.PRIMARY,
                        onClick = { onNavigate(Destination.MoreOptions) }
                    )
                }
                } // 非 iOS 主题布局结束
            }
        }
    }
}

/**
 * 单个设置入口卡片（逐项独立，卡片间少量分隔）。
 * v2 风格基线：白色无边框 20dp 圆角卡 + 极轻投影，chip 图标 + 行标题 + 副标题 + 灰 chevron。
 * 供设置主页与各二级设置页复用，保持视觉一致。
 */
@Composable
internal fun SettingCard(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    accent: AccentTone = AccentTone.PRIMARY,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle? = null,
    itemVerticalPadding: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit = {
        Icon(
            vectorResource(Res.drawable.chevron_right_24px),
            contentDescription = null,
            tint = appColors().textSecondary
        )
    }
) {
    val effectiveTitleStyle = titleStyle ?: MaterialTheme.typography.titleMedium.copy(
        fontSize = appType().rowTitle,
        fontWeight = FontWeight.SemiBold,
        color = appColors().textPrimary
    )
    AppCard(modifier = modifier.fillMaxWidth()) {
        // 内容 16dp 水平缩进（与 SectionCard 一致）：图标 chip / chevron 不贴卡片边缘
        Column(modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal)) {
            SettingItem(
                title = title,
                subtitle = subtitle,
                leadingIcon = leadingIcon,
                accent = accent,
                titleStyle = titleStyle,
                verticalPadding = itemVerticalPadding,
                onClick = onClick,
                trailingContent = trailingContent
            )
        }
    }
}

/**
 * 分区大卡：一个白卡包住一组的多个设置项，项与项之间用分割线分隔。
 * 与 SettingCard 同底色同圆角，仅结构上承载"分区内多项"的场景，
 * 用于各列表型二级页，替代逐项独立小卡。
 */
@Composable
internal fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal), content = content)
    }
}

/**
 * 分区大卡内的分割线，用于分隔同一大卡中的多个设置项。
 */
@Composable
internal fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        color = appColors().divider,
        thickness = 0.5.dp
    )
}

// ==================== 通透主题「我的」页分组列表组件 ====================
// 组件本体在 IosSettingsComponents.kt（与 SettingsScreen 分离，供其它页面复用）。

@Composable
internal fun ColorPreviewDot(colorIndex: Int, colorMaps: List<DualColor>) {
    val dualColor = colorMaps.getOrNull(colorIndex) ?: return
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
 * 使用当前主题的 courseColorMaps，确保与课程块实际渲染一致。
 */
@Composable
internal fun ColorPickerDialog(
    title: String,
    selectedIndex: Int,
    colorMaps: List<DualColor>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val colors = colorMaps
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
            // 取消弱化为灰字文本钮（与其他对话框的取消语言一致）
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(Res.string.action_cancel),
                    color = appColors().textSecondary
                )
            }
        }
    )
}

/**
 * 单个颜色色块，用于颜色选择对话框。
 */
@Composable
internal fun ColorSwatch(
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
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else dualColor.dark.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small
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
 * 封装单个设置项的可组合函数，提高代码复用性。
 * v2 风格基线：行标题 16sp SemiBold，副标题 13sp 灰，行高 ≥64dp，
 * leadingIcon 渲染为 48dp 语义淡底图标 chip，尾参默认灰 chevron。
 */
@Composable
internal fun SettingItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector = vectorResource(Res.drawable.chevron_right_24px),
    leadingIcon: ImageVector? = null,
    accent: AccentTone = AccentTone.PRIMARY,
    titleStyle: TextStyle? = null,
    verticalPadding: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit = {
        Icon(
            icon,
            contentDescription = null,
            tint = appColors().textSecondary
        )
    }
) {
    val isClaudePreset = LocalThemePreset.current == AppThemePreset.CLAUDE
    val effectiveTitleStyle = titleStyle ?: MaterialTheme.typography.titleMedium.copy(
        fontSize = if (isClaudePreset) 15.sp else appType().rowTitle,
        fontWeight = if (isClaudePreset) FontWeight.Medium else FontWeight.SemiBold,
        color = appColors().textPrimary
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (isClaudePreset) 48.dp else appSpacing().rowMinHeight)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (leadingIcon != null) {
            IconChip(
                icon = leadingIcon,
                tone = accent,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = effectiveTitleStyle)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = appType().caption,
                        lineHeight = 18.sp
                    ),
                    color = appColors().textSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
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
internal fun NumberPickerDialog(
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