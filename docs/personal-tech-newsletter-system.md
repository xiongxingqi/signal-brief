# SignalBrief 项目说明

SignalBrief 是面向 Java 后端开发者的个人资讯聚合与中文技术半月报系统。项目目标不是抓取尽可能多的信息，而是从官方源和一手源中提取高信号内容，完成采集、去重、分类、摘要、邮件推送和归档。

核心链路：

```text
官方源 / 一手源 -> RSS / Atom 抓取 -> 去重入库 -> 候选文章查询 -> Markdown 简报草稿 -> AI 摘要/润色 -> 归档 -> 邮件推送
```

`Signal` 表示从噪声里保留有效信号，`Brief` 表示最终输出短、清楚、来源可追溯的简报。当前目标读者是项目维护者本人，优先服务稳定的个人半月报工作流。

## 当前阶段

项目已经完成 Spring Boot 工程骨架和 RSS 入库基础能力。当前代码已具备：

- 通过 `signal-brief.feeds` 配置 RSS / Atom 源。
- 已内置第一批官方或一手 RSS / Atom 源，包括 Spring Blog、Inside Java、Kubernetes Blog 和 OpenAI News。
- 使用 Spring `RestClient` 抓取 feed 内容，feed 客户端底层使用 Apache HttpClient 5。
- 通过 `signal-brief.feed-http` 配置 User-Agent、连接超时、读取超时和有限重试。
- 使用 ROME 解析 RSS / Atom 条目。
- RSS / Atom 入库会保存短摘要和清洗后的正文片段。当前简报展示仍以短摘要为主，正文片段主要为后续 AI 摘要输入增强准备。
- 按 guid、url、contentHash 去重。
- 使用 MyBatis 写入 PostgreSQL `article` 表。
- 通过 Flyway 管理数据库迁移。
- 通过 `signal-brief.ingestion` 控制定时入库任务。
- 通过 `ArticleQueryService` 查询后续简报候选文章。
- 持久化 RSS 入库运行记录和源级明细，记录触发方式、状态、耗时、统计和失败诊断信息。
- 基于候选文章生成确定性的 Markdown 简报草稿。
- 基于确定性 Markdown 草稿调用 OpenAI-compatible Chat Completions Provider 生成中文 AI 摘要。
- 保存每次 AI 简报生成尝试，包括 Markdown 草稿、AI 摘要、生成状态和失败摘要。
- 基于已成功归档的简报手动发送邮件，并按收件人记录发送结果。
- 通过内部 REST API 手动触发 RSS 入库、查询 RSS 运行记录、生成 Markdown 简报草稿、生成 AI 摘要、归档 AI 摘要、发送归档邮件，并查询简报归档和邮件发送记录；接口文档通过 OpenAPI / Swagger UI 管理。

定时自动发送、HTML 邮件模板、邮件重试队列、用户订阅系统和运行告警仍属于后续阶段。

## 信息源策略

第一阶段优先使用官方 RSS、官方博客、Release Notes、安全公告和稳定文档源。没有 RSS 的站点先作为候选源记录，后续再评估是否用 jsoup 做低频 HTML 解析。

当前分类以 `ArticleCategory` 为准：

| 分类 | 内容范围 |
| --- | --- |
| `JAVA` | JDK、JEP、JVM、语言特性 |
| `FRAMEWORK` | Spring、中间件、数据库、云原生组件 |
| `INDUSTRY` | 云厂商、开源生态、监管政策、平台动态 |
| `CAREER` | 工程实践、团队协作、招聘和职场趋势 |
| `AI` | AI 工具、模型平台、API、价格和能力变化 |

系统应优先保留原文链接。英文资料不做逐句翻译，而是理解核心变化后输出中文摘要、影响和建议。

## 模块边界

当前主要代码边界如下：

- `config`：绑定项目配置，例如 RSS 源、入库调度和 AI 摘要配置。
- `content`：提供通用 HTML 片段清洗能力，供 feed 内容和后续网页正文处理复用。
- `feed`：定义 feed 获取与解析边界，屏蔽 HTTP 客户端和 RSS 解析细节。
- `article`：文章模型、去重、入库和候选查询。
- `ingestion`：RSS 入库编排和定时触发。
- `brief`：基于候选文章生成 Markdown 简报草稿，编排 AI 摘要生成，并保存简报生成归档。
- `ai`：封装 OpenAI-compatible Chat Completions 调用、prompt 约束和 AI 摘要错误。
- `mail`：基于已成功归档的简报发送纯文本邮件，并按收件人记录发送状态。
- `internal`：默认关闭的内部手动触发 API 和 OpenAPI 文档配置，只做 HTTP 协议适配。

