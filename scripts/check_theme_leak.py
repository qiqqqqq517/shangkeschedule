#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
A1 主题系统收口 · 静态校验器（P1 立规产物）

背景：走查 A1 的核心结论是「组件层不得写主题分支 —— 每个 `if (theme == X)` 都等于
缺了一个 token 或缺了一个角色」。本脚本把这条铁律变成可执行的检查，避免"感觉改好了"。

四项检查（对应方案 §6 的验收指标）：
  C1 组件层主题泄漏    —— ui/** 排除 ui/theme/** 内不得出现主题身份读取
  C2 色调映射单射性    —— 「语义角色 → 主题色调」不得折叠（同角色撞色）
  C3 token 显式覆盖    —— 每套主题的色彩 token 必须显式给 outline / outlineVariant
  C4 primary 单源      —— 不得用 ColorScheme 覆盖 token 的 primary（打补丁对齐）

用法：
  python scripts/check_theme_leak.py                       # 打印报告
  python scripts/check_theme_leak.py --md out.md            # 同时写出 Markdown 报告
  python scripts/check_theme_leak.py --baseline scripts/theme-leak-baseline.json   # 棘轮门禁：违规数不得高于基线
  python scripts/check_theme_leak.py --update-baseline scripts/theme-leak-baseline.json  # 锁定当前计数为基线
  python scripts/check_theme_leak.py --fail                 # 绝对门禁：只要非零就 exit 1

说明：本脚本是**文本级 lint**，不做完整 Kotlin 解析；注释行会被单独归类为「提示」而不计入违规。
棘轮（ratchet）语义：存量违规允许存在，但**不得增加** —— 这样规则可以立即生效而不冻结开发。
"""
import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UI_DIR = ROOT / "shared/src/commonMain/kotlin/com/shangkeschedule/ui"
THEME_DIR = UI_DIR / "theme"

# ---------------------------------------------------------------- C1 组件层主题泄漏

# A 类：主题**分支**（真违规，验收指标要求为 0）
FORBIDDEN_BRANCH = [
    ("LocalIsSoftTheme", "柔绘专用布尔开关"),
    ("LocalIsGlobalSoftTheme", "柔绘专用布尔开关（全局）"),
    ("isClaudePreset", "书卷专用布尔分支"),
    ("isIosPreset", "通透专用布尔分支"),
    ("isSoftPreset", "柔绘专用布尔分支"),
    ("softMaterial", "把主题身份当参数往下传"),
    ("LocalThemePreset.current ==", "按主题身份做相等判断"),
    ("LocalThemePreset.current !=", "按主题身份做不等判断"),
]

# B 类：主题**身份直引**（提示项，需人工判定是"取数据"还是"漏了的 token"）
FORBIDDEN_IDENTITY = [
    ("LocalThemePreset", "读取主题预设（含 import 已排除）"),
    ("AppThemePreset.", "直接引用主题枚举成员"),
]

COMMENT_PREFIXES = ("//", "*", "/*", "*/")


def iter_kt(base: Path, exclude: Path | None = None):
    for p in sorted(base.rglob("*.kt")):
        if exclude is not None and exclude in p.parents:
            continue
        yield p


def _classify(line: str):
    """返回 ('branch'|'identity'|None, 命中的串, 说明)"""
    for tok, note in FORBIDDEN_BRANCH:
        if tok in line:
            return "branch", tok, note
    for tok, note in FORBIDDEN_IDENTITY:
        if tok in line:
            return "identity", tok, note
    return None, None, None


def c1_theme_leak():
    """返回 (branch, identity, mentions)。import / 注释行单独归类，不计入违规。"""
    branch, identity, mentions = [], [], []
    for path in iter_kt(UI_DIR, exclude=THEME_DIR):
        rel = path.relative_to(ROOT).as_posix()
        for i, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            s = raw.strip()
            if not s or s.startswith("import "):
                continue
            kind, tok, note = _classify(raw)
            if kind is None:
                continue
            rec = (rel, i, tok, note, s)
            if s.startswith(COMMENT_PREFIXES):
                mentions.append(rec)
            elif kind == "branch":
                branch.append(rec)
            else:
                identity.append(rec)
    return branch, identity, mentions


# ---------------------------------------------------------------- C2 色调映射单射性

TONE_FN_RE = re.compile(
    r"fun\s+(?P<recv>\w+)\.(?P<fn>to\w*Tone)\(\)\s*:\s*(?P<enum>\w+)\s*=\s*when\s*\(this\)\s*\{(?P<body>.*?)\n\}",
    re.S,
)
ARROW_RE = re.compile(r"->\s*(?P<enum>\w+)\.(?P<member>\w+)")
ROLE_ENUM_RE = re.compile(r"enum class\s+(?P<name>\w*EntryTone|\w*Role\w*)\s*(?:\([^)]*\))?\s*\{(?P<body>[^}]*)\}")


