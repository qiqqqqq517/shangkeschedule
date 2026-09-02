// 沈阳农业大学 URP 教务系统专用适配器
// 入口：校外走 WebVPN（https://webvpn.syau.edu.cn/login），校内走统一认证（https://pass.syau.edu.cn/tpass/login）
// 课表页：登录后进入「选课管理→本学期课表」 /student/courseSelect/thisSemesterCurriculum/index
// 说明：实验课（如"动物生理学实验"）已作为课程块直接排在本学期课表网格 table#courseTable 中，
//       与理论课一并显示，故本适配器只解析理论课网格即可完整覆盖实验课，无需再单独解析实验课/实习课表，
//       避免重复导入。实习课本学期无数据（页面显示"暂时未找到实习课安排"），同样无需单独处理。
(function () {
    'use strict';

    var PLATFORM_NAME = '沈阳农业大学教务系统';
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

    // 周次解析：支持 "1-5,8-10周上" "第5周" "2,4,8,10,12,14周上" "21周上" "第3-4周" 等
    function parseWeeks(s) {
        if (!s) return [];
        var weeks = {};
        var found = false;
        var isOdd = /[（(]\s*单\s*[）)]/.test(s);
        var isEven = /[（(]\s*双\s*[）)]/.test(s);
        var ms = String(s).match(/[\d,\-~～－—至到]+\s*周/g);
        if (!ms) return [];
        for (var i = 0; i < ms.length; i++) {
            var part = ms[i].replace(/周.*$/, '');
            var segs = part.split(/[,，]/);
            for (var j = 0; j < segs.length; j++) {
                var seg = segs[j].trim();
                if (!seg) continue;
                var rm = seg.match(/(\d{1,2})\s*[-~～－—至到]\s*(\d{1,2})/);
                var a, b;
                if (rm) { a = parseInt(rm[1], 10); b = parseInt(rm[2], 10); }
                else { var sm = seg.match(/(\d{1,2})/); if (sm) { a = parseInt(sm[1], 10); b = a; } }
                if (a === undefined || isNaN(a) || a < 1) continue;
                if (isNaN(b) || b < a) b = a;
                if (b > 32) b = 32;
                for (var w = a; w <= b; w++) { weeks[w] = true; found = true; }
            }
        }
        if (!found) return [];
        var list = Object.keys(weeks).map(function (k) { return parseInt(k, 10); }).sort(function (x, y) { return x - y; });
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

    // 判断课程块所在 td 的星期（处理 rowspan 的 上午/下午/晚上 单元格导致的行内列偏移）
    function dayOfCell(td) {
        try {
            var row = td.parentNode;
            var tds = row.cells;
            if (!tds.length) return 0;
            var first = (tds[0].innerText || '').replace(/\s+/g, '');
            var offset = /上午|下午|晚上/.test(first) ? 2 : 1;
            return td.cellIndex - offset + 1;
        } catch (e) { return 0; }
    }

    // 本学期课表网格解析（理论课与实验课均以课程块 div.class_div 排入网格，完整数据源）
    function extractFromGrid() {
        var courses = [];
        var docs = collectDocuments();
        var gridTable = null;
        for (var i = 0; i < docs.length && !gridTable; i++) {
            try { var el = docs[i].querySelector('table#courseTable'); if (el) gridTable = el; } catch (e) {}
        }
        if (!gridTable) return courses;
        var divs = [];
        try { divs = Array.prototype.slice.call(gridTable.querySelectorAll('div.class_div')); } catch (e) { divs = []; }
        var seen = {};
        for (var di = 0; di < divs.length; di++) {
            var td = divs[di].parentNode;
            while (td && td.tagName !== 'TD') td = td.parentNode;
            if (!td) continue;
            var day = dayOfCell(td);
            if (day < 1 || day > 7) continue;
            var ps = [];
            try { ps = Array.prototype.slice.call(divs[di].querySelectorAll('p')); } catch (e) { ps = []; }
            var name = ps.length ? String(ps[0].innerText || '').replace(/_[A-Za-z\d]+$/, '').trim() : '';
            if (!name) continue;
            var teacher = '', position = '', weekSec = '';
            for (var pi = 0; pi < ps.length; pi++) {
                var t2 = (ps[pi].innerText || '').trim();
                if (!t2) continue;
                var cls = ps[pi].getAttribute('class') || '';
                if (/p-jxl/.test(cls)) { position = t2; }
                else if (/kcb_p_gray/.test(cls)) {
                    if (/周/.test(t2)) weekSec = (weekSec ? weekSec + ' ' : '') + t2;
                    else if (/节/.test(t2)) weekSec = (weekSec ? weekSec + ' ' : '') + t2;
                    else if (!teacher) teacher = t2;
                } else if (!teacher && /^[^\d]+$/.test(t2) && t2.length <= 10) { teacher = t2; }
            }
            var weeks = parseWeeks(weekSec);
            var sec = parseSections(weekSec);
            if (!weeks.length || !sec) continue;
            var key = name + '|' + day + '|' + sec.start + '|' + sec.end + '|' + weeks.join(',');
            if (seen[key]) continue;
            seen[key] = true;
            courses.push({
                name: name,
                teacher: teacher.replace(/\*/g, '').trim() || '未知',
                position: position || '待定',
                day: day,
                startSection: sec.start,
                endSection: sec.end || sec.start,
                weeks: weeks,
                remark: ''
            });
        }
        return courses;
    }

    // 跨来源合并（当前仅单一网格源，保留以备后续多源扩展）
    function mergeCourses(groups) {
        var courses = [];
        var seen = {};
        for (var g = 0; g < groups.length; g++) {
            var list = groups[g];
            for (var i = 0; i < list.length; i++) {
                var c = list[i];
                var key = c.name + '|' + c.day + '|' + c.startSection + '|' + c.endSection + '|' + (c.weeks || []).join(',');
                if (seen[key] !== undefined) {
                    var ex = courses[seen[key]];
                    if (c.remark && c.remark !== ex.remark) {
                        if (!ex.remark) ex.remark = c.remark;
                        else if (ex.remark.indexOf(c.remark) === -1) ex.remark = ex.remark + '；' + c.remark;
                    }
                    continue;
                }
                seen[key] = courses.length;
                courses.push(c);
            }
        }
        return courses;
    }

    async function runImport() {
        try {
            var grid = extractFromGrid();
            var courses = mergeCourses([grid]);
            if (!courses.length) {
                showToast('未识别到课程，请先登录并进入「选课管理→本学期课表」页面，等待课表加载后再点击导入');
                return;
            }
            await window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(courses));
            showToast('成功解析 ' + courses.length + ' 门课程（理论课/实验课均已按课表网格导入）');
            if (window.shangkeBridge && window.shangkeBridge.notifyTaskCompletion) {
                window.shangkeBridge.notifyTaskCompletion();
            }
        } catch (e) {
            showToast('导入失败: ' + (e && e.message ? e.message : e));
        }
    }

    window.shangkeImportEntry = runImport;
    window.syauImport = runImport;
})();
