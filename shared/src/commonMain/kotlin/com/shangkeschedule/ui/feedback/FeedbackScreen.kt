package com.shangkeschedule.ui.feedback

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.shangkeschedule.tool.AppExternalLinks
import com.shangkeschedule.tool.copyToClipboard
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppSegmentedControl
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.components.rememberAppHaptics
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.code_24px
import shangkeschedule.shared.generated.resources.content_copy_24px
import shangkeschedule.shared.generated.resources.feedback_action_copy
import shangkeschedule.shared.generated.resources.feedback_action_github
import shangkeschedule.shared.generated.resources.feedback_action_send_mail
import shangkeschedule.shared.generated.resources.feedback_adapter_tip
import shangkeschedule.shared.generated.resources.feedback_contact_label
import shangkeschedule.shared.generated.resources.feedback_contact_placeholder
import shangkeschedule.shared.generated.resources.feedback_copy_failed
import shangkeschedule.shared.generated.resources.feedback_copy_success
import shangkeschedule.shared.generated.resources.feedback_desc_label
import shangkeschedule.shared.generated.resources.feedback_desc_placeholder
import shangkeschedule.shared.generated.resources.feedback_desc_required
import shangkeschedule.shared.generated.resources.feedback_intro
import shangkeschedule.shared.generated.resources.feedback_mail_subject_prefix
import shangkeschedule.shared.generated.resources.feedback_no_data_note
import shangkeschedule.shared.generated.resources.feedback_scaffold_contact_empty
import shangkeschedule.shared.generated.resources.feedback_scaffold_text
import shangkeschedule.shared.generated.resources.feedback_type_issue
import shangkeschedule.shared.generated.resources.feedback_type_label
import shangkeschedule.shared.generated.resources.feedback_type_other
import shangkeschedule.shared.generated.resources.feedback_type_suggestion
import shangkeschedule.shared.generated.resources.title_feedback

/**
 * 反馈类型（应用内表单，v4.65.0 新增）。
 *
 * 注意：**没有「教务系统适配请求」这一项** —— 该类需求统一走
 * [AppExternalLinks.ADAPTER_REQUEST_FORM] 的 WPS 表单，避免两个通道抢同一类工单。
 */
private enum class FeedbackType(val labelRes: StringResource) {
    SUGGESTION(Res.string.feedback_type_suggestion),
    ISSUE(Res.string.feedback_type_issue),
    OTHER(Res.string.feedback_type_other),
}

/** 选项顺序即 [AppSegmentedControl] 的索引顺序，改这里必须同步 [FeedbackScreen] 里的标签列表。 */
private val FEEDBACK_TYPES = listOf(
    FeedbackType.SUGGESTION,
    FeedbackType.ISSUE,
    FeedbackType.OTHER,
)

