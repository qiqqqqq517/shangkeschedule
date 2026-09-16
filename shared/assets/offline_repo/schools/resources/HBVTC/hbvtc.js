// 湖北职业技术学院（hbvtc）教务专属适配器
//
// ===== 站点事实（2026-09-16 实测）=====
//   统一认证：https://cas.hbvtc.edu.cn/authserver/login （service=课表页 → 登录后 SSO 直达）
//   教务系统：https://jwgl.hbvtc.edu.cn/jsxsd/ （湖南强智新前端）
//   课表页：https://jwgl.hbvtc.edu.cn/jsxsd/xskb/xskb_list.do?viweType=0
//     直开即含完整课表（已 SSO）；表格 table#timetable，第 0 行表头（空|星期一..星期日）。
//   课程单元格 td 内含：
//     - 多个 div.kbcontent1（style="display:none"，历史周次备份，无教师，必须跳过）
//     - 一个可见 div.kbcontent（style="position:relative"）：
//         <font>课程名</font><br>
//         <font title="教师">邹娟娟</font><br>
//         <font title="周次(节次)">2-5,7-15(周)[01-02节]</font><br>
//         <font title="教学楼" style="display:none">【德艺楼(#06)】</font>
//         <font title="教室">6-313教室</font><br>
//         ...通知单编号/班级/备注（hidden）
//   行首节次标签："第一大节 08:00-09:40" / "第二大节 10:10-11:50" / 第三大节 14:30-16:10 / 第四大节 16:20-18:00
//     实际小节：第一大节=[01-02节] 第二大节=[03-04节] 第三大节=[05-06节] 第四大节=[07-08节]
//     （以课程块内 [xx-yy节] 为准，行标签仅作兜底）
//   学期下拉 #xnxq01id：如 2026-2027-1；节次模式 #kbjcmsid；周次 #zc（最多 30 周）
//   学期切换：POST /jsxsd/xskb/xskb_list.do?viweType=0（与南昌大学同套强智）
//
// ===== 桥接契约 =====
//   window.shangkeImportEntry 由注入器自动调用。
//   课程 JSON 字段：name / teacher / position / day / startSection / endSection / weeks

