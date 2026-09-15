// 南昌大学本科教务（jwpt.ncu.edu.cn/jsxsd，强智新前端 qz-weeklyTable）专属适配器
//
// ===== 站点事实（2026-09-15 实测）=====
//   门户 my.ncu.edu.cn（统一身份认证 SSO）→ 应用「本科教务服务平台」→ jwpt.ncu.edu.cn/jsxsd
//   课表页 https://jwpt.ncu.edu.cn/jsxsd/xskb/xskb_list.do?viweType=0
//     直开即返回含完整课表的 HTML（自动 SSO 登录）；外页形态下表格在 iframe(Iframe1) 内。
//   学期切换：表单 #searchFrom（layui）POST /jsxsd/xskb/xskb_list.do?viweType=0
//     字段：viweType=0 / showallprint=0 / showkchprint=0 / showkink=0 / showfzmprint=0
//           baseUrl=/jsxsd / xsflMapListJsonStr=讲课学时,... / xnxq01id=<学期> / zc= / kbjcmsid=<节次模式>
//     响应 HTML 直接包含表格（已实测 200 可用，如 2025-2026-1）。
//   学期下拉 #xnxq01id：如 2026-2027秋季学期=2026-2027-1（选项按学年倒序）
//   表格 table.qz-weeklyTable：
//     第 0 行 = 表头（周次 | 星期一..星期日）；末行 = 备注
//     中间行 = 节次行，行首单元格文字形如「第一大节 08:00~09:30」或「第一节 07:50~08:30」
//       —— 不同学期节次模式不同（大节模式 / 单节模式），节次一律以课程明细「时间:X周[a-b节]」为准
//   课程单元格 td[name=kbDataTd]（rowspan 可跨多个节次行）> ul.courselists > li.courselists-item：
//     div.qz-hasCourse-title = 课程名
//     span.qz-hasCourse-abbrinfo = "老师:肖凯文;时间:1周[1-2节];地点:教学大楼北翼(教学大楼北翼102/1002)"
//     同一格内同名课程的多条 li = 同一门课的不同周次段（如 1、3、5、10 周各一条），需合并周次
//   实测 120 条课程明细全部含「时间:[..节]」与「地点:」，字段完整。
//
// ===== 桥接契约 =====
//   window.shangkeImportEntry 由注入器（WebBridgeProtocol.JS_IMPORT_AUTOSTART）自动调用。
//   课程 JSON 字段：name / teacher / position / day / startSection / endSection / weeks
//   （position 为 Kotlin 侧 ImportCourseJsonModel 必填字段，不可写成 room）

