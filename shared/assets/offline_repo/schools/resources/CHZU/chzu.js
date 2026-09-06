// 滁州学院教务（金智 EAMS 老版，经联创统一身份认证 sso.chzu.edu.cn）适配器 v2
// 入口：https://sso.chzu.edu.cn/login?service=https%3A%2F%2Fjwgl.chzu.edu.cn%2Feams%2FhomeExt.action
//       联创 SSO（Angular SPA + DES 加密 + 验证码）在 WebView 内由用户真人登录，脚本不做自动登录。
// 教务：https://jwgl.chzu.edu.cn/eams/  金智 EAMS 老版
// 数据源（登录态）：
//   GET  /eams/courseTableForStd.action                        课表入口页
//        内含 bg.form.addInput(form,"ids","...") 与 semesterCalendar({value:"242"})（当前学期ID）
//   POST /eams/courseTableForStd!courseTable.action?sf_request_type=ajax
//        form: ignoreHead=1&setting.kind=std&startWeek=&project.id=1&semester.id={id}&ids={ids}
//        返回 HTML 内嵌 JS: var unitCount=10; var teachers=[{id,name,lab}];
//        activity=new TaskActivity(teacherIdExpr, teacherNameExpr, courseNo, courseName, subName, ?, room, weekBitmap, ...)
//        index = day*unitCount + unit; （index 表达式可能跨行）
// v2 修复（2026-09-06）：
//   1. 学期列表：dataQuery 接口(tagId=semesterBarSemester / semesterBar20826294511Semester)返回 HTTP 500，
//      改为从课表入口页内嵌的 semesterCalendar({value:"242"}) 直接提取当前学期 ID。
//   2. TaskActivity 正则：原正则要求 "var activity = new TaskActivity"，实际为 "activity = new TaskActivity"
//      （复用前置声明的 var activity=null），且 teachers 与 activity 之间有 actTeachers/assistant 等中间代码。
//   3. index 表达式跨行：实际为 "index =\n2*\nunitCount +0;"，先压缩空白再匹配。
//   4. unitCount：原硬编码 12，实际页面 var unitCount=10，改为动态提取。
//   5. 参数序：教师名=args[1]表达式、课程名=args[3]、教室=args[6]、周次bitmap=args[7]（原 args[5]/args[6] 错误）。
//   6. 教师名：args[1] 是 actTeacherName.join(',') 表达式而非字符串，从前面 teachers=[{name:"..."}] 数组提取。
// 桥接契约：window.shangkeImportEntry 由注入器自动调用。

var CHZU_BASE = 'https://jwgl.chzu.edu.cn';

function chzuToast(msg) {
    if (window.shangkeBridge && typeof window.shangkeBridge.showToast === 'function') {
        window.shangkeBridge.showToast(msg);
    }
}

function chzuAlert(title, msg, btn) {
    return window.shangkeBridgePromise.showAlert(title, msg, btn);
}

function chzuGetText(path) {
    return fetch(CHZU_BASE + path, { method: 'GET', credentials: 'include' }).then(function (r) {
        if (!r.ok) throw new Error('请求失败（HTTP ' + r.status + '）');
        return r.text();
    });
}

function chzuPostForm(path, params) {
    var body = Object.keys(params).map(function (k) {
        return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]);
    }).join('&');
    return fetch(CHZU_BASE + path, {
        method: 'POST',
        credentials: 'include',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
            'X-Requested-With': 'XMLHttpRequest'
        },
        body: body
    }).then(function (r) {
        if (!r.ok) throw new Error('请求失败（HTTP ' + r.status + '）');
        return r.text();
    });
}

// 从 courseTableForStd 页面提取 ids: bg.form.addInput(form,"ids","123456");
function chzuExtractIds(html) {
    var m = html.match(/bg\.form\.addInput\(form,\s*"ids",\s*"(\d+)"\)/);
    return m ? m[1] : null;
}

