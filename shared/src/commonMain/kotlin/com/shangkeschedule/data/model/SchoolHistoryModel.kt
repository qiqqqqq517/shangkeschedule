package com.shangkeschedule.data.model

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import school_index.School

/**
 * 单个学校的选择记录业务模型
 * 包含跳转到适配器选择页面所需的核心字段。
 */
data class CategoryLastSchool(
    val id: String = "",
    val name: String = "",
    val resourceFolder: String = ""
) {
    /** * 判断该记录是否为空（即该分类下从未选择过学校）
     */
    val isEmpty: Boolean get() = id.isBlank()

    /**
     * 将业务模型转换回 Wire 生成的 School 对象
     */
    fun toSchool(): School {
        return School(
            id = id,
            name = name,
            resource_folder = resourceFolder
        )
    }
}

/**
 * 一组「上次选择的学校」DataStore 键（id / 名称 / 适配目录）。
 */
data class SchoolHistoryKeys(
    val id: Preferences.Key<String>,
    val name: Preferences.Key<String>,
    val folder: Preferences.Key<String>
)

/**
 * 学校选择记录模型 (SchoolHistoryModel)
 *
 * 按 [SchoolCategoryTab] 的两个分类口径存储上次打开学校：教务系统、通用工具。
 *
 * v4.70.0 把本科/专科与研究生合并成「教务系统」后的字段口径：
 * - [bachelor]：**合并后「教务系统」的写入槽位**（键名 `last_school_bachelor_*` 保持不变，
 *   老版本写下的记录因此能直接继续用）；
 * - [postgraduate]：旧版「研究生」槽位，现在只读，仅作 [academic] 的回退来源；
 * - [general]：通用工具槽位，语义不变。
 */
data class SchoolHistoryModel(
    val bachelor: CategoryLastSchool = CategoryLastSchool(),
    val postgraduate: CategoryLastSchool = CategoryLastSchool(),
    val general: CategoryLastSchool = CategoryLastSchool()
) {
    /**
     * 「教务系统」这一类别的最近一次选择。
     *
     * 优先取本科/专科槽位（合并后的写入目标）；升级前最后一次选的是研究生学校的用户，
     * 本科槽位为空，这里回退到旧版研究生槽位 —— 否则「最近访问」会凭空消失。
     */
    val academic: CategoryLastSchool
        get() = if (!bachelor.isEmpty) bachelor else postgraduate

    companion object {
        // --- Preferences DataStore 存储键定义 ---

        // 本科/专科分类（v4.70.0 起 = 合并后的「教务系统」）
        private val KEY_BACHELOR_ID = stringPreferencesKey("last_school_bachelor_id")
        private val KEY_BACHELOR_NAME = stringPreferencesKey("last_school_bachelor_name")
        private val KEY_BACHELOR_FOLDER = stringPreferencesKey("last_school_bachelor_folder")

        // 研究生分类（v4.70.0 起只读：合并前的遗留槽位）
        private val KEY_POSTGRAD_ID = stringPreferencesKey("last_school_postgrad_id")
        private val KEY_POSTGRAD_NAME = stringPreferencesKey("last_school_postgrad_name")
        private val KEY_POSTGRAD_FOLDER = stringPreferencesKey("last_school_postgrad_folder")

        // 通用工具分类
        private val KEY_GENERAL_ID = stringPreferencesKey("last_school_general_id")
        private val KEY_GENERAL_NAME = stringPreferencesKey("last_school_general_name")
        private val KEY_GENERAL_FOLDER = stringPreferencesKey("last_school_general_folder")

        /**
         * 从 DataStore 的 Preferences 对象中解析出完整的业务模型
         */
        fun fromPreferences(prefs: Preferences): SchoolHistoryModel {
            return SchoolHistoryModel(
                bachelor = CategoryLastSchool(
                    id = prefs[KEY_BACHELOR_ID] ?: "",
                    name = prefs[KEY_BACHELOR_NAME] ?: "",
                    resourceFolder = prefs[KEY_BACHELOR_FOLDER] ?: ""
                ),
                postgraduate = CategoryLastSchool(
                    id = prefs[KEY_POSTGRAD_ID] ?: "",
                    name = prefs[KEY_POSTGRAD_NAME] ?: "",
                    resourceFolder = prefs[KEY_POSTGRAD_FOLDER] ?: ""
                ),
                general = CategoryLastSchool(
                    id = prefs[KEY_GENERAL_ID] ?: "",
                    name = prefs[KEY_GENERAL_NAME] ?: "",
                    resourceFolder = prefs[KEY_GENERAL_FOLDER] ?: ""
                )
            )
        }

        /**
         * 某一分类口径对应的写入 / 读取键。
         */
        fun getPrimaryKeys(tab: SchoolCategoryTab): SchoolHistoryKeys = when (tab) {
            SchoolCategoryTab.ACADEMIC_SYSTEM ->
                SchoolHistoryKeys(KEY_BACHELOR_ID, KEY_BACHELOR_NAME, KEY_BACHELOR_FOLDER)

            SchoolCategoryTab.GENERAL_TOOL ->
                SchoolHistoryKeys(KEY_GENERAL_ID, KEY_GENERAL_NAME, KEY_GENERAL_FOLDER)
        }

        /**
         * 该口径下需要一并清理的遗留键。
         *
         * 「教务系统」除了自己的主键，还要清掉合并前的研究生槽位，否则旧记录会在
         * 清空「最近访问」后从 [academic] 的回退分支里再冒出来。
         */
        fun getLegacyKeys(tab: SchoolCategoryTab): List<SchoolHistoryKeys> = when (tab) {
            SchoolCategoryTab.ACADEMIC_SYSTEM ->
                listOf(SchoolHistoryKeys(KEY_POSTGRAD_ID, KEY_POSTGRAD_NAME, KEY_POSTGRAD_FOLDER))

            SchoolCategoryTab.GENERAL_TOOL -> emptyList()
        }
    }
}
