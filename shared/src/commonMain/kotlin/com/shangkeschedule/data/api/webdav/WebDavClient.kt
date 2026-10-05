package com.shangkeschedule.data.api.webdav

import io.ktor.client.*
import io.ktor.client.plugins.*
import com.shangkeschedule.tool.AppLog
import com.shangkeschedule.tool.HttpClientFactory
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * WebDAV 协议通信客户端
 * 职责：处理物理文件与目录的传输，支持多级目录级联创建。
 */
class WebDavClient(
    private val config: WebDavConfig
) {

    private val client by lazy {
        // A17：原 `request = 30_000`（30 秒）对「上传/下载整个备份包」这种长传输必然超时
        // —— 备份包可达数十 MB，慢链路上 30 秒远远不够，表现为用户反复失败但服务端正常。
        // 连接超时保持 15 秒（建立连接快慢与传输量无关），请求超时放宽到 5 分钟。
        if (!isSecure) {
            // P2-2 / A18 / E16：明文 http 下 Basic 凭据会以 base64 明文过网。
            // 这里不直接拒绝（自建 NAS / 局域网 WebDAV 是合法用法，硬拒会打断既有用户），
            // 但**必须留下可查的痕迹**：原先这条风险完全静默。
            AppLog.w(
                TAG,
                "WebDAV 使用明文 http（${redactHost(config.baseUrl)}）：Basic 凭据未加密传输，" +
                    "建议改用 https。",
            )
        }
        HttpClientFactory.create(
            connectTimeout = 15_000,
            request = 300_000
        ) {
            install(Auth) {
                basic {
                    credentials {
                        BasicAuthCredentials(username = config.username, password = config.password)
                    }
                    // FIX: 原先 sendWithoutRequest { true } 会在首个请求就预置 Basic 凭据，
                    // 若服务端返回 3xx 跳转到其他主机，凭据存在被一并带出的风险。
                    // 改为等待 401 挑战后再发送（WebDAV 标准交互，功能不受影响）。
                    sendWithoutRequest { false }
                }
            }
        }
    }

    private val normalizedBaseUrl: String
        get() = if (config.baseUrl.endsWith("/")) config.baseUrl else "${config.baseUrl}/"

    /** 是否为加密传输（https）。用于决定是否就明文凭据留痕（P2-2 / A18 / E16）。 */
    private val isSecure: Boolean
        get() = config.baseUrl.trim().lowercase().startsWith("https://")

    /** 日志里只留主机名，不留完整 URL —— 避免把自建服务器的完整路径写进日志。 */
    private fun redactHost(url: String): String {
        val host = url.trim().removePrefix("https://").removePrefix("http://")
        return host.substringBefore('/').ifEmpty { "(空)" }
    }

    private fun buildFullUrl(relativePath: String): String {
        val root = config.getCleanRootPath()
        val relative = relativePath.trim('/')
        return "$normalizedBaseUrl$root$relative"
    }

    suspend fun ensureRootDirectoryExists(): Boolean {
        val rootDir = config.getCleanRootPath().trim('/')
        return ensureRemoteDirChainExists(rootDir)
    }

    private suspend fun ensureRemoteDirChainExists(relativeDirChain: String): Boolean {
        if (relativeDirChain.isEmpty()) return true

        val pathSegments = relativeDirChain.split('/').filter { it.isNotEmpty() }
        var currentPath = ""

        try {
            for (segment in pathSegments) {
                currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
                val fullUrl = "$normalizedBaseUrl$currentPath/"

                val response = client.request(fullUrl) {
                    method = HttpMethod("MKCOL")
                }

                val isSuccess = response.status == HttpStatusCode.Created ||
                        response.status == HttpStatusCode.MethodNotAllowed

                if (!isSuccess) return false
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 上传本地文件 (PUT)
     */
    suspend fun uploadFile(localPath: Path, remoteFileName: String): Boolean {
        if (!FileSystem.SYSTEM.exists(localPath)) return false

        val rootPrefix = config.getCleanRootPath()
        val fileRelativeDir = remoteFileName.substringBeforeLast('/', "")

        val fullRelativeDirChain = if (fileRelativeDir.isEmpty()) {
            rootPrefix.trim('/')
        } else {
            "${rootPrefix.trim('/')}/${fileRelativeDir.trim('/')}"
        }

        if (!ensureRemoteDirChainExists(fullRelativeDirChain)) return false

        return try {
            val bytes = FileSystem.SYSTEM.read(localPath) { readByteArray() }
            val fullUrl = buildFullUrl(remoteFileName)
            val response = client.put(fullUrl) {
                setBody(bytes)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 下载远端文件 (GET)
     *
     * P2-19 / N17 / 无编号-34 修正：
     *  1. 原实现用 `read <= 0` 当 EOF。`readAvailable` 返回 **0** 的含义是「此刻没有更多
     *     数据、但连接并未关闭」，返回 **-1** 才是真正的流结束。把 0 也当成结束 ⇒
     *     连接中途卡顿时会**静默截断**文件，却仍然返回 `true`，上层拿半截备份去恢复。
     *     现只在 `-1` 时结束。
     *  2. 服务端给了 `Content-Length` 就必须与实际写入字节数一致，否则判失败。
     *  3. 任何失败路径都删除半截文件（原实现「失败后文件留盘且不删除」）。
     *  4. 加单文件上限，避免恶意/异常服务端无限流（超限即中止并删除）。
     */
    suspend fun downloadFile(remoteFileName: String, targetLocalPath: Path): Boolean {
        var success = false
        var created = false
        return try {
            val fullUrl = buildFullUrl(remoteFileName)
            val response = client.get(fullUrl)

            if (!response.status.isSuccess()) {
                return false
            }

            val expectedLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (expectedLength != null && expectedLength > MAX_DOWNLOAD_BYTES) {
                AppLog.e(TAG, "WebDAV 下载被拒：声明长度 $expectedLength 超过上限 $MAX_DOWNLOAD_BYTES（$remoteFileName）")
                return false
            }

            val byteChannel = response.bodyAsChannel()
            val buffer = ByteArray(8192)
            var total = 0L
            created = true
            FileSystem.SYSTEM.write(targetLocalPath) {
                while (true) {
                    val read = byteChannel.readAvailable(buffer)
                    if (read == -1) break          // 只有 -1 才是流结束
                    if (read == 0) continue        // 0 = 暂时无数据，继续等
                    total += read
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw IllegalStateException(
                            "WebDAV 下载超过单文件上限 $MAX_DOWNLOAD_BYTES 字节（$remoteFileName）"
                        )
                    }
                    write(buffer, 0, read)
                }
            }

            if (expectedLength != null && total != expectedLength) {
                AppLog.e(
                    TAG,
                    "WebDAV 下载长度不符：期望 $expectedLength 实收 $total（$remoteFileName）"
                )
                return false
            }
            success = true
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "WebDAV 下载失败: $remoteFileName", e)
            false
        } finally {
            // 失败时不留半截文件 —— 半截备份比没有备份更危险（会被当成完好的去恢复）。
            if (!success && created) {
                try {
                    FileSystem.SYSTEM.delete(targetLocalPath)
                } catch (cleanupError: Exception) {
                    AppLog.w(TAG, "删除下载失败的半截文件失败: ${targetLocalPath.name}", cleanupError)
                }
            }
        }
    }

    fun close() = client.close()
}

/** 日志模块标签。 */
private const val TAG = "WebDavClient"

/**
 * 单个备份文件允许下载的上限（无编号-34）。
 *
 * 取 128 MiB：远高于任何真实备份模块（同仓 zip 路径的单条上限是 32 MiB），
 * 只为拦住「恶意/异常服务端无限流」这一情形。超限即中止并删除半截文件。
 */
private const val MAX_DOWNLOAD_BYTES = 128L * 1024 * 1024