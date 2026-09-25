"""PF6 性能基线与回归门禁 —— 真机掉帧采集（入库版）。

对应《界面优化方向清单》**PF6 性能基线与回归门禁**；判据见 `docs/agents/perf-baseline.md`。

为什么从 `tools/` 迁到 `scripts/`：
  旧脚本 `tools/verify_soft_jank.py` 功能完整（落盘 / 基线 / 回归判定），但 `tools/`
  被 `.gitignore:62` 忽略 ⇒ **脚本不随仓库分发**，换机即失，基线 JSON 也因此无法入库。
  本文件自包含（不依赖 `tools/verify_three_themes`），可随仓库分发。

一个必须知道的硬前提：
  `adb shell input tap/swipe` 需要 INJECT_EVENTS 权限。MIUI 等 ROM 默认关闭，
  表现为 `SecurityException: Injecting input events requires ... INJECT_EVENTS permission`。
  开启路径：开发者选项 → **USB 调试（安全设置）**（部分 ROM 需插 USB 且选「传输文件」）。
  未开启时本脚本会在自检阶段直接退出（退出码 2），不会给出看似正常实则全 0 的数据。

场景切换为什么是「半自动」：
  自动切主题/切 Tab 需要遍历 UI 树找文字再点击，逻辑长且随界面文案变化；一旦文案变动
  就会静默点错地方、采集到错误场景的数据。本脚本改为**提示用户在设备上切好场景再回车**，
  牺牲一点便利换取「采集的确实是这个场景」。

用法：
  python scripts/perf_jank.py                       # 采集 6 场景（柔绘/书卷/通透 × 课表/今日）
  python scripts/perf_jank.py --scenes 柔绘-课表     # 只采指定场景
  python scripts/perf_jank.py --save-baseline        # 本轮结果固化为回归基线
  python scripts/perf_jank.py --check-baseline       # 与基线比对，有回归则退出码 1

产物落在 `build_qa/soft_jank/`：`jank_<ts>.json`（供比对）、`jank_<ts>.md`（可贴进
Release 说明）、`baseline.json`（`--save-baseline` 生成）。
"""
import json
import os
import re
import subprocess
import sys
import time

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

PKG = "com.shangkeschedule"
OUT = "build_qa/soft_jank"
BASELINE = os.path.join(OUT, "baseline.json")

# 回归判据（两项同时满足才判 FAIL，避免 gfxinfo 采样噪声误报）
JANKY_ABS_TOL = 2.0     # 卡顿率绝对增加 > 2 个百分点
JANKY_REL_TOL = 0.30    # 且相对恶化 > 30%
P99_REL_TOL = 0.20      # p99 帧耗时相对恶化 > 20%

# 固定滚动负载：每页上滑 / 下滑各 N 次
SWIPE_PAIRS = 8
SWIPE_MS = 160
SWIPE_DY = 1100

THEMES = ("柔绘", "书卷", "通透")
TABS = ("课表", "今日")

ADB_CANDIDATES = (
    os.environ.get("ANDROID_HOME", "") + "/platform-tools/adb.exe",
    r"C:\Users\30458\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    r"D:\Android\SDK\platform-tools\adb.exe",
    r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe",
)


def log(*a):
    print(*a, flush=True)


def resolve_adb():
    for c in ADB_CANDIDATES:
        c = os.path.expandvars(c)
        if c and os.path.exists(c):
            return c
    return None


ADB = resolve_adb()


def adb(*args, check=False):
    if ADB is None:
        raise SystemExit("未找到 adb：请设置 ANDROID_HOME 或把 adb 放到常见路径")
    r = subprocess.run([ADB, *args], capture_output=True)
    out = r.stdout.decode("utf-8", "ignore") + r.stderr.decode("utf-8", "ignore")
    if check and r.returncode != 0:
        raise SystemExit(f"adb {' '.join(args)} 失败：{out.strip()}")
    return out


