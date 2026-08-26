// 青果教务系统通用适配器
(function() {
    'use strict';
    function extractText(h) { return h ? h.replace(/<[^>]+>/g, '').trim() : ''; }
    function cleanTeacher(n) { return n ? n.replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim() : ''; }
    function parseWeeks(s) {
        if (!s) return [];
        const w = new Set();
        s.split(/[,，、]/).forEach(seg => {
            const r = seg.match(/(\d+)\s*[-~]\s*(\d+)/);
            if (r) { for (let i = +r[1]; i <= +r[2]; i++) w.add(i); }
            else { const n = seg.match(/(\d+)/); if (n) w.add(+n[1]); }
        });
        return Array.from(w).sort((a,b) => a-b);
    }
    function isKingosoftPage() {
        return window.location.href.indexOf('jwxt') !== -1 || document.body.innerHTML.indexOf('青果') !== -1;
    }
    function fetchCourses() {
        if (!isKingosoftPage()) { Bridge.showToast('请先进入青果教务系统课表页面'); return; }
        const table = document.querySelector('.courseTable') || document.getElementById('kbtable') || document.querySelector('table[class*=course]');
        if (!table) { Bridge.showToast('未找到课表表格'); return; }
        const courses = [];
        const rows = table.querySelectorAll('tr');
        for (let r = 1; r < rows.length; r++) {
            const cells = rows[r].querySelectorAll('td');
            for (let c = 1; c < cells.length && c <= 7; c++) {
                const cell = cells[c];
                if (!cell || !cell.innerText.trim()) continue;
                const lines = cell.innerText.split('\n').map(l => l.trim()).filter(l => l);
                if (lines.length < 1) continue;
                const name = lines[0];
                const teacher = lines[1] ? cleanTeacher(lines[1]) : '';
                const position = lines[2] || '';
                const weekStr = lines.find(l => l.indexOf('周') !== -1) || '';
                const weeks = parseWeeks(weekStr);
                const start = (r - 1) * 2 + 1;
                const rs = parseInt(cell.getAttribute('rowspan')) || 1;
                if (name && weeks.length > 0) {
                    courses.push({ name, teacher, position: position || '待定', day: c, startSection: start, endSection: start + rs - 1, weeks, remark: cell.innerText });
                }
            }
        }
        if (courses.length === 0) { Bridge.showToast('未提取到有效课程'); return; }
        Bridge.saveImportedCourses(JSON.stringify(courses));
        Bridge.showToast('成功解析 ' + courses.length + ' 门课程');
    }
    window.kingosoftImport = fetchCourses;
    if (isKingosoftPage()) Bridge.showToast('检测到青果教务系统');
})();
