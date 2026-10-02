package com.shangkeschedule.tool

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import androidx.core.graphics.toColorInt
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.TimeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.app_name
import shangkeschedule.shared.generated.resources.course_teacher_prefix

private const val TAG = "CalendarAccountManager"

/**
 * 课表 → 系统日历的写回。
 *
 * ## 增量而非全量重建（XL-010）
 *
 * 旧实现每次同步都先 `delete(整个日历)` 再批量 `insert`。代价有三：
 *
 * 1. 系统日历 App 会把整批事件视为「全部删除 + 全部新增」，用户在日历上给事件设的
 *    提醒 / 「稍后提示」被连带清除；
 * 2. 每次同步都产生全量事件 ID 变更。与第三方云日历同步时，「删除事件」会向云端
 *    广播「这些课被取消了」，本应用随后再重新插入，容易在云端留下一串删除记录；
 * 3. 事件数量大时批次很大，部分 OEM 日历 Provider 有事务超时风险。
 *
 * 现在改为**按稳定 UID 做差分**：新增 / 更新 / 删除分开下发，只动真正变化的那些。
 * UID 由 `courseId + weekNumber` 确定性生成（`sk:<courseId>:<weekNumber>`），不含随机量，
 * 因此同一门课在第 N 周的那一次课永远映射到同一个 UID。
 *
 * ## 写入后回读校验
 *
 * `applyBatch` 返回即认为成功是不够的 —— 部分 Provider 会在批次里静默丢事件。
 * 因此写完再查一次本日历下由本应用写入的事件数，与期望值比对，不一致即判失败并记日志。
 */
