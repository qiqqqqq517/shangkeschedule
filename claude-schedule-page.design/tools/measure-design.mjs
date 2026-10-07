// 用 CDP 读取设计包页面的真实几何尺寸，作为 Compose 对齐的基准。
// 用法：node tools/measure-design.mjs [页面文件名]
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { setTimeout as sleep } from 'node:timers/promises'

const ROOT = process.cwd()
const PAGE = process.argv[2] || '今日日程.html'
const CHROME_CANDIDATES = [
  'C:/Users/30458/AppData/Local/ms-playwright/chromium-1228/chrome-win64/chrome.exe',
  'C:/Users/30458/AppData/Local/ms-playwright/chromium-1223/chrome-win64/chrome.exe',
]
const CHROME = CHROME_CANDIDATES.find((p) => existsSync(p))
const PORT = 9334
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

async function getPageWsUrl() {
  for (let i = 0; i < 60; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${PORT}/json/list`)
      const list = await res.json()
      const page = list.find((t) => t.type === 'page' && t.webSocketDebuggerUrl)
      if (page) return page.webSocketDebuggerUrl
    } catch {}
    await sleep(250)
  }
  throw new Error('CDP not reachable')
}

class CDP {
  constructor(ws) {
    this.ws = ws
    this.id = 0
    this.pending = new Map()
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        if (msg.error) reject(new Error(JSON.stringify(msg.error)))
        else resolve(msg.result)
      }
    })
  }
  send(method, params = {}) {
    const id = ++this.id
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
    })
  }
  async eval(expression) {
    const r = await this.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text)
    return r.result.value
  }
}

const ws = new WebSocket(await getPageWsUrl())
await new Promise((r) => ws.addEventListener('open', r, { once: true }))
const cdp = new CDP(ws)
await cdp.send('Runtime.enable')
await cdp.send('Page.enable')
await cdp.send('Emulation.setDeviceMetricsOverride', {
  width: 430,
  height: 932,
  deviceScaleFactor: 1,
  mobile: true,
})
await cdp.send('Page.navigate', {
  url: 'file:///' + encodeURI(join(ROOT, 'pages', PAGE).replace(/\\/g, '/')),
})
await sleep(1200)

const result = await cdp.eval(`(() => {
  const out = {};
  const box = (sel) => {
    const el = document.querySelector(sel);
    if (!el) return null;
    const r = el.getBoundingClientRect();
    const cs = getComputedStyle(el);
    return {
      top: Math.round(r.top), bottom: Math.round(r.bottom),
      left: Math.round(r.left), right: Math.round(r.right),
      w: Math.round(r.width), h: Math.round(r.height),
      bg: cs.backgroundColor, color: cs.color,
      pad: cs.padding, gap: cs.gap, radius: cs.borderRadius,
      border: cs.borderWidth + ' ' + cs.borderColor,
      font: cs.fontSize + '/' + cs.lineHeight + ' ' + cs.fontWeight + ' ' + cs.fontFamily.split(',')[0],
      shadow: cs.boxShadow,
    };
  };
  out['page'] = box('.page-today');
  out['top-bar'] = box('.top-bar');
  out['date-eyebrow'] = box('.date-eyebrow');
  out['page-title'] = box('.page-title');
  out['count-badge'] = box('.course-count-badge');
  out['week-switcher'] = box('.week-switcher');
  out['section-label'] = box('.section-label');
  out['course-stack'] = box('.course-stack');
  out['card1'] = box('.course-card');
  out['card1-name'] = box('.course-card .course-name');
  out['card1-type'] = box('.course-card .course-type');
  out['card1-meta'] = box('.course-card .course-meta');
  out['card1-accent'] = box('.course-accent');
  out['tomorrow-section'] = box('.tomorrow-section');
  out['tomorrow-card'] = box('.tomorrow-card');
  out['bottom-nav'] = box('#bottom-nav');
  out['cards'] = Array.from(document.querySelectorAll('.course-card')).map((el) => {
    const r = el.getBoundingClientRect();
    return { top: Math.round(r.top), bottom: Math.round(r.bottom), h: Math.round(r.height) };
  });
  out['tomorrow-cards'] = Array.from(document.querySelectorAll('.tomorrow-card')).map((el) => {
    const r = el.getBoundingClientRect();
    return { top: Math.round(r.top), bottom: Math.round(r.bottom), h: Math.round(r.height) };
  });
  return out;
})()`)

console.log(JSON.stringify(result, null, 2))
ws.close()
child.kill()
process.exit(0)
