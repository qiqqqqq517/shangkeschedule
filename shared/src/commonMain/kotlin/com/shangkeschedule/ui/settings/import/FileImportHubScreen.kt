package com.shangkeschedule.ui.settings.import

import org.jetbrains.compose.resources.vectorResource
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.import_cat_text_file
import shangkeschedule.shared.generated.resources.import_cat_csv
import shangkeschedule.shared.generated.resources.import_cat_ics
import shangkeschedule.shared.generated.resources.import_cat_json
import shangkeschedule.shared.generated.resources.import_cat_excel
import shangkeschedule.shared.generated.resources.import_file_hub_hint
import shangkeschedule.shared.generated.resources.import_file_hub_text_desc
import shangkeschedule.shared.generated.resources.import_file_hub_csv_desc
import shangkeschedule.shared.generated.resources.import_file_hub_ics_desc
import shangkeschedule.shared.generated.resources.import_file_hub_json_desc
import shangkeschedule.shared.generated.resources.import_file_hub_excel_desc
import shangkeschedule.shared.generated.resources.import_file_hub_title
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
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem

/**
 * 文件导入二级页（分类导航）：
 * 把 Excel / JSON / 文本文件三种文件导入方式拆分到各自的下一级页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileImportHubScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.import_file_hub_title)) },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            SectionCard {
                SettingItem(
                    title = stringResource(Res.string.import_cat_excel),
                    subtitle = stringResource(Res.string.import_file_hub_excel_desc),
                    onClick = { onNavigate(Destination.ExcelImport) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.import_cat_json),
                    subtitle = stringResource(Res.string.import_file_hub_json_desc),
                    onClick = { onNavigate(Destination.JsonFileImport) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.import_cat_ics),
                    subtitle = stringResource(Res.string.import_file_hub_ics_desc),
                    onClick = { onNavigate(Destination.TextFileImport("ICS")) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.import_cat_csv),
                    subtitle = stringResource(Res.string.import_file_hub_csv_desc),
                    onClick = { onNavigate(Destination.TextFileImport("CSV")) }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.import_cat_text_file),
                    subtitle = stringResource(Res.string.import_file_hub_text_desc),
                    onClick = { onNavigate(Destination.TextFileImport("AUTO")) }
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.import_file_hub_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
