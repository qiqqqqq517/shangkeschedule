package com.shangkeschedule.data.api

import com.shangkeschedule.tool.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okio.Buffer
import org.koin.core.annotation.Single

/**
 * 用户自填的 AI 接口配置（**OpenAI 兼容**：`POST {baseUrl}/chat/completions`）。
 *
 * 刻意不写死厂商：上课不代管 Key、不做代理转发，用户填自己的接口与模型
 * （方案 §5 J1「不绑定厂商」）。
 */
data class AiApiConfig(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
) {
    /** 三项都填了才算配置完成；缺任一项时界面提示去填，而不是发一个注定失败的请求。 */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()

    /** 归一化后的接口地址（用于界面回显与排错）。 */
    val normalizedBaseUrl: String get() = normalizeAiBaseUrl(baseUrl)
}

/** AI 识别失败的原因；界面按它给出明确提示（不静默、不假装成功）。 */
enum class AiFailureKind {
    /** 接口地址 / 模型名 / Key 有缺项。 */
    NOT_CONFIGURED,

    /** 既没选图也没输入文字。 */
    EMPTY_INPUT,

    /** 选的文件不是支持的图片格式（只认 PNG / JPEG / GIF / WebP 的魔数）。 */
    UNSUPPORTED_IMAGE,

    /** 图片太大，不适合直接上传。 */
    IMAGE_TOO_LARGE,

    /** 网络不可用 / 请求异常（含超时）。 */
    NETWORK,

    /** 服务器返回非 2xx（Key 错、额度不足、地址写错都会走到这里）。 */
    HTTP,

    /** 响应不是合法 JSON，或结构里没有可读的文本。 */
    PARSE,

    /** 请求成功，但模型回了个空内容。 */
    EMPTY_RESULT,
}

/** AI 识别结果。 */
sealed interface AiRecognitionResult {

    /** 识别出的课表纯文本（每行一门课），交给既有文本导入链路解析。 */
    data class Success(val text: String) : AiRecognitionResult

    /**
     * 失败。
     *
     * [detail] 尽量带上服务端返回的原因（如「Incorrect API key」），
     * 因为「HTTP 401」对用户没有可操作性，而服务端原话有。
     */
    data class Failure(
        val kind: AiFailureKind,
        val httpCode: Int = 0,
        val detail: String = "",
    ) : AiRecognitionResult
}

/**
 * 交给模型的系统提示词：把截图 / 文字整理成「一行一门课」的纯文本。
 *
 * 这里必须与 `TextImportFormat.PLAIN` 的格式约定保持一致
 * （`课程名 教师 教室 星期 节次 周次`），否则模型输出得再漂亮也解析不出来。
 */
internal const val AI_IMPORT_SYSTEM_PROMPT: String =
    "你是课表整理助手。用户会给你一张课表截图，或一段课表文字。" +
        "请把它整理成每行一门课的纯文本，字段之间用一个空格分隔，顺序为：\n" +
        "课程名 教师 教室 星期 节次 周次\n" +
        "要求：\n" +
        "1. 星期写作「周一」「周二」…「周日」；节次写作「1-2节」；周次写作「1-16周」，" +
        "单周写「3周」，不连续的周次写作「1-8,10-16周」。\n" +
        "2. 教师或教室看不出来时写「待定」，不要编造。\n" +
        "3. 只输出课程行本身：不要表头、不要序号、不要解释、不要 Markdown 代码块。\n" +
        "4. 拿不准的内容宁可少写一行，也不要猜。\n" +
        "示例：\n" +
        "高等数学 张三 教5-103 周一 1-2节 1-16周"

/** 只发图片时的默认指令（用户没另外输入文字）。 */
internal const val AI_IMPORT_IMAGE_INSTRUCTION: String = "请识别这张课表截图里的全部课程。"

/** 请求体构造 / 响应解析用的 JSON 工具（不开启 coerce，避免把异常结构悄悄改成默认值）。 */
internal val AI_IMPORT_JSON: Json = Json { encodeDefaults = true }

/**
 * 归一化用户填的接口地址。
 *
 * 两种常见手误都在这里兜住：① 忘了带协议（`api.deepseek.com/v1`）；② 结尾多写斜杠。
 * 界面与请求都用同一个函数，保证「界面显示什么、请求就打什么」。
 */
internal fun normalizeAiBaseUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    return if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

/**
 * 由基地址得到 chat completions 端点。
 *
 * 用户可能粘的是 `https://api.deepseek.com/v1`（推荐，文档里就这一层），
 * 也可能图省事直接粘完整端点；两种都认。基地址为空时返回空串，调用方按未配置处理。
 */
internal fun buildAiChatEndpoint(baseUrl: String): String {
    val base = normalizeAiBaseUrl(baseUrl)
    if (base.isEmpty()) return ""
    return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
}

