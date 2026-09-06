// 中国科学院大学（SEP 教育业务接入平台）课表适配器 v5
// 入口：https://sep.ucas.ac.cn （SEP 登录：用户名 + RSA 密码 + 验证码，WebView 内真人登录）
// 研究生链路（实测打通）：
//   SEP 登录 → 「课程学习/课程选修/选课」站点 /portal/site/524/2412（内含一次性 SSO 票据）
//   → https://xkgo.ucas.ac.cn:3000/user/login?Identity=<uuid>&roleId=2412 → 选课系统
//   → /course/personSchedule 个人课表网格（thead=星期表头、tbody 每格 <a href='xkcts/.../coursetime/ID'>课名</a>）
//   → 逐门拉 xkcts /course/coursetime/ID 详情：上课时间/上课地点/上课周次。
// 跨域说明（v5 核心修复）：
//   personSchedule 在 xkgo.ucas.ac.cn:3000 域，coursetime 详情在 xkcts.ucas.ac.cn:8443 域。
//   WebView 页面内 fetch 跨域被浏览器 CORS 拦截（服务器不返回 Access-Control-Allow-Origin）。
//   v5：跨域请求 URL 统一带 _webview_post_id 标记 → App 原生拦截器（WebViewRequestInterceptor）
//   接管转发（ktor 带 Cookie），并在响应中回填 Access-Control-Allow-Origin:*，绕过渲染进程同源策略。
//   实测：xkcts coursetime 详情页公开（无需会话），凭证用 omit 即可。
// 桥接契约：window.shangkeImportEntry 由注入器自动调用。

var UCAS_MY_COURSE_MENU = '/portal/site/226/xs/1/1/0B7E20D6805D812CE5A5443A0D206498C0B831F63C8AF7A7327B21A63C76B5B309';
var UCAS_XKCTS = 'https://xkcts.ucas.ac.cn:8443';

function ucasToast(msg) {
    if (window.shangkeBridge && typeof window.shangkeBridge.showToast === 'function') {
        window.shangkeBridge.showToast(msg);
    }
}

function ucasAlert(title, msg, btn) {
    return window.shangkeBridgePromise.showAlert(title, msg, btn);
}

function ucasGetText(url) {
    return fetch(url, { method: 'GET', credentials: 'include' }).then(function (r) {
        if (!r.ok) throw new Error('请求失败（HTTP ' + r.status + '）');
        return r.text();
    });
}

// 跨域 fetch（xkgo → xkcts 等）：浏览器 CORS 会拦截，统一带 _webview_post_id 标记
// 交给原生拦截器转发，拦截器会回填 ACAO 响应头；详情页公开无需会话 → credentials:'omit'。
function ucasGetCrossText(url) {
    return fetch(url, { method: 'GET', credentials: 'omit' }).then(function (r) {
        if (!r.ok) throw new Error('请求失败（HTTP ' + r.status + '）');
        return r.text();
    });
}

// ---------- 周次解析: "第2-5,7-18周" / "第2-18周" / "第3周" ----------
function ucasParseWeeks(text) {
    var t = String(text || '');
    // 先取"第...周"整体内容（含逗号多段），没有"第"时用全文
    var inner = t;
    var wm = t.match(/第([\d,，\-~～至到]+)周/);
    if (wm) inner = wm[1] + '周';
    var weeks = {};
    var isOdd = /[（(]\s*单\s*[）)]/.test(t);
    var isEven = /[（(]\s*双\s*[）)]/.test(t);
    var re = /(\d+)\s*(?:[-~～至到]\s*(\d+))?\s*周?/g;
    var m;
    while ((m = re.exec(inner)) !== null) {
        var a = parseInt(m[1], 10);
        var b = m[2] ? parseInt(m[2], 10) : a;
        if (isNaN(a) || a < 1) continue;
        if (isNaN(b) || b < a) b = a;
        if (b > 35) b = 35;
        for (var w = a; w <= b; w++) {
            if (isOdd && w % 2 === 0) continue;
            if (isEven && w % 2 === 1) continue;
            weeks[w] = true;
        }
    }
    return Object.keys(weeks).map(function (k) { return parseInt(k, 10); })
        .sort(function (x, y) { return x - y; });
}