def c2_tone_injectivity():
    """返回 [(文件, 函数, 角色数, 去重色数, [(角色, 与之撞色的角色, 共用色调)])]"""
    results = []
    for path in iter_kt(UI_DIR):
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(ROOT).as_posix()
        for m in TONE_FN_RE.finditer(text):
            body = m.group("body")
            pairs = [(a.group("enum"), a.group("member")) for a in ARROW_RE.finditer(body)]
            if not pairs:
                continue
            lefts = [x.group("role") for x in re.finditer(r"^\s*(?:\w+\.)?(?P<role>\w+)\s*->", body, re.M)]
            roles = lefts if len(lefts) == len(pairs) else [mbr for _, mbr in pairs]
            used, first, dup = [], {}, []
            for role, (_, member) in zip(roles, pairs):
                used.append(member)
                if member in first:
                    dup.append((role, first[member], member))
                else:
                    first[member] = role
            results.append((rel, m.group("fn"), len(used), len(set(used)), dup))
    return results


def role_enum_size():
    for path in iter_kt(UI_DIR):
        m = ROLE_ENUM_RE.search(path.read_text(encoding="utf-8"))
        if m:
            members = [x.strip() for x in m.group("body").replace("\n", " ").split(",") if x.strip()]
            return m.group("name"), len(members)
    return None, 0


# ---------------------------------------------------------------- C3 token 显式覆盖

TOKEN_CTOR_RE = re.compile(r"(?<![A-Za-z0-9_])AppColorTokens\s*\(")
REQUIRED_EXPLICIT = ("outline", "outlineVariant")


def _call_args(text: str, open_paren_idx: int) -> str:
    """从 '(' 位置开始做括号配对，返回参数文本。"""
    depth, i = 0, open_paren_idx
    while i < len(text):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return text[open_paren_idx + 1 : i]
        i += 1
    return ""


def _enclosing_fn(text: str, idx: int) -> str:
    head = text[:idx]
    ms = list(re.finditer(r"fun\s+(\w+)\s*\(", head))
    return ms[-1].group(1) if ms else "<unknown>"


def c3_token_explicit():
    """只检查"某主题的完整色彩 token 构造器"，返回 [(文件, 构造函数, 缺失的显式字段)]。

    限定在形如 `iosLightAppColorTokens` / `claudeDarkAppColorTokens` 的函数体内，
    避免把 `data class AppColorTokens(...)` 声明、`copy(...)` 等误判为构造点。
    """
    target = re.compile(r"fun\s+((?:ios|claude|soft)?(?:Light|Dark)?AppColorTokens)\s*\(")
    next_fn = re.compile(r"\n(?:@\w+(?:\([^)]*\))?\s*\n)?(?:internal |private |public )?fun ")
    out, seen = [], set()
    for path in iter_kt(UI_DIR):
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(ROOT).as_posix()
        for fm in target.finditer(text):
            fn = fm.group(1)
            # 函数体边界：到下一个顶层 fun 声明为止（避免窗口内串到别的构造函数）
            nxt = next_fn.search(text, fm.end())
            body = text[fm.start() : nxt.start() if nxt else len(text)]
            cm = TOKEN_CTOR_RE.search(body)
            if cm is None:
                continue  # 纯分发函数（只转调 light/dark），不含 token 字面量
            args = _call_args(body, cm.end() - 1)
            missing = [f for f in REQUIRED_EXPLICIT if not re.search(rf"\b{f}\s*=", args)]
            if missing and (rel, fn) not in seen:
                seen.add((rel, fn))
                out.append((rel, fn, missing))
    return out


# ---------------------------------------------------------------- C4 primary 单源

PRIMARY_PATCH_RE = re.compile(r"primary\s*=\s*(?:\w+\.)?colorScheme\.primary")


def c4_primary_patch():
    out = []
    for path in iter_kt(UI_DIR):
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(ROOT).as_posix()
        for i, line in enumerate(text.splitlines(), 1):
            if PRIMARY_PATCH_RE.search(line) and not line.strip().startswith(COMMENT_PREFIXES):
                out.append((rel, i, line.strip()))
    return out


# ---------------------------------------------------------------- 指标与基线（棘轮）

