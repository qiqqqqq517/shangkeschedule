import com.shangkeschedule.ui.settings.coursetables.copySemesterName
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [copySemesterName] 纯函数回归测试（学期管理页「历史学期 → 复制」命名）。
 *
 * 覆盖边界：不重名直接用模板结果、重名递增序号、跳号（副本2 已被占用后取副本3）、
 * 以及源学期名本身含「副本」字样时仍按现有名称集合判重，不与模板互相污染。
 * 模板串按 zh 文案拼装，与 UI 层「哨兵占位符替换」等价。
 */
class CopySemesterNameTest {

    private fun base(name: String) = "$name（副本）"

    private fun indexed(name: String, index: Int) = "$name（副本$index）"

    /** 副本名尚未被占用 → 直接用「原名（副本）」，不进入序号递推。 */
    @Test
    fun returnsBaseNameWhenNotTaken() {
        val result = copySemesterName(
            baseName = base("2024-2025学年 第一学期"),
            existingNames = setOf("2024-2025学年 第一学期"),
            duplicatedName = { indexed("不应被调用", it) }
        )
        assertEquals("2024-2025学年 第一学期（副本）", result)
    }

    /** 副本名已被占用 → 追加序号。 */
    @Test
    fun appendsIndexWhenBaseNameTaken() {
        val result = copySemesterName(
            baseName = base("A"),
            existingNames = setOf("A", "A（副本）"),
            duplicatedName = { indexed("A", it) }
        )
        assertEquals("A（副本2）", result)
    }

    /** 连续复制：中间序号已被占用时继续向后找，不覆盖也不失败。 */
    @Test
    fun skipsTakenIndexes() {
        val result = copySemesterName(
            baseName = base("A"),
            existingNames = setOf("A", "A（副本）", "A（副本2）", "A（副本3）"),
            duplicatedName = { indexed("A", it) }
        )
        assertEquals("A（副本4）", result)
    }

    /** 源学期名本身以「副本」结尾（副本的副本）时，仍与既有名称集合正确判重。 */
    @Test
    fun sourceNameContainingCopySuffixStillDeduplicates() {
        val result = copySemesterName(
            baseName = base("B（副本）"),
            existingNames = setOf("B", "B（副本）", "B（副本）（副本）"),
            duplicatedName = { indexed("B（副本）", it) }
        )
        assertEquals("B（副本）（副本2）", result)
    }
}
