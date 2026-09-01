package com.shangkeschedule.data.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** 兜底轮询间隔：覆盖系统时间被手动调整、时区变化等午夜唤醒无法覆盖的场景。 */
private const val FALLBACK_POLL_MS = 15 * 60 * 1000L

/**
 * 当前日期流：发射当前系统时区的 [LocalDate]，跨天（午夜）或日期变化时重新发射。
 *
 * - 主唤醒点：下一次午夜 + 1s，精准跨天；
 * - 兜底轮询：单次延迟最长 15 分钟，避免时区/系统时间调整导致长期不刷新；
 * - distinctUntilChanged：同一日期的重复检查不会向下游传播；
 * - 冷流：每个订阅者独立运行。订阅方均为长生命周期数据流（今日课表 / 周课表 / 作息解析），数量有限，
 *   每个订阅者仅在做日期比较时唤醒，开销可忽略。
 */
fun currentDateFlow(): Flow<LocalDate> = flow {
    while (true) {
        val zone = TimeZone.currentSystemDefault()
        val now: Instant = Clock.System.now()
        val today: LocalDate = now.toLocalDateTime(zone).date
        emit(today)

        val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        val untilMidnightMs = (nextMidnight - now).inWholeMilliseconds + 1_000
        delay(untilMidnightMs.coerceAtMost(FALLBACK_POLL_MS))
    }
}.distinctUntilChanged()

/** 每分钟发射当前本地时间，用于实时刷新 24H 时间轴的当前小时。 */
fun currentTimeFlow(): Flow<LocalTime> = flow {
    while (true) {
        val zone = TimeZone.currentSystemDefault()
        emit(Clock.System.now().toLocalDateTime(zone).time)
        delay(60_000L)
    }
}
