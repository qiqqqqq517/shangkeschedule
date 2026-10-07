// 对比度审计：按 WCAG 2.2 AA 检查设计系统关键前景/背景组合。
// 用法：node tools/check-contrast.mjs
// 阈值：正文 4.5:1，大字号（≥18.66px bold 或 ≥24px）与图形/边框 3:1。

const THEMES = {
  light: {
    '--background': '#faf9f5',
    '--card': '#f5f4ef',
    '--popover': '#ffffff',
    '--muted': '#ede9de',
    '--foreground': '#3d3929',
    '--card-foreground': '#141413',
    '--muted-foreground': '#6e6d68',
    '--secondary-foreground': '#535146',
    '--primary': '#c96442',
    '--primary-solid': '#b0562f',
    '--primary-foreground': '#ffffff',
    '--border': '#dad9d4',
    '--destructive': '#d64545',
    '--success': '#788c5d',
    '--success-solid': '#4f5d3a',
    '--success-foreground': '#ffffff',
    '--chart-1': '#b05730',
    '--chart-2': '#9c87f5',
    '--chart-3': '#ded8c4',
    '--chart-4': '#dbd3f0',
    '--chart-5': '#b4552d',
  },
  dark: {
    '--background': '#262624',
    '--card': '#2c2c2b',
    '--popover': '#30302e',
    '--muted': '#1b1b19',
    '--foreground': '#f1f1ef',
    '--card-foreground': '#faf9f5',
    '--muted-foreground': '#b7b5a9',
    '--secondary-foreground': '#30302e',
    '--primary': '#d97757',
    '--primary-solid': '#d97757',
    '--primary-foreground': '#141413',
    '--border': '#3e3e38',
    '--destructive': '#ef4444',
    '--success': '#8ca06f',
    '--success-solid': '#8ca06f',
    '--success-foreground': '#141413',
    '--chart-1': '#b05730',
    '--chart-2': '#9c87f5',
    '--chart-3': '#1a1915',
    '--chart-4': '#2f2b48',
    '--chart-5': '#b4552d',
  },
}

// [前景, 背景, 最小比值, 说明]
const PAIRS = [
  ['--foreground', '--background', 4.5, '正文 / 页面底'],
  ['--foreground', '--card', 4.5, '正文 / 卡片'],
  ['--card-foreground', '--popover', 4.5, '卡片标题 / 浮层'],
  ['--muted-foreground', '--background', 4.5, '辅助文字 / 页面底'],
  ['--muted-foreground', '--popover', 4.5, '辅助文字 / 浮层'],
  ['--primary', '--background', 3.0, '主色文字（大字号/图形）'],
  ['--primary-foreground', '--primary-solid', 4.5, '主色实底按钮文字'],
  ['--success-foreground', '--success-solid', 4.5, '成功徽章实底文字'],
  ['--destructive', '--background', 3.0, '危险色文字（图形/大字号）'],
  ['--border', '--background', 1.0, '边框（仅记录，无 AA 要求）'],
]

function hexToRgb(hex) {
  const h = hex.replace('#', '')
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h
  return [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16) / 255)
}

function relLuminance([r, g, b]) {
  const f = (c) => (c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4))
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
}

function contrast(fg, bg) {
  const L1 = relLuminance(hexToRgb(fg))
  const L2 = relLuminance(hexToRgb(bg))
  const [hi, lo] = L1 > L2 ? [L1, L2] : [L2, L1]
  return (hi + 0.05) / (lo + 0.05)
}

let failures = 0
for (const [themeName, tokens] of Object.entries(THEMES)) {
  console.log(`\n===== ${themeName.toUpperCase()} =====`)
  for (const [fgKey, bgKey, min, label] of PAIRS) {
    const fg = tokens[fgKey]
    const bg = tokens[bgKey]
    const ratio = contrast(fg, bg)
    const pass = ratio >= min
    if (!pass) failures++
    console.log(
      `${pass ? 'PASS' : 'FAIL'}  ${ratio.toFixed(2).padStart(6)}:1  (min ${min})  ${label.padEnd(22)} ${fgKey} ${fg} on ${bgKey} ${bg}`
    )
  }
}
console.log(`\n${failures === 0 ? 'ALL PASS' : failures + ' FAILURE(S)'}`)
process.exit(failures === 0 ? 0 : 1)
