package com.shangkeschedule.data.sync

import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.widget.WidgetAppSettings
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import com.shangkeschedule.data.repository.TimeSlotRepository
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.data.time.startOfWeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 负责主数据库与 Widget 数据库之间的数据同步（跨平台共享核心逻辑）。
 * 持续监听应用设置、课表及时间段的变化，自动计算并写入优化后的 Widget 专用数据库。
 */
@Single(createdAtStart = true)
class WidgetDataSynchronizer(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val widgetRepository: WidgetRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()
    private val widgetSyncDays = 7 // 每次同步未来 7 天的数据
    private val isStarted = MutableStateFlow(false)

    // 内部通道：用于向各平台分发“数据同步完成”的通知信号
    private val _syncCompletedChannel = Channel<Unit>(Channel.CONFLATED)

    /** 暴露给各平台（Android / iOS）监听的同步完成事件流 */
    val syncCompletedFlow: Flow<Unit> = _syncCompletedChannel.receiveAsFlow()

    /**
     * 持续监听主数据库变化的 Flow 核心链条。
     * 当当前课表 ID 改变时，会自动切换监听对应的课程、时间段与配置数据。
     *
     * DUC 收窄（v3.54.0）：同步链只消费「当前课表 ID + 免打扰日期」两个字段，
     * 其余任意设置项写入不再重启链条；快照内容与现库一致时跳过重写并抑制完成广播，
     * 截断「拨动无关开关 → widget 全量重写 + 101 闹钟重排 + 4 组件重绘」的放大链。
     * 发射值为「本次是否实际写库」，供 startSync 过滤无变化的完成信号。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val syncFlow: Flow<Boolean> = appSettingsRepository.getAppSettings()
        .map { SyncSource(it.currentCourseTableId, it.skippedDates) }
        .distinctUntilChanged()
        .flatMapLatest { source ->
            if (source.tableId.isNotEmpty()) {
                val coursesFlow = courseTableRepository.getCoursesWithWeeksByTableId(source.tableId)
                val configFlow = appSettingsRepository.getCourseTableConfigFlow(source.tableId)

                // 联合监听当前课表的所有相关数据表（时间段按当前日期自动解析作息方案读取）
                configFlow.flatMapLatest { config ->
                    val timeSlotsFlow = timeSlotRepository.getActiveTimeSlotsByConfigFlow(source.tableId, flowOf(config))

                    combine(coursesFlow, timeSlotsFlow) { courses, timeSlots ->
                        SyncFrame(source, config, courses, timeSlots)
                    }
                }
            } else {
                flowOf(SyncFrame(source, null, emptyList(), emptyList()))
            }
        }
        .map { frame ->
            if (frame.config != null) {
                performSync(frame.source.skippedDates, frame.config, frame.coursesWithWeeks, frame.timeSlots)
            } else {
                syncMutex.withLock {
                    widgetRepository.replaceSnapshotIfChanged(
                        courses = emptyList(),
                        settings = WidgetAppSettings(id = 1, semesterStartDate = null)
                    )
                }
            }
        }

    init {
        // 冷启动让路（v3.54.0）：初始同步需打开双库并重算 7 天课程，推迟 3s 执行，
        // 避免与首屏 DataStore/Room 读取抢占 IO；平台如需提前启动仍可显式调 startSync()（幂等）。
        scope.launch {
            delay(3.seconds)
            startSync()
        }
    }

    /**
     * 启动自动同步监听（跨平台调用入口）。
     * 会对数据库流的变化进行防抖处理，并在每次同步完成后发出通知。
     */
    @OptIn(FlowPreview::class)
    fun startSync() {
        // 确保防重：CAS 原子守卫（复审 P3），并发调用只放行首个
        if (!isStarted.compareAndSet(false, true)) return

        // 1. 监听课表数据与小组件所需数据的实时变更（快照无变化时不广播，v3.54.0）
        syncFlow
            .debounce(500.milliseconds)
            .filter { it }
            .onEach {
                _syncCompletedChannel.trySend(Unit)
            }
            .launchIn(scope)

        // 2. 监听通知/自动化配置变更，同样触发同步通知（以便各平台调度 WorkManager/系统闹钟/DND 任务）
        appSettingsRepository.getAppSettings()
            .map { settings ->
                Quadruple(
                    settings.reminderEnabled to settings.remindBeforeMinutes,
                    settings.autoModeEnabled to settings.autoControlMode,
                    settings.compatWearableSync,
                    settings.dynamicIslandEnabled
                )
            }
            .distinctUntilChanged()
            .onEach {
                _syncCompletedChannel.trySend(Unit)
            }
            .launchIn(scope)
    }

    /** 同步源键：只含同步真正消费的设置字段（DUC 紧凑源，v3.54.0）。 */
    private data class SyncSource(val tableId: String, val skippedDates: Set<String>)

    /** 一次同步帧：源键 + 当前课表配置 / 课程 / 时段。 */
    private data class SyncFrame(
        val source: SyncSource,
        val config: CourseTableConfig?,
        val coursesWithWeeks: List<CourseWithWeeks>,
        val timeSlots: List<TimeSlot>
    )

    /** 四元组辅助数据类，用于 combine 操作符传递多路数据 */
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    /**
     * 手动触发一次性数据同步（挂起函数）。
     */
    suspend fun syncNow() = syncMutex.withLock {
        val appSettings = appSettingsRepository.getAppSettings().first()
        val tableId = appSettings.currentCourseTableId

        val coursesWithWeeks = if (tableId.isNotEmpty()) courseTableRepository.getCoursesWithWeeksByTableId(tableId).first() else emptyList()
        val courseConfig = if (tableId.isNotEmpty()) appSettingsRepository.getCourseConfigOnce(tableId) else null
        val timeSlots = if (tableId.isNotEmpty()) timeSlotRepository.getActiveTimeSlotsOnce(tableId, courseConfig) else emptyList()

        if (courseConfig != null) {
            performSyncUnlocked(appSettings.skippedDates, courseConfig, coursesWithWeeks, timeSlots)
        } else {
            widgetRepository.replaceSnapshot(
                courses = emptyList(),
                settings = WidgetAppSettings(id = 1, semesterStartDate = null)
            )
        }
        // 手动同步完成后主动发出通知
        _syncCompletedChannel.trySend(Unit)
    }

    /**
     * 核心计算与写库逻辑：解析开学日期、计算周次、匹配课程时间并写入 Widget 数据库。
     * 返回快照是否实际发生变化（v3.54.0：无变化跳过重写）。
     */
    private suspend fun performSync(
        skippedDates: Set<String>,
        courseConfig: CourseTableConfig,
        coursesWithWeeks: List<CourseWithWeeks>,
        timeSlots: List<TimeSlot>
    ): Boolean = syncMutex.withLock {
        performSyncUnlocked(skippedDates, courseConfig, coursesWithWeeks, timeSlots)
    }

    private suspend fun performSyncUnlocked(
        skippedDates: Set<String>,
        courseConfig: CourseTableConfig,
        coursesWithWeeks: List<CourseWithWeeks>,
        timeSlots: List<TimeSlot>
    ): Boolean = withContext(Dispatchers.IO) {
        val semesterStartDateString = courseConfig.semesterStartDate
        val semesterTotalWeeks = courseConfig.semesterTotalWeeks
        val firstDayOfWeekInt = courseConfig.firstDayOfWeek

        val semesterStartDate: LocalDate? = semesterStartDateString?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val isValidConfig = semesterStartDate != null &&
            semesterTotalWeeks > 0 &&
            firstDayOfWeekInt in 1..7

        if (!isValidConfig) {
            return@withContext widgetRepository.replaceSnapshotIfChanged(
                courses = emptyList(),
                settings = WidgetAppSettings(id = 1, semesterStartDate = null)
            )
        }

        val widgetSettings = WidgetAppSettings(
            id = 1,
            semesterStartDate = semesterStartDateString,
            semesterTotalWeeks = semesterTotalWeeks,
            firstDayOfWeek = firstDayOfWeekInt
        )

        val timeSlotMap = timeSlots.associateBy { it.number }
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val alignedSemesterStartDate = startOfWeek(semesterStartDate, firstDayOfWeekInt)

        val widgetCourses = mutableListOf<WidgetCourse>()
        val startSyncDate = if (today < alignedSemesterStartDate) alignedSemesterStartDate else today

        for (i in 0 until widgetSyncDays) {
            val date = startSyncDate.plus(i, DateTimeUnit.DAY)
            val dateString = date.toString()
            val alignedDate = startOfWeek(date, firstDayOfWeekInt)
            val diffDays = alignedSemesterStartDate.daysUntil(alignedDate)
            val weekNumber = diffDays / 7 + 1
            val dayOfWeek = date.dayOfWeek.isoDayNumber

            if (weekNumber !in 1..semesterTotalWeeks) continue

            for (courseWithWeeks in coursesWithWeeks) {
                if (courseWithWeeks.weeks.any { it.weekNumber == weekNumber } && courseWithWeeks.course.day == dayOfWeek) {
                    val course = courseWithWeeks.course
                    val startTime: String
                    val endTime: String
                    if (course.isCustomTime) {
                        startTime = course.customStartTime ?: ""
                        endTime = course.customEndTime ?: ""
                    } else {
                        startTime = timeSlotMap[course.startSection]?.startTime ?: ""
                        endTime = timeSlotMap[course.endSection]?.endTime ?: ""
                    }
                    widgetCourses.add(
                        WidgetCourse(
                            id = "${course.id}-$dateString",
                            name = course.name,
                            teacher = course.teacher,
                            position = course.position,
                            startTime = startTime,
                            endTime = endTime,
                            isSkipped = skippedDates.contains(dateString),
                            date = dateString,
                            colorInt = course.colorInt
                        )
                    )
                }
            }
        }

        widgetRepository.replaceSnapshotIfChanged(widgetCourses, widgetSettings)
    }

}
