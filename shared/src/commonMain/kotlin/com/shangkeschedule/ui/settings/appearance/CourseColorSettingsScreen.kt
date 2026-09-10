package com.shangkeschedule.ui.settings.appearance

import com.shangkeschedule.ui.components.AppTopAppBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.ui.components.AdvancedColorPicker
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.ColorPickerConfig
import com.shangkeschedule.ui.settings.style.ColorPickerItem
import com.shangkeschedule.ui.settings.style.ColorSchemeSection
import com.shangkeschedule.ui.settings.style.StyleSettingsViewModel
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_reset_course_colors
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.dialog_reset_colors_message
import shangkeschedule.shared.generated.resources.dialog_reset_colors_title
import shangkeschedule.shared.generated.resources.item_course_color_settings
import shangkeschedule.shared.generated.resources.label_course_text_color
import shangkeschedule.shared.generated.resources.label_page_text_color
import shangkeschedule.shared.generated.resources.style_category_color_scheme
import shangkeschedule.shared.generated.resources.style_category_course_block
import shangkeschedule.shared.generated.resources.style_category_interface
import shangkeschedule.shared.generated.resources.title_dark_color_pool
import shangkeschedule.shared.generated.resources.title_light_color_pool

/**
 * 「个性化显示 → 个性化配色」三级页。
 *
 * 原先「课程颜色池 / 课程块文字颜色 / 页面文字颜色」散落在「自定义课表页」的网格尺寸与课程块分组之间，
 * 与布局项混在一起；现整块迁到此处独立成页，并新增「一键重置配色」入口
 * （把颜色池拉回当前主题预设的配色——预设配色在切换主题时会被快照进 DataStore，
 * 预设更新后老用户需手动拉回，见 `StyleSettingsViewModel.resetCourseColorMaps`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseColorSettingsScreen(
    onBack: () -> Unit,
    styleViewModel: StyleSettingsViewModel = koinViewModel()
) {
    val styleState by styleViewModel.styleState.collectAsStateWithLifecycle()

    var showColorPicker by remember { mutableStateOf(false) }
    var isDarkTarget by remember { mutableStateOf(false) }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }

    // 取色器面板玻璃：主内容 hazeSource
    val hazeState = rememberHazeState()

    Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
        Scaffold(
            topBar = {
                AppTopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.item_course_color_settings),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                vectorResource(Res.drawable.arrow_back_24px),
                                contentDescription = stringResource(Res.string.a11y_back)
                            )
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
                    .padding(appSpacing().pageHorizontal),
                verticalArrangement = Arrangement.spacedBy(appSpacing().listGap)
            ) {
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.action_reset_course_colors))
                }

                AppSectionHeader(stringResource(Res.string.style_category_color_scheme))
                ColorSchemeSection(
                    title = stringResource(Res.string.title_light_color_pool),
                    bgColor = lightColorScheme().surfaceContainerLow,
                    isDarkSection = false,
                    colors = styleState.courseColorMaps.map { it.light },
                    onEditColor = { index ->
                        isDarkTarget = false
                        selectedColorIndex = index
                        showColorPicker = true
                    }
                )
                ColorSchemeSection(
                    title = stringResource(Res.string.title_dark_color_pool),
                    bgColor = darkColorScheme().surfaceContainerLow,
                    isDarkSection = true,
                    colors = styleState.courseColorMaps.map { it.dark },
                    onEditColor = { index ->
                        isDarkTarget = true
                        selectedColorIndex = index
                        showColorPicker = true
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = appColors().divider, thickness = 0.5.dp)

                AppSectionHeader(stringResource(Res.string.style_category_course_block))
                ColorPickerItem(
                    label = stringResource(Res.string.label_course_text_color),
                    currentColor = styleState.courseTextColor,
                    onColorChanged = { styleViewModel.updateCourseTextColor(it) },
                    onReset = { styleViewModel.updateCourseTextColor(null) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = appColors().divider, thickness = 0.5.dp)

                AppSectionHeader(stringResource(Res.string.style_category_interface))
                ColorPickerItem(
                    label = stringResource(Res.string.label_page_text_color),
                    currentColor = styleState.pageTextColor,
                    onColorChanged = { styleViewModel.updatePageTextColor(it) },
                    onReset = { styleViewModel.updatePageTextColor(null) },
                    hazeState = hazeState
                )
            }
        }
    }

    // 一键重置配色确认：覆盖的是用户可能手动调过的颜色，先确认再执行
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(Res.string.dialog_reset_colors_title)) },
            text = { Text(stringResource(Res.string.dialog_reset_colors_message)) },
            confirmButton = {
                AppDialogActions(
                    confirmText = stringResource(Res.string.action_reset_course_colors),
                    onConfirm = {
                        styleViewModel.resetCourseColorMaps()
                        showResetDialog = false
                    },
                    dismissText = stringResource(Res.string.action_cancel),
                    onDismiss = { showResetDialog = false }
                )
            },
            dismissButton = {}
        )
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
