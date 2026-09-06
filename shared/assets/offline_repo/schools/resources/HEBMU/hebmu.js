// 河北医科大学教务（广州乘方科技 · 新版 /new/ 框架）适配器 v1
// 入口：https://jwweb.hebmu.edu.cn/  （账号+密码+验证码登录，WebView 内用户真人登录）
// 数据源（登录态）：
//   GET  /new/student/xsgrkb/week.page?xnxqdm={学期}        课表页（含学期下拉 select#xnxqdm + 作息 businessHours）
//   POST /new/student/xsgrkb/getCalendarWeekDatas            课表数据（FullCalendar events 源）
//        form: xnxqdm, szxqdm, yxdm, zc, d1, d2
//        返回 {code:0, data:[{kcmc, teaxms, jxcdmc, qsxq, ps, pe, zc, jxhjmc, ...}]}
//        qsxq=起始星期(1-7), ps/pe=起止节次(如"03"/"04"), zc=周次逗号分隔字符串
// 作息：页面内嵌 businessHours JSON，13 节课（08:00-21:40），含午休。
// 桥接契约：window.shangkeImportEntry 由注入器自动调用。

var HEBMU_MAX_PAGES = 1;

function hebmuToast(msg) {
    if (window.shangkeBridge && typeof window.shangkeBridge.showToast === 'function') {
        window.shangkeBridge.showToast(msg);
    }
}

function hebmuAlert(title, msg, btn) {
    return window.shangkeBridgePromise.showAlert(title, msg, btn);
}

function hebmuGet(path) {
    return fetch(path, { method: 'GET', credentials: 'include' })
        .then(function (r) {
            if (!r.ok) throw new Error('请求失败（HTTP ' + r.status + '）');
            return r.text();
        });
}

function hebmuPost(path, params) {
    var body = Object.keys(params).map(function (k) {
        return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]);
    }).join('&');
    return fetch(path, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body
    }).then(function (r) {
        if (!r.ok) throw new Error('请求失败（HTTP ' + r.status + '）');
        return r.json();
    });
}

// 从 week.page 提取学期列表（select#xnxqdm）
function hebmuExtractSemesters(htmlText) {
    var doc = new DOMParser().parseFromString(htmlText, 'text/html');
    var sel = doc.getElementById('xnxqdm');
    if (!sel) throw new Error('未找到学期列表，请确认已登录教务系统');
    var labels = [], values = [], defaultIndex = 0;
    var options = sel.querySelectorAll('option');
    for (var i = 0; i < options.length; i++) {
        var op = options[i];
        var v = (op.getAttribute('value') || '').trim();
        if (!v) continue;
        labels.push((op.textContent || '').trim());
        values.push(v);
        if (op.hasAttribute('selected')) defaultIndex = labels.length - 1;
    }
    if (labels.length === 0) throw new Error('学期列表为空');
    // 默认学期前后各留少量可选项
    var start = Math.max(0, defaultIndex - 1);
    var end = Math.min(labels.length, defaultIndex + 8);
    return {
        labels: labels.slice(start, end),
        values: values.slice(start, end),
        defaultIndex: defaultIndex - start
    };
}

// 从 week.page 提取隐藏字段（szxqdm, yxdm）
function hebmuExtractHiddenFields(htmlText) {
    var doc = new DOMParser().parseFromString(htmlText, 'text/html');
    var get = function (id) {
        var el = doc.getElementById(id);
        return el ? (el.value || '') : '';
    };
    return { szxqdm: get('szxqdm'), yxdm: get('yxdm') };
}

// 解析周次字符串 "16,18,17" → [16,17,18]
function hebmuParseWeeks(zcStr) {
    if (!zcStr) return [];
    var parts = String(zcStr).split(',');
    var weeks = [];
    for (var i = 0; i < parts.length; i++) {
        var w = parseInt(parts[i].trim(), 10);
        if (!isNaN(w) && w >= 1 && w <= 30 && weeks.indexOf(w) === -1) weeks.push(w);
    }
    weeks.sort(function (a, b) { return a - b; });
    return weeks;
}

