# 简报归档与邮件发送基础设计

## 背景

SignalBrief 当前已经具备 RSS 入库、候选文章查询、Markdown 简报草稿生成，以及基于 OpenAI-compatible Chat Completions 的 AI 摘要手动生成能力。AI 摘要目前只通过内部接口返回，不落库、不发送邮件，也没有发送状态记录。

下一步目标是把 MVP 主链路继续推进到“可追踪、可重发、可排查”的阶段：

```text
RSS 入库 -> Markdown 草稿 -> AI 摘要 -> 归档 -> 邮件发送
```

第一版采用“先归档，后发送”的两步链路。生成简报时保存归档记录；发送邮件时只读取一份已成功归档的简报，不重新查询文章、不重新生成 Markdown，也不重新调用 AI。

## 目标

- 保存每次 AI 简报生成尝试，包括成功和失败。
- 同一个时间窗口允许多次生成，每次生成都是独立记录。
- 保存 Markdown 草稿、AI 摘要、生成状态和失败摘要。
- 支持通过内部 API 发送指定归档简报。
- 支持多个收件人配置化发送。
- 按收件人保存邮件发送记录，支持部分失败排查。
- 邮件正文第一版使用 AI 摘要 Markdown 纯文本。

## 非目标

- 不做复杂 Web 管理后台。
- 不做订阅用户和权限系统。
- 不做 HTML 邮件模板。
- 不做定时自动发送。
- 不做邮件重试队列。
- 不抽象通用任务运行系统。
- 不记录 token 用量和 Provider 详细计费信息。

## 数据模型

新增 `brief_generation` 表保存一次简报生成尝试。

建议字段：

- `id`：主键。
- `start_inclusive`：候选文章时间窗口开始，包含。
- `end_exclusive`：候选文章时间窗口结束，不包含。
- `status`：生成状态，取值为 `GENERATING`、`SUCCESS`、`FAILED`。
- `draft_markdown`：确定性 Markdown 草稿。
- `summary_markdown`：AI 摘要 Markdown，失败时可为空。
- `error_summary`：失败摘要，成功时为空。
- `created_at`：创建时间。
- `updated_at`：更新时间。
- `completed_at`：生成完成时间，成功或失败时写入。

`start_inclusive + end_exclusive` 不加唯一约束，允许同一时间窗口生成多条记录，便于对比不同 AI 输出和保留失败历史。

Flyway 迁移脚本需要为新增表和关键字段补充 PostgreSQL `COMMENT ON` 说明，解释表用途、状态字段语义和大文本字段内容，避免后续维护只靠 Java 类型推断数据库含义。

新增 `brief_mail_delivery` 表保存邮件发送结果。

建议字段：

- `id`：主键。
- `brief_generation_id`：关联 `brief_generation(id)`。
- `recipient`：收件人邮箱。
- `status`：发送状态，取值为 `PENDING`、`SENT`、`FAILED`。
- `subject`：邮件主题。
- `error_summary`：失败摘要，成功时为空。
- `created_at`：创建时间。
- `updated_at`：更新时间。
- `sent_at`：发送成功时间。

每次发送请求按收件人创建多条发送记录。单个收件人失败不阻塞其他收件人。

## 模块边界

`brief` 模块继续负责简报生成和归档：

- `BriefArchiveService`：编排生成并保存一次 AI 简报。
- `BriefGenerationMapper`：读写 `brief_generation`。
- `BriefGenerationStatus`：表达生成状态。

`mail` 模块负责邮件投递：

- `BriefMailDeliveryService`：读取已成功归档简报，并发送给配置收件人。
- `BriefMailDeliveryMapper`：读写 `brief_mail_delivery`。
- `BriefMailProperties`：绑定邮件开关、发件人、收件人和主题前缀。
- `BriefMailSender`：隔离邮件发送边界，生产实现使用 `JavaMailSender`。

`internal` 模块只做 HTTP 协议适配：

- 复用已有内部 API 开关。
- Controller 校验请求结构和路径参数，业务判断委托服务层。
- 错误响应不暴露 Provider 原始响应、SMTP 密钥、堆栈或完整异常链。

## 归档流程

新增接口：

```text
POST /internal/briefs/ai-summary/archives
```

请求体复用 `MarkdownBriefRequest`：

```json
{
  "startInclusive": "2026-07-01T00:00:00Z",
  "endExclusive": "2026-07-16T00:00:00Z"
}
```

服务流程：

1. 校验时间窗口。
2. 检查 AI 摘要能力是否启用；未启用时返回 `503 Service Unavailable`，不创建归档记录。
3. 生成确定性 Markdown 草稿。
4. 插入 `brief_generation`，状态为 `GENERATING`，保存时间窗口和草稿。
5. 调用 AI 摘要服务。
6. AI 成功时更新状态为 `SUCCESS`，保存 `summary_markdown` 和 `completed_at`。
7. Provider 调用失败或响应异常时更新状态为 `FAILED`，保存 `error_summary` 和 `completed_at`，然后向 HTTP 层抛出可映射异常。