// ---------- 星期节次解析: "周一(3-4)" / "周四(10,12-13)" ----------
var UCAS_DAY_MAP = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7, '天': 7 };

function ucasParseDaySection(text) {
    // 返回 [{day, start, end}]；支持逗号多段 "周四(10,12-13)"
    var out = [];
    var re = /周([一二三四五六日天])\(([\d,，\-~～]+)\)/g;
    var m;
    while ((m = re.exec(text)) !== null) {
        var day = UCAS_DAY_MAP[m[1]];
        if (!day) continue;
        var segs = m[2].split(/[,，]/);
        for (var i = 0; i < segs.length; i++) {
            var sm = segs[i].match(/(\d+)(?:[-~～]\s*(\d+))?/);
            if (!sm) continue;
            var a = parseInt(sm[1], 10);
            var b = sm[2] ? parseInt(sm[2], 10) : a;
            if (isNaN(a) || a < 1 || b < a) continue;
            out.push({ day: day, start: a, end: b });
        }
    }
    return out;
}

// ---------- SEP → xkgo 选课系统 SSO ----------
// 入口: SEP「课程学习/课程选修/选课」站点 /portal/site/524/2412 内含一次性票据链接:
//   https://xkgo.ucas.ac.cn:3000/user/login?Identity=<uuid>&roleId=2412
var UCAS_XKGO_PORTAL = '/portal/site/524/2412';
var UCAS_XKGO_BASE = 'https://xkgo.ucas.ac.cn:3000';
var UCAS_XKCTS_BASE = 'https://xkcts.ucas.ac.cn:8443';

