package com.shangkeschedule.ui.components

import com.shangkeschedule.ui.theme.appSpacing

import com.shangkeschedule.ui.theme.appColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_add_new_table
import shangkeschedule.shared.generated.resources.action_add
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_confirm
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.course_table_created_at_prefix
import shangkeschedule.shared.generated.resources.course_table_id_prefix
import shangkeschedule.shared.generated.resources.dialog_title_add_table
import shangkeschedule.shared.generated.resources.label_current
import shangkeschedule.shared.generated.resources.label_table_name
import shangkeschedule.shared.generated.resources.text_no_course_tables
import shangkeschedule.shared.generated.resources.toast_add_table_success
import shangkeschedule.shared.generated.resources.toast_name_empty
import kotlin.time.Instant

/**
 * 适配 kotlinx-datetime 0.8.0 的格式化器定义
 */
private val defaultDateTimeFormat = LocalDateTime.Format {
    year()
    char('-')
    monthNumber()
    char('-')
    day()
    char(' ')
    hour()
    char(':')
    minute()
}

/**
 * 格式化毫秒时间戳为 yyyy-MM-dd HH:mm 格式
 */
private fun formatEpochMillis(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return defaultDateTimeFormat.format(localDateTime)
}

/**
 * 专门为弹窗提供 Koin 注入的仓库 ViewModel
 */
@KoinViewModel
class CourseTablePickerDeps(
    val courseTableRepository: CourseTableRepository,
    val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    fun createNewCourseTable(name: String) {
        viewModelScope.launch {
            courseTableRepository.createNewCourseTable(name)
        }
    }
}

@Composable
fun CourseTablePickerDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onTableSelected: (CourseTable) -> Unit,
    deps: CourseTablePickerDeps = koinViewModel()
) {
    val courseTables by deps.courseTableRepository.getAllCourseTables().collectAsState(initial = emptyList())
    val appSettings by deps.appSettingsRepository.getAppSettings().collectAsState(initial = null)

    var selectedTable by remember { mutableStateOf<CourseTable?>(null) }

    var showAddTableDialog by remember { mutableStateOf(false) }
    var newTableName by remember { mutableStateOf("") }

    LaunchedEffect(courseTables, appSettings) {
        if (selectedTable == null && appSettings?.currentCourseTableId != null) {
            selectedTable = courseTables.find { it.id == appSettings?.currentCourseTableId }
        }
    }

    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                if (courseTables.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.text_no_course_tables),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(courseTables) { courseTable ->
                            val isCurrentActive = courseTable.id == appSettings?.currentCourseTableId
                            val isSelectedForDialog = courseTable.id == selectedTable?.id

                            CourseTablePickerCard(
                                courseTable = courseTable,
                                isSelected = isSelectedForDialog,
                                isCurrentActive = isCurrentActive,
                                onCardClick = {
                                    selectedTable = it
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledIconButton(
                    onClick = { showAddTableDialog = true },
                    modifier = Modifier.size(appSpacing().touchMin),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = appColors().primarySoft,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.add_24px),
                        contentDescription = stringResource(Res.string.a11y_add_new_table),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 统一操作区：取消 = 灰字文本钮，确认 = 主色胶囊实心钮（AppDialogActions）
                AppDialogActions(
                    confirmText = stringResource(Res.string.action_confirm),
                    onConfirm = {
                        selectedTable?.let { onTableSelected(it) }
                        onDismissRequest()
                    },
                    confirmEnabled = selectedTable != null,
                    dismissText = stringResource(Res.string.action_cancel),
                    onDismiss = onDismissRequest,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        dismissButton = null
    )

    if (showAddTableDialog) {
        val toastAddSuccess = stringResource(Res.string.toast_add_table_success, newTableName)
        val toastNameEmpty = stringResource(Res.string.toast_name_empty)

        AppAlertDialog(
            onDismissRequest = {
                showAddTableDialog = false
                newTableName = ""
            },
            title = { Text(stringResource(Res.string.dialog_title_add_table)) },
            text = {
                // 柔和填充式输入框（Telegram 风格，取代方硬的 OutlinedTextField）
                AppTextField(
                    value = newTableName,
                    onValueChange = { newTableName = it },
                    label = stringResource(Res.string.label_table_name),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                // 主色胶囊确认 + 灰字取消，取代双 TextButton
                AppDialogActions(
                    confirmText = stringResource(Res.string.action_add),
                    onConfirm = {
                        if (newTableName.isNotBlank()) {
                            deps.createNewCourseTable(newTableName)

                            // 跨平台 ToastManager 提示
                            ToastManager.show(toastAddSuccess)

                            showAddTableDialog = false
                            newTableName = ""
                        } else {
                            ToastManager.show(toastNameEmpty)
                        }
                    },
                    dismissText = stringResource(Res.string.action_cancel),
                    onDismiss = {
                        showAddTableDialog = false
                        newTableName = ""
                    }
                )
            },
            dismissButton = {}
        )
    }
}

@Composable
fun CourseTablePickerCard(
    courseTable: CourseTable,
    isSelected: Boolean,
    isCurrentActive: Boolean,
    onCardClick: (CourseTable) -> Unit
) {
    AppSelectableCard(
        selected = isSelected,
        onClick = { onCardClick(courseTable) },
        // 「当前使用中」第三态：tertiary 底色 + 常规白卡（选中态统一主色描边）
        containerColor = when {
            isSelected -> null
            isCurrentActive -> MaterialTheme.colorScheme.tertiaryContainer
            else -> null
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = courseTable.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        Res.string.course_table_id_prefix,
                        courseTable.id.take(8) + "..."
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(
                        Res.string.course_table_created_at_prefix,
                        formatEpochMillis(courseTable.createdAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary.copy(alpha = 0.7f)
                )
            }
            if (isCurrentActive) {
                Text(
                    stringResource(Res.string.label_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}