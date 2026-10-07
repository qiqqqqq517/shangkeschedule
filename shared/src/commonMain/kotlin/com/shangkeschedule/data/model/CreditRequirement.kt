package com.shangkeschedule.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 培养方案里的一条学分要求（A2「学业情况」）。
 *
 * 只描述「这类课程要求修满多少学分」，**不复制任何成绩数据**：已获学分始终由成绩表实时
 * 现算（见 `GradeRepository.computeStudyProgress`），所以用户之后补录成绩、从教务重新导入
 * 或改判成绩，完成度会自动跟着变，不存在两份数据打架的问题。
 *
 * @param category 与 [com.shangkeschedule.data.db.main.Grade.category] 对齐的类别键
 *   （如「必修」「专业选修」）；空串代表「未分类」，即成绩里没填课程性质的记录。
 * @param requiredCredits 该类别要求的学分总数；0 表示尚未设置（页面按「未设置」展示，不计入总要求）。
 * @param displayName 用户可改写的显示名；空白 = 直接显示 [category]。
 * @param requiredCourses 该类别**应修门数**（v4.75.0）；null = 未知 / 未设置。
 *   来源有两处：用户在学业情况页手填，或适配脚本钩子从教务「学业情况」页读出
 *   （正方 V9 的类别行末尾带「共（N）门 通过（M）门」，N 即应修门数）。
 *   与 [requiredCredits] 一样**只描述要求**，已修门数永远由本机成绩表现算。
 */
@Serializable
data class CreditRequirement(
    val category: String = UNCATEGORIZED,
    val requiredCredits: Double = 0.0,
    val displayName: String = "",
    val requiredCourses: Int? = null
) {
    /** 去空白后的显示名；空白时回落到类别键。 */
    val effectiveDisplayName: String
        get() = displayName.trim().ifBlank { category.trim() }

    companion object {
        /** 未分类成绩（`Grade.category` 为空）对应的类别键。 */
        const val UNCATEGORIZED = ""

        /** 单条要求的学分上限，用于输入校验（部分专业单类要求可达 100+）。 */
        const val MAX_REQUIRED_CREDITS = 300.0

        /** 单条要求的应修门数上限，用于输入校验（培养方案单类最多 200 门量级）。 */
        const val MAX_REQUIRED_COURSES = 300

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** 序列化为 DataStore 里保存的 JSON 文本。 */
        fun encode(list: List<CreditRequirement>): String =
            runCatching { json.encodeToString(ListSerializer(serializer()), list) }.getOrDefault("[]")

        /**
         * 反序列化。
         *
         * 数据损坏时返回空列表而不是抛异常：这段解析发生在
         * `AppSettingsModel.fromPreferences` 的启动路径上，抛异常会让整个应用起不来，
         * 代价远大于「用户重设一次培养方案」。
         */
        fun decode(raw: String?): List<CreditRequirement> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching { json.decodeFromString(ListSerializer(serializer()), raw) }
                .getOrDefault(emptyList())
        }
    }
}
