package com.shangkeschedule.data.parser

/**
 * 文本/文件导入的格式类别。
 * 用于「文本粘贴导入」与「文件导入」的二级分类页面：
 * 每个类别对应一个独立页面，页面内强制使用该类别的解析器（不再依赖自动嗅探）。
 *
 * 本枚举只承载解析口径（枚举名 = 导航参数、扩展名映射），**不含任何展示文案**：
 * 二级页标题与说明文案在 UI 层按枚举取值（见 `ui/settings/import/ImportFormatText.kt`
 * 的 `labelRes` / `hintRes` / `screenTitle`），以免英文/繁体界面里出现硬编码简体文案。
 */
enum class TextImportFormat {
    WAKEUP,
    PLAIN,
    JSON,
    CSV,
    ICS,
    HTML;

    companion object {
        /** 按文件名猜测格式类别（用于文件导入时辅助识别，识别不出返回 null 走自动嗅探） */
        fun forFileName(fileName: String?): TextImportFormat? {
            val n = (fileName ?: "").lowercase().substringBeforeLast('.')
            val ext = (fileName ?: "").lowercase().substringAfterLast('.', "")
            return when (ext) {
                "json" -> JSON
                "ics", "ical", "ifb" -> ICS
                "csv" -> CSV
                "html", "htm" -> HTML
                else -> null
            }
        }

        /** 从名称字符串还原枚举（导航参数反序列化用，找不到返回 null） */
        fun fromName(name: String?): TextImportFormat? =
            entries.firstOrNull { it.name == name }
    }
}