// 从 courseTableForStd 页面提取当前学期 ID：semesterCalendar({empty:"false",onChange:"",value:"242"},"searchTable()")
function chzuExtractSemesterId(html) {
    var m = html.match(/semesterCalendar\s*\(\s*\{[^}]*?value\s*:\s*"(\d+)"[^}]*?\}/);
    if (m) return m[1];
    var m2 = html.match(/name=["']semester\.id["'][^>]*?value=["'](\d+)["']/);
    return m2 ? m2[1] : null;
}

// 从 teachers=[{id:3374,name:"薛大维",lab:false}] 数组提取教师名
function chzuExtractTeacherNames(teachersArrText) {
    var names = [];
    var re = /name\s*:\s*"([^"]*)"/g;
    var m;
    while ((m = re.exec(teachersArrText)) !== null) {
        if (m[1] && names.indexOf(m[1]) === -1) names.push(m[1]);
    }
    return names.join('、') || '未安排';
}

// 安全切分 JS 参数（处理嵌套引号与转义）
function chzuSplitJsArgs(argsText) {
    var args = [];
    var curr = "";
    var inQuote = "";
    var escaped = false;
    for (var i = 0; i < argsText.length; i++) {
        var ch = argsText[i];
        if (escaped) { curr += ch; escaped = false; continue; }
        if (ch === "\\") { curr += ch; escaped = true; continue; }
        if (inQuote) { curr += ch; if (ch === inQuote) inQuote = ""; continue; }
        if (ch === '"' || ch === "'") { curr += ch; inQuote = ch; continue; }
        if (ch === ",") { args.push(curr.trim()); curr = ""; continue; }
        curr += ch;
    }
    if (curr.trim() || argsText.endsWith(",")) args.push(curr.trim());
    return args;
}

