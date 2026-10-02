package com.shangkeschedule.data.codec

import com.shangkeschedule.data.model.CourseImportExport
import com.shangkeschedule.data.model.CourseImportExport.CourseConfigJsonModel
import com.shangkeschedule.data.model.CourseImportExport.CourseTableExportModel
import com.shangkeschedule.data.model.CourseImportExport.CourseTableImportModel
import com.shangkeschedule.data.model.CourseImportExport.ImportCourseJsonModel
import com.shangkeschedule.data.model.CourseImportExport.SchemeMetaJsonModel
import com.shangkeschedule.data.model.CourseImportExport.TimeSlotJsonModel
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * 「课表分享串」编解码（v4.66.0 / D1）。
 *
 * 文本形态：`SK1:` + Base64Url(CBOR(瘦身载荷)) + `#` + CRC32（8 位十六进制小写）
 *
 * 为什么不用 gzip/DEFLATE（原方案写法）：
 * KMP 三端没有统一的压缩 API——`java.util.zip` 只在 Android/JVM 有，iosMain 用不了，
 * 而本机也无法编译验证 iOS 目标。因此改为「结构化瘦身」而不是「压缩字节」：
 * 1. 字符串字典：课程名/教师/地点/备注/学分/考核方式去重后进 `dict`，课程里只存下标；
 * 2. 周次区间化：`[1,2,3,7,8]` → `[1,3,7,8]`，不逐周存数字；
 * 3. CBOR 二进制（复用 [CourseImportExport.cbor]，与备份同族），字段名不进字节流；
 * 4. 精简模式（[Mode.COMPACT]）：只保留 课名 / 地点 / 星期 / 节次 / 周次 与上课时间，
 *    供二维码容量不足时使用；完整模式（[Mode.FULL]）额外带教师、备注、学分、考核方式、
 *    作息时段、作息方案元信息与课程 id，导入效果与 JSON 文件导入一致。
 *
 * 载荷只含课表本身（不含成绩、课堂笔记、设置等其它表的数据）；导入侧直接交给既有
 * [com.shangkeschedule.data.repository.CourseConversionRepository.importCourseTableFromJson]。
 */
object CourseShareCodec {

    const val PREFIX = "SK1:"

    private const val PAYLOAD_VERSION = 1
    private const val CRC_LENGTH = 8
    private const val NO_INDEX = -1

    /** 周次区间展开时的上限保护，避免损坏的串生成超大课程周次表 */
    private const val MAX_WEEK_RANGE_SPAN = 60

    /** 分享内容模式 */
    enum class Mode { FULL, COMPACT }

    sealed interface DecodeResult {
        data class Success(val model: CourseTableImportModel, val tableName: String?) : DecodeResult

        /** 文本里没有 [PREFIX]，不是分享串（调用方应回退到其它解析器） */
        data object NotAShareCode : DecodeResult

        /** 有前缀但校验/解码失败，多半是复制不完整或被改动 */
        data object Invalid : DecodeResult
    }

    /** 廉价预判：文本中是否出现分享串前缀（允许用户连同说明文字一起粘贴） */
    fun looksLikeCode(text: String): Boolean = text.contains(PREFIX)

    @OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
    fun encode(model: CourseTableExportModel, mode: Mode, tableName: String? = null): String {
        val payload = toPayload(model, mode, tableName)
        val bytes = CourseImportExport.cbor.encodeToByteArray(SharePayload.serializer(), payload)
        return PREFIX + Base64.UrlSafe.encode(bytes) + "#" + crc32Hex(bytes)
    }

    /**
     * 分享串文本长度上限（64 KB）。
     *
     * 分享串本身只有几 KB；这里挡的是「用户把几 MB 聊天记录整段粘进来」的场景 ——
     * 解码前先做长度判断，避免 filterNot + Base64 + CBOR 按比例吃内存。
     */
    const val MAX_SHARE_TEXT_LENGTH = 64 * 1024

