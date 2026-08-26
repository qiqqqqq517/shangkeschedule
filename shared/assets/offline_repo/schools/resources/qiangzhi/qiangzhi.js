// 强智教务系统通用适配器
(function() {
    'use strict';
    function extractText(h) { return h ? h.replace(/<[^>]+>/g, '').trim() : ''; }
    function cleanTeacher(n) { return n ? n.replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim() : ''; }
    function parseWeeks(s) {
        if (!s) return [];
        const w = new Set();
        const isOdd = /单/.test(s) && !/双/.test(s);
        const isEven = /双/.test(s) && !/单/.test(s);
        s.split(/[,，、]/).forEach(seg => {
            const r = seg.match(/(\d+)\s*[-~]\s*(\d+)/);
            if (r) { for (let i = +r[1]; i <= +r[2]; i++) { if (isOdd && i%2===0) continue; if (isEven && i%2===1) continue; w.add(i); } }
            else { const n = seg.match(/(\d+)/); if (n) { const i=+n[1]; if (isOdd && i%2===0) return; if (isEven && i%2===1) return; w.add(i); } }
        });
        return Array.from(w).sort((a,b) => a-b);
    }
    function isQiangzhiPage() {
        return window.location.href.indexOf('jwgl') !== -1 || document.body.innerHTML.indexOf('强智') !== -1;
    }
    function fetchCourses() {
        if (!isQiangzhiPage()) { Bridge.showToast('请先进入强智教务系统课表页面'); return; }
        // 强智通常通过 API 获取 JSON 数据
        const table = document.getElementById('kbgrid') || document.querySelector('.schedule-table') || document.querySelector('table[id*=kb]');
        if (!table) { Bridge.showToast('未找到课表表格，请确保在个人课表页面'); return; }
        const courses = [];
        const cells = table.querySelectorAll('td[class*=course], td[rowspan]');
        cells.forEach(cell => {
            const text = cell.innerText || cell.textContent;
            if (!text || !text.trim()) return;
            const lines = text.split('\n').map(l => l.trim()).filter(l => l);
            if (lines.length < 1) return;
            const name = lines[0];
            const teacher = lines.find(l => /老师|教师/.test(l)) ? lines.find(l => /老师|教师/.test(l)).replace(/老师|教师/g, '') : (lines[1] ? cleanTeacher(lines[1]) : '');
            const position = lines.find(l => /教室|楼|室/.test(l)) || '';
            const weekStr = lines.find(l => /周/.test(l)) || '';
            const weeks = parseWeeks(weekStr);
            if (name && weeks.length > 0) {
                courses.push({ name, teacher, position: position || '待定', day: 0, startSection: 0, endSection: 0, weeks, remark: text });
            }
        });
        if (courses.length === 0) { Bridge.showToast('未提取到有效课程'); return; }
        Bridge.saveImportedCourses(JSON.stringify(courses));
        Bridge.showToast('成功解析 ' + courses.length + ' 门课程（需手动核对节次）');
    }
    window.qiangzhiImport = fetchCourses;
    if (isQiangzhiPage()) Bridge.showToast('检测到强智教务系统');
})();
