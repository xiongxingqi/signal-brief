# 配置约定

本文档是项目配置的统一入口。新增配置、调整环境变量或修改 profile 行为时，优先同步本文档，再按需要更新 README、部署文档、`.env.example` 和测试。

## 配置分层

- `src/main/resources/application.yaml`：跨环境公共配置、安全默认值和必要配置项的注释示例；不设置 active profile。
- `src/main/resources/application-dev.yaml`：本地开发便利默认值，只放 dev 差异，例如本地 PostgreSQL datasource。
- `src/main/resources/application-prod.yaml`：生产 profile 差异，不写真实地址、账号、密码或密钥。
- `src/test/resources/application-test.yaml`：测试 profile 差异；CI 通过环境变量和 PostgreSQL service 提供基础设施。
- `.env.example`：环境变量示例和说明，不提交真实密钥。
- 部署环境变量文件：例如 `/opt/signal-brief/config/signal-brief.env`，由部署环境维护，不提交到 Git。

## 写法规则

- 普通默认值直接写在 YAML 中，例如 `server.port: 8080`、`signal-brief.ingestion.enabled: false`。
- 不在 YAML 中写 `${ENV:default}` 或 `${ENV}` 做同名环境变量转发；Spring Boot 会自动把环境变量绑定到配置属性。
- Spring Boot 标准能力使用官方属性，例如 `spring.datasource.*`、`spring.mail.*`、`server.port`、`springdoc.*`。
- 项目业务语义统一放在 `signal-brief.*`，并通过 `@ConfigurationProperties` record 建模和校验。
- 启用后才必填、且不适合提交默认值的配置，用 YAML 注释示例和 `.env.example` 展示，不写空字符串默认值。
- `spring.mail.host` 不要写 `host: ""`。该属性只要存在就会触发 `JavaMailSender` 自动配置；未配置 SMTP 时只保留注释示例。
- datasource 在公共 YAML 中只保留 driver 和注释示例；本地 `dev` 可提供便利默认值，`test` 和 `prod` 必须由环境显式提供。
- 可能访问外部系统、产生投递行为或暴露内部入口的能力默认关闭，包括 RSS 定时、内部 API、OpenAPI、AI 摘要和邮件发送。

## 环境变量命名

环境变量使用大写下划线形式。配置项中的 `.` 和 `-` 都转换为 `_`：

| 配置项 | 环境变量 |
| --- | --- |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` |
| `server.port` | `SERVER_PORT` |
| `spring.docker.compose.enabled` | `SPRING_DOCKER_COMPOSE_ENABLED` |
| `springdoc.api-docs.enabled` | `SPRINGDOC_API_DOCS_ENABLED` |
| `springdoc.swagger-ui.enabled` | `SPRINGDOC_SWAGGER_UI_ENABLED` |
| `signal-brief.internal-api.enabled` | `SIGNAL_BRIEF_INTERNAL_API_ENABLED` |
| `signal-brief.ai-summary.max-output-tokens` | `SIGNAL_BRIEF_AI_SUMMARY_MAX_OUTPUT_TOKENS` |

不要使用 `SIGNALBRIEF_INTERNALAPI_ENABLED` 这类压缩写法；统一保留单词边界。

## 必填与校验

- `prod` 和 CI 必须提供 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`；缺失时应在 DataSource、Flyway 或 MyBatis 初始化阶段失败。
- `signal-brief.ai-summary.enabled=true` 时，必须提供 `base-url`、`api-key` 和 `model`；由 `AiSummaryProperties` 校验。
- `signal-brief.mail.enabled=true` 时，必须提供 `from` 和 `recipients`；由 `BriefMailProperties` 校验。SMTP 主机、端口、用户名和密码继续使用 `spring.mail.*`。
- OpenAPI 需要同时开启 `SPRINGDOC_API_DOCS_ENABLED=true` 和 `SPRINGDOC_SWAGGER_UI_ENABLED=true` 才能完整访问 Swagger UI 和 JSON 文档。

## 当前配置清单

### Spring Boot 标准配置

