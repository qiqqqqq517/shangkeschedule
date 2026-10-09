package com.shangkeschedule.ui.settings.appearance

import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppTopAppBar
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntSize
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.Destination
import com.shangkeschedule.data.model.AppThemeMode
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.AppCard
import com.shangkeschedule.ui.components.AppSegmentedControl
import com.shangkeschedule.ui.components.ImageCropper
import com.shangkeschedule.ui.schedule.WeeklyScheduleUiState
import com.shangkeschedule.ui.schedule.components.ScheduleGridStyleComposed
import com.shangkeschedule.ui.settings.SettingsViewModel
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SettingCard
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.theme.AccentTone
import com.shangkeschedule.ui.settings.style.ScheduleGridContent
import com.shangkeschedule.ui.settings.style.LocalSettingsScrollState
import com.shangkeschedule.ui.settings.style.SettingsListContent
import com.shangkeschedule.ui.settings.style.StyleSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import com.shangkeschedule.ui.components.AppDialogActions
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.appearance_switch_confirm
import shangkeschedule.shared.generated.resources.appearance_switch_message
import shangkeschedule.shared.generated.resources.appearance_switch_title
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.desc_schedule_style_settings
import shangkeschedule.shared.generated.resources.desc_theme_settings
import shangkeschedule.shared.generated.resources.desc_theme_share
import shangkeschedule.shared.generated.resources.image_24px
import shangkeschedule.shared.generated.resources.palette_24px
import shangkeschedule.shared.generated.resources.item_schedule_style_settings
import shangkeschedule.shared.generated.resources.item_personalized_display
import shangkeschedule.shared.generated.resources.desc_personalized_display
import shangkeschedule.shared.generated.resources.tune_24px
import shangkeschedule.shared.generated.resources.item_theme_settings
import shangkeschedule.shared.generated.resources.item_appearance_settings
import shangkeschedule.shared.generated.resources.item_personalization
import shangkeschedule.shared.generated.resources.theme_mode_label
import shangkeschedule.shared.generated.resources.theme_style_desc
import shangkeschedule.shared.generated.resources.section_share
import shangkeschedule.shared.generated.resources.theme_style_section
import shangkeschedule.shared.generated.resources.title_theme_share

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit = {}
) {
    // v3.24.0：本页改为「外观与样式」二级导航页——只承载两个入口（主题 / 自定义课表页），
    // 原主题配置与课表样式内容分别下沉到 ThemeSettingsScreen / ScheduleStyleSettingsScreen。
    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(text = stringResource(Res.string.item_appearance_settings), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = stringResource(Res.string.a11y_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = appSpacing().pageHorizontal, vertical = appSpacing().pageTop),
            verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap)
        ) {
            SettingCard(
                title = stringResource(Res.string.item_theme_settings),
                subtitle = stringResource(Res.string.desc_theme_settings),
                leadingIcon = vectorResource(Res.drawable.palette_24px),
                accent = AccentTone.PRIMARY,
                onClick = { onNavigate(Destination.ThemeSettings) }
            )
            SettingCard(
                title = stringResource(Res.string.item_schedule_style_settings),
                subtitle = stringResource(Res.string.desc_schedule_style_settings),
                leadingIcon = vectorResource(Res.drawable.image_24px),
                accent = AccentTone.INFO,
                onClick = { onNavigate(Destination.ScheduleStyleSettings) }
            )
            // v3.25.0：新增「个性化显示」——悬浮件（底栏/圆钮/挂起条）那一层的观感，
            // 与配色（主题页）、课表本体（自定义课表页）职责不同，单独成页
            SettingCard(
                title = stringResource(Res.string.item_personalized_display),
                subtitle = stringResource(Res.string.desc_personalized_display),
                leadingIcon = vectorResource(Res.drawable.tune_24px),
                accent = AccentTone.SUCCESS,
                onClick = { onNavigate(Destination.PersonalizedDisplay) }
            )
        }
    }
}

