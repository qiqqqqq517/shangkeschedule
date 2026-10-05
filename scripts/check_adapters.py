#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""教务适配脚本静态体检 + 双落点一致性校验（LF 归一化）。

用法：
    python scripts/check_adapters.py
    python scripts/check_adapters.py --strict          # WARN 也判失败
    python scripts/check_adapters.py --skip-node       # 跳过 node --check 语法校验
    python scripts/check_adapters.py --private-repo PATH
    python scripts/check_adapters.py --json out.json   # 附带机器可读报告

为什么需要这个脚本
------------------
1. 适配标准流程 SOP 4.4 要求主仓库 `shared/assets/offline_repo/schools/resources`
   与私有适配仓库 `.adapter_private/adapters` 内容一致。但两侧工作副本的行尾
   习惯不同（一侧 CRLF、一侧 LF），按原始字节比较会把同一份文件误报成不一致
   （历史案例：DLUT/dlut.js —— 493 行 CRLF 22681 B vs 493 行 LF 22188 B，
   LF 归一化后内容完全相同）。本脚本改用 **LF 归一化后的 SHA-256**，
   与私有仓库 `school_index.pb version_id switched to LF-normalized content hash`
   的口径保持一致。
2. 195 个适配脚本靠人工 review 不现实：入口契约缺失、未处理的 Promise 拒绝、
   裸 alert/prompt 阻塞 WebView、危险 API、语法错误都要能一条命令查出来。

关于裸 alert 的判据
-------------------
绝大多数适配脚本里的 `alert(` 是**浏览器调试兜底**，写在
`typeof window.shangkeBridgePromise === 'undefined'` 之类的 else 分支里，
应用内（JS_BRIDGE_INIT 已注入）永不执行。因此本脚本只把
「附近 GUARD_WINDOW 行内没有 shangkeBridge / isApp / hasToast 保护」的
裸 alert/confirm/prompt 记为 WARN，避免刷屏假阳性；文件自己定义了同名
`function alert(...)`（业务封装）的也不计。

关于孤儿适配器
--------------
判断依据是内置索引 `shared/assets/offline_repo/index/school_index.pb` 的原始字节
（直接扫 UTF-8 文本，**不**依赖 `tools/school_index_pb2.py` —— `tools/` 是仓库
红线目录、不入库，校验脚本不能依赖它）。`_common/`、`_timetable_parsers/`
是被复用的共享库，天然不会被索引单独引用，已排除。

退出码：0 = 通过；1 = 存在 ERROR（--strict 下含 WARN）。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path

# 输出编码固定为 UTF-8：报告含中文，Windows 控制台默认 GBK 会让 print() 按 GBK 编码
# 写出、并让下游（pre-commit 捕获、核验脚本、CI 日志解析）按 UTF-8 读时全变成 U+FFFD，
# 于是「按消息文本判定门禁结论」的一类消费全部失效（实测：断言『危险 API』『基线不存在』
# 在 UTF-8 侧恒 False）。固定编码后，本脚本输出可被机器稳定消费。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parent.parent
PUBLIC_DIR = ROOT / "shared" / "assets" / "offline_repo" / "schools" / "resources"
DEFAULT_PRIVATE_DIR = ROOT / ".adapter_private" / "adapters"
# 内置学校索引（protobuf）。孤儿检测直接扫它的原始字节，避免依赖 tools/ 下的 pb2 生成器。
SCHOOL_INDEX_PB = ROOT / "shared" / "assets" / "offline_repo" / "index" / "school_index.pb"
# `_common/`、`_timetable_parsers/` 是被复用的共享库，不是学校导入入口脚本
SHARED_LIB_PREFIX = "_"


class NodeUnavailable(RuntimeError):
    """node 不可用：语法校验无法进行，不得当作「语法全对」。"""


def load_dangerous_baseline(path: str):
    """读取危险 API 棘轮基线。返回 (count | None, error | None)。

    与 check_theme_leak.py 同契约：不存在与解析失败都 fail-closed（调用方判 ERROR），
    绝不静默跳过 —— 「门禁悄悄失效」比「门禁吵一次」危险得多。
    """
    p = Path(path)
    if not p.exists():
        return None, None
    try:
        # utf-8-sig 容忍 Windows 侧写入的 BOM
        data = json.loads(p.read_text(encoding="utf-8-sig"))
    except Exception as e:  # noqa: BLE001
        return None, str(e)
    n = data.get("dangerous_api_count")
    if not isinstance(n, int):
        return None, "缺少整数字段 dangerous_api_count"
    return n, None

