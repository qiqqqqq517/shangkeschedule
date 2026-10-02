package com.shangkeschedule.ui.share

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.data.codec.CourseShareCodec
import com.shangkeschedule.data.di.AppStorage
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.copyToClipboard
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.AppEmptyState
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppSegmentedControl
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.components.isShareDialogSupported
import com.shangkeschedule.ui.components.platformShareTextWithFile
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SectionDivider
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.SYSTEM
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.check_circle_24px
import shangkeschedule.shared.generated.resources.code_24px
import shangkeschedule.shared.generated.resources.content_copy_24px
import shangkeschedule.shared.generated.resources.link_24px
import shangkeschedule.shared.generated.resources.share_action_copy
import shangkeschedule.shared.generated.resources.share_action_save
import shangkeschedule.shared.generated.resources.share_action_share
import shangkeschedule.shared.generated.resources.share_code_summary
import shangkeschedule.shared.generated.resources.share_copied
import shangkeschedule.shared.generated.resources.share_copy_failed
import shangkeschedule.shared.generated.resources.share_empty_table
import shangkeschedule.shared.generated.resources.share_file_saved
import shangkeschedule.shared.generated.resources.share_mode_compact
import shangkeschedule.shared.generated.resources.share_mode_compact_desc
import shangkeschedule.shared.generated.resources.share_mode_full
import shangkeschedule.shared.generated.resources.share_mode_full_desc
import shangkeschedule.shared.generated.resources.share_no_table
import shangkeschedule.shared.generated.resources.share_qr_compact_note
import shangkeschedule.shared.generated.resources.share_qr_hint
import shangkeschedule.shared.generated.resources.share_qr_too_long
import shangkeschedule.shared.generated.resources.share_qr_use_compact
import shangkeschedule.shared.generated.resources.share_section_code
import shangkeschedule.shared.generated.resources.share_section_content
import shangkeschedule.shared.generated.resources.share_section_qr
import shangkeschedule.shared.generated.resources.share_section_table
import shangkeschedule.shared.generated.resources.share_tip_text
import shangkeschedule.shared.generated.resources.title_course_share

/** 导出的分享串文件名（写进 cacheDir/share_temp，再交给系统分享）。 */
private const val SHARE_FILE_NAME = "shangke-share.txt"
private const val SHARE_FILE_MIME = "text/plain"

/** 二维码点阵边长。 */
private val QR_SIDE_LENGTH = 220.dp