/**
 * 主题二级页：主题风格预设 / 深色模式。
 * 从「外观与样式」二级导航页进入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit = {},
    settingsViewModel: SettingsViewModel = koinViewModel(),
    styleViewModel: StyleSettingsViewModel = koinViewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.appSettings
    val styleState by styleViewModel.styleState.collectAsStateWithLifecycle()
    val demoUiState by styleViewModel.demoUiState.collectAsStateWithLifecycle()

    // 切换主题确认：避免误操作覆盖用户个性化配置
    var pendingThemePreset by remember { mutableStateOf<AppThemePreset?>(null) }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(text = stringResource(Res.string.item_theme_settings), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = stringResource(Res.string.a11y_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // 1. 主题预览固定在页面顶部（约 1/5 屏高，随主题色实时变化）
            AppearanceStylePreview(styleState, demoUiState, heightFraction = 0.20f)

            HorizontalDivider(color = appColors().divider, thickness = 0.5.dp)

            // 2. 主题配置滚动区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = appSpacing().pageHorizontal, vertical = appSpacing().pageTop),
                verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap)
            ) {
                AppearanceSectionHeader(stringResource(Res.string.theme_style_section))
                Text(
                    text = stringResource(Res.string.theme_style_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
                AppearancePresetSelector(
                    selectedPreset = settings.themePreset,
                    onSelect = { preset ->
                        if (preset != settings.themePreset) {
                            pendingThemePreset = preset
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = appSpacing().sectionTitleGap), color = appColors().divider, thickness = 0.5.dp)
                AppearanceSectionHeader(stringResource(Res.string.theme_mode_label))
                AppearanceThemeModeSelector(
                    selectedMode = settings.themeMode,
                    onModeSelected = { settingsViewModel.onThemeModeChanged(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = appSpacing().sectionTitleGap), color = appColors().divider, thickness = 0.5.dp)
                // v4.66.0（D3）：主题分享放在主题页页尾——它就是「切换主题」的对应动作，
                // 星链也把主题分享放在同一处；本页没有可追加行的既有卡片列表，
                // 故按本页「分区标题 + 卡片」的节奏单独成卡，不新开独立入口、不塞页顶。
                // v4.66.1（IA 再排）：本段原先只有卡片没有分区标题，与本页上方
                // 「主题风格 / 深色模式」两段（有 AppearanceSectionHeader）的节奏不齐，
                // 读起来像凭空多出来的一行。补上标题后与本页一致。
                AppearanceSectionHeader(stringResource(Res.string.section_share))
                SectionCard {
                    SettingItem(
                        title = stringResource(Res.string.title_theme_share),
                        subtitle = stringResource(Res.string.desc_theme_share),
                        leadingIcon = vectorResource(Res.drawable.palette_24px),
                        onClick = { onNavigate(Destination.ThemeShare) }
                    )
                }
                }
            }
        }
    }

    // 切换主题确认对话框：防止误操作覆盖个性化配置
    if (pendingThemePreset != null) {
        val targetPreset = pendingThemePreset!!
        AppAlertDialog(
            onDismissRequest = { pendingThemePreset = null },
            title = { Text(stringResource(Res.string.appearance_switch_title)) },
            text = {
                Text(stringResource(Res.string.appearance_switch_message, stringResource(targetPreset.labelRes)))
            },
            confirmButton = {
                AppDialogActions(
                    confirmText = stringResource(Res.string.appearance_switch_confirm),
                    onConfirm = {
                        settingsViewModel.onThemePresetChanged(targetPreset)
                        pendingThemePreset = null
                    },
                    dismissText = stringResource(Res.string.action_cancel),
                    onDismiss = { pendingThemePreset = null }
                )
            },
            dismissButton = {}
        )
    }
}

/**
 * 自定义课表页二级页：课表样式预览 / 壁纸 / 网格样式 / 课程功能色。
 * 从「外观与样式」二级导航页进入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleStyleSettingsScreen(
    onBack: () -> Unit,
    styleViewModel: StyleSettingsViewModel = koinViewModel()
) {
    val styleState by styleViewModel.styleState.collectAsStateWithLifecycle()
    val demoUiState by styleViewModel.demoUiState.collectAsStateWithLifecycle()

    // v4.75.12：手指按下任一滑块即进入「专注预览」态——全屏显示真实课表网格、
    // 只保留当前那一个滑块；松手（含手势被取消）即恢复原状。
    var activeSliderId by remember { mutableStateOf<String?>(null) }
    val isPreviewing = activeSliderId != null

    // v4.75.12：切换动效收口到全局令牌（与预览卡 resize 同一套值），避免额外魔法数字。
    val motion = LocalAppMotion.current
    val previewFadeSpec = remember(motion) {
        tween<Float>(durationMillis = motion.tokens.resizeDurationMs, easing = motion.tokens.resizeEasing)
    }
    val previewAlpha by animateFloatAsState(
        targetValue = if (isPreviewing) 1f else 0f,
        animationSpec = previewFadeSpec,
        label = "focusPreviewAlpha"
    )

    var loadedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showCropper by remember { mutableStateOf(false) }

    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onImagePicked = { bitmap ->
                if (bitmap != null) {
                    loadedBitmap = bitmap
                    showCropper = true
                }
            }
        )
    )

    if (showCropper && loadedBitmap != null) {
        val containerSize = LocalWindowInfo.current.containerSize
        val screenAspectRatio = if (containerSize.height > 0) {
            containerSize.width.toFloat() / containerSize.height.toFloat()
        } else {
            1f
        }
        ImageCropper(
            imageBitmap = loadedBitmap!!,
            aspectRatio = screenAspectRatio,
            onCropConfirmed = { bytes ->
                styleViewModel.saveCroppedWallpaper(bytes)
                showCropper = false
                loadedBitmap = null
            },
            onDismiss = {
                showCropper = false
                loadedBitmap = null
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(text = stringResource(Res.string.item_schedule_style_settings), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = stringResource(Res.string.a11y_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // v4.75.12 底层：全屏真实课表预览（专注预览态淡入）。
            // ★ 常驻挂载、只用 alpha 控制显隐：若写成 `if (previewAlpha > 0f)`，
            // 淡入淡出跨越 0 时节点会被增删——在手势进行中插入整棵 ScheduleGrid 组件树
            // 可能干扰拖动。预览内部不可交互（ScheduleGridContent 已吞掉触摸），
            // 置于内容之下，上层滑块照常直接拖动。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(previewAlpha)
            ) {
                AppearanceFullScreenPreview(styleState, demoUiState)
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // 专注预览态下把这一整列（预览卡 + 分隔线 + 设置列表）整体淡出，只留下
                // SettingsListContent 里那个「当前滑块」以 alpha=1 显示——所有非当前元素
                // （含这里的小窗预览卡与分隔线、AppearanceSectionHeader 标题）统一走同一条
                // 淡出路径，避免逐个加 dimmed 时漏掉某一条在全屏预览上留下可见残影（曾表现为
                // 全屏中间横着一条「裂缝」）。alpha 不改变占位尺寸，故滑块位置与手势不受影响。
                val contentDim = Modifier.alpha(if (isPreviewing) 0f else 1f)

                // 1. 个性化配置预览固定在页面顶部
                AppearanceStylePreview(
                    styleState,
                    demoUiState,
                    modifier = contentDim
                )

                HorizontalDivider(
                    modifier = contentDim,
                    color = appColors().divider,
                    thickness = 0.5.dp
                )

                // 2. 下方为课表页个性化微调（壁纸 / 网格尺寸 / 课程块外观）。
                //    课程配色（颜色池 / 课程块与页面文字颜色）已整块迁至「个性化显示 → 个性化配色」页。
                //
                // v4.76.12：滚动状态提到变量并 provide 给下面的滑块。
                // 原因：M3 Slider 的 sliderTapModifier 用 detectTapGestures，会在按下时 consume
                // 掉 down，本容器因此收不到、无法启动滚动 ⇒「手指按在滑块上时页面滑不动」。
                // 既然容器收不到事件，改由滑块在 Initial pass 抢先判定竖直意图并代滚
                //（见 StyleSliderItem 的 scrollState 参数）。这里把 scrollState 提供给它。
                val settingsScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // ★ 保留恒定 verticalScroll，不随预览态开关其 enabled。
                        // 历史教训：v4.76.7 移除 enabled = !isPreviewing —— 按下瞬间该参数翻转
                        // 会让滚动容器的手势节点重建，**当场中断正在进行的滑块拖动**
                        //（表现为「全屏闪一下就没了 + 滑块拖不动」）。
                        // ★ 同族教训：本功能已连续 5 次栽在「按下瞬间改变某个 modifier/参数」上
                        //（pointerInput key、Slider enabled、父级吞指针、verticalScroll enabled、
                        //  预览层条件渲染）。拖动期间**不得**改变任何祖先或自身的可交互参数。
                        .verticalScroll(settingsScrollState)
                ) {
                    CompositionLocalProvider(
                        LocalSettingsScrollState provides settingsScrollState
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(contentDim)
                                .padding(horizontal = appSpacing().pageHorizontal, vertical = appSpacing().pageTop)
                        ) {
                            AppearanceSectionHeader(stringResource(Res.string.item_personalization))
                        }

                    SettingsListContent(
                        currentStyle = styleState,
                        viewModel = styleViewModel,
                        onWallpaperClick = { fileManager.pickImage() },
                        modifier = Modifier.fillMaxWidth(),
                        scrollable = false,
                        activeSliderId = activeSliderId,
                        onSliderDragState = { id, pressed ->
                            activeSliderId = if (pressed) id else null
                        }
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceStylePreview(
    currentStyle: ScheduleGridStyleComposed,
    demoUiState: WeeklyScheduleUiState,
    // v3.24.1：高度占比参数化——自定义课表页 0.30，主题页 0.20（用户要求约 1/5 屏）
    heightFraction: Float = 0.30f,
    // v4.75.12：供专注预览态淡出小窗（只改 alpha、不改高度，避免滑块跳位）
    modifier: Modifier = Modifier
) {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val windowWidthDp = with(density) { containerSize.width.toDp() }
    val previewHeightDp = with(density) { containerSize.height.toDp() } * heightFraction
    // v3.26.0 动效收口：预览尺寸变化读全局令牌 resizeDurationMs/resizeEasing
    val motion = LocalAppMotion.current
    val resizeSpec = remember(motion) {
        tween<IntSize>(durationMillis = motion.tokens.resizeDurationMs, easing = motion.tokens.resizeEasing)
    }

    AppCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = appSpacing().pageHorizontal, vertical = appSpacing().pageTop)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeightDp)
                .background(appColors().primarySoft.copy(alpha = 0.3f))
                .horizontalScroll(rememberScrollState())
                .animateContentSize(animationSpec = resizeSpec)
        ) {
            Box(modifier = Modifier.requiredWidth(windowWidthDp)) {
                ScheduleGridContent(currentStyle, demoUiState)
            }
        }
    }
}

/**
 * v4.75.12 专注预览：铺满内容区的**真实课表网格**。
 *
 * 与顶部小窗预览共用同一个 [ScheduleGridContent]（内部即真实 `ScheduleGrid`，
 * 交互回调置空），因此「全屏所见」与「实际课表页」逐像素一致，样式改动即时可见。
 *
 * 触摸：ScheduleGridContent 内部有一层吞掉长按/点按的透明层，故本预览**不抢手势**，
 * 手指仍落在上层滑块上正常拖动。
 */
