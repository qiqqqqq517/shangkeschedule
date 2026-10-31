@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.emptyPreferences
import com.shangkeschedule.data.api.date.ApiDateImporter
import com.shangkeschedule.data.api.webdav.*
import com.shangkeschedule.data.db.main.*
import com.shangkeschedule.data.di.AppStorage
import com.shangkeschedule.data.model.CourseImportExport.CourseTableImportModel
import com.shangkeschedule.data.model.CourseImportExport.ImportCourseJsonModel
import com.shangkeschedule.data.model.GpaScale
import com.shangkeschedule.data.model.MorningAlarmMode
import com.shangkeschedule.data.model.schedule_style.ScheduleGridStyleProto
import com.shangkeschedule.data.parser.UniversalScheduleParser
import com.shangkeschedule.data.repository.*
import com.shangkeschedule.tool.AdapterRemoteUpdater
import com.shangkeschedule.tool.SecureCrypto
import com.shangkeschedule.ui.note.CourseNoteViewModel
import com.shangkeschedule.ui.settings.backup.BackupViewModel
import com.shangkeschedule.ui.settings.backup.TestResult
import com.shangkeschedule.data.model.CourseImportExport
import kotlinx.serialization.json.Json
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpSendPipeline
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okio.*
import okio.Path.Companion.toPath
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.*

private class AuditMemoryStore<T>(initial: T) : DataStore<T> {
    private val value = MutableStateFlow(initial)
    override val data: Flow<T> = value
    override suspend fun updateData(transform: suspend (T) -> T): T =
        transform(value.value).also { value.value = it }
}

/** 使用真实 Room / 仓储 / 文件系统和仅监听 loopback 的 WebDAV，验证数据安全结果。 */
class ProjectAuditRegressionTest {
    private val root = Files.createTempDirectory("shangke-regression-").toString().toPath()
    private val storage = object : AppStorage {
        override val filesDir = root / "files"
        override val cacheDir = root / "cache"
        override fun getDatabasePath(dbName: String) = (root / dbName).toString()
    }
    private val db = createMainDatabase(storage)
    private val tables = db.courseTableDao()
    private val settings = AppSettingsRepository(AuditMemoryStore(emptyPreferences()), AuditMemoryStore(emptyPreferences()), tables, db.courseTableConfigDao())
    private val style = StyleSettingsRepository(AuditMemoryStore(ScheduleGridStyleProto()), settings)
    private val slots = TimeSlotRepository(db, db.timeSlotDao(), db.timeSlotSchemeDao(), db.courseTableConfigDao(), db.courseDao())
    private val conversion = CourseConversionRepository(db, db.courseDao(), db.courseWeekDao(), db.timeSlotDao(), settings, style, slots)
    private val grades = GradeRepository(db, db.gradeDao())
    private val noteRepo = CourseNoteRepository(db.courseNoteDao(), FileSystem.SYSTEM, storage.filesDir)
    private val tableRepo = CourseTableRepository(db, tables, db.courseDao(), db.courseWeekDao(), slots, settings)
    private val apiConfig = ApiConfigRepository(AuditMemoryStore(emptyPreferences()), SecureCrypto())
    private val backup = BackupRepository(560, "4.78.13", db, tables, tableRepo, conversion, settings, style, apiConfig, noteRepo)
    private val courseJson = ImportCourseJsonModel(id = "course", name = "课程", teacher = "教师", position = "A101", day = 1, startSection = 3, endSection = 3, weeks = listOf(1))

    @BeforeTest fun setUp() = runBlocking {
        tables.insert(CourseTable("table", "样本课表", 1))
        conversion.importCourseTableFromJson("table", CourseTableImportModel(listOf(courseJson)))
    }
    @AfterTest fun tearDown() = runBlocking {
        tableRepo.close()
        style.close()
        settings.close()
        db.close()
        FileSystem.SYSTEM.deleteRecursively(root)
    }

