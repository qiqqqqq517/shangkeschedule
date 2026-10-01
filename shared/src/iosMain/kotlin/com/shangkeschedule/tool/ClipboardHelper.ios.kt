package com.shangkeschedule.tool

import platform.UIKit.UIPasteboard

actual fun copyToClipboard(text: String): Boolean = runCatching {
    UIPasteboard.generalPasteboard.string = text
    true
}.getOrDefault(false)
