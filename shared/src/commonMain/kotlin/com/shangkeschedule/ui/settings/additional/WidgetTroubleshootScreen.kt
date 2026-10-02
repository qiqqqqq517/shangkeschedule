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
import shangkeschedule.shared.generated.resources.widget_troubleshoot_section_status
import shangkeschedule.shared.generated.resources.widget_troubleshoot_snapshot
import shangkeschedule.shared.generated.resources.widget_troubleshoot_snapshot_value
import shangkeschedule.shared.generated.resources.widget_troubleshoot_tip
import shangkeschedule.shared.generated.resources.widget_troubleshoot_toast_failed
import shangkeschedule.shared.generated.resources.widget_troubleshoot_toast_nothing_placed
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

    LaunchedEffect(uiState.toast) {
        val toast = uiState.toast ?: return@LaunchedEffect
        ToastManager.show(
            when (toast) {
                WidgetTroubleshootToast.REBUILT -> toastRebuilt
                WidgetTroubleshootToast.REFRESHED -> toastRefreshed
                WidgetTroubleshootToast.NOTHING_PLACED -> toastNothingPlaced
                WidgetTroubleshootToast.FAILED -> toastFailed
                WidgetTroubleshootToast.UNSUPPORTED -> toastUnsupported
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
