package com.shangkeschedule.tool

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual fun copyToClipboard(text: String): Boolean = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    true
}.getOrDefault(false)
