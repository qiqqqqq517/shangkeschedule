package com.shangkeschedule.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.data.di.AppStorage
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.copyToClipboard
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ToastManager
import androidx.compose.runtime.rememberCoroutineScope
import com.shangkeschedule.ui.components.isShareDialogSupported
import com.shangkeschedule.ui.components.platformShareTextWithFile
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import okio.FileSystem
import okio.SYSTEM
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.code_24px
import shangkeschedule.shared.generated.resources.content_copy_24px
import shangkeschedule.shared.generated.resources.link_24px
import shangkeschedule.shared.generated.resources.save_24px
import shangkeschedule.shared.generated.resources.theme_share_action_copy
import shangkeschedule.shared.generated.resources.theme_share_action_save
import shangkeschedule.shared.generated.resources.theme_share_action_share
import shangkeschedule.shared.generated.resources.theme_share_applied
import shangkeschedule.shared.generated.resources.theme_share_apply
import shangkeschedule.shared.generated.resources.theme_share_apply_failed
import shangkeschedule.shared.generated.resources.theme_share_code_hint
import shangkeschedule.shared.generated.resources.theme_share_copied
import shangkeschedule.shared.generated.resources.theme_share_copy_failed
import shangkeschedule.shared.generated.resources.theme_share_current_format
import shangkeschedule.shared.generated.resources.theme_share_empty
import shangkeschedule.shared.generated.resources.theme_share_import_label
import shangkeschedule.shared.generated.resources.theme_share_import_placeholder
import shangkeschedule.shared.generated.resources.theme_share_invalid
import shangkeschedule.shared.generated.resources.theme_share_qr_hint
import shangkeschedule.shared.generated.resources.theme_share_qr_too_long
import shangkeschedule.shared.generated.resources.theme_share_saved
import shangkeschedule.shared.generated.resources.theme_share_section_code
import shangkeschedule.shared.generated.resources.theme_share_section_import
import shangkeschedule.shared.generated.resources.theme_share_section_share
import shangkeschedule.shared.generated.resources.theme_share_tip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shangkeschedule.shared.generated.resources.title_theme_share

private const val THEME_SHARE_FILE_NAME = "shangke-theme.txt"
private const val THEME_SHARE_FILE_MIME = "text/plain"
private val THEME_QR_SIDE_LENGTH = 200.dp

