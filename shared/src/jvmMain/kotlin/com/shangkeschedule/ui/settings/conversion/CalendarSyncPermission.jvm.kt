package com.shangkeschedule.ui.settings.conversion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** 桌面端无需日历权限，直接放行执行同步。 */
@Composable
actual fun rememberCalendarSyncGate(onSync: () -> Unit): () -> Unit = remember { { onSync() } }