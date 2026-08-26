import { parseWeeks } from "../parser.js";

import { DOM_MATRIX_HELPER } from "./dom_matrix_helper.js";

// 金智教务系统 (Wisedu) 解析器
// 常用于许多南京高校及其他重点高校（界面较新，地址多带有 wisedu）
export default {
    id: "wisedu",
    name: "金智教务系统",
    provider: `
        (function() {
            ${DOM_MATRIX_HELPER}
            try {
                if (document.body.innerText.indexOf('kcbxx') !== -1 || document.body.innerText.indexOf('kbxx') !== -1) {
                    document.title = 'KEBIAO_OK:JSON|' + btoa(encodeURIComponent(document.body.innerText));
                    return;
                }

                var table = document.getElementById('kbTable') || document.querySelector('.kb_tb, .scheduleTable');
                if (!table) return void (document.title = 'KEBIAO_ERR:未查找到金智教务课表表格');

                var grid = parseTableToGrid(table);
                var courses = [];
                
                for (var r = 1; r < grid.length; r++) {
                    if (!grid[r]) continue;
                    for (var c = 1; c <= 7; c++) {
                        var cell = grid[r][c];
                        if (!cell || !cell.isOrigin || cell.td.className.indexOf('kb-title') !== -1) continue;

                        var sections = cell.html.split(/<hr\\s*\\/?>|<br\\s*\\/?>=============|<div class="kbcontent">/i);
                        for (var k = 0; k < sections.length; k++) {
                            var textChunks = sections[k].split(/<br\\s*\\/?>|<p[^>]*>/i)
                                .map(v => v.replace(/<[^>]+>/g, '').trim())
                                .filter(v => v);

                            if (textChunks.length >= 3) {
                                var cName = textChunks[0];
                                var cTeacher = '', cRoom = '', cWeeks = '';
                                
                                for(var p = 1; p < textChunks.length; p++){
                                    if(textChunks[p].indexOf('周') !== -1) cWeeks = textChunks[p];
                                    else if(textChunks[p].indexOf('老师') !== -1 || textChunks[p].length <= 3) cTeacher = textChunks[p];
                                    else cRoom = textChunks[p];
                                }
                                
                                var start = (r * 2 - 1);
                                courses.push([
                                    cName, c, start + '-' + (start + cell.rowspan * 2 - 1), cRoom, cWeeks, cTeacher
                                ].join('~'));
                            }
                        }
                    }
                }
                
                document.title = courses.length ? 'KEBIAO_OK:HTML|' + courses.join('|') : 'KEBIAO_ERR:未提取到金智教务课程数据';
            } catch(e) { document.title = 'KEBIAO_ERR:' + e.message; }
        })();
    `,
    parser: function(compactStr) {
        if (compactStr.startsWith('JSON|')) return [];
        
        compactStr = compactStr.replace('HTML|', '');
        const items = compactStr.split("|");
        const courses = [];
        items.forEach((item, index) => {
            const parts = item.split("~");
            if (parts.length < 3) return;
            const [name, dayStr, jcsStr, room, weeksStr, teacher] = parts;
            const jcsMatch = jcsStr.match(/(\d+)-(\d+)/);
            let startNode = 1, endNode = 2;
            if (jcsMatch) {
                startNode = parseInt(jcsMatch[1]);
                endNode = parseInt(jcsMatch[2]);
            }
            courses.push({
                id: index + 1, name: name || "未知课程", day: parseInt(dayStr) || 1, startNode, endNode,
                room: room || "", teacher: teacher || "",
                weeks: parseWeeks(weeksStr || ""), weeksText: weeksStr || ""
            });
        });
        return courses;
    }
}
