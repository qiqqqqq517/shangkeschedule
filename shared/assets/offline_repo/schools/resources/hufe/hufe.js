// 湖南财政经济学院教务系统专用适配器
// 强智新版 qz-weeklyTable 课表结构（无旧版 .kbcontent），经 CAS 统一认证(uia.hufe.edu.cn)进入。
// 结构特征：table#timetable.qz-weeklyTable
//   表头行：节次 | 周一(09-07) | 周二 ... | 周日
//   数据行：节次标签(0102节) | 7 个星期单元格
//   课程单元格：td.qz-hasCourse > .td-cell > ul.courselists > li.courselists-item
//     .qz-hasCourse-title = 课程名
//     .qz-hasCourse-detailitem(部分 qz-FullInfo) = 教师：XXX / 01~02节 / [1-8周] 星期X / 教学楼XXX
// 说明：部分课程 rowspan=2（跨两个大节，如 05~08节），解析以单元格自带节次文本为准。
(function () {
    'use strict';

    var PLATFORM_NAME = '湖南财政经济学院教务系统';
    var DAY_MAP = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7 };

    function showToast(msg) {
        try {
            if (window.shangkeBridge && window.shangkeBridge.showToast) window.shangkeBridge.showToast(msg);
            else if (window.Bridge && window.Bridge.showToast) window.Bridge.showToast(msg);
        } catch (e) {}
    }

    function cleanText(s) {
        return String(s == null ? '' : s).replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
    }

    function cleanCourseName(n) {
        return String(n || '')
            .replace(/【[^】]*】/g, '')
            .replace(/[■◆▲●★▽▼]/g, '')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function cleanTeacher(n) {
        return String(n || '')
            .replace(/教师\s*[:：]?\s*/, '')
            .replace(/（[^）]*）/g, '')
            .replace(/\([^)]*\)/g, '')
            .replace(/(教授|副教授|讲师|助教|研究员|工程师|实验师|未评级|无职称)$/g, '')
            .trim();
    }

    // 解析周次：支持 [1-8周] / 1-16周 / 1,3,5 / (单)(双)
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

    // 解析节次：01~02节 / 1-2节 / 第3节 / 5节
    function parseSections(s) {
        if (!s) return null;
        var m = String(s).match(/(\d{1,2})\s*[-~～－—至到]\s*(\d{1,2})\s*节/);
        if (m) return { start: parseInt(m[1], 10), end: parseInt(m[2], 10) };
        var sm = String(s).match(/(\d{1,2})\s*节/);
        if (sm) { var n = parseInt(sm[1], 10); return { start: n, end: n }; }
        return null;
    }

    // 收集主文档 + 所有同源 iframe 文档（课表常渲染在 person iframe 内）
    function collectDocuments() {
        var docs = [document];
        var seen = {};
        function dkey(d) {
            try { return d.location ? d.location.href : (d.URL || ('doc' + docs.length)); } catch (e) { return 'doc' + docs.length + Math.random(); }
        }
        seen[dkey(document)] = true;
        function push(d) {
            if (!d) return;
            var k = dkey(d);
            if (seen[k]) return;
            seen[k] = true;
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
        try {
            for (var f = 0; f < window.frames.length; f++) {
                var fd = null;
                try { fd = window.frames[f].document; } catch (e) { fd = null; }
                if (fd) { push(fd); walk(fd); }
            }
        } catch (e) {}
        return docs;
    }

    // 在全部文档中定位强智新版课表表格
    function findTimetableTable() {
        var docs = collectDocuments();
        for (var i = 0; i < docs.length; i++) {
            var doc = docs[i];
            try {
                var el = doc.querySelector('#timetable.qz-weeklyTable') || doc.querySelector('.qz-weeklyTable') || doc.querySelector('#timetable');
                if (el) return { doc: doc, table: el };
            } catch (e) {}
        }
        // 兜底：按文本评分找含"节次"与星期表头的表格
        for (var j = 0; j < docs.length; j++) {
            try {
                var tables = docs[j].querySelectorAll('table');
                for (var k = 0; k < tables.length; k++) {
                    var t = tables[k].innerText || tables[k].textContent || '';
                    if (t.indexOf('节次') !== -1 && (t.indexOf('周一') !== -1 || t.indexOf('星期一') !== -1)) {
                        return { doc: docs[j], table: tables[k] };
                    }
                }
            } catch (e) {}
        }
        return null;
    }

    // 从课程单元格 li.courselists-item 提取一门课
    function parseCourseItem(li) {
        var titleEl = null;
        try { titleEl = li.querySelector('.qz-hasCourse-title'); } catch (e) {}
        var name = cleanCourseName(titleEl ? titleEl.innerText : '');
        if (!name) return null;

        var teacher = '';
        var position = '';
        var weekSecText = '';
        var detailEls = [];
        try { detailEls = Array.prototype.slice.call(li.querySelectorAll('.qz-hasCourse-detailitem')); } catch (e) { detailEls = []; }
        for (var j = 0; j < detailEls.length; j++) {
            var txt = cleanText(detailEls[j].innerText);
            if (!txt) continue;
            if (txt.indexOf('教师') !== -1) {
                teacher = cleanTeacher(txt);
            } else if (txt.indexOf('周') !== -1 || txt.indexOf('节') !== -1) {
                weekSecText = (weekSecText ? weekSecText + ' ' : '') + txt;
            } else if (txt.length <= 30) {
                position = position || txt;
            }
        }

        var weeks = parseWeeks(weekSecText);
        var sec = parseSections(weekSecText);
        var day = 0;
        var dm = weekSecText.match(/星期([一二三四五六日])/);
        if (dm) day = DAY_MAP[dm[1]] || 0;
        if (!weeks.length || !sec || !day) return null;

        return {
            name: name,
            teacher: teacher || '未知',
            position: position || '待定',
            day: day,
            startSection: sec.start,
            endSection: sec.end || sec.start,
            weeks: weeks,
            remark: cleanText(li.innerText || '').slice(0, 200)
        };
    }

    function extractCourses(table) {
        var courses = [];
        var seen = {};
        var items = [];
        try { items = Array.prototype.slice.call(table.querySelectorAll('li.courselists-item')); } catch (e) { items = []; }
        for (var i = 0; i < items.length; i++) {
            var c = parseCourseItem(items[i]);
            if (!c) continue;
            var key = c.name + '|' + c.teacher + '|' + c.position + '|' + c.day + '|' + c.startSection + '|' + c.endSection + '|' + c.weeks.join(',');
            if (seen[key]) continue;
            seen[key] = true;
            courses.push(c);
        }
        return courses;
    }

    async function runImport() {
        try {
            var found = findTimetableTable();
            if (!found || !found.table) {
                showToast('未找到课表，请先登录后在「培养服务→我的课表→学期理论课表」页面停留，再点击导入');
                return;
            }
            var courses = extractCourses(found.table);
            if (!courses.length) {
                showToast('未识别出有效课程，请确认课表页面已完整加载（含“周一”表头与课程）后重试');
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
    window.hufeImport = runImport;
})();
