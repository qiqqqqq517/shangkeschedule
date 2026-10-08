package com.shangkeschedule.tool

import com.shangkeschedule.data.db.main.Course
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.db.main.TimeSlot
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.getString
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.course_teacher_prefix
import shangkeschedule.shared.generated.resources.ics_alarm_description
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 课表数据转换与 ICS 日历生成工具类（基于 Kotlin Multiplatform）
 */
object IcsExportTool {

    /**
     * 核心引擎：遍历并计算学期内所有课程的具体发生时间实例。
     */
    inline fun processCourseInstances(
        courses: List<CourseWithWeeks>,
        timeSlots: List<TimeSlot>,
        semesterStartDate: LocalDate,
        semesterTotalWeeks: Int,
        firstDayOfWeekInt: Int,
        skippedDates: Set<String>? = null,
        action: (course: Course, startDateTime: LocalDateTime, endDateTime: LocalDateTime, weekNumber: Int) -> Unit
    ) {
        val timeSlotMap = timeSlots.associateBy { it.number }
        val dayOfWeekMap = mapOf(
            1 to DayOfWeek.MONDAY,
            2 to DayOfWeek.TUESDAY,
            3 to DayOfWeek.WEDNESDAY,
            4 to DayOfWeek.THURSDAY,
            5 to DayOfWeek.FRIDAY,
            6 to DayOfWeek.SATURDAY,
            7 to DayOfWeek.SUNDAY
        )

        val firstDayOfWeek = dayOfWeekMap[firstDayOfWeekInt] ?: DayOfWeek.MONDAY

        // 对齐学期起始周的第一天
        val diff = (semesterStartDate.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7
        val alignedSemesterStart = semesterStartDate.minus(diff.toLong(), DateTimeUnit.DAY)

        courses.forEach { courseWithWeeks ->
            val course = courseWithWeeks.course
            val weeks = courseWithWeeks.weeks.map { it.weekNumber }

            val startTime: LocalTime
            val endTime: LocalTime
            if (course.isCustomTime) {
                val s = course.customStartTime ?: return@forEach
                val e = course.customEndTime ?: return@forEach
                try {
                    startTime = LocalTime.parse(s)
                    endTime = LocalTime.parse(e)
                } catch (_: Exception) { return@forEach }
            } else {
                val s = timeSlotMap[course.startSection]?.startTime ?: return@forEach
                val e = timeSlotMap[course.endSection]?.endTime ?: return@forEach
                try {
                    startTime = LocalTime.parse(s)
                    endTime = LocalTime.parse(e)
                } catch (_: Exception) { return@forEach }
            }

            val dayOfWeek = dayOfWeekMap[course.day] ?: return@forEach

            weeks.forEach { week ->
                val dayOffset = (dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7
                val date = alignedSemesterStart
                    .plus((week - 1).toLong(), DateTimeUnit.WEEK)
                    .plus(dayOffset.toLong(), DateTimeUnit.DAY)

                val weekIndex = alignedSemesterStart.daysUntil(date) / 7 + 1
                // R4-002：与 Widget/Today 双边截断对齐（原仅截上界，week≤0 会落盘过去日期）
                if (weekIndex !in 1..semesterTotalWeeks) return@forEach

                if (skippedDates?.contains(date.toString()) == true) return@forEach

                action(
                    course,
                    LocalDateTime(date, startTime),
                    LocalDateTime(date, endTime),
                    week
                )
            }
        }
    }

    /**
     * 生成标准 ICS 日历文件内容字符串
     */
    suspend fun generateIcsFileContent(
        courses: List<CourseWithWeeks>,
        timeSlots: List<TimeSlot>,
        semesterStartDate: LocalDate,
        semesterTotalWeeks: Int,
        firstDayOfWeekInt: Int,
        alarmMinutes: Int? = null,
        skippedDates: Set<String>? = null
    ): String {
        val ics = StringBuilder()

        // 标准 ICS 头规范
        ics.append("BEGIN:VCALENDAR\r\n")
        ics.append("VERSION:2.0\r\n")
        ics.append("PRODID:-//ShangKeSchedule//ZH\r\n")
        ics.append("CALSCALE:GREGORIAN\r\n")
        ics.append("METHOD:PUBLISH\r\n")
        ics.append("BEGIN:VTIMEZONE\r\n")
        ics.append("TZID:Asia/Shanghai\r\n")
        ics.append("BEGIN:STANDARD\r\n")
        ics.append("DTSTART:19700101T000000Z\r\n")
        ics.append("TZOFFSETFROM:+0800\r\n")
        ics.append("TZOFFSETTO:+0800\r\n")
        ics.append("END:STANDARD\r\n")
        ics.append("END:VTIMEZONE\r\n")

        val dtStampStr = formatDateTimeUtc(Clock.System.now())

        processCourseInstances(
            courses, timeSlots, semesterStartDate, semesterTotalWeeks, firstDayOfWeekInt, skippedDates
        ) { course, start, end, _ ->
            ics.append("BEGIN:VEVENT\r\n")
            // UID 必须**稳定**：同一门课同一次课重复导出时 UID 不变，
            // 日历客户端才会把它识别为「同一条事件的更新」而不是新增一条。
            // 此前每次 `Uuid.random()` ⇒ 重复导入同一份课表会在日历里堆出重复事件，
            // 且违反 RFC 5545 §3.8.4.7「UID 一旦分配不得更改」的语义。
            // 取值由 课程ID + 课次开始时刻 稳定派生（同一课表内唯一）。
            ics.append("UID:${stableUid(course, start)}@shangkeschedule.com\r\n")
            ics.append("DTSTAMP:$dtStampStr\r\n")
            ics.append("DTSTART;TZID=Asia/Shanghai:${formatDateTimeLocal(start)}\r\n")
            ics.append("DTEND;TZID=Asia/Shanghai:${formatDateTimeLocal(end)}\r\n")
            ics.append("SUMMARY:${escapeText(course.name)}\r\n")

            if (course.position.isNotBlank()) {
                ics.append("LOCATION:${escapeText(course.position)}\r\n")
            }

            if (course.teacher.isNotBlank()) {
                val teacherDescription = getString(Res.string.course_teacher_prefix, course.teacher)
                ics.append("DESCRIPTION:${escapeText(teacherDescription)}\r\n")
            }

            if (alarmMinutes != null && alarmMinutes in 0..60) {
                ics.append("BEGIN:VALARM\r\n")
                ics.append("ACTION:DISPLAY\r\n")
                ics.append("DESCRIPTION:${escapeText(getString(Res.string.ics_alarm_description))}\r\n")
                ics.append("TRIGGER:-PT${alarmMinutes}M\r\n")
                ics.append("END:VALARM\r\n")
            }
            ics.append("END:VEVENT\r\n")
        }

        ics.append("END:VCALENDAR\r\n")
        return ics.toString()
    }

    /**
     * 格式化本地时间为 ICS 标准字符串格式（YYYYMMDD'T'HHMMSS）
     */
    private fun formatDateTimeLocal(dateTime: LocalDateTime): String {
        val y = dateTime.year.toString().padStart(4, '0')
        val m = dateTime.month.number.toString().padStart(2, '0')
        val d = dateTime.day.toString().padStart(2, '0')
        val h = dateTime.hour.toString().padStart(2, '0')
        val min = dateTime.minute.toString().padStart(2, '0')
        val s = dateTime.second.toString().padStart(2, '0')
        return "${y}${m}${d}T${h}${min}${s}"
    }

    /**
     * 格式化 UTC 时间戳为 ICS 标准格式（RFC 5545 §3.3.5：UTC DATE-TIME 末尾**恰好一个** Z）。
     *
     * R43-01：旧实现是 `toString().replace(...).substringBefore(".") + "Z"`。
     * `kotlin.time.Instant.toString()` 在**纳秒恰为 0** 时本身就以 `Z` 结尾
     * （如 `2026-10-08T01:29:00Z`），此时串里没有小数点，`substringBefore(".")` 找不到
     * 分隔符便返回整串（连 `Z` 一起留下），再拼一个 `"Z"` ⇒ `20261008T012900ZZ`。
     * 有纳秒分量时才有小数点、恰好把 `Z` 切掉，所以「看起来一直是对的」——
     * 而导出结果随导出时刻而变（整秒时坏），部分日历导入器会拒收或跳过该事件。
     *
     * 修法：不依赖小数点是否存在，而是**先剥掉可能已有的 `Z`** 再统一追加。
     */
    private fun formatDateTimeUtc(instant: Instant): String {
        val compact = instant.toString()
            .replace("-", "")
            .replace(":", "")
            .substringBefore(".")
            .removeSuffix("Z")
        return "${compact}Z"
    }

    /**
     * 生成**稳定**的事件标识符（基于内容派生，不是随机）。
     *
     * 输入是「课程 ID + 该课次开始时刻」，因此：
     * ① 同一门课的同一课次，无论导出多少次，UID 恒定 ⇒ 日历把它当作同一条事件更新；
     * ② 课表变更（改时间/改周次）后该课次是新事件，UID 随之改变 ⇒ 不会错误合并；
     * ③ 同一课表内不同课次 UID 天然不同。
     *
     * 用 `Uuid.fromString` 不可行（输入不是 UUID），故直接取两个稳定量的哈希前 32 位十六进制，
     * 拼成 RFC 4122 形状的字符串即可 —— 日历客户端只要求 UID **全局唯一且稳定**，
     * 并不校验它是否是合法 UUID。
     */
    private fun stableUid(course: Course, start: LocalDateTime): String {
        val seed = course.id + "|" + start.toString()
        val h1 = seed.hashCode().toUInt().toString(16).padStart(8, '0')
        val h2 = (seed + "#salt").hashCode().toUInt().toString(16).padStart(8, '0')
        val h3 = (seed + "#pepper").hashCode().toUInt().toString(16).padStart(8, '0')
        val h4 = (seed + "#final").hashCode().toUInt().toString(16).padStart(8, '0')
        return "$h1-$h2-$h3-$h4"
    }

    /**
     * 转义 ICS 格式中的特殊字符
     */
    private fun escapeText(text: String): String {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
    }
}