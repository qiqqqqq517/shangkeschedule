package com.shangkeschedule.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.db.main.CourseTableConfig
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.TimeSlot
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.data.model.schedule_style.ScheduleModeProto
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import com.shangkeschedule.data.repository.StyleSettingsRepository
import com.shangkeschedule.data.repository.TimeSlotRepository
import com.shangkeschedule.data.time.currentDateFlow
import com.shangkeschedule.ui.schedule.components.ScheduleGridStyleComposed
import com.shangkeschedule.ui.schedule.components.ScheduleGridStyleComposed.Companion.toComposedStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 课表展示块：封装单次或冲突课程
 * startSection/endSection：逻辑节次偏移量（0.0 代表网格最顶端：第一节课顶部 / 或者是24小时模式下的 00:00）
 */
data class MergedCourseBlock(
    val day: Int,
    val startSection: Float,
    val endSection: Float,
    val courses: List<CourseWithWeeks>,
    val needsProportionalRendering: Boolean = false,
    val isVisualDemoted: Boolean = false,
    val nonActiveRanges: List<Pair<Float, Float>> = emptyList(),
    /** 双人叠加视图中来自配对情侣课表的课程块：仅展示，禁止拖拽/跨周移动（节次语义不同）。 */
    val isForeignTable: Boolean = false,
    /** 叠加且双方作息不同时课程卡顶部显示的起止时间（如 "8:00-9:50"），按课程所属表的作息解析。 */
    val displayTimeRange: String? = null
)

data class WeeklyScheduleUiState(
    val style: ScheduleGridStyleComposed = ScheduleGridStyle().toComposedStyle(),
    val showWeekends: Boolean = false,
    val totalWeeks: Int = 20,
    val timeSlots: List<TimeSlot> = emptyList(),
    val courseCache: Map<String, List<MergedCourseBlock>> = emptyMap(),
    val currentMergedCourses: List<MergedCourseBlock> = emptyList(),
    val isSemesterSet: Boolean = false,
    val semesterStartDate: LocalDate? = null,
    val firstDayOfWeek: Int = DayOfWeek.MONDAY.isoDayNumber,
    val weekIndexInPager: Int? = null,
    val currentWeekNumber: Int? = null,
    val pagerMondayDate: LocalDate = getTodayLocalDate().startOfWeek(DayOfWeek.MONDAY),
    val currentSectionIndex: Int = -1,
    val daysUntilStart: Long = 0,
    val floatingCourse: CourseWithWeeks? = null,
    val floatingSourceWeek: Int? = null,
    /** 双人叠加视图是否生效（开关开 + 当前为本人课表 + 存在配对情侣课表）。 */
    val coupleOverlayActive: Boolean = false,
    /** 双方作息时间是否不一样（叠加视图下决定课程卡是否显示起止时间）。 */
    val coupleTimesDiffer: Boolean = false,
    /** 每张课表各自的生效作息（叠加模式下本人 / 情侣课程分别按自己的时间换算网格坐标）。 */
    val timeSlotsByTable: Map<String, List<TimeSlot>> = emptyMap()
)

/**
 * 规范化课程坐标的中间对象
 */
private data class NormalizedCourse(
    val raw: CourseWithWeeks,
    val start: Float,
    val end: Float
)

/**
 * 预解析后的节次时间，避免同一批 timeSlots 被反复 LocalTime.parse。
 */
private data class ParsedTimeSlot(
    val slot: TimeSlot,
    val startTime: LocalTime,
    val endTime: LocalTime
)

/**
 * 五路基础流聚合的源快照，把三层 combine 压缩为小幅中间载体。
 */
private data class ScheduleSourceSnapshot(
    val settings: AppSettingsModel,
    val config: CourseTableConfig?,
    val style: ScheduleGridStyle,
    val mondayDate: LocalDate,
    val timeSlots: List<TimeSlot>,
    val today: LocalDate
)

/**
 * 课程装配源键：currentCoursesFlow 的去重载体——只含课程链真正消费的字段，
 * DataStore 无关写入（主题/玻璃/动效等）被 distinctUntilChanged 拦截，不重启课程链。
 */
private data class CourseSourceKey(
    val tableId: String,
    val coupleEnabled: Boolean,
    val showNonCurrentWeek: Boolean,
    val selfColor: Int,
    val coupleColor: Int,
    val config: CourseTableConfig?,
    val style: ScheduleGridStyle,
    val mondayDate: LocalDate,
    val timeSlots: List<TimeSlot>
)

/**
 * 情侣课表叠加上下文：叠加是否生效 + 配对情侣课表 ID 与其生效作息。
 */
private data class CoupleOverlayContext(
    val active: Boolean = false,
    val coupleTableId: String? = null,
    val coupleTimeSlots: List<TimeSlot> = emptyList(),
    /** 双方作息时间是否不一样。 */
    val timesDiffer: Boolean = false
)

/**
 * 辅助函数：获取当前系统时区的当前日期
 */
private fun getTodayLocalDate(): LocalDate {
    return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}

/**
 * 辅助函数：获取当前系统时区的当前时间
 */
private fun getCurrentLocalTime(): LocalTime {
    return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
}

/**
 * 辅助扩展：计算当前日期所在周的起始日期（周一/周日等）
 */
private fun LocalDate.startOfWeek(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): LocalDate {
    val dayOfWeek = this.dayOfWeek.isoDayNumber
    val targetIso = firstDayOfWeek.isoDayNumber
    val diff = (dayOfWeek - targetIso + 7) % 7
    return this.minus(diff, DateTimeUnit.DAY)
}

/**
 * 辅助函数：格式化 LocalTime 为 HH:mm 格式
 */
private fun LocalTime.formatToHHmm(): String {
    val hourStr = hour.toString().padStart(2, '0')
    val minuteStr = minute.toString().padStart(2, '0')
    return "$hourStr:$minuteStr"
}