// 课程数据 → 课程块：按（课程|教师|场地|星期|节次）分组合并周次
function hebmuBuildCourseBlocks(rows) {
    var map = {};
    var order = [];
    for (var i = 0; i < rows.length; i++) {
        var r = rows[i];
        var name = String(r.kcmc || '').trim();
        if (!name) continue;
        var day = parseInt(r.qsxq, 10);
        if (isNaN(day) || day < 1 || day > 7) continue;
        var startSec = parseInt(r.ps, 10);
        var endSec = parseInt(r.pe, 10);
        if (isNaN(startSec) || isNaN(endSec) || startSec < 1 || endSec > 13 || startSec > endSec) continue;
        var weeks = hebmuParseWeeks(r.zc);
        if (weeks.length === 0) continue;

        var teacher = String(r.teaxms || '').trim() || '未安排';
        var position = String(r.jxcdmc || '').trim() || '待定';
        var type = String(r.jxhjmc || '').trim();

        var key = [name, teacher, position, day, startSec, endSec].join('__');
        if (!map[key]) {
            map[key] = {
                name: name, teacher: teacher, position: position,
                day: day, startSection: startSec, endSection: endSec,
                type: type, weeks: []
            };
            order.push(key);
        }
        for (var w = 0; w < weeks.length; w++) {
            if (map[key].weeks.indexOf(weeks[w]) === -1) map[key].weeks.push(weeks[w]);
        }
    }

    var blocks = [];
    for (var k = 0; k < order.length; k++) {
        var b = map[order[k]];
        b.weeks.sort(function (a, c) { return a - c; });
        blocks.push({
            name: b.name,
            teacher: b.teacher,
            position: b.position,
            day: b.day,
            startSection: b.startSection,
            endSection: b.endSection,
            weeks: b.weeks,
            isLab: b.type === '实验课' || b.type === '实验教学'
        });
    }
    return blocks;
}

// 获取指定学期的全部课表数据
function hebmuFetchCourses(xnxqdm, hidden) {
    // 日期范围覆盖一周即可（每条记录的 zc 字段含完整周次）
    var params = {
        xnxqdm: xnxqdm,
        szxqdm: hidden.szxqdm || '',
        yxdm: hidden.yxdm || '',
        zc: '',
        d1: '2026-09-06 00:00:00',
        d2: '2026-09-13 00:00:00'
    };
    return hebmuPost('/new/student/xsgrkb/getCalendarWeekDatas', params)
        .then(function (data) {
            if (data.code < 0) throw new Error(data.message || '课表查询失败');
            if (!data || !Array.isArray(data.data)) throw new Error('课表接口返回格式不正确');
            return data.data;
        });
}

// ---------- 主流程 ----------
function hebmuRunImport() {
    var bridge = window.shangkeBridgePromise;
    return bridge.showAlert(
        '河北医科大学教务导入',
        '请先在上方页面完成登录（账号+密码+验证码）。\n登录成功后，回到本页面再点击「确定」开始导入。',
        '开始导入'
    ).then(function (ok) {
        if (!ok) { hebmuToast('导入已取消'); return null; }

        // 1. 登录校验 + 学期列表 + 隐藏字段
        return hebmuGet('/new/student/xsgrkb/week.page').then(function (html) {
            if (html.indexOf('课表查询') === -1 && html.indexOf('xnxqdm') === -1) {
                throw new Error('尚未登录或登录已过期，请先在页面中完成登录后重试');
            }
            return {
                semesters: hebmuExtractSemesters(html),
                hidden: hebmuExtractHiddenFields(html)
            };
        });
    }).then(function (ctx) {
        if (!ctx) return null;

        // 2. 选学期
        return bridge.showSingleSelection(
            '选择学期',
            JSON.stringify(ctx.semesters.labels),
            ctx.semesters.defaultIndex
        ).then(function (idx) {
            if (idx === null || idx < 0 || idx >= ctx.semesters.values.length) {
                hebmuToast('导入已取消');
                return null;
            }
            return {
                label: ctx.semesters.labels[idx],
                value: ctx.semesters.values[idx],
                hidden: ctx.hidden
            };
        });
    }).then(function (semester) {
        if (!semester) return null;
        hebmuToast('正在获取 ' + semester.label + ' 的课表...');

        // 3. 拉取课表数据并解析
        return hebmuFetchCourses(semester.value, semester.hidden).then(function (rows) {
            var blocks = hebmuBuildCourseBlocks(rows);
            if (blocks.length === 0) {
                throw new Error('该学期没有获取到课程数据，请检查登录状态与所选学期');
            }

            // 4. 保存
            return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(blocks))
                .then(function () {
                    return hebmuAlert(
                        '导入完成',
                        '成功导入 ' + blocks.length + ' 个课程项（' + semester.label + '）。\n\n' +
                        '说明：课程按节次导入；如上下课时间与学校作息不符，\n' +
                        '请到「设置 → 自定义时间段」调整。',
                        '完成'
                    );
                })
                .then(function () {
                    window.shangkeBridge.notifyTaskCompletion();
                });
        });
    }).catch(function (error) {
        return hebmuAlert('导入失败', (error && error.message) || String(error), '确定');
    });
}

if (typeof window !== 'undefined') {
    window.shangkeImportEntry = hebmuRunImport;
}
