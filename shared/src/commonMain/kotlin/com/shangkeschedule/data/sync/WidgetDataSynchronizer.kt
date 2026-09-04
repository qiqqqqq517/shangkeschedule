package com.shangkeschedule.data.sync

import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.db.widget.WidgetAppSettings
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import com.shangkeschedule.data.repository.TimeSlotRepository
import com.shangkeschedule.data.repository.WidgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

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
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val syncFlow: Flow<Unit> = appSettingsRepository.getAppSettings()
        .flatMapLatest { appSettings ->
            val tableId = appSettings.currentCourseTableId

            if (tableId.isNotEmpty()) {
                val coursesFlow = courseTableRepository.getCoursesWithWeeksByTableId(tableId)
                val configFlow = appSettingsRepository.getCourseTableConfigFlow(tableId)

                // 联合监听当前课表的所有相关数据表（时间段按当前日期自动解析作息方案读取）
                configFlow.flatMapLatest { config ->
                    val timeSlotsFlow = timeSlotRepository.getActiveTimeSlotsByConfigFlow(tableId, flowOf(config))

                    combine(coursesFlow, timeSlotsFlow, flowOf(config)) { courses, timeSlots, config ->
                        Quadruple(appSettings, courses, timeSlots, config)
                    }
                }
            } else {
                flowOf(Quadruple(appSettings, emptyList(), emptyList(), null))
            }
        }
        .map { (appSettings, coursesWithWeeks, timeSlots, config) ->
            if (config != null) {
                performSync(appSettings, config, coursesWithWeeks, timeSlots)
            } else {
                syncMutex.withLock {
                    widgetRepository.replaceSnapshot(
                        courses = emptyList(),
                        settings = WidgetAppSettings(id = 1, semesterStartDate = null)
                    )
                }
            }
        }

    init {
        // 自动触发启动
        startSync()
    }

    /**
     * 启动自动同步监听（跨平台调用入口）。
     * 会对数据库流的变化进行防抖处理，并在每次同步完成后发出通知。
     */
    @OptIn(FlowPreview::class)
    fun startSync() {
        // 确保防重：若已经启动过则直接返回
        if (isStarted.value) return
        isStarted.value = true

        // 1. 监听课表数据与小组件所需数据的实时变更
        syncFlow
            .debounce(500.milliseconds)
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
            performSyncUnlocked(appSettings, courseConfig, coursesWithWeeks, timeSlots)
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
     */
    private suspend fun performSync(
        appSettings: AppSettingsModel,
        courseConfig: CourseTableConfig,
        coursesWithWeeks: List<CourseWithWeeks>,
        timeSlots: List<TimeSlot>
    ) = syncMutex.withLock {
        performSyncUnlocked(appSettings, courseConfig, coursesWithWeeks, timeSlots)
    }

    private suspend fun performSyncUnlocked(
        appSettings: AppSettingsModel,
        courseConfig: CourseTableConfig,
        coursesWithWeeks: List<CourseWithWeeks>,
        timeSlots: List<TimeSlot>
    ) = withContext(Dispatchers.IO) {
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
            widgetRepository.replaceSnapshot(
                courses = emptyList(),
                settings = WidgetAppSettings(id = 1, semesterStartDate = null)
            )
            return@withContext
        }

        val widgetSettings = WidgetAppSettings(
            id = 1,
            semesterStartDate = semesterStartDateString,
            semesterTotalWeeks = semesterTotalWeeks,
            firstDayOfWeek = firstDayOfWeekInt
        )

        val skippedDates = appSettings.skippedDates
        val timeSlotMap = timeSlots.associateBy { it.number }
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val alignedSemesterStartDate = getStartDayOfWeek(semesterStartDate, firstDayOfWeekInt)

        val widgetCourses = mutableListOf<WidgetCourse>()
        val startSyncDate = if (today < alignedSemesterStartDate) alignedSemesterStartDate else today

        for (i in 0 until widgetSyncDays) {
            val date = startSyncDate.plus(i, DateTimeUnit.DAY)
            val dateString = date.toString()
            val alignedDate = getStartDayOfWeek(date, firstDayOfWeekInt)
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

        widgetRepository.replaceSnapshot(widgetCourses, widgetSettings)
    }

    /**
     * 根据设定的每周起始日（如周一或周日），向前推算并对齐给定日期所在周的起始日。
     */
    private fun getStartDayOfWeek(date: LocalDate, firstDayOfWeekInt: Int): LocalDate {
        val targetFirstDay = DayOfWeek(firstDayOfWeekInt.coerceIn(1, 7))
        var current = date
        while (current.dayOfWeek != targetFirstDay) {
            current = current.minus(1, DateTimeUnit.DAY)
        }
        return current
    }
}