package com.shangkeschedule.data.repository

import androidx.room3.withWriteTransaction
import com.shangkeschedule.data.db.main.CurriculumCourse
import com.shangkeschedule.data.db.main.Grade
import com.shangkeschedule.data.db.main.GradeDao
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.model.CourseCategoryLexicon
import com.shangkeschedule.data.model.CreditRequirement
import com.shangkeschedule.data.model.GpaScale
import com.shangkeschedule.data.model.PendingCurriculumCourse
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
 *   每门课的绩点优先取**本校绩点**（[Grade.gradePoint]，教务系统按本校规则算出），
 *   本校没给时才按当前绩点制由分数换算（v4.75.0）。
 * @param averageScore 未加权平均分（仅统计百分制成绩）；无可统计课程时为 null。
 * @param totalCredits 已通过课程的学分合计（不含不及格与重修记录）。
 * @param courseCount **已出分**门数（不含重修记录，也不含只记课程不记分数的空成绩行）。
 *   R40-13：此前直接取记录数，空成绩行也被计入，与同屏平均分的样本口径不一致。
 * @param failedCount 不及格门数。
 * @param schoolPointCount 参与加权绩点、且绩点**取自本校**的课程门数（v4.75.0）。
 * @param convertedPointCount 参与加权绩点、绩点由**本机换算**的课程门数（v4.75.0）。
 *   两者一起用于界面上的口径说明，避免用户拿一个混合口径的数字去和学校对账却不知情。
 */
data class GradeSummary(
    val gpa: Double?,
    val averageScore: Double?,
    val totalCredits: Double,
    val courseCount: Int,
    val failedCount: Int,
    val schoolPointCount: Int = 0,
    val convertedPointCount: Int = 0
) {
    /** 加权绩点是否**完全**来自本校口径（决定界面说「教务口径」还是「含换算」）。 */
    val gpaFromSchoolOnly: Boolean
        get() = schoolPointCount > 0 && convertedPointCount == 0

    /** 是否一门课的绩点都取不到本校值。 */
    val gpaConvertedOnly: Boolean
        get() = convertedPointCount > 0 && schoolPointCount == 0
}

/**
 * 粘贴文本解析出的单条成绩草稿。导入前会在 UI 上以可编辑列表展示，
 * 用户确认后才写入数据库——解析只是启发式，不能当作权威结果。
 *
 * @param courseName 解析出的课程名。
 * @param credit 解析出的学分；无法判定时为 null。
 * @param scoreText 解析出的成绩原文（"92" / "优秀"）。
 * @param category 解析出的课程性质（"必修" / "专业选修"…）；无法判定时为 null（v4.75.0）。
 *   此前这条链路完全不解析性质，粘贴导入的成绩 100% 落进「未分类」，
 *   学业情况页因此只剩一行「未分类」——看起来就像「只统计总门数、不统计具体分类」。
 */
