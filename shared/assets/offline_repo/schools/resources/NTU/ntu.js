// 南通大学上课适配脚本
// 通过正方教务V9个人/班级课表接口获取课程，并转换为导入格式

(function () {
const WINTER_TIME_SLOTS = [
    { number: 1, startTime: "07:50", endTime: "08:30" },
    { number: 2, startTime: "08:40", endTime: "09:20" },
    { number: 3, startTime: "09:35", endTime: "10:15" },
    { number: 4, startTime: "10:30", endTime: "11:10" },
    { number: 5, startTime: "11:20", endTime: "12:00" },
    { number: 6, startTime: "13:30", endTime: "14:10" },
    { number: 7, startTime: "14:20", endTime: "15:00" },
    { number: 8, startTime: "15:20", endTime: "16:00" },
    { number: 9, startTime: "16:10", endTime: "16:50" },
    { number: 10, startTime: "18:30", endTime: "19:10" },
    { number: 11, startTime: "19:20", endTime: "20:00" },
    { number: 12, startTime: "20:10", endTime: "20:50" }
];

const SUMMER_TIME_SLOTS = [
    { number: 1, startTime: "07:50", endTime: "08:30" },
    { number: 2, startTime: "08:40", endTime: "09:20" },
    { number: 3, startTime: "09:35", endTime: "10:15" },
    { number: 4, startTime: "10:30", endTime: "11:10" },
    { number: 5, startTime: "11:20", endTime: "12:00" },
    { number: 6, startTime: "14:00", endTime: "14:40" },
    { number: 7, startTime: "14:50", endTime: "15:30" },
    { number: 8, startTime: "15:50", endTime: "16:30" },
    { number: 9, startTime: "16:40", endTime: "17:20" },
    { number: 10, startTime: "19:00", endTime: "19:40" },
    { number: 11, startTime: "19:50", endTime: "20:30" },
    { number: 12, startTime: "20:40", endTime: "21:20" }
];

function parseSections(value) {
    const numbers = String(value || "").match(/\d+/g);
    if (!numbers || numbers.length === 0) return null;
    const startSection = Number(numbers[0]);
    const endSection = Number(numbers[numbers.length - 1]);
    if (!Number.isInteger(startSection) || !Number.isInteger(endSection) || startSection < 1 || endSection < startSection) return null;
    return { startSection, endSection };
}

// 将周次文本转换为周数数组
function parseWeeks(value) {
    const weeks = new Set();
    String(value || "").replace(/（/g, "(").replace(/）/g, ")").split(/[，,、;]/).forEach((part) => {
        const numbers = part.match(/\d+/g);
        if (!numbers) return;
        const start = Number(numbers[0]);
        const end = Number(numbers[numbers.length - 1]);
        const odd = part.includes("单");
        const even = part.includes("双");
        for (let week = start; week <= end; week += 1) {
            if (odd && week % 2 === 0) continue;
            if (even && week % 2 !== 0) continue;
            weeks.add(week);
        }
    });
    return [...weeks].sort((left, right) => left - right);
}

function normalizeStartDate(value) {
    const match = String(value || "").match(/(\d{4})[-\/.年](\d{1,2})[-\/.月](\d{1,2})/);
    if (!match) return null;
    return `${match[1]}-${match[2].padStart(2, "0")}-${match[3].padStart(2, "0")}`;
}

function findStartDate(value) {
    if (!value || typeof value !== "object") return normalizeStartDate(value);
    if (Array.isArray(value)) {
        const firstWeek = value.find((item) => String(item?.zs) === "1" || String(item?.zsmc) === "1") || value[0];
        return normalizeStartDate(firstWeek?.zrq || firstWeek?.zcrq || firstWeek?.rq);
    }
    const firstWeekDate = normalizeStartDate(value.zrq || value.zcrq || value.rq);
    if (firstWeekDate) return firstWeekDate;
    for (const [key, item] of Object.entries(value)) {
        const date = item && typeof item === "object" ? findStartDate(item) : null;
        if (date) return date;
    }
    return null;
}

async function fetchSemesterStartDate() {
    const xnm = document.querySelector("#xnm")?.value;
    const xqm = document.querySelector("#xqm")?.value;
    if (!xnm || !xqm) {
        console.warn("NTU semester start date: missing xnm or xqm", { xnm, xqm });
        return null;
    }
    const url = new URL("/jwglxt/kbcx/xskbcxZccx_cxZcByXnxq.html?gnmkdm=N2154", window.location.origin);
    try {
        const response = await fetch(url.href, {
            headers: {
                accept: "application/json, text/javascript, */*; q=0.01",
                "content-type": "application/x-www-form-urlencoded;charset=UTF-8",
                "x-requested-with": "XMLHttpRequest"
            },
            body: `xnm=${encodeURIComponent(xnm)}&xqm=${encodeURIComponent(xqm)}`,
            method: "POST",
            credentials: "include"
        });
        const rawText = await response.text();
        let data;
        try {
            data = JSON.parse(rawText);
        } catch {
            console.warn("NTU semester start date: response is not JSON", response.status, rawText);
            return null;
        }
        console.log("NTU semester start date response:", data);
        const startDate = findStartDate(data);
        console.log("NTU semester start date:", startDate || "not found");
        return startDate;
    } catch (error) {
        console.warn("NTU semester start date request failed:", error);
        return null;
    }
}

// 将课程字段转换为导入协议字段
function normalizeCourses(rawCourses) {
    const uniqueCourses = new Map();
    rawCourses.forEach((rawCourse) => {
        const name = String(rawCourse.kcmc || "").replace(/[■◆▲]/g, "").trim();
        const teacher = String(rawCourse.xm || "未知").trim() || "未知";
        const position = String(rawCourse.cdmc || "待定").trim() || "待定";
        const day = Number(rawCourse.xqj);
        const sections = parseSections(rawCourse.jcs || rawCourse.jc);
        const weeks = parseWeeks(rawCourse.zcd);
        if (!name || !sections || !Number.isInteger(day) || day < 1 || day > 7 || weeks.length === 0) return;

        // 提取学分、考核方式（正方教务标准字段）
        const credit = pickText(rawCourse.xf);
        const assessmentMethod = pickText(rawCourse.khfsmc, rawCourse.khfs, rawCourse.ksfsmc);
        const remark = buildRemark(rawCourse);

        const course = { name, teacher, position, day, startSection: sections.startSection, endSection: sections.endSection, weeks };
        if (credit) course.credit = credit;
        if (assessmentMethod) course.assessmentMethod = assessmentMethod;
        if (remark) course.remark = remark;

        const key = [name, teacher, position, day, sections.startSection, sections.endSection, weeks.join(",")].join("|");
        if (!uniqueCourses.has(key)) uniqueCourses.set(key, course);
    });
    return [...uniqueCourses.values()].sort((left, right) => left.day - right.day || left.startSection - right.startSection || left.name.localeCompare(right.name));
}

// 取第一个非空字段值
function pickText(...values) {
    for (const value of values) {
        const text = value != null ? String(value).trim() : "";
        if (text !== "") return text;
    }
    return undefined;
}

// 将其他附加信息收集到备注
function buildRemark(rawCourse) {
    const parts = [];
    const fields = [
        ["课程性质", rawCourse.kcxzmc],
        ["课程类别", rawCourse.kclbmc],
        ["教学班", rawCourse.jxbmc],
        ["周学时", rawCourse.zhxs],
        ["总学时", rawCourse.zxs]
    ];
    for (const [label, value] of fields) {
        const text = value != null ? String(value).trim() : "";
        if (text !== "") parts.push(`${label}：${text}`);
    }
    return parts.length ? parts.join("；") : undefined;
}

// 合并同一课程的重复节次
function mergeAndDistinctCourses(courses) {
    if (courses.length <= 1) return courses;
    const list = courses.map((course) => ({ ...course, weeks: [...new Set(course.weeks)].sort((left, right) => left - right) }));
    const sameBase = (left, right) => left.name === right.name && left.teacher === right.teacher && left.position === right.position && left.day === right.day;
    list.sort((left, right) => left.name.localeCompare(right.name) || left.teacher.localeCompare(right.teacher) || left.position.localeCompare(right.position) || left.day - right.day || left.startSection - right.startSection);
    const merged = [];
    for (const course of list) {
        const previous = merged[merged.length - 1];
        if (previous && sameBase(previous, course) && previous.startSection === course.startSection && previous.endSection === course.endSection) {
            previous.weeks = [...new Set([...previous.weeks, ...course.weeks])].sort((left, right) => left - right);
        } else if (previous && sameBase(previous, course) && previous.weeks.join(",") === course.weeks.join(",") && previous.endSection + 1 === course.startSection) {
            previous.endSection = course.endSection;
        } else {
            merged.push(course);
        }
    }
    return merged;
}

// 个人课表
async function fetchApiCourses() {
    const xnm = document.querySelector("#xnm")?.value;
    const xqm = document.querySelector("#xqm")?.value;
    if (!xnm || !xqm) return null;
    const url = new URL("/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151", window.location.origin);
    try {
        const response = await fetch(url.href, {
            method: "POST",
            headers: { "content-type": "application/x-www-form-urlencoded;charset=UTF-8", "x-requested-with": "XMLHttpRequest" },
            body: `xnm=${encodeURIComponent(xnm)}&xqm=${encodeURIComponent(xqm)}&kzlx=ck&xsdm=&kclbdm=`,
            credentials: "include"
        });
        const data = await response.json();
        console.log("NTU course API response:", data);
        const rawCourses = data?.kbList || data?.datas?.kbList || data?.rows;
        if (!Array.isArray(rawCourses) || !rawCourses.length) return null;
        return mergeAndDistinctCourses(normalizeCourses(rawCourses));
    } catch (error) {
        console.warn("NTU course API request failed:", error);
        return null;
    }
}

// 班级课表
async function fetchClassApiCourses() {
    const pageMap = window.api?.data?.map || {};
    const readValue = (key) => pageMap[key] ?? document.querySelector(`#${key}`)?.value ?? "";
    const requestFields = [
        "xnm",
        "xqm",
        "njdm_id",
        "zyh_id",
        "bh_id",
        "tjkbzdm",
        "tjkbzxsdm",
        "zxszjjs"
    ];
    const body = new URLSearchParams();
    requestFields.forEach((key) => {
        const value = readValue(key);
        if (value !== "" && value !== null && typeof value !== "undefined") body.set(key, String(value));
    });
    if (!body.get("xnm") || !body.get("xqm") || !body.get("bh_id")) {
        console.warn("NTU class course API parameters are incomplete", Object.fromEntries(body));
        return null;
    }
    const url = new URL("/jwglxt/kbdy/bjkbdy_cxBjKb.html", window.location.origin);
    try {
        const response = await fetch(url.href, {
            method: "POST",
            headers: { "content-type": "application/x-www-form-urlencoded;charset=UTF-8", "x-requested-with": "XMLHttpRequest" },
            body,
            credentials: "include"
        });
        const data = await response.json();
        console.log("NTU class course API response:", data);
        const rawCourses = data?.kbList || data?.datas?.kbList || data?.rows;
        if (!Array.isArray(rawCourses) || !rawCourses.length) return null;
        return mergeAndDistinctCourses(normalizeCourses(rawCourses));
    } catch (error) {
        console.warn("NTU class course API request failed:", error);
        return null;
    }
}

// ============================================================================
// 适配钩子（v4.73.0）：成绩 / 空教室 / 学业情况
//
// 「上课」在成绩、空教室、学业三个用途的教务页里会先探测本文件是否声明了对应钩子，
// 有就调用，没有才回落到应用内置的通用脚本。钩子允许返回 Promise（下面三个都是
// async），结果经桥接回传，因此可以直接调本校接口，不必依赖页面把结果渲染出来。
//
// 以下接口地址与字段均为 2026-10-04 在南通大学正方 V9（tdjw.ntu.edu.cn）实测所得，
// 不是按 V9 惯例推测；实测记录见仓库 build_qa/ntu-portal-probe.md。
//
// ⚠️ 与 zhengfang/zhengfang.js 的关系（改一处务必核对另一处）：
//   通用平台脚本里有一份**逻辑等价的**钩子实现，供其余 1022 所正方学校使用
//   （它们没有专用脚本，走 zhengfang/）。本文件是**经真实登录会话实测验证**的那一份，
//   是正方钩子的基准件。两者解析逻辑应保持一致；本文件若修正了字段/口径，
//   需同步回 zhengfang.js，反之亦然。差异仅在两处，都是有意的：
//     1. 本文件用 async/await 与模板串，平台脚本用 ES5 风格以兼容更多页面环境；
//     2. 本文件可调用自身已有的 pickText() 等工具函数。
// ============================================================================

// 同源 POST，带上课 V9 需要的三个头；credentials 带上 JSESSIONID（教务登录态）
async function postForm(path, body) {
    const response = await fetch(new URL(path, window.location.origin).href, {
        method: "POST",
        headers: {
            "content-type": "application/x-www-form-urlencoded;charset=UTF-8",
            "x-requested-with": "XMLHttpRequest",
            accept: "application/json, text/javascript, */*; q=0.01"
        },
        body,
        credentials: "include"
    });
    return response.json();
}

// 读取当前页/地址栏里的学年学期（正方 V9 的页面一般都有 #xnm / #xqm 隐藏域）
function readSemesterParams() {
    const xnm = document.querySelector("#xnm")?.value;
    const xqm = document.querySelector("#xqm")?.value;
    return { xnm: xnm || "", xqm: xqm || "" };
}

/**
 * 成绩接口的绩点字段 → 数值（v4.75.0）。
 *
 * 空串 / `-` / 超出 0–5 的取值一律返回 null（**不猜、不填 0**）：
 * 0 会被 App 当成「挂科绩点」参与加权，反而污染汇总；null 才会让 App 回落到按分数换算。
 */
function gradePointOf(raw) {
    const text = String(raw ?? "").trim();
    if (!text) return null;
    const value = Number(text);
    return Number.isFinite(value) && value >= 0 && value <= 5 ? value : null;
}

/**
 * 成绩钩子：调本校成绩查询接口，回传**带真实学期与课程性质**的成绩列表。
 *
 * 关键点：
 * 1. 请求参数 xnm/xqm 是内部编码（xnm=2025、xqm=3 表示 2025-2026 学年第 1 学期），
 *    而返回里的 xnmmc/xqmmc 才是显示值。这里一律用 xnmmc + xqmmc 拼学期，
 *    避免把「第 1 学期」写成「第 3 学期」—— 这类错误用户很难发现。
 * 2. 分页走 `pageNo` / `pageSize`（实测有效），不要用 `queryModel.*`。
 */
async function shangkeScanGrades() {
    // xnm/xqm 留空 = 全部学期；成绩页默认只查当前学期，那样会漏掉历史成绩
    const data = await postForm(
        "/jwglxt/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005",
        "xnm=&xqm=&kcbjdm=&ksxzdm=&zymc=&kcmc=&pageNo=1&pageSize=500&showCount=500"
    );
    const items = Array.isArray(data?.items) ? data.items : [];
    const grades = [];
    for (const item of items) {
        const courseName = String(item.kcmc || "").trim();
        if (!courseName) continue;
        // 数字成绩优先（便于算平均分/绩点），等级制课程回落到显示成绩（优良中及格）
        const scoreText = String(pickText(item.bfzcj, item.cj) || "").trim();
        if (!scoreText) continue;
        const creditText = String(item.xf ?? "").trim();
        const credit = creditText === "" ? null : Number(creditText);
        const semesterYear = String(item.xnmmc || "").trim();
        const semesterTerm = String(item.xqmmc || "").trim();
        const semester = semesterYear && semesterTerm ? semesterYear + "-" + semesterTerm : "";
        grades.push({
            courseName,
            credit: Number.isFinite(credit) ? credit : null,
            scoreText,
            semester: semester || null,
            category: pickText(item.kcxzmc, item.kclbmc) || null,
            // 本校绩点（v4.75.0）：实测字段 `jd`，样例 "4.00"（四级制）。
            // 这是学校按本校规则算出的既成事实，App 的换算表不可能对上，必须整列带回；
            // 此前不回传，绩点在抓取时被丢弃 —— 「算出来的绩点与学校对不上」由此而来。
            gradePoint: gradePointOf(item.jd)
        });
    }
    console.log("NTU grade hook:", grades.length, "of", items.length);
    return grades;
}

/**
 * 空教室钩子：按校区 + 楼栋 + 座位数直接查本校接口，不要求用户先在页面上查询。
 *
 * 条件从当前页面控件读取（用户在教务页选好校区/楼栋再点「读取本页空教室」即可），
 * 读不到就用默认值：全部楼栋、不限座位数、当前学期。
 */
async function shangkeScanEmptyClassrooms() {
    const read = (selector) => document.querySelector(selector)?.value ?? "";
    const semester = readSemesterParams();
    const xnm = semester.xnm;
    const xqm = semester.xqm;
    const xqhId = read("#xqh_id") || "1";           // 1=啬园 2=启秀 5=启东
    const building = read("#lh");                    // 楼号，空=全部
    const categoryId = read("#cdlb_id");             // 场地类别，空=全部
    const minSeats = read("#qszws");
    const maxSeats = read("#jszws");
    const roomKeyword = read("#cdmc");
    const body = new URLSearchParams({
        xnm: xnm || "",
        xqm: xqm || "",
        dm: xnm && xqm ? xnm + "-" + xqm : "",
        xqh_id: xqhId,
        lh: building,
        cdlb_id: categoryId,
        cdejlb_id: "",
        cdmc: roomKeyword,
        qszws: minSeats,
        jszws: maxSeats,
        jyfs: "2",                                   // 2=按节次
        qssj: "", jssj: "", sjfw: "", qssd: "", jssd: "",
        _search: "false",
        nd: String(Date.now()),
        "queryModel.showCount": "500",
        "queryModel.currentPage": "1",
        "queryModel.sortName": "",
        "queryModel.sortOrder": "asc",
        time: "0"
    });
    const data = await postForm("/jwglxt/cdjy/cdjy_cxKxcdlb.html?doType=query", body);
    const items = Array.isArray(data?.items) ? data.items : [];
    const rooms = [];
    for (const item of items) {
        const room = String(item.cdmc || "").trim();
        if (!room) continue;
        const seats = Number(String(item.zws ?? "").trim());
        rooms.push({
            room,
            campus: String(item.xqmc || "").trim(),
            // 「无楼号」是正方占位文本，当作没有楼号，避免拼进展示串
            building: String(item.jxlmc || "").trim().replace(/^无楼号$/, ""),
            capacity: Number.isFinite(seats) ? seats : null,
            freeSlots: String(item.cdlbmc || "").trim()
        });
    }
    console.log("NTU empty-classroom hook:", rooms.length, "of", items.length);
    return rooms;
}

/**
 * 学业情况钩子：从本校学业情况页读培养方案各类别的**要求学分**。
 *
 * 只回传要求学分，「已获学分」交给本机成绩表现算 —— 学校页面的「获得学分」与本机
 * 成绩表的统计口径不一致（是否含重修/未出分课程），混用必然对不上。
 *
 * 类别 key 用「平台 / 必修|选修」两级：实测南通大学四个课程平台各有独立的必修、
 * 选修要求，只用「必修」会把 41+11+69+37 合并成一个数字，完全失去意义。
 *
 * **只回传叶子类别（必修/选修），不回传平台汇总行**：学业情况页会把每个平台的
 * 「要求学分」作为总需求逐项相加，若平台行与子行都回传，同一个平台会被算两遍
 * （实测 11 条全部回传会得到 310 学分，而实际培养方案总量约 174）。
 */
async function shangkeScanStudy() {
    const rows = document.querySelectorAll("ul.treeview p.title1");
    const requirements = [];
    let currentPlatform = "";
    for (const row of rows) {
        const text = String(row.textContent || "").replace(/\s+/g, "");
        const required = text.match(/要求学分[:：]([\d.]+)/);
        if (!required) continue;
        const credits = Number(required[1]);
        if (!Number.isFinite(credits)) continue;

        // 平台行：「XXX课程平台要求学分:…」；子行：「必修课程要求学分:…」
        const platformMatch = text.match(/^(.+?课程平台)要求学分/);
        if (platformMatch) {
            // 只用来给下面的叶子行定位父平台，本身不入结果（避免总量重复计算）
            currentPlatform = platformMatch[1];
            continue;
        }
        const categoryMatch = text.match(/^(必修课程|选修课程|任选课程|限选课程)要求学分/);
        if (categoryMatch && currentPlatform) {
            // 应修门数（v4.75.0）：子行末尾常带「共（N）门 通过（M）门」，N 即该类别应修门数。
            // 认不出就回传 null（页面只显示已出分门数），**不要猜**。
            // 「通过（M）门」刻意不回传：已修门数一律由本机成绩表现算，混两套口径必然打架。
            const totalMatch = text.match(/共\s*[（(]?\s*(\d+)\s*[）)]?\s*门/);
            const requiredCourses = totalMatch ? Number(totalMatch[1]) : null;
            requirements.push({
                category: currentPlatform + "/" + categoryMatch[1].replace("课程", ""),
                requiredCredits: credits,
                requiredCourses: Number.isFinite(requiredCourses) && requiredCourses > 0
                    ? requiredCourses
                    : null
            });
        }
    }
    console.log("NTU study hook: read", requirements.length, "requirements");
    // 页面还没展开（用户没打开学业情况页）时返回空数组，由应用侧提示
    return { requirements };
}

// 声明钩子：应用按这三个名字探测并调用
window.shangkeScanGrades = shangkeScanGrades;
window.shangkeScanEmptyClassrooms = shangkeScanEmptyClassrooms;
window.shangkeScanStudy = shangkeScanStudy;

async function runImport() {
    try {
        if (!await window.shangkeBridgePromise.showAlert("南通大学课表导入", "导入前请确保已登录南通大学教务系统。", "开始导入")) return;
        const formAction = document.querySelector("#ajaxForm")?.getAttribute("action") || "";
        const isPersonalPage = /xskbcx_cxXskbcxIndex/i.test(formAction) || /\/kbcx\/xskbcx/i.test(window.location.pathname);
        const pageCourses = isPersonalPage ? await fetchApiCourses() : await fetchClassApiCourses();
        if (pageCourses?.length) {
            const pageWeeks = pageCourses.flatMap((course) => course.weeks);
            const semesterStartDate = await fetchSemesterStartDate();
            if (!semesterStartDate) throw new Error("未获取到开学日期，无法判断令时");
            const startMonth = Number(semesterStartDate.slice(5, 7));
            if (!Number.isInteger(startMonth) || startMonth < 1 || startMonth > 12) throw new Error(`开学日期格式无效：${semesterStartDate}`);
            const timeSlots = startMonth <= 6 ? WINTER_TIME_SLOTS : SUMMER_TIME_SLOTS;
            const courseConfig = { semesterTotalWeeks: Math.max(19, ...pageWeeks), defaultClassDuration: 40, firstDayOfWeek: 1 };
            courseConfig.semesterStartDate = semesterStartDate;
            await window.shangkeBridgePromise.saveCourseConfig(JSON.stringify(courseConfig));
            await window.shangkeBridgePromise.savePresetTimeSlots(JSON.stringify(timeSlots));
            await window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(pageCourses));
            window.shangkeBridge.showToast(`导入成功：${pageCourses.length} 条课程安排`);
            window.shangkeBridge.notifyTaskCompletion();
            return;
        }
        window.shangkeBridge.showToast("当前页面未找到课表，请先打开个人或班级课表页面。");
    } catch (error) {
        console.error("NTU adapter error", error);
        window.shangkeBridge.showToast(`导入失败：${error.message}`);
    }
}

/**
 * 只在「课表页」自动启动课表导入。
 *
 * 这段守卫在 v4.73.0 起是必需的：成绩 / 空教室 / 学业三个用途也会把本文件注入页面
 * （为了声明下面的钩子），如果还是无条件 runImport()，用户一打开成绩页就会弹出
 * 「南通大学课表导入」对话框，并且因为当前页没有课表而失败。
 *
 * 判定依据：个人课表页在 /kbcx/ 下，班级课表页在 /kbdy/ 下；两者之外一律不自动启动。
 * 纯浏览器模式（考证查分等）用户手动点按钮时，导入仍按原逻辑走 beginImport()。
 */
function isTimetablePage() {
    const path = window.location.pathname;
    if (/\/kbcx\/xskbcx/i.test(path) || /\/kbdy\/bjkbdy/i.test(path)) return true;
    const formAction = document.querySelector("#ajaxForm")?.getAttribute("action") || "";
    return /xskbcx_cxXskbcxIndex|bjkbdy_cxBjkbdyIndex/i.test(formAction);
}

if (isTimetablePage()) {
    runImport();
} else {
    console.log("NTU adapter: not a timetable page, hooks only:", window.location.pathname);
}
})();