| 配置项 | 环境变量 | 默认 / 要求 |
| --- | --- | --- |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `dev` 有本地默认值；`test`、`prod` 必须提供 |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `dev` 有本地默认值；`test`、`prod` 必须提供 |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | `dev` 有本地默认值；`test`、`prod` 必须提供 |
| `server.port` | `SERVER_PORT` | 默认 `8080` |
| `spring.docker.compose.enabled` | `SPRING_DOCKER_COMPOSE_ENABLED` | 默认 `false` |
| `spring.mail.host` | `SPRING_MAIL_HOST` | 开启邮件发送后按 SMTP 服务要求提供 |
| `spring.mail.port` | `SPRING_MAIL_PORT` | 开启邮件发送后按 SMTP 服务要求提供 |
| `spring.mail.username` | `SPRING_MAIL_USERNAME` | 开启邮件发送后按 SMTP 服务要求提供 |
| `spring.mail.password` | `SPRING_MAIL_PASSWORD` | 开启邮件发送后按 SMTP 服务要求提供 |
| `spring.mail.properties.mail.smtp.auth` | `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` | 示例值见 `.env.example` |
| `spring.mail.properties.mail.smtp.starttls.enable` | `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | 示例值见 `.env.example` |
| `springdoc.api-docs.enabled` | `SPRINGDOC_API_DOCS_ENABLED` | 默认 `false` |
| `springdoc.swagger-ui.enabled` | `SPRINGDOC_SWAGGER_UI_ENABLED` | 默认 `false` |

### SignalBrief 自定义配置

| 配置项 | 环境变量 | 默认 / 要求 |
| --- | --- | --- |
| `signal-brief.internal-api.enabled` | `SIGNAL_BRIEF_INTERNAL_API_ENABLED` | 默认 `false` |
| `signal-brief.ingestion.enabled` | `SIGNAL_BRIEF_INGESTION_ENABLED` | 默认 `false` |
| `signal-brief.ingestion.cron` | `SIGNAL_BRIEF_INGESTION_CRON` | 默认 `0 0 6 1,16 * *` |
| `signal-brief.ai-summary.enabled` | `SIGNAL_BRIEF_AI_SUMMARY_ENABLED` | 默认 `false` |
| `signal-brief.ai-summary.base-url` | `SIGNAL_BRIEF_AI_SUMMARY_BASE_URL` | 开启 AI 摘要后必填 |
| `signal-brief.ai-summary.api-key` | `SIGNAL_BRIEF_AI_SUMMARY_API_KEY` | 开启 AI 摘要后必填 |
| `signal-brief.ai-summary.model` | `SIGNAL_BRIEF_AI_SUMMARY_MODEL` | 开启 AI 摘要后必填 |
| `signal-brief.ai-summary.connect-timeout` | `SIGNAL_BRIEF_AI_SUMMARY_CONNECT_TIMEOUT` | 默认 `3s` |
| `signal-brief.ai-summary.read-timeout` | `SIGNAL_BRIEF_AI_SUMMARY_READ_TIMEOUT` | 默认 `30s`；真实 Provider 试运行建议 `60s` |
| `signal-brief.ai-summary.temperature` | `SIGNAL_BRIEF_AI_SUMMARY_TEMPERATURE` | 默认 `0.2` |
| `signal-brief.ai-summary.max-output-tokens` | `SIGNAL_BRIEF_AI_SUMMARY_MAX_OUTPUT_TOKENS` | 默认 `2000` |
| `signal-brief.mail.enabled` | `SIGNAL_BRIEF_MAIL_ENABLED` | 默认 `false` |
| `signal-brief.mail.from` | `SIGNAL_BRIEF_MAIL_FROM` | 开启邮件发送后必填 |
| `signal-brief.mail.recipients` | `SIGNAL_BRIEF_MAIL_RECIPIENTS` | 开启邮件发送后必填，多个值用逗号分隔 |
| `signal-brief.mail.subject-prefix` | `SIGNAL_BRIEF_MAIL_SUBJECT_PREFIX` | 默认 `SignalBrief 技术半月报` |
| `signal-brief.feed-http.user-agent` | `SIGNAL_BRIEF_FEED_HTTP_USER_AGENT` | 默认 `signal-brief/0.0.1` |
| `signal-brief.feed-http.connect-timeout` | `SIGNAL_BRIEF_FEED_HTTP_CONNECT_TIMEOUT` | 默认 `3s` |
| `signal-brief.feed-http.read-timeout` | `SIGNAL_BRIEF_FEED_HTTP_READ_TIMEOUT` | 默认 `10s` |
| `signal-brief.feed-http.retry.max-attempts` | `SIGNAL_BRIEF_FEED_HTTP_RETRY_MAX_ATTEMPTS` | 默认 `2` |
| `signal-brief.feed-http.retry.backoff` | `SIGNAL_BRIEF_FEED_HTTP_RETRY_BACKOFF` | 默认 `1s` |
| `signal-brief.feeds` | 不建议用环境变量维护 | RSS 源列表，配置文件维护，修改后重启 |

## 新增配置检查清单

- 是否已有 Spring Boot 标准属性？有则优先使用标准属性。
- 是否表达项目业务语义？是则放入 `signal-brief.*`。
- 是否有安全默认值？有则直接写在 YAML 中。
- 是否启用后才必填、敏感或环境相关？用注释示例和 `.env.example` 展示，由运行环境提供。
- 是否会触发自动配置副作用？例如 `spring.mail.host`，不要用空值占位。
- 是否需要配置绑定测试或启用态校验测试？
- 是否同步更新 `.env.example`、README、部署文档和相关 records？
