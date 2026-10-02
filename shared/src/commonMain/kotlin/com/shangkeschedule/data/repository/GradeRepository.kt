package com.shangkeschedule.data.repository

import androidx.room3.withWriteTransaction
import com.shangkeschedule.data.db.main.Grade
import com.shangkeschedule.data.db.main.GradeDao
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.model.CreditRequirement
import com.shangkeschedule.data.model.GpaScale
import com.shangkeschedule.data.model.StudyCategoryProgress
import com.shangkeschedule.data.model.StudyProgress
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 成绩页顶部汇总数据（按当前绩点制实时换算，不落库）。
 *
 * @param gpa 学分加权绩点；没有可计算的课程（无分数或无学分）时为 null。
 * @param averageScore 未加权平均分（仅统计百分制成绩）；无可统计课程时为 null。
 * @param totalCredits 已通过课程的学分合计（不含不及格与重修记录）。
 * @param courseCount 成绩记录门数（不含重修记录）。
 * @param failedCount 不及格门数。
 */
data class GradeSummary(
    val gpa: Double?,
    val averageScore: Double?,
    val totalCredits: Double,
    val courseCount: Int,
    val failedCount: Int
)

/**
 * 粘贴文本解析出的单条成绩草稿。导入前会在 UI 上以可编辑列表展示，
 * 用户确认后才写入数据库——解析只是启发式，不能当作权威结果。
 *
 * @param courseName 解析出的课程名。
 * @param credit 解析出的学分；无法判定时为 null。
 * @param scoreText 解析出的成绩原文（"92" / "优秀"）。
 */
data class ParsedGrade(
    val courseName: String,
    val credit: Double?,
    val scoreText: String
)

/**
 * 成绩 / GPA 数据仓库。
 *
 * 职责：
 * 1. 成绩记录的增删改查（手动录入、粘贴解析导入、教务抓取导入三种来源）；
 * 2. 按当前绩点制把成绩换算成绩点，并计算加权 GPA / 平均分 / 总学分；
 * 3. 提供粘贴文本的本地解析（不联网、不外发任何数据）。
 *
 * 设计取舍：**绩点与 GPA 一律实时计算，不写回数据库**。
 * 用户随时可以切换 4.0 / 5.0 制式，若把绩点存进表里，切换后旧行就是脏数据。
 */