    @OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
    fun decode(text: String): DecodeResult {
        if (text.length > MAX_SHARE_TEXT_LENGTH) return DecodeResult.Invalid
        // 容忍换行/空格（聊天软件经常折行），但 Base64Url 不忽略空白，必须自己剥掉
        val compact = text.filterNot { it.isWhitespace() }
        val start = compact.indexOf(PREFIX)
        if (start < 0) return DecodeResult.NotAShareCode

        val body = compact.substring(start + PREFIX.length)
        val separator = body.lastIndexOf('#')
        if (separator <= 0) return DecodeResult.Invalid

        val encoded = body.substring(0, separator)
        val expectedCrc = body.substring(separator + 1).take(CRC_LENGTH).lowercase()
        if (expectedCrc.length != CRC_LENGTH) return DecodeResult.Invalid

        val bytes = runCatching { Base64.UrlSafe.decode(encoded) }.getOrNull() ?: return DecodeResult.Invalid
        if (crc32Hex(bytes) != expectedCrc) return DecodeResult.Invalid

        val payload = runCatching {
            CourseImportExport.cbor.decodeFromByteArray(SharePayload.serializer(), bytes)
        }.getOrNull() ?: return DecodeResult.Invalid
        if (payload.v != PAYLOAD_VERSION) return DecodeResult.Invalid

        return DecodeResult.Success(toModel(payload), payload.name)
    }

    // ---------------------------------------------------------------- 编码

    private fun toPayload(model: CourseTableExportModel, mode: Mode, tableName: String?): SharePayload {
        val full = mode == Mode.FULL
        val dict = StringDictionary()

        val courses = model.courses.map { course ->
            ShareCourse(
                n = dict.add(course.name.trim()),
                d = course.day,
                s = course.startSection,
                e = course.endSection,
                w = weeksToRanges(course.weeks),
                p = dict.addOrNull(course.position) ?: NO_INDEX,
                t = if (full) dict.addOrNull(course.teacher) ?: NO_INDEX else NO_INDEX,
                r = if (full) dict.addOrNull(course.remark) ?: NO_INDEX else NO_INDEX,
                c = course.color,
                cr = if (full) dict.addOrNull(course.credit) ?: NO_INDEX else NO_INDEX,
                am = if (full) dict.addOrNull(course.assessmentMethod) ?: NO_INDEX else NO_INDEX,
                lab = full && course.isLab,
                ct = course.isCustomTime,
                cs = course.customStartTime,
                ce = course.customEndTime,
                id = if (full) course.id else null
            )
        }

        val slots = if (full) {
            model.timeSlots.map { slot ->
                ShareSlot(
                    n = slot.number,
                    s = slot.startTime,
                    e = slot.endTime,
                    a = dict.addOrNull(slot.alias) ?: NO_INDEX,
                    sch = dict.add(slot.schemeId)
                )
            }
        } else {
            emptyList()
        }

        val schemes = if (full) {
            model.timeSlotSchemes.map { scheme ->
                ShareScheme(id = scheme.schemeId, sm = scheme.startMonthDay, em = scheme.endMonthDay)
            }
        } else {
            emptyList()
        }

        return SharePayload(
            v = PAYLOAD_VERSION,
            name = tableName?.trim()?.takeIf { it.isNotEmpty() },
            dict = dict.values(),
            courses = courses,
            slots = slots,
            schemes = schemes,
            config = ShareConfig(
                sd = model.config.semesterStartDate,
                tw = model.config.semesterTotalWeeks,
                cd = model.config.defaultClassDuration,
                bd = model.config.defaultBreakDuration,
                fd = model.config.firstDayOfWeek,
                cs = model.config.currentSchemeId,
                a = model.config.autoSwitchScheme,
                sw = model.config.showWeekends
            )
        )
    }

    // ---------------------------------------------------------------- 解码

    private fun toModel(payload: SharePayload): CourseTableImportModel {
        val dict = payload.dict
        return CourseTableImportModel(
            courses = payload.courses.map { course ->
                ImportCourseJsonModel(
                    id = course.id,
                    name = dict.getOrNull(course.n).orEmpty(),
                    teacher = dict.getOrNull(course.t).orEmpty(),
                    position = dict.getOrNull(course.p).orEmpty(),
                    day = course.d,
                    startSection = course.s,
                    endSection = course.e,
                    weeks = rangesToWeeks(course.w),
                    isCustomTime = course.ct,
                    customStartTime = course.cs,
                    customEndTime = course.ce,
                    color = course.c,
                    remark = dict.getOrNull(course.r),
                    credit = dict.getOrNull(course.cr),
                    assessmentMethod = dict.getOrNull(course.am),
                    isLab = course.lab
                )
            },
            timeSlots = payload.slots.map { slot ->
                TimeSlotJsonModel(
                    number = slot.n,
                    startTime = slot.s,
                    endTime = slot.e,
                    alias = dict.getOrNull(slot.a),
                    schemeId = dict.getOrNull(slot.sch) ?: "default"
                )
            },
            config = payload.config?.let { config ->
                CourseConfigJsonModel(
                    semesterStartDate = config.sd,
                    semesterTotalWeeks = config.tw,
                    defaultClassDuration = config.cd,
                    defaultBreakDuration = config.bd,
                    firstDayOfWeek = config.fd,
                    currentSchemeId = config.cs,
                    autoSwitchScheme = config.a,
                    showWeekends = config.sw
                )
            },
            timeSlotSchemes = payload.schemes.map { scheme ->
                SchemeMetaJsonModel(
                    schemeId = scheme.id,
                    startMonthDay = scheme.sm,
                    endMonthDay = scheme.em
                )
            }
        )
    }

