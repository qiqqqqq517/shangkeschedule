package com.shangkeschedule.ui.settings.import

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shangkeschedule.Destination
import com.shangkeschedule.data.api.AI_IMPORT_SYSTEM_PROMPT
import com.shangkeschedule.data.parser.TextImportFormat
import com.shangkeschedule.tool.copyToClipboard
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.ai_image_import_copy_prompt
import shangkeschedule.shared.generated.resources.ai_image_import_copy_prompt_desc
import shangkeschedule.shared.generated.resources.ai_image_import_copied_hint
import shangkeschedule.shared.generated.resources.ai_image_import_format_line
import shangkeschedule.shared.generated.resources.ai_image_import_go_paste
import shangkeschedule.shared.generated.resources.ai_image_import_intro
import shangkeschedule.shared.generated.resources.ai_image_import_paste_desc
import shangkeschedule.shared.generated.resources.ai_image_import_prompt_desc
import shangkeschedule.shared.generated.resources.ai_image_import_section_paste
import shangkeschedule.shared.generated.resources.ai_image_import_section_prompt
import shangkeschedule.shared.generated.resources.ai_image_import_section_steps
import shangkeschedule.shared.generated.resources.ai_image_import_step1
import shangkeschedule.shared.generated.resources.ai_image_import_step2
import shangkeschedule.shared.generated.resources.ai_image_import_step3
import shangkeschedule.shared.generated.resources.ai_image_import_title
import shangkeschedule.shared.generated.resources.ai_image_import_toast_copy_failed
import shangkeschedule.shared.generated.resources.ai_image_import_toast_copied
import shangkeschedule.shared.generated.resources.ai_image_import_use_api_instead
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.content_copy_24px
import shangkeschedule.shared.generated.resources.image_24px

/**
 * 「AI 图片导入」页（v4.78.0）：图片 + 固定提示词 → 用户自己的 AI → 粘回解析导入。
 *
 * 与「AI 识别导入」（[com.shangkeschedule.ui.settings.import.AiImportScreen]）刻意分成两页，
 * 因为两者的使用前提完全不同：
 * - AI 识别导入：用户愿意在应用里填接口地址 / 模型名 / Key，由应用代发请求；
 * - 本页：用户**不想填任何接口配置**，只想用自己平时就在用的 AI（DeepSeek / ChatGPT /
 *   Kimi / 豆包等），图片和提示词都由用户自己在那边发送。
 *
 * 因此本页**不发任何网络请求、不保存任何配置**，只做三件事：
 * 1. 给出固定提示词并支持一键复制（提示词与「AI 识别导入」共用同一个常量
 *    [AI_IMPORT_SYSTEM_PROMPT]，保证两条路径产出同一格式、解析口径不分叉）；
 * 2. 讲清中间三步（打开自己的 AI → 发图片+提示词 → 复制输出）；
 * 3. 一键跳到既有的「文本粘贴导入（纯文本）」页解析预览导入 —— 不另造导入链路。
 *
 * 提示词本身刻意**不本地化**：它要求 AI 输出「周一」「1-2节」「1-16周」这类解析器
 * 认得的令牌，与界面语言无关；界面文案才随语言切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiImageImportScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
) {
    // 文案在非组合回调（点击复制）里要用，提前在组合作用域解析
    val toastCopied = stringResource(Res.string.ai_image_import_toast_copied)
    val toastCopyFailed = stringResource(Res.string.ai_image_import_toast_copy_failed)
    // 复制成功后把按钮副标题换成「下一步怎么做」——比只弹一次 Toast 更容易接上后续动作
    var promptCopied by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.ai_image_import_title),
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
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = appSpacing().pageHorizontal,
                    vertical = appSpacing().pageTop
                ),
            verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap)
        ) {
            Text(
                text = stringResource(Res.string.ai_image_import_intro),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary
            )

            // ── 第一步：复制固定提示词 ─────────────────────────────────────
            AppSectionHeader(stringResource(Res.string.ai_image_import_section_prompt))

            SectionCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(appSpacing().cardInner)
                ) {
                    Text(
                        text = stringResource(Res.string.ai_image_import_prompt_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textSecondary
                    )
                    Spacer(modifier = Modifier.height(appSpacing().listGap))
                    // 提示词原文：可选中（复制）以便剪贴板不可用时手动选取，
                    // 底用 inputBg 与卡片区分，明确「这是要复制走的内容」。
                    Surface(
                        color = appColors().inputBg,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = AI_IMPORT_SYSTEM_PROMPT,
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textPrimary,
                                modifier = Modifier.padding(appSpacing().cardInner)
                            )
                        }
                    }
                }
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.ai_image_import_copy_prompt),
                    subtitle = if (promptCopied) {
                        stringResource(Res.string.ai_image_import_copied_hint)
                    } else {
                        stringResource(Res.string.ai_image_import_copy_prompt_desc)
                    },
                    leadingIcon = vectorResource(Res.drawable.content_copy_24px),
                    trailingContent = {},
                    onClick = {
                        // 复制失败不静默：Toast 说明，且上方提示词区块支持长按手动选取
                        val copied = copyToClipboard(AI_IMPORT_SYSTEM_PROMPT)
                        promptCopied = copied
                        ToastManager.show(
                            if (copied) toastCopied else toastCopyFailed
                        )
                    }
                )
            }

            // ── 第二步：在自己的 AI 里发送图片 + 提示词 ─────────────────────
            AppSectionHeader(stringResource(Res.string.ai_image_import_section_steps))

            SectionCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(appSpacing().cardInner),
                    verticalArrangement = Arrangement.spacedBy(appSpacing().listGap)
                ) {
                    StepRow(
                        number = "1",
                        text = stringResource(Res.string.ai_image_import_step1)
                    )
                    StepRow(
                        number = "2",
                        text = stringResource(Res.string.ai_image_import_step2)
                    )
                    StepRow(
                        number = "3",
                        text = stringResource(Res.string.ai_image_import_step3)
                    )
                    Text(
                        text = stringResource(Res.string.ai_image_import_format_line),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ── 第三步：粘回上课解析导入 ───────────────────────────────────
            AppSectionHeader(stringResource(Res.string.ai_image_import_section_paste))

            SectionCard {
                SettingItem(
                    title = stringResource(Res.string.ai_image_import_go_paste),
                    subtitle = stringResource(Res.string.ai_image_import_paste_desc),
                    leadingIcon = vectorResource(Res.drawable.image_24px),
                    onClick = {
                        // 固定提示词已约定输出「纯文本（一行一门课）」，
                        // 故直接进强制 PLAIN 的二级页，不走自动嗅探（避免 CSV/HTML 误判）。
                        onNavigate(
                            Destination.TextImportFormatPage(TextImportFormat.PLAIN.name)
                        )
                    }
                )
            }

            Text(
                text = stringResource(Res.string.ai_image_import_use_api_instead),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary
            )

            Spacer(modifier = Modifier.height(appSpacing().contentBottom))
        }
    }
}

/** 步骤行：序号圆点 + 说明文字。仅为视觉引导，不可点击。 */
@Composable
private fun StepRow(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(appSpacing().listGap)) {
        Surface(
            color = appColors().primarySoft,
            shape = CircleShape,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    color = appColors().primary
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = appColors().textPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
