package com.shangkeschedule.ui.settings.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.api.AiApiConfig
import com.shangkeschedule.data.api.AiFailureKind
import com.shangkeschedule.data.api.AiImportClient
import com.shangkeschedule.data.api.AiRecognitionResult
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.tool.ExternalTextImport
import com.shangkeschedule.tool.ExternalTextSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/**
 * AI 识别导入的失败类型。
 *
 * 界面按它映射本地化文案；服务端原话放在 [AiImportFailure.detail]，
 * 因为「HTTP 401」对用户没有可操作性。
 */
enum class AiImportError {
    /** 接口地址 / 模型名 / Key 有缺项。 */
    NOT_CONFIGURED,

    /** 既没选图也没输入文字。 */
    EMPTY_INPUT,

    /** 选的文件不是支持的图片格式。 */
    UNSUPPORTED_IMAGE,

    /** 图片太大。 */
    IMAGE_TOO_LARGE,

    /** 网络不可用 / 请求超时。 */
    NETWORK,

    /** 服务端返回非 2xx。 */
    HTTP,

    /** 响应结构不可解析。 */
    PARSE,

    /** 请求成功但模型回了空内容。 */
    EMPTY_RESULT,
}

/** 一次识别失败：界面用 [error] 选文案，[httpCode] / [detail] 作补充说明。 */
data class AiImportFailure(
    val error: AiImportError,
    val httpCode: Int = 0,
    val detail: String = "",
)

/** 一次性轻提示（文案留在界面层，ViewModel 只给枚举，便于三语切换）。 */
enum class AiImportToast {
    CONFIG_SAVED,
}

data class AiImportUiState(
    val isReady: Boolean = false,
    val enabled: Boolean = false,
    val noticeAccepted: Boolean = false,
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val inputText: String = "",
    val imageBytes: ByteArray? = null,
    val imageName: String? = null,
    val busy: Boolean = false,
    val failure: AiImportFailure? = null,
    val showNoticeDialog: Boolean = false,
    val toast: AiImportToast? = null,
) {
    /** 三项都填了才算配置完成（与 [AiApiConfig.isConfigured] 同口径）。 */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()

    /** 已开启、配置齐全、不在识别中，且有内容可识别，才允许点「开始识别」。 */
    val canRecognize: Boolean
        get() = enabled && isConfigured && !busy && (inputText.isNotBlank() || imageBytes != null)
}

/**
 * AI 识别导入（v4.66.0 J1）。
 *
 * 三条口径（由用户裁决：先只搭骨架、不绑定任何厂商）：
 * 1. **默认关闭**：开关关闭时不发任何网络请求，页面只展示开关与说明。
 * 2. **明示外发**：首次开启前必须确认一次「上传什么 / 发给谁 / 不含什么」。
 * 3. **保留纯本地路径**：识别结果只填进既有「文本粘贴导入」页的文本框，核对后由用户决定是否导入。
 *
 * 识别用的配置取**界面当前值**而非已存值：用户改完地址 / 模型 / Key 不必先点保存再识别。
 */
