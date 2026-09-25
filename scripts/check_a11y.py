"""AC2 无障碍语义棘轮门禁。

对应《界面优化方向清单》**AC2 语义标注补齐** / **AC5 无障碍回归纳入流程**。

为什么需要脚本而不是人工走查：
  `contentDescription = null` 这个字符串本身**不是缺陷信号**——图标与文字同处
  一个可读/可点击节点时，图标保持 null 才是正确做法（否则 TalkBack 会重复播报）。
  本仓库 54 处 null 里，约 46 处属于这种「装饰性且正确」的情形。
  因此门禁不能数 null，只能数**真正不可辨识的可操作节点**。

两条规则（都要求可静态判定、无歧义）：

  A 类 · 独立图标按钮无描述
      `IconButton` / `IconToggleButton` / `FloatingActionButton` 内的 `Icon`
      没有 contentDescription，或显式写了 null。这类按钮没有文字，TalkBack
      只能读到「未加标签的按钮」。

  B 类 · 可点击节点不可辨识
      含 `.clickable` / `.combinedClickable` 的最小包围块内，既没有 `Text`
      （读不出内容），也没有任何语义声明（contentDescription / toggleable /
      selectable / semantics / role）——用户点下去不知道自己点了什么。
      典型：`Modifier.clickable` 的复选框、纯色块选择器。

用法：
  python scripts/check_a11y.py --baseline scripts/a11y-baseline.json
  python scripts/check_a11y.py --baseline scripts/a11y-baseline.json --update-baseline scripts/a11y-baseline.json

只拦「违规增加」（棘轮）：单项超过基线即退出码 1；下降不拦，但会提示可收紧基线。
"""
import argparse
import json
import os
import re
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = os.path.join("shared", "src", "commonMain", "kotlin")

# 独立图标按钮：内部没有文字，图标描述是唯一语义来源
ICON_BUTTON_CALLS = ("IconButton(", "IconToggleButton(", "FloatingActionButton(")
CLICKABLE_RE = re.compile(r"\.(clickable|combinedClickable)\s*[({]")
# 块内出现任一项即视为「可辨识」
IDENTIFIABLE_TOKENS = (
    "contentDescription",
    "toggleable(",
    "selectable(",
    "semantics",
    "role =",
    "Text(",
    "BasicText(",
)
# 静态分析无法判定、且实践中并非缺陷的三类写法（逐条抽查确认过）：
#   1. `Text(... modifier = Modifier.clickable{})` —— 文本本身就是内容，天然可辨识；
#   2. `matchParentSize()` 覆盖点击层 —— 语义由被覆盖的 Icon 层提供（外层已带描述），
#      本项目玻璃件普遍采用此写法以保住向外绘制的阴影/高光；
#   3. content 由调用方传入的容器 —— 内容在调用点，静态扫不到，不能据此判违规。
UNDECIDABLE_TOKENS = (
    "matchParentSize",
    "content = content",
    "content()",
)
# 调用名本身即内容载体
SELF_DESCRIBING_CALLS = ("Text", "BasicText")
# Modifier 链式组合器：clickable 常写在 `.then(if (...) {...})` 里，
# 若把 `then(...)` 当成包围块，就会漏看外层容器的内容与语义，需向外扩展。
CHAIN_CALLS = ("then", "let", "apply", "also", "run", "with")


def match_pairs(src):
    """返回所有括号/花括号对 (kind, open, close)。

    必须同时解析 `{}`：Compose 的内容写在**尾部 lambda** 里（`Row(...) { Text(...) }`），
    只看 `()` 区间会把 body 内的 Text 漏掉 —— 第一版实现因此误报 61 处
    （把「月份标题 Row」「设置行 Row」这类明明有文字的节点判成不可辨识）。
    """
    stack = []
    pairs = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if c == '"':
            i += 1
            while i < n and src[i] != '"':
                if src[i] == "\\":
                    i += 1
                i += 1
        elif c == "/" and i + 1 < n and src[i + 1] == "/":
            while i < n and src[i] != "\n":
                i += 1
        elif c == "/" and i + 1 < n and src[i + 1] == "*":
            i += 2
            while i + 1 < n and not (src[i] == "*" and src[i + 1] == "/"):
                i += 1
        elif c in "({":
            stack.append((c, i))
        elif c in ")}":
            if stack:
                kind, o = stack.pop()
                if (kind == "(" and c == ")") or (kind == "{" and c == "}"):
                    pairs.append((kind, o, i))
        i += 1
    return pairs


def block_with_body(src, o, c, pairs):
    """把 `Foo(...) { ... }` 的尾部 lambda 并入检查区间。"""
    i = c + 1
    n = len(src)
    while i < n and src[i] in " \t\r\n":
        i += 1
    if i < n and src[i] == "{":
        for kind, bo, bc in pairs:
            if kind == "{" and bo == i:
                return o, bc
    return o, c


def find_call_blocks(src, call_names):
    """找到以 call_names 开头的所有调用块（含参数区间）。"""
    blocks = []
    for name in call_names:
        start = 0
        while True:
            idx = src.find(name, start)
            if idx < 0:
                break
            open_pos = idx + len(name) - 1
            # 从 open_pos 起做括号平衡
            depth = 0
            i = open_pos
            n = len(src)
            while i < n:
                if src[i] == "(":
                    depth += 1
                elif src[i] == ")":
                    depth -= 1
                    if depth == 0:
                        blocks.append((name, open_pos, i))
                        break
                i += 1
            start = idx + len(name)
    return blocks


