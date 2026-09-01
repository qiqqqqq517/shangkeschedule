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
                title = { Text("文本粘贴导入") },
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
                    title = "WakeUp 分享文本",
                    subtitle = "粘贴 WakeUp 课程表 App 分享的文本内容",
                    onClick = { onNavigate(Destination.TextImportFormatPage(TextImportFormat.WAKEUP.name)) }
                )
                SectionDivider()
                SettingItem(
                    title = "纯文本导入",
                    subtitle = "一行一课：课程名 教师 教室 星期 节次 周次",
                    onClick = { onNavigate(Destination.TextImportFormatPage(TextImportFormat.PLAIN.name)) }
                )
                SectionDivider()
                SettingItem(
                    title = "JSON 导入",
                    subtitle = "本App导出 JSON / WakeUp JSON",
                    onClick = { onNavigate(Destination.TextImportFormatPage(TextImportFormat.JSON.name)) }
                )
                SectionDivider()
                SettingItem(
                    title = "CSV 导入",
                    subtitle = "表头：课程,教师,教室,星期,节次,周次",
                    onClick = { onNavigate(Destination.TextImportFormatPage(TextImportFormat.CSV.name)) }
                )
                SectionDivider()
                SettingItem(
                    title = "ICS 日历导入",
                    subtitle = "粘贴 .ics 日历文本（含 VEVENT）",
                    onClick = { onNavigate(Destination.TextImportFormatPage(TextImportFormat.ICS.name)) }
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "提示：不确定格式可任选一类粘贴尝试；各类别解析失败时会给出具体原因。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
