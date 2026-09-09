// 广州中医药大学教务（广州乘方科技 · 新版）适配器 v2
// 系统特征：
//   登录页 https://jw.gzucm.edu.cn/ （layui 框架 + AES 加密密码 + 滑块/点选验证码）
//   登录 API POST /new/login （account + pwd(AES) + captchaToken + clientId）
//   课表页 /new/student/xsgrkb/main.page （FullCalendar 周视图渲染，嵌套 iframe）
//   课程块 .fc-time-grid-event 的 lay-tips 属性含完整结构化数据（星期/节次/周次/课程/教师/场地）
//   业务 API 前缀 /new/api/ ，统一返回 {code, data, message}（code>=0 成功，-401 未认证）
//
// 适配策略（按优先级）：
//   1. 验证码由前端弹窗完成，适配器提示用户在页面手动登录。
//   2. 用户登录后进入课表查询页，适配器优先解析当前页面 FullCalendar 已渲染的课程块（lay-tips）。
//   3. 若页面未渲染课表，尝试课表 API 端点（/xskb /kb /schedule /courseTable）。
//   4. API 也失败时，回退到解析传统 HTML table 课表。
//
// 桥接契约：window.shangkeImportEntry 由注入器自动调用（WebBridgeProtocol.JS_IMPORT_AUTOSTART）。

