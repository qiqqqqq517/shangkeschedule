package com.shangkeschedule.ui.settings.conversion

import androidx.compose.runtime.Composable

/**
 * 系统日历同步权限门（跨平台 expect 声明）。
 *
 * 返回一个「请求同步」的函数：调用方（Android）在真正同步前先检查/申请
 * 日历读写权限；非 Android 平台直接放行。授权成功后调用 [onSync]。
 *
 * @param onSync 权限已就绪时真正执行同步的回调（通常指向 ViewModel 的同步方法）
 * @return 供 UI 绑定的「请求同步」回调
 */
@Composable
expect fun rememberCalendarSyncGate(onSync: () -> Unit): () -> Unit