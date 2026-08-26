import { parseWeeks } from "../parser.js";

export default {
    id: "urp_new",
    name: "URP 综合教务 (新版)",
    // 新版 URP 一般是一个大 JSON 或者 div 包裹，为了兼顾健壮性，同时监听页面直传 JSON和表格
    provider: `
        (function() {
            try {
                // 如果当前页面直接是一个 JSON 对象（比如新版某些只返回数据的 API 拦截）
                if (document.body.innerText.indexOf('"xkxx"') !== -1 || document.body.innerText.indexOf('"kcxx"') !== -1) {
                    var data = JSON.parse(document.body.innerText);
                    // 假设 data 里包含具体字段，此处可以直接处理
                    document.title = 'KEBIAO_OK:JSON|' + btoa(encodeURIComponent(document.body.innerText));
                    return;
                }

                // 退回到前端渲染出的 .course-cell 跨行 div 或者大表格
                var courses = [];
                var cells = document.querySelectorAll('.course-cell, .timetable-item, .kbcontent');
                if (cells.length > 0) {
                    for(var i=0; i<cells.length; i++) {
                        var text = cells[i].innerText.replace(/\\s+/g, ' ').trim();
                        if (text.length > 5) {
                            var parts = cells[i].innerHTML.split(/<br\\s*\\/?>/).map(v => v.replace(/<[^>]+>/g, '').trim()).filter(v=>v);
                            // 这里需要知道横纵坐标，如果 div 实现了 grid-column / grid-row：
                            var style = window.getComputedStyle(cells[i]);
                            var rowMatch = style.gridRowStart || cells[i].getAttribute('data-row');
                            var colMatch = style.gridColumnStart || cells[i].getAttribute('data-col');
                            
                            var day = parseInt(colMatch) || 1; // 假定如果拿不到就是默认
                            var startNode = parseInt(rowMatch) || 1;
                            
                            courses.push([
                                parts[0] || '末知课程', day, startNode + '-' + (startNode+1), 
                                parts[2] || '', parts[1] || '', parts[3] || ''
                            ].join('~'));
                        }
                    }
                }
                
                // 更常见的：新版依旧有 table 但类名不同
                if (courses.length === 0) {
                    var trs = document.querySelectorAll('tbody tr');
                    // ... 通用解析预留 ...
                    document.title = 'KEBIAO_ERR:新版 URP DOM 解析失败，需适配';
                    return;
                }

                document.title = 'KEBIAO_OK:HTML|' + courses.join('|');
            } catch(e) { document.title = 'KEBIAO_ERR:' + e.message; }
        })();
    `,
    parser: function(compactStr) {
        if (compactStr.startsWith('JSON|')) {
            // 解析 JSON
            const raw = JSON.parse(decodeURIComponent(atob(compactStr.substring(5))));
            // 简单处理，需要根据实际数据格式修改
            return []; 
        }

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
                id: index + 1, name, day: parseInt(dayStr) || 1, startNode, endNode,
                room: room || "", teacher: teacher || "",
                weeks: parseWeeks(weeksStr || ""), weeksText: weeksStr || ""
            });
        });
        return courses;
    }
}
