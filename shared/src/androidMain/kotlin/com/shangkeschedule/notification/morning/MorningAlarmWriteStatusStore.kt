package com.shangkeschedule.notification.morning

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDateTime

/** 最近一次一次性闹钟写入的待处理状态，不参与周闹钟核对屏障。 */
class MorningAlarmWriteStatusStore internal constructor(private val directory: File) {
    constructor(context: Context) : this(context.noBackupFilesDir)
    enum class Kind { BLOCKED, DEFERRED, NOT_EXPRESSIBLE, WRITE_FAILED, NO_CLOCK, UNKNOWN }
    data class Status(val kind: Kind, val plannedAt: LocalDateTime?)

    private val file get() = File(directory, "morning_alarm_write_status.txt")

    fun save(status: Status): Boolean = synchronized(lock) {
        runCatching {
            check(directory.isDirectory)
            val temporary = File.createTempFile("morning-status-", ".tmp", directory)
            try {
                temporary.writeText("${status.kind.name}\n${status.plannedAt ?: ""}", Charsets.UTF_8)
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
            changed.value += 1
            true
        }.getOrDefault(false)
    }

    fun read(): Status? = synchronized(lock) {
        val unknown = Status(Kind.UNKNOWN, null)
        runCatching {
            if (!directory.isDirectory) return@synchronized unknown
            if (Files.notExists(file.toPath())) return@synchronized null
            val lines = file.readText(Charsets.UTF_8).split('\n')
            if (lines.size != 2) return@synchronized unknown
            val kind = Kind.valueOf(lines[0])
            val plannedAt = if (lines[1].isEmpty()) null else LocalDateTime.parse(lines[1])
            Status(kind, plannedAt)
        }.getOrDefault(unknown)
    }

    fun clear(): Boolean = synchronized(lock) {
        runCatching {
            check(directory.isDirectory)
            Files.deleteIfExists(file.toPath())
            changed.value += 1
            true
        }.getOrDefault(false)
    }

    companion object {
        private val lock = Any()
        private val changed = MutableStateFlow(0L)
        /** 进程内状态变更信号；重进应用时仍须先读取持久状态。 */
        val changes = changed.asStateFlow()
    }
}
