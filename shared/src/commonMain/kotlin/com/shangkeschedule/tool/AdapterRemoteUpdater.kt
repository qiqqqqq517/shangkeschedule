package com.shangkeschedule.tool

import com.shangkeschedule.ui.components.ToastManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.adapter_remote_update_failed

/** 远程适配清单，对应私有仓库根目录的 index.json。 */
@Serializable
private data class AdapterManifest(
    @SerialName("schema_version") val schemaVersion: Int = 0,
    @SerialName("generated_at") val generatedAt: String = "",
    @SerialName("file_count") val fileCount: Int = 0,
    val files: List<AdapterManifestEntry> = emptyList(),
)

@Serializable
private data class AdapterManifestEntry(
    val path: String = "",
    val sha256: String = "",
)

/** 一次远程适配同步的结果。 */
sealed interface AdapterSyncResult {
    /** 未注入密钥，远程更新未启用。 */
    data object Disabled : AdapterSyncResult

    /** 本地已是最新，无需变更。 */
    data object UpToDate : AdapterSyncResult

    /** 成功更新了若干文件。 */
    data class Updated(val fileCount: Int) : AdapterSyncResult

    /** 存在 sha256 校验失败的文件（已丢弃并提示用户）。 */
    data class VerificationFailed(val path: String) : AdapterSyncResult

    /** 网络或数据异常（静默回退到内置适配，不打扰用户）。 */
    data class Failed(val reason: String) : AdapterSyncResult
}

/**
 * 远程适配更新器（只新增，不改变任何既有业务逻辑）。
 *
 * 通过 Cloudflare Worker（携带 `X-App-Secret` 鉴权）拉取私有适配仓库的 index.json，
 * 遍历各文件逐个下载并强制 sha256 校验；校验通过才覆盖本地
 * `repo/schools/resources/...` 下的适配脚本，校验失败直接丢弃并提示用户。
 *
 * 拉取失败（断网、Worker 不可达等）时静默回退——内置适配资源照常可用，
 * 教务抓取、课表解析、学校匹配逻辑完全不受影响。
 */
@Single
class AdapterRemoteUpdater(
    private val fileSystem: FileSystem,
    @Named("FilesDir") private val filesDir: Path,
) {
    private companion object {
        const val MANIFEST_PATH = "index.json"
        const val REMOTE_PREFIX = "adapters/"
        const val LOCAL_PREFIX = "schools/resources/"
        const val SECRET_HEADER = "X-App-Secret"
        const val TEMP_SUFFIX = ".shangke-tmp"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * 不安装 Ktor Logging 插件，避免把鉴权请求头写进日志；
     * 显式设置超时，确保 Worker 不可达时快速失败、后台同步协程不会长时间挂起。
     */
    private val httpClient: HttpClient by lazy {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
    }

    private val repoDir: Path get() = filesDir / "repo"

    /** 执行一次远程适配同步；校验失败时提示用户，其余失败静默回退。 */
    suspend fun sync(): AdapterSyncResult {
        val result = withContext(Dispatchers.IO) { runSync() }
        if (result is AdapterSyncResult.VerificationFailed) {
            notifyVerificationFailure()
        }
        return result
    }

    private suspend fun runSync(): AdapterSyncResult {
        val baseUrl = AdapterRemoteSecrets.WORKER_BASE_URL.trim().trimEnd('/')
        val appSecret = AdapterRemoteSecrets.APP_SECRET.trim()
        if (baseUrl.isEmpty() || appSecret.isEmpty()) {
            return AdapterSyncResult.Disabled
        }

        val manifest = try {
            val bytes = fetchBytes("$baseUrl/$MANIFEST_PATH", appSecret)
            json.decodeFromString<AdapterManifest>(bytes.decodeToString())
        } catch (_: Exception) {
            return AdapterSyncResult.Failed("manifest")
        }

        if (manifest.files.isEmpty()) {
            return AdapterSyncResult.Failed("empty-manifest")
        }

        var updated = 0
        for (entry in manifest.files) {
            val relative = entry.path.removePrefix(REMOTE_PREFIX)
            if (entry.sha256.isBlank() || relative == entry.path || relative.isEmpty()) {
                return AdapterSyncResult.VerificationFailed(entry.path)
            }

            val localPath = repoDir / (LOCAL_PREFIX + relative)
            if (localHashMatches(localPath, entry.sha256)) continue

            val bytes = try {
                fetchBytes("$baseUrl/${entry.path}", appSecret)
            } catch (_: Exception) {
                return AdapterSyncResult.Failed("download:${entry.path}")
            }

            val actual = bytes.toByteString().sha256().hex()
            if (!actual.equals(entry.sha256, ignoreCase = true)) {
                return AdapterSyncResult.VerificationFailed(entry.path)
            }

            writeAtomically(localPath, bytes)
            updated += 1
        }

        return if (updated > 0) AdapterSyncResult.Updated(updated) else AdapterSyncResult.UpToDate
    }

    /** 本地文件已是目标版本时跳过下载（内容一致才跳过，被篡改会自动重下）。 */
    private fun localHashMatches(path: Path, expectedSha256: String): Boolean {
        if (!fileSystem.exists(path)) return false
        return try {
            val bytes = fileSystem.read(path) { readByteArray() }
            bytes.toByteString().sha256().hex().equals(expectedSha256, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun fetchBytes(url: String, appSecret: String): ByteArray {
        val response = httpClient.get(url) {
            header(SECRET_HEADER, appSecret)
            header("Accept", "application/octet-stream")
        }
        if (response.status != HttpStatusCode.OK) {
            throw IllegalStateException("unexpected status ${response.status.value}")
        }
        return response.bodyAsBytes()
    }

    /** 先写临时文件再原子替换，避免中途失败导致适配脚本损坏。 */
    private fun writeAtomically(target: Path, bytes: ByteArray) {
        val parent = target.parent ?: return
        fileSystem.createDirectories(parent)

        val temp = parent / (target.name + TEMP_SUFFIX)
        fileSystem.write(temp) { write(bytes) }

        try {
            if (fileSystem.exists(target)) fileSystem.delete(target)
            fileSystem.atomicMove(temp, target)
        } catch (_: Exception) {
            fileSystem.write(target) { write(bytes) }
            runCatching { if (fileSystem.exists(temp)) fileSystem.delete(temp) }
        }
    }

    private suspend fun notifyVerificationFailure() {
        runCatching {
            val message = getString(Res.string.adapter_remote_update_failed)
            withContext(Dispatchers.Main) {
                ToastManager.show(message)
            }
        }
    }
}
