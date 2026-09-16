// 湖北职业技术学院（hbvtc）教务专属适配器 v2
//
// ===== 站点事实（2026-09-16 重测）=====
//   门户：https://jwc.hbvtc.edu.cn/（教务处官网），「服务直通车 → 教务系统」→ https://casp.hbvtc.edu.cn 一站式服务大厅
//   教务：https://jwgl.hbvtc.edu.cn/jsxsd/（湖南强智新前端，固定宽度桌面布局）
//   课表页：https://jwgl.hbvtc.edu.cn/jsxsd/xskb/xskb_list.do?viweType=0
//     table#timetable；第 0 行表头（周/节次 | 星期一..星期日）；行首：第一大节..第五大节 / 备注:
//   课程 td 内 div：
//     - div.kbcontent1（display:none，历史周次备份，无教师字段，跳过）
//     - div.kbcontent（含完整字段；GET 当前学期时可见，POST 切旧学期后服务端返回 display:none 但仍可解析）
//     - div.kbcontent（display:none，空，跳过）
//     每门课字段（font[title]）：
//       font（无 title）→ 课程名
//       font[title=教师] → 邹娟娟
//       font[title=周次(节次)] → 2-5,7-15(周)[01-02节]
//       font[title=教学楼]（display:none，name=jxlmc）→ 【德艺楼(#06)】
//       font[title=教室] → 6-313教室
//     **同一 div.kbcontent 内多门课用 "---------------------" 文本分隔线隔开**（必须按分隔线拆多门课）
//   学期下拉 #xnxq01id（多年多学期）；节次模式 #kbjcmsid；周次 #zc。
//   学期切换：POST /jsxsd/xskb/xskb_list.do?viweType=0（同套强智，实测返回目标学期课表）。
//
// ===== 桥接契约 =====
//   window.shangkeImportEntry 由注入器自动调用。
//   课程 JSON 字段：name / teacher / position / day / startSection / endSection / weeks

