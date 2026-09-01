package com.shangkeschedule.data.parser

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
                    ?.let { readSharedStrings(zipFs.read(it) { readUtf8() }) }
                    ?: emptyList()

                val sheetEntry = entries
                    .filter { Regex("""/xl/worksheets/sheet\d+\.xml$""").containsMatchIn(it.toString()) }
                    .minByOrNull { Regex("""sheet(\d+)\.xml$""").find(it.toString())?.groupValues?.get(1)?.toIntOrNull() ?: 999 }
                    ?: throw IllegalArgumentException("xlsx 中未找到工作表")

                return parseSheetXml(zipFs.read(sheetEntry) { readUtf8() }, sharedStrings)
            } finally {
                zipFs.close()
            }
        } finally {
            runCatching { fs.delete(tempPath) }
        }
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
        Regex("""<si(?:\s[^>]*)?>(.*?)</si>""", setOf(RegexOption.DOT_MATCHES_ALL))
            .findAll(xml)
            .forEach { siMatch ->
                val inner = siMatch.groupValues[1]
                val sb = StringBuilder()
                Regex("""<t(?:\s[^>]*)?>(.*?)</t>""", setOf(RegexOption.DOT_MATCHES_ALL))
                    .findAll(inner)
                    .forEach { tMatch -> sb.append(tMatch.groupValues[1]) }
                result.add(unescapeXml(sb.toString()))
            }
        return result
    }

    // ========== sheetN.xml ==========

    private fun parseSheetXml(xml: String, sharedStrings: List<String>): List<List<String>> {
        // 提取 <sheetData> 主体（防止 <sheetData/> 自闭合空表）
        val sheetData = Regex("""<sheetData(?:\s[^>]*)?>(.*?)</sheetData>""", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(xml)?.groupValues?.get(1)
            ?: ""

        val grid = mutableListOf<MutableList<String>>()
        var autoRow = 0

        val rowRegex = Regex("""<row(?:\s[^>]*)?>(.*?)</row>""", setOf(RegexOption.DOT_MATCHES_ALL))

        for (rowMatch in rowRegex.findAll(sheetData)) {
            autoRow++
            val rowXml = rowMatch.groupValues[1]
            val rowIndex = rowMatch.groupValues[0].let { r -> Regex("""\br="(\d+)"""").find(r)?.groupValues?.get(1)?.toIntOrNull() } ?: autoRow

            val cells = mutableMapOf<Int, String>()
            var autoCol = 0

            val cellRegex = Regex("""<c\b([^>]*?)(/>|>(.*?)</c>)""", setOf(RegexOption.DOT_MATCHES_ALL))
            for (cellMatch in cellRegex.findAll(rowXml)) {
                autoCol++
                val attrs = cellMatch.groupValues[1]
                val inner = cellMatch.groupValues[3]

                val ref = Regex("""\br="([A-Z]+)(\d+)"""").find(attrs)
                val colIndex = ref?.groupValues?.get(1)?.let { columnLettersToIndex(it) } ?: (autoCol - 1)

                val type = Regex("""\bt="([a-z]+)"""").find(attrs)?.groupValues?.get(1)
                val value = when (type) {
                    "s" -> inner.let { i -> Regex("""<v(?:\s[^>]*)?>(.*?)</v>""", setOf(RegexOption.DOT_MATCHES_ALL)).find(i)?.groupValues?.get(1) }
                        ?.trim()?.toIntOrNull()?.let { idx -> sharedStrings.getOrNull(idx) } ?: ""
                    "inlineStr" -> Regex("""<t(?:\s[^>]*)?>(.*?)</t>""", setOf(RegexOption.DOT_MATCHES_ALL))
                        .findAll(inner).joinToString("") { it.groupValues[1] }
                    "str", "e" -> Regex("""<v(?:\s[^>]*)?>(.*?)</v>""", setOf(RegexOption.DOT_MATCHES_ALL))
                        .find(inner)?.groupValues?.get(1) ?: ""
                    else -> Regex("""<v(?:\s[^>]*)?>(.*?)</v>""", setOf(RegexOption.DOT_MATCHES_ALL))
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
        var index = 0
        for (c in letters.uppercase()) {
            index = index * 26 + (c - 'A' + 1)
        }
        return index - 1
    }

    /** 数值清洗："1.0" → "1"，"3.5" 保持，其余原样 */
    private fun normalizeValue(v: String): String {
        val t = v.trim()
        return if (Regex("""^\d+\.0+$""").matches(t)) t.substringBefore('.') else t
    }

    private fun unescapeXml(s: String): String {
        if (!s.contains('&')) return s
        var out = s
        // 数字实体（含 &#10; 换行）
        Regex("""&#x([0-9a-fA-F]+);""").findAll(out).toList().forEach { m ->
            m.groupValues[1].toIntOrNull(16)?.let { out = out.replace(m.value, it.toChar().toString()) }
        }
        Regex("""&#(\d+);""").findAll(out).toList().forEach { m ->
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
