// 汕头大学教务（正方教务系统 jwxsd + CAS 统一身份认证 sso.stu.edu.cn）适配器 v4
//
// ===== 站点事实（2026-09-10 实测）=====
//   入口 https://jw.stu.edu.cn/jsxsd/framework/xsMainV.htmlx
//     302 → http://jw.stu.edu.cn（该页 200 后用 JS 跳转）→ CAS https://sso.stu.edu.cn/login?service=…
//     CAS 表单字段：username / password / lt / execution / _eventId=submit（无验证码、无前端加密）
//     登录成功 → http://jw.stu.edu.cn/?ticket=ST-… → /jsxsd/xk/LoginToXk?method=jwxt&ticket1=…
//       → https://jw.stu.edu.cn/jsxsd/framework/xsMainV.htmlx（教务门户）
//   课表页 /jsxsd/xskb/xskb_list.do（<title>学期理论课表</title>）
//     学期切换：GET /jsxsd/xskb/xskb_list.do?xnxq01id=<学期ID> 可直接取到该学期课表（实测可用）
//     学期列表 = select#xnxq01id（如 2026-2027-1 / 2026年秋季学期）
//
// ===== 必须强制「电脑版」=====
//   门户 xsMainV.htmlx 自带 <meta name="viewport" content="width=device-width">，但页面是
//   固定宽度的桌面布局（左侧 edu-sideMenu 侧栏 + 内容区 iframe）。手机 UA 下该布局被压成一条，
//   菜单点不开、登录后进不去课表。因此本适配要求以桌面 UA + 1280px 视口进入
//   （由 AdapterSelectionScreen 的 FORCE_DESKTOP_MODE_SCHOOL_IDS 对 u_15f498f5 强制开启）。
//   解析本身只依赖 DOM 结构，与 UA 无关；桌面模式解决的是「人能不能点到课表页」。
//
// ===== 课表表格真实结构（实测；这是 v1/v2 导入失败的真因）=====
//   table#timetable = 1 个表头行 + 5 个「大节」行 + 1 个「备注」行；列 1..7 = 星期一..星期日
//   表头行与行首「大节」格用的是 <th>，课程格才是 <td> —— 必须用 row.cells（按文档顺序含 th+td），
//     用 getElementsByTagName('td') 会少掉大节标签列，导致**星期整体错位一列**。
//   行首单元格文字形如「第二大节 (03,04,05小节)」：大节→小节的映射必须从这行文字里取。
//     该校为 01,02 / 03,04,05 / 06,07 / 08,09,10 / 11,12,13，并非「每大节固定 2 小节」；
//     按 (行号-1)*2+1 推断会把所有节次算错。
//   课程单元格 = 多个同 GUID 的 div：kbcontent1（简版）/ kbcontent（详版，含教师、教室）等；
//     空单元格同样有占位 div（内容 &nbsp;），必须按「课程名是否为空」过滤，不能按 class 判断。
//   字段载体为 <font title="…">：无 title = 课程名；教师 / 周次(节次) / 教学楼 / 教室 / 通知单编号 / 班级 / 备注
//   周次(节次) 形如 "3-18(周)[01-02节]"、"3-4,6-11(周)[06-07节]"，节次可为三段 "[03-04-05节]"
//
// ===== v4 相对 v3 的修复 =====
//   1. 【核心】不再用 location.href 跳转课表页。v3 在门户首页点导入时会先跳转再 sleep 等待，
//      但页面一导航当前 JS 上下文即被销毁，await 之后的代码永远不会执行 —— 表现为
//      「点了导入、页面跳到课表、然后什么都不发生」，用户必须再点一次。
//      v4 改为**同源 fetch** 课表页（/jsxsd/xskb/xskb_list.do 与门户同源、自动带上会话 Cookie），
//      用 DOMParser 解析后直接导入，门户首页一次点击即可完成导入。
//   2. 位置不变：仍在门户首页 / 课表页 / 课表在 iframe 内三种情况下都能导入。
//   3. 新增 window.shangkeNavigateToTimetable()，供底部栏「一键导航到课表」精确跳转，
//      不再依赖 DOM 文本模糊探测（门户菜单里「课表」字样较多，易命中错误入口）。
//   4. 节次、周次、教室、学期选择逻辑与 v3 一致（v3 已实测正确，未改动解析规则）。
//
// ===== 桥接契约 =====
//   window.shangkeImportEntry 由注入器（WebBridgeProtocol.JS_IMPORT_AUTOSTART）自动调用。
//   课程 JSON 字段：name / teacher / position / day / startSection / endSection / weeks
//   （position 为 Kotlin 侧 ImportCourseJsonModel 必填字段，不可写成 room）

