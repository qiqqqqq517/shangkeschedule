#!/usr/bin/env python3
"""百度网盘正式包发布：校验 Release、上传、生成分享并更新官网配置。"""
import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import time
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

ROOT = Path(__file__).resolve().parents[1]
SITE = ROOT / "website/assets/js/site.js"
ARCHIVE = ROOT.parent / "正式版-arm64"
FOLDER = "上课"


class PublishError(Exception):
    pass


def find_bdpan():
    found = shutil.which("bdpan")
    if found:
        return found
    local_appdata = os.environ.get("LOCALAPPDATA")
    if local_appdata:
        candidate = Path(local_appdata) / "bdpan" / "bdpan.exe"
        if candidate.is_file():
            return str(candidate)
    return None


def verify_release(apk, release):
    if release.get("isDraft") is not False or release.get("isPrerelease") is not False:
        raise PublishError("Release 不是已发布的正式版本")
    matches = [a for a in release.get("assets", []) if a.get("name") == apk.name]
    digest = "sha256:" + hashlib.sha256(apk.read_bytes()).hexdigest()
    if len(matches) != 1 or matches[0].get("size") != apk.stat().st_size or matches[0].get("digest") != digest:
        raise PublishError("APK 与 Release 资产的名称、大小或 SHA-256 不一致")


def share_url(share):
    data = share.get("data", share)
    if not isinstance(data, dict):
        raise PublishError("分享结果格式无效")
    link, pwd = data.get("link"), data.get("pwd")
    if not isinstance(link, str) or not isinstance(pwd, str) or not re.fullmatch(r"[a-z0-9]{4}", pwd):
        raise PublishError("分享结果缺少有效链接或提取码")
    try:
        url = urlsplit(link)
    except ValueError as exc:
        raise PublishError("生成的百度分享链接格式无效") from exc
    if url.scheme != "https" or url.netloc != "pan.baidu.com" or not re.fullmatch(r"/s/[A-Za-z0-9_-]+", url.path):
        raise PublishError("分享结果不是有效的百度网盘链接")
    if data.get("period") != 0:
        raise PublishError("分享不是永久有效，拒绝更新官网")
    query = [(k, v) for k, v in parse_qsl(url.query, keep_blank_values=True) if k != "pwd"]
    query.append(("pwd", pwd))
    return urlunsplit((url.scheme, url.netloc, url.path, urlencode(query), ""))


def existing_share(site):
    text = site.read_text(encoding="utf-8")
    matches = re.findall(r"(?m)^\s*baidu:\s*'([^'\r\n]*)'", text)
    if len(matches) != 1:
        raise PublishError("官网百度链接配置不唯一")
    link = matches[0]
    try:
        url = urlsplit(link)
    except ValueError as exc:
        raise PublishError("现有百度分享链接格式无效") from exc
    codes = [value for key, value in parse_qsl(url.query, keep_blank_values=True) if key == "pwd"]
    if (url.scheme != "https" or url.netloc != "pan.baidu.com"
            or not re.fullmatch(r"/s/[A-Za-z0-9_-]+", url.path)
            or len(codes) != 1 or not re.fullmatch(r"[a-z0-9]{4}", codes[0])):
        raise PublishError("现有百度分享链接或提取码无效")
    return link


def publish(drive, apk, remote, site, reuse_existing=False):
    existing = existing_share(site) if reuse_existing else None
    upload = drive.upload(apk, remote)
    if not isinstance(upload, dict):
        raise PublishError("上传结果格式无效")
    data = upload.get("data", {})
    if not isinstance(data, dict) or upload.get("code") != 0 or data.get("action") not in {"created", "renamed", "unchanged"}:
        raise PublishError("上传失败或结果不确定；保留现场，不自动重试")
    actual = data.get("remote_path")
    if isinstance(actual, str) and actual.startswith("/apps/bdpan/"):
        actual = actual[len("/apps/bdpan/"):]
    if not isinstance(actual, str) or not actual.startswith(FOLDER + "/") or len(actual.split("/")) != 2 or any(part in {"", ".", ".."} for part in actual.split("/")):
        raise PublishError("上传结果缺少有效的实际保存路径")
    if reuse_existing:
        return existing, actual
    result = drive.share(FOLDER)
    if not isinstance(result, dict) or result.get("code", 0) != 0:
        raise PublishError("创建分享失败；官网配置保持不变")
    link = share_url(result)
    original = site.read_text(encoding="utf-8")
    updated, count = re.subn(r"(?m)^(\s*baidu:\s*)'[^'\r\n]*'", lambda m: m.group(1) + json.dumps(link, ensure_ascii=True).replace('"', "'"), original)
    if count != 1:
        raise PublishError("官网百度链接配置不唯一，未修改官网")
    site.write_text(updated, encoding="utf-8", newline="")
    return link, actual


