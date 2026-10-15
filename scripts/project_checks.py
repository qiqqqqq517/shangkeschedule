#!/usr/bin/env python3
"""统一静态门禁：按实际输入选择检查，详细输出留本地，不缓存通过结果。"""
import argparse
import ast
from datetime import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit


CHECKS = {
    "theme": ["scripts/check_theme_leak.py", "--baseline", "scripts/theme-leak-baseline.json", "--quiet"],
    "a11y": ["scripts/check_a11y.py", "--baseline", "scripts/a11y-baseline.json"],
    "font": ["scripts/check_font_scale.py", "--baseline", "scripts/font-scale-baseline.json"],
    "widget": ["scripts/check_widget_contrast.py", "--quiet"],
    "adapters": ["scripts/check_adapters.py", "--dangerous-baseline", "scripts/adapters-dangerous-baseline.json"],
    "version": ["scripts/check_version_sync.py", "--quiet"],
}
LOCAL_DIRS = (".trae/", ".zcode/", ".claude/", ".workbuddy/", ".commandcode/", "build_qa/", "tools/")
LOCAL_NAMES = {"工作日志.md", "keystore.properties", "local.properties", "google-services.json", "adapter_secrets.properties"}
SECRET_PATTERNS = [(name, re.compile(pattern)) for name, pattern in [
    ("private-key-block", r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    ("aws-akid", r"\bAKIA[0-9A-Z]{16}\b"),
    ("github-pat", r"\bgh[pousr]_[A-Za-z0-9]{36,}\b"),
    ("google-api-key", r"\bAIza[0-9A-Za-z_\-]{35}\b"),
    ("slack-token", r"\bxox[abprs]-[0-9A-Za-z\-]{10,}\b"),
    ("openai-key", r"\bsk-[A-Za-z0-9]{32,}\b"),
    ("cf-app-secret", r"appSecret\s*[=:]\s*[\"'][A-Za-z0-9_\-]{16,}[\"']"),
    ("generic-secret", r"(?i)\b(secret|api[_-]?key|token|password)\b\s*[=:]\s*[\"'][A-Za-z0-9+/_\-]{24,}[\"']"),
]]


def git(root, *args, check=True):
    result = subprocess.run(["git", "-c", "core.quotepath=false", *args], cwd=root,
                            capture_output=True, encoding="utf-8", errors="replace")
    if check and result.returncode:
        raise RuntimeError("Git 读取失败，停止检查：" + " ".join(args[:2]))
    return result


def paths_from_diff(root, *args):
    return [p for p in git(root, "diff", *args, "--name-only", "-z", "--no-renames", "--").stdout.split("\0") if p]


def affected(path):
    result = set()
    if path in ("scripts/project_checks.py", ".githooks/pre-commit"):
        return set(CHECKS)
    for name, command in CHECKS.items():
        if path in command:
            result.add(name)
    if path.startswith("shared/src/commonMain/kotlin/") and path.endswith(".kt"):
        result.update(("a11y", "font"))
        if path.startswith("shared/src/commonMain/kotlin/com/shangkeschedule/ui/"):
            result.add("theme")
    if re.fullmatch(r"androidApp/src/main/res/values[^/]*/colors\.xml", path):
        result.add("widget")
    if path.startswith(("shared/assets/offline_repo/", "adapter-worker/")):
        result.add("adapters")
    if path in {"androidApp/build.gradle.kts", "website/assets/js/site.js", "website/version.json", "README.md", "CHANGELOG.md", "scripts/sync_web_version_fallbacks.py"} or re.fullmatch(r"website/[^/]+\.html", path):
        result.add("version")
    return result


def select_checks(paths, full=False):
    selected = set(CHECKS) if full else set().union(*(affected(p) for p in paths))
    result = ["security", "whitespace"] if paths or full else []
    if any(p.endswith(".md") for p in paths):
        result.append("docs")
    if any(p.startswith("scripts/") and p.endswith((".py", ".cjs", ".mjs", ".js")) for p in paths):
        result.append("syntax")
    return result + [name for name in CHECKS if name in selected]


def read_content(root, path, staged):
    if staged:
        result = git(root, "show", ":" + path, check=False)
        return result.stdout if result.returncode == 0 else None
    target = root / path
    return target.read_text(encoding="utf-8-sig") if target.is_file() else None


def security(root, paths, diff_args):
    errors = []
    for path in paths:
        if path.startswith(LOCAL_DIRS) or Path(path).name in LOCAL_NAMES or path.lower().endswith((".jks", ".keystore")):
            # 删除已跟踪的红线文件应允许；仅拦截待提交的新内容。
            exists = git(root, "cat-file", "-e", ":" + path, check=False).returncode == 0 if "--cached" in diff_args else (root / path).exists()
            if exists:
                errors.append("禁止入库：" + path)
    patch = git(root, "diff", *diff_args, "--unified=0", "--no-renames", "--no-ext-diff", "--no-color", "--", *paths).stdout if paths else ""
    current, line = None, 0
    for raw in patch.splitlines():
        if raw.startswith("+++ "):
            current = raw[4:]
            if current.startswith('"'):
                current = ast.literal_eval(current)
            current = current[2:] if current.startswith("b/") else current
            continue
        header = re.match(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@", raw)
        if header:
            line = int(header.group(1))
        elif raw.startswith("+") and current:
            for name, pattern in SECRET_PATTERNS:
                for match in pattern.finditer(raw[1:]):
                    errors.append(f"{current}:{line} [{name}] 命中 {len(match.group(0))} 字符（原文隐藏）")
            line += 1
        elif not raw.startswith(("-", "\\")):
            line += 1
    return errors


def check_docs(root, paths, staged):
    errors = []
    for path in paths:
        if not path.endswith(".md"):
            continue
        content = read_content(root, path, staged)
        if content is None:
            continue
        if content.count("```") % 2:
            errors.append(f"{path}: 代码围栏未闭合")
        content = re.sub(r"(?ms)^```[^\n]*\n.*?^```[^\n]*$", "", content)
        for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", content):
            target = target.strip().strip("<>")
            if target.startswith("#") or urlsplit(target).scheme:
                continue
            target = unquote(target.split("#", 1)[0])
            resolved = (root / path).parent.joinpath(target).resolve()
            if not resolved.is_relative_to(root):
                continue
            relative = resolved.relative_to(root).as_posix()
            exists = git(root, "cat-file", "-e", ":" + relative, check=False).returncode == 0 if staged else resolved.exists()
            if not exists:
                errors.append(f"{path}: 本地引用不存在：{target}")
    return errors


def check_syntax(root, paths, staged):
    errors = []
    for path in paths:
        if not path.startswith("scripts/"):
            continue
        content = read_content(root, path, staged) if path.endswith((".py", ".cjs", ".mjs", ".js")) else None
        if content is None:
            continue
        if path.endswith(".py"):
            try:
                ast.parse(content, filename=path)
            except SyntaxError as error:
                errors.append(f"{path}:{error.lineno}: {error.msg}")
        else:
            kind = "module" if path.endswith(".mjs") else "commonjs"
            result = subprocess.run(["node", "--check", "--input-type=" + kind], input=content,
                                    cwd=root, capture_output=True, encoding="utf-8", errors="replace")
            if result.returncode:
                errors.append(f"{path}: node --check 失败")
    return errors


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--staged", action="store_true", help="提交门禁，检查暂存区")
    mode.add_argument("--changed", action="store_true", help="检查相对 HEAD 的已跟踪改动（默认）")
    mode.add_argument("--full", action="store_true", help="显式执行全部静态门禁")
    mode.add_argument("--files", nargs="+", help="仅检查明确列出的文件，含未跟踪文件")
    parser.add_argument("--plan", action="store_true", help="只显示调度计划，不声称检查通过")
    parser.add_argument("--json", action="store_true", help="机器可读摘要")
    parser.add_argument("--verbose", action="store_true", help="显示各检查完整输出")
    parser.add_argument("--require-private", action="store_true", help="适配门禁强制要求私有仓库")
    args = parser.parse_args(argv)
    root = Path(git(Path.cwd(), "rev-parse", "--show-toplevel").stdout.strip()).resolve()
    diff_args = ["--cached"] if args.staged else ["HEAD"]
    paths = args.files if args.files is not None else paths_from_diff(root, *diff_args)
    normalized = []
    for path in paths:
        target = (root / path).resolve()
        if not target.is_relative_to(root):
            raise RuntimeError("检查路径超出仓库")
        normalized.append(target.relative_to(root).as_posix())
    paths = sorted(set(normalized))
    checks = select_checks(paths, args.full)
    if args.plan:
        plan = {"checks": checks, "files": paths, "executed": False}
        print(json.dumps(plan, ensure_ascii=False) if args.json else "计划（未执行）：" + ", ".join(checks))
        return 0
    if not checks:
        print(json.dumps({"checks": [], "executed": False, "passed": None}, ensure_ascii=False)
              if args.json else "[checks] NO-CHANGE：没有检查输入，未执行门禁")
        return 0
    report, details = [], []
    errors = security(root, paths, diff_args)
    # 显式未跟踪文件没有 Git diff，仍检查其完整新内容。
    if args.files:
        for path in paths:
            if git(root, "ls-files", "--error-unmatch", "--", path, check=False).returncode and (root / path).is_file():
                for line, text in enumerate((root / path).read_text(encoding="utf-8", errors="replace").splitlines(), 1):
                    for name, pattern in SECRET_PATTERNS:
                        if pattern.search(text):
                            errors.append(f"{path}:{line} [{name}] 原文隐藏")
    if errors:
        print(json.dumps({"passed": False, "checks": [{"name": "security", "status": "FAIL"}], "errors": errors}, ensure_ascii=False)
              if args.json else "[checks] FAIL security\n" + "\n".join(errors))
        return 1
    if args.staged:
        active = set(checks).intersection(CHECKS)
        conflicts = [p for p in paths_from_diff(root) if affected(p).intersection(active)]
        if conflicts:
            message = "相关输入的暂存版本与工作区不同，无法证明门禁对应提交；请先明确暂存范围"
            print(json.dumps({"passed": False, "errors": [message], "conflicts": conflicts}, ensure_ascii=False)
                  if args.json else "[checks] FAIL：" + message + "\n" + "\n".join(conflicts))
            return 1
    for name in checks:
        if name == "security":
            code, output = 0, "红线文件与新增行密钥扫描通过（不回显原文）"
        elif name == "whitespace":
            result = git(root, "diff", *diff_args, "--check", "--", *paths, check=False)
            code, output = result.returncode, result.stdout + result.stderr
        elif name in ("docs", "syntax"):
            errors = check_docs(root, paths, args.staged) if name == "docs" else check_syntax(root, paths, args.staged)
            code, output = bool(errors), "\n".join(errors)
        else:
            command = [sys.executable, *CHECKS[name]]
            if name == "adapters":
                if args.staged:
                    command.append("--skip-node")
                adapter_changed = any(p.startswith(("shared/assets/offline_repo/", "adapter-worker/")) for p in paths)
                if args.require_private or (args.staged and adapter_changed):
                    command.append("--require-private")
            result = subprocess.run(command, cwd=root, capture_output=True, encoding="utf-8", errors="replace",
                                    env={**os.environ, "PYTHONUTF8": "1"}, timeout=180)
            code, output = result.returncode, result.stdout + result.stderr
        report.append({"name": name, "status": "FAIL" if code else "PASS"})
        details.append(f"[{name}] {'FAIL' if code else 'PASS'}\n{output}")
        if not args.json and (args.verbose or code):
            # 失败只展示有限上下文；完整原始结果始终保存在日志。
            print(f"[{name}] {'FAIL' if code else 'PASS'}\n" + (output if args.verbose else "\n".join(output.splitlines()[-8:])))
    log_dir = root / "build_qa/checks"
    log_dir.mkdir(parents=True, exist_ok=True)
    log = log_dir / (datetime.now().strftime("%Y%m%d-%H%M%S-%f") + ".log")
    log.write_text("\n\n".join(details), encoding="utf-8")
    failed = [r["name"] for r in report if r["status"] == "FAIL"]
    summary = {"checks": report, "executed": True, "notSelected": [n for n in CHECKS if n not in checks], "log": str(log.relative_to(root)), "passed": not failed}
    print(json.dumps(summary, ensure_ascii=False) if args.json else f"[checks] {'FAIL' if failed else 'PASS'} {len(report) - len(failed)}/{len(report)}；未触发 {len(summary['notSelected'])} 项；日志：{summary['log']}")
    return int(bool(failed))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, UnicodeError, subprocess.TimeoutExpired) as error:
        print(f"[checks] ERROR：{error}", file=sys.stderr)
        raise SystemExit(1)
