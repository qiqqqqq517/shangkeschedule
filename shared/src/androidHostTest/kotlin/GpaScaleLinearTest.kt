import com.shangkeschedule.data.model.GpaScale
import com.shangkeschedule.data.repository.LINEAR_5_BASE
import com.shangkeschedule.data.repository.gpaPointFromLevel
import com.shangkeschedule.data.repository.gpaPointFromPercent
import com.shangkeschedule.data.repository.levelRepresentativeScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 绩点换算规则单测（v4.75.0）。
 *
 * ★ 样本来源：2026-10-07 在南通大学正方 V9（`tdjw.ntu.edu.cn`，菜单模块 N305005，
 * 页面 `/jwglxt/cjcx/cjcx_cxDgXscj.html`，接口 `?doType=query`）用真实登录会话
 * 取该生 2025-2026 学年第 1 学期全部 10 门有成绩课程，逐门记录
 * 「总评成绩 `bfzcj` → 学校标注绩点 `jd` → 学分绩点 `xfjd`」。
 *
 * 这些期望值**不是设计者想象的规则，而是学校系统的真实输出**：任何改动导致
 * 本机换算与学校对不上，这里立刻红。
 */
class GpaScaleLinearTest {

    private data class Real(
        val course: String,
        val score: Double,
        val schoolPoint: Double,
        val credit: Double,
        val schoolCreditPoint: Double,
    )

    /** 该生真实成绩快照（去重后 10 门）。 */
    private val ntuReal = listOf(
        Real("军事训练", 85.0, 3.50, 2.0, 7.00),
        Real("专业入门与专业伦理", 95.0, 4.50, 1.0, 4.50),
        Real("体育（一）", 99.0, 4.90, 1.0, 4.90),
        Real("思想道德与法治", 95.0, 4.50, 3.0, 13.50),
        Real("工程图学(一)", 91.0, 4.10, 3.5, 14.35),
        Real("基础英语（一）", 75.0, 2.50, 2.0, 5.00),
        Real("劳动教育（一）", 95.0, 4.50, 0.5, 2.25),
        Real("高等数学A（一）", 80.0, 3.00, 5.0, 15.00),
        Real("人工智能通识B", 87.0, 3.70, 2.0, 7.40),
        Real("大学物理B（一）", 90.0, 4.00, 3.0, 12.00),
    )

    @Test
    fun `线性制逐门复现学校给出的十个绩点`() {
        ntuReal.forEach { r ->
            val got = gpaPointFromPercent(r.score, GpaScale.LINEAR_5)
            assertEquals(
                expected = r.schoolPoint,
                actual = round2(got),
                message = "${r.course}：总评 ${r.score} 分，学校绩点 ${r.schoolPoint}，" +
                    "线性制算出 $got（差 ${round2(got - r.schoolPoint)}）",
            )
        }
    }

    @Test
    fun `十个样本的加权平均绩点与学校一致`() {
        val sumCreditPoint = ntuReal.sumOf { it.schoolCreditPoint }
        val sumCredit = ntuReal.sumOf { it.credit }
        val schoolGpa = round2(sumCreditPoint / sumCredit)
        // 实测：Σ学分绩点 85.90 ÷ Σ学分 23.0 = 3.7348…（保留两位即 3.73）
        assertEquals(3.73, schoolGpa, message = "实测加权平均绩点基线（2026-10-07）")
        assertEquals(85.90, sumCreditPoint, 1e-9, message = "学分绩点合计")
        assertEquals(23.0, sumCredit, 1e-9, message = "学分合计")

        // 用本机换算重算一遍，两条路径必须给出同一个数
        val local = ntuReal.sumOf { gpaPointFromPercent(it.score, GpaScale.LINEAR_5) * it.credit }
        assertEquals(
            expected = schoolGpa,
            actual = round2(local / sumCredit),
            message = "加权平均绩点：本机 ${round2(local / sumCredit)} vs 学校 $schoolGpa",
        )

        // 同一批数据用阶梯 4.0 制必须算错 —— 证明「选错制式」正是原来的偏差来源。
        // 实测阶梯 4.0 制的加权平均绩点为 3.62，与学校 3.73 差 0.11。
        val stepped = ntuReal.sumOf { gpaPointFromPercent(it.score, GpaScale.SCALE_4) * it.credit }
        assertEquals(3.62, round2(stepped / sumCredit), message = "阶梯 4.0 制的错误基线")
        assertEquals(3.73, schoolGpa, message = "线性制与学校一致")
    }

    @Test
    fun `阶梯制与线性制曲线交叉（不是单调高低）`() {
        // 高分段：线性 > 阶梯
        assertEquals(4.90, round2(gpaPointFromPercent(99.0, GpaScale.LINEAR_5)))
        assertEquals(4.00, round2(gpaPointFromPercent(99.0, GpaScale.SCALE_4)))
        // 低分段：线性 < 阶梯（方向相反）
        assertEquals(2.50, round2(gpaPointFromPercent(75.0, GpaScale.LINEAR_5)))
        assertEquals(2.70, round2(gpaPointFromPercent(75.0, GpaScale.SCALE_4)))
    }

