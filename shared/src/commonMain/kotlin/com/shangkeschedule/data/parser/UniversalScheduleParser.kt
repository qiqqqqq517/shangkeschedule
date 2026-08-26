package com.shangkeschedule.data.parser

import com.shangkeschedule.data.model.CourseImportExport
import com.shangkeschedule.data.model.CourseImportExport.CourseTableImportModel
import com.shangkeschedule.data.model.CourseImportExport.ImportCourseJsonModel
import com.shangkeschedule.data.model.CourseImportExport.TimeSlotJsonModel
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 通用课表文本/文件导入解析器
 * 支持格式：WakeUp分享文本、WakeUp/Sleepy JSON、ICS日历、CSV、HTML表格、纯文本
 * 所有结果统一为 CourseTableImportModel，保留 remark 字段以便自动提取学分/考核方式/实验课。
 */
object UniversalScheduleParser {

    fun parseAuto(content: String): ParseResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return ParseResult.Error("内容为空")

        return when {
            trimmed.startsWith("【来自WakeUp课程表】") -> parseWakeUpShareText(trimmed)
            trimmed.startsWith("BEGIN:VCALENDAR") || trimmed.contains("VEVENT") -> parseIcs(trimmed)
            trimmed.startsWith("{") && trimmed.contains("courses") -> parseWakeUpJson(trimmed)
            trimmed.contains("<table", ignoreCase = true) -> parseHtmlTable(trimmed)
            isCsvContent(trimmed) -> parseCsv(trimmed)
            else -> parsePlainText(trimmed)
        }
    }

    sealed class ParseResult {
        data class Success(val model: CourseTableImportModel, val format: String) : ParseResult()
        data class Error(val message: String) : ParseResult()
    }

    // ========== WakeUp 分享文本 ==========

    private fun parseWakeUpShareText(text: String): ParseResult {
        return try {
            val jsonStart = text.indexOf("{")
            if (jsonStart == -1) return ParseResult.Error("无法找到JSON数据")

            val jsonStr = text.substring(jsonStart).trim()
                .replace("%7B", "{").replace("%7D", "}")
                .replace("%22", "\"").replace("%3A", ":")
                .replace("%2C", ",").replace("%5B", "[")
                .replace("%5D", "]").replace("%20", " ")
                .replace("%0A", "\n").replace("%0D", "\r")

            parseWakeUpJson(jsonStr)
        } catch (e: Exception) {
            ParseResult.Error("WakeUp文本解析失败: ${e.message}")
        }
    }

    // ========== WakeUp / Sleepy JSON ==========

    private fun parseWakeUpJson(jsonStr: String): ParseResult {
        return try {
            val json = CourseImportExport.json
            val obj = json.parseToJsonElement(jsonStr).jsonObject

            val courses = obj["courses"]?.jsonArray?.map { c ->
                val co = c.jsonObject
                ImportCourseJsonModel(
                    name = co["name"]?.jsonPrimitive?.content ?: "",
                    teacher = co["teacher"]?.jsonPrimitive?.content ?: "",
                    position = co["position"]?.jsonPrimitive?.content ?: "",
                    day = co["day"]?.jsonPrimitive?.intOrNull ?: 0,
                    startSection = co["startSection"]?.jsonPrimitive?.intOrNull,
                    endSection = co["endSection"]?.jsonPrimitive?.intOrNull,
                    weeks = co["weeks"]?.jsonArray?.map { it.jsonPrimitive.int } ?: emptyList(),
                    color = co["color"]?.jsonPrimitive?.intOrNull,
                    remark = co["remark"]?.jsonPrimitive?.content,
                    credit = co["credit"]?.jsonPrimitive?.content,
                    assessmentMethod = co["assessmentMethod"]?.jsonPrimitive?.content,
                    isLab = co["isLab"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                )
            }?.filter { it.name.isNotBlank() && it.day in 1..7 } ?: emptyList()

            val timeSlots = obj["timeSlots"]?.jsonArray?.map { t ->
                val to = t.jsonObject
                TimeSlotJsonModel(
                    number = to["number"]?.jsonPrimitive?.intOrNull ?: 0,
                    startTime = to["startTime"]?.jsonPrimitive?.content ?: "",
                    endTime = to["endTime"]?.jsonPrimitive?.content ?: "",
                    alias = to["alias"]?.jsonPrimitive?.content
                )
            }?.filter { it.number > 0 } ?: emptyList()

            if (courses.isEmpty()) return ParseResult.Error("JSON中未找到有效课程")

            ParseResult.Success(CourseTableImportModel(courses = courses, timeSlots = timeSlots), "WakeUp JSON")
        } catch (e: Exception) {
            ParseResult.Error("JSON解析失败: ${e.message}")
        }
    }

    // ========== ICS 日历 ==========

    private fun parseIcs(content: String): ParseResult {
        return try {
            val events = mutableListOf<Map<String, String>>()
            val lines = content.lines()
            var i = 0
            while (i < lines.size) {
                if (lines[i].trim() == "BEGIN:VEVENT") {
                    val event = mutableMapOf<String, String>()
                    i++
                    while (i < lines.size && lines[i].trim() != "END:VEVENT") {
                        val colonIdx = lines[i].indexOf(':')
                        if (colonIdx > 0) {
                            val key = lines[i].substring(0, colonIdx).substringBefore(';').trim()
                            val value = lines[i].substring(colonIdx + 1).trim()
                            event[key] = value
                        }
                        i++
                    }
                    events.add(event)
                }
                i++
            }

            val courses = events.mapNotNull { ev ->
                val summary = ev["SUMMARY"] ?: return@mapNotNull null
                val startStr = ev["DTSTART"] ?: return@mapNotNull null
                val endStr = ev["DTEND"] ?: return@mapNotNull null

                val parts = summary.split("@", "-", " ", "（", "(").map { it.trim() }.filter { it.isNotBlank() }
                val name = parts.firstOrNull() ?: summary
                val teacher = parts.getOrNull(1) ?: ""
                val position = ev["LOCATION"] ?: parts.getOrNull(2) ?: ""

                val day = parseIcsDay(startStr)
                val startMinutes = parseIcsMinutes(startStr)
                val endMinutes = parseIcsMinutes(endStr)
                val startSection = if (startMinutes >= 0) ((startMinutes - 8 * 60) / 55) + 1 else null
                val endSection = if (endMinutes >= 0) ((endMinutes - 8 * 60) / 55) + 1 else null

                val weeks = parseIcsWeeks(ev["RRULE"])

                ImportCourseJsonModel(
                    name = name,
                    teacher = teacher,
                    position = position.ifBlank { "待定" },
                    day = day,
                    startSection = startSection,
                    endSection = endSection,
                    weeks = weeks.ifEmpty { listOf(1) },
                    remark = ev["DESCRIPTION"]
                )
            }.filter { it.name.isNotBlank() && it.day in 1..7 }

            if (courses.isEmpty()) return ParseResult.Error("ICS中未找到有效课程事件")

            ParseResult.Success(CourseTableImportModel(courses = courses), "ICS日历")
        } catch (e: Exception) {
            ParseResult.Error("ICS解析失败: ${e.message}")
        }
    }

    private fun parseIcsDay(s: String): Int {
        // 格式：20250825T080000 或 2025-08-25
        val dateStr = s.substringBefore('T').replace("-", "")
        if (dateStr.length < 8) return 0
        return try {
            val y = dateStr.substring(0, 4).toInt()
            val m = dateStr.substring(4, 6).toInt()
            val d = dateStr.substring(6, 8).toInt()
            // Zeller 公式计算星期几（1=周一, 7=周日）
            val (yy, mm) = if (m <= 2) y - 1 to m + 12 else y to m
            val c = yy / 100
            val yy2 = yy % 100
            var w = (yy2 + yy2 / 4 + c / 4 - 2 * c + (26 * (mm + 1)) / 10 + d - 1) % 7
            if (w < 0) w += 7
            if (w == 0) 7 else w
        } catch (_: Exception) { 0 }
    }

    private fun parseIcsMinutes(s: String): Int {
        val tIdx = s.indexOf('T')
        if (tIdx == -1 || s.length < tIdx + 5) return -1
        return try {
            val h = s.substring(tIdx + 1, tIdx + 3).toInt()
            val min = s.substring(tIdx + 3, tIdx + 5).toInt()
            h * 60 + min
        } catch (_: Exception) { -1 }
    }

    private fun parseIcsWeeks(rrule: String?): List<Int> {
        if (rrule == null) return emptyList()
        val interval = Regex("INTERVAL=(\\d+)").find(rrule)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val count = Regex("COUNT=(\\d+)").find(rrule)?.groupValues?.get(1)?.toIntOrNull() ?: 16
        return (1..count step interval).toList()
    }

    // ========== CSV ==========

    private fun isCsvContent(content: String): Boolean {
        val firstLine = content.lines().firstOrNull() ?: return false
        return firstLine.contains(",") && firstLine.length < 200
    }

    private fun parseCsv(content: String): ParseResult {
        return try {
            val lines = content.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return ParseResult.Error("CSV内容为空")

            val header = lines.first().split(",").map { it.trim().lowercase() }
            val dataLines = if (hasCsvHeader(header)) lines.drop(1) else lines

            val nameIdx = header.indexOfFirst { it.contains("课程") || it.contains("name") || it.contains("课名") }
            val teacherIdx = header.indexOfFirst { it.contains("教师") || it.contains("teacher") || it.contains("老师") }
            val positionIdx = header.indexOfFirst { it.contains("教室") || it.contains("位置") || it.contains("location") || it.contains("room") }
            val dayIdx = header.indexOfFirst { it.contains("星期") || it.contains("周几") || it.contains("day") }
            val sectionIdx = header.indexOfFirst { it.contains("节次") || it.contains("节") || it.contains("section") }
            val weeksIdx = header.indexOfFirst { it.contains("周次") || it.contains("周") || it.contains("week") }

            val courses = dataLines.mapNotNull { line ->
                val cols = line.split(",").map { it.trim() }
                if (cols.size < 2) return@mapNotNull null

                val name = if (nameIdx >= 0) cols.getOrElse(nameIdx) { "" } else cols.getOrElse(0) { "" }
                val teacher = if (teacherIdx >= 0) cols.getOrElse(teacherIdx) { "" } else cols.getOrElse(1) { "" }
                val position = if (positionIdx >= 0) cols.getOrElse(positionIdx) { "" } else cols.getOrElse(2) { "" }
                val dayStr = if (dayIdx >= 0) cols.getOrElse(dayIdx) { "" } else cols.getOrElse(3) { "" }
                val sectionStr = if (sectionIdx >= 0) cols.getOrElse(sectionIdx) { "" } else cols.getOrElse(4) { "" }
                val weeksStr = if (weeksIdx >= 0) cols.getOrElse(weeksIdx) { "" } else cols.getOrElse(5) { "" }

                val day = parseDay(dayStr)
                val (startSec, endSec) = parseSectionRange(sectionStr)
                val weeks = parseWeeksSimple(weeksStr)

                if (name.isBlank() || day !in 1..7) return@mapNotNull null

                ImportCourseJsonModel(
                    name = name, teacher = teacher,
                    position = position.ifBlank { "待定" },
                    day = day, startSection = startSec, endSection = endSec,
                    weeks = weeks.ifEmpty { (1..16).toList() }
                )
            }

            if (courses.isEmpty()) return ParseResult.Error("CSV中未找到有效课程")
            ParseResult.Success(CourseTableImportModel(courses = courses), "CSV")
        } catch (e: Exception) {
            ParseResult.Error("CSV解析失败: ${e.message}")
        }
    }

    private fun hasCsvHeader(header: List<String>): Boolean {
        return header.any { it.contains("课程") || it.contains("name") || it.contains("教师") || it.contains("teacher") }
    }

    // ========== HTML 表格 ==========

    private fun parseHtmlTable(content: String): ParseResult {
        return try {
            val rows = Regex("""(?is)<tr[^>]*>(.*?)</tr>""")
                .findAll(content).map { it.groupValues[1] }.toList()

            if (rows.isEmpty()) return ParseResult.Error("未找到HTML表格行")

            val courses = mutableListOf<ImportCourseJsonModel>()
            for (row in rows) {
                val cells = Regex("""(?is)<t[dh][^>]*>(.*?)</t[dh]>""")
                    .findAll(row).map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }.toList()

                if (cells.size < 3) continue
                val name = cells.getOrElse(0) { "" }
                if (name.isBlank() || name.contains("课程") || name.contains("课名")) continue

                val teacher = cells.getOrElse(1) { "" }
                val position = cells.getOrElse(2) { "" }
                val day = parseDay(cells.getOrElse(3) { "" })
                val (startSec, endSec) = parseSectionRange(cells.getOrElse(4) { "" })
                val weeks = parseWeeksSimple(cells.getOrElse(5) { "" })

                if (name.isNotBlank() && day in 1..7) {
                    courses.add(ImportCourseJsonModel(
                        name = name, teacher = teacher,
                        position = position.ifBlank { "待定" },
                        day = day, startSection = startSec, endSection = endSec,
                        weeks = weeks.ifEmpty { (1..16).toList() }
                    ))
                }
            }

            if (courses.isEmpty()) return ParseResult.Error("HTML表格中未找到有效课程")
            ParseResult.Success(CourseTableImportModel(courses = courses), "HTML表格")
        } catch (e: Exception) {
            ParseResult.Error("HTML解析失败: ${e.message}")
        }
    }

    // ========== 纯文本 ==========

    private fun parsePlainText(content: String): ParseResult {
        return try {
            val lines = content.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return ParseResult.Error("文本内容为空")

            val courses = lines.mapNotNull { line ->
                val cols = line.split("\t", ",", "|", "  ").map { it.trim() }.filter { it.isNotBlank() }
                if (cols.size < 2) return@mapNotNull null

                val name = cols.getOrElse(0) { "" }
                val teacher = cols.getOrElse(1) { "" }
                val position = cols.getOrElse(2) { "" }
                val day = parseDay(cols.getOrElse(3) { "" })
                val (startSec, endSec) = parseSectionRange(cols.getOrElse(4) { "" })
                val weeks = parseWeeksSimple(cols.getOrElse(5) { "" })

                if (name.isBlank() || day !in 1..7) return@mapNotNull null

                ImportCourseJsonModel(
                    name = name, teacher = teacher,
                    position = position.ifBlank { "待定" },
                    day = day, startSection = startSec, endSection = endSec,
                    weeks = weeks.ifEmpty { (1..16).toList() }
                )
            }

            if (courses.isEmpty()) {
                return ParseResult.Error("无法识别文本格式。请确保每行包含：课程名 教师 教室 星期 节次 周次")
            }
            ParseResult.Success(CourseTableImportModel(courses = courses), "纯文本")
        } catch (e: Exception) {
            ParseResult.Error("文本解析失败: ${e.message}")
        }
    }

    // ========== 通用工具 ==========

    private fun parseDay(s: String): Int {
        if (s.isBlank()) return 0
        s.toIntOrNull()?.let { if (it in 1..7) return it }

        val cnMap = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
        for ((k, v) in cnMap) {
            if (s.contains("周$k") || s.contains("星期$k") || s == k) return v
        }

        val enMap = mapOf("mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6, "sun" to 7)
        val lower = s.lowercase()
        for ((k, v) in enMap) {
            if (lower.contains(k)) return v
        }
        return 0
    }

    private fun parseSectionRange(s: String): Pair<Int?, Int?> {
        if (s.isBlank()) return null to null
        Regex("""(\d+)\s*[-~至到]\s*(\d+)""").find(s)?.let {
            return it.groupValues[1].toInt() to it.groupValues[2].toInt()
        }
        Regex("""(\d+)""").find(s)?.let {
            val n = it.groupValues[1].toInt()
            return n to n
        }
        return null to null
    }

    private fun parseWeeksSimple(s: String): List<Int> {
        if (s.isBlank()) return emptyList()
        val weeks = mutableSetOf<Int>()
        s.split(",", "，", "、", ";", " ").forEach { seg ->
            val trimmed = seg.trim()
            Regex("""(\d+)\s*[-~至到]\s*(\d+)""").find(trimmed)?.let {
                val start = it.groupValues[1].toInt()
                val end = it.groupValues[2].toInt()
                for (w in minOf(start, end)..maxOf(start, end)) weeks.add(w)
            } ?: Regex("""(\d+)""").find(trimmed)?.let {
                weeks.add(it.groupValues[1].toInt())
            }
        }
        return weeks.sorted()
    }
}