def icon_button_violations(path, src):
    """A 类：独立图标按钮内的 Icon 缺描述。"""
    out = []
    for name, o, c in find_call_blocks(src, ICON_BUTTON_CALLS):
        body = src[o:c]
        if "Icon(" not in body and "Icon(" not in body.replace("IconButton(", ""):
            continue
        # 按钮体内没有 contentDescription 声明，或显式写成 null
        has_desc = "contentDescription" in body
        if not has_desc or re.search(r"contentDescription\s*=\s*null", body):
            line = src[:o].count("\n") + 1
            out.append({
                "file": path,
                "line": line,
                "rule": "A",
                "detail": f"{name.rstrip('(')} 内图标{'显式 null' if has_desc else '未声明 contentDescription'}",
            })
    return out


def call_name_before(src, open_pos):
    """回溯 `(` 之前的调用名，用于识别 `Text(...)` 这类「自身即内容」的调用。"""
    i = open_pos - 1
    while i >= 0 and src[i] in " \t\r\n":
        i -= 1
    end = i + 1
    while i >= 0 and (src[i].isalnum() or src[i] in "_."):
        i -= 1
    return src[i + 1:end].split(".")[-1]


def enclosing_call_block(src, pos, pairs):
    """包含 pos 的最内层「有意义」调用块。

    跳过 `then/let/apply` 等 Modifier 链组合器：clickable 常写在
    `.then(if (...) { Modifier.clickable(...) })` 里，若停在 `then(...)`，
    就会漏看外层容器的文字与语义声明，把正常容器误判为不可辨识。
    """
    cands = [(o, c) for kind, o, c in pairs if kind == "(" and o < pos < c]
    if not cands:
        return None
    cands.sort(key=lambda t: t[1] - t[0])
    for o, c in cands:
        if call_name_before(src, o) not in CHAIN_CALLS:
            return o, c
    return cands[-1]


def unidentifiable_clickable(path, src):
    """B 类：可点击的最小包围块（含尾部 lambda）内既无文字也无语义声明。"""
    pairs = match_pairs(src)
    out = []
    for m in CLICKABLE_RE.finditer(src):
        pos = m.start()
        best = enclosing_call_block(src, pos, pairs)
        if best is None:
            continue
        start, end = block_with_body(src, best[0], best[1], pairs)
        body = src[start:end]
        if call_name_before(src, best[0]) in SELF_DESCRIBING_CALLS:
            continue
        if any(tok in body for tok in UNDECIDABLE_TOKENS):
            continue
        if any(tok in body for tok in IDENTIFIABLE_TOKENS):
            continue
        line = src[:start].count("\n") + 1
        out.append({
            "file": path,
            "line": line,
            "rule": "B",
            "detail": "可点击节点内无文字且无语义声明",
        })
    return out


def scan():
    a, b = [], []
    for dp, _, fns in os.walk(ROOT):
        for fn in sorted(fns):
            if not fn.endswith(".kt"):
                continue
            p = os.path.join(dp, fn)
            rel = os.path.relpath(p, ".").replace("\\", "/")
            src = open(p, encoding="utf-8", errors="replace").read()
            a += icon_button_violations(rel, src)
            b += unidentifiable_clickable(rel, src)
    return a, b


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--baseline", required=True)
    ap.add_argument("--update-baseline", default=None)
    args = ap.parse_args()

    a, b = scan()
    counts = {"a_icon_button_no_desc": len(a), "b_unidentifiable_clickable": len(b)}
    total = len(a) + len(b)

    print("=== AC2 无障碍语义棘轮 ===")
    print(f"A 类 · 独立图标按钮无描述：{counts['a_icon_button_no_desc']}")
    for v in a:
        print(f"  - {v['file']}:{v['line']}  {v['detail']}")
    print(f"B 类 · 可点击节点不可辨识：{counts['b_unidentifiable_clickable']}")
    for v in b:
        print(f"  - {v['file']}:{v['line']}  {v['detail']}")
    print(f"合计：{total}")

    if args.update_baseline:
        payload = {"updated": __import__("time").strftime("%Y-%m-%d"), **counts, "total": total}
        with open(args.update_baseline, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
        print(f"\n[baseline] 已写入 {args.update_baseline}")
        return 0

    if not os.path.exists(args.baseline):
        print(f"\n[baseline] 缺少基线 {args.baseline}")
        return 1

    base = json.load(open(args.baseline, encoding="utf-8"))
    ok = True
    for k, v in counts.items():
        b0 = base.get(k, 0)
        if v > b0:
            ok = False
            print(f"  FAIL {k}: {b0} → {v}（新增 {v - b0}）")
        elif v < b0:
            print(f"  ↓    {k}: {b0} → {v}（下降，可收紧基线）")
        else:
            print(f"  OK   {k}: {v}")
    print("\n[a11y] " + ("✅ 通过" if ok else "❌ 存在新增违规"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