/**
 * 意见反馈页（v4.65.0 新增）。
 *
 * 设计要点（与「星链课表」的差异在这里体现为「不上传」）：
 * - **本页只有出口、没有上传接口**：内容留在输入框里，直到用户点「通过邮件发送」/「在 GitHub 提交 Issue」
 *   /「复制反馈内容」三者之一才离开本机；
 * - **不采集任何标识符**（没有设备号、没有账号、没有埋点），文案已明示；
 * - 三个出口都是「打开外部应用或复制」，因此桌面端（无 mailto 处理器）也能用「复制」兜底；
 * - 主按钮复用 [AppDialogActions]（等宽铺满主色胶囊）以保持与对话框一致的主题胶囊样式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val haptics = rememberAppHaptics()
    val tokens = appColors()

    var type by remember { mutableStateOf(FeedbackType.SUGGESTION) }
    var description by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    // 只有点过发送/复制之后才显示「请先填写详细描述」，避免一进页面就报错
    var validateAttempted by remember { mutableStateOf(false) }

    // 逐个取 @Composable 文案（不能在 map 之类的普通 lambda 里调 stringResource）
    val typeLabels = listOf(
        stringResource(FeedbackType.SUGGESTION.labelRes),
        stringResource(FeedbackType.ISSUE.labelRes),
        stringResource(FeedbackType.OTHER.labelRes),
    )
    val copySuccessText = stringResource(Res.string.feedback_copy_success)
    val copyFailedText = stringResource(Res.string.feedback_copy_failed)
    val selectedTypeLabel = stringResource(type.labelRes)
    val subject = stringResource(Res.string.feedback_mail_subject_prefix) + " " + selectedTypeLabel
    val contactEmpty = stringResource(Res.string.feedback_scaffold_contact_empty)

    // 反馈正文（三处出口共用同一份文本）
    val body = stringResource(
        Res.string.feedback_scaffold_text,
        selectedTypeLabel,
        description.trim(),
        contact.trim().ifBlank { contactEmpty },
    )
    val isDescriptionMissing = validateAttempted && description.isBlank()

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(text = stringResource(Res.string.title_feedback)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))

            // 说明卡：写清「不自动上传 / 不采集数据」
            SectionCard(
                modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal)
            ) {
                Text(
                    text = stringResource(Res.string.feedback_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textPrimary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    text = stringResource(Res.string.feedback_no_data_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))

            // 反馈类型
            SectionCard(
                modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal)
            ) {
                Text(
                    text = stringResource(Res.string.feedback_type_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                AppSegmentedControl(
                    options = typeLabels,
                    selectedIndex = type.ordinal,
                    onSelect = {
                        type = FEEDBACK_TYPES[it]
                        haptics.tick()
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))

            // 详细描述 + 联系方式
            SectionCard(
                modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal)
            ) {
                AppTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        if (it.isNotBlank()) validateAttempted = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    label = stringResource(Res.string.feedback_desc_label),
                    placeholder = stringResource(Res.string.feedback_desc_placeholder),
                    singleLine = false,
                    minLines = 5,
                    maxLines = 10,
                    isError = isDescriptionMissing
                )
                if (isDescriptionMissing) {
                    Text(
                        text = stringResource(Res.string.feedback_desc_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.danger,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                SectionDivider()
                Spacer(modifier = Modifier.height(12.dp))
                AppTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    label = stringResource(Res.string.feedback_contact_label),
                    placeholder = stringResource(Res.string.feedback_contact_placeholder),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))

            // 主出口：邮件（沿用作者邮箱，用户可换任意邮箱客户端）
            AppDialogActions(
                confirmText = stringResource(Res.string.feedback_action_send_mail),
                onConfirm = {
                    if (description.isBlank()) {
                        validateAttempted = true
                    } else {
                        haptics.confirm()
                        val url = "mailto:${AppExternalLinks.SUPPORT_EMAIL}" +
                            "?subject=${percentEncode(subject)}" +
                            "&body=${percentEncode(body)}"
                        runCatching { uriHandler.openUri(url) }
                    }
                },
                confirmEnabled = description.isNotBlank(),
                modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal)
            )

            Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))

            // 其余两个出口
            SectionCard(
                modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal)
            ) {
                SettingItem(
                    title = stringResource(Res.string.feedback_action_github),
                    leadingIcon = vectorResource(Res.drawable.code_24px),
                    onClick = {
                        if (description.isBlank()) {
                            validateAttempted = true
                        } else {
                            haptics.tick()
                            val url = AppExternalLinks.GITHUB_NEW_ISSUE +
                                "?title=${percentEncode(subject)}" +
                                "&body=${percentEncode(body)}"
                            runCatching { uriHandler.openUri(url) }
                        }
                    }
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.feedback_action_copy),
                    leadingIcon = vectorResource(Res.drawable.content_copy_24px),
                    onClick = {
                        if (description.isBlank()) {
                            validateAttempted = true
                        } else {
                            val copied = copyToClipboard(body)
                            if (copied) haptics.confirm() else haptics.tick()
                            ToastManager.show(if (copied) copySuccessText else copyFailedText)
                        }
                    }
                )
            }

            // 教务适配需求引导到 WPS 表单（本页不接这类工单）
            if (AppExternalLinks.ADAPTER_REQUEST_FORM.isNotBlank()) {
                Spacer(modifier = Modifier.height(appSpacing().sectionTitleGap))
                Text(
                    text = stringResource(Res.string.feedback_adapter_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(horizontal = appSpacing().pageHorizontal)
                )
            }

            Spacer(modifier = Modifier.height(appSpacing().sectionGap))
        }
    }
}

private const val HEX_UPPER = "0123456789ABCDEF"

/**
 * 最小百分号编码（RFC 3986 unreserved 之外全部转义）。
 *
 * commonMain 里没有 `Uri.encode` / `URLEncoder`（前者仅 Android，后者仅 JVM），
 * 而 mailto / GitHub Issue 的 subject、body 都必须转义（中文、换行、`&`、`#` 都要处理），
 * 所以在这里自己实现一份，三端行为一致。
 */
internal fun percentEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        when {
            char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' -> append(char)
            char == '-' || char == '_' || char == '.' || char == '~' -> append(char)
            else -> {
                append('%')
                append(HEX_UPPER[code shr 4])
                append(HEX_UPPER[code and 0x0F])
            }
        }
    }
}