private fun List<TimeSlot>.sortedParsedTimeSlots(): List<ParsedTimeSlot> =
    map { slot ->
        ParsedTimeSlot(
            slot = slot,
            startTime = LocalTime.parse(slot.startTime),
            endTime = LocalTime.parse(slot.endTime)
        )
    }.sortedBy { it.slot.number }

@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
@KoinViewModel
class WeeklyScheduleViewModel (
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val styleSettingsRepository: StyleSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyScheduleUiState())
    val uiState: StateFlow<WeeklyScheduleUiState> = _uiState.asStateFlow()

    private val _pagerMondayDate = MutableStateFlow(
        getTodayLocalDate().startOfWeek(DayOfWeek.MONDAY)
    )

    private val appSettingsFlow = appSettingsRepository.getAppSettings()

    /** 运行时课表样式完全以用户个性化配置为准，主题预设仅在切换时写入。 */
    private val styleFlow = styleSettingsRepository.styleFlow

    /** 周课表视图模式，持久化到 AppSettings。 */
    val scheduleViewModeState: StateFlow<ScheduleViewMode> = appSettingsFlow
        .map { it.scheduleViewMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ScheduleViewMode.WEEK
        )

    private val courseTableConfigFlow = appSettingsFlow.flatMapLatest { settings ->
        val tableId = settings.currentCourseTableId
        if (tableId.isNotEmpty()) {
            appSettingsRepository.getCourseTableConfigFlow(tableId)
        } else {
            flowOf(null)
        }
    }

    private val timeSlotsFlow = appSettingsFlow.flatMapLatest { settings ->
        timeSlotRepository.getActiveTimeSlotsByConfigFlow(
            settings.currentCourseTableId,
            courseTableConfigFlow
        )
    }

    /** 当前选中的课表实体（判断当前表是否情侣课表 / 是否存在配对情侣课表）。 */
    private val currentTableFlow: Flow<CourseTable?> = appSettingsFlow.flatMapLatest { settings ->
        if (settings.currentCourseTableId.isEmpty()) {
            flowOf(null)
        } else {
            courseTableRepository.getAllCourseTables().map { tables ->
                tables.firstOrNull { it.id == settings.currentCourseTableId }
            }
        }
    }.distinctUntilChanged()

    /**
     * 情侣课表叠加上下文：开关开启 + 当前为本人课表 + 存在配对情侣课表时生效。
     * 全链路响应式：情侣课表的创建/删除、其作息配置与方案切换变化都会重新装配，
     * 保证叠加网格的坐标映射始终跟随 TA 课表的最新作息。
     */
    private val coupleOverlayContextFlow: StateFlow<CoupleOverlayContext> = combine(
        appSettingsFlow.map { it.coupleScheduleEnabled },
        currentTableFlow,
        timeSlotsFlow
    ) { coupleEnabled, currentTable, selfSlots ->
        Triple(coupleEnabled, currentTable?.takeIf { !it.isCouple }, selfSlots)
    }
        // DUC 前移：设置里任何无关字段写入都不再重启内层冷链（情侣表/配置/时段观察）
        .distinctUntilChanged()
        .flatMapLatest { (coupleEnabled, selfTable, selfSlots) ->
        if (!coupleEnabled || selfTable == null) {
            flowOf(CoupleOverlayContext())
        } else {
            courseTableRepository.getCoupleTableFor(selfTable.id).flatMapLatest { coupleTable ->
                if (coupleTable == null) {
                    flowOf(CoupleOverlayContext())
                } else {
                    val coupleConfigFlow = appSettingsRepository.getCourseTableConfigFlow(coupleTable.id)
                    timeSlotRepository.getActiveTimeSlotsByConfigFlow(coupleTable.id, coupleConfigFlow)
                        .map { coupleSlots ->
                            CoupleOverlayContext(
                                active = true,
                                coupleTableId = coupleTable.id,
                                coupleTimeSlots = coupleSlots,
                                timesDiffer = scheduleTimesDiffer(selfSlots, coupleSlots)
                            )
                        }
                }
            }
        }
    }
        // 热化：currentCoursesFlow 与 init 最终 combine 两路共用同一上游，
        // 消除重复 Room 订阅与「缓存/开关错帧」闪变
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoupleOverlayContext())

    private val currentCoursesFlow = combine(
        _pagerMondayDate,
        appSettingsFlow,
        courseTableConfigFlow,
        timeSlotsFlow,
        styleFlow
    ) { date, settings, config, slots, style ->
        // 紧凑源键：只保留课程装配真正消费的字段——DataStore 无关写入
        //（主题/玻璃/动效等）不再触发三窗口课程链整链重启
        CourseSourceKey(
            tableId = settings.currentCourseTableId,
            coupleEnabled = settings.coupleScheduleEnabled,
            showNonCurrentWeek = settings.showNonCurrentWeekCourses,
            selfColor = settings.selfCourseColorIndex,
            coupleColor = settings.crushCourseColorIndex,
            config = config,
            style = style,
            mondayDate = date,
            timeSlots = slots
        )
    }.distinctUntilChanged().flatMapLatest { source ->
        coupleOverlayContextFlow.flatMapLatest { overlay ->
            if (source.config != null) {
                val window = listOf(
                    source.mondayDate.minus(1, DateTimeUnit.WEEK),
                    source.mondayDate,
                    source.mondayDate.plus(1, DateTimeUnit.WEEK)
                )

                combine(window.map { day -> dayCoursesFlow(day, source, overlay) }) { results ->
                    results.toMap()
                }
            } else {
                flowOf(emptyMap())
            }
        }
    }

    /**
     * 单个页面日期的课程流：本人课程 +（叠加模式）配对情侣课表课程，
     * 合并后按各自课表的作息换算为网格坐标块。
     */
    private fun dayCoursesFlow(
        day: LocalDate,
        source: CourseSourceKey,
        overlay: CoupleOverlayContext
    ): Flow<Pair<String, List<MergedCourseBlock>>> {
        val config = source.config
            ?: return flowOf(day.toString() to emptyList())
        val mode = source.style.scheduleMode
        val tableId = source.tableId

        val pageWeekNum = appSettingsRepository.getWeekIndexAtDate(
            targetDate = day,
            startDateStr = config.semesterStartDate,
            firstDayOfWeekInt = config.firstDayOfWeek
        )

        val isWithinSemester = pageWeekNum != null && pageWeekNum in 1..config.semesterTotalWeeks

        val coursesFlow = if (source.showNonCurrentWeek && isWithinSemester) {
            courseTableRepository.getCoursesWithWeeksByTableId(tableId).map { allCourses ->
                allCourses.filter { cw ->
                    cw.weeks.any { it.weekNumber >= pageWeekNum!! }
                }
            }
        } else {
            courseTableRepository.getCoursesWithWeeksByDate(tableId, day, config)
        }

        // 情侣课表叠加：合并配对情侣课表课程并统一着色。
        // 情侣课程按本人课表的周次过滤（叠加视图的学期进度以本人课表为准）。
        val combinedCoursesFlow: Flow<List<CourseWithWeeks>> =
            if (overlay.active && overlay.coupleTableId != null) {
                val coupleCoursesFlow = if (source.showNonCurrentWeek && isWithinSemester) {
                    courseTableRepository.getCoursesWithWeeksByTableId(overlay.coupleTableId).map { allCourses ->
                        allCourses.filter { cw ->
                            cw.weeks.any { it.weekNumber >= pageWeekNum!! }
                        }
                    }
                } else {
                    courseTableRepository.getCoursesWithWeeksByDate(overlay.coupleTableId, day, config)
                }
                combine(coursesFlow, coupleCoursesFlow) { selfCourses, coupleCourses ->
                    val selfColored = selfCourses.map { cw ->
                        cw.copy(course = cw.course.copy(colorInt = source.selfColor))
                    }
                    val coupleColored = coupleCourses.map { cw ->
                        cw.copy(course = cw.course.copy(colorInt = source.coupleColor))
                    }
                    selfColored + coupleColored
                }
            } else {
                coursesFlow
            }

        val slotsByTable = buildMap {
            put(tableId, source.timeSlots)
            if (overlay.active && overlay.coupleTableId != null) {
                put(overlay.coupleTableId, overlay.coupleTimeSlots)
            }
        }

        return combinedCoursesFlow.map { courses ->
            day.toString() to mergeCourses(
                courses = courses,
                timeSlots = source.timeSlots,
                currentWeek = pageWeekNum ?: -1,
                mode = mode,
                timeSlotsByTable = slotsByTable,
                gridTableId = tableId,
                showTimeRanges = overlay.active && overlay.timesDiffer
            )
        }
    }

    /** 作息时间表签名：节次号 + 起止时间逐项比对。 */
    private fun scheduleSignature(slots: List<TimeSlot>): List<String> =
        slots.sortedBy { it.number }.map { "${it.number}:${it.startTime}-${it.endTime}" }

    private fun scheduleTimesDiffer(selfSlots: List<TimeSlot>, coupleSlots: List<TimeSlot>): Boolean {
        if (selfSlots.isEmpty() && coupleSlots.isEmpty()) return false
        return scheduleSignature(selfSlots) != scheduleSignature(coupleSlots)
    }
        // v3.51.2 修复（保留）：切周三窗口课程重建（查询 + mergeCourses）为 CPU/IO 密集工作，
        // flowOn(Default) 把上游移到默认线程池，下游 collect 仍留 Main 只做轻量状态拼装
        .flowOn(Dispatchers.Default)


    init {
        // 0. 切表即清空跨周悬浮课程：手势落库按课程原属表写入，残留状态会写到旧表
        viewModelScope.launch {
            appSettingsFlow.map { it.currentCourseTableId }.distinctUntilChanged().collect {
                if (_uiState.value.floatingCourse != null) exitFloatingMode()
            }
        }

        // 1. 扁平化组合：先合并 5 路基础流 + 当前日期流（跨天自动重算周次/倒计时），
        //    再并入课程缓存流，避免三层 configAndTimeFlow 中间流。
        viewModelScope.launch {
            val baseSourceFlow = combine(
                appSettingsFlow,
                courseTableConfigFlow,
                styleFlow,
                _pagerMondayDate,
                timeSlotsFlow
            ) { settings, config, style, mondayDate, timeSlots ->
                ScheduleSourceSnapshot(
                    settings, config, style, mondayDate, timeSlots,
                    today = getTodayLocalDate()
                )
            }

            val sourceFlow = combine(baseSourceFlow, currentDateFlow()) { snapshot, today ->
                snapshot.copy(today = today)
            }

            combine(sourceFlow, currentCoursesFlow, coupleOverlayContextFlow) { source, cache, overlay ->
                val config = source.config
                val style = source.style
                val mondayDate = source.mondayDate
                val timeSlots = source.timeSlots
                val composedStyle = style.toComposedStyle()
                val startDate = config?.semesterStartDate?.let { LocalDate.parse(it) }
                val firstDayOfWeekInt = config?.firstDayOfWeek ?: DayOfWeek.MONDAY.isoDayNumber
                val totalWeeks = config?.semesterTotalWeeks ?: 20
                val today = source.today

                val currentWeekNum = appSettingsRepository.getWeekIndexAtDate(
                    targetDate = today,
                    startDateStr = config?.semesterStartDate,
                    firstDayOfWeekInt = firstDayOfWeekInt
                )

                val weekIndex = appSettingsRepository.getWeekIndexAtDate(
                    targetDate = mondayDate,
                    startDateStr = config?.semesterStartDate,
                    firstDayOfWeekInt = firstDayOfWeekInt
                )

                val currentSectionIndex = calculateCurrentSectionIndex(timeSlots)

                val daysUntil = if (startDate != null && today < startDate) {
                    today.daysUntil(startDate).toLong()
                } else 0L

                val previousState = _uiState.value

                val slotsByTable = if (overlay.active && overlay.coupleTableId != null) {
                    mapOf(
                        source.settings.currentCourseTableId to timeSlots,
                        overlay.coupleTableId to overlay.coupleTimeSlots
                    )
                } else {
                    mapOf(source.settings.currentCourseTableId to timeSlots)
                }

                WeeklyScheduleUiState(
                    style = composedStyle,
                    showWeekends = config?.showWeekends ?: false,
                    totalWeeks = totalWeeks,
                    courseCache = cache,
                    currentMergedCourses = cache[mondayDate.toString()] ?: emptyList(),
                    timeSlots = timeSlots,
                    isSemesterSet = startDate != null,
                    semesterStartDate = startDate,
                    firstDayOfWeek = firstDayOfWeekInt,
                    weekIndexInPager = weekIndex,
                    currentWeekNumber = currentWeekNum,
                    pagerMondayDate = mondayDate,
                    currentSectionIndex = currentSectionIndex,
                    daysUntilStart = daysUntil,
                    floatingCourse = previousState.floatingCourse,
                    floatingSourceWeek = previousState.floatingSourceWeek,
                    coupleOverlayActive = overlay.active,
                    coupleTimesDiffer = overlay.timesDiffer,
                    timeSlotsByTable = slotsByTable
                )
                // 同 v3.51.2：块内含 getWeekIndexAtDate（数据库查询）×2 与 LocalDate.parse，
                // flowOn(Default) 让合成在默认线程池执行，.collect 在下游 Main 仅做状态引用赋值。
            }.flowOn(Dispatchers.Default)
                .collect { _uiState.value = it }
        }

        // 2. 颜色去重 + 越界修复：以「整张课表」为基准，而不是当前可视周的课程缓存。
        //    可视缓存经 buildMergedBlocks / filterNonActiveCourseOverlaps 过滤，会漏掉「本周不上、
        //    其它周才上」的课程，导致统计不完整、跨周滑动时配色不一致、撞色残留。
        viewModelScope.launch {
            appSettingsFlow
                .flatMapLatest { settings ->
                    courseTableRepository.getCoursesWithWeeksByTableId(settings.currentCourseTableId)
                }
                .distinctUntilChanged()
                .collect { courses ->
                    val style = styleFlow.firstOrNull() ?: return@collect
                    ensureDistinctCourseColors(courses, style)
                }
        }

        // 3. 当前节次指示每分钟刷新一次。
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                val slots = _uiState.value.timeSlots
                _uiState.update { state ->
                    state.copy(currentSectionIndex = calculateCurrentSectionIndex(slots))
                }
            }
        }
    }

    private fun calculateCurrentSectionIndex(timeSlots: List<TimeSlot>): Int {
        if (timeSlots.isEmpty()) return -1
        val now = getCurrentLocalTime()
        val currentMinutes = now.hour * 60 + now.minute

        timeSlots.forEachIndexed { index, slot ->
            val startParts = slot.startTime.split(":")
            val endParts = slot.endTime.split(":")

            if (startParts.size == 2 && endParts.size == 2) {
                val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
                val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()

                if (currentMinutes in startMinutes until endMinutes) {
                    return index + 1
                }
            }
        }
        return -1
    }

    fun updatePagerDate(newDate: LocalDate) = _pagerMondayDate.update { newDate }

    /** 更新并持久化周课表视图模式。 */
    fun updateScheduleViewMode(mode: ScheduleViewMode) = viewModelScope.launch {
        appSettingsRepository.updateScheduleViewMode(mode)
    }

    fun switchCourseTable(tableId: String) {
        viewModelScope.launch {
            val currentSettings = appSettingsRepository.getAppSettingsOnce()
            val newSettings = currentSettings.copy(currentCourseTableId = tableId)
            appSettingsRepository.insertOrUpdateAppSettings(newSettings)
        }
    }

    /**
     * 保证当前课表内「不同课程名 → 不同颜色」，并顺带修复越界的颜色索引。
     *
     * 历史数据由早前的随机分配产生，极易撞色；原实现只修越界索引、不处理重复，故撞色长期残留。
     * 这里按课程名归一：同名课次统一为一个颜色（与导入路径 getOrAssignColorByName 语义一致），
     * 尽量保留课程名已有的颜色，仅在与其它课程名冲突时改配「全局占用最少」的颜色。
     * crush 课程不参与：其颜色由 crushCourseColorIndex 统一控制。
     */
    private suspend fun ensureDistinctCourseColors(courses: List<CourseWithWeeks>, style: ScheduleGridStyle) {
        // 课程会按周在缓存中重复出现，先按 id 去重，避免重复统计与重复写库。
        val distinctCourses = courses
            .filter { !it.course.isCrush }
            .distinctBy { it.course.id }
        if (distinctCourses.isEmpty()) return

        val colorByName = style.resolveDistinctColorIndices(
            distinctCourses
                .groupBy { it.course.name }
                .mapValues { (_, group) -> group.map { it.course.colorInt } }
        )

        distinctCourses.forEach { courseWithWeeks ->
            val target = colorByName[courseWithWeeks.course.name] ?: return@forEach
            if (courseWithWeeks.course.colorInt != target) {
                courseTableRepository.updateCourseColor(courseWithWeeks.course.id, target)
            }
        }
    }

    /**
     * 核心统一时间换算器：将任意 [LocalTime] 转化为网格上的 Float 纵坐标。
     * [parsedSlots] 为入口处预解析并排序后的节次时间，避免函数内重复 parse。
     */
    private fun timeToGridScale(
        time: LocalTime,
        parsedSlots: List<ParsedTimeSlot>,
        mode: ScheduleModeProto
    ): Float {
        return when (mode) {
            ScheduleModeProto.TIME_24H_MODE -> {
                val currentMinutes = time.hour * 60 + time.minute
                val hourOffset = currentMinutes.toFloat() / 60f
                1.0f + hourOffset
            }
            ScheduleModeProto.SECTION_MODE -> {
                if (parsedSlots.isEmpty()) return 1.0f

                val firstSlotStart = parsedSlots.first().startTime
                val lastSlotEnd = parsedSlots.last().endTime

                if (time <= firstSlotStart) return 1.0f
                if (time >= lastSlotEnd) return (parsedSlots.size + 1).toFloat()

                val currentSlot = parsedSlots.find {
                    time in it.startTime..it.endTime
                }

                if (currentSlot != null) {
                    val sTime = currentSlot.startTime
                    val eTime = currentSlot.endTime
                    val duration = (eTime.toSecondOfDay() - sTime.toSecondOfDay()) / 60
                    val safeDuration = if (duration <= 0) 1 else duration
                    val elapsedMinutes = (time.toSecondOfDay() - sTime.toSecondOfDay()) / 60
                    return currentSlot.slot.number.toFloat() + (elapsedMinutes.toFloat() / safeDuration)
                }

                val nextSlot = parsedSlots.find { it.startTime > time }
                nextSlot?.slot?.number?.toFloat() ?: (parsedSlots.size + 1).toFloat()
            }
        }
    }

    /**
     * 反向坐标时间换算器。
     */
    private fun gridScaleToTime(
        gridSection: Float,
        parsedSlots: List<ParsedTimeSlot>,
        mode: ScheduleModeProto
    ): LocalTime {
        return when (mode) {
            ScheduleModeProto.TIME_24H_MODE -> {
                val totalMinutes = (gridSection * 60f).toInt().coerceIn(0, 24 * 60 - 1)
                val hour = totalMinutes / 60
                val minute = totalMinutes % 60
                LocalTime(hour, minute)
            }
            ScheduleModeProto.SECTION_MODE -> {
                if (parsedSlots.isEmpty()) return LocalTime(8, 0)

                val targetScale = gridSection + 1.0f
                val integerPart = targetScale.toInt()
                val fraction = targetScale - integerPart

                val matchedSlot = parsedSlots.find { it.slot.number == integerPart }
                if (matchedSlot != null) {
                    val sTime = matchedSlot.startTime
                    val eTime = matchedSlot.endTime
                    val totalDuration = (eTime.toSecondOfDay() - sTime.toSecondOfDay()) / 60
                    val addedMinutes = (fraction * totalDuration).toInt()
                    val totalSeconds = sTime.toSecondOfDay() + addedMinutes * 60
                    val finalHour = (totalSeconds / 3600) % 24
                    val finalMinute = (totalSeconds % 3600) / 60
                    LocalTime(finalHour, finalMinute)
                } else {
                    if (integerPart < parsedSlots.first().slot.number) {
                        parsedSlots.first().startTime
                    } else {
                        parsedSlots.last().endTime
                    }
                }
            }
        }
    }

    /**
     * 进入跨周移动暂存状态
     */
    fun enterFloatingMode(course: CourseWithWeeks, sourceWeek: Int) {
        _uiState.update {
            it.copy(
                floatingCourse = course,
                floatingSourceWeek = sourceWeek
            )
        }
    }

    /**
     * 全清空或取消挂起队列
     */
    fun exitFloatingMode() {
        _uiState.update {
            it.copy(
                floatingCourse = null,
                floatingSourceWeek = null
            )
        }
    }

    /**
     * 配合跨周结算的最终持久化落地更新
     */
    fun updateCourseTimeByFloatingGesture(
        targetWeek: Int,
        targetDay: Int,
        startSection: Float,
        endSection: Float,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val courseWrapper = state.floatingCourse ?: return@launch
                val sourceWeek = state.floatingSourceWeek ?: return@launch
                val mode = state.style.scheduleMode
                val slots = state.timeSlots

                val currentSettings = appSettingsRepository.getAppSettingsOnce()
                val tableId = currentSettings.currentCourseTableId
                if (tableId.isBlank()) return@launch

                val originalCourse = courseWrapper.course

                val updatedCourseForTime = if (mode == ScheduleModeProto.TIME_24H_MODE) {
                    val parsedSlots = slots.sortedParsedTimeSlots()
                    val baseStartTime = gridScaleToTime(startSection, parsedSlots, mode)
                    val origStart = LocalTime.parse(originalCourse.customStartTime ?: "08:00")
                    val origEnd = LocalTime.parse(originalCourse.customEndTime ?: "09:00")
                    val originalDurationMinutes = ((origEnd.toSecondOfDay() - origStart.toSecondOfDay()) / 60).coerceAtLeast(1)
                    val newStartTime = baseStartTime
                    val startMinutesFromMidnight = newStartTime.hour * 60 + newStartTime.minute
                    val rawEndMinutes = startMinutesFromMidnight + originalDurationMinutes

                    val (finalEndTime, isTruncatedToMidnight) = if (rawEndMinutes >= 1440) {
                        LocalTime(23, 59) to true
                    } else {
                        val endSec = (newStartTime.toSecondOfDay() + originalDurationMinutes * 60) % (24 * 3600)
                        LocalTime(endSec / 3600, (endSec % 3600) / 60) to false
                    }
                    val calcStartSection = newStartTime.hour + 1

                    val finalEndSection = if (isTruncatedToMidnight) {
                        24
                    } else {
                        val calcEndSection = if (finalEndTime.minute > 0) finalEndTime.hour + 1 else finalEndTime.hour
                        if (calcEndSection == 0) 24 else calcEndSection
                    }

                    originalCourse.copy(
                        day = targetDay,
                        isCustomTime = true,
                        customStartTime = newStartTime.formatToHHmm(),
                        customEndTime = finalEndTime.formatToHHmm(),
                        startSection = calcStartSection.coerceIn(1, 24),
                        endSection = finalEndSection.coerceIn(1, 24)
                    )
                } else {
                    val newStartSection = startSection.toInt().coerceIn(1, slots.size)
                    val newEndSection = endSection.toInt().coerceIn(1, slots.size)
                    if (newStartSection > newEndSection) return@launch

                    originalCourse.copy(
                        day = targetDay,
                        isCustomTime = false,
                        customStartTime = null,
                        customEndTime = null,
                        startSection = newStartSection,
                        endSection = newEndSection
                    )
                }

                val isNoPositionChange = originalCourse.day == updatedCourseForTime.day &&
                        originalCourse.startSection == updatedCourseForTime.startSection &&
                        originalCourse.endSection == updatedCourseForTime.endSection &&
                        originalCourse.customStartTime == updatedCourseForTime.customStartTime &&
                        originalCourse.customEndTime == updatedCourseForTime.customEndTime

                if (sourceWeek == targetWeek && isNoPositionChange) {
                    return@launch
                }

                val isSingleWeek = courseWrapper.weeks.size <= 1

                if (isSingleWeek) {
                    val weekNumbers = listOf(targetWeek)
                    courseTableRepository.upsertCourse(updatedCourseForTime, weekNumbers)
                } else {
                    val remainingWeeks = courseWrapper.weeks
                        .map { it.weekNumber }
                        .filter { it != sourceWeek }
                    courseTableRepository.upsertCourse(originalCourse, remainingWeeks)

                    val clonedNewId = Uuid.random().toString()
                    val finalClonedCourse = updatedCourseForTime.copy(id = clonedNewId)
                    courseTableRepository.upsertCourse(finalClonedCourse, listOf(targetWeek))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update {
                    it.copy(
                        floatingCourse = null,
                        floatingSourceWeek = null
                    )
                }
                onComplete()
            }
        }
    }

    /**
     * 统一持久化调度手势调课方法（拆分并更新单周/多周周次逻辑）
     */
    fun updateCourseTimeByGesture(
        courseId: String,
        targetDay: Int,
        startSection: Float,
        endSection: Float,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val mode = state.style.scheduleMode
                val slots = state.timeSlots
                val currentWeek = state.weekIndexInPager ?: state.currentWeekNumber ?: return@launch

                val currentSettings = appSettingsRepository.getAppSettingsOnce()
                val tableId = currentSettings.currentCourseTableId
                if (tableId.isBlank()) return@launch

                val allCoursesWithWeeks = courseTableRepository.getCoursesWithWeeksByTableId(tableId).firstOrNull() ?: return@launch
                val targetWrapper = allCoursesWithWeeks.find { it.course.id == courseId } ?: return@launch
                val originalCourse = targetWrapper.course

                val updatedCourseForTime = if (mode == ScheduleModeProto.TIME_24H_MODE) {
                    val parsedSlots = slots.sortedParsedTimeSlots()
                    val newStartTime = gridScaleToTime(startSection, parsedSlots, mode)
                    val newEndTime = gridScaleToTime(endSection, parsedSlots, mode)

                    originalCourse.copy(
                        day = targetDay,
                        isCustomTime = true,
                        customStartTime = newStartTime.formatToHHmm(),
                        customEndTime = newEndTime.formatToHHmm(),
                        startSection = (startSection.toInt() + 1).coerceIn(1, 24),
                        endSection = (endSection.toInt() + 1).coerceIn(1, 24)
                    )
                } else {
                    val newStartSection = (startSection.toInt() + 1).coerceIn(1, slots.size)
                    val newEndSection = endSection.toInt().coerceIn(1, slots.size)
                    if (newStartSection > newEndSection) return@launch

                    originalCourse.copy(
                        day = targetDay,
                        isCustomTime = false,
                        customStartTime = null,
                        customEndTime = null,
                        startSection = newStartSection,
                        endSection = newEndSection
                    )
                }
                val isNoPositionChange = originalCourse.day == updatedCourseForTime.day &&
                        originalCourse.startSection == updatedCourseForTime.startSection &&
                        originalCourse.endSection == updatedCourseForTime.endSection &&
                        originalCourse.customStartTime == updatedCourseForTime.customStartTime &&
                        originalCourse.customEndTime == updatedCourseForTime.customEndTime

                if (isNoPositionChange) {
                    return@launch
                }

                val isSingleWeek = targetWrapper.weeks.size <= 1

                if (isSingleWeek) {
                    val weekNumbers = targetWrapper.weeks.map { it.weekNumber }
                    courseTableRepository.upsertCourse(updatedCourseForTime, weekNumbers)
                } else {
                    val remainingWeeks = targetWrapper.weeks
                        .map { it.weekNumber }
                        .filter { it != currentWeek }
                    courseTableRepository.upsertCourse(originalCourse, remainingWeeks)

                    val clonedNewId = Uuid.random().toString()
                    val finalClonedCourse = updatedCourseForTime.copy(id = clonedNewId)
                    courseTableRepository.upsertCourse(finalClonedCourse, listOf(currentWeek))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                onComplete()
            }
        }
    }

    /**
     * 展平排版调度引擎：入口只负责统一预解析时间，具体排版拆给下面的私有函数。
     *
     * [timeSlotsByTable] 叠加模式下携带本人 / 情侣课表各自的作息：课程先按所属表的节次
     * 解析出绝对起止时间，再统一换算到网格坐标（网格由 [timeSlots] 即本人课表作息决定）。
     * 单表模式下留空，全部课程回落到网格作息，行为与旧版一致。
     */
    fun mergeCourses(
        courses: List<CourseWithWeeks>,
        timeSlots: List<TimeSlot>,
        currentWeek: Int,
        mode: ScheduleModeProto = ScheduleModeProto.SECTION_MODE,
        timeSlotsByTable: Map<String, List<TimeSlot>> = emptyMap(),
        gridTableId: String? = null,
        showTimeRanges: Boolean = false
    ): List<MergedCourseBlock> {
        if (timeSlots.isEmpty() && mode == ScheduleModeProto.SECTION_MODE) return emptyList()

        val parsedSlots = timeSlots.sortedParsedTimeSlots()
        val parsedSlotsByTable = timeSlotsByTable.mapValues { (_, slots) -> slots.sortedParsedTimeSlots() }
        return buildMergedBlocks(courses, parsedSlots, parsedSlotsByTable, gridTableId, currentWeek, mode, showTimeRanges)
    }

    private fun buildMergedBlocks(
        courses: List<CourseWithWeeks>,
        parsedSlots: List<ParsedTimeSlot>,
        parsedSlotsByTable: Map<String, List<ParsedTimeSlot>>,
        gridTableId: String?,
        currentWeek: Int,
        mode: ScheduleModeProto,
        showTimeRanges: Boolean
    ): List<MergedCourseBlock> {
        val maxSection = if (mode == ScheduleModeProto.TIME_24H_MODE) 24f else parsedSlots.size.toFloat()
        val limit = maxSection + 1.0f
        val minSafeHeight = if (mode == ScheduleModeProto.TIME_24H_MODE) 0.0f else 0.3f

        val normalizedList = normalizeCourses(courses, parsedSlots, parsedSlotsByTable, mode, limit, minSafeHeight)

        val result = mutableListOf<MergedCourseBlock>()
        normalizedList.groupBy { it.raw.course.day }.forEach { (day, dailyCourses) ->
            if (dailyCourses.isEmpty()) return@forEach

            // 过滤周次不相交的同时间段课程，避免“后面的课程和前面的课程重合”。
            // 开启“显示非本周课程”后，页面会加载所有未来周仍开课的课程；若课程 A（如 1-15 周）
            // 与课程 B（如 16-18 周）处于同一时间段但周次完全不相交，它们会被拆成独立簇，
            // 各自以整列宽度绘制在完全相同坐标上造成重合。二者周次不相交、真实课表中永远不会
            // 同时出现，因此这里只保留当前周活跃的课程，非本周课程仅在它不遮挡任何已保留课程时
            // 作为该时段的占位预览展示。
            val filteredCourses = filterNonActiveCourseOverlaps(dailyCourses, currentWeek)

            // 本人课程优先排序，确保时间重叠时本人占左列、情侣课程占右列
            val sorted = filteredCourses.sortedWith(
                compareBy<NormalizedCourse> { it.raw.course.courseTableId != gridTableId }
                    .thenBy { it.start }
                    .thenByDescending { it.end - it.start }
            )

            val clusters = buildOverlappingClusters(sorted)

            for (cluster in clusters) {
                result.addAll(buildClusterBlocks(day, cluster, mode, maxSection, currentWeek, gridTableId, parsedSlots, parsedSlotsByTable, showTimeRanges))
            }
        }
        return result
    }

    private fun normalizeCourses(
        courses: List<CourseWithWeeks>,
        parsedSlots: List<ParsedTimeSlot>,
        parsedSlotsByTable: Map<String, List<ParsedTimeSlot>>,
        mode: ScheduleModeProto,
        limit: Float,
        minSafeHeight: Float
    ): List<NormalizedCourse> {
        val normalizedList = mutableListOf<NormalizedCourse>()
        for (cw in courses) {
            val normalized = normalizeCourse(cw, parsedSlots, parsedSlotsByTable, mode, limit, minSafeHeight)
            if (normalized != null) {
                normalizedList.add(normalized)
            }
        }
        return normalizedList
    }

    private fun normalizeCourse(
        cw: CourseWithWeeks,
        parsedSlots: List<ParsedTimeSlot>,
        parsedSlotsByTable: Map<String, List<ParsedTimeSlot>>,
        mode: ScheduleModeProto,
        limit: Float,
        minSafeHeight: Float
    ): NormalizedCourse? {
        return try {
            val c = cw.course

            // 课程所属表的作息优先（叠加模式下情侣课程按情侣课表自己的节次时间换算），
            // 无对应记录（单表模式）时回落到网格作息，行为与旧版一致。
            val ownSlots = parsedSlotsByTable[c.courseTableId] ?: parsedSlots

            val (sTime, eTime) = if (c.isCustomTime) {
                LocalTime.parse(c.customStartTime ?: return null) to
                        LocalTime.parse(c.customEndTime ?: return null)
            } else {
                val startSlot = ownSlots.find { it.slot.number == c.startSection } ?: return null
                val endSlot = ownSlots.find { it.slot.number == c.endSection } ?: return null
                startSlot.startTime to endSlot.endTime
            }

            val s = timeToGridScale(sTime, parsedSlots, mode)
            val e = timeToGridScale(eTime, parsedSlots, mode)

            var finalStart = s
            var finalEnd = e
            if (finalStart >= limit) {
                finalEnd = limit
                finalStart = limit - minSafeHeight
            } else if (finalEnd <= 1.0f) {
                finalStart = 1.0f
                finalEnd = 1.0f + minSafeHeight
            }

            if (finalEnd - finalStart < minSafeHeight) {
                if (finalEnd + minSafeHeight <= limit) {
                    finalEnd = finalStart + minSafeHeight
                } else {
                    finalStart = finalEnd - minSafeHeight
                }
            }

            NormalizedCourse(cw, finalStart.coerceIn(1.0f, limit - 0.1f), finalEnd.coerceIn(1.0f + 0.1f, limit))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 过滤掉与“当前周活跃课程”或已保留课程在时间上重叠的非本周课程（幽灵块）。
     *
     * 开启“显示非本周课程”后，页面会加载所有“含 ≥ 当前页周次”的课程，因此同一时间段可能同时
     * 出现当前周活跃课程与其后某周开课的课程（如 1-15 周与 16-18 周同时间段的两个课程）。二者
     * 周次不相交、真实课表中永远不会同时出现，若都展示就会在网格上完全重合。
     *
     * 规则：
     * 1. 当前周活跃课程（weeks 含 currentWeek）全部保留，真实时间冲突由后续聚类分列处理；
     * 2. 非本周课程（幽灵块）仅在不与任何已保留课程时间重叠时才保留，作为该时段的占位预览，
     *    且幽灵块之间也互不重叠（多个未来周同时间段的课程只保留一个）。
     */
    private fun filterNonActiveCourseOverlaps(
        dailyCourses: List<NormalizedCourse>,
        currentWeek: Int
    ): List<NormalizedCourse> {
        fun isActiveInWeek(c: NormalizedCourse): Boolean =
            c.raw.weeks.any { it.weekNumber == currentWeek }

        fun timeOverlaps(a: NormalizedCourse, b: NormalizedCourse): Boolean =
            a.start < b.end - 0.01f && a.end > b.start + 0.01f

        val actives = dailyCourses.filter { isActiveInWeek(it) }
        val ghosts = dailyCourses.filter { !isActiveInWeek(it) }
            .sortedBy { it.start }

        val result = actives.toMutableList()
        val kept = actives.toMutableList()
        for (ghost in ghosts) {
            if (kept.none { timeOverlaps(ghost, it) }) {
                kept.add(ghost)
                result.add(ghost)
            }
        }
        return result
    }

    private fun buildOverlappingClusters(sorted: List<NormalizedCourse>): List<List<NormalizedCourse>> {
        val clusters = mutableListOf<MutableList<NormalizedCourse>>()

        for (item in sorted) {
            // 找出所有与 item 重叠的 cluster；可能有多个，必须全部合并，
            // 否则同一连通组会被割裂，导致不同簇独立分列后出现重合。
            val overlappingClusters = clusters.filter { cluster ->
                cluster.any { existing ->
                    item.start < existing.end - 0.01f && item.end > existing.start + 0.01f &&
                            weeksOverlap(item.raw, existing.raw)
                }
            }
            if (overlappingClusters.isEmpty()) {
                clusters.add(mutableListOf(item))
            } else {
                // 合并到第一个重叠簇，追加 item，再把其余簇的课程并入并移除原簇
                val mergedCluster = overlappingClusters.first()
                mergedCluster.add(item)
                for (i in 1 until overlappingClusters.size) {
                    mergedCluster.addAll(overlappingClusters[i])
                    clusters.remove(overlappingClusters[i])
                }
            }
        }
        return clusters
    }

    private fun buildClusterBlocks(
        day: Int,
        cluster: List<NormalizedCourse>,
        mode: ScheduleModeProto,
        maxSection: Float,
        currentWeek: Int,
        gridTableId: String?,
        parsedGridSlots: List<ParsedTimeSlot>,
        parsedSlotsByTable: Map<String, List<ParsedTimeSlot>>,
        showTimeRanges: Boolean
    ): List<MergedCourseBlock> {
        val columnEnds = mutableListOf<Float>()
        val itemToColumnIndex = mutableMapOf<NormalizedCourse, Int>()

        for (item in cluster) {
            var assignedIndex = -1
            for (i in columnEnds.indices) {
                if (columnEnds[i] <= item.start + 0.01f) {
                    assignedIndex = i
                    columnEnds[i] = item.end
                    break
                }
            }
            if (assignedIndex == -1) {
                columnEnds.add(item.end)
                assignedIndex = columnEnds.size - 1
            }
            itemToColumnIndex[item] = assignedIndex
        }

        val totalSubColumns = columnEnds.size
        val blocks = mutableListOf<MergedCourseBlock>()

        for (item in cluster) {
            val cw = item.raw
            val isCurrentWeekActive = cw.weeks.any { it.weekNumber == currentWeek }
            val myColumnIndex = itemToColumnIndex[item] ?: 0

            blocks.add(
                MergedCourseBlock(
                    day = day,
                    startSection = (item.start - 1f).coerceIn(0f, maxSection),
                    endSection = (item.end - 1f).coerceIn(0f, maxSection),
                    courses = listOf(cw),
                    needsProportionalRendering = (mode == ScheduleModeProto.TIME_24H_MODE) || cw.course.isCustomTime,
                    isVisualDemoted = !isCurrentWeekActive,
                    nonActiveRanges = listOf(myColumnIndex.toFloat() to totalSubColumns.toFloat()),
                    isForeignTable = gridTableId != null && cw.course.courseTableId != gridTableId,
                    displayTimeRange = if (showTimeRanges) {
                        formatCourseTimeRange(cw, parsedGridSlots, parsedSlotsByTable)
                    } else null
                )
            )
        }
        return blocks
    }

    /**
     * 课程起止时间文本（如 "8:00-9:50"）：按课程所属表的作息解析节次时间，
     * 自定义时间课程直接用其 HH:MM。仅在叠加视图且双方作息不一样时展示。
     */
    private fun formatCourseTimeRange(
        cw: CourseWithWeeks,
        parsedGridSlots: List<ParsedTimeSlot>,
        parsedSlotsByTable: Map<String, List<ParsedTimeSlot>>
    ): String? {
        return try {
            val c = cw.course
            val ownSlots = parsedSlotsByTable[c.courseTableId] ?: parsedGridSlots
            val (start, end) = if (c.isCustomTime) {
                LocalTime.parse(c.customStartTime ?: return null) to
                        LocalTime.parse(c.customEndTime ?: return null)
            } else {
                val startSlot = ownSlots.find { it.slot.number == c.startSection } ?: return null
                val endSlot = ownSlots.find { it.slot.number == c.endSection } ?: return null
                startSlot.startTime to endSlot.endTime
            }
            "${start.trimLeadingZero()}-${end.trimLeadingZero()}"
        } catch (e: Exception) {
            null
        }
    }

    /** "08:00" -> "8:00"（对齐需求示例 8:00-9:50 的展示格式）。 */
    private fun LocalTime.trimLeadingZero(): String = buildString {
        if (hour > 0) append(hour) else append("0")
        append(':')
        if (minute < 10) append('0')
        append(minute)
    }

    /**
     * 判断两个课程是否在周次上存在重叠。
     * 用于冲突检测：即使时间重叠，若周次完全不重叠（例如分别在第 1-3 周与第 5-7 周），
     * 也不应视作冲突课程同时显示。
     */
    private fun weeksOverlap(a: CourseWithWeeks, b: CourseWithWeeks): Boolean {
        val bWeeks = b.weeks.map { it.weekNumber }.toSet()
        return a.weeks.any { it.weekNumber in bWeeks }
    }
}
