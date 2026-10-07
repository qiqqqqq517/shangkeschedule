// 生成 assets/claude-icons.js：把 assets/icons/*.svg 打包成一份 <symbol> sprite，
// 页面用 <svg class="icon"><use href="#i-clock"/></svg> 引用。
// 好处：离线可用、可随主题变色（stroke="currentColor"）、无需 CDN。
// 用法：node tools/build-icon-sprite.mjs
import { readFileSync, writeFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'

const ROOT = process.cwd()
const ICON_DIR = join(ROOT, 'assets', 'icons')
const OUT = join(ROOT, 'assets', 'claude-icons.js')

const files = readdirSync(ICON_DIR).filter((f) => f.endsWith('.svg')).sort()

const symbols = files.map((file) => {
  const name = file.replace(/\.svg$/, '')
  let svg = readFileSync(join(ICON_DIR, file), 'utf8')
  const inner = svg
    .replace(/^[\s\S]*?<svg[^>]*>/, '')
    .replace(/<\/svg>\s*$/, '')
    .trim()
  return `  <symbol id="i-${name}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</symbol>`
})

const js = `/* 自动生成，请勿手改；重新生成：node tools/build-icon-sprite.mjs
 * 图标来源：lucide-static v1.43.0（ISC License）
 * 用法：<svg class="icon" aria-hidden="true"><use href="#i-clock"></use></svg>
 */
(function () {
  if (document.getElementById('claude-icon-sprite')) return;
  var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('id', 'claude-icon-sprite');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('style', 'position:absolute;width:0;height:0;overflow:hidden');
  svg.innerHTML = ${JSON.stringify(symbols.join('\n'))};
  document.body.insertBefore(svg, document.body.firstChild);
})();
`

writeFileSync(OUT, js, 'utf8')
console.log(`wrote ${OUT} (${files.length} icons, ${js.length} bytes)`)
