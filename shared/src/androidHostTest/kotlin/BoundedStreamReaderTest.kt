import com.shangkeschedule.tool.readAtMostBytes
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 带上限字节读取回归测试。
 *
 * 背景：WebView 请求拦截器原先只校验响应头 `Content-Length`，分块传输（不带该头）
 * 或声明值与实际不符时仍会整包读入堆；本条修复改为读取时计数，超限返回 null
 * 交还 WebView 原生栈流式处理。本测试锁定「不超限完整读出、超限一律 null」契约，
 * 并覆盖跨分块边界与恰好等于上限的临界值。
 */
class BoundedStreamReaderTest {

    @Test
    fun readsAllBytesWhenUnderLimit() {
        val payload = ByteArray(1024) { (it % 251).toByte() }
        val result = ByteArrayInputStream(payload).readAtMostBytes(1024 * 1024)
        assertContentEquals(payload, result)
    }

    @Test
    fun readsExactlyAtLimit() {
        val payload = ByteArray(4096) { it.toByte() }
        val result = ByteArrayInputStream(payload).readAtMostBytes(4096)
        assertContentEquals(payload, result)
    }

    @Test
    fun returnsNullOnePastLimit() {
        val payload = ByteArray(4097)
        assertNull(ByteArrayInputStream(payload).readAtMostBytes(4096))
    }

    @Test
    fun returnsEmptyArrayForEmptyStream() {
        assertEquals(0, ByteArrayInputStream(ByteArray(0)).readAtMostBytes(1024)?.size)
    }

    @Test
    fun bailsOutOnOversizedChunkedBody() {
        // 模拟无 Content-Length 的超大响应：100KB 输入、上限 8KB，必须放弃而不是读满
        val payload = ByteArray(100 * 1024)
        assertNull(ByteArrayInputStream(payload).readAtMostBytes(8L * 1024))
    }
}
