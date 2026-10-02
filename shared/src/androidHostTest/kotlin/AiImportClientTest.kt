import com.shangkeschedule.data.api.AiApiConfig
import com.shangkeschedule.data.api.AI_IMPORT_IMAGE_INSTRUCTION
import com.shangkeschedule.data.api.AI_IMPORT_SYSTEM_PROMPT
import com.shangkeschedule.data.api.buildAiChatEndpoint
import com.shangkeschedule.data.api.buildAiChatRequestJson
import com.shangkeschedule.data.api.buildImageDataUrl
import com.shangkeschedule.data.api.normalizeAiBaseUrl
import com.shangkeschedule.data.api.parseAiChatResponse
import com.shangkeschedule.data.api.parseAiErrorMessage
import com.shangkeschedule.data.api.sniffImageMimeType
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 「AI 识别导入」（J1，v4.66.0）请求层的纯逻辑单测。
 *
 * 覆盖：接口地址归一化与端点拼接、图片魔数嗅探、data URL、请求体构造（纯文字 / 带图两条路径）、
 * 响应解析（字符串 content / parts 数组两种形态）、服务端错误说明提取。
 *
 * 不测网络：`AiImportClient.recognize()` 需要真实响应体，由真机验收覆盖
 * （未配置 / 无输入 / 非图片 / 超大图这几条会在调用网络之前就返回，另有真机用例）。
 */
class AiImportClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- 地址归一化 ---

    @Test
    fun `地址为空时归一化仍为空`() {
        assertEquals("", normalizeAiBaseUrl(""))
        assertEquals("", normalizeAiBaseUrl("   "))
        assertEquals("", normalizeAiBaseUrl("/"))
    }

    @Test
    fun `地址去掉首尾空白与结尾斜杠`() {
        assertEquals(
            "https://api.deepseek.com/v1",
            normalizeAiBaseUrl("  https://api.deepseek.com/v1/  "),
        )
    }

    @Test
    fun `地址没写协议时补 https`() {
        assertEquals("https://api.deepseek.com/v1", normalizeAiBaseUrl("api.deepseek.com/v1"))
    }

    @Test
    fun `地址写成 http 时保持不变`() {
        // 自建/内网服务可能只有 http，不能强行升级成 https 导致连不上。
        assertEquals("http://192.168.1.9:8000/v1", normalizeAiBaseUrl("http://192.168.1.9:8000/v1"))
    }

    @Test
    fun `端点由基地址拼出且已写全时不重复拼`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            buildAiChatEndpoint("https://api.deepseek.com/v1"),
        )
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            buildAiChatEndpoint("https://api.deepseek.com/v1/"),
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            buildAiChatEndpoint("https://api.openai.com/v1/chat/completions"),
        )
        assertEquals("", buildAiChatEndpoint(""))
    }

    // --- 图片魔数 ---

    @Test
    fun `按魔数识别常见图片类型`() {
        assertEquals("image/png", sniffImageMimeType(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)))
        assertEquals("image/jpeg", sniffImageMimeType(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertEquals("image/gif", sniffImageMimeType("GIF89a".encodeToByteArray()))
        assertEquals("image/webp", sniffImageMimeType(webpHeader()))
    }

    @Test
    fun `非图片或过短内容认不出来`() {
        assertNull(sniffImageMimeType(byteArrayOf()))
        assertNull(sniffImageMimeType("hello".encodeToByteArray()))
        assertNull(sniffImageMimeType("%PDF-1.7".encodeToByteArray()))
        // 只有 RIFF 头、没有 WEBP 标记：不能当成 webp 发上去。
        assertNull(sniffImageMimeType("RIFFxxxxWAVE".encodeToByteArray()))
    }

    @Test
    fun `后缀骗人也没关系`() {
        // 字节是 PNG：文件名/后缀如何不影响判定（聊天工具转存常把后缀写错）。
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        assertEquals("image/png", sniffImageMimeType(png))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `data url 带类型前缀且能解回原字节`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val url = buildImageDataUrl(bytes, "image/png")
        assertTrue(url.startsWith("data:image/png;base64,"), "data URL 必须带 mime 前缀：$url")
        val decoded = Base64.decode(url.removePrefix("data:image/png;base64,"))
        assertTrue(bytes.contentEquals(decoded), "base64 往返必须一致")
    }

    // --- 请求体 ---

    @Test
    fun `纯文字请求的 content 是字符串`() {
        val body = buildAiChatRequestJson(
            config = AiApiConfig("https://api.deepseek.com/v1", "deepseek-chat", "sk-test"),
            userText = "高等数学 张三 教5-103 周一 1-2节 1-16周",
            imageDataUrl = null,
        )
        val root = json.parseToJsonElement(body) as JsonObject
        assertEquals("deepseek-chat", (root["model"] as JsonPrimitive).content)

        val messages = root["messages"] as JsonArray
        assertEquals(2, messages.size)
        val system = messages[0] as JsonObject
        assertEquals("system", (system["role"] as JsonPrimitive).content)
        assertEquals(AI_IMPORT_SYSTEM_PROMPT, (system["content"] as JsonPrimitive).content)

        val user = messages[1] as JsonObject
        // 只有文字时 content 用字符串，兼容面最广（部分厂商不接受 parts 数组的纯文字用法）。
        val content = assertNotNull(user["content"] as? JsonPrimitive, "纯文字场景 content 应该是字符串")
        assertTrue(content.content.contains("高等数学"), "用户原文要原样带上")
        assertFalse(root.containsKey("image"), "不该出现无关字段")
    }

    @Test
    fun `带图请求的 content 是 text 加 image_url 的数组`() {
        val dataUrl = "data:image/png;base64,AAAA"
        val body = buildAiChatRequestJson(
            config = AiApiConfig("api.deepseek.com/v1", "deepseek-vl", "sk-test"),
            userText = "这是朋友发我的课表",
            imageDataUrl = dataUrl,
        )
        val root = json.parseToJsonElement(body) as JsonObject
        val messages = root["messages"] as JsonArray
        val parts = (messages[1] as JsonObject)["content"] as JsonArray
        assertEquals(2, parts.size)

        val textPart = parts[0] as JsonObject
        assertEquals("text", (textPart["type"] as JsonPrimitive).content)
        assertEquals("这是朋友发我的课表", (textPart["text"] as JsonPrimitive).content)

        val imagePart = parts[1] as JsonObject
        assertEquals("image_url", (imagePart["type"] as JsonPrimitive).content)
        val imageUrl = (imagePart["image_url"] as JsonObject)["url"] as JsonPrimitive
        assertEquals(dataUrl, imageUrl.content)
    }

    @Test
    fun `带图但没写文字时用默认指令`() {
        val body = buildAiChatRequestJson(
            config = AiApiConfig("https://x.cn/v1", "m", "k"),
            userText = "   ",
            imageDataUrl = "data:image/jpeg;base64,AAAA",
        )
        val parts = ((json.parseToJsonElement(body) as JsonObject)["messages"] as JsonArray)[1] as JsonObject
        val first = (parts["content"] as JsonArray)[0] as JsonObject
        assertEquals(AI_IMPORT_IMAGE_INSTRUCTION, (first["text"] as JsonPrimitive).content)
    }

    @Test
    fun `文字里的引号与换行不会破坏 JSON`() {
        val tricky = "第一行 \"带引号\" 的课\n第二行 反斜杠 \\ 与制表\t符"
        val body = buildAiChatRequestJson(
            config = AiApiConfig("https://x.cn/v1", "m", "k"),
            userText = tricky,
            imageDataUrl = null,
        )
        // 能解析回同一段文字，说明转义是对的（手写字符串拼接最容易在这里翻车）。
        val root = json.parseToJsonElement(body) as JsonObject
        val content = ((root["messages"] as JsonArray)[1] as JsonObject)["content"] as JsonPrimitive
        assertEquals(tricky, content.content)
    }

    @Test
    fun `模型名会去掉首尾空白`() {
        val body = buildAiChatRequestJson(
            config = AiApiConfig("https://x.cn/v1", "  gpt-4o-mini  ", "k"),
            userText = "课",
            imageDataUrl = null,
        )
        val root = json.parseToJsonElement(body) as JsonObject
        assertEquals("gpt-4o-mini", (root["model"] as JsonPrimitive).content)
    }

    // --- 响应解析 ---

    @Test
    fun `解析标准响应`() {
        val body = """
            {
              "id": "chatcmpl-1",
              "choices": [
                { "index": 0, "message": { "role": "assistant", "content": "高等数学 张三 教5-103 周一 1-2节 1-16周" } }
              ]
            }
        """.trimIndent()
        assertEquals("高等数学 张三 教5-103 周一 1-2节 1-16周", parseAiChatResponse(body))
    }

    @Test
    fun `解析只取原始文本不清洗`() {
        // 这是有意的契约：parse 只负责「结构上取得到取不到」，
        // 去空白与「内容全空」的判定交给 AiImportClient（对应 EMPTY_RESULT 分支），
        // 这样「模型返回了但什么都没说」与「响应结构不对」才能给出不同提示。
        val body = """{"choices":[{"message":{"content":"  高等数学  \n"}}]}"""
        assertEquals("  高等数学  \n", parseAiChatResponse(body))
    }

    @Test
    fun `解析 parts 数组形态的 content`() {
        val body = """
            { "choices": [ { "message": { "content": [ { "type": "text", "text": "第一行" }, { "type": "text", "text": "第二行" } ] } } ] }
        """.trimIndent()
        assertEquals("第一行第二行", parseAiChatResponse(body))
    }

    @Test
    fun `响应结构取不到文本时返回 null`() {
        assertNull(parseAiChatResponse("not json at all"))
        assertNull(parseAiChatResponse("{}"))
        assertNull(parseAiChatResponse("""{"choices":[]}"""))
        assertNull(parseAiChatResponse("""{"choices":[{"message":{"role":"assistant"}}]}"""))
        assertNull(parseAiChatResponse("""{"choices":[{"message":{"content":null}}]}"""))
    }

    @Test
    fun `内容为空串或全空白时仍返回原值`() {
        assertEquals("", parseAiChatResponse("""{"choices":[{"message":{"content":""}}]}"""))
        assertEquals("   ", parseAiChatResponse("""{"choices":[{"message":{"content":"   "}}]}"""))
    }

    @Test
    fun `提取服务端错误说明`() {
        assertEquals(
            "Incorrect API key provided",
            parseAiErrorMessage("""{"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}"""),
        )
        // 有的服务端把说明放在顶层 message
        assertEquals("model not found", parseAiErrorMessage("""{"message":"model not found"}"""))
        // 有的直接把 error 写成字符串
        assertEquals("rate limited", parseAiErrorMessage("""{"error":"rate limited"}"""))
        assertEquals("", parseAiErrorMessage("not json"))
        assertEquals("", parseAiErrorMessage("{}"))
    }

    @Test
    fun `错误说明按上限截断`() {
        val long = "x".repeat(500)
        val parsed = parseAiErrorMessage("""{"error":{"message":"$long"}}""", limit = 10)
        assertEquals(10, parsed.length)
    }

    // --- 配置判定 ---

    @Test
    fun `三项齐全才算配置完成`() {
        assertTrue(AiApiConfig("https://x.cn/v1", "m", "k").isConfigured)
        assertFalse(AiApiConfig("", "m", "k").isConfigured)
        assertFalse(AiApiConfig("https://x.cn/v1", "", "k").isConfigured)
        assertFalse(AiApiConfig("https://x.cn/v1", "m", " ").isConfigured)
        assertFalse(AiApiConfig().isConfigured)
    }

    @Test
    fun `配置对象能回显归一化地址`() {
        assertEquals("https://api.deepseek.com/v1", AiApiConfig("  api.deepseek.com/v1/ ", "m", "k").normalizedBaseUrl)
    }

    private fun webpHeader(): ByteArray {
        val bytes = ByteArray(16)
        "RIFF".encodeToByteArray().copyInto(bytes, 0)
        "WEBP".encodeToByteArray().copyInto(bytes, 8)
        return bytes
    }
}