@KoinViewModel
class AiImportViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val aiImportClient: AiImportClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiImportUiState())
    val uiState: StateFlow<AiImportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = appSettingsRepository.getAppSettingsOnce()
            // Key 不在设置模型里：它是「只在本机」的密钥，单独从凭据存储读（v4.67.16）。
            val apiKey = appSettingsRepository.getAiApiKey()
            _uiState.update {
                it.copy(
                    isReady = true,
                    enabled = settings.aiImportEnabled,
                    noticeAccepted = settings.aiImportNoticeAccepted,
                    baseUrl = settings.aiApiBaseUrl,
                    model = settings.aiApiModel,
                    apiKey = apiKey,
                )
            }
        }
    }

    /**
     * 开关。
     *
     * 想开启但还没确认过外发说明时，只弹说明框、**不真正开启** ——
     * 避免「点一下开关数据就出去了」。
     */
    fun onToggleEnabled(enabled: Boolean) {
        if (enabled && !_uiState.value.noticeAccepted) {
            _uiState.update { it.copy(showNoticeDialog = true) }
            return
        }
        persistEnabled(enabled)
    }

    fun onNoticeDismiss() {
        _uiState.update { it.copy(showNoticeDialog = false) }
    }

    /** 用户在明示外发框里确认：先记下「已确认」，再开启。 */
    fun onNoticeAccepted() {
        viewModelScope.launch {
            appSettingsRepository.updateAiImportNoticeAccepted(true)
            appSettingsRepository.updateAiImportEnabled(true)
        }
        _uiState.update { it.copy(noticeAccepted = true, enabled = true, showNoticeDialog = false) }
    }

    fun onBaseUrlChange(value: String) {
        _uiState.update { it.copy(baseUrl = value, toast = null) }
    }

    fun onModelChange(value: String) {
        _uiState.update { it.copy(model = value, toast = null) }
    }

    fun onApiKeyChange(value: String) {
        _uiState.update { it.copy(apiKey = value, toast = null) }
    }

    /** 保存接口配置；三项一起写，避免「地址换了 Key 没换」的中间态。 */
    fun onSaveConfig() {
        val state = _uiState.value
        val baseUrl = state.baseUrl.trim()
        val model = state.model.trim()
        val apiKey = state.apiKey.trim()
        viewModelScope.launch {
            appSettingsRepository.updateAiApiConfig(baseUrl = baseUrl, model = model, apiKey = apiKey)
        }
        _uiState.update {
            it.copy(
                baseUrl = baseUrl,
                model = model,
                apiKey = apiKey,
                toast = AiImportToast.CONFIG_SAVED,
            )
        }
    }

    fun onInputTextChange(value: String) {
        _uiState.update { it.copy(inputText = value, failure = null) }
    }

    fun onImagePicked(bytes: ByteArray?, fileName: String?) {
        if (bytes == null || bytes.isEmpty()) return
        _uiState.update { it.copy(imageBytes = bytes, imageName = fileName, failure = null) }
    }

    fun onClearImage() {
        _uiState.update { it.copy(imageBytes = null, imageName = null) }
    }

    /**
     * 开始识别。
     *
     * 成功时把结果交给 [ExternalTextImport]（来源标记为 AI），由界面跳到既有的
     * 「文本/文件导入」页复核解析，再走原本地导入流程；失败只在本页展示原因，不吞掉。
     */
    fun onRecognize(onRecognized: () -> Unit) {
        val state = _uiState.value
        if (state.busy) return
        _uiState.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            val result = aiImportClient.recognize(
                config = AiApiConfig(
                    baseUrl = state.baseUrl,
                    model = state.model,
                    apiKey = state.apiKey,
                ),
                userText = state.inputText.trim().ifBlank { null },
                imageBytes = state.imageBytes,
            )
            when (result) {
                is AiRecognitionResult.Success -> {
                    _uiState.update { it.copy(busy = false, failure = null) }
                    ExternalTextImport.offer(result.text, ExternalTextSource.AI)
                    onRecognized()
                }

                is AiRecognitionResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            failure = AiImportFailure(
                                error = result.kind.toUiError(),
                                httpCode = result.httpCode,
                                detail = result.detail,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun consumeToast() {
        _uiState.update { it.copy(toast = null) }
    }

    private fun persistEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.updateAiImportEnabled(enabled) }
        _uiState.update { it.copy(enabled = enabled) }
    }

    private fun AiFailureKind.toUiError(): AiImportError = when (this) {
        AiFailureKind.NOT_CONFIGURED -> AiImportError.NOT_CONFIGURED
        AiFailureKind.EMPTY_INPUT -> AiImportError.EMPTY_INPUT
        AiFailureKind.UNSUPPORTED_IMAGE -> AiImportError.UNSUPPORTED_IMAGE
        AiFailureKind.IMAGE_TOO_LARGE -> AiImportError.IMAGE_TOO_LARGE
        AiFailureKind.NETWORK -> AiImportError.NETWORK
        AiFailureKind.HTTP -> AiImportError.HTTP
        AiFailureKind.PARSE -> AiImportError.PARSE
        AiFailureKind.EMPTY_RESULT -> AiImportError.EMPTY_RESULT
    }
}
