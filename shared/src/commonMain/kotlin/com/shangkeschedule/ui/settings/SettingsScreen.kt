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
import shangkeschedule.shared.generated.resources.item_appearance_settings
import shangkeschedule.shared.generated.resources.item_show_non_current_week
import shangkeschedule.shared.generated.resources.item_show_weekends
import shangkeschedule.shared.generated.resources.item_time_slot_customization
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
import shangkeschedule.shared.generated.resources.settings_group_preference
import shangkeschedule.shared.generated.resources.settings_group_about
import shangkeschedule.shared.generated.resources.item_backup_restore
import shangkeschedule.shared.generated.resources.cloud_24px
import shangkeschedule.shared.generated.resources.status_current_week_format
import shangkeschedule.shared.generated.resources.title_course_notification_settings
import shangkeschedule.shared.generated.resources.title_manage_course_tables
import shangkeschedule.shared.generated.resources.nav_settings
import shangkeschedule.shared.generated.resources.title_vacation
// v4.66.0（信息架构搬迁 · 用户 m05093 / 裁决 A）：学习与教务类入口从「更多」页上移到「我的」页
import androidx.compose.runtime.rememberCoroutineScope
import com.shangkeschedule.WebPagePurpose
import com.shangkeschedule.tool.AdapterRemoteUpdater
import com.shangkeschedule.tool.AdapterSyncResult
import com.shangkeschedule.ui.components.ToastManager
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import shangkeschedule.shared.generated.resources.adapter_remote_update_failed
import shangkeschedule.shared.generated.resources.check_circle_24px
import shangkeschedule.shared.generated.resources.grade_page_title
import shangkeschedule.shared.generated.resources.item_auto_sync_adapter
import shangkeschedule.shared.generated.resources.list_alt_24px
import shangkeschedule.shared.generated.resources.search_24px
import shangkeschedule.shared.generated.resources.settings_group_study
import shangkeschedule.shared.generated.resources.sync_alt_24px
import shangkeschedule.shared.generated.resources.sync_status_disabled
import shangkeschedule.shared.generated.resources.sync_status_failed
import shangkeschedule.shared.generated.resources.sync_status_syncing
import shangkeschedule.shared.generated.resources.sync_status_up_to_date
import shangkeschedule.shared.generated.resources.sync_status_updated
import shangkeschedule.shared.generated.resources.title_adapter_status
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

    // v4.66.0（IA 搬迁）：教务适配的「自动同步」入口从「更多」页移到本页「课表」分组，
    // 与「教务适配状态 / 空教室查询」同组；本页行是数据驱动的（buildSettingsSections），
    // 所以这里只把「触发动作 + 结果副标题」两个参数传进去，不把同步状态机搬进数据层。
    val adapterRemoteUpdater: AdapterRemoteUpdater = koinInject()
    val syncScope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var syncStatusText by remember { mutableStateOf<String?>(null) }
    fun triggerAdapterSync() {
        if (syncing) return
        syncing = true
        syncStatusText = null
        syncScope.launch {
            val result = adapterRemoteUpdater.sync()
            val text = when (result) {
                is AdapterSyncResult.Updated ->
                    getString(Res.string.sync_status_updated, result.fileCount)
                AdapterSyncResult.UpToDate -> getString(Res.string.sync_status_up_to_date)
                AdapterSyncResult.Disabled -> getString(Res.string.sync_status_disabled)
                is AdapterSyncResult.VerificationFailed ->
                    getString(Res.string.adapter_remote_update_failed)
                is AdapterSyncResult.Failed -> getString(Res.string.sync_status_failed)
            }
            syncing = false
            syncStatusText = text
            ToastManager.show(text)
        }
    }
    val adapterSyncDetail = when {
        syncing -> stringResource(Res.string.sync_status_syncing)
        syncStatusText != null -> syncStatusText
        else -> null
    }
    // 数据驱动（v3.54.0）：全部设置条目只在此定义一份，一套组件渲染，
    // 新增设置项不再需要同步改三处（历史上已出现 tone 映射漂移）
    val settingsSections = buildSettingsSections(
        uiState = uiState,
        viewModel = viewModel,
        adapterSyncDetail = adapterSyncDetail,
        onAdapterSync = ::triggerAdapterSync
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
                verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap),
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
    val effectiveTitleStyle = titleStyle ?: MaterialTheme.typography.titleMedium.copy(
        fontSize = appType().settingsRowTitle,
        fontWeight = appType().settingsRowTitleWeight,
        color = appColors().textPrimary
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = appSpacing().settingsRowMinHeight)
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

