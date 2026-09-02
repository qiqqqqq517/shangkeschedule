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

        // 候选解析器按置信度排序；任何一个成功立即返回，全部失败时返回最后一个错误。
        // 这样修复了此前「被误判成 CSV 后直接报错、不再尝试其他格式」导致文本导入不可用的问题。
        val attempts = mutableListOf<Pair<String, () -> ParseResult>>()

        if (trimmed.startsWith("【来自WakeUp课程表】") || (trimmed.contains("WakeUp", ignoreCase = true) && trimmed.contains("{"))) {
            attempts.add("WakeUp文本" to { parseWakeUpShareText(trimmed) })
        }
        if (trimmed.startsWith("{")) {
            attempts.add("JSON" to { parseWakeUpJson(trimmed) })
        }
        if (trimmed.contains("VEVENT")) {
            attempts.add("ICS" to { parseIcs(trimmed) })
        }
        if (trimmed.contains("<table", ignoreCase = true)) {
            attempts.add("HTML表格" to { parseHtmlTable(trimmed) })
        }
        if (isCsvContent(trimmed)) {
            attempts.add("CSV" to { parseCsv(trimmed) })
        }
        attempts.add("纯文本" to { parsePlainText(trimmed) })

        var lastError: ParseResult.Error? = null
        for ((_, attempt) in attempts) {
            when (val result = attempt()) {
                is ParseResult.Success -> return result
                is ParseResult.Error -> lastError = result
            }
        }
        return lastError ?: ParseResult.Error("无法识别内容格式")
    }

    /**
     * 按指定格式类别解析（二级分类页强制格式用）。
     * @param format 目标格式；null 时等同 parseAuto
     */
    fun parseWithFormat(content: String, format: TextImportFormat?): ParseResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return ParseResult.Error("内容为空")
        return when (format) {
            null -> parseAuto(trimmed)
            TextImportFormat.WAKEUP -> parseWakeUpShareText(trimmed)
                .takeIfErrorThen { parseWakeUpJson(trimmed) }
            TextImportFormat.JSON -> parseWakeUpJson(trimmed)
            TextImportFormat.ICS -> parseIcs(trimmed)
            TextImportFormat.CSV -> parseCsv(trimmed)
            TextImportFormat.HTML -> parseHtmlTable(trimmed)
            TextImportFormat.PLAIN -> parsePlainText(trimmed)
        }
    }

    /** 格式强制解析失败时退回自动嗅探（WakeUp 分享文本可能混入额外文字） */
    private fun ParseResult.takeIfErrorThen(fallback: () -> ParseResult): ParseResult =
        if (this is ParseResult.Error) fallback() else this

    /** Excel 二维网格解析（ExcelScheduleParser.extractGrid 的后续步骤） */
    fun parseExcelGrid(grid: List<List<String>>): ParseResult {
        if (grid.isEmpty()) return ParseResult.Error("Excel 内容为空")
        parseGridTimetable(grid).let { result -> if (result is ParseResult.Success) return result }
        return parseExcelAsList(grid)
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
            val events = parseIcsEvents(content)
            if (events.isEmpty()) return ParseResult.Error("ICS中未找到有效课程事件")

            // 基准日期：所有事件 DTSTART 最小日期作为「第 1 周」（绝对日期 → 相对周次）
            val baseDate = events.mapNotNull { ev ->
                ev["DTSTART"]?.substringBefore('T')?.replace("-", "")?.takeIf { it.length == 8 }
            }.minOrNull()
            val baseDays = if (baseDate != null) {
                icsDaysFromYmd(baseDate.substring(0, 4).toInt(), baseDate.substring(4, 6).toInt(), baseDate.substring(6, 8).toInt())
            } else 0
            val hasBase = baseDate != null

            val courses = events.mapNotNull { ev ->
                val summary = ev["SUMMARY"] ?: return@mapNotNull null
                val startStr = ev["DTSTART"] ?: return@mapNotNull null
                val endStr = ev["DTEND"] ?: return@mapNotNull null

                val summaryClean = icsUnescape(summary)
                val parts = summaryClean.split("@", "-", " ", "（", "(").map { it.trim() }.filter { it.isNotBlank() }
                val name = parts.firstOrNull() ?: summaryClean
                val teacherHint = parts.getOrNull(1) ?: ""

                // DESCRIPTION（WakeUp 导出格式：第1行节次、第2行地点、第3行教师）
                val desc = icsUnescape(ev["DESCRIPTION"] ?: "")
                val descLines = desc.lines().map { it.trim() }.filter { it.isNotBlank() }
                val descSections = Regex("第\\s*(\\d+)\\s*[-~至]\\s*(\\d+)\\s*节").find(desc)

                val day = parseIcsDay(startStr)

                var startSection: Int? = null
                var endSection: Int? = null
                if (descSections != null) {
                    startSection = descSections.groupValues[1].toIntOrNull()
                    endSection = descSections.groupValues[2].toIntOrNull()
                }
                if (startSection == null || endSection == null) {
                    val startMinutes = parseIcsMinutes(startStr)
                    val endMinutes = parseIcsMinutes(endStr)
                    startSection = if (startMinutes >= 0) ((startMinutes - 8 * 60) / 55) + 1 else null
                    endSection = if (endMinutes >= 0) ((endMinutes - 8 * 60) / 55) + 1 else null
                }

                var position = if (descLines.size >= 2) descLines[1] else ""
                var teacher = if (descLines.size >= 3) descLines[2] else ""
                if (position.isBlank()) {
                    val loc = icsUnescape(ev["LOCATION"] ?: "")
                    val lp = loc.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (lp.size >= 2) {
                        position = lp.dropLast(1).joinToString(" ")
                        if (teacher.isBlank()) teacher = lp.last()
                    } else {
                        position = loc.trim()
                    }
                }
                if (teacher.isBlank()) teacher = teacherHint

                val weeks = parseIcsWeeks(ev["RRULE"], startStr, hasBase, baseDays)

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

    /** 解析 VEVENT，跳过 VALARM 子块（避免 VALARM 的 DESCRIPTION 等字段覆盖 VEVENT 字段） */
    private fun parseIcsEvents(content: String): List<Map<String, String>> {
        val events = mutableListOf<Map<String, String>>()
        val lines = content.lines()
        var i = 0
        while (i < lines.size) {
            if (lines[i].trim() == "BEGIN:VEVENT") {
                val event = mutableMapOf<String, String>()
                i++
                while (i < lines.size && lines[i].trim() != "END:VEVENT") {
                    if (lines[i].trim() == "BEGIN:VALARM") {
                        i++
                        while (i < lines.size && lines[i].trim() != "END:VALARM") i++
                    }
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
        return events
    }

    /** ICS 转义还原：\, → ,  \n → 换行  \; → ;  \\ → \ */
    private fun icsUnescape(s: String): String =
        s.replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").replace("\\n", "\n")

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

    /**
     * ICS 周次计算：
     * - RRULE 有 COUNT：从 startWeek 起按 INTERVAL 步进共 count 次
     * - 否则有 UNTIL（UTC）：UNTIL 转当地（+8h，可能跨天），最后实例日期 = 当地日期 - 1 天，计算结束周
     * - 都没有：单周
     * startWeek 由事件 DTSTART 相对基准日期（所有事件最早日期 = 第1周）推算
     */
    private fun parseIcsWeeks(rrule: String?, startStr: String, hasBase: Boolean, baseDays: Int): List<Int> {
        val rr = rrule ?: ""
        val interval = Regex("INTERVAL=(\\d+)").find(rr)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val count = Regex("COUNT=(\\d+)").find(rr)?.groupValues?.get(1)?.toIntOrNull()

        val dateStr = startStr.substringBefore('T').replace("-", "")
        val startWeek = if (hasBase && dateStr.length == 8) {
            val sd = icsDaysFromYmd(dateStr.substring(0, 4).toInt(), dateStr.substring(4, 6).toInt(), dateStr.substring(6, 8).toInt())
            ((sd - baseDays) / 7) + 1
        } else 1

        if (count != null && count > 0) {
            return (0 until count).map { startWeek + it * interval }
        }

        val untilM = Regex("UNTIL=(\\d{8})T(\\d{6})Z?").find(rr)?.groupValues
        if (untilM != null && hasBase) {
            val ud = icsDaysFromYmd(untilM[1].substring(0, 4).toInt(), untilM[1].substring(4, 6).toInt(), untilM[1].substring(6, 8).toInt())
            val uh = untilM[2].substring(0, 2).toIntOrNull() ?: 0
            val um = untilM[2].substring(2, 4).toIntOrNull() ?: 0
            // UTC +8h 后若跨天则 +1 天；最后实例日期 = 当地日期 - 1 天（UNTIL 覆盖到当天结束）
            val utcDays = if (uh * 60 + um + 8 * 60 >= 24 * 60) ud + 1 else ud
            val lastInstanceDays = utcDays - 1
            val endWeek = ((lastInstanceDays - baseDays) / 7) + 1
            if (endWeek >= startWeek) {
                return (startWeek..endWeek step interval).toList()
            }
        }

        return listOf(startWeek)
    }

    /** Gregorian 历法日期到 1970-01-01 的天数（Howard Hinnant days_from_civil） */
    private fun icsDaysFromYmd(y: Int, m: Int, d: Int): Int {
        var yy = y
        if (m <= 2) yy -= 1
        val era = if (yy >= 0) yy / 400 else (yy - 399) / 400
        val yoe = yy - era * 400
        val mp = if (m > 2) m - 3 else m + 9
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    // ========== CSV ==========

    private fun isCsvContent(content: String): Boolean {
        val firstLine = content.lines().firstOrNull() ?: return false
        // 至少 2 个逗号才可能是表格（避免把含单个逗号的普通句子误判为 CSV）
        return firstLine.split(",").size >= 3 && firstLine.length < 500
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
                // 先按强分隔符（制表符/逗号/竖线/连续空格/中文分隔符）切分；
                // 切不出多列时退回单空格切分（用户手打的课表常为单空格分隔）
                val cols = splitCourseLine(line)
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

    /** 课程行切分：强分隔符优先，其次单空格（用户手打的课表常为单空格分隔） */
    private fun splitCourseLine(line: String): List<String> {
        val strong = line.split("\t", ",", "|", "，", "、", "  ").map { it.trim() }.filter { it.isNotBlank() }
        if (strong.size >= 3) return strong
        val single = line.trim().split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        return if (single.size > strong.size) single else strong
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

    // ========== Excel 网格课表 ==========

    private val cnDayMap = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
    private val enDayMap = mapOf("mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6, "sun" to 7)

    /**
     * 策略一：网格式课表（超级课程表/QQ群课表/教务导出常见形态）。
     * 结构：表头行含 ≥2 个「星期X/周X」列；行首为节次标签列（第一二节/第11 12节 等）；
     * 课程单元格常见 ◇ 分隔格式：名称(56学时,3.5学分)◇1-12(1,2)◇教5-305【普通教室】◇薛丹。
     */
    private fun parseGridTimetable(grid: List<List<String>>): ParseResult {
        // 1. 定位表头行与星期列
        val headerRowIndex = grid.indexOfFirst { row ->
            row.count { dayOfHeaderStrict(it) != null } >= 2
        }
        if (headerRowIndex == -1) return ParseResult.Error("未识别为网格课表")

        val dayColumns = sortedMapOf<Int, Int>()
        grid[headerRowIndex].forEachIndexed { col, cell ->
            dayOfHeaderStrict(cell)?.let { day -> dayColumns[col] = day }
        }
        if (dayColumns.isEmpty()) return ParseResult.Error("未识别为网格课表")

        val firstDayCol = dayColumns.firstKey()
        val maxLabelCol = firstDayCol - 1

        // 2. 逐行解析课程单元格
        val courses = mutableListOf<ImportCourseJsonModel>()
        for (r in (headerRowIndex + 1) until grid.size) {
            val row = grid[r]
            if (row.all { it.isBlank() }) continue

            // 行首标签列：解析节次范围（第一二节 / 第11 12节），作为单元格缺省节次
            var labelSections: Pair<Int, Int>? = null
            for (c in 0..maxLabelCol) {
                val labelText = row.getOrElse(c) { "" }
                if (labelText.isNotBlank()) {
                    parseSectionLabel(labelText)?.let { labelSections = it }
                }
            }

            for ((col, day) in dayColumns) {
                val cellText = row.getOrElse(col) { "" }
                if (cellText.isBlank()) continue
                cellText.split('\n')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { line ->
                        parseGridCellLine(line, day, labelSections)?.let { courses.add(it) }
                    }
            }
        }

        val uniqueCourses = courses
            .filter { it.name.isNotBlank() && it.day in 1..7 }
            .distinctBy { "${it.name}|${it.day}|${it.startSection}|${it.endSection}|${it.weeks}" }

        if (uniqueCourses.isEmpty()) return ParseResult.Error("网格课表中未识别到课程")
        return ParseResult.Success(CourseTableImportModel(courses = uniqueCourses), "Excel 网格课表")
    }

    /** 严格匹配表头单元格：星期一 / 周一 / 礼拜天 / Mon 等；防止把课程内容误判为表头 */
    private fun dayOfHeaderStrict(s: String): Int? {
        val t = s.trim()
        if (t.isEmpty() || t.length > 5) return null
        Regex("""^(?:星期|周|礼拜)?\s*([一二三四五六日天])$""").find(t)?.let { m ->
            return cnDayMap[m.groupValues[1].toString()] ?: 0
        }
        val lower = t.lowercase()
        return enDayMap[lower.take(3)]
    }

    /** 节次标签解析：第一二节 → 1..2；第11 12节 → 11..12；第九十节 → 9..10；第3节 → 3..3 */
    private fun parseSectionLabel(text: String): Pair<Int, Int>? {
        val t = text.trim()
        // 阿拉伯数字区间/相邻：1-2节、第1~2节、第11 12节
        Regex("""第?\s*(\d{1,2})\s*[-~至到]\s*(\d{1,2})\s*节?""").find(t)?.let {
            val a = it.groupValues[1].toInt()
            val b = it.groupValues[2].toInt()
            return minOf(a, b) to maxOf(a, b)
        }
        Regex("""第\s*(\d{1,2})\s+(\d{1,2})\s*节""").find(t)?.let {
            return it.groupValues[1].toInt() to it.groupValues[2].toInt()
        }
        Regex("""第\s*(\d{1,2})\s*节""").find(t)?.let {
            val n = it.groupValues[1].toInt()
            return n to n
        }
        // 中文数字：第一二节、第九十节、第十一十二节
        Regex("""第([一二三四五六七八九十]+)节""").find(t)?.let {
            val nums = chineseSectionNumbers(it.groupValues[1])
            if (nums.isNotEmpty()) return nums.first() to nums.last()
        }
        return null
    }

    /**
     * 中文节次序列 → 节次号列表。
     * 规则：十后接数字 → 10+X（十一=11）；否则十=10（第九十节 → 9,10）。
     */
    private fun chineseSectionNumbers(s: String): List<Int> {
        val digits = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        val out = mutableListOf<Int>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '十' -> {
                    val next = if (i + 1 < s.length) s[i + 1] else ' '
                    if (digits.containsKey(next)) {
                        out.add(10 + digits[next]!!)
                        i++
                    } else {
                        out.add(10)
                    }
                }
                digits.containsKey(c) -> out.add(digits[c]!!)
            }
            i++
        }
        return out
    }

    /** 单元格单行课程解析（◇ 分隔格式优先，其次通用松散提取） */
    private fun parseGridCellLine(line: String, day: Int, fallbackSections: Pair<Int, Int>?): ImportCourseJsonModel? {
        val text = line.trim()
        if (text.isBlank()) return null
        // 行内节次标签（如「第一节」），不是课程
        if (!text.contains('◇') && text.length <= 8 && text.contains('节') && !text.contains(Regex("""\d\s*[-~]\s*\d"""))) return null

        return if (text.contains('◇')) parseDiamondCourse(text, day, fallbackSections)
        else parseLooseCourse(text, day, fallbackSections)
    }

    /**
     * ◇ 分隔格式：名称(56学时,3.5学分)◇1-12(1,2)◇教5-305【普通教室】◇薛丹
     * 兼容实践课程行：实践课程:XXX◇姚宁(2周)/13-14周
     */
    private fun parseDiamondCourse(text: String, day: Int, fallbackSections: Pair<Int, Int>?): ImportCourseJsonModel? {
        val segs = text.split('◇').map { it.trim() }.filter { it.isNotBlank() }
        if (segs.isEmpty()) return null

        var name = segs[0]
        var remark: String? = null
        // 实践课程: 前缀剥离
        Regex("""^实践(?:课程|环节|课)?[:：]\s*(.+)$""").find(name)?.let { m ->
            remark = "实践课程"
            name = m.groupValues[1].trim()
        }

        var credit: String? = null
        // 名称(56学时,3.5学分) → 名称 + 学分
        Regex("""^(.*?)\s*[（(]([^()（）]*学分[^()（）]*)[)）]\s*$""").find(name)?.let { m ->
            name = m.groupValues[1].trim()
            credit = Regex("""([0-9]+(?:\.[0-9]+)?)\s*学分""").find(m.groupValues[2])?.groupValues?.get(1)
        }
        if (name.isBlank()) return null

        var weeks: List<Int> = emptyList()
        var sections: Pair<Int, Int>? = fallbackSections

        // 周次(节次)：1-12(1,2) / 1-16(单) / 1-16(双)
        val wkSec = Regex("""^(\d{1,2})\s*[-~]\s*(\d{1,2})\s*[（(]\s*([^()（）]*)\s*[)）]$""")
            .find(segs.getOrElse(1) { "" })
        if (wkSec != null) {
            val a = wkSec.groupValues[1].toInt()
            val b = wkSec.groupValues[2].toInt()
            weeks = (minOf(a, b)..maxOf(a, b)).toList()
            val token = wkSec.groupValues[3].trim()
            weeks = applyOddEvenFilter(weeks, token)
            parseSectionToken(token)?.let { sections = it }
        } else {
            // 退路：任意「a-b周」片段（实践课程 13-14周 等）
            Regex("""(\d{1,2})\s*[-~至到]\s*(\d{1,2})\s*周""").find(text)?.let { m ->
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                weeks = (minOf(a, b)..maxOf(a, b)).toList()
            }
        }

        var position = ""
        var teacher = ""
        if (segs.size >= 3) {
            position = segs[2].replace(Regex("""【[^】]*】"""), "").trim()
            teacher = segs.getOrElse(3) { "" }.trim()
        } else if (segs.size == 2) {
            // 实践课程形态：名称◇教师(2周)/13-14周
            teacher = segs[1].substringBefore('(').substringBefore('（').trim()
        }

        return ImportCourseJsonModel(
            name = name,
            teacher = teacher,
            position = position.ifBlank { "待定" },
            day = day,
            startSection = sections?.first,
            endSection = sections?.second,
            weeks = weeks.ifEmpty { (1..16).toList() },
            credit = credit,
            remark = remark
        )
    }

    /** 非 ◇ 单元格：名称 周次 节次 教室 教师 松散提取 */
    private fun parseLooseCourse(text: String, day: Int, fallbackSections: Pair<Int, Int>?): ImportCourseJsonModel? {
        var working = text

        var weeks: List<Int> = emptyList()
        Regex("""(\d{1,2})\s*[-~至到]\s*(\d{1,2})\s*周""").find(working)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            weeks = (minOf(a, b)..maxOf(a, b)).toList()
            working = working.replace(m.value, " ")
        }

        var sections: Pair<Int, Int>? = fallbackSections
        Regex("""第?\s*(\d{1,2})\s*[-~至到]\s*(\d{1,2})\s*节""").find(working)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            sections = minOf(a, b) to maxOf(a, b)
            working = working.replace(m.value, " ")
        }

        val tokens = working.split(Regex("""[\s,，、|]+""")).filter { it.isNotBlank() }
        val name = tokens.firstOrNull() ?: return null
        if (name.length > 30) return null

        val roomHint = Regex("""(楼|室|馆|厅|栋|层|教[\-A-Z0-9]|#|栋)""")
        val position = tokens.drop(1).firstOrNull { roomHint.containsMatchIn(it) } ?: ""
        val teacher = tokens.drop(1).filter { it != position }.joinToString(" ")

        return ImportCourseJsonModel(
            name = name,
            teacher = teacher,
            position = position.ifBlank { "待定" },
            day = day,
            startSection = sections?.first,
            endSection = sections?.second,
            weeks = weeks.ifEmpty { (1..16).toList() }
        )
    }

    /** (单)/(双) 周次过滤 */
    private fun applyOddEvenFilter(weeks: List<Int>, token: String): List<Int> = when {
        token.contains("单") -> weeks.filter { it % 2 == 1 }
        token.contains("双") -> weeks.filter { it % 2 == 0 }
        else -> weeks
    }

    /** 节次 token 解析：1,2 → 1..2；3 → 3..3；单/双 → null（仅用于周次过滤） */
    private fun parseSectionToken(token: String): Pair<Int, Int>? {
        val digits = Regex("""\d+""").findAll(token).map { it.value.toInt() }.toList()
        return when {
            digits.size >= 2 -> minOf(digits.first(), digits.last()) to maxOf(digits.first(), digits.last())
            digits.size == 1 -> digits[0] to digits[0]
            else -> null
        }
    }

    /**
     * 策略二：列表式课表（一行一门课）。
     * 有表头 → 关键字映射列；无表头 → 位置映射：名称/教师/教室/星期/节次/周次。
     */
    private fun parseExcelAsList(grid: List<List<String>>): ParseResult {
        // 定位表头行
        val headerIdx = grid.indexOfFirst { row ->
            row.any { it.contains("课程") || it.lowercase().contains("name") || it.contains("教师") || it.lowercase().contains("teacher") }
        }

        val nameIdx: Int; val teacherIdx: Int; val positionIdx: Int
        val dayIdx: Int; val sectionIdx: Int; val weeksIdx: Int
        val dataRows: List<List<String>>

        if (headerIdx >= 0) {
            val header = grid[headerIdx].map { it.trim().lowercase() }
            nameIdx = header.indexOfFirst { it.contains("课程") || it.contains("课名") || it.contains("name") }
            teacherIdx = header.indexOfFirst { it.contains("教师") || it.contains("老师") || it.contains("teacher") }
            positionIdx = header.indexOfFirst { it.contains("教室") || it.contains("地点") || it.contains("位置") || it.contains("场地") || it.contains("location") || it.contains("room") }
            dayIdx = header.indexOfFirst { it.contains("星期") || it.contains("礼拜") || it.contains("周几") || it.contains("day") }
            sectionIdx = header.indexOfFirst { it.contains("节次") || it.contains("节") || it.contains("section") }
            weeksIdx = header.indexOfFirst { it.contains("周次") || it.contains("周") || it.contains("week") }
            dataRows = grid.drop(headerIdx + 1)
        } else {
            nameIdx = 0; teacherIdx = 1; positionIdx = 2; dayIdx = 3; sectionIdx = 4; weeksIdx = 5
            dataRows = grid
        }

        val skipKeywords = listOf("节次", "星期", "课程名", "课程", "教师", "教室", "name", "teacher", "上午", "下午", "晚上")
        val courses = dataRows.mapNotNull { row ->
            val get = { idx: Int -> if (idx >= 0) row.getOrElse(idx) { "" } else "" }
            val name = get(nameIdx).trim()
            if (name.isBlank() || name.length > 30) return@mapNotNull null
            if (skipKeywords.any { name.contains(it) }) return@mapNotNull null

            val day = parseDay(get(dayIdx))
            if (day !in 1..7) return@mapNotNull null

            val (startSec, endSec) = parseSectionRange(get(sectionIdx))
            val weeks = parseWeeksSimple(get(weeksIdx))

            ImportCourseJsonModel(
                name = name,
                teacher = get(teacherIdx).trim(),
                position = get(positionIdx).trim().ifBlank { "待定" },
                day = day,
                startSection = startSec,
                endSection = endSec,
                weeks = weeks.ifEmpty { (1..16).toList() }
            )
        }

        if (courses.isEmpty()) return ParseResult.Error("Excel 中未识别到课程（请确认是课表文件）")
        return ParseResult.Success(
            CourseTableImportModel(courses = courses),
            if (headerIdx >= 0) "Excel 列表课表" else "Excel 位置映射"
        )
    }
}
