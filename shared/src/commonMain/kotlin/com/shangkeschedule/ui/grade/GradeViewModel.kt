package com.shangkeschedule.ui.grade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.Grade
import com.shangkeschedule.data.model.GpaScale
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.GradeRepository
import com.shangkeschedule.data.repository.GradeSummary
import com.shangkeschedule.data.repository.ParsedGrade
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

/** 按学期分组的一段成绩（[semester] 为空表示未标注学期）。 */
data class GradeGroup(
    val semester: String,
    val grades: List<Grade>
)

/**
 * 成绩 / GPA 页 ViewModel（v4.66.0 新增）。
 *
 * 换算绩点**不落库**（随绩点制实时算），本校绩点**落库**（教务给出的既成事实，见
 * [Grade.gradePoint]），因此这里把「成绩列表」与「当前绩点制」两条流合起来算 [summary]，
 * 切换 4.0 / 5.0 制时列表本身不变，只有**本校未提供绩点的那部分**换算值跟着变。
 */
@KoinViewModel
class GradeViewModel(
    private val gradeRepository: GradeRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    /** 全部成绩（数据库按学期倒序返回）。 */
    val grades: StateFlow<List<Grade>> = gradeRepository.getAllGrades()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 当前绩点制（持久化在应用设置里，全局唯一）。 */
    val gpaScale: StateFlow<GpaScale> = appSettingsRepository.getAppSettings()
        .map { it.gpaScale }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GpaScale.SCALE_4
        )

    /** 顶部汇总（加权绩点 / 平均分 / 已修学分 / 门数 / 不及格数）。 */
    val summary: StateFlow<GradeSummary> = combine(
        gradeRepository.getAllGrades(),
        gpaScale
    ) { list, scale ->
        gradeRepository.computeSummary(list, scale)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GradeSummary(
            gpa = null,
            averageScore = null,
            totalCredits = 0.0,
            courseCount = 0,
            failedCount = 0
        )
    )

    /**
     * 按学期分组的列表。
     *
     * 排序：有学期标签的组在前（学期名倒序，最近的学期排最上），
     * 未标注学期的一组固定放最后（抓取不到学期时成绩不会插到时间线中间）。
     */
    val groupedGrades: StateFlow<List<GradeGroup>> = grades
        .map { list ->
            list.groupBy { it.semester }
                .map { (semester, items) -> GradeGroup(semester, items) }
                .sortedWith(
                    compareByDescending<GradeGroup> { it.semester.isNotBlank() }
                        .thenByDescending { it.semester }
                )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 切换绩点制（4.0 / 5.0），立即影响本页所有绩点与汇总。 */
    suspend fun setGpaScale(scale: GpaScale) {
        appSettingsRepository.updateGpaScale(scale)
    }

    /** 手动新增一条成绩。 */
    suspend fun addGrade(
        semester: String,
        courseName: String,
        credit: Double?,
        scoreText: String?,
        category: String?,
        isRetake: Boolean,
        note: String?
    ) {
        gradeRepository.addGrade(
            semester = semester,
            courseName = courseName,
            credit = credit,
            scoreText = scoreText,
            category = category,
            isRetake = isRetake,
            note = note
        )
    }

    /** 保存编辑后的成绩（不改来源）。 */
    suspend fun updateGrade(grade: Grade) {
        gradeRepository.updateGrade(grade)
    }

    /** 删除一条成绩。 */
    suspend fun deleteGrade(gradeId: String) {
        gradeRepository.deleteGrade(gradeId)
    }

    /** 清空全部成绩（UI 必须先二次确认）。 */
    suspend fun deleteAllGrades() {
        gradeRepository.deleteAllGrades()
    }

    /** 本地解析粘贴文本（不联网），返回草稿供 UI 预览编辑。 */
    fun parsePastedGrades(text: String): List<ParsedGrade> =
        gradeRepository.parsePastedGrades(text)

    /** 把解析草稿转成待导入记录（来源标记为「粘贴」）。 */
    fun buildPastedGrades(semester: String, drafts: List<ParsedGrade>): List<Grade> =
        drafts.map { draft ->
            gradeRepository.buildGrade(
                semester = semester,
                courseName = draft.courseName,
                credit = draft.credit,
                scoreText = draft.scoreText,
                source = Grade.SOURCE_PASTE,
                // v4.75.0：粘贴解析出的课程性质一并带上。此前这里不传 category，
                // 粘贴导入的成绩 100% 落「未分类」，学业情况页就只剩一行「未分类」。
                category = draft.category
            )
        }

    /** 批量导入（内部按「学期 + 课程名」去重，重复导入不会翻倍）。 */
    suspend fun importGrades(items: List<Grade>) {
        gradeRepository.importGrades(items)
    }

    /** 单条成绩的绩点（供列表右下角展示；无法换算时为 null）。 */
    fun pointOf(grade: Grade, scale: GpaScale): Double? =
        gradeRepository.pointOf(grade, scale)

    /**
     * 单条成绩**实际用于展示与汇总**的绩点及其口径（v4.75.0）。
     *
     * @return `first` = 绩点（本校优先，其次换算）；`second` = 是否取自本校教务。
     */
    fun effectivePointOf(grade: Grade, scale: GpaScale): Pair<Double?, Boolean> =
        gradeRepository.effectivePointOf(grade, scale)

    /**
     * 是否为「挂科」：百分制分数 < 60，或等级词为不及格类（不及格 / 不合格 / 未通过 / 失败 / F）。
     *
     * 不能用 `scoreValue ?: 100.0` 判断 —— 纯文本等级成绩没有百分制分数，
     * 那样写会把「不及格」当成 100 分，徽标永远不出现。
     */
    fun isFailed(grade: Grade): Boolean {
        val percent = grade.scoreValue ?: gradeRepository.scoreValueOf(grade.scoreText)
        if (percent != null) return percent < 60.0
        // pointFromLevel 对不及格类等级词返回 0.0，对无法识别的文本返回 null
        return gradeRepository.pointFromLevel(grade.scoreText, GpaScale.SCALE_4) == 0.0
    }
}
