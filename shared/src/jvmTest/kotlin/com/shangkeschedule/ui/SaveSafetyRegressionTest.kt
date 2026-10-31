package com.shangkeschedule.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.*
import com.shangkeschedule.data.di.AppStorage
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.schedule_style.ScheduleGridStyleProto
import com.shangkeschedule.data.repository.*
import com.shangkeschedule.ui.note.CourseNoteViewModel
import com.shangkeschedule.ui.settings.course.AddEditCourseViewModel
import com.shangkeschedule.ui.settings.course.UiEvent
import com.shangkeschedule.ui.settings.time.TimeSlotViewModel
import com.shangkeschedule.navigation.AddEditCourseChannel
import com.shangkeschedule.navigation.PresetCourseData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.IOException
import java.nio.file.Files
import kotlin.test.*

private class SaveMemoryStore<T>(initial: T) : DataStore<T> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<T> = state
    override suspend fun updateData(transform: suspend (T) -> T): T = transform(state.value).also { state.value = it }
}

/** 真实 Room 与 ViewModel 回归，验证身份回填、在途互斥及失败回滚后的用户数据。 */
class SaveSafetyRegressionTest {
    private val root = Files.createTempDirectory("save-safety-").toString().toPath()
    private val storage = object : AppStorage {
        override val filesDir = root / "files"
        override val cacheDir = root / "cache"
        override fun getDatabasePath(dbName: String) = (root / dbName).toString()
    }
    private val db = createMainDatabase(storage)
    private val settings = AppSettingsRepository(SaveMemoryStore(emptyPreferences()), SaveMemoryStore(emptyPreferences()), db.courseTableDao(), db.courseTableConfigDao())
    private val style = StyleSettingsRepository(SaveMemoryStore(ScheduleGridStyleProto()), settings)
    private val slots = TimeSlotRepository(db, db.timeSlotDao(), db.timeSlotSchemeDao(), db.courseTableConfigDao(), db.courseDao())
    private val store = ViewModelStore()
    private var vmCount = 0
    private val jobs = mutableListOf<Job>()
    private val repositories = mutableListOf<CourseTableRepository>()
    private fun <T : ViewModel> keep(vm: T): T = vm.also {
        jobs += it.viewModelScope.coroutineContext[Job]!!
        store.put("vm${vmCount++}", it)
    }
    private fun tables(dao: CourseDao = db.courseDao()) = CourseTableRepository(db, db.courseTableDao(), dao, db.courseWeekDao(), slots, settings).also { repositories += it }
    private fun course(id: String, position: String = "A101") = Course(id, "table", "课程", "教师", position, 1, 1, 2, false, null, null, 0)

    @BeforeTest fun setup() = runBlocking {
        db.courseTableDao().insert(CourseTable("table", "样本课表", 1))
        settings.insertOrUpdateAppSettings(AppSettingsModel(currentCourseTableId = "table"))
        db.courseTableConfigDao().insertOrUpdate(CourseTableConfig("table", currentSchemeId = "summer"))
        db.timeSlotDao().insertAll(listOf(TimeSlot(1, "08:00", "08:45", "table", schemeId = "summer")))
    }
    @AfterTest fun cleanup() = runBlocking {
        store.clear()
        jobs.forEach { it.join() }
        repositories.forEach { it.close() }
        style.close()
        settings.close()
        db.close()
        // Windows 上 SQLite 关闭后文件句柄可能稍晚释放；有限重试，超时仍报错。
        var failure: IOException? = null
        repeat(20) {
            try {
                FileSystem.SYSTEM.deleteRecursively(root)
                return@runBlocking
            } catch (e: IOException) {
                failure = e
                delay(100)
            }
        }
        throw failure ?: IOException("无法清理测试临时目录")
    }

    @Test fun savingCustomSchemeDoesNotReportIdentityFieldsAsUnsaved() = runBlocking {
        val vm = keep(TimeSlotViewModel(slots, settings, tables()))
        withTimeout(3000) { vm.timeSlotsUiState.first { it.isDataLoaded && it.currentSchemeId == "summer" } }
        // 模拟旧版内层弹窗留下 default 标识的草稿，保存必须按当前页面方案落库。
        val draft = listOf(TimeSlot(1, "09:00", "09:45", ""))
        assertTrue(vm.hasUnsavedChanges(draft, 45, 10))
        val result = CompletableDeferred<Unit>()
        vm.onSaveAllSettings(draft, 45, 10, onSuccess = { result.complete(Unit) })
        withTimeout(3000) { result.await() }
        assertEquals("09:00", db.timeSlotDao().getTimeSlotsOnce("table", "summer").single().startTime)
        assertFalse(vm.hasUnsavedChanges(draft, 45, 10), "已保存草稿不能因为方案标识差异再次提示未保存")
        assertTrue(vm.hasUnsavedChanges(draft.map { it.copy(alias = "新修改") }, 45, 10))
    }

