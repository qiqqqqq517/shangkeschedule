package com.shangkeschedule.ui.schoolselection.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import school_index.Adapter
import school_index.AdapterCategory
import school_index.School

/**
 * 选校列表「**一校一行**」的合并口径（v4.73.0）。
 *
 * ## 这个测试在钉死什么
 *
 * 学校索引把同一所学校拆成本科、研究生**两条独立记录**（实测 18 所，如南京大学
 * `nju` + `u_151d2892_pg`）。v4.70.0 把两类合并进同一个「教务系统」分类后，
 * 用户会在列表里看到两行同名学校。本测试钉死三件事，缺任何一件都会造成用户可见故障：
 *
 * 1. **合并**：同名只出一行（用户明确要求的口径）；
 * 2. **不丢适配器**：两条记录的适配器要**都**出现在二级页 —— 丢掉研究生那条等于
 *    让该校的研究生入口彻底不可达；
 * 3. **不丢脚本目录**：两条记录分属不同 `resource_folder`（南开本科 `qiangzhi/`、
 *    研究生 `NANKAI_YJS/`）。若二级页统一用本科目录拼脚本路径，研究生适配器必然
 *    指向不存在的文件，导入时报「适配脚本文件不存在」。
 *
 * 另有一条「最近访问」的回归：历史里存的是合并**前**的研究生记录 id，合并后若只按
 * 主 id（本科那条）匹配，这些用户的最近访问会凭空消失。
 */
class SchoolListEntryTest {

    private fun school(
        id: String,
        name: String,
        folder: String,
        initial: String = "",
        vararg adapters: Adapter
    ) = School(
        id = id,
        name = name,
        initial = initial,
        resource_folder = folder,
        adapters = adapters.toList()
    )

    private fun adapter(
        id: String,
        category: AdapterCategory,
        url: String = ""
    ) = Adapter(
        adapter_id = id,
        adapter_name = id,
        category = category,
        import_url = url
    )

    private val njuUndergrad = school(
        "nju", "南京大学", "kingosoft", "nju",
        adapter("nju_01", AdapterCategory.BACHELOR_AND_ASSOCIATE, "http://jw.nju.edu.cn/")
    )
    private val njuPostgrad = school(
        "u_151d2892_pg", "南京大学", "wisedu", "nju",
        adapter("u_151d2892_pg_01", AdapterCategory.POSTGRADUATE, "https://ehall.nju.edu.cn/")
    )
    private val other = school(
        "seu", "东南大学", "urp", "s",
        adapter("seu_01", AdapterCategory.BACHELOR_AND_ASSOCIATE)
    )

    @Test
    fun `同名学校合并为一行而不是两行`() {
        val entries = mergeSchoolsByName(listOf(njuUndergrad, other, njuPostgrad))

        assertEquals("1792 条索引里有 18 所是本科+研究生两条，合并后应少 18 行", 2, entries.size)
        assertEquals("南京大学", entries[0].name)
        assertEquals("东南大学", entries[1].name)

        // 关键：全表不得出现第二个「南京大学」
        assertEquals(
            "合并后同一校名不得出现两次",
            entries.size,
            entries.map { it.name }.distinct().size
        )
    }

    @Test
    fun `合并后两边的适配器都保留在教务系统口径下`() {
        val entry = mergeSchoolsByName(listOf(njuUndergrad, njuPostgrad)).single()
        val academic = entry.allAdaptersIn(
            setOf(AdapterCategory.BACHELOR_AND_ASSOCIATE, AdapterCategory.POSTGRADUATE)
        )

        assertEquals(
            "本科与研究生的适配器必须都在（丢了就等于该校研究生入口不可达）",
            listOf("nju_01", "u_151d2892_pg_01"),
            academic.map { it.adapter_id }
        )
    }

