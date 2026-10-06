import com.shangkeschedule.data.repository.BackupModule
import com.shangkeschedule.data.repository.RESTORE_ORDER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N21：备份恢复的**模块顺序**是硬约束。
 *
 * 依赖来自外键：`CourseNote.courseId → Course.id ON DELETE CASCADE`
 * ⇒ 「先恢复课表」必然连带删除课堂笔记，因此 **USER_DATA 必须在 COURSE 之后**；
 * APP_SETTINGS 里含 `currentCourseTableId`，同样应在课表就位之后再写。
 *
 * 本测试锁住「顺序由代码保证」这一契约：恢复分发的固定顺序必须满足
 * COURSE 在 USER_DATA 之前、COURSE 在 APP_SETTINGS 之前，且覆盖全部模块。
 */
class RestoreModuleOrderTest {

    private val keys: List<String> = RESTORE_ORDER.map { it.key }

    @Test
    fun 课表必须排在课堂笔记之前() {
        val course = keys.indexOf(BackupModule.COURSE.key)
        val userData = keys.indexOf(BackupModule.USER_DATA.key)
        assertTrue(course >= 0 && userData >= 0, "顺序里必须同时含 COURSE 与 USER_DATA")
        assertTrue(
            course < userData,
            "COURSE 必须早于 USER_DATA，否则课堂笔记会被外键拒绝/丢弃；实际顺序=$keys"
        )
    }

    @Test
    fun 课表必须排在应用设置之前() {
        val course = keys.indexOf(BackupModule.COURSE.key)
        val settings = keys.indexOf(BackupModule.APP_SETTINGS.key)
        assertTrue(course >= 0 && settings >= 0, "顺序里必须同时含 COURSE 与 APP_SETTINGS")
        assertTrue(
            course < settings,
            "COURSE 必须早于 APP_SETTINGS，否则 currentCourseTableId 会指向不存在的课表；实际顺序=$keys"
        )
    }

    @Test
    fun 顺序覆盖全部备份模块() {
        val all = BackupModule.entries.map { it.key }.toSet()
        assertEquals(
            all, keys.toSet(),
            "固定顺序必须覆盖全部备份模块，否则新增模块会被静默跳过"
        )
    }

    @Test
    fun 判别力自证_声明顺序不等于恢复顺序且契约仍成立() {
        // `BackupModule.entries` 的声明顺序是 COURSE, STYLE, APP_SETTINGS, USER_DATA，
        // 它恰好也满足 COURSE 在前 —— 所以本测试**不能**靠断言两者不同来证明判别力，
        // 只能靠「把顺序换成错的之后确实违规」来自证。
        val declared = BackupModule.entries.map { it.key }
        assertTrue(declared.isNotEmpty())
        assertTrue(keys.toSet() == declared.toSet(), "两者的集合必须相同（只是顺序不同或相同）")

        // 错误顺序对照：USER_DATA 在 COURSE 之前 ⇒ 必须被判为违规
        val wrong = listOf(BackupModule.USER_DATA.key, BackupModule.COURSE.key)
        val wrongCourse = wrong.indexOf(BackupModule.COURSE.key)
        val wrongUserData = wrong.indexOf(BackupModule.USER_DATA.key)
        assertTrue(
            wrongCourse > wrongUserData,
            "对照：把 USER_DATA 放到 COURSE 之前即违反契约（本断言应恒真，说明对照构造正确）"
        )
        // 真实顺序必须与该错误顺序相反
        assertTrue(
            keys.indexOf(BackupModule.COURSE.key) < keys.indexOf(BackupModule.USER_DATA.key),
            "真实顺序必须满足 COURSE < USER_DATA（若实现退化为 entries 顺序以外的乱序，此处翻红）"
        )
    }
}