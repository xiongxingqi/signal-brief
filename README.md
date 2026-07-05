# SignalBrief

SignalBrief 是一个面向 Java 后端开发者的个人资讯聚合与 AI 摘要工具。项目目标是从官方源和一手源中抓取技术与行业资讯，完成去重、分类、摘要生成、邮件推送和归档，最终输出结构清晰、来源可追溯的中文技术半月报。

> 当前仓库已完成 Spring Boot 工程骨架、RSS 抓取入库、定时采集、候选文章查询和 Markdown 简报草稿生成；AI 摘要、邮件推送和归档仍在开发中。

## 目标流程

```text
官方源 / 一手源
-> RSS / Atom 抓取
-> 去重与时间过滤
-> 分类汇总
-> AI 摘要生成
-> Markdown 简报
-> 邮件推送
-> 归档与执行日志
```

MVP 优先跑通：

```text
RSS 抓取 -> 去重过滤 -> 候选文章查询 -> Markdown 简报草稿 -> AI 摘要/润色 -> 邮件推送
```

## 技术栈

- Java 25
- Spring Boot 4.0.7
- Maven Wrapper
- PostgreSQL
- Flyway
- MyBatis
- ROME：RSS / Atom 解析
- jsoup：HTML 解析兜底
- Spring Mail：邮件推送
- Spring Scheduling：定时任务

## 环境要求

- JDK 25
- Docker 与 Docker Compose
- Bash 或兼容 shell

## 快速开始

复制本地环境变量示例：

```bash
cp .env.example .env
```

启动 PostgreSQL：

```bash
docker compose up -d postgres
```

默认会把 PostgreSQL 映射到宿主机 `5432` 端口；如需改端口，在 `.env` 中设置 `POSTGRES_PORT`，并同步调整 `SPRING_DATASOURCE_URL`。

启动应用：

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

应用启动必须显式设置 `SPRING_PROFILES_ACTIVE`，本地开发通常使用 `dev`。

## 常用命令

```bash
./mvnw test
```

运行本地基础测试。日常开发和提交前默认只跑这一条。

```bash
./mvnw verify
```

运行完整 Maven 验证流程，包括 Failsafe 管理的 `*IT` 集成测试。该命令默认交给 CI 执行；本地只有在专门排查数据库、迁移或 Mapper 问题时才运行。

```bash
./mvnw package
```

构建应用产物，输出到 `target/`。

## 配置说明

核心配置文件：

- `src/main/resources/application.yaml`：跨环境公共配置、Flyway 路径、线程池配置和默认关闭的业务开关。
- `src/main/resources/application-dev.yaml`：本地开发配置。
- `src/main/resources/application-prod.yaml`：生产环境配置。
- `src/test/resources/application-test.yaml`：测试环境配置。
- `.env.example`：环境变量示例，不应包含真实密钥。

本地私密配置放在 `.env`，不要提交真实数据库、SMTP 或 AI Provider 密钥。

环境变量配置原则：

- Profile 由外部环境显式指定，例如 `SPRING_PROFILES_ACTIVE=dev|test|prod`；不要在 `application.yaml` 中设置 `spring.profiles.active`。
- `application.yaml` 只放跨环境公共配置和安全默认值，例如默认关闭的业务开关。
- `application-dev.yaml` 可以提供匹配本地 Docker Compose 的便利默认值；`application-prod.yaml` 和 `application-test.yaml` 对 datasource、端口等基础设施配置保持必填，占位符不写默认值。
- 新增环境变量时同步更新 `.env.example` 和本文档；敏感值只写变量名，不写真实密钥。
- 手动触发、OpenAPI、定时任务等可能产生副作用或暴露接口的开关默认关闭，需要运行时显式开启。

常用环境变量：

- `SIGNAL_BRIEF_INGESTION_ENABLED`：RSS 定时入库开关，默认 `false`。
- `SIGNAL_BRIEF_INGESTION_CRON`：RSS 入库 cron，默认 `0 0 6 1,16 * *`。
- `SIGNAL_BRIEF_FEED_HTTP_USER_AGENT`：RSS 抓取请求的 User-Agent，默认 `signal-brief/0.0.1`。
- `SIGNAL_BRIEF_FEED_HTTP_CONNECT_TIMEOUT`：RSS 抓取连接超时，默认 `3s`。
- `SIGNAL_BRIEF_FEED_HTTP_READ_TIMEOUT`：RSS 抓取读取超时，默认 `10s`。
- `SIGNAL_BRIEF_FEED_HTTP_RETRY_MAX_ATTEMPTS`：RSS 抓取总尝试次数，默认 `2`。
- `SIGNAL_BRIEF_FEED_HTTP_RETRY_BACKOFF`：RSS 抓取重试间隔，默认 `1s`。
- `SIGNAL_BRIEF_INTERNAL_API_ENABLED`：内部手动触发 API 开关，默认 `false`。
- `SIGNAL_BRIEF_OPENAPI_ENABLED`：OpenAPI / Swagger UI 文档开关，默认 `false`。

