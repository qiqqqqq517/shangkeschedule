package com.shangkeschedule.tool

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.koin.mp.KoinPlatform

actual fun copyToClipboard(text: String): Boolean = runCatching {
    val context = KoinPlatform.getKoin().get<Context>()
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("shangkeschedule", text))
    true
}.getOrDefault(false)
