package com.shangkeschedule.data.parser

/**
 * 文本/文件导入的格式类别。
 * 用于「文本粘贴导入」与「文件导入」的二级分类页面：
 * 每个类别对应一个独立页面，页面内强制使用该类别的解析器（不再依赖自动嗅探）。
 */
enum class TextImportFormat(
    val label: String,
    val hint: String
) {
    WAKEUP(
        "WakeUp 分享文本",
        "粘贴 WakeUp 课程表 App「分享给好友/复制文本」得到的完整内容（含【来自WakeUp课程表】开头或 JSON 数据段）"
    ),
    PLAIN(
        "纯文本（一行一课）",
        "每行一门课，字段用制表符 / 逗号 / | / 连续空格分隔：\n课程名 教师 教室 星期 节次 周次\n例：高等数学 张三 教5-103 周一 1-2节 1-16周"
    ),
    JSON(
        "JSON",
        "粘贴本 App 导出的 .json 课表文件内容，或 WakeUp JSON（含 courses 数组）"
    ),
    CSV(
        "CSV 表格",
        "首行为表头：课程,教师,教室,星期,节次,周次（列名支持 中英文/部分匹配，顺序可变）\n例：课程,教师,教室,星期,节次,周次"
    ),
    ICS(
        "ICS 日历",
        "粘贴 .ics 日历文本（以 BEGIN:VCALENDAR 开头、含 VEVENT 事件），每条事件识别为一门课"
    ),
    HTML(
        "HTML 表格",
        "粘贴含 <table> 的 HTML 源码，按行解析：课程/教师/教室/星期/节次/周次"
    );

    /** 二级页标题 */
    val screenTitle: String get() = "$label 导入"

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
