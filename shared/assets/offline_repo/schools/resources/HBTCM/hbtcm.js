// 康普科技「全新教务」系统通用适配器
// 适用于河北中医药大学等部署康普教务系统的高校。
// 课表由服务器端直接渲染为 HTML 表格（无需接口），直接解析 DOM。
// 用户需先登录教务系统并进入「教务信息-个人课表」页面，再点击导入。

(function () {
    'use strict';

    var WEEK_NAMES = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];

    function clean(s) {
        return (s || '').replace(/\s+/g, ' ').trim();
    }

    function parseSections(info) {
        var m = (info || '').match(/(\d+)\s*-\s*(\d+)\s*节/);
        if (m) {
            return { start: parseInt(m[1], 10), end: parseInt(m[2], 10) };
        }
        var s = (info || '').match(/(\d+)\s*节/);
        if (s) {
            var n = parseInt(s[1], 10);
            return { start: n, end: n };
        }
        return null;
    }

    // 解析周次：支持 1-17周 / 1-14,16周 / 9,15,17周 / 12周
    function parseWeeks(info) {
        if (!info) return [];
        var afterSec = String(info).split('节').pop() || '';
        var m = afterSec.match(/([\d,\-]+)\s*周/);
        if (!m) return [];
        var weeks = [];
        var seen = {};
        m[1].split(',').forEach(function (part) {
            part = part.trim();
            if (!part) return;
            var r = part.match(/^(\d+)\s*-\s*(\d+)$/);
            if (r) {
                var a = parseInt(r[1], 10);
                var b = parseInt(r[2], 10);
                if (isNaN(a) || isNaN(b) || a < 1 || b < a) return;
                for (var w = a; w <= b; w++) {
                    if (!seen[w]) { seen[w] = true; weeks.push(w); }
                }
            } else if (/^\d+$/.test(part)) {
                var w = parseInt(part, 10);
                if (w > 0 && !seen[w]) { seen[w] = true; weeks.push(w); }
            }
        });
        return weeks.sort(function (a, b) { return a - b; });
    }

    function cellText(cell) {
        if (!cell) return '';
        var t = cell.innerText;
        if (t === undefined || t === null || String(t).trim() === '') {
            t = (cell.innerHTML || '').replace(/<br\s*\/?>/gi, '\n').replace(/<[^>]+>/g, '\n');
        }
        return clean(t);
    }

    // 还原表格二维结构，处理 rowspan / colspan
    function buildGrid(table) {
        var grid = [];
        var occupied = {};
        var rows = table.rows;
        for (var r = 0; r < rows.length; r++) {
            var cells = rows[r].cells;
            var col = 0;
            for (var i = 0; i < cells.length; i++) {
                var cell = cells[i];
                while (occupied[r + ':' + col]) col++;
                var rs = parseInt(cell.getAttribute('rowspan') || '1', 10);
                var cs = parseInt(cell.getAttribute('colspan') || '1', 10);
                if (isNaN(rs) || rs < 1) rs = 1;
                if (isNaN(cs) || cs < 1) cs = 1;
                for (var rr = 0; rr < rs; rr++) {
                    for (var cc = 0; cc < cs; cc++) {
                        occupied[(r + rr) + ':' + (col + cc)] = true;
                        if (!grid[r + rr]) grid[r + rr] = [];
                        grid[r + rr][col + cc] = cell;
                    }
                }
                col += cs;
            }
        }
        return grid;
    }

    // 收集顶层文档与所有同源 iframe 文档（教务系统常把功能页放在 iframe 内）
    function collectDocuments() {
        var docs = [document];
        var frames = [];
        try { frames = document.querySelectorAll('iframe'); } catch (e) { frames = []; }
        for (var i = 0; i < frames.length; i++) {
            try {
                var d = frames[i].contentDocument;
                if (!d) {
                    var w = frames[i].contentWindow;
                    if (w) d = w.document;
                }
                if (d) docs.push(d);
            } catch (e) {
                // 跨域 iframe 无法访问，忽略
            }
        }
        return docs;
    }

    function findScheduleTable() {
        var docs = collectDocuments();
        var best = null;
        var bestScore = 0;
        var bestRows = -1;
        for (var di = 0; di < docs.length; di++) {
            var tables = docs[di].querySelectorAll('table');
            for (var i = 0; i < tables.length; i++) {
                var text = tables[i].innerText || tables[i].textContent || '';
                var score = 0;
                if (text.indexOf('星期一') !== -1) score += 4;
                if (text.indexOf('节') !== -1) score += 2;
                if (text.indexOf('周') !== -1) score += 1;
                // 课表常被外层布局表格包裹，评分相同时选行数更多（更内层）的表格
                var rows = tables[i].rows ? tables[i].rows.length : 0;
                if (score > bestScore || (score === bestScore && rows > bestRows)) {
                    bestScore = score;
                    bestRows = rows;
                    best = tables[i];
                }
            }
        }
        return bestScore >= 5 ? best : null;
    }

    function extractCourseFromAnchor(a, day, fallbackSection, out) {
        var lines = (a.innerText || '').split(/\r?\n/).map(function (s) { return clean(s); }).filter(function (s) { return s.length; });
        if (lines.length < 2) return;

        var name = lines[0];
        if (!name || name.length < 2) return;
        var info = lines[1];
        var position = lines.length >= 3 ? lines[2] : '';

        var sections = parseSections(info);
        if (!sections) {
            if (!fallbackSection) return;
            sections = { start: fallbackSection, end: fallbackSection };
        }

        out.push({
            name: clean(name),
            teacher: '',
            position: position,
            day: day,
            startSection: sections.start,
            endSection: sections.end,
            weeks: parseWeeks(info),
            remark: ''
        });
    }

    function extractCourseFromText(text, day, fallbackSection, out) {
        var parts = String(text).split(/\n+/).map(clean).filter(function (s) { return s.length; });
        if (parts.length < 2) return;
        var name = parts[0];
        var info = parts[1];
        var position = parts[2] || '';
        if (!name || !info) return;
        var sec = parseSections(info);
        if (!sec) {
            if (!fallbackSection) return;
            sec = { start: fallbackSection, end: fallbackSection };
        }
        out.push({
            name: clean(name),
            teacher: '',
            position: position,
            day: day,
            startSection: sec.start,
            endSection: sec.end,
            weeks: parseWeeks(info),
            remark: ''
        });
    }

    function importCourses() {
        var table = findScheduleTable();
        if (!table) {
            Bridge.showToast('未找到课表，请登录后进入「教务信息-个人课表」页面再点导入');
            return;
        }

        var grid = buildGrid(table);
        var headerRow = -1;
        var dayColByDay = {};
        var dayByCol = {};
        var sectionCol = -1;

        for (var r = 0; r < grid.length; r++) {
            var row = grid[r] || [];
            for (var c = 0; c < row.length; c++) {
                var cell = row[c];
                if (!cell) continue;
                var text = cellText(cell);
                if (!text) continue;

                if (sectionCol === -1 && /^第\s*\d+\s*节/.test(text)) {
                    sectionCol = c;
                }
                for (var d = 0; d < WEEK_NAMES.length; d++) {
                    if (text === WEEK_NAMES[d] || text.indexOf(WEEK_NAMES[d]) === 0) {
                        dayColByDay[d + 1] = c;
                        if (headerRow === -1) headerRow = r;
                    }
                }
            }
        }

        if (headerRow === -1 || Object.keys(dayColByDay).length === 0) {
            Bridge.showToast('未识别到课表表头，请确认已进入「个人课表」页面');
            return;
        }
        for (var key in dayColByDay) {
            if (Object.prototype.hasOwnProperty.call(dayColByDay, key)) {
                dayByCol[dayColByDay[key]] = parseInt(key, 10);
            }
        }

        var courses = [];
        var handled = [];
        var startRow = headerRow + 1;

        for (var r2 = startRow; r2 < grid.length; r2++) {
            var row2 = grid[r2] || [];

            var secCellIndex = -1;
            var domRow = table.rows[r2];
            if (domRow) {
                for (var ci = 0; ci < domRow.cells.length; ci++) {
                    if (/^第\s*\d+\s*节/.test(cellText(domRow.cells[ci]))) {
                        secCellIndex = ci;
                        break;
                    }
                }
            }

            var fallbackSection = null;
            if (sectionCol >= 0 && row2[sectionCol]) {
                var sn = parseInt(cellText(row2[sectionCol]).replace(/[^\d]/g, ''), 10);
                if (!isNaN(sn) && sn >= 1 && sn <= 24) fallbackSection = sn;
            }

            // 该行第一个「新出现」课程单元格的视觉星期相对星期一的偏移；
            // 当周一/周二等列被上一行 rowspan 占位时，行内序号会比真实星期少若干位。
            var rowDelta = 0;
            for (var cc = 0; cc < row2.length; cc++) {
                if (cc === sectionCol) continue;
                var fc = row2[cc];
                if (!fc) continue;
                if (handled.indexOf(fc) !== -1) continue;
                var fct = cellText(fc);
                if (!fct || /上午|下午|晚上/.test(fct)) continue;
                var fcd = dayByCol[cc];
                if (fcd !== undefined) rowDelta = fcd - 1;
                break;
            }

            for (var col = 0; col < row2.length; col++) {
                if (col === sectionCol) continue;
                var target = row2[col];
                if (!target) continue;
                if (handled.indexOf(target) !== -1) continue;
                handled.push(target);

                var renderDay = dayByCol[col];

                // 用行内顺序推算星期，叠加 rowDelta 修正被上一行 rowspan 占掉的前置天
                var cellDay;
                if (secCellIndex !== -1 && typeof target.cellIndex === 'number' && target.cellIndex > secCellIndex) {
                    cellDay = (target.cellIndex - secCellIndex) + rowDelta;
                    if (cellDay < 1 || cellDay > 7) cellDay = undefined;
                }

                var day = renderDay;
                // 渲染列与行内顺序不一致，说明该课被其他 rowspan 挤错了列，采用行内顺序结果
                if (day === undefined || (cellDay !== undefined && day !== cellDay)) {
                    day = cellDay;
                }
                if (day === undefined) continue;

                var anchors = target.querySelectorAll ? target.querySelectorAll('a') : [];
                if (anchors.length) {
                    for (var x = 0; x < anchors.length; x++) {
                        extractCourseFromAnchor(anchors[x], day, fallbackSection, courses);
                    }
                } else {
                    var t2 = cellText(target);
                    if (t2) extractCourseFromText(t2, day, fallbackSection, courses);
                }
            }
        }

        if (courses.length === 0) {
            Bridge.showToast('未解析到课程，请确认已打开「个人课表」页面');
            return;
        }

        Bridge.saveImportedCourses(JSON.stringify(courses));
        Bridge.showToast('成功解析 ' + courses.length + ' 门课程，正在导入...');
    }

    window.kangpuImport = importCourses;
    window.shangkeImportEntry = importCourses;

    if (findScheduleTable()) {
        Bridge.showToast('检测到康普教务课表，点击导入按钮抓取课表');
    }
})();
