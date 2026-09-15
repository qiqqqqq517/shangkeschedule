package com.shangkeschedule.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.shangkeschedule.MainActivity
import com.shangkeschedule.R
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.WidgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 状态栏「灵动岛」前台服务（Android 16 实时更新 / Promoted Ongoing）。
 *
 * 生命周期由 [DynamicIslandManager] 的「时间窗口启停」策略控制：
 *  - 由窗口开始闹钟启动、窗口结束闹钟停止，窗口之外不常驻；
 *  - 本服务每隔 [UPDATE_INTERVAL_MS] 读取今日课程数据，实时计算当前状态
 *    （正在上课 / 下一节课 / 今日无课 / 已结束）并以 ongoing 通知呈现；
 *  - 每次刷新额外做窗口兜底检查：若当前已滑出显示窗口（闹钟延迟 / 跨天等），
 *    立即自停并移除通知，避免「剩 -2 分钟」之类的错乱显示。
 *
 * Android 16（API 36）及以上通过 [NotificationCompat.Builder.setRequestPromotedOngoing]
 * 请求 Promoted Ongoing（Live Updates 状态栏胶囊），并用 [NotificationCompat.Builder.setShortCriticalText]
 * 设置胶囊短文本；进度采用官方「以进度为中心的通知」样式 [NotificationCompat.ProgressStyle]
 * （按官方文档 3.3 节与 Demo：setProgress + setProgressSegments 课程分段 + setProgressTrackerIcon +
 * setStyledByProgress，进度为天级刻度=已完成节数×100+课内百分比）。
 * 胶囊呈现条件（官方 Live Updates）：ongoing + ProgressStyle + 上述两项请求 + Manifest 声明
 * POST_PROMOTED_NOTIFICATIONS 权限；Android 16 基础版默认不渲染胶囊（按普通常驻通知展示），
 * QPR1 起（vivo OriginOS 6 已集成到原子岛）正式生效。低版本降级为兼容的
 * [NotificationCompat.Builder.setProgress] 水平进度条。
 * 开启「兼容穿戴设备同步通知」时不请求提升（与课程提醒 compatWearableSync 语义一致）。
 */
class DynamicIslandService : Service(), KoinComponent {

    private val widgetRepository: WidgetRepository by inject()
    private val appSettingsRepository: AppSettingsRepository by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickerJob: Job? = null

    /** 最近一次真实投递的通知：重复 onStartCommand 时复用它进入前台，避免占位内容来回覆盖（v3.57.1）。
     *  ticker 在 IO 线程写入、onStartCommand 在主线程读取，故用 @Volatile 保证可见性。 */
    @Volatile
    private var lastNotification: Notification? = null

    /** 最近一次投递的通知内容签名；内容未变化时不再重复 notify（v3.57.1）。 */
    private var lastStateSignature: String? = null

    /** 最近一次投递通知的时刻（毫秒），用于内容长期不变时的保活重投。 */
    private var lastNotifyWallMillis = 0L

