package com.shangkeschedule.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CurriculumCourse
import com.shangkeschedule.data.model.CreditRequirement
import com.shangkeschedule.data.model.StudyProgress
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CurriculumRepository
import com.shangkeschedule.data.repository.GradeRepository
import com.shangkeschedule.data.repository.ParsedCurriculum
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/**
 * 学业情况页。
 *
 * 学业完成度是**三路数据**的交叉计算：成绩、用户设定的各类别学分要求、
 * 培养方案课程清单（v4.75.0）。三者都在本机，因此这里把三个仓库的 Flow 直接 combine，
 * 不在中间再落一份缓存：成绩页新增/修改成绩、设置页改要求、本页导入课程清单，
 * 本页都会自动跟着变。
 *
 * 课程清单存在的意义：此前只有「成绩 + 学分要求」，**还没修 / 还没出成绩的课程在结构上
 * 不存在**，页面只能回答「考过了几门」，无法回答「培养方案一共要上哪些课、还差几门」。
 */
@KoinViewModel
class StudyProgressViewModel(
    private val gradeRepository: GradeRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val curriculumRepository: CurriculumRepository
) : ViewModel() {

    /** 培养方案课程清单（供「未修课程」与清单管理区展示）。 */
    val curriculum: StateFlow<List<CurriculumCourse>> = curriculumRepository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val progress: StateFlow<StudyProgress> = combine(
        gradeRepository.getAllGrades(),
        appSettingsRepository.getAppSettings(),
        curriculumRepository.getAll()
    ) { grades, settings, courses ->
        gradeRepository.computeStudyProgress(grades, settings.creditRequirements, courses)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StudyProgress()
    )

    /**
     * 新增或覆盖一条类别学分要求（按类别名匹配，即与成绩的 `Grade.category` 对齐）。
     *
     * @param requiredCourses 应修门数；null = 不设（不影响展示）。
     */
    fun upsertRequirement(
        category: String,
        requiredCredits: Double,
        requiredCourses: Int?,
        displayName: String
    ) {
        val key = category.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            appSettingsRepository.mutateCreditRequirements { current ->
                current.filterNot { it.category.trim() == key } +
                    CreditRequirement(
                        category = key,
                        requiredCredits = requiredCredits.coerceIn(0.0, CreditRequirement.MAX_REQUIRED_CREDITS),
                        displayName = displayName.trim(),
                        requiredCourses = requiredCourses
                            ?.takeIf { it > 0 }
                            ?.coerceAtMost(CreditRequirement.MAX_REQUIRED_COURSES)
                    )
            }
        }
    }

    /**
     * 删除一条类别要求（只删要求，不动成绩与课程清单）。
     */
    fun removeRequirement(category: String) {
        val key = category.trim()
        viewModelScope.launch {
            appSettingsRepository.mutateCreditRequirements { current ->
                current.filterNot { it.category.trim() == key }
            }
        }
    }

    /** 新增一条培养方案课程。 */
    fun addCurriculumCourse(
        courseName: String,
        category: String?,
        credit: Double?,
        suggestedTerm: String?
    ) {
        val name = courseName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            curriculumRepository.addCourse(
                courseName = name,
                category = category?.trim()?.ifBlank { null },
                credit = credit,
                suggestedTerm = suggestedTerm
            )
        }
    }

    /** 保存编辑后的培养方案课程。 */
    fun updateCurriculumCourse(item: CurriculumCourse) {
        if (item.courseName.trim().isEmpty()) return
        viewModelScope.launch { curriculumRepository.updateCourse(item) }
    }

    /** 删除一条培养方案课程。 */
    fun deleteCurriculumCourse(courseId: String) {
        viewModelScope.launch { curriculumRepository.deleteCourse(courseId) }
    }

    /** 清空课程清单（UI 必须先二次确认）。 */
    fun deleteAllCurriculumCourses() {
        viewModelScope.launch { curriculumRepository.deleteAll() }
    }

    /** 本地解析粘贴的培养方案文本（不联网），返回草稿供 UI 预览后再入库。 */
    fun parsePastedCurriculum(text: String): ParsedCurriculum =
        curriculumRepository.parsePastedCurriculum(text)

    /**
     * 导入解析草稿：类别要求合并进应用设置（同类别以本次为准），课程清单按课程名去重导入。
     *
     * @return 实际写入的（类别要求条数, 课程门数）——调用方用它生成提示，
     *   避免「解析到 20 条但一条都没通过校验」时还报成功。
     */
    suspend fun importParsedCurriculum(parsed: ParsedCurriculum): Pair<Int, Int> {
        val requirements = parsed.requirements.filter { it.category.isNotBlank() }
        if (requirements.isNotEmpty()) {
            appSettingsRepository.mutateCreditRequirements { current ->
                val incomingKeys = requirements.map { it.category.trim() }.toSet()
                val kept = current.filter { it.category.trim() !in incomingKeys }
                kept + requirements.map {
                    it.copy(
                        category = it.category.trim(),
                        requiredCredits = it.requiredCredits
                            .coerceIn(0.0, CreditRequirement.MAX_REQUIRED_CREDITS),
                        requiredCourses = it.requiredCourses
                            ?.takeIf { count -> count > 0 }
                            ?.coerceAtMost(CreditRequirement.MAX_REQUIRED_COURSES)
                    )
                }
            }
        }
        val courses = parsed.courses.filter { it.courseName.isNotBlank() }
        if (courses.isNotEmpty()) {
            curriculumRepository.importCourses(
                courses.map {
                    curriculumRepository.buildCourse(
                        courseName = it.courseName,
                        category = it.category,
                        credit = it.credit,
                        source = CurriculumCourse.SOURCE_PASTE
                    )
                }
            )
        }
        return requirements.size to courses.size
    }
}