(function () {
    'use strict';

    var JW_HOST = 'jwpt.ncu.edu.cn';
    var KB_URL = 'https://jwpt.ncu.edu.cn/jsxsd/xskb/xskb_list.do?viweType=0';
    var MAIN_URL = 'https://jwpt.ncu.edu.cn/jsxsd/framework/xsMainV.htmlx';
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

    // ---------- 定位课表表格 ----------
    function findTimetableIn(doc) {
        if (!doc || !doc.querySelectorAll) return null;
        var tables = doc.querySelectorAll('table.qz-weeklyTable');
        for (var i = 0; i < tables.length; i++) {
            var text = tables[i].textContent || '';
            if (text.indexOf('星期一') !== -1 && tables[i].rows && tables[i].rows.length > 1) {
                return { doc: doc, table: tables[i] };
            }
        }
        // 兜底：包含星期表头 + 课程列表的表格
        var all = doc.querySelectorAll('table');
        for (var j = 0; j < all.length; j++) {
            var t = all[j];
            if (!t.rows || t.rows.length < 2) continue;
            var txt = t.textContent || '';
            if (txt.indexOf('星期一') !== -1 && (txt.indexOf('老师:') !== -1 || t.querySelector('li.courselists-item'))) {
                return { doc: doc, table: t };
            }
        }
        return null;
    }

    function findTimetable() {
        var docs = collectDocuments();
        for (var i = 0; i < docs.length; i++) {
            var found = findTimetableIn(docs[i]);
            if (found) return found;
        }
        return null;
    }

    // ---------- 判断当前处于哪个环节 ----------
    function isJwHost() {
        return location.host.indexOf(JW_HOST) !== -1;
    }

    function isPortalHost() {
        return location.host.indexOf('ncu.edu.cn') !== -1 && location.host.indexOf(JW_HOST) === -1;
    }

    // ---------- 同源抓取 ----------
    // 课表页与教务同源，fetch 自动携带会话 Cookie；credentials 显式声明以兼容旧内核。
    function fetchDoc(url) {
        return fetch(url, { credentials: 'include' })
            .then(function (resp) { return resp.text(); })
            .then(function (html) {
                return new DOMParser().parseFromString(html, 'text/html');
            });
    }

    // ---------- 从文档读取学期与节次模式 ----------
    function readSelect(doc, id) {
        if (!doc || !doc.getElementById) return null;
        var sel = doc.getElementById(id);
        if (!sel) return null;
        var options = sel.getElementsByTagName('option');
        var list = [];
        var currentIndex = -1;
        for (var i = 0; i < options.length; i++) {
            var value = options[i].getAttribute('value') || '';
            var label = clean(options[i].textContent) || value;
            if (!value) continue;
            list.push({ value: value, label: label });
            if (options[i].selected) currentIndex = list.length - 1;
        }
        if (currentIndex < 0 && list.length) currentIndex = 0;
        return { list: list, currentIndex: currentIndex };
    }

    function readSemesters(doc) {
        var r = readSelect(doc, 'xnxq01id');
        return r || { list: [], currentIndex: -1 };
    }

    function readKbjcmsid(doc) {
        var r = readSelect(doc, 'kbjcmsid');
        if (r && r.list.length) return r.list[0].value;
        return '';
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

    // ---------- 按学期抓取课表（POST，实测可用）----------
    function postSemester(semesterValue, kbjcmsid) {
        // 手工拼 URL 编码表单体（不依赖 URLSearchParams，兼容旧 WebView 内核）
        function enc(v) {
            return encodeURIComponent(v == null ? '' : String(v));
        }
        var body = 'viweType=' + enc('0') +
            '&showallprint=' + enc('0') +
            '&showkchprint=' + enc('0') +
            '&showkink=' + enc('0') +
            '&showfzmprint=' + enc('0') +
            '&baseUrl=' + enc('/jsxsd') +
            '&xsflMapListJsonStr=' + enc('讲课学时,实验学时,实践学时,上机学时,其他学时,') +
            '&xnxq01id=' + enc(semesterValue || '') +
            '&zc=' + enc('') +
            '&kbjcmsid=' + enc(kbjcmsid || '');

        return fetch('/jsxsd/xskb/xskb_list.do?viweType=0', {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
            body: body
        }).then(function (resp) {
            return resp.text();
        }).then(function (html) {
            return new DOMParser().parseFromString(html, 'text/html');
        });
    }

    // ---------- 行列网格（处理 rowspan/colspan）----------
    // 课表单元格可能 rowspan 跨多个节次行；必须把每个 <td> 按 rowspan/colspan
    // 展开到 grid[r][c]，否则跨行课程会让后续行的星期列错位。
    function buildGrid(table) {
        var trs = Array.prototype.slice.call(table.rows || []);
        var grid = [];
        for (var r = 0; r < trs.length; r++) {
            var tds = Array.prototype.slice.call(trs[r].querySelectorAll('td, th'));
            var c = 0;
            grid[r] = grid[r] || [];
            for (var i = 0; i < tds.length; i++) {
                var td = tds[i];
                while (grid[r][c] !== undefined) c++;
                var rowspan = parseInt(td.getAttribute('rowspan'), 10) || 1;
                var colspan = parseInt(td.getAttribute('colspan'), 10) || 1;
                if (rowspan < 1) rowspan = 1;
                if (colspan < 1) colspan = 1;
                for (var rr = 0; rr < rowspan; rr++) {
                    for (var cc = 0; cc < colspan; cc++) {
                        grid[r + rr] = grid[r + rr] || [];
                        grid[r + rr][c + cc] = {
                            html: td.innerHTML || '',
                            td: td,
                            isOrigin: rr === 0 && cc === 0
                        };
                    }
                }
                c += colspan;
            }
        }
        return grid;
    }

    // ---------- 列索引 → 星期几 ----------
    function buildDayMap(headerRowCells) {
        var map = [];
        for (var i = 0; i < headerRowCells.length; i++) {
            var text = clean(headerRowCells[i].textContent);
            var day = 0;
            for (var w = 0; w < WEEKDAY.length; w++) {
                if (text.indexOf(WEEKDAY[w]) !== -1) { day = w + 1; break; }
            }
            map.push(day > 0 ? day : i);
        }
        return map;
    }

    // ---------- 周次解析 ----------
    // "1" / "1-14" / "3-4,6-11" → 周次数组；flag '单'/'双' 时过滤
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

    // ---------- 课程明细字段 ----------
    // "老师:肖凯文;时间:1周[1-2节];地点:教学大楼北翼(教学大楼北翼102/1002)"
    // 返回 { teacher, weeks, start, end, position }
    function parseDetail(text, fallbackRange) {
        var info = { teacher: '', weeks: [], start: 0, end: 0, position: '' };
        if (!text) return info;

        var teacherMatch = text.match(/老师\s*:\s*([^;]*)/);
        if (teacherMatch) info.teacher = clean(teacherMatch[1]);

        var timeMatch = text.match(/时间\s*:\s*([^;]*)/);
        var start = 0, end = 0;
        if (timeMatch) {
            // "1周[1-2节]" / "1-14周[3-5节]" / "3-4,6-11周[06-07节]"；周次前可有(单周)/(双周)等
            var m = timeMatch[1].match(/([\d\s,，、\-~－—]+)\s*[（(]?\s*([单双]?\s*周)[）)]?\s*\[([^\]]*)\]/);
            if (m) {
                var weekPart = m[1];
                var flag = m[2].indexOf('单') !== -1 ? '单' : (m[2].indexOf('双') !== -1 ? '双' : '');
                info.weeks = parseWeeks(weekPart, flag);
                var secs = m[3].match(/\d+/g) || [];
                if (secs.length) {
                    var vals = [];
                    for (var i = 0; i < secs.length; i++) {
                        var n = parseInt(secs[i], 10);
                        if (!isNaN(n) && n > 0 && n <= 30) vals.push(n);
                    }
                    if (vals.length) {
                        vals.sort(function (a, b) { return a - b; });
                        start = vals[0];
                        end = vals[vals.length - 1];
                    }
                }
            }
        }
        // 明细缺节次时退回行内节次（fallbackRange）
        if (!start || !end) {
            if (fallbackRange && fallbackRange.start) {
                start = fallbackRange.start;
                end = fallbackRange.end;
            } else {
                return info;
            }
        }
        if (!info.weeks.length) return info;
        info.start = start;
        info.end = end;

        // 地点：优先取括号内教室号，无括号时取整串；"()" 视为无教室
        var placeMatch = text.match(/地点\s*:\s*([^;]*)/);
        if (placeMatch) {
            var place = clean(placeMatch[1]);
            var paren = place.match(/[（(]([^（）()]*)[）)]/);
            if (paren && clean(paren[1])) {
                info.position = clean(paren[1]);
            } else if (place && place !== '()') {
                info.position = place;
            } else {
                var building = place.replace(/[（(][^（）()]*[）)]/g, '').trim();
                info.position = building;
            }
        }
        return info;
    }

    // ---------- 行首节次标签 → 兜底节次 ----------
    // "第一大节 08:00~09:30" / "第一节 07:50~08:30" / "中午 12:20~13:50" / "备注"
    function parseRowLabel(label) {
        var text = clean(label);
        if (!text || text.indexOf('备注') === 0) return null;

        var single = text.match(/第\s*(\d{1,2})\s*节/);
        if (single) {
            var n = parseInt(single[1], 10);
            if (n >= 1 && n <= 30) return { start: n, end: n };
        }
        var big = text.match(/第\s*(\d{1,2})\s*大节/);
        if (big) {
            var bn = parseInt(big[1], 10);
            // 大节 → 小节映射（该校 2026-2027-1 大节模式作息）
            var MAP = { 1: [1, 2], 2: [3, 4, 5], 3: [6, 7], 4: [8, 9, 10], 5: [11, 12, 13] };
            if (MAP[bn]) return { start: MAP[bn][0], end: MAP[bn][MAP[bn].length - 1] };
        }
        return null;
    }

    // ---------- 解析一张课表 ----------
    function parseTimetable(doc, table) {
        var courses = [];
        var rows = table.rows;
        if (!rows || rows.length < 2) return courses;

        var dayMap = buildDayMap(rows[0].cells);
        var grid = buildGrid(table);
        var totalWeeks = totalWeeksOf(doc);
        var fallbackWeeks = range(totalWeeks);

        // 行号 → 该行节次兜底
        var rowRange = {};
        for (var r = 0; r < grid.length; r++) {
            var labelCell = grid[r] && grid[r][0] && grid[r][0].td ? grid[r][0].td : null;
            if (!labelCell) continue;
            var rr = parseRowLabel(labelCell.textContent);
            if (rr) rowRange[r] = rr;
        }

        for (var r = 0; r < grid.length; r++) {
            var row = grid[r];
            if (!row) continue;
            for (var c = 0; c < row.length; c++) {
                var cell = row[c];
                if (!cell || !cell.isOrigin || !cell.td) continue;
                var day = dayMap[c] || c;
                if (day < 1 || day > 7) continue;

                var lis;
                try { lis = cell.td.querySelectorAll('li.courselists-item'); } catch (e) { continue; }
                if (!lis.length) continue;

                var parsed = [];
                for (var i = 0; i < lis.length; i++) {
                    var titleEl = lis[i].querySelector('.qz-hasCourse-title');
                    var name = titleEl ? clean(titleEl.textContent) : '';
                    if (!name) continue;
                    var abbrEl = lis[i].querySelector('.qz-hasCourse-abbrinfo');
                    var detail = parseDetail(abbrEl ? abbrEl.textContent : '', rowRange[r] || null);
                    if (!detail.weeks.length || !detail.start) continue;
                    parsed.push({
                        name: name,
                        teacher: detail.teacher || '未安排',
                        position: detail.position || '',
                        day: day,
                        startSection: detail.start,
                        endSection: detail.end,
                        weeks: detail.weeks
                    });
                }

                // 同一格内同名同节次的课程（多条周次段 li）合并周次，避免同一门课拆成多个块
                var merged = [];
                var indexByKey = {};
                for (var k = 0; k < parsed.length; k++) {
                    var p = parsed[k];
                    var key = p.name + '|' + p.startSection + '|' + p.endSection;
                    if (indexByKey[key] === undefined) {
                        indexByKey[key] = merged.length;
                        merged.push({
                            name: p.name,
                            teacher: p.teacher,
                            position: p.position,
                            day: p.day,
                            startSection: p.startSection,
                            endSection: p.endSection,
                            weeks: p.weeks.slice()
                        });
                    } else {
                        var exist = merged[indexByKey[key]];
                        // 教师/地点以第一条为准：南昌大学同一课位按周轮换讲师，
                        // 全部并列会过长，课表块只展示首位讲师（周次已在 weeks 中合并）
                        if (exist.teacher === '未安排' && p.teacher !== '未安排') exist.teacher = p.teacher;
                        if (!exist.position && p.position) exist.position = p.position;
                        for (var w = 0; w < p.weeks.length; w++) {
                            if (exist.weeks.indexOf(p.weeks[w]) === -1) exist.weeks.push(p.weeks[w]);
                        }
                    }
                }
                for (var m = 0; m < merged.length; m++) {
                    merged[m].weeks.sort(function (a, b) { return a - b; });
                    courses.push(merged[m]);
                }
            }
        }
        return courses;
    }

    // ---------- 去重 + 排序 ----------
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

    // ---------- 取得课表来源 ----------
    // 1. 当前页面（含 iframe）已有表格 → 直接用
    // 2. 在教务主机上 → 同源抓取课表页（POST 当前学期）→ 含表格 + 学期下拉
    // 3. 其它（门户/登录页）→ null
    function resolveSource() {
        var live = findTimetable();
        if (live) return Promise.resolve({ doc: live.doc, table: live.table });

        if (!isJwHost()) return Promise.resolve(null);

        toast('正在读取课表…');
        // 先用 GET 拿到默认学期页（含学期下拉与默认表格）
        return fetchDoc(KB_URL)
            .then(function (doc) {
                var found = findTimetableIn(doc);
                if (found) return { doc: found.doc, table: found.table };
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

        function gotoMain() {
            location.href = MAIN_URL;
        }

        return Promise.resolve()
            .then(function () {
                // 0. 不在教务主机（门户 / 登录页）→ 引导进入本科教务服务平台
                if (!isJwHost()) {
                    return alert(
                        '请先进入本科教务服务平台',
                        '当前不在南昌大学教务系统页面。\n\n' +
                        '点击「确定」后将跳转到本科教务服务平台，\n' +
                        '如未登录会自动进入统一身份认证，登录完成后\n' +
                        '请再次点击「执行导入」。'
                    ).then(function () {
                        gotoMain();
                        return null;
                    });
                }
                return resolveSource();
            })
            .then(function (src) {
                if (!src) {
                    return fail(
                        '未找到课表',
                        '没有读到课表数据。\n\n' +
                        '请确认：\n' +
                        '1. 已登录南昌大学本科教务服务平台（jwpt.ncu.edu.cn）\n' +
                        '2. 已进入「个人课表信息」页面且课表已显示\n\n' +
                        '也可以先点下方「一键导航到课表」，进入课表页后再点「执行导入」。'
                    );
                }
                return src;
            })
            .then(function (src) {
                if (!src) return null;

                var sems = readSemesters(src.doc);
                var currentCourses = dedupe(parseTimetable(src.doc, src.table));

                if (sems.list.length <= 1) {
                    if (!currentCourses.length) {
                        return fail(
                            '本学期没有课程',
                            '在课表中没有解析到课程。\n\n' +
                            '请确认：\n' +
                            '1. 已进入「个人课表信息」页面\n' +
                            '2. 已切换到有课的学期'
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
                        var idx = (index === null || index === undefined) ? -1 : parseInt(index, 10);
                        if (isNaN(idx) || idx < 0 || idx >= sems.list.length) {
                            toast('导入已取消');
                            return null;
                        }
                        var chosen = sems.list[idx];
                        semesterLabel = chosen.label;

                        if (idx === sems.currentIndex) {
                            if (!currentCourses.length) {
                                return fail('该学期没有课程', '「' + chosen.label + '」课表中没有解析到课程。');
                            }
                            return { courses: currentCourses, semester: chosen };
                        }

                        toast('正在获取「' + chosen.label + '」的课表…');
                        return postSemester(chosen.value, readKbjcmsid(src.doc))
                            .then(function (doc) {
                                var found = findTimetableIn(doc);
                                if (!found) {
                                    return fail('获取课表失败', '未能获取「' + chosen.label + '」的课表页面。\n\n请在该页面手动切换学期后重试。');
                                }
                                var courses = dedupe(parseTimetable(found.doc, found.table));
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
                var info = { tables: 0, lis: 0 };
                try {
                    var found = findTimetable();
                    if (found) {
                        info.tables = 1;
                        info.lis = found.table.querySelectorAll('li.courselists-item').length;
                    }
                } catch (e) { }
                return alert(
                    '导入失败',
                    '错误信息：' + (error && error.message ? error.message : String(error)) + '\n\n' +
                    '诊断：课表表格 ' + info.tables + ' 个 / 课程块 ' + info.lis + ' 个\n\n' +
                    '请确认已登录并进入「个人课表信息」后重试。',
                    '确定'
                );
            });
    }

    // ---------- 「一键导航到课表」入口 ----------
    // 供 WebBridgeProtocol.JS_NAVIGATE_TO_TIMETABLE 直接调用。
    // 课表页可独立打开（实测 200 且含完整 table.qz-weeklyTable），因此直接跳转最稳。
    window.shangkeNavigateToTimetable = function () {
        if (isJwHost()) {
            location.href = KB_URL;
        } else {
            location.href = MAIN_URL;
        }
        return true;
    };

    if (typeof window !== 'undefined') {
        window.shangkeImportEntry = runImport;
        window.ncuImport = runImport;
    }
})();
