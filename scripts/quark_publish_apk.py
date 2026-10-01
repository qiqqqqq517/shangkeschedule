# -*- coding: utf-8 -*-
"""夸克网盘发版同步：当版 arm64-v8a 正式包上传「上课-课程表」，旧包移入「旧版本在此」。

规则出处：`AGENTS.md`「正式版构建与发布」节；逐条实操见 `docs/agents/release-runbook.md` §6。

前置（缺一不可）：
  1. 本机已安装夸克网盘 Skill —— `node ~/.dsh/skills/quarkclouddrive/scripts/quark-drive.cjs --version`
  2. 该 Skill 已完成账号授权 —— `... get-user-info` 能返回昵称与会员信息

行为：
  * 按 `androidApp/build.gradle.kts` 的 `versionName` 找 `shangke-vX.Y.Z-arm64-v8a-release.apk`；
    先在 `androidApp/build/outputs/apk/release/` 找，找不到回退仓库外的 `../正式版-arm64/`。
  * **发版闸**：默认要求该版本在 GitHub 上已有「已发布（非 draft）」Release，否则中止——
    防止把尚未发版的本地构建公开到分发目录；确需上传须显式 `--allow-unreleased`。
  * 目标目录与「旧版本在此」的 fid 一律**按目录名现场解析**，不写死 fid（分享/重建目录后仍可用）。
  * 同名包已存在即跳过上传（幂等）；随后把该目录内**其余全部 .apk** 移入「旧版本在此」。
  * browse 产物落在临时目录，不污染仓库工作区。

用法：
    python scripts/quark_publish_apk.py --dry-run          # 只打印将执行的动作，不改动网盘
    python scripts/quark_publish_apk.py                    # 上传 + 归档旧包
    python scripts/quark_publish_apk.py --apk <路径>        # 指定要上传的包（跳过发版闸）
    python scripts/quark_publish_apk.py --allow-unreleased  # 放行未发版构建（危险）
"""
import argparse
import io
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

