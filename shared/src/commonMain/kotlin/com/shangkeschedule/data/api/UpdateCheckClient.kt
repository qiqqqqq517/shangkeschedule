package com.shangkeschedule.data.api

import com.shangkeschedule.tool.AppExternalLinks
import com.shangkeschedule.tool.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Buffer
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

/**
 * 「检查更新」的版本清单（官网静态文件 `website/version.json`）。
 *
 * 夸克网盘分享页是 JS 渲染的动态页面，客户端无法稳定解析出「最新版本号」，
 * 所以版本号走官网静态 JSON，网盘只作为**下载落点**（方案 §5 K4 的混合方案）。
 *
 * 发版时该文件必须与 `website/assets/js/site.js` 的 `SITE.version`/`versionCode`、
 * `website/changelog.html`、`website/sitemap.xml` 一起同步；漏更新会导致
 * 「检查更新」永远说已是最新。
 */
@Serializable
data class UpdateManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    /** 下载落点（当前为夸克网盘分享页）。 */
    val downloadUrl: String = "",
    /** 夸克分享口令：「复制整段打开夸克 App」用；为空则界面上不提供复制入口。 */
    val shareToken: String = "",
    /** 更新说明地址（官网更新日志）。 */
    val releaseNotesUrl: String = "",
)

/** 检查失败的原因；界面按它给出明确提示（不静默、不卡住）。 */
enum class UpdateFailureKind {
    /** 网络不可用 / 请求异常。 */
    NETWORK,

    /** 服务器返回了非 200（例如官网还没发布 version.json 时的 404）。 */
    HTTP,

    /** 响应不是合法 JSON。 */
    PARSE,

    /** JSON 能解析但缺少必要字段（版本号、或确认有新版时的下载地址）。 */
    INCOMPLETE,
}

/** 「检查更新」的结果。 */
sealed interface UpdateCheckResult {

    /** 已是最新；[versionName] 是本地（= 远端）版本名。 */
    data class UpToDate(val versionName: String) : UpdateCheckResult

    /** 发现新版本。 */
    data class UpdateAvailable(val manifest: UpdateManifest) : UpdateCheckResult

    /** 检查失败；[httpCode] 仅在 [UpdateFailureKind.HTTP] 时有意义。 */
    data class Failed(
        val kind: UpdateFailureKind,
        val httpCode: Int = 0,
    ) : UpdateCheckResult
}

internal val UPDATE_MANIFEST_JSON: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** 解析版本清单；非法 JSON 返回 null（调用方转成 [UpdateFailureKind.PARSE]）。 */
internal fun parseUpdateManifest(body: String): UpdateManifest? =
    runCatching { UPDATE_MANIFEST_JSON.decodeFromString<UpdateManifest>(body) }.getOrNull()

/**
 * 纯函数比对（便于单测，不需要网络）：
 * - 清单缺失 / 版本号<=0 / 版本名为空 → 明确失败；
 * - 远端版本号 <= 本地 → 已是最新（此时**不要求**下载地址，历史清单缺字段也不该报错）；
 * - 确认有新版但没有下载地址 → 明确失败（不能给一个点不动的「打开下载页」）。
 */
internal fun evaluateUpdateManifest(
    localVersionCode: Int,
    localVersionName: String,
    manifest: UpdateManifest?,
): UpdateCheckResult {
    if (manifest == null) return UpdateCheckResult.Failed(UpdateFailureKind.PARSE)
    if (manifest.versionCode <= 0 || manifest.versionName.isBlank()) {
        return UpdateCheckResult.Failed(UpdateFailureKind.INCOMPLETE)
    }
    if (manifest.versionCode <= localVersionCode) {
        return UpdateCheckResult.UpToDate(localVersionName)
    }
    if (manifest.downloadUrl.isBlank()) {
        return UpdateCheckResult.Failed(UpdateFailureKind.INCOMPLETE)
    }
    return UpdateCheckResult.UpdateAvailable(manifest)
}

/**
 * 检查更新客户端。
 *
 * 只在用户**主动点击**「检查更新」时请求一次官网 JSON：不做后台轮询、不做开机自检，
 * 避免「偷偷联网」败坏隐私口碑（方案 §5 K4 的硬要求）。
 */
@Single
class UpdateCheckClient(
    @Named("AppVersionCode") private val appVersionCode: Int,
    @Named("AppVersionName") private val appVersionName: String,
) {
    private companion object {
        /** 版本清单只有几百字节，超时给短一点，点击后要快速给结论。 */
        const val TIMEOUT_MS = 15_000L

        /** 响应体上限：清单正常 <1KB，防止被替换成超大响应时把整包读进堆。 */
        const val MAX_MANIFEST_BYTES = 64L * 1024

        const val READ_CHUNK_BYTES = 4 * 1024
    }

    val currentVersionName: String get() = appVersionName

    val currentVersionCode: Int get() = appVersionCode

    private val httpClient: HttpClient by lazy {
        HttpClientFactory.create(
            connectTimeout = TIMEOUT_MS,
            request = TIMEOUT_MS,
            socket = TIMEOUT_MS,
        )
    }

    suspend fun check(): UpdateCheckResult {
        val url = AppExternalLinks.VERSION_MANIFEST_URL
        if (url.isBlank()) return UpdateCheckResult.Failed(UpdateFailureKind.INCOMPLETE)

        return withContext(Dispatchers.IO) {
            val response = try {
                httpClient.get(url) {
                    header(HttpHeaders.Accept, "application/json")
                }
            } catch (_: Throwable) {
                return@withContext UpdateCheckResult.Failed(UpdateFailureKind.NETWORK)
            }

            if (response.status != HttpStatusCode.OK) {
                return@withContext UpdateCheckResult.Failed(
                    kind = UpdateFailureKind.HTTP,
                    httpCode = response.status.value,
                )
            }

            val text = try {
                readBounded(response)
            } catch (_: Throwable) {
                return@withContext UpdateCheckResult.Failed(UpdateFailureKind.NETWORK)
            }

            evaluateUpdateManifest(
                localVersionCode = appVersionCode,
                localVersionName = appVersionName,
                manifest = parseUpdateManifest(text),
            )
        }
    }

    /** 带上限读取响应体（分块计数兜底，因为分块传输不带 Content-Length）。 */
    private suspend fun readBounded(response: HttpResponse): String {
        val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declared != null && declared > MAX_MANIFEST_BYTES) {
            throw IllegalStateException("declared size $declared exceeds limit")
        }

        val channel = response.bodyAsChannel()
        val buffer = Buffer()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        var total = 0L
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(chunk)
            if (read <= 0) break
            total += read
            if (total > MAX_MANIFEST_BYTES) {
                throw IllegalStateException("response exceeds $MAX_MANIFEST_BYTES bytes")
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.readByteArray().decodeToString()
    }
}
