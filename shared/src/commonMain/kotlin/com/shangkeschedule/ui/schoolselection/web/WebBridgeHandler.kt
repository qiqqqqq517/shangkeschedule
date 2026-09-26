package com.shangkeschedule.ui.schoolselection.web

import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.wb_default_confirm
import shangkeschedule.shared.generated.resources.wb_config_import_failed_fmt
import shangkeschedule.shared.generated.resources.wb_config_import_no_table
import shangkeschedule.shared.generated.resources.wb_config_import_success
import shangkeschedule.shared.generated.resources.wb_import_failed_fmt
import shangkeschedule.shared.generated.resources.wb_import_no_table
import shangkeschedule.shared.generated.resources.wb_import_success_fmt
import shangkeschedule.shared.generated.resources.wb_import_timeout
import shangkeschedule.shared.generated.resources.wb_list_invalid
import shangkeschedule.shared.generated.resources.wb_list_json_invalid_fmt
import shangkeschedule.shared.generated.resources.wb_preset_import_failed_fmt
import shangkeschedule.shared.generated.resources.wb_preset_import_success
import shangkeschedule.shared.generated.resources.wb_show_alert_queue_full
import shangkeschedule.shared.generated.resources.wb_show_list_queue_full
import shangkeschedule.shared.generated.resources.wb_show_prompt_queue_full
import shangkeschedule.shared.generated.resources.wb_table_cancelled
import shangkeschedule.shared.generated.resources.wb_table_cancelled_or_unset

import org.jetbrains.compose.resources.getString

import com.shangkeschedule.data.model.CourseImportExport
import com.shangkeschedule.data.repository.CourseConversionRepository
import com.shangkeschedule.tool.AppLog
import com.shangkeschedule.ui.components.ToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * JS Bridge 消息处理器，负责路由通信请求并与 Native 业务及 UI 进行交互。
 */
