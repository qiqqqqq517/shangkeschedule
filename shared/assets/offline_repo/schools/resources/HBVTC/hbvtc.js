// 湖北职业技术学院（hbvtc）教务专属适配器 v3
// 【完全参照汕头大学 stu.js 的 v4 架构重写】
//
// ===== 站点事实（2026-09-16/17 实测）=====
//   入口（直连教务域，对照汕头 jw.stu.edu.cn/jsxsd/framework/xsMainV.htmlx）：
//     https://jwgl.hbvtc.edu.cn/jsxsd/framework/xsMainV.htmlx
//   未登录访问该入口返回强智自带登录表单页（<form id="loginForm">，
//     input#userAccount / input#userPassword / #yzm 验证码——强智经典登录，非 CAS），
//     UTF-8 正常，无「暂不支持手机浏览器」限制，但页面是固定 1200px 桌面布局，
//     需强制桌面 UA + 1280 视口（由 AdapterSelectionScreen.FORCE_DESKTOP_MODE_SCHOOL_IDS 对
//     u_c654f04a 开启，对照汕头 u_15f498f5）。
//   教务域内会话建立后：/jsxsd/xskb/xskb_list.do 可同源 fetch（自动携带会话 Cookie），
//     DOMParser 解析即得课表，无需页面跳转。
//   课表页：/jsxsd/xskb/xskb_list.do?viweType=0
//     table#timetable；第 0 行表头（[空|节次] | 星期一..星期日）；行首 第一大节..第五大节 / 备注:
//   课程单元格结构：
//     - div.kbcontent（含完整字段）+ div.kbcontent1（display:none 历史周次备份，无教师，跳过）
//     - 每门课字段用 <font title="…">：无 title → 课程名；title=教师 → 教师名；
//       title=周次(节次) → "2-5,7-15(周)[01-02节]"；title=教学楼 → 【德艺楼(#06)】；title=教室 → 6-313教室
//     - **同一 div.kbcontent 内多门课用 "---------------------" 文本分隔线隔开**（必须按分隔线拆多门课）
//   学期：#xnxq01id 下拉（多年多学期）；frame 节次模式 #kbjcmsid；周次 #zc
//   学期切换：POST /jsxsd/xskb/xskb_list.do?viweType=0（同套强智，实测返回目标学期课表）
//
// ===== v3 相对旧 v2 的架构修复（对照汕头 stu.js v4）=====
//   1. 【核心】不再用 location.href 裸跳到课表页。旧 v2 在「非教务域（官网 jwc）点导入」时会
//      先 alert 再 location.href=KB_URL 跳转，此时教务域会话未建立，强智直接返回
//      {"flag1":2,"msgContent":"请先登录系统"}（UTF-8 被国产 ROM 回退 GBK 解成乱码），
//      用户看到一整屏乱码 JSON。v3 改为：**入口就落在教务域登录页**（xsMainV.htmlx 未登录即登录表
//      单页，loginForm 正常渲染），用户在教务域完成登录后，适配器**同源 fetch** 课表页
//      （fetch 自动带会话 Cookie，credentials:include），DOMParser 解析后直接导入——
//      一次点击完成，绝不裸跳、绝不把 JSON 当页面显示。
//   2. 增加 isLoginPage() 检测：若仍停在登录表单页，明确提示用户先输入账号密码登录后再导入。
//   3. 增加 fetchDoc 返回内容校验：非 HTML / 无课表表格 → 判为未登录或会话失效，提示登录，
//      而不是把一段 JSON 渲染出来。
//   4. 菜单校园改造与汕头一致：KB_PATH 同源路径、shangkeNavigateToTimetable 精确跳转课表页。
//
// ===== 桥接契约 =====
//   window.shangkeImportEntry 由注入器（WebBridgeProtocol.JS_IMPORT_AUTOSTART）自动调用。
//   课程 JSON 字段：name / teacher / position / day / startSection / endSection / weeks

