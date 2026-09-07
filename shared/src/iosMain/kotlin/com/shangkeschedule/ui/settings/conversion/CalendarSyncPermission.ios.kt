package com.shangkeschedule.ui.settings.conversion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** iOS 端暂无该系统日历同步入口，直接放行执行同步（保持同名 expect 编译通过）。 */
@Composable
actual fun rememberCalendarSyncGate(onSync: () -> Unit): () -> Unit = remember { { onSync() } }