package com.shangkeschedule.data.model

import school_index.AdapterCategory

/**
 * 选校页的分类口径（v4.70.0）。
 *
 * 本科/专科与研究生教务系统在这里合并成同一类「教务系统」：对用户来说两者是同一件事
 * （进本校教务系统抓课表），此前拆成两个胶囊会带来两个实际问题 ——
 * 同一所学校在列表里按分类出现两次；搜索只命中当前分类，在「本科/专科」下
 * 搜不到只开了研究生适配的学校。
 *
 * [categories] 是该口径在索引里实际覆盖的类别：学校只要命中其中一个就出现在该分类下，
 * 二级页也会把该校这些类别的适配器一起列出，由用户自行选择。
 *
 * ⚠️ **声明顺序即导航参数**（`Destination.AdapterSelection.tabNumber` 存的是本枚举 ordinal）：
 * 新增口径只能追加到末尾，**不得重排或插到中间** —— 重排不会编译报错，只会让已保存的
 * 返回栈选中的胶囊与用户当时点的那一段对不上。
 */
enum class SchoolCategoryTab(val categories: Set<AdapterCategory>) {

    /** 教务系统：本科/专科 + 研究生（合并后的同一入口）。 */
    ACADEMIC_SYSTEM(
        setOf(
            AdapterCategory.BACHELOR_AND_ASSOCIATE,
            AdapterCategory.POSTGRADUATE
        )
    ),

    /** 通用工具：非教务系统类入口（不含任何教务类别）。 */
    GENERAL_TOOL(
        setOf(AdapterCategory.GENERAL_TOOL)
    );

    /** 该适配器（或学校）是否属于本分类口径。 */
    fun matches(category: AdapterCategory): Boolean = category in categories

    companion object {
        /**
         * 由导航参数（[ordinal]）反解口径。
         *
         * 越界一律回退到「教务系统」：v4.70.0 之前该参数存的是 `AdapterCategory` 数值
         * （2 = 本科/专科、3 = 研究生），旧返回栈里可能取到 3。
         */
        fun fromNumber(number: Int): SchoolCategoryTab =
            entries.getOrNull(number) ?: ACADEMIC_SYSTEM
    }
}
