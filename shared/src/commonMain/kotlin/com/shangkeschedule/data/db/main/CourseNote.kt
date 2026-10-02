package com.shangkeschedule.data.db.main

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * 课堂笔记（v4.66.0）。
 *
 * 与 [Course.remark] 的区别：`remark` 是**课程级**的静态备注（学期初填一次，用于记录学分/考核方式
 * 等课程属性，导入时还会被自动解析成 `credit`/`assessmentMethod`/`isLab`）；本表是**按课次**记录的
 * 听课内容，一次课一条（[date] + [sections]），可以带图片附件，会随课程一起删除。
 *
 * 删除语义：外键指向 `courses.id` 且 `onDelete = CASCADE`，因此「课程管理页批量删除 / 只删本次 /
 * 整表删除」都不需要额外的清理逻辑，笔记与图片路径一起随课程消失（图片文件由仓库层顺带删除）。
 */
@Entity(
    tableName = "course_notes",
    foreignKeys = [ForeignKey(
        entity = Course::class,
        parentColumns = ["id"],
        childColumns = ["courseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["courseId"]), Index(value = ["date"])]
)
data class CourseNote(
    @PrimaryKey
    val id: String, // 笔记唯一标识（本地生成）
    val courseId: String, // 所属课程，级联删除的锚点
    val date: String, // 课次日期，格式 "yyyy-MM-dd"
    val sections: String? = null, // 节次描述，如 "3-4节"（自定义时间课程可为空）
    val title: String = "", // 标题，限 60 字
    val content: String = "", // 正文，限 2000 字
    val imagePaths: String? = null, // 图片的本地绝对路径，多张以 '\n' 分隔
    val createdAt: Long,
    val updatedAt: Long
)