function ucasGetXkgoSsoUrl() {
    var here = '';
    try { here = window.location.href || ''; } catch (e) {}
    if (here.indexOf('xkgo.ucas.ac.cn') !== -1) {
        return Promise.resolve(null); // 已在 xkgo 域内
    }
    return ucasGetText(UCAS_XKGO_PORTAL).then(function (html) {
        if (/统一身份认证|login\?service=/.test(html) && html.indexOf('Identity=') === -1) {
            throw new Error('尚未登录 SEP，请先登录 SEP 后再点导入');
        }
        var m = html.match(/(https:\/\/xkgo\.ucas\.ac\.cn[^"'\s<>]+)/);
        if (!m) throw new Error('SEP 站点页未找到选课系统入口（请确认身份为学生角色）');
        return m[1];
    });
}

function ucasLoginXkgo(ssoUrl) {
    if (!ssoUrl) return Promise.resolve(null);
    // 跨域 SSO 登录同样带 _webview_post_id 标记，经原生拦截器转发（绕过 CORS 且自动带 Cookie）
    var sep = ssoUrl.indexOf('?') === -1 ? '?' : '&';
    return ucasGetCrossText(ssoUrl + sep + '_webview_post_id=1');
}

// ---------- 个人课表网格 → 课程ID列表 ----------
// 网格: thead=节次/星期+星期一~日; tbody 行 <tr><th>N</th><td>[<a href='.../course/coursetime/ID'>课名</a>]</td>x7
// 单元格无 title/data-* 扩展属性，网格仅含 星期(列)+节次(行)+课程名+课程ID；周次/地点须逐门拉详情。
function ucasParsePersonSchedule(html) {
    var ids = {};
    var names = {};
    var tbody = html.match(/<tbody[^>]*>([\s\S]*?)<\/tbody>/);
    var thead = html.match(/<thead[^>]*>([\s\S]*?)<\/thead>/);
    if (!tbody || !thead) throw new Error('个人课表页结构变化（未找到课程网格）');

    var dayCols = [];
    var ths = thead[1].match(/<th[^>]*>([\s\S]*?)<\/th>/g) || [];
    for (var i = 0; i < ths.length; i++) {
        var t = ths[i].replace(/<[^>]+>/g, '').replace(/\s+/g, '');
        var dm = t.match(/星期([一二三四五六日天])/);
        if (dm) dayCols.push(UCAS_DAY_MAP[dm[1]]);
    }
    if (dayCols.length < 5) throw new Error('个人课表页结构变化（星期表头缺失）');

    var rows = tbody[1].match(/<tr[^>]*>([\s\S]*?)<\/tr>/g) || [];
    for (var r = 0; r < rows.length; r++) {
        var secM = rows[r].match(/<th[^>]*>\s*(\d+)\s*<\/th>/);
        if (!secM) continue;
        var tds = rows[r].match(/<td[^>]*>([\s\S]*?)<\/td>/g) || [];
        for (var c = 0; c < dayCols.length; c++) {
            var tdHtml = tds[c] || '';
            var aM = tdHtml.match(/course\/coursetime\/(\d+)[^>]*>([^<]+)</);
            if (aM) {
                var id = aM[1];
                ids[id] = true;
                if (!names[id]) names[id] = aM[2].replace(/\s+/g, ' ').trim();
            }
        }
    }
    return { ids: Object.keys(ids), names: names };
}

// ---------- coursetime 详情页解析 ----------
// 结构: 课程名称：X / tr(上课时间|上课地点|上课周次) 可多组
//   时间 "星期一： 第1、2节。" / 周次 "5、6、7、8、9" 或区间
function ucasParseCoursetime(html, fallbackName) {
    var clean = function (s) { return String(s || '').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim(); };
    var name = (clean((html.match(/课程名称：\s*([^<\n]+)/) || [])[1]) || fallbackName || '').trim();

    var trs = html.match(/<tr[^>]*>[\s\S]*?<\/tr>/g) || [];
    var timeArr = [], roomArr = [], weekArr = [];
    for (var i = 0; i < trs.length; i++) {
        var t = trs[i];
        var tm = t.match(/上课时间<\/th>[\s\S]*?<td[^>]*>([\s\S]*?)<\/td>/);
        if (tm) timeArr.push(clean(tm[1]));
        var rm = t.match(/上课地点<\/th>[\s\S]*?<td[^>]*>([\s\S]*?)<\/td>/);
        if (rm) roomArr.push(clean(rm[1]));
        var wm = t.match(/上课周次<\/th>[\s\S]*?<td[^>]*>([\s\S]*?)<\/td>/);
        if (wm) weekArr.push(clean(wm[1]));
    }
    if (!timeArr.length) return [];

    var sessions = [];
    for (var k = 0; k < timeArr.length; k++) {
        var timeText = timeArr[k];
        var room = roomArr[k] || roomArr[0] || '待定';
        var weekText = weekArr[k] || weekArr[0] || '';

        var daySecRe = /星期([一二三四五六日天])\s*[：:]\s*第([\d、，,～\-—\-至到\s]+)节/g;
        var dm;
        while ((dm = daySecRe.exec(timeText)) !== null) {
            var day = UCAS_DAY_MAP[dm[1]];
            if (!day) continue;
            var nums = [];
            var parts = dm[2].split(/[、，,]/);
            for (var q = 0; q < parts.length; q++) {
                var sm = parts[q].match(/(\d+)(?:\s*[～\-—\-至到]\s*(\d+))?/);
                if (!sm) continue;
                var a = parseInt(sm[1], 10);
                var b = sm[2] ? parseInt(sm[2], 10) : a;
                if (isNaN(a) || a < 1) continue;
                if (isNaN(b) || b < a) b = a;
                for (var u = a; u <= b; u++) nums.push(u);
            }
            if (!nums.length) continue;

            var weeks = {};
            var isOdd = /[（(]\s*单\s*[）)]/.test(weekText);
            var isEven = /[（(]\s*双\s*[）)]/.test(weekText);
            var wkRe = /(\d+)\s*(?:[～\-—\-至到]\s*(\d+))?/g;
            var wm2;
            while ((wm2 = wkRe.exec(weekText)) !== null) {
                var a2 = parseInt(wm2[1], 10);
                var b2 = wm2[2] ? parseInt(wm2[2], 10) : a2;
                if (isNaN(a2) || a2 < 1) continue;
                if (isNaN(b2) || b2 < a2) b2 = a2;
                if (b2 > 35) b2 = 35;
                for (var w = a2; w <= b2; w++) {
                    if (isOdd && w % 2 === 0) continue;
                    if (isEven && w % 2 === 1) continue;
                    weeks[w] = true;
                }
            }
            var weekList = Object.keys(weeks).map(function (x) { return parseInt(x, 10); })
                .sort(function (p, q2) { return p - q2; });
            if (!weekList.length) continue;

            var sorted = nums.slice().sort(function (x, y) { return x - y; });
            var segs = [];
            var start = sorted[0], prev = sorted[0];
            for (var si = 1; si < sorted.length; si++) {
                if (sorted[si] === prev + 1) { prev = sorted[si]; }
                else { segs.push({ start: start, end: prev }); start = sorted[si]; prev = sorted[si]; }
            }
            segs.push({ start: start, end: prev });

            for (var gi = 0; gi < segs.length; gi++) {
                sessions.push({
                    name: name,
                    teacher: '未安排',
                    position: room,
                    day: day,
                    startSection: segs[gi].start,
                    endSection: segs[gi].end,
                    weeks: weekList,
                    isLab: /实验/.test(name)
                });
            }
        }
    }
    return sessions;
}

// ---------- 主流程（单阶段） ----------
// 阶段（全程停留在 xkgo 域，无页面跳转）：
//   经 SEP SSO 进 xkgo → 解析 personSchedule 网格课程ID
//   → 逐门经拦截器跨域拉 xkcts coursetime 详情（周次/地点）→ 保存
function ucasRunImport() {
    var bridge = window.shangkeBridgePromise;
    return bridge.showAlert(
        '国科大课表导入',
        '流程：自动经 SEP 进入选课系统，读取「个人课表」并逐门获取上课时间/地点。\n\n' +
        '请先登录 SEP，再点「确定」。',
        '开始导入'
    ).then(function (ok) {
        if (!ok) { ucasToast('导入已取消'); return null; }

        ucasToast('正在通过 SEP 进入选课系统...');
        return ucasGetXkgoSsoUrl().then(ucasLoginXkgo).then(function () {
            ucasToast('正在读取个人课表...');
            return ucasGetText(UCAS_XKGO_BASE + '/course/personSchedule');
        });
    }).then(function (html) {
        if (!html || /登录失败|请重新登录/.test(html)) {
            throw new Error('选课系统会话未建立：请从 SEP「课程学习→选课」进入一次后再试');
        }
        var parsed = ucasParsePersonSchedule(html);
        if (!parsed.ids.length) {
            throw new Error('个人课表为空（本学期尚未选课？请先在选课系统选课）');
        }
        ucasToast('课表共 ' + parsed.ids.length + ' 门课，正在获取上课时间/地点...');

        var all = [];
        var chain = Promise.resolve();
        parsed.ids.forEach(function (id) {
            chain = chain.then(function () {
                // 跨域详情经原生拦截器转发（_webview_post_id 标记 + ACAO 回填），绕过浏览器 CORS
                return ucasGetCrossText(UCAS_XKCTS_BASE + '/course/coursetime/' + id + '?_webview_post_id=1')
                    .then(function (detailHtml) {
                        var sessions = ucasParseCoursetime(detailHtml, parsed.names[id]);
                        for (var i = 0; i < sessions.length; i++) all.push(sessions[i]);
                    })
                    .catch(function (e) {
                        ucasToast('「' + (parsed.names[id] || id) + '」详情获取失败：' + (e && e.message ? e.message : e));
                    });
            });
        });
        return chain.then(function () {
            if (!all.length) throw new Error('课程详情全部获取失败（网络或选课系统问题，请重试）');
            return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(all))
                .then(function () {
                    return ucasAlert(
                        '导入完成',
                        '成功导入 ' + all.length + ' 个课程项（' + parsed.ids.length + ' 门课）。\n' +
                        '课程按节次导入；如上下课时间与学校作息不符，请到「设置 → 自定义时间段」调整。',
                        '完成'
                    );
                })
                .then(function () {
                    window.shangkeBridge.notifyTaskCompletion();
                });
        });
    }).catch(function (e) {
        ucasToast('导入失败：' + (e && e.message ? e.message : e));
    });
}

window.shangkeImportEntry = ucasRunImport;
window.ucasImport = ucasRunImport;
