// URP教务系统通用适配器（DOM 矩阵解析版）
// 自动识别课表表格：星期表头 + 节次行，支持 rowspan/colspan 与 iframe 内课表。
(function () {
    'use strict';

    var PLATFORM_NAME = 'URP教务系统';
    var PREFERRED_SELECTORS = ['#kbgrid_table_0','.kb-table','table[id*=kb]','table[class*=kb]'];
    var BODY_KEYWORDS = ['URP','个人课表'];
    var URL_PATTERNS = [
        /urp/g,
        /jwgl/g,
        /jwc/g,
        /eam/g,
        /ehall/g,
        /sso/g
    ];
    var WEEKDAYS = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];

    function cleanText(s) {
        return String(s == null ? '' : s).replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
    }

    function cleanTeacher(name) {
        if (!name) return '';
        return String(name).replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim();
    }

    function cleanCourseName(name) {
        if (!name) return '';
        return String(name)
            .replace(/【[^】]*】/g, '')
            .replace(/[■◆▲●★▽▼☆★→↘↙]/g, '')
            .replace(/^\s*[0-9]{1,2}[、.\-]\s*/, '')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function showToast(msg) {
        try {
            if (window.shangkeBridge && window.shangkeBridge.showToast) window.shangkeBridge.showToast(msg);
            else if (window.Bridge && window.Bridge.showToast) window.Bridge.showToast(msg);
        } catch (e) {}
    }

    function matchesPlatformHint() {
        try {
            var u = window.location.href || '';
            for (var i = 0; i < URL_PATTERNS.length; i++) {
                URL_PATTERNS[i].lastIndex = 0;
                if (URL_PATTERNS[i].test(u)) return true;
            }
            var body = (document.body && document.body.innerText) || '';
            for (var j = 0; j < BODY_KEYWORDS.length; j++) {
                if (body.indexOf(BODY_KEYWORDS[j]) !== -1) return true;
            }
        } catch (e) {}
        return false;
    }

    function parseWeeks(s) {
        if (!s) return [];
        var weeks = {};
        var found = false;
        var isOdd = /[（(]\s*单\s*[）)]/.test(s);
        var isEven = /[（(]\s*双\s*[）)]/.test(s);
        var re = /(\d+)\s*(?:[-~－—至到]\s*(\d+))?\s*周/g;
        var m;
        while ((m = re.exec(s)) !== null) {
            var start = parseInt(m[1], 10);
            var end = m[2] ? parseInt(m[2], 10) : start;
            if (isNaN(start) || start < 1) continue;
            if (isNaN(end) || end < start) end = start;
            if (end > 32) end = 32;
            for (var w = start; w <= end; w++) {
                weeks[w] = true;
                found = true;
            }
        }
        if (!found) {
            var single = s.match(/(\d+)\s*周/g);
            if (single) {
                for (var i = 0; i < single.length; i++) {
                    var nn = parseInt(single[i], 10);
                    if (nn >= 1 && nn <= 32) { weeks[nn] = true; found = true; }
                }
            }
        }
        if (!found) return [];
        var list = Object.keys(weeks).map(function (k) { return parseInt(k, 10); }).sort(function (a, b) { return a - b; });
        if (isOdd) list = list.filter(function (w) { return w % 2 === 1; });
        else if (isEven) list = list.filter(function (w) { return w % 2 === 0; });
        return list;
    }

    function rawTextOf(cell) {
        if (!cell) return '';
        if (cell.text !== undefined && String(cell.text).trim() !== '') {
            return String(cell.text).replace(/\u00a0/g, ' ');
        }
        if (cell.html !== undefined && String(cell.html).trim() !== '') {
            return String(cell.html)
                .replace(/<br\s*\/?>/gi, '\n')
                .replace(/<\/(div|p|td|tr|span)>/gi, '\n')
                .replace(/<[^>]+>/g, '')
                .replace(/\u00a0/g, ' ');
        }
        var text = cell.innerText;
        if (text === undefined || text === null || String(text).trim() === '') {
            text = (cell.innerHTML || '')
                .replace(/<br\s*\/?>/gi, '\n')
                .replace(/<\/(div|p|td|tr|span)>/gi, '\n')
                .replace(/<[^>]+>/g, '');
        }
        return String(text).replace(/\u00a0/g, ' ');
    }

    function cellLines(cell) {
        return rawTextOf(cell)
            .split(/\r?\n/)
            .map(function (s) { return cleanText(s); })
            .filter(function (s) { return s.length > 0; });
    }

    function splitBlocks(cell) {
        var text = rawTextOf(cell).replace(/\r/g, '\n');
        var parts = text.split(/\n\s*\n|\n[-=_]{3,}\n|(?=\n【)/);
        return parts.map(function (p) { return cleanText(p); }).filter(function (p) { return p.length > 0; });
    }

    function parseTableToGrid(table) {
        var trs = Array.prototype.slice.call(table.querySelectorAll('tr'));
        var grid = [];
        for (var r = 0; r < trs.length; r++) {
            var tds = Array.prototype.slice.call(trs[r].querySelectorAll('td, th'));
            var c = 0;
            grid[r] = grid[r] || [];
            for (var i = 0; i < tds.length; i++) {
                var td = tds[i];
                while (grid[r][c] !== undefined) c++;
                var rowspan = parseInt(td.getAttribute('rowspan')) || 1;
                var colspan = parseInt(td.getAttribute('colspan')) || 1;
                if (rowspan < 1) rowspan = 1;
                if (colspan < 1) colspan = 1;
                for (var rr = 0; rr < rowspan; rr++) {
                    for (var cc = 0; cc < colspan; cc++) {
                        grid[r + rr] = grid[r + rr] || [];
                        grid[r + rr][c + cc] = {
                            html: td.innerHTML || '',
                            text: rawTextOf(td),
                            td: td,
                            isOrigin: rr === 0 && cc === 0,
                            rowspan: rowspan,
                            colspan: colspan
                        };
                    }
                }
                c += colspan;
            }
        }
        return grid;
    }

    function collectDocuments() {
        var docs = [document];
        try {
            var frames = document.querySelectorAll('iframe');
            for (var i = 0; i < frames.length; i++) {
                var d = null;
                try {
                    d = frames[i].contentDocument;
                    if (!d && frames[i].contentWindow) d = frames[i].contentWindow.document;
                } catch (e) {}
                if (d) docs.push(d);
            }
        } catch (e) {}
        return docs;
    }

    function tablesOf(doc) {
        try { return doc.querySelectorAll('table'); } catch (e) { return []; }
    }

    function scheduleScore(text) {
        var score = 0;
        if (text.indexOf('星期一') !== -1) score += 4;
        if (text.indexOf('节次') !== -1) score += 3;
        if (text.indexOf('上午') !== -1) score += 2;
        if (text.indexOf('课表') !== -1 || text.indexOf('课程') !== -1) score += 1;
        return score;
    }

    function findScheduleTable() {
        var docs = collectDocuments();
        var best = null;
        var bestScore = 0;
        for (var d = 0; d < docs.length; d++) {
            var doc = docs[d];
            for (var s = 0; s < PREFERRED_SELECTORS.length; s++) {
                var el = null;
                try { el = doc.querySelector(PREFERRED_SELECTORS[s]); } catch (e) {}
                if (el) {
                    var st = scheduleScore(el.innerText || el.textContent || '');
                    if (st > bestScore) { bestScore = st; best = el; }
                }
            }
            var tables = tablesOf(doc);
            for (var i = 0; i < tables.length; i++) {
                var text = tables[i].innerText || tables[i].textContent || '';
                var score = scheduleScore(text);
                if (score > bestScore) {
                    bestScore = score;
                    best = tables[i];
                }
            }
        }
        return { table: best, score: bestScore };
    }

    function detectDayColumns(grid) {
        for (var r = 0; r < Math.min(grid.length, 4); r++) {
            var row = grid[r] || [];
            var hit = {};
            for (var c = 0; c < row.length; c++) {
                if (!row[c]) continue;
                var text = row[c].text || '';
                for (var d = 1; d <= 7; d++) {
                    if (text.indexOf(WEEKDAYS[d - 1]) !== -1 || text.indexOf('周' + String(d)) !== -1) {
                        if (hit[d] === undefined) hit[d] = c;
                    }
                }
            }
            var keys = Object.keys(hit);
            if (keys.length >= 4) return { headerRow: r, dayCols: hit };
        }
        return null;
    }

    function inferDayColumns(grid) {
        var width = (grid[0] ? grid[0].length : 0);
        var offset = width >= 8 ? 1 : 0;
        var cols = {};
        for (var d = 1; d <= 7; d++) cols[d] = offset + d - 1;
        return cols;
    }

    function sectionOfRow(rowArr, rowIndex, headerRow, minDayCol) {
        var labelIdx = minDayCol > 0 ? minDayCol - 1 : 0;
        var labelCell = rowArr[labelIdx];
        var labelText = labelCell ? (labelCell.text || '') : '';
        var m = labelText.match(/(\d+)\s*(?:[-~－—至到]\s*(\d+))?\s*节/);
        if (m) {
            return { start: parseInt(m[1], 10) || null, end: (m[2] ? parseInt(m[2], 10) : null) };
        }
        var first = labelText.match(/(\d+)/);
        if (first) {
            var n = parseInt(first[0], 10);
            if (n >= 1 && n <= 24) return { start: n, end: null };
        }
        var baseRow = headerRow >= 0 ? rowIndex - headerRow : rowIndex;
        var start = Math.max(1, baseRow * 2 - 1);
        return { start: start, end: null };
    }

    function pickWeeksLine(lines, rawText) {
        for (var i = 0; i < lines.length; i++) {
            if (lines[i].indexOf('周') !== -1) return lines[i];
        }
        var m = rawText.match(/(?:周|星期)[^\n;；]*/);
        return m ? m[0] : '';
    }

    function classifyBlock(lines, rawText, fallbackSection) {
        if (lines.length === 0) return null;
        var name = cleanCourseName(lines[0]);
        if (!name) return null;
        var teacher = '';
        var position = '';
        var weekStr = pickWeeksLine(lines.slice(1), rawText);
        var sections = { start: fallbackSection.start, end: fallbackSection.end || fallbackSection.start };

        for (var i = 1; i < lines.length; i++) {
            var t = cleanText(lines[i]);
            if (!t) continue;
            if (/老师|教师|教授|讲师/.test(t)) {
                teacher = teacher || cleanTeacher(t.replace(/老师|教师|教授|讲师/g, ''));
            } else if (/教室|教学楼|楼|室|校区|机房|地点/.test(t)) {
                position = position || t;
            } else if (t.indexOf('周') !== -1 || (i === 1 && /\d/.test(t) && /周/.test(t))) {
                // already caught by pickWeeksLine
            } else if (t.length <= 4 && !/\d/.test(t)) {
                teacher = teacher || cleanTeacher(t);
            } else {
                position = position || t;
            }
            if (/周/.test(t)) weekStr = weekStr || t;
        }

        var secLine = lines.slice(1).find(function (l) { return /节/.test(l); });
        if (secLine) {
            var sm = secLine.match(/(\d+)\s*(?:[-~－—至到]\s*(\d+))?\s*节/);
            if (sm) {
                sections.start = parseInt(sm[1], 10);
                sections.end = sm[2] ? parseInt(sm[2], 10) : sections.start;
            }
        }

        var weeks = parseWeeks(weekStr);
        if (weeks.length === 0) weeks = parseWeeks(rawText);

        return {
            name: name,
            teacher: teacher || '未知',
            position: position || '待定',
            day: 0,
            startSection: sections.start,
            endSection: sections.end,
            weeks: weeks,
            remark: rawText.slice(0, 200)
        };
    }

    function extractCourses(table) {
        var grid = parseTableToGrid(table);
        if (!grid.length) return [];
        var header = detectDayColumns(grid);
        var headerRow = header ? header.headerRow : 0;
        var dayCols = header ? header.dayCols : inferDayColumns(grid);
        var minDayCol = Infinity;
        for (var d = 1; d <= 7; d++) if (dayCols[d] !== undefined && dayCols[d] < minDayCol) minDayCol = dayCols[d];
        if (minDayCol === Infinity) minDayCol = 0;

        var courses = [];
        for (var r = headerRow + 1; r < grid.length; r++) {
            var rowArr = grid[r] || [];
            if (!rowArr.length) continue;
            var fallbackSection = sectionOfRow(rowArr, r, headerRow, minDayCol);
            for (var d = 1; d <= 7; d++) {
                var c = dayCols[d];
                if (c === undefined) continue;
                var cell = rowArr[c];
                if (!cell || !cell.isOrigin) continue;
                if (!cell.text && !cell.html) continue;

                var blocks = splitBlocksRaw(rawTextOf(cell));
                for (var b = 0; b < blocks.length; b++) {
                    var lines = blocks[b];
                    var blockText = lines.join(' ');
                    var parsed = classifyBlock(lines, blockText, fallbackSection);
                    if (!parsed) continue;
                    if (parsed.daysIncorrect) continue;
                    if (parsed.weeks.length === 0) continue;
                    parsed.day = d;
                    courses.push(parsed);
                }
            }
        }
        return dedupeCourses(courses);
    }

    function splitBlocksRaw(text) {
        var t = String(text || '').replace(/\r/g, '\n');
        var parts = t.split(/\n\s*\n|\n[-=_]{3,}\n/);
        return parts.map(function (p) {
            return p.split(/\n/).map(function (s) { return cleanText(s); }).filter(function (s) { return s.length > 0; });
        }).filter(function (lines) { return lines.length > 0; });
    }

    function dedupeCourses(courses) {
        var seen = {};
        var out = [];
        for (var i = 0; i < courses.length; i++) {
            var course = courses[i];
            var key = course.name + '|' + course.teacher + '|' + course.position + '|' + course.day + '|' + course.startSection + '|' + course.endSection + '|' + course.weeks.join(',');
            if (seen[key]) continue;
            seen[key] = true;
            out.push(course);
        }
        return out;
    }

    async function runImport() {
        try {
            var found = findScheduleTable();
            if (!found.table) {
                showToast('未找到课表表格，请进入URP教务系统的学生个人课表页面并等待表格加载后再试');
                return;
            }
            var courses = extractCourses(found.table);
            if (!courses.length) {
                showToast('没有识别出有效课程。请停留在课表页面（含“星期一”表头）后重新点击导入');
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
    if (matchesPlatformHint()) {
        showToast('检测到URP教务系统，点击导入按钮抓取课表');
    }
})();
