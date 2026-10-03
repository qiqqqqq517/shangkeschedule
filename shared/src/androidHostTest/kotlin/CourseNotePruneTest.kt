import com.shangkeschedule.data.db.main.CourseNote
import com.shangkeschedule.data.db.main.CourseNoteDao
import com.shangkeschedule.data.repository.CourseNoteRepository
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * 笔记图片「孤儿清理」回归测试（v4.67.34）。
 *
 * 锁定两条契约：
 * 1. **在途图片不能被删**：保存笔记是「先落盘图片、再把路径写进行」两步（`CourseNoteViewModel.saveNote`），
 *    启动清理若恰好在这两步之间读到数据库快照，就会把刚写入的图片当孤儿删掉 —— 用户刚添加的
 *    照片静默丢失且无法恢复。静置期生效后，新建文件一律保留；
 * 2. **清理本身不能失效**：静置期只保护「新文件」，真正的孤儿（无数据库引用且已静置）必须仍被
 *    删除。否则「规则被自己关掉」，本测试就成了假绿灯 —— 因此第二例与第一例必须成对存在。
 *
 * 用真实 `FileSystem.SYSTEM` + 临时目录（与 `OfflineRepoArchiveTest` 同一套做法），不引入新依赖。
 */
class CourseNotePruneTest {

    private val root = File(System.getProperty("java.io.tmpdir"), "note_prune_${System.nanoTime()}")
    private val notesDir = File(root, "notes")

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    /** 在途写入：文件刚落盘、笔记行还没落库 ⇒ 清理必须放过它。 */
    @Test
    fun freshUnreferencedImageSurvivesPrune() {
        val image = writeImage("fresh", ageMillis = 0L)
        prune(notes = emptyList())
        assertTrue(image.first.exists(), "刚落盘（在途）的图片被清理误删：${image.first.name}")
    }

    /** 真孤儿：无引用且已静置 ⇒ 清理必须仍然删得掉（否则静置期等于关掉了清理）。 */
    @Test
    fun staleUnreferencedImageIsDeleted() {
        val image = writeImage("stale", ageMillis = ONE_HOUR)
        prune(notes = emptyList())
        assertFalse(image.first.exists(), "既无引用、又已静置的孤儿图片未被清理：${image.first.name}")
    }

    /** 仍被引用的图片（即使已静置）⇒ 清理必须保留。 */
    @Test
    fun referencedImageSurvivesPrune() {
        val image = writeImage("alive", ageMillis = ONE_HOUR)
        prune(notes = listOf(noteReferencing(image.second)))
        assertTrue(image.first.exists(), "仍被笔记引用的图片被清理误删：${image.first.name}")
    }

    /**
     * 写一张图片并指定「年龄」。
     *
     * 返回值同时给出 java.io.File（断言用）与 okio 口径的路径字符串（数据库引用用）——
     * 仓库里 `saveImage` 记录的正是 okio `Path.toString()`，两侧必须同口径，否则引用集合对不上。
     */
    private fun writeImage(tag: String, ageMillis: Long): Pair<File, String> {
        assertTrue(notesDir.isDirectory || notesDir.mkdirs(), "无法创建笔记图片目录：$notesDir")
        val file = File(notesDir, "note-$tag.jpg")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        if (ageMillis > 0L) {
            Files.setLastModifiedTime(
                file.toPath(),
                FileTime.fromMillis(System.currentTimeMillis() - ageMillis)
            )
        }
        return file to (root.toOkioPath() / "notes" / file.name).toString()
    }

    private fun prune(notes: List<CourseNote>) {
        val repository = CourseNoteRepository(FakeCourseNoteDao(notes), FileSystem.SYSTEM, root.toOkioPath())
        runBlocking { repository.pruneOrphanImages() }
    }

    private fun noteReferencing(okioPath: String) = CourseNote(
        id = "note-alive",
        courseId = "course-1",
        date = "2026-10-03",
        imagePaths = okioPath,
        createdAt = 0L,
        updatedAt = 0L
    )

    private class FakeCourseNoteDao(private var notes: List<CourseNote>) : CourseNoteDao {
        override fun getAllNotes(): Flow<List<CourseNote>> = MutableStateFlow(notes)

        override suspend fun getAllNotesOnce(): List<CourseNote> = notes

        override fun getNotesForCourse(courseId: String): Flow<List<CourseNote>> =
            MutableStateFlow(notes.filter { it.courseId == courseId })

        override suspend fun getNoteOnce(noteId: String): CourseNote? = notes.firstOrNull { it.id == noteId }

        override fun countFlow(): Flow<Int> = MutableStateFlow(notes.size)

        override suspend fun upsert(note: CourseNote) {
            notes = notes.filterNot { it.id == note.id } + note
        }

        override suspend fun insertAll(notes: List<CourseNote>) {
            this.notes = this.notes + notes
        }

        override suspend fun deleteById(noteId: String) {
            notes = notes.filterNot { it.id == noteId }
        }

        override suspend fun deleteAll() {
            notes = emptyList()
        }
    }

    private companion object {
        /** 一小时：远大于静置期（10 分钟），用来构造「真正的孤儿」。 */
        const val ONE_HOUR = 60L * 60L * 1000L
    }
}
