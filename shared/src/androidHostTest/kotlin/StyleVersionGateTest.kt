import com.shangkeschedule.data.repository.migrateStyleProtoBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * R1-003：样式备份版本闸门（`migrateStyleProtoBytes`）的回归测试。
 *
 * 缺陷：原实现只拦「版本 > 当前」，其余一律把原始字节喂进解析器；
 * 且迁移逻辑是一句长期悬挂的 TODO。
 *
 * 本测试刻意覆盖 fail-closed 的三个方向：
 *   ① 过高版本 ⇒ 拒绝（不能把新格式喂进旧解析器）
 *   ② 非法版本（0 / 负数）⇒ 拒绝（不能放行损坏数据）
 *   ③ 未来新增的未知版本 ⇒ 拒绝（不能「原样使用」蒙混过关）
 *   ④ 唯一已知版本 v1 ⇒ 原样通过（迁移为空操作）
 */
class StyleVersionGateTest {

    private val current = 1
    private val bytes = byteArrayOf(1, 2, 3, 4)

    @Test
    fun v1_已知版本原样通过() {
        val r = migrateStyleProtoBytes(1, current, bytes)
        assertTrue(r.isSuccess, "v1 应通过，实际失败：${r.exceptionOrNull()?.message}")
        assertContentEquals(bytes, r.getOrThrow())
    }

    @Test
    fun 高于当前版本必须拒绝() {
        val r = migrateStyleProtoBytes(2, current, bytes)
        assertTrue(r.isFailure, "版本 2 > 当前 1 必须拒绝")
        assertTrue(
            r.exceptionOrNull()?.message?.startsWith("STYLE_TOO_NEW") == true,
            "错误码应是 STYLE_TOO_NEW，实际：${r.exceptionOrNull()?.message}"
        )
    }

    @Test
    fun 零版本必须拒绝() {
        val r = migrateStyleProtoBytes(0, current, bytes)
        assertTrue(r.isFailure, "版本 0 必须拒绝（放行等于把任意字节喂进解析器）")
        assertTrue(
            r.exceptionOrNull()?.message?.startsWith("STYLE_VERSION_INVALID") == true,
            "错误码应是 STYLE_VERSION_INVALID，实际：${r.exceptionOrNull()?.message}"
        )
    }

    @Test
    fun 负版本必须拒绝() {
        val r = migrateStyleProtoBytes(-1, current, bytes)
        assertTrue(r.isFailure, "负版本必须拒绝")
        assertTrue(r.exceptionOrNull()?.message?.startsWith("STYLE_VERSION_INVALID") == true)
    }

    @Test
    fun 未来新增的未知版本必须拒绝而不是原样放行() {
        // 模拟：将来 STYLE_SCHEMA_VERSION 升到 3，但忘了给 2 写迁移分支
        val r = migrateStyleProtoBytes(2, 3, bytes)
        assertTrue(r.isFailure, "未写迁移分支的版本必须拒绝，不能原样使用")
        assertTrue(
            r.exceptionOrNull()?.message?.startsWith("STYLE_VERSION_UNSUPPORTED") == true,
            "错误码应是 STYLE_VERSION_UNSUPPORTED，实际：${r.exceptionOrNull()?.message}"
        )
    }

    @Test
    fun 判别力自证_实现不得恒真放行() {
        // 三个应当失败的输入必须**各自**失败；
        // 若实现退化成「一律成功」，下面三条会一起翻红。
        val mustFail = listOf(0, -1, 2).map { migrateStyleProtoBytes(it, current, bytes) }
        assertTrue(mustFail.all { it.isFailure }, "非法/过高版本必须全部被拒")
        // 且已知版本仍须通过 —— 若实现退化成「一律拒绝」，这条会翻红
        assertTrue(migrateStyleProtoBytes(1, current, bytes).isSuccess, "v1 仍须通过")
    }
}