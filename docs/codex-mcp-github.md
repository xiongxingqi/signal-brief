# Codex MCP 与 GitHub MCP 使用备忘

本文记录当前项目使用 Codex MCP 的常用命令，以及接入 GitHub MCP 的推荐配置。它是本项目维护者的操作备忘，不保存真实 GitHub token、账号私密配置或授权结果。

## 基本概念

MCP Server 是 Agent 的工具适配器，不是大模型本体。大模型只在上下文里看到工具名称、说明和参数结构，并决定是否发起 tool call；真正执行调用的是 Codex 运行时和 MCP Client。

一次 GitHub issue 创建链路大致是：

```text
用户请求
  -> 模型选择 create_issue 工具并填参数
  -> Codex 运行时调用 MCP Client
  -> GitHub MCP Server 调 GitHub API
  -> GitHub 返回结果
  -> Codex 汇总结果给用户
```

## Codex MCP 配置位置

Codex MCP 配置写在 `config.toml` 中：

- 用户级配置：`~/.codex/config.toml`
- 项目级配置：`.codex/config.toml`

GitHub MCP 绑定个人 GitHub 授权，优先使用用户级配置，不要提交到项目仓库。

Codex CLI 和 IDE extension 共享配置。配置完成后，通常需要重启 Codex 会话，新的 MCP 工具才会出现在工具列表中。

## 常用命令

查看当前 MCP server：

```bash
codex mcp list
```

查看某个 server 配置：

```bash
codex mcp get github
```

添加 Streamable HTTP MCP server：

```bash
codex mcp add github --url https://api.githubcopilot.com/mcp/
```

添加带 Bearer token 环境变量的 HTTP MCP server：

```bash
codex mcp add github \
  --url https://api.githubcopilot.com/mcp/ \
  --bearer-token-env-var GITHUB_MCP_PAT
```

删除 MCP server：

```bash
codex mcp remove github
```

对支持 OAuth 的 MCP server 登录：

```bash
codex mcp login github
```

退出 OAuth 登录：

```bash
codex mcp logout github
```

在 Codex TUI 中查看当前会话加载的 MCP server：

```text
/mcp
```

## GitHub MCP 推荐方案：Remote + fine-grained PAT

当前项目优先使用 GitHub 官方 Remote MCP Server + fine-grained PAT：

```text
Codex
  -> https://api.githubcopilot.com/mcp/
  -> GitHub API
```

推荐原因：

- 不依赖本地 Docker。
- 比 remote OAuth 更容易在 Codex CLI 中跑通。
- fine-grained PAT 可以限制到 `xiongxingqi/signal-brief` 单仓库和最小权限。

当前维护环境已按该方式接入 GitHub MCP，并可通过 MCP 操作 `xiongxingqi/signal-brief` 的 issues、PR 等对象。使用规则见 [GitHub 工作流备忘](github-workflow.md)。

### 创建 PAT

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

如果后续要操作 PR，可再增加：

```text
Pull requests: Read and write
Contents: Read
```

如果后续要管理 GitHub Projects，再单独评估 Projects 相关权限。

### 配置环境变量

临时配置当前 shell：

```bash
export GITHUB_MCP_PAT='你的_token'
```

只验证变量存在，不要打印真实 token：

```bash
echo "${GITHUB_MCP_PAT:+SET}"
```

输出 `SET` 即表示当前 shell 已有变量。

长期使用时，可放入个人 shell 配置，例如 `~/.bashrc`。不要写入项目 `.env`，也不要提交到仓库。

### 配置 Codex MCP

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

应看到：

```text
bearer_token_env_var: GITHUB_MCP_PAT
```

PAT 方式不需要执行 `codex mcp login github`。

### 重启 Codex

如果是在终端中启动 Codex，推荐顺序是：

```bash
export GITHUB_MCP_PAT='你的_token'
codex
```

如果已经在 Codex 会话中添加了环境变量，当前会话可能读不到新变量。退出 Codex 后，在同一个 shell 中重新进入。

## 不推荐直接使用 Remote OAuth

下面这种配置只提供远程 MCP 地址：

```bash
codex mcp add github --url https://api.githubcopilot.com/mcp/
codex mcp login github
```

在当前 Codex CLI 下可能失败：

```text
Dynamic registration failed:
Dynamic client registration not supported
```

含义是 Codex 发现 server 支持 OAuth 后尝试动态注册 OAuth client，但 GitHub Remote MCP 不支持该动态注册方式。它不是仓库权限问题，也不是 GitHub 账号密码问题。

如果一定要用 OAuth，优先考虑本地 Docker 版 GitHub MCP Server。

## 备选方案：本地 Docker 版 GitHub MCP + OAuth

本地 Docker 版不是连接 GitHub Remote MCP，而是在本机启动官方 GitHub MCP Server：

```text
Codex
  -> 本地 Docker 容器里的 github-mcp-server
  -> GitHub API
```

配置示例：

```bash
codex mcp remove github
```

```bash
codex mcp add github \
  --env GITHUB_OAUTH_CALLBACK_PORT=8085 \
  -- docker run -i --rm \
  -p 127.0.0.1:8085:8085 \
  -e GITHUB_OAUTH_CALLBACK_PORT \
  ghcr.io/github/github-mcp-server
```

关键点：

- `-p 127.0.0.1:8085:8085` 只把回调端口暴露到本机。
- `GITHUB_OAUTH_CALLBACK_PORT=8085` 告诉本地 MCP Server 使用固定 OAuth 回调端口。
- 该方案依赖 Docker，并且首次授权、端口占用、容器网络问题排查成本更高。

本项目第一阶段优先使用 Remote + PAT。

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

如果报 `Dynamic client registration not supported`，说明走到了 remote OAuth 登录路径。改用 Remote + PAT，或者使用本地 Docker 版 OAuth。

## 参考资料

- Codex MCP 文档：`https://developers.openai.com/codex/codex-manual.md`
- GitHub MCP Server：`https://github.com/github/github-mcp-server`
