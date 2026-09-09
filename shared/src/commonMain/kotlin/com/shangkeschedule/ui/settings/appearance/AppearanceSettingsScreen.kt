package com.shangkeschedule.ui.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntSize
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
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
import com.shangkeschedule.ui.components.AdvancedColorPicker
import com.shangkeschedule.ui.components.AppCard
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import com.shangkeschedule.ui.components.AppSegmentedControl
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.components.ColorPickerConfig
import com.shangkeschedule.ui.components.ImageCropper
import com.shangkeschedule.ui.schedule.WeeklyScheduleUiState
import com.shangkeschedule.ui.schedule.components.ScheduleGridStyleComposed
import com.shangkeschedule.ui.settings.SettingsViewModel
import com.shangkeschedule.ui.settings.SettingCard
import com.shangkeschedule.ui.theme.AccentTone
import com.shangkeschedule.ui.settings.style.ScheduleGridContent
import com.shangkeschedule.ui.settings.style.SettingsListContent
import com.shangkeschedule.ui.settings.style.StyleSettingsViewModel
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.supportsDynamicColor
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
import shangkeschedule.shared.generated.resources.action_reset
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.custom_color_title
import shangkeschedule.shared.generated.resources.dark_primary_color
import shangkeschedule.shared.generated.resources.desc_schedule_style_settings
import shangkeschedule.shared.generated.resources.desc_theme_settings
import shangkeschedule.shared.generated.resources.image_24px
import shangkeschedule.shared.generated.resources.palette_24px
import shangkeschedule.shared.generated.resources.item_schedule_style_settings
import shangkeschedule.shared.generated.resources.item_personalized_display
import shangkeschedule.shared.generated.resources.desc_personalized_display
import shangkeschedule.shared.generated.resources.tune_24px
import shangkeschedule.shared.generated.resources.item_theme_settings
import shangkeschedule.shared.generated.resources.dynamic_color_desc
import shangkeschedule.shared.generated.resources.dynamic_color_title
import shangkeschedule.shared.generated.resources.item_appearance_settings
import shangkeschedule.shared.generated.resources.item_personalization
import shangkeschedule.shared.generated.resources.light_primary_color
import shangkeschedule.shared.generated.resources.refresh_24px
import shangkeschedule.shared.generated.resources.theme_color_hint
import shangkeschedule.shared.generated.resources.theme_color_disabled_hint
import shangkeschedule.shared.generated.resources.theme_mode_label
import shangkeschedule.shared.generated.resources.theme_style_desc
import shangkeschedule.shared.generated.resources.theme_style_section

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
            TopAppBar(
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
 * 主题二级页：主题风格预设 / 深色模式 / 动态取色 / 自定义主题色。
 * 从「外观与样式」二级导航页进入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = koinViewModel(),
    styleViewModel: StyleSettingsViewModel = koinViewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val settings = uiState.appSettings
    val styleState by styleViewModel.styleState.collectAsStateWithLifecycle()
    val demoUiState by styleViewModel.demoUiState.collectAsStateWithLifecycle()

    // 切换主题确认：避免误操作覆盖用户个性化配置
    var pendingThemePreset by remember { mutableStateOf<AppThemePreset?>(null) }

    // 取色器面板玻璃：主内容 hazeSource
    val hazeState = rememberHazeState()

    Scaffold(
        topBar = {
            TopAppBar(
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
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = appColors().divider, thickness = 0.5.dp)
                AppearanceSectionHeader(stringResource(Res.string.theme_mode_label))
                AppearanceThemeModeSelector(
                    selectedMode = settings.themeMode,
                    onModeSelected = { settingsViewModel.onThemeModeChanged(it) }
                )

                if (supportsDynamicColor) {
                    AppearanceDynamicColorToggle(
                        enabled = settings.useDynamicColor,
                        onEnabledChange = { settingsViewModel.onUseDynamicColorChanged(it) }
                    )
                }

                val customColorUsesDynamic = supportsDynamicColor && settings.useDynamicColor
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppearanceSectionHeader(stringResource(Res.string.custom_color_title))
                    if (customColorUsesDynamic) {
                        Text(
                            text = stringResource(Res.string.theme_color_disabled_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    val isDark = LocalIsDarkTheme.current
                    if (isDark) {
                        AppearanceThemeColorPickerItem(
                            label = stringResource(Res.string.dark_primary_color),
                            currentColor = Color(settings.customDarkPrimary),
                            onColorChanged = { settingsViewModel.onCustomDarkPrimaryChanged(it) },
                            onReset = { settingsViewModel.onCustomDarkPrimaryChanged() },
                            enabled = true,
                            hazeState = hazeState
                        )
                    } else {
                        AppearanceThemeColorPickerItem(
                            label = stringResource(Res.string.light_primary_color),
                            currentColor = Color(settings.customLightPrimary),
                            onColorChanged = { settingsViewModel.onCustomLightPrimaryChanged(it) },
                            onReset = { settingsViewModel.onCustomLightPrimaryChanged() },
                            enabled = true,
                            hazeState = hazeState
                        )
                    }
                    Text(
                        text = stringResource(Res.string.theme_color_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                }
            }
        }
    }

    // 切换主题确认对话框：防止误操作覆盖个性化配置
    if (pendingThemePreset != null) {
        val targetPreset = pendingThemePreset!!
        AlertDialog(
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

    var showColorPicker by remember { mutableStateOf(false) }
    var isDarkTarget by remember { mutableStateOf(false) }
    var selectedColorIndex by remember { mutableIntStateOf(0) }

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

    // 悬浮面板玻璃：主内容 hazeSource，功能色取色器面板背板模糊
    val hazeState = rememberHazeState()

    Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.item_schedule_style_settings), style = MaterialTheme.typography.titleLarge) },
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
            // 1. 个性化配置预览固定在页面顶部
            AppearanceStylePreview(styleState, demoUiState)

            HorizontalDivider(color = appColors().divider, thickness = 0.5.dp)

            // 2. 下方为课表页个性化微调（壁纸 / 样式 / 功能色）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = appSpacing().pageHorizontal, vertical = 8.dp)
                ) {
                    AppearanceSectionHeader(stringResource(Res.string.item_personalization))
                }

                SettingsListContent(
                    currentStyle = styleState,
                    viewModel = styleViewModel,
                    onWallpaperClick = { fileManager.pickImage() },
                    modifier = Modifier.fillMaxWidth(),
                    scrollable = false,
                    hazeState = hazeState
                ) { isDark, idx ->
                    isDarkTarget = isDark
                    selectedColorIndex = idx
                    showColorPicker = true
                }
            }
        }
    }
    }

    if (showColorPicker) {
        AppGlassBottomSheet(
            hazeState = hazeState,
            onDismissRequest = { showColorPicker = false }
        ) {
            // 功能色（豁免声明）：取色器无色池可用时的兜底初始值，
            // 仅作为拾色起点、不作为界面文字/图标颜色，不随主题 token。
            val initialColor = styleState.courseColorMaps.getOrNull(selectedColorIndex)?.let { pair ->
                if (isDarkTarget) pair.dark else pair.light
            } ?: Color.Gray

            var currentColorInPicker by remember { mutableStateOf(initialColor) }

            AdvancedColorPicker(
                initialColor = initialColor,
                config = ColorPickerConfig(showAlpha = false),
                onColorChanged = { newColor ->
                    currentColorInPicker = newColor
                    styleViewModel.updatePrimaryColor(selectedColorIndex, newColor, isDarkTarget)
                }
            )
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun AppearanceStylePreview(
    currentStyle: ScheduleGridStyleComposed,
    demoUiState: WeeklyScheduleUiState,
    // v3.24.1：高度占比参数化——自定义课表页 0.30，主题页 0.20（用户要求约 1/5 屏）
    heightFraction: Float = 0.30f
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = appSpacing().pageHorizontal, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeightDp)
                .background(appColors().primarySoft.copy(alpha = 0.3f))
                .horizontalScroll(rememberScrollState())
                .animateContentSize(animationSpec = resizeSpec)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
        ) {
            Box(modifier = Modifier.requiredWidth(windowWidthDp)) {
                ScheduleGridContent(currentStyle, demoUiState)
            }
        }
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
    // 预设数量会随主题增加（经典/云舒/利落/通透/Claude …），等分 Row 在窄屏会把
    // 标签挤到换行甚至截断。改为横向可滚动 + 固定项宽：项数与屏幕宽度解耦，
    // 5 个及以上预设也不会挤压，宽屏下同样完整可见。
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

@Composable
private fun AppearanceDynamicColorToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onEnabledChange(!enabled) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.dynamic_color_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.dynamic_color_desc), style = MaterialTheme.typography.bodySmall)
            }
            AppSwitch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceThemeColorPickerItem(
    label: String,
    hazeState: HazeState? = null,
    currentColor: Color,
    onColorChanged: (Color) -> Unit,
    onReset: () -> Unit,
    enabled: Boolean = true
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Surface(
        onClick = { if (enabled) showSheet = true },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.alpha(if (enabled) 1f else 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(currentColor)
            )
        }
    }

    if (showSheet) {
        AppGlassBottomSheet(
            hazeState = hazeState,
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 40.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onReset) {
                        Icon(vectorResource(Res.drawable.refresh_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.action_reset))
                    }
                }
                AdvancedColorPicker(
                    initialColor = currentColor,
                    onColorChanged = onColorChanged,
                    config = ColorPickerConfig(
                        showAlpha = false,
                        showInputMode = true
                    )
                )
            }
        }
    }
}
