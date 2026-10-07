// 设计包页面校验：无头 Chromium + CDP，按多端断点逐页检查
//   - 控制台错误 / 资源 404
//   - 横向溢出（scrollWidth > clientWidth）
//   - 元素越界出视口
//   - 导航可见性（手机显示底栏 / 平板以上显示侧栏）
//   - 关键交互：主题切换、周次切换、课程弹层、导入步骤、学期切换
// 用法：node tools/verify-pages.mjs [--screenshot]
import { spawn } from 'node:child_process'
import { mkdirSync, existsSync } from 'node:fs'
import { join } from 'node:path'
import { setTimeout as sleep } from 'node:timers/promises'

const ROOT = process.cwd()
// 截图与 CDP 临时 profile 落到仓库根 build_qa/（已 gitignore），不污染设计包目录
const SHOT_DIR = join(ROOT, '..', 'build_qa', 'claude-design')
const PROFILE_DIR = join(ROOT, '..', 'build_qa', 'claude-design-cdp-profile')
mkdirSync(SHOT_DIR, { recursive: true })
mkdirSync(PROFILE_DIR, { recursive: true })

const CHROME_CANDIDATES = [
  'C:/Users/30458/AppData/Local/ms-playwright/chromium-1228/chrome-win64/chrome.exe',
  'C:/Users/30458/AppData/Local/ms-playwright/chromium-1223/chrome-win64/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
]
const CHROME = CHROME_CANDIDATES.find((p) => existsSync(p))
if (!CHROME) {
  console.error('no chrome found')
  process.exit(1)
}

const PAGES = [
  'index.html',
  'pages/今日日程.html',
  'pages/周课表.html',
  'pages/多学期管理.html',
  'pages/教务导入.html',
  'pages/组件预览.html',
]

const VIEWPORTS = [
  { name: 'phone', width: 430, height: 932, mobile: true },
  { name: 'phone-sm', width: 360, height: 780, mobile: true },
  { name: 'tablet', width: 834, height: 1112, mobile: false },
  { name: 'desktop', width: 1440, height: 900, mobile: false },
]

const PORT = 9333
const args = [
  '--headless=new',
  '--disable-gpu',
  '--no-sandbox',
  '--hide-scrollbars',
  '--disable-dev-shm-usage',
  `--remote-debugging-port=${PORT}`,
  '--user-data-dir=' + PROFILE_DIR,
  'about:blank',
]

const child = spawn(CHROME, args, { stdio: 'ignore' })

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
    this.events = []
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        if (msg.error) reject(new Error(JSON.stringify(msg.error)))
        else resolve(msg.result)
      } else if (msg.method) {
        this.events.push(msg)
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
    const r = await this.send('Runtime.evaluate', {
      expression,
      returnByValue: true,
      awaitPromise: true,
      userGesture: true,
    })
    if (r.exceptionDetails) {
      throw new Error(
        'eval error: ' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text)
      )
    }
    return r.result.value
  }
}

const PROBE = `(() => {
  const doc = document.documentElement;
  const vw = window.innerWidth, vh = window.innerHeight;
  const overflowX = doc.scrollWidth - vw;
  const inScroller = (el) => {
    let p = el.parentElement;
    while (p) {
      const cs = getComputedStyle(p);
      if ((cs.overflowX === 'auto' || cs.overflowX === 'scroll' || cs.overflowY === 'auto' || cs.overflowY === 'scroll')) return true;
      p = p.parentElement;
    }
    return false;
  };
  const outOfView = [];
  document.querySelectorAll('body *').forEach((el) => {
    const cs = getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden' || cs.position === 'fixed') return;
    const r = el.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) return;
    if (r.right > vw + 1 || r.left < -1) {
      if (inScroller(el)) return;
      const cls = typeof el.className === 'string' ? el.className : (el.getAttribute('class') || '');
      outOfView.push({ tag: el.tagName.toLowerCase(), cls: cls.slice(0, 60), left: Math.round(r.left), right: Math.round(r.right), w: Math.round(r.width), h: Math.round(r.height) });
    }
  });
  const nav = document.getElementById('bottom-nav');
  const rail = document.querySelector('.app-rail');
  const vis = (el) => !!el && getComputedStyle(el).display !== 'none' && el.getBoundingClientRect().height > 0;
  return {
    title: document.title,
    bodyPage: document.body.dataset.page || null,
    overflowX,
    outOfView: outOfView.slice(0, 6),
    outOfViewCount: outOfView.length,
    bottomNavVisible: vis(nav),
    railVisible: vis(rail),
    theme: doc.getAttribute('data-theme'),
    fontLoaded: document.fonts ? document.fonts.check('600 16px Poppins') : null,
    courseCards: document.querySelectorAll('.course-card').length,
    iconUse: document.querySelectorAll('use[href]').length,
  };
})()`

