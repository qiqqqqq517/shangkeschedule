package com.shangkeschedule.tool

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** 带上限读取时的分块大小（16KB，与 ktor / OkHttp 默认拷贝块同量级）。 */
private const val READ_CHUNK_BYTES = 16 * 1024

/**
 * 带上限读取 [this] 的全部字节；累计超过 [limit] 时立即返回 null（已读部分被丢弃）。
 *
 * 存在的意义：靠响应头 `Content-Length` 做保护只能覆盖**诚实声明长度**的响应。
 * 分块传输（`Transfer-Encoding: chunked`）不带该头，声明值与实际不符时也不作数，
 * 此时 `readBytes()` / `bodyAsBytes()` 会把整包无上限读进堆，服务端（或被劫持的网关）
 * 返回超大响应即可 OOM 客户端。本函数边读边计数，超限即放弃，调用方据此降级。
 *
 * @param limit 允许读取的最大字节数；等于该值仍算成功，再多一个字节即失败。
 * @return 读取到的全部字节；超限返回 null。
 */
internal fun InputStream.readAtMostBytes(limit: Long): ByteArray? {
    val buffer = ByteArray(READ_CHUNK_BYTES)
    val output = ByteArrayOutputStream()
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
