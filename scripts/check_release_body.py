#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""发版防乱码门禁：核对 GitHub Release 正文与 CHANGELOG 对应段落逐字一致。

背景（2026-09-30 实测事故）：Windows PowerShell 5.1 的 `Get-Content` 在不带
`-Encoding` 时，会把无 BOM 的 UTF-8 文件按系统 ANSI（GBK）解码，导致抽出的
notes 段落实为 mojibake（v4.64.2/v4.64.3 的 Release 正文因此乱码，CJK 计数
247→350 膨胀）。本脚本在发版流程中做最终把关。

用法：
    python scripts/check_release_body.py v4.64.3

退出码 0 表示通过；非 0 表示正文不一致或含乱码特征。
依赖：`gh` 已登录（取 token 用，不打印）。
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CHANGELOG = REPO_ROOT / "CHANGELOG.md"
REPO = "qiqqqqq517/shangkeschedule"


def local_paragraph(version: str) -> str:
    text = CHANGELOG.read_text(encoding="utf-8")  # 严格按 UTF-8 读，不走系统编码
    m = re.search(r"(?ms)^### %s.*?(?=^### |\Z)" % re.escape(version), text)
    if not m:
        raise SystemExit("CHANGELOG.md 中未找到 %s 段落" % version)
    return m.group(0).rstrip("\r\n")


def gh_token() -> str:
    proc = subprocess.run(["gh", "auth", "token"],
                          capture_output=True, text=True, timeout=30)
    token = proc.stdout.strip()
    if not token:
        raise SystemExit("无法从 gh 获取 token（未登录？）")
    return token


def remote_body(tag: str) -> str:
    req = urllib.request.Request(
        "https://api.github.com/repos/%s/releases/tags/%s" % (REPO, tag),
        headers={"Authorization": "token " + gh_token(),
                 "User-Agent": "shangke-check",
                 "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return (json.load(resp)["body"] or "")


def cjk_count(s: str) -> int:
    return sum(1 for ch in s if "\u4e00" <= ch <= "\u9fff")


def main() -> int:
    if len(sys.argv) != 2 or not re.fullmatch(r"v\d+\.\d+\.\d+", sys.argv[1]):
        print("用法: python scripts/check_release_body.py vX.Y.Z")
        return 2
    tag = sys.argv[1]
    local = local_paragraph(tag).strip().replace("\r\n", "\n")
    remote = remote_body(tag).strip().replace("\r\n", "\n")

    problems = []
    if local != remote:
        problems.append("正文与 CHANGELOG 段落不一致 "
                        "(local=%d字/%dCJK, remote=%d字/%dCJK)"
                        % (len(local), cjk_count(local),
                           len(remote), cjk_count(remote)))
    for label, text in (("local", local), ("remote", remote)):
        if "�" in text:
            problems.append("%s 含 U+FFFD 替换字符" % label)
    if (len(remote) > len(local)
            and cjk_count(remote) > cjk_count(local)):
        problems.append("CJK 计数膨胀 (local=%d → remote=%d)，"
                        "疑似 UTF-8 按 GBK 解码的 mojibake"
                        % (cjk_count(local), cjk_count(remote)))

    if problems:
        print("门禁未通过 %s：" % tag)
        for p in problems:
            print("  - " + p)
        return 1
    print("门禁通过 %s：正文与 CHANGELOG 逐字一致，无乱码特征 (%d字)"
          % (tag, len(local)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
