# AI 摘要生成记录

> 本文档记录 AI 摘要生成的当前设计。实现细节以源码和测试为准。

## 定位

AI 摘要生成用于把确定性的 Markdown 简报草稿压缩和润色为中文摘要 Markdown，既可通过手动接口直接返回，也可保存为简报归档并作为邮件正文输入。

当前已具备手动生成和手动归档链路；归档与邮件发送细节见 [简报归档与邮件发送基础记录](brief-archive-mail-delivery.md)。第一版仍不记录 token 用量，也不引入多 Provider 路由。

## 当前范围

- 通过 `signal-brief.ai-summary` 配置 OpenAI-compatible Chat Completions Provider。
- 默认关闭，开启后必须配置 `base-url`、`api-key` 和 `model`。
- 使用专用 `aiSummaryRestClient` 调用 `POST {base-url}/chat/completions`。
- `POST /internal/briefs/ai-summary` 基于时间窗口生成 Markdown 草稿和 AI 摘要。
- `POST /internal/briefs/ai-summary/archives` 基于时间窗口生成 Markdown 草稿和 AI 摘要，并保存到 `brief_generation`。
- Provider HTTP 错误、访问失败和响应结构异常统一转换为 `AiSummaryException`。

## 模块边界

- `config/AiSummaryProperties`：绑定 AI 摘要开关、Provider 地址、模型和生成参数。
- `config/AiSummaryConfiguration`：装配 AI 专用 `RestClient`，不复用 RSS feed HTTP 配置。
- `ai/OpenAiCompatibleAiSummaryClient`：封装 Chat Completions 请求和响应解析。
- `ai/AiSummaryService`：检查开关、构造 prompt，并把 Markdown 草稿交给客户端。
- `brief/AiBriefGenerationService`：先生成确定性 Markdown 草稿，再调用 AI 摘要服务。
- `brief/BriefArchiveService`：保存 AI 摘要生成尝试，成功时写入摘要，失败时写入失败摘要。
- `internal/ManualTriggerController`：只做 HTTP 协议适配和响应映射。

## Prompt 约束

- 使用中文，面向 Java 后端开发者。
- 基于 Markdown 草稿生成摘要版 Markdown。
- 保留草稿中已有 Markdown 链接的 URL，不伪造新链接，不改变链接目标。
- 不编造来源、版本、日期或影响范围。
- 摘要重点关注变更、影响和维护者需要采取的行动。

AI 只处理 Markdown 草稿，不参与候选文章查询、分类分组和来源组织。

## 错误处理

- AI 摘要未启用时，内部 API 返回 `503 Service Unavailable`。
- Provider HTTP 失败、访问失败或响应异常时，内部 API 返回 `502 Bad Gateway`。
- 归档接口中，AI 未启用时不创建归档；Provider 失败时创建 `FAILED` 归档并在响应中返回 `briefGenerationId`。
- 错误响应不暴露 API key、请求正文、Provider 原始响应或堆栈。
- 第一版不自动重试，避免隐藏重复调用外部 Provider。

## 后续方向

- 使用真实 Provider 验证 prompt、输出结构、超时和 token 参数。
- 增加 AI 调用耗时、token 用量和失败统计。
- 增加按归档 ID 查询简报详情的内部接口。
- 根据实际 Provider 支持情况新增 Responses API 客户端实现，不破坏 Chat Completions 兼容路径。
