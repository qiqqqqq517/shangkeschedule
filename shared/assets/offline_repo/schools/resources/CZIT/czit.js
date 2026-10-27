// 常州信息职业技术学院（2026 年更名：常州信息职业技术大学）：正方 V9 教务（jx.ccit.js.cn），
// 须经 authserver 统一认证 + ehall「阳光院务」门户进入，外网无法直连教务。
//
// ⚠️ 本校域名 ccit.js.cn 与长春工程学院（ccit.edu.cn）共享 "ccit" 域名段，
// 不得放入 resources/CCIT/（会被 build_schools.py 的域名段匹配与长春混线），故用 CZIT。
//
// 实测链路（2026-10-26，测试账号实走）：
//   authserver.ccit.js.cn 登录 → ehall.ccit.js.cn「阳光院务」大厅
//     → 大厅点「教务系统」= window.open('//ehall.ccit.js.cn/appShow?appId=5843399900513599')
//     → (CAS TGC 静默换票) → jx.ccit.js.cn/jwglxt/xtgl/index_initMenu.html
//
// 关键事实（均已实测，勿凭惯例改）：
//   · 正方登录页 login_slogin.html 的统一认证按钮 #authJwglxtLoginURL 值为空 ——
//     jwglxt 未配直通 CAS，直接访问 jwglxt 会 302 到正方自己的登录页 ⇒
//     进教务只能走 ehall appShow 启动链（无 ehall 会话时它自己会 302 到登录页）。
//   · 个人课表查询菜单 gnmkdm=N2151；整学期接口
//     POST /jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151 实测返回 kbList 33 条。
//   · 成绩接口 cjcx_cxXsgrcj.html?doType=query 连通（新生账号暂无出分数据）；
//     空教室接口 cdjy_cxKxcdlb.html?doType=query 连通，字段 cdmc/zws/jxlmc/xqmc 齐全。
//   · 菜单无「学业情况」页面 ⇒ 不声明 shangkeScanStudy 钩子（App 会提示本校暂未适配）。
//   · kcmc 带尾部制表符（"设计基础\t"）、zcd 形如 "19周"、jcs 形如 "1-8节"。
//
// 状态机：教务页(jw) 导入 / 大厅页(portal) 自动进教务 / 登录页(cas|ehall_login) 引导。
// 注意：自动跳转只发生在用户点击「执行导入」之后（runImport 内），脚本顶层不自动跑 ——
// 成绩/空教室用途也会注入本文件，顶层跳转会打断用户手头的操作。
(function () {
    'use strict';

    var HOST_JW = 'jx.ccit.js.cn';
    var HOST_EHALL = 'ehall.ccit.js.cn';
    var HOST_CAS = 'authserver.ccit.js.cn';
    // 门户「教务系统」应用的启动地址（实测 window.open 参数，含静默 SSO 换票链）。
    // ⚠️ 不能写死 http://：若当前已在 https 门户，跳到 http 会被 WebView 按不安全导航拦下
    // （2026-10-27 用户侧表现为点「执行导入」后页面无反应/报网络错误）⇒ 跟随当前页协议。
    function appShowUrl() {
        var scheme = window.location.protocol === 'https:' ? 'https:' : 'http:';
        return scheme + '//ehall.ccit.js.cn/appShow?appId=5843399900513599';
    }

    var importing = null;

    function toast(text) {
        if (window.shangkeBridge && window.shangkeBridge.showToast) window.shangkeBridge.showToast(text);
    }
    function extractText(html) {
        if (!html) return '';
        return String(html).replace(/<[^>]+>/g, '').trim();
    }
    function cleanTeacher(name) {
        if (!name) return '';
        return String(name).replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim();
    }
    function cleanCourseName(name) {
        return String(name || '')
            .replace(/【[^】]*】/g, '')
            .replace(/[■◆▲●★]/g, '')
            .replace(/\s+/g, ' ')
            .trim();
    }

    // ---------- 页面状态识别 ----------
    function detectState() {
        var host = (window.location.hostname || '').toLowerCase();
        var url = window.location.href || '';
        if (host === HOST_JW) return 'jw';
        if (host === HOST_CAS) return 'cas';
        if (host === HOST_EHALL) {
            if (/\/ywtb-portal\//i.test(url)) return 'portal';
            return 'ehall_login';
        }
        return 'unknown';
    }

    // ---------- 学年学期 ----------
    // 优先读课表页的 #xnm/#xqm；读不到按日期启发（与 hbnu.js 同规则）：
    // 9 月起算当前学年第一学期(xqm=3)，2-8 月属上一学年的第二学期(xqm=12)。
    function termOf() {
        var now = new Date(), month = now.getMonth() + 1, year = now.getFullYear();
        var xn = document.getElementById('xnm');
        var xq = document.getElementById('xqm');
        return {
            xnm: (xn && xn.value) || String(month >= 9 ? year : year - 1),
            xqm: (xq && xq.value) || (month >= 2 && month < 9 ? '12' : '3')
        };
    }

    // ---------- 接口课表 ----------
    // ⚠️ 本校教务页面重写了 Array.prototype（Prototype.js 风格老库，实测 filter 不给回调传
    // index，"filter+indexOf 去重"会把数组滤空）。因此本文件一律使用手写 for 循环，
    // 禁止使用 forEach/map/filter/reduce/indexOf 等可被页面污染的原型高阶方法。
    function arrayUniqueSorted(arr) {
        var seen = {};
        var out = [];
        for (var i = 0; i < arr.length; i++) {
            var v = arr[i];
            if (!seen[v]) { seen[v] = true; out.push(v); }
        }
        out.sort(function (a, b) { return a - b; });
        return out;
    }

    // 周次：zcd 兼容 1-16 / 1-16(单/双) / 1,3,5 / 19周（实测该校为 "19周"）
    function parseApiWeeks(value) {
        var weeks = {};
        var found = false;
        var parts = String(value || '').replace(/（/g, '(').replace(/）/g, ')').split(/[，,、;]/);
        for (var p = 0; p < parts.length; p++) {
            var part = parts[p];
            var numbers = part.match(/\d+/g);
            if (!numbers) continue;
            var start = parseInt(numbers[0], 10);
            var end = parseInt(numbers[numbers.length - 1], 10);
            if (isNaN(start) || isNaN(end) || end < start) continue;
            if (end > 32) end = 32;
            var odd = /单/.test(part);
            var even = /双/.test(part);
            for (var w = start; w <= end; w++) {
                if (odd && w % 2 === 0) continue;
                if (even && w % 2 !== 0) continue;
                weeks[w] = true;
                found = true;
            }
        }
        if (!found) return [];
        var out = [];
        for (var k in weeks) {
            if (Object.prototype.hasOwnProperty.call(weeks, k)) out.push(parseInt(k, 10));
        }
        out.sort(function (a, b) { return a - b; });
        return out;
    }

    // 节次：该校实测 jcs="1-8节"、jcor="1-8"；兼容单节 "3节"
    function parseApiSections(item) {
        var text = String(item.jcs || item.jcor || '');
        var range = text.match(/(\d+)\s*[-~－—至到]\s*(\d+)/);
        if (range) {
            var s = parseInt(range[1], 10), e = parseInt(range[2], 10);
            if (!isNaN(s) && !isNaN(e) && s >= 1 && e >= s) return { start: s, end: e };
        }
        var single = text.match(/(\d+)/);
        if (single) {
            var n = parseInt(single[1], 10);
            if (!isNaN(n) && n >= 1) return { start: n, end: n };
        }
        return null;
    }

    function parseAndImport(kbList) {
        var courses = [];
        var dropped = 0;
        for (var i = 0; i < kbList.length; i++) {
            var item = kbList[i];
            var name = cleanCourseName(extractText(item.kcmc));
            var teacher = cleanTeacher(extractText(item.xm || ''));
            var position = extractText(item.cdmc || '') || '待定';
            var day = parseInt(item.xqj, 10);
            var sections = parseApiSections(item);
            var weeks = parseApiWeeks(item.zcd);

            if (!name || isNaN(day) || day < 1 || day > 7 || !sections || weeks.length === 0) { dropped++; continue; }

            var remarkParts = [];
            if (item.jxbmc) remarkParts.push('教学班：' + String(item.jxbmc).replace(/\s+/g, ' ').trim());
            if (item.xf) remarkParts.push('学分：' + item.xf);
            if (item.kcxzmc || item.kcxz) remarkParts.push(String(item.kcxzmc || item.kcxz).trim());

            courses.push({
                name: name,
                teacher: teacher || '未安排',
                position: position,
                day: day,
                startSection: sections.start,
                endSection: sections.end,
                weeks: weeks,
                remark: remarkParts.join('；')
            });
        }
        if (dropped > 0) {
            toast('有 ' + dropped + ' 条课程因缺少星期/节次/周次被跳过，请到课表页核对');
        }
        return courses;
    }

    // 相邻同名连堂合并（1-2节 与 3-4节 拆行时拼回）
    function mergeCourses(courses) {
        var map = {};
        var order = [];
        for (var i = 0; i < courses.length; i++) {
            var c = courses[i];
            if (!c.name || !c.weeks.length) continue;
            var key = JSON.stringify([c.name, c.teacher, c.position, c.day, c.startSection, c.endSection]);
            if (!map[key]) {
                map[key] = c;
                order.push(key);
            } else {
                map[key].weeks = map[key].weeks.concat(c.weeks);
            }
        }
        var merged = [];
        for (var j = 0; j < order.length; j++) {
            var m = map[order[j]];
            m.weeks = arrayUniqueSorted(m.weeks);
            merged.push(m);
        }
        merged.sort(function (a, b) { return a.startSection - b.startSection; });
        return merged;
    }

    function postJson(url, body) {
        return fetch(url, {
            method: 'POST',
            headers: {
                'content-type': 'application/x-www-form-urlencoded;charset=UTF-8',
                'x-requested-with': 'XMLHttpRequest'
            },
            body: body,
            credentials: 'include'
        }).catch(function () {
            // 网络层失败（DNS/连接被拒/TLS/代理拦截/断网）时 fetch 只抛 TypeError: Failed to fetch，
            // 直接透传会让用户在弹窗里看到一串英文（2026-10-27 报障原文即 "failed to fetch"）。
            // 本校教务在校园网外经常完全不可达（实测 jx.ccit.js.cn 校外 443 直接连不上）⇒ 翻译成可执行提示。
            throw new Error('无法连接常州信息职院教务系统（' + HOST_JW + '）。' +
                '请确认已连接校园网或学校 VPN，并关闭手机/电脑上的代理、加速器后重试。');
        }).then(function (response) {
            if (response.status !== 200) throw new Error('教务接口暂不可用（HTTP ' + response.status + '）');
            return response.text();
        }).then(function (text) {
            try { return JSON.parse(text); }
            catch (e) { throw new Error('登录可能已失效，请重新进入教务系统'); }
        });
    }

    // 从 performance 记录里找页面自己请求过的课表接口（防模块号变动），失败用固定 N2151
    function courseApiCandidates() {
        var seen = {};
        var list = [];
        function pushOnce(url) {
            if (url && !seen[url]) { seen[url] = true; list.push(url); }
        }
        try {
            var entries = performance.getEntriesByType('resource') || [];
            for (var i = 0; i < entries.length; i++) {
                if (/xskbcx_cxXsgrkb\.html/i.test(entries[i].name || '')) pushOnce(entries[i].name);
            }
        } catch (e) { /* 老内核无 performance 时走固定路径 */ }
        pushOnce('/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151');
        return list;
    }

    // ⚠️ 正方服务端按「功能上下文」返回数据（2026-10-26 实测踩坑）：
    // 只进过教务首页就直调 xskbcx_cxXsgrkb.html，返回的是 qsxqj/xsxx 初始化结构而非课表；
    // 必须先 GET 一次「个人课表查询」页面（xskbcx_cxXskbcxIndex.html?gnmkdm=<模块号>）
    // 把服务端会话切到该功能，接口才会回 kbList（实测：先开页后调接口 kb=33）。
    function gnmkdmOf(url) {
        var match = String(url).match(/[?&]gnmkdm=([^&]+)/);
        return match ? decodeURIComponent(match[1]) : 'N2151';
    }
    function warmupCoursePage(url) {
        var module = gnmkdmOf(url);
        return fetch('/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=' + encodeURIComponent(module) + '&layout=default', {
            credentials: 'include'
        }).catch(function () { /* 初始化失败时仍尝试接口，错误由接口调用给出 */ });
    }

    // ⚠️ 正方接口对刚建立的 session 偶发返回「初始化包」（qsxqj/xsxx 结构，无 kbList），
    // 实测随后再调一次即正常返回课表（2026-10-26 对照实验 A/B/C 全部 kb=33）⇒ 遇初始化包重试一次。
    function requestRows(url, term) {
        function once() {
            return postJson(url, 'xnm=' + encodeURIComponent(term.xnm) + '&xqm=' + encodeURIComponent(term.xqm))
                .then(function (data) {
                    var rows = (data && data.kbList) || [];
                    if (rows.length) return rows;
                    if (data && data.qsxqj) throw new Error('INIT_PACKET');
                    throw new Error('当前学期没有课表，请确认学年学期后重试');
                });
        }
        return once().catch(function (e) {
            if (e && e.message === 'INIT_PACKET') return once();
            throw e;
        });
    }

    function fetchCoursesFromApi() {
        var term = termOf();
        var candidates = courseApiCandidates();
        var lastError = null;
        var courses = [];
        // 手写串行（reduce 属原型高阶方法，本文件一律不用）
        var chain = Promise.resolve([]);
        for (var i = 0; i < candidates.length; i++) {
            (function (url) {
                chain = chain.then(function (prev) {
                    if (prev.length) return prev;
                    return warmupCoursePage(url).then(function () {
                        return requestRows(url, term)
                            .then(function (rows) { return parseAndImport(rows); })
                            .catch(function (e) { lastError = e; return []; });
                    });
                });
            })(candidates[i]);
        }
        return chain.then(function (courses) {
            if (!courses.length) {
                var error = lastError || new Error('未取到课表数据');
                if (error.message === 'INIT_PACKET') error = new Error('教务接口暂时未就绪，请稍后重试');
                throw error;
            }
            return courses;
        });
    }

    // ---------- 页面课表解析（接口失效时的兜底，扫当前页表格） ----------
    function cellLines(cell) {
        var text = cell.innerText;
        if (text === undefined || text === null || String(text).trim() === '') {
            text = (cell.innerHTML || '').replace(/<br\s*\/?>/gi, '\n')
                .replace(/<\/(div|p|td|tr|span)>/gi, '\n').replace(/<[^>]+>/g, '');
        }
        var rawLines = String(text).split(/\r?\n/);
        var lines = [];
        for (var i = 0; i < rawLines.length; i++) {
            var s = rawLines[i].replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
            if (s.length > 0) lines.push(s);
        }
        return lines;
    }
    function parseScheduleFromPage() {
        var courses = [];
        var tables = document.querySelectorAll('table');
        for (var t = 0; t < tables.length; t++) {
            var table = tables[t];
            if (!table.rows || table.rows.length < 2) continue;
            var dayColumns = {};
            var headRow = table.rows[0];
            for (var c = 0; c < headRow.cells.length; c++) {
                var headText = extractText(headRow.cells[c].innerHTML);
                var dayMatch = headText.match(/星期([一二三四五六日天])/);
                if (dayMatch) {
                    var d = '一二三四五六'.indexOf(dayMatch[1]);
                    if (dayMatch[1] === '日' || dayMatch[1] === '天') d = 6;
                    dayColumns[c] = d + 1;
                }
            }
            if (Object.keys(dayColumns).length === 0) continue;

            for (var r = 1; r < table.rows.length; r++) {
                var cells = table.rows[r].cells;
                for (var col in dayColumns) {
                    var cell = cells[parseInt(col, 10)];
                    if (!cell) continue;
                    var lines = cellLines(cell);
                    if (lines.length === 0) continue;
                    var day = dayColumns[col];
                    var blockText = lines.join('\n');
                    var name = null, teacher = null, room = null;
                    var weeks = [];
                    for (var li = 0; li < lines.length; li++) {
                        var line = lines[li];
                        var wm = line.match(/\d+(?:\s*[-~－—至到]\s*\d+)?\s*周(?:\s*[（(]\s*[单双]\s*[）)])?/g);
                        if (wm) {
                            for (var wi = 0; wi < wm.length; wi++) {
                                var ws = parseApiWeeks(wm[wi]);
                                for (var wj = 0; wj < ws.length; wj++) {
                                    // 手写去重：本校页面重写了 Array.prototype（见文件头），不用 indexOf
                                    var existed = false;
                                    for (var wk = 0; wk < weeks.length; wk++) {
                                        if (weeks[wk] === ws[wj]) { existed = true; break; }
                                    }
                                    if (!existed) weeks.push(ws[wj]);
                                }
                            }
                        }
                        if (!name && /[\u4e00-\u9fa5A-Za-z]/.test(line) && !/周/.test(line)) name = line;
                        else if (name && !teacher && /教授|老师|讲师|副教授|教师|、/.test(line)) teacher = line;
                        else if (name && !room && /(楼|室|区|馆|机房|实训|实验室)/.test(line)) room = line;
                    }
                    var secMatch = blockText.match(/(\d+)\s*[-~－—至到]\s*(\d+)\s*节/) || blockText.match(/第?\s*(\d+)\s*节/);
                    if (!secMatch || !name || weeks.length === 0) continue;
                    courses.push({
                        name: cleanCourseName(name),
                        teacher: cleanTeacher(teacher) || '未安排',
                        position: room || '待定',
                        day: day,
                        startSection: parseInt(secMatch[1], 10),
                        endSection: secMatch[2] ? parseInt(secMatch[2], 10) : parseInt(secMatch[1], 10),
                        weeks: weeks.sort(function (a, b) { return a - b; }),
                        remark: '页面解析'
                    });
                }
            }
        }
        return courses;
    }

    // ---------- 主流程 ----------
    async function runImport() {
        var state = detectState();

        if (state === 'portal') {
            // 已登录大厅：直接走 appShow 启动链（CAS TGC 静默换票），整链路无感
            toast('正在进入教务系统，请稍候…进入后请再次点击「执行导入」');
            window.location.href = appShowUrl();
            return;
        }
        if (state === 'cas' || state === 'ehall_login') {
            toast('请先在统一身份认证页完成登录，登录后再次点击「执行导入」');
            return;
        }
        if (state !== 'jw') {
            toast('请登录「阳光院务」大厅并进入教务系统后重试');
            return;
        }

        toast('正在读取完整学期课表…');
        var courses;
        try {
            courses = await fetchCoursesFromApi();
        } catch (error) {
            courses = parseScheduleFromPage();
            var validCourses = [];
            for (var vi = 0; vi < courses.length; vi++) {
                if (courses[vi].weeks.length > 0) validCourses.push(courses[vi]);
            }
            courses = validCourses;
            if (!courses.length) throw error;
            toast('接口不可用，已按课表页面解析');
        }
        courses = mergeCourses(courses);
        if (!courses.length) throw new Error('未解析到课程，请进入「信息查询 → 个人课表查询」后重试');

        var bridge = window.shangkeBridgePromise;
        if (!bridge || typeof bridge.saveImportedCourses !== 'function') {
            throw new Error('导入桥接不可用，请返回 App 重新执行导入');
        }
        var ok = await bridge.saveImportedCourses(JSON.stringify(courses));
        if (ok !== true && ok !== 'true') throw new Error('课表保存未成功，请确认已选择课表后重试');
        toast('成功导入 ' + courses.length + ' 项课程安排');
        if (window.shangkeBridge && window.shangkeBridge.notifyTaskCompletion) window.shangkeBridge.notifyTaskCompletion();
    }

    function startImport() {
        if (!importing) importing = runImport().catch(function (error) {
            if (typeof window.__shangkeReportScriptError === 'function') window.__shangkeReportScriptError(error);
            else toast((error && error.message) || '课表导入失败，请重新登录后重试');
        }).then(function () { importing = null; });
        return importing;
    }

    window.czitImport = startImport;
    window.zhengfangImport = startImport;
    window.shangkeImportEntry = startImport;

    // ========================================================================
    // 能力钩子（接口路径为正方 V9 平台级，且均已在本校实测连通）
    // ========================================================================

    // 成绩：POST /jwglxt/cjcx/cjcx_cxXsgrcj.html?doType=query
    // xnm/xqm 留空 = 全部学期。实测连通（2026-10-26，新生账号 items=0 属正常）。
    window.shangkeScanGrades = function () {
        return postJson('/jwglxt/cjcx/cjcx_cxXsgrcj.html?doType=query',
            'xnm=&xqm=&sfzgcj=&kcbj=&pkey=&_search=false&nd=' + Date.now() +
            '&queryModel.showCount=500&queryModel.currentPage=1' +
            '&queryModel.sortName=&queryModel.sortOrder=asc&time=0'
        ).then(function (data) {
            var items = (data && data.items) || [];
            var grades = [];
            for (var i = 0; i < items.length; i++) {
                var item = items[i];
                var courseName = String(item.kcmc || '').trim();
                if (!courseName) continue;
                var scoreText = String(item.bfzcj || item.cj || '').trim();
                if (!scoreText) continue;
                var creditText = String(item.xf == null ? '' : item.xf).trim();
                var credit = creditText === '' ? null : Number(creditText);
                var year = String(item.xnmmc || '').trim();
                var term = String(item.xqmmc || '').trim();
                var rawPoint = String(item.jd == null ? '' : item.jd).trim();
                var pointValue = Number(rawPoint);
                grades.push({
                    courseName: courseName,
                    credit: isFinite(credit) ? credit : null,
                    scoreText: scoreText,
                    semester: (year && term) ? (year + '-' + term) : null,
                    category: String(item.kcxzmc || item.kclbmc || '').trim() || null,
                    gradePoint: (isFinite(pointValue) && pointValue >= 0 && pointValue <= 5) ? pointValue : null
                });
            }
            return grades;
        });
    };

    // 空教室：POST /jwglxt/cdjy/cdjy_cxKxcdlb.html?doType=query
    // 实测字段 cdmc/zws/jxlmc/xqmc/cdlbmc 齐全（total=411）。
    window.shangkeScanEmptyClassrooms = function () {
        function val(id) {
            var el = document.getElementById(id);
            return el && el.value != null ? String(el.value) : '';
        }
        var term = termOf();
        var body = 'xnm=' + encodeURIComponent(term.xnm) +
            '&xqm=' + encodeURIComponent(term.xqm) +
            '&dm=' + encodeURIComponent(term.xnm + '-' + term.xqm) +
            '&xqh_id=' + encodeURIComponent(val('xqh_id') || '1') +
            '&lh=' + encodeURIComponent(val('lh')) +
            '&cdlb_id=' + encodeURIComponent(val('cdlb_id')) +
            '&cdmc=' + encodeURIComponent(val('cdmc')) +
            '&qszws=' + encodeURIComponent(val('qszws')) +
            '&jszws=' + encodeURIComponent(val('jszws')) +
            '&cdejlb_id=&jyfs=2&qssj=&jssj=&sjfw=&qssd=&jssd=' +
            '&_search=false&nd=' + Date.now() +
            '&queryModel.showCount=500&queryModel.currentPage=1' +
            '&queryModel.sortName=&queryModel.sortOrder=asc&time=0';
        return postJson('/jwglxt/cdjy/cdjy_cxKxcdlb.html?doType=query', body)
            .then(function (data) {
                var items = (data && data.items) || [];
                var rooms = [];
                for (var i = 0; i < items.length; i++) {
                    var item = items[i];
                    var room = String(item.cdmc || '').trim();
                    if (!room) continue;
                    var seats = Number(String(item.zws == null ? '' : item.zws).trim());
                    rooms.push({
                        room: room,
                        campus: String(item.xqmc || '').trim(),
                        building: String(item.jxlmc || '').trim().replace(/^无楼号$/, ''),
                        capacity: isFinite(seats) ? seats : null,
                        freeSlots: String(item.cdlbmc || '').trim()
                    });
                }
                return rooms;
            });
    };
})();