const results = []
const consoleErrors = []

const wsUrl = await getPageWsUrl()
const ws = new WebSocket(wsUrl)
await new Promise((r) => ws.addEventListener('open', r, { once: true }))
const cdp = new CDP(ws)
await cdp.send('Runtime.enable')
await cdp.send('Log.enable')
await cdp.send('Page.enable')
await cdp.send('Network.enable')

for (const page of PAGES) {
  const url = 'file:///' + encodeURI(join(ROOT, page).replace(/\\/g, '/'))
  for (const vp of VIEWPORTS) {
    consoleErrors.length = 0
    cdp.events.length = 0
    await cdp.send('Emulation.setDeviceMetricsOverride', {
      width: vp.width,
      height: vp.height,
      deviceScaleFactor: 1,
      mobile: vp.mobile,
    })
    await cdp.send('Page.navigate', { url })
    await sleep(900)
    let probe
    try {
      probe = await cdp.eval(PROBE)
    } catch (e) {
      probe = { error: String(e.message) }
    }
    const errs = cdp.events
      .filter(
        (e) =>
          e.method === 'Runtime.consoleAPICalled' && e.params.type === 'error'
      )
      .map((e) => e.params.args.map((a) => a.value ?? a.description).join(' '))
    const failedReqs = cdp.events
      .filter((e) => e.method === 'Network.loadingFailed')
      .map((e) => e.params.errorText)
    results.push({ page, viewport: vp.name, ...probe, consoleErrors: errs, failedReqs: [...new Set(failedReqs)] })

    if (process.argv.includes('--screenshot')) {
      const { data } = await cdp.send('Page.captureScreenshot', {
        format: 'png',
        captureBeyondViewport: false,
      })
      const fs = await import('node:fs')
      const shotName = page.replace(/\.html$/, '').replace(/[\\/]/g, '-')
      fs.writeFileSync(
        join(SHOT_DIR, `${shotName}-${vp.name}.png`),
        Buffer.from(data, 'base64')
      )
    }
  }
}

/* ---------- 交互校验（在手机断点下） ---------- */
await cdp.send('Emulation.setDeviceMetricsOverride', {
  width: 430,
  height: 932,
  deviceScaleFactor: 1,
  mobile: true,
})

const interactions = []

// 1. 今日日程：周次切换 + 课程弹层 + 主题切换
{
  const url = 'file:///' + encodeURI(join(ROOT, 'pages', '今日日程.html').replace(/\\/g, '/'))
  await cdp.send('Page.navigate', { url })
  await sleep(800)
  const before = await cdp.eval(`document.getElementById('week-label').textContent`)
  await cdp.eval(`document.getElementById('week-next').click()`)
  await sleep(150)
  const after = await cdp.eval(`document.getElementById('week-label').textContent`)
  await cdp.eval(`document.querySelector('.course-card').click()`)
  await sleep(350)
  const sheetOpen = await cdp.eval(
    `document.getElementById('detail-sheet').classList.contains('open')`
  )
  const sheetTitle = await cdp.eval(`document.getElementById('sheet-title').textContent`)
  await cdp.eval(`document.getElementById('sheet-close').click()`)
  await sleep(300)
  const sheetClosed = await cdp.eval(
    `!document.getElementById('detail-sheet').classList.contains('open')`
  )
  await cdp.eval(`document.querySelector('[data-theme-toggle]').click()`)
  await sleep(200)
  const theme = await cdp.eval(`document.documentElement.getAttribute('data-theme')`)
  const bgAfterDark = await cdp.eval(
    `getComputedStyle(document.body).backgroundColor`
  )
  await cdp.eval(`document.querySelector('[data-theme-toggle]').click()`)
  await sleep(200)
  const themeBack = await cdp.eval(`document.documentElement.getAttribute('data-theme')`)
  interactions.push({
    name: '今日日程',
    weekSwitch: `${before} -> ${after}`,
    weekSwitchOk: before !== after,
    sheetOpen,
    sheetTitle,
    sheetClosed,
    themeAfterToggle: theme,
    bgAfterDark,
    themeBack,
  })
}