/**
 * 按魔数判断图片类型（不看文件名后缀：截图从聊天工具转存常常是错的）。
 * 认不出返回 null，界面据此提示「只支持 PNG / JPG / WebP 截图」。
 */
internal fun sniffImageMimeType(bytes: ByteArray): String? {
    fun startsWith(vararg signature: Int): Boolean {
        if (bytes.size < signature.size) return false
        return signature.withIndex().all { (index, expected) -> (bytes[index].toInt() and 0xFF) == expected }
    }

    // RIFF....WEBP：前 4 字节 RIFF、第 8-11 字节 WEBP，中间 4 字节是长度。
    fun isWebp(): Boolean {
        if (bytes.size < 12) return false
        val riff = String(bytes, 0, 4, Charsets.US_ASCII)
        val webp = String(bytes, 8, 4, Charsets.US_ASCII)
        return riff == "RIFF" && webp == "WEBP"
    }

    return when {
        startsWith(0x89, 0x50, 0x4E, 0x47) -> "image/png"
        startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        startsWith(0x47, 0x49, 0x46, 0x38) -> "image/gif"
        isWebp() -> "image/webp"
        else -> null
    }
}

/** 图片转 data URL（OpenAI 兼容接口的行内图片写法）。 */
@OptIn(ExperimentalEncodingApi::class)
internal fun buildImageDataUrl(bytes: ByteArray, mimeType: String): String =
    "data:$mimeType;base64,${Base64.encode(bytes)}"

/**
 * 构造 chat completions 请求体。
 *
 * - 只有文字时 `content` 是字符串（兼容面最广）；
 * - 带图片时 `content` 是 parts 数组（`text` + `image_url`），这是视觉接口的标准写法。
 */
internal fun buildAiChatRequestJson(
    config: AiApiConfig,
    systemPrompt: String = AI_IMPORT_SYSTEM_PROMPT,
    userText: String?,
    imageDataUrl: String?,
): String {
    val userContent: JsonElement = if (imageDataUrl != null) {
        buildJsonArray {
            val text = userText?.takeIf { it.isNotBlank() } ?: AI_IMPORT_IMAGE_INSTRUCTION
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", text)
                }
            )
            add(
                buildJsonObject {
                    put("type", "image_url")
                    put(
                        "image_url",
                        buildJsonObject { put("url", imageDataUrl) }
                    )
                }
            )
        }
    } else {
        JsonPrimitive(userText.orEmpty())
    }

    val payload = buildJsonObject {
        put("model", config.model.trim())
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", userContent)
                    }
                )
            }
        )
    }
    return AI_IMPORT_JSON.encodeToString(JsonElement.serializer(), payload)
}

/**
 * 从响应里取第一个候选的文本内容。
 *
 * 兼容两种 `content` 形态：字符串（绝大多数厂商）、以及 parts 数组（部分厂商）。
 * 取不到返回 null，由调用方转成 [AiFailureKind.PARSE] / [AiFailureKind.EMPTY_RESULT]。
 */
