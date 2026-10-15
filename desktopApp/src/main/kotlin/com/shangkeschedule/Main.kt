package com.shangkeschedule

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "上课 · ShangKeSchedule",
    ) {
        App()
    }
}