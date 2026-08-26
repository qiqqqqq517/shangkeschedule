export const DOM_MATRIX_HELPER = `
    // 核心提取框架：DOM Matrix 网格提取算法
    // 能将包含各种恶心 rowspan/colspan 跨行跨列的 HTML 课表
    // 完美拉平成一个精准的 grid[行][列] 的二维数组，列号严格对齐星期，行号严格对齐节次
    function parseTableToGrid(tableNode) {
        if (!tableNode) return [];
        var trs = Array.from(tableNode.querySelectorAll('tr'));
        var grid = [];
        for (var r = 0; r < trs.length; r++) {
            var tds = Array.from(trs[r].querySelectorAll('td, th'));
            var c = 0;
            grid[r] = grid[r] || [];
            for (var i = 0; i < tds.length; i++) {
                var td = tds[i];
                while (grid[r][c] !== undefined) c++;
                var rowspan = parseInt(td.getAttribute('rowspan')) || 1;
                var colspan = parseInt(td.getAttribute('colspan')) || 1;
                for (var rr = 0; rr < rowspan; rr++) {
                    for (var cc = 0; cc < colspan; cc++) {
                        grid[r + rr] = grid[r + rr] || [];
                        grid[r + rr][c + cc] = {
                            html: td.innerHTML,
                            text: td.innerText.trim(),
                            td: td,
                            isOrigin: rr === 0 && cc === 0,
                            rowspan: rowspan,
                            colspan: colspan
                        };
                    }
                }
                c += colspan;
            }
        }
        return grid;
    }
`;
