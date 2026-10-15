"""使用独立 Git 仓库验证门禁调度、拦截与输出，不修改项目暂存区。"""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


RUNNER = Path(__file__).resolve().parents[1] / "project_checks.py"
HOOKS = RUNNER.parent.parent / ".githooks"


class ProjectChecksTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="shangke-checks-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git("init", "-q")
        self.git("config", "user.name", "Checks Test")
        self.git("config", "user.email", "checks@example.invalid")
        self.write("initial.txt", "initial\n")
        self.git("add", "--", "initial.txt")
        self.git("-c", "core.hooksPath=", "commit", "-qm", "initial")

    def git(self, *args):
        return subprocess.run(["git", *args], cwd=self.root, check=True,
                              capture_output=True, encoding="utf-8")

    def write(self, name, content):
        target = self.root / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

    def stage(self, name, content="changed\n"):
        self.write(name, content)
        self.git("add", "--", name)

    def run_checks(self, *args):
        env = {**os.environ, "PYTHONUTF8": "1"}
        return subprocess.run([sys.executable, str(RUNNER), *args], cwd=self.root,
                              capture_output=True, encoding="utf-8", env=env)

    def plan(self, name):
        self.stage(name)
        result = self.run_checks("--staged", "--plan", "--json")
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        return json.loads(result.stdout)["checks"]

    def test_document_does_not_schedule_app_scans(self):
        self.assertEqual(self.plan("docs/中文 说明.md"),
                         ["security", "whitespace", "docs"])

    def test_ui_schedules_its_three_scanners(self):
        checks = self.plan("shared/src/commonMain/kotlin/com/shangkeschedule/ui/Page.kt")
        self.assertTrue({"theme", "a11y", "font"}.issubset(checks))
        self.assertNotIn("widget", checks)
        self.assertNotIn("version", checks)

    def test_non_ui_common_source_still_schedules_broad_scanners(self):
        checks = self.plan("shared/src/commonMain/kotlin/com/shangkeschedule/data/Model.kt")
        self.assertTrue({"a11y", "font"}.issubset(checks))
        self.assertNotIn("theme", checks)

    def test_widget_colors_do_not_schedule_compose_scans(self):
        checks = self.plan("androidApp/src/main/res/values-night/colors.xml")
        self.assertIn("widget", checks)
        self.assertFalse({"theme", "a11y", "font"}.intersection(checks))

    def test_version_source_schedules_version_gate(self):
        self.assertIn("version", self.plan("website/version.json"))

    def test_checker_or_baseline_change_triggers_its_gate(self):
        self.assertIn("font", self.plan("scripts/font-scale-baseline.json"))

    def test_adapter_changes_trigger_adapter_gate(self):
        self.assertIn("adapters", self.plan("shared/assets/offline_repo/schools/resources/a.js"))

    def test_full_explicitly_selects_all_static_gates(self):
        result = self.run_checks("--full", "--plan", "--json")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue({"theme", "a11y", "font", "widget", "version", "adapters"}
                        .issubset(json.loads(result.stdout)["checks"]))

    def test_no_changes_is_not_reported_as_executed_success(self):
        result = self.run_checks("--staged", "--json")
        self.assertEqual(result.returncode, 0)
        summary = json.loads(result.stdout)
        self.assertFalse(summary["executed"])
        self.assertIsNone(summary["passed"])

    def test_sensitive_filename_is_rejected(self):
        self.stage("androidApp/keystore.properties", "placeholder\n")
        result = self.run_checks("--staged")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("keystore.properties", result.stdout + result.stderr)

    def test_local_artifact_is_rejected(self):
        self.stage("tools/session.py", "pass\n")
        self.assertNotEqual(self.run_checks("--staged").returncode, 0)

    def test_secret_is_rejected_without_echoing_value(self):
        secret = "ghp_" + "A" * 36
        self.stage("docs/中文 密钥.md", "# 测试\n" + secret + "\n")
        result = self.run_checks("--staged")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("github-pat", result.stdout + result.stderr)
        self.assertNotIn(secret, result.stdout + result.stderr)

    def test_json_failure_remains_machine_readable(self):
        self.stage("androidApp/keystore.properties", "placeholder\n")
        result = self.run_checks("--staged", "--json")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(json.loads(result.stdout)["passed"])

    def test_real_git_hook_runs_the_same_gate(self):
        self.write("scripts/project_checks.py", RUNNER.read_text(encoding="utf-8"))
        self.stage("docs/note.md", "# 文档\n")
        result = subprocess.run(["git", "-c", f"core.hooksPath={HOOKS.as_posix()}", "commit", "-qm", "hook integration"],
                                cwd=self.root, capture_output=True, encoding="utf-8",
                                env={**os.environ, "SHANGKE_PYTHON": sys.executable, "PYTHONUTF8": "1"})
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("[checks] PASS", result.stdout + result.stderr)

    def test_real_git_hook_rejects_sensitive_files(self):
        self.write("scripts/project_checks.py", RUNNER.read_text(encoding="utf-8"))
        self.stage("local.properties", "placeholder\n")
        result = subprocess.run(["git", "-c", f"core.hooksPath={HOOKS.as_posix()}", "commit", "-qm", "must fail"],
                                cwd=self.root, capture_output=True, encoding="utf-8",
                                env={**os.environ, "SHANGKE_PYTHON": sys.executable, "PYTHONUTF8": "1"})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("local.properties", result.stdout + result.stderr)

    def test_broken_link_uses_staged_content(self):
        self.stage("docs/note.md", "[引用](missing.md)\n")
        # 工作区被改成合法内容也不能遮蔽暂存版本。
        self.write("docs/note.md", "# valid\n")
        result = self.run_checks("--staged")
        self.assertNotEqual(result.returncode, 0)

    def test_good_document_has_one_compact_summary(self):
        self.stage("docs/note.md", "# 文档\n")
        result = self.run_checks("--staged")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertLessEqual(len(result.stdout.splitlines()), 3)

    def test_summary_remains_utf8_when_python_utf8_mode_is_disabled(self):
        self.stage("docs/中文.md", "# 文档\n")
        result = subprocess.run([sys.executable, str(RUNNER), "--staged"], cwd=self.root,
                                capture_output=True, env={**os.environ, "PYTHONUTF8": "0"})
        self.assertEqual(result.returncode, 0)
        self.assertIn("未触发", result.stdout.decode("utf-8"))

    def test_deleted_document_does_not_break_reader(self):
        self.stage("docs/note.md", "# 文档\n")
        self.git("-c", "core.hooksPath=", "commit", "-qm", "document")
        self.git("rm", "--", "docs/note.md")
        result = self.run_checks("--staged")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_partial_staging_cannot_mask_scanner_failure(self):
        name = "shared/src/commonMain/kotlin/com/shangkeschedule/ui/Page.kt"
        self.stage(name, "staged\n")
        self.write(name, "different worktree\n")
        result = self.run_checks("--staged")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("暂存", result.stdout + result.stderr)

    def test_unstaged_baseline_cannot_mask_staged_ui_failure(self):
        baseline = "scripts/theme-leak-baseline.json"
        self.stage(baseline, "{}\n")
        self.git("-c", "core.hooksPath=", "commit", "-qm", "baseline")
        self.write(baseline, '{"relaxed": true}\n')
        self.stage("shared/src/commonMain/kotlin/com/shangkeschedule/ui/Page.kt")
        result = self.run_checks("--staged")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("暂存", result.stdout + result.stderr)

    def test_relevant_scanner_failure_is_not_silently_skipped(self):
        self.stage("website/version.json", "{}\n")
        self.write("scripts/check_version_sync.py", "print('expected scanner failure')\nraise SystemExit(1)\n")
        result = self.run_checks("--staged")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("version", result.stdout + result.stderr)

    def test_gate_infrastructure_change_does_not_require_unrelated_private_checkout(self):
        self.stage(".githooks/pre-commit", "#!/bin/sh\n")
        for name in ("theme_leak", "a11y", "font_scale", "widget_contrast", "adapters", "version_sync"):
            self.write(f"scripts/check_{name}.py", "import sys\nraise SystemExit(9 if '--require-private' in sys.argv else 0)\n")
        result = self.run_checks("--staged")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_staged_adapter_changes_still_require_private_checkout(self):
        self.stage("shared/assets/offline_repo/schools/resources/a.js", "// adapter\n")
        self.write("scripts/check_adapters.py", "import sys\nraise SystemExit(0 if '--require-private' in sys.argv else 9)\n")
        result = self.run_checks("--staged")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_invalid_python_is_rejected_without_app_build(self):
        self.stage("scripts/example.py", "def broken(\n")
        self.assertNotEqual(self.run_checks("--staged").returncode, 0)


if __name__ == "__main__":
    unittest.main()
