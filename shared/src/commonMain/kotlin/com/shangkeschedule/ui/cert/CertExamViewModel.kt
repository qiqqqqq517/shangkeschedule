package com.shangkeschedule.ui.cert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.model.CertCredential
import com.shangkeschedule.data.repository.AppSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/**
 * 考证查分页状态（v4.66.0）。
 *
 * 页面只需要一份小数据：每个模块已保存的凭据。四个模块一次读出（`Map`），
 * 而不是每行各建一个 flow —— 模块数量固定且极少，一次读取更省订阅开销。
 */
@KoinViewModel
class CertExamViewModel(
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    /** moduleId -> 该模块已保存的凭据（未保存过则不在 Map 中）。 */
    val credentials: StateFlow<Map<String, CertCredential>> =
        appSettingsRepository
            .getCertCredentials(CertExamRegistry.modules.map { it.id })
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )

    /** 保存某个模块的凭据（只落本机 DataStore）。 */
    fun saveCredential(moduleId: String, name: String, ticket: String) {
        viewModelScope.launch {
            appSettingsRepository.updateCertCredential(moduleId, name, ticket)
        }
    }
}
