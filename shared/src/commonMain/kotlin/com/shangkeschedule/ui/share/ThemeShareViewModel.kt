package com.shangkeschedule.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.codec.ThemeShareCodec
import com.shangkeschedule.data.codec.ThemeShareContent
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.AppThemeMode
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.StyleSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel

/** 「应用主题」这一步的结果，交给界面弹 Toast。 */
enum class ThemeApplyState {
    /** 还没点过 / 已提示过（界面消费后调 [ThemeShareViewModel.consumeApplyState] 复位）。 */
    IDLE,

    /** 正在写入。 */
    APPLYING,

    /** 应用成功。 */
    APPLIED,

    /** 串不对（前缀不符、校验位错、版本过高）。 */
    INVALID_CODE,

    /** 串是对的，但写入失败。 */
    FAILED
}

data class ThemeShareUiState(
    val isLoading: Boolean = true,
    /** 当前主题生成的分享串；生成失败时为空。 */
    val code: String = "",
    /** 当前主题预设，界面据此显示本地化名称。 */
    val preset: AppThemePreset = AppThemePreset.default,
    /** 当前深浅模式，界面据此显示本地化名称。 */
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val applyState: ThemeApplyState = ThemeApplyState.IDLE
)

/**
 * 「主题分享」页（v4.66.0，D3）。
 *
 * 生成侧：把当前「主题预设 + 深浅模式 + 整份课表样式（配色 / 圆角 / 字号 / 网格开关）」
 * 打包成一条 `SKT1:` 分享串；导入侧：解析对面的串并原样落到本机。
 *
 * 应用顺序有讲究：先写预设（决定骨架与回落色板），再用分享串里的样式字节覆盖配色与细节 ——
 * 反过来的话，预设自带的色板会把对面精心调过的配色盖掉。
 */
@KoinViewModel
class ThemeShareViewModel(
    private val styleSettingsRepository: StyleSettingsRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThemeShareUiState())
    val uiState: StateFlow<ThemeShareUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** 读取当前主题（预设 + 深浅模式 + 整份样式）并编码成分享串；任何一步失败返回 null。 */
    private suspend fun currentSnapshot(): Pair<ThemeShareContent, AppSettingsModel>? =
        withContext(Dispatchers.Default) {
            runCatching {
                val settings = appSettingsRepository.getAppSettingsOnce()
                val content = ThemeShareContent(
                    preset = settings.themePreset.value,
                    themeMode = settings.themeMode.value,
                    styleBytes = styleSettingsRepository.exportRawStyleBytes(),
                    styleSchemaVersion = StyleSettingsRepository.STYLE_SCHEMA_VERSION
                )
                content to settings
            }.getOrNull()
        }

    private suspend fun encodeSnapshot(content: ThemeShareContent): String =
        withContext(Dispatchers.Default) {
            runCatching { ThemeShareCodec.encode(content) }.getOrDefault("")
        }

    fun load() {
        viewModelScope.launch {
            val snapshot = currentSnapshot()
            if (snapshot == null) {
                _uiState.update { it.copy(isLoading = false, code = "") }
                return@launch
            }
            val code = encodeSnapshot(snapshot.first)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    code = code,
                    preset = snapshot.second.themePreset,
                    themeMode = snapshot.second.themeMode
                )
            }
        }
    }

    /**
     * 应用一条对面给的主题串。
     *
     * @param text 可能夹带聊天软件加的说明文字与换行，[ThemeShareCodec.decode] 会先做规整。
     */
    fun apply(text: String) {
        if (text.isBlank()) {
            _uiState.update { it.copy(applyState = ThemeApplyState.INVALID_CODE) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(applyState = ThemeApplyState.APPLYING) }
            // 粘贴内容可能夹带几 MB 垃圾文本：解码（剥空白 + Base64 + CBOR）不能占主线程。
            val result = withContext(Dispatchers.Default) { ThemeShareCodec.decode(text) }
            if (result !is ThemeShareCodec.DecodeResult.Success) {
                _uiState.update { it.copy(applyState = ThemeApplyState.INVALID_CODE) }
                return@launch
            }
            val content = result.content
            // 对方样式协议比本机新：拒绝，而不是按老结构硬写。
            if (content.styleSchemaVersion > StyleSettingsRepository.STYLE_SCHEMA_VERSION) {
                _uiState.update { it.copy(applyState = ThemeApplyState.INVALID_CODE) }
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    // 1) 先落预设：骨架与「配色为空时」的回落色板都以它为准。
                    appSettingsRepository.updateThemePreset(AppThemePreset.fromString(content.preset))
                    // 2) 深浅模式（仓库没有单独的 setter，按既有做法整包写回）。
                    val mode = AppThemeMode.fromString(content.themeMode) ?: AppThemeMode.FOLLOW_SYSTEM
                    val current = appSettingsRepository.getAppSettingsOnce()
                    if (current.themeMode != mode) {
                        appSettingsRepository.insertOrUpdateAppSettings(current.copy(themeMode = mode))
                    }
                    // 3) 再用分享串里的样式覆盖配色与细节（保留本机壁纸）。
                    styleSettingsRepository.restoreRawStyleBytes(content.styleBytes).getOrThrow()
                }.isSuccess
            }
            if (!ok) {
                _uiState.update { it.copy(applyState = ThemeApplyState.FAILED) }
                return@launch
            }
            // 应用成功后本机主题已经变了，顺手刷新「当前主题」与分享串，别让页面停留在旧值上。
            val refreshed = currentSnapshot()
            val refreshedCode = refreshed?.let { encodeSnapshot(it.first) }
            _uiState.update {
                it.copy(
                    applyState = ThemeApplyState.APPLIED,
                    code = refreshedCode ?: it.code,
                    preset = refreshed?.second?.themePreset ?: it.preset,
                    themeMode = refreshed?.second?.themeMode ?: it.themeMode
                )
            }
        }
    }

    /** 界面弹过 Toast 后复位，避免重组时重复提示。 */
    fun consumeApplyState() {
        if (_uiState.value.applyState == ThemeApplyState.APPLYING) return
        _uiState.update { it.copy(applyState = ThemeApplyState.IDLE) }
    }
}