/**
 * 「课表分享串 / 二维码」页。
 *
 * 上课的课表本来就能导出 JSON、也能从文本导入，但中间要经过文件/网盘。
 * 这一页把它压成一条自包含的短串（`SK1:` 前缀 + CRC 校验，见 [CourseShareCodec]）：
 * 复制粘贴、发微信、或直接让对方扫二维码，都能在「文本导入」里还原整张课表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseShareScreen(
    onBack: () -> Unit,
    viewModel: CourseShareViewModel = koinViewModel(),
    appStorage: AppStorage = koinInject()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    val copiedToast = stringResource(Res.string.share_copied)
    val copyFailedToast = stringResource(Res.string.share_copy_failed)
    val savedToast = stringResource(Res.string.share_file_saved)
    val shareSubject = stringResource(Res.string.title_course_share)

    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onFileExported = { success ->
                if (success) ToastManager.show(savedToast)
            }
        )
    )

    val code = uiState.code
    // 只有编码结果变化时才重算二维码（编码矩阵对同一串是确定的）。
    // 完整分享串通常超出二维码容量（16 门课约 4700 字符），此时自动退到精简串——
    // 精简串同样是合法的 SK1 分享串，导入侧能解，只是少了教师/备注/学分等字段。
    val fullQrMatrix = remember(uiState.fullCode) { QrCodeMatrix.of(uiState.fullCode) }
    val compactQrMatrix = remember(uiState.compactCode) { QrCodeMatrix.of(uiState.compactCode) }
    val qrMatrix = if (uiState.mode == CourseShareCodec.Mode.COMPACT) {
        compactQrMatrix
    } else {
        fullQrMatrix ?: compactQrMatrix
    }
    val qrUsesCompact = uiState.mode == CourseShareCodec.Mode.FULL &&
        fullQrMatrix == null && compactQrMatrix != null
    val colors = appColors()
    val spacing = appSpacing()

    val copyCode: () -> Unit = {
        if (code.isBlank() || !copyToClipboard(code)) {
            ToastManager.show(copyFailedToast)
        } else {
            ToastManager.show(copiedToast)
        }
    }

    val shareCode: () -> Unit = {
        if (code.isNotBlank()) {
            coroutineScope.launch {
                // 写缓存文件是阻塞 IO，换到 IO 调度器（原先在 Main 上同步写）。
                val path = withContext(Dispatchers.IO) { writeShareTextFile(appStorage, code) }
                if (path == null) {
                    ToastManager.show(copyFailedToast)
                } else {
                    // 系统分享只能带文件（没有纯文本通道），先把串写进 cacheDir 再发。
                    platformShareTextWithFile(
                        subject = shareSubject,
                        text = code,
                        filePath = path,
                        mimeType = SHARE_FILE_MIME
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                AppTopAppBar(
                    title = { Text(stringResource(Res.string.title_course_share)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.arrow_back_24px),
                                contentDescription = stringResource(Res.string.a11y_back)
                            )
                        }
                    }
                )
                if (uiState.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = spacing.pageHorizontal)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(spacing.pageTop))

            if (uiState.hasNoTable) {
                AppEmptyState(
                    hint = stringResource(Res.string.share_no_table),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
                Spacer(Modifier.height(spacing.contentBottom))
                return@Column
            }

            // ---------- 选择课表 ----------
            AppSectionHeader(
                text = stringResource(Res.string.share_section_table),
                modifier = Modifier.fillMaxWidth()
            )
            SectionCard {
                uiState.tables.forEachIndexed { index, table ->
                    if (index > 0) SectionDivider()
                    SettingItem(
                        title = table.name,
                        onClick = if (uiState.tables.size > 1) {
                            { viewModel.selectTable(table.id) }
                        } else {
                            null
                        },
                        trailingContent = {
                            if (table.id == uiState.selectedTableId) {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.check_circle_24px),
                                    contentDescription = null,
                                    tint = colors.primary
                                )
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(spacing.sectionGap))

            if (uiState.isEmptyTable || uiState.encodeFailed) {
                SectionCard {
                    AppEmptyState(
                        hint = stringResource(Res.string.share_empty_table),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    )
                }
                Spacer(Modifier.height(spacing.contentBottom))
                return@Column
            }

            // ---------- 分享内容（完整 / 精简） ----------
            AppSectionHeader(
                text = stringResource(Res.string.share_section_content),
                modifier = Modifier.fillMaxWidth()
            )
            SectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    AppSegmentedControl(
                        options = listOf(
                            stringResource(Res.string.share_mode_full),
                            stringResource(Res.string.share_mode_compact)
                        ),
                        selectedIndex = if (uiState.mode == CourseShareCodec.Mode.COMPACT) 1 else 0,
                        onSelect = { index ->
                            viewModel.setMode(
                                if (index == 0) CourseShareCodec.Mode.FULL else CourseShareCodec.Mode.COMPACT
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            if (uiState.mode == CourseShareCodec.Mode.COMPACT) {
                                Res.string.share_mode_compact_desc
                            } else {
                                Res.string.share_mode_full_desc
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(Modifier.height(spacing.sectionGap))

            // ---------- 二维码 ----------
            AppSectionHeader(
                text = stringResource(Res.string.share_section_qr),
                modifier = Modifier.fillMaxWidth()
            )
            SectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (qrMatrix != null) {
                        QrCodePanel(matrix = qrMatrix, sideLength = QR_SIDE_LENGTH)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(
                                if (qrUsesCompact) Res.string.share_qr_compact_note else Res.string.share_qr_hint
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.share_qr_too_long),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.danger,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (qrMatrix == null && uiState.mode == CourseShareCodec.Mode.FULL) {
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.share_qr_use_compact),
                        leadingIcon = vectorResource(Res.drawable.code_24px),
                        trailingContent = {},
                        onClick = { viewModel.setMode(CourseShareCodec.Mode.COMPACT) }
                    )
                }
            }

            Spacer(Modifier.height(spacing.sectionGap))

            // ---------- 分享串 ----------
            AppSectionHeader(
                text = stringResource(Res.string.share_section_code),
                modifier = Modifier.fillMaxWidth()
            )
            SectionCard {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    AppTextField(
                        value = code,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 4,
                        maxLines = 8,
                        readOnly = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            Res.string.share_code_summary,
                            code.length,
                            uiState.courseCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.share_action_copy),
                    leadingIcon = vectorResource(Res.drawable.content_copy_24px),
                    trailingContent = {},
                    onClick = copyCode
                )
                if (isShareDialogSupported) {
                    SectionDivider()
                    SettingItem(
                        title = stringResource(Res.string.share_action_share),
                        leadingIcon = vectorResource(Res.drawable.link_24px),
                        trailingContent = {},
                        onClick = shareCode
                    )
                }
                SectionDivider()
                SettingItem(
                    title = stringResource(Res.string.share_action_save),
                    leadingIcon = vectorResource(Res.drawable.code_24px),
                    trailingContent = {},
                    onClick = { fileManager.exportFile(SHARE_FILE_NAME, code.encodeToByteArray()) }
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.share_tip_text),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            Spacer(Modifier.height(spacing.contentBottom))
        }
    }
}

/** 把分享串写进 cacheDir/share_temp，返回绝对路径（失败返回 null）。 */
private fun writeShareTextFile(appStorage: AppStorage, code: String): String? = runCatching {
    val dir = appStorage.cacheDir / "share_temp"
    val file = dir / SHARE_FILE_NAME
    FileSystem.SYSTEM.createDirectories(dir)
    FileSystem.SYSTEM.write(file) { writeUtf8(code) }
    file.toString()
}.getOrNull()
