#!/usr/bin/env python3
"""将已核验的 Release 转正式，并在成功后同步百度网盘；不部署官网。"""
import argparse
import json
import re
import secrets
import shutil
import subprocess
import sys
import time
from pathlib import Path

import baidu_publish_apk as baidu


class SyncError(Exception):
    pass


def publish_then_sync(apk, release, promote, sync):
    if not isinstance(release.get("isDraft"), bool) or release.get("isPrerelease") is not False:
        raise SyncError("Release 状态不明确或为预发布版本")
    try:
        baidu.verify_release(apk, {**release, "isDraft": False})
    except (baidu.PublishError, OSError) as exc:
        raise SyncError(str(exc)) from exc
    if release["isDraft"]:
        promote()
    try:
        sync()
    except (SyncError, OSError, subprocess.TimeoutExpired) as exc:
        raise SyncError(f"GitHub Release 已发布，但百度同步失败：{exc}；修复后重新运行本命令") from exc


def run_checked(args, description):
    try:
        result = subprocess.run(args, cwd=baidu.ROOT, capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=600)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise SyncError(f"{description}无法完成") from exc
    if result.returncode:
        raise SyncError(f"{description}失败（退出码 {result.returncode}）")
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="版本号，例如 4.81.3")
    parser.add_argument("--apk", required=True, type=Path, help="已校验的 arm64 正式包路径")
    args = parser.parse_args()
    if not re.fullmatch(r"\d+\.\d+\.\d+", args.version):
        raise SyncError("版本格式必须为 X.Y.Z")
    apk = args.apk.resolve()
    if not apk.is_file() or apk.name != f"shangke-v{args.version}-arm64-v8a-release.apk":
        raise SyncError("找不到指定版本的 arm64 正式包")
    site_match = re.search(r"(?m)^\s*version:\s*'([^']+)'", baidu.SITE.read_text(encoding="utf-8"))
    if not site_match or site_match.group(1) != args.version:
        raise SyncError("本地官网版本与待发布版本不一致")
    try:
        baidu.existing_share(baidu.SITE)
    except baidu.PublishError as exc:
        raise SyncError(f"百度现有分享配置无效，未发布：{exc}") from exc
    gh, bdpan = shutil.which("gh"), baidu.find_bdpan()
    if not gh or not bdpan:
        raise SyncError("缺少 GitHub CLI 或百度网盘 CLI，未发布")
    session_id = f"{int(time.time())}-{secrets.token_hex(3)}"
    try:
        auth = json.loads(run_checked([bdpan, "whoami", "--json", "--no-check-update",
                                       "--agentname", "codex", "--session-input", "要自动同步", "--session-id", session_id], "百度授权检查"))
    except ValueError as exc:
        raise SyncError("无法解析百度授权状态，未发布") from exc
    if not isinstance(auth, dict) or auth.get("authenticated") is not True or auth.get("has_valid_token") is not True:
        raise SyncError("百度账号未授权或授权已失效，未发布")
    tag = "v" + args.version
    try:
        release = json.loads(run_checked([gh, "release", "view", tag, "--json", "isDraft,isPrerelease,assets"], "读取 Release"))
    except ValueError as exc:
        raise SyncError("无法解析 Release 状态，未发布") from exc

    def promote():
        run_checked([gh, "release", "edit", tag, "--draft=false"], "发布 GitHub Release")
        print(f"[OK] GitHub Release {tag} 已从草稿转正式")

    def sync():
        out = run_checked([sys.executable, str(baidu.ROOT / "scripts/baidu_publish_apk.py"),
                           "--version", args.version, "--apk", str(apk), "--keep-share", "--session-input", "要自动同步", "--session-id", session_id], "百度同步")
        print(out.strip())

    publish_then_sync(apk, release, promote, sync)
    print("[OK] 已保留原官网分享配置；本命令不部署官网")


if __name__ == "__main__":
    try:
        main()
    except (SyncError, OSError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        sys.exit(1)