def preflight():
    """设备在线 + 注入权限自检。权限缺失时明确退出，避免采出全 0 数据。"""
    if ADB is None:
        log("[preflight] ❌ 未找到 adb")
        return False
    devices = adb("devices")
    if "\tdevice" not in devices:
        log("[preflight] ❌ 没有在线设备（adb devices）")
        return False
    log(f"[preflight] adb = {ADB}")

    # 注入能力探测：对当前界面发一次 tap，看是否被 INJECT_EVENTS 拒绝
    probe = adb("shell", "input", "tap", "1", "1")
    if "INJECT_EVENTS" in probe or "SecurityException" in probe:
        log("[preflight] ❌ 输入注入被拒绝（INJECT_EVENTS）——无法施加滚动负载")
        log("             开启：开发者选项 → USB 调试（安全设置），然后重插 USB")
        return False
    log("[preflight] ✅ 设备在线，输入注入可用")
    return True


def screen_size():
    m = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
    if m:
        return int(m.group(1)), int(m.group(2))
    return 1080, 2460


def load_scroll(w, h):
    """固定滚动负载：上滑 × N 再下滑 × N，制造持续重绘 + 复用。"""
    cx = int(w * 0.5)
    y0 = int(h * 0.75)
    y1 = max(1, y0 - SWIPE_DY)
    for _ in range(SWIPE_PAIRS):
        adb("shell", "input", "swipe", str(cx), str(y0), str(cx), str(y1), str(SWIPE_MS))
        time.sleep(0.09)
    for _ in range(SWIPE_PAIRS):
        adb("shell", "input", "swipe", str(cx), str(y1), str(cx), str(y0), str(SWIPE_MS))
        time.sleep(0.09)


def parse(dump):
    def num(pat, cast=float):
        m = re.search(pat, dump)
        return cast(m.group(1)) if m else None

    janky_line = re.search(r"Janky frames:\s*(\d+)\s*\(([\d.]+)%\)", dump)
    return {
        "total": num(r"Total frames rendered:\s*(\d+)", int),
        "janky": int(janky_line.group(1)) if janky_line else None,
        "janky_pct": float(janky_line.group(2)) if janky_line else None,
        "p50": num(r"50th percentile:\s*(\d+)ms", int),
        "p90": num(r"90th percentile:\s*(\d+)ms", int),
        "p95": num(r"95th percentile:\s*(\d+)ms", int),
        "p99": num(r"99th percentile:\s*(\d+)ms", int),
        "missed_vsync": num(r"Number Missed Vsync:\s*(\d+)", int),
        "slow_draw": num(r"Number Slow draw:\s*(\d+)", int),
        "slow_bitmap": num(r"Number Slow bitmap uploads:\s*(\d+)", int),
    }


def measure(theme, tab, w, h):
    """采集单个场景：reset → 固定滚动负载 → 解析 gfxinfo。"""
    adb("shell", "dumpsys", "gfxinfo", PKG, "reset")
    time.sleep(0.4)
    load_scroll(w, h)
    time.sleep(1.2)
    d = parse(adb("shell", "dumpsys", "gfxinfo", PKG))
    if not d.get("total"):
        log(f"   !! [{theme}·{tab}] 未采集到帧数据 —— 应用是否在前台？")
        return None
    log(f"   total={d['total']}  janky={d['janky']} ({d['janky_pct']}%)  "
        f"p50={d['p50']}  p90={d['p90']}  p95={d['p95']}  p99={d['p99']}")
    return d


