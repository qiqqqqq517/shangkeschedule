package com.shangkeschedule.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.model.CreditRequirement
import com.shangkeschedule.data.model.StudyProgress
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.GradeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/**
 * 学业情况页。
 *
 * 学业完成度是「成绩数据」与「用户设定的各类别学分要求」两路数据的交叉计算，
 * 因此这里把两个仓库的 Flow 直接 combine 起来，不在中间再落一份缓存：
 * 成绩页新增/修改成绩、设置页修改要求，本页都会自动跟着变。
 */
@KoinViewModel
class StudyProgressViewModel(
    private val gradeRepository: GradeRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    val progress: StateFlow<StudyProgress> = combine(
        gradeRepository.getAllGrades(),
        appSettingsRepository.getAppSettings()
    ) { grades, settings ->
        gradeRepository.computeStudyProgress(grades, settings.creditRequirements)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StudyProgress()
    )

    /**
     * 新增或覆盖一条类别学分要求（按类别名匹配，即与成绩的 `Grade.category` 对齐）。
     */
    fun upsertRequirement(category: String, requiredCredits: Double, displayName: String) {
        val key = category.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            appSettingsRepository.mutateCreditRequirements { current ->
                current.filterNot { it.category.trim() == key } +
                    CreditRequirement(
                        category = key,
                        requiredCredits = requiredCredits.coerceIn(0.0, CreditRequirement.MAX_REQUIRED_CREDITS),
                        displayName = displayName.trim()
                    )
            }
        }
    }

    /**
     * 删除一条类别要求（只删要求，不动成绩数据）。
     */
    fun removeRequirement(category: String) {
        val key = category.trim()
        viewModelScope.launch {
            appSettingsRepository.mutateCreditRequirements { current ->
                current.filterNot { it.category.trim() == key }
            }
        }
    }
}