长期上，AI 摘要、简报生成、邮件发送和归档应继续保持独立边界，避免把抓取、生成和投递逻辑混在一个服务里。

## 数据与去重

当前 `article` 表保存来源名称、来源 URL、分类、标题、文章 URL、guid、发布时间、摘要、正文片段、内容哈希和创建更新时间。

`summary` 表示用于简报展示的短摘要，`content_text` 保存 RSS / Atom 正文片段清洗后的纯文本。历史文章的 `content_text` 允许为空；重复文章重新抓取时只补齐为空的 `summary` / `content_text`，不覆盖已有非空内容。`content_text` 不参与现有 `content_hash` 计算，避免改变历史去重口径。

RSS 入库运行记录由 `rss_ingestion_run` 和 `rss_ingestion_source_run` 保存。运行级记录包含触发方式、状态、时间和抓取/入库/跳过/失败统计；源级记录包含单个源的状态、统计、失败类型、HTTP 状态、尝试次数和错误摘要。内部 API 支持查询最近 RSS 运行记录和单次运行明细。

简报归档由 `brief_generation` 保存，每条记录代表一次 AI 简报生成尝试，包含时间窗口、确定性 Markdown 草稿、AI 摘要 Markdown、生成状态、失败摘要和完成时间。生成状态为 `GENERATING`、`SUCCESS` 或 `FAILED`；同一时间窗口允许多次生成，便于保留重试和失败历史。

邮件发送由 `brief_mail_delivery` 保存，每条记录代表一次发送请求中某个收件人的投递结果，关联 `brief_generation`。发送状态为 `PENDING`、`SENT` 或 `FAILED`；部分收件人失败不影响同一次请求中其他收件人继续发送。

内部 API 支持按最新记录倒序查询简报归档，按归档 ID 查询简报详情，并按归档 ID 查询邮件发送记录。归档不存在时返回 `404 Not Found`；存在但尚未发送邮件时，邮件记录返回空列表。

去重顺序：

1. 有 guid 时，按 `source_name + guid` 判断。
2. 没有 guid 但有 url 时，按 url 判断。
3. guid 和 url 都不可用时，按 contentHash 判断。

应用层去重和数据库唯一约束必须保持一致。新增文章字段时，需要同步更新 Flyway 迁移、`NewArticle`、`Article`、MyBatis Mapper 和测试。

## 配置与运行

完整配置分层、环境变量命名、必填项和当前配置清单见 [配置约定](configuration.md)。本项目的运行配置按两类处理：

- Spring Boot 标准基础设施配置继续使用官方属性，例如 `spring.datasource.*`、`spring.mail.*`、`server.port` 和 `springdoc.*`。
- 项目业务语义统一放在 `signal-brief.*`，例如 RSS 源、抓取超时、入库调度、内部 API、AI 摘要和邮件业务开关。

可能访问外部系统、产生投递行为或暴露内部入口的能力默认关闭，包括 RSS 定时、内部 API、OpenAPI、AI 摘要和邮件发送。RSS 源列表通过 `signal-brief.feeds` 维护，修改后需要重启应用。

环境变量和 profile 约束：

- `application.yaml` 只保存跨环境公共配置、安全默认值和必要配置项的注释示例，不设置 `spring.profiles.active`。
- `SPRING_PROFILES_ACTIVE` 由启动命令、CI 或部署平台显式指定；本地开发使用 `dev`，CI 使用 `test`，生产使用 `prod`。
- 本地 `dev` 配置可以为 Docker Compose 示例服务提供安全默认值；`test` 和 `prod` 的 datasource、端口等 Spring Boot 标准配置不在 YAML 中写占位符转发，统一依赖环境变量自动绑定；必需配置缺失时应在启动阶段失败。
- Spring Boot 标准配置优先使用官方属性和环境变量自动绑定；项目自定义配置才放入 `signal-brief.*` 并通过配置属性类表达启用条件和校验规则。默认值直接写在 YAML 中，环境变量名称通过 `.env.example` 和配置约定说明。
- 业务开关和内部接口开关默认关闭，避免启动时自动访问外部源或暴露管理入口。
- 新增环境变量时同步更新 `.env.example`、配置约定文档和相关 records。

本地日常开发默认只跑基础测试：

```bash
./mvnw -Dspring.docker.compose.enabled=false test
```

