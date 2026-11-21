package com.shangkeschedule.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.*
import com.shangkeschedule.data.di.AppStorage
import com.shangkeschedule.data.model.*
import com.shangkeschedule.data.model.schedule_style.ScheduleGridStyleProto
import com.shangkeschedule.data.repository.*
import com.shangkeschedule.ui.settings.SettingsViewModel
import com.shangkeschedule.ui.settings.import.TextImportViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.*
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.*
import kotlin.time.Clock

private class WiringMemoryStore<T>(initial: T) : DataStore<T> {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()
    var delayWrites = false
    override val data: Flow<T> = state
    override suspend fun updateData(transform: suspend (T) -> T): T = mutex.withLock {
        if (delayWrites) delay(60)
        transform(state.value).also { state.value = it }
    }
}

/** 真实 Room / ViewModel 回归：验证跨日期作息、并发设置和解析结果归属。 */
class WiringRegressionTest {
    private val root = Files.createTempDirectory("wiring-regression-").toString().toPath()
    private val storage = object : AppStorage {
        override val filesDir = root / "files"
        override val cacheDir = root / "cache"
        override fun getDatabasePath(dbName: String) = (root / dbName).toString()
    }
    private val db = createMainDatabase(storage)
    private val memory = WiringMemoryStore(emptyPreferences())
    private val settings = AppSettingsRepository(memory, WiringMemoryStore(emptyPreferences()), db.courseTableDao(), db.courseTableConfigDao())
    private val style = StyleSettingsRepository(WiringMemoryStore(ScheduleGridStyleProto()), settings)
    private val slots = TimeSlotRepository(db, db.timeSlotDao(), db.timeSlotSchemeDao(), db.courseTableConfigDao(), db.courseDao())
    private val conversion = CourseConversionRepository(db, db.courseDao(), db.courseWeekDao(), db.timeSlotDao(), settings, style, slots)
    private val tables = CourseTableRepository(db, db.courseTableDao(), db.courseDao(), db.courseWeekDao(), slots, settings)
    private val viewModels = ViewModelStore()
    private val jobs = mutableListOf<Job>()
    private fun <T : ViewModel> keep(vm: T): T = vm.also {
        viewModels.put(jobs.size.toString(), vm)
        jobs += vm.viewModelScope.coroutineContext[Job]!!
    }

    @BeforeTest fun setup() = runBlocking {
        tables.getAllCourseTables().first { it.isNotEmpty() }
        db.courseTableDao().insert(CourseTable("sample", "回归课表", 1))
        settings.insertOrUpdateAppSettings(AppSettingsModel(currentCourseTableId = "sample"))
    }

    @AfterTest fun cleanup() = runBlocking {
        viewModels.clear()
        jobs.forEach { it.join() }
        tables.close()
        style.close()
        settings.close()
        db.close()
        FileSystem.SYSTEM.deleteRecursively(root)
    }

    @Test fun concurrentAppearanceChangesKeepBothFields() = runBlocking {
        val vm = keep(SettingsViewModel(settings, style, FileSystem.SYSTEM, storage.filesDir))
        withTimeout(5000) { vm.uiState.first { it.isReady } }
        memory.delayWrites = true
        withContext(Dispatchers.Main) {
            vm.onThemeModeChanged(AppThemeMode.DARK)
            vm.onStartScreenChanged(StartScreen.TODAY_SCHEDULE)
        }
        withTimeout(5000) {
            settings.getAppSettings().first { it.startScreen == StartScreen.TODAY_SCHEDULE }
        }
        assertEquals(AppThemeMode.DARK, settings.getAppSettingsOnce().themeMode,
            "启动页修改不得回退刚写入的主题")
    }

    @Test fun changedInputCannotReceivePreviousParseResult() = runBlocking {
        val vm = keep(TextImportViewModel(conversion, tables, storage))
        withContext(Dispatchers.Main) {
            vm.updateInputText("课程,教师,教室,星期,节次,周次\n课程A,甲,A101,1,1-2,1-16")
            vm.parseInput()
            vm.updateInputText("用户刚输入的新文本B")
        }
        withTimeout(5000) { vm.uiState.first { !it.isLoading } }
        // 让已调度的旧任务有机会完成；旧代码会在此期间回填 A。
        delay(300)
        assertEquals("用户刚输入的新文本B", vm.uiState.value.inputText)
        assertNull(vm.uiState.value.parseResult, "旧输入的结果不能关联到新输入")
        assertNull(vm.uiState.value.error)
    }

    @Test fun wholeSemesterIcsSelectsSchemeForEachCourseDate() = runBlocking {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        db.courseTableConfigDao().insertOrUpdate(CourseTableConfig("sample",
            semesterStartDate = today.toString(), semesterTotalWeeks = 3, autoSwitchScheme = true))
        db.timeSlotDao().insertAll(listOf(
            TimeSlot(1, "08:00", "08:45", "sample"),
            TimeSlot(1, "09:00", "09:45", "sample", schemeId = "special")
        ))
        val monthDay = "%02d-%02d".format(tomorrow.monthNumber, tomorrow.dayOfMonth)
        db.timeSlotSchemeDao().insertOrUpdate(TimeSlotScheme("sample", "special", monthDay, monthDay))
        db.courseDao().insertAll(listOf(Course("lesson", "sample", "测试课", "教师", "A101",
            tomorrow.dayOfWeek.isoDayNumber, 1, 1, false, null, null, 0)))
        db.courseWeekDao().insertAll((1..3).map { CourseWeek("lesson", it) })
        val ics = assertNotNull(conversion.exportToIcsString("sample", 15))
        val dayText = tomorrow.toString().replace("-", "")
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:${dayText}T090000"),
            "切换日必须按该日作息导出，不能沿用今天的 08:00")
        assertTrue(ics.contains("T080000"), "其余周仍沿用默认方案")
    }

    @Test fun renamingInactiveTableEmitsBackupChangeEvenWhenRowCountIsUnchanged() = runBlocking {
        db.courseTableDao().insert(CourseTable("inactive", "旧名称", 2))
        val subscribed = CompletableDeferred<Unit>()
        val changed = async {
            var initial = true
            db.courseTableDao().observeBackupChanges().first {
                if (initial) { initial = false; subscribed.complete(Unit); false } else true
            }
        }
        subscribed.await()
        db.courseTableDao().update(CourseTable("inactive", "新名称", 2))
        withTimeout(3000) { changed.await() }
        assertEquals("新名称", db.courseTableDao().getCourseTableById("inactive")?.name)
    }

    @Test fun changingInactiveSchemeEmitsBackupChange() = runBlocking {
        db.courseTableDao().insert(CourseTable("inactive", "非当前课表", 2))
        val subscribed = CompletableDeferred<Unit>()
        val changed = async {
            var initial = true
            db.courseTableDao().observeBackupChanges().first {
                if (initial) { initial = false; subscribed.complete(Unit); false } else true
            }
        }
        subscribed.await()
        db.timeSlotDao().insertAll(listOf(TimeSlot(1, "10:00", "10:45", "inactive")))
        withTimeout(3000) { changed.await() }
        Unit
    }
}