// 2. 周课表：周次前后翻 + 周次胶囊 + 课程块弹层
{
  const url = 'file:///' + encodeURI(join(ROOT, 'pages', '周课表.html').replace(/\\/g, '/'))
  await cdp.send('Page.navigate', { url })
  await sleep(900)
  const before = await cdp.eval(`document.getElementById('week-title').textContent`)
  await cdp.eval(`document.getElementById('btn-next-week').click()`)
  await sleep(150)
  const after = await cdp.eval(`document.getElementById('week-title').textContent`)
  await cdp.eval(`document.querySelector('.week-chip[data-week="7"]').click()`)
  await sleep(200)
  const chipWeek = await cdp.eval(`document.getElementById('week-title').textContent`)
  const chipActive = await cdp.eval(`document.querySelector('.week-chip.active').getAttribute('data-week')`)
  await cdp.eval(`document.querySelector('[data-course-block]').click()`)
  await sleep(350)
  const modalOpen = await cdp.eval(`document.getElementById('course-modal').classList.contains('open')`)
  const modalTitle = await cdp.eval(`document.getElementById('modal-title').textContent`)
  await cdp.eval(`document.getElementById('modal-close').click()`)
  await sleep(300)
  const modalClosed = await cdp.eval(`!document.getElementById('course-modal').classList.contains('open')`)
  await cdp.eval(`document.querySelector('#view-toggle .tab[data-view="day"]').click()`)
  await sleep(250)
  const dayViewCols = await cdp.eval(`Array.from(document.querySelectorAll('.day-col')).filter(c => c.style.display !== 'none').length`)
  await cdp.eval(`document.querySelector('#view-toggle .tab[data-view="week"]').click()`)
  await sleep(250)
  const weekViewCols = await cdp.eval(`Array.from(document.querySelectorAll('.day-col')).filter(c => c.style.display !== 'none').length`)
  const blocks = await cdp.eval(`document.querySelectorAll('[data-course-block]').length`)
  interactions.push({
    name: '周课表', blocks,
    weekNext: `${before} -> ${after}`, weekNextOk: before !== after,
    chipJump: `${chipWeek} (active=${chipActive})`,
    modalOpen, modalTitle, modalClosed,
    dayViewCols, weekViewCols,
  })
}

// 3. 教务导入：学校搜索 + 步骤流转 + 登录 + 进度
{
  const url = 'file:///' + encodeURI(join(ROOT, 'pages', '教务导入.html').replace(/\\/g, '/'))
  await cdp.send('Page.navigate', { url })
  await sleep(900)
  const schoolCount = await cdp.eval(`document.querySelectorAll('.school-card').length`)
  await cdp.eval(`(() => {
    const i = document.getElementById('school-search');
    i.value = '复旦';
    i.dispatchEvent(new Event('input', { bubbles: true }));
  })()`)
  await sleep(250)
  const filteredCount = await cdp.eval(`document.querySelectorAll('.school-card').length`)
  await cdp.eval(`document.querySelector('.school-card').click()`)
  await sleep(350)
  const stepAfterSchool = await cdp.eval(`document.querySelector('.step-active').getAttribute('data-step')`)
  await cdp.eval(`(() => {
    document.getElementById('student-id').value = '2024001';
    document.getElementById('password').value = 'demo1234';
    document.getElementById('captcha').value = 'A7K9';
    document.getElementById('login-form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  })()`)
  await sleep(1700)
  const stepAfterLogin = await cdp.eval(`document.querySelector('.step-active').getAttribute('data-step')`)
  const semesterCount = await cdp.eval(`document.querySelectorAll('.semester-card').length`)
  await cdp.eval(`document.querySelector('.semester-card').click()`)
  await sleep(600)
  const stepAfterSemester = await cdp.eval(`document.querySelector('.step-active').getAttribute('data-step')`)
  const progressWidth = await cdp.eval(`document.getElementById('import-fill').style.width`)
  const progressAria = await cdp.eval(`document.getElementById('import-progress').getAttribute('aria-valuenow')`)
  interactions.push({
    name: '教务导入', schoolCount, filteredCount, stepAfterSchool, stepAfterLogin,
    semesterCount, stepAfterSemester, progressWidth, progressAria,
  })
}

