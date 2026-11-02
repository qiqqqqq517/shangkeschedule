package com.shangkeschedule.service.notification.morning

import android.content.Context
import android.util.Log
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.MorningAlarmMode
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.MorningAlarmSemesterRepository
import com.shangkeschedule.notification.morning.MorningAlarmSyncGate
import com.shangkeschedule.notification.morning.WeeklyAlarmConfirmationStore
import com.shangkeschedule.notification.morning.weeklyConfirmationRevision
import com.shangkeschedule.notification.plan.MorningAlarmSemesterProjection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime

/** 共同守住前台补写和后台重排：核对期间不写系统时钟，也不排应用内兜底。 */
internal class MorningAlarmCoordinator(
    private val context: Context,
    private val settingsRepository: AppSettingsRepository,
    private val semesterRepository: MorningAlarmSemesterRepository
) {
    sealed interface Outcome<out T> {
        data class Executed<T>(val value: T) : Outcome<T>
        data object ReviewRequired : Outcome<Nothing>
        data object Stale : Outcome<Nothing>
    }

    suspend fun <T> run(
        settings: AppSettingsModel,
        now: LocalDateTime,
        action: (AppSettingsModel) -> T
    ): Outcome<T> = evaluate(settings, now, false, true, action)

    /** 仅检查风险，不改写系统闹钟或清除其他调度状态。 */
    suspend fun inspectRisk(settings: AppSettingsModel, now: LocalDateTime, sourceUnavailable: Boolean = false): Outcome<Unit> =
        evaluate(settings, now, sourceUnavailable, false) { }

    private suspend fun <T> evaluate(
        settings: AppSettingsModel,
        now: LocalDateTime,
        sourceUnavailable: Boolean,
        clearResolvedNotice: Boolean,
        action: (AppSettingsModel) -> T
    ): Outcome<T> = withContext(Dispatchers.IO) {
        val projection = if (!sourceUnavailable && settings.morningAlarmEnabled && settings.morningAlarmMode == MorningAlarmMode.HYBRID_WEEKLY) {
            semesterRepository.read(now.date, settings.morningAlarmLeadMinutes)
        } else MorningAlarmSemesterProjection.Result.incomplete()
        val store = WeeklyAlarmConfirmationStore(context)
        val outcome: Outcome<T> = MorningAlarmSyncGate.run locked@{
            // 不能只检查 marker：一轮完整核对可能在投影读取期间开始并结束。
            val current = try {
                runBlocking { settingsRepository.getAppSettingsOnce() }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Log.w(TAG, "无法复核早八设置，本轮跳过", error)
                return@locked Outcome.Stale
            }
            if (!MorningAlarmReviewDecision.sameSettings(settings, current)) return@locked Outcome.Stale
            if (!current.morningAlarmEnabled) return@locked Outcome.Executed(action(current))
            if (store.requiresReview()) return@locked Outcome.ReviewRequired
            val revision = weeklyConfirmationRevision(context, current.morningAlarmWeeklyRevision)
            val confirmed = revision?.let { store.confirmedFor(it, current.morningAlarmWeeklyTimes) }.orEmpty()
            if (MorningAlarmReviewDecision.requiresReview(current, confirmed, projection)) {
                if (!store.beginReview()) Log.e(TAG, "无法持久化早八核对标记，本轮仍暂停写入")
                return@locked Outcome.ReviewRequired
            }
            Outcome.Executed(action(current))
        }
        when (outcome) {
            Outcome.ReviewRequired -> MorningAlarmReviewNotifier.post(context)
            is Outcome.Executed -> if (store.requiresReview()) MorningAlarmReviewNotifier.post(context)
                else if (clearResolvedNotice) MorningAlarmReviewNotifier.cancel(context) else Unit
            Outcome.Stale -> Unit
        }
        outcome
    }

    private companion object { const val TAG = "MorningAlarmCoordinator" }
}
