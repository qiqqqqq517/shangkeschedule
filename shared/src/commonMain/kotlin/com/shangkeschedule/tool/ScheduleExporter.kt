package com.shangkeschedule.tool

import com.shangkeschedule.data.db.main.Course
import com.shangkeschedule.data.db.main.CourseWeek
import com.shangkeschedule.data.model.CourseImportExport
import com.shangkeschedule.data.repository.CourseConversionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 课表导出工具
 * 支持格式：WakeUp JSON、分享文本、ICS 日历
 * 参考 sleepy 项目的 ScheduleExporter
 */
object ScheduleExporter {

    /**
     * 导出为 WakeUp 兼容 JSON
     */
    suspend fun exportWakeUpJson(
        tableId: String,
        tableName: String,
        courseConversionRepository: CourseConversionRepository
    ): String = withContext(Dispatchers.Default) {
        val exportModel = courseConversionRepository.exportCourseTableToJson(tableId)
            ?: return@withContext "{}"

        val json = buildJsonObject {
            put("name", tableName)
            put("version", "1.0")
            putJsonArray("courses") {
                exportModel.courses.forEach { c ->
                    add(buildJsonObject {
                        put("name", c.name)
                        put("teacher", c.teacher)
                        put("position", c.position)
                        put("day", c.day)
                        put("startSection", c.startSection ?: 0)
                        put("endSection", c.endSection ?: 0)
                        putJsonArray("weeks") { c.weeks.forEach { add(JsonPrimitive(it)) } }
                        c.color?.let { put("color", it) }
                        put("remark", c.remark ?: "")
                        c.credit?.let { put("credit", it) }
                        c.assessmentMethod?.let { put("assessmentMethod", it) }
                        put("isLab", c.isLab)
                    })
                }
            }
            putJsonArray("timeSlots") {
                exportModel.timeSlots.forEach { t ->
                    add(buildJsonObject {
                        put("number", t.number)
                        put("startTime", t.startTime)
                        put("endTime", t.endTime)
                        t.alias?.let { put("alias", it) }
                    })
                }
            }
            putJsonObject("config") {
                put("semesterStartDate", exportModel.config.semesterStartDate ?: "")
                put("semesterTotalWeeks", exportModel.config.semesterTotalWeeks)
                put("defaultClassDuration", exportModel.config.defaultClassDuration)
                put("defaultBreakDuration", exportModel.config.defaultBreakDuration)
                put("firstDayOfWeek", exportModel.config.firstDayOfWeek)
            }
        }
        CourseImportExport.json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), json)
    }

    /**
     * 导出为分享文本（URL编码的JSON，可粘贴到聊天工具）
     */
    suspend fun exportShareText(
        tableId: String,
        tableName: String,
        courseConversionRepository: CourseConversionRepository
    ): String = withContext(Dispatchers.Default) {
        val json = exportWakeUpJson(tableId, tableName, courseConversionRepository)
        // 简单 URL 编码
        val encoded = json
            .replace("{", "%7B").replace("}", "%7D")
            .replace("\"", "%22").replace(":", "%3A")
            .replace(",", "%2C").replace("[", "%5B")
            .replace("]", "%5D").replace(" ", "%20")
        "【来自拾光课程表】\n$encoded"
    }

    /**
     * 导出为 ICS 日历格式
     * @param semesterStartDate 学期开始日期，格式 YYYY-MM-DD
     */
    suspend fun exportIcs(
        tableId: String,
        tableName: String,
        semesterStartDate: String?,
        courseConversionRepository: CourseConversionRepository
    ): String = withContext(Dispatchers.Default) {
        val exportModel = courseConversionRepository.exportCourseTableToJson(tableId)
            ?: return@withContext ""

        val timeSlots = exportModel.timeSlots.associateBy { it.number }
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//ShangKeSchedule//Timetable Export//CN")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("METHOD:PUBLISH")
        sb.appendLine("X-WR-CALNAME:$tableName")

        val startDate = parseDate(semesterStartDate)
        val now = System.currentTimeMillis()

        exportModel.courses.forEach { course ->
            course.weeks.forEach { week ->
                val slot = timeSlots[course.startSection]
                val endSlot = timeSlots[course.endSection]
                if (startDate == null || slot == null) return@forEach

                val eventDate = startDate.plusDays((week - 1) * 7L + (course.day - 1))
                val dtStart = formatIcsDateTime(eventDate, slot.startTime)
                val dtEnd = formatIcsDateTime(eventDate, (endSlot ?: slot).endTime)
                val uid = "${course.id}_${week}@shangkeschedule"

                sb.appendLine("BEGIN:VEVENT")
                sb.appendLine("UID:$uid")
                sb.appendLine("DTSTAMP:${formatIcsTimestamp(now)}")
                sb.appendLine("DTSTART:$dtStart")
                sb.appendLine("DTEND:$dtEnd")
                sb.appendLine("SUMMARY:${course.name}")
                sb.appendLine("LOCATION:${course.position}")
                val desc = buildString {
                    append("教师: ${course.teacher}")
                    course.credit?.let { append("\\n学分: $it") }
                    course.assessmentMethod?.let { append("\\n考核: $it") }
                    if (course.isLab) append("\\n(实验课)")
                    course.remark?.let { append("\\n备注: $it") }
                }
                sb.appendLine("DESCRIPTION:$desc")
                sb.appendLine("END:VEVENT")
            }
        }

        sb.appendLine("END:VCALENDAR")
        sb.toString()
    }

    // ========== 内部工具 ==========

    private data class SimpleDate(val year: Int, val month: Int, val day: Int) {
        fun plusDays(days: Long): SimpleDate {
            // 简化实现：使用天数累加
            val epoch = toEpochDays() + days
            return fromEpochDays(epoch)
        }
        private fun toEpochDays(): Long {
            var y = year
            var m = month
            if (m <= 2) { y -= 1; m += 12 }
            val e = (365.25 * (y + 4716)).toInt() + (30.6001 * (m + 1)).toInt() + day - 1524
            return e.toLong()
        }
        private fun fromEpochDays(epoch: Long): SimpleDate {
            val a = (epoch + 1524).toInt()
            val b = ((a - 122.1) / 365.25).toInt()
            val c = a - (365.25 * b).toInt()
            val d = (c / 30.6001).toInt()
            val day = c - (30.6001 * d).toInt()
            val month = if (d < 14) d - 1 else d - 13
            val year = if (month > 2) b - 4716 else b - 4715
            return SimpleDate(year, month, day)
        }
    }

    private fun parseDate(s: String?): SimpleDate? {
        if (s.isNullOrBlank()) return null
        return try {
            val parts = s.split("-", "/")
            if (parts.size != 3) return null
            SimpleDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        } catch (_: Exception) { null }
    }

    private fun formatIcsDateTime(date: SimpleDate, time: String): String {
        val timeParts = time.split(":")
        val h = if (timeParts.size >= 2) timeParts[0].padStart(2, '0') else "08"
        val m = if (timeParts.size >= 2) timeParts[1].padStart(2, '0') else "00"
        return "${date.year}${date.month.toString().padStart(2, '0')}${date.day.toString().padStart(2, '0')}T${h}${m}00"
    }

    private fun formatIcsTimestamp(epochMs: Long): String {
        // 简化实现：使用当前时间的近似 UTC 格式
        // 注意：精确实现需要 kotlinx-datetime，这里用近似值
        val totalSeconds = epochMs / 1000
        val days = totalSeconds / 86400
        val secondsOfDay = totalSeconds % 86400
        val h = (secondsOfDay / 3600).toInt().toString().padStart(2, '0')
        val m = ((secondsOfDay % 3600) / 60).toInt().toString().padStart(2, '0')
        val s = (secondsOfDay % 60).toInt().toString().padStart(2, '0')
        // 日期部分简化（从 1970-01-01 开始计算）
        val date = SimpleDate(1970, 1, 1).plusDays(days)
        return "${date.year}${date.month.toString().padStart(2, '0')}${date.day.toString().padStart(2, '0')}T${h}${m}${s}Z"
    }
}
