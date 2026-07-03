# Repository Guidelines

## 项目结构与模块组织

本仓库是 Spring Boot 4 + Maven 服务，目标运行环境为 Java 25。主代码位于 `src/main/java/cn/name/celestrong/signalbrief`，`SignalBriefApplication` 是应用入口。运行配置放在 `src/main/resources/application.yaml`，环境差异配置放在 `application-dev.yaml`、`application-prod.yaml` 等 profile 文件中。Flyway 迁移和回调目录预留在 `src/main/resources/db/migration` 与 `src/main/resources/db/callback`。静态资源和模板如需新增，分别放入 `src/main/resources/static` 和 `src/main/resources/templates`。测试代码位于 `src/test/java`，测试配置位于 `src/test/resources/application-test.yaml`，项目说明文档放在 `docs/`。

## 构建、测试与本地运行

- `./mvnw spring-boot:run`：本地启动应用；默认使用 `dev` profile，除非设置 `SPRING_PROFILES_ACTIVE`。
- `./mvnw test`：运行 Maven Surefire 管理的单元测试。
- `./mvnw verify`：执行完整验证流程，并运行 Failsafe 集成测试，例如 `*IT`。
- `./mvnw package`：构建应用产物，输出到 `target/`。
- `docker compose up -d postgres`：启动 `compose.yaml` 中定义的 PostgreSQL 服务；如端口映射变化，同步调整 datasource 环境变量。

## 编码风格与命名规范

Java 编码规范以 [docs/java-coding-standard.md](docs/java-coding-standard.md) 为准。代码优先保证正确性、安全性和可读性；新增抽象必须降低真实复杂度，不为局部“优雅”破坏项目一致性。Java 使用四空格缩进，禁止通配符 import，控制语句建议始终写花括号。遵循常见 Spring 命名：`*Controller`、`*Service`、`*Repository` 或 MyBatis `*Mapper`、`*Config`、`*Properties`。新增代码放在基础包 `cn.name.celestrong.signalbrief` 或其子包下，确保组件扫描生效。优先使用构造器注入，必需依赖使用 `final` 字段。

代码上下文保存规则以 [docs/preserving-context-in-code.md](docs/preserving-context-in-code.md) 为准。命名要表达业务语义，避免 `data`、`info`、`process`、`handle` 等低信息量名称。状态、类型、渠道、权限等固定取值优先使用枚举；避免魔法值、长参数列表和裸布尔参数。复杂判断优先使用具名变量、提取方法、规则对象或参数对象保存业务意图；注释用于说明原因、约束和取舍，不翻译代码本身。

YAML 配置按 Spring namespace 分组，新增环境变量需同步写入 `.env.example`，只提供安全示例值。

## 测试规范

测试使用 JUnit 5 和 Spring Boot Test。快速单元测试命名为 `*Test`，集成测试命名为 `*IT`；当前 Failsafe 配置会在 `verify` 阶段运行集成测试。测试 fixture、profile 配置和测试专用资源放在 `src/test/resources`。涉及数据库、迁移或外部服务的改动，应补充聚焦的集成测试，并说明所需环境变量。

## Commit 与 PR 规范

Git 提交规范以 [docs/git-commit-convention.md](docs/git-commit-convention.md) 为准。提交信息采用 Conventional Commits：

```text
<type>[optional scope][!]: <description>
```

优先使用 `feat`、`fix`、`docs`、`style`、`refactor`、`perf`、`test`、`build`、`ci`、`chore`、`revert`。`scope` 可用于标明影响模块；破坏性变更必须用 `!`、`BREAKING CHANGE:` 或 `BREAKING-CHANGE:` 标识。一个提交尽量只表达一件事，`description` 要简短明确，不以句号结尾。

项目主语言为中文。面向维护者的文档、注释、日志、异常说明、Issue、PR 描述、评审意见、提交摘要和代理回复默认使用中文；代码标识符、配置键、协议字段和通用技术术语可保留英文。

PR 应包含变更目的、关键实现点、关联 issue（如有）和已运行的验证命令。涉及 profile、环境变量、迁移、定时任务、邮件或数据库行为的改动必须明确说明。仅在修改模板或静态 UI 资源时附截图。

## 安全与配置提示

不要提交真实密钥。本地凭据放在 `.env`，共享默认值放在 `.env.example`。datasource、SMTP、AI provider 等配置优先通过环境变量注入。
