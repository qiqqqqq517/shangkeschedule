package com.shangkeschedule.notification.morning

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.shangkeschedule.data.model.MorningAlarmWeeklyTimes
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.MorningAlarmMode
import com.shangkeschedule.notification.plan.MorningAlarmHybridPlan
import com.shangkeschedule.notification.plan.MorningAlarmPlan
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class WeeklyAlarmConfirmationStore internal constructor(private val directory: File) {
    constructor(context: Context) : this(context.noBackupFilesDir)

    private val file get() = File(directory, "morning_weekly_confirmation.txt")
    private val reviewMarker get() = File(directory, "morning_weekly_review_pending")

    /** 核对中或存储不可读时，不得把闹钟状态视为已确认。 */
    fun requiresReview(): Boolean = synchronized(lock) {
        !directory.isDirectory || !Files.notExists(reviewMarker.toPath())
    }

    /** 跳转系统时钟前持久化屏障；取消或进程退出均保留。 */
    fun beginReview(): Boolean = synchronized(lock) {
        runCatching {
            if (!directory.exists()) check(directory.mkdirs())
            check(directory.isDirectory)
            if (!reviewMarker.exists()) Files.createFile(reviewMarker.toPath())
            check(reviewMarker.isFile)
            true
        }.getOrDefault(false)
    }

    /** 所有本地状态写入均成功后才解除屏障；失败和异常留待用户核对恢复。 */
    fun completeReview(action: () -> Boolean): Boolean = synchronized(lock) {
        if (!beginReview()) return@synchronized false
        runCatching {
            if (!action()) return@synchronized false
            Files.delete(reviewMarker.toPath())
            true
        }.getOrDefault(false)
    }

    /**
     * [completeReview] 的挂起版本：屏障语义完全相同，只是 [action] 可以是挂起函数。
     *
     * ## 为什么需要它
     *
     * 周核对弹窗的保存链要「先建屏障 → 保存设置（挂起，走 Room）→ 成功才解除屏障」。
     * 旧实现在 `completeReview` 的同步 lambda 里用 `runBlocking { ... }` 包住那次挂起调用：
     *  - 它会新开一个事件循环，把 Room 的挂起写入压在同一次同步临界区里；
     *  - `runBlocking` 不响应外部取消，用户中途退出页面时无法及时中断；
     *  - 一旦 Room 抛异常，`runBlocking` 的传播路径与 `runCatching` 叠加后语义变得难读。
     *
     * ## 为什么不能简单地把 synchronized 包在 suspend 外面
     *
     * `synchronized` 的监视器是**线程级**的，而挂起函数可能在恢复时跑到**另一个线程**上：
     * 跨挂起点持有监视器会造成「原线程持锁、协程在新线程继续」，退出临界区时抛
     * `IllegalMonitorStateException`，同时其他线程被无谓阻塞。故这里把三段拆开——
     * 建屏障、执行、解除屏障，**只在前后两段**进出锁，中间那段不持锁。
     *
     * 屏障期间的正确性由**屏障文件的存在性**保证，而不是由监视器保证：
     * 并发的调度入口读 [requiresReview] 会看到屏障文件从而拒绝写入系统时钟。
     * 这与同步版 [completeReview] 的对外可观测行为一致。
     */
    suspend fun completeReviewSuspend(action: suspend () -> Boolean): Boolean {
        // 建屏障与解除屏障各自短暂持「调度入口锁 + 存储锁」；中间的挂起段不持任何锁。
        // 挂起期间调度入口被**屏障文件**挡住（requiresReview 见到标记即拒绝写入系统时钟），
        // 这与同步版对外可观测行为一致，且不会跨挂起点持有线程级监视器。
        if (!MorningAlarmSyncGate.run { beginReview() }) return false
        return try {
            if (!action()) return false
            MorningAlarmSyncGate.run {
                Files.delete(reviewMarker.toPath())
                true
            }
        } catch (cancel: CancellationException) {
            // 取消也要保留屏障：本轮没有完成核对，交给下次前台时机重新核对。
            throw cancel
        } catch (_: Exception) {
            false
        }
    }

    private fun encoded(revision: String, weeklyTimes: Map<Int, String>): String? {
        if (!revision.matches(Regex("[A-Za-z0-9-]{1,80}"))) return null
        val normalized = MorningAlarmWeeklyTimes.normalize(weeklyTimes)
        if (normalized.isEmpty() || normalized != weeklyTimes) return null
        return revision + "\n" + MorningAlarmWeeklyTimes.encode(normalized).sorted().joinToString("\n")
    }

    fun confirm(revision: String, weeklyTimes: Map<Int, String>): Boolean = synchronized(lock) {
        val payload = encoded(revision, weeklyTimes) ?: return@synchronized false
        runCatching {
            if (!directory.exists()) check(directory.mkdirs())
            val temporary = File.createTempFile("weekly-", ".tmp", directory)
            try {
                temporary.writeText(payload, Charsets.UTF_8)
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
            true
        }.getOrDefault(false)
    }

    fun confirmedFor(revision: String, weeklyTimes: Map<Int, String>): Map<DayOfWeek, LocalTime> = synchronized(lock) {
        val expected = encoded(revision, weeklyTimes) ?: return@synchronized emptyMap()
        if (runCatching { file.readText(Charsets.UTF_8) }.getOrNull() != expected) return@synchronized emptyMap()
        weeklyTimes.mapKeys { (day, _) -> DayOfWeek.entries.first { it.isoDayNumber == day } }
            .mapValues { (_, time) -> LocalTime.parse(time) }
    }

    fun clear(): Boolean = synchronized(lock) { runCatching { Files.deleteIfExists(file.toPath()); true }.getOrDefault(false) }

    private companion object { val lock = Any() }
}

/** 系统默认时钟更换后不能沿用此前对另一时钟应用的确认。 */
fun weeklyConfirmationRevision(context: Context, revision: String): String? = runCatching {
    val activity = context.packageManager.resolveActivity(Intent(AlarmClock.ACTION_SET_ALARM), 0) ?: return@runCatching null
    val packageName = activity.activityInfo.packageName
    // 解析到系统选择器时，无法知道用户选择了哪个时钟，不能记为已核对。
    if (packageName == "android" || packageName == "com.android.intentresolver" ||
        activity.activityInfo.name.contains("ResolverActivity")) return@runCatching null
    val digest = MessageDigest.getInstance("SHA-256").digest(packageName.toByteArray(Charsets.UTF_8))
        .take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    "$revision-$digest"
}.getOrNull()

/** 未经本机核对的星期仍走逐日路径，避免恢复备份后漏响。 */
fun pendingMorningAlarms(
    context: Context,
    settings: AppSettingsModel,
    plan: List<MorningAlarmPlan.MorningAlarm>
): List<MorningAlarmPlan.MorningAlarm> {
    if (settings.morningAlarmMode != MorningAlarmMode.HYBRID_WEEKLY) return plan
    // 时钟应用不可用时，之前的确认不能保证仍有系统周闹钟；保留逐日降级路径。
    val revision = weeklyConfirmationRevision(context, settings.morningAlarmWeeklyRevision) ?: return plan
    val covered = WeeklyAlarmConfirmationStore(context)
        .confirmedFor(revision, settings.morningAlarmWeeklyTimes)
    return MorningAlarmHybridPlan.partition(plan, covered).oneOff
}
