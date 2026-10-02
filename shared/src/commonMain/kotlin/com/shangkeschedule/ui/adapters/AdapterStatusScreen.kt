package com.shangkeschedule.ui.adapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.data.model.AdapterSyncRecord
import com.shangkeschedule.tool.AppExternalLinks
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
import shangkeschedule.shared.generated.resources.adapter_remote_update_failed
import shangkeschedule.shared.generated.resources.adapter_request_form_unavailable
import shangkeschedule.shared.generated.resources.adapter_status_adapter
import shangkeschedule.shared.generated.resources.adapter_status_apply
import shangkeschedule.shared.generated.resources.adapter_status_apply_desc
import shangkeschedule.shared.generated.resources.adapter_status_check_now
import shangkeschedule.shared.generated.resources.adapter_status_folder_missing
import shangkeschedule.shared.generated.resources.adapter_status_folder_scripts
import shangkeschedule.shared.generated.resources.adapter_status_last_sync
import shangkeschedule.shared.generated.resources.adapter_status_local_scripts
import shangkeschedule.shared.generated.resources.adapter_status_local_schools
import shangkeschedule.shared.generated.resources.adapter_status_never_synced
import shangkeschedule.shared.generated.resources.adapter_status_note
import shangkeschedule.shared.generated.resources.adapter_status_repo_missing
import shangkeschedule.shared.generated.resources.adapter_status_repo_ready
import shangkeschedule.shared.generated.resources.adapter_status_res_title
import shangkeschedule.shared.generated.resources.adapter_status_school_none
import shangkeschedule.shared.generated.resources.adapter_status_selected_title
import shangkeschedule.shared.generated.resources.adapter_status_sync_title
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.build_24px
import shangkeschedule.shared.generated.resources.category_bachelor_associate
import shangkeschedule.shared.generated.resources.category_general_tool
import shangkeschedule.shared.generated.resources.category_other
import shangkeschedule.shared.generated.resources.category_postgraduate
import shangkeschedule.shared.generated.resources.info_24px
import shangkeschedule.shared.generated.resources.school_24px
import shangkeschedule.shared.generated.resources.sync_status_disabled
import shangkeschedule.shared.generated.resources.sync_status_failed
import shangkeschedule.shared.generated.resources.sync_status_syncing
import shangkeschedule.shared.generated.resources.sync_status_up_to_date
import shangkeschedule.shared.generated.resources.sync_status_updated
import shangkeschedule.shared.generated.resources.title_adapter_status
import school_index.AdapterCategory
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * 教务适配状态页（v4.66.0）。
 *
 * 只展示本机真实数据，不做承诺式进度：适配脚本数 / 学校索引数 / 离线仓库是否就绪 /
 * 已选学校命中的适配目录 / 上次检查结果，末尾给适配申请入口。
 * 远程适配清单目前没有学校维度的状态字段，因此这里不出现「已支持 / 适配中」字样。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdapterStatusScreen(
    onBack: () -> Unit,
    viewModel: AdapterStatusViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    // 文案在 Compose 上下文里先取好：onClick 里不能调 stringResource
    val formUnavailableText = stringResource(Res.string.adapter_request_form_unavailable)
    val applyLabel = stringResource(Res.string.adapter_status_apply)
    val applyDesc = stringResource(Res.string.adapter_status_apply_desc)

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(text = stringResource(Res.string.title_adapter_status)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = appSpacing().pageHorizontal,
                vertical = appSpacing().cardGap
            ),
            verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap)
        ) {
            // 本机适配资源
            item(key = "adapter-resources") {
                StatusSection(title = stringResource(Res.string.adapter_status_res_title)) {
                    SettingItem(
                        title = stringResource(Res.string.adapter_status_local_scripts, state.localScriptCount),
                        leadingIcon = vectorResource(Res.drawable.build_24px),
                        trailingContent = {}
                    )
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.adapter_status_local_schools, state.schoolIndexCount),
                        leadingIcon = vectorResource(Res.drawable.school_24px),
                        trailingContent = {}
                    )
                    SectionDivider()
                    SettingItem(
                        title = if (state.repoReady) {
                            stringResource(Res.string.adapter_status_repo_ready)
                        } else {
                            stringResource(Res.string.adapter_status_repo_missing)
                        },
                        leadingIcon = vectorResource(Res.drawable.info_24px),
                        trailingContent = {}
                    )
                }
            }

            // 已选学校与其适配目录
            item(key = "adapter-selected-schools") {
                StatusSection(title = stringResource(Res.string.adapter_status_selected_title)) {
                    SectionCard {
                        if (state.selectedSchools.isEmpty()) {
                            SettingItem(
                                title = stringResource(Res.string.adapter_status_school_none),
                                leadingIcon = vectorResource(Res.drawable.school_24px),
                                trailingContent = {}
                            )
                        } else {
                            state.selectedSchools.forEachIndexed { index, school ->
                                if (index > 0) SectionDivider()
                                SettingItem(
                                    title = "${categoryLabel(school.category)} · ${school.schoolName}",
                                    subtitle = schoolSubtitle(school),
                                    leadingIcon = vectorResource(Res.drawable.school_24px),
                                    trailingContent = {}
                                )
                            }
                        }
                    }
                }
            }

            // 适配更新（手动检查）
            item(key = "adapter-sync") {
                StatusSection(title = stringResource(Res.string.adapter_status_sync_title)) {
                    SectionCard {
                        SettingItem(
                            title = stringResource(Res.string.adapter_status_check_now),
                            subtitle = syncSubtitle(state),
                            leadingIcon = vectorResource(Res.drawable.school_24px),
                            trailingContent = {},
                            onClick = { viewModel.sync() }
                        )
                    }
                }
            }

            // 适配申请入口（与「更多」页共用金山表单）
            item(key = "adapter-apply") {
                SectionCard {
                    SettingItem(
                        title = applyLabel,
                        subtitle = applyDesc,
                        leadingIcon = vectorResource(Res.drawable.school_24px),
                        onClick = {
                            val form = AppExternalLinks.ADAPTER_REQUEST_FORM
                            if (form.isBlank()) {
                                ToastManager.show(formUnavailableText)
                            } else {
                                runCatching { uriHandler.openUri(form) }
                            }
                        }
                    )
                }
            }

            // 口径说明
            item(key = "adapter-note") {
                Text(
                    text = stringResource(Res.string.adapter_status_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/** 小节标题 + 卡片。 */
@Composable
private fun StatusSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = appColors().textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        content()
    }
}

@Composable
private fun categoryLabel(category: AdapterCategory): String = stringResource(
    when (category) {
        AdapterCategory.BACHELOR_AND_ASSOCIATE -> Res.string.category_bachelor_associate
        AdapterCategory.POSTGRADUATE -> Res.string.category_postgraduate
        AdapterCategory.GENERAL_TOOL -> Res.string.category_general_tool
        // protobuf 枚举带 UNKNOWN：与学校选择页保持一致的兜底文案
        else -> Res.string.category_other
    }
)

@Composable
private fun schoolSubtitle(school: SelectedSchoolStatus): String {
    val folderText = if (school.folder.isBlank()) {
        null
    } else {
        stringResource(Res.string.adapter_status_adapter, school.folder)
    }
    val scriptText = if (school.scriptCount > 0) {
        stringResource(Res.string.adapter_status_folder_scripts, school.scriptCount)
    } else {
        stringResource(Res.string.adapter_status_folder_missing)
    }
    return listOfNotNull(folderText, scriptText).joinToString(" · ")
}

/** 「上次检查」与结果；正在同步时优先显示进行中。 */
@Composable
private fun syncSubtitle(state: AdapterStatusUiState): String {
    if (state.syncing) return stringResource(Res.string.sync_status_syncing)
    val record = state.lastSync ?: return stringResource(Res.string.adapter_status_never_synced)
    val resultText = when (record.kind) {
        AdapterSyncRecord.KIND_UPDATED -> stringResource(Res.string.sync_status_updated, record.updatedCount)
        AdapterSyncRecord.KIND_UP_TO_DATE -> stringResource(Res.string.sync_status_up_to_date)
        AdapterSyncRecord.KIND_DISABLED -> stringResource(Res.string.sync_status_disabled)
        AdapterSyncRecord.KIND_VERIFICATION_FAILED -> stringResource(Res.string.adapter_remote_update_failed)
        else -> stringResource(Res.string.sync_status_failed)
    }
    val timeText = stringResource(Res.string.adapter_status_last_sync, formatSyncTime(record.atMillis))
    return "$timeText · $resultText"
}

/** 本地时间 "yyyy-MM-dd HH:mm"（与 IcsExportTool 的日期拼装口径一致）。 */
private fun formatSyncTime(atMillis: Long): String {
    val dateTime = Instant.fromEpochMilliseconds(atMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    val month = dateTime.month.number.toString().padStart(2, '0')
    val day = dateTime.day.toString().padStart(2, '0')
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    return "${dateTime.year}-$month-$day $hour:$minute"
}
