package com.shangkeschedule.service.notification.morning

import android.content.Context
import android.util.Log
import com.shangkeschedule.data.model.MorningAlarmMode
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import com.shangkeschedule.data.repository.MorningAlarmSemesterRepository
import com.shangkeschedule.data.repository.TimeSlotRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.Clock

/** 进程存活时观察完整课表，仅复核每周闹钟风险，不重排其它通知。 */
@OptIn(FlowPreview::class)
@Single(createdAtStart = true)
internal class MorningAlarmRiskObserver(
    context: Context,
    private val settings: AppSettingsRepository,
    courses: CourseTableRepository,
    timeSlots: TimeSlotRepository,
    semesters: MorningAlarmSemesterRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val coordinator = MorningAlarmCoordinator(context.applicationContext, settings, semesters)

    init {
        scope.launch {
            while (isActive) {
                try {
                    morningAlarmRiskSignals(
                        settings.getAppSettings(),
                        courses::getCoursesWithWeeksByTableId,
                        settings::getCourseTableConfigFlow,
                        timeSlots::getSchemeMetasByCourseTableId,
                        timeSlots::getSchemeIdsByCourseTableId,
                        timeSlots::getTimeSlotsByCourseTableId
                    ).debounce(500L).collect {
                        val current = settings.getAppSettingsOnce()
                        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                        coordinator.inspectRisk(current, now)
                    }
                    break
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    Log.w(TAG, "早八闹钟风险观察失败，将重试", error)
                    try {
                        val current = settings.getAppSettingsOnce()
                        if (current.morningAlarmEnabled && current.morningAlarmMode == MorningAlarmMode.HYBRID_WEEKLY) {
                            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                            coordinator.inspectRisk(current, now, sourceUnavailable = true)
                        }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (noticeError: Exception) {
                        Log.w(TAG, "无法记录早八闹钟核对状态", noticeError)
                    }
                    delay(10_000L)
                }
            }
        }
    }

    private companion object { const val TAG = "MorningAlarmRiskObserver" }
}
