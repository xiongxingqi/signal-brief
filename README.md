# SignalBrief

SignalBrief 是一个面向 Java 后端开发者的个人资讯聚合与 AI 摘要工具。项目目标是从官方源和一手源中抓取技术与行业资讯，完成去重、分类、摘要生成、邮件推送和归档，最终输出结构清晰、来源可追溯的中文技术半月报。

> 当前仓库已完成 Spring Boot 工程骨架、RSS 抓取入库、定时采集、候选文章查询、Markdown 简报草稿生成、基于 OpenAI-compatible Chat Completions 的 AI 摘要手动生成、手动归档、手动邮件发送，以及归档和邮件发送记录查询基础；定时自动发送、HTML 邮件和重试队列仍未实现。

## 目标流程

```text
官方源 / 一手源
-> RSS / Atom 抓取
-> 去重与时间过滤
-> 分类汇总
-> Markdown 简报草稿
-> AI 摘要/润色
-> 归档与执行日志
-> 邮件推送
```

MVP 优先跑通：

```text
RSS 抓取 -> 去重过滤 -> 候选文章查询 -> Markdown 简报草稿 -> AI 摘要/润色 -> 手动归档 -> 手动邮件发送
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
- Spring RestClient：RSS 抓取和 AI Provider 调用
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
./mvnw -Dspring.docker.compose.enabled=false test
```

运行本地基础测试。显式关闭 Spring Boot Docker Compose 接管，避免本地环境变量覆盖默认配置。

```bash
./mvnw verify
```

运行完整 Maven 验证流程，包括 Failsafe 管理的 `*IT` 集成测试。该命令默认交给 CI 执行；本地只有在专门排查数据库、迁移或 Mapper 问题时才运行。

```bash
./mvnw package
```

构建应用产物，输出到 `target/`。

## 配置说明

配置分层、环境变量命名、必填项校验和新增配置检查清单统一见 [docs/configuration.md](docs/configuration.md)。本地私密配置放在 `.env`，共享示例放在 `.env.example`，不要提交真实数据库、SMTP 或 AI Provider 密钥。

核心配置文件：

- `src/main/resources/application.yaml`：跨环境公共配置、安全默认值和必要配置项的注释示例。
- `src/main/resources/application-dev.yaml`：本地开发便利默认值。
- `src/main/resources/application-prod.yaml`：生产 profile 差异，不保存真实基础设施值。
- `src/test/resources/application-test.yaml`：测试 profile 差异。
- `.env.example`：环境变量示例和说明。
- `docs/deployment.md`：阿里云内网试运行部署、备份、升级和回滚手册。

关键原则：

- Profile 由外部显式指定，例如 `SPRING_PROFILES_ACTIVE=dev|test|prod`；不要在 `application.yaml` 中设置 `spring.profiles.active`。
- 普通默认值直接写在 YAML 中，不为同名环境变量额外写 `${ENV:default}` 转发。
- Spring Boot 标准配置使用官方属性和标准环境变量，例如 `spring.datasource.*` / `SPRING_DATASOURCE_*`。
- 项目业务配置统一放在 `signal-brief.*`，环境变量使用大写下划线形式，例如 `SIGNAL_BRIEF_INTERNAL_API_ENABLED`。
- 手动触发、OpenAPI、定时任务、AI 和邮件等可能产生副作用或暴露入口的开关默认关闭。

## 测试策略

本地默认只运行基础测试：

```bash
./mvnw -Dspring.docker.compose.enabled=false test
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
SPRINGDOC_API_DOCS_ENABLED=true \
SPRINGDOC_SWAGGER_UI_ENABLED=true \
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

生成指定窗口的 AI 摘要简报。该接口还需要显式开启 `SIGNAL_BRIEF_AI_SUMMARY_ENABLED=true`，并提供 AI Provider 的 `base-url`、`api-key` 和 `model`：

```bash
curl -X POST http://localhost:8080/internal/briefs/ai-summary \
  -H 'Content-Type: application/json' \
  -d '{
    "startInclusive": "2026-07-01T00:00:00Z",
    "endExclusive": "2026-07-16T00:00:00Z"
  }'
```

生成并归档指定窗口的 AI 摘要简报。AI 未启用时返回 `503` 且不创建归档；Provider 调用失败时会创建 `FAILED` 归档并返回 `502` 和 `briefGenerationId`：

```bash
curl -X POST http://localhost:8080/internal/briefs/ai-summary/archives \
  -H 'Content-Type: application/json' \
  -d '{
    "startInclusive": "2026-07-01T00:00:00Z",
    "endExclusive": "2026-07-16T00:00:00Z"
  }'
```

发送指定归档简报邮件。该接口需要启用 `SIGNAL_BRIEF_MAIL_ENABLED=true`，配置收件人、发件人和有效 `spring.mail.*`；每次调用都会创建新的发送尝试，部分收件人失败时接口整体仍返回 `200`，失败写入每个 delivery 记录：

```bash
curl -X POST http://localhost:8080/internal/briefs/100/mail-deliveries
```

查询简报归档和邮件发送记录：

```bash
curl 'http://localhost:8080/internal/briefs/archives?limit=20'
curl http://localhost:8080/internal/briefs/archives/100
curl http://localhost:8080/internal/briefs/archives/100/mail-deliveries
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
- [RSS 入库运行记录](docs/records/rss-ingestion-run-record.md)
- [Markdown 简报生成记录](docs/records/markdown-brief-generation.md)
- [AI 摘要生成记录](docs/records/ai-summary-generation.md)
- [简报归档与邮件发送基础记录](docs/records/brief-archive-mail-delivery.md)
- [内部手动触发 API 记录](docs/records/manual-trigger-api.md)
- [事务边界与状态机实践](docs/transaction-boundaries-and-state-machines.md)
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