    companion object {
        private const val TAG = "DynamicIslandService"

        /** 灵动岛通知专用频道（v2：IMPORTANCE_DEFAULT，状态栏常驻可见；旧 v1 为 LOW 无法升级故换新 ID） */
        const val NOTIFICATION_CHANNEL_ID = "dynamic_island_v2_channel"

        /** 灵动岛通知 ID */
        const val NOTIFICATION_ID = 20240904

        /** 刷新间隔：课程粒度到分钟，20s 即可让进度条/倒计时顺滑变化 */
        private const val UPDATE_INTERVAL_MS = 20_000L

        /**
         * 实时更新保活上限（v3.57.1）：内容长时间不变（如整节课的进度百分比取整后相同）时，
         * 也至少每 5 分钟重投一次，避免系统判定 Live Updates 长时间未更新而收回胶囊。
         * 内容真正变化时（倒计时/进度按分钟变化）照常立即刷新。
         */
        private const val KEEP_ALIVE_REFRESH_MS = 5 * 60_000L

        /** ProgressStyle 分段长度（每节课等长段） */
        private const val SEGMENT_LENGTH = 100

        /**
         * 服务是否正在运行（v3.57.1）。
         *
         * 仅用于同进程的 [DynamicIslandAlarmReceiver] 做「窗口开始闹钟重复投递」兜底判断，
         * 不是业务状态：进程被杀后随进程重置，[onDestroy] 时归位。
         */
        @Volatile
        var isRunning: Boolean = false

        /** 已完成课程分段的颜色（绿色，完成感） */
        private val SEGMENT_DONE_COLOR = Color.rgb(76, 175, 80)

        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
        private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE

        /** 启动前台服务 */
        fun start(context: Context) {
            val intent = Intent(context, DynamicIslandService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 停止前台服务并移除通知 */
        fun stop(context: Context) {
            context.stopService(Intent(context, DynamicIslandService::class.java))
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        // v3.57.1：重复 start（同步完成 / 窗口闹钟兜底 / 系统重启）不再投占位通知。
        // 旧实现每次 onStartCommand 都先 startForeground 一条「应用名」占位通知——它会覆盖真实
        // 课程内容、并丢掉 Live Updates 的提升请求（胶囊先消失再出现），是「灵动岛每隔几秒弹一次」
        // 的直接可见来源。已有真实通知时直接复用同一对象进前台：内容零抖动，且照样满足
        // startForegroundService 必须在 onStartCommand 内进入前台的要求。
        val existing = lastNotification
        if (existing != null) {
            startForegroundCompat(existing)
        } else {
            startForegroundWithPlaceholder()
        }
        startTicker()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithPlaceholder() {
        val placeholder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        lastNotification = placeholder
        startForegroundCompat(placeholder)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startTicker() {
        // v3.57.1：循环已在运行就直接复用，不再 cancel + 重启。
        // 旧实现每次 onStartCommand 都重启循环，而新循环会立刻重算并（无脑）再投递一次通知，
        // 于是每多一次 start 就多一次「灵动岛弹出」。
        if (tickerJob?.isActive == true) return
        tickerJob = serviceScope.launch {
            while (isActive) {
                try {
                    val settings = appSettingsRepository.getAppSettingsOnce()
                    // 开关已被关闭（例如在其它入口关闭）→ 自停并移除通知
                    if (!settings.dynamicIslandEnabled) {
                        Log.d(TAG, "灵动岛开关已关闭，服务自停")
                        stopSelf()
                        cancelNotification()
                        break
                    }

                    val now = LocalDateTime.now()
                    val courses = loadTodayCourses()

                    // 读库失败（null）与「今日确实无课」（emptyList）必须区分：
                    // 前者若按无课处理会 self-stop，且窗口内的 START 闹钟已按 v3.57.1 新策略不再补排，
                    // 灵动岛就会静默消失到下一次同步；这里保留服务、下一轮重试。
                    if (courses == null) {
                        Log.w(TAG, "读取今日课程失败，保留灵动岛服务并等待下一轮重试")
                    } else {
                        // 窗口兜底检查：已滑出「第一节课开始-lead → 最后一节课结束」则自停
                        val window = DynamicIslandManager.computeWindow(
                            courses,
                            settings.remindBeforeMinutes,
                            now.toLocalDate()
                        )
                        val nowMillis = System.currentTimeMillis()
                        if (window == null || nowMillis !in window.startMillis until window.endMillis) {
                            Log.d(TAG, "当前不在灵动岛显示窗口内，服务自停")
                            stopSelf()
                            cancelNotification()
                            break
                        }

                        val state = computeState(courses, now)
                        val requestPromoted = !settings.compatWearableSync
                        val signature = stateSignature(state, requestPromoted)
                        // 内容去重（v3.57.1）：倒计时/进度按分钟变化，固定 20s 无脑重投会让状态栏
                        // 实时胶囊被系统反复重新渲染（观感即「隔几秒又弹一次」）；仅在内容变化或
                        // 超过保活上限时投递。
                        if (signature != lastStateSignature ||
                            nowMillis - lastNotifyWallMillis >= KEEP_ALIVE_REFRESH_MS
                        ) {
                            val notification = buildNotification(
                                state = state,
                                requestPromoted = requestPromoted
                            )
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            nm?.notify(NOTIFICATION_ID, notification)
                            lastStateSignature = signature
                            lastNotifyWallMillis = nowMillis
                            lastNotification = notification
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "更新灵动岛通知失败", e)
                }
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * 通知内容签名（v3.57.1）：只取真正影响通知呈现的标量字段。
     * 不直接用 [IslandState.toString]——其中的 ProgressStyle.Segment 未重写 toString，
     * 会带上对象身份哈希，导致签名每次都不同、去重失效。
     */
    private fun stateSignature(state: IslandState, requestPromoted: Boolean): String = listOf(
        requestPromoted.toString(),
        state.title,
        state.text,
        state.subText,
        state.chipText,
        state.progress.toString(),
        state.dayProgress.toString(),
        (state.segments?.size ?: 0).toString()
    ).joinToString("\u0001")

    private fun cancelNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID)
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        // IMPORTANCE_DEFAULT：让「进行中」的灵动岛通知在状态栏常驻图标（LOW 在多数厂商 ROM 上不显示状态栏图标）；
        // 同时显式静音、无震动、无横幅，避免打扰——它只是常驻状态展示。
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.dynamic_island_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.dynamic_island_channel_desc)
            setShowBadge(false)
            setSound(null, null)
            setVibrationPattern(null)
            enableVibration(false)
            setBypassDnd(false)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * 读取今日课程；读库失败返回 null，与「今日确实无课」的 emptyList 区分
     * （ticker 对前者保留服务重试，对后者按窗口外自停）。
     */
    private suspend fun loadTodayCourses(): List<WidgetCourse>? {
        val today = LocalDate.now().format(ISO_DATE)
        return runCatching {
            widgetRepository.getWidgetCoursesByDateRange(today, today).first()
        }.getOrNull()
    }

    // ---------------------------------------------------------------------
    // 状态计算
    // ---------------------------------------------------------------------

    private data class IslandState(
        val title: String,
        val text: String,
        val subText: String,
        val chipText: String,
        /** -1 表示不显示进度，0..100 表示当前节课内进度百分比（低版本普通水平进度条用） */
        val progress: Int,
        /**
         * ProgressStyle 天级刻度进度：相对分段总长（= 今日节数 × [SEGMENT_LENGTH]），
         * 取值 = 已完成节数 × 100 + 进行中课内百分比。
         * 官方语义中 setProgress 相对全部分段长度之和，直接用课内百分比
         * 会让进度点在多节课的日子只走到整条 1/N 处。
         */
        val dayProgress: Int = 0,
        /**
         * 今日课程分段（Android 16 以进度为中心的通知 NotificationCompat.ProgressStyle）。
         * 每个已排课程一个 segment：已完成=绿色、进行中=课程色、未开始=灰色。
         * 为 null 表示不渲染分段（今日无课 / 已结束）。
         */
        val segments: List<NotificationCompat.ProgressStyle.Segment>? = null
    )

    private fun computeState(courses: List<WidgetCourse>, now: LocalDateTime): IslandState {
        val filtered = courses
            .filter { !it.isSkipped }
            .filter { it.startTime.isNotBlank() && it.endTime.isNotBlank() }
            .sortedWith(compareBy({ it.startTime }, { it.endTime }))

        val currentTime = now.toLocalTime()

        if (filtered.isEmpty()) {
            return IslandState(
                title = getString(R.string.dynamic_island_no_class_title),
                text = getString(R.string.dynamic_island_no_class_text),
                subText = getString(R.string.dynamic_island_no_class_sub),
                chipText = getString(R.string.dynamic_island_chip_no_class),
                progress = -1,
                segments = null
            )
        }

        // 1) 正在上课
        val active = filtered.firstOrNull { course ->
            val s = DynamicIslandManager.parseTime(course.startTime)
            val e = DynamicIslandManager.parseTime(course.endTime)
            s != null && e != null && !currentTime.isBefore(s) && currentTime.isBefore(e)
        }
        if (active != null) {
            val s = DynamicIslandManager.parseTime(active.startTime)!!
            val e = DynamicIslandManager.parseTime(active.endTime)!!
            val totalMin = Duration.between(s, e).toMinutes().coerceAtLeast(1)
            val elapsedMin = Duration.between(s, currentTime).toMinutes().coerceAtLeast(0)
            val pct = ((elapsedMin * 100) / totalMin).toInt().coerceIn(0, 100)
            val remainingMin = (totalMin - elapsedMin).coerceAtLeast(0)
            val doneCount = countDoneCourses(filtered, currentTime)
            return IslandState(
                title = active.name,
                text = getString(
                    R.string.dynamic_island_in_class_text,
                    active.position.ifBlank { getString(R.string.notification_unknown_position) }
                ),
                subText = getString(R.string.dynamic_island_in_class_sub, remainingMin),
                chipText = getString(
                    R.string.dynamic_island_chip_in_class_pct,
                    shortName(active.name),
                    pct
                ),
                progress = pct,
                dayProgress = doneCount * SEGMENT_LENGTH + pct,
                segments = buildSegments(filtered, currentTime)
            )
        }

        // 2) 下一节课
        val next = filtered.firstOrNull { course ->
            val s = DynamicIslandManager.parseTime(course.startTime)
            s != null && currentTime.isBefore(s)
        }
        if (next != null) {
            val s = DynamicIslandManager.parseTime(next.startTime)!!
            val minutesUntil = Duration.between(currentTime, s).toMinutes().coerceAtLeast(0)
            return IslandState(
                title = getString(R.string.dynamic_island_next_class_title),
                text = getString(
                    R.string.dynamic_island_next_class_text,
                    next.name,
                    next.position.ifBlank { getString(R.string.notification_unknown_position) }
                ),
                subText = getString(
                    R.string.dynamic_island_next_class_sub,
                    getString(R.string.dynamic_island_minutes_format, minutesUntil),
                    s.format(TIME_FORMATTER)
                ),
                chipText = getString(R.string.dynamic_island_chip_next, shortName(next.name)),
                progress = 0,
                dayProgress = countDoneCourses(filtered, currentTime) * SEGMENT_LENGTH,
                segments = buildSegments(filtered, currentTime)
            )
        }

        // 3) 今日课程已结束
        return IslandState(
            title = getString(R.string.dynamic_island_all_done_title),
            text = getString(R.string.dynamic_island_all_done_text, filtered.size),
            subText = getString(R.string.dynamic_island_all_done_sub),
            chipText = getString(R.string.dynamic_island_chip_done),
            progress = -1,
            segments = null
        )
    }

    /** 今日已上完（结束时间 ≤ 当前）的课程数，用于 ProgressStyle 天级刻度定位。 */
    private fun countDoneCourses(courses: List<WidgetCourse>, now: LocalTime): Int {
        return courses.count { course ->
            val e = DynamicIslandManager.parseTime(course.endTime)
            e != null && !now.isBefore(e)
        }
    }

    /**
     * 构建 Android 16 以进度为中心通知的分段：
     * 今天每节已排课程一个 segment，已完成（绿）→ 进行中（课程色）→ 未开始（灰），
     * 直观呈现「一天的课程序列」。
     */
    private fun buildSegments(
        courses: List<WidgetCourse>,
        now: LocalTime
    ): List<NotificationCompat.ProgressStyle.Segment> {
        return courses.map { course ->
            val s = DynamicIslandManager.parseTime(course.startTime)
            val e = DynamicIslandManager.parseTime(course.endTime)
            val color = when {
                s == null || e == null -> Color.GRAY
                !now.isBefore(e) -> SEGMENT_DONE_COLOR
                !now.isBefore(s) -> courseColor(course.colorInt)
                else -> Color.GRAY
            }
            NotificationCompat.ProgressStyle.Segment(SEGMENT_LENGTH).setColor(color)
        }
    }

    private fun courseColor(colorInt: Int): Int = when (colorInt % 6) {
        0 -> Color.rgb(156, 39, 176) // 紫
        1 -> Color.rgb(244, 67, 54)  // 红
        2 -> Color.rgb(255, 152, 0)  // 橙
        3 -> Color.rgb(0, 150, 136)  // 青
        4 -> Color.rgb(33, 150, 243) // 蓝
        else -> Color.rgb(255, 193, 7) // 黄
    }

    /** 状态栏芯片文本尽量短：课程名截断 */
    private fun shortName(name: String): String = if (name.length > 6) name.take(6) else name

    // ---------------------------------------------------------------------
    // 通知构建
    // ---------------------------------------------------------------------

    private fun buildNotification(state: IslandState, requestPromoted: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 统一使用 NotificationCompat 构建，保证跨版本安全：
        // setRequestPromotedOngoing / setShortCriticalText 仅存在于 NotificationCompat，
        // 平台 Notification.Builder 没有这两个方法（真机曾因误用平台 Builder 而 NoSuchMethodError）。
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(state.title)
            .setContentText(state.text)
            .setSubText(state.subText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setShowWhen(false)
            .setColor(getColor(R.color.purple_500))

        // Android 16 实时更新（灵动岛）：请求状态栏实时芯片 + 芯片短文本
        if (requestPromoted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(state.chipText)
        }

        // Android 16 以进度为中心的通知（Live Updates 的官方样式）：
        // 用 NotificationCompat.ProgressStyle 渲染「课程分段 + 进度」，替代旧的水平进度条 setProgress。
        // 与官方文档/Demo 对齐：setProgress + setProgressSegments + setProgressTrackerIcon + setStyledByProgress。
        // setProgress 用天级刻度（相对分段总长），进度点才会落在「当前正在上的课」的分段上。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && state.segments != null) {
            val style = NotificationCompat.ProgressStyle()
                .setProgress(state.dayProgress.coerceAtLeast(0))
                .setProgressSegments(state.segments)
                // false：保留分段自身配色（绿=已完成 / 课程色=进行中 / 灰=未开始）；
                // true 会把进度点之前的部分统一染成强调色，覆盖掉「已完成=绿色」的设计
                .setStyledByProgress(false)
                .setProgressTrackerIcon(IconCompat.createWithResource(this, R.drawable.ic_notification))
            builder.setStyle(style)
        } else if (state.progress >= 0) {
            // 低版本降级：普通水平进度条（仍用课内百分比）
            builder.setProgress(100, state.progress, false)
        }
        return builder.build()
    }
}
