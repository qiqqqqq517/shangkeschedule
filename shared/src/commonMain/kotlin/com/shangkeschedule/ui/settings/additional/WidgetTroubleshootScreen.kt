package com.shangkeschedule.ui.settings.additional

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppEmptyState
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.tool.OemGuide
import com.shangkeschedule.tool.WidgetTroubleshootBridge
import com.shangkeschedule.tool.textRes
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.action_close
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.build_24px
import shangkeschedule.shared.generated.resources.info_24px
import shangkeschedule.shared.generated.resources.sync_alt_24px
import shangkeschedule.shared.generated.resources.title_widget_troubleshoot
import shangkeschedule.shared.generated.resources.tune_24px
import shangkeschedule.shared.generated.resources.widget_oem_guide_open_settings
import shangkeschedule.shared.generated.resources.widget_oem_guide_step_fmt
import shangkeschedule.shared.generated.resources.widget_oem_guide_summary
import shangkeschedule.shared.generated.resources.widget_oem_guide_summary_title
import shangkeschedule.shared.generated.resources.widget_oem_guide_title
import shangkeschedule.shared.generated.resources.widget_troubleshoot_add_desc
import shangkeschedule.shared.generated.resources.widget_troubleshoot_added_count
import shangkeschedule.shared.generated.resources.widget_troubleshoot_help
import shangkeschedule.shared.generated.resources.widget_troubleshoot_help_desc
import shangkeschedule.shared.generated.resources.widget_troubleshoot_help_message
import shangkeschedule.shared.generated.resources.widget_troubleshoot_help_title
import shangkeschedule.shared.generated.resources.widget_troubleshoot_open_settings
import shangkeschedule.shared.generated.resources.widget_troubleshoot_open_settings_desc
import shangkeschedule.shared.generated.resources.widget_troubleshoot_placed
import shangkeschedule.shared.generated.resources.widget_troubleshoot_placed_value
import shangkeschedule.shared.generated.resources.widget_troubleshoot_rebuild
import shangkeschedule.shared.generated.resources.widget_troubleshoot_rebuild_desc
import shangkeschedule.shared.generated.resources.widget_troubleshoot_refresh
import shangkeschedule.shared.generated.resources.widget_troubleshoot_refresh_desc
import shangkeschedule.shared.generated.resources.widget_troubleshoot_section_actions
import shangkeschedule.shared.generated.resources.widget_troubleshoot_section_add
import shangkeschedule.shared.generated.resources.widget_troubleshoot_section_status
import shangkeschedule.shared.generated.resources.widget_troubleshoot_snapshot
import shangkeschedule.shared.generated.resources.widget_troubleshoot_snapshot_value
import shangkeschedule.shared.generated.resources.widget_troubleshoot_tip
import shangkeschedule.shared.generated.resources.widget_troubleshoot_toast_failed
import shangkeschedule.shared.generated.resources.widget_troubleshoot_toast_nothing_placed
import shangkeschedule.shared.generated.resources.widget_troubleshoot_toast_pin_rejected
import shangkeschedule.shared.generated.resources.widget_troubleshoot_toast_pin_requested
import shangkeschedule.shared.generated.resources.widget_troubleshoot_toast_rebuilt
import shangkeschedule.shared.generated.resources.widget_troubleshoot_toast_refreshed
import shangkeschedule.shared.generated.resources.widget_troubleshoot_toast_unsupported
import shangkeschedule.shared.generated.resources.widget_troubleshoot_unsupported
import shangkeschedule.shared.generated.resources.widget_troubleshoot_unsupported_desc
import shangkeschedule.shared.generated.resources.widget_troubleshoot_week
import shangkeschedule.shared.generated.resources.widget_troubleshoot_week_unset
import shangkeschedule.shared.generated.resources.widget_troubleshoot_week_value

