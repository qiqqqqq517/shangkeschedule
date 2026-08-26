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
import androidx.compose.material3.Card
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
import com.shangkeschedule.data.model.AppThemeMode
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.AdvancedColorPicker
import com.shangkeschedule.ui.components.ColorPickerConfig
import com.shangkeschedule.ui.components.ImageCropper
import com.shangkeschedule.ui.schedule.WeeklyScheduleUiState
import com.shangkeschedule.ui.schedule.components.ScheduleGridStyleComposed
import com.shangkeschedule.ui.settings.SettingsViewModel
import com.shangkeschedule.ui.settings.style.ScheduleGridContent
import com.shangkeschedule.ui.settings.style.SettingsListContent
import com.shangkeschedule.ui.settings.style.StyleSettingsViewModel
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.supportsDynamicColor
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.action_reset
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.custom_color_title
import shangkeschedule.shared.generated.resources.dark_primary_color
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
    settingsViewModel: SettingsViewModel = koinViewModel(),
    styleViewModel: StyleSettingsViewModel = koinViewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val settings = uiState.appSettings
    val styleState by styleViewModel.styleState.collectAsStateWithLifecycle()
    val demoUiState by styleViewModel.demoUiState.collectAsStateWithLifecycle()

    var showColorPicker by remember { mutableStateOf(false) }
    var isDarkTarget by remember { mutableStateOf(false) }
    var selectedColorIndex by remember { mutableIntStateOf(0) }

    var loadedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showCropper by remember { mutableStateOf(false) }

    // 切换主题确认：避免误操作覆盖用户个性化配置
    var pendingThemePreset by remember { mutableStateOf<AppThemePreset?>(null) }

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
        ) {
            // 1. 个性化配置预览固定在页面顶部
            AppearanceStylePreview(styleState, demoUiState)

            HorizontalDivider()

            // 2. 下方依次展示：主题风格 + 深色模式 + 个性化微调
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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

                    val customColorDisabled = supportsDynamicColor && settings.useDynamicColor
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppearanceSectionHeader(stringResource(Res.string.custom_color_title))
                        if (customColorDisabled) {
                            Text(
                                text = stringResource(Res.string.theme_color_disabled_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                enabled = !customColorDisabled
                            )
                        } else {
                            AppearanceThemeColorPickerItem(
                                label = stringResource(Res.string.light_primary_color),
                                currentColor = Color(settings.customLightPrimary),
                                onColorChanged = { settingsViewModel.onCustomLightPrimaryChanged(it) },
                                onReset = { settingsViewModel.onCustomLightPrimaryChanged() },
                                enabled = !customColorDisabled
                            )
                        }
                        Text(
                            text = stringResource(Res.string.theme_color_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                HorizontalDivider()

                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    AppearanceSectionHeader(stringResource(Res.string.item_personalization))
                }

                SettingsListContent(
                    currentStyle = styleState,
                    viewModel = styleViewModel,
                    onWallpaperClick = { fileManager.pickImage() },
                    modifier = Modifier.fillMaxWidth(),
                    scrollable = false
                ) { isDark, idx ->
                    isDarkTarget = isDark
                    selectedColorIndex = idx
                    showColorPicker = true
                }
            }
        }
    }

    if (showColorPicker) {
        ModalBottomSheet(
            onDismissRequest = { showColorPicker = false },
            sheetState = rememberModalBottomSheetState()
        ) {
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

    // 切换主题确认对话框：防止误操作覆盖个性化配置
    if (pendingThemePreset != null) {
        val targetPreset = pendingThemePreset!!
        AlertDialog(
            onDismissRequest = { pendingThemePreset = null },
            title = { Text("切换主题") },
            text = {
                Text("切换到「${stringResource(targetPreset.labelRes)}」将重置当前的个性化配置（圆角、间距、配色等），是否继续？")
            },
            confirmButton = {
                TextButton(onClick = {
                    settingsViewModel.onThemePresetChanged(targetPreset)
                    pendingThemePreset = null
                }) {
                    Text("确认切换", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingThemePreset = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun AppearanceStylePreview(
    currentStyle: ScheduleGridStyleComposed,
    demoUiState: WeeklyScheduleUiState
) {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val windowWidthDp = with(density) { containerSize.width.toDp() }
    val previewHeightDp = with(density) { containerSize.height.toDp() } * 0.30f

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeightDp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .horizontalScroll(rememberScrollState())
                .animateContentSize()
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppThemePreset.entries.forEach { preset ->
            val selected = preset == selectedPreset
            Surface(
                onClick = { onSelect(preset) },
                modifier = Modifier.weight(1f),
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
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceThemeModeSelector(
    selectedMode: AppThemeMode,
    onModeSelected: (AppThemeMode) -> Unit
) {
    val modes = AppThemeMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
            ) {
                Text(
                    text = stringResource(mode.labelRes),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AppearanceDynamicColorToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onEnabledChange(!enabled) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.dynamic_color_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.dynamic_color_desc), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceThemeColorPickerItem(
    label: String,
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
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
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
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
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