// 4. 多学期管理：学期切换 / 复制 / 删除确认 / 展开更多
{
  const url = 'file:///' + encodeURI(join(ROOT, 'pages', '多学期管理.html').replace(/\\/g, '/'))
  await cdp.send('Page.navigate', { url })
  await sleep(900)
  const initialCards = await cdp.eval(`document.querySelectorAll('[data-semester]').length`)
  const totalText = await cdp.eval(`document.getElementById('semester-total').textContent`)
  await cdp.eval(`document.getElementById('btn-more-semesters').click()`)
  await sleep(250)
  const expandedCards = await cdp.eval(`document.querySelectorAll('[data-semester]').length`)
  const expandedLabel = await cdp.eval(`document.querySelector('#btn-more-semesters span').textContent`)
  await cdp.eval(`document.querySelector('[data-act="copy"]').click()`)
  await sleep(250)
  const afterCopy = await cdp.eval(`document.querySelectorAll('[data-semester]').length`)
  await cdp.eval(`document.querySelector('[data-act="delete"]').click()`)
  await sleep(300)
  const dialogOpen = await cdp.eval(`document.getElementById('confirm-dialog').classList.contains('open')`)
  await cdp.eval(`document.getElementById('confirm-ok').click()`)
  await sleep(300)
  const afterDelete = await cdp.eval(`document.querySelectorAll('[data-semester]').length`)
  interactions.push({
    name: '多学期管理', initialCards, totalText, expandedCards, expandedLabel,
    afterCopy, dialogOpen, afterDelete,
  })
}

// 5. 组件预览：分区跳转 + token 复制 + 主题切换
{
  const url = 'file:///' + encodeURI(join(ROOT, 'pages', '组件预览.html').replace(/\\/g, '/'))
  await cdp.send('Page.navigate', { url })
  await sleep(900)
  const swatches = await cdp.eval(`document.querySelectorAll('.swatch').length`)
  const sections = await cdp.eval(`document.querySelectorAll('.section').length`)
  await cdp.eval(`document.querySelector('.page-toolbar .tab[data-jump="section-navigation"]').click()`)
  await sleep(700)
  const scrollY = await cdp.eval(`Math.round(window.scrollY)`)
  const navTop = await cdp.eval(`Math.round(document.getElementById('section-navigation').getBoundingClientRect().top)`)
  await cdp.eval(`document.querySelector('[data-copy]').click()`)
  await sleep(300)
  const toastShown = await cdp.eval(`document.getElementById('copy-toast').classList.contains('show')`)
  await cdp.eval(`document.querySelector('[data-theme-toggle]').click()`)
  await sleep(250)
  const theme = await cdp.eval(`document.documentElement.getAttribute('data-theme')`)
  const swatchBg = await cdp.eval(`getComputedStyle(document.querySelector('.swatch')).backgroundColor`)
  interactions.push({
    name: '组件预览', swatches, sections, scrollY, navTop, toastShown, theme, swatchBg,
  })
}

console.log('\n===== 布局校验 =====')
for (const r of results) {
  const flag =
    r.overflowX > 1 || r.outOfViewCount > 0 || (r.consoleErrors && r.consoleErrors.length)
      ? 'FAIL'
      : 'ok  '
  console.log(
    `${flag} ${r.page.padEnd(14)} ${r.viewport.padEnd(9)} overflowX=${String(r.overflowX).padStart(4)} outOfView=${String(r.outOfViewCount).padStart(2)} nav=${r.bottomNavVisible ? 'bottom' : ''}${r.railVisible ? 'rail' : ''} font=${r.fontLoaded} theme=${r.theme}` +
      (r.consoleErrors && r.consoleErrors.length ? ` consoleErr=${JSON.stringify(r.consoleErrors)}` : '') +
      (r.failedReqs && r.failedReqs.length ? ` netFail=${JSON.stringify(r.failedReqs)}` : '')
  )
  if (r.outOfViewCount > 0) console.log('      outOfView:', JSON.stringify(r.outOfView))
  if (r.error) console.log('      probeError:', r.error)
}

console.log('\n===== 交互校验 =====')
for (const i of interactions) console.log(JSON.stringify(i))

ws.close()
child.kill()
process.exit(0)