/**
 * K7「小组件排障」（v4.66.0，开发者模式下可见）。
 *
 * 星链课表有一个小组件调试页（`widget_debug_page`：「小组件调试工具」「测试更新失败」），
 * 上课这边的诉求更朴素：小组件不刷新时，用户和我们都无法判断是「没添加」「快照为空」
 * 还是「系统限制了后台」。这一页把这三件事都摊开：
 *
 * 1. **看**：已放置的小组件数、快照里未来 7 天的课程条数、快照推算出的当前周；
 * 2. **修**：重建快照 + 请求重绘（等价于把小组件删掉重挂），以及只请求重绘；
 * 3. **解释**：跳系统设置放开自启动 / 后台限制，并列出最常见的四类原因。
 *
 * 这里故意不做「测试更新失败」这类人为制造错误的按钮 —— 排障页给的每个动作都必须真的有用且无副作用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetTroubleshootScreen(
    onBack: () -> Unit,
    viewModel: WidgetTroubleshootViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var showHelp by remember { mutableStateOf(false) }

    val toastRebuilt = stringResource(Res.string.widget_troubleshoot_toast_rebuilt)
    val toastRefreshed = stringResource(Res.string.widget_troubleshoot_toast_refreshed)
    val toastNothingPlaced = stringResource(Res.string.widget_troubleshoot_toast_nothing_placed)
    val toastFailed = stringResource(Res.string.widget_troubleshoot_toast_failed)
    val toastUnsupported = stringResource(Res.string.widget_troubleshoot_toast_unsupported)
    val toastPinRequested = stringResource(Res.string.widget_troubleshoot_toast_pin_requested)
    val toastPinRejected = stringResource(Res.string.widget_troubleshoot_toast_pin_rejected)

    LaunchedEffect(uiState.toast) {
        val toast = uiState.toast ?: return@LaunchedEffect
        ToastManager.show(
            when (toast) {
                WidgetTroubleshootToast.REBUILT -> toastRebuilt
                WidgetTroubleshootToast.REFRESHED -> toastRefreshed
                WidgetTroubleshootToast.NOTHING_PLACED -> toastNothingPlaced
                WidgetTroubleshootToast.FAILED -> toastFailed
                WidgetTroubleshootToast.UNSUPPORTED -> toastUnsupported
                WidgetTroubleshootToast.PIN_REQUESTED -> toastPinRequested
                WidgetTroubleshootToast.PIN_REJECTED -> toastPinRejected
            }
        )
        viewModel.consumeToast()
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(stringResource(Res.string.title_widget_troubleshoot)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back),
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(
                    horizontal = appSpacing().pageHorizontal,
                    vertical = appSpacing().listGap,
                ),
            verticalArrangement = Arrangement.spacedBy(appSpacing().listGap),
        ) {
            if (uiState.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (uiState.isReady && !uiState.supported) {
                // 桌面版 / iOS：没有小组件宿主，如实说不支持，不给任何「刷新」按钮。
                AppSectionHeader(text = stringResource(Res.string.widget_troubleshoot_unsupported))
                AppEmptyState(hint = stringResource(Res.string.widget_troubleshoot_unsupported_desc))
            }

            // XL-013：排障只能告诉用户「有问题」，这里补上「怎么一步步解决」。
            // 只在解析出非空步骤时渲染：未识别厂商不给猜测路径。
            val oemGuide = uiState.oemGuide
            if (oemGuide != null) {
                OemGuideSection(guide = oemGuide)
            }

            if (uiState.isReady && uiState.supported) {
                AppSectionHeader(text = stringResource(Res.string.widget_troubleshoot_section_status))
                SectionCard {
                    SettingItem(
                        title = stringResource(Res.string.widget_troubleshoot_placed),
                        subtitle = stringResource(
                            Res.string.widget_troubleshoot_placed_value,
                            uiState.placedCount,
                            uiState.providerCount,
                        ),
                        trailingContent = {},
                    )
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.widget_troubleshoot_snapshot),
                        subtitle = stringResource(
                            Res.string.widget_troubleshoot_snapshot_value,
                            uiState.snapshotCourseCount,
                        ),
                        trailingContent = {},
                    )
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.widget_troubleshoot_week),
                        subtitle = uiState.currentWeek?.let {
                            stringResource(Res.string.widget_troubleshoot_week_value, it)
                        } ?: stringResource(Res.string.widget_troubleshoot_week_unset),
                        trailingContent = {},
                    )
                }

                // XL-015：还没添加过小组件的用户，此前只能退回桌面长按。
                // 放在排障操作之前 —— 「先添加」是「再排障」的前提。
                val specs = uiState.specs
                if (specs.isNotEmpty()) {
                    AppSectionHeader(text = stringResource(Res.string.widget_troubleshoot_section_add))
                    SectionCard {
                        val addDesc = stringResource(Res.string.widget_troubleshoot_add_desc)
                        specs.forEachIndexed { index, spec ->
                            if (index > 0) SectionDivider()
                            SettingItem(
                                title = spec.label,
                                subtitle = when {
                                    // 已添加过的显示个数 —— 这时副标题的用途是告知现状，
                                    // 「点一下添加」那句话只在还没添加时才有意义。
                                    spec.placedCount > 0 -> stringResource(
                                        Res.string.widget_troubleshoot_added_count,
                                        spec.placedCount,
                                    )
                                    else -> addDesc
                                },
                                enabled = !uiState.busy,
                                onClick = { viewModel.pin(spec.key) },
                            )
                        }
                    }
                }

                AppSectionHeader(text = stringResource(Res.string.widget_troubleshoot_section_actions))
                SectionCard {
                    SettingItem(
                        title = stringResource(Res.string.widget_troubleshoot_rebuild),
                        subtitle = stringResource(Res.string.widget_troubleshoot_rebuild_desc),
                        leadingIcon = vectorResource(Res.drawable.build_24px),
                        onClick = viewModel::rebuild,
                    )
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.widget_troubleshoot_refresh),
                        subtitle = stringResource(Res.string.widget_troubleshoot_refresh_desc),
                        leadingIcon = vectorResource(Res.drawable.sync_alt_24px),
                        onClick = viewModel::refreshOnly,
                    )
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.widget_troubleshoot_open_settings),
                        subtitle = stringResource(Res.string.widget_troubleshoot_open_settings_desc),
                        leadingIcon = vectorResource(Res.drawable.tune_24px),
                        onClick = viewModel::openSystemSettings,
                    )
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.widget_troubleshoot_help),
                        subtitle = stringResource(Res.string.widget_troubleshoot_help_desc),
                        leadingIcon = vectorResource(Res.drawable.info_24px),
                        onClick = { showHelp = true },
                    )
                }

                Text(
                    text = stringResource(Res.string.widget_troubleshoot_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary,
                )
            }
        }
    }

    if (showHelp) {
        AppAlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(Res.string.widget_troubleshoot_help_title)) },
            text = { Text(stringResource(Res.string.widget_troubleshoot_help_message)) },
            confirmButton = {
                AppDialogActions(
                    confirmText = stringResource(Res.string.action_close),
                    onConfirm = { showHelp = false }
                )
            },
        )
    }
}

/**
 * 厂商后台限制分步引导（XL-013）。
 *
 * 整个区块只在 [OemGuideResolver] 返回非空步骤时渲染；
 * 未识别厂商不显示 —— 给错路径比不给更伤用户信任。
 *
 * 全部用 [SettingItem] 行搭建，不自行 `padding`：行内边距由 SettingItem 统一
 * 维护，手写 `padding` 会与 token 进行体系化脱结。
 * 文案从 StringResource 取，四语维护在 composeResources，不在 commonMain 里硬编码中文。
 *
 * v4.67.36：新增「一键打开自启动设置」入口。
 * 此前本区块是**纯文案**——用户读到了「允许关联启动」，却要自己去找那个开关在哪，
 * 而国产 ROM 的自启动页往往深达三级且入口随版本漂移（这正是竞品星链课表
 * 用 `OemReminderGuide.startupIntents()` 解决的问题）。跳转由
 * [WidgetTroubleshootBridge.openOemStartupSettings] 承担，它内部逐个试探厂商私有入口、
 * 全部失败再回退应用详情页；非 Android 平台恒 false，故此处只在 Android 显示该行。
 */
@Composable
private fun OemGuideSection(guide: OemGuide) {
    AppSectionHeader(text = stringResource(Res.string.widget_oem_guide_title))
    SectionCard {
        SettingItem(
            title = stringResource(Res.string.widget_oem_guide_summary_title),
            subtitle = stringResource(Res.string.widget_oem_guide_summary),
            trailingContent = {},
        )
        guide.steps.forEachIndexed { index, step ->
            SectionDivider()
            SettingItem(
                title = stringResource(Res.string.widget_oem_guide_step_fmt, index + 1),
                subtitle = stringResource(step.textRes()),
                trailingContent = {},
            )
        }
        // 「一键打开」放在步骤之后而不是之前：用户先知道要做什么，点下去才知道去哪做。
        if (WidgetTroubleshootBridge.manufacturer() != null) {
            SectionDivider()
            SettingItem(
                title = stringResource(Res.string.widget_oem_guide_open_settings),
                onClick = { WidgetTroubleshootBridge.openOemStartupSettings() },
            )
        }
    }
}
