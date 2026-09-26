package com.shangkeschedule.tool

import com.shangkeschedule.ui.components.ToastManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Buffer
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
 * 遍历各文件逐个下载并强制 sha256 校验；校验通过才覆盖本地目标文件，校验失败直接丢弃并提示用户。
 *
 * 支持两类同步目标（由清单中的相对路径决定）：
 * - `adapters/...` → 适配脚本，写入 `repo/schools/resources/...`
 * - `index/school_index.pb` → 学校索引，写入 `repo/index/school_index.pb`（OTA 同步索引）
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
        /** 学校索引的远程相对路径（相对仓库根目录），同步后写入 repo/index/school_index.pb。 */
        const val INDEX_RELATIVE_PATH = "index/school_index.pb"
        const val SECRET_HEADER = "X-App-Secret"
        const val TEMP_SUFFIX = ".shangke-tmp"

        /**
         * 单个远端文件（清单与适配脚本共用）可读取的字节上限。
         *
         * 适配脚本与学校索引正常在数十 KB 量级；网关被替换、配置错误或中间人篡改时
         * 可能返回超大响应，`bodyAsBytes()` 会把整包无上限读进堆导致 OOM。
         * 超限即放弃本次同步（静默回退内置适配资源，业务不受影响）。
         */
        const val MAX_ADAPTER_BYTES = 8L * 1024 * 1024

        /** 单轮同步允许处理的清单条目上限，防止被替换的清单塞入海量条目使后台空转下载。 */
        const val MAX_MANIFEST_ENTRIES = 5_000

        /** 带上限读取响应体时的分块大小。 */
        const val READ_CHUNK_BYTES = 16 * 1024
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

        if (manifest.files.size > MAX_MANIFEST_ENTRIES) {
            return AdapterSyncResult.Failed("manifest-too-large")
        }

        var updated = 0
        for (entry in manifest.files) {
            if (entry.sha256.isBlank()) {
                return AdapterSyncResult.VerificationFailed(entry.path)
            }
            val localPath = resolveLocalPath(entry.path)
                ?: return AdapterSyncResult.VerificationFailed(entry.path)

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

    /**
     * 把清单中的远程路径映射为本地 repo 内的绝对路径。
     *
     * - `adapters/<相对路径>` → `repo/schools/resources/<相对路径>`（适配脚本）
     * - `index/school_index.pb` → `repo/index/school_index.pb`（学校索引，OTA 同步索引的关键）
     * - 其余路径一律视为非法清单项，拒绝下载（VerificationFailed）。
     */
    private fun resolveLocalPath(remotePath: String): Path? {
        if (remotePath.startsWith(REMOTE_PREFIX)) {
            val relative = remotePath.removePrefix(REMOTE_PREFIX)
            if (!isSafeRelativePath(relative)) return null
            return confinedToRepo(repoDir / (LOCAL_PREFIX + relative))
        }
        if (remotePath == INDEX_RELATIVE_PATH) {
            return confinedToRepo(repoDir / INDEX_RELATIVE_PATH)
        }
        return null
    }

    /**
     * 拒绝路径穿越：`..`、`.`、空段、绝对路径、反斜杠、空字节与 URL 编码的点/斜杠。
     *
     * 清单（index.json）本身无签名、path 字段不可信；即便网关侧已做白名单，
     * 客户端也必须独立校验，避免网关被替换或配置错误时写到 repo 沙箱之外。
     */
    private fun isSafeRelativePath(relative: String): Boolean {
        if (relative.isEmpty()) return false
        if (relative.contains('\u0000')) return false
        if (relative.contains('\\')) return false
        if (relative.startsWith("/")) return false
        if (relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) return false
        val lowered = relative.lowercase()
        if (lowered.contains("%2e") || lowered.contains("%2f") || lowered.contains("%5c")) return false
        return true
    }

    /** 双保险：断言归一化后的目标仍落在 repo 目录内（必须带路径分隔符，避免 repo 与 repo_x 前缀混淆）。 */
    private fun confinedToRepo(path: Path): Path? {
        val normalized = path.normalized()
        val repo = repoDir.normalized()
        val normalizedPath = normalized.toString()
        val repoPath = repo.toString()
        return if (normalizedPath == repoPath || normalizedPath.startsWith("$repoPath/")) normalized else null
    }

    /** 本地文件已是目标版本时跳过下载（内容一致才跳过，被篡改会自动重下）。 */
    private fun localHashMatches(path: Path, expectedSha256: String): Boolean {
        if (!fileSystem.exists(path)) return false
        return try {
            // 超大本地文件直接判为不匹配（触发重下），避免整包读入堆
            val size = fileSystem.metadata(path).size
            if (size != null && size > MAX_ADAPTER_BYTES) return false
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
        // 先看声明长度快速拒绝，再在读取时逐块计数兜底（分块传输不带 Content-Length）
        val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_ADAPTER_BYTES) {
            throw IllegalStateException("declared size $declaredLength exceeds limit")
        }
        val channel = response.bodyAsChannel()
        val buffer = Buffer()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        var total = 0L
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(chunk)
            if (read <= 0) break
            total += read
            if (total > MAX_ADAPTER_BYTES) {
                throw IllegalStateException("response exceeds $MAX_ADAPTER_BYTES bytes")
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.readByteArray()
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