if sys.stdout.encoding and sys.stdout.encoding.lower() not in ("utf-8", "utf8"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "androidApp" / "build.gradle.kts"
DIST_DIR = ROOT / "androidApp" / "build" / "outputs" / "apk" / "release"
ARCHIVE_DIR = ROOT.parent / "正式版-arm64"

SKILL_DIR = Path.home() / ".dsh" / "skills" / "quarkclouddrive"
CLI = SKILL_DIR / "scripts" / "quark-drive.cjs"
INSTALL_SH = SKILL_DIR / "scripts" / "install.sh"

TARGET_FOLDER = "上课-课程表"      # 分发目录：只留最新包
OLD_FOLDER = "旧版本在此"          # 归档子目录
MOVE_BATCH = 100                   # move 单次上限

# 夸克 Skill 要求每次调用都带 --session-input（用户原始提问）与 --session-id。
# 本脚本是「用户已下达规则、由发版流程触发」的调用，故固定复用该规则的原文。
SESSION_INPUT = "把每次最新的安卓v8a文件自动上传到上课课程表页面，并把旧版本移入旧版本在此文件夹"


def log(msg=""):
    print(msg, flush=True)


def die(msg):
    log(f"[FAIL] {msg}")
    sys.exit(1)


def find_git_bash():
    """PATH 上的 bash 在 Windows 会命中 WSL 的 bash（uname 返回 Linux，install.sh 判错平台），
    所以显式优先 Git Bash。"""
    for p in (r"C:\Program Files\Git\bin\bash.exe",
              r"C:\Program Files (x86)\Git\bin\bash.exe"):
        if os.path.isfile(p):
            return p
    return None


def version_name():
    text = GRADLE.read_text(encoding="utf-8")
    m = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not m:
        die('无法从 androidApp/build.gradle.kts 解析 versionName')
    return m.group(1)


def resolve_apk(explicit):
    if explicit:
        p = Path(explicit)
        if not p.is_file():
            die(f"指定的 APK 不存在：{p}")
        return p
    ver = version_name()
    name = f"shangke-v{ver}-arm64-v8a-release.apk"
    for d in (DIST_DIR, ARCHIVE_DIR):
        cand = d / name
        if cand.is_file():
            return cand
    die(f"未找到当版 arm64 包 {name}\n"
        f"  已查：{DIST_DIR}\n"
        f"        {ARCHIVE_DIR}\n"
        f"  先本地构建：$env:JAVA_HOME='C:\\Program Files\\Android\\Android Studio\\jbr'; .\\gradlew.bat :androidApp:assembleRelease")


def gh_release_state(ver):
    """返回 'published' / 'draft' / 'unreleased'；gh 不可用或查询异常返回 None（无法判定）。"""
    gh = shutil.which("gh")
    if not gh:
        return None
    try:
        r = subprocess.run([gh, "release", "view", f"v{ver}", "--json", "isDraft"],
                           capture_output=True, text=True, encoding="utf-8",
                           errors="replace", timeout=60)
    except Exception:
        return None
    if r.returncode != 0:
        return "unreleased"
    try:
        data = json.loads((r.stdout or "").strip() or "{}")
    except ValueError:
        return None
    return "draft" if data.get("isDraft") else "published"


def find_node():
    node = shutil.which("node")
    if not node:
        die("PATH 上找不到 node（夸克网盘 Skill 的 CLI 需要 Node >= 16）")
    return node


def run_cli(node, args, cwd, session_id, timeout=1800):
    """跑一次夸克 CLI，返回 (returncode, stdout, result 对象或 None)。"""
    cmd = [node, str(CLI)] + list(args) + [
        "--session-input", SESSION_INPUT,
        "--session-id", session_id,
    ]
    r = subprocess.run(cmd, cwd=str(cwd), capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=timeout)
    result = None
    for line in (r.stdout or "").splitlines():
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            obj = json.loads(line)
        except ValueError:
            continue
        if obj.get("type") == "result":
            result = obj
    return r.returncode, (r.stdout or "") + (r.stderr or ""), result


def _walk_files(obj, out):
    """browse 的 JSONL 一行可能是一个文件对象，也可能带 children；统一拍平。"""
    if isinstance(obj, dict):
        if "filename" in obj and "fid" in obj:
            out.append(obj)
        for v in obj.values():
            _walk_files(v, out)
    elif isinstance(obj, list):
        for v in obj:
            _walk_files(v, out)


def browse(node, parent_fid, cwd, session_id, tag):
    """browse 一个目录，返回文件对象列表。browse 的全量结果写进 <cwd>/browse/*.jsonl。"""
    before = set(str(p) for p in (cwd / "browse").glob("*.jsonl")) if (cwd / "browse").is_dir() else set()
    code, out, res = run_cli(node, ["browse", "--parent-fid", parent_fid, "--page-size", "100", "--all"],
                             cwd, session_id)
    if code != 0 or (res or {}).get("code") not in (0, None):
        die(f"browse {tag} 失败：\n{out.strip()[-800:]}")
    candidates = [p for p in (cwd / "browse").glob("*.jsonl") if str(p) not in before]
    if not candidates:
        candidates = sorted((cwd / "browse").glob("*.jsonl"), key=lambda p: p.stat().st_mtime)
    if not candidates:
        die(f"browse {tag} 未产出结果文件")
    newest = max(candidates, key=lambda p: p.stat().st_mtime)
    files = []
    for line in newest.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            _walk_files(json.loads(line), files)
        except ValueError:
            continue
    return files


def pick_folder(files, name):
    for f in files:
        if str(f.get("filename")) == name and str(f.get("file_type")) == "0":
            return str(f.get("fid"))
    return None


def main():
    ap = argparse.ArgumentParser(description="夸克网盘发版同步（上传最新 arm64 包 + 归档旧包）")
    ap.add_argument("--apk", help="要上传的 APK 路径（默认按 build.gradle.kts 的 versionName 找）")
    ap.add_argument("--dry-run", action="store_true", help="只打印将执行的动作，不改动网盘")
    ap.add_argument("--allow-unreleased", action="store_true",
                    help="放行 GitHub 上尚未正式发布的版本（默认中止，防止把未发版构建公开到分发目录）")
    args = ap.parse_args()

    node = find_node()
    if not CLI.is_file():
        die(f"未找到夸克网盘 Skill 的 CLI：{CLI}\n"
            f"  先安装：bash scripts/install.sh（在该 skill 目录内）")

    apk = resolve_apk(args.apk)
    size_mb = apk.stat().st_size / 1024 / 1024
    log(f"[INFO] 待上传：{apk.name}  {apk.stat().st_size} 字节 ({size_mb:.2f} MB)")
    log(f"[INFO] 来源目录：{apk.parent}")

    # 发版闸：没有已发布的 GitHub Release 就不上传，避免把未发版构建公开到分发目录。
    # （--apk 显式指定包时跳过：此时版本与 build.gradle.kts 未必对应）
    if not args.apk:
        ver = version_name()
        state = gh_release_state(ver)
        if state == "published":
            log(f"[INFO] 发版闸：v{ver} 已是 GitHub 已发布 Release")
        elif state == "draft":
            log(f"[WARN] v{ver} 在 GitHub 上仍是 Draft Release —— 分发目录会提前暴露未正式发布的版本")
        elif state == "unreleased":
            if not args.allow_unreleased:
                die(f"v{ver} 在 GitHub 上还不是已发布的 Release，拒绝上传未发版构建。\n"
                    f"  正常流程：先完成 GitHub Release（见 docs/agents/release-runbook.md §4），再跑本脚本。\n"
                    f"  确要把未发版构建公开到分发目录，请显式加 --allow-unreleased。")
            log(f"[WARN] v{ver} 尚未在 GitHub 发版，但已指定 --allow-unreleased，继续")
        else:
            log("[WARN] 无法确认 GitHub Release 状态（gh 不可用或未登录），跳过发版闸")

    # 安装检查：夸克 Skill 要求 CLI 操作前先跑一次 install.sh（安装/更新不得与 CLI 操作并发）
    bash = find_git_bash()
    if bash and INSTALL_SH.is_file():
        r = subprocess.run([bash, "scripts/install.sh"], cwd=str(SKILL_DIR),
                           capture_output=True, text=True, encoding="utf-8",
                           errors="replace", timeout=600)
        if r.returncode == 0:
            log("[INFO] 夸克 Skill 安装检查通过")
        else:
            log("[WARN] 夸克 Skill 安装检查返回非 0，继续尝试调用 CLI")
    else:
        log("[WARN] 未找到 Git Bash 或 install.sh，跳过安装检查")

    session_id = f"{int(time.time())}-{secrets.token_hex(3)}"
    tmp = Path(tempfile.mkdtemp(prefix="quark-publish-"))
    try:
        log(f"[INFO] 解析网盘目录（fid 现场查询，不写死）…")
        root_files = browse(node, "0", tmp, session_id, "根目录")
        target_fid = pick_folder(root_files, TARGET_FOLDER)
        if not target_fid:
            die(f"夸克网盘根目录下找不到文件夹「{TARGET_FOLDER}」")
        log(f"        {TARGET_FOLDER}  fid={target_fid}")

        items = browse(node, target_fid, tmp, session_id, TARGET_FOLDER)
        old_fid = pick_folder(items, OLD_FOLDER)
        if not old_fid:
            die(f"「{TARGET_FOLDER}」下找不到子文件夹「{OLD_FOLDER}」（请先在夸克网盘 App/网页里建好）")
        log(f"        {OLD_FOLDER}  fid={old_fid}")

        present = [str(f.get("filename")) for f in items if str(f.get("file_type")) == "1"]
        apks = [f for f in items
                if str(f.get("file_type")) == "1" and str(f.get("filename")).lower().endswith(".apk")]
        already = apk.name in present
        to_move = [f for f in apks if str(f.get("filename")) != apk.name]

        log("")
        log(f"[PLAN] 上传：{apk.name}" + ("  （同名已存在，将跳过）" if already else ""))
        if to_move:
            for f in to_move:
                log(f"[PLAN] 归档：{f.get('filename')}  → {OLD_FOLDER}/")
        else:
            log(f"[PLAN] 归档：无（{TARGET_FOLDER} 内没有其它 .apk）")

        if args.dry_run:
            log("")
            log("[dry-run] 未改动网盘。")
            return

        # 1) 上传（同名已存在则跳过，保持幂等）
        if already:
            log(f"[SKIP] {apk.name} 已在「{TARGET_FOLDER}」中，跳过上传")
        else:
            log(f"[RUN ] 上传 {apk.name} …")
            code, out, res = run_cli(node, ["upload", str(apk), "--parent-fid", target_fid],
                                     tmp, session_id)
            if code != 0 or not res or res.get("code") != 0:
                die(f"上传失败：\n{out.strip()[-800:]}")
            data = res.get("data") or {}
            log(f"[OK  ] 上传成功：{data.get('fileNames')} → {data.get('fullPath')}")

        # 2) 旧包归档
        if to_move:
            fids = [str(f.get("fid")) for f in to_move]
            for i in range(0, len(fids), MOVE_BATCH):
                batch = fids[i:i + MOVE_BATCH]
                log(f"[RUN ] 归档 {len(batch)} 个旧包 → {OLD_FOLDER} …")
                code, out, res = run_cli(node, ["move"] + batch + ["--target-fid", old_fid],
                                         tmp, session_id)
                if code != 0 or not res or res.get("code") != 0:
                    die(f"移动失败：\n{out.strip()[-800:]}")
                log(f"[OK  ] {res.get('data', {}).get('move_path')}  （{len(batch)} 个）")
        else:
            log("[SKIP] 没有需要归档的旧包")

        log("")
        log(f"[DONE] 「{TARGET_FOLDER}」当前最新包：{apk.name}")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