def compute_metrics():
    branch, identity, mentions = c1_theme_leak()
    tone = c2_tone_injectivity()
    role_name, role_n = role_enum_size()
    outline = c3_token_explicit()
    patch = c4_primary_patch()
    tone_bad = [t for t in tone if t[3] < t[2]]
    m = {
        # 硬指标（参与棘轮门禁）
        "c1_branch": len(branch),
        "c2_collapse": len(tone_bad),
        "c3_missing": len(outline),
        "c4_patch": len(patch),
        # 提示项（不参与门禁）
        "c1_identity": len(identity),
        "c1_comment": len(mentions),
    }
    m["total"] = m["c1_branch"] + m["c2_collapse"] + m["c3_missing"] + m["c4_patch"]
    data = {
        "branch": branch, "identity": identity, "mentions": mentions,
        "tone": tone, "role_name": role_name, "role_n": role_n,
        "outline": outline, "patch": patch,
    }
    return m, data


GATED_KEYS = ("c1_branch", "c2_collapse", "c3_missing", "c4_patch", "total")


def load_baseline(path: str):
    """读取基线。返回 (baseline | None, error | None)。

    - 文件不存在 → (None, None)：调用方 fail-open（开发机上可能还没生成）
    - 文件存在但读不出/解析失败 → (None, "原因")：调用方 **fail-closed**，
      因为"门禁悄悄失效"比"门禁吵一次"危险得多（已踩：Windows 编辑器写入 UTF-8 BOM
      会让 json.loads 直接抛错，若当作"缺失"就会被静默跳过）。
    """
    import json
    p = Path(path)
    if not p.exists():
        return None, None
    try:
        # utf-8-sig：容忍 Windows 侧（记事本 / PowerShell）写入的 BOM
        return json.loads(p.read_text(encoding="utf-8-sig")), None
    except Exception as e:  # noqa: BLE001
        return None, str(e)


def save_baseline(path: str, metrics: dict) -> None:
    import json
    from datetime import date
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "_comment": "A1 主题系统收口 · 棘轮基线。只允许下降；上升即为回归，pre-commit 会拦截。",
        "updated": date.today().isoformat(),
        "counts": {k: metrics[k] for k in GATED_KEYS} | {"c1_identity": metrics["c1_identity"]},
    }
    p.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"[baseline] 已写入 {p}", file=sys.stderr)


def check_baseline(metrics: dict, baseline: dict) -> list[str]:
    """返回回归项描述；空列表 = 通过。"""
    base = (baseline or {}).get("counts") or {}
    regressions = []
    for k in GATED_KEYS:
        if k not in base:
            continue
        if metrics[k] > base[k]:
            regressions.append(f"{k}: {base[k]} → {metrics[k]}（+{metrics[k] - base[k]}）")
    return regressions


