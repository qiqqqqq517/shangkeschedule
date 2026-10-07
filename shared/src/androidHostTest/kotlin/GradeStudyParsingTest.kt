import com.shangkeschedule.data.model.CourseCategoryLexicon
import com.shangkeschedule.data.model.CreditRequirement
import com.shangkeschedule.data.model.PendingCurriculumCourse
import com.shangkeschedule.data.model.StudyCategoryProgress
import com.shangkeschedule.data.model.StudyProgress
import com.shangkeschedule.data.repository.parsePastedCurriculumText
import com.shangkeschedule.data.repository.parsePastedGradeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 成绩 / 学业「粘贴解析 + 门数派生量」单测（v4.75.0）。
 *
 * 为什么单测这几处：这两段是本次改动里**最容易悄悄出错**的部分 ——
 * 分类认错、平台前缀没拼上、门数口径混用，页面上的数字依然「看起来很合理」，
 * 只有拿真实文本逐条对拍才能发现问题。解析函数提为顶层纯函数正是为了能在这里直接跑。
 */
class GradeStudyParsingTest {

    // ==================== 成绩粘贴解析 ====================

    @Test
    fun `成绩行按课程名-性质-学分-成绩切分`() {
        val parsed = parsePastedGradeText("高等数学A 必修 5.0 92").single()
        assertEquals("高等数学A", parsed.courseName)
        assertEquals(5.0, parsed.credit)
        assertEquals("92", parsed.scoreText)
        assertEquals("必修", parsed.category)
    }

    @Test
    fun `没有性质列时不臆造类别`() {
        val parsed = parsePastedGradeText("大学物理 3 85").single()
        assertEquals("大学物理", parsed.courseName)
        assertEquals(3.0, parsed.credit)
        assertNull(parsed.category)
    }

    @Test
    fun `等级制成绩也能作为成绩列被认出`() {
        val parsed = parsePastedGradeText("大学英语 2.0 优秀").single()
        assertEquals("大学英语", parsed.courseName)
        assertEquals("优秀", parsed.scoreText)
        assertEquals(2.0, parsed.credit)
    }

    @Test
    fun `制表符分隔与表头行`() {
        val text = "课程名称\t学分\t成绩\n线性代数\t3\t88"
        val parsed = parsePastedGradeText(text)
        assertEquals(1, parsed.size)
        assertEquals("线性代数", parsed.single().courseName)
    }

    @Test
    fun `性质在学分与成绩之间时同样能识别`() {
        val parsed = parsePastedGradeText("概率论 3.0 必修 90").single()
        assertEquals("概率论", parsed.courseName)
        assertEquals("必修", parsed.category)
        assertEquals(3.0, parsed.credit)
        assertEquals("90", parsed.scoreText)
    }

    /** 阴性对照：性质词表按**整词**匹配，课程名里含「专业」不得被当成性质。 */
    @Test
    fun `性质词整词匹配不误伤课程名`() {
        val parsed = parsePastedGradeText("专业英语 2 91").single()
        assertEquals("专业英语", parsed.courseName)
        assertNull(parsed.category)
        assertTrue(CourseCategoryLexicon.matches("必修"))
        assertTrue(!CourseCategoryLexicon.matches("专业英语"))
    }

    // ==================== 培养方案粘贴解析 ====================

    @Test
    fun `平台行只作前缀叶子行才产出要求`() {
        val text = """
            通识教育课程平台    要求学分:47.0  获得学分:32.0  未获得学分:15.0
              必修课程          要求学分:41.0  获得学分:30.0  未获得学分:11.0  共（12）门通过（5）门
              选修课程          要求学分:6.0   获得学分:2.0   未获得学分:4.0
        """.trimIndent()
        val parsed = parsePastedCurriculumText(text)
        // 平台行本身不得产出要求（否则同一平台会被算两遍）
        assertEquals(2, parsed.requirements.size)
        val required = parsed.requirements.first { it.category == "通识教育课程平台/必修" }
        assertEquals(41.0, required.requiredCredits)
        assertEquals(12, required.requiredCourses)
        val elective = parsed.requirements.first { it.category == "通识教育课程平台/选修" }
        assertEquals(6.0, elective.requiredCredits)
        assertNull(elective.requiredCourses)
    }

    @Test
    fun `课程行剥离序号-性质-学分后得到课程名`() {
        val parsed = parsePastedCurriculumText("1 高等数学A 必修 5.0")
        assertEquals(1, parsed.courses.size)
        val course = parsed.courses.single()
        assertEquals("高等数学A", course.courseName)
        assertEquals("必修", course.category)
        assertEquals(5.0, course.credit)
    }

    @Test
    fun `汇总行与表头行被跳过`() {
        val parsed = parsePastedCurriculumText(
            "课程名称 学分 成绩\n合计 160.0\n已修学分 60.0"
        )
        assertTrue(parsed.isEmpty, "表头 / 合计 / 已修学分行不得被当成课程：${parsed.courses}")
    }

