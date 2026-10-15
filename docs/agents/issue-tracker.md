# Issue 与 PR 操作

Issues 位于 GitHub `qiqqqqq517/shangkeschedule`，`gitee` 仅镜像。使用已登录的 `gh`；通用授权、凭据与重试见 [开发流程](workflow.md)。

## 按需读取

```powershell
# 先取列表元数据，使用 label/state/limit 收敛范围
# 不默认拉全量正文、评论和 diff
gh issue list --state open --limit 20 --json number,title,labels,updatedAt
gh issue view <number> --json number,title,body,labels,state
# 确实需要讨论上下文时才取评论
gh issue view <number> --comments
```

裸 `#编号` 先根据上下文判断 Issue/PR；无法确定时查询其中一种，明确“类型不匹配”再查另一种，网络/鉴权错误不能当类型依据。PR 仅在与当前任务相关时读 diff；本项目不把外部 PR 自动当功能请求。

## 写入与引用

仅在用户明确授权操作对应 Issue/PR，或明确调用且已说明写入范围的 Skill 时创建、评论、标记、认领、关闭；“查看/分析”不含写入授权，不把 Skill 的泛用措辞当成用户授权。

- 多行正文写 UTF-8 临时文件，使用 `--body-file`，不在 shell 内拼接复杂正文。
- 创建前按标题/关键词查重，复用已有事项；只更新状态发生变化的内容。
- 分诊角色见 [标签表](triage-labels.md)，未查到的标签不擅自创建。
- GitHub PR 创建成功或用户要求继续处理已有 PR 时，按宿主工具要求附加到当前聊天；普通引用不附加。

```powershell
gh issue create --title '<标题>' --body-file '<正文.md>'
gh issue comment <number> --body-file '<评论.md>'
gh issue edit <number> --add-label '<标签>'
gh issue close <number>
```

## Wayfinding（仅明确使用时）

地图用单个 `wayfinder:map` Issue，子任务用 GitHub sub-issues；不可用时退回地图任务列表与 `Part of #编号` 引用。依赖优先使用 GitHub 原生关系，数据库 ID 现场查，不混用 Issue 编号或 node_id。

仅查询当前地图的子任务与未解决阻塞，不扫描仓库全部事项。认领、发结论、关闭和更新地图均属于写入，遵循上面的授权。若接口细节不明，按任务查询官方契约，不预加载整套 API 文档。
