"""本机推送助手：绕开 git 凭据链，直接用 wincred helper 取令牌推送。

**为什么需要它**：本机 `git push` 的凭据链不可靠 ——
`~/.gitconfig` 里曾出现空值 `credential.helper =` 覆盖系统级 helper-selector，
之后又有写法错误的 `!\\"...\\"`（引号被转义，git 无法执行）等多行污染；
`git credential fill` 时常报
`could not read Password for 'https://qiqqqqq517@github.com': terminal prompts disabled`。
但**凭据本身一直好好的**，存在 Windows 凭据管理器里，用 wincred helper 能稳定取到。

本脚本显式定位 `git-credential-wincred.exe` 并直接调用它取凭据，再把令牌注入 URL 完成推送，
全程不依赖 git 的 helper 配置。

用法：
  python scripts/push_via_wincred.py                 # 推当前分支到 origin
  python scripts/push_via_wincred.py --remote gitee   # 指定远端
  python scripts/push_via_wincred.py --dry-run        # 只验证凭据可取，不推送

推送后请用 `git ls-remote --heads <remote> <branch>` 独立核验 ——
远端是否真的收到提交，只有它能确认（本项目曾出现「push 看似执行、远端未动」）。
"""
import argparse
import glob
import os
import re
import subprocess
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

# 常见的 wincred helper 位置（含各版本 PortableGit）
CANDIDATE_PATTERNS = (
    r"C:\Program Files\Git\mingw64\bin\git-credential-wincred.exe",
    r"C:\Program Files\Git\mingw64\libexec\git-core\git-credential-wincred.exe",
    r"C:\Program Files (x86)\Git\mingw64\bin\git-credential-wincred.exe",
    os.path.expandvars(r"%USERPROFILE%\.workbuddy\binaries\PortableGit\versions\*\mingw64\bin\git-credential-wincred.exe"),
    os.path.expandvars(r"%USERPROFILE%\scoop\apps\git\current\mingw64\bin\git-credential-wincred.exe"),
)


def find_helper():
    for pat in CANDIDATE_PATTERNS:
        for hit in glob.glob(pat):
            if os.path.exists(hit):
                return hit
    # 兜底：问 git 自己的 exec-path
    try:
        r = subprocess.run(["git", "--exec-path"], capture_output=True, text=True)
        cand = os.path.join(r.stdout.strip(), "git-credential-wincred.exe")
        if os.path.exists(cand):
            return cand
    except Exception:
        pass
    return None


def run(args, **kw):
    return subprocess.run(args, capture_output=True, text=True, **kw)


def get_credential(helper, host="github.com"):
    p = run([helper, "get"], input=f"protocol=https\nhost={host}\n\n")
    if p.returncode != 0 and not p.stdout.strip():
        return None, None, p.stderr.strip()
    u = re.search(r"^username=(.*)$", p.stdout, re.M)
    t = re.search(r"^password=(.*)$", p.stdout, re.M)
    return (u.group(1).strip() if u else None), (t.group(1).strip() if t else None), None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--remote", default="origin")
    ap.add_argument("--branch", default=None)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    helper = find_helper()
    if not helper:
        print("[push] ❌ 找不到 git-credential-wincred.exe（请手动指定路径）")
        return 1
    print(f"[push] wincred helper = {helper}")

    url = run(["git", "remote", "get-url", args.remote]).stdout.strip()
    if not url:
        print(f"[push] ❌ 取不到远端 {args.remote} 的 URL")
        return 1
    m = re.match(r"https://([^/]+)/(.+?)(?:\.git)?$", url)
    if not m:
        print(f"[push] ❌ 远端 URL 不是 https 形式，本脚本不适用：{url}")
        return 1
    host, path = m.group(1), m.group(2)

    user, tok, err = get_credential(helper, host)
    if not user or not tok:
        print(f"[push] ❌ 未取到凭据：{err or '返回为空'}")
        print("       请确认 Windows 凭据管理器里存有 git:https://%s" % host)
        return 1
    print(f"[push] user={user} token_len={len(tok)} prefix={tok[:4]}...")

    if args.dry_run:
        print("[push] --dry-run：凭据可取，未执行推送")
        return 0

    branch = args.branch or run(["git", "rev-parse", "--abbrev-ref", "HEAD"]).stdout.strip()
    push_url = f"https://{user}:{tok}@{host}/{path}.git"
    r = run(["git", "push", push_url, branch])
    out = (r.stdout + r.stderr).replace(tok, "***")
    for line in out.splitlines():
        print("  " + line)
    print(f"[push] RC={r.returncode}")
    if r.returncode != 0:
        return r.returncode

    verify = run(["git", "ls-remote", "--heads", args.remote, branch]).stdout.strip()
    local = run(["git", "rev-parse", "HEAD"]).stdout.strip()
    print(f"[push] 远端 {args.remote}/{branch} = {verify.split()[0] if verify else '(空)'}")
    print(f"[push] 本地 HEAD = {local}")
    print("[push] ✅ 一致" if verify.startswith(local) else "[push] ⚠️ 远端与本地不一致，请检查")
    return 0


if __name__ == "__main__":
    sys.exit(main())
