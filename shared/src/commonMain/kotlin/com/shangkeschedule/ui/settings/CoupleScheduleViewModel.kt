package com.shangkeschedule.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.DualColor
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.CourseTableRepository
import com.shangkeschedule.data.repository.StyleSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/**
 * 情侣课表管理页 UI 状态。
 *
 * 视角说明：本页既可以在「本人课表」视角下管理配对情侣课表，
 * 也可以在「当前显示的就是情侣课表」时反向操作（返回本人课表 / 编辑其作息）。
 */
data class CoupleManageUiState(
    val isReady: Boolean = false,
    /** 当前显示的课表。 */
    val currentTable: CourseTable? = null,
    /** 配对的情侣课表（当前表为本人表时 = 其配对情侣表；当前表为情侣表时 = 自己）。 */
    val coupleTable: CourseTable? = null,
    /** 本人课表（当前表为情侣表时有效）。 */
    val selfTable: CourseTable? = null,
    /** 情侣课表课程数。 */
    val coupleCourseCount: Int = 0,
    /** 双人叠加显示开关（当前为情侣课表时自动失效）。 */
    val coupleScheduleEnabled: Boolean = false,
    val selfCourseColorIndex: Int = 0,
    val crushCourseColorIndex: Int = 1,
    val courseColorMaps: List<DualColor> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class CoupleScheduleViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseTableRepository: CourseTableRepository,
    private val styleSettingsRepository: StyleSettingsRepository
) : ViewModel() {

    /** 当前显示的课表实体。 */
    private val currentTableFlow: Flow<CourseTable?> = appSettingsRepository.getAppSettings()
        .flatMapLatest { settings ->
            if (settings.currentCourseTableId.isEmpty()) {
                flowOf(null)
            } else {
                courseTableRepository.getAllCourseTables().map { tables ->
                    tables.firstOrNull { it.id == settings.currentCourseTableId }
                }
            }
        }

    /** (当前表, 配对情侣表, 本人表) 上下文。 */
    private val coupleContextFlow: Flow<Triple<CourseTable?, CourseTable?, CourseTable?>> =
        currentTableFlow.flatMapLatest { current ->
            when {
                current == null -> flowOf(Triple(null, null, null))
                current.isCouple -> courseTableRepository.getAllCourseTables().map { tables ->
                    val self = tables.firstOrNull { it.id == current.pairedCourseTableId }
                    Triple(current, current, self)
                }
                else -> courseTableRepository.getCoupleTableFor(current.id).map { couple ->
                    Triple(current, couple, current)
                }
            }
        }

    private val coupleCourseCountFlow: Flow<Int> = coupleContextFlow.flatMapLatest { (_, couple, _) ->
        val coupleId = couple?.id
        if (coupleId == null) flowOf(0)
        else courseTableRepository.getCoursesWithWeeksByTableId(coupleId).map { it.size }
    }

    val uiState: StateFlow<CoupleManageUiState> = combine(
        appSettingsRepository.getAppSettings(),
        coupleContextFlow,
        coupleCourseCountFlow,
        styleSettingsRepository.styleFlow
    ) { settings, context, coupleCount, style ->
        val (current, couple, self) = context
        CoupleManageUiState(
            isReady = true,
            currentTable = current,
            coupleTable = couple,
            selfTable = self,
            coupleCourseCount = coupleCount,
            coupleScheduleEnabled = settings.coupleScheduleEnabled,
            selfCourseColorIndex = settings.selfCourseColorIndex,
            crushCourseColorIndex = settings.crushCourseColorIndex,
            courseColorMaps = style.courseColorMaps
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoupleManageUiState())

    /**
     * 双人叠加开关。
     * 当前显示的已是情侣课表（单独显示）时开关自动失效，不响应切换。
     */
    fun onCoupleScheduleEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            val settings = appSettingsRepository.getAppSettingsOnce()
            val current = courseTableRepository.getCourseTableById(settings.currentCourseTableId)
            if (current?.isCouple == true) return@launch
            appSettingsRepository.insertOrUpdateAppSettings(
                settings.copy(coupleScheduleEnabled = enabled)
            )
        }
    }

    fun onSelfCourseColorIndexChanged(index: Int) {
        viewModelScope.launch { updateSettings { it.copy(selfCourseColorIndex = index) } }
    }

    fun onCrushCourseColorIndexChanged(index: Int) {
        viewModelScope.launch { updateSettings { it.copy(crushCourseColorIndex = index) } }
    }

    /**
     * 为当前学期（本人课表）创建配对情侣课表；已有则幂等返回。
     * @return 是否实际执行了创建（解析不到本人课表时为 false，供 UI 决定提示）。
     */
    suspend fun createCoupleTable(): Boolean {
        val selfId = resolveSelfTableId() ?: return false
        courseTableRepository.createCoupleTable(selfId)
        return true
    }

    /** 重命名情侣课表；@return 是否找到并写入了情侣课表。 */
    suspend fun renameCoupleTable(name: String): Boolean {
        val couple = resolveCoupleTableOnce() ?: return false
        courseTableRepository.updateCourseTable(couple.copy(name = name))
        return true
    }

    /**
     * 删除情侣课表（整表删除，含课程与作息；不影响本人课表）。
     * @return 是否实际删除（剩余课表不足被仓储拒绝时为 false）。
     */
    suspend fun deleteCoupleTable(): Boolean {
        val couple = resolveCoupleTableOnce() ?: return false
        return courseTableRepository.deleteCourseTableAndResolveCurrent(couple)
    }

    /** 切到情侣课表单独显示；@return 是否切换成功。 */
    suspend fun switchToCouple(): Boolean {
        val couple = resolveCoupleTableOnce() ?: return false
        updateSettings { it.copy(currentCourseTableId = couple.id) }
        return true
    }

    /** 从情侣课表返回本人课表；@return 是否切换成功。 */
    suspend fun switchToSelf(): Boolean {
        val settings = appSettingsRepository.getAppSettingsOnce()
        val current = courseTableRepository.getCourseTableById(settings.currentCourseTableId)
        val selfId = current?.takeIf { it.isCouple }?.pairedCourseTableId ?: return false
        appSettingsRepository.insertOrUpdateAppSettings(settings.copy(currentCourseTableId = selfId))
        return true
    }

    private suspend fun resolveSelfTableId(): String? {
        val settings = appSettingsRepository.getAppSettingsOnce()
        val current = courseTableRepository.getCourseTableById(settings.currentCourseTableId)
        return when {
            current == null -> null
            current.isCouple -> current.pairedCourseTableId
            else -> current.id
        }
    }

    private suspend fun resolveCoupleTableOnce(): CourseTable? {
        val settings = appSettingsRepository.getAppSettingsOnce()
        val current = courseTableRepository.getCourseTableById(settings.currentCourseTableId)
        return if (current?.isCouple == true) {
            current
        } else {
            courseTableRepository.getCoupleTableForOnce(settings.currentCourseTableId)
        }
    }

    private suspend fun updateSettings(transform: (AppSettingsModel) -> AppSettingsModel) {
        val settings = appSettingsRepository.getAppSettingsOnce()
        appSettingsRepository.insertOrUpdateAppSettings(transform(settings))
    }
}