class WebBridgeHandler(
    private val coroutineScope: CoroutineScope,
    private val uiEventChannel: SendChannel<WebUiEvent>,
    private val courseConversionRepository: CourseConversionRepository,
    private val onTaskCompleted: () -> Unit,
    private val evaluateJs: (script: String, callback: ((String?) -> Unit)?) -> Unit,
    private val onImportStateChanged: (ImportRunState) -> Unit = {}
) {
    private val json = CourseImportExport.json
    private var importTableId: String? = null

    /** 当前导入运行状态，用于避免「成功结果被收尾动作覆盖成 Idle」。 */
    private var importState: ImportRunState = ImportRunState.Idle

    /** 弹窗事件序号，单调递增，保证 UI 层可以稳定区分前后两个弹窗。 */
    private var nextEventId = 0L

    /**
     * 导入看门狗。仅在「已注入脚本但一条桥接消息都没收到」时触发，
     * 收到任意一条消息即永久取消（脚本已证明存活，后续耗时不再视为卡死）。
     */
    private var importWatchdogJob: Job? = null

    private companion object {
        const val TAG = "WebBridgeHandler"

        /**
         * 无响应超时。JS_IMPORT_AUTOSTART 自身在 1500ms 探测不到入口时就会发一条
         * toast（即一条桥接消息），因此零消息超时只可能是脚本整体没跑起来。
         */
        const val IMPORT_IDLE_TIMEOUT_MS = 30_000L
    }

    /**
     * 设置当前导入的目标课表 ID。
     *
     * 传入非 null 表示开始一次新导入：进入 Running 态并启动看门狗。
     * 传入 null 表示导入上下文结束（任务完成/页面销毁）：回到 Idle 态并取消看门狗。
     */
    fun setImportTableId(tableId: String?) {
        this.importTableId = tableId
        if (tableId == null) {
            cancelImportWatchdog()
            updateImportState(ImportRunState.Idle)
        } else {
            updateImportState(ImportRunState.Running)
            armImportWatchdog()
        }
    }

    private fun updateImportState(state: ImportRunState) {
        importState = state
        onImportStateChanged(state)
    }

    private fun armImportWatchdog() {
        cancelImportWatchdog()
        importWatchdogJob = coroutineScope.launch {
            delay(IMPORT_IDLE_TIMEOUT_MS)
            // 走到这里说明整段时间内没有任何桥接消息 ⇒ 适配脚本没跑起来。
            // 此时刻意保留 importTableId：脚本可能只是启动很慢，晚到的
            // saveImportedCourses 仍应能正常落库，不能被判成「未选择课表」。
            val timeoutMessage = getString(Res.string.wb_import_timeout)
            updateImportState(ImportRunState.Failed(timeoutMessage))
            ToastManager.show(timeoutMessage)
        }
    }

    private fun cancelImportWatchdog() {
        importWatchdogJob?.cancel()
        importWatchdogJob = null
    }

    /**
     * 接收并解析来自 JS 端的消息 JSON 字符串。
     */
    fun onMessageReceived(jsonString: String) {
        try {
            val message = bridgeJson.decodeFromString<JsBridgeMessage>(jsonString)
            val callbackId = message.callbackId

            // 只要收到任何一条消息，就说明脚本已成功启动，不再需要看门狗。
            cancelImportWatchdog()

            when (message.action) {
                "showToast" -> parsePayload<ShowToastPayload>(message.payload)?.let {
                    showToast(it.message)
                }

                "showAlert" -> parsePayload<ShowAlertPayload>(message.payload)?.let {
                    showAlert(it.titleText, it.contentText, it.confirmText, callbackId)
                }

                "showPrompt" -> parsePayload<ShowPromptPayload>(message.payload)?.let {
                    showPrompt(it.titleText, it.tipText, it.defaultText, it.validatorJsFunction, callbackId)
                }

                "showSingleSelection" -> parsePayload<ShowSingleSelectionPayload>(message.payload)?.let {
                    showSingleSelection(it.titleText, it.itemsJsonString, it.defaultSelectedIndex, callbackId)
                }

                "saveImportedCourses" -> parsePayload<SaveCoursesPayload>(message.payload)?.let {
                    saveImportedCourses(it.coursesJsonString, callbackId)
                }

                "saveCourseConfig" -> parsePayload<SaveConfigPayload>(message.payload)?.let {
                    saveCourseConfig(it.configJsonString, callbackId)
                }

                "savePresetTimeSlots" -> parsePayload<SaveTimeSlotsPayload>(message.payload)?.let {
                    savePresetTimeSlots(it.timeSlotsJsonString, callbackId)
                }

                "notifyTaskCompletion" -> notifyTaskCompletion()
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "桥接消息处理失败", e)
        }
    }

    /**
     * 显示短提示 Toast。
     */
    fun showToast(message: String) {
        ToastManager.show(message)
    }

    /**
     * 显示 Alert 确认弹窗。
     */
    fun showAlert(
        titleText: String,
        contentText: String,
        confirmText: String? = null,
        callbackId: String? = null
    ) {
        coroutineScope.launch(Dispatchers.Main) {
            val resolvedConfirmText = confirmText ?: getString(Res.string.wb_default_confirm)
            val data = AlertDialogData(titleText, contentText, resolvedConfirmText)

            val promiseCallback: (Boolean) -> Unit = { confirmed ->
                if (callbackId != null) {
                    resolveJsPromise(callbackId, if (confirmed) "true" else "false")
                }
            }

            val sendResult = uiEventChannel.trySend(
                WebUiEvent.ShowAlert(data, promiseCallback, nextEventId++)
            )
            if (sendResult.isFailure && callbackId != null) {
                rejectJsPromise(callbackId, getString(Res.string.wb_show_alert_queue_full))
            }
        }
    }

    /**
     * 显示 Prompt 输入弹窗并支持 JS 校验。
     */
    fun showPrompt(
        titleText: String,
        tipText: String,
        defaultText: String = "",
        validatorJsFunction: String? = null,
        callbackId: String? = null
    ) {
        val data = PromptDialogData(
            title = titleText,
            tip = tipText,
            defaultText = defaultText,
            validatorJsFunction = validatorJsFunction
        )

        val errorFlow = MutableSharedFlow<String?>(extraBufferCapacity = 1)

        val onCancel: () -> Unit = {
            if (callbackId != null) {
                resolveJsPromise(callbackId, "null")
            }
        }

        val onRequestValidation: (String, () -> Unit) -> Unit = { input, onSuccess ->
            val encodedInput = bridgeJson.encodeToString(input)

            if (data.validatorJsFunction.isNullOrEmpty()) {
                if (callbackId != null) {
                    resolveJsPromise(callbackId, encodedInput)
                }
                onSuccess()
            } else {
                val jsScript = "${data.validatorJsFunction}($encodedInput)"
                evaluateJs(jsScript) { result ->
                    val validationResult = result?.trim('\"')
                    if (validationResult.isNullOrEmpty() || validationResult.equals("false", ignoreCase = true)) {
                        if (callbackId != null) {
                            resolveJsPromise(callbackId, encodedInput)
                        }
                        onSuccess()
                    } else {
                        coroutineScope.launch {
                            errorFlow.emit(validationResult)
                        }
                    }
                }
            }
        }

        val sendResult = uiEventChannel.trySend(
            WebUiEvent.ShowPrompt(data, onRequestValidation, errorFlow.asSharedFlow(), onCancel, nextEventId++)
        )
        if (sendResult.isFailure && callbackId != null) {
            coroutineScope.launch {
                rejectJsPromise(callbackId, getString(Res.string.wb_show_prompt_queue_full))
            }
        }
    }

    /**
     * 显示单选列表弹窗。
     */
    fun showSingleSelection(
        titleText: String,
        itemsJsonString: String,
        defaultSelectedIndex: Int = -1,
        callbackId: String? = null
    ) {
        try {
            val items = json.decodeFromString<List<String>>(itemsJsonString)
            val data = SingleSelectionDialogData(titleText, items, defaultSelectedIndex)

            val promiseCallback: (Int?) -> Unit = { selectedIndex ->
                if (callbackId != null) {
                    resolveJsPromise(callbackId, selectedIndex?.toString() ?: "null")
                }
            }

            val sendResult = uiEventChannel.trySend(
                WebUiEvent.ShowSingleSelection(data, promiseCallback, nextEventId++)
            )
            if (sendResult.isFailure && callbackId != null) {
                coroutineScope.launch {
                    rejectJsPromise(callbackId, getString(Res.string.wb_show_list_queue_full))
                }
            }
        } catch (e: Exception) {
            coroutineScope.launch {
                ToastManager.show(getString(Res.string.wb_list_invalid))
                if (callbackId != null) {
                    rejectJsPromise(callbackId, getString(Res.string.wb_list_json_invalid_fmt, e.message ?: ""))
                }
            }
        }
    }

    /**
     * 解析并保存导入的课程数据。
     */
    fun saveImportedCourses(coursesJsonString: String, callbackId: String? = null) {
        coroutineScope.launch(Dispatchers.Default) {
            val tableId = importTableId
            if (tableId == null) {
                coroutineScope.launch(Dispatchers.Main) {
                    val noTableMessage = getString(Res.string.wb_import_no_table)
                    ToastManager.show(noTableMessage)
                    updateImportState(ImportRunState.Failed(noTableMessage))
                    if (callbackId != null) rejectJsPromise(callbackId, getString(Res.string.wb_table_cancelled))
                }
                return@launch
            }

            val result = runCatching {
                val importedCoursesList = json.decodeFromString<List<CourseImportExport.ImportCourseJsonModel>>(coursesJsonString)
                courseConversionRepository.importCoursesFromList(tableId, importedCoursesList)
                importedCoursesList.size
            }

            coroutineScope.launch(Dispatchers.Main) {
                result.onSuccess { importedCount ->
                    ToastManager.show(getString(Res.string.wb_import_success_fmt, importedCount))
                    updateImportState(ImportRunState.Succeeded(importedCount))
                    if (callbackId != null) resolveJsPromise(callbackId, "true")
                }.onFailure { e ->
                    val failureMessage = getString(Res.string.wb_import_failed_fmt, e.message ?: "")
                    ToastManager.show(failureMessage)
                    updateImportState(ImportRunState.Failed(failureMessage))
                    if (callbackId != null) rejectJsPromise(callbackId, failureMessage)
                }
            }
        }
    }

    /**
     * 解析并保存课表配置信息。
     */
    fun saveCourseConfig(configJsonString: String, callbackId: String? = null) {
        coroutineScope.launch(Dispatchers.Default) {
            val tableId = importTableId
            if (tableId == null) {
                coroutineScope.launch(Dispatchers.Main) {
                    ToastManager.show(getString(Res.string.wb_config_import_no_table))
                    if (callbackId != null) rejectJsPromise(callbackId, getString(Res.string.wb_table_cancelled_or_unset))
                }
                return@launch
            }

            val result = runCatching {
                val importedConfig = json.decodeFromString<CourseImportExport.CourseConfigJsonModel>(configJsonString)
                courseConversionRepository.importCourseConfig(tableId, importedConfig)
            }

            coroutineScope.launch(Dispatchers.Main) {
                result.onSuccess {
                    ToastManager.show(getString(Res.string.wb_config_import_success))
                    if (callbackId != null) resolveJsPromise(callbackId, "true")
                }.onFailure { e ->
                    ToastManager.show(getString(Res.string.wb_config_import_failed_fmt, e.message ?: ""))
                    if (callbackId != null) rejectJsPromise(callbackId, getString(Res.string.wb_config_import_failed_fmt, e.message ?: ""))
                }
            }
        }
    }

    /**
     * 解析并保存预设时间段信息。
     */
    fun savePresetTimeSlots(timeSlotsJsonString: String, callbackId: String? = null) {
        coroutineScope.launch(Dispatchers.Default) {
            val tableId = importTableId
            if (tableId == null) {
                coroutineScope.launch(Dispatchers.Main) {
                    ToastManager.show(getString(Res.string.wb_import_no_table))
                    if (callbackId != null) rejectJsPromise(callbackId, getString(Res.string.wb_table_cancelled))
                }
                return@launch
            }

            val result = runCatching {
                val importedTimeSlotsJson = json.decodeFromString<List<CourseImportExport.TimeSlotJsonModel>>(timeSlotsJsonString)
                courseConversionRepository.importTimeSlots(tableId, importedTimeSlotsJson)
            }

            coroutineScope.launch(Dispatchers.Main) {
                result.onSuccess {
                    ToastManager.show(getString(Res.string.wb_preset_import_success))
                    if (callbackId != null) resolveJsPromise(callbackId, "true")
                }.onFailure { e ->
                    ToastManager.show(getString(Res.string.wb_preset_import_failed_fmt, e.message ?: ""))
                    if (callbackId != null) rejectJsPromise(callbackId, getString(Res.string.wb_preset_import_failed_fmt, e.message ?: ""))
                }
            }
        }
    }

    /**
     * 通知 Web 任务执行完成，清理上下文状态。
     */
    fun notifyTaskCompletion() {
        importTableId = null
        cancelImportWatchdog()
        // 脚本没回传成功态就直接收尾时，Running 会把按钮永久卡死，这里复位；
        // 已拿到成功/失败结果时保留该状态，供用户在离开页面前仍有据可查。
        if (importState == ImportRunState.Running) {
            updateImportState(ImportRunState.Idle)
        }
        onTaskCompleted()
    }

    private inline fun <reified T> parsePayload(payloadJson: String?): T? {
        if (payloadJson == null) return null
        return try {
            bridgeJson.decodeFromString<T>(payloadJson)
        } catch (e: Exception) {
            AppLog.e(TAG, "桥接 payload 解析失败", e)
            null
        }
    }

    private fun resolveJsPromise(callbackId: String, resultRawJs: String) {
        val script = buildJsCallbackScript(callbackId, isSuccess = true, resultRawJs = resultRawJs)
        coroutineScope.launch(Dispatchers.Main) {
            evaluateJs(script, null)
        }
    }

    private fun rejectJsPromise(callbackId: String, errorText: String) {
        val safeErrorJson = bridgeJson.encodeToString(errorText)
        val script = buildJsCallbackScript(callbackId, isSuccess = false, resultRawJs = safeErrorJson)
        coroutineScope.launch(Dispatchers.Main) {
            evaluateJs(script, null)
        }
    }
}
