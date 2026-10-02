package com.shangkeschedule.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseNote
import com.shangkeschedule.data.repository.CourseNoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/**
 * 课堂笔记页 ViewModel（v4.66.0）。
 *
 * 课程 id 由页面通过 [bindCourse] 注入（与 `AddEditCourseViewModel.initWithId` 同一惯例：
 * Koin `@KoinViewModel` 构造函数不接参数，页面在 `LaunchedEffect` 里喂参数）。
 * 先绑定再订阅，避免页面重组时反复重建 Flow。
 */
@KoinViewModel
class CourseNoteViewModel(
    private val courseNoteRepository: CourseNoteRepository
) : ViewModel() {

    private val boundCourseId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<CourseNote>> = boundCourseId
        .filterNotNull()
        .flatMapLatest { courseId -> courseNoteRepository.getNotesForCourse(courseId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun bindCourse(courseId: String) {
        boundCourseId.value = courseId
    }

    /**
     * 保存一条笔记（[noteId] 为 null 表示新建）。
     *
     * [newImages] 是本次新选的图片字节，落盘后与 [keptPaths]（保留的旧图）合并写入
     * `imagePaths`；旧图里被用户移除的路径会顺带删除文件。
     *
     * [onResult] 回传落库结果：UI 必须等成功后才提示「已保存」并关闭编辑器，
     * 否则写库 / 落盘失败时用户输入会随着编辑器关闭静默丢失。
     */
    fun saveNote(
        noteId: String?,
        date: String,
        sections: String?,
        title: String,
        content: String,
        keptPaths: List<String>,
        newImages: List<ByteArray>,
        onResult: (Boolean) -> Unit = {}
    ) {
        val courseId = boundCourseId.value
        if (courseId == null) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val ok = runCatching {
                val id = noteId ?: courseNoteRepository.newNoteId()
                val existing = noteId?.let { courseNoteRepository.getNoteOnce(it) }
                val written = newImages.mapNotNull { courseNoteRepository.saveImage(id, it) }
                val paths = keptPaths + written
                val now = kotlin.time.Clock.System.now().toEpochMilliseconds()

                courseNoteRepository.saveNote(
                    CourseNote(
                        id = id,
                        courseId = courseId,
                        date = date,
                        sections = sections?.trim()?.takeIf { it.isNotEmpty() },
                        title = title.trim().take(MAX_TITLE_LENGTH),
                        content = content.take(MAX_CONTENT_LENGTH),
                        imagePaths = paths.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now
                    )
                )

                // 用户在这次编辑里移除的旧图：文件一并清掉，避免 notes/ 目录里留孤儿
                val removed = existing?.imagePaths
                    ?.split('\n')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() && it !in keptPaths }
                    .orEmpty()
                if (removed.isNotEmpty()) {
                    courseNoteRepository.deleteImages(removed.joinToString("\n"))
                }
            }.isSuccess
            onResult(ok)
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            val note = courseNoteRepository.getNoteOnce(noteId) ?: return@launch
            courseNoteRepository.deleteNote(note)
        }
    }

    companion object {
        /** 标题上限：够写「第 3 章 导数」这类短标题，又不至于把列表撑爆。 */
        const val MAX_TITLE_LENGTH = 60

        /** 正文上限：与备注 300 字的上限不同，笔记允许写长（约 2000 字）。 */
        const val MAX_CONTENT_LENGTH = 2000
    }
}
