import { parseWeeks } from "../parser.js";

import { DOM_MATRIX_HELPER } from "./dom_matrix_helper.js";

export default {
    id: "qiangzhi",
    name: "强智教务系统",
    provider: `
        (function() {
            ${DOM_MATRIX_HELPER}
            try {
                var table = document.getElementById('kbtable') || document.querySelector('.timetable');
                if (!table) {
                    var tables = document.getElementsByTagName('table');
                    for (var i = 0; i < tables.length; i++) {
                        if (tables[i].innerHTML.indexOf('星期') !== -1) {
                            table = tables[i];
                            break;
                        }
                    }
                }
                
                if (!table) {
                    document.title = 'KEBIAO_ERR:未查找到强智课表表格';
                    return;
                }

                var grid = parseTableToGrid(table);
                var courses = [];
                for (var r = 1; r < grid.length; r++) {
                    if (!grid[r]) continue;
                    for (var c = 1; c <= 7; c++) {
                        var cell = grid[r][c];
                        if (!cell || !cell.isOrigin) continue;
                        var td = cell.td;

                        if (td.querySelector('.kbcontent') || td.innerHTML.indexOf('编排') !== -1 || td.querySelectorAll('a, p').length > 0) {
                            var texts = Array.from(td.childNodes)
                                .map(n => n.textContent ? n.textContent.trim() : '')
                                .filter(t => t.length > 0);
                            
                            if (texts.length >= 3) {
                                var dayMatch = td.id ? td.id.match(/-(\\d)-/) : null;
                                var nodeMatch = td.id ? td.id.match(/-(\\d+)-/) : null;
                                
                                var day = dayMatch ? parseInt(dayMatch[1]) : c;
                                var node = nodeMatch ? parseInt(nodeMatch[1]) : r;
                                
                                courses.push([
                                    texts[0].replace(/[~|]/g, ' '),
                                    day,
                                    node + '-' + (node + cell.rowspan - 1),
                                    (texts[2] || '').replace(/[~|]/g, ' '),
                                    (texts[1] || '').replace(/[~|]/g, ' '),
                                    (texts[3] || '').replace(/[~|]/g, ' ')
                                ].join('~'));
                            }
                        }
                    }
                }
                
                if (courses.length === 0) {
                    document.title = 'KEBIAO_ERR:未能提取到课程数据';
                } else {
                    document.title = 'KEBIAO_OK:' + courses.join('|');
                }
            } catch(e) {
                document.title = 'KEBIAO_ERR:' + e.message;
            }
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