/** 一条设置条目：[destination] 为 null 时是纯开关行（点击不导航）。 */
private data class SettingsEntry(
    val titleRes: StringResource,
    val iconRes: DrawableResource,
    val tone: SettingsEntryTone,
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
 * 构建设置主页全部分组。结构（5 组【课表 / 课程 / 学习 / 偏好 / 关于】、条目、开关位置）
 * 与 v3.53.5 三份主题复制完全一致；开关条目的取值随 [uiState] 刷新。
 *
 * v4.66.0（信息架构搬迁）：新增「学习」分组，并把教务适配三行并入「课表」分组 ——
 * 依据用户 m05093「新加的功能位置重新排列，不要放在更多里面」与随后的裁决 A：
 * 学习/教务类放「我的」页，「更多」页只留关于本应用 / 反馈与协议 / 联系作者。
 */
private fun buildSettingsSections(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    adapterSyncDetail: String? = null,
    onAdapterSync: (() -> Unit)? = null
): List<SettingsSection> = listOf(
    SettingsSection(
        labelRes = Res.string.settings_group_timetable,
        entries = listOf(
            SettingsEntry(Res.string.item_course_conversion, Res.drawable.school_24px, SettingsEntryTone.PURPLE, Destination.CourseTableConversion),
            SettingsEntry(Res.string.section_title_semester_settings, Res.drawable.calendar_today_24px, SettingsEntryTone.ORANGE, Destination.SemesterSettings),
            SettingsEntry(Res.string.item_time_slot_customization, Res.drawable.schedule_24px, SettingsEntryTone.RED, Destination.TimeSlotSettings()),
            SettingsEntry(Res.string.title_manage_course_tables, Res.drawable.class_24px, SettingsEntryTone.OLIVE, Destination.ManageCourseTables),
            // v4.66.0（IA 搬迁 · 裁决 A）：教务适配三行从「更多」页移到「课表」分组
            SettingsEntry(Res.string.title_adapter_status, Res.drawable.school_24px, SettingsEntryTone.OLIVE, Destination.AdapterStatus),
            SettingsEntry(Res.string.item_auto_sync_adapter, Res.drawable.sync_alt_24px, SettingsEntryTone.GREEN, detail = adapterSyncDetail, onClick = onAdapterSync),
            SettingsEntry(Res.string.title_empty_classroom, Res.drawable.search_24px, SettingsEntryTone.AMBER, Destination.SchoolSelectionListScreen(WebPagePurpose.EMPTY_CLASSROOM))
        )
    ),
    SettingsSection(
        labelRes = Res.string.settings_group_courses,
        entries = listOf(
            SettingsEntry(Res.string.item_course_management, Res.drawable.edit_24px, SettingsEntryTone.MATCHA, Destination.CourseManagementList),
            SettingsEntry(Res.string.item_couple_schedule, Res.drawable.favorite_24px, SettingsEntryTone.PINK, Destination.CoupleScheduleSettings)
        )
    ),
    SettingsSection(
        // v4.66.0（IA 搬迁 · 裁决 A）：学习类入口从「更多」页上移到「我的」页独立分组
        labelRes = Res.string.settings_group_study,
        entries = listOf(
            SettingsEntry(Res.string.grade_page_title, Res.drawable.list_alt_24px, SettingsEntryTone.PURPLE, Destination.Grade),
            SettingsEntry(Res.string.title_study_progress, Res.drawable.check_circle_24px, SettingsEntryTone.MATCHA, Destination.StudyProgress),
            SettingsEntry(Res.string.title_cert_exam, Res.drawable.school_24px, SettingsEntryTone.PINK, Destination.CertExam)
        )
    ),
    SettingsSection(
        labelRes = Res.string.settings_group_preference,
        entries = listOf(
            SettingsEntry(Res.string.item_appearance_settings, Res.drawable.palette_24px, SettingsEntryTone.ORANGE, Destination.AppearanceSettings),
            SettingsEntry(Res.string.title_course_notification_settings, Res.drawable.notifications_24px, SettingsEntryTone.PURPLE, Destination.NotificationSettings),
            SettingsEntry(Res.string.item_backup_restore, Res.drawable.cloud_24px, SettingsEntryTone.GREEN, Destination.BackupAndRestore),
            SettingsEntry(
                Res.string.item_show_non_current_week, Res.drawable.filter_list_24px, SettingsEntryTone.GRAY,
                toggle = SettingsEntryToggle(
                    checked = uiState.appSettings.showNonCurrentWeekCourses,
                    onCheckedChange = viewModel::onShowNonCurrentWeekChanged
                )
            ),
            SettingsEntry(
                Res.string.item_show_weekends, Res.drawable.view_week_24px, SettingsEntryTone.AMBER,
                toggle = SettingsEntryToggle(
                    checked = uiState.courseConfig?.showWeekends ?: false,
                    onCheckedChange = viewModel::onShowWeekendsChanged
                )
            )
        )
    ),
    SettingsSection(
        labelRes = Res.string.settings_group_about,
        entries = listOf(
            SettingsEntry(Res.string.item_more_options, Res.drawable.more_horiz_24px, SettingsEntryTone.BROWN, Destination.MoreOptions)
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
        item(key = "settings-$sectionIndex") {
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