(function () {
    'use strict';

    var JW_HOST = 'jwgl.hbvtc.edu.cn';
    var KB_URL = 'https://jwgl.hbvtc.edu.cn/jsxsd/xskb/xskb_list.do?viweType=0';
    var WEEKDAY = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
    var SEP_RE = /^[-=_]{3,}$/;
    // 大节 → 节次兜底映射（块内 font[title=周次(节次)] 通常自带 [xx-yy节]，此处仅在缺失时兜底）
    var BIG_SECTION = { '第一大节': [1, 2], '第二大节': [3, 4], '第三大节': [5, 6], '第四大节': [7, 8], '第五大节': [9, 10], '第六大节': [11, 12] };

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
    function clean(text) {
        if (!text) return '';
        return String(text).replace(/ /g, ' ').replace(/\s+/g, ' ').trim();
    }
    function isJwHost() {
        return location.host.indexOf(JW_HOST) !== -1;
    }
    function fetchDoc(url) {
        return fetch(url, { credentials: 'include' })
            .then(function (r) { return r.text(); })
            .then(function (html) { return new DOMParser().parseFromString(html, 'text/html'); });
    }
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
    function postSemester(semesterValue, kbjcmsid) {
        function enc(v) { return encodeURIComponent(v == null ? '' : String(v)); }
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
            method: 'POST', credentials: 'include',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
            body: body
        }).then(function (r) { return r.text(); })
          .then(function (html) { return new DOMParser().parseFromString(html, 'text/html'); });
    }

    // ---------- 周次 / 节次 ----------
    function parseWeeks(numText) {
        var weeks = [];
        if (!numText) return weeks;
        var parts = String(numText).split(/[,，、]/);
        for (var p = 0; p < parts.length; p++) {
            var part = clean(parts[p]);
            if (!part) continue;
            var rm = part.match(/(\d+)\s*[-~－—]+\s*(\d+)/);
            if (rm) {
                var s = parseInt(rm[1], 10), e = parseInt(rm[2], 10);
                if (isNaN(s) || s < 1) continue;
                if (isNaN(e) || e < s) e = s;
                if (e > 40) e = 40;
                for (var w = s; w <= e; w++) weeks.push(w);
            } else {
                var sm = part.match(/\d+/);
                if (sm) { var n = parseInt(sm[0], 10); if (n >= 1 && n <= 40) weeks.push(n); }
            }
        }
        var u = [];
        for (var i = 0; i < weeks.length; i++) if (u.indexOf(weeks[i]) === -1) u.push(weeks[i]);
        return u.sort(function (a, b) { return a - b; });
    }
    function parseWeekSec(text) {
        var info = { weeks: [], start: 0, end: 0 };
        if (!text) return info;
        var secM = String(text).match(/\[(\d{1,2})\s*[-~－—]\s*(\d{1,2})\s*节\]/);
        if (secM) { info.start = parseInt(secM[1], 10); info.end = parseInt(secM[2], 10); }
        else {
            var s1 = String(text).match(/\[(\d{1,2})\s*节\]/);
            if (s1) info.start = info.end = parseInt(s1[1], 10);
        }
        var core = String(text).replace(/\[[^\]]*\]/g, ' ');
        var wk = core.search(/[（(]?\s*周/);
        var head = wk >= 0 ? core.slice(0, wk) : core;
        info.weeks = parseWeeks(head);
        return info;
    }
    function parseRowLabel(label) {
        var text = clean(label);
        if (!text || text.indexOf('备注') === 0) return null;
        var m = text.match(/第\s*(\d{1,2})\s*[-~－—]\s*(\d{1,2})\s*节/);
        if (m) return { start: parseInt(m[1], 10), end: parseInt(m[2], 10) };
        var s1 = text.match(/第\s*(\d{1,2})\s*节/);
        if (s1) { var n = parseInt(s1[1], 10); return { start: n, end: n }; }
        var big = BIG_SECTION[text];
        if (big) return { start: big[0], end: big[1] };
        return null;
    }

    // ---------- 网格 ----------
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
                        grid[r + rr][c + cc] = { td: td, isOrigin: rr === 0 && cc === 0 };
                    }
                }
                c += colspan;
            }
        }
        return grid;
    }
    function buildDayMap(headerCells) {
        var map = [];
        for (var i = 0; i < headerCells.length; i++) {
            var text = clean(headerCells[i].textContent);
            var day = 0;
            for (var w = 0; w < WEEKDAY.length; w++) {
                if (text.indexOf(WEEKDAY[w]) !== -1) { day = w + 1; break; }
            }
            map.push(day > 0 ? day : 0);
        }
        return map;
    }

    // ---------- 解析一个 div.kbcontent：按分隔线拆多门课 ----------
    function parseVisibleBlock(div, day, fallbackSec) {
        var blocks = [[]];
        var nodes = div.childNodes;
        for (var n = 0; n < nodes.length; n++) {
            var node = nodes[n];
            if (node.nodeType === 3 && SEP_RE.test(clean(node.textContent))) {
                blocks.push([]);
            } else {
                blocks[blocks.length - 1].push(node);
            }
        }
        var out = [];
        for (var b = 0; b < blocks.length; b++) {
            var children = blocks[b];
            var name = '', teacher = '', position = '', building = '', weekSecText = '';
            for (var i = 0; i < children.length; i++) {
                var node2 = children[i];
                if (node2.nodeType === 3) {
                    var t = clean(node2.textContent);
                    if (t && !SEP_RE.test(t) && !name) name = t;
                } else if (node2.nodeType === 1 && node2.tagName === 'FONT') {
                    var title = node2.getAttribute('title') || '';
                    var fv = clean(node2.textContent);
                    if (!fv) continue;
                    // 湖北职院课程名在无 title 的 font 内（非裸文本节点）
                    if (!title && !name) name = fv;
                    else if (title.indexOf('老师') !== -1 || title.indexOf('教师') !== -1) teacher = teacher || fv;
                    else if (title.indexOf('周次') !== -1 || title.indexOf('节次') !== -1) weekSecText = weekSecText || fv;
                    else if (title.indexOf('教学楼') !== -1) building = building || fv.replace(/[【】]/g, '');
                    else if (title.indexOf('教室') !== -1 || title.indexOf('地点') !== -1) position = position || fv;
                }
            }
            if (!name) continue;
            var info = parseWeekSec(weekSecText);
            if (!info.weeks.length) continue;
            var startSec = info.start, endSec = info.end;
            if (!startSec || !endSec) {
                if (fallbackSec) { startSec = fallbackSec.start; endSec = fallbackSec.end; }
                else continue;
            }
            out.push({
                name: name, teacher: teacher || '未安排', position: (building ? building + position : position) || '',
                day: day, startSection: startSec, endSection: endSec, weeks: info.weeks
            });
        }
        return out;
    }

    function parseTimetable(doc, table) {
        var courses = [];
        var rows = table.rows;
        if (!rows || rows.length < 2) return courses;
        var dayMap = buildDayMap(rows[0].cells);
        var grid = buildGrid(table);
        var rowRange = {};
        for (var r = 0; r < grid.length; r++) {
            var lc = grid[r] && grid[r][0] && grid[r][0].td ? grid[r][0].td : null;
            if (!lc) continue;
            var rr = parseRowLabel(lc.textContent);
            if (rr) rowRange[r] = rr;
        }
        for (var r = 0; r < grid.length; r++) {
            var row = grid[r];
            if (!row) continue;
            for (var c = 0; c < row.length; c++) {
                var cell = row[c];
                if (!cell || !cell.isOrigin || !cell.td) continue;
                var day = dayMap[c] || 0;
                if (day < 1 || day > 7) continue;
                var divs;
                try { divs = cell.td.querySelectorAll('div.kbcontent'); } catch (e) { continue; }
                for (var k = 0; k < divs.length; k++) {
                    var div = divs[k];
                    // 注意：POST 切学期后服务端返回的课程块也带 display:none，因此**不**按 display 过滤；
                    // 空块（无课程名 font）由 parseVisibleBlock 的 !name continue 自然过滤。
                    var list = parseVisibleBlock(div, day, rowRange[r] || null);
                    for (var j = 0; j < list.length; j++) courses.push(list[j]);
                }
            }
        }
        return dedupe(courses);
    }

    function dedupe(courses) {
        var result = [], seen = {};
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

    function findTimetableIn(doc) {
        if (!doc || !doc.getElementById) return null;
        var t = doc.getElementById('timetable');
        if (t && t.rows && t.rows.length > 1 && (t.textContent || '').indexOf('星期一') !== -1) return t;
        var t2 = doc.getElementById('kbtable');
        if (t2 && t2.rows && t2.rows.length > 1 && (t2.textContent || '').indexOf('星期一') !== -1) return t2;
        var all = doc.querySelectorAll('table');
        for (var i = 0; i < all.length; i++) {
            if (all[i].querySelector('div.kbcontent')) return all[i];
        }
        return null;
    }
    function resolveSource() {
        var live = findTimetableIn(document);
        if (live) return Promise.resolve({ doc: document, table: live });
        if (!isJwHost()) return Promise.resolve(null);
        toast('正在读取课表…');
        return fetchDoc(KB_URL).then(function (doc) {
            var t = findTimetableIn(doc);
            return t ? { doc: doc, table: t } : null;
        }).catch(function () { return null; });
    }

    function runImport() {
        var semesterLabel = '';
        function fail(title, message) {
            return alert(title, message, '确定').then(function () { return null; });
        }
        return Promise.resolve()
            .then(function () {
                if (!isJwHost()) {
                    return alert('跳转到教务系统',
                        '当前不在湖北职业技术学院教务系统页面。\n\n' +
                        '点击「确定」将跳转到课表页：\n' +
                        '1. 如未登录，会先到统一身份认证（CAS）登录页\n' +
                        '2. 完成账号密码登录后自动回到课表页\n' +
                        '3. 再点「执行导入」即可。'
                    ).then(function () { location.href = KB_URL; return null; });
                }
                return resolveSource();
            })
            .then(function (src) {
                if (!src) {
                    return fail('未找到课表',
                        '没有读到课表数据。\n\n' +
                        '请确认：\n1. 已登录教务系统并进入「学期理论课表」页面\n' +
                        '2. 课表已正常显示\n\n也可以先点下方「一键导航到课表」，进入后再点「执行导入」。');
                }
                return src;
            })
            .then(function (src) {
                if (!src) return null;
                var sems = readSemesters(src.doc);
                var currentCourses = dedupe(parseTimetable(src.doc, src.table));
                if (sems.list.length <= 1) {
                    if (!currentCourses.length) return fail('本学期没有课程', '课表中没有解析到课程。');
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
                        if (isNaN(idx) || idx < 0 || idx >= sems.list.length) { toast('导入已取消'); return null; }
                        var chosen = sems.list[idx];
                        semesterLabel = chosen.label;
                        if (idx === sems.currentIndex) {
                            if (!currentCourses.length) return fail('该学期没有课程', '「' + chosen.label + '」没有解析到课程。');
                            return { courses: currentCourses, semester: chosen };
                        }
                        toast('正在获取「' + chosen.label + '」的课表…');
                        return postSemester(chosen.value, readKbjcmsid(src.doc))
                            .then(function (doc) {
                                var t = findTimetableIn(doc);
                                if (!t) return fail('获取课表失败', '未能获取「' + chosen.label + '」的课表。');
                                var courses = dedupe(parseTimetable(doc, t));
                                if (!courses.length) return fail('该学期没有课程', '「' + chosen.label + '」没有解析到课程。');
                                return { courses: courses, semester: chosen };
                            });
                    });
            })
            .then(function (payload) {
                if (!payload || !payload.courses || !payload.courses.length) return;
                var courses = payload.courses;
                var nameCount = {};
                for (var i = 0; i < courses.length; i++) nameCount[courses[i].name] = true;
                var distinctNames = Object.keys(nameCount).length;
                return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(courses))
                    .then(function () {
                        return alert('导入完成',
                            '成功导入 ' + distinctNames + ' 门课程（共 ' + courses.length + ' 个课次）' +
                            (semesterLabel ? '\n学期：' + semesterLabel : '') + '\n\n' +
                            '课程按节次导入；如上下课时间与学校作息不符，\n' +
                            '请到「设置 → 自定义时间段」按学校作息调整。',
                            '完成');
                    })
                    .then(function () { window.shangkeBridge.notifyTaskCompletion(); });
            })
            .catch(function (error) {
                var live = findTimetableIn(document);
                var info = { tables: live ? 1 : 0, blocks: live ? live.querySelectorAll('div.kbcontent').length : 0 };
                return alert('导入失败',
                    '错误信息：' + (error && error.message ? error.message : String(error)) + '\n\n' +
                    '诊断：课表表格 ' + info.tables + ' 个 / 课程块 ' + info.blocks + ' 个\n\n' +
                    '请确认已登录并进入「学期理论课表」后重试。', '确定');
            });
    }

    window.shangkeNavigateToTimetable = function () {
        location.href = KB_URL;
        return true;
    };
    if (typeof window !== 'undefined') {
        window.shangkeImportEntry = runImport;
        window.hbvtcImport = runImport;
    }
})();
