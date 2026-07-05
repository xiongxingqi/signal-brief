# 内部手动触发 API 记录

> 本文档记录内部 REST API 和 OpenAPI 文档的当前设计。实现细节以源码和测试为准。

## 定位

内部手动触发 API 用于本地调试、补偿执行和后续运维调用，让维护者可以不等待定时任务，主动触发 RSS 入库或生成指定时间窗口的 Markdown 简报草稿。

当前已经覆盖：RSS 入库手动触发、Markdown 草稿手动生成、默认关闭的内部 API、默认关闭的 OpenAPI / Swagger UI、基础错误响应。

当前暂不覆盖：认证授权、任务运行记录、AI 摘要、邮件发送、简报归档、CLI 入口和多实例运行锁。

## 当前接口

`POST /internal/ingestions/rss` 触发一次 `FeedIngestionService.ingestEnabledFeeds()`，返回 `FeedIngestionResult` 入库统计。该入口不受 `signal-brief.ingestion.enabled` 影响，因为该配置只控制定时任务是否注册；单个 RSS 源失败仍通过统计结果体现，不映射为整次 HTTP 500。

`POST /internal/briefs/markdown` 按请求中的 `startInclusive` 和 `endExclusive` 调用 `BriefGenerationService.generate(...)`，返回时间窗口和 Markdown 文本。时间窗口继续使用 `[startInclusive, endExclusive)` 语义，窗口合法性由查询链路兜底，避免 Controller 复制业务规则。

请求示例：

```json
{
  "startInclusive": "2026-07-01T00:00:00Z",
  "endExclusive": "2026-07-16T00:00:00Z"
}
```

## 配置

- `signal-brief.internal-api.enabled`：内部 API 开关，对应环境变量 `SIGNAL_BRIEF_INTERNAL_API_ENABLED`，默认 `false`。
- `springdoc.api-docs.enabled` 和 `springdoc.swagger-ui.enabled`：OpenAPI / Swagger UI 开关，对应环境变量 `SIGNAL_BRIEF_OPENAPI_ENABLED`，默认 `false`。
- OpenAPI JSON 路径为 `/internal/api-docs/internal`。
- Swagger UI 路径为 `/internal/swagger-ui.html`。

OpenAPI 使用 `GroupedOpenApi` 注册 internal 分组，匹配 `/internal/**` 和 `cn.name.celestrong.signalbrief.internal`。后续如新增对外 API，应增加独立 public 分组，例如 `/api/**` 或 `/v1/**`，不要通过全局扫描配置让内部文档影响公开接口。

## 模块边界

- `internal/ManualTriggerController`：HTTP 入口，只做请求解析、服务调用和响应返回。
- `internal/ManualTriggerExceptionHandler`：把内部 API 的请求错误和服务异常映射为 HTTP 响应。
- `internal/MarkdownBriefRequest`、`internal/MarkdownBriefResponse`、`internal/InternalApiErrorResponse`：描述内部 API 契约。
- `internal/OpenApiConfiguration`：提供 OpenAPI 元信息和 internal 分组。

业务逻辑仍保留在 `ingestion`、`brief` 和 `article` 等模块中，Controller 不实现抓取、查询、渲染或去重逻辑。

## 错误处理

- 请求体缺失、字段缺失或时间格式非法，返回 `400 Bad Request`。
- 时间窗口非法，例如结束时间不晚于开始时间，返回 `400 Bad Request`。
- 未预期服务异常返回 `500 Internal Server Error`，响应中只暴露简短中文错误说明，不输出堆栈。
- RSS 单源抓取失败保持失败隔离，通过入库统计体现。

## 测试与验证

当前测试覆盖：

- `ManualTriggerControllerTest`：内部 API 成功路径、请求解析和错误映射。
- `ManualTriggerControllerConditionTest`：内部 API 默认关闭时 Controller 不注册。
- `OpenApiConfigurationTest`：OpenAPI 默认关闭和开启后的 internal 分组。
- `InternalApiPropertiesTest`：内部 API 配置默认值和绑定。
- `ApplicationConfigurationTest`：默认配置可在无 profile 的测试上下文中加载。

本地基础验证：

```bash
./mvnw -o -Dspring.docker.compose.enabled=false test
```

## 维护约束

- 内部 API 是运维和调试入口，不应直接当作公开 API 暴露。
- 内部 API 和 OpenAPI 默认必须保持关闭，部署或本地调试时显式开启。
- 对外暴露前应先补充 Header Token 或 Spring Security 等保护措施。
- 新增手动触发接口时，继续复用应用服务，不要把业务逻辑下沉到 Controller。
- 多实例部署前，应评估任务运行记录、幂等锁或运行中任务保护。

## 后续方向

- 增加内部 API 鉴权。
- 引入任务运行记录，保存触发时间、统计结果和失败原因。
- 接入 AI 摘要、邮件发送和简报归档后，扩展对应手动触发入口。
- 按部署形态评估多实例运行锁和重复触发保护。
