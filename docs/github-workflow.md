# GitHub 工作流备忘

本文记录 SignalBrief 当前阶段的 GitHub 使用方式。目标是先跑通最小协作闭环，再逐步引入 Projects、自动化和更细的分类能力。

## 基本概念

- `Repository`：代码仓库，保存源码、文档、分支、提交、CI 配置和发布记录。
- `Issue`：任务、缺陷、需求或待讨论事项。当前项目用 issue 承接可执行日程。
- `Branch`：一条独立开发线。新功能和修复应从 `main` 切出短生命周期分支。
- `Pull Request`：请求把分支合入 `main`，用于审查代码、运行 CI 和沉淀变更说明。
- `Actions`：GitHub CI/CD 入口，用于运行测试、构建和后续手动部署。
- `Projects`：任务管理面板，可把 issues 和 PR 放进表格、看板或路线图。

当前阶段先使用 `Issues -> Branch -> Commit -> Pull Request -> Actions -> Merge` 的基础链路。Projects、复杂自动化和 GitHub Agents 暂缓。

## 当前约定

项目日程不再只写在 `docs/personal-tech-newsletter-system.md`。长期背景和设计决策继续放在 `docs/` 和 `docs/records/`，可执行任务放到 GitHub Issues。

当前第一批 issues 已拆出：

- `#2`：建立 GitHub 基础工作流与项目日程。
- `#3`：建立新增 RSS 源质量审计清单。
- `#4`：增加文章内容增强模块。
- `#5`：增加抓取失败告警策略和源级健康状态。
- `#6`：增加定时自动生成、归档和发送。
- `#7`：扩展邮件投递能力。
- `#8`：补充 CD 手动部署 workflow。

每个 issue 至少包含：

- 背景：为什么要做。
- 目标：完成后达到什么效果。
- 范围：本次做什么，不做什么。
- 验收标准：如何判断完成。

## Labels

GitHub 仓库默认会提供常见 labels，例如 `bug`、`documentation`、`duplicate`、`enhancement`、`good first issue`、`help wanted`、`invalid`、`question` 和 `wontfix`。

当前项目先使用默认 labels，不创建 `type:*`、`area:*`、`priority:*` 等自定义 labels。原因是项目仍处于个人试运行阶段，过早建立复杂分类会增加维护成本。

默认 labels 的使用建议：

- 文档、规范和流程类任务使用 `documentation`。
- 新功能或能力增强使用 `enhancement`。
- 缺陷修复使用 `bug`。
- 无效或不再计划处理的问题再使用 `invalid` 或 `wontfix`。

后续当 issue 数量明显增加，再评估新增 `area:ai`、`area:mail`、`area:deploy`、`priority:p1` 等自定义 labels。

## Issue Type

`Issue type` 是 GitHub 官方的结构化字段，通常在 Organization 级别管理，例如 `task`、`bug`、`feature`。当前仓库位于个人账号 `xiongxingqi/signal-brief` 下，不依赖 issue type。

不要把 GitHub 官方 `Issue type` 和普通 label 混在一起：

| 名称 | 性质 | 当前项目使用方式 |
| --- | --- | --- |
| Issue type | GitHub 官方字段，偏 Organization 能力 | 暂不使用 |
| `type:*` label | 仓库普通标签，自定义约定 | 暂不创建 |

如果后续仓库迁移到 Organization，再重新评估是否启用官方 issue type。

## Projects

GitHub Projects 是 issues 和 PR 的管理视图，不是代码仓库本身。一个仓库可以关联多个 Project，一个 Project 也可以管理多个仓库的事项。

当前阶段暂不急于创建 `SignalBrief Roadmap` Project。建议在基础 issues 使用稳定后，再创建最小 Project：

- `Backlog`：表格视图，管理优先级和排序。
- `Board`：看板视图，按状态移动任务。
- `Roadmap`：路线图视图，后续再用日期或 iteration 字段规划时间。

第一版 Project 字段保持克制：`Status`、`Priority`、`Area`、`Type` 足够；不要一开始引入过多自动化。

## 开发流程

推荐流程：

```text
选定一个 issue
  -> 从 main 新建分支
  -> 实现或整理文档
  -> 本地运行匹配验证
  -> 提交 commit
  -> 创建 PR
  -> CI 通过
  -> 合并 main
  -> 关闭 issue
```

分支命名示例：

```text
docs/github-workflow
feat/rss-source-audit
feat/article-content-enrichment
ci/manual-deploy
```

提交信息继续遵守 [Git 提交规范](git-commit-convention.md)。如果 PR 完成某个 issue，PR 描述中使用 `Closes #编号` 关联关闭。

## Agent 使用 GitHub

当前 Codex 已接入 GitHub MCP，可在用户明确要求时操作 `xiongxingqi/signal-brief` 仓库。GitHub 写操作包括创建或更新 issue、创建 PR、评论 issue、合并 PR 等，执行前应先说明将操作的对象和目的。

代理操作 GitHub 时遵循：

- 先读后写：创建 issue 前先搜索或列出现有 issue，避免重复。
- 写操作保持最小范围：只改用户要求的 issue、PR 或仓库对象。
- 不创建自定义 labels、Projects、milestones 或仓库设置，除非用户明确要求。
- 不输出、记录或提交 GitHub token。
- 完成后用只读查询验证远端状态，并向用户报告 issue 或 PR 链接。

Codex MCP 与 GitHub MCP 的具体配置见 [Codex MCP 与 GitHub MCP 使用备忘](codex-mcp-github.md)。
