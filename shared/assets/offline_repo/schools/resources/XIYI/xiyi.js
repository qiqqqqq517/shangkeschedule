// 西安医学院专用适配器 v3
// 入口：http://ehall.xiyi.edu.cn/new/index_teacher_phone.html（金智网上办事大厅，authserver 统一认证）
// 教务系统：青果(kingosoft)，http://jwxt.xiyi.edu.cn:8080/xayxyjw/
// 链路：办事大厅登录 → 打开「教务系统」应用(CAS 免登) → 青果教务任意页 → 点「执行导入」
// 适配策略：
//   ① 识别页面状态（ehall登录页 / ehall大厅页 / 青果教务页），未到青果页时给出引导；
//   ② 青果页内用同源 XHR 直取「我的课表」数据接口：
//      - 学期列表  GET frame/droplist/getDropLists.action?comboBoxName=StMsXnxqDxDesc
//      - 个人课表  GET wsxk/xkjg.ckdgxsxdkchj_data10319.jsp?params=base64("xn=..&xq=..&xh=<学号>")
//        （学号取自 SetMainInfo.jsp 注入的 G_USER_CODE）
//      - 响应 charset=GBK，用 TextDecoder('gbk') 解码
//   ③ 列表表格解析失败时回退 DOM 矩阵解析（课表网格页）。
(function () {
    'use strict';

    var WEEKDAY_MAP = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7, '天': 7 };

    function showToast(msg) {
        try {
            if (window.shangkeBridge && window.shangkeBridge.showToast) window.shangkeBridge.showToast(msg);
            else if (window.Bridge && window.Bridge.showToast) window.Bridge.showToast(msg);
        } catch (e) {}
    }

    function showAlert(title, message, confirmText) {
        try {
            if (window.shangkeBridgePromise && window.shangkeBridgePromise.showAlert) {
                return window.shangkeBridgePromise.showAlert(title, message, confirmText || '知道了');
            }
            if (window.Bridge && window.Bridge.showAlert) {
                return window.Bridge.showAlert(title, message, confirmText || '知道了');
            }
        } catch (e) {}
        showToast(message);
        return Promise.resolve(true);
    }

    function cleanText(s) {
        return String(s == null ? '' : s)
            .replace(/ /g, ' ')
            .replace(/&ensp;/g, ' ')
            .replace(/&nbsp;/g, ' ')
            .replace(/\s+/g, ' ')
            .trim();
    }

    // ---------- 响应解码 ----------
    // 该校编码不统一：课表数据页 charset=GBK，学期下拉 JSON 与失效提示为 UTF-8，须按接口显式指定。
    var decoderCache = {};

    function getDecoder(charset) {
        if (!decoderCache[charset]) {
            try { decoderCache[charset] = new TextDecoder(charset); } catch (e) { decoderCache[charset] = null; }
        }
        return decoderCache[charset];
    }

    function decodeResponse(buffer, charset) {
        var dec = getDecoder(charset || 'utf-8');
        if (dec) {
            try { return dec.decode(buffer); } catch (e) {}
        }
        // 兜底：按 latin1 逐字节还原（纯 ASCII 的 JSON 不受影响）
        var bytes = new Uint8Array(buffer);
        var s = '';
        for (var i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
        return decodeURIComponent(escape(s));
    }

    function xget(path, charset) {
        return fetch(path, { method: 'GET', credentials: 'include' }).then(function (r) {
            if (!r.ok) throw new Error('请求失败 HTTP ' + r.status + ' ' + path);
            return r.arrayBuffer();
        }).then(function (buf) { return decodeResponse(buf, charset); });
    }

    // ---------- 页面状态识别 ----------
    function getCurrentUrl() {
        try { return window.location.href || ''; } catch (e) { return ''; }
    }

    function isLoginPage() {
        var url = getCurrentUrl();
        if (/authserver\./i.test(url) && /login/i.test(url)) return true;
        if (/cas\.xiyi/i.test(url)) return true;
        try {
            var body = (document.body && document.body.innerText) || '';
            if (body.indexOf('统一身份认证') !== -1 && (body.indexOf('密码') !== -1 || body.indexOf('学号') !== -1)) return true;
        } catch (e) {}
        return false;
    }

    function isEhallPortal() {
        return /ehall\.xiyi\.edu\.cn/i.test(getCurrentUrl());
    }

    function isKingosoftPage() {
        var url = getCurrentUrl();
        if (/jwxt\.xiyi\.edu\.cn/i.test(url) || /xayxyjw/i.test(url)) return true;
        // iframe 嵌入的青果页
        try {
            var frames = document.querySelectorAll('iframe');
            for (var i = 0; i < frames.length; i++) {
                var src = frames[i].getAttribute('src') || '';
                if (/xayxyjw|jwxt/i.test(src)) return true;
            }
        } catch (e) {}
        return typeof window.G_SCHOOL_CODE === 'string' && window.G_SCHOOL_CODE === '60844';
    }

    // ---------- 学号 ----------
    function resolveStudentId() {
        // 青果页面全局注入 SetMainInfo.jsp: G_USER_CODE / G_LOGIN_ID
        if (typeof window.G_USER_CODE === 'string' && /^\d{6,}$/.test(window.G_USER_CODE)) {
            return window.G_USER_CODE;
        }
        if (typeof window.G_LOGIN_ID === 'string' && /^\d{6,}$/.test(window.G_LOGIN_ID)) {
            return window.G_LOGIN_ID;
        }
        return '';
    }

    // ---------- base64（参数为纯 ASCII，btoa 足够） ----------
    function b64(s) {
        try { return btoa(s); } catch (e) {
            return window.btoa(unescape(encodeURIComponent(s)));
        }
    }

    // ---------- 学期 ----------
    function fetchSemesters() {
        return xget('/xayxyjw/frame/droplist/getDropLists.action?comboBoxName=StMsXnxqDxDesc&paramValue=&isYXB=0&isCDDW=0', 'utf-8')
            .then(function (txt) {
                var arr = JSON.parse(txt);
                var labels = [];
                var values = [];
                var defaultIndex = 0;
                for (var i = 0; i < arr.length; i++) {
                    labels.push(arr[i].name);
                    values.push(arr[i].code); // 形如 "2026-0"
                    if (i === 0) defaultIndex = 0; // 接口已按最新在前排序
                }
                return { labels: labels, values: values, defaultIndex: defaultIndex };
            });
    }

    function pickSemester(sems) {
        if (window.shangkeBridgePromise && window.shangkeBridgePromise.showSingleSelection) {
            return window.shangkeBridgePromise.showSingleSelection(
                '选择学年学期',
                JSON.stringify(sems.labels),
                sems.defaultIndex
            ).then(function (idx) {
                if (idx === null || idx < 0) return null;
                return sems.values[idx];
            });
        }
        return Promise.resolve(sems.values[sems.defaultIndex]);
    }

    // ---------- 个人课表（列表接口） ----------
    // 返回形如 "2026-0" 的学期值 → xn=2026, xq=0
    function fetchPersonalSchedule(xnxq, studentId) {
        var parts = xnxq.split('-');
        var xn = parts[0];
        var xq = parts.length > 1 ? parts[1] : '0';
        var params = 'xn=' + xn + '&xq=' + xq + '&xh=' + studentId;
        var path = '/xayxyjw/wsxk/xkjg.ckdgxsxdkchj_data10319.jsp?params=' + b64(params);
        return xget(path, 'gbk').then(function (html) {
            // 会话失效时教务返回 alert 跳转脚本（中文可能因编码差异变乱码，
            // 故用 ASCII 特征识别：alert( + location.href='/xayxyjw/'）
            if (/<script>\s*alert\(/.test(html) && /location\.href\s*=\s*'\/xayxyjw\//.test(html)) {
                throw new Error('教务会话已失效，请回到「教务系统」重新进入后再点导入');
            }
            if (/style="color:red;"/.test(html) || /40[34]|Not Found/i.test(html.slice(0, 500))) {
                throw new Error('接口访问受限，请确认已登录教务系统');
            }
            return html;
        });
    }

    // 解析列表表格：列 = 班级代码|班级名称|课程|学时|学分|修读性质|教师|选课状态|外年级|教材|上课时间地点|备注
    function parseListTable(html) {
        var courses = [];
        var rowRe = /<tr[^>]*>([\s\S]*?)<\/tr>/g;
        var m;
        while ((m = rowRe.exec(html)) !== null) {
            var rowHtml = m[1];
            var cells = [];
            var cellRe = /<t[dh][^>]*>([\s\S]*?)<\/t[dh]>/g;
            var cm;
            while ((cm = cellRe.exec(rowHtml)) !== null) {
                cells.push(cleanText(cm[1].replace(/<[^>]+>/g, ' ')));
            }
            if (cells.length < 11) continue;
            if (!/^\d+-\d+$/.test(cells[0])) continue; // 首列 = 上课班级代码(如 3140593-002)
            var courseRaw = cells[2] || '';
            var nameMatch = courseRaw.match(/^\[[^\]]*\]\s*(.*)$/);
            var courseName = cleanText(nameMatch ? nameMatch[1] : courseRaw);
            if (!courseName) continue;

            var teacher = cleanText(String(cells[6] || '').replace(/\[[^\]]*\]/g, '')) || '未安排';
            var creditMatch = String(cells[4] || '').match(/[\d.]+/);
            var credit = creditMatch ? creditMatch[0] : '';
            var timeCell = cells[10] || '';
            var remark = cells[11] || '';

            // 上课时间地点: "1-2周 三[5-6] 教学楼210(160)；1-18周 四[3-4] 教学楼阶二(300)"
            var slotRe = /([\d,，\d\-~－—至]+)周\s*([一二三四五六日天])\s*(?:\[?(\d+)\s*[-~－—至]\s*(\d+)\]?)?([^；;]*)/g;
            var sm;
            var found = false;
            while ((sm = slotRe.exec(timeCell)) !== null) {
                var weeksStr = sm[1];
                var dayChar = sm[2];
                var secStart = sm[3] ? parseInt(sm[3], 10) : null;
                var secEnd = sm[4] ? parseInt(sm[4], 10) : secStart;
                var position = cleanText(sm[5]) || '待定';
                var day = WEEKDAY_MAP[dayChar] || 0;
                if (!day) continue;
                var weeks = parseWeeks(weeksStr);
                if (weeks.length === 0) continue;
                found = true;
                courses.push({
                    name: courseName,
                    teacher: teacher,
                    position: position,
                    day: day,
                    startSection: secStart,
                    endSection: secEnd,
                    weeks: weeks,
                    credit: credit,
                    remark: remark ? cleanText(remark) : courseRaw
                });
            }
            if (!found && weeksOf(timeCell) === false) {
                // 无有效时段（如仅周次无节次），仍尝试整段解析
                var weeks2 = parseWeeks(timeCell);
                if (weeks2.length > 0) {
                    courses.push({
                        name: courseName,
                        teacher: teacher,
                        position: '待定',
                        day: 1,
                        startSection: null,
                        endSection: null,
                        weeks: weeks2,
                        credit: credit,
                        remark: cleanText(timeCell)
                    });
                }
            }
        }
        return dedupe(courses);
    }

    function weeksOf() { return false; }

    function parseWeeks(s) {
        if (!s) return [];
        var weeks = {};
        var found = false;
        var isOdd = /[（(]\s*单\s*[）)]/.test(s);
        var isEven = /[（(]\s*双\s*[）)]/.test(s);
        // 逗号/顿号是列举分隔，仅 - ~ 至 到 构成范围（如 "1,3,5,7周" ≠ 1-7周）
        var re = /(\d+)\s*(?:(?:[-~－—至到])\s*(\d+))?/g;
        var m;
        var str = String(s).replace(/周/g, ' ');
        while ((m = re.exec(str)) !== null) {
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

    function dedupe(courses) {
        var seen = {};
        var out = [];
        for (var i = 0; i < courses.length; i++) {
            var c = courses[i];
            var key = c.name + '|' + c.teacher + '|' + c.position + '|' + c.day + '|' +
                c.startSection + '|' + c.endSection + '|' + c.weeks.join(',');
            if (seen[key]) continue;
            seen[key] = true;
            out.push(c);
        }
        return out;
    }

    // ---------- 回退：DOM 网格解析（沿用 v2 逻辑精简版） ----------
    function collectDocuments() {
        var docs = [document];
        try {
            var frames = document.querySelectorAll('iframe');
            for (var i = 0; i < frames.length; i++) {
                var d = null;
                try { d = frames[i].contentDocument; if (!d && frames[i].contentWindow) d = frames[i].contentWindow.document; } catch (e) {}
                if (d) docs.push(d);
            }
        } catch (e) {}
        return docs;
    }

    function findGridTable() {
        var docs = collectDocuments();
        var best = null;
        var bestScore = 0;
        for (var d = 0; d < docs.length; d++) {
            var tables = [];
            try { tables = docs[d].querySelectorAll('table'); } catch (e) {}
            for (var i = 0; i < tables.length; i++) {
                var text = tables[i].innerText || tables[i].textContent || '';
                var score = 0;
                if (text.indexOf('星期一') !== -1) score += 4;
                if (text.indexOf('节次') !== -1) score += 3;
                if (text.indexOf('上午') !== -1) score += 2;
                if (score > bestScore) { bestScore = score; best = tables[i]; }
            }
        }
        return bestScore >= 4 ? best : null;
    }

    function extractFromGrid(table) {
        // 交给通用网格解析：遍历单元格文本 "课程名 教师 周次 星期 节次"
        var courses = [];
        var trs = table.querySelectorAll('tr');
        for (var r = 0; r < trs.length; r++) {
            var tds = trs[r].querySelectorAll('td');
            for (var c = 0; c < tds.length; c++) {
                var text = cleanText(tds[c].innerText || tds[c].textContent || '');
                if (!text || text.length < 4) continue;
                // 该校网格单元格格式: "课程名 教师 周次[节次] 地点"（二维表视图）
                var m = text.match(/^([\s\S]+?)\s+([\d,，\-~－—至]+)\[([一二三四五六日天])\]\s*(\d+)-(\d+)?/);
                if (!m) continue;
                var weeks = parseWeeks(m[2]);
                if (!weeks.length) continue;
                courses.push({
                    name: cleanText(m[1]),
                    teacher: '',
                    position: cleanText(text.replace(m[0], '')) || '待定',
                    day: WEEKDAY_MAP[m[3]] || c,
                    startSection: parseInt(m[4], 10),
                    endSection: m[5] ? parseInt(m[5], 10) : parseInt(m[4], 10),
                    weeks: weeks,
                    remark: text.slice(0, 200)
                });
            }
        }
        return dedupe(courses);
    }

    // ---------- 主流程 ----------
    async function runImport() {
        try {
            // 状态1: 未登录（authserver 登录页）
            if (isLoginPage()) {
                await showAlert('需要登录', '请先在当前页面登录西安医学院网上办事大厅（账号=学号，密码=统一认证密码），\n登录后点击页面中的「教务系统」应用进入教务，再点本页「执行导入」。');
                return;
            }

            // 状态2: 还在 ehall 大厅，未进入教务系统
            if (isEhallPortal() && !isKingosoftPage()) {
                var go = await showAlert('请先进入教务系统',
                    '已登录办事大厅，但还未进入教务系统。\n\n点击「确定」自动打开「教务管理系统」，\n进入青果教务系统后，再点「执行导入」。', '确定');
                if (go) {
                    try { window.top.location.href = 'http://ehall.xiyi.edu.cn/appShow?appId=5590454032412716'; } catch (e) {}
                }
                return;
            }

            // 状态3: 青果教务页（或其 iframe 宿主）
            var studentId = resolveStudentId();
            if (!studentId) {
                // iframe 场景下 G_USER_CODE 可能取不到，提示先打开课表页
                var grid = findGridTable();
                if (grid) {
                    var gridCourses = extractFromGrid(grid);
                    if (gridCourses.length > 0) {
                        await window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(gridCourses));
                        showToast('成功解析 ' + gridCourses.length + ' 门课程（页面解析）');
                        if (window.shangkeBridge && window.shangkeBridge.notifyTaskCompletion) window.shangkeBridge.notifyTaskCompletion();
                        return;
                    }
                }
                await showAlert('缺少学号', '无法自动获取学号。\n请先在教务系统左侧菜单点开「我的课表」（教学安排→我的课表），再点「执行导入」。');
                return;
            }

            showToast('正在获取学期列表…');
            var sems = await fetchSemesters();
            if (!sems.labels.length) throw new Error('未获取到学年学期列表');
            var xnxq = await pickSemester(sems);
            if (!xnxq) { showToast('已取消导入'); return; }

            showToast('正在获取课表…');
            var html = await fetchPersonalSchedule(xnxq, studentId);
            var courses = parseListTable(html);

            if (courses.length === 0) {
                // 回退：当前页面网格
                var grid2 = findGridTable();
                if (grid2) courses = extractFromGrid(grid2);
            }

            if (courses.length === 0) {
                await showAlert('未解析出课程', '已获取课表数据，但未解析出有效课程。\n该学期可能没有课程，或数据格式变化，请反馈给作者。');
                return;
            }

            await window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(courses));
            showToast('成功导入 ' + courses.length + ' 条课程安排');
            if (window.shangkeBridge && window.shangkeBridge.notifyTaskCompletion) {
                window.shangkeBridge.notifyTaskCompletion();
            }
        } catch (e) {
            var msg = (e && e.message) ? e.message : String(e);
            showToast('导入失败: ' + msg);
        }
    }

    window.shangkeImportEntry = runImport;
    window.xiyiImport = runImport;

    // 自动检测页面状态并提示
    try {
        if (isLoginPage()) {
            showToast('西安医学院：请先登录网上办事大厅');
        } else if (isEhallPortal() && !isKingosoftPage()) {
            showToast('西安医学院：请点击「教务管理系统」应用进入教务后再导入');
        } else if (isKingosoftPage()) {
            showToast('西安医学院：点击底部「执行导入」即可抓取课表');
        }
    } catch (e) {}
})();
