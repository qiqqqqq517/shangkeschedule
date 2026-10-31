package com.shangkeschedule.notification.morning

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.shangkeschedule.data.model.MorningAlarmWeeklyTimes
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.MorningAlarmMode
import com.shangkeschedule.notification.plan.MorningAlarmHybridPlan
import com.shangkeschedule.notification.plan.MorningAlarmPlan
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
