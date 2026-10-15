# 统一检查入口

手动验证、提交钩子、巡检均使用 `scripts/project_checks.py`，不各自维护触发正则或重复调用同一组门禁。App 编译/测试、发版与真实设备验收仍按对应流程执行。

## 使用

```powershell
# 预览范围，不执行、不表示通过
python scripts/project_checks.py --changed --plan --json
# 已跟踪改动；未跟踪的新文件请显式列出或暂存后检查
python scripts/project_checks.py --changed
python scripts/project_checks.py --files AGENTS.md docs/agents/workflow.md
# 提交钩子使用暂存区；完整检查只在明确需要时使用
python scripts/project_checks.py --staged
python scripts/project_checks.py --full
# 正式发版涉及适配时
python scripts/project_checks.py --full --require-private
```

正常只输出一条摘要；失败展示有限报错，完整结果保存在 `build_qa/checks/`。`--json` 给工具消费，`--verbose` 仅在摘要不足以定位时使用。未触发项目列为 `notSelected`；没有输入时输出 `NO-CHANGE/executed=false/passed=null`，均不能算已执行并通过。

## 范围与一致性

- 文件名红线、会话文件排除、当前新增行密钥扫描、diff 空白检查始终针对本次改动执行；密钥只显示位置和规则名。
- Markdown 改动检查本地引用与代码围栏，脚本改动检查 Python/JavaScript 语法。
- 各扫描器实际覆盖目录、脚本与基线的触发关系只维护在 `project_checks.py` 的 `affected/CHECKS` 中；UI、配色、适配、版本分别按真实输入选择。
- 修改统一调度器或提交钩子时运行全部静态门禁，防止调度规则改坏后所有检查都被跳过。不因此运行 App 编译。
- `--staged` 的红线、正文、文档、语法检查读取暂存版本；原有项目扫描器读工作区，因此相关输入存在未暂存修改时拒绝给出成功结果，不能用新工作区内容掩盖旧暂存内容。
- 统一入口不持久缓存成功。agent/持续巡检可复用相同内容与环境的已验证证据，但必须记录来源，输入变化就重新运行；不能把 `--plan`、`notSelected` 或 `NO-SOURCE` 当成功。
- 不使用 `--no-verify`、放宽基线、默认 `--rerun-tasks` 作为省时手段。钩子缺 Python 时明确失败，不能跳过安全门后继续提交。

## 门禁自身的回归验证

修改入口、钩子或 IMA CLI 时运行对应测试，不为业务文案改动执行本节：

```powershell
python -m unittest discover -s scripts/tests -p test_project_checks.py -q
node --test scripts/tests/ima_kb.test.cjs
```

测试使用独立临时 Git 仓库与离线 API 响应，覆盖范围调度、真实 Git hook、密钥不回显、部分暂存、失败传递与读取限长，不访问用户知识库或修改主仓库暂存区。
