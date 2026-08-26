import { parseWeeks } from "../parser.js";

import { DOM_MATRIX_HELPER } from "./dom_matrix_helper.js";

export default {
    id: "urp",
    name: "URP 综合教务系统",
    provider: `
        (function() {
            ${DOM_MATRIX_HELPER}
            try {
                var table = document.getElementById('cwTable') || document.querySelector('.displayTag');
                if (!table) {
                    var tables = document.getElementsByTagName('table');
                    for (var i = 0; i < tables.length; i++) {
                        if (tables[i].innerHTML.indexOf('星期') !== -1) {
                            table = tables[i];
                            break;
                        }
                    }
                }
                if (!table) return void (document.title = 'KEBIAO_ERR:未查找到URP课表表格');

                var grid = parseTableToGrid(table);
                var courses = [];
                for (var r = 1; r < grid.length; r++) {
                    if (!grid[r]) continue;
                    for (var c = 1; c <= 7; c++) {
                        var cell = grid[r][c];
                        if (!cell || !cell.isOrigin || cell.td.className === 'infoTitle') continue;
                        
                        var html = cell.html;
                        var courseTexts = html.split(/<br\\s*\\/?>|<hr\\s*\\/?>/gi);
                        var validTexts = courseTexts.map(t => t.replace(/<[^>]+>/g, '').trim()).filter(t => t);
                        
                        if (validTexts.length >= 3) {
                            courses.push([
                                validTexts[0].replace(/[~|]/g, ' '),
                                c, // 列即精确星期
                                (r * 2 - 1) + '-' + ((r + cell.rowspan - 1) * 2), // 根据原版 urp.js，单行通常为两节
                                (validTexts[2] || '').replace(/[~|]/g, ' '),
                                (validTexts[1] || '').replace(/[~|]/g, ' '),
                                (validTexts[3] || '').replace(/[~|]/g, ' ')
                            ].join('~'));
                        }
                    }
                }
                
                document.title = courses.length ? 'KEBIAO_OK:' + courses.join('|') : 'KEBIAO_ERR:未能提取到课程数据';
            } catch(e) { document.title = 'KEBIAO_ERR:' + e.message; }
        })();
    `,
    parser: function(compactStr) {
        const items = compactStr.split("|");
        const courses = [];
        let id = 0;
        for (const item of items) {
            const parts = item.split("~");
            if (parts.length < 3) continue;
            const [name, dayStr, jcsStr, room, weeksStr, teacher] = parts;
            const day = parseInt(dayStr);
            if (!day || day < 1 || day > 7) continue;

            const jcsMatch = jcsStr.match(/(\d+)-(\d+)/);
            let startNode = 1, endNode = 2;
            if (jcsMatch) {
                startNode = parseInt(jcsMatch[1]);
                endNode = parseInt(jcsMatch[2]);
            }

            const weeks = parseWeeks(weeksStr || "");
            
            courses.push({
                id: ++id,
                name: name || "未知课程",
                day,
                startNode,
                endNode,
                room: room || "",
                teacher: teacher || "",
                weeks,
                weeksText: weeksStr || ""
            });
        }
        return courses;
    }
}
