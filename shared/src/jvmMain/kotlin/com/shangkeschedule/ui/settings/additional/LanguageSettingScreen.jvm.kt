package com.shangkeschedule.ui.settings.additional

import com.shangkeschedule.data.model.JvmBasePreferences
import java.util.Locale

/**
 * JVM / Desktop 平台的语言配置管理器 actual 实现
 * 继承 [JvmBasePreferences]，数据统一落盘保存在 AppData/files/locale_settings.properties
 */
actual object PlatformLocaleManager : JvmBasePreferences("locale_settings.properties") {

    private const val PREF_KEY_LANGUAGE = "app_language_tag"

    actual fun setLanguageTag(tag: String) {
        if (tag.isEmpty()) {
            putString(PREF_KEY_LANGUAGE, null)
            // 「跟随系统」= 清掉覆盖，回到进程启动时由系统决定的默认 Locale。
            // 原写法 `Locale.setDefault(Locale.getDefault())` 是自赋值 no-op（P2 桌面语言契约 C2）。
            Locale.setDefault(systemDefaultLocale)
        } else {
            putString(PREF_KEY_LANGUAGE, tag)
            Locale.setDefault(Locale.forLanguageTag(tag))
        }
    }

    actual fun getCurrentLanguageTag(): String {
        return getString(PREF_KEY_LANGUAGE, "")
    }

    /**
     * 进程启动时的系统默认 Locale 快照，用作「跟随系统」的还原基准。
     *
     * 注意：它必须在**任何** [setLanguageTag] 覆盖之前初始化，故用 by lazy 在首次读取时
     * 定格；object 初始化早于桌面 UI 装配，normal 场景下即为系统默认值。
     */
    private val systemDefaultLocale: Locale by lazy { Locale.getDefault() }
}