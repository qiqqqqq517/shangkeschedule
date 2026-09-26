package com.shangkeschedule.tool

import com.shangkeschedule.data.di.OperatingSystem
import java.io.File

/**
 * 桌面端应用数据目录的统一解析
 * （Windows: `%APPDATA%`，macOS: `~/Library/Application Support`，Linux: `$XDG_CONFIG_HOME`，
 * 无法识别系统时兜底 `~/.shangkeschedule`）。
 *
 * 桌面端所有"必须跨启动保持稳定"的数据都应落在这里，而不是进程的当前工作目录（`user.dir`）——
 * 后者会随快捷方式的"起始位置"或命令行 cwd 而变，同一份安装可能读到不同的数据。
 */
object DesktopAppDirs {

    private const val FOLDER_NAME = "shangkeschedule"

    /** 应用数据根目录（不存在则创建）。 */
    val root: File by lazy {
        val userHome = System.getProperty("user.home")

        val dir = when (OperatingSystem.current) {
            OperatingSystem.WINDOWS -> {
                val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
                File(appData, FOLDER_NAME)
            }
            OperatingSystem.MACOS -> File(userHome, "Library/Application Support/$FOLDER_NAME")
            OperatingSystem.LINUX -> {
                val configHome = System.getenv("XDG_CONFIG_HOME") ?: "$userHome/.config"
                File(configHome, FOLDER_NAME)
            }
            OperatingSystem.UNKNOWN -> File(userHome, ".$FOLDER_NAME")
        }

        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /** 取根目录下的子目录（不存在则创建）。 */
    fun subDir(name: String): File {
        val dir = File(root, name)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
