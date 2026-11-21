package com.shangkeschedule

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.koin.plugin.module.dsl.startKoin

fun main() {
    val dependencies = startKoin<DesktopAppConfig>()
    try {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "上课 · ShangKeSchedule",
            ) {
                App()
            }
        }
    } finally {
        dependencies.close()
    }
}
