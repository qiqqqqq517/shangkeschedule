package com.shangkeschedule.data.parser

import okio.BufferedSource
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip
import kotlin.random.Random

/**
 * 轻量 .xlsx（Office Open XML）解析器。
 *
 * 仅依赖 okio：将字节数据落盘为临时文件 → openZip 读取 → 正则提取
 * xl/sharedStrings.xml（共享字符串表）与 xl/worksheets/sheetN.xml（工作表），
 * 还原为二维字符串网格 [row][col] 交给 UniversalScheduleParser.parseExcelGrid 识别课程。
 *
 * 支持：
 * - t="s" 共享字符串 / t="inlineStr" 内联字符串 / t="str" 公式结果 / 数值单元格
 * - 富文本 runs（<si> 内多个 <r><t>）拼接
 * - XML 实体反转义（含 &#10; 换行，保留单元格内换行）
 * - 稀疏行/列（r="A1" 引用定位，缺省时顺序递增）
 * - 多工作表时取第一个 sheetN.xml
 *
 * 旧版二进制 .xls 不在本解析器范围内（需在 Office/WPS 中另存为 .xlsx）。
 */
object ExcelScheduleParser {
    // ========== 预编译正则 ==========
    // 原实现把 Regex 写在函数体/逐行逐格循环里，大 xlsx 会放大成万级重复编译。
    private val RE_SHEET_PATH = Regex("""/xl/worksheets/sheet\d+\.xml$""")
    private val RE_SHEET_NUM = Regex("""sheet(\d+)\.xml$""")
    private val RE_SI = Regex("""<si(?:\s[^>]*)?>(.*?)</si>""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val RE_T = Regex("""<t(?:\s[^>]*)?>(.*?)</t>""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val RE_SHEET_DATA = Regex("""<sheetData(?:\s[^>]*)?>(.*?)</sheetData>""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val RE_ROW = Regex("""<row(?:\s[^>]*)?>(.*?)</row>""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val RE_ROW_INDEX = Regex("""\br="(\d+)"""")
    private val RE_CELL = Regex("""<c\b([^>]*?)(/>|>(.*?)</c>)""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val RE_CELL_REF = Regex("""\br="([A-Z]+)(\d+)"""")
    private val RE_CELL_TYPE = Regex("""\bt="([a-z]+)"""")
    private val RE_V = Regex("""<v(?:\s[^>]*)?>(.*?)</v>""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val RE_TRAILING_ZERO = Regex("""^\d+\.0+$""")
    private val RE_HEX_ENTITY = Regex("""&#x([0-9a-fA-F]+);""")
    private val RE_DEC_ENTITY = Regex("""&#(\d+);""")


    //FIX:xlsx 的 r="0"/超大行列引用若不做上限校验，会触发索引越界或一次性分配海量空行导致 OOM
    private const val MAX_SHEET_ROWS = 20_000
    private const val MAX_SHEET_COLS = 1_000

    //FIX:单个内部 XML（sharedStrings.xml / sheetN.xml）解压后超过上限即中止，防 zip 炸弹式 xlsx 占满内存
    private const val MAX_XML_BYTES = 64L * 1024 * 1024
    private const val XML_READ_CHUNK_BYTES = 64L * 1024

    /** ZIP 魔数判断（xlsx 本质是 ZIP 容器） */
    fun isZipBytes(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /**
     * 从 .xlsx 字节中提取二维网格。
     * @param cacheDir 用于临时落盘的缓存目录（okio Path）
     */
    fun extractGrid(bytes: ByteArray, cacheDir: Path): List<List<String>> {
        if (bytes.isEmpty()) throw IllegalArgumentException("文件内容为空")
        if (!isZipBytes(bytes)) {
            throw IllegalArgumentException("不是有效的 .xlsx 文件（旧版 .xls 为二进制格式，请先另存为 .xlsx）")
        }

        val fs = FileSystem.SYSTEM
        val tempPath = cacheDir / "xlsx_import_${Random.nextLong(100000, 999999)}.tmp"
        fs.createDirectories(cacheDir)
        fs.write(tempPath) { write(bytes) }
        try {
            val zipFs = fs.openZip(tempPath)
            try {
                val entries = mutableListOf<Path>().also { listAllFiles(zipFs, "/".toPath(), it) }

                val sharedStrings = entries
                    .firstOrNull { it.toString().endsWith("xl/sharedStrings.xml") }
                    ?.let { readSharedStrings(zipFs.read(it) { readUtf8Capped(MAX_XML_BYTES) }) }
                    ?: emptyList()

                val sheetEntry = entries
                    .filter { RE_SHEET_PATH.containsMatchIn(it.toString()) }
                    .minByOrNull { RE_SHEET_NUM.find(it.toString())?.groupValues?.get(1)?.toIntOrNull() ?: 999 }
                    ?: throw IllegalArgumentException("xlsx 中未找到工作表")

                return parseSheetXml(zipFs.read(sheetEntry) { readUtf8Capped(MAX_XML_BYTES) }, sharedStrings)
            } finally {
                zipFs.close()
            }
        } finally {
            runCatching { fs.delete(tempPath) }
        }
    }

    /**
     * 分块读取内部 XML，并对解压后总长设限。
     * FIX: 原实现直接 readUtf8()，损坏或恶意 xlsx 可用超大条目一次性耗尽内存。
     */
    private fun BufferedSource.readUtf8Capped(maxBytes: Long): String {
        val buffer = okio.Buffer()
        var total = 0L
        while (true) {
            val bytesRead = read(buffer, XML_READ_CHUNK_BYTES)
            if (bytesRead == -1L) break
            total += bytesRead
            if (total > maxBytes) {
                throw IllegalArgumentException("xlsx 内部 XML 过大，已中止解析")
            }
        }
        return buffer.readUtf8()
    }

    // ========== Zip 内部遍历 ==========

    private fun listAllFiles(fs: FileSystem, dir: Path, out: MutableCollection<Path>) {
        val children = runCatching { fs.list(dir) }.getOrDefault(emptyList())
        for (child in children) {
            if (runCatching { fs.metadata(child).isDirectory }.getOrDefault(false)) {
                listAllFiles(fs, child, out)
            } else {
                out.add(child)
            }
        }
    }

    // ========== sharedStrings.xml ==========

    /**
     * 提取共享字符串表。每个 <si> 为一个条目；条目内可能含多个 <r><t> 富文本 run，
     * 全部拼接为单个字符串。空白单元格在结果中保留为 ""（下标对齐 <si> 顺序）。
     */
    private fun readSharedStrings(xml: String): List<String> {
        val result = mutableListOf<String>()
        RE_SI
            .findAll(xml)
            .forEach { siMatch ->
                val inner = siMatch.groupValues[1]
                val sb = StringBuilder()
                RE_T
                    .findAll(inner)
                    .forEach { tMatch -> sb.append(tMatch.groupValues[1]) }
                result.add(unescapeXml(sb.toString()))
            }
        return result
    }

    // ========== sheetN.xml ==========

    private fun parseSheetXml(xml: String, sharedStrings: List<String>): List<List<String>> {
        // 提取 <sheetData> 主体（防止 <sheetData/> 自闭合空表）
        val sheetData = RE_SHEET_DATA
            .find(xml)?.groupValues?.get(1)
            ?: ""

        val grid = mutableListOf<MutableList<String>>()
        var autoRow = 0

        val rowRegex = RE_ROW

        for (rowMatch in rowRegex.findAll(sheetData)) {
            autoRow++
            val rowXml = rowMatch.groupValues[1]
            val rowIndex = rowMatch.groupValues[0].let { r -> RE_ROW_INDEX.find(r)?.groupValues?.get(1)?.toIntOrNull() } ?: autoRow
            if (rowIndex !in 1..MAX_SHEET_ROWS) continue

            val cells = mutableMapOf<Int, String>()
            var autoCol = 0

            val cellRegex = RE_CELL
            for (cellMatch in cellRegex.findAll(rowXml)) {
                autoCol++
                val attrs = cellMatch.groupValues[1]
                val inner = cellMatch.groupValues[3]

                val ref = RE_CELL_REF.find(attrs)
                val refCol = ref?.groupValues?.get(1)?.let { columnLettersToIndex(it) }
                //FIX:显式列引用非法时不能退回 autoCol，否则会静默错位；仅无 r 属性时才按顺序推断
                if (ref != null && (refCol == null || refCol !in 0 until MAX_SHEET_COLS)) continue
                val colIndex = refCol ?: (autoCol - 1)
                if (colIndex !in 0 until MAX_SHEET_COLS) continue

                val type = RE_CELL_TYPE.find(attrs)?.groupValues?.get(1)
                val value = when (type) {
                    "s" -> inner.let { i -> RE_V.find(i)?.groupValues?.get(1) }
                        ?.trim()?.toIntOrNull()?.let { idx -> sharedStrings.getOrNull(idx) } ?: ""
                    "inlineStr" -> RE_T
                        .findAll(inner).joinToString("") { it.groupValues[1] }
                    "str", "e" -> RE_V
                        .find(inner)?.groupValues?.get(1) ?: ""
                    else -> RE_V
                        .find(inner)?.groupValues?.get(1) ?: ""
                }

                cells[colIndex] = normalizeValue(unescapeXml(value))
            }

            ensureRowCapacity(grid, rowIndex)
            if (cells.isNotEmpty()) {
                val width = cells.keys.max() + 1
                val rowList = mutableListOf<String>()
                for (c in 0 until width) rowList.add(cells[c] ?: "")
                grid[rowIndex - 1] = rowList
            }
        }

        // 行间空行补齐为空行（保持网格对齐）
        return grid.map { row -> row.toList() }
    }

    private fun ensureRowCapacity(grid: MutableList<MutableList<String>>, rowIndex: Int) {
        while (grid.size < rowIndex) grid.add(mutableListOf())
    }

    // ========== 工具 ==========

    private fun columnLettersToIndex(letters: String): Int {
        if (letters.length > 4) return -1
        var index = 0
        for (c in letters.uppercase()) {
            index = index * 26 + (c - 'A' + 1)
        }
        return index - 1
    }

    /** 数值清洗："1.0" → "1"，"3.5" 保持，其余原样 */
    private fun normalizeValue(v: String): String {
        val t = v.trim()
        return if (RE_TRAILING_ZERO.matches(t)) t.substringBefore('.') else t
    }

    private fun unescapeXml(s: String): String {
        if (!s.contains('&')) return s
        var out = s
        // 数字实体（含 &#10; 换行）
        RE_HEX_ENTITY.findAll(out).toList().forEach { m ->
            m.groupValues[1].toIntOrNull(16)?.let { out = out.replace(m.value, it.toChar().toString()) }
        }
        RE_DEC_ENTITY.findAll(out).toList().forEach { m ->
            m.groupValues[1].toIntOrNull()?.let { out = out.replace(m.value, it.toChar().toString()) }
        }
        return out
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}
