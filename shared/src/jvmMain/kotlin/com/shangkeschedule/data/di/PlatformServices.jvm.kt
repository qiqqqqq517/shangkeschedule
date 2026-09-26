package com.shangkeschedule.data.di

import com.shangkeschedule.tool.DesktopAppDirs
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.annotation.Single
import java.io.File

@Single
class JvmAppStorage : AppStorage {

    // 目录规则与桌面端其它组件（密钥库等）共用一份实现，避免多处硬编码导致目录漂移
    private val appRootDir: File get() = DesktopAppDirs.root

    override val filesDir: Path
        get() {
            val dir = File(appRootDir, "files")
            if (!dir.exists()) dir.mkdirs()
            return dir.absolutePath.toPath()
        }

    override val cacheDir: Path
        get() {
            val dir = File(appRootDir, "cache")
            if (!dir.exists()) dir.mkdirs()
            return dir.absolutePath.toPath()
        }

    override fun getDatabasePath(dbName: String): String {
        val dbDir = File(appRootDir, "databases")
        if (!dbDir.exists()) dbDir.mkdirs()
        return File(dbDir, dbName).absolutePath
    }
}