package com.shangkeschedule.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_confirm

/**
 * Material 3 风格的日期范围选择对话框。
 *
 * 与 [DatePickerModal]（单日）同处 `ui/components`：标题由调用方传入，
 * 避免把某一个功能的文案写死在共享组件里。
 *
 * @param title 对话框标题
 * @param onConfirm 用户确认后的回调，参数为起止日期（均为 `LocalDate`）
 * @param onDismiss 对话框被关闭时的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerModal(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val state = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis?.toUtcLocalDate()
                    val end = state.selectedEndDateMillis?.toUtcLocalDate()
                    if (start != null && end != null) {
                        onConfirm(start, end)
                    }
                },
                enabled = state.selectedEndDateMillis != null
            ) {
                Text(
                    stringResource(Res.string.action_confirm),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel), color = appColors().textSecondary)
            }
        }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = {
                Text(
                    modifier = Modifier.padding(appSpacing().cardInner),
                    text = title
                )
            }
        )
    }
}

/**
 * Material 3 日期选择器返回的是**选中日在 UTC 的当日零点**毫秒，不是本地时区瞬时，
 * 因此固定按 UTC 折算成 `LocalDate`。若按 `currentSystemDefault()` 折算，
 * UTC 以西的时区会整体偏一天（选 10-01 得到 09-30）。
 */
fun Long.toUtcLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.UTC)
        .date
