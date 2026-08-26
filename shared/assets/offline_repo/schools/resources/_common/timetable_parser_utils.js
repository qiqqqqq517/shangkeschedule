/**
 * 拾光课程表 - 通用教务系统解析工具库
 * 所有学校适配器可复用此工具，减少重复代码
 *
 * 包含：
 * - HTML 文本提取
 * - 教师名清理
 * - 周次解析（支持多种格式）
 * - 节次解析
 * - 课程合并（连续节次、同名同周次）
 * - 数据标准化
 */

const TimetableParser = (function () {

    /**
     * 从 HTML 字符串中提取纯文本内容
     */
    function extractText(htmlStr) {
        if (!htmlStr) return '';
        if (typeof htmlStr !== 'string') return String(htmlStr);
        return htmlStr.replace(/<[^>]+>/g, '').trim();
    }

    /**
     * 从 <a> 标签中提取文本
     */
    function extractAnchorText(htmlStr) {
        if (!htmlStr) return '';
        const match = htmlStr.match(/>([^<]+)</);
        return match ? match[1].trim() : htmlStr.trim();
    }

    /**
     * 清理教师名称，去除括号及其内容、职称等
     */
    function cleanTeacherName(name) {
        if (!name) return '';
        return name
            .replace(/（[^）]*）/g, '')
            .replace(/\([^)]*\)/g, '')
            .replace(/\[.*?\]/g, '')
            .replace(/【.*?】/g, '')
            .replace(/教授|副教授|讲师|助教|导师/g, '')
            .trim();
    }

    /**
     * 解析周次字符串，支持多种格式：
     * - "1,2,3,4,5"
     * - "1-5,8-12"
     * - "1-16周"
     * - "单周" / "双周"
     * - "1-16(单)" / "1-16(双)"
     */
    function parseWeeks(weekStr) {
        if (!weekStr) return [];
        const weeks = new Set();

        let isOddOnly = /单周|单/.test(weekStr) && !/双/.test(weekStr);
        let isEvenOnly = /双周|双/.test(weekStr) && !/单/.test(weekStr);

        const cleaned = weekStr
            .replace(/周/g, '')
            .replace(/第/g, '')
            .replace(/[（(]单[）)]/g, '')
            .replace(/[（(]双[）)]/g, '')
            .trim();

        const segments = cleaned.split(/[,，、]/);

        segments.forEach(seg => {
            seg = seg.trim();
            if (!seg) return;

            const rangeMatch = seg.match(/(\d+)\s*[-~至到]\s*(\d+)/);
            if (rangeMatch) {
                const start = parseInt(rangeMatch[1]);
                const end = parseInt(rangeMatch[2]);
                if (!isNaN(start) && !isNaN(end) && start > 0 && end > 0) {
                    for (let w = Math.min(start, end); w <= Math.max(start, end); w++) {
                        if (isOddOnly && w % 2 === 0) continue;
                        if (isEvenOnly && w % 2 === 1) continue;
                        weeks.add(w);
                    }
                }
                return;
            }

            const singleMatch = seg.match(/(\d+)/);
            if (singleMatch) {
                const w = parseInt(singleMatch[1]);
                if (!isNaN(w) && w > 0) {
                    if (isOddOnly && w % 2 === 0) return;
                    if (isEvenOnly && w % 2 === 1) return;
                    weeks.add(w);
                }
            }
        });

        return Array.from(weeks).sort((a, b) => a - b);
    }

    /**
     * 解析节次字符串
     */
    function parseSections(sectionStr) {
        if (!sectionStr) return { start: 0, end: 0 };
        const cleaned = String(sectionStr).replace(/节|第|大节/g, '').trim();

        const rangeMatch = cleaned.match(/(\d+)\s*[-~至到]\s*(\d+)/);
        if (rangeMatch) {
            return { start: parseInt(rangeMatch[1]), end: parseInt(rangeMatch[2]) };
        }

        const commaMatch = cleaned.match(/(\d+)[,，、](\d+)/);
        if (commaMatch) {
            return { start: parseInt(commaMatch[1]), end: parseInt(commaMatch[2]) };
        }

        const singleMatch = cleaned.match(/(\d+)/);
        if (singleMatch) {
            const n = parseInt(singleMatch[1]);
            return { start: n, end: n };
        }

        return { start: 0, end: 0 };
    }

    /**
     * 解析星期几
     */
    function parseDay(dayStr) {
        if (typeof dayStr === 'number') return dayStr;
        if (!dayStr) return 0;

        const s = String(dayStr).trim();

        const numMatch = s.match(/(\d+)/);
        if (numMatch) {
            const n = parseInt(numMatch[1]);
            if (n >= 1 && n <= 7) return n;
        }

        const cnMap = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7, '天': 7 };
        for (const [key, val] of Object.entries(cnMap)) {
            if (s.includes(key)) return val;
        }

        const enMap = { 'mon': 1, 'tue': 2, 'wed': 3, 'thu': 4, 'fri': 5, 'sat': 6, 'sun': 7 };
        const lower = s.toLowerCase();
        for (const [key, val] of Object.entries(enMap)) {
            if (lower.includes(key)) return val;
        }

        return 0;
    }

    /**
     * 合并连续节次的同一课程
     */
    function mergeConsecutiveCourses(courses) {
        if (!courses || courses.length === 0) return [];

        const sorted = [...courses].sort((a, b) =>
            a.day - b.day || a.startSection - b.startSection
        );

        const merged = [];
        for (const course of sorted) {
            const last = merged[merged.length - 1];
            if (last &&
                last.name === course.name &&
                last.teacher === course.teacher &&
                last.position === course.position &&
                last.day === course.day &&
                JSON.stringify(last.weeks) === JSON.stringify(course.weeks) &&
                last.endSection + 1 === course.startSection
            ) {
                last.endSection = course.endSection;
            } else {
                merged.push({ ...course });
            }
        }

        return merged;
    }

    /**
     * 标准化课程数据为统一格式
     */
    function normalizeCourse(raw, fieldMap) {
        if (!raw) return null;

        const name = extractText(raw[fieldMap.name] || '');
        const teacher = cleanTeacherName(extractText(raw[fieldMap.teacher] || ''));
        const position = extractText(raw[fieldMap.position] || '') || '待定';
        const day = parseDay(raw[fieldMap.day]);
        const sections = parseSections(raw[fieldMap.sections]);
        const weeks = parseWeeks(raw[fieldMap.weeks] || '');

        if (!name || day < 1 || day > 7 || sections.start < 1 || weeks.length === 0) {
            return null;
        }

        return {
            name, teacher, position, day,
            startSection: sections.start,
            endSection: sections.end,
            weeks,
            color: raw[fieldMap.color] || null,
            remark: raw[fieldMap.remark] || ''
        };
    }

    /**
     * 从日期字符串中提取日期
     */
    function extractDate(dateStr) {
        if (!dateStr) return null;
        const match = String(dateStr).match(/(\d{4})[-/](\d{1,2})[-/](\d{1,2})/);
        if (!match) return null;
        return `${match[1]}-${match[2].padStart(2, '0')}-${match[3].padStart(2, '0')}`;
    }

    /**
     * 构建 Bridge 调用所需的课程 JSON
     */
    function buildBridgeCourses(courses) {
        return courses.map(c => ({
            name: c.name,
            teacher: c.teacher,
            position: c.position,
            day: c.day,
            startSection: c.startSection,
            endSection: c.endSection,
            weeks: c.weeks,
            color: c.color,
            remark: c.remark || ''
        }));
    }

    return {
        extractText,
        extractAnchorText,
        cleanTeacherName,
        parseWeeks,
        parseSections,
        parseDay,
        mergeConsecutiveCourses,
        normalizeCourse,
        extractDate,
        buildBridgeCourses
    };
})();
