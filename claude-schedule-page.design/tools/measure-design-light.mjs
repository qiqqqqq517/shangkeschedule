// 量测设计包页面在强制浅色下的真实几何尺寸（经 iframe 包裹，避免系统深色偏好干扰）。
// 用法：node tools/measure-design-light.mjs
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { setTimeout as sleep } from 'node:timers/promises'

const ROOT = process.cwd()
const CHROME = [
  'C:/Users/30458/AppData/Local/ms-playwright/chromium-1228/chrome-win64/chrome.exe',
  'C:/Users/30458/AppData/Local/ms-playwright/chromium-1223/chrome-win64/chrome.exe',
].find((p) => existsSync(p))
const PORT = 9335
const child = spawn(
  CHROME,
  [
    '--headless=new',
    '--disable-gpu',
    '--no-sandbox',
    '--hide-scrollbars',
    `--remote-debugging-port=${PORT}`,
    '--user-data-dir=' + join(ROOT, '..', 'build_qa', 'claude-design-cdp-profile'),
    'about:blank',
  ],
  { stdio: 'ignore' }
)

async function wsUrl() {
  for (let i = 0; i < 60; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      const p = list.find((t) => t.type === 'page' && t.webSocketDebuggerUrl)
      if (p) return p.webSocketDebuggerUrl
    } catch {}
    await sleep(250)
  }
  throw new Error('CDP not reachable')
}

const ws = new WebSocket(await wsUrl())
await new Promise((r) => ws.addEventListener('open', r, { once: true }))
let id = 0
const pending = new Map()
ws.addEventListener('message', (ev) => {
  const m = JSON.parse(ev.data)
  if (m.id && pending.has(m.id)) {
    const { resolve, reject } = pending.get(m.id)
    pending.delete(m.id)
    m.error ? reject(new Error(JSON.stringify(m.error))) : resolve(m.result)
  }
})
const send = (method, params = {}) =>
  new Promise((resolve, reject) => {
    const i = ++id
    pending.set(i, { resolve, reject })
    ws.send(JSON.stringify({ id: i, method, params }))
  })
const evalIn = async (expr) => {
  const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text)
  return r.result.value
}

await send('Runtime.enable')
await send('Page.enable')
await send('Emulation.setDeviceMetricsOverride', { width: 430, height: 932, deviceScaleFactor: 1, mobile: true })
// 先在同源下写入主题偏好，再导航到设计页；页面脚本启动时会读取并应用，避免系统深色偏好干扰量测
const pageUrl = 'file:///' + encodeURI(join(ROOT, 'pages', '今日日程.html').replace(/\\/g, '/'))
await send('Page.navigate', { url: pageUrl })
await sleep(400)
await evalIn(`try { localStorage.setItem('shangke-claude-theme','light') } catch(e) {}`)
await send('Page.reload', {})
await sleep(1500)

const out = await evalIn(`(() => {
  const win = window;
  const doc = document;
  const box = (sel) => {
    const el = doc.querySelector(sel);
    if (!el) return null;
    const r = el.getBoundingClientRect();
    const cs = win.getComputedStyle(el);
    return {
      top: Math.round(r.top), bottom: Math.round(r.bottom), h: Math.round(r.height), w: Math.round(r.width),
      bg: cs.backgroundColor, color: cs.color, pad: cs.padding, gap: cs.gap,
      radius: cs.borderRadius, border: cs.borderWidth + ' ' + cs.borderColor,
      font: cs.fontSize + '/' + cs.lineHeight + ' ' + cs.fontWeight + ' ' + cs.fontFamily.split(',')[0],
      shadow: cs.boxShadow === 'none' ? 'none' : 'has-shadow',
    };
  };
  const o = {};
  for (const [k, sel] of Object.entries({
    page: '.page-today', 'top-bar': '.top-bar', 'date-eyebrow': '.date-eyebrow',
    'page-title': '.page-title', 'count-badge': '.course-count-badge',
    'week-switcher': '.week-switcher', 'section-label': '.section-label',
    'course-stack': '.course-stack', card1: '.course-card',
    'card1-name': '.course-card .course-name', 'card1-type': '.course-card .course-type',
    'card1-meta': '.course-card .course-meta', 'card1-accent': '.course-accent',
    'meta-item': '.course-card .meta-item', 'meta-text': '.course-card .meta-text',
    'tomorrow-section': '.tomorrow-section', 'tomorrow-card': '.tomorrow-card',
    'bottom-nav': '#bottom-nav',
  })) o[k] = box(sel);
  o.cards = Array.from(doc.querySelectorAll('.course-card')).map((el) => {
    const r = el.getBoundingClientRect();
    return { top: Math.round(r.top), bottom: Math.round(r.bottom), h: Math.round(r.height) };
  });
  o.theme = doc.documentElement.getAttribute('data-theme');
  return o;
})()`)

for (const [k, v] of Object.entries(out)) {
  if (k === 'cards' || k === 'theme') continue
  if (!v) { console.log(k.padEnd(16), '(null)'); continue }
  console.log(
    k.padEnd(16),
    'top=' + String(v.top).padStart(4),
    'bottom=' + String(v.bottom).padStart(4),
    'h=' + String(v.h).padStart(4),
    'w=' + String(v.w).padStart(4),
    'bg=' + v.bg.padEnd(20),
    'radius=' + v.radius.padEnd(18),
    'font=' + v.font
  )
}
console.log('theme:', out.theme)
console.log('cards:', JSON.stringify(out.cards))

ws.close()
child.kill()
process.exit(0)