    /** 阴性对照：要求学分为 0 或负数的行不产生要求（避免把「未设置」写成 0 覆盖用户设置）。 */
    @Test
    fun `要求学分为零的行不产出要求`() {
        val parsed = parsePastedCurriculumText("专业教育课程平台/必修 要求学分:0.0")
        assertTrue(parsed.requirements.isEmpty())
    }

    // ==================== 门数派生量 ====================

    @Test
    fun `有清单时已修与未修按清单算`() {
        val item = StudyCategoryProgress(
            category = "必修",
            displayName = "必修",
            requiredCredits = 40.0,
            earnedCredits = 12.0,
            courseCount = 3,          // 已出分 3 门
            failedCount = 0,
            requiredCourses = 10,
            plannedCourses = 12,      // 清单 12 门
            pendingCourses = 5        // 其中 5 门还没成绩
        )
        assertEquals(10, item.targetCourses)   // 应修门数优先用学校给的
        assertEquals(7, item.doneCourses)      // 12 - 5
        assertEquals(5, item.remainingCourses)
    }

    @Test
    fun `只有应修门数时用已出分门数近似`() {
        val item = StudyCategoryProgress(
            category = "必修",
            displayName = "必修",
            requiredCredits = 40.0,
            earnedCredits = 12.0,
            courseCount = 7,
            failedCount = 0,
            requiredCourses = 10
        )
        assertEquals(7, item.doneCourses)
        assertEquals(3, item.remainingCourses)
        assertTrue(item.hasCoursePlan)
    }

    @Test
    fun `既无应修门数也无清单时门数派生量为零`() {
        val item = StudyCategoryProgress(
            category = "",
            displayName = "",
            requiredCredits = 0.0,
            earnedCredits = 0.0,
            courseCount = 4,
            failedCount = 0
        )
        assertNull(item.targetCourses)
        assertEquals(4, item.doneCourses)
        assertEquals(0, item.remainingCourses)
        assertTrue(!item.hasCoursePlan)
    }

    @Test
    fun `总览应修门数优先取教务口径并合计未修门数`() {
        val withPlan = StudyCategoryProgress(
            category = "必修", displayName = "必修", requiredCredits = 40.0, earnedCredits = 10.0,
            courseCount = 3, failedCount = 0, requiredCourses = 10, plannedCourses = 12, pendingCourses = 5
        )
        val withoutPlan = StudyCategoryProgress(
            category = "选修", displayName = "选修", requiredCredits = 10.0, earnedCredits = 4.0,
            courseCount = 2, failedCount = 0, requiredCourses = 4
        )
        val progress = StudyProgress(
            categories = listOf(withPlan, withoutPlan),
            totalRequiredCredits = 50.0,
            totalEarnedCredits = 14.0,
            totalCourses = 5,
            totalFailedCourses = 0,
            hasAnyGrade = true,
            totalPlannedCourses = 12,
            totalPendingCourses = 5
        )
        assertEquals(14, progress.totalRequiredCourses)          // 10 + 4，取自教务
        assertEquals(14, progress.displayTargetCourses)
        assertEquals(5 + 2, progress.totalRemainingCourses)      // 5（清单）+ (4-2)（近似）
        assertTrue(progress.hasAnyCoursePlan)
        assertTrue(progress.hasAnyCurriculum)
    }

    @Test
    fun `没有任何门数信息时总览不伪造口径`() {
        val progress = StudyProgress(
            categories = listOf(
                StudyCategoryProgress(
                    category = "", displayName = "", requiredCredits = 0.0,
                    earnedCredits = 0.0, courseCount = 3, failedCount = 0
                )
            ),
            totalCourses = 3,
            hasAnyGrade = true
        )
        assertNull(progress.totalRequiredCourses)
        assertNull(progress.displayTargetCourses)
        assertTrue(!progress.hasAnyCoursePlan)
        assertEquals(0, progress.totalRemainingCourses)
    }

    /** 待修课程明细只在「本机成绩表里没有这门课」时产生（由仓库层构造，这里校验模型契约）。 */
    @Test
    fun `待修课程明细字段完整`() {
        val pending = PendingCurriculumCourse(
            courseName = "大学物理B（一）",
            category = "学科基础课程平台/必修",
            credit = 3.0,
            suggestedTerm = "2025-2026-2"
        )
        assertEquals("大学物理B（一）", pending.courseName)
        assertEquals(3.0, pending.credit)
        assertEquals("2025-2026-2", pending.suggestedTerm)
    }

    /** 类别要求新增字段默认值必须向后兼容（旧 DataStore JSON 不含该字段）。 */
    @Test
    fun `类别要求的应修门数默认未设置`() {
        val decoded = CreditRequirement(category = "必修", requiredCredits = 40.0)
        assertNull(decoded.requiredCourses)
        assertEquals("必修", decoded.effectiveDisplayName)
    }
}
