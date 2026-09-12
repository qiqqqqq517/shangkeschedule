package com.shangkeschedule.ui.settings.appearance

import com.shangkeschedule.ui.components.AppTopAppBar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangkeschedule.data.model.NextCardMode
import com.shangkeschedule.ui.components.AppRadioIndicator
import com.shangkeschedule.ui.settings.SettingsViewModel
import com.shangkeschedule.ui.theme.appColors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.desc_next_card_settings
import shangkeschedule.shared.generated.resources.item_next_card_settings
import shangkeschedule.shared.generated.resources.next_card_mode_auto
import shangkeschedule.shared.generated.resources.next_card_mode_auto_desc
import shangkeschedule.shared.generated.resources.next_card_mode_ended
import shangkeschedule.shared.generated.resources.next_card_mode_ended_desc
import shangkeschedule.shared.generated.resources.next_card_mode_hide
import shangkeschedule.shared.generated.resources.next_card_mode_hide_desc

/**
 * 「个性化显示 → 下节课卡」三级页（v3.47.0 新增）。
 *
 * 决定「今日课程结束后」下节课卡如何显示：
 * ① 自动显示下一次课程或日程（默认，卡片不再消失）；
 * ② 显示「今日课程已结束，自由探索吧」提示（变淡）；
 * ③ 课程结束后消失（旧行为）。
 *
 * 值经 `AppSettingsModel.nextCardMode` → 今日页统一解析器生效（三套主题共用）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextCardSettingsScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val settings = uiState.appSettings

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.item_next_card_settings),
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.desc_next_card_settings),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            NextCardMode.entries.forEach { mode ->
                NextCardModeCard(
                    labelRes = mode.labelRes(),
                    descRes = mode.descRes(),
                    selected = settings.nextCardMode == mode,
                    onSelect = { settingsViewModel.onNextCardModeChanged(mode) }
                )
            }
        }
    }
}

private fun NextCardMode.labelRes(): StringResource = when (this) {
    NextCardMode.AUTO_NEXT -> Res.string.next_card_mode_auto
    NextCardMode.TODAY_ENDED -> Res.string.next_card_mode_ended
    NextCardMode.HIDE -> Res.string.next_card_mode_hide
}

private fun NextCardMode.descRes(): StringResource = when (this) {
    NextCardMode.AUTO_NEXT -> Res.string.next_card_mode_auto_desc
    NextCardMode.TODAY_ENDED -> Res.string.next_card_mode_ended_desc
    NextCardMode.HIDE -> Res.string.next_card_mode_hide_desc
}

/** 三档单选卡（与「动效风格」同构）。 */
@Composable
private fun NextCardModeCard(
    labelRes: StringResource,
    descRes: StringResource,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onSelect,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) selectedColor.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.dp, selectedColor.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary
                )
            }
            AppRadioIndicator(selected = selected)
        }
    }
}
