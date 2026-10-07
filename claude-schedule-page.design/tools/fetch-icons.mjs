// 一次性工具：从 lucide-static 拉取设计稿用到的图标，落盘为本地 SVG（离线可用）。
// 用法：node tools/fetch-icons.mjs
import { writeFileSync, mkdirSync } from 'node:fs'
import { join } from 'node:path'

const OUT = join(process.cwd(), 'assets', 'icons')
mkdirSync(OUT, { recursive: true })

const ICONS = [
  'clock', 'settings-2', 'settings', 'signal', 'wifi', 'battery-full',
  'calendar-check', 'calendar', 'layout-grid', 'upload-cloud', 'user-circle',
  'book-open', 'copy', 'eye', 'plus', 'circle-play', 'circle-check',
  'trash-2', 'chevron-right', 'chevron-down', 'chevron-left', 'map-pin',
  'user', 'folder-open', 'pen-line', 'check', 'circle-alert', 'x',
]

const BASE = 'https://cdn.jsdelivr.net/npm/lucide-static@1.43.0/icons'

for (const name of ICONS) {
  const res = await fetch(`${BASE}/${name}.svg`)
  if (!res.ok) {
    console.error(`FAIL ${name} ${res.status}`)
    continue
  }
  const raw = await res.text()
  const svg = raw
    .replace(/<!--[\s\S]*?-->\s*/g, '')
    .replace(/\s+class="[^"]*"/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  writeFileSync(join(OUT, `${name}.svg`), svg + '\n', 'utf8')
  console.log(`OK   ${name}.svg`)
}
