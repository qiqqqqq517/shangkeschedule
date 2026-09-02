// 西南交通大学希望学院 URP 综合教务系统专用适配器
// 登录：http://119.6.110.75:9007/（Spring Security + 验证码，密码 hex_md5 后提交）
// 课表页：登录后进入「选课管理→本学期课表」 /student/courseSelect/thisSemesterCurriculum/index
// 页面含两张表：网格课表（table#courseTable）与「全部课程清单」列表（含 课程号/课程名/学分/教师/时间/地点 结构化列）。
// 本适配器优先解析课程清单表（数据最干净），未找到时回退网格课表文本解析。
// 清单表时间格式：1-16周>>星期二>>1-2节（一门课可能多行多时段，拆成多条课程）。
(function () {
    'use strict';

    var PLATFORM_NAME = '西南交通大学希望学院教务系统';
    var DAY_MAP = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7, '天': 7 };

    function showToast(msg) {
        try {
            if (window.shangkeBridge && window.shangkeBridge.showToast) window.shangkeBridge.showToast(msg);
            else if (window.Bridge && window.Bridge.showToast) window.Bridge.showToast(msg);
        } catch (e) {}
    }

    function cleanText(s) {
        return String(s == null ? '' : s).replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
    }

    function parseWeeks(s) {
        if (!s) return [];
        var weeks = {};
        var found = false;
        var isOdd = /[（(]\s*单\s*[）)]/.test(s);
        var isEven = /[（(]\s*双\s*[）)]/.test(s);
        var re = /(\d{1,2})\s*(?:[-~－—至到]\s*(\d{1,2}))?\s*周/g;
        var m;
        while ((m = re.exec(s)) !== null) {
            var start = parseInt(m[1], 10);
            var end = m[2] ? parseInt(m[2], 10) : start;
            if (isNaN(start) || start < 1) continue;
            if (isNaN(end) || end < start) end = start;
            if (end > 32) end = 32;
            for (var w = start; w <= end; w++) { weeks[w] = true; found = true; }
        }
        if (!found) return [];
        var list = Object.keys(weeks).map(function (k) { return parseInt(k, 10); }).sort(function (a, b) { return a - b; });
        if (isOdd) list = list.filter(function (w) { return w % 2 === 1; });
        else if (isEven) list = list.filter(function (w) { return w % 2 === 0; });
        return list;
    }

    function parseDay(s) {
        var m = String(s).match(/星期([一二三四五六日天])/);
        return m ? (DAY_MAP[m[1]] || 0) : 0;
    }

    function parseSections(s) {
        if (!s) return null;
        var m = String(s).match(/(\d{1,2})\s*[-~～－—至到]\s*(\d{1,2})\s*节/);
        if (m) return { start: parseInt(m[1], 10), end: parseInt(m[2], 10) };
        var sm = String(s).match(/(\d{1,2})\s*节/);
        if (sm) { var n = parseInt(sm[1], 10); return { start: n, end: n }; }
        return null;
    }

    // 收集主文档 + iframe
    function collectDocuments() {
        var docs = [document];
        var seen = {};
        function k(d) {
            try { return d.location ? d.location.href : (d.URL || ('d' + docs.length)); } catch (e) { return 'd' + Math.random(); }
        }
        seen[k(document)] = true;
        function push(d) {
            if (!d) return;
            var kk = k(d);
            if (seen[kk]) return;
            seen[kk] = true;
            docs.push(d);
        }
        function walk(root) {
            var els = [];
            try { els = Array.prototype.slice.call(root.querySelectorAll('iframe, frame')); } catch (e) { els = []; }
            for (var i = 0; i < els.length; i++) {
                var c = null;
                try { c = els[i].contentDocument || (els[i].contentWindow && els[i].contentWindow.document); } catch (e) { c = null; }
                if (c) { push(c); walk(c); }
            }
        }
        walk(document);
        return docs;
    }

    // 定位"全部课程清单"表（表头含 课程号/地点）
    function findCourseListTable() {
        var docs = collectDocuments();
        for (var i = 0; i < docs.length; i++) {
            try {
                var tables = docs[i].querySelectorAll('table');
                for (var j = 0; j < tables.length; j++) {
                    var head = '';
                    try {
                        var firstTr = tables[j].querySelector('tr');
                        if (firstTr) head = (firstTr.innerText || firstTr.textContent || '');
                    } catch (e) {}
                    if (head.indexOf('课程号') !== -1 && head.indexOf('时间') !== -1 && head.indexOf('地点') !== -1) {
                        return tables[j];
                    }
                }
            } catch (e) {}
        }
        return null;
    }

    // 从课程清单表提取课程（一门课可能有多条时间→多条课程）
    function extractFromList(listTable) {
        var courses = [];
        var seen = {};
        var trs = [];
        try { trs = Array.prototype.slice.call(listTable.querySelectorAll('tr')); } catch (e) { trs = []; }
        for (var r = 1; r < trs.length; r++) {
            var tds = [];
            try { tds = Array.prototype.slice.call(trs[r].querySelectorAll('td')); } catch (e) { tds = []; }
            if (tds.length < 14) continue;
            var cells = [];
            for (var c = 0; c < tds.length; c++) cells.push(cleanText(tds[c].innerText));
            var name = cells[1];
            if (!name) continue;
            var teacher = String(cells[9] || '').replace(/\*+$/g, '').trim();
            var credit = cleanText(cells[5] || '');
            var timeLines = String(cells[12] || '').split(/\r?\n/).map(function (s) { return cleanText(s); }).filter(function (s) { return s.length > 0; });
            var posLines = String(cells[13] || '').split(/\r?\n/).map(function (s) { return cleanText(s); }).filter(function (s) { return s.length > 0; });
            if (!timeLines.length) continue; // 未安排具体时间
            for (var ti = 0; ti < timeLines.length; ti++) {
                var tl = timeLines[ti];
                var pos = posLines[ti] || posLines[0] || '';
                var weeks = parseWeeks(tl);
                var day = parseDay(tl);
                var sec = parseSections(tl);
                if (!weeks.length || !day || !sec) continue;
                // 地点取"成都校区>>图书馆>>XX"最后一段
                var cleanPos = String(pos).split('>>').pop().trim();
                if (!cleanPos) cleanPos = pos;
                var key = name + '|' + day + '|' + sec.start + '|' + sec.end + '|' + weeks.join(',');
                if (seen[key]) continue;
                seen[key] = true;
                courses.push({
                    name: name,
                    teacher: teacher || '未知',
                    position: cleanPos || '待定',
                    day: day,
                    startSection: sec.start,
                    endSection: sec.end || sec.start,
                    weeks: weeks,
                    remark: credit ? ('学分：' + credit) : ''
                });
            }
        }
        return courses;
    }

    // 回退：网格课表文本解析（table#courseTable，节次/时间 + 星期列）
    function extractFromGrid() {
        var docs = collectDocuments();
        var gridTable = null;
        for (var i = 0; i < docs.length && !gridTable; i++) {
            try {
                var el = docs[i].querySelector('table#courseTable');
                if (el) gridTable = el;
            } catch (e) {}
        }
        if (!gridTable) return [];
        // 简化网格解析：按星期列 + 节次标签，读取单元格文本
        var courses = [];
        var seen = {};
        var WEEKNAMES = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
        var trs = [];
        try { trs = Array.prototype.slice.call(gridTable.querySelectorAll('tr')); } catch (e) { trs = []; }
        var dayCols = {};
        for (var r = 0; r < Math.min(trs.length, 3); r++) {
            var ths = [];
            try { ths = Array.prototype.slice.call(trs[r].querySelectorAll('th, td')); } catch (e) { ths = []; }
            for (var c = 0; c < ths.length; c++) {
                var t = cleanText(ths[c].innerText);
                for (var d = 0; d < 7; d++) {
                    if (t.indexOf(WEEKNAMES[d]) !== -1 && dayCols[d + 1] === undefined) dayCols[d + 1] = c;
                }
            }
            if (Object.keys(dayCols).length >= 4) break;
        }
        if (Object.keys(dayCols).length < 4) return [];
        for (var r2 = 1; r2 < trs.length; r2++) {
            var tds = [];
            try { tds = Array.prototype.slice.call(trs[r2].querySelectorAll('td')); } catch (e) { tds = []; }
            for (var d2 = 1; d2 <= 7; d2++) {
                var col = dayCols[d2];
                if (col === undefined || !tds[col]) continue;
                var cellText = cleanText(tds[col].innerText);
                if (!cellText || cellText.length < 2) continue;
                // 单元格内可能多门课，按空行分段
                var blocks = String(cellText).split(/\n\s*\n/).map(function (s) { return cleanText(s); }).filter(function (s) { return s.length > 0; });
                for (var b = 0; b < blocks.length; b++) {
                    var lines = blocks[b].split(/\n/).map(function (s) { return cleanText(s); }).filter(function (s) { return s.length > 0; });
                    if (!lines.length) continue;
                    var name = lines[0];
                    var teacher = '';
                    var position = '';
                    var weekSec = '';
                    for (var li = 1; li < lines.length; li++) {
                        if (/周/.test(lines[li])) weekSec = (weekSec ? weekSec + ' ' : '') + lines[li];
                        else if (/节/.test(lines[li])) weekSec = (weekSec ? weekSec + ' ' : '') + lines[li];
                        else if (/楼|室|馆|场|中心/.test(lines[li])) position = position || lines[li];
                        else teacher = teacher || lines[li];
                    }
                    var weeks = parseWeeks(weekSec);
                    var sec = parseSections(weekSec);
                    if (!weeks.length || !sec) continue;
                    var key = name + '|' + d2 + '|' + sec.start + '|' + sec.end + '|' + weeks.join(',');
                    if (seen[key]) continue;
                    seen[key] = true;
                    courses.push({
                        name: name,
                        teacher: teacher || '未知',
                        position: position || '待定',
                        day: d2,
                        startSection: sec.start,
                        endSection: sec.end || sec.start,
                        weeks: weeks,
                        remark: ''
                    });
                }
            }
        }
        return courses;
    }

    async function runImport() {
        try {
            var listTable = findCourseListTable();
            var courses = [];
            if (listTable) {
                courses = extractFromList(listTable);
            }
            if (!courses.length) {
                courses = extractFromGrid();
            }
            if (!courses.length) {
                showToast('未识别到课程，请先登录并进入「选课管理→本学期课表」页面，等待课程清单加载后再点击导入');
                return;
            }
            await window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(courses));
            showToast('成功解析 ' + courses.length + ' 门课程');
            if (window.shangkeBridge && window.shangkeBridge.notifyTaskCompletion) {
                window.shangkeBridge.notifyTaskCompletion();
            }
        } catch (e) {
            showToast('导入失败: ' + (e && e.message ? e.message : e));
        }
    }

    window.shangkeImportEntry = runImport;
    window.xwxyImport = runImport;
})();
