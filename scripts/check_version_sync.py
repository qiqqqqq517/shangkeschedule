# -*- coding: utf-8 -*-
"""
版本号一致性门禁：`androidApp/build.gradle.kts` ↔ `website/assets/js/site.js`。

背景：发版时版本号被写进三个地方，而三处都靠人工/被 gitignore 的
`tools/update_website.py` 同步：

  1. `androidApp/build.gradle.kts` → `versionCode` / `versionName`（决定 APK 真实版本）
  2. `website/assets/js/site.js` → `SITE.version` / `SITE.versionCode`
     （运行时覆写官网页上所有 `data-version` / `data-version-code` 占位符）
  3. `CHANGELOG.md` 顶部条目（人工撰写，无机器可校验的锚点）

仓库内**没有任何**校验：`.githooks/` 无版本检查，`dco.yml` / `check-pr-source.yml`
都不看版本号，`tools/` 整目录被 gitignore（AGENTS.md 明文红线：新 worktree 里根本
没有 `update_website.py`）。结果就是「App 已 bump、官网仍显示旧版」可以静默发生 ——
用户从官网点下载，拿到的却是上一版。

本门禁把 (1) 与 (2) 的四个字段逐一对齐，不一致即 exit 1。

用法：
    python scripts/check_version_sync.py            # 检查，不一致时 exit 1
    python scripts/check_version_sync.py --quiet    # 仅输出结论
"""
import io
import os
import re
import sys

if sys.stdout.encoding and sys.stdout.encoding.lower() not in ("utf-8", "utf8"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE = os.path.join(ROOT, "androidApp", "build.gradle.kts")
SITE_JS = os.path.join(ROOT, "website", "assets", "js", "site.js")


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def gradle_versions():
    text = read(GRADLE)
    code = re.search(r'^\s*versionCode\s*=\s*(\d+)\s*$', text, re.M)
    name = re.search(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', text, re.M)
    if not code or not name:
        return None, None
    return code.group(1), name.group(1)


def site_versions():
    text = read(SITE_JS)
    ver = re.search(r"version:\s*'([^']+)'", text)
    code = re.search(r"versionCode:\s*'(\d+)'", text)
    if not ver or not code:
        return None, None
    return ver.group(1), code.group(1)


def main():
    quiet = "--quiet" in sys.argv

    g_code, g_name = gradle_versions()
    s_name, s_code = site_versions()

    if g_code is None or g_name is None:
        print("[FAIL] 无法从 build.gradle.kts 解析 versionCode / versionName")
        return 1
    if s_name is None or s_code is None:
        print("[FAIL] 无法从 site.js 解析 SITE.version / SITE.versionCode")
        return 1

    checks = [
        ("versionCode", "build.gradle.kts", g_code, "site.js SITE.versionCode", s_code),
        ("versionName", "build.gradle.kts", g_name, "site.js SITE.version", s_name),
    ]

    failed = [(label, a_src, a_val, b_src, b_val)
              for label, a_src, a_val, b_src, b_val in checks if a_val != b_val]

    if not quiet:
        for label, a_src, a_val, b_src, b_val in checks:
            mark = "OK  " if a_val == b_val else "FAIL"
            print(f"  [{mark}] {label}: {a_src}={a_val}  {b_src}={b_val}")

    if failed:
        print()
        print("[version-sync] 未通过：官网显示的版本与 App 实际版本不一致")
        for label, a_src, a_val, b_src, b_val in failed:
            print(f"  - {label}: {a_src}={a_val}，但 {b_src}={b_val}")
        print("  影响：官网页面会把用户导向错误的版本说明/下载链接。")
        print("  修法：同步 site.js 的 SITE.version / SITE.versionCode（勿改 build.gradle.kts —— 那是 APK 的真实版本）。")
        return 1

    print()
    print(f"[version-sync] 通过：App 与官网版本一致（v{g_name} / {g_code}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())