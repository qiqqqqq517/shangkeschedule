package com.shangkeschedule.data.db.main

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Room 实体类，代表「培养方案课程」数据表（v4.75.0 学业情况）。
 *
 * 为什么需要这张表：学业情况此前只有「成绩（已出分）+ 类别学分要求」两类数据，
 * 于是**还没修 / 还没出成绩的课程在结构上根本不存在**，页面上只能看到「已经考过的门数」，
 * 无法回答「培养方案一共要上哪些课、还差几门」。本表把「培养方案要求的课程清单」独立存下来，
 * 与成绩表按**课程名**做匹配，即可算出「应修 / 已修 / 未修」。
 *
 * 与 `grades` **刻意不建外键**（同 [Grade] 的理由）：成绩是历史事实，
 * 课程清单是规划；两者按课程名软匹配，删掉清单不该连带删成绩，反之亦然。
 *
 * @param id 唯一标识符（UUID 字符串）。
 * @param courseName 课程名（与 `Grade.courseName` 精确匹配用，两侧都去空白）。
 * @param category 课程类别（与 `Grade.category`、`CreditRequirement.category` 对齐）；null / 空 = 未分类。
 * @param credit 该课程学分；null = 未填。
 * @param suggestedTerm 建议修读学期（自由文本，仅展示用）。
 * @param source 数据来源：[SOURCE_MANUAL] / [SOURCE_PASTE] / [SOURCE_IMPORT]。
 * @param createdAt 创建时间戳（毫秒）。
 * @param updatedAt 最近更新时间戳（毫秒）。
 */
@Entity(
    tableName = "curriculum_courses",
    indices = [
        Index(value = ["category"]),
        Index(value = ["courseName"])
    ]
)
data class CurriculumCourse(
    @PrimaryKey
    val id: String,
    val courseName: String,
    val category: String? = null,
    val credit: Double? = null,
    val suggestedTerm: String? = null,
    @ColumnInfo(defaultValue = "MANUAL")
    val source: String = SOURCE_MANUAL,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        /** 手动录入 / 编辑。 */
        const val SOURCE_MANUAL = "MANUAL"

        /** 粘贴培养方案文本后本地解析导入。 */
        const val SOURCE_PASTE = "PASTE"

        /** 在内嵌教务页面里用适配脚本钩子抓取导入。 */
        const val SOURCE_IMPORT = "IMPORT"

        /** 单条课程名长度上限（与 `Grade.courseName` 一致，避免超长脏数据）。 */
        const val MAX_COURSE_NAME_LENGTH = 100
    }
}
