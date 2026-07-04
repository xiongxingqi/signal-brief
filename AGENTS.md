# Repository Guidelines

## 项目概览

SignalBrief 是 Spring Boot 4 + Maven 服务，运行目标为 Java 25。当前核心能力是 RSS / Atom 抓取、解析、去重入库、定时触发和简报候选文章查询；AI 摘要、Markdown 简报、邮件推送和归档仍属于后续阶段。项目主语言为中文，面向维护者的文档、注释、日志、异常说明、Issue、PR、评审意见、提交摘要和代理回复默认使用中文；代码标识符、配置键、协议字段和通用技术术语可保留英文。

## 项目结构

- `src/main/java/cn/name/celestrong/signalbrief`：应用源码，按 `config`、`feed`、`article`、`ingestion` 等业务边界组织。
- `src/main/resources`：应用配置、Flyway 迁移、模板和静态资源；数据库迁移放在 `db/migration`。
- `src/test/java`：测试源码；普通单元测试命名为 `*Test`，数据库和 Mapper 集成测试命名为 `*IT`。
- `src/test/resources`：测试配置和 fixture，例如 `fixtures/rss`。
- `docs/`：项目说明和协作规范；`docs/records/` 只保存长期设计记录和维护备忘。

## 常用命令

- `./mvnw spring-boot:run`：本地启动应用，默认 `dev` profile。
- `./mvnw -Dspring.docker.compose.enabled=false test`：本地基础测试，日常开发和提交前默认使用。
- `./mvnw verify`：完整验证并运行 Failsafe 管理的 `*IT` 集成测试；默认交给 CI，本地仅在排查数据库、迁移或 Mapper 问题时运行。
- `./mvnw package`：构建应用产物到 `target/`。
- `docker compose up -d postgres`：启动本地 PostgreSQL；端口或凭据变化时同步 `.env` 与 datasource 配置。

## 编码约定

Java 代码以 [docs/java-coding-standard.md](docs/java-coding-standard.md) 为准，上下文保存以 [docs/preserving-context-in-code.md](docs/preserving-context-in-code.md) 为准。优先沿用当前技术边界：Spring Boot、MyBatis、Flyway、ROME、PostgreSQL、Spring `RestClient`、Apache Commons 和 Guava。Spring Boot 已管理版本的依赖不要显式声明版本。

新增代码应放在最贴近的现有包中，优先使用构造器注入和 `final` 依赖。固定取值使用枚举，例如文章分类使用 `ArticleCategory`。RSS / Atom 内容保持 `InputStream` 交给 XML 解析器处理编码；不要在解析前强制转成 `String`。数据库结构变更必须通过 Flyway 迁移表达，并同步更新 record、Mapper 和测试。

## 测试约定

纯业务逻辑优先写不启动 Spring 上下文的 JUnit 5 单元测试。涉及数据库、迁移、MyBatis 映射或 SQL 排序窗口的行为，用 `*IT` 覆盖并交给 CI 运行。CI 使用独立 PostgreSQL service，并设置 `SPRING_DOCKER_COMPOSE_ENABLED=false`，避免测试进程接管本地 `compose.yaml`。

## 文档与记录

项目说明见 [docs/personal-tech-newsletter-system.md](docs/personal-tech-newsletter-system.md)。RSS 入库、调度和查询出口等长期维护记录放在 `docs/records/`。records 文档只沉淀背景、关键决策、验证结论和后续方向，不保留大段过程计划、执行清单或源码片段；精确实现以源码、测试和迁移脚本为准。

## 提交与协作

Git 提交规范以 [docs/git-commit-convention.md](docs/git-commit-convention.md) 为准，采用 Conventional Commits，例如 `feat(feed): 支持 RSS 源解析`、`fix(article): 修正去重判断`、`docs: 更新本地运行说明`。一个提交尽量只表达一件事，破坏性变更必须明确标注。

代理协作时不要主动提交代码；只有用户明确要求“提交”时才执行 `git add` 和 `git commit`。提交前必须重新运行与改动范围匹配的验证命令，并在回复中说明实际执行结果。遇到用户未提交的改动，必须保留并基于现状继续工作，不要擅自回滚。

## 安全与配置

不要提交真实密钥。本地凭据放在 `.env`，共享示例放在 `.env.example`。datasource、SMTP、AI provider 等敏感配置通过环境变量注入。新增环境变量时同步更新 `.env.example`，只提供安全示例值。
