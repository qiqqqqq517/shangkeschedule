package com.shangkeschedule.ui.settings.notification

import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.item_clear_skipped_dates
import shangkeschedule.shared.generated.resources.item_update_holiday_info
import shangkeschedule.shared.generated.resources.item_view_skipped_dates
import shangkeschedule.shared.generated.resources.section_title_advanced
import shangkeschedule.shared.generated.resources.section_title_skip_dates
import shangkeschedule.shared.generated.resources.skipped_dates_count_format
import shangkeschedule.shared.generated.resources.skipped_dates_none
import shangkeschedule.shared.generated.resources.text_skip_dates_experimental
import shangkeschedule.shared.generated.resources.update_holiday_info_hint

/**
 * 高级设置卡片 UI 组件
 */
@Composable
fun AdvancedSettingsCard(
    uiState: NotificationSettingsUiState,
    onUpdateHolidays: () -> Unit,
    onClearSkippedDates: () -> Unit,
    onViewSkippedDates: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.section_title_advanced),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.text_skip_dates_experimental),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SectionCard {
            SettingItem(
                title = stringResource(Res.string.item_update_holiday_info),
                onClick = onUpdateHolidays,
                trailingContent = {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 4.dp
                        )
                    }
                }
            )
            SectionDivider()
            SettingItem(
                title = stringResource(Res.string.item_clear_skipped_dates),
                onClick = onClearSkippedDates
            )
            SectionDivider()
            SettingItem(
                title = stringResource(Res.string.item_view_skipped_dates),
                onClick = onViewSkippedDates,
                trailingContent = {
                    Text(
                        text = if (uiState.skippedDates.isNotEmpty()) {
                            stringResource(Res.string.skipped_dates_count_format, uiState.skippedDates.size)
                        } else {
                            stringResource(Res.string.skipped_dates_none)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            )
        }
        Text(
            text = stringResource(Res.string.update_holiday_info_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )
    }
}