    @Test
    fun `通用工具口径只取通用工具适配器`() {
        val entry = mergeSchoolsByName(listOf(njuUndergrad, njuPostgrad)).single()

        assertTrue(
            "教务类别不应出现在通用工具口径里",
            entry.allAdaptersIn(setOf(AdapterCategory.GENERAL_TOOL)).isEmpty()
        )
    }

    @Test
    fun `跨记录同一适配器 id 去重避免二级页出现两张同样卡片`() {
        // 重庆理工大学实测两条记录指向同一入口 uis.cqut.edu.cn，但适配器 id 不同；
        // 这里构造「id 相同」的情形：两条记录各自登记同一个适配器 id
        val a = school("x1", "重庆理工大学", "CQUT", "c", adapter("cqut_01", AdapterCategory.BACHELOR_AND_ASSOCIATE))
        val b = school("x2", "重庆理工大学", "CQUT", "c", adapter("cqut_01", AdapterCategory.POSTGRADUATE))
        val entry = mergeSchoolsByName(listOf(a, b)).single()

        val all = entry.allAdaptersIn(
            setOf(AdapterCategory.BACHELOR_AND_ASSOCIATE, AdapterCategory.POSTGRADUATE)
        )
        assertEquals("同一 adapter_id 只应出一张卡片", 1, all.size)
    }

    @Test
    fun `每条适配器带自己那条记录的资源目录而不是统一用主记录目录`() {
        // 这是最容易被忽略、后果最严重的一条：脚本路径按 <resource_folder>/<脚本名> 拼接。
        val entry = mergeSchoolsByName(listOf(njuUndergrad, njuPostgrad)).single()
        val academic = entry.allAdaptersIn(
            setOf(AdapterCategory.BACHELOR_AND_ASSOCIATE, AdapterCategory.POSTGRADUATE)
        )

        val folders = academic.associate { it.adapter_id to entry.entryOf(it.adapter_id).resource_folder }
        assertEquals("nju_01", "kingosoft", folders["nju_01"])
        assertEquals(
            "研究生适配器必须用研究生的 wisedu/ 目录，用本科的 kingosoft/ 会指向不存在的脚本",
            "wisedu",
            folders["u_151d2892_pg_01"]
        )
        // 代表记录仍是本科那条（历史记录与「最近访问」按它落库）
        assertEquals("nju", entry.id)
        assertEquals("kingosoft", entry.resourceFolder)
    }

    @Test
    fun `最近访问能按合并前的研究生记录 id 找回该行`() {
        val entries = mergeSchoolsByName(listOf(njuUndergrad, other, njuPostgrad))

        // 主 id 命中
        assertNotNull(resolveEntryById(entries, "nju"))
        // 合并前的研究生 id 也要命中 —— 否则老用户的「最近访问」会消失
        val byPostgrad = resolveEntryById(entries, "u_151d2892_pg")
        assertNotNull("合并前存的研究生 id 必须仍能找回该行", byPostgrad)
        assertEquals("南京大学", byPostgrad!!.name)
        // 两条 id 应回指同一行
        assertEquals(resolveEntryById(entries, "nju"), byPostgrad)

        assertNull(resolveEntryById(entries, ""))
        assertNull(resolveEntryById(entries, "not_exist"))
    }

    @Test
    fun `校名首尾空白视为同一所`() {
        val padded = school("nju2", "南京大学 ", "kingosoft")
        val entry = mergeSchoolsByName(listOf(njuUndergrad, padded)).single()

        assertEquals("『南京大学』与『南京大学 』应合并为一行", "南京大学", entry.name)
        assertEquals(2, entry.entries.size)
    }

    @Test
    fun `不同校名不合并`() {
        val entries = mergeSchoolsByName(listOf(njuUndergrad, other))
        assertEquals(2, entries.size)
        assertEquals(listOf("南京大学", "东南大学"), entries.map { it.name })
    }

    @Test
    fun `空输入返回空列表`() {
        assertTrue(mergeSchoolsByName(emptyList()).isEmpty())
    }
}