def dump_results(results):
    os.makedirs(OUT, exist_ok=True)
    ts = time.strftime("%Y%m%d_%H%M%S")
    scenes = [{"theme": t, "tab": b, **d} for (t, b), d in sorted(results.items())]
    payload = {"captured_at": ts, "swipe_pairs": SWIPE_PAIRS, "scenes": scenes}

    jpath = os.path.join(OUT, f"jank_{ts}.json")
    with open(jpath, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    lines = [
        f"# 掉帧实测 {ts}",
        "",
        f"负载：每页上滑 / 下滑各 {SWIPE_PAIRS} 次；采集 `dumpsys gfxinfo {PKG}`",
        "",
        "| 主题 | 页面 | 总帧 | 卡顿帧 | 卡顿率 | p50 | p90 | p95 | p99 |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    for s in scenes:
        lines.append(
            f"| {s['theme']} | {s['tab']} | {s['total'] or 0} | {s['janky'] or 0} | "
            f"{s['janky_pct'] if s['janky_pct'] is not None else '-'}% | "
            f"{s['p50'] or 0} | {s['p90'] or 0} | {s['p95'] or 0} | {s['p99'] or 0} |"
        )
    mpath = os.path.join(OUT, f"jank_{ts}.md")
    with open(mpath, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    log(f"\nartifacts: {jpath}")
    log(f"           {mpath}")
    return payload


def check_against_baseline(payload):
    if not os.path.exists(BASELINE):
        log(f"\n[baseline] 尚无基线（{BASELINE}）—— 加 --save-baseline 可固化本轮结果")
        return True
    base = json.load(open(BASELINE, encoding="utf-8"))
    base_map = {(s["theme"], s["tab"]): s for s in base.get("scenes", [])}

    ok = True
    log("\n=== 基线回归判定 ===")
    for s in payload["scenes"]:
        b = base_map.get((s["theme"], s["tab"]))
        if not b:
            log(f"  SKIP {s['theme']}·{s['tab']}：基线缺该场景")
            continue
        j0, j1 = b.get("janky_pct"), s.get("janky_pct")
        p0, p1 = b.get("p99"), s.get("p99")
        bad_j = (j0 is not None and j1 is not None and j0 > 0
                 and (j1 - j0) > JANKY_ABS_TOL and (j1 - j0) / j0 > JANKY_REL_TOL)
        bad_p = bool(p0 and p1 and (p1 - p0) / p0 > P99_REL_TOL)
        if bad_j or bad_p:
            ok = False
        log(f"  {'FAIL' if (bad_j or bad_p) else 'OK  '}  {s['theme']}·{s['tab']}："
            f"janky {j0}% → {j1}%   p99 {p0}ms → {p1}ms")
    return ok


def main():
    argv = sys.argv[1:]
    targets = [(t, b) for t in THEMES for b in TABS]
    if "--scenes" in argv:
        want = set(argv[argv.index("--scenes") + 1].split(","))
        targets = [x for x in targets if f"{x[0]}-{x[1]}" in want]

    log("=== PF6 真机掉帧采集 ===")
    if not preflight():
        sys.exit(2)

    w, h = screen_size()
    adb("shell", "wm", "dismiss-keyguard")
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(6)

    results = {}
    for theme, tab in targets:
        # 半自动：切主题/切页交给人做，避免 UI 遍历点错地方导致采到错误场景
        input(f"\n>>> 请在设备上切到「{theme}」主题 ·「{tab}」页，回到该页后按 Enter 采集… ")
        d = measure(theme, tab, w, h)
        if d:
            results[(theme, tab)] = d

    if not results:
        log("\n没有任何场景采集成功")
        sys.exit(1)

    log("\n\n=== 汇总 ===")
    log(f"{'主题':<6}{'页面':<6}{'总帧':>8}{'卡顿帧':>8}{'卡顿率':>9}{'p99':>6}")
    for (theme, tab), d in results.items():
        log(f"{theme:<6}{tab:<6}{d['total'] or 0:>8}{d['janky'] or 0:>8}"
            f"{(str(d['janky_pct']) + '%') if d['janky_pct'] is not None else '-':>9}"
            f"{d['p99'] or 0:>6}")

    payload = dump_results(results)

    if "--save-baseline" in argv:
        os.makedirs(OUT, exist_ok=True)
        with open(BASELINE, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
        log(f"\n[baseline] 已固化基线 → {BASELINE}")

    if "--check-baseline" in argv and not check_against_baseline(payload):
        log("\n[baseline] 判定：存在性能回归（退出码 1）")
        sys.exit(1)


if __name__ == "__main__":
    main()
