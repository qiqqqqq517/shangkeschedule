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
 * 现在改为**按开始时刻做差分**：新增 / 更新 / 删除分开下发，只动真正变化的那些。
 * 匹配键取一次课的开始时刻（见 [ExistingEvent] 的说明：为什么不用隐藏列）。
 *
 * ## 写入后回读校验
 *
 * `applyBatch` 返回即认为成功是不够的 —— 部分 Provider 会在批次里静默丢事件。
 * 因此写完再查一次本日历下由本应用写入的事件数，与期望值比对，不一致即判失败并记日志。
 */
actual object CalendarAccountManager : KoinComponent {

    private const val ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL

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
         *
         * `startMillis` 不进指纹 —— 它是匹配键本身（见 [ExistingEvent]）。
         */
        val fingerprint: String
            get() = "$endMillis|$title|$location|$description|${alarmMinutes ?: NO_ALARM}"
    }

    /**
     * 日历里已存在的一次课。
     *
     * ## 为什么用开始时刻当匹配键（v4.66.5 真机修复）
     *
     * 差分需要一个能跨次同步认出「还是那堂课」的稳定键。v4.66.4 曾试图把
     * `courseId:weekNumber` 写进日历的隐藏列，两种写法都被真机证伪：
     *
     * - `"uid"`：Provider 不在普通调用方的投影白名单里，查询即抛
     *   `IllegalArgumentException: Invalid column uid`；
     * - `Events.ORIGINAL_ID`：语义是「本事件作为例外所归属的**原重复事件**的
     *   `_id`」。在非重复事件上写它，Provider 会去解析那个不存在的原事件，
     *   `applyBatch` 抛 `NullPointerException: Long.longValue() on null`。
     *
     * 与其找一个「能塞进日历又没被占用语义」的隐藏列，不如承认 **`dtstart` 本身就是
     * 天然唯一键**：同一时刻在本日历里至多一堂课（`processCourseInstances` 对每个
     * (课程, 周次) 只产出一个跨越全部连排节的实例）。它公开、可查可写、不参与任何
     * 重复规则解析，且课程改名 / 换教室时保持不变 —— 正好符合「同一堂课」的定义。
     */
    /** 日历里已存在的一次课。 */
    private class ExistingEvent(val eventId: Long, val fingerprint: String, val ownedByUs: Boolean)

    /** 本日历下的现状快照。 */
    private class ExistingSnapshot(
        /** 开始时刻 -> 已存在事件。 */
        val byStart: Map<Long, ExistingEvent>,
        /**
         * 同一开始时刻出现多条事件时的**多余**那几条。
         *
         * 达不到「一个时刻至多一条」就说明日历里混进了手工事件（或历史脏数据）。
         * 它们必须被显式删掉，否则回读校验会永远对不上、每次同步都判失败。
         *
         * ⚠️ v4.68.2：**只删本应用写的那些**（`ownedByUs = true`）。
         * 手工事件与本应用的课撞在同一时刻时，保留手工的那条 ——
         * 删错的是用户数据，删对的只是「一条重复的课」，后者下次同步仍会补回来。
         */
        val duplicateIds: List<Long>
    )

    /**
     * 读出本日历下的全部事件，按开始时刻索引。
     *
     * 分两次查询：先取事件行，再取提醒分钟数并按 EVENT_ID 归并。
     * 本应用用的是 `ACCOUNT_TYPE_LOCAL` 的**专用**本地日历（账号名固定为本包名），
     * 其下事件全部由本应用写入，因此整表扫的代价与事件量同阶，可忽略。
     */
    private fun readExisting(resolver: ContentResolver, calendarId: Long): ExistingSnapshot {
        val rows = LinkedHashMap<Long, Array<String>>()   // eventId -> 指纹的字段数组
        val startById = LinkedHashMap<Long, Long>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION
        )
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.CALENDAR_ID} = ?",
            arrayOf(calendarId.toString()),
            null
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val startIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val locIdx = c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            val descIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                startById[id] = c.getLong(startIdx)
                rows[id] = arrayOf(
                    c.getLong(endIdx).toString(),
                    c.getString(titleIdx).orEmpty(),
                    c.getString(locIdx).orEmpty(),
                    c.getString(descIdx).orEmpty(),
                    NO_ALARM.toString()
                )
            }
        }
        if (rows.isEmpty()) return ExistingSnapshot(emptyMap(), emptyList())

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
                val fields = rows[id] ?: continue
                if (fields[4] == NO_ALARM.toString()) {
                    fields[4] = c.getInt(minIdx).toString()
                }
            }
        }

        val byStart = LinkedHashMap<Long, ExistingEvent>()
        val duplicates = ArrayList<Long>()
        for ((id, fields) in rows) {
            val start = startById[id] ?: continue
            val description = fields[3]
            val owned = CalendarOwnerMark.isOwnedBy(description)
            // 指纹里的描述用**去掉标记的正文**，否则标记一旦变化就会把
            // 「内容其实没变」误判成「变了」而全量重写。
            val event = ExistingEvent(
                eventId = id,
                fingerprint = fields.toMutableList().apply { this[3] = CalendarOwnerMark.unmark(description).orEmpty() }
                    .joinToString("|"),
                ownedByUs = owned
            )
            val kept = byStart.put(start, event)
            if (kept != null) {
                // 同一时刻有两条：只把**本应用写的**那条当多余副本。
                // 若两条都不是本应用写的（全是手工事件），一条都不动。
                val redundant = when {
                    kept.ownedByUs && !event.ownedByUs -> kept.eventId
                    !kept.ownedByUs && event.ownedByUs -> id
                    else -> id
                }
                duplicates.add(redundant)
            }
        }
        return ExistingSnapshot(byStart, duplicates)
    }

    /** 回读校验：本日历下**由本应用写入**的事件条数。 */
    private fun verifyCount(resolver: ContentResolver, calendarId: Long): Int {
        val cursor: Cursor? = resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DESCRIPTION),
            "${CalendarContract.Events.CALENDAR_ID} = ?",
            arrayOf(calendarId.toString()),
            null
        )
        var count = 0
        cursor?.use {
            val descIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            // 只数本应用写的：用户手工加的事件不参与校验，否则它会让本应用
            // 每次同步都判「少写了事件」而反复失败。
            while (it.moveToNext()) {
                if (CalendarOwnerMark.isOwnedBy(it.getString(descIdx))) count++
            }
        }
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

    /**
     * 定位单条事件的 item URI。
     *
     * 必须用 `ContentUris.withAppendedId` 把 `_id` 作为**路径段**拼上去，
     * 不能 `appendQueryParameter("_id", …)` —— CalendarProvider 会拒绝后者并抛
     * `IllegalArgumentException: Invalid URI parameter: _id`（v4.66.5 真机实测）。
     */
    private fun eventUri(eventId: Long): Uri =
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)

    private fun deleteOp(eventId: Long): ContentProviderOperation =
        ContentProviderOperation.newDelete(eventUri(eventId)).build()

    /**
     * 改一次课的内容。
     *
     * 不带 `DTSTART`：它就是匹配键，本分支能走到说明日历里的开始时刻与期望一致。
     * 反过来，若用户把事件在日历 App 里挪了时间，读回的 `dtstart` 就不再匹配任何期望项，
     * 那条事件会被当成「多余」删掉再按正确时间重建 —— 时间因此始终以课表为准。
     */
    private fun updateOp(eventId: Long, e: DesiredEvent): ContentProviderOperation =
        ContentProviderOperation.newUpdate(eventUri(eventId))
            .withValue(CalendarContract.Events.TITLE, e.title)
            .withValue(CalendarContract.Events.EVENT_LOCATION, e.location)
            .withValue(CalendarContract.Events.DESCRIPTION, e.description)
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

                // 学期内没有课时：把本日历下的事件清空即达成「日历与课表一致」。
                if (semesterTotalWeeks <= 0 || courses.isEmpty()) {
                    val stale = readExisting(resolver, calendarId)
                    // ⚠️ v4.68.2：**只清本应用写的**。原实现把 `byStart` 与 `duplicateIds`
                    // 全删，等于「课表为空 = 把这个日历清空」—— 用户手工加的事件会被一起清掉，
                    // 且这是最容易触发的路径（删掉课表/切换空课表即命中）。
                    val ownedIds = buildList {
                        stale.byStart.values.forEach { if (it.ownedByUs) add(it.eventId) }
                        stale.duplicateIds.forEach { add(it) }   // 2a 已只收本应用写的
                    }
                    val kept = stale.byStart.size + stale.duplicateIds.size - ownedIds.size
                    if (ownedIds.isNotEmpty()) {
                        val ops = ArrayList<ContentProviderOperation>(ownedIds.size)
                        ownedIds.forEach { ops.add(deleteOp(it)) }
                        resolver.applyBatch(CalendarContract.AUTHORITY, ops)
                        AppLog.i(
                            TAG,
                            "课表为空，已清理 ${ownedIds.size} 条本应用写入的日历事件" +
                                (if (kept > 0) "（保留 $kept 条用户手工事件）" else "")
                        )
                    }
                    return@withContext true
                }

                val timeZone = TimeZone.currentSystemDefault()
                val effectiveAlarm = alarmMinutes?.takeIf { it in 0..60 }

                // ---- 1. 算出期望集合（键 = 一次课的开始时刻）----
                val desired = LinkedHashMap<Long, DesiredEvent>()
                IcsExportTool.processCourseInstances(
                    courses = courses,
                    timeSlots = timeSlots,
                    semesterStartDate = semesterStartDate,
                    semesterTotalWeeks = semesterTotalWeeks,
                    firstDayOfWeekInt = firstDayOfWeekInt,
                    skippedDates = skippedDates
                ) { course, start, end, _ ->
                    val teacherDescription = if (course.teacher.isNotBlank()) {
                        // 带归属标记（v4.68.2）：差分删除据此区分「本应用写的」与
                        // 「用户手工加的」—— 后者若无标记会在下一次同步被误删。
                        CalendarOwnerMark.stamp(
                            getString(Res.string.course_teacher_prefix, course.teacher)
                        )
                    } else {
                        // 无教师也要打标记，否则这条事件会被当成「不是本应用写的」而永远删不掉。
                        CalendarOwnerMark.stamp("")
                    }
                    val startMillis = start.toInstant(timeZone).toEpochMilliseconds()
                    desired[startMillis] = DesiredEvent(
                        title = course.name,
                        location = course.position,
                        description = teacherDescription,
                        startMillis = startMillis,
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

                // 2a. 同一开始时刻的多余副本（日历里混进了手工事件）→ 删
                existing.duplicateIds.forEach {
                    ops.add(deleteOp(it))
                    deleted++
                }

                // 2b. 多余的（课被删 / 调到别的周 / 学期缩短）→ 删
                // ⚠️ v4.68.2：**只删本应用写的**。原判据是「开始时刻不在期望集合里就删」，
                // 那会连用户在这个日历里**手工添加**的事件（生日 / 社团 / 临时提醒）一起删掉 ——
                // 用户既不知道是谁删的，也看不到任何报错。改为按「归属标记」判定后，
                // 手工事件完整保留，而真正该清的陈旧课程一条都不会漏。
                var skippedForeign = 0
                for ((startMillis, old) in existing.byStart) {
                    if (startMillis in desired) continue
                    if (!old.ownedByUs) {
                        skippedForeign++
                        continue
                    }
                    ops.add(deleteOp(old.eventId))
                    deleted++
                }
                if (skippedForeign > 0) {
                    AppLog.i(
                        TAG,
                        "保留 $skippedForeign 条非本应用写入的事件（用户手工添加，不参与课表同步）"
                    )
                }

                for ((startMillis, want) in desired) {
                    val old = existing.byStart[startMillis]
                    when {
                        old == null -> {
                            appendInsert(ops, calendarId, want)
                            inserted++
                        }

                        // ⚠️ v4.68.2：该时刻占位的是**用户手工加**的事件 → 不动它，
                        // 另插一条本应用的课。原先走 update 分支会把用户的「生日/社团」
                        // 直接改写成课程内容，是**静默的数据破坏**且不可恢复。
                        // 代价是日历里同一时刻出现两条（手工 + 课程），符合用户「两者都要」的意图。
                        !old.ownedByUs -> {
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
