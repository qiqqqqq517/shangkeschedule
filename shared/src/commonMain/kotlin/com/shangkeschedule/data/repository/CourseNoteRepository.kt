package com.shangkeschedule.data.repository

import com.shangkeschedule.data.db.main.CourseNote
import com.shangkeschedule.data.db.main.CourseNoteDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 课堂笔记仓库（v4.66.0）。
 *
 * 除了笔记文本本身，还负责图片附件的落盘与回收：
 * - 图片写在 `filesDir/notes/` 下（文件名 `<笔记 id>_<时间戳>.jpg`），数据库里只存绝对路径；
 * - 删除笔记时顺带删掉它的图片文件，避免 `notes/` 目录无限膨胀；
 * - 图片路径写进笔记行，因此随课程级联删除时，行会消失，但**孤儿文件仍需由
 *   [deleteNote] 或 [deleteImages] 清理**（级联删除发生在 SQLite 侧，仓库拿不到通知；
 *   应用启动时的清理见 `ResourceInitializerManager` 之外的本仓库 [pruneOrphanImages]）。
 */
@Single
class CourseNoteRepository(
    private val courseNoteDao: CourseNoteDao,
    private val fileSystem: FileSystem,
    @Named("FilesDir") private val filesDir: Path
) {

    /** 笔记图片目录：`filesDir/notes`。 */
    private val notesDir: Path get() = filesDir / "notes"

    fun getAllNotes(): Flow<List<CourseNote>> = courseNoteDao.getAllNotes()

    fun getNotesForCourse(courseId: String): Flow<List<CourseNote>> =
        courseNoteDao.getNotesForCourse(courseId)

    fun getNoteCount(): Flow<Int> = courseNoteDao.countFlow()

    suspend fun getNoteOnce(noteId: String): CourseNote? = courseNoteDao.getNoteOnce(noteId)

    suspend fun getAllNotesOnce(): List<CourseNote> = courseNoteDao.getAllNotesOnce()

    suspend fun saveNote(note: CourseNote) = courseNoteDao.upsert(note)

    suspend fun insertAll(notes: List<CourseNote>) = courseNoteDao.insertAll(notes)

    suspend fun deleteAll() = courseNoteDao.deleteAll()

    /**
     * 保存一张笔记图片，返回可直接交给 `AsyncImage(model = ...)` 的绝对路径；失败返回 null。
     *
     * 复用 [com.shangkeschedule.ui.components.cropImageBitmapNative] 的输出（三端统一 JPEG），
     * 因此扩展名固定 `.jpg`。
     */
    suspend fun saveImage(noteId: String, bytes: ByteArray): String? = withContext(Dispatchers.Default) {
        runCatching {
            fileSystem.createDirectories(notesDir)
            val target = notesDir / "${noteId}_${Clock.System.now().toEpochMilliseconds()}.jpg"
            fileSystem.write(target) { write(bytes) }
            target.toString()
        }.getOrNull()
    }

    /** 删除一条笔记，并回收它的图片文件。 */
    suspend fun deleteNote(note: CourseNote) {
        courseNoteDao.deleteById(note.id)
        note.imagePaths?.let { deleteImages(it) }
    }

    /** 删除 [imagePaths]（`'\n'` 分隔的绝对路径）指向的图片文件；不存在的路径静默跳过。 */
    suspend fun deleteImages(imagePaths: String) = withContext(Dispatchers.Default) {
        imagePaths.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { path ->
                runCatching {
                    val target = path.toPath()
                    if (fileSystem.exists(target)) fileSystem.delete(target)
                }
            }
    }

    /**
     * 清理孤儿图片（v4.66.0）。
     *
     * 课程被删除时，笔记行由外键 CASCADE 清掉，仓库收不到任何回调，`notes/` 里的图片
     * 文件就成了孤儿。这里按「数据库里还在引用的路径集合」反向清理目录，
     * 由应用启动时的资源初始化阶段调用一次即可。
     *
     * 保存笔记是「先落盘图片、再把路径写进行」两步（见 `CourseNoteViewModel.saveNote`）。
     * 启动清理的数据库快照若恰好落在这两步之间，刚写入的图片会因为「库里还没有引用」
     * 被误判成孤儿删掉 —— 用户刚添加的照片静默丢失，且无法恢复。因此只清理「静置」
     * 够久的文件：比 [ORPHAN_GRACE_MILLIS] 更新的、以及读不到时间戳的，一律保留，
     * 交给下次启动再判（真孤儿最多多留一轮启动，在途图片不可能被误删）。
     */
    suspend fun pruneOrphanImages() = withContext(Dispatchers.Default) {
        runCatching {
            if (!fileSystem.exists(notesDir)) return@runCatching
            // 静置期基准要在读取数据库「引用集」之前取，才能覆盖本次清理开始后才落盘的在途图片。
            val cutoff = Clock.System.now().toEpochMilliseconds() - ORPHAN_GRACE_MILLIS
            val alive = courseNoteDao.getAllNotesOnce()
                .mapNotNull { it.imagePaths }
                .flatMap { it.split('\n') }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            fileSystem.list(notesDir).forEach { path ->
                if (path.toString() in alive) return@forEach
                val writtenAt = fileSystem.metadataOrNull(path)?.lastModifiedAtMillis
                if (writtenAt == null || writtenAt >= cutoff) return@forEach
                runCatching { fileSystem.delete(path) }
            }
        }
    }

    /** 生成一个新的笔记 id（本地，不依赖服务端）。 */
    @OptIn(ExperimentalUuidApi::class)
    fun newNoteId(): String = Uuid.random().toString()

    private companion object {
        /**
         * 孤儿图片判定静置期（10 分钟）。
         *
         * 取值远大于「一张图片落盘 → 笔记行落库」的间隔（毫秒级），又远小于两次启动之间的
         * 正常间隔：真实孤儿最多多留一轮启动，而在途写入的图片不可能被误删。
         */
        const val ORPHAN_GRACE_MILLIS = 10 * 60 * 1000L
    }
}
