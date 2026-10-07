package com.shangkeschedule.data.repository

import androidx.room3.withWriteTransaction
import com.shangkeschedule.data.db.main.CurriculumCourse
import com.shangkeschedule.data.db.main.CurriculumCourseDao
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.model.CourseCategoryLexicon
import com.shangkeschedule.data.model.CreditRequirement
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 粘贴文本解析出的单条「培养方案课程」草稿（v4.75.0）。
 *
 * @param courseName 课程名。
 * @param credit 该课程学分；无法判定时为 null。
 * @param category 课程类别；无法判定时为 null。
 */
data class ParsedCurriculumCourse(
    val courseName: String,
    val credit: Double?,
    val category: String? = null
)

/**
 * 培养方案课程清单的解析结果草稿（v4.75.0）。
 *
 * 粘贴一段教务文本时，两种信息可能同时出现，因此一次解析两类都返回：
 * - [requirements] 类别汇总行（「XXX课程平台 要求学分:47.0」→「必修课程 要求学分:41.0」）
 *   ⇒ 变成 [CreditRequirement]（类别 + 应修学分 + 应修门数）；
 * - [courses] 逐门课程行 ⇒ 变成课程清单。
 *
 * 与成绩侧一样，解析只是启发式，UI 必须先展示可编辑草稿让用户确认再入库。
 */
data class ParsedCurriculum(
    val requirements: List<CreditRequirement> = emptyList(),
    val courses: List<ParsedCurriculumCourse> = emptyList()
) {
    /** 是否什么都没解析出来（UI 用来提示「换一段文本」而不是静默成功）。 */
    val isEmpty: Boolean get() = requirements.isEmpty() && courses.isEmpty()
}

/**
 * 培养方案课程清单仓库（v4.75.0 学业情况）。
 *
 * 存在理由：学业情况此前只有「成绩（已出分）+ 类别学分要求」，
 * **还没修 / 还没出成绩的课程在结构上不存在**，页面只能回答「考过了几门」，
 * 无法回答「培养方案一共要上哪些课、还差几门」。本仓库负责那张清单的增删改查与本地解析。
 *
 * 与成绩表按**课程名**软匹配（不建外键）：清单是规划、成绩是事实，两者互不牵连。
 */
