// 强智教务系统通用适配器（DOM 矩阵解析版）
// 自动识别课表表格：星期表头 + 节次行，支持 rowspan/colspan 与 iframe 内课表。
(function () {
    'use strict';

    var PLATFORM_NAME = '强智教务系统';
    var PREFERRED_SELECTORS = ['#kbtable','.timetable','#kbgrid','table[id*=kb]','table[class*=kb]'];
    var BODY_KEYWORDS = ['强智','kbcontent'];
    var URL_PATTERNS = [
        /jwgl/g,
        /jwxt/g,
        /jsxsd/g,
        /jsxs/g,
        /qiangzhi/g
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
        var isOdd = /[（(]\s*单(?:周)?\s*[）)]/.test(s);
        var isEven = /[（(]\s*双(?:周)?\s*[）)]/.test(s);

        function addRange(a, b) {
            var start = parseInt(a, 10);
            if (isNaN(start) || start < 1 || start > 32) return;
            var end = b ? parseInt(b, 10) : start;
            if (isNaN(end) || end < start || end > 32) end = start;
            for (var w = start; w <= end; w++) { weeks[w] = true; found = true; }
        }

        // 强智新版："1-14,17-18(周)[01-02节]"、"1,3-9,11-14(周)"，多段逗号分隔、周字在括号内。
        // 先去掉节次方括号（避免节次数字混入），再截取"（周）/周"之前的周次主体逐段解析。
        var core = String(s).replace(/\[[^\]]*\]/g, ' ');
        var weekMark = core.search(/[（(]?\s*[单双]?\s*周/);
        var head = weekMark >= 0 ? core.slice(0, weekMark) : core;
        var rangeRe = /(\d{1,2})\s*(?:[-~－—至到]\s*(\d{1,2}))?/g;
        var mm;
        while ((mm = rangeRe.exec(head)) !== null) {
            addRange(mm[1], mm[2]);
        }

        // 兼容旧格式兜底："1-16周"、"第3周"、"5周"（数字后紧跟周字）
        if (!found) {
            var oldRe = /(\d{1,2})\s*(?:[-~－—至到]\s*(\d{1,2}))?\s*周/g;
            while ((mm = oldRe.exec(core)) !== null) addRange(mm[1], mm[2]);
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
                .replace(/&nbsp;/gi, ' ')
                .replace(/\u00a0/g, ' ');
        }
        var text = cell.innerText;
        if (text === undefined || text === null || String(text).trim() === '') {
            text = (cell.innerHTML || '')
                .replace(/<br\s*\/?>/gi, '\n')
                .replace(/<\/(div|p|td|tr|span)>/gi, '\n')
                .replace(/<[^>]+>/g, '')
                .replace(/&nbsp;/gi, ' ');
        }
        return String(text).replace(/&nbsp;/gi, ' ').replace(/\u00a0/g, ' ');
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
        // 强智 xsMain.jsp 等页面常用 frameset/<frame> 或多层 <iframe> 嵌套课表，
        // 统一收集主文档 + 所有同源子框架文档（递归、去重）。
        var docs = [document];
        var seen = {};
        function docKey(d) {
            try { return d.location ? d.location.href : (d.URL || ('doc' + docs.length)); }
            catch (e) { return 'doc' + docs.length + Math.random(); }
        }
        seen[docKey(document)] = true;
        function pushDoc(d) {
            if (!d) return;
            var k = docKey(d);
            if (seen[k]) return;
            seen[k] = true;
            docs.push(d);
        }
        function walk(rootDoc) {
            var els = [];
            try { els = Array.prototype.slice.call(rootDoc.querySelectorAll('iframe, frame')); }
            catch (e) { els = []; }
            for (var i = 0; i < els.length; i++) {
                var child = null;
                try { child = els[i].contentDocument || (els[i].contentWindow && els[i].contentWindow.document); }
                catch (e) { child = null; }
                if (child) { pushDoc(child); walk(child); }
            }
        }
        walk(document);
        // window.frames 兜底：覆盖 frameset 中通过 name 访问、DOM 查询可能遗漏的 <frame>
        try {
            for (var f = 0; f < window.frames.length; f++) {
                var fd = null;
                try { fd = window.frames[f].document; } catch (e) { fd = null; }
                if (fd) { pushDoc(fd); walk(fd); }
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

    function tableDataRowCount(table) {
        try {
            var trs = table.querySelectorAll('tr');
            var n = 0;
            for (var i = 0; i < trs.length; i++) {
                if (trs[i].querySelector('td')) n++;
            }
            return n;
        } catch (e) { return 0; }
    }

    function findScheduleTable() {
        var docs = collectDocuments();
        var best = null;
        var bestScore = 0;
        var bestRows = -1;
        function consider(el) {
            if (!el) return;
            var text = el.innerText || el.textContent || '';
            var score = scheduleScore(text);
            var dataRows = tableDataRowCount(el);
            // 只有表头、没有任何 td 数据行的是空壳模板，降权以避免误选
            if (dataRows === 0) score -= 5;
            if (score > bestScore || (score === bestScore && score > 0 && dataRows > bestRows)) {
                bestScore = score;
                best = el;
                bestRows = dataRows;
            }
        }
        for (var d = 0; d < docs.length; d++) {
            var doc = docs[d];
            for (var s = 0; s < PREFERRED_SELECTORS.length; s++) {
                var el = null;
                try { el = doc.querySelector(PREFERRED_SELECTORS[s]); } catch (e) {}
                consider(el);
            }
            var tables = tablesOf(doc);
            for (var i = 0; i < tables.length; i++) consider(tables[i]);
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
        var bigSection = labelText.match(/第\s*([一二三四五六七八九十])\s*大节/);
        if (bigSection) {
            var bigMap = { '一': 1, '二': 3, '三': 5, '四': 7, '五': 9, '六': 11, '七': 13 };
            var bigStart = bigMap[bigSection[1]];
            if (bigStart) return { start: bigStart, end: bigStart + 1 };
        }
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

    // 从"[01-02节]"之类文本提取起止小节
    function parseSectionFromText(text, fallback) {
        if (text) {
            var sm = String(text).match(/(\d{1,2})\s*(?:[-~－—至到]\s*(\d{1,2}))?\s*节/);
            if (sm) {
                var st = parseInt(sm[1], 10);
                var en = sm[2] ? parseInt(sm[2], 10) : st;
                return { start: st, end: en };
            }
        }
        return fallback || { start: 1, end: 1 };
    }

    // 解析强智新版语义化单元格：div.kbcontent 内裸文本为课程名，font[title] 标注老师/周次(节次)/教室
    function parseSemanticCell(td, day, fallbackSection) {
        var nodes = [];
        try { nodes = Array.prototype.slice.call(td.querySelectorAll('div.kbcontent, div[class*="kbcontent"]')); }
        catch (e) { return null; }
        var divs = [];
        for (var i = 0; i < nodes.length; i++) {
            var style = nodes[i].getAttribute('style') || '';
            if (/display:\s*none/i.test(style)) continue; // 隐藏的历史周次备份 kbcontent1
            var t = cleanText(nodes[i].textContent || nodes[i].innerText || '');
            if (!t || t === '&nbsp;') continue;
            divs.push(nodes[i]);
        }
        if (!divs.length) return null;

        var result = [];
        for (var k = 0; k < divs.length; k++) {
            var div = divs[k];
            var name = '';
            var teacher = '';
            var position = '';
            var weekSecText = '';

            // 课程名：div 内第一个有效裸文本节点
            var childNodes = div.childNodes;
            for (var cn = 0; cn < childNodes.length; cn++) {
                if (childNodes[cn].nodeType === 3) {
                    var nt = cleanText(childNodes[cn].nodeValue);
                    if (nt && nt !== '&nbsp;') { name = cleanCourseName(nt); break; }
                }
            }

            var fonts = div.querySelectorAll('font[title]');
            for (var fi = 0; fi < fonts.length; fi++) {
                var title = fonts[fi].getAttribute('title') || '';
                var ftext = cleanText(fonts[fi].textContent || fonts[fi].innerText || '');
                if (!ftext) continue;
                if (title.indexOf('老师') !== -1 || title.indexOf('教师') !== -1) {
                    teacher = teacher || cleanTeacher(ftext);
                } else if (title.indexOf('周次') !== -1 || title.indexOf('节次') !== -1) {
                    weekSecText = ftext;
                } else if (title.indexOf('教室') !== -1 || title.indexOf('地点') !== -1) {
                    position = position || ftext;
                }
                // "行政职务(党员情况)"等其余 title 忽略
            }

            if (!name) continue;
            var weeks = parseWeeks(weekSecText);
            if (!weeks.length) continue;
            var sec = parseSectionFromText(weekSecText, fallbackSection);
            result.push({
                name: name,
                teacher: teacher || '未知',
                position: position || '待定',
                day: day,
                startSection: sec.start,
                endSection: sec.end || sec.start,
                weeks: weeks,
                remark: cleanText(div.textContent || '').slice(0, 200)
            });
        }
        return result.length ? result : null;
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

            // 跳过"备注/说明"整行：首列为备注，其余是整周说明文字，并非课程单元格
            var labelCell = minDayCol > 0 ? rowArr[minDayCol - 1] : rowArr[0];
            var rowLabel = labelCell ? (labelCell.text || '') : '';
            if (/备注|说明|^\s*注\s*[:：]/.test(rowLabel)) continue;

            for (var d = 1; d <= 7; d++) {
                var c = dayCols[d];
                if (c === undefined) continue;
                var cell = rowArr[c];
                if (!cell || !cell.isOrigin) continue;

                var cellText = rawTextOf(cell);
                if (!cellText || !/[一-龥A-Za-z]/.test(cellText)) continue; // 纯空白/&nbsp;/符号跳过

                // 优先：强智新版语义化结构
                var semantic = cell.td ? parseSemanticCell(cell.td, d, fallbackSection) : null;
                if (semantic && semantic.length) {
                    for (var si = 0; si < semantic.length; si++) courses.push(semantic[si]);
                    continue;
                }

                // 回退：通用文本块解析（兼容其他强智版本）
                var blocks = splitBlocksRaw(cellText);
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
                showToast('未找到课表表格，请进入强智教务系统的学生个人课表页面并等待表格加载后再试');
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
        showToast('检测到强智教务系统，点击导入按钮抓取课表');
    }
})();
