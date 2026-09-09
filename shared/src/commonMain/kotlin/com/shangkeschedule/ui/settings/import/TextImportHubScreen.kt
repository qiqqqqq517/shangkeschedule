package com.shangkeschedule.ui.settings.import

import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing

import org.jetbrains.compose.resources.vectorResource
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.import_text_hub_hint
import shangkeschedule.shared.generated.resources.import_text_hub_ics_desc
import shangkeschedule.shared.generated.resources.import_text_hub_csv_desc
import shangkeschedule.shared.generated.resources.import_text_hub_json_desc
import shangkeschedule.shared.generated.resources.import_text_hub_plain_desc
import shangkeschedule.shared.generated.resources.import_text_hub_plain
import shangkeschedule.shared.generated.resources.import_text_hub_wakeup_desc
import shangkeschedule.shared.generated.resources.import_text_hub_wakeup
import shangkeschedule.shared.generated.resources.import_cat_text_paste
import shangkeschedule.shared.generated.resources.import_cat_csv
import shangkeschedule.shared.generated.resources.import_cat_ics
import shangkeschedule.shared.generated.resources.import_cat_json
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shangkeschedule.Destination
import com.shangkeschedule.data.parser.TextImportFormat
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem

/**
 * 文本粘贴导入二级页（分类导航）：
 * 按格式类别拆分为独立的下一级页面，每类页面内强制使用对应解析器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextImportHubScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.import_cat_text_paste)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = stringResource(Res.string.a11y_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = appSpacing().pageHorizontal)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            SectionCard {
                SettingItem(
                    title = stringResource(Res.string.import_text_hub_wakeup),
                    subtitle = stringResource(Res.string.import_text_hub_wakeup_desc),
                    onClick = { onNavigate(Destination.TextImportFormatPage(TextImportFormat.WAKEUP.name)) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.import_text_hub_plain),
                    subtitle = stringResource(Res.string.import_text_hub_plain_desc),
                    onClick = { onNavigate(Destination.TextImportFormatPage(TextImportFormat.PLAIN.name)) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.import_cat_json),
                    subtitle = stringResource(Res.string.import_text_hub_json_desc),
                    onClick = { onNavigate(Destination.TextImportFormatPage(TextImportFormat.JSON.name)) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.import_cat_csv),
                    subtitle = stringResource(Res.string.import_text_hub_csv_desc),
                    onClick = { onNavigate(Destination.TextImportFormatPage(TextImportFormat.CSV.name)) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.import_cat_ics),
                    subtitle = stringResource(Res.string.import_text_hub_ics_desc),
                    onClick = { onNavigate(Destination.TextImportFormatPage(TextImportFormat.ICS.name)) }
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.import_text_hub_hint),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
