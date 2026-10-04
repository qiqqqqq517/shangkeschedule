package com.shangkeschedule.ui.schoolselection.list

import school_index.Adapter
import school_index.AdapterCategory
import school_index.School
import com.shangkeschedule.data.model.CategoryLastSchool

/**
 * 找出某条适配器**属于组内哪条索引记录**（v4.73.0）。
 *
 * 这是脚本路径能拼对的关键：适配脚本按 `<resource_folder>/<脚本名>` 拼接，
 * 而同校本科与研究生记录分属不同目录（南开：本科 `qiangzhi/`、研究生 `NANKAI_YJS/`）。
 * 统一用主记录目录会让其中一半指向不存在的文件、导入时报「适配脚本文件不存在」。
 *
 * 找不到时返回 [representative]（兜底，保证调用方总能拿到一个目录）。
 */
fun SchoolListEntry.entryOf(adapterId: String): School {
    for (school in entries) {
        if (school.adapters.any { it.adapter_id == adapterId }) return school
    }
    return representative
}

/**
 * 把一条索引记录转成「最近访问」存储用的业务模型（v4.73.0）。
 */
internal fun School.toLastSchool(): CategoryLastSchool = CategoryLastSchool(
    id = id,
    name = name,
    resourceFolder = resource_folder
)

/**
 * 选校列表的一行：**以学校为单位**，而不是以索引里的 `School` 记录为单位（v4.73.0）。
 *
 * ## 为什么要合并
 *
 * 学校索引里，同一所学校常被拆成**两条独立记录** —— 本科一条、研究生一条，各有各的
 * `id`、`resource_folder`、适配器与入口 URL。实测 1792 条记录里有 **18 所**如此，典型如：
 *
 * ```
 * 南京大学  nju              (cat=2 本科)  folder=kingosoft  http://jw.nju.edu.cn/
 * 南京大学  u_151d2892_pg    (cat=3 研究生) folder=wisedu    https://ehall.nju.edu.cn/...
 * ```
 *
 * v4.70.0 把两类合并进同一个「教务系统」分类后，用户会在列表里看到**两行同名学校** ——
 * 这不是「按分类各显示一次」，而是索引本身就是两条记录。
 *
 * ## 合并规则
 *
 * 按**校名**（`name` 去空白后精确相等）分组，每校产出一行：
 *   - [entries] 保留组内全部索引记录，顺序稳定（先本科、后研究生，与索引顺序一致）；
 *   - [representative] 是展示与跳转用的主记录 —— 取**第一条**，其 `id` / `initial` /
 *     `resource_folder` 作为该校的代表值，保证「最近访问」与旧的 32 条研究生历史记录
 *     仍能按 id 命中；
 *   - [allAdaptersIn] 汇总组内全部记录中属于指定分类口径的适配器，供二级页一次列全。
 *
 * ## 为什么不能简单地只保留一条记录
 *
 * 两条记录的 `resource_folder` 通常不同（南开：qiangzhi vs NANKAI_YJS），适配脚本
 * 路径按 `resource_folder/<脚本名>` 拼接，丢记录就等于让该校的另一半适配入口彻底不可达。
 * 故必须保留两条，只是**不再作为两行显示**。
 */
data class SchoolListEntry(
    /** 组内全部索引记录（顺序稳定）。 */
    val entries: List<School>
) {
    init {
        require(entries.isNotEmpty()) { "SchoolListEntry 不允许为空" }
    }

    /** 展示与跳转用的主记录：取组内第一条（索引顺序，本科在前）。 */
    val representative: School get() = entries.first()

    val id: String get() = representative.id
    val name: String get() = representative.name
    val initial: String get() = representative.initial

    /**
     * 该校在指定分类口径下的全部适配器。
     *
     * 跨记录汇总（南开在「教务系统」下会有本科 qiangzhi + 研究生 NANKAI_YJS 两条），
     * 故二级页天然能同时列出本科与研究生的入口，由用户自行选择。
     */
    fun allAdaptersIn(categories: Set<AdapterCategory>): List<Adapter> {
        val out = ArrayList<Adapter>()
        val seen = HashSet<String>()
        for (school in entries) {
            for (adapter in school.adapters) {
                if (adapter.category !in categories) continue
                // 同一适配器 id 可能跨记录重复（如重庆理工两条都指向 uis.cqut.edu.cn），
                // 去重后避免二级页出现两张一模一样的卡片
                if (seen.add(adapter.adapter_id)) out.add(adapter)
            }
        }
        return out
    }

    /** 该条目所属主记录的资源目录，供「最近访问」与历史记录回退使用。 */
    val resourceFolder: String get() = representative.resource_folder
}

/**
 * 按校名把索引记录合并成「一校一行」。
 *
 * 分组键取 `name` 去首尾空白；**不做**繁简/全半角归一 —— 那是另一类合并需求，
 * 且会引入「两所真实不同的学校被判为同一所」的风险。这里只合并**确切同名**的记录。
 *
 * 输出顺序：保持**第一条记录首次出现的顺序**，使列表整体顺序与索引一致（索引已按
 * 拼音 initial + 名称排序），避免因分组打乱 A–Z 索引。
 */
fun mergeSchoolsByName(schools: List<School>): List<SchoolListEntry> {
    if (schools.isEmpty()) return emptyList()
    val byName = LinkedHashMap<String, MutableList<School>>(schools.size)
    for (school in schools) {
        byName.getOrPut(school.name.trim()) { ArrayList(1) }.add(school)
    }
    return byName.values.map { SchoolListEntry(it) }
}

/**
 * 用一个学校 id 在合并后的条目里找回所在行（纯函数，v4.73.0）。
 *
 * 历史记录里存的 id 可能是**合并前的研究生记录** id（如 `u_151d2892_pg`），
 * 而列表主 id 是本科那条（`nju`）；只按主 id 精确匹配会让这些用户的「最近访问」
 * 在合并后凭空消失。故先试主 id，再试组内任一 id。
 */
fun resolveEntryById(entries: List<SchoolListEntry>, entryId: String): SchoolListEntry? {
    if (entryId.isBlank()) return null
    entries.firstOrNull { it.id == entryId }?.let { return it }
    return entries.firstOrNull { entry -> entry.entries.any { it.id == entryId } }
}