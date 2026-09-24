// 青岛理工大学 · 正方教务系统（教学管理信息服务平台 V-9.0）课表适配
//
// 入口：https://jxgl.qut.edu.cn/jwglxt/xtgl/login_slogin.html
//   （登录后可经「信息查询 → 学生课表查询」进入，页面路径 /jwglxt/kbcx/xskbcx_cxXskbcxIndex.html）
//
// 数据源：POST /jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=<模块号>
//   body: xnm=<学年>&xqm=<学期码>  →  JSON.kbList[]
//
// 修复记录（v3.68.0）：
//   ① 原脚本把接口写成 http://jxgl.qut.edu.cn/...，而入口是 https，WebView 按混合内容拦截，
//      导入必然失败。现全部改为同源相对路径。
//   ② 原脚本在顶层直接 runImportFlow()，注入即执行；改为只挂 window.shangkeImportEntry。
//   ③ 学年学期优先读页面上的 xnm/xqm 下拉，读不到再让用户选择。

(function () {
    'use strict';

    var PLATFORM_NAME = '青岛理工大学课表导入';
    var LOGIN_PATH = '/jwglxt/xtgl/login_slogin.html';
    var KB_API = '/jwglxt/kbcx/xskbcx_cxXsgrkb.html';
    var DEFAULT_GNMKDM = 'N253508';

    // 青岛理工大学统一作息时间
    var TIME_SLOTS = [
        { number: 1, startTime: '08:00', endTime: '08:45' },
        { number: 2, startTime: '08:50', endTime: '09:35' },
        { number: 3, startTime: '09:55', endTime: '10:40' },
        { number: 4, startTime: '10:45', endTime: '11:30' },
        { number: 5, startTime: '11:35', endTime: '12:20' },
        { number: 6, startTime: '14:00', endTime: '14:45' },
        { number: 7, startTime: '14:50', endTime: '15:35' },
        { number: 8, startTime: '15:55', endTime: '16:40' },
        { number: 9, startTime: '16:45', endTime: '17:30' },
        { number: 10, startTime: '19:00', endTime: '19:45' }
    ];

    function toast(msg) {
        try {
            if (window.shangkeBridge && window.shangkeBridge.showToast) window.shangkeBridge.showToast(msg);
            else if (window.Bridge && window.Bridge.showToast) window.Bridge.showToast(msg);
        } catch (e) {}
    }

    function alertBox(title, content, confirmText) {
        return window.shangkeBridgePromise.showAlert(title, content, confirmText || '确定');
    }

    function clean(s) {
        return String(s == null ? '' : s).replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
    }

    function cleanTeacher(name) {
        var t = clean(name);
        if (!t) return '未安排';
        return t.replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim() || '未安排';
    }

    function cleanCourseName(name) {
        return clean(name).replace(/[★○●◇◆■▲]/g, '').trim();
    }

    function isLoginPage() {
        var path = window.location.pathname || '';
        if (path.indexOf(LOGIN_PATH) !== -1) return true;
        if (document.getElementById('yhm') && document.getElementById('mm')) return true;
        return /用户登录/.test(document.title || '');
    }

    // "1-8,10-16周" / "7~17(单)周" / "3周" / "5-11周(单),12-19周"
    // 注意：单/双周标注只修饰紧跟它的那一段，必须按逗号分段独立判断，
    // 否则 "5-11周(单),12-19周" 的第二段会被误按单周过滤而丢周。
    function parseWeeks(text) {
        var s = clean(text);
        var set = {};
        var segs = s.split(/[,，;；]/);
        for (var si = 0; si < segs.length; si++) {
            var seg = segs[si];
            var odd = /[（(]\s*单\s*[）)]/.test(seg);
            var even = /[（(]\s*双\s*[）)]/.test(seg);
            var re = /(\d+)\s*(?:[-~—－]\s*(\d+))?\s*周/g;
            var m;
            while ((m = re.exec(seg)) !== null) {
                var a = parseInt(m[1], 10);
                var b = m[2] ? parseInt(m[2], 10) : a;
                if (isNaN(a) || a < 1) continue;
                if (isNaN(b) || b < a) b = a;
                if (b > 40) b = 40;
                for (var w = a; w <= b; w++) {
                    if (odd && w % 2 === 0) continue;
                    if (even && w % 2 === 1) continue;
                    set[w] = true;
                }
            }
        }
        return Object.keys(set).map(Number).sort(function (x, y) { return x - y; });
    }

    function parseSections(text) {
        var parts = clean(text).split('-');
        var start = parseInt(parts[0], 10);
        var end = parseInt(parts[parts.length - 1], 10);
        if (isNaN(start) || start < 1) return null;
        if (isNaN(end) || end < start) end = start;
        return { startSection: start, endSection: end };
    }

    function parseKbList(kbList) {
        var out = [];
        for (var i = 0; i < kbList.length; i++) {
            var item = kbList[i];
            if (!item || !item.kcmc || !item.xqj || !item.jcs || !item.zcd) continue;
            var day = Number(item.xqj);
            if (isNaN(day) || day < 1 || day > 7) continue;
            var sec = parseSections(item.jcs);
            if (!sec) continue;
            var weeks = parseWeeks(item.zcd);
            if (!weeks.length) continue;
            out.push({
                name: cleanCourseName(item.kcmc),
                teacher: cleanTeacher(item.xm),
                position: clean(item.cdmc) || '待定',
                day: day,
                startSection: sec.startSection,
                endSection: sec.endSection,
                weeks: weeks
            });
        }
        return out;
    }

    // 同一课程（名/师/地点/星期/节次）合并周次
    function mergeCourses(list) {
        var map = {};
        var order = [];
        for (var i = 0; i < list.length; i++) {
            var c = list[i];
            var key = [c.name, c.teacher, c.position, c.day, c.startSection, c.endSection].join('|');
            if (!map[key]) {
                map[key] = { name: c.name, teacher: c.teacher, position: c.position, day: c.day,
                    startSection: c.startSection, endSection: c.endSection, weeks: {} };
                order.push(key);
            }
            for (var j = 0; j < c.weeks.length; j++) map[key].weeks[c.weeks[j]] = true;
        }
        return order.map(function (k) {
            var c = map[k];
            return {
                name: c.name, teacher: c.teacher, position: c.position, day: c.day,
                startSection: c.startSection, endSection: c.endSection,
                weeks: Object.keys(c.weeks).map(Number).sort(function (a, b) { return a - b; })
            };
        }).sort(function (a, b) {
            return a.day - b.day || a.startSection - b.startSection || a.name.localeCompare(b.name);
        });
    }

    function getGnmkdm() {
        try {
            var qm = (window.location.search || '').match(/[?&]gnmkdm=([^&]+)/);
            if (qm) return decodeURIComponent(qm[1]);
            var el = document.getElementById('gnmkdm');
            if (el && el.value) return el.value;
        } catch (e) {}
        return DEFAULT_GNMKDM;
    }

    // 优先读页面上的学年/学期下拉；正方 V-9.0 的学期码 3=第一学期、12=第二学期
    function getXnxqFromPage() {
        var xnmEl = document.getElementById('xnm');
        var xqmEl = document.getElementById('xqm');
        var xnm = xnmEl && xnmEl.value ? String(xnmEl.value) : '';
        var xqm = xqmEl && xqmEl.value ? String(xqmEl.value) : '';
        if (xnm && xqm) return { xnm: xnm, xqm: xqm };
        return null;
    }

    function guessXnxq() {
        var now = new Date();
        var y = now.getFullYear();
        var m = now.getMonth() + 1;
        if (m >= 9) return { xnm: String(y), xqm: '3' };
        if (m >= 2) return { xnm: String(y - 1), xqm: '12' };
        return { xnm: String(y - 1), xqm: '3' };
    }

    function fetchKb(xnm, xqm) {
        var body = 'xnm=' + encodeURIComponent(xnm) + '&xqm=' + encodeURIComponent(xqm) +
            '&kzlx=ck&xsdm=&kclbdm=&kclxdm=';
        return fetch(KB_API + '?gnmkdm=' + encodeURIComponent(getGnmkdm()), {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: body
        }).then(function (r) {
            if (!r.ok) throw new Error('网络请求失败（HTTP ' + r.status + '）');
            return r.text();
        }).then(function (text) {
            var data;
            try { data = JSON.parse(text); } catch (e) {
                throw new Error('未登录或登录已过期，请先在教务系统完成登录');
            }
            if (!data || !Array.isArray(data.kbList)) {
                throw new Error('课表接口返回异常，请确认已登录且该学期有课');
            }
            var courses = mergeCourses(parseKbList(data.kbList));
            if (!courses.length) throw new Error('该学期没有解析到课程');
            return courses;
        });
    }

    function askTerm() {
        var fromPage = getXnxqFromPage();
        if (fromPage) return Promise.resolve(fromPage);
        var guess = guessXnxq();
        return window.shangkeBridgePromise.showPrompt(
            '选择学年',
            '请输入要导入课程的起始学年（例如 2026-2027 学年输入 2026）：',
            guess.xnm
        ).then(function (xnm) {
            if (xnm === null || xnm === '') { toast('导入已取消'); return null; }
            return window.shangkeBridgePromise.showSingleSelection(
                '选择学期',
                JSON.stringify(['第一学期', '第二学期']),
                guess.xqm === '3' ? 0 : 1
            ).then(function (idx) {
                if (idx === null || idx < 0) { toast('导入已取消'); return null; }
                return { xnm: String(xnm).trim(), xqm: idx === 0 ? '3' : '12' };
            });
        });
    }

    function runImport() {
        if (isLoginPage()) {
            toast('请先登录教务系统，再点「执行导入」');
            return Promise.resolve();
        }
        return alertBox(
            PLATFORM_NAME,
            '将从正方教务系统导入课程表。\n请确保已在教务系统中登录。',
            '开始导入'
        ).then(function (ok) {
            if (!ok) { toast('导入已取消'); return null; }
            return askTerm();
        }).then(function (term) {
            if (!term) return null;
            toast('正在获取课表数据...');
            return fetchKb(term.xnm, term.xqm).then(function (courses) {
                return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(courses))
                    .then(function () {
                        return window.shangkeBridgePromise.savePresetTimeSlots(JSON.stringify(TIME_SLOTS)).catch(function () {});
                    })
                    .then(function () {
                        toast('成功导入 ' + courses.length + ' 门课程！');
                        window.shangkeBridge.notifyTaskCompletion();
                    });
            });
        }).catch(function (e) {
            toast('导入失败：' + (e && e.message ? e.message : e));
        });
    }

    window.shangkeImportEntry = runImport;
    window.qutImport = runImport;
})();
