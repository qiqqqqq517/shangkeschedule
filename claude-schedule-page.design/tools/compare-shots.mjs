// PNG 像素级对比：比较 Compose 渲染结果与设计包 HTML 截图。
//   - 逐像素 RGB 差异统计
//   - 分区（按行带）差异分布，定位布局偏差出现在页面哪一段
//   - 采样点的实际颜色，便于人工核对色值
// 用法：node tools/compare-shots.mjs <a.png> <b.png> [--bands 12]
import { readFileSync, existsSync } from 'node:fs'
import { inflateSync } from 'node:zlib'
import { join } from 'node:path'

function decodePng(path) {
  const buf = readFileSync(path)
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error('not png: ' + path)
  let pos = 8
  let width = 0
  let height = 0
  let bitDepth = 0
  let colorType = 0
  const idat = []
  while (pos < buf.length) {
    const len = buf.readUInt32BE(pos)
    const type = buf.toString('ascii', pos + 4, pos + 8)
    const data = buf.subarray(pos + 8, pos + 8 + len)
    if (type === 'IHDR') {
      width = data.readUInt32BE(0)
      height = data.readUInt32BE(4)
      bitDepth = data[8]
      colorType = data[9]
    } else if (type === 'IDAT') {
      idat.push(data)
    } else if (type === 'IEND') {
      break
    }
    pos += 12 + len
  }
  if (bitDepth !== 8) throw new Error('unsupported bit depth ' + bitDepth)
  const channels = colorType === 6 ? 4 : colorType === 2 ? 3 : colorType === 0 ? 1 : 0
  if (!channels) throw new Error('unsupported color type ' + colorType)

  const raw = inflateSync(Buffer.concat(idat))
  const stride = width * channels
  const out = Buffer.alloc(height * stride)
  let prev = Buffer.alloc(stride)
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)]
    const line = raw.subarray(y * (stride + 1) + 1, y * (stride + 1) + 1 + stride)
    const cur = Buffer.alloc(stride)
    for (let x = 0; x < stride; x++) {
      const a = x >= channels ? cur[x - channels] : 0
      const b = prev[x]
      const c = x >= channels ? prev[x - channels] : 0
      let v = line[x]
      if (filter === 1) v = (v + a) & 0xff
      else if (filter === 2) v = (v + b) & 0xff
      else if (filter === 3) v = (v + ((a + b) >> 1)) & 0xff
      else if (filter === 4) {
        const p = a + b - c
        const pa = Math.abs(p - a)
        const pb = Math.abs(p - b)
        const pc = Math.abs(p - c)
        const pr = pa <= pb && pa <= pc ? a : pb <= pc ? b : c
        v = (v + pr) & 0xff
      }
      cur[x] = v
    }
    cur.copy(out, y * stride)
    prev = cur
  }
  return { width, height, channels, data: out }
}

function px(img, x, y) {
  const i = (y * img.width + x) * img.channels
  return [img.data[i], img.data[i + 1], img.data[i + 2]]
}

function hex(rgb) {
  return '#' + rgb.map((v) => v.toString(16).padStart(2, '0')).join('')
}

const [, , pathA, pathB, ...rest] = process.argv
if (!pathA || !pathB) {
  console.error('usage: node tools/compare-shots.mjs a.png b.png [--bands N]')
  process.exit(1)
}
const bandsArg = rest.indexOf('--bands')
const BANDS = bandsArg >= 0 ? parseInt(rest[bandsArg + 1], 10) : 12

const A = decodePng(pathA)
const B = decodePng(pathB)
console.log(`A: ${pathA} ${A.width}x${A.height}`)
console.log(`B: ${pathB} ${B.width}x${B.height}`)

const W = Math.min(A.width, B.width)
const H = Math.min(A.height, B.height)

let diffPixels = 0
let sumAbs = 0
const bandStats = Array.from({ length: BANDS }, () => ({ diff: 0, total: 0, sum: 0 }))

for (let y = 0; y < H; y++) {
  const band = Math.min(BANDS - 1, Math.floor((y / H) * BANDS))
  for (let x = 0; x < W; x++) {
    const a = px(A, x, y)
    const b = px(B, x, y)
    const d = Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2])
    bandStats[band].total++
    bandStats[band].sum += d
    if (d > 24) {
      diffPixels++
      bandStats[band].diff++
      sumAbs += d
    }
  }
}

const total = W * H
console.log(`\n不同像素（阈值 ΔRGB>24）: ${diffPixels} / ${total} = ${((diffPixels / total) * 100).toFixed(2)}%`)
console.log(`\n行带差异分布（每带 ${Math.round(H / BANDS)}px 高）：`)
bandStats.forEach((s, i) => {
  const pct = ((s.diff / s.total) * 100).toFixed(1)
  const bar = '█'.repeat(Math.round((s.diff / s.total) * 40))
  console.log(`  band ${String(i).padStart(2)}  y=${String(Math.round((i * H) / BANDS)).padStart(4)}-${String(Math.round(((i + 1) * H) / BANDS)).padStart(4)}  ${pct.padStart(5)}%  ${bar}`)
})

console.log('\n采样点颜色（A=Compose, B=设计包）：')
const samples = [
  [10, 10, '页面底'],
  [215, 120, '页头区'],
  [215, 260, '周次条'],
  [60, 420, '课程卡 1'],
  [60, 560, '课程卡 2'],
  [215, 900, '底栏'],
]
for (const [x, y, label] of samples) {
  if (x < W && y < H) {
    console.log(`  (${String(x).padStart(3)},${String(y).padStart(3)}) ${label.padEnd(10)} A=${hex(px(A, x, y))}  B=${hex(px(B, x, y))}`)
  }
}
