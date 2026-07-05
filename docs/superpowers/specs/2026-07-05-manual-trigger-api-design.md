# 手动触发 API 设计

## 背景

当前项目已经具备 RSS / Atom 入库、定时调度、候选文章查询和 Markdown 简报草稿生成能力，但缺少一个可主动调用的入口。手动触发 API 用于本地调试、补偿执行和后续运维调用，让维护者可以无需等到定时任务触发就执行入库或生成指定时间窗口的简报草稿。

## 目标

- 提供内部 REST API 触发一次 RSS 入库。
- 提供内部 REST API 按半开时间窗口生成 Markdown 简报草稿。
- 提供默认关闭的 OpenAPI / Swagger UI 文档，方便本地管理和调试内部接口。
- 默认关闭内部 API，避免生产环境或本地误暴露。
- 复用现有业务服务，不在 Controller 中实现抓取、查询、渲染或去重逻辑。

## 非目标

- 不引入 Spring Security、登录态、权限体系或用户管理。
- 不实现邮件发送、AI 摘要、简报归档或任务运行表。
- 不改变现有定时入库开关语义。
- 不在本次实现命令行触发入口。

## 接口设计

新增内部接口统一放在 `/internal` 路径下，并通过配置控制是否注册。

### 触发 RSS 入库

`POST /internal/ingestions/rss`

该接口触发一次 `FeedIngestionService.ingestEnabledFeeds()`。它不受 `signal-brief.ingestion.enabled` 影响，因为该配置只表示定时任务是否注册。

响应返回入库统计，字段以 `FeedIngestionResult` 当前模型为准，包含总源数量、成功源数量、失败源数量和保存文章数量等信息。单个 RSS 源失败仍沿用现有失败隔离语义，不导致整次请求失败。

### 生成 Markdown 简报

`POST /internal/briefs/markdown`

请求体：

```json
{
  "startInclusive": "2026-07-01T00:00:00Z",
  "endExclusive": "2026-07-16T00:00:00Z"
}
```

该接口调用 `BriefGenerationService.generate(startInclusive, endExclusive)`，返回时间窗口和 Markdown 文本。时间窗口继续使用 `[startInclusive, endExclusive)` 语义，窗口合法性由现有查询链路兜底，避免 Controller 复制业务规则。

## 配置设计

新增配置：

```yaml
signal-brief:
  internal-api:
    enabled: ${SIGNAL_BRIEF_INTERNAL_API_ENABLED:false}

springdoc:
  api-docs:
    enabled: ${SIGNAL_BRIEF_OPENAPI_ENABLED:false}
    path: /internal/api-docs
  swagger-ui:
    enabled: ${SIGNAL_BRIEF_OPENAPI_ENABLED:false}
    path: /internal/swagger-ui.html
    urls:
      - name: internal
        url: /internal/api-docs/internal
    urls-primary-name: internal
```

内部 API 和 OpenAPI 文档默认值都为 `false`。本地需要启用时显式设置：

```bash
SPRING_PROFILES_ACTIVE=dev \
SIGNAL_BRIEF_INTERNAL_API_ENABLED=true \
SIGNAL_BRIEF_OPENAPI_ENABLED=true \
./mvnw spring-boot:run
```

OpenAPI 文档使用 `springdoc-openapi-starter-webmvc-ui`，版本由 `pom.xml` 属性集中声明。Spring Boot 不管理 springdoc 版本，因此这里允许显式声明版本。Swagger UI 路径放在 `/internal` 下，当前只展示 internal 分组。

不要使用全局 `springdoc.paths-to-match` 或 `springdoc.packages-to-scan` 限制扫描范围。当前通过 `GroupedOpenApi` 注册 internal 分组，匹配 `/internal/**` 和 `cn.name.celestrong.signalbrief.internal`；后续如新增对外 API，再增加 public 分组，例如匹配 `/api/**` 或 `/v1/**`，避免内部接口文档配置影响公开接口文档。

## 模块边界

新增 `internal` 包承载 HTTP 入口、请求对象、响应对象和错误处理。该包只负责协议适配：接收 HTTP 请求、转换参数、调用应用服务、返回 HTTP 响应。

新增 `internal/OpenApiConfiguration` 承载 OpenAPI 元信息和 internal 分组配置。Controller 和 DTO 可以使用 Swagger annotation 补充接口摘要、请求字段和错误响应说明，但这些注解只描述 HTTP 契约，不承载业务逻辑。

现有边界保持不变：

- `ingestion` 继续负责 RSS 入库编排。
- `brief` 继续负责 Markdown 简报生成。
- `article` 继续负责文章查询和窗口校验。

## 错误处理

- 请求体缺失或时间字段格式非法，返回 `400 Bad Request`。
- 时间窗口非法，例如结束时间不晚于开始时间，返回 `400 Bad Request`。
- 未预期服务异常返回 `500 Internal Server Error`，响应中只暴露简短中文错误说明，不输出堆栈。
- RSS 单源抓取失败不映射为 HTTP 500，继续通过入库统计体现。

## 测试策略

第一版以不依赖数据库的 Web 层测试为主：

- 内部 API 开启时，RSS 入库接口可用并委托 `FeedIngestionService`。
- 内部 API 开启时，Markdown 接口可解析时间窗口并返回 Markdown 内容。
- 内部 API 关闭时，相关 Controller 不注册。
- 非法请求体、非法时间格式和非法时间窗口返回 `400 Bad Request`。
- OpenAPI 配置开启时注册 internal 分组，默认关闭时不注册自定义文档配置。
- 本地完整测试需要至少验证 springdoc 依赖与 Spring Boot 4 当前版本可编译、可启动测试上下文。

本地基础验证命令：

```bash
./mvnw -Dspring.docker.compose.enabled=false test
```

## 后续方向

- 增加 Header Token 或接入 Spring Security，保护内部接口。
- 引入任务运行记录，保存手动触发时间、统计结果和失败原因。
- 接入 AI 摘要、邮件发送和简报归档后，扩展对应手动触发接口。
- 如未来部署多实例，再评估幂等锁或运行中任务保护。
