# GitHub 工作流备忘

本文记录 SignalBrief 当前阶段的 GitHub 执行流程。目标是用 Issues 承接可执行任务，并用分支、提交、PR 和 CI 跑通最小协作闭环。

## 当前任务池

项目日程不再只写在 `docs/personal-tech-newsletter-system.md`。长期背景和设计决策继续放在 `docs/` 和 `docs/records/`，可执行任务放到 GitHub Issues。

当前 open 任务：

- `#2`：建立 GitHub 基础工作流与项目日程。
- `#3`：建立新增 RSS 源质量审计清单。
- `#4`：增加文章内容增强模块。
- `#5`：增加抓取失败告警策略和源级健康状态。
- `#6`：增加定时自动生成、归档和发送。
- `#7`：扩展邮件投递能力。
- `#8`：补充 CD 手动部署 workflow。
- `#10`：整理 docs 文档并同步当前项目状态。

`#2` 保持 open，直到至少有一个真实任务通过分支、PR、CI 和合并跑通 issue 关联闭环。

## Issue 编写要求

每个 issue 至少包含：

- 背景：为什么要做。
- 目标：完成后达到什么效果。
- 范围：本次做什么，不做什么。
- 验收标准：如何判断完成。

当前先使用 GitHub 默认 labels，不创建自定义 labels、Projects、milestones 或 issue type。文档类任务可使用 `documentation`，功能增强可使用 `enhancement`，缺陷修复可使用 `bug`。

## 开发流程

推荐流程：

```text
选定一个 issue
  -> 从 main 新建短生命周期分支
  -> 实现或整理文档
  -> 本地运行匹配验证
  -> 提交 commit
  -> 创建 PR
  -> 等待 CI 通过
  -> 合并 main
  -> 关闭 issue
```

分支命名示例：

```text
docs/sync-current-state
feat/rss-source-audit
feat/article-content-enrichment
ci/manual-deploy
```

提交信息继续遵守 [Git 提交规范](git-commit-convention.md)。一个提交尽量只表达一件事。

## PR 约定

PR 描述应包含：

- 关联 issue，例如 `Closes #10` 或 `Refs #2`。
- 本次改动摘要。
- 实际执行过的验证命令和结果。
- 影响范围或不做事项。

如果 PR 完成某个 issue，使用 `Closes #编号`；如果只是推进工作流或关联背景，使用 `Refs #编号`。

## Agent 使用 GitHub

当前 Codex 已接入 GitHub MCP，可在用户明确要求时操作 `xiongxingqi/signal-brief` 仓库。GitHub 写操作包括创建或更新 issue、创建 PR、评论 issue、合并 PR 等，执行前应先说明将操作的对象和目的。

代理操作 GitHub 时遵循：

- 先读后写：创建 issue 前先搜索或列出现有 issue，避免重复。
- 写操作保持最小范围：只改用户要求的 issue、PR 或仓库对象。
- 不创建自定义 labels、Projects、milestones 或修改仓库设置，除非用户明确要求。
- 不输出、记录或提交 GitHub token。
- 完成后用只读查询验证远端状态，并向用户报告 issue 或 PR 链接。

Codex MCP 与 GitHub MCP 的配置和排查见 [Codex MCP 与 GitHub MCP 使用备忘](codex-mcp-github.md)。
