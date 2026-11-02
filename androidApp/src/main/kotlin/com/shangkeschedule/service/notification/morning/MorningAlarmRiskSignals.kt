package com.shangkeschedule.service.notification.morning

import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.MorningAlarmMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private data class RiskSelection(
    val enabled: Boolean,
    val mode: MorningAlarmMode,
    val tableId: String,
    val skippedDates: Set<String>,
    val lead: Int,
    val weeklyTimes: Map<Int, String>,
    val revision: String
)

/** 跟随当前课表订阅完整数据，不能依赖仅保留未来七天的小组件快照。 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <C, F, M, S> morningAlarmRiskSignals(
    settings: Flow<AppSettingsModel>, courses: (String) -> Flow<C>, config: (String) -> Flow<F>,
    schemes: (String) -> Flow<M>, schemeIds: (String) -> Flow<List<String>>, slots: (String, String) -> Flow<S>
): Flow<Unit> = settings.map {
    RiskSelection(it.morningAlarmEnabled, it.morningAlarmMode, it.currentCourseTableId,
        it.skippedDates, it.morningAlarmLeadMinutes, it.morningAlarmWeeklyTimes, it.morningAlarmWeeklyRevision)
}.distinctUntilChanged().flatMapLatest { selected ->
    val id = selected.tableId
    if (!selected.enabled || selected.mode != MorningAlarmMode.HYBRID_WEEKLY) return@flatMapLatest emptyFlow()
    // 已存在的系统周闹钟不会因课表被删除而自动消失。
    if (id.isBlank()) return@flatMapLatest flowOf(Unit)
    val allSlots = schemeIds(id).distinctUntilChanged().flatMapLatest { ids ->
        val flows = ids.distinct().sorted().map { scheme -> slots(id, scheme).distinctUntilChanged().map { Unit } }
        if (flows.isEmpty()) flowOf(Unit) else combine(flows) { Unit }
    }
    combine(
        courses(id).distinctUntilChanged().map { Unit },
        config(id).distinctUntilChanged().map { Unit },
        schemes(id).distinctUntilChanged().map { Unit },
        allSlots
    ) { _, _, _, _ -> Unit }
}
