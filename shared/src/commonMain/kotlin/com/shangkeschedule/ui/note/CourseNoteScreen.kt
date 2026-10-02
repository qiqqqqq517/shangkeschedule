package com.shangkeschedule.ui.note

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.shangkeschedule.data.db.main.CourseNote
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.DatePickerModal
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.components.cropImageBitmapNative
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.image_24px
import shangkeschedule.shared.generated.resources.note_add_image
import shangkeschedule.shared.generated.resources.note_cancel
import shangkeschedule.shared.generated.resources.note_delete
import shangkeschedule.shared.generated.resources.note_delete_confirm_text
import shangkeschedule.shared.generated.resources.note_delete_confirm_title
import shangkeschedule.shared.generated.resources.note_edit
import shangkeschedule.shared.generated.resources.note_empty
import shangkeschedule.shared.generated.resources.note_empty_hint
import shangkeschedule.shared.generated.resources.note_image_failed
import shangkeschedule.shared.generated.resources.note_label_content
import shangkeschedule.shared.generated.resources.note_label_date
import shangkeschedule.shared.generated.resources.note_label_images
import shangkeschedule.shared.generated.resources.note_label_sections
import shangkeschedule.shared.generated.resources.note_label_title
import shangkeschedule.shared.generated.resources.note_new
import shangkeschedule.shared.generated.resources.note_save
import shangkeschedule.shared.generated.resources.note_save_failed
import shangkeschedule.shared.generated.resources.note_saved
import shangkeschedule.shared.generated.resources.title_course_notes
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 课堂笔记页（v4.66.0）。
 *
 * 按课次记录的听课笔记：一次课一条（日期 + 节次），正文之外可以附图片（板书照片）。
 * 入口在课程详情弹窗的「课堂笔记」一行，课程名与节次由调用方传入，页面本身只认
 * `courseId`。
 *
 * 设计取舍（与星链对齐时的判断）：
 * - **不做富文本**：纯文本 + 图片足够覆盖「记重点 / 拍板书」这两个真实场景，富文本会
 *   引入跨端编辑器与序列化成本，收益不成正比；
 * - **不做独立于课程的笔记夹**：笔记一定要挂在某门课的某一次课上，否则「随课表删除」
 *   （外键 CASCADE）这条最省心的清理路径就断了；
 * - 图片只做「拍/选 → 压缩为 JPEG → 落本地文件」，「保存后即可见」由列表里的缩略图承担。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseNoteScreen(
    courseId: String,
    courseName: String,
    sectionLabel: String = "",
    onBack: () -> Unit,
    viewModel: CourseNoteViewModel = koinViewModel()
) {
    LaunchedEffect(courseId) { viewModel.bindCourse(courseId) }

    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = appColors()
    val spacing = appSpacing()

    // 不 remember：跨零点后新建笔记要取当天日期，缓存住会一直用进页面那天的日期
    val today = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()

    // ---- 文案 ----
    val pageTitle = stringResource(Res.string.title_course_notes)
    val textNew = stringResource(Res.string.note_new)
    val textEdit = stringResource(Res.string.note_edit)
    val textEmpty = stringResource(Res.string.note_empty)
    val textEmptyHint = stringResource(Res.string.note_empty_hint)
    val labelDate = stringResource(Res.string.note_label_date)
    val labelSections = stringResource(Res.string.note_label_sections)
    val labelTitle = stringResource(Res.string.note_label_title)
    val labelContent = stringResource(Res.string.note_label_content)
    val labelImages = stringResource(Res.string.note_label_images)
    val textAddImage = stringResource(Res.string.note_add_image)
    val textSave = stringResource(Res.string.note_save)
    val textCancel = stringResource(Res.string.note_cancel)
    val textDelete = stringResource(Res.string.note_delete)
    val textDeleteTitle = stringResource(Res.string.note_delete_confirm_title)
    val textDeleteText = stringResource(Res.string.note_delete_confirm_text)
    val textImageFailed = stringResource(Res.string.note_image_failed)
    val textSaved = stringResource(Res.string.note_saved)
    val textSaveFailed = stringResource(Res.string.note_save_failed)

    // ---- 编辑态 ----
    // 标量草稿用 rememberSaveable：转屏 / 进程重建后不丢用户输入；
    // 图片（含还没落盘的字节）无法序列化，仍用 remember，重建后需要重新选图。
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorDate by rememberSaveable { mutableStateOf(today) }
    var editorSections by rememberSaveable { mutableStateOf("") }
    var editorTitle by rememberSaveable { mutableStateOf("") }
    var editorContent by rememberSaveable { mutableStateOf("") }
    val editorImages = remember { mutableStateListOf<NoteImageSource>() }

    var showDatePicker by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CourseNote?>(null) }

    fun openNewNote() {
        editorNoteId = null
        editorDate = today
        editorSections = sectionLabel
        editorTitle = ""
        editorContent = ""
        editorImages.clear()
        editorOpen = true
    }

    fun openExistingNote(note: CourseNote) {
        editorNoteId = note.id
        editorDate = note.date
        editorSections = note.sections.orEmpty()
        editorTitle = note.title
        editorContent = note.content
        editorImages.clear()
        editorImages.addAll(
            note.imagePaths
                ?.split('\n')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.map { NoteImageSource.Existing(it) }
                .orEmpty()
        )
        editorOpen = true
    }

    val fileManager = rememberFileManager(
        FileManagerCallbacks(
            onImagePicked = { bitmap ->
                if (bitmap != null) {
                    scope.launch {
                        val bytes = runCatching {
                            cropImageBitmapNative(bitmap, 0, 0, bitmap.width, bitmap.height)
                        }.getOrNull()
                        if (bytes == null || bytes.isEmpty()) {
                            ToastManager.show(textImageFailed)
                        } else {
                            editorImages.add(NoteImageSource.Fresh(bytes, bitmap))
                        }
                    }
                }
            }
        )
    )

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(pageTitle) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = spacing.pageHorizontal,
                end = spacing.pageHorizontal,
                top = spacing.cardGap,
                bottom = spacing.contentBottom
            )
        ) {
            item(key = "course-name") {
                Text(
                    text = courseName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(spacing.listGap))
            }

            item(key = "new-note") {
                SectionCard {
                    SettingItem(
                        title = textNew,
                        leadingIcon = vectorResource(Res.drawable.add_24px),
                        onClick = { openNewNote() }
                    )
                }
                Spacer(modifier = Modifier.height(spacing.listGap))
            }

            if (notes.isEmpty()) {
                item(key = "empty") {
                    SectionCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp)
                        ) {
                            Text(
                                text = textEmpty,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = textEmptyHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            items(notes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    deleteLabel = textDelete,
                    onClick = { openExistingNote(note) },
                    onDelete = { pendingDelete = note }
                )
                Spacer(modifier = Modifier.height(spacing.listGap))
            }
        }
    }

    if (editorOpen) {
        AppGlassBottomSheet(
            hazeState = null,
            onDismissRequest = { editorOpen = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                Text(
                    text = if (editorNoteId == null) textNew else textEdit,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 课次日期：用系统日期选择器，避免手打 "yyyy-MM-dd" 出错
                SettingItem(
                    title = labelDate,
                    subtitle = editorDate,
                    verticalPadding = 10.dp,
                    onClick = { showDatePicker = true }
                )

                AppTextField(
                    value = editorSections,
                    onValueChange = { editorSections = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = labelSections,
                    singleLine = true
                )

                AppTextField(
                    value = editorTitle,
                    onValueChange = { if (it.length <= CourseNoteViewModel.MAX_TITLE_LENGTH) editorTitle = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = labelTitle,
                    singleLine = true
                )

                AppTextField(
                    value = editorContent,
                    onValueChange = { if (it.length <= CourseNoteViewModel.MAX_CONTENT_LENGTH) editorContent = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = labelContent,
                    singleLine = false,
                    minLines = 5,
                    maxLines = 10
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = labelImages,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                NoteImagePickerRow(
                    images = editorImages,
                    addLabel = textAddImage,
                    onAdd = { fileManager.pickImage() },
                    onRemove = { index -> editorImages.removeAt(index) }
                )

                Spacer(modifier = Modifier.height(18.dp))
                AppDialogActions(
                    confirmText = textSave,
                    onConfirm = {
                        viewModel.saveNote(
                            noteId = editorNoteId,
                            date = editorDate,
                            sections = editorSections,
                            title = editorTitle,
                            content = editorContent,
                            keptPaths = editorImages.filterIsInstance<NoteImageSource.Existing>().map { it.path },
                            newImages = editorImages.filterIsInstance<NoteImageSource.Fresh>().map { it.bytes }
                        ) { ok ->
                            // 落库成功才提示「已保存」并关闭编辑器；失败时保留编辑态，输入不丢
                            if (ok) {
                                ToastManager.show(textSaved)
                                editorOpen = false
                                editorImages.clear()
                            } else {
                                ToastManager.show(textSaveFailed)
                            }
                        }
                    },
                    dismissText = textCancel,
                    onDismiss = { editorOpen = false }
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            onDateSelected = { millis ->
                if (millis != null) {
                    editorDate = Instant.fromEpochMilliseconds(millis)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                        .toString()
                }
            },
            onDismiss = { showDatePicker = false }
        )
    }

    pendingDelete?.let { note ->
        AppAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    text = textDeleteTitle,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = textDeleteText,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                AppDialogActions(
                    confirmText = textDelete,
                    onConfirm = {
                        viewModel.deleteNote(note.id)
                        pendingDelete = null
                    },
                    dismissText = textCancel,
                    onDismiss = { pendingDelete = null },
                    danger = true
                )
            }
        )
    }
}

/**
 * 单条笔记卡：日期 + 节次 / 标题 / 正文摘要 / 缩略图，右侧删除入口。
 * 整卡可点，进入编辑。
 */
@Composable
private fun NoteCard(
    note: CourseNote,
    deleteLabel: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = appColors()
    val paths = remember(note.imagePaths) {
        note.imagePaths
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listOfNotNull(
                        note.date.takeIf { it.isNotBlank() },
                        note.sections?.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
                if (note.title.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary
                    )
                }
                if (note.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        maxLines = 4
                    )
                }
                if (paths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        paths.take(MAX_NOTE_THUMBNAILS).forEach { path ->
                            AsyncImage(
                                model = path,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        if (paths.size > MAX_NOTE_THUMBNAILS) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.textSecondary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${paths.size - MAX_NOTE_THUMBNAILS}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = vectorResource(Res.drawable.delete_24px),
                    contentDescription = deleteLabel,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 图片编辑行：已有的图 + 本次新选的图缩略图并排，每张右上角「×」移除；末尾是「添加图片」。
 */
@Composable
private fun NoteImagePickerRow(
    images: List<NoteImageSource>,
    addLabel: String,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    val colors = appColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        images.forEachIndexed { index, source ->
            Box(modifier = Modifier.size(64.dp)) {
                when (source) {
                    is NoteImageSource.Existing -> AsyncImage(
                        model = source.path,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    is NoteImageSource.Fresh -> Image(
                        bitmap = source.preview,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.textPrimary.copy(alpha = 0.55f))
                        .clickable { onRemove(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.delete_24px),
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.textSecondary.copy(alpha = 0.10f))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = vectorResource(Res.drawable.image_24px),
                    contentDescription = addLabel,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = addLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}

/** 编辑态里的一张图片：库里的旧图（按路径复用）或本次新选的图（带预览位图，保存前即可见）。 */
private sealed interface NoteImageSource {
    data class Existing(val path: String) : NoteImageSource
    data class Fresh(val bytes: ByteArray, val preview: ImageBitmap) : NoteImageSource
}

/** 单条笔记最多展示的缩略图张数，超出折叠成「+N」。 */
private const val MAX_NOTE_THUMBNAILS = 3
