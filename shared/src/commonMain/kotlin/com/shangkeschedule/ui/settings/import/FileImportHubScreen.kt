package com.shangkeschedule.ui.settings.import

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
                title = { Text("文件导入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
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
                    title = "Excel 导入",
                    subtitle = "网格课表 / 列表课表自动识别（.xlsx）",
                    onClick = { onNavigate(Destination.ExcelImport) }
                )
                SectionDivider()
                SettingItem(
                    title = "JSON 导入",
                    subtitle = "导入到所选课表，支持本App导出文件与 WakeUp JSON",
                    onClick = { onNavigate(Destination.JsonFileImport) }
                )
                SectionDivider()
                SettingItem(
                    title = "文本文件导入",
                    subtitle = "CSV / ICS / HTML / TXT，自动识别格式并预览",
                    onClick = { onNavigate(Destination.TextFileImport) }
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "提示：教务系统导入请返回上一页使用「教务系统导入」；粘贴文本请使用「文本粘贴导入」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