@Composable
private fun AppearanceFullScreenPreview(
    currentStyle: ScheduleGridStyleComposed,
    demoUiState: WeeklyScheduleUiState
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScheduleGridContent(currentStyle, demoUiState)
    }
}

@Composable
private fun AppearanceSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
    )
}

@Composable
private fun AppearancePresetSelector(
    selectedPreset: AppThemePreset,
    onSelect: (AppThemePreset) -> Unit
) {
    // 预设数量会随主题增加（经典/云舒/通透/Claude …），等分 Row 在窄屏会把
    // 标签挤到换行甚至截断。改为横向可滚动 + 固定项宽：项数与屏幕宽度解耦，
    // 5 个及以上预设也不会挤压，宽屏下同样完整可见。
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(appSpacing().sectionTitleGap)
    ) {
        AppThemePreset.entries.forEach { preset ->
            val selected = preset == selectedPreset
            Surface(
                onClick = { onSelect(preset) },
                modifier = Modifier.width(84.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = appSpacing().cardInner, horizontal = appSpacing().sectionTitleGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(appSpacing().sectionTitleGap)
                ) {
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(preset.seedColor)
                    )
                    Text(
                        text = stringResource(preset.labelRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                    Text(
                        text = if (selected) "✓" else " ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceThemeModeSelector(
    selectedMode: AppThemeMode,
    onModeSelected: (AppThemeMode) -> Unit
) {
    val modes = AppThemeMode.entries
    // 基线分段控件：浅灰胶囊容器 + 白色选中胶囊（v2 规范 §2）
    AppSegmentedControl(
        options = modes.map { stringResource(it.labelRes) },
        selectedIndex = modes.indexOfFirst { it == selectedMode }.coerceAtLeast(0),
        onSelect = { index -> modes.getOrNull(index)?.let(onModeSelected) },
        modifier = Modifier.fillMaxWidth()
    )
}