ENTRY_EXPLICIT = "shangkeImportEntry"
ENTRY_CONVENTIONAL = (
    "startImport",
    "runImport",
    "scheduleImport",
    "importCourses",
    "importSchedule",
)
# 裸原生对话框：只在「没有被 bridge 分支保护」时才算问题
NATIVE_DIALOGS = ("alert", "confirm", "prompt")
GUARD_MARKERS = ("shangkeBridge", "isApp", "hasToast")
GUARD_WINDOW = 20

NETWORK_HINTS = (re.compile(r"\bfetch\s*\("), re.compile(r"\bXMLHttpRequest\b"))
UNHANDLED_HINT = re.compile(r"\.catch\s*\(")

DANGEROUS = {
    "eval(": re.compile(r"(^|[^.\w])eval\s*\("),
    "new Function(": re.compile(r"\bnew\s+Function\s*\("),
    "document.write(": re.compile(r"document\.write\s*\("),
    "innerHTML=": re.compile(r"\.innerHTML\s*="),
}


def read_lf(path: Path) -> str:
    """按 UTF-8 读取并把行尾统一成 LF。"""
    raw = path.read_bytes().decode("utf-8", errors="replace")
    return raw.replace("\r\n", "\n").replace("\r", "\n")


def sha256_lf(path: Path) -> str:
    return hashlib.sha256(read_lf(path).encode("utf-8")).hexdigest()


def is_shared_lib(rel: str) -> bool:
    return rel.split("/", 1)[0].startswith(SHARED_LIB_PREFIX)


def strip_block_comments(text: str) -> str:
    """去掉 /* ... */ 块注释（保留换行以便行号对齐）。

    P2-23：**不能用裸正则 `re.sub(r"/\\*.*?\\*/", ...)`**。JS 的字符串字面量里
    完全可以出现 `/*`（例如 `const re = "a/*b";` 或 `url = "http://x/*y"`），
    裸正则会把从这里开始到**下一个** `*/`（可能在几十行后）之间的**真实代码**
    整段当注释吞掉 ⇒ 该段内的危险 API / 裸对话框全部漏检（fail-open）。

    正确做法：按字符扫描，跟踪 '  "  ` 三种引号与转义，
    仅在「不在字符串里」时才把 `/*` 视为注释开始。块注释同样保留换行以对齐行号。
    """
    out: list[str] = []
    i = 0
    n = len(text)
    quote: str | None = None
    while i < n:
        ch = text[i]
        if quote is not None:
            # 字符串内部：原样保留，只跟踪转义与闭合
            if ch == "\\" and i + 1 < n:
                out.append(text[i:i + 2])
                i += 2
                continue
            if ch == quote:
                quote = None
            out.append(ch)
            i += 1
            continue
        if ch in ("'", '"', "`"):
            quote = ch
            out.append(ch)
            i += 1
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "*":
            # 块注释开始：吞到匹配的 */（JS 不支持嵌套块注释），换行原样保留
            j = text.find("*/", i + 2)
            if j == -1:
                seg = text[i:]
                i = n
            else:
                seg = text[i:j + 2]
                i = j + 2
            out.append("".join("\n" if c == "\n" else " " for c in seg))
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def strip_line_comment(line: str) -> str:
    """去掉行内 // 注释（引号内的 // 不算）。"""
    quote: str | None = None
    i = 0
    while i < len(line):
        ch = line[i]
        if quote is not None:
            if ch == "\\":
                i += 2
                continue
            if ch == quote:
                quote = None
        elif ch in ("'", '"', "`"):
            quote = ch
        elif ch == "/" and i + 1 < len(line) and line[i + 1] == "/":
            return line[:i]
        i += 1
    return line


