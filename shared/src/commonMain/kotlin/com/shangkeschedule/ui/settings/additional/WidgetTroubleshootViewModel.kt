package com.shangkeschedule.ui.settings.additional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.repository.WidgetRepository
import com.shangkeschedule.data.sync.WidgetDataSynchronizer
import com.shangkeschedule.tool.OemGuide
import com.shangkeschedule.tool.OemGuideResolver
import com.shangkeschedule.tool.WidgetTroubleshootBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Clock

/**
 * 排障操作的结果提示（v4.66.0，K7）。
 *
 * 刻意用枚举而不是本地化字符串：ViewModel 不持有 `stringResource`，文案交给界面按语言取。
 */
enum class WidgetTroubleshootToast {
    /** 快照已重建，并且已经把重绘请求发给桌面。 */
    REBUILT,

    /** 只请求了重绘。 */
    REFRESHED,

    /** 一个小组件都没放置，无从刷新 —— 这不是失败，是要用户先去桌面添加。 */
    NOTHING_PLACED,

    /** 平台侧调用抛错或没发出任何刷新请求。 */
    FAILED,

    /** 当前平台没有小组件机制（桌面版 / iOS）。 */
    UNSUPPORTED,
}

/**
 * 小组件排障页状态（v4.66.0，K7）。
 *
 * @property snapshotCourseCount 小组件快照里「今天起 7 天」的课程条数 —— 判断快照是否为空的依据。
 * @property currentWeek 快照推算出的当前周；学期开始日期没设置时为 null。
 */
data class WidgetTroubleshootUiState(
    val isReady: Boolean = false,
    val supported: Boolean = true,
    val providerCount: Int = 0,
    val placedCount: Int = 0,
    val snapshotCourseCount: Int = 0,
    val currentWeek: Int? = null,
    val busy: Boolean = false,
    val toast: WidgetTroubleshootToast? = null,
    /**
     * 当前设备的厂商后台限制引导（XL-013）。
     *
     * null 表示「未识别厂商，无可给出的步骤」——
     * 此时界面**不显示**该区块，而不是显示一个猜测的路径。
     */
    val oemGuide: OemGuide? = null,
)

/**
 * 小组件排障的 ViewModel（v4.66.0，K7）。
 *
 * 三件事都靠既有能力，不新增数据通路：
 * - 诊断读的是小组件自己的快照表（`WidgetRepository`），因此看到的就是桌面真正拿到的数据；
 * - 「重建」先跑 `WidgetDataSynchronizer.syncNow()`（与每日轮转、后台任务用的是同一个入口），
 *   再发一次重绘广播，避免「只重绘了旧快照」的假修复；
 * - 平台侧动作全部走 `WidgetTroubleshootBridge`，桌面版 / iOS 会如实报「不支持」。
 */
@KoinViewModel
class WidgetTroubleshootViewModel(
    private val widgetRepository: WidgetRepository,
    private val widgetDataSynchronizer: WidgetDataSynchronizer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WidgetTroubleshootUiState())
    val uiState: StateFlow<WidgetTroubleshootUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    /** 读一次小组件放置情况与快照概况。 */
    fun reload() {
        viewModelScope.launch {
            val placement = withContext(Dispatchers.Default) { WidgetTroubleshootBridge.placement() }
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val courses = runCatching {
                widgetRepository
                    .getWidgetCoursesByDateRange(
                        startDate = today.toString(),
                        endDate = today.plus(WIDGET_SNAPSHOT_DAYS - 1, DateTimeUnit.DAY).toString(),
                    )
                    .first()
            }.getOrDefault(emptyList())
            val week = runCatching { widgetRepository.getCurrentWeekFlow().first() }.getOrNull()
            _uiState.update {
                it.copy(
                    isReady = true,
                    supported = placement.providerCount > 0,
                    providerCount = placement.providerCount,
                    placedCount = placement.placedCount,
                    snapshotCourseCount = courses.size,
                    currentWeek = week,
                    oemGuide = resolveOemGuide(),
                )
            }
        }
    }

    /** 重建小组件数据：重算快照 + 请求桌面重绘（等价于把小组件删掉重挂）。 */
    fun rebuild() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val synced = runCatching { widgetDataSynchronizer.syncNow() }.isSuccess
            val toast = when {
                !synced -> WidgetTroubleshootToast.FAILED
                _uiState.value.placedCount == 0 -> WidgetTroubleshootToast.NOTHING_PLACED
                WidgetTroubleshootBridge.requestRefresh() -> WidgetTroubleshootToast.REBUILT
                else -> WidgetTroubleshootToast.FAILED
            }
            _uiState.update { it.copy(busy = false, toast = toast) }
            reload()
        }
    }

    /** 只请求重绘，不动快照。 */
    fun refreshOnly() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val toast = when {
                !_uiState.value.supported -> WidgetTroubleshootToast.UNSUPPORTED
                _uiState.value.placedCount == 0 -> WidgetTroubleshootToast.NOTHING_PLACED
                WidgetTroubleshootBridge.requestRefresh() -> WidgetTroubleshootToast.REFRESHED
                else -> WidgetTroubleshootToast.FAILED
            }
            _uiState.update { it.copy(busy = false, toast = toast) }
        }
    }

    /** 跳到系统设置，让用户自己放开自启动 / 后台限制。 */
    fun openSystemSettings() {
        viewModelScope.launch {
            if (!WidgetTroubleshootBridge.openSystemSettings()) {
                _uiState.update { it.copy(toast = WidgetTroubleshootToast.UNSUPPORTED) }
            }
        }
    }

    fun consumeToast() {
        _uiState.update { it.copy(toast = null) }
    }

    /**
     * 解析厂商引导：未识别厂商时返回 null，界面不展示该区块。
     */
    private fun resolveOemGuide(): OemGuide? =
        runCatching { OemGuideResolver.resolve(WidgetTroubleshootBridge.manufacturer()) }
            .getOrNull()
            ?.takeIf { it.steps.isNotEmpty() }

    private companion object {
        /** 与「周课程」小组件一致：今天起 7 天。 */
        const val WIDGET_SNAPSHOT_DAYS = 7
    }
}