class Drive:
    def __init__(self, executable, session_input, session_id=None):
        self.executable = executable
        self.session_input = session_input
        self.session_id = session_id or f"{int(time.time())}-{secrets.token_hex(3)}"

    def call(self, command, *args):
        result = subprocess.run([self.executable, command, *map(str, args), "--json", "--agentname", "codex",
                                 "--session-input", self.session_input, "--session-id", self.session_id],
                                capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=600)
        if result.returncode:
            raise PublishError(f"百度网盘 {command} 失败（退出码 {result.returncode}）；检查授权和服务状态")
        try:
            return json.loads(result.stdout)
        except ValueError as exc:
            raise PublishError(f"百度网盘 {command} 返回无法解析的结果") from exc

    def upload(self, apk, remote):
        return self.call("upload", apk, remote)

    def share(self, remote):
        return self.call("share", remote, "--period", "0")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="已发布版本，例如 4.81.3")
    parser.add_argument("--apk", type=Path, help="正式 arm64 APK；默认从仓库外归档读取")
    parser.add_argument("--dry-run", action="store_true", help="只检查本地包及 GitHub Release")
    parser.add_argument("--keep-share", action="store_true", help="保留现有分享链接，仅同步文件")
    parser.add_argument("--session-input", default="优化网页端布局，接入百度网盘发布")
    parser.add_argument("--session-id", help="复用本次发版的工具会话标识")
    args = parser.parse_args()
    if not re.fullmatch(r"\d+\.\d+\.\d+", args.version):
        raise PublishError("版本格式必须为 X.Y.Z")
    name = f"shangke-v{args.version}-arm64-v8a-release.apk"
    apk = args.apk or ARCHIVE / name
    if not apk.is_file() or apk.name != name:
        raise PublishError(f"找不到指定版本的 arm64 正式包：{name}")
    site_version = re.search(r"(?m)^\s*version:\s*'([^']+)'", SITE.read_text(encoding="utf-8"))
    if not site_version or site_version.group(1) != args.version:
        raise PublishError("官网版本与待发布 APK 不一致")
    gh = shutil.which("gh")
    if not gh:
        raise PublishError("未找到 gh，无法验证 GitHub Release")
    result = subprocess.run([gh, "release", "view", "v" + args.version, "--json", "isDraft,isPrerelease,assets"],
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=60)
    if result.returncode:
        raise PublishError("无法核验 GitHub Release，未上传")
    try:
        verify_release(apk, json.loads(result.stdout))
    except (ValueError, KeyError) as exc:
        raise PublishError("GitHub Release 返回无效数据") from exc
    print(f"[OK] v{args.version} 正式包与 Release 的 SHA-256 一致")
    if args.dry_run:
        print("[dry-run] 不上传、不创建分享、不改官网")
        return
    bdpan = find_bdpan()
    if not bdpan:
        raise PublishError("未找到百度网盘 bdpan CLI；请先安装并完成授权")
    link, remote = publish(Drive(bdpan, args.session_input, args.session_id), apk, FOLDER + "/" + name, SITE, reuse_existing=args.keep_share)
    if args.keep_share:
        print(f"[OK] 已同步文件并保留现有分享：{link}")
    else:
        print(f"[OK] 已上传并生成永久分享：{link}")
        print("[OK] 官网链接配置已在本地更新；部署需按用户授权另行执行")


if __name__ == "__main__":
    try:
        main()
    except (PublishError, OSError, subprocess.TimeoutExpired) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        sys.exit(1)