@OptIn(ExperimentalUuidApi::class)
@Single
class CurriculumRepository(
    private val database: MainAppDatabase,
    private val dao: CurriculumCourseDao
) {

    /** 全部培养方案课程。 */
    fun getAll(): Flow<List<CurriculumCourse>> = dao.getAll()

    /** 全部培养方案课程的一次性快照（导出 / 汇总用）。 */
    suspend fun getAllOnce(): List<CurriculumCourse> = dao.getAllOnce()

    /**
     * 新增一条培养方案课程。
     *
     * @param suggestedTerm 建议修读学期；null / 空白 = 未填。
     */
    suspend fun addCourse(
        courseName: String,
        category: String? = null,
        credit: Double? = null,
        suggestedTerm: String? = null
    ): CurriculumCourse {
        val now = Clock.System.now().toEpochMilliseconds()
        val item = CurriculumCourse(
            id = Uuid.random().toString(),
            courseName = courseName.trim().take(CurriculumCourse.MAX_COURSE_NAME_LENGTH),
            category = category?.trim()?.ifBlank { null },
            credit = credit?.takeIf { it > 0.0 },
            suggestedTerm = suggestedTerm?.trim()?.ifBlank { null },
            source = CurriculumCourse.SOURCE_MANUAL,
            createdAt = now,
            updatedAt = now
        )
        database.withWriteTransaction { dao.insertAll(listOf(item)) }
        return item
    }

    /** 更新一条培养方案课程。 */
    suspend fun updateCourse(item: CurriculumCourse) {
        val updated = item.copy(
            courseName = item.courseName.trim().take(CurriculumCourse.MAX_COURSE_NAME_LENGTH),
            category = item.category?.trim()?.ifBlank { null },
            credit = item.credit?.takeIf { it > 0.0 },
            suggestedTerm = item.suggestedTerm?.trim()?.ifBlank { null },
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )
        database.withWriteTransaction {
            if (dao.getByIdOnce(updated.id) != null) dao.update(updated) else dao.insertAll(listOf(updated))
        }
    }

    /** 删除一条。 */
    suspend fun deleteCourse(courseId: String) {
        database.withWriteTransaction { dao.deleteById(courseId) }
    }

    /** 清空清单（调用方必须二次确认）。 */
    suspend fun deleteAll() {
        database.withWriteTransaction { dao.deleteAll() }
    }

    /**
     * 构造待导入的课程记录（供粘贴导入 / 教务钩子导入复用）。
     *
     * @param source [CurriculumCourse.SOURCE_PASTE] 或 [CurriculumCourse.SOURCE_IMPORT]。
     */
    fun buildCourse(
        courseName: String,
        category: String?,
        credit: Double?,
        suggestedTerm: String? = null,
        source: String = CurriculumCourse.SOURCE_PASTE
    ): CurriculumCourse {
        val now = Clock.System.now().toEpochMilliseconds()
        return CurriculumCourse(
            id = Uuid.random().toString(),
            courseName = courseName.trim().take(CurriculumCourse.MAX_COURSE_NAME_LENGTH),
            category = category?.trim()?.ifBlank { null },
            credit = credit?.takeIf { it > 0.0 },
            suggestedTerm = suggestedTerm?.trim()?.ifBlank { null },
            source = source,
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * 批量导入课程清单，**按课程名去重**：同名的以本次导入为准（补上类别 / 学分），
     * 未命中的插入。重复导入同一份培养方案不会翻倍。
     */
    suspend fun importCourses(items: List<CurriculumCourse>) {
        if (items.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        database.withWriteTransaction {
            items.forEach { incoming ->
                val name = incoming.courseName.trim()
                if (name.isEmpty()) return@forEach
                val existing = dao.findByNameOnce(name)
                if (existing != null) {
                    dao.update(
                        existing.copy(
                            category = incoming.category ?: existing.category,
                            credit = incoming.credit ?: existing.credit,
                            suggestedTerm = incoming.suggestedTerm ?: existing.suggestedTerm,
                            updatedAt = now
                        )
                    )
                } else {
                    dao.insertAll(
                        listOf(
                            incoming.copy(
                                courseName = name,
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
     * 解析用户粘贴的培养方案 / 学业情况页文本（本地解析，不联网）。
     *
     * 两类行分别处理：
     * 1. **类别汇总行**（含「要求学分」）：取出应修学分；类别名重建规则与适配脚本
     *    `shangkeScanStudy` **刻意一致** —— 平台行（含「课程平台」）只作为后续叶子行的前缀，
     *    自身不入结果（否则同一个平台会被算两遍：南通大学实测 11 条全传得 310 学分，
     *    只算叶子才是真实的 172）；叶子行是「必修课程 / 选修课程 / 任选课程 / 限选课程」
     *    这类通用名时，用最近一次的平台名拼成「通识教育课程平台/必修」。
     *    行末若带「共（N）门」则取 N 作为应修门数。
     * 2. **课程行**：序号（纯整数首列）→ 性质（[CourseCategoryLexicon]）→ 学分（最后一个 0–30 的数字）
     *    依次剥离，其余拼成课程名。表头 / 合计行直接跳过。
     *
     * 解析结果作为草稿返回，UI 先展示再入库。
     */
    fun parsePastedCurriculum(text: String): ParsedCurriculum = parsePastedCurriculumText(text)
}

/** 「要求学分:47.0」/「要求学分 47」/「要求学分：47.0」。 */
private val CURRICULUM_REQUIRED_CREDITS = Regex("""要求学分\s*[:：]?\s*([\d.]+)""")

/** 行为「共（12）门」「共 12 门」「共(12)门」。 */
private val CURRICULUM_REQUIRED_COURSE_COUNT = Regex("""共\s*[（(]?\s*(\d+)\s*[）)]?\s*门""")

/** 平台行特征（与适配脚本 `shangkeScanStudy` 同一判据）。 */
private val CURRICULUM_PLATFORM_SUFFIX = Regex("""课程平台""")

/** 无区分度的叶子类别名：单独出现时要用平台名做前缀消歧。 */
private val CURRICULUM_GENERIC_LEAF_LABELS = setOf("必修课程", "选修课程", "任选课程", "限选课程")

/** 表头 / 汇总行关键词，直接跳过。 */
private val CURRICULUM_HEADER_WORDS = listOf(
    "课程名称", "课程代码", "课程编号", "合计", "小计", "总计", "平均",
    "备注", "获得学分", "未获得学分", "应修学分", "已修学分", "学分要求"
)

/** 行内分隔：制表符、逗号、分号、竖线、连续空格。 */
private val CURRICULUM_TOKEN_SEPARATOR = Regex("""[\t,，;；]+|\s+""")

/** 学分 token：允许 "3"、"3.0"、"3学分"、"学分3"。 */
private val CURRICULUM_CREDIT_TOKEN = Regex("""\d{1,2}(?:\.\d+)?(?!\d)""")

/**
 * 粘贴导入的培养方案文本解析（v4.75.0 从 [CurriculumRepository.parsePastedCurriculum] 提为顶层函数）。
 *
 * 提出来是为了**可测**：这段启发式决定「哪些课算没修」，而平台 / 叶子两级的合并规则
 * （叶子类别名要用平台名前缀消歧）在适配脚本与本地解析两侧必须完全一致，
 * 任何一侧改错都会让学分归不进类别，页面上却只是数字偏小、看不出错。
 * 顶层纯函数可脱离 Room 直接跑单测（见 `GradeStudyParsingTest`）。
 */
internal fun parsePastedCurriculumText(text: String): ParsedCurriculum {
    val requirements = mutableListOf<CreditRequirement>()
        val courses = mutableListOf<ParsedCurriculumCourse>()
        var currentPlatform = ""

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.replace('\u3000', ' ').replace('|', ' ').trim()
            if (line.isEmpty()) return@forEach

            if (CURRICULUM_REQUIRED_CREDITS.containsMatchIn(line)) {
                val credits = CURRICULUM_REQUIRED_CREDITS.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                    ?: return@forEach
                if (credits <= 0.0) return@forEach
                // 类别名 = 行内「要求学分」之前的文字，去掉数字与标点噪音
                val markerStart = CURRICULUM_REQUIRED_CREDITS.find(line)?.range?.first ?: return@forEach
                val rawCategory = line.substring(0, markerStart)
                    .replace(Regex("""[\d.]+"""), " ")
                    .replace(Regex("""[:：,，、\t]+"""), " ")
                    .trim()
                if (rawCategory.isEmpty()) return@forEach

                if (CURRICULUM_PLATFORM_SUFFIX.containsMatchIn(rawCategory)) {
                    // 平台行：只记前缀，不产出要求（与适配脚本口径一致）
                    currentPlatform = rawCategory.replace(Regex("""\s+"""), "")
                    return@forEach
                }
                val leaf = rawCategory.replace(Regex("""\s+"""), "")
                val category = if (leaf in CURRICULUM_GENERIC_LEAF_LABELS && currentPlatform.isNotEmpty()) {
                    currentPlatform + "/" + leaf.replace("课程", "")
                } else {
                    leaf
                }
                val requiredCourses = CURRICULUM_REQUIRED_COURSE_COUNT.find(line)
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?.takeIf { it > 0 }
                // 同一类别重复出现时以最后一条为准
                requirements.removeAll { it.category == category }
                requirements.add(
                    CreditRequirement(
                        category = category,
                        requiredCredits = credits.coerceAtMost(CreditRequirement.MAX_REQUIRED_CREDITS),
                        requiredCourses = requiredCourses?.coerceAtMost(CreditRequirement.MAX_REQUIRED_COURSES)
                    )
                )
                return@forEach
            }

            if (CURRICULUM_HEADER_WORDS.any { line.contains(it) }) return@forEach
            val tokens = line.split(CURRICULUM_TOKEN_SEPARATOR).filter { it.isNotBlank() }
            if (tokens.isEmpty()) return@forEach
            // 序号列：首列是纯整数且后面还有内容时才剥掉
            val body = if (tokens.size >= 3 && tokens.first().toIntOrNull() != null) {
                tokens.drop(1)
            } else {
                tokens
            }
            val category = CourseCategoryLexicon.firstMatch(body)
            val withoutCategory = if (category == null) body else body.filterNot { it == category }
            // 学分取**最后一个** 0–30 的数字：形如「高等数学 5.0 第1学期」时避开学期里的数字
            var credit: Double? = null
            var creditIndex = -1
            withoutCategory.forEachIndexed { index, token ->
                val value = CURRICULUM_CREDIT_TOKEN.find(token)?.value?.toDoubleOrNull()
                if (value != null && value > 0.0 && value <= 30.0) {
                    credit = value
                    creditIndex = index
                }
            }
            val nameParts = withoutCategory.filterIndexed { index, _ -> index != creditIndex }
                .filterNot { it.toDoubleOrNull() != null }
            val courseName = nameParts.joinToString(" ").trim()
            if (courseName.length < 2) return@forEach
            courses.add(
                ParsedCurriculumCourse(
                    courseName = courseName.take(CurriculumCourse.MAX_COURSE_NAME_LENGTH),
                    credit = credit,
                    category = category
                )
            )
        }

    return ParsedCurriculum(requirements = requirements, courses = courses)
}