(function () {
    'use strict';

    var KB_PATH = '/jsxsd/xskb/xskb_list.do';
    var WEEKDAY = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];

    // ---------- 桥接工具 ----------
    function toast(msg) {
        try {
            if (window.shangkeBridge && typeof window.shangkeBridge.showToast === 'function') {
                window.shangkeBridge.showToast(msg);
            }
        } catch (e) { }
    }

    function alert(title, msg, btn) {
        return window.shangkeBridgePromise.showAlert(title, msg, btn || '确定');
    }

    function select(title, items, defaultIdx) {
        return window.shangkeBridgePromise.showSingleSelection(title, JSON.stringify(items), defaultIdx);
    }

    function sleep(ms) {
        return new Promise(function (resolve) { setTimeout(resolve, ms); });
    }

    function clean(text) {
        if (!text) return '';
        return String(text).replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
    }

    function range(count) {
        var out = [];
        for (var i = 1; i <= count; i++) out.push(i);
        return out;
    }

    // ---------- 文档收集：当前文档 + 所有同源 iframe ----------
    function collectDocuments() {
        var docs = [];
        var seen = [];
        seen.push(document);
        docs.push(document);

        function collect(doc) {
            var iframes;
            try { iframes = doc.querySelectorAll('iframe, frame'); } catch (e) { return; }
            for (var i = 0; i < iframes.length; i++) {
                try {
                    var d = iframes[i].contentDocument || (iframes[i].contentWindow && iframes[i].contentWindow.document);
                    if (d && seen.indexOf(d) === -1) {
                        seen.push(d);
                        docs.push(d);
                        collect(d);
                    }
                } catch (e) { /* 跨域忽略 */ }
            }
        }
        collect(document);
        return docs;
    }

    // ---------- 判断是否停在 CAS 登录页 ----------
    function isLoginPage() {
        if (location.host.indexOf('sso.stu.edu.cn') !== -1) return true;
        if (document.getElementById('fm1') && document.getElementById('username')) return true;
        var docs = collectDocuments();
        for (var i = 0; i < docs.length; i++) {
            if (docs[i].getElementById && docs[i].getElementById('fm1')) return true;
        }
        return false;
    }

    // ---------- 定位课表表格（在给定文档集合内查找）----------
    function findTimetableIn(docs) {
        for (var i = 0; i < docs.length; i++) {
            var byId = docs[i].getElementById ? docs[i].getElementById('timetable') : null;
            if (byId) return { doc: docs[i], table: byId };
        }

        for (var j = 0; j < docs.length; j++) {
            var tables = docs[j].getElementsByTagName ? docs[j].getElementsByTagName('table') : [];
            for (var t = 0; t < tables.length; t++) {
                var text = tables[t].textContent || '';
                if (text.indexOf('星期一') !== -1 &&
                    (text.indexOf('大节') !== -1 || text.indexOf('小节') !== -1)) {
                    return { doc: docs[j], table: tables[t] };
                }
            }
        }
        return null;
    }

    function findTimetable() {
        return findTimetableIn(collectDocuments());
    }

    // ---------- 同源抓取课表页 ----------
    // 课表页与门户同源，fetch 会自动携带会话 Cookie；credentials 显式声明以兼容旧内核。
    function fetchDoc(url) {
        return fetch(url, { credentials: 'include' })
            .then(function (resp) { return resp.text(); })
            .then(function (html) {
                return new DOMParser().parseFromString(html, 'text/html');
            });
    }

    // ---------- 列索引 → 星期几 ----------
    // 表头行给出「星期一…星期日」，据此建映射；解析不出时退回「第 n 列 = 星期 n」
    function buildDayMap(headerCells) {
        var map = [];
        for (var i = 0; i < headerCells.length; i++) {
            var text = clean(headerCells[i].textContent);
            var day = 0;
            for (var w = 0; w < WEEKDAY.length; w++) {
                if (text.indexOf(WEEKDAY[w]) !== -1) { day = w + 1; break; }
            }
            map.push(day > 0 ? day : i);
        }
        return map;
    }

    // ---------- 「第二大节 (03,04,05小节)」→ { start:3, end:5 } ----------
    function parseSectionRangeFromLabel(label) {
        var text = clean(label);
        if (!text) return null;

        var bracket = text.match(/[（(]([^）)]*)[）)]/);
        var nums = [];
        if (bracket) nums = bracket[1].match(/\d+/g) || [];
        if (!nums.length) {
            var after = text.match(/(\d+(?:\s*[,，、\-~－—]\s*\d+)*)\s*小节/);
            if (after) nums = after[1].match(/\d+/g) || [];
        }
        if (!nums.length) return null;

        var values = [];
        for (var i = 0; i < nums.length; i++) {
            var n = parseInt(nums[i], 10);
            if (!isNaN(n) && n > 0 && n <= 30) values.push(n);
        }
        if (!values.length) return null;
        values.sort(function (a, b) { return a - b; });
        return { start: values[0], end: values[values.length - 1] };
    }

    function isRemarkRow(label) {
        return clean(label).indexOf('备注') === 0;
    }

    // ---------- 周次解析 ----------
    // "3-18" / "3-4,6-11" / "1"  →  周次数组；flag 为 '单' / '双' 时按单双周过滤
    function parseWeeks(numText, flag) {
        var weeks = [];
        if (!numText) return weeks;

        var parts = String(numText).split(/[,，、]/);
        for (var p = 0; p < parts.length; p++) {
            var part = clean(parts[p]);
            if (!part) continue;

            var rangeMatch = part.match(/(\d+)\s*[-~－—]+\s*(\d+)/);
            if (rangeMatch) {
                var start = parseInt(rangeMatch[1], 10);
                var end = parseInt(rangeMatch[2], 10);
                if (isNaN(start) || start < 1) continue;
                if (isNaN(end) || end < start) end = start;
                if (end > 40) end = 40;
                for (var w = start; w <= end; w++) weeks.push(w);
            } else {
                var single = part.match(/\d+/);
                if (single) {
                    var n = parseInt(single[0], 10);
                    if (n >= 1 && n <= 40) weeks.push(n);
                }
            }
        }

        if (flag === '单') weeks = weeks.filter(function (w) { return w % 2 === 1; });
        if (flag === '双') weeks = weeks.filter(function (w) { return w % 2 === 0; });

        var unique = [];
        for (var i = 0; i < weeks.length; i++) {
            if (unique.indexOf(weeks[i]) === -1) unique.push(weeks[i]);
        }
        return unique.sort(function (a, b) { return a - b; });
    }

    function parseSectionsFromText(text) {
        if (!text) return null;
        var nums = String(text).match(/\d+/g);
        if (!nums || !nums.length) return null;
        var values = [];
        for (var i = 0; i < nums.length; i++) {
            var n = parseInt(nums[i], 10);
            if (!isNaN(n) && n > 0 && n <= 30) values.push(n);
        }
        if (!values.length) return null;
        values.sort(function (a, b) { return a - b; });
        return { start: values[0], end: values[values.length - 1] };
    }

    // ---------- 拆分「周次(节次)」字符串 ----------
    // 形如 "3-18(周)[01-02节]"、"3-4,6-11(周)[06-07节]"、"1-16(单周)[03-04-05节]"
    // 可能含多段（调课），返回 [{ weeks, start, end }]
    function splitWeekSections(raw, fallbackWeeks, fallbackRange) {
        var result = [];
        var text = clean(raw);
        if (!text) {
            if (fallbackWeeks.length && fallbackRange) {
                result.push({ weeks: fallbackWeeks.slice(), start: fallbackRange.start, end: fallbackRange.end });
            }
            return result;
        }

        var re = /([0-9][0-9\s,，、\-~－—]*)\s*[（(]([^）)]*)[）)]\s*(?:\[([^\]]*)\])?/g;
        var m;
        while ((m = re.exec(text)) !== null) {
            var flagText = m[2] || '';
            var flag = flagText.indexOf('单') !== -1 ? '单' : (flagText.indexOf('双') !== -1 ? '双' : '');
            var weeks = parseWeeks(m[1], flag);
            if (!weeks.length) continue;

            var sec = parseSectionsFromText(m[3] || '') || fallbackRange;
            if (!sec) continue;
            result.push({ weeks: weeks, start: sec.start, end: sec.end });
        }

        if (!result.length) {
            // 没有「(周)」标记：尝试把整串当周次，节次用行内小节范围兜底
            var secOnly = parseSectionsFromText((text.match(/\[([^\]]*)\]/) || [])[1] || '');
            var weeksOnly = parseWeeks(text.replace(/\[[^\]]*\]/g, ''), '');
            if (!weeksOnly.length) weeksOnly = fallbackWeeks.slice();
            var r = secOnly || fallbackRange;
            if (weeksOnly.length && r) {
                result.push({ weeks: weeksOnly, start: r.start, end: r.end });
            }
        }
        return result;
    }

    // ---------- 单元格内的课程块归组 ----------
    // 同 GUID 的多个 div 属于同一门课；不同 GUID 表示同一格里的不同课程
    function groupCourseDivs(cell) {
        var divs = cell.querySelectorAll('div.kbcontent, div.kbcontent1');
        var groups = [];
        var indexByKey = {};

        for (var i = 0; i < divs.length; i++) {
            var div = divs[i];
            var id = div.getAttribute('id') || '';
            var key = id.replace(/-\d+-\d+$/, '') || ('__idx_' + i);
            if (indexByKey[key] === undefined) {
                indexByKey[key] = groups.length;
                groups.push([]);
            }
            groups[indexByKey[key]].push(div);
        }
        return groups;
    }

    function pickDetailDiv(divs) {
        var best = divs[0];
        var bestScore = -1;
        for (var i = 0; i < divs.length; i++) {
            var d = divs[i];
            var score = 0;
            var fonts = d.getElementsByTagName('font');
            for (var f = 0; f < fonts.length; f++) {
                var title = fonts[f].getAttribute('title') || '';
                if (title === '教师') score += 3;
                else if (title === '教室') score += 2;
                else if (title.indexOf('周次') !== -1) score += 1;
            }
            if (score > bestScore) { bestScore = score; best = d; }
        }
        return best;
    }

    // ---------- 解析单个课程块 ----------
    // 教室优先取 <font title="教室">（精确到教室号），没有时才退回 <font title="教学楼">（只到楼）
    function parseCourseDiv(div) {
        var info = { name: '', teacher: '', position: '', room: '', building: '', weekSecRaw: '' };
        var fonts = div.getElementsByTagName('font');

        for (var i = 0; i < fonts.length; i++) {
            var font = fonts[i];
            var title = font.getAttribute('title') || '';
            var text = clean(font.textContent);
            if (!text) continue;

            if (title === '教师') {
                info.teacher = info.teacher ? (info.teacher + '/' + text) : text;
            } else if (title.indexOf('周次') !== -1) {
                if (!info.weekSecRaw) info.weekSecRaw = text;
            } else if (title === '教室') {
                if (!info.room) info.room = text;
            } else if (title === '教学楼') {
                if (!info.building) info.building = text.replace(/[【】]/g, '');
            } else if (!title) {
                // 无 title 的 font：课程名（备注里的「(讲课:32)」等不会出现在首位）
                if (!info.name) info.name = text;
            }
        }
        info.position = info.room || info.building;
        return info;
    }

    // ---------- 解析一张课表 ----------
    // 注意：该校课表的「表头行」「大节标签列」用的是 <th>，课程格才是 <td>，
    // 必须用 row.cells（按文档顺序返回 th+td）而不是 getElementsByTagName('td')，
    // 否则会少掉大节标签列、星期整体错位一列。
    function parseTimetable(doc, table, fallbackWeeks) {
        var courses = [];
        var rows = table.rows;
        if (!rows || rows.length < 2) return courses;

        var dayMap = buildDayMap(rows[0].cells);

        for (var r = 1; r < rows.length; r++) {
            var cells = rows[r].cells;
            if (!cells || cells.length < 2) continue;

            var rowLabel = cells[0].textContent || '';
            if (isRemarkRow(rowLabel)) continue;
            var rowRange = parseSectionRangeFromLabel(rowLabel);

            for (var c = 1; c < cells.length; c++) {
                var day = dayMap[c] || c;
                if (day < 1 || day > 7) continue;

                var groups = groupCourseDivs(cells[c]);
                for (var g = 0; g < groups.length; g++) {
                    var detail = pickDetailDiv(groups[g]);
                    var info = parseCourseDiv(detail);
                    if (!info.name) continue;

                    // 同一 GUID 的其它 div 是「简版 / 整大节占位」等显示档，不是额外的调课段：
                    // 时间串一律以详版为准，仅在详版缺时间串时借用同组其它档，避免同一门课被重复导入。
                    if (!info.weekSecRaw) {
                        for (var d2 = 0; d2 < groups[g].length; d2++) {
                            if (groups[g][d2] === detail) continue;
                            var alt = parseCourseDiv(groups[g][d2]);
                            if (alt.name && alt.weekSecRaw) { info.weekSecRaw = alt.weekSecRaw; break; }
                        }
                    }

                    var segments = splitWeekSections(info.weekSecRaw, fallbackWeeks, rowRange);

                    for (var s = 0; s < segments.length; s++) {
                        if (!segments[s].weeks.length) continue;
                        courses.push({
                            name: info.name,
                            teacher: info.teacher || '未安排',
                            position: info.position || '',
                            day: day,
                            startSection: segments[s].start,
                            endSection: segments[s].end,
                            weeks: segments[s].weeks
                        });
                    }
                }
            }
        }
        return courses;
    }

    // ---------- 去重（课程名 + 星期 + 节次 + 周次）----------
    function dedupe(courses) {
        var result = [];
        var seen = {};
        for (var i = 0; i < courses.length; i++) {
            var c = courses[i];
            var key = [c.name, c.day, c.startSection, c.endSection, c.weeks.join(',')].join('|');
            if (seen[key]) continue;
            seen[key] = true;
            result.push(c);
        }
        result.sort(function (a, b) {
            if (a.day !== b.day) return a.day - b.day;
            if (a.startSection !== b.startSection) return a.startSection - b.startSection;
            if (a.name !== b.name) return a.name < b.name ? -1 : 1;
            return 0;
        });
        return result;
    }

    // ---------- 学期 ----------
    function readSemesters(doc) {
        var result = { list: [], currentIndex: -1 };
        if (!doc || !doc.getElementById) return result;
        var sel = doc.getElementById('xnxq01id');
        if (!sel) return result;

        var options = sel.getElementsByTagName('option');
        for (var i = 0; i < options.length; i++) {
            var value = options[i].getAttribute('value') || '';
            var label = clean(options[i].textContent) || value;
            if (!value) continue;
            result.list.push({ value: value, label: label });
            if (options[i].selected) result.currentIndex = result.list.length - 1;
        }
        if (result.currentIndex < 0 && result.list.length) result.currentIndex = 0;
        return result;
    }

    // 学期周数上限：select#zc 的「第N周」选项数，用于周次兜底
    function readSemesterWeekCount(doc) {
        var count = 0;
        try {
            var sel = doc && doc.getElementById ? doc.getElementById('zc') : null;
            if (sel) {
                var options = sel.getElementsByTagName('option');
                for (var i = 0; i < options.length; i++) {
                    var m = clean(options[i].textContent).match(/第\s*(\d+)\s*周/);
                    if (m) count = Math.max(count, parseInt(m[1], 10));
                }
            }
        } catch (e) { }
        return count;
    }

    function totalWeeksOf(doc) {
        var n = readSemesterWeekCount(doc);
        return n > 0 && n <= 40 ? n : 20;
    }

    // ---------- 诊断 ----------
    function diagnose() {
        var docs = collectDocuments();
        var info = {
            url: location.href,
            docCount: docs.length,
            tableCount: 0,
            weekHeaderTables: 0,
            courseDivs: 0,
            namedDivs: 0,
            semesterOptions: 0
        };

        for (var i = 0; i < docs.length; i++) {
            var tables = docs[i].getElementsByTagName ? docs[i].getElementsByTagName('table') : [];
            info.tableCount += tables.length;
            for (var t = 0; t < tables.length; t++) {
                if ((tables[t].textContent || '').indexOf('星期一') !== -1) info.weekHeaderTables++;
            }
            var divs = docs[i].querySelectorAll ? docs[i].querySelectorAll('div.kbcontent, div.kbcontent1') : [];
            info.courseDivs += divs.length;
            for (var d = 0; d < divs.length; d++) {
                var fonts = divs[d].getElementsByTagName('font');
                for (var f = 0; f < fonts.length; f++) {
                    if (!fonts[f].getAttribute('title') && clean(fonts[f].textContent)) { info.namedDivs++; break; }
                }
            }
            if (docs[i].getElementById && docs[i].getElementById('xnxq01id')) {
                var sel = docs[i].getElementById('xnxq01id');
                info.semesterOptions = Math.max(info.semesterOptions, sel.getElementsByTagName('option').length);
            }
        }
        return info;
    }

    // ---------- 取得课表来源：优先当前页面，否则同源抓取课表页 ----------
    // 关键：不要用 location.href 跳转 —— 一旦导航，当前 JS 上下文立即销毁，
    // 后续 await / then 不会执行，导入就此静默中断（v3 的缺陷）。
    function resolveSource() {
        // 1. 当前文档（含同源 iframe）已有课表
        var live = findTimetable();
        if (live) return Promise.resolve({ doc: live.doc, table: live.table, fromPage: true });

        // 2. 不在 jw 主机上（例如仍停在 CAS 或其它域名）→ 无法同源抓取
        if (location.host.indexOf('jw.stu.edu.cn') === -1) return Promise.resolve(null);

        // 3. 同源抓取课表页（门户首页点导入即走这条路径，一次点击完成）
        toast('正在读取课表…');
        return fetchDoc(KB_PATH)
            .then(function (doc) {
                var found = findTimetableIn([doc]);
                if (found) return { doc: found.doc, table: found.table, fromPage: false };
                return null;
            })
            .catch(function () { return null; });
    }

    // ---------- 主流程 ----------
    function runImport() {
        var semesterLabel = '';

        function fail(title, message) {
            return alert(title, message, '确定').then(function () { return null; });
        }

        return Promise.resolve()
            .then(function () {
                // 0. 还停在登录页 → 先让用户登录
                if (isLoginPage()) {
                    return fail(
                        '请先登录',
                        '当前仍停留在统一身份认证（CAS）登录页。\n\n' +
                        '请先在页面中输入学号与密码完成登录，\n' +
                        '登录后会自动回到教务系统，再点击「执行导入」即可。'
                    );
                }

                // 1. 取课表来源（当前页面 或 同源抓取课表页）
                return resolveSource().then(function (src) {
                    if (!src) {
                        return fail(
                            '未找到课表',
                            '没有读到课表数据。\n\n' +
                            '请确认：\n' +
                            '1. 已登录汕头大学教务系统（jw.stu.edu.cn）\n' +
                            '2. 当前学期已选课\n\n' +
                            '也可以先点下方「一键导航到课表」，进入课表页后再点「执行导入」。'
                        );
                    }
                    return src;
                });
            })
            .then(function (src) {
                if (!src) return null;

                var weeks = range(totalWeeksOf(src.doc));
                var currentCourses = dedupe(parseTimetable(src.doc, src.table, weeks));
                var sems = readSemesters(src.doc);

                if (sems.list.length <= 1) {
                    if (!currentCourses.length) {
                        return fail(
                            '本学期没有课程',
                            '在课表中没有解析到课程。\n\n' +
                            '请确认：\n' +
                            '1. 已进入「培养管理 → 我的课表 → 学期理论课表」\n' +
                            '2. 已切换到有课的学期\n' +
                            '3. 课表已完整显示（能看到课程方块）'
                        );
                    }
                    semesterLabel = sems.list.length ? sems.list[0].label : '';
                    return { courses: currentCourses, semester: sems.list.length ? sems.list[0] : null };
                }

                var labels = [];
                for (var i = 0; i < sems.list.length; i++) {
                    labels.push(sems.list[i].label + (i === sems.currentIndex ? '（当前）' : ''));
                }

                return select('选择要导入的学期', labels, sems.currentIndex < 0 ? 0 : sems.currentIndex)
                    .then(function (index) {
                        // 桥接返回的是字符串（取消时为空或 "null"），统一归一化为数字索引
                        var idx = (index === null || index === undefined) ? -1 : parseInt(index, 10);
                        if (isNaN(idx) || idx < 0 || idx >= sems.list.length) {
                            toast('导入已取消');
                            return null;
                        }
                        var chosen = sems.list[idx];
                        semesterLabel = chosen.label;

                        // 选中的就是当前页学期：直接用已经解析好的结果
                        if (idx === sems.currentIndex) {
                            if (!currentCourses.length) {
                                return fail('该学期没有课程', '「' + chosen.label + '」课表中没有解析到课程。');
                            }
                            return { courses: currentCourses, semester: chosen };
                        }

                        toast('正在获取「' + chosen.label + '」的课表…');
                        return fetchDoc(KB_PATH + '?xnxq01id=' + encodeURIComponent(chosen.value))
                            .then(function (doc) {
                                var found = findTimetableIn([doc]);
                                if (!found) {
                                    return fail('获取课表失败', '未能获取「' + chosen.label + '」的课表页面。\n\n请在该页面手动切换学期后重试。');
                                }
                                var w = range(totalWeeksOf(doc));
                                var courses = dedupe(parseTimetable(found.doc, found.table, w));
                                if (!courses.length) {
                                    return fail('该学期没有课程', '「' + chosen.label + '」课表中没有解析到课程。');
                                }
                                return { courses: courses, semester: chosen };
                            });
                    });
            })
            .then(function (payload) {
                if (!payload || !payload.courses || !payload.courses.length) return;

                var courses = payload.courses;
                var nameCount = {};
                for (var i = 0; i < courses.length; i++) {
                    nameCount[courses[i].name] = true;
                }
                var distinctNames = Object.keys(nameCount).length;

                return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(courses))
                    .then(function () {
                        return alert(
                            '导入完成',
                            '成功导入 ' + distinctNames + ' 门课程（共 ' + courses.length + ' 个课次）' +
                            (semesterLabel ? '\n学期：' + semesterLabel : '') + '\n\n' +
                            '课程按节次导入；如上下课时间与学校作息不符，\n' +
                            '请到「设置 → 自定义时间段」按学校作息调整。',
                            '完成'
                        );
                    })
                    .then(function () {
                        window.shangkeBridge.notifyTaskCompletion();
                    });
            })
            .catch(function (error) {
                var info = diagnose();
                return alert(
                    '导入失败',
                    '错误信息：' + (error && error.message ? error.message : String(error)) + '\n\n' +
                    '诊断：表格 ' + info.tableCount + ' 个 / 课表表格 ' + info.weekHeaderTables +
                    ' 个 / 课程块 ' + info.courseDivs + ' 个 / 有课程名 ' + info.namedDivs +
                    ' 个 / 学期选项 ' + info.semesterOptions + ' 个\n\n' +
                    '请确认已登录并进入「学期理论课表」后重试。',
                    '确定'
                );
            });
    }

    // ---------- 「一键导航到课表」入口 ----------
    // 供 WebBridgeProtocol.JS_NAVIGATE_TO_TIMETABLE 直接调用（优先级高于 DOM 文本模糊探测）。
    // 课表页可独立打开（实测 200 且含完整 table#timetable），因此直接跳转最稳。
    window.shangkeNavigateToTimetable = function () {
        location.href = KB_PATH;
        return true;
    };

    if (typeof window !== 'undefined') {
        window.shangkeImportEntry = runImport;
        window.stuImport = runImport;
    }
})();