actual object CalendarAccountManager : KoinComponent {

    private const val ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL

    /**
     * `Events.uid` 列名。
     *
     * 为什么用字面量而不写 `CalendarContract.Events.UID`：该常量实际声明在
     * `CalendarContract.SyncColumns` 上，而这个接口在 SDK 里是 **protected**；
     * `Events` 只是 `implements` 它，Kotlin 不会把接口常量当作 `Events` 的成员解析。
     * 列名本身是 CalendarProvider 的公开契约（见 SDK 文档 Events 表），用具名常量
     * 固定可读、且避免拼错后静默返回空集。
     */
    private const val COL_UID = "uid"

    /** 本应用写入的事件在日历里的稳定标识前缀，用于把「自己的事件」与用户自建事件区分开。 */
    private const val UID_PREFIX = "sk:"

    // 通过 Koin 动态注入全局 Application Context
    private val context: Context by inject()

    private suspend fun getOrCreateCalendarId(): Long {
        val contentResolver = context.contentResolver
        val accountName = "${context.packageName}.account"

        val projection = arrayOf(Calendars._ID)
        val selection = "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(accountName, ACCOUNT_TYPE)

        val cursor: Cursor? = contentResolver.query(Calendars.CONTENT_URI, projection, selection, selectionArgs, null)
        cursor?.use {
            if (it.moveToFirst()) return it.getLong(0)
        }

        val calendarDisplayName = getString(Res.string.app_name)
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, accountName)
            put(Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(Calendars.NAME, accountName)
            put(Calendars.CALENDAR_DISPLAY_NAME, calendarDisplayName)
            put(Calendars.CALENDAR_COLOR, "#4285F4".toColorInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, accountName)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, java.util.TimeZone.getDefault().id)
            put(Calendars.CAN_ORGANIZER_RESPOND, 1)
        }

        val uri: Uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()

        return try {
            val resultUri = contentResolver.insert(uri, values)
            resultUri?.let { ContentUris.parseId(it) } ?: -1L
        } catch (e: Exception) {
            AppLog.e(TAG, "创建本地日历账户失败", e)
            -1L
        }
    }

    /** 一次课在目标日历里应有的全部内容。 */
    private class DesiredEvent(
        val uid: String,
        val title: String,
        val location: String,
        val description: String,
        val startMillis: Long,
        val endMillis: Long,
        val timeZoneId: String,
        val alarmMinutes: Int?
    ) {
        /**
         * 内容指纹：任意一项变化即需写回。
         *
         * 提醒分钟数**必须**在内 —— `CalendarContract.Reminders` 挂在 `EVENT_ID` 上，
         * 对事件行做 `update` 改不动已存在的提醒记录，所以提醒变化要走「删事件+重建」。
         * 因此读回时必须把 `Reminders.MINUTES` 一并查出，否则每次同步都会把全部
         * 带提醒的事件误判成「变了」而全量重写（那等于退回旧行为，只是更慢）。
         */
        val fingerprint: String
            get() = "$startMillis|$endMillis|$title|$location|$description|${alarmMinutes ?: NO_ALARM}"
    }

    /** 日历里已存在的一次课（按 UID 索引）。 */
    private class ExistingEvent(val eventId: Long, val fingerprint: String)

    /**
     * 读出本日历下已存在、由本应用写入的事件（按 UID 索引）。
     *
     * 分两次查询：先取事件行，再取提醒分钟数并按 EVENT_ID 归并。
     * 本应用用的是 `ACCOUNT_TYPE_LOCAL` 的本地日历，事件量与学期周数同阶，
     * 全表扫 Reminders 的开销可以忽略。
     */
    private fun readExisting(resolver: ContentResolver, calendarId: Long): Map<String, ExistingEvent> {
        val events = LinkedHashMap<Long, Array<String>>()   // eventId -> 指纹的字段数组
        val uidById = LinkedHashMap<Long, String>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            COL_UID,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION
        )
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${COL_UID} LIKE ?",
            arrayOf(calendarId.toString(), "$UID_PREFIX%"),
            null
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val uidIdx = c.getColumnIndexOrThrow(COL_UID)
            val startIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val locIdx = c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            val descIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            while (c.moveToNext()) {
                val uid = c.getString(uidIdx) ?: continue
                if (!uid.startsWith(UID_PREFIX)) continue
                val id = c.getLong(idIdx)
                uidById[id] = uid
                events[id] = arrayOf(
                    c.getLong(startIdx).toString(),
                    c.getLong(endIdx).toString(),
                    c.getString(titleIdx).orEmpty(),
                    c.getString(locIdx).orEmpty(),
                    c.getString(descIdx).orEmpty(),
                    NO_ALARM.toString()
                )
            }
        }
        if (events.isEmpty()) return emptyMap()

        // 归并提醒分钟数（同一事件可能有多条提醒，取第一条即可 —— 我们只会建一条）
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES),
            null, null, null
        )?.use { c ->
            val evIdx = c.getColumnIndexOrThrow(CalendarContract.Reminders.EVENT_ID)
            val minIdx = c.getColumnIndexOrThrow(CalendarContract.Reminders.MINUTES)
            while (c.moveToNext()) {
                val id = c.getLong(evIdx)
                val fields = events[id] ?: continue
                if (fields[5] == NO_ALARM.toString()) {
                    fields[5] = c.getInt(minIdx).toString()
                }
            }
        }

        return events.mapNotNull { (id, fields) ->
            val uid = uidById[id] ?: return@mapNotNull null
            uid to ExistingEvent(id, fields.joinToString("|"))
        }.toMap()
    }

    /** 回读校验：本日历下由本应用写入的事件条数。 */
    private fun verifyCount(resolver: ContentResolver, calendarId: Long): Int {
        val cursor: Cursor? = resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${COL_UID} LIKE ?",
            arrayOf(calendarId.toString(), "$UID_PREFIX%"),
            null
        )
        var count = 0
        cursor?.use { while (it.moveToNext()) count++ }
        return count
    }

    /**
     * 追加「插入事件 + 其提醒」两条操作。
     *
     * `Reminders.EVENT_ID` 用 [ContentProviderOperation.withValueBackReference] 指向
     * **本批次内**事件 insert 的下标，因此必须与事件 insert 相邻入队 —— 下标由入队时的
     * `ops.size` 决定，拆成两个互不相知的函数就会算错。
     */
    private fun appendInsert(
        ops: ArrayList<ContentProviderOperation>,
        calendarId: Long,
        e: DesiredEvent
    ) {
        val eventOpIndex = ops.size
        ops.add(
            ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
                .withValue(COL_UID, e.uid)
                .withValue(CalendarContract.Events.ORIGINAL_ID, e.uid)
                .withValue(CalendarContract.Events.TITLE, e.title)
                .withValue(CalendarContract.Events.EVENT_LOCATION, e.location)
                .withValue(CalendarContract.Events.DESCRIPTION, e.description)
                .withValue(CalendarContract.Events.DTSTART, e.startMillis)
                .withValue(CalendarContract.Events.DTEND, e.endMillis)
                .withValue(CalendarContract.Events.EVENT_TIMEZONE, e.timeZoneId)
                .withValue(CalendarContract.Events.HAS_ALARM, if (e.alarmMinutes != null) 1 else 0)
                .build()
        )
        if (e.alarmMinutes != null) {
            ops.add(
                ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                    .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventOpIndex)
                    .withValue(CalendarContract.Reminders.MINUTES, e.alarmMinutes)
                    .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    .build()
            )
        }
    }

    private fun deleteOp(eventId: Long): ContentProviderOperation =
        ContentProviderOperation.newDelete(
            CalendarContract.Events.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.Events._ID, eventId.toString())
                .build()
        ).build()

    private fun updateOp(eventId: Long, e: DesiredEvent): ContentProviderOperation =
        ContentProviderOperation.newUpdate(
            CalendarContract.Events.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.Events._ID, eventId.toString())
                .build()
        )
            .withValue(CalendarContract.Events.TITLE, e.title)
            .withValue(CalendarContract.Events.EVENT_LOCATION, e.location)
            .withValue(CalendarContract.Events.DESCRIPTION, e.description)
            .withValue(CalendarContract.Events.DTSTART, e.startMillis)
            .withValue(CalendarContract.Events.DTEND, e.endMillis)
            .withValue(CalendarContract.Events.EVENT_TIMEZONE, e.timeZoneId)
            .build()

    actual suspend fun syncCurrentTableToSystemCalendar(
        courses: List<CourseWithWeeks>,
        timeSlots: List<TimeSlot>,
        semesterStartDate: LocalDate,
        semesterTotalWeeks: Int,
        firstDayOfWeekInt: Int,
        alarmMinutes: Int?,
        skippedDates: Set<String>?
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val calendarId = getOrCreateCalendarId()
                if (calendarId == -1L) return@withContext false

                val resolver = context.contentResolver

                // 学期内没有课时：把本应用写入的事件清空即达成「日历与课表一致」。
                if (semesterTotalWeeks <= 0 || courses.isEmpty()) {
                    val stale = readExisting(resolver, calendarId)
                    if (stale.isNotEmpty()) {
                        val ops = ArrayList<ContentProviderOperation>(stale.size)
                        stale.values.forEach { ops.add(deleteOp(it.eventId)) }
                        resolver.applyBatch(CalendarContract.AUTHORITY, ops)
                        AppLog.i(TAG, "课表为空，已清理 ${stale.size} 条日历事件")
                    }
                    return@withContext true
                }

                val timeZone = TimeZone.currentSystemDefault()
                val effectiveAlarm = alarmMinutes?.takeIf { it in 0..60 }

                // ---- 1. 算出期望集合 ----
                val desired = LinkedHashMap<String, DesiredEvent>()
                IcsExportTool.processCourseInstances(
                    courses = courses,
                    timeSlots = timeSlots,
                    semesterStartDate = semesterStartDate,
                    semesterTotalWeeks = semesterTotalWeeks,
                    firstDayOfWeekInt = firstDayOfWeekInt,
                    skippedDates = skippedDates
                ) { course, start, end, weekNumber ->
                    val teacherDescription = if (course.teacher.isNotBlank()) {
                        getString(Res.string.course_teacher_prefix, course.teacher)
                    } else ""
                    val uid = "$UID_PREFIX${course.id}:$weekNumber"
                    desired[uid] = DesiredEvent(
                        uid = uid,
                        title = course.name,
                        location = course.position,
                        description = teacherDescription,
                        startMillis = start.toInstant(timeZone).toEpochMilliseconds(),
                        endMillis = end.toInstant(timeZone).toEpochMilliseconds(),
                        timeZoneId = timeZone.id,
                        alarmMinutes = effectiveAlarm
                    )
                }

                // ---- 2. 与现状做差分 ----
                val existing = readExisting(resolver, calendarId)
                val ops = ArrayList<ContentProviderOperation>()
                var inserted = 0
                var updated = 0
                var deleted = 0

                // 2a. 多余的（课被删 / 调到别的周 / 学期缩短）→ 删
                for ((uid, old) in existing) {
                    if (uid in desired) continue
                    ops.add(deleteOp(old.eventId))
                    deleted++
                }

                for ((uid, want) in desired) {
                    val old = existing[uid]
                    when {
                        old == null -> {
                            appendInsert(ops, calendarId, want)
                            inserted++
                        }

                        old.fingerprint == want.fingerprint -> Unit // 无变化，一个字节都不写

                        // 提醒分钟数变了：Reminders 挂在 EVENT_ID 上，update 改不动，
                        // 只能删掉整条事件重建。
                        old.fingerprint.substringAfterLast('|') != want.fingerprint.substringAfterLast('|') -> {
                            ops.add(deleteOp(old.eventId))
                            deleted++
                            appendInsert(ops, calendarId, want)
                            inserted++
                        }

                        else -> {
                            ops.add(updateOp(old.eventId, want))
                            updated++
                        }
                    }
                }

                if (ops.isNotEmpty()) {
                    resolver.applyBatch(CalendarContract.AUTHORITY, ops)
                }

                // ---- 3. 回读校验 ----
                val verified = verifyCount(resolver, calendarId)
                if (verified != desired.size) {
                    AppLog.e(
                        TAG,
                        "日历写回回读校验不一致：期望=${desired.size} 回读=$verified " +
                            "（新增 $inserted / 更新 $updated / 删除 $deleted）"
                    )
                    return@withContext false
                }
                AppLog.i(
                    TAG,
                    "日历写回完成：期望=${desired.size} 回读=$verified " +
                        "（新增 $inserted / 更新 $updated / 删除 $deleted）"
                )
                true
            } catch (e: Exception) {
                AppLog.e(TAG, "同步课表到系统日历失败", e)
                false
            }
        }
    }

    /** 「无提醒」在指纹里的哨兵值，与任何合法分钟数（0..60）都不冲突。 */
    private const val NO_ALARM = -1
}