@OptIn(ExperimentalUuidApi::class)
@Single
class GradeRepository(
    private val database: MainAppDatabase,
    private val gradeDao: GradeDao
) {

    /** 全部成绩（按学期倒序）。 */
    fun getAllGrades(): Flow<List<Grade>> = gradeDao.getAllGrades()

    /** 指定学期的成绩。 */
    fun getGradesBySemester(semester: String): Flow<List<Grade>> = gradeDao.getGradesBySemester(semester)

    /** 全部成绩的一次性快照。 */
    suspend fun getAllGradesOnce(): List<Grade> = gradeDao.getAllGradesOnce()

    /** 已有成绩的学期列表（倒序）。 */
    fun getSemesters(): Flow<List<String>> = gradeDao.getSemesters()

    /**
     * 手动新增一条成绩。
     *
     * @param semester 学期标签（自由文本，如 "2025-2026-1"）。
     * @param courseName 课程名（自动截断到 100 字）。
     * @param credit 学分；null 表示未填。
     * @param scoreText 成绩原文（"92" / "优秀"）；null 表示只记课程不记分数。
     */
    suspend fun addGrade(
        semester: String,
        courseName: String,
        credit: Double?,
        scoreText: String?,
        category: String? = null,
        isRetake: Boolean = false,
        note: String? = null
    ): Grade {
        val now = Clock.System.now().toEpochMilliseconds()
        val grade = Grade(
            id = Uuid.random().toString(),
            semester = semester.trim(),
            courseName = courseName.trim().take(100),
            credit = credit,
            scoreText = scoreText?.trim()?.ifBlank { null },
            scoreValue = scoreValueOf(scoreText),
            category = category?.trim()?.ifBlank { null },
            isRetake = isRetake,
            note = note?.trim()?.ifBlank { null },
            source = Grade.SOURCE_MANUAL,
            createdAt = now,
            updatedAt = now
        )
        database.withWriteTransaction {
            gradeDao.insertAll(listOf(grade))
        }
        return grade
    }

    /** 更新一条成绩（重新解析分数值，保持来源不变）。 */
    suspend fun updateGrade(grade: Grade) {
        val updated = grade.copy(
            semester = grade.semester.trim(),
            courseName = grade.courseName.trim().take(100),
            scoreText = grade.scoreText?.trim()?.ifBlank { null },
            scoreValue = scoreValueOf(grade.scoreText),
            category = grade.category?.trim()?.ifBlank { null },
            note = grade.note?.trim()?.ifBlank { null },
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )
        database.withWriteTransaction {
            if (gradeDao.exists(updated.id)) {
                gradeDao.update(updated)
            } else {
                gradeDao.insertAll(listOf(updated))
            }
        }
    }

    /** 删除一条成绩。 */
    suspend fun deleteGrade(gradeId: String) {
        database.withWriteTransaction {
            gradeDao.deleteById(gradeId)
        }
    }

    /** 清空全部成绩（调用方必须二次确认）。 */
    suspend fun deleteAllGrades() {
        database.withWriteTransaction {
            gradeDao.deleteAll()
        }
    }

    /**
     * 批量导入成绩（粘贴解析结果 / 教务抓取结果）。
     *
     * 去重规则：按「学期 + 课程名」匹配已有记录；命中则覆盖分数与学分（保留原 id 与创建时间），
     * 未命中则插入。这样重复导入同一份成绩不会翻倍，补考后再次抓取也能直接刷新分数。
     * 传入 [Grade] 的 id 由调用方生成，命中已有记录时会被忽略。
     */
    suspend fun importGrades(items: List<Grade>) {
        if (items.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        database.withWriteTransaction {
            items.forEach { incoming ->
                val semester = incoming.semester.trim()
                val courseName = incoming.courseName.trim().take(100)
                if (semester.isEmpty() || courseName.isEmpty()) return@forEach
                val existing = gradeDao.findByNameOnce(semester, courseName)
                if (existing != null) {
                    gradeDao.update(
                        existing.copy(
                            credit = incoming.credit ?: existing.credit,
                            scoreText = incoming.scoreText ?: existing.scoreText,
                            scoreValue = incoming.scoreValue ?: scoreValueOf(incoming.scoreText)
                                ?: existing.scoreValue,
                            category = incoming.category ?: existing.category,
                            updatedAt = now
                        )
                    )
                } else {
                    gradeDao.insertAll(
                        listOf(
                            incoming.copy(
                                semester = semester,
                                courseName = courseName,
                                scoreValue = incoming.scoreValue ?: scoreValueOf(incoming.scoreText),
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    )
                }
            }
        }
    }

    /**
     * 构造一条待导入的成绩记录（供粘贴导入 / 抓取导入复用）。
     *
     * @param semester 目标学期标签。
     * @param courseName 课程名。
     * @param credit 学分。
     * @param scoreText 成绩原文。
     * @param source [Grade.SOURCE_PASTE] 或 [Grade.SOURCE_IMPORT]。
     */
    fun buildGrade(
        semester: String,
        courseName: String,
        credit: Double?,
        scoreText: String?,
        source: String,
        category: String? = null
    ): Grade {
        val now = Clock.System.now().toEpochMilliseconds()
        return Grade(
            id = Uuid.random().toString(),
            semester = semester.trim(),
            courseName = courseName.trim().take(100),
            credit = credit,
            scoreText = scoreText?.trim()?.ifBlank { null },
            scoreValue = scoreValueOf(scoreText),
            category = category?.trim()?.ifBlank { null },
            source = source,
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * 按当前绩点制计算汇总数据。
     *
     * 计算口径（UI 上有一行说明文案同步展示，避免用户误解）：
     * - 只统计**非重修**记录（重修会让同一门课被计两次）；
     * - 加权 GPA = Σ(绩点 × 学分) / Σ学分，仅统计「有绩点且有学分」的课程；
     * - 「通过 / 不通过」这类不计绩点的成绩，只计入 [GradeSummary.totalCredits]；
     * - 未加权平均分只统计百分制成绩。
     */
    fun computeSummary(grades: List<Grade>, scale: GpaScale): GradeSummary {
        val counted = grades.filter { !it.isRetake }
        var weightedPoints = 0.0
        var weightedCredits = 0.0
        var scoreSum = 0.0
        var scoreCount = 0
        var totalCredits = 0.0
        var failedCount = 0

        counted.forEach { grade ->
            val point = pointOf(grade, scale)
            val credit = grade.credit ?: 0.0
            if (point != null) {
                if (point <= 0.0) failedCount++
                if (credit > 0.0) {
                    weightedPoints += point * credit
                    weightedCredits += credit
                }
                if (point > 0.0) totalCredits += credit
            } else if (isPassed(grade.scoreText)) {
                // 通过 / 不通过：不计绩点，但通过后计入已修学分
                totalCredits += credit
            }
            val percent = grade.scoreValue
            if (percent != null) {
                scoreSum += percent
                scoreCount++
            }
        }

        return GradeSummary(
            gpa = if (weightedCredits > 0.0) weightedPoints / weightedCredits else null,
            averageScore = if (scoreCount > 0) scoreSum / scoreCount else null,
            totalCredits = totalCredits,
            courseCount = counted.size,
            failedCount = failedCount
        )
    }

    /**
     * 汇总「学业情况」：按课程类别统计已获 / 要求 / 未获学分（A2）。
     *
     * 与 [computeSummary] 的分工：后者回答「考得怎么样」（绩点 / 平均分），
     * 本函数回答「培养方案修到哪了」（学分进度）。
     *
     * 已获学分 = 该类别下**及格**课程的学分之和（百分制 ≥ 60，或等级制「优秀 / 良好 / 中等 / 及格」，
     * 或「通过 / 合格 / 达标」这类达标表述）；
     * 不及格课程不产生学分，但计入 [StudyCategoryProgress.failedCount]，让用户看出差在哪一类。
     * 重修记录与 [computeSummary] 一致地排除，避免同一门课被算两次学分。
     *
     * @param requirements 用户在学业情况页维护的类别要求。没有成绩的类别同样保留，
     *   这样「先立目标、后出成绩」的用法成立。
     */
    fun computeStudyProgress(
        grades: List<Grade>,
        requirements: List<CreditRequirement>
    ): StudyProgress {
        val counted = grades.filter { !it.isRetake }
        val gradesByCategory = counted.groupBy { it.category?.trim().orEmpty() }
        val requirementByCategory = requirements.associateBy { it.category.trim() }

        val categories = (requirementByCategory.keys + gradesByCategory.keys).map { key ->
            val requirement = requirementByCategory[key]
            val items = gradesByCategory[key].orEmpty()
            var earned = 0.0
            var failed = 0
            items.forEach { grade ->
                val percent = grade.scoreValue ?: scoreValueOf(grade.scoreText)
                val hasScore = percent != null || !grade.scoreText.isNullOrBlank()
                // 等级制（优秀 / 良好 / 中等 / 及格）没有百分制分数，但同样是及格：
                // 能按等级词折算出 > 0 的绩点即算通过，避免把及格课报成挂科。
                val passed = if (percent != null) {
                    percent >= 60.0
                } else {
                    isPassed(grade.scoreText) ||
                        (pointFromLevel(grade.scoreText, GpaScale.SCALE_4) ?: 0.0) > 0.0
                }
                when {
                    // 及格：只累加学分，不作为「差了多少」的负担
                    passed -> earned += grade.credit ?: 0.0
                    // 有成绩但没及格：记一笔挂科，提示这类还差在哪
                    hasScore -> failed++
                }
            }
            StudyCategoryProgress(
                category = key,
                displayName = requirement?.effectiveDisplayName.orEmpty(),
                requiredCredits = requirement?.requiredCredits ?: 0.0,
                earnedCredits = earned,
                courseCount = items.size,
                failedCount = failed
            )
        }.sortedWith(
            compareByDescending<StudyCategoryProgress> { it.requiredCredits }
                .thenBy { it.category }
        )

        return StudyProgress(
            categories = categories,
            totalRequiredCredits = categories.sumOf { it.requiredCredits },
            totalEarnedCredits = categories.sumOf { it.earnedCredits },
            totalCourses = counted.size,
            totalFailedCourses = categories.sumOf { it.failedCount },
            hasAnyGrade = counted.isNotEmpty()
        )
    }

    /**
     * 把单条成绩换算成绩点；无法换算（如「通过」）时返回 null。
     *
     * 优先用百分制分数换算；分数缺失时按等级词换算。
     */
    fun pointOf(grade: Grade, scale: GpaScale): Double? {
        val percent = grade.scoreValue ?: scoreValueOf(grade.scoreText)
        if (percent != null) return pointFromPercent(percent, scale)
        return pointFromLevel(grade.scoreText, scale)
    }

    /** 百分制分数转绩点。 */
    fun pointFromPercent(score: Double, scale: GpaScale): Double = when (scale) {
        GpaScale.SCALE_4 -> when {
            score >= 90 -> 4.0
            score >= 85 -> 3.7
            score >= 82 -> 3.3
            score >= 78 -> 3.0
            score >= 75 -> 2.7
            score >= 72 -> 2.3
            score >= 68 -> 2.0
            score >= 64 -> 1.5
            score >= 60 -> 1.0
            else -> 0.0
        }

        GpaScale.SCALE_5 -> when {
            score >= 90 -> 5.0
            score >= 80 -> 4.0
            score >= 70 -> 3.0
            score >= 60 -> 2.0
            else -> 0.0
        }
    }

    /**
     * 等级制成绩转绩点。
     *
     * 「通过 / 不通过（合格与否的达标表述）」返回 null：这类课程学校本身不计绩点，
     * 强行折算会凭空抬高或拉低 GPA。它们只在 [GradeSummary.totalCredits] 里体现。
     */
    fun pointFromLevel(scoreText: String?, scale: GpaScale): Double? {
        val text = scoreText?.trim()?.uppercase() ?: return null
        if (text.isEmpty()) return null
        val high = scale == GpaScale.SCALE_4
        return when {
            text.startsWith("优秀") || text == "优" || text.startsWith("A") ->
                if (high) 4.0 else 5.0

            text.startsWith("良好") || text == "良" || text.startsWith("B") ->
                if (high) 3.7 else 4.0

            text.startsWith("中等") || text == "中" || text.startsWith("C") ->
                if (high) 2.7 else 3.0

            text.startsWith("及格") || text.startsWith("合格") || text.startsWith("D") ->
                if (high) 1.5 else 2.0

            text.startsWith("不及格") || text.startsWith("不合格") ||
                text.startsWith("未通过") || text.startsWith("失败") || text.startsWith("F") -> 0.0

            // 「通过 / 不通过」不折算绩点
            text.startsWith("通过") || text.startsWith("不通过") -> null

            else -> null
        }
    }

    /**
     * 一段文本转百分制分数；无法解析（等级制、纯文字）时返回 null。
     * 允许 "92"、"92.5"、"=92"、"92分" 这类常见写法。
     */
    fun scoreValueOf(scoreText: String?): Double? {
        val raw = scoreText?.trim() ?: return null
        if (raw.isEmpty()) return null
        val match = PERCENT_PATTERN.find(raw) ?: return null
        val value = match.value.toDoubleOrNull() ?: return null
        return if (value in 0.0..100.0) value else null
    }

    /** 是否为「通过」类（不计绩点但算学分）的成绩文本。 */
    private fun isPassed(scoreText: String?): Boolean {
        val text = scoreText?.trim() ?: return false
        return text.startsWith("通过") || text.startsWith("合格") || text.startsWith("达标")
    }

    /**
     * 解析用户粘贴的教务成绩页文本（本地解析，不联网）。
     *
     * 启发式规则：逐行处理，从右往左找到第一个「像成绩」的 token
     * —— 1–100 的整数，或不小于 50 的小数，或等级词（优秀 / 良好 / 92 分…）；
     * 再往左找 0–30 的数字当学分；其余部分拼成课程名。
     * 识别不到的行走不了解析（如纯表头行），由 UI 提示用户手动补录。
     *
     * 解析结果一律作为**草稿**返回，UI 必须先展示可编辑列表让用户确认再入库。
     */
    fun parsePastedGrades(text: String): List<ParsedGrade> {
        val result = mutableListOf<ParsedGrade>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.replace('\u3000', ' ').replace('|', ' ').trim()
            if (line.isEmpty()) return@forEach
            val tokens = line.split(TOKEN_SEPARATOR).filter { it.isNotBlank() }
            if (tokens.size < 2) return@forEach

            var scoreIndex = -1
            var scoreText: String? = null
            for (i in tokens.indices.reversed()) {
                val token = tokens[i]
                val value = token.toDoubleOrNull()
                if (value != null && value in 1.0..100.0 &&
                    (!token.contains('.') || value >= 50.0)
                ) {
                    scoreIndex = i
                    scoreText = token
                    break
                }
                if (LEVEL_WORDS.any { token.startsWith(it) }) {
                    scoreIndex = i
                    scoreText = token
                    break
                }
            }
            if (scoreIndex <= 0 || scoreText == null) return@forEach

            var credit: Double? = null
            var nameEnd = scoreIndex
            for (i in (scoreIndex - 1) downTo 1) {
                val value = tokens[i].toDoubleOrNull() ?: continue
                if (value > 0.0 && value <= 30.0) {
                    credit = value
                    nameEnd = i
                    break
                }
            }

            val name = tokens.subList(0, nameEnd)
                .filterNot { token ->
                    token.toDoubleOrNull() != null || token == "学分" || token == "成绩" || token == "分数"
                }
                .joinToString(" ")
                .trim()
            if (name.isEmpty()) return@forEach
            result.add(ParsedGrade(courseName = name, credit = credit, scoreText = scoreText))
        }
        return result
    }

    private companion object {
        /** 允许 "92"、"92.5"、"=92"、"92分"、"92 分" 等写法。 */
        val PERCENT_PATTERN = Regex("""\d{1,3}(?:\.\d)?(?=\s*分|$)""")

        /** 行内分隔：制表符、逗号、分号、竖线、连续空格、单个空格。 */
        val TOKEN_SEPARATOR = Regex("""[\t,，;；]+|\s+""")

        /** 等级制成绩词（用于粘贴解析时认出成绩列）。 */
        val LEVEL_WORDS = listOf(
            "优秀", "良好", "中等", "及格", "不及格", "合格", "不合格",
            "通过", "不通过", "优", "良", "中", "差"
        )
    }
}