internal fun parseAiChatResponse(body: String): String? {
    val root = runCatching { AI_IMPORT_JSON.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
    val choices = root["choices"] as? JsonArray ?: return null
    val first = choices.firstOrNull() as? JsonObject ?: return null
    val message = first["message"] as? JsonObject ?: return null
    return extractContentText(message["content"])
}

/**
 * 提取服务端返回的错误说明（Key 错 / 额度不足 / 模型名不对都由它给出人话）。
 *
 * 形如 `{"error":{"message":"Incorrect API key provided"}}`；取不到返回空串。
 */
internal fun parseAiErrorMessage(body: String, limit: Int = 300): String {
    val root = runCatching { AI_IMPORT_JSON.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return ""
    val error = root["error"]
    val message = when (error) {
        is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
        is JsonPrimitive -> error.contentOrNull
        else -> null
    }
    // 有的服务端在非 2xx 时把说明放在 message 字段
    val fallback = (root["message"] as? JsonPrimitive)?.contentOrNull
    return (message ?: fallback).orEmpty().trim().take(limit)
}

private fun extractContentText(content: JsonElement?): String? = when (content) {
    is JsonPrimitive -> content.contentOrNull
    is JsonArray -> content
        .mapNotNull { part -> ((part as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull }
        .joinToString(separator = "")
        .ifBlank { null }

    else -> null
}

/**
 * AI 识别导入客户端（v4.66.0 J1）。
 *
 * 只做一件事：把「一段文字」或「一张图片」交给用户自己填的 OpenAI 兼容接口，
 * 拿回「一行一门课」的纯文本。**不代管 Key、不中转、不缓存识别结果**。
 *
 * 与「检查更新」一样只在用户主动点击时联网：关掉开关就一个请求都不发。
 */
@Single
class AiImportClient {
    private companion object {
        /** 视觉识别比纯文本慢，给足时间；连不上则快速失败。 */
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val REQUEST_TIMEOUT_MS = 90_000L

        /** 响应体上限：纯文本课表远小于此，防止异常响应把整包读进堆。 */
        const val MAX_RESPONSE_BYTES = 256L * 1024
        const val READ_CHUNK_BYTES = 8 * 1024

        /** 单张截图上限：手机截图通常 1–3MB，超过 8MB 多半是选错文件了。 */
        const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
    }

    private val httpClient: HttpClient by lazy {
        HttpClientFactory.create(
            connectTimeout = CONNECT_TIMEOUT_MS,
            request = REQUEST_TIMEOUT_MS,
            socket = REQUEST_TIMEOUT_MS,
        )
    }

    /**
     * 识别课表。
     *
     * @param userText 用户粘贴的课表文字（可空）
     * @param imageBytes 所选截图的原始字节（可空，走 `FileManager.importFile` 拿到的）
     */
    suspend fun recognize(
        config: AiApiConfig,
        userText: String?,
        imageBytes: ByteArray?,
    ): AiRecognitionResult {
        if (!config.isConfigured) {
            return AiRecognitionResult.Failure(AiFailureKind.NOT_CONFIGURED)
        }
        val endpoint = buildAiChatEndpoint(config.baseUrl)
        if (endpoint.isEmpty()) {
            return AiRecognitionResult.Failure(AiFailureKind.NOT_CONFIGURED)
        }
        val hasText = !userText.isNullOrBlank()
        if (!hasText && imageBytes == null) {
            return AiRecognitionResult.Failure(AiFailureKind.EMPTY_INPUT)
        }

        // 图片先校验再编码：编成 base64 后再发现格式不对，白花内存。
        if (imageBytes != null) {
            if (imageBytes.size > MAX_IMAGE_BYTES) {
                return AiRecognitionResult.Failure(AiFailureKind.IMAGE_TOO_LARGE)
            }
            if (sniffImageMimeType(imageBytes) == null) {
                return AiRecognitionResult.Failure(AiFailureKind.UNSUPPORTED_IMAGE)
            }
        }

        // base64 编码（单张最大 8 MB）+ 拼整段请求 JSON 是纯 CPU/内存操作，必须在后台线程
        // 做：调用方是 viewModelScope（Main），同步构造大截图会直接卡住界面。
        val requestJson = withContext(Dispatchers.Default) {
            val dataUrl = imageBytes?.let { bytes ->
                buildImageDataUrl(bytes, sniffImageMimeType(bytes).orEmpty())
            }
            buildAiChatRequestJson(
                config = config,
                userText = userText,
                imageDataUrl = dataUrl,
            )
        }

        return withContext(Dispatchers.IO) {
            val response = try {
                httpClient.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer ${config.apiKey.trim()}")
                    header(HttpHeaders.Accept, "application/json")
                    setBody(requestJson)
                }
            } catch (t: Throwable) {
                return@withContext AiRecognitionResult.Failure(
                    kind = AiFailureKind.NETWORK,
                    detail = t.message.orEmpty(),
                )
            }

            val body = try {
                readBounded(response)
            } catch (t: Throwable) {
                return@withContext AiRecognitionResult.Failure(
                    kind = AiFailureKind.NETWORK,
                    detail = t.message.orEmpty(),
                )
            }

            if (response.status.value !in 200..299) {
                return@withContext AiRecognitionResult.Failure(
                    kind = AiFailureKind.HTTP,
                    httpCode = response.status.value,
                    detail = parseAiErrorMessage(body),
                )
            }

            val text = parseAiChatResponse(body)
            if (text == null) {
                return@withContext AiRecognitionResult.Failure(
                    kind = AiFailureKind.PARSE,
                    detail = parseAiErrorMessage(body),
                )
            }
            val trimmed = text.trim()
            if (trimmed.isEmpty()) {
                return@withContext AiRecognitionResult.Failure(
                    kind = AiFailureKind.EMPTY_RESULT,
                    detail = parseAiErrorMessage(body),
                )
            }
            AiRecognitionResult.Success(trimmed)
        }
    }

    /** 带上限读取响应体（分块计数兜底，因为分块传输不带 Content-Length）。 */
    private suspend fun readBounded(response: HttpResponse): String {
        val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declared != null && declared > MAX_RESPONSE_BYTES) {
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
            if (total > MAX_RESPONSE_BYTES) {
                throw IllegalStateException("response exceeds $MAX_RESPONSE_BYTES bytes")
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.readByteArray().decodeToString()
    }
}
