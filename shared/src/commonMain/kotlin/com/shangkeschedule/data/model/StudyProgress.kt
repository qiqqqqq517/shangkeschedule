package com.shangkeschedule.data.model

/**
 * 单个课程类别的学分完成情况（A2「学业情况」）。
 *
 * @param category 类别键；[CreditRequirement.UNCATEGORIZED]（空串）= 未分类成绩。
 * @param displayName 展示名：优先用户改写名，其次类别键；两者都为空时由界面回落到「未分类」。
 * @param requiredCredits 要求学分；0 = 未设置。
 * @param earnedCredits 已获学分（只统计及格课程）。
 * @param courseCount 该类别课程门数（不含重修记录）。
 * @param failedCount 该类别不及格门数。
 */
data class StudyCategoryProgress(
    val category: String,
    val displayName: String,
    val requiredCredits: Double,
    val earnedCredits: Double,
    val courseCount: Int,
    val failedCount: Int
) {
    /** 还差多少学分（已超出要求时为 0）。 */
    val missingCredits: Double get() = (requiredCredits - earnedCredits).coerceAtLeast(0.0)

    /** 是否已设置该类别的学分要求。 */
    val hasRequirement: Boolean get() = requiredCredits > 0.0

    /** 完成度 0f–1f；未设置要求时为 0f。 */
    val ratio: Float
        get() = if (requiredCredits > 0.0) {
            (earnedCredits / requiredCredits).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }
}

/**
 * 「学业情况」总览（A2）。
 *
 * 语义刻意与成绩页的 [com.shangkeschedule.data.repository.GradeSummary] 区分开：
 * 成绩页回答「考得怎么样」（绩点 / 平均分），这里回答「培养方案修到哪了」（学分进度）。
 */
data class StudyProgress(
    val categories: List<StudyCategoryProgress> = emptyList(),
    val totalRequiredCredits: Double = 0.0,
    val totalEarnedCredits: Double = 0.0,
    val totalCourses: Int = 0,
    val totalFailedCourses: Int = 0,
    val hasAnyGrade: Boolean = false
) {
    /** 总要求之外还差多少学分（已达标时为 0）。 */
    val totalMissingCredits: Double get() = (totalRequiredCredits - totalEarnedCredits).coerceAtLeast(0.0)

    /** 是否已设置任意一条学分要求。 */
    val hasAnyRequirement: Boolean get() = totalRequiredCredits > 0.0

    /** 总完成度 0f–1f；未设置要求时为 0f。 */
    val ratio: Float
        get() = if (totalRequiredCredits > 0.0) {
            (totalEarnedCredits / totalRequiredCredits).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }
}
