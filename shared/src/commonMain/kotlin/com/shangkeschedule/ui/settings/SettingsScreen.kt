package com.shangkeschedule.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.ui.theme.SettingsPageMaxWidth
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.Destination
import com.shangkeschedule.data.model.DualColor
import com.shangkeschedule.ui.components.AdaptiveNavigationScaffold
import com.shangkeschedule.ui.components.AppCard
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.components.IconChip
import com.shangkeschedule.ui.components.NativeNumberPicker
import com.shangkeschedule.ui.theme.AccentTone
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.SettingsEntryTone
import com.shangkeschedule.ui.components.AppPageHeader
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSettingsPage
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.DrawableResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.app_name
import shangkeschedule.shared.generated.resources.hero_subtitle
import shangkeschedule.shared.generated.resources.item_couple_schedule
import shangkeschedule.shared.generated.resources.check_24px
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.day_of_week_monday
import shangkeschedule.shared.generated.resources.day_of_week_sunday
import shangkeschedule.shared.generated.resources.dialog_title_manual_set_week
import shangkeschedule.shared.generated.resources.dialog_title_set_first_day_of_week
import shangkeschedule.shared.generated.resources.item_course_conversion
import shangkeschedule.shared.generated.resources.item_course_management
import shangkeschedule.shared.generated.resources.item_more_options
import shangkeschedule.shared.generated.resources.download_24px
import shangkeschedule.shared.generated.resources.item_appearance_settings
import shangkeschedule.shared.generated.resources.item_show_non_current_week
import shangkeschedule.shared.generated.resources.item_show_weekends
import shangkeschedule.shared.generated.resources.item_time_slot_customization
import shangkeschedule.shared.generated.resources.settings_group_app
import shangkeschedule.shared.generated.resources.settings_group_display_notify
import shangkeschedule.shared.generated.resources.settings_group_school_system
import shangkeschedule.shared.generated.resources.settings_sub_appearance
import shangkeschedule.shared.generated.resources.settings_sub_backup_restore
import shangkeschedule.shared.generated.resources.settings_sub_cert_exam
import shangkeschedule.shared.generated.resources.settings_sub_couple_schedule
import shangkeschedule.shared.generated.resources.settings_sub_empty_classroom
import shangkeschedule.shared.generated.resources.settings_sub_grade
import shangkeschedule.shared.generated.resources.settings_sub_study_progress
import shangkeschedule.shared.generated.resources.settings_sub_course_conversion
import shangkeschedule.shared.generated.resources.settings_sub_course_management
import shangkeschedule.shared.generated.resources.settings_sub_manage_course_tables
import shangkeschedule.shared.generated.resources.settings_sub_more_options
import shangkeschedule.shared.generated.resources.settings_sub_notification
import shangkeschedule.shared.generated.resources.settings_sub_semester_settings
import shangkeschedule.shared.generated.resources.settings_sub_show_non_current_week
import shangkeschedule.shared.generated.resources.settings_sub_show_weekends
import shangkeschedule.shared.generated.resources.settings_sub_time_slot
import shangkeschedule.shared.generated.resources.calendar_today_24px
import shangkeschedule.shared.generated.resources.schedule_24px
import shangkeschedule.shared.generated.resources.favorite_24px
import shangkeschedule.shared.generated.resources.palette_24px
import shangkeschedule.shared.generated.resources.school_24px
import shangkeschedule.shared.generated.resources.class_24px
import shangkeschedule.shared.generated.resources.edit_24px
import shangkeschedule.shared.generated.resources.section_title_semester_settings
import shangkeschedule.shared.generated.resources.more_horiz_24px
import shangkeschedule.shared.generated.resources.notifications_24px
import shangkeschedule.shared.generated.resources.filter_list_24px
import shangkeschedule.shared.generated.resources.view_week_24px
import shangkeschedule.shared.generated.resources.settings_group_timetable
import shangkeschedule.shared.generated.resources.settings_group_courses
import shangkeschedule.shared.generated.resources.item_backup_restore
import shangkeschedule.shared.generated.resources.cloud_24px
import shangkeschedule.shared.generated.resources.status_current_week_format
import shangkeschedule.shared.generated.resources.title_course_notification_settings
import shangkeschedule.shared.generated.resources.title_manage_course_tables
import shangkeschedule.shared.generated.resources.nav_settings
import shangkeschedule.shared.generated.resources.title_vacation
// v4.66.0（信息架构搬迁 · 用户 m05093 / 裁决 A）：学习与教务类入口从「更多」页上移到「我的」页
import com.shangkeschedule.WebPagePurpose
import kotlinx.coroutines.launch
import shangkeschedule.shared.generated.resources.check_circle_24px
import shangkeschedule.shared.generated.resources.grade_page_title
import shangkeschedule.shared.generated.resources.list_alt_24px
import shangkeschedule.shared.generated.resources.search_24px
import shangkeschedule.shared.generated.resources.settings_group_study
import shangkeschedule.shared.generated.resources.title_cert_exam
import shangkeschedule.shared.generated.resources.title_empty_classroom
import shangkeschedule.shared.generated.resources.title_study_progress

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // v3.69.0（V3 去重）：页骨架差异（有无顶栏 / 宽屏是否居中）改由
    // [appSettingsPage] 提供 —— 原先这里声明 isIos/isSoft/isClaude 三个身份布尔，
    // 再在 7 处按身份分派；设置页组件族合一后，身份判断全部消失。
    val page = appSettingsPage()

    // v4.66.2：教务适配的「状态 / 自动同步」两条已移回「更多」页（抓取脚本的维护/诊断
    // 入口，对普通用户没有实际作用），其同步状态机随之整段搬走 —— 本页不再持有
    // adapterRemoteUpdater / syncing / syncStatusText。留在本页只会让这两个状态成为死代码。
    //
    // 数据驱动（v3.54.0）：全部设置条目只在此定义一份，一套组件渲染，
    // 新增设置项不再需要同步改三处（历史上已出现 tone 映射漂移）
    val settingsSections = buildSettingsSections(
        uiState = uiState,
        viewModel = viewModel
    )
    // 批 2：页头统一为内容区 AppPageHeader 后，Scaffold 不再有吸顶玻璃栏
    // ⇒ haze 三件套（rememberHazeState / glassTint / glassFallback）与
    // `.hazeSource` 一并移除 —— 没有 hazeEffect 消费方时，source 只是白付的每帧开销。

    // v3.49.0：「我的」页顶部身份卡的展示文案（三套主题共用一份推导）
    // - 昵称：未设置在「我的信息」页填写时回落为应用名（观感与旧版「上课」卡一致）
    // - 副标题：学校 · 专业；两者都为空时回落为品牌语
    val appName = stringResource(Res.string.app_name)
    val brandSubtitle = stringResource(Res.string.hero_subtitle)
    val profileName = uiState.appSettings.profileNickname.ifBlank { appName }
    val profileSubtitle = listOf(
        uiState.appSettings.profileSchool,
        uiState.appSettings.profileMajor
    ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { brandSubtitle }

    AdaptiveNavigationScaffold(
        currentDestination = Destination.Settings,
        onTabSelected = { dest -> onNavigate(dest) }
    ) { navPadding ->
        Scaffold(
            // 全局 UI 优化批 2：三主题统一为「内容区页头」，Scaffold 不再挂顶栏
            // ⇒ 也随之不再需要 exitUntilCollapsed 的 nestedScroll 连接
            // （原注释：无顶栏的主题挂上会把滚动整段吞掉，故此前只能条件挂载）
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = appSpacing().pageHorizontal),
                // v4.64.29 重排：列表项间距改用 sectionGap（24dp，三主题一致）。
                // 原用 cardGap（16dp），而本列表的"项"就是**一个整组**（组标签 + 大卡）——
                // 组的轮廓比卡片更需要呼吸：16dp 下相邻两组几乎贴在一起，读起来像
                // 一整块连续列表，分组标签的存在感被压掉。24dp 才让"四组"真的读成四组。
                // 组**内部**的紧凑度不受影响（由 AppSettingsGroup 的自身内距控制）。
                verticalArrangement = Arrangement.spacedBy(appSpacing().sectionGap),
                // 宽屏（平板/桌面）内容限宽 640dp 居中，对齐 iPad 设置 App 行为
                horizontalAlignment = if (page.centerContent) Alignment.CenterHorizontally else Alignment.Start,
                // 顶部 inset 走 contentPadding：列表内容滚动到吸顶玻璃栏后（顶部不再裁切）
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = navPadding.calculateBottomPadding() + appSpacing().contentBottom
                )
            ) {
                // ===== 页头（批 2）+ 身份卡 + 数据驱动分组列表（v3.69.0 三份复制合一）=====
                //
                // 页头：三主题统一 —— 此前**只有通透**有吸顶大标题，书卷 / 柔绘
                // 完全没有页面标题（A1 遗留缺陷，原 ClaudePageHeader / SoftPageHeader
                // 从未被调用）。现在四主页面共用同一个 AppPageHeader。
                item {
                    AppPageHeader(
                        title = stringResource(Res.string.nav_settings),
                        modifier = Modifier.widthIn(max = SettingsPageMaxWidth)
                    )
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = SettingsPageMaxWidth)
                            .padding(top = appSpacing().pageTop)
                    ) {
                        AppSettingsUserRow(
                            name = profileName,
                            school = profileSubtitle,
                            avatarPath = uiState.appSettings.profileAvatarPath,
                            onClick = { onNavigate(Destination.ProfileInfo) }
                        )
                    }
                }
                appSettingsItems(settingsSections, onNavigate)
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
    AppAlertDialog(
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
            // AC2（v3.69.2）：色块没有文字，此前 TalkBack 读「未加标签，可点击」，
            // 既不知可切换也不知是否已选。selectable + Role.RadioButton 由框架播报
            // 「单选按钮，已选中 / 未选中」；选中态的勾是纯装饰，保持无描述。
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            ),
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
    /**
     * 行是否可交互。false 时整行不可点、文字降到次级色（XL-015 固定小组件时用它做防连点）。
     *
     * 与 `onClick` 分开而不是用 `onClick = null` 表达「禁用」：后者会让行**彻底失去点击语义**
     * （既没有 ripple 也没有禁用视觉），调用方分不清「禁用」和「这行本来就是纯展示」。
     */
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit = {
        Icon(
            icon,
            contentDescription = null,
            tint = appColors().textSecondary
        )
    }
) {
    // A1/P2：原先这里是 `val isClaudePreset = LocalThemePreset.current == AppThemePreset.CLAUDE`
    // 加三处 `if (isClaudePreset)`（15sp / Medium / 48dp）。根因是 token 缺失而非"需要分支"：
    // `rowTitle` 描述的是**页面行标题**（书卷 18sp），设置行是另一个角色。补 `settingsRowTitle` /
    // `settingsRowTitleWeight` / `settingsRowMinHeight` 三个 token 后，分支自然消失（规范 R1/R2）。
    val titleColor = if (enabled) {
        appColors().textPrimary
    } else {
        appColors().textSecondary
    }
    val effectiveTitleStyle = (titleStyle ?: MaterialTheme.typography.titleMedium).copy(
        fontSize = appType().settingsRowTitle,
        fontWeight = appType().settingsRowTitleWeight,
        color = titleColor
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = appSpacing().settingsRowMinHeight)
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
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
 * 可点设置项右侧的「当前值 + chevron」。
 *
 * ⚠️ 由来（设计走查 E1）：[SettingItem] 的 [trailingContent] 默认就是 chevron，
 * 但需要「顺带显示当前值」的行往往直接覆盖成 `Text(value)`，**连带把 chevron 丢掉** ⇒
 * 行内只剩一段纯文本，看不出可点（学期设置 4 行、更多设置「起始页」等）。
 * 这里把「值 + chevron」固化成一个组件，避免再次漏掉可点暗示。
 *
 * 值统一用 textSecondary：与左侧 16sp SemiBold 标题拉开层级，
 * 避免标题与值同字重同颜色造成"分不清哪个是标签、哪个是值"。
 */
@Composable
internal fun SettingValueTrailing(
    value: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = style,
            color = appColors().textSecondary
        )
        Icon(
            vectorResource(Res.drawable.chevron_right_24px),
            contentDescription = null,
            tint = appColors().textSecondary
        )
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

    AppAlertDialog(
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

    AppAlertDialog(
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

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            NativeNumberPicker(
                values = range.toList(),
                // 必须传本地编辑值：传恒定的 initialValue 时，NativeNumberPicker 内部
                // LaunchedEffect(initialSelectedIndex) 会把滚轮拉回原位并再次回调
                // onValueChange(initialValue)，把 dialogSelectedValue 重置为初始值 ——
                // 用户拨动滚轮后点确认写入的是「未修改的旧值」，改总周数直接无效。
                selectedValue = dialogSelectedValue,
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

// ==================== 设置主页数据驱动模型（v3.54.0） ====================

//
// v3.69.0（V3 去重）：[SettingsEntryTone] 已上移到 `ui/theme/SettingsTone.kt`。
// 原先「语义角色 → 三套视觉枚举」的映射由本文件的 toClaudeTone/toSoftTone/toIosTone
// 三张表承担，且柔绘、通透各有一处折叠（10 → 9 / 10 → 8）；上移后色表归主题层持有，
// 组件只认语义角色，三张表与三套视觉枚举一并删除。
//

/** 开关类条目的 trailing 状态包。 */
private data class SettingsEntryToggle(
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

/**
 * 一条设置条目：[destination] 为 null 时是纯开关行（点击不导航）。
 *
 * [subtitleRes]（v4.64.29 新增）：功能说明，回答「点进去能干什么」，取代过去
 * 「只有标题、必须点进去才知道是什么」的状态。导航行全部填满；开关行同样填写
 * （说明该开关的影响范围），以保证 14 行的说明密度一致。
 */
private data class SettingsEntry(
    val titleRes: StringResource,
    val iconRes: DrawableResource,
    val tone: SettingsEntryTone,
    val subtitleRes: StringResource? = null,
    val destination: Destination? = null,
    val toggle: SettingsEntryToggle? = null,
    /** 行副标题（本页大多数行不用；目前只有「自动同步教务系统」用它显示同步状态）。 */
    val detail: String? = null,
    /** 无 destination 的行自带动作（如触发一次适配同步）。 */
    val onClick: (() -> Unit)? = null
)

/** 一个设置分组：组标题 + 条目列表。 */
private data class SettingsSection(
    val labelRes: StringResource,
    val entries: List<SettingsEntry>
)

/**
 * 构建设置主页全部分组（v4.64.29「我的」页功能位置重排 + v4.66.0 信息架构搬迁）。
 *
 * ── 重排依据：按**功能性质**归位，不按「设置类型」平铺 ──────────────
 *
 * 旧结构是 4 组【课表 / 课程 / 偏好 / 关于】，三处归类站不住脚：
 * 1. **「课表」组塞了三种东西**：导入导出（数据流动）、学期 / 时间段（课表参数）、
 *    我的课表（课表集合）。它们不是同一类操作。
 * 2. **「课程」组把情侣课表算作课程**：情侣课表是一张**独立课表**的叠加与同步，
 *    归到「课表」才符合用户心智（用户想的是"我又有了一张表"，不是"我又加了一门课"）。
 * 3. **「偏好」组把二级页面入口和纯开关混排**：外观 / 提醒 / 备份三个下钻页与
 *    「显示非本周课程」「显示周末」两个开关同处一卡，开关夹在入口中间，
 *    看不出哪行能点进去；且两个开关本质是**课表显示规则**，被"偏好"这个大词吞掉。
 * 4. **「关于」只放「更多设置」**：而更多设置里装的是语言、启动页、官网、许可证
 *    —— 是"应用"，不是"关于"。
 *
 * 新结构按功能性质重划为 4 组（条目数 4 / 3 / 4 / 1，与旧 4 / 2 / 5 / 1 相比更均衡）：
 * - **课表**（4）：课表集合与其参数 —— 我的课表 / 学期 / 时间段 / 情侣课表
 * - **课程**（3）：课程内容与数据进出 —— 课程管理 / 导入导出 / 备份恢复
 * - **显示与提醒**（4）：看什么 + 何时提醒 —— 外观 / 通知 / 两个显示开关
 * - **应用与关于**（1）：应用元信息 —— 更多设置
 *
 * 组内顺序统一为「高频在前」：我的课表（切表最频繁）提到组首（原排末位），
 * 外观与样式（用户最常改的偏好）提到显示组首。开关行统一排在组尾——
 * 开关是就地生效的次要操作，不该插在下钻入口中间抢位置。
 *
 * **零遗漏保证**：本页 14 个可交互元素（11 个下钻入口 + 身份卡 + 2 个开关）
 * 与 36 个 Destination 的归属关系全部保持不变，只调整呈现分组、顺序与说明。
 *
 * ── v4.66.0（信息架构搬迁 · 用户 m05093 / 裁决 A）──────────────────────────
 * 在「显示与提醒」前新增「学习」分组（成绩与绩点 / 学业情况 / 考证查分），并把教务适配
 * 三行（教务适配状态 / 自动同步教务系统 / 空教室查询）并入「课表」分组 —— 这三行不在
 * v4.64.29 的 14 个可交互元素之内，属本次新增入口。「更多」页只留关于本应用 / 反馈与
 * 协议 / 联系作者。
 *
 * ── v4.66.1（功能更新后的再次重排 · 本轮）─────────────────────────────────
 * v4.66.0 那次搬迁留下三处问题，本轮一并收口：
 * 1. **「课表」组被撑到 7 条，且重新混了两种性质**：原有 4 条是课表**自身的参数**
 *    （集合 / 学期 / 时间段 / 情侣叠加），新塞进来的 3 条是**与学校教务系统对接的能力**
 *    （抓取脚本是否可用、能否自动更新、能否查空教室）—— 后者不改变课表本身，
 *    依赖的是教务系统连通性，与前者不是一类操作。这正是 v4.64.29 批评过的
 *    「课表组塞了三种东西」的复发。故把三行独立成「教务系统」组，「课表」组回到 4 条。
 * 2. **6 条新条目没有副标题**，破坏了 v4.64.29 建立的 12 条统一说明密度
 *    （用户必须点进去才知道能干什么）。本轮补齐至 18 条全覆盖。
 * 3. **同组撞色 + 跨组撞图标**：
 *    - 教务适配状态原用 OLIVE，与同组「我的课表」OLIVE **同组内撞色** → 改 PURPLE；
 *    - 教务适配状态原用 school_24px，与「课表导入/导出」的 school_24px 撞图标
 *      → 改 build_24px（语义＝构建脚本，比"学校"更贴）；
 *    - 「课表导入/导出」顺势改用 download_24px（导出文件语义更准，且该图标此前闲置），
 *      把 school_24px 让给考证查分（全页三处 school_24px → 一处）。
 *
 * 分组结果（6 组 / 18 条 + 身份卡）：
 * 课表(4) · 教务系统(3) · 课程(3) · 学习(3) · 显示与提醒(4) · 应用与关于(1)
 *
 * ── v4.66.2 / v4.66.3（按用户两次反馈继续收口）─────────────────────────
 * - v4.66.2：教务适配两条（适配状态 / 自动同步）移回「更多」页 —— 抓取脚本的维护/
 *   诊断入口，对普通用户没有实际作用，不该占本页首屏。同步状态机随行搬迁。
 * - v4.66.3：v4.66.2 把「教务系统」组收窄到只剩 1 条（空教室查询），成了空壳分组；
 *   用户指出「课表导入导出和其他多项等均属于教务系统」。据此重划：
 *   · **教务系统**（3→2）：课表导入与导出（从「课程」组并入）+ 空教室查询 ——
 *     两条都要**先打通学校教务系统**才能拿到数据，是同族；
 *   · **课程**组**整体撤销**：课程管理并入「课表」组（它管的是课表里的课程条目，
 *     本质是课表自身内容）；备份与恢复并入「应用与关于」组（跨课表的数据保全，
 *     不属于任何单张课表）。撤销后本页由 6 组降为 5 组，避免出现单条分组。
 *   · 「学习」组保留（成绩与绩点 / 学业情况 / 考证查分）—— 区分「向教务系统取数」
 *     （教务系统组）与「看学业结果」（学习组）。
 *
 * 分组结果（5 组 / 16 条 + 身份卡）：
 * 课表(5) · 教务系统(2) · 学习(3) · 显示与提醒(4) · 应用与关于(2)
 */
private fun buildSettingsSections(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
): List<SettingsSection> = listOf(
    // ── 课表：集合与参数 ──────────────────────────────────────────────────
    SettingsSection(
        labelRes = Res.string.settings_group_timetable,
        entries = listOf(
            SettingsEntry(
                Res.string.title_manage_course_tables, Res.drawable.class_24px, SettingsEntryTone.OLIVE,
                Res.string.settings_sub_manage_course_tables, destination = Destination.ManageCourseTables
            ),
            SettingsEntry(
                Res.string.section_title_semester_settings, Res.drawable.calendar_today_24px, SettingsEntryTone.ORANGE,
                Res.string.settings_sub_semester_settings, destination = Destination.SemesterSettings
            ),
            SettingsEntry(
                Res.string.item_time_slot_customization, Res.drawable.schedule_24px, SettingsEntryTone.RED,
                Res.string.settings_sub_time_slot, destination = Destination.TimeSlotSettings()
            ),
            // 从「课程」组迁入「课表」：情侣课表是一张独立课表的叠加，不是课程条目
            SettingsEntry(
                Res.string.item_couple_schedule, Res.drawable.favorite_24px, SettingsEntryTone.PINK,
                Res.string.settings_sub_couple_schedule, destination = Destination.CoupleScheduleSettings
            ),
            // v4.66.3：从已撤销的「课程」组并入。课程管理管的是**课表里的课程条目**
            // （课程名 / 同课不同班次），本质是课表自身的内容，与课表同属一域。
            SettingsEntry(
                Res.string.item_course_management, Res.drawable.edit_24px, SettingsEntryTone.MATCHA,
                Res.string.settings_sub_course_management, destination = Destination.CourseManagementList
            )
        )
    ),
    // ── 教务系统：向学校教务系统取数的入口（v4.66.3 扩为 2 条）──────────────
    SettingsSection(
        labelRes = Res.string.settings_group_school_system,
        entries = listOf(
            // v4.66.3：课表导入与导出从「课程」组并入。它与空教室查询同族 ——
            // **都要先打通学校教务系统**才能拿到数据（其「学校导入」分区就是
            // 教务系统导入 / 文本粘贴 / AI 识别）。先前「教务系统」组只挂一条
            // 空教室查询是个空壳分组，现由这两条把它撑起来。
            // 注：该页还含文件导入导出与系统日历同步，严格说不是纯教务系统，
            // 但从用户心智看它就是「从学校把课表弄进来」的总入口，归在此处最直观。
            SettingsEntry(
                Res.string.item_course_conversion, Res.drawable.download_24px, SettingsEntryTone.PURPLE,
                Res.string.settings_sub_course_conversion, destination = Destination.CourseTableConversion
            ),
            SettingsEntry(
                Res.string.title_empty_classroom, Res.drawable.search_24px, SettingsEntryTone.AMBER,
                Res.string.settings_sub_empty_classroom,
                destination = Destination.SchoolSelectionListScreen(WebPagePurpose.EMPTY_CLASSROOM)
            )
        )
    ),
    // ── 显示与提醒：看什么 + 何时提醒（下钻入口在前，就地开关在后）─────────
    SettingsSection(
        // v4.66.0（IA 搬迁 · 裁决 A）：学习类入口从「更多」页上移到「我的」页独立分组
        labelRes = Res.string.settings_group_study,
        entries = listOf(
            SettingsEntry(
                Res.string.grade_page_title, Res.drawable.list_alt_24px, SettingsEntryTone.PURPLE,
                Res.string.settings_sub_grade, destination = Destination.Grade
            ),
            SettingsEntry(
                Res.string.title_study_progress, Res.drawable.check_circle_24px, SettingsEntryTone.MATCHA,
                Res.string.settings_sub_study_progress, destination = Destination.StudyProgress
            ),
            // v4.66.0 曾让考证查分与「课表导入/导出」共用 school_24px。此处改为把
            // 导入/导出换成 download_24px（语义更贴：导出文件），把 school_24px 让给考证查分
            // —— 证书考试本就由学校教务组织，school 语义正确，且撞图标消除。
            SettingsEntry(
                Res.string.title_cert_exam, Res.drawable.school_24px, SettingsEntryTone.PINK,
                Res.string.settings_sub_cert_exam, destination = Destination.CertExam
            )
        )
    ),
    SettingsSection(
        labelRes = Res.string.settings_group_display_notify,
        entries = listOf(
            SettingsEntry(
                Res.string.item_appearance_settings, Res.drawable.palette_24px, SettingsEntryTone.ORANGE,
                Res.string.settings_sub_appearance, destination = Destination.AppearanceSettings
            ),
            SettingsEntry(
                Res.string.title_course_notification_settings, Res.drawable.notifications_24px, SettingsEntryTone.PINK,
                Res.string.settings_sub_notification, destination = Destination.NotificationSettings
            ),
            SettingsEntry(
                Res.string.item_show_non_current_week, Res.drawable.filter_list_24px, SettingsEntryTone.GRAY,
                Res.string.settings_sub_show_non_current_week,
                toggle = SettingsEntryToggle(
                    checked = uiState.appSettings.showNonCurrentWeekCourses,
                    onCheckedChange = viewModel::onShowNonCurrentWeekChanged
                )
            ),
            SettingsEntry(
                Res.string.item_show_weekends, Res.drawable.view_week_24px, SettingsEntryTone.AMBER,
                Res.string.settings_sub_show_weekends,
                toggle = SettingsEntryToggle(
                    checked = uiState.courseConfig?.showWeekends ?: false,
                    onCheckedChange = viewModel::onShowWeekendsChanged
                )
            )
        )
    ),
    // ── 应用与关于：数据保全 + 应用元信息 ──────────────────────────────────
    SettingsSection(
        labelRes = Res.string.settings_group_app,
        entries = listOf(
            // v4.66.3：从已撤销的「课程」组并入。备份与恢复是**跨课表**的数据保全动作
            // （整库打包 / WebDAV / 本地文件），不属于任何单张课表或课程条目，
            // 与应用元信息并在一组，由「与」字承担两类含义。
            SettingsEntry(
                Res.string.item_backup_restore, Res.drawable.cloud_24px, SettingsEntryTone.GREEN,
                // 刻意不复用 BackupScreen 内层的 desc_backup_restore（24 字）：那条是详情页
                // 的完整说明，在「我的」页行列表里会折成两行，把该行撑高、与其余单行
                // 副标题不齐。行内副标题统一 12~16 字。
                Res.string.settings_sub_backup_restore, destination = Destination.BackupAndRestore
            ),
            SettingsEntry(
                Res.string.item_more_options, Res.drawable.more_horiz_24px, SettingsEntryTone.BROWN,
                Res.string.settings_sub_more_options, destination = Destination.MoreOptions
            )
        )
    )
)

// ---- 三主题分组渲染器：同一份数据，一套组件 ----
//
// v3.69.0（V3 去重）：原 `claudeSettingsItems` / `softSettingsItems` / `iosSettingsItems`
// 三份包装各传一套组件与一张 tone 映射表；组件合一 + 色表归主题层后，三份包装退化为
// 同一份 —— 下面的 [appSettingsItems] 是唯一入口，内部全部走统一的 App* 组件。

/**
 * 设置分组渲染骨架：分组标题 + 分组容器 + 逐条 cell。
 *
 * 三套主题的差异只有「用哪组组件」与「语义色调怎么映射」，遍历、分隔线、
 * 开关尾部、导航回调完全一致；收口后新增设置条目只需改 [buildSettingsSections]。
 */
private fun LazyListScope.appSettingsItems(
    sections: List<SettingsSection>,
    onNavigate: (Destination) -> Unit
) {
    sections.forEachIndexed { sectionIndex, section ->
        // key 用分组标签文案而非下标：v4.64.29 重排改动了分组内容与顺序，
        // 下标 key 会让 LazyColumn 把旧项的滚动位置/状态错配到新项上。
        item(key = "settings-${section.labelRes}") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = SettingsPageMaxWidth)
            ) {
                AppGroupLabel(stringResource(section.labelRes))
                AppSettingsGroup {
                    section.entries.forEachIndexed { entryIndex, entry ->
                        val toggleTrailing: (@Composable () -> Unit)? = entry.toggle?.let { t ->
                            { AppSwitch(checked = t.checked, onCheckedChange = t.onCheckedChange) }
                        }
                        AppSettingRow(
                            title = stringResource(entry.titleRes),
                            subtitle = entry.subtitleRes?.let { stringResource(it) },
                            icon = vectorResource(entry.iconRes),
                            tone = entry.tone,
                            detail = entry.detail,
                            showDivider = entryIndex > 0,
                            // 有 destination 就导航；否则执行条目自带动作（如「自动同步教务系统」）
                            onClick = entry.destination?.let { d -> { onNavigate(d) } } ?: entry.onClick,
                            trailing = toggleTrailing
                        )
                    }
                }
            }
        }
    }
}

