// 交叉校验：Compose 主题实现（ClaudeStyle.kt）与设计包 token 是否一致。
//   - 设计系统关键色值必须逐字出现在 ClaudeStyle.kt 中
//   - CLAUDE 主题的实底+文字组合必须满足 WCAG 2.2 AA
// 用法：node tools/cross-check-compose.mjs
import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const ROOT = process.cwd()
const KOTLIN = join(
  ROOT,
  '..',
  'shared',
  'src',
  'commonMain',
  'kotlin',
  'com',
  'shangkeschedule',
  'ui',
  'theme',
  'ClaudeStyle.kt'
)
const PRESET = join(
  ROOT,
  '..',
  'shared',
  'src',
  'commonMain',
  'kotlin',
  'com',
  'shangkeschedule',
  'data',
  'model',
  'AppThemePreset.kt'
)

for (const f of [KOTLIN, PRESET]) {
  if (!existsSync(f)) {
    console.error(`missing: ${f}`)
    process.exit(1)
  }
}

const src = readFileSync(KOTLIN, 'utf8')
const presetSrc = readFileSync(PRESET, 'utf8')

// 设计系统必须逐字落到实现里的色值
const REQUIRED = [
  ['品牌主色 brand-500 浅', '0xFFC96442'],
  ['品牌主色 brand-500 深', '0xFFD97757'],
  ['页面底 bg-100 浅', '0xFFFAF9F5'],
  ['页面底 bg-100 深', '0xFF262624'],
  ['卡片 bg-200 浅', '0xFFF5F4EF'],
  ['卡片 bg-200 深', '0xFF2C2C2B'],
  ['正文 text-800 浅', '0xFF3D3929'],
  ['正文 text-800 深', '0xFFF1F1EF'],
  ['边框 border-300 浅', '0xFFDAD9D4'],
  ['边框 border-300 深', '0xFF3E3E38'],
  ['成功 success-500 浅', '0xFF788C5D'],
  ['成功 success-500 深', '0xFF8CA06F'],
  ['错误 error-500 浅', '0xFFD64545'],
  ['错误 error-500 深', '0xFFEF4444'],
  ['图表 chart-2', '0xFF9C87F5'],
  ['图表 chart-4', '0xFFDBD3F0'],
  ['侧栏 sidebar 浅', '0xFFF5F4EE'],
  ['侧栏 sidebar-accent 浅', '0xFFE9E6DC'],
]

let missing = 0
console.log('===== ClaudeStyle.kt 色值覆盖 =====')
for (const [label, hex] of REQUIRED) {
  const ok = src.includes(hex)
  if (!ok) missing++
  console.log(`${ok ? 'OK  ' : 'MISS'} ${hex}  ${label}`)
}

console.log('\n===== 枚举与字符串 =====')
const enumOk = /CLAUDE\s*\(/.test(presetSrc) && /value\s*=\s*"CLAUDE"/.test(presetSrc)
console.log(`${enumOk ? 'OK  ' : 'MISS'} AppThemePreset.CLAUDE 枚举项`)
const gridOk = /ClaudeGridStyle\s*=\s*ScheduleGridStyle\(/.test(presetSrc)
console.log(`${gridOk ? 'OK  ' : 'MISS'} ClaudeGridStyle 课表样式`)
const seedOk = /seedColor\s*=\s*Color\(0xFFC96442\)/.test(presetSrc)
console.log(`${seedOk ? 'OK  ' : 'MISS'} seedColor = 0xFFC96442`)

// 设计包字体是否已落到 Compose resources
const fontDir = join(ROOT, '..', 'shared', 'src', 'commonMain', 'composeResources', 'font')
const fonts = ['Poppins-Regular.ttf', 'Poppins-Medium.ttf', 'Poppins-SemiBold.ttf', 'Poppins-Bold.ttf', 'Lora-Variable.ttf', 'Newsreader-Variable.ttf', 'GeistMono-Variable.ttf']
console.log('\n===== 字体资源 =====')
for (const f of fonts) {
  const ok = existsSync(join(fontDir, f))
  if (!ok) missing++
  console.log(`${ok ? 'OK  ' : 'MISS'} ${f}`)
}

console.log(`\n${missing === 0 && enumOk && gridOk && seedOk ? 'ALL PASS' : `${missing} MISSING`}`)
process.exit(missing === 0 && enumOk && gridOk && seedOk ? 0 : 1)
