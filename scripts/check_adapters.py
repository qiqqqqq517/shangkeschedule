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
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PUBLIC_DIR = ROOT / "shared" / "assets" / "offline_repo" / "schools" / "resources"
DEFAULT_PRIVATE_DIR = ROOT / ".adapter_private" / "adapters"
# 内置学校索引（protobuf）。孤儿检测直接扫它的原始字节，避免依赖 tools/ 下的 pb2 生成器。
SCHOOL_INDEX_PB = ROOT / "shared" / "assets" / "offline_repo" / "index" / "school_index.pb"
# `_common/`、`_timetable_parsers/` 是被复用的共享库，不是学校导入入口脚本
SHARED_LIB_PREFIX = "_"

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
    """去掉 /* ... */ 块注释（保留换行以便行号对齐）。"""

    def _blank(match: re.Match[str]) -> str:
        return "".join("\n" if ch == "\n" else " " for ch in match.group(0))

    return re.sub(r"/\*.*?\*/", _blank, text, flags=re.S)


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


def defines_dialog_function(text: str, name: str) -> bool:
    return re.search(r"\bfunction\s+%s\s*\(" % re.escape(name), text) is not None


def find_unguarded_dialogs(lines: list[str], text: str) -> list[tuple[int, str]]:
    """返回 (行号, 对话框名) —— 只含未被 bridge 分支保护的裸调用。"""
    found: list[tuple[int, str]] = []
    for name in NATIVE_DIALOGS:
        # 文件自己定义了同名函数（形如 function alert(title, msg, btn)）说明调用是
        # 走业务封装的，不是 WebView 原生对话框。
        if defines_dialog_function(text, name):
            continue
        pattern = re.compile(r"(^|[^.\w$])%s\s*\(" % re.escape(name))
        for index, raw_line in enumerate(lines):
            line = strip_line_comment(raw_line)
            if not pattern.search(line):
                continue
            lo = max(0, index - GUARD_WINDOW)
            window = "\n".join(lines[lo:index])
            if any(marker in window for marker in GUARD_MARKERS):
                continue
            found.append((index + 1, name))
    return found


def node_syntax_error(path: Path) -> str | None:
    try:
        proc = subprocess.run(
            ["node", "--check", str(path)],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except FileNotFoundError:
        return None
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
            detail = node_syntax_error(path)
            if detail:
                syntax_hits.append(f"{rel} {detail}")

    # 语法错误属硬问题；其余为提示
    errors.extend(syntax_hits)
    warns.extend(missing_bridge)
    warns.extend(native_dialog_hits)

    # ---- 2. 双落点一致性（LF 归一化哈希）----
    private_dir = Path(args.private_repo)
    dual: dict[str, object] = {}
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
        warns.append(f"私有仓库工作副本不存在，跳过双落点校验: {private_dir}")

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
        "errors": errors,
        "warnings": warns,
        "strict": args.strict,
    }
    if args.json_out:
        Path(args.json_out).write_text(
            json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(f"\n报告已写入 {args.json_out}")

    if errors or (args.strict and warns):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
