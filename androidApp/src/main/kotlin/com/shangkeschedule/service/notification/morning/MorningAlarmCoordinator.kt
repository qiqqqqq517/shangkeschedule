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

/**
 * 共同守住前台补写和后台重排：核对期间不写系统时钟，也不排应用内兜底。
 *
 * ## Outcome 三分（2026-11-06 重构）
 *
 * 旧版只有 `Executed` / `ReviewRequired` / `Stale` 三态，而 `Stale` 被
 * `NotificationScheduler` 当作「策略失败」计入 `failedStrategies`，进而触发
 * Worker 重试。但 `Stale` 的真实语义是「本轮开始时读到的设置已经变了，本轮作废、
 * 下一轮会用新设置重来」——这是**正常的并发作废**，不是失败。
 * 现在拆成 [Outcome.Stale]（静默作废，不计失败）与 [Outcome.Unavailable]
 * （读设置抛异常，本轮确实无法判断，计为失败并允许重试）。
 */
internal class MorningAlarmCoordinator(
    private val context: Context,
    private val settingsRepository: AppSettingsRepository,
    private val semesterRepository: MorningAlarmSemesterRepository
) {
    sealed interface Outcome<out T> {
        data class Executed<T>(val value: T) : Outcome<T>

        /** 核对尚未完成，本轮不得写入；已投递核对通知。 */
        data object ReviewRequired : Outcome<Nothing>

        /**
         * 本轮开始时传入的 [AppSettingsModel] 已与库中现状不一致，本轮**作废**。
         *
         * 不是失败：调用方应静默跳过，不得计入失败策略、不得触发重试——
         * 设置变更本身会再触发一轮排程。
         */
        data object Stale : Outcome<Nothing>

        /** 无法读到当前设置（读库异常），本轮**确实**无法判断，调用方可计失败并重试。 */
        data object Unavailable : Outcome<Nothing>
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
            //
            // 这里的 runBlocking **不是坏味道，不能删**：本块要同时满足两个约束——
            // ① `MorningAlarmSyncGate` 是 `synchronized`（线程级监视器），锁内不能挂起
            //   （跨挂起点恢复可能换线程，退出时抛 IllegalMonitorStateException）；
            // ② 同一把锁又被 `Writer.sync` / 排程入口共享，换成 Mutex 会与 synchronized
            //   嵌套（Writer 里 `SyncGate.run` → 决策再进锁）造成自死锁。
            // 故在 IO 线程里用 runBlocking 桥接这一次冷读设置。它已是阻塞 IO 语境，
            // 阻塞成本可接受；外层 withContext(Dispatchers.IO) 保证不会占住主线程。
            // CancellationException 仍在此显式透传，取消语义不受影响。
            val current = try {
                runBlocking { settingsRepository.getAppSettingsOnce() }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Log.w(TAG, "无法复核早八设置，本轮跳过", error)
                return@locked Outcome.Unavailable
            }
            if (!MorningAlarmReviewDecision.sameSettings(settings, current)) return@locked Outcome.Stale
            if (!current.morningAlarmEnabled) {
                val result = action(current)
                updateReviewNotice(current.morningAlarmEnabled, store.requiresReview(), clearResolvedNotice)
                return@locked Outcome.Executed(result)
            }
            if (store.requiresReview()) {
                MorningAlarmReviewNotifier.post(context)
                return@locked Outcome.ReviewRequired
            }
            val revision = weeklyConfirmationRevision(context, current.morningAlarmWeeklyRevision)
            val confirmed = revision?.let { store.confirmedFor(it, current.morningAlarmWeeklyTimes) }.orEmpty()
            if (MorningAlarmReviewDecision.requiresReview(current, confirmed, projection)) {
                if (!store.beginReview()) Log.e(TAG, "无法持久化早八核对标记，本轮仍暂停写入")
                MorningAlarmReviewNotifier.post(context)
                return@locked Outcome.ReviewRequired
            }
            val result = action(current)
            updateReviewNotice(current.morningAlarmEnabled, store.requiresReview(), clearResolvedNotice)
            Outcome.Executed(result)
        }
        outcome
    }

    private fun updateReviewNotice(enabled: Boolean, pending: Boolean, clearResolved: Boolean) {
        when (MorningAlarmReviewDecision.notice(enabled, pending, clearResolved)) {
            MorningAlarmReviewDecision.Notice.POST -> MorningAlarmReviewNotifier.post(context)
            MorningAlarmReviewDecision.Notice.CANCEL -> MorningAlarmReviewNotifier.cancel(context)
            MorningAlarmReviewDecision.Notice.KEEP -> Unit
        }
    }

    private companion object { const val TAG = "MorningAlarmCoordinator" }
}
