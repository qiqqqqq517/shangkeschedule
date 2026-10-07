package com.shangkeschedule.data.model

/**
 * 培养方案里「还没修 / 还没出成绩」的一门课（v4.75.0）。
 *
 * 只是给界面展示用的轻量投影：真正的数据落在 `curriculum_courses` 表，
 * 由 [StudyProgress] 的计算过程带上「本机成绩表里没有它」这一事实后产生。
 *
 * @param courseName 课程名。
 * @param category 所属类别（与 [StudyCategoryProgress.category] 对齐）。
 * @param credit 该课程学分；null = 未填。
 * @param suggestedTerm 建议修读学期；null = 未填。
 */
data class PendingCurriculumCourse(
    val courseName: String,
    val category: String,
    val credit: Double? = null,
    val suggestedTerm: String? = null
)

/**
 * 单个课程类别的学分 / 门数完成情况（A2「学业情况」）。
 *
 * @param category 类别键；[CreditRequirement.UNCATEGORIZED]（空串）= 未分类成绩。
 * @param displayName 展示名：优先用户改写名，其次类别键；两者都为空时由界面回落到「未分类」。
 * @param requiredCredits 要求学分；0 = 未设置。
 * @param earnedCredits 已获学分（只统计及格课程）。
 * @param courseCount 该类别**已有成绩**的门数（不含重修记录）。
 * @param failedCount 该类别不及格门数。
 * @param requiredCourses 该类别**应修门数**；null = 未知 / 未设置（v4.75.0）。
 *   与 [courseCount] 语义不同：前者是培养方案的要求，后者是本机已有的成绩记录。
 * @param plannedCourses 该类别**培养方案清单里的课程门数**（v4.75.0）；0 = 还没导入清单。
 * @param pendingCourses 该类别**未修门数** = 清单里有、成绩表里没有的门数（v4.75.0）。
 * @param pendingCourseList 未修课程明细，供界面直接列名（v4.75.0）。
 */
data class StudyCategoryProgress(
    val category: String,
    val displayName: String,
    val requiredCredits: Double,
    val earnedCredits: Double,
    val courseCount: Int,
    val failedCount: Int,
    val requiredCourses: Int? = null,
    val plannedCourses: Int = 0,
    val pendingCourses: Int = 0,
    val pendingCourseList: List<PendingCurriculumCourse> = emptyList()
) {
    /** 还差多少学分（已超出要求时为 0）。 */
    val missingCredits: Double get() = (requiredCredits - earnedCredits).coerceAtLeast(0.0)

    /** 是否已设置该类别的学分要求。 */
    val hasRequirement: Boolean get() = requiredCredits > 0.0

    /**
     * 该类别的「应修门数」展示基准：
     * 优先用学校培养方案给的 [requiredCourses]，没有时退回清单门数 [plannedCourses]（两者都没有则为 null）。
     */
    val targetCourses: Int?
        get() = requiredCourses ?: plannedCourses.takeIf { it > 0 }

    /** 是否已经能统计「应修 / 已修 / 未修」门数（有应修门数或有清单）。 */
    val hasCoursePlan: Boolean get() = targetCourses != null || plannedCourses > 0

    /**
     * 该类别「已修门数」：
     * 有课程清单时按清单算（清单门数 − 未修门数，只有清单里的课才算「应修」）；
     * 没有清单、只有应修门数时退回本机**已出分门数**（近似，见页面脚注）。
     */
    val doneCourses: Int
        get() = if (plannedCourses > 0) (plannedCourses - pendingCourses).coerceAtLeast(0) else courseCount

    /**
     * 该类别「还差几门」：
     * 有课程清单时 = 未修门数（清单里有、成绩里没有）；只有应修门数时 = 应修 − 已出分（不小于 0）。
     */
    val remainingCourses: Int
        get() = if (plannedCourses > 0) {
            pendingCourses
        } else {
            targetCourses?.let { (it - courseCount).coerceAtLeast(0) } ?: 0
        }

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
 * 成绩页回答「考得怎么样」（绩点 / 平均分），这里回答「培养方案修到哪了」（学分进度 + 门数进度）。
 *
 * @param totalCourses **已有成绩**的门数（不含重修记录）。刻意不叫「应修门数」——
 *   它只是本机成绩记录的条数，与培养方案要求无关。
 * @param totalPlannedCourses 培养方案清单里的课程门数；0 = 还没导入清单（v4.75.0）。
 * @param totalPendingCourses 未修门数合计（v4.75.0）。
 */
data class StudyProgress(
    val categories: List<StudyCategoryProgress> = emptyList(),
    val totalRequiredCredits: Double = 0.0,
    val totalEarnedCredits: Double = 0.0,
    val totalCourses: Int = 0,
    val totalFailedCourses: Int = 0,
    val hasAnyGrade: Boolean = false,
    val totalPlannedCourses: Int = 0,
    val totalPendingCourses: Int = 0
) {
    /** 总要求之外还差多少学分（已达标时为 0）。 */
    val totalMissingCredits: Double get() = (totalRequiredCredits - totalEarnedCredits).coerceAtLeast(0.0)

    /** 是否已设置任意一条学分要求。 */
    val hasAnyRequirement: Boolean get() = totalRequiredCredits > 0.0

    /** 是否已经导入培养方案课程清单（决定页面是否展示「未修课程」区）。 */
    val hasAnyCurriculum: Boolean get() = totalPlannedCourses > 0

    /**
     * 学校给出的应修门数合计（各类别 [StudyCategoryProgress.requiredCourses] 之和）；
     * 一条都没设置时为 null。与 [totalPlannedCourses]（本机清单门数）是两个不同来源。
     */
    val totalRequiredCourses: Int?
        get() = categories.mapNotNull { it.requiredCourses }
            .takeIf { it.isNotEmpty() }
            ?.sum()

    /** 是否已经能统计「应修 / 已修 / 未修」门数（教务给了应修门数，或导入了课程清单）。 */
    val hasAnyCoursePlan: Boolean
        get() = totalRequiredCourses != null || hasAnyCurriculum

    /** 页面展示用的「应修门数」：优先学校给的应修门数，其次本机清单门数；都没有时为 null。 */
    val displayTargetCourses: Int?
        get() = totalRequiredCourses ?: totalPlannedCourses.takeIf { it > 0 }

    /** 全部类别的「还差门数」合计。 */
    val totalRemainingCourses: Int get() = categories.sumOf { it.remainingCourses }

    /** 总完成度 0f–1f；未设置要求时为 0f。 */
    val ratio: Float
        get() = if (totalRequiredCredits > 0.0) {
            (totalEarnedCredits / totalRequiredCredits).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }
}
