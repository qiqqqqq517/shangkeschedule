import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.emptyPreferences
import com.shangkeschedule.data.db.main.Course
import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseWeek
import com.shangkeschedule.data.db.main.createMainDatabase
import com.shangkeschedule.data.di.AppStorage
import com.shangkeschedule.data.model.schedule_style.ScheduleGridStyleProto
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseConversionRepository
import com.shangkeschedule.data.repository.StyleSettingsRepository
import com.shangkeschedule.data.repository.TimeSlotRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class CalendarMemoryStore<T>(initial: T) : DataStore<T> {
    private val value = MutableStateFlow(initial)
    override val data: Flow<T> = value
    override suspend fun updateData(transform: suspend (T) -> T): T =
        transform(value.value).also { value.value = it }
}

/** 日历同步前置条件：真实 Room 与仓储，不启动无关的课表/备份仓储。 */
class CalendarSyncPrerequisitesTest {
    private val root = Files.createTempDirectory("calendar-sync-regression-").toString().toPath()
    private val storage = object : AppStorage {
        override val filesDir = root / "files"
        override val cacheDir = root / "cache"
        override fun getDatabasePath(dbName: String) = (root / dbName).toString()
    }
    private val db = createMainDatabase(storage)
    private val tables = db.courseTableDao()
    private val settings = AppSettingsRepository(
        CalendarMemoryStore(emptyPreferences()), CalendarMemoryStore(emptyPreferences()),
        tables, db.courseTableConfigDao()
    )
    private val style = StyleSettingsRepository(CalendarMemoryStore(ScheduleGridStyleProto()), settings)
    private val slots = TimeSlotRepository(db, db.timeSlotDao(), db.timeSlotSchemeDao(), db.courseTableConfigDao(), db.courseDao())
    private val conversion = CourseConversionRepository(db, db.courseDao(), db.courseWeekDao(), db.timeSlotDao(), settings, style, slots)

    @BeforeTest fun setUp() = runBlocking {
        tables.insert(CourseTable("table", "样本课表", 1))
        db.courseDao().insertAll(listOf(Course(
            id = "course", courseTableId = "table", name = "课程", teacher = "教师",
            position = "A101", day = 1, startSection = 3, endSection = 3,
            customStartTime = null, customEndTime = null, colorInt = 0
        )))
        db.courseWeekDao().insertAll(listOf(CourseWeek("course", 1)))
        // 预热协程最后一步写入缓存；等其完成后测试结束才能安全关闭 Room。
        withTimeout(5_000) { while (style.getCachedStyle() == null) delay(10) }
        assertEquals("table", settings.getAppSettingsOnce().currentCourseTableId)
        assertTrue(db.courseDao().getCoursesWithWeeksByTableId("table").first().isNotEmpty())
    }

    @AfterTest fun tearDown() {
        db.close()
        FileSystem.SYSTEM.deleteRecursively(root)
    }

    @Test fun noTableReportsFailure() = runBlocking {
        tables.deleteById("table")
        assertEquals("", settings.getAppSettingsOnce().currentCourseTableId)
        assertFalse(conversion.syncCurrentTableToSystemCalendar())
    }

    @Test fun invalidSemesterStartReportsFailure() = runBlocking {
        db.courseTableConfigDao().insertOrUpdate(CourseTableConfig("table", semesterStartDate = "invalid"))
        assertFalse(conversion.syncCurrentTableToSystemCalendar())
    }

    @Test fun emptyCourseTableReportsFailure() = runBlocking {
        db.courseTableConfigDao().insertOrUpdate(CourseTableConfig("table", semesterStartDate = "2026-09-01"))
        db.courseDao().deleteCoursesByTableId("table")
        assertTrue(db.courseDao().getCoursesWithWeeksByTableId("table").first().isEmpty())
        assertFalse(conversion.syncCurrentTableToSystemCalendar())
    }

    @Test fun invalidWeekCountReportsFailure() = runBlocking {
        db.courseTableConfigDao().insertOrUpdate(CourseTableConfig("table", semesterStartDate = "2026-09-01", semesterTotalWeeks = 0))
        assertFalse(conversion.syncCurrentTableToSystemCalendar())
    }
}