    // ---------------------------------------------------------------- 周次区间

    /** `[1,2,3,7,8]` → `[1,3,7,8]`（区间扁平化：起点,终点,起点,终点…） */
    private fun weeksToRanges(weeks: List<Int>): List<Int> {
        val sorted = weeks.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return emptyList()

        val ranges = mutableListOf<Int>()
        var start = sorted.first()
        var previous = start
        for (index in 1 until sorted.size) {
            val current = sorted[index]
            if (current == previous + 1) {
                previous = current
                continue
            }
            ranges += start
            ranges += previous
            start = current
            previous = current
        }
        ranges += start
        ranges += previous
        return ranges
    }

    private fun rangesToWeeks(ranges: List<Int>): List<Int> {
        val weeks = mutableListOf<Int>()
        var index = 0
        while (index + 1 < ranges.size) {
            val start = ranges[index]
            val end = ranges[index + 1]
            if (end >= start && start > 0) {
                val cappedEnd = minOf(end, start + MAX_WEEK_RANGE_SPAN)
                for (week in start..cappedEnd) weeks += week
            }
            index += 2
        }
        return weeks.distinct().sorted()
    }

    // ---------------------------------------------------------------- 校验位

    private val crc32Poly = 0xEDB88320.toInt()

    private fun crc32(bytes: ByteArray): Int {
        var crc = -1
        for (byte in bytes) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor crc32Poly else crc ushr 1
            }
        }
        return crc.inv()
    }

    private fun crc32Hex(bytes: ByteArray): String =
        crc32(bytes).toUInt().toString(16).padStart(CRC_LENGTH, '0')

    // ---------------------------------------------------------------- 内部模型

    private class StringDictionary {
        private val indexByValue = LinkedHashMap<String, Int>()
        private val ordered = mutableListOf<String>()

        fun add(value: String): Int {
            indexByValue[value]?.let { return it }
            val next = ordered.size
            ordered += value
            indexByValue[value] = next
            return next
        }

        fun addOrNull(value: String?): Int? {
            val trimmed = value?.trim()
            if (trimmed.isNullOrEmpty()) return null
            return add(trimmed)
        }

        fun values(): List<String> = ordered
    }

    @Serializable
    private class SharePayload(
        val v: Int = PAYLOAD_VERSION,
        val name: String? = null,
        val dict: List<String> = emptyList(),
        val courses: List<ShareCourse> = emptyList(),
        val slots: List<ShareSlot> = emptyList(),
        val schemes: List<ShareScheme> = emptyList(),
        val config: ShareConfig? = null
    )

    @Serializable
    private class ShareCourse(
        /** 课程名在字典中的下标 */
        val n: Int,
        /** 星期（1..7） */
        val d: Int,
        val s: Int? = null,
        val e: Int? = null,
        /** 周次区间扁平化 */
        val w: List<Int> = emptyList(),
        val p: Int = NO_INDEX,
        val t: Int = NO_INDEX,
        val r: Int = NO_INDEX,
        val c: Int? = null,
        val cr: Int = NO_INDEX,
        val am: Int = NO_INDEX,
        val lab: Boolean = false,
        val ct: Boolean = false,
        val cs: String? = null,
        val ce: String? = null,
        val id: String? = null
    )

    @Serializable
    private class ShareSlot(
        val n: Int,
        val s: String,
        val e: String,
        val a: Int = NO_INDEX,
        val sch: Int = NO_INDEX
    )

    @Serializable
    private class ShareScheme(
        val id: String,
        val sm: String? = null,
        val em: String? = null
    )

    @Serializable
    private class ShareConfig(
        val sd: String? = null,
        val tw: Int = 20,
        val cd: Int = 45,
        val bd: Int = 10,
        val fd: Int = 1,
        val cs: String = "default",
        val a: Boolean = false,
        val sw: Boolean = false
    )
}