    @Test
    fun `线性制边界与上限`() {
        assertEquals(5.00, round2(gpaPointFromPercent(100.0, GpaScale.LINEAR_5)))
        assertEquals(5.00, round2(gpaPointFromPercent(120.0, GpaScale.LINEAR_5)), "超 100 不外推")
        assertEquals(LINEAR_5_BASE, round2(gpaPointFromPercent(80.0, GpaScale.LINEAR_5)))
        assertEquals(1.00, round2(gpaPointFromPercent(60.0, GpaScale.LINEAR_5)))
        assertEquals(0.00, round2(gpaPointFromPercent(59.0, GpaScale.LINEAR_5)))
        assertEquals(0.00, round2(gpaPointFromPercent(0.0, GpaScale.LINEAR_5)))
    }

    @Test
    fun `线性制下等级制走同一条曲线`() {
        // 实测：优秀门的总评分就是 95（绩点 4.5），良好门就是 85（绩点 3.5）
        assertEquals(4.50, round2(gpaPointFromLevel("优秀", GpaScale.LINEAR_5)))
        assertEquals(3.50, round2(gpaPointFromLevel("良好", GpaScale.LINEAR_5)))
        assertEquals(4.50, round2(gpaPointFromLevel("优", GpaScale.LINEAR_5)))
        assertEquals(3.50, round2(gpaPointFromLevel("良", GpaScale.LINEAR_5)))
        // 与同分数的百分制课必须一致，否则两套口径并存
        assertEquals(
            gpaPointFromPercent(95.0, GpaScale.LINEAR_5),
            gpaPointFromLevel("优秀", GpaScale.LINEAR_5),
        )
        assertEquals(0.0, gpaPointFromLevel("不及格", GpaScale.LINEAR_5))
    }

    @Test
    fun `没有实测依据的等级不猜数`() {
        // 中等 / 及格 在样本中不存在，学校折算方式无证据 ⇒ 返回 null 而不是编一个值
        assertNull(gpaPointFromLevel("中等", GpaScale.LINEAR_5))
        assertNull(gpaPointFromLevel("及格", GpaScale.LINEAR_5))
        assertNull(gpaPointFromLevel("中", GpaScale.LINEAR_5))
        assertNull(levelRepresentativeScore("中等"))
        assertNull(levelRepresentativeScore("及格"))
        // 「通过 / 不通过」依旧不折算
        assertNull(gpaPointFromLevel("通过", GpaScale.LINEAR_5))
        assertNull(gpaPointFromLevel("不通过", GpaScale.LINEAR_5))
    }

    @Test
    fun `阶梯制的等级换算保持原样`() {
        assertEquals(4.0, gpaPointFromLevel("优秀", GpaScale.SCALE_4))
        assertEquals(3.7, gpaPointFromLevel("良好", GpaScale.SCALE_4))
        assertEquals(2.7, gpaPointFromLevel("中等", GpaScale.SCALE_4))
        assertEquals(1.5, gpaPointFromLevel("及格", GpaScale.SCALE_4))
        assertEquals(5.0, gpaPointFromLevel("优秀", GpaScale.SCALE_5))
        assertEquals(4.0, gpaPointFromLevel("良好", GpaScale.SCALE_5))
        assertEquals(3.0, gpaPointFromLevel("中等", GpaScale.SCALE_5))
    }

    @Test
    fun `制式在两条持久化路径上都能往返`() {
        // 路径一：DataStore —— 写 `settings.gpaScale.value`，读 `GpaScale.fromString`
        GpaScale.entries.forEach { s ->
            assertEquals(s, GpaScale.fromString(s.value), "DataStore 往返失败：${s.name}")
        }
        // 路径二：备份导出写 `.name`（见 BackupRepository:848），恢复用 `GpaScale.valueOf`
        GpaScale.entries.forEach { s ->
            val restored = runCatching { GpaScale.valueOf(s.name) }.getOrNull()
            assertEquals(s, restored, "备份往返失败：${s.name}")
        }
        assertEquals(GpaScale.SCALE_4, GpaScale.fromString(null), "空值回落默认")
        assertEquals(GpaScale.SCALE_4, GpaScale.fromString("不存在的制式"))

        // 三档 value 必须互不相同，否则 UI 的 indexOf 会选错档
        assertEquals(
            3,
            GpaScale.entries.map { it.value }.toSet().size,
            "三个制式的 value 必须互不相同",
        )
        // ★ 两条路径的标识不同（value="L5.0" vs name="LINEAR_5"），因此「导出用 name、
        // 恢复用 valueOf」这件事本身是对的，但**任何一侧改成另一套标识都会静默退回默认档**。
        // 这里显式钉住：LINEAR_5 的两个标识都不得为空且互不相等，防止将来有人图省事统一成同一套
        // 却在备份文件兼容上踩坑。
        val linear = GpaScale.LINEAR_5
        assertTrue(linear.value.isNotBlank() && linear.name.isNotBlank())
        assertTrue(
            GpaScale.entries.all { it == linear || it.value != linear.value },
            "线性档的 value 不得与其他档撞值",
        )
        // 用 name 也能唯一还原（备份恢复实际走的这条）
        assertEquals(linear, GpaScale.entries.single { it.name == linear.name })
    }

    /** 等级制换算返回可空，这里把「有值」的情况取出来再四舍五入，避免每个用例都写 `!!`。 */
    private fun round2(v: Double?): Double = Math.round((v ?: 0.0) * 100.0) / 100.0
}