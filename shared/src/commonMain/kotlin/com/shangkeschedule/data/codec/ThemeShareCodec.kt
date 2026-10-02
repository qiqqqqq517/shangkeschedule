package com.shangkeschedule.data.codec

import com.shangkeschedule.data.model.CourseImportExport
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 主题分享内容（v4.66.0 新增，D3）。
 *
 * 只带「看得见的那一层」：
 * - [preset]：主题预设（通透 / 柔绘 / 书卷），决定课表骨架与默认配色；
 * - [themeMode]：深浅模式偏好；
 * - [styleBytes]：`StyleSettingsRepository.exportRawStyleBytes()` 输出的样式 proto 字节，
 *   已经**剔除壁纸路径**（那是本机私有文件，传给对方只会变成坏路径），
 *   落到对方设备时也由 `restoreRawStyleBytes` 保留对方自己的壁纸。
 * - [styleSchemaVersion]：样式协议版本，高于本机版本时拒绝应用（宁可不动，也不要把样式写坏）。
 *
 * 不含任何课程、成绩、账号信息 —— 换机备份以外的第二种「只带外观」的分享形态。
 */
@Serializable
data class ThemeShareContent(
    val preset: String,
    val themeMode: String,
    val styleBytes: ByteArray,
    val styleSchemaVersion: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ThemeShareContent) return false
        return preset == other.preset &&
            themeMode == other.themeMode &&
            styleBytes.contentEquals(other.styleBytes) &&
            styleSchemaVersion == other.styleSchemaVersion
    }

    override fun hashCode(): Int {
        var result = preset.hashCode()
        result = 31 * result + themeMode.hashCode()
        result = 31 * result + styleBytes.contentHashCode()
        result = 31 * result + styleSchemaVersion
        return result
    }
}

/**
 * 主题分享串的编解码（v4.66.0，D3）。
 *
 * 形态与课表分享串（[CourseShareCodec] 的 `SK1:`）同族，前缀换成 `SKT1:`，两者可共存：
 * `SKT1:` + Base64Url(CBOR 载荷) + `#` + CRC32 十六进制。
 *
 * 为什么另起一份而不是把 [CourseShareCodec] 的校验工具抽出来共用：两条串的载荷类型不同、
 * 且 D1 的串已发出去并有单测守着，动共用工具会把回归面扩到课表分享上；这里的 crc32 只有
 * 十来行，自带一份更稳（KC-1 注释见 `crc32`）。
 */
@OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
object ThemeShareCodec {

    /** 主题分享串前缀。 */
    const val PREFIX = "SKT1:"

    /** 载荷版本：不兼容地改字段时 +1，旧版读到更高版本一律判无效。 */
    private const val PAYLOAD_VERSION = 1

    private const val CRC_LENGTH = 8

    sealed interface DecodeResult {
        data class Success(val content: ThemeShareContent) : DecodeResult

        /** 文本里压根没有主题分享串前缀。 */
        data object NotAThemeShareCode : DecodeResult

        /** 有前缀但校验位 / 编码 / 版本不对。 */
        data object Invalid : DecodeResult
    }

    fun looksLikeCode(text: String): Boolean = text.contains(PREFIX)

    fun encode(content: ThemeShareContent): String {
        val payload = SharePayload(v = PAYLOAD_VERSION, content = content)
        val bytes = CourseImportExport.cbor.encodeToByteArray(SharePayload.serializer(), payload)
        return PREFIX + Base64.UrlSafe.encode(bytes) + "#" + crc32Hex(bytes)
    }

    /** 主题分享串长度上限（64 KB）：挡超长粘贴，避免解码前按比例分配内存。 */
    const val MAX_SHARE_TEXT_LENGTH = 64 * 1024

    fun decode(text: String): DecodeResult {
        if (text.length > MAX_SHARE_TEXT_LENGTH) return DecodeResult.Invalid
        val normalized = text.filterNot { it.isWhitespace() }
        val start = normalized.indexOf(PREFIX)
        if (start < 0) return DecodeResult.NotAThemeShareCode

        val body = normalized.substring(start + PREFIX.length)
        val separator = body.lastIndexOf('#')
        if (separator <= 0) return DecodeResult.Invalid

        val encoded = body.substring(0, separator)
        // take(CRC_LENGTH)：串后面跟着聊天文字时只取校验位那 8 位（与课表分享串的口径一致）
        val expectedCrc = body.substring(separator + 1).take(CRC_LENGTH).lowercase()
        if (expectedCrc.length != CRC_LENGTH) return DecodeResult.Invalid

        val bytes = try {
            Base64.UrlSafe.decode(encoded)
        } catch (_: Exception) {
            return DecodeResult.Invalid
        }
        if (crc32Hex(bytes) != expectedCrc) return DecodeResult.Invalid

        val payload = try {
            CourseImportExport.cbor.decodeFromByteArray(SharePayload.serializer(), bytes)
        } catch (_: Exception) {
            return DecodeResult.Invalid
        }
        if (payload.v != PAYLOAD_VERSION) return DecodeResult.Invalid

        return DecodeResult.Success(payload.content)
    }

    /** 标准 CRC32（IEEE 802.3，反射多项式 0xEDB88320），与课表分享串的算法一致。 */
    private val crc32Poly = 0xEDB88320.toInt()

    private fun crc32(bytes: ByteArray): Int {
        var crc = -1
        for (byte in bytes) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor crc32Poly else crc ushr 1
            }
        }
        return crc.inv()
    }

    private fun crc32Hex(bytes: ByteArray): String =
        crc32(bytes).toUInt().toString(16).padStart(CRC_LENGTH, '0')

    @Serializable
    private class SharePayload(
        val v: Int = PAYLOAD_VERSION,
        val content: ThemeShareContent
    )
}