def dialog_definition_line(text: str, name: str) -> int | None:
    """返回文件内 `function <name>(` 定义的 **0 基行号**；无则 None。"""
    m = re.search(r"(?m)^[^\n]*\bfunction\s+%s\s*\(" % re.escape(name), text)
    if m is None:
        return None
    return text[: m.start()].count("\n")


def find_unguarded_dialogs(lines: list[str], text: str) -> list[tuple[int, str]]:
    """返回 (行号, 对话框名) —— 只含未被 bridge 分支保护的裸调用。

    P2-24：此前只要文件里出现 `function alert(...)` 就 **continue 掉整文件**，
    抑制范围过宽 —— 该文件内**其它**未走封装的裸调用（例如定义之前的调用、
    或定义在某个 if 分支里而全局仍是原生）会一并豁免。同时「一行注释即可
    关闭检测」的弱点也在此收口：注释行不参与判定。

    现改为**逐调用点**判定：仅当该调用出现在同名函数定义**之后**时才视为
    走业务封装（定义之前调用的是原生对话框）；文件内无同名定义则一律检查。
    """
    found: list[tuple[int, str]] = []
    for name in NATIVE_DIALOGS:
        def_line = dialog_definition_line(text, name)
        pattern = re.compile(r"(^|[^.\w$])%s\s*\(" % re.escape(name))
        for index, raw_line in enumerate(lines):
            # 注释行不参与判定（避免「一行注释关掉检测」）
            if raw_line.strip().startswith(("//", "*", "/*")):
                continue
            line = strip_line_comment(raw_line)
            if not pattern.search(line):
                continue
            # 调用点是否落在同名函数定义的**包裹范围内**：
            #   · index == def_line —— 就是定义行自身（含 `alert(` 字面量），排除；
            #   · index  > def_line —— 定义之后的调用，视为走业务封装，排除；
            #   · index  < def_line —— 定义**之前**的调用，走的仍是 WebView 原生，**保留**。
            # （若整文件都当封装跳过，就会漏掉「定义之前」的裸调用 —— 即 P2-24 的过宽抑制。）
            if def_line is not None and index >= def_line:
                continue
            lo = max(0, index - GUARD_WINDOW)
            window = "\n".join(lines[lo:index])
            if any(marker in window for marker in GUARD_MARKERS):
                continue
            found.append((index + 1, name))
    return found