## 测试策略

本地默认只运行基础测试：

```bash
./mvnw test
```

数据库集成测试由 GitHub Actions 执行，CI 使用独立 PostgreSQL service，并设置 `SPRING_DOCKER_COMPOSE_ENABLED=false`，避免测试进程管理本地 `compose.yaml`。

### RSS 源配置

RSS 源通过 `signal-brief.feeds` 配置。仓库默认配置包含第一批官方或一手 RSS / Atom 源：

```yaml
signal-brief:
  ingestion:
    enabled: false
    cron: "0 0 6 1,16 * *"
  feeds:
    - name: Spring Blog
      url: https://spring.io/blog.atom
      category: FRAMEWORK
      enabled: true
    - name: Inside Java
      url: https://inside.java/feed.xml
      category: JAVA
      enabled: true
    - name: Kubernetes Blog
      url: https://kubernetes.io/feed.xml
      category: FRAMEWORK
      enabled: true
    - name: OpenAI News
      url: https://openai.com/news/rss.xml
      category: AI
      enabled: true
```

RSS 源默认 `enabled=true`，但定时任务默认关闭，因此普通启动不会自动访问外部站点。只有开启定时任务或调用内部手动触发接口时才会抓取真实源。修改源配置后需要重启应用。

RSS 入库任务默认关闭，避免本地启动时自动访问外部源。如需开启：

```bash
SPRING_PROFILES_ACTIVE=dev SIGNAL_BRIEF_INGESTION_ENABLED=true ./mvnw spring-boot:run
```

默认 cron 为 `0 0 6 1,16 * *`，表示每月 1 日和 16 日 06:00 执行。可通过 `SIGNAL_BRIEF_INGESTION_CRON` 覆盖。

### 内部手动触发 API

内部 API 和 OpenAPI 文档默认关闭。如需本地调试或手动补偿执行，启动时显式开启：

```bash
SPRING_PROFILES_ACTIVE=dev \
SIGNAL_BRIEF_INTERNAL_API_ENABLED=true \
SIGNAL_BRIEF_OPENAPI_ENABLED=true \
./mvnw spring-boot:run
```

Swagger UI 地址：

```text
http://localhost:8080/internal/swagger-ui.html
```

Internal 分组 OpenAPI JSON 地址：

```text
http://localhost:8080/internal/api-docs/internal
```

触发一次 RSS 入库，响应中包含本次运行的 `runId`：

```bash
curl -X POST http://localhost:8080/internal/ingestions/rss
```

查询 RSS 入库运行记录：

```bash
curl http://localhost:8080/internal/ingestions/rss/runs
curl http://localhost:8080/internal/ingestions/rss/runs/12
```

生成指定窗口的 Markdown 简报草稿：

```bash
curl -X POST http://localhost:8080/internal/briefs/markdown \
  -H 'Content-Type: application/json' \
  -d '{
    "startInclusive": "2026-07-01T00:00:00Z",
    "endExclusive": "2026-07-16T00:00:00Z"
  }'
```

## 项目结构

```text
.
├── docs/                         # 项目文档与协作规范
├── src/main/java/                # 应用源码
├── src/main/resources/           # 应用配置、迁移、模板、静态资源
├── src/test/java/                # 测试源码
├── src/test/resources/           # 测试配置与资源
├── compose.yaml                  # 本地 PostgreSQL 服务
├── pom.xml                       # Maven 项目配置
└── AGENTS.md                     # 贡献者与代理协作指南
```

## 文档

- [立项文档](docs/personal-tech-newsletter-system.md)
- [RSS 抓取入库备忘](docs/records/rss-ingestion.md)
- [RSS 入库任务化与文章查询出口记录](docs/records/2026-07-04-rss-ingestion-scheduling.md)
- [Markdown 简报生成记录](docs/records/markdown-brief-generation.md)
- [内部手动触发 API 记录](docs/records/manual-trigger-api.md)
- [Git 提交规范](docs/git-commit-convention.md)
- [贡献者指南](AGENTS.md)

## 协作约定

项目交流语言为中文。提交信息遵循 [Git 提交规范](docs/git-commit-convention.md)，采用 Conventional Commits，例如：

```text
feat(feed): add rss source parser
fix(mail): handle empty recipient list
docs: update local setup guide
```

提交前至少运行与改动范围匹配的验证命令；涉及数据库、定时任务、邮件或外部接口的改动应补充必要说明和测试。
