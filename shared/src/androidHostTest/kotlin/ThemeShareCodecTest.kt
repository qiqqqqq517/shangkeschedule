import com.shangkeschedule.data.codec.ThemeShareCodec
import com.shangkeschedule.data.codec.ThemeShareContent
import com.shangkeschedule.data.model.CourseImportExport
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * ThemeShareCodec 单测（v4.66.0，D3）。
 *
 * 重点守两件事：① 自编自解能还原（含 ByteArray 的相等语义）；② 任何形式的损坏
 * （缺前缀 / CRC 不符 / 载荷被改 / 版本过高）都判无效 —— 宁可让用户重发一次串，
 * 也不能把坏样式写进本机 DataStore。
 */
@OptIn(ExperimentalSerializationApi::class, ExperimentalEncodingApi::class)
class ThemeShareCodecTest {

    private val sample = ThemeShareContent(
        preset = "SOFT",
        themeMode = "DARK",
        styleBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
        styleSchemaVersion = 1
    )

    @Test
    fun encodeThenDecodeRoundTrips() {
        val code = ThemeShareCodec.encode(sample)

        assertTrue(ThemeShareCodec.looksLikeCode(code))

        val result = ThemeShareCodec.decode(code)
        assertIs<ThemeShareCodec.DecodeResult.Success>(result)
        assertEquals(sample, result.content)
    }

    @Test
    fun decodeToleratesWhitespaceAndSurroundingText() {
        val code = ThemeShareCodec.encode(sample)
        val messy = "  看这个主题 \n$code\n 谢谢！"

        val result = ThemeShareCodec.decode(messy)
        assertIs<ThemeShareCodec.DecodeResult.Success>(result)
        assertEquals(sample, result.content)
    }

    @Test
    fun decodeRejectsTextWithoutPrefix() {
        assertEquals(
            ThemeShareCodec.DecodeResult.NotAThemeShareCode,
            ThemeShareCodec.decode("这不是分享串，只是一段普通文字")
        )
    }

    @Test
    fun decodeTreatsCourseShareCodeAsNotAThemeShareCode() {
        // D1 的课表分享串前缀是 SK1:，两者必须互不误判
        assertEquals(
            ThemeShareCodec.DecodeResult.NotAThemeShareCode,
            ThemeShareCodec.decode("SK1:AAAA#deadbeef")
        )
    }

    @Test
    fun decodeRejectsTamperedCrc() {
        val code = ThemeShareCodec.encode(sample)
        val separator = code.lastIndexOf('#')
        val crc = code.substring(separator + 1)
        val flipped = if (crc[0] == '0') '1' else '0'
        val tampered = code.substring(0, separator + 1) + flipped + crc.substring(1)

        assertEquals(ThemeShareCodec.DecodeResult.Invalid, ThemeShareCodec.decode(tampered))
    }

    @Test
    fun decodeRejectsTamperedPayload() {
        val code = ThemeShareCodec.encode(sample)
        val separator = code.lastIndexOf('#')
        val encoded = code.substring(ThemeShareCodec.PREFIX.length, separator)
        // 改动 Base64 正文的第一个字符：CRC 必然对不上
        val head = encoded[0]
        val flipped = if (head == 'A') 'B' else 'A'
        val tampered = ThemeShareCodec.PREFIX + flipped + encoded.substring(1) +
            code.substring(separator)

        assertEquals(ThemeShareCodec.DecodeResult.Invalid, ThemeShareCodec.decode(tampered))
    }

    @Test
    fun decodeRejectsTruncatedCode() {
        val code = ThemeShareCodec.encode(sample)
        val separator = code.lastIndexOf('#')
        // 丢掉 CRC 整段
        assertEquals(
            ThemeShareCodec.DecodeResult.Invalid,
            ThemeShareCodec.decode(code.substring(0, separator))
        )
    }

    @Test
    fun decodeRejectsTooNewPayloadVersion() {
        // 手搓一条 v=2 的串：载荷字段名与生产代码一致，只把版本号抬高
        val crafted = craft(sample, version = 2)

        assertEquals(ThemeShareCodec.DecodeResult.Invalid, ThemeShareCodec.decode(crafted))
    }

    @Test
    fun looksLikeCodeIsFalseForPlainText() {
        assertFalse(ThemeShareCodec.looksLikeCode("普通文字"))
        assertFalse(ThemeShareCodec.looksLikeCode("SK1:AAAA#deadbeef"))
    }

    /** 用与生产代码相同的 CBOR 形状手搓一条串，用于构造「版本过高」这类边界。 */
    private fun craft(content: ThemeShareContent, version: Int): String {
        val bytes = CourseImportExport.cbor.encodeToByteArray(
            TestPayload.serializer(),
            TestPayload(v = version, content = content)
        )
        return ThemeShareCodec.PREFIX + Base64.UrlSafe.encode(bytes) + "#" + crc32Hex(bytes)
    }

    private fun crc32Hex(bytes: ByteArray): String =
        crc32(bytes).toUInt().toString(16).padStart(8, '0')

    private fun crc32(bytes: ByteArray): Int {
        var crc = -1
        for (byte in bytes) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xEDB88320.toInt() else crc ushr 1
            }
        }
        return crc.inv()
    }

    @Serializable
    private class TestPayload(val v: Int, val content: ThemeShareContent)
}
