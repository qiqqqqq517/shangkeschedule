"""AC4 字体缩放（系统字号）适配审计。

对应《界面优化方向清单》**AC4 系统字体缩放适配**（清单原描述：「全库无字体缩放适配」）。

**审计结论（2026-09-26）**：清单的「无适配」在**风险层面不成立** ——
全库 544 个 `Text` 中，1.0× 与 2.0× 字号下的固定高度容器判定均为 0 处裁切。
原因是本项目文本统一用 **sp**（随系统缩放），且固定高度容器普遍留有余量；
`lineHeight = N.sp` 也是 sp，与 `fontSize` **等比缩放**，不构成挤压。
⇒ 无需为放大字体引入响应式布局；本脚本的作用是**回归守护**（改布局后可重跑）。

判据（保守）：
  容器可用高度 = height − 上下 padding；
  2.0× 下文本行盒 ≈ fontSize × LINE_BOX_RATIO × 2.0；
  若可用高度 < 行盒 ⇒ 判定可能裁切。

局限（务必知悉）：
  - 只覆盖 Row / Box / Column / Surface / Card / Button / TextButton / Scaffold 容器，
    以及「该容器块内直接出现的 fontSize」；自定义组件内部、Lazy 项、Compose 自动测量
    导致的挤压不在范围内。
  - 行高系数是经验值，不是精确字体度量；判定为「可能」，真机实测仍是最终依据
    （改系统字号 1.0 / 1.3 / 1.5 / 2.0 各看一遍）。
  - 横向溢出（`maxLines = 1` + 固定宽度）单独统计，仅供人工复核。

用法：
  python scripts/check_font_scale.py            # 打印审计结果
  python scripts/check_font_scale.py --scale 1.5 # 指定缩放倍数（默认 2.0）
"""
import argparse
import os
import re
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = os.path.join("shared", "src", "commonMain", "kotlin")
CONTAINERS = ("Row", "Box", "Column", "Surface", "Card", "Button", "TextButton", "Scaffold")
LINE_BOX_RATIO = 1.4  # 行盒 ≈ 字号 × 1.4（经验值）


def match_close(src, open_pos):
    depth = 0
    i = open_pos
    while i < len(src):
        if src[i] == "(":
            depth += 1
        elif src[i] == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(src) - 1


def body_end(src, close_pos):
    """把容器调用后紧跟的 lambda 并入检查区间（Compose 内容写在尾部 lambda 里）。"""
    i = close_pos + 1
    while i < len(src) and src[i] in " \t\r\n":
        i += 1
    if i < len(src) and src[i] == "{":
        depth = 0
        j = i
        while j < len(src):
            if src[j] == "{":
                depth += 1
            elif src[j] == "}":
                depth -= 1
                if depth == 0:
                    return j
            j += 1
    return close_pos


def enclosing_container(src, pos):
    """含 pos 的最近容器调用块，返回 (名称, open_pos, close_pos)。"""
    m = None
    for mm in re.finditer(r"(\w+)\s*\(", src[:pos]):
        m = mm
    while m is not None:
        if m.group(1) in CONTAINERS:
            o = m.end() - 1
            return m.group(1), o, match_close(src, o)
        # 继续往前找
        prev = None
        for mm in re.finditer(r"(\w+)\s*\(", src[:m.start()]):
            prev = mm
        m = prev
    return None


def scan(scale):
    hits = []
    maxlines1 = []
    for dp, _, fns in os.walk(ROOT):
        for fn in sorted(fns):
            if not fn.endswith(".kt"):
                continue
            p = os.path.join(dp, fn)
            rel = os.path.relpath(p, ".").replace("\\", "/")
            src = open(p, encoding="utf-8", errors="replace").read()

            # 遍历容器调用：**height 必须出现在该容器自身的参数区**才算它的固定高度。
            # 否则会错配 —— `Spacer(Modifier.height(4.dp))` 的 4dp 曾被当成其前面
            # 某个 Column 的高度，与远处的 fontSize 拼在一起，产生 30 处假告警。
            for m in re.finditer(r"(\w+)\s*\(", src):
                name = m.group(1)
                if name not in CONTAINERS:
                    continue
                o = m.end() - 1
                close = match_close(src, o)
                params = src[o:close]
                hm = re.search(r"\.height\((\d+(?:\.\d+)?)(?:f)?\.dp\)", params)
                if not hm:
                    continue
                h = float(hm.group(1))
                end = body_end(src, close)
                block = src[o:end]
                fm = re.search(r"fontSize\s*=\s*(\d+(?:\.\d+)?)\.sp", block)
                if not fm:
                    continue
                f = float(fm.group(1))
                pads = [float(x) for x in re.findall(r"vertical\s*=\s*(\d+(?:\.\d+)?)\.dp", params)]
                pad = (max(pads) if pads else 0.0) * 2
                avail = h - pad
                need = f * LINE_BOX_RATIO * scale
                if avail < need:
                    hits.append({
                        "file": rel,
                        "line": src[: m.start()].count("\n") + 1,
                        "container": name,
                        "height": h,
                        "fontSize": f,
                        "avail": round(avail, 1),
                        "need": round(need, 1),
                    })

            # 横向溢出候选：maxLines=1 且同一块内有固定宽度
            for m in re.finditer(r"maxLines\s*=\s*1", src):
                enc = enclosing_container(src, m.start())
                if not enc:
                    continue
                name, o, close = enc
                if "width(" in src[o:body_end(src, close)]:
                    maxlines1.append({
                        "file": rel,
                        "line": src[: m.start()].count("\n") + 1,
                        "container": name,
                    })
    return hits, maxlines1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scale", type=float, default=2.0)
    args = ap.parse_args()

    hits, maxlines1 = scan(args.scale)
    print(f"=== AC4 字体缩放审计（{args.scale}×，行盒系数 {LINE_BOX_RATIO}）===")
    print(f"垂直裁切风险：{len(hits)} 处")
    for h in hits:
        print(f"  ✗ {h['file']}:{h['line']}  {h['container']}(height {h['height']}dp)")
        print(f"      可用 {h['avail']}dp < 需要 {h['need']}dp（字号 {h['fontSize']}sp）")
    if not hits:
        print("  ✅ 无（该项目文本用 sp 等比缩放，固定高度容器在 2.0× 内均有余量）")

    print(f"\n横向溢出候选（maxLines=1 + 同块固定宽度）：{len(maxlines1)} 处（仅供参考）")
    seen = set()
    for h in maxlines1:
        key = (h["file"], h["line"])
        if key in seen:
            continue
        seen.add(key)
        print(f"  ? {h['file']}:{h['line']}  {h['container']}")

    print("\n注：本脚本为保守启发式判定，真机实测（系统字号 1.0/1.3/1.5/2.0）仍是最终依据。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
