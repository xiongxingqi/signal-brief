# SignalBrief

SignalBrief 是一个面向 Java 后端开发者的个人资讯聚合与 AI 摘要工具。项目目标是从官方源和一手源中抓取技术与行业资讯，完成去重、分类、摘要生成、邮件推送和归档，最终输出结构清晰、来源可追溯的中文技术半月报。

> 当前仓库处于项目初始化阶段，已完成 Spring Boot 工程骨架、基础配置、PostgreSQL Compose 配置和项目文档沉淀；核心业务模块仍在开发中。

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
RSS 抓取 -> 去重过滤 -> AI 分类汇总 -> Markdown 简报 -> 邮件推送
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

如果手动启动 Compose，请用 `docker compose ps` 确认 PostgreSQL 映射到宿主机的端口，并在 `.env` 中调整 `SPRING_DATASOURCE_URL`。

启动应用：

```bash
./mvnw spring-boot:run
```

默认 profile 为 `dev`，可通过环境变量切换：

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

## 常用命令

```bash
./mvnw test
```

运行单元测试。

```bash
./mvnw verify
```

运行完整 Maven 验证流程，包括 Failsafe 管理的 `*IT` 集成测试。

```bash
./mvnw package
```

构建应用产物，输出到 `target/`。

## 配置说明

核心配置文件：

- `src/main/resources/application.yaml`：通用配置、profile 默认值、Flyway 路径、线程池配置。
- `src/main/resources/application-dev.yaml`：本地开发配置。
- `src/main/resources/application-prod.yaml`：生产环境配置。
- `src/test/resources/application-test.yaml`：测试环境配置。
- `.env.example`：环境变量示例，不应包含真实密钥。

本地私密配置放在 `.env`，不要提交真实数据库、SMTP 或 AI Provider 密钥。

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