(function () {
    'use strict';

    var JW_HOST = 'jwgl.hbvtc.edu.cn';
    var KB_PATH = '/jsxsd/xskb/xskb_list.do?viweType=0';
    var KB_ABSOLUTE = 'https://' + JW_HOST + KB_PATH;
    var WEEKDAY = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
    var SEP_RE = /^[-=_]{3,}$/;
    var BIG_SECTION = { '第一大节': [1, 2], '第二大节': [3, 4], '第三大节': [5, 6], '第四大节': [7, 8], '第五大节': [9, 10], '第六大节': [11, 12] };

    // ---------- 桥接工具（汕头 stu.js 同款）----------
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

    // ---------- 文档收集（含同源 iframe，汕头同款）----------
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
                } catch (e) { }
            }
        }
        collect(document);
        return docs;
    }

    // ---------- 是否停在登录表单（强智经典登录）----------
    function isLoginPageIn(doc) {
        if (!doc || !doc.getElementById) return false;
        if (doc.getElementById('loginForm')) return true;
        if (doc.getElementById('userAccount') && doc.getElementById('userPassword')) return true;
        if ((doc.title || '').indexOf('登录') !== -1 && doc.getElementById('userPassword')) return true;
        return false;
    }
    function isLoginPage() {
        var docs = collectDocuments();
        for (var i = 0; i < docs.length; i++) {
            if (isLoginPageIn(docs[i])) return true;
        }
        if (location.host.indexOf('casp.hbvtc.edu.cn') !== -1) return true;
        return false;
    }

    // ---------- 是否在教务域 ----------
    function isJwHost() {
        return location.host.indexOf(JW_HOST) !== -1;
    }

    // ---------- 定位课表表格 ----------
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
    function findTimetable() {
        var docs = collectDocuments();
        for (var i = 0; i < docs.length; i++) {
            var t = findTimetableIn(docs[i]);
            if (t) return { doc: docs[i], table: t };
        }
        return null;
    }

    // ---------- 同源抓取课表页（汕头 v4 核心：不裸跳）----------
    // 课表页与门户同源，fetch 自动携带会话 Cookie。返回内容若为 JSON / 无课表表格，
    // 一律判为未登录或会话失效，绝不把原始内容渲染给用户。
    function fetchDoc(url) {
        return fetch(url, { credentials: 'include' })
            .then(function (resp) {
                var ct = (resp.headers && resp.headers.get ? resp.headers.get('content-type') : '') || '';
                return resp.text().then(function (text) {
                    var trimmed = text.replace(/^\s+/, '');
                    // 内容为 JSON（强智未登录/会话失效返回 {"flag1":2,"msgContent":".."}）→ 视为未登录
                    if (trimmed.charAt(0) === '{' || trimmed.charAt(0) === '[') {
                        return { json: true, text: text };
                    }
                    if (ct.indexOf('application/json') !== -1) {
                        return { json: true, text: text };
                    }
                    return { json: false, doc: new DOMParser().parseFromString(text, 'text/html') };
                });
            });
    }

    // ---------- 周次 / 节次（保留 v2 已验证规则）----------
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

    // ---------- 网格（rowspan/colspan 展开）----------
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

    // ---------- 学期 ----------
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
    // 学期周数上限：#zc 「第N周」选项数，用于周次兜底
    function totalWeeksOf(doc) {
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
        return count > 0 && count <= 40 ? count : 20;
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
          .then(function (html) {
              var trimmed = html.replace(/^\s+/, '');
              if (trimmed.charAt(0) === '{' || trimmed.charAt(0) === '[' || (html.match && html.match(/{\s*"flag1"/))) {
                  return { json: true };
              }
              return { json: false, doc: new DOMParser().parseFromString(html, 'text/html') };
          });
    }

    // ---------- 诊断 ----------
    function diagnose() {
        var info = { url: location.href, loginPage: isLoginPage(), onJwHost: isJwHost() };
        var live = findTimetable();
        info.tables = live ? 1 : 0;
        info.blocks = live ? live.table.querySelectorAll('div.kbcontent').length : 0;
        return info;
    }

    // ---------- 取得课表来源：优先当前页，否则同源 fetch（绝不裸跳）----------
    // 汕头 v4 关键：不 location.href 跳转 —— 一旦导航即销毁当前 JS 上下文，后续 await 不执行，
    // 且可能把会话未建立时的 JSON 响应整页渲染。这里一律同源 fetch，失败则判为未登录提示登录。
    function resolveSource() {
        // 1. 当前文档（含 iframe）已有课表 → 直接用
        var live = findTimetable();
        if (live) return Promise.resolve({ doc: live.doc, table: live.table });

        // 2. 停在登录页 → 让用户先登录，不做任何抓取
        if (isLoginPage()) return Promise.resolve(null);

        // 3. 非教务域 → 无法同源抓取，交给主流程提示（引导进教务域）
        if (!isJwHost()) return Promise.resolve(null);

        // 4. 教务域内同源 fetch 课表页
        toast('正在读取课表…');
        return fetchDoc(KB_PATH).then(function (res) {
            if (res.json) return null;                  // 会话失效返回 JSON → 判未登录
            var t = findTimetableIn(res.doc);
            return t ? { doc: res.doc, table: t } : null;
        }).catch(function () { return null; });
    }

    // ---------- 主流程 ----------
    function runImport() {
        var semesterLabel = '';
        function fail(title, message) {
            return alert(title, message, '确定').then(function () { return null; });
        }
        return Promise.resolve()
            .then(function () {
                // 0. 停在登录页 / 非教务域：提示用户先登录（对照汕头）
                if (isLoginPage() || !isJwHost()) {
                    return alert('请先在教务系统登录',
                        '请先在登录页输入学号 / 密码（含验证码）完成登录，\n' +
                        '登录成功后会自动进入教务系统，再点「执行导入」即可。\n\n' +
                        '若当前不在教务系统页面，请先点下方「一键导航到课表」。'
                    ).then(function () { return null; });
                }
                return resolveSource();
            })
            .then(function (src) {
                if (!src) {
                    return fail('未找到课表',
                        '没有读到课表数据。\n\n' +
                        '请确认：\n1. 已登录教务系统并进入「学期理论课表」\n' +
                        '2. 会话未过期、课表已正常显示\n\n' +
                        '也可以先点下方「一键导航到课表」，进入后再点「执行导入」。');
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
                            .then(function (res) {
                                if (res.json) return fail('登录已失效', '会话已过期，请重新登录后再导入。');
                                var t = findTimetableIn(res.doc);
                                if (!t) return fail('获取课表失败', '未能获取「' + chosen.label + '」的课表。');
                                var courses = dedupe(parseTimetable(res.doc, t));
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
                var info = diagnose();
                return alert('导入失败',
                    '错误信息：' + (error && error.message ? error.message : String(error)) + '\n\n' +
                    '诊断：课表表格 ' + info.tables + ' 个 / 课程块 ' + info.blocks + ' 个' +
                    (info.loginPage ? '（当前在登录页）' : '') + '\n\n' +
                    '请确认已登录教务系统进入「学期理论课表」后重试。', '确定');
            });
    }

    // ---------- 「一键导航到课表」入口（会话先探后跳，杜绝跳进 JSON 乱码页）----------
    // 原因：未登录/会话失效时，直接 location.href=KB_ABSOLUTE 会导航到服务器返回的
    //   {"flag1":2,"msgContent":"请先登录教务系统"}（GBK JSON），被 WebView 整页按 UTF-8
    //   渲染成黑色乱码。这里先同源 fetch 探测课表接口：能取到 HTML 课表才跳；
    //   返回 JSON 判定未登录 → 改回教务域首页（未登录时它是正常 HTML 登录表单，绝不乱码）。
    window.shangkeNavigateToTimetable = function () {
        if (!isJwHost()) {
            location.href = 'https://' + JW_HOST + '/jsxsd/framework/xsMainV.htmlx';
            return true;
        }
        if (!window.fetch) { location.href = KB_ABSOLUTE; return true; }
        return fetch(KB_PATH, { credentials: 'include' })
            .then(function (r) { return r.text(); })
            .then(function (text) {
                var t = (text || '').replace(/^\s+/, '');
                if (!t || t.charAt(0) === '{' || t.charAt(0) === '[' || t.indexOf('flag1') !== -1) {
                    toast('教务系统会话未就绪，请先确认已登录');
                    location.href = 'https://' + JW_HOST + '/jsxsd/framework/xsMainV.htmlx';
                    return;
                }
                location.href = KB_ABSOLUTE;
            })
            .catch(function () { location.href = KB_ABSOLUTE; });
    };

    if (typeof window !== 'undefined') {
        window.shangkeImportEntry = runImport;
        window.hbvtcImport = runImport;
    }
})();