def node_syntax_error(path: Path) -> str | None:
    """返回语法错误描述；None = 语法 OK。

    注意：node 不可用（未安装 / 不在 PATH）**不是**「语法 OK」。曾把 FileNotFoundError
    静默 return None，与「全对」逐字节同值 ⇒ 语法校验整类 fail-open。
    现在抛 NodeUnavailable，由调用方显式处置（默认记 ERROR）。
    """
    try:
        proc = subprocess.run(
            ["node", "--check", str(path)],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except FileNotFoundError as e:
        raise NodeUnavailable("node 不在 PATH 或未安装") from e
    if proc.returncode == 0:
        return None
    detail = (proc.stderr or proc.stdout or "").strip().splitlines()
    return detail[0] if detail else "node --check failed"


def collect_scripts(base: Path) -> dict[str, Path]:
    return {
        p.relative_to(base).as_posix(): p
        for p in sorted(base.rglob("*.js"))
        if p.is_file()
    }


def read_index_reference_text(path: Path) -> str:
    """把内置索引读成一段文本，用于「脚本名/资源目录是否被引用」的包含判断。"""
    if not path.is_file():
        return ""
    return path.read_bytes().decode("utf-8", errors="ignore")


def folder_referenced(folder: str, index_text: str) -> bool:
    if not folder:
        return False
    return re.search(r"(?<![A-Za-z0-9_])%s(?![A-Za-z0-9_])" % re.escape(folder), index_text) is not None


def main() -> int:
    parser = argparse.ArgumentParser(description="教务适配脚本静态体检")
    parser.add_argument("--strict", action="store_true", help="WARN 也视为失败")
    parser.add_argument("--skip-node", action="store_true", help="跳过 node --check 语法校验")
    parser.add_argument("--private-repo", default=str(DEFAULT_PRIVATE_DIR), help="私有适配仓库工作副本目录")
    parser.add_argument(
        "--require-private",
        action="store_true",
        help="私有仓库工作副本必须存在；缺失即为 ERROR（不再静默跳过双落点校验）",
    )
    parser.add_argument(
        "--allow-node-missing",
        action="store_true",
        help="node 不可用时只记 WARN 不判失败（默认 ERROR，因为语法校验会整体失效）",
    )
    parser.add_argument(
        "--dangerous-baseline",
        default=None,
        metavar="PATH",
        help="危险 API 棘轮基线 JSON；命中数高于基线即 ERROR（不给则命中即 ERROR）",
    )
    parser.add_argument(
        "--update-dangerous-baseline",
        default=None,
        metavar="PATH",
        help="把当前危险 API 命中数写为基线并退出",
    )
    parser.add_argument("--json", dest="json_out", default=None, help="把机器可读报告写到该文件")
    parser.add_argument("--max-list", type=int, default=20, help="每类问题最多列多少条")
    args = parser.parse_args()

    errors: list[str] = []
    warns: list[str] = []

    if not PUBLIC_DIR.is_dir():
        print(f"ERROR: 主仓库适配目录不存在: {PUBLIC_DIR}")
        return 1

    public_scripts = collect_scripts(PUBLIC_DIR)

    # ---- 1. 单文件体检 ----
    entry_explicit = 0
    entry_conventional = 0
    entry_none: list[str] = []
    no_catch: list[str] = []
    native_dialog_hits: list[str] = []
    dangerous_hits: list[str] = []
    missing_bridge: list[str] = []
    syntax_hits: list[str] = []
    node_unavailable: list[str] = []

    for rel, path in public_scripts.items():
        text = read_lf(path)
        stripped = strip_block_comments(text)
        lines = stripped.split("\n")

        if ENTRY_EXPLICIT in text:
            entry_explicit += 1
        elif any(re.search(r"\b%s\b" % name, text) for name in ENTRY_CONVENTIONAL):
            entry_conventional += 1
        else:
            entry_none.append(rel)

        has_network = any(p.search(text) for p in NETWORK_HINTS)
        if has_network and not UNHANDLED_HINT.search(text):
            no_catch.append(rel)

        for line_no, name in find_unguarded_dialogs(lines, text):
            native_dialog_hits.append(f"{rel}:{line_no} 裸 {name}()")

        for label, pattern in DANGEROUS.items():
            if pattern.search(text):
                dangerous_hits.append(f"{rel} 含 {label}")

        if "saveImportedCourses" not in text and not is_shared_lib(rel):
            missing_bridge.append(f"{rel} 未调用 saveImportedCourses")

        if not args.skip_node:
            try:
                detail = node_syntax_error(path)
            except NodeUnavailable as e:
                node_unavailable.append(str(e))
                detail = None
            if detail:
                syntax_hits.append(f"{rel} {detail}")

    if args.update_dangerous_baseline:
        from datetime import date
        _payload = {
            "_comment": "危险 API 棘轮基线（适配脚本）。只允许下降；上升即为回归。",
            "updated": date.today().isoformat(),
            "dangerous_api_count": len(dangerous_hits),
            "detail": dangerous_hits,
        }
        _bp = Path(args.update_dangerous_baseline)
        _bp.parent.mkdir(parents=True, exist_ok=True)
        _bp.write_text(json.dumps(_payload, ensure_ascii=False, indent=2) + "\n",
                       encoding="utf-8")
        print(f"[dangerous-baseline] 已写入 {_bp}（{len(dangerous_hits)} 处）")
        return 0

    # 语法错误属硬问题；危险 API / 危险对话框 / 入口缺失属需要人看的发现。
    #
    # 历史缺陷：dangerous_hits 与 no_catch 只被 append 进数据字典并 print，
    # **从未接进 errors/warns** ⇒ 危险 API 一类零否决权（P1-20 / 「缺 1」）。
    #
    # 处置采用**棘轮**（与 check_theme_leak.py / check_a11y.py 同一套打法）：
    # 存量 26 处危险 API 是历史存量，一次性判 ERROR 会让每次适配脚本提交都变红，
    # 进而被 --no-verify 常态化绕过 —— 那等于再造一个「噪音门禁」。
    # 故：以 --dangerous-baseline 记存量，**只允许下降、不得上升**；
    # 未给基线时退化为「有命中即 ERROR」（严格模式，供 runbook / CI 使用）。
    errors.extend(syntax_hits)
    warns.extend(missing_bridge)
    warns.extend(native_dialog_hits)
    warns.extend(no_catch)

    dangerous_regressions: list[str] = []
    if dangerous_hits:
        if args.dangerous_baseline:
            base_n, base_err = load_dangerous_baseline(args.dangerous_baseline)
            if base_err is not None:
                errors.append(
                    f"危险 API 基线存在但无法解析：{args.dangerous_baseline}（{base_err}）")
            elif base_n is None:
                errors.append(
                    f"危险 API 基线不存在：{args.dangerous_baseline}（门禁无基准可依）")
            elif len(dangerous_hits) > base_n:
                dangerous_regressions = [
                    f"危险 API {base_n} → {len(dangerous_hits)}（+{len(dangerous_hits) - base_n}）"]
                errors.append("危险 API 命中数较基线增加：" + "；".join(dangerous_regressions))
        else:
            errors.extend(dangerous_hits)

    if node_unavailable:
        # node 缺失 ⇒ 语法校验整体没跑。默认 ERROR（fail-closed）；
        # 显式 --skip-node 是「我知道我不跑」，--allow-node-missing 是「跑不了但接受」。
        _msg = "node 不可用，语法校验未执行（{} 例，首例：{}）".format(
            len(node_unavailable), node_unavailable[0])
        if args.allow_node_missing:
            warns.append(_msg)
        else:
            errors.append(_msg)

    # ---- 2. 双落点一致性（LF 归一化哈希）----
    private_dir = Path(args.private_repo)
    dual: dict[str, object] = {}
    dual_skipped = False
    if private_dir.is_dir():
        private_scripts = collect_scripts(private_dir)
        only_public = sorted(set(public_scripts) - set(private_scripts))
        only_private = sorted(set(private_scripts) - set(public_scripts))
        mismatched = [
            rel
            for rel in sorted(set(public_scripts) & set(private_scripts))
            if sha256_lf(public_scripts[rel]) != sha256_lf(private_scripts[rel])
        ]
        # 原始字节不同、但 LF 归一化后一致 —— 只是行尾风格差异，两侧工作副本的
        # 行尾习惯本就不同，不算问题，但要看得见（INFO，不计入 ERROR）。
        raw_only = [
            rel
            for rel in sorted(set(public_scripts) & set(private_scripts))
            if sha256_lf(public_scripts[rel]) == sha256_lf(private_scripts[rel])
            and hashlib.sha256(public_scripts[rel].read_bytes()).hexdigest()
            != hashlib.sha256(private_scripts[rel].read_bytes()).hexdigest()
        ]
        for rel in only_public:
            errors.append(f"双落点缺失：{rel} 只在主仓库")
        for rel in only_private:
            errors.append(f"双落点缺失：{rel} 只在私有仓库")
        for rel in mismatched:
            errors.append(f"双落点内容不一致（已按 LF 归一化）：{rel}")
        dual = {
            "public_count": len(public_scripts),
            "private_count": len(private_scripts),
            "only_public": only_public,
            "only_private": only_private,
            "mismatched": mismatched,
            "raw_only": raw_only,
        }
    else:
        # 私有仓库工作副本缺失 ⇒ 双落点校验（2026-10-04 OTA 事故的核心防线）**整体失效**。
        #
        # 为何不能无条件判 ERROR：新 worktree / 未 clone 私有仓库的贡献者本来就拿不到它，
        # 一律拦截会让门禁变成噪音（噪音门禁 = 下一个恒绿门禁）。
        # 但也不能像从前那样只记 WARN —— 默认模式下 warns 不影响退出码，
        # 于是「最需要这条防线的场景」恰恰是它静默失效的场景。
        #
        # 处置：默认仍为 WARN，但**提升可见性**；当显式声明"本环境应当有它"
        # （--require-private 或环境变量 ADAPTER_REQUIRE_PRIVATE=1）时判 ERROR。
        # 发版 runbook / CI / 主工作区应带 --require-private。
        msg = f"私有仓库工作副本不存在，跳过双落点校验: {private_dir}"
        if args.require_private or os.environ.get("ADAPTER_REQUIRE_PRIVATE") == "1":
            errors.append(f"{msg}（已声明 --require-private，缺失即为失败）")
        else:
            warns.append(msg)
        dual_skipped = True

    # ---- 3. 孤儿适配器（未被内置索引 school_index.pb 引用）----
    orphans: list[str] = []
    index_text = read_index_reference_text(SCHOOL_INDEX_PB)
    if index_text:
        for rel in public_scripts:
            if is_shared_lib(rel):
                continue
            name = Path(rel).name
            folder = Path(rel).parent.name
            if name in index_text:
                continue
            if folder_referenced(folder, index_text):
                continue
            orphans.append(rel)
        if orphans:
            warns.append(f"{len(orphans)} 个适配脚本未被内置索引引用（历史遗留或纯 OTA 脚本，需人工确认）")
    else:
        warns.append(f"内置学校索引不存在，跳过孤儿检测: {SCHOOL_INDEX_PB}")

    # ---- 输出 ----
    def dump(title: str, items: list[str], limit: int) -> None:
        if not items:
            return
        print(f"  {title}：{len(items)}")
        for line in items[:limit]:
            print(f"    - {line}")
        if len(items) > limit:
            print(f"    ... 另有 {len(items) - limit} 条")

    print("== 教务适配脚本体检 ==")
    print(f"主仓库脚本: {len(public_scripts)}  ({PUBLIC_DIR.relative_to(ROOT).as_posix()})")
    if dual:
        print(f"私有仓库脚本: {dual['private_count']}  ({private_dir})")
    print(
        "入口契约: 显式 shangkeImportEntry={0}  仅约定名={1}  两者皆无={2}".format(
            entry_explicit, entry_conventional, len(entry_none)
        )
    )
    if dual:
        print(
            "双落点: only-public={0} only-private={1} 内容不一致={2} 仅行尾差异={3}（INFO）".format(
                len(dual["only_public"]),
                len(dual["only_private"]),
                len(dual["mismatched"]),
                len(dual["raw_only"]),
            )
        )
    print(f"有网络请求但无 .catch: {len(no_catch)} 个（由 App 侧全局错误上报兜底）")
    print(f"裸原生对话框（未有 bridge 分支保护）: {len(native_dialog_hits)} 处")
    print(f"危险 API: {len(dangerous_hits)} 处")
    print(f"孤儿适配脚本: {len(orphans)} 个")
    print(f"ERROR: {len(errors)}   WARN: {len(warns)}")

    if errors:
        print("\n[ERROR]")
        dump("错误", errors, args.max_list)
    if warns:
        print("\n[WARN]")
        dump("警告", warns, args.max_list)

    report = {
        "public_count": len(public_scripts),
        "entry_explicit": entry_explicit,
        "entry_conventional": entry_conventional,
        "entry_none": entry_none,
        "network_without_catch": no_catch,
        "unguarded_native_dialogs": native_dialog_hits,
        "dangerous_api": dangerous_hits,
        "orphans": orphans,
        "dual_location": dual,
        "dual_skipped": dual_skipped,
        "node_unavailable": node_unavailable,
        "dangerous_api_count": len(dangerous_hits),
        "dangerous_regressions": dangerous_regressions,
        "errors": errors,
        "warnings": warns,
        "strict": args.strict,
        "allow_node_missing": args.allow_node_missing,
    }
    if args.json_out:
        # P1-6：此前直接 write_text ⇒ 目标**目录**不存在时，会在跑完全部检查后
        # 才崩溃（白跑一趟且退出码语义变成「脚本坏了」而非「有 ERROR」）。
        # 现在先建目录并给出明确错误。
        try:
            _jp = Path(args.json_out)
            _jp.parent.mkdir(parents=True, exist_ok=True)
            _jp.write_text(json.dumps(report, ensure_ascii=False, indent=2),
                           encoding="utf-8")
        except OSError as e:
            print(f"[check-adapters] ❌ 无法写出报告到 {args.json_out}：{e}", file=sys.stderr)
            return 2
        print(f"\n报告已写入 {args.json_out}")

    if errors or (args.strict and warns):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
