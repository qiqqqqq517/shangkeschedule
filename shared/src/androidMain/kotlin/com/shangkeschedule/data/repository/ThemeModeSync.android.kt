package com.shangkeschedule.data.repository

import androidx.appcompat.app.AppCompatDelegate
import com.shangkeschedule.data.model.AppThemeMode
import kotlinx.coroutines.runBlocking

/**
 * PF4（v3.69.0）：把持久化的深浅模式同步给 `AppCompatDelegate`，
 * 使冷启动首帧的 `windowBackground`（`themes.xml` → `@color/launch_background`，
 * 按 `values` / `values-night` 分档）与**用户在 App 内选择的**模式一致。
 *
 * 背景：资源是按**系统** DayNight 解析的，而用户可在 App 内选「强制深色 / 强制浅色」
 * （`AppThemeMode`），系统仍是浅色时首帧会闪白 —— 原 windowBackground 取
 * `?android:attr/colorBackground`，接近纯白。同步后即与 Compose 侧
 * `ShangKeScheduleTheme` 的 darkTheme 口径一致（`Theme.kt:41-45`）。
 *
 * 时机：必须在 `Application.onCreate` 内、Activity 创建前调用 —— 这个阶段设置默认
 * 夜间模式不会触发 Activity 重建（运行时切换才会，那会导致返回栈丢失）。
 *
 * 读取一律安全降级：DataStore 读失败就保持系统默认，不影响启动。
 *
 * ⚠️ 放在 androidMain 而非 androidApp：`androidApp` 模块没有 datastore 依赖，
 * 在 `MyApplication` 里直接读 `DataStore<Preferences>` 会编译失败
 * （`Cannot access class 'androidx.datastore.preferences.core.Preferences.Key'`）。
 */
public fun syncNightModeFromStoredThemeMode(repository: AppSettingsRepository) {
    val mode = runCatching { runBlocking { repository.currentThemeMode() } }.getOrNull() ?: return
    AppCompatDelegate.setDefaultNightMode(
        when (mode) {
            AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    )
}