数据库集成测试、迁移验证和 Mapper 行为由 CI 运行完整 `verify`，CI 使用独立 PostgreSQL service，并关闭 Spring Boot Docker Compose 接管。

## 技术选型

当前实现以 `pom.xml` 为准：

| 模块 | 选型 |
| --- | --- |
| 主体框架 | Spring Boot 4 |
| 运行时 | Java 25 |
| 构建 | Maven Wrapper |
| 数据库 | PostgreSQL |
| 迁移 | Flyway |
| 数据访问 | MyBatis |
| RSS / Atom | ROME |
| HTTP | Spring `RestClient`，底层使用 Apache HttpClient 5 |
| AI 摘要 | OpenAI-compatible Chat Completions，通过 Spring `RestClient` 调用 |
| HTML 兜底 | jsoup |
| 邮件 | Spring Mail |
| API 文档 | springdoc-openapi |
| 工具库 | Apache Commons、Guava |

Spring Boot 已管理版本的依赖不显式声明版本；未被 Boot 管理的依赖在 `pom.xml` 属性中集中声明。

## AI 与简报原则

AI 模块围绕“可靠简报”设计，而不是追求模型能力堆叠：

- 模型 provider、model、base URL 和 API key 必须配置化。
- 默认关闭，开启后必须显式配置 provider 地址、API key 和模型。
- 当前实现使用 OpenAI-compatible Chat Completions，AI 只消费确定性 Markdown 草稿。
- 每条重要结论必须能追溯到原文链接，不改变草稿中已有 Markdown 链接目标。
- 不让模型编造来源、版本、日期或影响范围。
- 输出优先使用稳定 Markdown 结构。
- 英文技术资料保留必要英文术语，中文表达要面向 Java 后端开发者。
- 内容过多时先按条目压缩，再做板块合并，避免一次请求过长。

## 后续路线

已完成的近期里程碑：

- 已基于部署设计记录和部署手册完成一次阿里云内网试运行部署，验证第一版 `executable jar + systemd + Docker Compose PostgreSQL` 形态可用于个人试运行。
- 已使用真实 AI Provider 验证摘要 prompt、OpenAI-compatible Chat Completions 输出结构、成功归档链路和超时表现；试运行阶段建议将 AI 读取超时配置为 `60s`。
- 已增强 RSS / Atom 内容提取策略，在短摘要之外保存清洗后的正文片段，并覆盖 RSS `description`、RSS `content:encoded`、Atom `summary` 和 Atom `content` 等常见字段差异。

近期优先级：

1. 建立新增 RSS 源质量审计清单，检查字段完整性、内容所在字段、是否全文或短摘要、是否与已有源重复，再决定是否纳入默认源。
2. 给关键链路补充克制的运行日志，覆盖 RSS 入库、AI Provider 调用、简报归档和邮件发送，便于试运行排查但不输出密钥、正文全文或授权头。
3. 增加文章内容增强模块，针对 RSS 正文片段过短或缺失的文章抓取原文正文、清洗 HTML、截断入库，并让 AI 摘要优先消费增强内容。
4. 增加抓取失败告警策略，并基于运行记录沉淀源级健康状态。
5. 增加定时自动生成、归档和发送。
6. 扩展邮件投递能力，例如 HTML 模板、失败重试和发送耗时统计。

暂不做：

- 复杂 Web 管理后台。
- 多用户权限系统。
- 用户订阅系统。
- 泛化全文爬虫系统。
- 向量库和 RAG。
- 多模型复杂路由。
- 实时全量资讯监控。

## 项目记录

长期维护记录放在 `docs/records/`：

- `docs/records/rss-ingestion.md`：RSS 抓取、解析、去重和入库备忘。
- `docs/records/2026-07-04-rss-ingestion-scheduling.md`：RSS 定时入库和文章查询出口记录。
- `docs/records/rss-ingestion-run-record.md`：RSS 入库运行记录和源级明细设计。
- `docs/records/markdown-brief-generation.md`：Markdown 简报草稿生成记录。
- `docs/records/ai-summary-generation.md`：AI 摘要生成记录。
- `docs/records/brief-archive-mail-delivery.md`：简报归档与邮件发送基础记录。
- `docs/records/manual-trigger-api.md`：内部手动触发 API 和 OpenAPI 文档记录。
- `docs/records/deployment-runbook.md`：第一版部署和内网试运行设计记录。

本文档只描述项目定位、当前状态和路线。具体实现细节以源码、测试、迁移脚本和 records 文档为准。
