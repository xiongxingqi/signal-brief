# 简报归档与邮件发送基础记录

> 本文档记录手动归档和手动邮件发送的当前设计。实现细节以源码、测试和迁移脚本为准。

## 范围

当前覆盖：

- 手动生成并归档 AI 摘要简报。
- 保存每次 AI 简报生成尝试，包括 Markdown 草稿、AI 摘要、状态和失败摘要。
- 手动发送指定归档简报邮件。
- 按收件人保存每次发送尝试的结果。
- 查询最近简报归档、单个归档详情和指定归档的邮件发送记录。
- 邮件正文第一版使用 AI 摘要 Markdown 纯文本。

当前不覆盖：

- 定时自动生成和自动发送。
- HTML 邮件模板。
- 邮件重试队列。
- 用户订阅系统。
- 通用任务运行系统和告警系统。

## 数据表

`brief_generation` 保存每次 AI 简报生成尝试。同一时间窗口允许多次生成，便于保留重试、失败和不同输出版本。状态取值：

- `GENERATING`：已创建归档记录，正在调用 AI Provider。
- `SUCCESS`：AI 摘要生成成功，已保存 `summary_markdown` 和完成时间。
- `FAILED`：AI 摘要生成失败，已保存失败摘要和完成时间。

`brief_mail_delivery` 保存指定归档简报的邮件发送结果，每个收件人一条记录。每次调用发送接口都是一次新的发送尝试。状态取值：

- `PENDING`：发送记录已创建，尚未完成发送。
- `SENT`：该收件人发送成功，已保存发送时间。
- `FAILED`：该收件人发送失败，已保存失败摘要。

新增业务表和重要字段的 Flyway 迁移必须写 PostgreSQL `COMMENT ON`，至少说明表用途、状态字段语义、大文本字段内容和关键关联字段含义。

## 内部 API

`POST /internal/briefs/ai-summary/archives` 按 `[startInclusive, endExclusive)` 时间窗口生成确定性 Markdown 草稿，调用 AI Provider 生成摘要，并保存到 `brief_generation`。

- AI 摘要未启用时返回 `503 Service Unavailable`，不创建归档。
- Provider 调用失败或响应异常时创建 `FAILED` 归档，返回 `502 Bad Gateway`，响应包含 `briefGenerationId`。
- 请求体缺失、字段缺失或时间窗口非法时返回 `400 Bad Request`。

`POST /internal/briefs/{id}/mail-deliveries` 发送指定 `SUCCESS` 归档简报。

- 归档不存在时返回 `404 Not Found`。
- 归档状态不是 `SUCCESS` 时返回 `409 Conflict`。
- 邮件功能未启用或发送器不可用时返回 `503 Service Unavailable`。
- 开启邮件但发件人或收件人缺失属于启动期配置错误，应用应尽早失败，不进入发送接口。
- 前置条件满足后，即使部分或全部收件人发送失败，接口整体仍返回 `200 OK`，失败写入每个 `brief_mail_delivery` 记录。

`GET /internal/briefs/archives` 查询最近简报归档，支持 `limit` 参数，默认 20，最大 100，结果按最新记录倒序返回。

`GET /internal/briefs/archives/{id}` 查询单个简报归档。归档不存在时返回 `404 Not Found`。

`GET /internal/briefs/archives/{id}/mail-deliveries` 查询指定归档的邮件发送记录。归档不存在时返回 `404 Not Found`；归档存在但未发送过邮件时返回空列表。

这些接口都受 `signal-brief.internal-api.enabled` 控制；错误响应不暴露 API key、SMTP 密钥、Provider 原始响应或堆栈。

## 配置

业务邮件配置位于 `signal-brief.mail.*`：

- `signal-brief.mail.enabled`：邮件发送开关，默认关闭。
- `signal-brief.mail.from`：邮件发件人，开启邮件发送时必填。
- `signal-brief.mail.recipients`：邮件收件人列表，开启邮件发送时必填。
- `signal-brief.mail.subject-prefix`：邮件主题前缀，默认 `SignalBrief 技术半月报`。

SMTP 主机、端口、用户名、密码和 TLS 等基础设施配置继续使用 Spring Boot 标准 `spring.mail.*`，不要放进 `signal-brief.mail.*`。真实凭据只放本地 `.env` 或部署环境变量，不写入仓库。

## 后续方向

- 增加定时自动生成、归档和发送。
- 增加 HTML 邮件模板和失败重试队列。
- 增加 AI token 用量、调用耗时和邮件发送耗时统计。