data class ParsedGrade(
    val courseName: String,
    val credit: Double?,
    val scoreText: String,
    val category: String? = null
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
     * @param gradePoint 本校教务给出的绩点；手动录入时一般为 null（v4.75.0）。
     */
    suspend fun addGrade(
        semester: String,
        courseName: String,
        credit: Double?,
        scoreText: String?,
        gradePoint: Double? = null,
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
            gradePoint = gradePoint?.takeIf { it >= 0.0 },
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

    /**
     * 更新一条成绩（重新解析分数值，保持来源不变）。
     *
     * R40-02 / R41-04：**分数被改动时必须作废教务口径的本校绩点**。
     * 旧实现直接取 `grade.gradePoint`（= 旧值）无失效判断，于是「92 分/绩点 3.9」被改成
     * 「59 分」后仍是 `gradePoint=3.9` ⇒ `computeSummary` 把不及格课计入已修学分、failedCount 不增，
     * 界面同时出现「不及格」与「绩点 3.9 · 教务」两个互斥徽章。
     *
     * 判据用**实际生效的分数**（解析后比较，不是比较原始文本）：改分数段（92→59）判改动；
     * 「92」→「92.0」只是书写差异，解析后同为 92.0，不作废 —— 否则用户在编辑框里补个「.0」
     * 就会把教务口径的绩点悄悄丢掉。分数未变则保留 gradePoint。
     */
    suspend fun updateGrade(grade: Grade) {
        val newScoreValue = scoreValueOf(grade.scoreText)
        val scoreChanged = newScoreValue != grade.scoreValue
        val updated = grade.copy(
            semester = grade.semester.trim(),
            courseName = grade.courseName.trim().take(100),
            scoreText = grade.scoreText?.trim()?.ifBlank { null },
            scoreValue = newScoreValue,
            gradePoint = if (scoreChanged) null else grade.gradePoint?.takeIf { it >= 0.0 },
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
                            // 本校绩点：教务这次给了就以教务为准（补考/成绩更正后绩点会变），
                            // 没给则保留原值，避免「重新导入一次把上次拿到的本校绩点擦掉」。
                            gradePoint = incoming.gradePoint ?: existing.gradePoint,
                            // N11（2026-10-07）：原实现**漏写 isRetake**，导致重修标记永远落不到已存在的那行上。
                            // 而 GPA / 平均分统计按 `!it.isRetake` 过滤（见本文件 GPA 与平均分计算），
                            // 于是「同名同学期的重修课」会被**永久排除在 GPA 之外**且毫无提示 —— 用户看到的是
                            // 「这门课明明有成绩，却没算进绩点」。
                            // 语义：任一来源标记为重修即视为重修（true 可覆盖 false，不可反向清除）。
                            isRetake = incoming.isRetake || existing.isRetake,
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
     * @param gradePoint 本校教务给出的绩点；不适用时 null（v4.75.0）。
     */
    fun buildGrade(
        semester: String,
        courseName: String,
        credit: Double?,
        scoreText: String?,
        source: String,
        gradePoint: Double? = null,
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
            gradePoint = gradePoint?.takeIf { it >= 0.0 },
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
     * - **每门课的绩点优先取本校值** [Grade.gradePoint]（教务系统按本校规则算出，
     *   各校档位 / 是否含重修 / 等级制折算规则都不同，本机换算表不可能对上）；
     *   本校没给（手动录入、老数据、粘贴导入）时才按当前绩点制由分数换算。
     *   两类各计多少门一并返回（[GradeSummary.schoolPointCount] /
     *   [GradeSummary.convertedPointCount]），界面据此注明口径，不让用户拿一个混合口径的数字去对账。
     * - 「通过 / 不通过」这类不计绩点的成绩，只计入 [GradeSummary.totalCredits]；
     * - 未加权平均分只统计百分制成绩（语义未变，仍是算术平均）。
     */
    fun computeSummary(grades: List<Grade>, scale: GpaScale): GradeSummary {
        val counted = grades.filter { !it.isRetake }
        var weightedPoints = 0.0
        var weightedCredits = 0.0
        var scoreSum = 0.0
        var scoreCount = 0
        var totalCredits = 0.0
        var failedCount = 0
        // R40-13：真正有成绩（分数可解析或成绩文本非空）的门数。
        var gradedCourseCount = 0
        var schoolPointCount = 0
        var convertedPointCount = 0

        counted.forEach { grade ->
            val schoolPoint = grade.gradePoint
            val point = schoolPoint ?: pointOf(grade, scale)
            val credit = grade.credit ?: 0.0
            if (point != null) {
                if (point <= 0.0) failedCount++
                if (credit > 0.0) {
                    weightedPoints += point * credit
                    weightedCredits += credit
                    if (schoolPoint != null) schoolPointCount++ else convertedPointCount++
                }
                if (point > 0.0) totalCredits += credit
            } else if (isCreditEarned(grade.scoreText, grade.scoreValue)) {
                // 不计绩点但**已及格**：仍应计入已修学分。
                // R40-08：旧判据是 isPassed（只认「通过 / 合格 / 达标」）。线性 5.0 制下
                // 「中等 / 及格」被 levelRepresentativeScore 归为 null（无实测依据、刻意不猜），
                // 于是走进这条 else 分支却又不命中 isPassed ⇒ **既不计绩点、也不计学分**，
                // 用户仅仅切换绩点制式分段控件，同一份数据的「已修学分」就少掉若干门
                //（4.0 制下中等→2.7 / 及格→1.5，学分正常计入）。
                // ⇒ 「是否折算绩点」与「是否获得学分」在该制式下不再等价，必须解耦。
                totalCredits += credit
            }
            val percent = grade.scoreValue
            if (percent != null) {
                scoreSum += percent
                scoreCount++
            }
            // R40-13：「已出分门数」此前直接取 counted.size（只过滤 !isRetake），
            // 于是**留空成绩**的课程行也被计入 —— 而空成绩是允许的
            //（UI 校验只拦 isNotBlank && !isValid 的组合，仓库层 KDoc 明写「null 表示只记课程不记分数」）。
            // 后果：同屏「已出分门数 12」与「平均分 87.3（实为 11 门）」口径不一致。
            // 判据与上面的平均分样本同源：有解析分数或非空成绩文本才算「已出分」。
            if (percent != null || !grade.scoreText.isNullOrBlank()) gradedCourseCount++
        }

        return GradeSummary(
            gpa = if (weightedCredits > 0.0) weightedPoints / weightedCredits else null,
            averageScore = if (scoreCount > 0) scoreSum / scoreCount else null,
            totalCredits = totalCredits,
            courseCount = gradedCourseCount,
            failedCount = failedCount,
            schoolPointCount = schoolPointCount,
            convertedPointCount = convertedPointCount
        )
    }

    /**
     * 单门课用于汇总的绩点及其口径（v4.75.0）。
     *
     * @param point 绩点；无法判定时为 null。
     * @param fromSchool true = 取自本校教务（[Grade.gradePoint]），false = 本机按绩点制换算。
     */
    fun effectivePointOf(grade: Grade, scale: GpaScale): Pair<Double?, Boolean> {
        val school = grade.gradePoint
        return if (school != null) school to true else pointOf(grade, scale) to false
    }

    /**
     * 汇总「学业情况」：按课程类别统计已获 / 要求 / 未获学分，以及应修 / 已修 / 未修门数（A2）。
     *
     * 与 [computeSummary] 的分工：后者回答「考得怎么样」（绩点 / 平均分），
     * 本函数回答「培养方案修到哪了」（学分进度 + 门数进度）。
     *
     * 已获学分 = 该类别下**及格**课程的学分之和（百分制 ≥ 60，或等级制「优秀 / 良好 / 中等 / 及格」，
     * 或「通过 / 合格 / 达标」这类达标表述）；
     * 不及格课程不产生学分，但计入 [StudyCategoryProgress.failedCount]，让用户看出差在哪一类。
     * 重修记录与 [computeSummary] 一致地排除，避免同一门课被算两次学分。
     *
     * 门数分三类，**语义刻意不同**（v4.75.0）：
     * - [StudyCategoryProgress.courseCount] = 本机**已有成绩**的门数（变更前唯一的门数口径）；
     * - [StudyCategoryProgress.requiredCourses] = 培养方案要求的**应修门数**（用户手填或钩子从教务读出）；
     * - [StudyCategoryProgress.plannedCourses] / [StudyCategoryProgress.pendingCourses] =
     *   培养方案**课程清单**里的门数与其中**还没出现在成绩表里**的门数。
     *   此前只有第一类，于是「还没修 / 还没出成绩的课程」在页面上根本不存在，
     *   用户看到的永远只是「已经考过几门」——这正是「不统计所有需要上的课程」的成因。
     *
     * @param requirements 用户在学业情况页维护的类别要求。没有成绩的类别同样保留，
     *   这样「先立目标、后出成绩」的用法成立。
     * @param curriculumCourses 培养方案课程清单（`curriculum_courses` 表）；空列表 = 还没导入。
     *   与成绩按**课程名**（去空白后精确匹配）对齐，清单里有、成绩里没有的即为「未修」。
     */
    fun computeStudyProgress(
        grades: List<Grade>,
        requirements: List<CreditRequirement>,
        curriculumCourses: List<CurriculumCourse> = emptyList()
    ): StudyProgress {
        val counted = grades.filter { !it.isRetake }
        val requirementByCategory = requirements.associateBy { it.category.trim() }
        val curriculumByCategory = curriculumCourses.groupBy { it.category?.trim().orEmpty() }

        // v4.76.16：先归位到规范类别再分组（详见顶层函数 alignGradesToCategories 的注释）。
        val gradesByCategory = alignGradesToCategories(counted, requirements, curriculumCourses)
        // 「已出成绩」的课程名集合，**按类别分开**维护（R40-09）。
        // 不及格的课由 failedCount 单独提示，不再重复算作「未修」。
        val gradedNamesByCategory = gradesByCategory.mapValues { (_, list) ->
            list.map { it.courseName.trim() }.filter { it.isNotEmpty() }
        }
        // 跨类别兜底：类别名不一致时，若全局只有一个类别含该课程名，仍按已修处理。
        val gradeCountByCategory = gradesByCategory

        val categories = (
            requirementByCategory.keys + gradesByCategory.keys + curriculumByCategory.keys
            ).map { key ->
            val requirement = requirementByCategory[key]
            val items = gradesByCategory[key].orEmpty()
            var earned = 0.0
            var failed = 0
            items.forEach { grade ->
                val percent = grade.scoreValue ?: scoreValueOf(grade.scoreText)
                val hasScore = percent != null || !grade.scoreText.isNullOrBlank()
                // 等级制（优秀 / 良好 / 中等 / 及格）没有百分制分数，但同样是及格：
                // 能按等级词折算出 > 0 的绩点即算通过，避免把及格课报成挂科。
                // R40-08：复用与 computeSummary 同一套「已及格」判据，
                // 避免「总览学分」与「分类学分」两处口径再次分叉（旧实现固定用 SCALE_4 试折算，
                // 且只认「通过 / 合格 / 达标」，5.0 制下「中等 / 及格」会被判成挂科）。
                val passed = isCreditEarned(grade.scoreText, percent)
                when {
                    // 及格：只累加学分，不作为「差了多少」的负担
                    passed -> earned += grade.credit ?: 0.0
                    // 有成绩但没及格：记一笔挂科，提示这类还差在哪
                    hasScore -> failed++
                }
            }
            val planItems = curriculumByCategory[key].orEmpty()
            val gradedHere = gradedNamesByCategory[key].orEmpty().toSet()
            val pendingList = planItems
                .filter { plan ->
                    val courseName = plan.courseName.trim()
                    if (courseName.isEmpty()) return@filter false
                    if (courseName in gradedHere) return@filter false
                    // R40-09 跨类别兜底：同名课程只出现在别的类别时仍算已修，
                    // 避免「已修门数」与「已获学分」因类别名不一致而各说各话。
                    val elsewhere = gradeCountByCategory.filterKeys { it != key }
                        .values.count { group -> group.any { g -> g.courseName.trim() == courseName } }
                    elsewhere == 0
                }
                .map {
                    PendingCurriculumCourse(
                        courseName = it.courseName.trim(),
                        category = key,
                        credit = it.credit,
                        suggestedTerm = it.suggestedTerm
                    )
                }
            StudyCategoryProgress(
                category = key,
                displayName = requirement?.effectiveDisplayName.orEmpty(),
                requiredCredits = requirement?.requiredCredits ?: 0.0,
                earnedCredits = earned,
                courseCount = items.size,
                failedCount = failed,
                requiredCourses = requirement?.requiredCourses,
                plannedCourses = planItems.size,
                pendingCourses = pendingList.size,
                pendingCourseList = pendingList
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
            hasAnyGrade = counted.isNotEmpty(),
            totalPlannedCourses = curriculumCourses.count { it.courseName.trim().isNotEmpty() },
            totalPendingCourses = categories.sumOf { it.pendingCourses }
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

    /**
     * 百分制分数转绩点。实现见顶层纯函数 [gpaPointFromPercent]
     * （阶梯档 / 线性档两类曲线的区别与取证见那里的注释）。
     */
    fun pointFromPercent(score: Double, scale: GpaScale): Double =
        gpaPointFromPercent(score, scale)

    /**
     * 等级制成绩转绩点；无法换算（如「通过」）时返回 null。
     * 实现见顶层纯函数 [gpaPointFromLevel]。
     */
    fun pointFromLevel(scoreText: String?, scale: GpaScale): Double? =
        gpaPointFromLevel(scoreText, scale)

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

    /**
     * 是否为「通过」类（不计绩点但算学分）的成绩文本。
     *
     * @deprecated 保留给既有语义引用；学分判据请用 [isCreditEarned]。
     */
    private fun isPassed(scoreText: String?): Boolean {
        val text = scoreText?.trim() ?: return false
        return text.startsWith("通过") || text.startsWith("合格") || text.startsWith("达标")
    }

    /**
     * 该成绩文本是否代表**已及格**（无论能否折算绩点）。
     *
     * R40-08：判据必须独立于绩点折算。原先学分是否计入取决于 `point != null`，
     * 于是「不能折算绩点」被当成了「没拿到学分」的代理量 —— 在线性 5.0 制下这是错的：
     * 「中等 / 及格」刻意不折算（无实测依据），但它们**显然已及格**，学分必须计入。
     *
     * 覆盖：百分制 ≥ 及格线按 [LINEAR_5_PASS] 的同一阈值判断（60），
     * 其余按「不及格类」前缀否定，其余等级词（优秀/良好/中等/及格/合格/通过/达标/P）视为及格。
     * 判定不出（空文本、纯文字）时返回 false —— 与「不计入」的保守方向一致。
     */
    private fun isCreditEarned(scoreText: String?, scoreValue: Double?): Boolean {
        if (scoreValue != null) return scoreValue >= LINEAR_5_PASS
        val text = scoreText?.trim()?.uppercase().orEmpty()
        if (text.isEmpty()) return false
        if (text.startsWith("不及格") || text.startsWith("不合格") ||
            text.startsWith("未通过") || text.startsWith("失败") ||
            text.startsWith("不通过") || text == "F" || text.startsWith("F ")
        ) {
            return false
        }
        return text.startsWith("优秀") || text.startsWith("良好") || text.startsWith("中等") ||
            text.startsWith("及格") || text.startsWith("合格") || text.startsWith("通过") ||
            text.startsWith("达标") || text.startsWith("A") || text.startsWith("B") ||
            text.startsWith("C") || text.startsWith("D") || text.startsWith("P")
    }

    /**
     * 解析用户粘贴的教务成绩页文本（本地解析，不联网）。
     *
     * 启发式规则：逐行处理，从右往左找到第一个「像成绩」的 token
     * —— 1–100 的整数，或不小于 50 的小数，或等级词（优秀 / 良好 / 92 分…）；
     * 再往左找 0–30 的数字当学分；其余部分拼成课程名。
     * 识别不到的行走不了解析（如纯表头行），由 UI 提示用户手动补录。
     *
     * **课程性质也顺手认出来**（v4.75.0）：粘贴文本里性质常被挤进课程名
     * （「高等数学 必修 5 92」里「必修」会落进 name），因此先看课程名的**最后一个 token**，
     * 再看「学分与成绩之间」的列，命中 [CourseCategoryLexicon] 就把它从课程名里摘出来。
     * 此前这条链路完全不解析性质，导入的成绩 100% 落「未分类」。
     *
     * 刻意**不猜绩点**：粘贴文本里的数字列没有可靠的自描述（「3.0」可能是学分也可能是绩点），
     * 猜错会伪造出一个学校根本不存在的小数绩点。绩点只从教务抓取（有明确列名/接口字段）得到。
     *
     * 解析结果一律作为**草稿**返回，UI 必须先展示可编辑列表让用户确认再入库。
     */
    fun parsePastedGrades(text: String): List<ParsedGrade> = parsePastedGradeText(text)

    private companion object {
        /** 允许 "92"、"92.5"、"=92"、"92分"、"92 分" 等写法。 */
        val PERCENT_PATTERN = Regex("""\d{1,3}(?:\.\d)?(?=\s*分|$)""")
    }
}

/**
 * 百分制分数转绩点（顶层纯函数）。
 *
 * 三种制式对应两类完全不同的换算曲线：
 * - [GpaScale.SCALE_4] / [GpaScale.SCALE_5]：**阶梯档**，逐段取常数；
 * - [GpaScale.LINEAR_5]：**线性**，`(分数 − 80) / 10 + 3.0`，见 [LINEAR_5_BASE] 等常量。
 *
 * 做成顶层函数而不是 Repository 的成员方法，是为了让它能脱离 Room / Koin 依赖
 * 在单测里直接跑（真实样本见 `GpaScaleLinearTest`）。
 */
internal fun gpaPointFromPercent(score: Double, scale: GpaScale): Double = when (scale) {
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

    GpaScale.LINEAR_5 -> {
        if (score >= LINEAR_5_PASS) {
            val point = (score - LINEAR_5_ORIGIN) / LINEAR_5_STEP + LINEAR_5_BASE
            // 100 分及以上不外推，避免算出 5.1 这种学校不存在的值
            if (point > LINEAR_5_MAX) LINEAR_5_MAX else point
        } else {
            0.0
        }
    }
}

/**
 * 等级制成绩转绩点（顶层纯函数）；无法换算时返回 null。
 *
 * 「通过 / 不通过」返回 null：这类课程学校本身不计绩点，强行折算会凭空抬高或拉低 GPA。
 *
 * [GpaScale.LINEAR_5] 不在此硬编码等级值，而是**先把等级折成「代表分数」再走线性公式** ——
 * 这样与百分制课程共用同一条曲线，不会出现「等级课与百分制课用两套互不相容档位」。
 */
internal fun gpaPointFromLevel(scoreText: String?, scale: GpaScale): Double? {
    val text = scoreText?.trim()?.uppercase() ?: return null
    if (text.isEmpty()) return null
    // 不及格先判：任何制式下它都是 0，不能走到「代表分数」分支去碰运气
    if (text.startsWith("不及格") || text.startsWith("不合格") ||
        text.startsWith("未通过") || text.startsWith("失败") || text.startsWith("F")
    ) {
        return 0.0
    }
    if (scale == GpaScale.LINEAR_5) {
        val representative = levelRepresentativeScore(text) ?: return null
        return gpaPointFromPercent(representative, scale)
    }
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

        // 「通过 / 不通过」不折算绩点
        text.startsWith("通过") || text.startsWith("不通过") -> null

        else -> null
    }
}

/** 行内分隔：制表符、逗号、分号、竖线、连续空格、单个空格。 */
private val PASTE_TOKEN_SEPARATOR = Regex("""[\t,，;；]+|\s+""")

/**
 * 线性 5.0 制的四个常量。提成命名常量而不是散在 `when` 里，
 * 是为了让「这条规则从哪来」的注释和实现贴在一起，也让单测能直接引用同一组数。
 *
 * 取证见 `build_qa/ntu-gpa-rule.md`：南通大学实测 10 门课 10/10 精确吻合。
 */
internal const val LINEAR_5_ORIGIN = 80.0   // 基准分：80 分 → 3.0
internal const val LINEAR_5_STEP = 10.0     // 每 10 分涨 1.0
internal const val LINEAR_5_BASE = 3.0      // 基准分对应的绩点
internal const val LINEAR_5_PASS = 60.0     // 及格线；低于此计 0
internal const val LINEAR_5_MAX = 5.0       // 绩点上限（100 分恰好 5.0）

/**
 * 等级 → 「该校线性制里的代表分数」，只认**有实测依据**的等级。
 *
 * 南通大学实测（2026-10-07，学生真实成绩 10 门）：
 * `优秀` 门课的 `bfzcj` 全为 95（绩点 4.5）、`良好` 门全为 85（绩点 3.5）⇒ 推得代表分数即 95 / 85。
 *
 * **中等 / 及格 / 不及格 一律返回 null**，不是遗漏而是**故意**：
 * 该样本里没有这类课程，学校怎么折算**没有证据**，猜一个数会把误差伪装成「算过了」——
 * 这正是本次要修的毛病。这里返回 null 会让该门课不计入绩点并在口径说明里体现为「未换算」，
 * 比编一个看起来合理的数字诚实。
 */
internal fun levelRepresentativeScore(levelText: String?): Double? {
    val t = levelText?.trim()?.uppercase() ?: return null
    return when {
        t.startsWith("优秀") || t == "优" || t.startsWith("A") -> 95.0
        t.startsWith("良好") || t == "良" || t.startsWith("B") -> 85.0
        else -> null
    }
}

/** 等级制成绩词（用于粘贴解析时认出成绩列）。 */
private val PASTE_LEVEL_WORDS = listOf(
    "优秀", "良好", "中等", "及格", "不及格", "合格", "不合格",
    "通过", "不通过", "优", "良", "中", "差"
)

/**
 * 粘贴导入的成绩文本解析（v4.75.0 从 [GradeRepository.parsePastedGrades] 提为顶层函数）。
 *
 * 提出来是为了**可测**：这是全仓最容易出错又最难察觉的一段启发式 ——
 * 分类认错会让学业情况页的类别统计整体错位，而页面上的数字看起来仍然「很正常」。
 * 顶层纯函数可以脱离 Room 直接跑单测（见 `GradeStudyParsingTest`）。
 *
 * 规则：逐行处理，从右往左找第一个「像成绩」的 token（1–100 的整数，或不小于 50 的小数，
 * 或等级词）；再往左找 0–30 的数字当学分；性质优先看课程名尾部，其次看学分与成绩之间的列。
 * 刻意**不猜绩点**：粘贴文本里「3.0」可能既是学分也可能被误当绩点，猜错会伪造出学校没有的小数绩点。
 */
internal fun parsePastedGradeText(text: String): List<ParsedGrade> {
    val result = mutableListOf<ParsedGrade>()
    text.lineSequence().forEach { rawLine ->
        val line = rawLine.replace('\u3000', ' ').replace('|', ' ').trim()
        if (line.isEmpty()) return@forEach
        val tokens = line.split(PASTE_TOKEN_SEPARATOR).filter { it.isNotBlank() }
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
            if (PASTE_LEVEL_WORDS.any { token.startsWith(it) }) {
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

        val nameParts = tokens.subList(0, nameEnd)
            .filterNot { token ->
                token.toDoubleOrNull() != null || token == "学分" || token == "成绩" || token == "分数"
            }
        // 性质优先从课程名尾部摘（「高等数学 必修」），其次看学分与成绩之间的列
        val tailCategory = nameParts.lastOrNull()?.takeIf { CourseCategoryLexicon.matches(it) }
        val category = tailCategory
            ?: CourseCategoryLexicon.firstMatch(tokens.subList(nameEnd, scoreIndex))
        val finalNameParts = if (tailCategory != null) nameParts.dropLast(1) else nameParts

        val name = finalNameParts.joinToString(" ").trim()
        if (name.isEmpty()) return@forEach
        result.add(
            ParsedGrade(
                courseName = name,
                credit = credit,
                scoreText = scoreText,
                category = category?.trim()?.ifBlank { null }
            )
        )
    }
    return result
}

/**
 * 把成绩**归位到规范类别**再分组（v4.76.16，提为顶层纯函数以便单测，惯例同 parsePastedCurriculumText）。
 *
 * 两侧类别名不一致（R40-09 已记载）：成绩侧是教务「性质」列的**短名**（「必修」），而要求 / 清单侧是
 * 学校**长名**（「学科基础课程平台/必修」，见 NTU/ntu.js）。旧实现直接按 grade.category 分组 ⇒
 * 成绩落在「必修」而类别 key 是长名 ⇒ earnedCredits 恒为 0；但「已修门数」走课程名跨类别兜底，
 * 所以是对的 —— 于是同一页面出现「已修 2 门」与「已获 0 学分」并存（南通大学实测反馈）。
 *
 * 归位规则（按优先级）：
 * 1. 成绩自带类别本身已是已知类别（要求或清单里有）→ 用它；
 * 2. 否则以**课程清单的课程名**为桥梁，归到该课所属的类别；
 * 3. 都定不了 → 保留原值（含空串 = 未分类）。
 *
 * 这样 earnedCredits 与 pendingCourses 用同一套口径，不再各说各话。
 *
 * 注意：必须写在 GradeRepository 类**之后**。放在类之前会让 Koin/KSP 报
 * 「Missing dependency: GradeRepository」（KOIN-D001）—— 注解处理器对文件内声明顺序敏感。
 */
internal fun alignGradesToCategories(
    grades: List<Grade>,
    requirements: List<CreditRequirement>,
    curriculumCourses: List<CurriculumCourse>
): Map<String, List<Grade>> {
    val requirementByCategory = requirements.associateBy { it.category.trim() }
    val curriculumByCategory = curriculumCourses.groupBy { it.category?.trim().orEmpty() }
    val planNameToCategory = LinkedHashMap<String, String>()
    curriculumByCategory.forEach { (cat, plans) ->
        plans.forEach { plan ->
            val name = plan.courseName.trim()
            if (name.isNotEmpty() && !planNameToCategory.containsKey(name)) {
                planNameToCategory[name] = cat
            }
        }
    }
    return grades.groupBy { grade ->
        val raw = grade.category?.trim().orEmpty()
        val known = raw.isNotEmpty() &&
            (requirementByCategory.containsKey(raw) || curriculumByCategory.containsKey(raw))
        if (known) raw else planNameToCategory[grade.courseName.trim()] ?: raw
    }
}