(function () {
    'use strict';

    var API_BASE = '/new/api';
    // 课表 API 优先级（从最具体到最通用）
    var SCHEDULE_ENDPOINTS = [
        '/xskb',        // 学生课表
        '/kb',           // 课表
        '/schedule',     // 日程/课表
        '/courseTable',  // 课程表
    ];
    // 学期 API 优先级
    var SEMESTER_ENDPOINTS = [
        '/xnxq',         // 学年学期
        '/semester',     // 学期
        '/term',         // 学期
    ];

    // ---------- 工具 ----------
    function toast(msg) {
        if (window.shangkeBridge && typeof window.shangkeBridge.showToast === 'function') {
            window.shangkeBridge.showToast(msg);
        }
    }

    function alert(title, msg, btn) {
        return window.shangkeBridgePromise.showAlert(title, msg, btn);
    }

    function select(title, items, defaultIdx) {
        return window.shangkeBridgePromise.showSingleSelection(title, JSON.stringify(items), defaultIdx);
    }

    function saveCourses(list) {
        return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(list));
    }

    // 同源 fetch（携带登录态 cookie）
    function apiGet(path, params) {
        var url = API_BASE + path;
        if (params) {
            var qs = Object.keys(params).map(function (k) {
                return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]);
            }).join('&');
            if (qs) url += '?' + qs;
        }
        return fetch(url, { method: 'GET', credentials: 'include' })
            .then(function (r) { return r.text(); })
            .then(function (t) {
                try { return JSON.parse(t); } catch (e) { return { code: -999, message: '返回不是JSON', raw: t }; }
            });
    }

    // 检测是否已登录：调用一个需认证的端点，code != -401 即视为已登录
    function checkLogin() {
        return apiGet('/schedule').then(function (j) {
            return j && j.code !== -401;
        }).catch(function () { return false; });
    }

    // ---------- 学期 ----------
    // 从学期 API 返回中提取 {labels, values, defaultIndex}
    // 兼容多种返回结构：数组 / {list:[]} / {data:[]} / {data:{list:[]}}
    function extractSemesters(resp) {
        var raw = null;
        if (Array.isArray(resp)) raw = resp;
        else if (resp && Array.isArray(resp.data)) raw = resp.data;
        else if (resp && resp.data && Array.isArray(resp.data.list)) raw = resp.data.list;
        else if (resp && Array.isArray(resp.list)) raw = resp.list;
        else if (resp && resp.data && resp.data.rows) raw = resp.data.rows;
        else if (resp && resp.rows) raw = resp.rows;

        if (!raw || !Array.isArray(raw) || raw.length === 0) return null;

        var labels = [], values = [], defaultIndex = 0;
        for (var i = 0; i < raw.length; i++) {
            var item = raw[i];
            var label = '', value = '';
            if (typeof item === 'string') {
                label = item; value = item;
            } else if (item) {
                // 常见字段名
                label = item.xnxqmc || item.xnxq || item.name || item.label || item.semesterName || item.termName || item.text || '';
                value = item.xnxqdm || item.xnxq || item.id || item.value || item.code || item.semesterId || item.termId || item.key || label;
                // 检测默认选中
                if (item.default === true || item.selected === true || item.isDefault === true || item.current === true) {
                    defaultIndex = i;
                }
            }
            if (label && value) {
                labels.push(String(label));
                values.push(String(value));
            }
        }
        if (labels.length === 0) return null;
        return { labels: labels, values: values, defaultIndex: Math.min(defaultIndex, labels.length - 1) };
    }

    function fetchSemesters() {
        var idx = 0;
        function tryNext() {
            if (idx >= SEMESTER_ENDPOINTS.length) return Promise.reject(new Error('未能获取学期列表'));
            var path = SEMESTER_ENDPOINTS[idx++];
            return apiGet(path).then(function (resp) {
                var sem = extractSemesters(resp);
                if (sem) return sem;
                return tryNext();
            }).catch(function () { return tryNext(); });
        }
        return tryNext();
    }

    // ---------- 课表解析 ----------
    // 从课表 API 返回中提取课程行数组
    function extractRows(resp) {
        if (Array.isArray(resp)) return resp;
        if (resp && Array.isArray(resp.data)) return resp.data;
        if (resp && resp.data && Array.isArray(resp.data.list)) return resp.data.list;
        if (resp && resp.data && Array.isArray(resp.data.rows)) return resp.data.rows;
        if (resp && resp.data && resp.data.kbList) return resp.data.kbList;
        if (resp && Array.isArray(resp.list)) return resp.list;
        if (resp && Array.isArray(resp.rows)) return resp.rows;
        if (resp && resp.kbList) return resp.kbList;
        // 嵌套：data 是对象，里面有某个数组字段
        if (resp && resp.data && typeof resp.data === 'object') {
            for (var k in resp.data) {
                if (Array.isArray(resp.data[k]) && resp.data[k].length > 0) {
                    // 检查数组元素是否像课程（有课程名/星期等字段）
                    var first = resp.data[k][0];
                    if (first && typeof first === 'object') {
                        return resp.data[k];
                    }
                }
            }
        }
        return null;
    }

    // 从单个课程行中提取字段（兼容多种命名）
    function pickField(row, candidates) {
        for (var i = 0; i < candidates.length; i++) {
            if (row[candidates[i]] !== undefined && row[candidates[i]] !== null && row[candidates[i]] !== '') {
                return row[candidates[i]];
            }
        }
        return '';
    }

    // 解析节次：支持 "0102" / "1-2" / {startSection,endSection} / {djj,cs}
    function parseSections(row) {
        // 显式字段
        var s = pickField(row, ['startSection', 'start_section', 'startJc', 'djj', 'ksjc', 'beginSection']);
        var e = pickField(row, ['endSection', 'end_section', 'endJc', 'jsjc', 'finishSection']);
        if (s) {
            var start = parseInt(s, 10);
            var end = e ? parseInt(e, 10) : start;
            // djj + cs 模式
            var cs = pickField(row, ['cs', 'sectionCount', 'count']);
            if (cs && !e) end = start + parseInt(cs, 10) - 1;
            if (!isNaN(start) && !isNaN(end) && start >= 1 && end >= start) {
                return { start: start, end: end };
            }
        }
        // jcdm / jcs 字符串模式："0102" / "0607080910"
        var jcdm = pickField(row, ['jcdm', 'jcs', 'jc', 'section', 'sections']);
        if (jcdm) {
            var str = String(jcdm).trim();
            // 纯数字连续2位一节
            if (/^\d+$/.test(str) && str.length >= 2 && str.length % 2 === 0) {
                var st = parseInt(str.slice(0, 2), 10);
                var en = parseInt(str.slice(str.length - 2), 10);
                if (!isNaN(st) && !isNaN(en) && st >= 1 && en <= 30 && st <= en) {
                    return { start: st, end: en };
                }
            }
            // 范围模式 "1-2" / "第1-2节"
            var m = str.match(/(\d+)\s*[-~－—至到]\s*(\d+)/);
            if (m) {
                return { start: parseInt(m[1], 10), end: parseInt(m[2], 10) };
            }
            // 单节 "1" / "第1节"
            var single = str.match(/(\d+)/);
            if (single) {
                var n = parseInt(single[1], 10);
                if (n >= 1 && n <= 30) return { start: n, end: n };
            }
        }
        return null;
    }

    // 解析周次：支持 [1,2,3] / "1-16周" / "1,3,5" / zcd 字段
    function parseWeeks(row) {
        var w = pickField(row, ['weeks', 'weekList', 'zcd', 'zc', 'week', 'weekDescription', 'weekText']);
        if (!w) return [];
        // 数组
        if (Array.isArray(w)) {
            return w.map(function (x) { return parseInt(x, 10); }).filter(function (x) { return !isNaN(x) && x >= 1 && x <= 30; });
        }
        var str = String(w);
        var weeks = {};
        var found = false;
        // 范围 "1-16"
        var rangeRe = /(\d+)\s*[-~－—至到]\s*(\d+)/g;
        var m;
        while ((m = rangeRe.exec(str)) !== null) {
            var st = parseInt(m[1], 10), en = parseInt(m[2], 10);
            if (st >= 1 && en <= 30 && st <= en) {
                for (var i = st; i <= en; i++) { weeks[i] = true; found = true; }
            }
        }
        // 单个数字 "3周" / "第5周"
        if (!found) {
            var singleRe = /(\d+)\s*周?/g;
            while ((m = singleRe.exec(str)) !== null) {
                var n = parseInt(m[1], 10);
                if (n >= 1 && n <= 30) { weeks[n] = true; found = true; }
            }
        }
        if (!found) return [];
        return Object.keys(weeks).map(function (k) { return parseInt(k, 10); }).sort(function (a, b) { return a - b; });
    }

    // 解析星期：1-7
    function parseDay(row) {
        var d = pickField(row, ['day', 'xqj', 'xq', 'weekday', 'weekDay', 'dayOfWeek']);
        if (d) {
            var n = parseInt(d, 10);
            if (!isNaN(n) && n >= 1 && n <= 7) return n;
        }
        return null;
    }

    // 行 → 课程对象
    function rowToCourse(row) {
        var name = String(pickField(row, ['kcmc', 'courseName', 'name', 'kch', 'course'])).trim();
        // 清理课程名
        name = name.replace(/【[^】]*】/g, '').replace(/[■◆▲●★]/g, '').replace(/\s+/g, ' ').trim();
        if (!name) return null;

        var teacher = String(pickField(row, ['xm', 'tmc', 'teaxms', 'teacher', 'teacherName', 'jsxm', 'js'])).trim() || '未安排';
        // 清理教师名（去掉括号内的职称）
        teacher = teacher.replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim();

        var position = String(pickField(row, ['cdmc', 'jxcdmc', 'position', 'location', 'room', 'classroom', 'jsmc'])).trim() || '待定';

        var day = parseDay(row);
        var sections = parseSections(row);
        var weeks = parseWeeks(row);

        if (!day || !sections || weeks.length === 0) return null;

        var remarkParts = [];
        var jxbmc = pickField(row, ['jxbmc', 'className', 'class']);
        if (jxbmc) remarkParts.push('教学班：' + jxbmc);
        var xf = pickField(row, ['xf', 'credit']);
        if (xf) remarkParts.push('学分：' + xf);
        var kcxzmc = pickField(row, ['kcxzmc', 'courseType', 'type']);
        if (kcxzmc) remarkParts.push('课程类型：' + kcxzmc);

        return {
            name: name,
            teacher: teacher,
            position: position,
            day: day,
            startSection: sections.start,
            endSection: sections.end,
            weeks: weeks,
            remark: remarkParts.join('；'),
            isLab: kcxzmc === '实验教学' || kcxzmc === '实验'
        };
    }

    // 从 API 响应中解析课程列表
    function parseCoursesFromApi(resp) {
        var rows = extractRows(resp);
        if (!rows || rows.length === 0) return [];
        var courses = [];
        for (var i = 0; i < rows.length; i++) {
            var c = rowToCourse(rows[i]);
            if (c) courses.push(c);
        }
        return courses;
    }

    // ---------- FullCalendar 课表解析（广中医实际渲染方式，优先使用）----------
    // 课程块 .fc-time-grid-event 的 lay-tips 属性包含完整结构化 HTML 表格
    function parseLayTips(html) {
        var parser = new DOMParser();
        var doc = parser.parseFromString(html, 'text/html');
        var rows = doc.querySelectorAll('tr');
        var data = {};
        var dataHtml = {};
        for (var i = 0; i < rows.length; i++) {
            var ths = rows[i].querySelectorAll('th');
            var tds = rows[i].querySelectorAll('td');
            for (var j = 0; j < ths.length && j < tds.length; j++) {
                var key = (ths[j].textContent || '').trim().replace(/[：:]$/, '');
                var val = (tds[j].textContent || '').trim();
                if (key) {
                    data[key] = val;
                    dataHtml[key] = tds[j].innerHTML;
                }
            }
        }

        // 课程名：[编号]课程名 → 去掉编号前缀
        var courseRaw = data['课程'] || '';
        var name = courseRaw.replace(/^\[[^\]]*\]/, '').trim();
        if (!name) return null;

        // 星期：直接是数字 1-7
        var day = parseInt(data['星期'], 10);
        if (isNaN(day) || day < 1 || day > 7) return null;

        // 节次：第01-04节 → start=1, end=4
        var sectionRaw = data['节次'] || '';
        var secMatch = sectionRaw.match(/(\d+)\s*[-~－—至到]\s*(\d+)/);
        var startSection, endSection;
        if (secMatch) {
            startSection = parseInt(secMatch[1], 10);
            endSection = parseInt(secMatch[2], 10);
        } else {
            var single = sectionRaw.match(/(\d+)/);
            if (!single) return null;
            startSection = endSection = parseInt(single[1], 10);
        }

        // 周次：3,3,3,6,6,6,13-14,... → 去重并展开范围
        var weekRaw = (data['上课周次'] || '').replace(/周$/, '');
        var weeks = [];
        var weekSeen = {};
        var parts = weekRaw.split(/[,，、]/);
        for (var p = 0; p < parts.length; p++) {
            var part = parts[p].trim();
            if (!part) continue;
            var rangeMatch = part.match(/(\d+)\s*[-~－—]\s*(\d+)/);
            if (rangeMatch) {
                var ws = parseInt(rangeMatch[1], 10);
                var we = parseInt(rangeMatch[2], 10);
                for (var w = ws; w <= we; w++) {
                    if (w >= 1 && w <= 30 && !weekSeen[w]) { weekSeen[w] = true; weeks.push(w); }
                }
            } else {
                var n = parseInt(part, 10);
                if (!isNaN(n) && n >= 1 && n <= 30 && !weekSeen[n]) { weekSeen[n] = true; weeks.push(n); }
            }
        }
        if (weeks.length === 0) return null;
        weeks.sort(function (a, b) { return a - b; });

        // 教师
        var teacher = (data['授课教师'] || '未安排').trim();

        // 地点：教学场地可能含多个场地（<br>分隔，每个场地后带[周次]），取第一个场地名
        var position = '待定';
        var locationHtml = dataHtml['教学场地'] || '';
        if (locationHtml) {
            // 按 <br> 分割，取第一段，去掉 HTML 标签和 [周次] 后缀
            var firstLoc = locationHtml.split(/<br\s*\/?>/i)[0]
                .replace(/<[^>]+>/g, '')
                .replace(/\s*\[[^\]]*\]\s*$/g, '')
                .trim();
            if (firstLoc) position = firstLoc;
        }

        // 类型：上课任务[实验教学] → 实验教学
        var typeRaw = data['类型'] || '';
        var typeMatch = typeRaw.match(/\[([^\]]+)\]/);
        var type = typeMatch ? typeMatch[1] : '';

        var remarkParts = [];
        if (type) remarkParts.push('课程类型：' + type);
        if (data['学时']) remarkParts.push('学时：' + data['学时']);
        if (data['上课班级']) remarkParts.push('班级：' + data['上课班级']);

        return {
            name: name,
            teacher: teacher,
            position: position,
            day: day,
            startSection: startSection,
            endSection: endSection,
            weeks: weeks,
            remark: remarkParts.join('；'),
            isLab: type === '实验教学' || type === '实验'
        };
    }

    // 遍历当前文档及所有 iframe（含嵌套），收集 FullCalendar 课程块
    function parseFromFullCalendar() {
        var docs = [document];
        function collectIframes(doc) {
            try {
                var iframes = doc.querySelectorAll('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    try {
                        var d = iframes[i].contentDocument || iframes[i].contentWindow.document;
                        if (d) { docs.push(d); collectIframes(d); }
                    } catch (e) { /* 跨域忽略 */ }
                }
            } catch (e) {}
        }
        collectIframes(document);

        var allCourses = [];
        var seen = {};
        for (var di = 0; di < docs.length; di++) {
            var events = docs[di].querySelectorAll('.fc-time-grid-event');
            for (var i = 0; i < events.length; i++) {
                var layTips = events[i].getAttribute('lay-tips');
                if (!layTips) continue;
                try {
                    var course = parseLayTips(layTips);
                    if (course) {
                        var key = course.name + '_' + course.day + '_' + course.startSection + '-' + course.endSection + '_' + course.weeks.join(',');
                        if (!seen[key]) { seen[key] = true; allCourses.push(course); }
                    }
                } catch (e) { /* 解析失败跳过 */ }
            }
        }
        return allCourses;
    }

    // ---------- 页面课表回退解析（传统 HTML table）----------
    function parseFromPage() {
        // 查找课表表格
        var tables = document.querySelectorAll('table');
        for (var t = 0; t < tables.length; t++) {
            var table = tables[t];
            // 判断是否是课表（包含"星期"或"节"或"周"）
            var text = table.innerText || table.textContent || '';
            if (text.indexOf('星期') === -1 && text.indexOf('节') === -1 && text.indexOf('周') === -1) continue;

            var courses = [];
            var rows = table.rows;
            // 第一行通常是星期表头
            var dayMap = {}; // 列索引 -> 星期(1-7)
            if (rows.length > 0) {
                var headerCells = rows[0].cells;
                for (var c = 0; c < headerCells.length; c++) {
                    var ht = (headerCells[c].innerText || '').trim();
                    var dm = ht.match(/星期([一二三四五六日天])/);
                    if (dm) {
                        var map = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7, '天': 7 };
                        dayMap[c] = map[dm[1]];
                    }
                }
            }
            // 遍历数据行
            for (var r = 1; r < rows.length; r++) {
                var cells = rows[r].cells;
                var sectionText = (cells[0] && (cells[0].innerText || '')) || '';
                var secMatch = sectionText.match(/(\d+)\s*[-~－—]\s*(\d+)/) || sectionText.match(/第?\s*(\d+)\s*节?/);
                var secStart = secMatch ? parseInt(secMatch[1], 10) : r;
                var secEnd = secMatch && secMatch[2] ? parseInt(secMatch[2], 10) : secStart;

                for (var cc = 1; cc < cells.length; cc++) {
                    var cell = cells[cc];
                    var ct = (cell.innerText || '').trim();
                    if (!ct || ct.length < 2) continue;
                    var day = dayMap[cc];
                    if (!day) continue;
                    // 解析单元格内容（课程名/教师/地点/周次）
                    var lines = ct.split(/\n/).map(function (s) { return s.trim(); }).filter(function (s) { return s; });
                    if (lines.length < 1) continue;
                    var cname = lines[0];
                    var cteacher = '未安排', cposition = '待定', cweeks = [];
                    for (var li = 1; li < lines.length; li++) {
                        var line = lines[li];
                        if (/周/.test(line) && /\d/.test(line)) {
                            // 周次行
                            var wm = line.match(/(\d+)\s*[-~]\s*(\d+)/);
                            if (wm) {
                                for (var wi = parseInt(wm[1], 10); wi <= parseInt(wm[2], 10); wi++) cweeks.push(wi);
                            } else {
                                var wsingle = line.match(/(\d+)/);
                                if (wsingle) cweeks.push(parseInt(wsingle[1], 10));
                            }
                        } else if (/教|师|副教授|教授|讲师/.test(line) && cteacher === '未安排') {
                            cteacher = line;
                        } else if (/楼|室|馆|教室|实验/.test(line) && cposition === '待定') {
                            cposition = line;
                        } else if (cteacher === '未安排' && line.length < 20) {
                            cteacher = line;
                        }
                    }
                    if (cweeks.length === 0) {
                        // 默认1-16周
                        for (var dwi = 1; dwi <= 16; dwi++) cweeks.push(dwi);
                    }
                    courses.push({
                        name: cname,
                        teacher: cteacher,
                        position: cposition,
                        day: day,
                        startSection: secStart,
                        endSection: secEnd,
                        weeks: cweeks
                    });
                }
            }
            if (courses.length > 0) return courses;
        }
        return [];
    }

    // 从课表页面的学期下拉框提取学期列表（兜底，API 不可用时使用）
    function extractSemestersFromPage() {
        var docs = [document];
        function collectIframes(doc) {
            try {
                var iframes = doc.querySelectorAll('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    try {
                        var d = iframes[i].contentDocument || iframes[i].contentWindow.document;
                        if (d) { docs.push(d); collectIframes(d); }
                    } catch (e) {}
                }
            } catch (e) {}
        }
        collectIframes(document);

        for (var di = 0; di < docs.length; di++) {
            // 查找学期 select 下拉框
            var selects = docs[di].querySelectorAll('select');
            for (var s = 0; s < selects.length; s++) {
                var sel = selects[s];
                var selText = (sel.textContent || '').trim();
                if (selText.indexOf('20') !== -1 && sel.options && sel.options.length > 3) {
                    var labels = [], values = [], defaultIndex = 0;
                    for (var i = 0; i < sel.options.length; i++) {
                        var opt = sel.options[i];
                        var label = (opt.textContent || '').trim();
                        var value = (opt.value || '').trim();
                        if (label && value) {
                            labels.push(label);
                            values.push(value);
                            if (opt.selected) defaultIndex = labels.length - 1;
                        }
                    }
                    if (labels.length > 0) {
                        return { labels: labels, values: values, defaultIndex: defaultIndex };
                    }
                }
            }
        }
        return null;
    }

    // ---------- 主流程 ----------
    function runImport() {
        var bridge = window.shangkeBridgePromise;

        // 1. 检测登录状态
        toast('正在检测登录状态...');
        return checkLogin().then(function (loggedIn) {
            if (!loggedIn) {
                return alert(
                    '广州中医药大学教务导入',
                    '请先在上方页面完成登录（学号 + 密码 + 验证码）。\n\n' +
                    '登录成功后，请进入「信息查询 → 课表查询」页面，\n' +
                    '确认课表已显示后，回到本页面点击「确定」开始导入。\n\n' +
                    '提示：如遇验证码无法显示，请点击验证码图片刷新。',
                    '已登录并打开课表页'
                ).then(function (ok) {
                    if (!ok) { toast('导入已取消'); return null; }
                    return checkLogin().then(function (ok2) {
                        if (!ok2) throw new Error('仍未检测到登录状态，请确认已在页面中完成登录');
                        return true;
                    });
                });
            }
            return true;
        }).then(function (ready) {
            if (!ready) return null;

            // 2. 优先直接解析当前页面已渲染的 FullCalendar 课表（不需要学期参数）
            toast('正在解析课表...');
            var fcCourses = parseFromFullCalendar();
            if (fcCourses.length > 0) {
                return fcCourses;
            }

            // 3. 页面未渲染课表，尝试获取学期列表后走 API
            //    优先从页面下拉框提取，API 作为兜底
            toast('当前页面未检测到课表，正在获取学期列表...');
            var pageSem = extractSemestersFromPage();
            var semPromise;
            if (pageSem) {
                semPromise = Promise.resolve(pageSem);
            } else {
                semPromise = fetchSemesters();
            }

            return semPromise.then(function (sem) {
                if (!sem) throw new Error('未能获取学期列表。请确认已进入「信息查询 → 课表查询」页面，或手动选择学期后重试。');
                return select('选择学期', sem.labels, sem.defaultIndex).then(function (idx) {
                    if (idx === null || idx < 0 || idx >= sem.values.length) {
                        toast('导入已取消');
                        return null;
                    }
                    return { label: sem.labels[idx], value: sem.values[idx] };
                });
            });
        }).then(function (result) {
            // result 可能是课程数组（FullCalendar 直接解析）或学期对象（API 路径）
            if (!result) return null;

            // 如果是课程数组，直接返回
            if (Array.isArray(result)) {
                return result;
            }

            // 如果是学期对象，走 API 路径
            var semester = result;
            toast('正在获取 ' + semester.label + ' 的课表...');

            var params = {};
            params.xnxq = semester.value;
            params.xnxqdm = semester.value;
            params.semester = semester.value;
            params.term = semester.value;

            var epIdx = 0;
            function tryEndpoint() {
                if (epIdx >= SCHEDULE_ENDPOINTS.length) return Promise.resolve(null);
                var path = SCHEDULE_ENDPOINTS[epIdx++];
                return apiGet(path, params).then(function (resp) {
                    var courses = parseCoursesFromApi(resp);
                    if (courses.length > 0) return courses;
                    return apiGet(path).then(function (resp2) {
                        var courses2 = parseCoursesFromApi(resp2);
                        if (courses2.length > 0) return courses2;
                        return tryEndpoint();
                    });
                }).catch(function () { return tryEndpoint(); });
            }

            return tryEndpoint().then(function (courses) {
                if (courses && courses.length > 0) return courses;
                // 最后回退：解析当前页面传统 table 课表
                toast('API 未返回数据，尝试解析页面课表...');
                var pageCourses = parseFromPage();
                if (pageCourses.length > 0) return pageCourses;
                throw new Error('未能获取课表数据。请确认已进入「信息查询 → 课表查询」页面并选择了学期，或该学期暂无课程。');
            });
        }).then(function (courses) {
            if (!courses || courses.length === 0) return null;

            // 保存导入
            return saveCourses(courses).then(function () {
                return alert(
                    '导入完成',
                    '成功导入 ' + courses.length + ' 个课程项。\n\n' +
                    '说明：本次按节次导入课程，如上下课时间与学校作息不符，\n' +
                    '请到「设置 → 自定义时间段」按学校作息调整。',
                    '完成'
                );
            }).then(function () {
                window.shangkeBridge.notifyTaskCompletion();
            });
        }).catch(function (error) {
            return alert('导入失败', (error && error.message) || String(error), '确定');
        });
    }

    // 注入器（JS_IMPORT_AUTOSTART）会调用 window.shangkeImportEntry
    if (typeof window !== 'undefined') {
        window.shangkeImportEntry = runImport;
    }
})();