    @Test fun courseSaveAssignsPersistentIdsAndUpdatesBaseline() = runBlocking {
        val vm = keep(AddEditCourseViewModel(tables(), slots, settings, style))
        AddEditCourseChannel.sendEvent(PresetCourseData())
        vm.initWithId(null)
        withTimeout(3000) { vm.uiState.first { it.isDataLoaded } }
        vm.onNameChange("新课程")
        vm.onSave()
        withTimeout(3000) { vm.uiEvent.first { it == UiEvent.SaveSuccess } }
        assertFalse(vm.hasUnsavedChanges(), "保存成功后须以刚提交的快照建立新基线")
        assertTrue(vm.uiState.value.schemes.all { it.dbId != null }, "新方案必须回填持久化身份")
        val firstIds = db.courseDao().getCoursesOnce("table").map { it.id }
        vm.onNameChange("新课程再次编辑")
        vm.onSave()
        withTimeout(3000) { vm.uiEvent.first { it == UiEvent.SaveSuccess } }
        assertEquals(firstIds, db.courseDao().getCoursesOnce("table").map { it.id }, "再次保存必须更新原记录")
    }

    @Test fun failureInLaterSchemeRollsBackDeletedCoursesUpdatesAndNotes() = runBlocking {
        db.courseDao().insertAll(listOf(course("one"), course("two")))
        db.courseNoteDao().upsert(CourseNote("note", "two", "2026-10-23", title = "保留笔记", createdAt = 1, updatedAt = 1))
        val reached = CompletableDeferred<Unit>()
        val failingDao = object : CourseDao by db.courseDao() {
            override suspend fun insertAll(courses: List<Course>) {
                if (courses.any { it.position == "FAIL" }) { reached.complete(Unit); throw IOException("合成写入故障") }
                db.courseDao().insertAll(courses)
            }
        }
        val vm = keep(AddEditCourseViewModel(tables(failingDao), slots, settings, style))
        vm.initWithId("one")
        withTimeout(3000) { vm.uiState.first { it.isDataLoaded } }
        vm.removeScheme("two")
        vm.updateScheme("one") { it.copy(position = "已修改") }
        vm.addScheme()
        val newId = vm.uiState.value.schemes.last().id
        vm.updateScheme(newId) { it.copy(position = "FAIL") }
        vm.onSave()
        withTimeout(3000) { reached.await() }
        // 等待事务结束后读库；旧实现会留下已删除的 two 和已修改的 one。
        withContext(Dispatchers.Main) { }
        val persisted = db.courseDao().getCoursesOnce("table")
        assertEquals(setOf("one", "two"), persisted.map { it.id }.toSet())
        assertEquals("A101", persisted.first { it.id == "one" }.position)
        assertNotNull(db.courseNoteDao().getNoteOnce("note"))
        assertTrue(vm.hasUnsavedChanges())
        assertTrue(withTimeout(3000) { vm.uiEvent.first() } is UiEvent.SaveError)
        assertFalse(vm.uiState.value.isSaving)
        vm.updateScheme(newId) { it.copy(position = "修正后重试") }
        vm.onSave()
        assertEquals(UiEvent.SaveSuccess, withTimeout(3000) { vm.uiEvent.first() })
        assertEquals(2, db.courseDao().getCoursesOnce("table").size)
    }

    @Test fun duplicateNoteSaveIsRejectedWhileFirstWriteIsInFlight() = runBlocking {
        db.courseDao().insertAll(listOf(course("one")))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blockingDao = object : CourseNoteDao by db.courseNoteDao() {
            override suspend fun upsert(note: CourseNote) {
                entered.complete(Unit)
                release.await()
                db.courseNoteDao().upsert(note)
            }
        }
        val vm = keep(CourseNoteViewModel(CourseNoteRepository(blockingDao, FileSystem.SYSTEM, storage.filesDir)))
        vm.bindCourse("one")
        val first = CompletableDeferred<Boolean>()
        val second = CompletableDeferred<Boolean>()
        vm.saveNote(null, "2026-10-23", null, "标题", "内容", emptyList(), emptyList()) { first.complete(it) }
        withTimeout(3000) { entered.await() }
        vm.saveNote(null, "2026-10-23", null, "标题", "内容", emptyList(), emptyList()) { second.complete(it) }
        release.complete(Unit)
        assertTrue(withTimeout(3000) { first.await() })
        assertFalse(withTimeout(3000) { second.await() }, "同一编辑器不能排队执行第二次新建保存")
        assertEquals(1, db.courseNoteDao().getAllNotesOnce().size)
    }