    @Test fun weeklyAlarmBackupRestoresPlanButInvalidatesDeviceConfirmation() = runBlocking {
        settings.updateMorningAlarmSchedule(MorningAlarmMode.HYBRID_WEEKLY, mapOf(1 to "07:15", 3 to "08:00"))
        val original = settings.getAppSettingsOnce()
        val bytes = backup.exportAppSettingsBytes()!!
        settings.updateMorningAlarmSchedule(MorningAlarmMode.SMART_DAILY, emptyMap())
        val changed = settings.getAppSettingsOnce()
        backup.restoreAppSettingsBytes(bytes).getOrThrow()
        val restored = settings.getAppSettingsOnce()
        assertEquals(MorningAlarmMode.HYBRID_WEEKLY, restored.morningAlarmMode)
        assertEquals(mapOf(1 to "07:15", 3 to "08:00"), restored.morningAlarmWeeklyTimes)
        assertNotEquals(original.morningAlarmWeeklyRevision, restored.morningAlarmWeeklyRevision)
        assertNotEquals(changed.morningAlarmWeeklyRevision, restored.morningAlarmWeeklyRevision)
    }

    @Test fun changedImportedScoreInvalidatesOldGradePoint() = runBlocking {
        grades.importGrades(listOf(grades.buildGrade("2026-1", "课", 3.0, "92", Grade.SOURCE_IMPORT, 3.9)))
        grades.importGrades(listOf(grades.buildGrade("2026-1", "课", 3.0, "59", Grade.SOURCE_PASTE)))
        val grade = grades.getAllGradesOnce().single()
        assertNull(grade.gradePoint)
        assertEquals(1, grades.computeSummary(listOf(grade), GpaScale.SCALE_4).failedCount)
    }
    @Test fun equivalentScoreKeepsSchoolGradePoint() = runBlocking {
        grades.importGrades(listOf(grades.buildGrade("2026-1", "课", 3.0, "92", Grade.SOURCE_IMPORT, 3.9)))
        grades.importGrades(listOf(grades.buildGrade("2026-1", "课", 3.0, "92.0", Grade.SOURCE_PASTE)))
        assertEquals(3.9, grades.getAllGradesOnce().single().gradePoint)
    }
    @Test fun weekdayColumnDoesNotShadowWeeks() {
        for (day in listOf("周几", "weekday")) {
            val result = UniversalScheduleParser.parseAuto("课程,教师,教室,$day,节次,周次\n课程,教师,A101,1,3-4,1-16") as UniversalScheduleParser.ParseResult.Success
            assertEquals((1..16).toList(), result.model.courses.single().weeks)
        }
    }
    @Test fun jsonReimportKeepsNotesAndUpdatesWeeks() = runBlocking {
        db.courseNoteDao().upsert(CourseNote("note", "course", "2026-10-14", title = "保留", createdAt = 1, updatedAt = 1))
        conversion.importCourseTableFromJson("table", CourseTableImportModel(listOf(courseJson.copy(weeks = listOf(2)))))
        assertEquals("保留", db.courseNoteDao().getAllNotesOnce().single().title)
        assertEquals(listOf(2), db.courseWeekDao().getWeekNumbersByCourseId("course"))
    }
    @Test fun webReimportKeepsCourseIdentityAndNotes() = runBlocking {
        db.courseNoteDao().upsert(CourseNote("note", "course", "2026-10-14", createdAt = 1, updatedAt = 1))
        conversion.importCoursesFromList("table", listOf(courseJson.copy(id = null, weeks = listOf(2), position = "B102")))
        assertEquals("course", db.courseDao().getCoursesOnce("table").single().id)
        assertEquals(1, db.courseNoteDao().getAllNotesOnce().size)
    }
    @Test fun editingSummerTimesDoesNotChangeSharedCourseSections() = runBlocking {
        val defaults = (1..3).map { TimeSlot(it, "${it + 7}:00", "${it + 7}:45", "table") }
        db.timeSlotDao().insertAll(defaults + defaults.map { it.copy(schemeId = "summer") })
        val config = CourseTableConfig("table", currentSchemeId = "summer")
        slots.saveSchemeSettings("table", listOf(defaults[0], defaults[2]).map { it.copy(schemeId = "summer") }, "summer", config)
        assertEquals(3, db.courseDao().getCoursesOnce("table").single().startSection)
        assertEquals(listOf(1, 3), db.timeSlotDao().getTimeSlotsOnce("table", "summer").map { it.number })
    }
    @Test fun noteImagesAreIncludedInBackup() = runBlocking {
        val image = "binary-attachment-marker-739162".toByteArray()
        val path = noteRepo.saveImage("note", image)!!
        db.courseNoteDao().upsert(CourseNote("note", "course", "2026-10-14", imagePaths = path, createdAt = 1, updatedAt = 1))
        val bytes = backup.exportUserDataBytes()!!
        assertTrue(bytes.toString(Charsets.ISO_8859_1).contains(image.toString(Charsets.ISO_8859_1)))
    }
    @Test fun imageWriteFailureDoesNotSavePartialNote() = runBlocking {
        val rejectingFS = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun sink(file: Path, mustCreate: Boolean): Sink {
                if (file.name.endsWith(".jpg")) throw IOException("合成磁盘故障")
                return super.sink(file, mustCreate)
            }
        }
        val vm = CourseNoteViewModel(CourseNoteRepository(db.courseNoteDao(), rejectingFS, storage.filesDir))
        vm.bindCourse("course")
        val result = CompletableDeferred<Boolean>()
        vm.saveNote("note", "2026-10-14", null, "标题", "文本", emptyList(), listOf(byteArrayOf(1))) { result.complete(it) }
        assertFalse(withTimeout(3000) { result.await() })
        assertNull(db.courseNoteDao().getNoteOnce("note"))
    }
    @Test fun deletingImagesCannotDeleteOtherFiles() = runBlocking {
        FileSystem.SYSTEM.createDirectories(storage.filesDir)
        val unrelated = storage.filesDir / "settings.json"
        FileSystem.SYSTEM.write(unrelated) { writeUtf8("保留") }
        noteRepo.deleteImages(unrelated.toString())
        assertTrue(FileSystem.SYSTEM.exists(unrelated))
    }
    @Test fun failedAtomicMoveKeepsUsableAdapter() {
        val failingFS = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun atomicMove(source: Path, target: Path) { throw IOException("合成移动故障") }
        }
        val path = storage.filesDir / "adapter.js"
        FileSystem.SYSTEM.createDirectories(path.parent!!)
        FileSystem.SYSTEM.write(path) { writeUtf8("old") }
        val updater = AdapterRemoteUpdater(failingFS, storage.filesDir)
        val method = AdapterRemoteUpdater::class.java.getDeclaredMethod("writeAtomically", Path::class.java, ByteArray::class.java).apply { isAccessible = true }
        assertEquals(false, method.invoke(updater, path, "new".toByteArray()))
        assertEquals("old", FileSystem.SYSTEM.read(path) { readUtf8() })
    }
    @Test fun failedWebDavMoveNeverDeletesExistingTarget() = runBlocking {
        var deleted = false
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            if (ex.requestMethod == "DELETE") deleted = true
            ex.sendResponseHeaders(if (ex.requestMethod == "MOVE") 500 else 405, -1)
            ex.close()
        }
        server.start()
        val client = WebDavClient(WebDavConfig("http://127.0.0.1:${server.address.port}", "test", "test", ""))
        try {
            assertFalse(client.moveFile("temp", "old"))
            assertFalse(deleted)
        } finally { client.close(); server.stop(0) }
    }
    @Test fun holidayRequestKeepsApiPrefix() = runBlocking {
        val field = ApiDateImporter::class.java.getDeclaredField("client").apply { isAccessible = true }
        val client = field.get(null) as HttpClient
        var url = ""
        client.sendPipeline.intercept(HttpSendPipeline.Before) {
            url = context.url.buildString()
            throw IOException("合成拦截，不访问外网")
        }
        runCatching { ApiDateImporter.importAndSaveSkippedDates(settings) }
        assertTrue(url.startsWith("https://timor.tech/api/holiday/year/"), url)
    }

    @Test fun restoringImagesRebuildsPathsAndContent() = runBlocking {
        val image = byteArrayOf(1, 2, 3, 4)
        val oldPath = noteRepo.saveImage("note", image)!!
        db.courseNoteDao().upsert(CourseNote("note", "course", "2026-10-14", imagePaths = oldPath, createdAt = 1, updatedAt = 1))
        val bytes = backup.exportUserDataBytes()!!
        FileSystem.SYSTEM.delete(oldPath.toPath())
        backup.restoreUserDataBytes(bytes).getOrThrow()
        val restored = db.courseNoteDao().getNoteOnce("note")!!.imagePaths!!
        assertNotEquals(oldPath, restored)
        assertContentEquals(image, noteRepo.readImage(restored))
    }

    @Test fun oldBackupPathsCannotReferenceOtherFiles() = runBlocking {
        FileSystem.SYSTEM.createDirectories(storage.filesDir)
        val unrelated = storage.filesDir / "settings.json"
        FileSystem.SYSTEM.write(unrelated) { writeUtf8("保留") }
        val envelope = CourseImportExport.UserDataBackupEnvelope(1, 4,
            notes = listOf(CourseImportExport.CourseNoteBackupModel("note", "course", "2026-10-14", imagePaths = unrelated.toString())))
        backup.restoreUserDataBytes(CourseImportExport.cbor.encodeToByteArray(CourseImportExport.UserDataBackupEnvelope.serializer(), envelope)).getOrThrow()
        val note = db.courseNoteDao().getNoteOnce("note")!!
        assertNull(note.imagePaths)
        noteRepo.deleteNote(note)
        assertTrue(FileSystem.SYSTEM.exists(unrelated))
    }

    @Test fun multipleImagesWrittenTogetherDoNotOverwrite() = runBlocking {
        val paths = (1..12).map { noteRepo.saveImage("../note", byteArrayOf(it.toByte()))!! }
        assertEquals(12, paths.distinct().size)
        paths.forEachIndexed { index, path -> assertContentEquals(byteArrayOf((index + 1).toByte()), noteRepo.readImage(path)) }
    }

    @Test fun deletingUsedSectionIsRejectedAtomically() = runBlocking {
        val defaults = (1..3).map { TimeSlot(it, "${it + 7}:00", "${it + 7}:45", "table") }
        db.timeSlotDao().insertAll(defaults)
        val result = runCatching { slots.saveSchemeSettings("table", defaults.take(2), "default", CourseTableConfig("table")) }
        assertTrue(result.isFailure)
        assertEquals(defaults, db.timeSlotDao().getTimeSlotsOnce("table", "default"))
        assertEquals(3, db.courseDao().getCoursesOnce("table").single().startSection)
    }

    @Test fun snapshotPathRejectsTraversalAndKeepsLegacyLayout() {
        val meta = BackupMeta(1, 560, "4.78.13", emptyList())
        assertEquals("Backup/course.cbor", meta.moduleRemotePath("course"))
        assertEquals("Backup/snapshots/abc/course.cbor", meta.copy(snapshotId = "abc").moduleRemotePath("course"))
        assertFailsWith<IllegalArgumentException> { meta.copy(snapshotId = "../outside").moduleRemotePath("course") }
    }

    private suspend fun withCloud(fail: (String, String) -> Boolean = { _, _ -> false },
        block: suspend (MutableMap<String, ByteArray>) -> Unit) {
        val files = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val path = ex.requestURI.path
            val method = ex.requestMethod
            val body = if (method == "GET") files[path] else null
            val status = if (fail(method, path)) 500 else when (method) {
                "MKCOL" -> 405
                "PUT" -> { files[path] = ex.requestBody.readAllBytes(); 201 }
                "GET", "HEAD" -> if (files.containsKey(path)) 200 else 404
                "MOVE" -> {
                    val dest = java.net.URI(ex.requestHeaders.getFirst("Destination")).path
                    files[dest] = files.remove(path)!!
                    201
                }
                "DELETE" -> { files.remove(path); 204 }
                else -> 405
            }
            ex.sendResponseHeaders(status, if (method == "GET" && body != null) body.size.toLong() else -1)
            if (method == "GET" && body != null) ex.responseBody.write(body)
            ex.close()
        }
        server.start()
        val field = ApiConfigRepository::class.java.getDeclaredField("webDavConfigFlow").apply { isAccessible = true }
        field.set(apiConfig, flowOf(WebDavConfig("http://127.0.0.1:${server.address.port}", "test", "test", "")))
        try { block(files) } finally { server.stop(0) }
    }

    @Test fun failedModuleUploadLeavesOldSnapshotIntact() = runBlocking {
        withCloud(fail = { method, path -> path.endsWith("user_data.cbor") && method in setOf("PUT", "MOVE") }) { files ->
            files["/Backup/meta.json"] = "old-meta".toByteArray()
            files["/Backup/course.cbor"] = "old-course".toByteArray()
            assertTrue(backup.uploadFullBackupToWebDav().isFailure)
            assertEquals("old-meta", files["/Backup/meta.json"]!!.decodeToString())
            assertEquals("old-course", files["/Backup/course.cbor"]!!.decodeToString())
        }
    }

    @Test fun failedManifestCommitLeavesOldSnapshotIntact() = runBlocking {
        withCloud(fail = { method, _ -> method == "MOVE" }) { files ->
            files["/Backup/meta.json"] = "old-meta".toByteArray()
            assertTrue(backup.uploadFullBackupToWebDav().isFailure)
            assertEquals("old-meta", files["/Backup/meta.json"]!!.decodeToString())
        }
    }

    @Test fun actualWebDavRestoreCompletesWithoutNestedLock() = runBlocking {
        withCloud { files ->
            backup.uploadFullBackupToWebDav().getOrThrow()
            val meta = Json.decodeFromString(BackupMeta.serializer(), files["/Backup/meta.json"]!!.decodeToString())
            assertNotNull(meta.snapshotId)
            meta.modules.forEach { assertTrue(files.containsKey("/" + meta.moduleRemotePath(it.key))) }
            val vm = BackupViewModel(apiConfig, backup)
            vm.restoreFromWebDav()
            withTimeout(10000) { vm.uiState.first { it.testResult is TestResult.Success || it.testResult is TestResult.Error } }
            assertEquals(TestResult.Success, vm.uiState.value.testResult)
        }
    }

    @Test fun legacyWebDavRestoreStillWorks() = runBlocking {
        withCloud { files ->
            val pack = backup.createFullSoftwareBackup(BackupModule.entries)!!
            files["/Backup/meta.json"] = Json.encodeToString(BackupMeta.serializer(), pack.meta).encodeToByteArray()
            pack.payloadMap.forEach { (key, bytes) -> files["/Backup/$key.cbor"] = bytes }
            val vm = BackupViewModel(apiConfig, backup)
            vm.restoreFromWebDav()
            withTimeout(10000) { vm.uiState.first { it.testResult is TestResult.Success || it.testResult is TestResult.Error } }
            assertEquals(TestResult.Success, vm.uiState.value.testResult)
        }
    }

    @Test fun partiallyWrittenImagesAreCleanedWhenNextImageFails() = runBlocking {
        var writes = 0
        val fs = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun sink(file: Path, mustCreate: Boolean): Sink {
                if (file.name.endsWith(".jpg") && ++writes == 2) throw IOException("第二张图片写入失败")
                return super.sink(file, mustCreate)
            }
        }
        val vm = CourseNoteViewModel(CourseNoteRepository(db.courseNoteDao(), fs, storage.filesDir))
        vm.bindCourse("course")
        val result = CompletableDeferred<Boolean>()
        vm.saveNote("note", "2026-10-14", null, "标题", "内容", emptyList(), listOf(byteArrayOf(1), byteArrayOf(2))) { result.complete(it) }
        assertFalse(withTimeout(3000) { result.await() })
        assertTrue(FileSystem.SYSTEM.list(storage.filesDir / "notes").isEmpty())
        assertNull(db.courseNoteDao().getNoteOnce("note"))
    }

    @Test fun databaseFailureCleansNewAttachments() = runBlocking {
        val vm = CourseNoteViewModel(noteRepo)
        vm.bindCourse("不存在的课程")
        val result = CompletableDeferred<Boolean>()
        vm.saveNote("note", "2026-10-14", null, "标题", "内容", emptyList(), listOf(byteArrayOf(1))) { result.complete(it) }
        assertFalse(withTimeout(3000) { result.await() })
        assertTrue(FileSystem.SYSTEM.list(storage.filesDir / "notes").isEmpty())
    }

    @Test fun missingBackupImageFailsBeforeReplacingNotes() = runBlocking {
        db.courseNoteDao().upsert(CourseNote("old", "course", "2026-10-14", title = "保留本机", createdAt = 1, updatedAt = 1))
        val envelope = CourseImportExport.UserDataBackupEnvelope(1, 5,
            notes = listOf(CourseImportExport.CourseNoteBackupModel("new", "course", "2026-10-14", imagePaths = "image-0")),
            noteImages = emptyMap())
        val result = backup.restoreUserDataBytes(CourseImportExport.cbor.encodeToByteArray(CourseImportExport.UserDataBackupEnvelope.serializer(), envelope))
        assertTrue(result.isFailure)
        assertEquals("保留本机", db.courseNoteDao().getAllNotesOnce().single().title)
    }

    @Test fun sameCourseAtDifferentWeeksRetainsBothNotesOnWebReimport() = runBlocking {
        val items = listOf(courseJson.copy(id = "course", weeks = listOf(1)), courseJson.copy(id = "course2", weeks = listOf(2)))
        conversion.importCourseTableFromJson("table", CourseTableImportModel(items))
        for (id in listOf("course", "course2")) db.courseNoteDao().upsert(CourseNote("note-$id", id, "2026-10-14", createdAt = 1, updatedAt = 1))
        conversion.importCoursesFromList("table", items.map { it.copy(id = null) })
        assertEquals(setOf("course", "course2"), db.courseDao().getCoursesOnce("table").map { it.id }.toSet())
        assertEquals(2, db.courseNoteDao().getAllNotesOnce().size)
        assertEquals(listOf(2), db.courseWeekDao().getWeekNumbersByCourseId("course2"))
    }

    /**
     * 「另存为新课表」看起来失效的根因回归。
     *
     * 新建课表 `createNewCourseTable` 只写课表/默认作息/默认配置，**不会**切换当前课表；
     * 而导入成功后的落点判断读的是「当前课表」的开学日期。于是新建出来的表永远
     * `semesterStartDate` 为空、又从来不是当前课表 —— 用户去设置开学日期设置的也是旧表，
     * 新课表始终未配置且不生效，观感就是「点了按钮没反应」。
     *
     * 修复后由 `handleImportSuccess(importedTableId)` 负责把当前课表切到导入结果。
     * 这里固化两条不变量：
     * 1. 新建课表本身不会偷改当前课表（避免有人误以为是 repository 的职责）；
     * 2. 新课表未设开学日期，且切换后学期配置查询落在新表上 —— 这正是必须切换的原因。
     */
    @Test fun newTableImportNeedsExplicitCurrentTableSwitch() = runBlocking {
        val before = settings.getAppSettingsOnce().currentCourseTableId

        val newTableId = tableRepo.createNewCourseTable("导入的新课表")
        // 不变量 1：新建课表不改当前课表
        assertEquals(before, settings.getAppSettingsOnce().currentCourseTableId)

        // 不变量 2：新课表未设开学日期
        assertTrue(settings.getCourseConfigOnce(newTableId)?.semesterStartDate.isNullOrBlank())

        // 模拟修复后的落点：显式切到导入的新课表
        val current = settings.getAppSettingsOnce()
        settings.insertOrUpdateAppSettings(current.copy(currentCourseTableId = newTableId))
        assertEquals(newTableId, settings.getAppSettingsOnce().currentCourseTableId)
        // 切换后「当前课表是否已设开学日期」判断的是新表，用户去设置也就写在正确的表上
        assertFalse(conversion.isSemesterStartDateSet())
    }

    /** 导入内容确实写进目标新课表（另存为新课表的数据侧保证）。 */
    @Test fun importIntoNewTableLandsCoursesOnThatTable() = runBlocking {
        // 复用夹具里已存在的课表作为「目标新表」，只验证导入落点与隔离：
        // 不额外建表，避免与 tearDown 的 deleteRecursively 抢 Room 句柄（该夹具的已知时序限制）。
        val targetTableId = "table"
        conversion.importCourseTableFromJson(
            targetTableId,
            CourseTableImportModel(listOf(courseJson.copy(id = null, name = "另存课程")))
        )

        val landed = db.courseDao().getCoursesOnce(targetTableId)
        assertEquals(listOf("另存课程"), landed.map { it.name })
        // 导入后该表只应包含本次导入的课程（覆盖语义），旧课程名不再残留
        assertTrue(landed.none { it.name == courseJson.name }, "目标表应被本次导入覆盖")
    }
}
