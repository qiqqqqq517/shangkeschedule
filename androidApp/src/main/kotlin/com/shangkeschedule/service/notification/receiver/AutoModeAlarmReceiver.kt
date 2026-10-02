package com.shangkeschedule.service.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.notification.plan.ReminderEngine
import com.shangkeschedule.service.notification.control.AutoModeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

/**
 * 上课自动勿扰/静音闹钟接收器。
 *
 * 取代旧实现「借用课程提醒 action + `EXTRA_DND_ACTION` extras 挂在 `CourseAlarmReceiver`」的
 * 混淆做法（旧版槽位 50001/50002 与课程提醒 50010–50110 挤在同一区间，靠 extras 区分语义）。
 * 现在有**专属 action + 专属请求码命名空间**，两侧彻底解耦。
 *
 * ## 关闭前先自证（XL-001）
 *
 * END 闹钟到达时**不无条件恢复铃声**，而是用 [ReminderEngine.shouldModeBeOn] 以「此刻的
 * 课表 + 跳过日」重新判定一次是否仍在上课；仍在上课就跳过恢复。
 *
 * 为什么不用「落盘的活动会话登记表」：课表本身就是事实来源，
 * `shouldModeBeOn` 已是与排程共用的判定函数（`AutoModeScheduler.reconcileState` 用的是同一个）。
 * **多存一份派生状态就多一处会漂移的地方** —— 而漂移的代价是「本该静音时响了」或「该响时不响」。
 * 重算一次成本只是一次主键范围查询，且在 `Dispatchers.IO` 上，不在用户可感知的路径上。
 *
 * 这一层同时兜住三类异常，而不只是重叠课程：
 * 1. END 闹钟丢失/被系统改期后仍到达 → 以真实课表为准，不误恢复
 * 2. 上课中途用户改了课表（加了一节重叠的课）→ 以新课表为准，不误恢复
 * 3. 精确闹钟权限被撤销导致排程错乱 → 不因错乱而提前恢复
 *
 * 反之 START 侧不做同样判定：漏开会造成课上响铃，比误开（多静一段）严重得多，
 * 因此 START 保持「到点就开」，恢复责任交给 END。
 */
class AutoModeAlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val appSettingsRepository: AppSettingsRepository by inject()
    private val widgetRepository: WidgetRepository by inject()

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val enable = when (intent?.action) {
            ACTION_AUTO_MODE_START -> true
            ACTION_AUTO_MODE_END -> false
            else -> {
                Log.d(TAG, "忽略未知 action: ${intent?.action}")
                return
            }
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = appSettingsRepository.getAppSettingsOnce()
                if (!settings.autoModeEnabled) {
                    Log.d(TAG, "自动模式已关闭，忽略本次切换")
                    return@launch
                }
                if (!enable && stillInSession(settings)) {
                    Log.i(
                        TAG,
                        "此刻仍在上课（重叠或课表已变更），跳过恢复铃声；" +
                            "待最后一节课的 END 闹钟再恢复"
                    )
                    return@launch
                }
                AutoModeController.toggle(ctx, enable, settings.autoControlMode)
            } catch (e: Exception) {
                Log.e(TAG, "自动模式切换失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 此刻是否仍处于应静音的上课区间。
     *
     * 读库失败时**保守返回 false**（照常恢复）：宁可多响一次，也不要在数据库异常时
     * 把用户按在静音里出不来 —— 后者是「手机上课后一直没声音」这类不可自愈的故障。
     */
    private suspend fun stillInSession(settings: AppSettingsModel): Boolean {
        return try {
            val zone = TimeZone.currentSystemDefault()
            val now = Clock.System.now().toLocalDateTime(zone)
            val today = now.date
            val courses = widgetRepository.getWidgetCoursesByDateRange(
                today.toString(),
                today.plus(1, DateTimeUnit.DAY).toString()
            ).first()
            ReminderEngine.shouldModeBeOn(
                courses = courses,
                skippedDates = settings.skippedDates,
                date = today,
                time = now.time
            )
        } catch (e: Exception) {
            Log.w(TAG, "判定是否仍在上课失败，按「已下课」处理以免用户被困静音", e)
            false
        }
    }

    companion object {
        private const val TAG = "AutoModeAlarmReceiver"

        /** 进入勿扰/静音。 */
        const val ACTION_AUTO_MODE_START = "com.shangkeschedule.action.AUTO_MODE_START"

        /** 退出勿扰/静音。 */
        const val ACTION_AUTO_MODE_END = "com.shangkeschedule.action.AUTO_MODE_END"
    }
}
