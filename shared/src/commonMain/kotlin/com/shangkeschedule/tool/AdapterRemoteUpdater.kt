package com.shangkeschedule.tool

import com.shangkeschedule.ui.components.ToastManager
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/** 日志模块标签。 */
private const val TAG = "AdapterRemoteUpdater"

/** 临时文件名的进程内单调递增段，配合随机段保证唯一（P1-9）。 */
private val tempSuffixCounter = AtomicLong(0)

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

    /**
     * 部分成功：更新了 [updated] 个文件，另有 [failed] 个文件本轮失败。
     *
     * P2-6：原先任一条目失败即**整轮 return**，其后所有合法更新**永远不会生效**
     * （只要清单里有那一个坏条目，用户就永远停在旧版本）。现改为逐条目记录失败、
     * 继续处理其余条目，最后用本类型如实报告「成了几个、败了几个」。
     */
    data class PartiallyUpdated(val updated: Int, val failed: Int) : AdapterSyncResult

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

    /** 进程内串行化 `sync()`（P1-9）：并发两轮会互踩同一批目标文件与同一临时路径。 */
    private val syncMutex = Mutex()

    /**
     * 不安装 Ktor Logging 插件，避免把鉴权请求头写进日志；
     * 显式设置超时，确保 Worker 不可达时快速失败、后台同步协程不会长时间挂起。
     */
    private val httpClient: HttpClient by lazy {
        HttpClientFactory.create(
            connectTimeout = 10_000,
            request = 20_000,
            socket = 20_000
        )
    }

    private val repoDir: Path get() = filesDir / "repo"

    /**
     * 执行一次远程适配同步；校验失败时提示用户，其余失败静默回退。
     *
     * P1-9：**必须串行化**。App 启动、回到前台、手动触发等多条路径都可能调用
     * `sync()`，并发时两轮会同时写同一批目标文件。原先既无互斥、临时文件名又是
     * 固定的 `target.name + TEMP_SUFFIX` ⇒ 两个写入者落在**同一个临时路径**上，
     * 可交错出半截内容再被原子搬成正式脚本（适配脚本语法错误 = 该校教务导入直接坏掉）。
     * 这里用 `Mutex` 保证进程内串行；`writeAtomically` 另用唯一临时名兜底跨实例情形。
     */
    suspend fun sync(): AdapterSyncResult {
        val result = syncMutex.withLock {
            withContext(Dispatchers.IO) { runSync() }
        }
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
        var failed = 0
        var firstVerificationFailure: String? = null

        // P2-6：逐条目**记录**失败而不是整轮中止 —— 一个坏条目不得让其余合法更新永不生效。
        for (entry in manifest.files) {
            if (entry.sha256.isBlank()) {
                failed++
                if (firstVerificationFailure == null) firstVerificationFailure = entry.path
                continue
            }
            val localPath = resolveLocalPath(entry.path)
            if (localPath == null) {
                failed++
                if (firstVerificationFailure == null) firstVerificationFailure = entry.path
                continue
            }

            if (localHashMatches(localPath, entry.sha256)) continue

            val bytes = try {
                fetchBytes("$baseUrl/${entry.path}", appSecret)
            } catch (_: Exception) {
                failed++
                continue
            }

            val actual = bytes.toByteString().sha256().hex()
            if (!actual.equals(entry.sha256, ignoreCase = true)) {
                failed++
                if (firstVerificationFailure == null) firstVerificationFailure = entry.path
                continue
            }

            if (writeAtomically(localPath, bytes)) {
                updated += 1
            } else {
                failed++
            }
        }

        // 有校验失败时优先如实回报（用户需要知道有文件被丢弃）；
        // 其余失败只要本轮成功更新过，就按「部分成功」汇报，而不是整轮判失败。
        firstVerificationFailure?.let { return AdapterSyncResult.VerificationFailed(it) }
        return when {
            failed > 0 && updated > 0 -> AdapterSyncResult.PartiallyUpdated(updated, failed)
            failed > 0 -> AdapterSyncResult.Failed("all-entries-failed")
            updated > 0 -> AdapterSyncResult.Updated(updated)
            else -> AdapterSyncResult.UpToDate
        }
    }

    /**
     * 把清单中的远程路径映射为本地 repo 内的绝对路径。
     *
     * - `adapters/<相对路径>` → `repo/schools/resources/<相对路径>`（适配脚本）
     * - `index/school_index.pb` → `repo/index/school_index.pb`（学校索引，OTA 同步索引的关键）
     * - 其余路径一律视为非法清单项，拒绝下载（VerificationFailed）。
     */
    internal fun resolveLocalPath(remotePath: String): Path? {
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
    internal fun isSafeRelativePath(relative: String): Boolean {
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
    internal fun confinedToRepo(path: Path): Path? {
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

    /**
     * 先写临时文件再原子替换，避免中途失败导致适配脚本损坏。
     *
     * P2-7 修正两处：
     *  1. **不再「先删目标再搬」**。原实现在 `exists(target)` 时先 `delete(target)` 再
     *     `atomicMove`，两步之间存在目标文件**整体消失**的窗口 —— WebView 恰好在这
     *     一瞬读取该适配脚本就会拿到「文件不存在」，表现为「本校暂未适配」。
     *     okio 的 `atomicMove` 本身语义即为「已存在则替换」（内部走
     *     `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`），删除是多余且有害的。
     *  2. **异常分支不再退化为「直接写目标」**。原先 catch 里 `fileSystem.write(target)`
     *     是**非原子**写：中途失败会在正式路径上留下半截脚本，比不做更糟。
     *     现在兜底也走「删+移」（窗口只在罕见失败路径且极短），再失败就如实返回 false。
     *
     * P1-9：临时文件名加入随机后缀，避免两个同步者共用同一临时路径而交错写坏。
     *
     * @return 是否成功落地（false 时调用方计入本轮失败，不再声称已更新）
     */
    private fun writeAtomically(target: Path, bytes: ByteArray): Boolean {
        val parent = target.parent ?: return false
        return try {
            fileSystem.createDirectories(parent)
            val temp = parent / (target.name + TEMP_SUFFIX + "-" + nextTempSuffix())
            try {
                fileSystem.write(temp) { write(bytes) }
                try {
                    // 直接原子替换；atomicMove 已含 REPLACE_EXISTING，无需先删目标。
                    fileSystem.atomicMove(temp, target)
                } catch (moveError: Exception) {
                    // 兜底：仍走「删+移」而不是直接写目标 —— 直接写会在正式路径留下半截文件。
                    AppLog.w(TAG, "atomicMove 失败，回退为删除后重搬：${target.name}", moveError)
                    runCatching { if (fileSystem.exists(target)) fileSystem.delete(target) }
                    fileSystem.atomicMove(temp, target)
                }
            } finally {
                runCatching { if (fileSystem.exists(temp)) fileSystem.delete(temp) }
            }
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "写入适配文件失败：${target.name}", e)
            false
        }
    }

    /** 生成临时文件随机后缀（不依赖平台 UUID API，纯数值即可满足唯一性）。 */
    private fun nextTempSuffix(): String = tempSuffixCounter.incrementAndGet().toString(36) +
        "-" + Random.nextLong(0, Long.MAX_VALUE).toString(36)

    private suspend fun notifyVerificationFailure() {
        runCatching {
            val message = getString(Res.string.adapter_remote_update_failed)
            withContext(Dispatchers.Main) {
                ToastManager.show(message)
            }
        }
    }
}