function chzuUnquote(s) {
    var t = String(s || '').trim();
    var m = t.match(/^(["'])([\s\S]*)\1$/);
    return m ? m[2] : t;
}

// TaskActivity JS 解析（v2：适配滁州学院实际格式）
// 实际格式：
//   var unitCount = 10;
//   var teachers = [{id:3374,name:"薛大维",lab:false}];
//   var actTeachers = [...]; var assistant = ...; var actTeacherId=[]; var actTeacherName=[];
//   activity = new TaskActivity(actTeacherId.join(','), actTeacherName.join(','), "28072(...)", "审计学", subCourseName, "406", "YF4102", "0111...", null, null, ...);
//   index = 2*unitCount +0;  (可能跨行)
function chzuParseCourses(jsCode) {
    var ucMatch = jsCode.match(/var\s+unitCount\s*=\s*(\d+)/);
    var unitCount = ucMatch ? parseInt(ucMatch[1], 10) : 10;

    var courses = [];
    // 匹配完整块：var teachers = [...]; ... activity = new TaskActivity(args); ... index = ...
    var blockRe = /var\s+teachers\s*=\s*(\[[^;]*?\]);\s*[\s\S]*?activity\s*=\s*new\s+TaskActivity\(([^]*?)\)\s*;([\s\S]*?)(?=var\s+teachers\s*=|<\/script>|$)/g;
    var match;
    while ((match = blockRe.exec(jsCode)) !== null) {
        var args = chzuSplitJsArgs(match[2]);
        if (args.length < 8) continue;

        // 教师名从 teachers 数组提取（args[1] 是表达式而非字符串）
        var teacherName = chzuExtractTeacherNames(match[1]);

        // 参数序（滁州学院实测）：
        //   args[0]=教师ID表达式, args[1]=教师名表达式, args[2]=课程编号, args[3]=课程名,
        //   args[4]=子课程名, args[5]=?, args[6]=教室, args[7]=周次bitmap
        var courseName = chzuUnquote(args[3]) || chzuUnquote(args[2]).replace(/\(.*\)/, '');
        var position = chzuUnquote(args[6]) || '待定';
        var weekStr = chzuUnquote(args[7]);

        // index 表达式可能跨行，先压缩空白再匹配
        var compact = match[3].replace(/\s+/g, ' ');
        var indexRe = /index\s*=\s*(\d+)\s*\*\s*unitCount\s*\+\s*(\d+)\s*;/g;
        var days = [];
        var sections = [];
        var im;
        while ((im = indexRe.exec(compact)) !== null) {
            var d = parseInt(im[1], 10) + 1;
            var u = parseInt(im[2], 10) + 1;
            if (days.indexOf(d) === -1) days.push(d);
            if (sections.indexOf(u) === -1) sections.push(u);
        }
        if (!days.length || days.length > 1 || !courseName) continue;
        sections.sort(function (a, b) { return a - b; });

        var weeks = [];
        for (var i = 0; i < weekStr.length; i++) {
            if (weekStr[i] === '1') weeks.push(i + 1);
        }
        if (!weeks.length) continue;

        courses.push({
            name: courseName,
            teacher: teacherName,
            position: position,
            day: days[0],
            startSection: sections[0],
            endSection: sections[sections.length - 1],
            weeks: weeks,
            isLab: /实验/.test(courseName)
        });
    }
    return courses;
}

function chzuMergeCourses(courses) {
    var map = {};
    var order = [];
    for (var i = 0; i < courses.length; i++) {
        var c = courses[i];
        var key = [c.name, c.teacher, c.position, c.day, c.startSection, c.endSection].join('__');
        if (!map[key]) {
            map[key] = { block: c, weeks: c.weeks.slice() };
            order.push(key);
        } else {
            for (var w = 0; w < c.weeks.length; w++) {
                if (map[key].weeks.indexOf(c.weeks[w]) === -1) map[key].weeks.push(c.weeks[w]);
            }
        }
    }
    var out = [];
    for (var k = 0; k < order.length; k++) {
        var e = map[order[k]];
        e.weeks.sort(function (a, c2) { return a - c2; });
        out.push({
            name: e.block.name,
            teacher: e.block.teacher,
            position: e.block.position,
            day: e.block.day,
            startSection: e.block.startSection,
            endSection: e.block.endSection,
            weeks: e.weeks,
            isLab: e.block.isLab
        });
    }
    return out;
}

function chzuRunImport() {
    var bridge = window.shangkeBridgePromise;
    return bridge.showAlert(
        '滁州学院教务导入',
        '请先在统一身份认证（sso.chzu.edu.cn）完成登录，进入教务系统。\n' +
        '登录后直接点「确定」即可，脚本会自动定位当前学期课表数据。',
        '开始导入'
    ).then(function (ok) {
        if (!ok) { chzuToast('导入已取消'); return null; }

        return chzuGetText('/eams/courseTableForStd.action').then(function (html) {
            if (/统一身份认证|login\?service=/.test(html) && html.indexOf('"ids"') === -1) {
                throw new Error('尚未登录：请先完成联创 SSO 登录进入教务系统');
            }
            var ids = chzuExtractIds(html);
            if (!ids) throw new Error('课表入口页解析失败（未找到 ids，请确认已进入教务系统）');
            var semesterId = chzuExtractSemesterId(html);
            if (!semesterId) throw new Error('未获取到当前学期（课表页结构可能变化）');
            return { ids: ids, semesterId: semesterId };
        });
    }).then(function (picked) {
        if (!picked) return null;
        chzuToast('正在获取当前学期课表...');

        return chzuPostForm('/eams/courseTableForStd!courseTable.action?sf_request_type=ajax', {
            'ignoreHead': '1',
            'setting.kind': 'std',
            'startWeek': '',
            'project.id': '1',
            'semester.id': picked.semesterId,
            'ids': picked.ids
        }).then(function (html) {
            var courses = chzuParseCourses(html);
            if (!courses.length) throw new Error('当前学期未解析到课程（请确认该学期有课）');
            var blocks = chzuMergeCourses(courses);

            return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(blocks))
                .then(function () {
                    return chzuAlert(
                        '导入完成',
                        '成功导入 ' + blocks.length + ' 个课程项（当前学期）。\n' +
                        '课程按节次导入；如上下课时间与学校作息不符，请到「设置 → 自定义时间段」调整。',
                        '完成'
                    );
                })
                .then(function () {
                    window.shangkeBridge.notifyTaskCompletion();
                });
        });
    }).catch(function (e) {
        chzuToast('导入失败：' + (e && e.message ? e.message : e));
    });
}

window.shangkeImportEntry = chzuRunImport;
window.chzuImport = chzuRunImport;
