package com.shangkeschedule.ui.schoolselection.web

import kotlinx.coroutines.flow.Flow

// --- 常量 ---
const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

// --- UI 数据与事件模型 ---
data class AlertDialogData(
    val title: String,
    val content: String,
    val confirmText: String
)

data class PromptDialogData(
    val title: String,
    val tip: String,
    val defaultText: String,
    val validatorJsFunction: String?
)

data class SingleSelectionDialogData(
    val title: String,
    val items: List<String>,
    val defaultSelectedIndex: Int = -1
)

sealed interface WebUiEvent {
    data class ShowAlert(
        val data: AlertDialogData,
        val callback: (confirmed: Boolean) -> Unit,
        override val eventId: Long = 0L
    ) : WebUiEvent

    data class ShowPrompt(
        val data: PromptDialogData,
        val onRequestValidation: (input: String, onSuccess: () -> Unit) -> Unit,
        val errorFeedbackFlow: Flow<String?>,
        val onCancel: () -> Unit,
        override val eventId: Long = 0L
    ) : WebUiEvent

    data class ShowSingleSelection(
        val data: SingleSelectionDialogData,
        val callback: (selectedIndex: Int?) -> Unit,
        override val eventId: Long = 0L
    ) : WebUiEvent

    /**
     * 每次弹窗事件携带的稳定序号，供 UI 层做 FIFO 队列与 key 复用，
     * 避免连续两个弹窗之间 rememberSaveable 状态串台。
     */
    val eventId: Long
}

// --- 导入运行状态 ---

/**
 * 一次「执行导入」的运行状态，用于驱动导入页的过程反馈 UI。
 *
 * 状态流转：Idle → Running →（Succeeded | Failed）→ Idle。
 * Running 期间禁止重复触发导入，避免并发注入脚本导致重复落库。
 */
sealed interface ImportRunState {
    /** 未在执行导入。 */
    data object Idle : ImportRunState

    /** 已选定目标课表并注入适配脚本，等待脚本回传数据。 */
    data object Running : ImportRunState

    /** 导入成功，[courseCount] 为本次落库的课程数。 */
    data class Succeeded(val courseCount: Int) : ImportRunState

    /** 导入失败，[reason] 为可直接展示给用户的失败原因。 */
    data class Failed(val reason: String) : ImportRunState
}