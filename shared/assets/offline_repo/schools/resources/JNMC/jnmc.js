// 济宁医学院教务（广州乘方教务 · Struts2 老版）适配器 v2
// 流程：确认登录 → 选学期 → 拉课表 JSON（分页） → 合并周次 → 保存课程
// 数据源：
//   GET /xsgrkbcx!getXsgrbkList.action                                学期下拉（HTML select#xnxqdm）
//   GET /xsgrkbcx!getDataList.action?xnxqdm=&zc=&page=&rows=          排课记录 JSON（easyui datagrid）
//       行字段: kcmc/kcbh/teaxms/jxcdmc/zc/xq/jcdm/jxhjmc/jxbmc/pkrs...
//       jcdm 为 2 位一节的连续数字串："0102"=第1-2节，"0607080910"=第6-10节
// 作息时间（节次↔时间）：按需求不做自动获取；教务无完整时间表，课程按节次导入，
//       完成后提醒用户在 App「设置 → 自定义时间段」按学校作息配置。
// 桥接契约：window.shangkeImportEntry 由注入器自动调用（WebBridgeProtocol.JS_IMPORT_AUTOSTART）。

var JNMC_HOST = '210.44.16.13';
var JNMC_MAX_PAGES = 8;
var JNMC_PAGE_ROWS = 200;

function jnmcToast(msg) {
    if (window.shangkeBridge && typeof window.shangkeBridge.showToast === 'function') {
        window.shangkeBridge.showToast(msg);
    }
}

function jnmcAlert(title, msg, btn) {
    return window.shangkeBridgePromise.showAlert(title, msg, btn);
}

// 相对路径 fetch（同源，携带登录态）
function jnmcGet(path) {
    return fetch(path, { method: 'GET', credentials: 'include' })
        .then(function (r) {
            if (!r.ok) throw new Error('请求失败（HTTP ' + r.status + '）');
            return r.text();
        });
}

function jnmcGetJson(path) {
    return jnmcGet(path).then(function (t) { return JSON.parse(t); });
}

// ---------- 学期 ----------
// 从「学生个人课表」页解析学期下拉（select#xnxqdm），返回 {labels, values, defaultIndex}
function jnmcExtractSemesters(htmlText) {
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
    // 与 CMC 一致：默认学期前后各留少量可选项
    var start = Math.max(0, defaultIndex - 1);
    var end = Math.min(labels.length, defaultIndex + 10);
    return {
        labels: labels.slice(start, end),
        values: values.slice(start, end),
        defaultIndex: defaultIndex - start
    };
}

// ---------- 排课记录 ----------
function jnmcParseJcdm(s) {
    var str = String(s || '').trim();
    if (!/^\d+$/.test(str)) return null;
    if (str.length < 2 || str.length % 2 !== 0) return null;
    var start = parseInt(str.slice(0, 2), 10);
    var end = parseInt(str.slice(str.length - 2), 10);
    if (isNaN(start) || isNaN(end) || start < 1 || end > 24 || start > end) return null;
    return { start: start, end: end };
}

// 行 → 课程块：按（课程|教师|场地|星期|节次|类型）分组合并周次
function jnmcBuildCourseBlocks(rows) {
    var map = {};       // key -> block
    var order = [];     // 保持首次出现顺序
    for (var i = 0; i < rows.length; i++) {
        var r = rows[i];
        var name = String(r.kcmc || '').trim();
        if (!name) continue;
        var day = parseInt(r.xq, 10);
        if (isNaN(day) || day < 1 || day > 7) continue;
        var jc = jnmcParseJcdm(r.jcdm);
        if (!jc) continue;
        var week = parseInt(r.zc, 10);
        if (isNaN(week) || week < 1 || week > 30) continue;

        var teacher = String(r.teaxms || '').trim() || '未安排';
        var position = String(r.jxcdmc || '').trim() || '待定';
        var type = String(r.jxhjmc || '').trim();

        var key = [name, teacher, position, day, jc.start, jc.end, type].join('__');
        if (!map[key]) {
            map[key] = {
                name: name,
                teacher: teacher,
                position: position,
                day: day,
                startSection: jc.start,
                endSection: jc.end,
                type: type,
                weeks: []
            };
            order.push(key);
        }
        if (map[key].weeks.indexOf(week) === -1) map[key].weeks.push(week);
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
            isLab: b.type === '实验教学'
        });
    }
    return blocks;
}