(function () {
    'use strict';

    var JW_HOST = 'jwgl.hbvtc.edu.cn';
    var KB_URL = 'https://jwgl.hbvtc.edu.cn/jsxsd/xskb/xskb_list.do?viweType=0';
    var MAIN_URL = 'https://jwgl.hbvtc.edu.cn/jsxsd/framework/xsMainV.htmlx';
    var CAS_KB_URL = 'https://cas.hbvtc.edu.cn/authserver/login?service=' +
        encodeURIComponent(KB_URL);
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

    function clean(text) {
        if (!text) return '';
        return String(text).replace(/ /g, ' ').replace(/\s+/g, ' ').trim();
    }

    // ---------- 判断当前处于哪个环节 ----------
    function isJwHost() {
        return location.host.indexOf(JW_HOST) !== -1;
    }

    // ---------- 同源抓取课表页 ----------
    function fetchDoc(url) {
        return fetch(url, { credentials: 'include' })
            .then(function (resp) { return resp.text(); })
            .then(function (html) {
                return new DOMParser().parseFromString(html, 'text/html');
            });
    }

    // ---------- 学期下拉 ----------
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

    // 学期周数上限：select#zc 的"第N周"最大数
    function totalWeeksOf(doc) {
        var n = 0;
        try {
            var sel = doc && doc.getElementById ? doc.getElementById('zc') : null;
            if (sel) {
                var options = sel.getElementsByTagName('option');
                for (var i = 0; i < options.length; i++) {
                    var m = clean(options[i].textContent).match(/第\s*(\d+)\s*周/);
                    if (m) n = Math.max(n, parseInt(m[1], 10));
                }
            }
        } catch (e) { }
        return (n > 0 && n <= 40) ? n : 20;
    }

    // ---------- 按学期 POST 抓取 ----------
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
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
            body: body
        }).then(function (resp) { return resp.text(); })
          .then(function (html) {
              return new DOMParser().parseFromString(html, 'text/html');
          });
    }

    // ---------- 周次解析 ----------
    // "2-5,7-15" / "1" / "3-4,6-11"；flag '单'/'双' 过滤
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

    // 从 "2-5,7-15(周)[01-02节]" 这种文本解析周次与节次
    function parseWeekSec(text) {
        var info = { weeks: [], start: 0, end: 0 };
        if (!text) return info;
        var s = String(text);
        // 节次：[01-02节]
        var secMatch = s.match(/\[(\d{1,2})\s*[-~－—]\s*(\d{1,2})\s*节\]/);
        if (secMatch) {
            info.start = parseInt(secMatch[1], 10);
            info.end = parseInt(secMatch[2], 10);
        } else {
            var singleSec = s.match(/\[(\d{1,2})\s*节\]/);
            if (singleSec) info.start = info.end = parseInt(singleSec[1], 10);
        }
        // 周次：去掉方括号节次后，截到 (周)/周 之前
        var core = s.replace(/\[[^\]]*\]/g, ' ');
        var weekMark = core.search(/[（(]?\s*[单双]?\s*周/);
        var head = weekMark >= 0 ? core.slice(0, weekMark) : core;
        var flag = /[（(]\s*单/.test(s) ? '单' : (/[（(]\s*双/.test(s) ? '双' : '');
        info.weeks = parseWeeks(head, flag);
        return info;
    }

    // 行首节次标签兜底："第一大节 08:00-09:40"
    function parseRowLabel(label) {
        var text = clean(label);
        if (!text || text.indexOf('备注') === 0) return null;
        var big = text.match(/第\s*([一二三四五六七八九十])\s*大节/);
        if (big) {
            var MAP = { '一': 1, '二': 3, '三': 5, '四': 7, '五': 9, '六': 11 };
            var bn = MAP[big[1]];
            if (bn) return { start: bn, end: bn + 1 };
        }
        var single = text.match(/第\s*(\d{1,2})\s*节/);
        if (single) {
            var n = parseInt(single[1], 10);
            if (n >= 1 && n <= 30) return { start: n, end: n };
        }
        return null;
    }

    // ---------- 网格展开（rowspan/colspan）----------
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

    function buildDayMap(headerRowCells) {
        var map = [];
        for (var i = 0; i < headerRowCells.length; i++) {
            var text = clean(headerRowCells[i].textContent);
            var day = 0;
            for (var w = 0; w < WEEKDAY.length; w++) {
                if (text.indexOf(WEEKDAY[w]) !== -1) { day = w + 1; break; }
            }
            map.push(day > 0 ? day : 0);
        }
        return map;
    }

    // ---------- 解析一张课表 ----------
    function parseTimetable(doc, table) {
        var courses = [];
        var rows = table.rows;
        if (!rows || rows.length < 2) return courses;

        var dayMap = buildDayMap(rows[0].cells);
        var grid = buildGrid(table);

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
                var day = dayMap[c] || 0;
                if (day < 1 || day > 7) continue;

                var blocks;
                try { blocks = cell.td.querySelectorAll('div.kbcontent'); } catch (e) { continue; }
                for (var k = 0; k < blocks.length; k++) {
                    var div = blocks[k];
                    var st = div.getAttribute('style') || '';
                    if (/display:\s*none/i.test(st)) continue; // 跳过隐藏块

                    var name = '', teacher = '', position = '', weekSecText = '';
                    var fonts = div.querySelectorAll('font');
                    for (var fi = 0; fi < fonts.length; fi++) {
                        var title = fonts[fi].getAttribute('title') || '';
                        var fv = clean(fonts[fi].textContent);
                        if (!fv) continue;
                        if (!title && !name) {
                            name = fv; // 课程名 font 无 title
                        } else if (title.indexOf('教师') !== -1) {
                            teacher = teacher || fv;
                        } else if (title.indexOf('周次') !== -1 || title.indexOf('节次') !== -1) {
                            weekSecText = weekSecText || fv;
                        } else if (title.indexOf('教室') !== -1 || title.indexOf('地点') !== -1) {
                            position = position || fv;
                        }
                        // title=教学楼/通知单编号/班级/备注 忽略
                    }
                    if (!name) continue;

                    var info = parseWeekSec(weekSecText);
                    if (!info.weeks.length) continue;
                    var startSec = info.start, endSec = info.end;
                    if (!startSec || !endSec) {
                        var fb = rowRange[r];
                        if (fb) { startSec = fb.start; endSec = fb.end; }
                        else continue;
                    }
                    courses.push({
                        name: name,
                        teacher: teacher || '未安排',
                        position: position || '',
                        day: day,
                        startSection: startSec,
                        endSection: endSec,
                        weeks: info.weeks
                    });
                }
            }
        }
        return dedupe(courses);
    }

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

    function findTimetableIn(doc) {
        if (!doc || !doc.getElementById) return null;
        var t = doc.getElementById('timetable');
        if (t && t.rows && t.rows.length > 1) {
            var txt = t.textContent || '';
            if (txt.indexOf('星期一') !== -1) return t;
        }
        // 兜底：任意含 kbcontent 的表格
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
        return fetchDoc(KB_URL)
            .then(function (doc) {
                var t = findTimetableIn(doc);
                return t ? { doc: doc, table: t } : null;
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
                if (!isJwHost()) {
                    return alert(
                        '跳转到教务系统',
                        '当前不在湖北职业技术学院教务系统页面。\n\n' +
                        '点击「确定」将通过学校统一身份认证登录并直达课表页，\n' +
                        '登录完成后请再次点击「执行导入」。'
                    ).then(function () {
                        location.href = CAS_KB_URL;
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
                        '1. 已通过统一身份认证登录教务系统\n' +
                        '2. 已进入「学期理论课表」页面\n\n' +
                        '也可以先点下方「一键导航到课表」，进入后再点「执行导入」。'
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
                        return fail('本学期没有课程', '课表中没有解析到课程，请确认已进入课表页。');
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
                                var t = findTimetableIn(doc);
                                if (!t) {
                                    return fail('获取课表失败', '未能获取「' + chosen.label + '」的课表。');
                                }
                                var courses = dedupe(parseTimetable(doc, t));
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
                for (var i = 0; i < courses.length; i++) nameCount[courses[i].name] = true;
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
                var live = findTimetableIn(document);
                var info = { tables: live ? 1 : 0, blocks: live ? live.querySelectorAll('div.kbcontent').length : 0 };
                return alert(
                    '导入失败',
                    '错误信息：' + (error && error.message ? error.message : String(error)) + '\n\n' +
                    '诊断：课表表格 ' + info.tables + ' 个 / 课程块 ' + info.blocks + ' 个\n\n' +
                    '请确认已登录并进入「学期理论课表」后重试。',
                    '确定'
                );
            });
    }

    // ---------- 一键导航到课表 ----------
    window.shangkeNavigateToTimetable = function () {
        if (isJwHost()) {
            location.href = KB_URL;
        } else {
            location.href = CAS_KB_URL;
        }
        return true;
    };

    if (typeof window !== 'undefined') {
        window.shangkeImportEntry = runImport;
        window.hbvtcImport = runImport;
    }
})();