# ---------------------------------------------------------------- 报告

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--md", help="额外写出 Markdown 报告到该路径")
    ap.add_argument("--fail", action="store_true", help="有违规时以退出码 1 结束（绝对门禁）")
    ap.add_argument("--baseline", help="棘轮基线 JSON；违规数高于基线则 exit 1")
    ap.add_argument("--update-baseline", metavar="PATH", help="把当前计数写为基线并退出")
    ap.add_argument("--quiet", action="store_true", help="只输出摘要/回归信息（门禁用）")
    args = ap.parse_args()

    metrics, d = compute_metrics()

    if args.update_baseline:
        save_baseline(args.update_baseline, metrics)
        return 0

    # 棘轮判定（优先于 --fail：门禁语义是"不得变差"，不是"必须为零"）
    if args.baseline:
        baseline, err = load_baseline(args.baseline)
        if err is not None:
            print(f"[theme-leak] ❌ 基线文件存在但无法解析：{args.baseline}", file=sys.stderr)
            print(f"    原因：{err}", file=sys.stderr)
            print("    修复：python scripts/check_theme_leak.py --update-baseline scripts/theme-leak-baseline.json", file=sys.stderr)
            return 1  # fail-closed：门禁不可静默失效
        if baseline is None:
            print(f"[theme-leak] 基线文件不存在：{args.baseline}（跳过门禁，不阻断）", file=sys.stderr)
            return 0
        if not isinstance(baseline.get("counts"), dict):
            print(f"[theme-leak] ❌ 基线文件缺少 counts 字段：{args.baseline}", file=sys.stderr)
            return 1
        regressions = check_baseline(metrics, baseline)
        if regressions:
            print("[theme-leak] ❌ 主题系统违规数较基线增加：", file=sys.stderr)
            for r in regressions:
                print(f"    - {r}", file=sys.stderr)
            print("    查看详情：python scripts/check_theme_leak.py", file=sys.stderr)
            print("    确属预期：python scripts/check_theme_leak.py --update-baseline scripts/theme-leak-baseline.json", file=sys.stderr)
            return 1
        improved = [
            f"{k}: {baseline['counts'][k]} → {metrics[k]}"
            for k in GATED_KEYS
            if k in baseline.get("counts", {}) and metrics[k] < baseline["counts"][k]
        ]
        if args.quiet:
            msg = f"[theme-leak] ✅ 通过（硬指标 {metrics['total']}，基线 {baseline['counts'].get('total')}）"
            if improved:
                msg += "；已下降：" + "，".join(improved) + " —— 建议更新基线锁定收益"
            print(msg, file=sys.stderr)
            return 0

    if args.quiet and not args.md:
        print(f"[theme-leak] 硬指标 {metrics['total']}", file=sys.stderr)
        return 0

    branch = d["branch"]; identity = d["identity"]; mentions = d["mentions"]
    tone = d["tone"]; role_name = d["role_name"]; role_n = d["role_n"]
    outline = d["outline"]; patch = d["patch"]

    by_file: dict[str, int] = {}
    for rel, *_ in branch:
        by_file[rel] = by_file.get(rel, 0) + 1

    tone_bad = [t for t in tone if t[3] < t[2]]
    total_bad = metrics["total"]

    L: list[str] = []
    p = L.append
    p("# A1 主题系统收口 · 静态校验报告")
    p("")
    p(f"- 扫描根：`{UI_DIR.relative_to(ROOT).as_posix()}`（C1 排除 `ui/theme/**`，已排除 import 行）")
    p(f"- **硬指标违规合计：{total_bad}**（C1 分支 + C2 折叠 + C3 缺失 + C4 补丁）")
    p("")

    p("## C1 组件层主题泄漏")
    p("")
    p(f"**A 类 · 主题分支（硬指标，目标 0）：{len(branch)} 处 / {len(by_file)} 个文件**")
    p("")
    if by_file:
        p("| 文件 | 处数 |")
        p("| --- | --- |")
        for rel, n in sorted(by_file.items(), key=lambda kv: -kv[1]):
            p(f"| `{rel}` | {n} |")
        p("")
        p("| 文件:行 | 命中 | 说明 | 代码 |")
        p("| --- | --- | --- | --- |")
        for rel, line, tok, note, src in branch:
            safe = src.replace("|", "\\|")
            p(f"| `{rel}:{line}` | `{tok}` | {note} | `{safe[:70]}` |")
    else:
        p("✅ 无违规。")
    p("")
    p(f"**B 类 · 主题身份直引（提示项，需人工判定）：{len(identity)} 处**")
    p("")
    if identity:
        p("| 文件:行 | 命中 | 代码 |")
        p("| --- | --- | --- |")
        for rel, line, tok, _note, src in identity:
            safe = src.replace("|", "\\|")
            p(f"| `{rel}:{line}` | `{tok}` | `{safe[:70]}` |")
    else:
        p("无。")
    p("")
    if mentions:
        p(f"（另有注释/文档提及 {len(mentions)} 处，不计入。）")
        p("")

    p("## C2 色调映射单射性（角色 → 色调不得折叠）")
    p("")
    p(f"语义角色枚举：`{role_name}`（{role_n} 个角色）")
    p("")
    p("| 文件 | 映射函数 | 角色数 | 去重后色调数 | 撞色 |")
    p("| --- | --- | --- | --- | --- |")
    for rel, fn, total, uniq, dup in tone:
        flag = "❌" if uniq < total else "✅"
        if dup:
            dup_txt = "、".join(f"`{r}` 与 `{first}` 同为 `{mbr}`" for r, first, mbr in dup)
        else:
            dup_txt = "—"
        p(f"| `{rel}` | `{fn}` | {total} | {uniq} {flag} | {dup_txt} |")
    p("")

    p("## C3 token 显式覆盖（不得依赖 data class 默认值）")
    p("")
    if outline:
        p("| 文件 | 构造函数 | 缺失显式字段 |")
        p("| --- | --- | --- |")
        for rel, fn, missing in outline:
            p(f"| `{rel}` | `{fn}` | {', '.join('`'+m+'`' for m in missing)} |")
    else:
        p("✅ 全部显式覆盖。")
    p("")

    p("## C4 `primary` 单源（不得用 ColorScheme 覆盖 token）")
    p("")
    if patch:
        for rel, line, src in patch:
            p(f"- ❌ `{rel}:{line}` — `{src}`")
    else:
        p("✅ 无补丁。")
    p("")

    text = "\n".join(L)
    print(text)

    if args.md:
        out = Path(args.md)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text + "\n", encoding="utf-8")
        print(f"\n[saved] {out}", file=sys.stderr)

    if args.fail and total_bad:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
