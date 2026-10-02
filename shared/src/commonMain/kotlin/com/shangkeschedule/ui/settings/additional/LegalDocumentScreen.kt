package com.shangkeschedule.ui.settings.additional

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ThemedLoadingIndicator
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.legal_load_failed_plain
import shangkeschedule.shared.generated.resources.legal_offline_note
import shangkeschedule.shared.generated.resources.title_privacy_policy
import shangkeschedule.shared.generated.resources.title_user_agreement

/**
 * 应用内协议文档类型（v4.65.0 新增）。
 *
 * 正文以 Markdown 纯文本随包内置在 `composeResources/files/` 下，**不联网也能读**——
 * 与官网 `website/privacy.html` 是同源内容，应用内这份是离线可查的副本。
 */
enum class LegalDocumentType(val resPath: String, val titleRes: StringResource) {
    PRIVACY("files/privacy_policy.md", Res.string.title_privacy_policy),
    TERMS("files/terms_of_service.md", Res.string.title_user_agreement);

    companion object {
        /** 导航参数容错：未知取值一律回退隐私政策，避免脏参数导致白屏。 */
        fun fromName(name: String): LegalDocumentType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: PRIVACY
    }
}

private sealed interface LegalContentState {
    data object Loading : LegalContentState
    data class Success(val text: String) : LegalContentState

    /**
     * 读取/解码失败。
     *
     * 刻意**不带异常原文**：异常消息里可能夹带资源路径等内部信息，对用户也没有意义，
     * 界面统一显示本地化的 [shangkeschedule.shared.generated.resources.Res.string.legal_load_failed_plain]。
     */
    data object Error : LegalContentState
}

/**
 * 应用内隐私政策 / 用户协议页（v4.65.0 新增）。
 *
 * 此前仓库**没有任何应用内政策页**，隐私政策的唯一载体是官网 `website/privacy.html`；
 * 用户在应用内只能跳浏览器。这里把同一份正文内置进来，顺带解决两个问题：
 * 1. 离线（没网也能查自己有哪些数据权利）；
 * 2. 应用商店与合规审核通常要求应用内可见。
 *
 * 渲染器是**极简自实现**（`#` 标题 / `##` 小节 / `-` 列表 / 空行留白），刻意不引入 Markdown 依赖：
 * 正文是我们自己维护的固定文本，不需要完整语法。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun LegalDocumentScreen(type: String, onBack: () -> Unit) {
    val document = LegalDocumentType.fromName(type)

    val contentState by produceState<LegalContentState>(
        initialValue = LegalContentState.Loading,
        document
    ) {
        value = try {
            // 读内置文档 + 解码走后台调度器，别占合成线程。
            val text = withContext(Dispatchers.Default) { Res.readBytes(document.resPath).decodeToString() }
            LegalContentState.Success(text)
        } catch (_: Exception) {
            LegalContentState.Error
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopAppBar(
                title = { Text(text = stringResource(document.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        when (val state = contentState) {
            is LegalContentState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    ThemedLoadingIndicator()
                }
            }

            is LegalContentState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.legal_load_failed_plain),
                        color = appColors().danger,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(appSpacing().cardInner)
                    )
                }
            }

            is LegalContentState.Success -> {
                val lines = state.text.replace("\r\n", "\n").split("\n")
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = appSpacing().pageHorizontal,
                        end = appSpacing().pageHorizontal,
                        top = appSpacing().sectionTitleGap,
                        bottom = appSpacing().sectionGap
                    )
                ) {
                    items(lines) { line ->
                        MarkdownBlock(line)
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(Res.string.legal_offline_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单行 Markdown 渲染：只认 `#` / `##` / `-` 三种前缀，其余按正文段落。
 */
@Composable
private fun MarkdownBlock(line: String) {
    val tokens = appColors()
    val trimmed = line.trimEnd()
    when {
        trimmed.startsWith("## ") -> {
            Text(
                text = trimmed.removePrefix("## "),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
            )
        }

        trimmed.startsWith("# ") -> {
            Text(
                text = trimmed.removePrefix("# "),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary
                )
                Text(
                    text = trimmed.drop(2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        trimmed.isBlank() -> Spacer(modifier = Modifier.height(8.dp))

        else -> {
            Text(
                text = trimmed,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}