AI Provider 调用失败时保留失败归档记录。接口返回 `502 Bad Gateway`，响应里可带 `briefGenerationId` 和通用错误信息，便于调用方继续查询归档记录。AI 摘要功能未启用属于配置前置条件失败，不生成草稿、不创建归档。

## 邮件发送流程

新增接口：

```text
POST /internal/briefs/{id}/mail-deliveries
```

服务流程：

1. 查询 `brief_generation`。
2. 不存在时返回 `404 Not Found`。
3. 状态不是 `SUCCESS` 时返回 `409 Conflict`。
4. 邮件功能未启用、发件人或收件人未配置时返回 `503 Service Unavailable`。
5. 基于时间窗口和主题前缀生成邮件主题。
6. 按配置收件人逐个创建 `PENDING` 记录。
7. 每个收件人独立发送 AI 摘要 Markdown 纯文本。
8. 发送成功更新为 `SENT`，写入 `sent_at`。
9. 发送失败更新为 `FAILED`，写入错误摘要。

只要前置条件满足，接口整体返回 `200 OK`，响应中包含每个收件人的发送结果。即使部分或全部收件人发送失败，也返回 `200 OK`，因为发送尝试已经完成并被记录；失败细节通过响应和 `brief_mail_delivery` 表表达。

## 配置

新增项目配置：

```yaml
signal-brief:
  mail:
    enabled: ${SIGNAL_BRIEF_MAIL_ENABLED:false}
    from: ${SIGNAL_BRIEF_MAIL_FROM:}
    recipients: ${SIGNAL_BRIEF_MAIL_RECIPIENTS:}
    subject-prefix: ${SIGNAL_BRIEF_MAIL_SUBJECT_PREFIX:SignalBrief 技术半月报}
```

约束：

- 邮件功能默认关闭。
- `enabled=true` 时，`from` 和 `recipients` 必填。
- `recipients` 使用逗号分隔，绑定为列表并过滤空白。
- SMTP 主机、端口、用户名、密码和 TLS 等配置继续使用 Spring Boot 标准 `spring.mail.*`。
- 新增环境变量需要同步 `.env.example`、README 和项目说明文档。

## 错误处理

生成归档：

- 请求体缺失或时间窗口非法：`400 Bad Request`。
- AI 摘要未启用：`503 Service Unavailable`，不创建归档。
- Provider 调用失败或响应异常：保存失败记录后返回 `502 Bad Gateway`。
- 错误响应包含简短错误信息和可选归档 ID，不返回 Provider 原始响应。

邮件发送：

- 简报不存在：`404 Not Found`。
- 简报状态不是 `SUCCESS`：`409 Conflict`。
- 邮件功能不可用或配置不完整：`503 Service Unavailable`。
- 单个收件人发送失败：该收件人记录为 `FAILED`，接口整体仍返回 `200 OK`。

错误摘要需要截断，避免数据库保存过长异常或敏感信息。

## 测试策略

单元测试：

- `BriefArchiveServiceTest`：成功归档、AI 失败保存失败记录、时间窗口委托、错误摘要截断。
- `BriefMailDeliveryServiceTest`：多收件人逐个发送、部分失败不阻塞、状态不是 `SUCCESS` 时拒绝发送、邮件配置缺失时拒绝发送。
- `BriefMailPropertiesTest`：默认关闭、开启后必填、收件人列表解析。

集成测试：

- `BriefGenerationMapperIT`：插入、更新、按 ID 查询和同窗口多记录。
- `BriefMailDeliveryMapperIT`：发送记录插入、状态更新、按简报 ID 查询。

Web 切片测试：

- `ManualTriggerControllerTest`：归档接口成功、AI 失败返回归档 ID、发送接口成功、404、409、503 和部分失败响应。
- `ManualTriggerControllerConditionTest`：内部 API 开关继续控制新增接口注册。

本地基础测试继续使用：

```bash
./mvnw -Dspring.docker.compose.enabled=false test
```

涉及 Flyway、Mapper 和 PostgreSQL 行为的 `*IT` 继续交给 CI 的 `verify` 执行，本地只在排查数据库问题时运行完整验证。

## 后续方向

- 增加按归档 ID 查询简报详情的内部接口。
- 增加按归档 ID 查询邮件发送记录的内部接口。
- 增加手动重发某个失败收件人的接口。
- 增加定时自动生成、归档和发送。
- 增加 HTML 邮件模板。
- 增加 AI token 用量、调用耗时和邮件发送耗时统计。
