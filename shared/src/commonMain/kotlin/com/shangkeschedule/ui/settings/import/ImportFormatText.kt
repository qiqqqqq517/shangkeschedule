package com.shangkeschedule.ui.settings.import

import androidx.compose.runtime.Composable
import com.shangkeschedule.data.parser.TextImportFormat
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.import_format_csv_hint
import shangkeschedule.shared.generated.resources.import_format_csv_label
import shangkeschedule.shared.generated.resources.import_format_html_hint
import shangkeschedule.shared.generated.resources.import_format_html_label
import shangkeschedule.shared.generated.resources.import_format_ics_hint
import shangkeschedule.shared.generated.resources.import_format_ics_label
import shangkeschedule.shared.generated.resources.import_format_json_hint
import shangkeschedule.shared.generated.resources.import_format_json_label
import shangkeschedule.shared.generated.resources.import_format_plain_hint
import shangkeschedule.shared.generated.resources.import_format_plain_label
import shangkeschedule.shared.generated.resources.import_format_screen_title
import shangkeschedule.shared.generated.resources.import_format_wakeup_hint
import shangkeschedule.shared.generated.resources.import_format_wakeup_label

/**
 * 文本导入格式类别的展示文案（UI 层职责）。
 *
 * `TextImportFormat` 只承载解析口径（枚举名 = 导航参数、扩展名映射），不自带文案；
 * 文案改由资源表提供，随系统语言切换，避免英文/繁体界面里出现硬编码简体字。
 */

/** 类别名（作为名词用在句子与标题里，如「导入 CSV 表格 文件」）。 */
fun TextImportFormat.labelRes(): StringResource = when (this) {
    TextImportFormat.WAKEUP -> Res.string.import_format_wakeup_label
    TextImportFormat.PLAIN -> Res.string.import_format_plain_label
    TextImportFormat.JSON -> Res.string.import_format_json_label
    TextImportFormat.CSV -> Res.string.import_format_csv_label
    TextImportFormat.ICS -> Res.string.import_format_ics_label
    TextImportFormat.HTML -> Res.string.import_format_html_label
}

/** 该格式的说明：粘贴什么内容、字段按什么顺序。 */
fun TextImportFormat.hintRes(): StringResource = when (this) {
    TextImportFormat.WAKEUP -> Res.string.import_format_wakeup_hint
    TextImportFormat.PLAIN -> Res.string.import_format_plain_hint
    TextImportFormat.JSON -> Res.string.import_format_json_hint
    TextImportFormat.CSV -> Res.string.import_format_csv_hint
    TextImportFormat.ICS -> Res.string.import_format_ics_hint
    TextImportFormat.HTML -> Res.string.import_format_html_hint
}

/** 二级页标题：简中「CSV 表格 导入」/ 英文 "Import CSV table" / 繁中「CSV 表格 匯入」。 */
@Composable
fun TextImportFormat.screenTitle(): String =
    stringResource(Res.string.import_format_screen_title, stringResource(labelRes()))
