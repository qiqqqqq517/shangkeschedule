package com.shangkeschedule.data.db.main

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Room 实体类，代表「成绩」数据表。
 *
 * 与课表**刻意解耦**：成绩是历史数据（跨学期长期留存），而课程表会随学期切换、
 * 也可能被用户删掉重建。若按外键挂到 courses / course_tables 上，就会出现
 * 「删课表连带删成绩」与「换课表后上学期成绩消失」两类数据事故。
 * 因此学期用自由文本标签（如 "2025-2026-1"）表达，只用于分组与排序。
 *
 * 绩点分两类，处理方式刻意不同（v4.75.0）：
 * - **换算绩点**（由分数按当前绩点制实时算出）**不落库**：同一门课的换算值随用户选择的
 *   绩点制（4.0 制 / 5.0 制）而变，存下来就会在切换绩点制后变成脏数据；
 * - **本校绩点**（[gradePoint]）**必须落库**：它是教务系统按本校规则算出的既成事实，
 *   无法由分数反推（各校档位、是否含重修、等级制折算规则都不同）。此前这一列被丢弃，
 *   是「App 算出的绩点与学校对不上」的首要成因。
 *
 * @param id 唯一标识符（UUID 字符串）。
 * @param semester 学期标签，自由文本，仅用于分组与倒序排序。
 * @param courseName 课程名。
 * @param credit 学分；null 表示未填。未填学分的课程不计入加权绩点，只计入门数。
 * @param scoreText 原始成绩文本（"92"、"优秀"、"通过"），用于展示与等级制换算。
 * @param scoreValue 百分制分数；仅当 [scoreText] 能解析为 0–100 的数字时非空。
 * @param gradePoint 本校教务给出的绩点（学校口径，如正方 V9 成绩接口的 `jd`）；
 *   null = 本校未提供，读取时按当前绩点制由分数换算。
 * @param category 课程性质（必修 / 选修 / 限选…），可选，仅用于展示。
 * @param isRetake 是否重修 / 补考记录，可选。
 * @param note 备注。
 * @param source 数据来源：MANUAL（手动）/ PASTE（粘贴解析）/ IMPORT（教务抓取）。
 * @param createdAt 创建时间戳（毫秒）。
 * @param updatedAt 最近更新时间戳（毫秒）。
 */
@Entity(
    tableName = "grades",
    indices = [
        Index(value = ["semester"]),
        Index(value = ["semester", "courseName"])
    ]
)
data class Grade(
    @PrimaryKey
    val id: String,
    val semester: String,
    val courseName: String,
    val credit: Double? = null,
    val scoreText: String? = null,
    val scoreValue: Double? = null,
    val gradePoint: Double? = null,
    val category: String? = null,
    @ColumnInfo(defaultValue = "0")
    val isRetake: Boolean = false,
    val note: String? = null,
    @ColumnInfo(defaultValue = "MANUAL")
    val source: String = SOURCE_MANUAL,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        /** 手动录入 / 编辑。 */
        const val SOURCE_MANUAL = "MANUAL"

        /** 粘贴教务成绩页文本后本地解析导入。 */
        const val SOURCE_PASTE = "PASTE"

        /** 在内嵌教务页面里用脚本抓取导入。 */
        const val SOURCE_IMPORT = "IMPORT"
    }
}