/**
 * 「主题分享」页（v4.66.0，D3）。
 *
 * 与「课表分享」同一套语言：上半页把自己当前的主题变成一条 `SKT1:` 串（可复制 / 扫码 / 存文件），
 * 下半页接收对方的串并原样应用。只带外观，不带课程与账号 —— 页面底部有一句明示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeShareScreen(
    onBack: () -> Unit,
    viewModel: ThemeShareViewModel = koinViewModel(),
    appStorage: AppStorage = koinInject()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var importText by remember { mutableStateOf("") }

    val copiedToast = stringResource(Res.string.theme_share_copied)
    val copyFailedToast = stringResource(Res.string.theme_share_copy_failed)
    // 系统分享的标题：此前直接把整段 base64 分享串当 subject，通知栏/分享面板会刷一屏乱码。
    val shareSubject = stringResource(Res.string.title_theme_share)
    val scope = rememberCoroutineScope()
    val savedToast = stringResource(Res.string.theme_share_saved)
    val appliedToast = stringResource(Res.string.theme_share_applied)
    val invalidToast = stringResource(Res.string.theme_share_invalid)
    val failedToast = stringResource(Res.string.theme_share_apply_failed)

    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onFileExported = { success -> if (success) ToastManager.show(savedToast) }
        )
    )

    // 「应用主题」的结果只在 Toast 里说，提示过就复位，避免重组重复弹。
    LaunchedEffect(uiState.applyState) {
        when (uiState.applyState) {
            ThemeApplyState.APPLIED -> {
                ToastManager.show(appliedToast)
                importText = ""
                viewModel.consumeApplyState()
            }

            ThemeApplyState.INVALID_CODE -> {
                ToastManager.show(invalidToast)
                viewModel.consumeApplyState()
            }

            ThemeApplyState.FAILED -> {
                ToastManager.show(failedToast)
                viewModel.consumeApplyState()
            }

            ThemeApplyState.IDLE, ThemeApplyState.APPLYING -> Unit
        }
    }

    val qrMatrix = remember(uiState.code) {
        uiState.code.takeIf { it.isNotBlank() }?.let { QrCodeMatrix.of(it) }
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.title_theme_share),
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = appSpacing().pageHorizontal,
                    vertical = appSpacing().pageTop
                ),
            verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap)
        ) {
            AppSectionHeader(stringResource(Res.string.theme_share_section_share))

            SectionCard {
                SettingItem(
                    title = stringResource(
                        Res.string.theme_share_current_format,
                        stringResource(uiState.preset.labelRes),
                        stringResource(uiState.themeMode.labelRes)
                    ),
                    onClick = null,
                    trailingContent = {}
                )
            }

            SectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(appSpacing().cardInner),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(appSpacing().listGap)
                ) {
                    if (qrMatrix != null) {
                        QrCodePanel(matrix = qrMatrix, sideLength = THEME_QR_SIDE_LENGTH)
                        Text(
                            text = stringResource(Res.string.theme_share_qr_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textSecondary
                        )
                    } else {
                        Text(
                            text = if (uiState.isLoading) {
                                stringResource(Res.string.theme_share_empty)
                            } else {
                                stringResource(Res.string.theme_share_qr_too_long)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textSecondary
                        )
                    }
                }
            }

            AppSectionHeader(stringResource(Res.string.theme_share_section_code))

            SectionCard {
                Column(modifier = Modifier.padding(appSpacing().cardInner)) {
                    AppTextField(
                        value = uiState.code,
                        onValueChange = {},
                        singleLine = false,
                        minLines = 4,
                        maxLines = 8,
                        readOnly = true
                    )
                }
                Text(
                    text = stringResource(Res.string.theme_share_code_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textSecondary,
                    modifier = Modifier.padding(
                        horizontal = appSpacing().cardInner,
                        vertical = appSpacing().listGap
                    )
                )
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.theme_share_action_copy),
                    leadingIcon = vectorResource(Res.drawable.content_copy_24px),
                    trailingContent = {},
                    onClick = {
                        if (uiState.code.isBlank() || !copyToClipboard(uiState.code)) {
                            ToastManager.show(copyFailedToast)
                        } else {
                            ToastManager.show(copiedToast)
                        }
                    }
                )
                if (isShareDialogSupported) {
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.theme_share_action_share),
                        leadingIcon = vectorResource(Res.drawable.link_24px),
                        trailingContent = {},
                        onClick = {
                            scope.launch {
                                // 写缓存文件是阻塞 IO，必须换到 IO 调度器（原先在点击回调里
                                // 同步写，主线程直接卡住）。
                                val path = withContext(Dispatchers.IO) {
                                    writeThemeShareFile(appStorage, uiState.code)
                                }
                                if (path == null) {
                                    ToastManager.show(copyFailedToast)
                                } else {
                                    platformShareTextWithFile(
                                        subject = shareSubject,
                                        text = uiState.code,
                                        filePath = path,
                                        mimeType = THEME_SHARE_FILE_MIME
                                    )
                                }
                            }
                        }
                    )
                }
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.theme_share_action_save),
                    leadingIcon = vectorResource(Res.drawable.save_24px),
                    trailingContent = {},
                    onClick = {
                        if (uiState.code.isBlank()) {
                            ToastManager.show(copyFailedToast)
                        } else {
                            fileManager.exportFile(
                                THEME_SHARE_FILE_NAME,
                                uiState.code.encodeToByteArray()
                            )
                        }
                    }
                )
            }

            AppSectionHeader(stringResource(Res.string.theme_share_section_import))

            SectionCard {
                Column(modifier = Modifier.padding(appSpacing().cardInner)) {
                    AppTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = stringResource(Res.string.theme_share_import_label),
                        placeholder = stringResource(Res.string.theme_share_import_placeholder),
                        singleLine = false,
                        minLines = 3,
                        maxLines = 6
                    )
                }
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.theme_share_apply),
                    leadingIcon = vectorResource(Res.drawable.code_24px),
                    trailingContent = {},
                    onClick = { viewModel.apply(importText) }
                )
            }

            Text(
                text = stringResource(Res.string.theme_share_tip),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textSecondary
            )
        }
    }
}

/**
 * 把分享串写进缓存目录，供「分享到其他应用」当附件用。
 * 返回文件路径；写不进去（磁盘异常等）返回 null，由调用方降级提示。
 */
private fun writeThemeShareFile(appStorage: AppStorage, code: String): String? = runCatching {
    val dir = appStorage.cacheDir / "share_temp"
    val file = dir / THEME_SHARE_FILE_NAME
    FileSystem.SYSTEM.createDirectories(dir)
    FileSystem.SYSTEM.write(file) { writeUtf8(code) }
    file.toString()
}.getOrNull()