// 分页拉取指定学期全部排课记录
function jnmcFetchAllRows(xnxqdm) {
    var all = [];
    function fetchPage(page) {
        return jnmcGetJson('/xsgrkbcx!getDataList.action?xnxqdm=' + encodeURIComponent(xnxqdm) +
            '&zc=&page=' + page + '&rows=' + JNMC_PAGE_ROWS + '&sort=kxh&order=asc')
            .then(function (j) {
                if (!j || !Array.isArray(j.rows)) throw new Error('课表接口返回格式不正确（请确认已登录）');
                all = all.concat(j.rows);
                var total = parseInt(j.total, 10);
                if (isNaN(total) || all.length >= total || j.rows.length === 0 || page >= JNMC_MAX_PAGES) {
                    return all;
                }
                return fetchPage(page + 1);
            });
    }
    return fetchPage(1);
}

// ---------- 主流程 ----------
function jnmcRunImport() {
    var bridge = window.shangkeBridgePromise;
    return bridge.showAlert(
        '济宁医学院教务导入',
        '请先在上方页面完成登录（账号+密码+验证码）。\n登录成功后，回到本页面再点击「确定」开始导入。',
        '开始导入'
    ).then(function (ok) {
        if (!ok) { jnmcToast('导入已取消'); return null; }

        // 1. 登录校验 + 学期列表
        return jnmcGet('/xsgrkbcx!getXsgrbkList.action').then(function (html) {
            if (html.indexOf('学生个人课表') === -1) {
                throw new Error('尚未登录或登录已过期，请先在页面中完成登录后重试');
            }
            return jnmcExtractSemesters(html);
        });
    }).then(function (sem) {
        if (!sem) return null;

        // 2. 选学期
        return bridge.showSingleSelection('选择学期', JSON.stringify(sem.labels), sem.defaultIndex)
            .then(function (idx) {
                if (idx === null || idx < 0 || idx >= sem.values.length) {
                    jnmcToast('导入已取消');
                    return null;
                }
                return { label: sem.labels[idx], value: sem.values[idx] };
            });
    }).then(function (semester) {
        if (!semester) return null;
        jnmcToast('正在获取 ' + semester.label + ' 的课表...');

        // 3. 拉取排课记录并解析
        return jnmcFetchAllRows(semester.value).then(function (rows) {
            var blocks = jnmcBuildCourseBlocks(rows);
            if (blocks.length === 0) {
                throw new Error('该学期没有获取到课程数据，请检查登录状态与所选学期');
            }

            // 4. 保存课程（按节次导入；作息时间不自动获取）
            return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(blocks))
                .then(function () {
                    return jnmcAlert(
                        '导入完成',
                        '成功导入 ' + blocks.length + ' 个课程项（' + semester.label + '）。\n\n' +
                        '说明：本次未获取作息时间，课程按节次显示；\n' +
                        '如上下课时间与学校作息不符，请到「设置 → 自定义时间段」调整。',
                        '完成'
                    );
                })
                .then(function () {
                    window.shangkeBridge.notifyTaskCompletion();
                });
        });
    }).catch(function (error) {
        return jnmcAlert('导入失败', (error && error.message) || String(error), '确定');
    });
}

// 注入器（JS_IMPORT_AUTOSTART）会调用 window.shangkeImportEntry
if (typeof window !== 'undefined') {
    window.shangkeImportEntry = jnmcRunImport;
}
