# Codex MCP 与 GitHub MCP 使用备忘

本文记录本项目维护者如何配置和使用 Codex MCP / GitHub MCP。文档只保存可复用命令和排查步骤，不保存真实 GitHub token、账号私密配置或授权结果。

## 配置位置

Codex MCP 配置写在 `config.toml` 中：

- 用户级配置：`~/.codex/config.toml`
- 项目级配置：`.codex/config.toml`

GitHub MCP 绑定个人 GitHub 授权，优先使用用户级配置，不要提交到项目仓库。配置完成后通常需要重启 Codex 会话。

## 常用命令

查看当前 MCP server：

```bash
codex mcp list
```

查看 GitHub MCP 配置：

```bash
codex mcp get github
```

删除旧配置：

```bash
codex mcp remove github
```

在 Codex TUI 中查看当前会话加载的 MCP server：

```text
/mcp
```

## 推荐配置：Remote + fine-grained PAT

当前项目优先使用 GitHub 官方 Remote MCP Server + fine-grained PAT：

```bash
codex mcp add github \
  --url https://api.githubcopilot.com/mcp/ \
  --bearer-token-env-var GITHUB_MCP_PAT
```

当前维护环境已按该方式接入 GitHub MCP，并可通过 MCP 操作 `xiongxingqi/signal-brief` 的 issues、PR 等对象。GitHub 操作规则见 [GitHub 工作流备忘](github-workflow.md)。

## PAT 权限

在 GitHub 页面进入：

```text
Settings
  -> Developer settings
  -> Personal access tokens
  -> Fine-grained tokens
  -> Generate new token
```

建议设置：

```text
Token name: signal-brief-codex-mcp
Expiration: 30 days 或 90 days
Repository access: Only select repositories
Selected repository: xiongxingqi/signal-brief
```

第一阶段只代工 Issues 时，最小权限为：

```text
Metadata: Read
Issues: Read and write
```

如果后续要操作 PR，再增加：

```text
Pull requests: Read and write
Contents: Read
```

如果后续要管理 GitHub Projects，再单独评估 Projects 相关权限。

## 本地环境变量

临时配置当前 shell：

```bash
export GITHUB_MCP_PAT='你的_token'
```

只验证变量存在，不要打印真实 token：

```bash
echo "${GITHUB_MCP_PAT:+SET}"
```

输出 `SET` 表示当前 shell 已有变量。长期使用时，可放入个人 shell 配置，例如 `~/.bashrc`。不要写入项目 `.env`，也不要提交到仓库。

## 配置步骤

如果之前已经添加过失败的 GitHub MCP 配置，先删除：

```bash
codex mcp remove github
```

重新添加 Remote + PAT：

```bash
codex mcp add github \
  --url https://api.githubcopilot.com/mcp/ \
  --bearer-token-env-var GITHUB_MCP_PAT
```

确认配置：

```bash
codex mcp get github
```

输出中应看到：

```text
bearer_token_env_var: GITHUB_MCP_PAT
```

PAT 方式不需要执行 `codex mcp login github`。

启动 Codex 推荐顺序：

```bash
export GITHUB_MCP_PAT='你的_token'
codex
```

如果是在已有 Codex 会话里新加环境变量，当前会话可能读不到。退出 Codex 后，在同一个 shell 中重新进入。

## 不采用的路径

当前不使用 Remote OAuth：

```bash
codex mcp add github --url https://api.githubcopilot.com/mcp/
codex mcp login github
```

在当前 Codex CLI 下可能失败：

```text
Dynamic registration failed:
Dynamic client registration not supported
```

遇到该错误时，改用 Remote + PAT。除非后续明确需要 OAuth，否则不维护本地 Docker 版 GitHub MCP 配置。

## 安全规则

- 不把 PAT 写入仓库、文档、issue、PR 或聊天记录。
- 优先使用 fine-grained PAT，并限制到当前仓库。
- 按任务阶段逐步增加权限，不一次性给全量 `repo` 权限。
- 设置过期时间，建议 30 天或 90 天。
- token 泄露或不再使用时，立即在 GitHub 中撤销。
- 让 Agent 执行 GitHub 写操作前，先说明要创建或修改哪些对象。

## 排查清单

看不到 GitHub MCP 工具时，按顺序检查：

```bash
codex mcp list
codex mcp get github
echo "${GITHUB_MCP_PAT:+SET}"
```

如果配置正确但当前会话仍没有工具，退出并重新进入 Codex。

如果报 `401` 或授权失败，优先检查：

- `GITHUB_MCP_PAT` 是否在启动 Codex 的 shell 中存在。
- PAT 是否过期或被撤销。
- PAT 是否选中了 `xiongxingqi/signal-brief` 仓库。
- PAT 是否包含当前操作需要的权限，例如 `Issues: Read and write`。

如果报 `Dynamic client registration not supported`，说明走到了 remote OAuth 登录路径，改用 Remote + PAT。

## 参考资料

- Codex MCP 文档：`https://developers.openai.com/codex/codex-manual.md`
- GitHub MCP Server：`https://github.com/github/github-mcp-server`
