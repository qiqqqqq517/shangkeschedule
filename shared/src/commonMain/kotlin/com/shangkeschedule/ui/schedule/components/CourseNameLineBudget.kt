package com.shangkeschedule.ui.schedule.components

/**
 * 课程块内**课程名可占几行**的判定（纯逻辑，无 Compose 依赖）。
 *
 * ## 要解决什么
 *
 * 课表格格里课程名原为「行数不限 + Ellipsis 兜底」。实际效果是：
 * 「毛泽东思想和中国特色社会主义理论体系概论」这类长课名（20 字）在约 44dp 宽的
 * 单列里会一路折到 **7 行**，把「地点」「教师」整段挤出可视区，
 * 而同一格的时长标签被压到最上方，整块看上去是「挤」而不是「清晰」。
 *
 * 真机截图证据（Redmi K50 Ultra，2026-10-03，7 列课表）：
 * 周四第 8 节「毛泽东思想和中国特色社会主义理论体系概论」占满整格、
 * 地点「@JX04-101」与教师「王海柳」被压到第 7 行末尾；
 * 周五第 10 节「电工电子技术实验」的「实验」二字溢出到格外。
 *
 * ## 为什么不直接写死 maxLines
 *
 * 格子高度是**随节次时长变化**的（同一节课占 1 节还是 2 节，高度差一倍）。
 * 写死 2 行会让「时长两倍的大格子」显得空；写死 4 行会让「一节的小格子」
 * 仍然挤压地点与教师。故按**可用高度**算预算。
 *
 * ## 口径
 *
 * 预算 = 剩余高度 ÷ 单行行高，并**硬性钳制在 [MIN, MAX]**：
 *  - [MIN]：再窄也至少给 2 行，否则「大学物理（一）」这类短课名也会被截成「大学物…」；
 *  - [MAX]：再高也不超过 4 行，否则超长课名会把地点/教师彻底顶掉（那是信息密度更低而非更高）。
 */
object CourseNameLineBudget {

    /** 至少给几行：短课名（4–6 字）在窄格里也要能完整显示。 */
    const val MIN_LINES = 2

    /** 至多给几行：再多就会把地点/教师顶出可视区。 */
    const val MAX_LINES = 4

    /** 行高倍数（与 CourseBlock 里 courseName 的 lineHeight 保持一致）。 */
    private const val LINE_HEIGHT_EM = 1.2f

    /**
     * 按可用高度算课程名的行数上限。
     *
     * @param contentHeightDp 课程块内**文字可用**高度（已扣内边距），单位 dp
     * @param fontSizeDp      课程名字号（已含 fontScale 与宽度自适应缩放），单位 dp
     * @return [MIN_LINES]..[MAX_LINES] 之间的行数
     */
    fun linesFor(contentHeightDp: Float, fontSizeDp: Float): Int {
        // 高度或字号异常（0 / 负 / NaN）时保守给 MIN：宁可截断也不让布局崩掉。
        if (contentHeightDp <= 0f || fontSizeDp <= 0f) return MIN_LINES
        val lineHeightDp = fontSizeDp * LINE_HEIGHT_EM
        if (lineHeightDp <= 0f) return MIN_LINES
        val affordable = (contentHeightDp / lineHeightDp).toInt()
        return affordable.coerceIn(MIN_LINES, MAX_LINES)
    }
}