package com.shangkeschedule.tool

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.TimeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val calendarIdentityMutex = Mutex()

    // 通过 Koin 动态注入全局 Application Context
    private val context: Context by inject()

    /**
     * 系统账户的 authenticator 使用应用图标；日历颜色只负责日历/事件的色块。
     * 旧 LOCAL 日历原位改为本应用账户类型，保留日历 ID、事件、提醒和用户设置。
     */
    private suspend fun getOrCreateCalendarId(): Long = calendarIdentityMutex.withLock {
        val contentResolver = context.contentResolver
        val accountName = "${context.packageName}.account"
        val calendarDisplayName = getString(Res.string.app_name)
        val existingColors = mutableMapOf<Long, Int?>()

        val store = object : CalendarIdentityStore {
            override fun ensureAccount(): Boolean {
                val manager = AccountManager.get(context)
                val account = Account(accountName, CalendarAccountIdentity.TYPE)
                if (manager.getAccountsByType(account.type).any { it == account }) return true
                val created = manager.addAccountExplicitly(account, null, null)
                if (created) {
                    // 仅登记系统日历能力，仍由原有手动同步入口写课表。
                    ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 0)
                    ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, false)
                }
                return created || manager.getAccountsByType(account.type).any { it == account }
            }

            override fun findCalendars(): List<CalendarIdentity> {
                val projection = arrayOf(Calendars._ID, Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_COLOR)
                val selection = "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} IN (?, ?)"
                val args = arrayOf(accountName, CalendarAccountIdentity.TYPE, CalendarAccountIdentity.LEGACY_TYPE)
                val cursor = contentResolver.query(Calendars.CONTENT_URI, projection, selection, args, null)
                    ?: error("无法读取本应用日历，已停止创建以避免重复")
                return cursor.use { c ->
                    val idIdx = c.getColumnIndexOrThrow(Calendars._ID)
                    val typeIdx = c.getColumnIndexOrThrow(Calendars.ACCOUNT_TYPE)
                    val colorIdx = c.getColumnIndexOrThrow(Calendars.CALENDAR_COLOR)
                    buildList {
                        while (c.moveToNext()) {
                            val id = c.getLong(idIdx)
                            existingColors[id] = if (c.isNull(colorIdx)) null else c.getInt(colorIdx)
                            add(CalendarIdentity(id, c.getString(typeIdx)))
                        }
                    }
                }
            }

            override fun migrateLocalCalendar(id: Long): Boolean {
                // 单行 URI 不允许 selection；集合 URI 用账户和 ID 限定更新范围。
                val values = ContentValues().apply { put(Calendars.ACCOUNT_TYPE, CalendarAccountIdentity.TYPE) }
                val updated = contentResolver.update(
                    calendarSyncAdapterUri(null, accountName, CalendarAccountIdentity.LEGACY_TYPE),
                    values,
                    "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars._ID} = ?",
                    arrayOf(accountName, CalendarAccountIdentity.LEGACY_TYPE, id.toString())
                )
                if (updated != 1) return false
                // OEM Provider 可能忽略账户字段；回读确认后才能继续，失败时不另建副本。
                return contentResolver.query(
                    ContentUris.withAppendedId(Calendars.CONTENT_URI, id),
                    arrayOf(Calendars.ACCOUNT_TYPE), null, null, null
                )?.use { it.moveToFirst() && it.getString(0) == CalendarAccountIdentity.TYPE } == true
            }

            override fun createCalendar(): Long {
                val values = ContentValues().apply {
                    put(Calendars.ACCOUNT_NAME, accountName)
                    put(Calendars.ACCOUNT_TYPE, CalendarAccountIdentity.TYPE)
                    put(Calendars.NAME, accountName)
                    put(Calendars.CALENDAR_DISPLAY_NAME, calendarDisplayName)
                    put(Calendars.CALENDAR_COLOR, CalendarBrandColor.BRAND)
                    put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                    put(Calendars.OWNER_ACCOUNT, accountName)
                    put(Calendars.VISIBLE, 1)
                    put(Calendars.SYNC_EVENTS, 1)
                    put(Calendars.CALENDAR_TIME_ZONE, java.util.TimeZone.getDefault().id)
                    put(Calendars.CAN_ORGANIZER_RESPOND, 1)
                }
                return contentResolver.insert(calendarSyncAdapterUri(null, accountName), values)
                    ?.let { ContentUris.parseId(it) } ?: -1L
            }
        }
        val id = CalendarIdentityMigration.resolve(store)
        if (id == -1L) {
            AppLog.e(TAG, "系统日历账户登记或图标迁移失败，已保留已有日历")
            return@withLock -1L
        }
        if (id in existingColors) {
            val currentColor = existingColors[id]
            if (CalendarBrandColor.shouldMigrate(currentColor)) {
                val values = ContentValues().apply {
                    put(Calendars.CALENDAR_COLOR, CalendarBrandColor.BRAND)
                }
                try {
                    contentResolver.update(calendarSyncAdapterUri(id, accountName), values, null, null)
                    AppLog.i(TAG, "系统日历标识色已迁移到当前品牌（旧值 ${currentColor ?: "未设置"}）")
                } catch (e: Exception) {
                    // 迁移失败**不得**让整次同步失败：事件写回才是主职责。
                    AppLog.e(TAG, "迁移系统日历标识色失败（不影响事件同步）", e)
                }
            }
        }
        id
    }

    /**
     * 构造本地日历 `Calendars` 行的写入 URI。
     *
     * `CALLER_IS_SYNCADAPTER=true` 加账号名 / 账号类型，是 CalendarProvider 对本地日历
     * `Calendars` 行读写的硬性要求（缺了会抛 `IllegalArgumentException` 或被判无权）。
     *
     * [calendarId] 非空时拼成单行 URI —— 必须用 `ContentUris.withAppendedId` 作为**路径段**，
     * 不能用 `appendQueryParameter("_id", …)`：Provider 会拒绝并抛
     * `Invalid URI parameter: _id`（v4.66.5 真机实测，同 [eventUri]）。
     */
    private fun calendarSyncAdapterUri(
        calendarId: Long?,
        accountName: String,
        accountType: String = CalendarAccountIdentity.TYPE
    ): Uri {
        val base = if (calendarId != null) {
            ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId)
        } else {
            Calendars.CONTENT_URI
        }
        return base.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType)
            .build()
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
            get() = calendarEventFingerprint(endMillis, title, location, description, alarmMinutes ?: NO_ALARM)
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
    /** 本日历下的现状快照。 */
    private class ExistingSnapshot(
        /** 开始时刻 -> 已存在事件。 */
        val byStart: Map<Long, ExistingEvent>,
        /**
         * 同一开始时刻出现多条事件时的**多余**那几条。
         *
         * 仅包含同一时刻多条本应用课程的多余项；手工事件始终保留。
         */
        val duplicateIds: List<Long>
    )

    /**
     * 读出本日历下的全部事件，按开始时刻索引。
     *
     * 分两次查询：先取事件行，再取提醒分钟数并按 EVENT_ID 归并。
     * 只读取本应用专属日历；其中也可能有用户手工添加的事件，必须按归属标记区分。
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
        } ?: error("无法读取日历事件")
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
        } ?: error("无法读取日历提醒")

        val indexed = indexCalendarEvents(rows.mapNotNull { (id, fields) ->
            val start = startById[id] ?: return@mapNotNull null
            CalendarEventRow(id, start, fields[0].toLong(), fields[1], fields[2], fields[3], fields[4].toInt())
        })
        return ExistingSnapshot(indexed.byStart, indexed.duplicateIds)
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
        (cursor ?: error("无法回读日历事件")).use {
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

                // 2a. 同一开始时刻多条本应用课程的多余副本 → 删
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

internal data class ExistingEvent(val eventId: Long, val fingerprint: String, val ownedByUs: Boolean)
internal data class CalendarEventRow(
    val id: Long, val start: Long, val end: Long, val title: String,
    val location: String, val description: String, val alarmMinutes: Int
)
internal data class CalendarEventIndex(val byStart: Map<Long, ExistingEvent>, val duplicateIds: List<Long>)

internal fun calendarEventFingerprint(end: Long, title: String, location: String, description: String, alarmMinutes: Int): String =
    "$end|$title|$location|${CalendarOwnerMark.unmark(description) ?: description}|$alarmMinutes"

internal fun indexCalendarEvents(rows: List<CalendarEventRow>): CalendarEventIndex {
    val byStart = LinkedHashMap<Long, ExistingEvent>()
    val duplicates = ArrayList<Long>()
    for (row in rows) {
        val event = ExistingEvent(row.id,
            calendarEventFingerprint(row.end, row.title, row.location, row.description, row.alarmMinutes),
            CalendarOwnerMark.isOwnedBy(row.description))
        val kept = byStart[row.start]
        when {
            kept == null -> byStart[row.start] = event
            !kept.ownedByUs && event.ownedByUs -> byStart[row.start] = event
            kept.ownedByUs && event.ownedByUs -> duplicates.add(event.eventId)
        }
    }
    return CalendarEventIndex(byStart, duplicates)
}