    @Test fun courseDoubleSaveAndNewerEditsKeepOnePersistentCourse() = runBlocking {
        db.courseDao().insertAll(listOf(course("one")))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blockingDao = object : CourseDao by db.courseDao() {
            override suspend fun update(course: Course) {
                entered.complete(Unit)
                release.await()
                db.courseDao().update(course)
            }
        }
        val vm = keep(AddEditCourseViewModel(tables(blockingDao), slots, settings, style))
        vm.initWithId("one")
        withTimeout(3000) { vm.uiState.first { it.isDataLoaded } }
        vm.onNameChange("已提交")
        vm.onSave()
        withTimeout(3000) { entered.await() }
        assertTrue(vm.uiState.value.isSaving)
        vm.onSave()
        vm.onNameChange("保存期间的新输入")
        release.complete(Unit)
        assertEquals(UiEvent.SavedWithChanges, withTimeout(3000) { vm.uiEvent.first() })
        assertEquals("已提交", db.courseDao().getCoursesOnce("table").single().name)
        assertEquals("保存期间的新输入", vm.uiState.value.name)
        assertTrue(vm.hasUnsavedChanges())
        assertFalse(vm.uiState.value.isSaving)
        vm.onSave()
        assertEquals(UiEvent.SaveSuccess, withTimeout(3000) { vm.uiEvent.first() })
        assertEquals("保存期间的新输入", db.courseDao().getCoursesOnce("table").single().name)
    }

    @Test fun failedTimeSlotSaveKeepsDraftDirtyAndDoesNotRunContinuation() = runBlocking {
        db.courseDao().insertAll(listOf(course("one")))
        val vm = keep(TimeSlotViewModel(slots, settings, tables()))
        withTimeout(3000) { vm.timeSlotsUiState.first { it.isDataLoaded } }
        val draft = listOf(TimeSlot(2, "09:00", "09:45", "", schemeId = "summer"))
        val error = CompletableDeferred<String>()
        var continued = false
        vm.onSaveAllSettings(draft, 50, 10, onSuccess = { continued = true }, onError = { error.complete(it) })
        assertTrue(withTimeout(3000) { error.await() }.isNotBlank())
        withContext(Dispatchers.Main) { }
        assertFalse(continued)
        assertFalse(vm.isSaving.value)
        assertEquals(1, db.timeSlotDao().getTimeSlotsOnce("table", "summer").single().number)
        assertTrue(vm.hasUnsavedChanges(draft, 50, 10))
    }

    @Test fun failedNoteSaveReleasesBusyStateAndAllowsRetry() = runBlocking {
        db.courseDao().insertAll(listOf(course("one")))
        var fail = true
        val failingDao = object : CourseNoteDao by db.courseNoteDao() {
            override suspend fun upsert(note: CourseNote) {
                if (fail) throw IOException("合成写入故障")
                db.courseNoteDao().upsert(note)
            }
        }
        val vm = keep(CourseNoteViewModel(CourseNoteRepository(failingDao, FileSystem.SYSTEM, storage.filesDir)))
        vm.bindCourse("one")
        val failed = CompletableDeferred<Boolean>()
        vm.saveNote(null, "2026-10-23", null, "重试", "原草稿", emptyList(), emptyList()) { failed.complete(it) }
        assertFalse(withTimeout(3000) { failed.await() })
        assertFalse(vm.isSaving.value)
        fail = false
        val retried = CompletableDeferred<Boolean>()
        vm.saveNote(null, "2026-10-23", null, "重试", "原草稿", emptyList(), emptyList()) { retried.complete(it) }
        assertTrue(withTimeout(3000) { retried.await() })
        assertEquals("原草稿", db.courseNoteDao().getAllNotesOnce().single().content)
    }

    @Test fun failedSchemeDateSaveDoesNotCloseEditorOrRunContinuation() = runBlocking {
        val failingMetaDao = object : TimeSlotSchemeDao by db.timeSlotSchemeDao() {
            override suspend fun insertOrUpdate(scheme: TimeSlotScheme) { throw IOException("合成元信息写入故障") }
        }
        val failingSlots = TimeSlotRepository(db, db.timeSlotDao(), failingMetaDao, db.courseTableConfigDao(), db.courseDao())
        val vm = keep(TimeSlotViewModel(failingSlots, settings, tables()))
        var continued = false
        val error = CompletableDeferred<String>()
        vm.onSaveSchemeDates("summer", "05-01", "10-01", onSuccess = { continued = true }, onError = { error.complete(it) })
        assertTrue(withTimeout(3000) { error.await() }.isNotBlank())
        withContext(Dispatchers.Main) { }
        assertFalse(continued)
        assertFalse(vm.isSaving.value)
        assertTrue(db.timeSlotSchemeDao().getSchemesOnce("table").isEmpty())
    }
}
